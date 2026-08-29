---
icon: lucide/waypoints
---

# Sensor Fusion Pipeline (`robot_localization`)

Extracted from the system architecture guide — this is RPi/host-side detail, not top-level
architecture. See [Architecture Guide](../architecture.md) for how this fits into the overall
system data flow.

To prevent position and heading drift during competition runs:

1. **Wheel Odometry (v_x):** `ackermann_steering_controller` calculates forward linear velocity from rear wheel encoders (`/ackermann_steering_controller/odometry`).
2. **IMU Heading (ω_z, θ):** The ICM-20948 IMU publishes angular velocity and rotation (`/imu/data`).
3. **EKF Fusion (`ekf_node`):** `robot_localization` fuses both streams to output smooth, drift-free `/odometry/filtered` and updates the dynamic `odom` → `base_link` TF transform.

See [Closed-Loop Control & Sensor Fusion](../stm32/control_loop.md#3-imu-ros2_control-robot_localization-proper-data-flow)
for the STM32-side half of this pipeline (what the IMU actually sends, and why `ekf.yaml` fuses
`angular_velocity` rather than `orientation`).
