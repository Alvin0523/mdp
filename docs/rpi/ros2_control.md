---
icon: lucide/joystick
---

# ROS2 Control (`ros2_control`)

`ros2_control` is ROS2's standard hardware-abstraction framework: a **controller** (like
`ackermann_steering_controller`) does the kinematics math and knows nothing about the actual
hardware — it just reads/writes "joint state/command interfaces". A swappable **hardware plugin**
underneath is what actually talks to Gazebo or the real STM32. That's what lets the exact same
controller drive both sim and real hardware unmodified.

## Sim vs. Real Hardware

Both simulation and real hardware use the exact same `ackermann_steering_controller` implementation:

- **Simulation Mode**: `ackermann_steering_controller` connects directly to Gazebo via `gz_ros2_control` plugin.
- **Real Hardware Mode**: `ackermann_steering_controller` connects to `topic_based_ros2_control` which bridges topics to `mdp_bridge` (`serial_bridge_node`). The originally-planned micro-ROS Agent path was superseded by this custom binary protocol.

## Ackermann Controller Parameters (`ackermann_controller.yaml` & `real_controller.yaml`)

Both simulation (`ackermann_controller.yaml`) and real hardware (`real_controller.yaml`) share identical kinematic parameters matching the URDF CAD geometry:

| Parameter Key | Value | Explanation |
| --- | --- | --- |
| `steering_joints_names` | `['left_joint', 'right_joint']` | Front steering knuckle revolute joint names. |
| `traction_joints_names` | `['lb_joint', 'rb_joint']` | Rear driven wheel continuous joint names. |
| `wheelbase` | `0.1433` | Measured front-to-rear axle center distance (m). |
| `steering_track_width` | `0.1040` | Left-to-right steering kingpin pivot distance (m). |
| `traction_track_width` | `0.1600` | Left-to-right rear wheel center distance (m). |
| `traction_wheels_radius` | `0.0325` | Drive wheel radius (65 mm diameter). |
| `reference_timeout` | `10.0` | Command heartbeat timeout (seconds). |
| `base_frame_id` | `base_footprint` (Sim) / `base_link` (Real) | Robot base frame anchor. |

## Gazebo Plugin & Remapping Setup

Inside [`src/mdp_description/urdf/mini_akm_robot.urdf`](file:///home/wm_u26/dev/school/mdp/mdp_ros/src/mdp_description/urdf/mini_akm_robot.urdf), the `gz_ros2_control` plugin is configured to map standard `/cmd_vel` directly into the controller's reference interface:

```xml
<gazebo>
  <plugin filename="gz_ros2_control-system" name="gz_ros2_control::GazeboSimROS2ControlPlugin">
    <parameters>package://mdp_bringup/config/ackermann_controller.yaml</parameters>
    <ros>
      <remapping>/ackermann_steering_controller/reference:=/cmd_vel</remapping>
    </ros>
  </plugin>
</gazebo>
```

## URDF Physics & Joint Limits

To ensure physical realism matching the **HWZ020** steering servo and **MG513P3012V** drive motors:

1. **REP-103 Joint Axis Orientation:**
   - Rear Wheels (`lb_joint`, `rb_joint`): `<axis xyz="0 1 0"/>` (positive rotation = forward motion).
   - Steering Knuckles (`left_joint`, `right_joint`): `<axis xyz="0 0 1"/>` (positive angle = left turn).

2. **Steering Joint Limits:**
   - Angle limits: `lower="-0.39" upper="0.39"` ($\pm 22.35^\circ$) — the servo's bare datasheet spec; intentionally different from the real robot's measured `±0.5672 rad` (32.5°), see [STM32: Servo Range & Steering Calibration](../stm32/tuning.md#servo-range-steering-calibration).
   - Torque limit: `effort="10.0"` (prevents Gazebo solver contact lockup).
   - Speed limit: `velocity="5.0"` (matches HWZ020 servo max speed of 6.54 rad/s).
