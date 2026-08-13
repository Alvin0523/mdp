---
icon: lucide/box
---

# Gazebo Simulation & Controllers

Overview of Gazebo Sim integration, `gz_ros2_control`, topic remappings, and keyboard teleop controls.

## Controller Architecture

Per [ADR 0003](../adr/0003-pattern-b-kinematics.md), both simulation and real hardware use the exact same `ackermann_steering_controller` implementation:

- **Simulation Mode**: `ackermann_steering_controller` connects directly to Gazebo via `gz_ros2_control` plugin.
- **Real Hardware Mode**: `ackermann_steering_controller` connects to `topic_based_ros2_control` which bridges topics to `micro-ROS Agent`.

---

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

---

## URDF Physics & Joint Limits

To ensure physical realism matching the **HWZ020** steering servo and **MG513P3012V** drive motors:

1. **REP-103 Joint Axis Orientation:**
   - Rear Wheels (`lb_joint`, `rb_joint`): `<axis xyz="0 1 0"/>` (positive rotation = forward motion).
   - Steering Knuckles (`left_joint`, `right_joint`): `<axis xyz="0 0 1"/>` (positive angle = left turn).

2. **Steering Joint Limits:**
   - Angle limits: `lower="-0.39" upper="0.39"` ($\pm 22.35^\circ$).
   - Torque limit: `effort="10.0"` (prevents Gazebo solver contact lockup).
   - Speed limit: `velocity="5.0"` (matches HWZ020 servo max speed of 6.54 rad/s).

---

## Pixi Shortcut Commands

| Task | Command | Description |
| --- | --- | --- |
| **Launch Simulation** | `pixi run sim` | Launches Gazebo Sim, spawns robot URDF, starts `joint_state_broadcaster` and `ackermann_steering_controller`. |
| **Run Teleop** | `pixi run teleop` | Launches `teleop_twist_keyboard` with `-p stamped:=true` to drive the car with keyboard. |
| **Launch Foxglove** | `pixi run foxglove` | Starts `foxglove_bridge` WebSocket server on port `8765` (`use_sim_time:=true`). |
| **Build Workspace** | `pixi run build` | Builds `mdp_description` and `mdp_bringup` via `colcon`. |

---

## Driving via Keyboard Teleop

When `pixi run teleop` is active (ensure terminal window has keyboard focus):

- **`i`** / **`,`**: Drive straight **Forward** / **Backward**
- **`j`** / **`l`**: Steering **Left** / **Right**
- **`u`** / **`o`**: Drive **Forward + Left** / **Forward + Right**
- **`k`**: **Stop** (zero velocity)
