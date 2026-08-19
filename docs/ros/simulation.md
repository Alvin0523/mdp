---
icon: lucide/box
---

# Gazebo Simulation & Controllers

Overview of Gazebo Sim integration, `gz_ros2_control`, topic remappings, and keyboard teleop controls.

## Controller Architecture

Per [ADR 0004](../architecture.md#0004-hardware-interface-topic_based_ros2_control-host-side-kinematics), both simulation and real hardware use the exact same `ackermann_steering_controller` implementation:

- **Simulation Mode**: `ackermann_steering_controller` connects directly to Gazebo via `gz_ros2_control` plugin.
- **Real Hardware Mode**: `ackermann_steering_controller` connects to `topic_based_ros2_control` which bridges topics to `micro-ROS Agent`.

---

## Gazebo Plugin & Remapping Setup

Inside [`src/mdp_description/urdf/mini_akm_robot.urdf.xacro`](file:///home/wm_u26/dev/school/mdp/mdp_ros/src/mdp_description/urdf/mini_akm_robot.urdf.xacro) (`is_sim:=true` branch), the `gz_ros2_control` plugin is configured to map standard `/cmd_vel` directly into the controller's reference interface:

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

## IMU-Fused Odometry (EKF)

Per [ADR 0007](../architecture.md#0007-imu-fused-odometry-via-robot_localization-ekf), `ackermann_steering_controller`'s wheel-only odometry is a two-parameter bicycle-model estimate with no term for the front wheels' kingpin-to-wheel-center offset, so its heading drifts from the true turn rate under friction — worst on sharp turns. In simulation this is now corrected by a `robot_localization` `ekf_node`:

- A simulated IMU is attached directly to `base_link` (`is_sim:=true` branch of the xacro) via the `gz-sim-imu-system` plugin, publishing `/imu/data`, bridged into ROS by `ros_gz_bridge` alongside `/clock` and `/tf`.
- `ekf_node` (config: `src/mdp_bringup/config/ekf.yaml`) fuses `/ackermann_steering_controller/odometry` (linear x only) with `/imu/data` (yaw + yaw rate).
- **TF ownership changed**: `ackermann_controller.yaml` now sets `enable_odom_tf: false` — `ekf_node` is the sole publisher of `odom→base_footprint` TF in sim, avoiding two nodes competing for the same transform. `task2_runner.py`'s pose estimate (via TF lookup) is unaffected in code, but now reflects the fused estimate rather than raw wheel odometry.
- Both `sim.launch.py` and `task2_sim.launch.py` launch `ekf_node`.

Real hardware is **not yet wired up** — `real_controller.yaml` still has `enable_odom_tf: true` and `real.launch.py` does not launch an EKF node, because `mdp_stm32` firmware has no IMU or encoder publishing yet (still stubs, see `docs/architecture.md` Section 5). A matching `src/mdp_bringup/config/ekf_real.yaml` (`base_link_frame: base_link`, matching `real_controller.yaml`'s `base_frame_id`) is prepared so activating it later is a drop-in once that firmware exists.

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
