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
```

- **Loads:** `mini_akm_real_robot.urdf` with `topic_based_ros2_control/TopicBasedSystem` plugin.
- **Starts Nodes:**
  - `robot_state_publisher`
  - `ros2_control_node` (`controller_manager` configured with `real_controller.yaml`)
  - `spawner` ➔ `joint_state_broadcaster`
  - `spawner` ➔ `ackermann_steering_controller`

---

### 3. micro-ROS Serial Transport Link (`agent`)
Launches the micro-ROS Agent daemon to connect the host ROS2 graph to the STM32 MCU over USB serial:

```bash
pixi run agent
# Equivalent to: ros2 run micro_ros_agent micro_ros_agent serial --dev /dev/ttyUSB0 -b 115200
```

- **Device:** `/dev/ttyUSB0` (or Docker `--privileged` USB serial device).
- **Baud Rate:** `115200` (matching `USART3` on WHEELTEC C30D board).
- **Bridged Topics:** `/joint_commands` ➔ MCU Motor PWM & Servo, MCU Encoders ➔ `/joint_states`.

---

## Core Topic Specifications

| Topic Name | Message Type | Direction | Publisher ➔ Subscriber | Description |
| --- | --- | --- | --- | --- |
| **`/cmd_vel`** | `geometry_msgs/TwistStamped` or `Twist` | Input | `teleop_twist_keyboard` / Task Runners ➔ `ackermann_steering_controller` | Velocity command setpoints (linear velocity $v_x$ m/s, steering rate $\omega_z$ rad/s). |
| **`/ackermann_steering_controller/reference`** | `geometry_msgs/TwistStamped` | Input | Remapped from `/cmd_vel` via `<ros><remapping>` in URDF | Internal setpoint reference topic for Ackermann controller. |
| **`/joint_commands`** | `sensor_msgs/JointState` | Output | `topic_based_ros2_control` ➔ `micro_ros_agent` | Hardware joint target commands (position for steering knuckles `left_joint`/`right_joint`, velocity for rear wheels `lb_joint`/`rb_joint`). |
| **`/joint_states`** | `sensor_msgs/JointState` | Bidirectional | `gz_ros2_control` or `micro_ros_agent` ➔ `joint_state_broadcaster` | Measured wheel encoder positions/velocities and steering angle feedback. |
| **`/imu/data`** | `sensor_msgs/Imu` | Input | MCU micro-ROS ➔ `robot_localization` | ICM-20948 IMU orientation quaternion and angular velocity $\omega_z$. |
| **`/odometry/filtered`** | `nav_msgs/Odometry` | Output | `robot_localization` (`ekf_node`) ➔ Autonomy Nodes | Fused, drift-free odometry combining rear wheel encoders ($v_x$) and IMU yaw ($\theta_{\text{yaw}}$). |
| **`/tf` / `/tf_static`** | `tf2_msgs/TFMessage` | Output | `robot_state_publisher` / `ekf_node` ➔ All Nodes | Coordinate transform tree connecting `odom` ➔ `base_link` ➔ wheel/sensor links. |
| **`/clock`** | `rosgraph_msgs/Clock` | Input | `ros_gz_bridge` ➔ All ROS Nodes | Simulation clock synchronization topic (active when `use_sim_time:=true`). |

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
