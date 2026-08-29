---
icon: lucide/terminal-square
---

# Launch Files & Topics

Basic ROS2 concepts (nodes, topics, launch files): see the official
[ROS2 Concepts guide](https://docs.ros.org/en/jazzy/Concepts/Basic.html). This page covers this
project's launch files and the topics that connect everything.

## Launch Entry Points

=== "⚡ Real Hardware"

    ### Real Hardware Mode (`real.launch.py`)

    Launches host-side `topic_based_ros2_control` for physical robot operation. The STM32 shows up as
    different device names depending on host/driver (e.g. `/dev/ttyACM0` on some machines,
    `/dev/ttyUSB0` on others) — override with the `serial_port` launch argument rather than editing the
    launch file.

    - **Loads:** `mini_akm_real_robot.urdf` with `topic_based_ros2_control/TopicBasedSystem` plugin.
    - **Starts Nodes:**
      - `robot_state_publisher`
      - `ros2_control_node` (`controller_manager` configured with `real_controller.yaml`)
      - `spawner` ➔ `joint_state_broadcaster`
      - `spawner` ➔ `ackermann_steering_controller`
      - `mdp_bridge` (`serial_bridge_node`) — the actual STM32↔host transport, see below.
      - `robot_localization` (`ekf_node`) — fuses wheel odometry + IMU into `/odometry/filtered`, see [ROS2 EKF Localization](ros2_ekf_localization.md).

    !!! note "Sim doesn't run the EKF yet"
        `sim.launch.py` has no `/imu/data` source (no IMU sensor plugin bridged from Gazebo), so `ekf_node` is only wired into `real.launch.py` for now. Gazebo's own ground-truth state is accurate enough for sim that the EKF isn't solving a problem there yet anyway.

    !!! warning "The `/ackermann_steering_controller/reference` remap must go on `ros2_control_node`, not the spawner"
        `real.launch.py` remaps `/ackermann_steering_controller/reference` to `/cmd_vel` on the `controller_manager` (`ros2_control_node`) `Node(...)`. That controller's actual subscriber lives inside `ros2_control_node` itself — the `spawner` process is a short-lived CLI that just calls a service to load/activate the controller and owns none of its topics, so a `remappings=` on the spawner silently does nothing. (This is different from `sim.launch.py`, where the equivalent remap lives in the URDF's `gz_ros2_control` plugin `<ros><remapping>` block instead, since Gazebo owns that controller manager internally.)

    ### STM32 Serial Bridge (`mdp_bridge`)

    Bridges the ROS2 graph to the STM32 MCU over the custom binary protocol on `USART3` — see [STM32: Serial Protocol](../stm32/serial_protocol.md#serial-protocol). Launched automatically as part of `pixi run real`; can also be run standalone (see [Quickstart: Pixi Task Reference](../quickstart.md#pixi-task-reference-mdp_ros)).

    - **Device:** configurable via the `serial_port` parameter (default `/dev/ttyUSB0`).
    - **Baud Rate:** `115200` (matching `USART3` on WHEELTEC C30D board).
    - **Bridged Topics:** `/joint_commands` ➔ MCU Motor PWM & Servo, MCU Encoders/IMU ➔ `/joint_states_raw`, `/imu/data`, `/estop`, `/battery_state`.

    !!! warning "`/joint_states` has two writers if `joint_states_topic` isn't split from `joint_state_broadcaster`'s output"
        `mdp_bridge` publishes the STM32's raw encoder/steering feedback on `/joint_states_raw`, which `mini_akm_real_robot.urdf`'s `TopicBasedSystem` reads as its hardware state input. `joint_state_broadcaster` then re-publishes those same values (read back out of `ros2_control`'s state interfaces) on `/joint_states` for the rest of the graph. If `TopicBasedSystem`'s `joint_states_topic` param is ever pointed at `/joint_states` directly (as it originally was), that topic gets **two independent publishers** — the bridge's raw feed (fixed order `left_joint, right_joint, lb_joint, rb_joint`) and the broadcaster's aggregated one (alphabetical order `lb_joint, left_joint, rb_joint, right_joint`) — and `ros2 topic echo /joint_states` shows messages from both interleaved, with the field order appearing to randomly swap between reads. Not a data-correctness bug (both report the same underlying values), but any consumer indexing the arrays positionally instead of matching `name[]` would break intermittently. Keep `joint_states_topic` on its own name (`/joint_states_raw`) and remap the bridge node's `Node(...)` (`remappings=[('/joint_states', '/joint_states_raw')]`) rather than editing the C++ source's hardcoded topic string.

=== "▶️ Simulation"

    ### Simulation Mode (`sim.launch.py`)

    Launches Gazebo Sim physics environment with `gz_ros2_control`.

    - **Loads:** `mini_akm_robot.urdf` with `gz_ros2_control/GazeboSimSystem` plugin.
    - **Starts Nodes:**
      - `robot_state_publisher` (Publishes `/robot_description` & TF tree)
      - `ros_gz_sim` (Gazebo 3D simulation engine)
      - `ros_gz_bridge` (Bridges `/clock`, `/cmd_vel`, `/tf` between ROS2 and Gazebo)
      - `spawner` ➔ `joint_state_broadcaster` (Publishes `/joint_states`)
      - `spawner` ➔ `ackermann_steering_controller` (Ackermann kinematics engine)

---

## Core Topic Specifications

| Topic Name | Message Type | Direction | Publisher ➔ Subscriber | Description |
| --- | --- | --- | --- | --- |
| **`/cmd_vel`** | `geometry_msgs/TwistStamped` or `Twist` | Input | `teleop_twist_keyboard` / Task Runners ➔ `ackermann_steering_controller` | Velocity command setpoints (linear velocity $v_x$ m/s, steering rate $\omega_z$ rad/s). |
| **`/ackermann_steering_controller/reference`** | `geometry_msgs/TwistStamped` | Input | Remapped from `/cmd_vel` via `<ros><remapping>` in URDF | Internal setpoint reference topic for Ackermann controller. |
| **`/joint_commands`** | `sensor_msgs/JointState` | Output | `topic_based_ros2_control` ➔ `mdp_bridge` | Hardware joint target commands (position for steering knuckles `left_joint`/`right_joint`, velocity for rear wheels `lb_joint`/`rb_joint`). |
| **`/joint_states_raw`** | `sensor_msgs/JointState` | Internal | `mdp_bridge` ➔ `TopicBasedSystem` (real HW only) | STM32's raw encoder/steering feedback, fed into `ros2_control`'s state interfaces. Not the graph-wide topic - see warning below. |
| **`/joint_states`** | `sensor_msgs/JointState` | Output | `joint_state_broadcaster` (real HW) / `gz_ros2_control` (sim) ➔ rest of the graph | Aggregated joint state for RViz, TF, `ackermann_steering_controller`, autonomy nodes. Single publisher on real HW - see warning below. |
| **`/imu/data`** | `sensor_msgs/Imu` | Input | `mdp_bridge` ➔ `robot_localization` | ICM-20948 IMU orientation quaternion and angular velocity $\omega_z$. |
| **`/odometry/filtered`** | `nav_msgs/Odometry` | Output | `robot_localization` (`ekf_node`) ➔ Autonomy Nodes | Fused, drift-free odometry combining rear wheel encoders ($v_x$) and IMU yaw ($\theta_{\text{yaw}}$). |
| **`/tf` / `/tf_static`** | `tf2_msgs/TFMessage` | Output | `robot_state_publisher` / `ekf_node` ➔ All Nodes | Coordinate transform tree connecting `odom` ➔ `base_link` ➔ wheel/sensor links. |
| **`/clock`** | `rosgraph_msgs/Clock` | Input | `ros_gz_bridge` ➔ All ROS Nodes | Simulation clock synchronization topic (active when `use_sim_time:=true`). |
| **`/estop`** | `std_msgs/Bool` | Output | `mdp_bridge` | Onboard `PD3` motor switch state. Not yet consumed by any node - informational only. |
| **`/battery_state`** | `sensor_msgs/BatteryState` | Output | `mdp_bridge` | Pack voltage from the STM32's ADC. Not yet consumed by any node - informational only. |
| **`/hardware_bridge/link_ok`** | `std_msgs/Bool` | Output | `mdp_bridge` | Serial-link watchdog - false if no valid telemetry frame in the last 500ms (mirrors the MCU's own command-timeout window). Not yet consumed by any node - informational only. |
| **`/yolo_result`** | `std_msgs/String` | Output | `yolo_detector` ➔ `task1_runner` / `task2_runner` | Detected target/arrow label string. |
| **`/planned_path`**, **`/waypoint_markers`**, **`/robot_pose`** | `nav_msgs/Path`, `visualization_msgs/MarkerArray`, `geometry_msgs/PoseStamped` | Output | `task2_runner` | RViz-visualization-only topics for Task 2's planned route; no other node subscribes. |

!!! warning "`/joint_commands` field layout - `position[]`/`velocity[]` are grouped by interface type, not indexed by `name[]`"
    `topic_based_ros2_control`'s `TopicBasedSystem` hardware plugin publishes `/joint_commands` with `name` listing **every** joint that has a command interface (`left_joint`, `right_joint`, `lb_joint`, `rb_joint`, in URDF order), but `position[]` and `velocity[]` are **separate, independently-indexed arrays** containing only the joints that actually use that interface type — `position[0..1]` are `left_joint`/`right_joint`'s angles, `velocity[0..1]` are `lb_joint`/`rb_joint`'s speeds. They are **not** one slot per `name[]` entry. A consumer that indexes `position[i]`/`velocity[i]` using the same loop index it used for `name[i]` will silently read past the end of the shorter arrays (or misalign values) for any joint after the first interface-type group — `mdp_bridge`'s `onJointCommand()` had exactly this bug (rear wheels silently never received a velocity command). Track a separate running index per array as you iterate `name[]`, incrementing it only when you actually consume a value from that specific array.

!!! warning "Front-wheel steering sign convention - ROS says positive = left, the STM32 firmware says positive = right"
    ROS's `left_joint`/`right_joint` (`axis="0 0 1"`) follow REP-103: a positive joint angle is a counter-clockwise rotation about +Z, i.e. **steering left**. `mdp_stm32`'s `servo_set_angle()` (`src/servo.c`) is documented the other way — **positive `angle_deg` = right**. Neither convention is "wrong" in isolation (ROS's matches the rest of the ROS graph; the firmware's presumably matches how the physical servo horn/linkage was tested), but `mdp_bridge` has to reconcile them: `serial_bridge_node.cpp` negates the angle in both directions (`onJointCommand()` sending `steer_rad` to the MCU, and the telemetry parser converting `pkt.steer_deg` back into `/joint_states_raw`). If you ever touch that conversion, keep it negated both ways, or steering will visibly point the wrong direction from what was commanded.

!!! bug "`mdp_bringup` task-runner topic-name mismatches found during a topic-naming audit"
    Two nodes were publishing/subscribing to topics nobody else used, both silent (no error, no crash, the callback/subscriber just never fired):

    - `task2_runner.py` subscribed to `/arrow_detection`, but `yolo_detector.py` (the only publisher of YOLO results) publishes on `/yolo_result` - the same topic `task1_runner.py` already used correctly. `arrow_callback` never fired. Fixed by pointing the subscription at `/yolo_result`.
    - `task1_runner.py` published every command twice: once to `/cmd_vel` (correct - matches the remap both `real.launch.py` and `sim.launch.py` apply to `ackermann_steering_controller`'s reference subscription) and once to `/ackermann_steering_controller/reference` directly, which nothing subscribes to post-remap - dead traffic. `pure_pursuit_follower.py` had the same leftover pattern gone one step further: it called `self.ref_pub.publish(msg)` in `publish_cmd()` without `self.ref_pub` ever being created in `__init__` - a guaranteed `AttributeError` the first time `publish_cmd()`/`stop()` ran. Fixed by removing the dead second publish in both files; `/cmd_vel` alone is correct and sufficient.
