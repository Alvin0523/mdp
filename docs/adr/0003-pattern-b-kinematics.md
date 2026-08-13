# 0003. Compute kinematics on the host, not the MCU (Pattern B)

**Status:** :material-check-circle: Accepted
**Date:** 2026-08-07

## Context

There are two valid ways to split "who computes the kinematics" between the MCU and the host:

- **Pattern A (smart MCU):** MCU subscribes to `/cmd_vel` directly, computes Ackermann kinematics + PID on-device, publishes odometry back. The host never touches `ros2_control`. This is how TurtleBot3's OpenCR board works, and how Gazebo mirrors it in sim via its own `gz-sim-ackermann-steering-system` plugin — a second, independently-written implementation of the same math.
- **Pattern B (dumb MCU):** MCU does pure I/O — encoders in, servo/motor commands out. All kinematics and odometry move to the host via `ros2_control` + `ackermann_steering_controller`. The same controller code runs against both `gz_ros2_control` (sim) and `topic_based_ros2_control` (real) — one implementation, not two independently-written ones that can drift apart.

Full comparison in the original analysis: `references/mdp_ws/docs/control-architecture.md`.

## Decision

Pattern B. The MCU's job is pure I/O — motor encoders, servo, IMU, battery — no on-device kinematics. All Ackermann math and odometry live in `ackermann_steering_controller` on the host.

## Consequences

Sim/real parity is structural, not something to keep manually in sync: the same controller code runs in both, so behavior differences between sim and real are much less likely to be a "someone forgot to update the other implementation" bug. The cost: extra packages (`ros2_control`, `ros2_controllers`, `gz_ros2_control`, `topic_based_ros2_control`) and no hard real-time guarantee, since the host↔MCU link is best-effort over topics rather than a tight on-device control loop. IMU and battery telemetry stay as plain ROS topics, published directly, bypassing `ros2_control` entirely — it only covers joint/actuator interfaces.
