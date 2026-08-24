---
icon: lucide/terminal
---

# Launch Files & Node Topic Interfaces

Complete guide to launch scripts, controller configurations, and topic interfaces in `mdp_ros`.

## Workspace Packages

| Package | Purpose | Primary Files |
| --- | --- | --- |
| **`mdp_description`** | URDF robot models & STL meshes | `mini_akm_robot.urdf` (Simulation), `mini_akm_real_robot.urdf` (Real Hardware) |
| **`mdp_bringup`** | System bringup launch scripts & controller YAMLs | `sim.launch.py`, `real.launch.py`, `ackermann_controller.yaml`, `real_controller.yaml` |

---

## Launch Entry Points

### 1. Simulation Mode (`sim.launch.py`)
Launches Gazebo Sim physics environment with `gz_ros2_control`:

```bash
pixi run sim
# Equivalent to: ros2 launch mdp_bringup sim.launch.py
```

- **Loads:** `mini_akm_robot.urdf` with `gz_ros2_control/GazeboSimSystem` plugin.
- **Starts Nodes:**
  - `robot_state_publisher` (Publishes `/robot_description` & TF tree)
  - `ros_gz_sim` (Gazebo 3D simulation engine)
  - `ros_gz_bridge` (Bridges `/clock`, `/cmd_vel`, `/tf` between ROS2 and Gazebo)
  - `spawner` ➔ `joint_state_broadcaster` (Publishes `/joint_states`)
  - `spawner` ➔ `ackermann_steering_controller` (Ackermann kinematics engine)

---

### 2. Real Hardware Mode (`real.launch.py`)
Launches host-side `topic_based_ros2_control` for physical robot operation:

```bash
pixi run hardware
# Equivalent to: ros2 launch mdp_bringup real.launch.py

# The STM32 shows up as different device names depending on host/driver
# (e.g. /dev/ttyACM0 on some machines, /dev/ttyUSB0 on others) - override
# with the serial_port launch argument rather than editing the launch file:
pixi run hardware serial_port:=/dev/ttyACM0
```

- **Loads:** `mini_akm_real_robot.urdf` with `topic_based_ros2_control/TopicBasedSystem` plugin.
- **Starts Nodes:**
  - `robot_state_publisher`
  - `ros2_control_node` (`controller_manager` configured with `real_controller.yaml`)
  - `spawner` ➔ `joint_state_broadcaster`
  - `spawner` ➔ `ackermann_steering_controller`
  - `mdp_hardware_bridge` (`serial_bridge_node`) — the actual STM32↔host transport, see below.

!!! warning "The `/ackermann_steering_controller/reference` remap must go on `ros2_control_node`, not the spawner"
    `real.launch.py` remaps `/ackermann_steering_controller/reference` to `/cmd_vel` on the `controller_manager` (`ros2_control_node`) `Node(...)`. That controller's actual subscriber lives inside `ros2_control_node` itself — the `spawner` process is a short-lived CLI that just calls a service to load/activate the controller and owns none of its topics, so a `remappings=` on the spawner silently does nothing. (This is different from `sim.launch.py`, where the equivalent remap lives in the URDF's `gz_ros2_control` plugin `<ros><remapping>` block instead, since Gazebo owns that controller manager internally.)

