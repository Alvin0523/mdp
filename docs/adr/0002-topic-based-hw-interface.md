# 0002. Use `topic_based_ros2_control` for the hardware interface

**Status:** :material-check-circle: Accepted
**Date:** 2026-08-07

## Context

`ros2_control` needs a hardware_interface plugin to talk to the real robot. The MCU already speaks ROS2 topics via micro-ROS ([0001](0001-microros-transport.md)), so a hand-rolled serial protocol plus a matching C++ parser on the host would be duplicating work the transport already does.

## Decision

Use `topic_based_ros2_control` as the real-hardware `hardware_interface` — it maps `ros2_control`'s command/state interfaces onto plain topics, with no protocol of its own to write or maintain.

## Consequences

Works with any `ros2_control` controller, including `ackermann_steering_controller` ([0003](0003-pattern-b-kinematics.md)), without custom serial-parsing code. It isn't on conda, so it has to be vendored from source into `mdp_ros/src/`. It's also async over topics, not a tight real-time call — acceptable here since the MCU does no on-device kinematics that would need hard real-time coordination from the host side.