### 3. STM32 Serial Bridge (`mdp_hardware_bridge`)
Bridges the ROS2 graph to the STM32 MCU over the custom binary protocol on `USART3` — see [ADR 0002](../architecture.md#0002-custom-binary-serial-protocol-vs-micro-ros-rclc) and [Serial Protocol](../stm32/protocol.md). Launched automatically as part of `pixi run hardware`; can also be run standalone:

```bash
ros2 run mdp_hardware_bridge serial_bridge_node --ros-args -p serial_port:=/dev/ttyACM0
```

- **Device:** configurable via the `serial_port` parameter (default `/dev/ttyUSB0`).
- **Baud Rate:** `115200` (matching `USART3` on WHEELTEC C30D board).
- **Bridged Topics:** `/joint_commands` ➔ MCU Motor PWM & Servo, MCU Encoders/IMU ➔ `/joint_states`, `/imu/data`.

!!! note "Superseded micro-ROS Agent"
    An earlier design used the micro-ROS Agent (`pixi run agent`, standalone `Micro-XRCE-DDS-Agent`) for this same link. `mdp_hardware_bridge` replaced it ([ADR 0002](../architecture.md#0002-custom-binary-serial-protocol-vs-micro-ros-rclc)) — the `agent`/`agent-build` pixi tasks still exist but aren't part of the current `real.launch.py` path. See [ROS Workspace docs](index.md#micro-ros-agent-superseded-host-side).

---

## Core Topic Specifications

| Topic Name | Message Type | Direction | Publisher ➔ Subscriber | Description |
| --- | --- | --- | --- | --- |
| **`/cmd_vel`** | `geometry_msgs/TwistStamped` or `Twist` | Input | `teleop_twist_keyboard` / Task Runners ➔ `ackermann_steering_controller` | Velocity command setpoints (linear velocity $v_x$ m/s, steering rate $\omega_z$ rad/s). |
| **`/ackermann_steering_controller/reference`** | `geometry_msgs/TwistStamped` | Input | Remapped from `/cmd_vel` via `<ros><remapping>` in URDF | Internal setpoint reference topic for Ackermann controller. |
| **`/joint_commands`** | `sensor_msgs/JointState` | Output | `topic_based_ros2_control` ➔ `mdp_hardware_bridge` | Hardware joint target commands (position for steering knuckles `left_joint`/`right_joint`, velocity for rear wheels `lb_joint`/`rb_joint`). |
| **`/joint_states`** | `sensor_msgs/JointState` | Bidirectional | `gz_ros2_control` or `mdp_hardware_bridge` ➔ `joint_state_broadcaster` | Measured wheel encoder positions/velocities and steering angle feedback. |
| **`/imu/data`** | `sensor_msgs/Imu` | Input | `mdp_hardware_bridge` ➔ `robot_localization` | ICM-20948 IMU orientation quaternion and angular velocity $\omega_z$. |
| **`/odometry/filtered`** | `nav_msgs/Odometry` | Output | `robot_localization` (`ekf_node`) ➔ Autonomy Nodes | Fused, drift-free odometry combining rear wheel encoders ($v_x$) and IMU yaw ($\theta_{\text{yaw}}$). |
| **`/tf` / `/tf_static`** | `tf2_msgs/TFMessage` | Output | `robot_state_publisher` / `ekf_node` ➔ All Nodes | Coordinate transform tree connecting `odom` ➔ `base_link` ➔ wheel/sensor links. |
| **`/clock`** | `rosgraph_msgs/Clock` | Input | `ros_gz_bridge` ➔ All ROS Nodes | Simulation clock synchronization topic (active when `use_sim_time:=true`). |

!!! warning "`/joint_commands` field layout - `position[]`/`velocity[]` are grouped by interface type, not indexed by `name[]`"
    `topic_based_ros2_control`'s `TopicBasedSystem` hardware plugin publishes `/joint_commands` with `name` listing **every** joint that has a command interface (`left_joint`, `right_joint`, `lb_joint`, `rb_joint`, in URDF order), but `position[]` and `velocity[]` are **separate, independently-indexed arrays** containing only the joints that actually use that interface type — `position[0..1]` are `left_joint`/`right_joint`'s angles, `velocity[0..1]` are `lb_joint`/`rb_joint`'s speeds. They are **not** one slot per `name[]` entry. A consumer that indexes `position[i]`/`velocity[i]` using the same loop index it used for `name[i]` will silently read past the end of the shorter arrays (or misalign values) for any joint after the first interface-type group — `mdp_hardware_bridge`'s `onJointCommand()` had exactly this bug (rear wheels silently never received a velocity command). Track a separate running index per array as you iterate `name[]`, incrementing it only when you actually consume a value from that specific array.

!!! warning "Front-wheel steering sign convention - ROS says positive = left, the STM32 firmware says positive = right"
    ROS's `left_joint`/`right_joint` (`axis="0 0 1"`) follow REP-103: a positive joint angle is a counter-clockwise rotation about +Z, i.e. **steering left**. `mdp_stm32`'s `servo_set_angle()` (`src/servo.c`) is documented the other way — **positive `angle_deg` = right**. Neither convention is "wrong" in isolation (ROS's matches the rest of the ROS graph; the firmware's presumably matches how the physical servo horn/linkage was tested), but `mdp_hardware_bridge` has to reconcile them: `serial_bridge_node.cpp` negates the angle in both directions (`onJointCommand()` sending `steer_rad` to the MCU, and the telemetry parser converting `pkt.steer_deg` back into `/joint_states`). If you ever touch that conversion, keep it negated both ways, or steering will visibly point the wrong direction from what was commanded.

---

## Controller & Sensor Fusion Configurations

### Ackermann Controller Parameters (`ackermann_controller.yaml` & `real_controller.yaml`)

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

### Sensor Fusion Architecture (`robot_localization`)

To prevent position and orientation drift during Task 1 exploration and Task 2 fastest path runs:
- **`ackermann_steering_controller`** publishes raw wheel odometry (`/ackermann_steering_controller/odometry`).
- **`ekf_node`** (`robot_localization` package) subscribes to linear velocity ($v_x$) from wheel odometry and yaw rate ($\omega_z$) / orientation from `/imu/data`.
- Fused pose is published on `/odometry/filtered` and updates the dynamic `odom` ➔ `base_link` TF transform.
