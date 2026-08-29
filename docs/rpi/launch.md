---
icon: lucide/terminal
---

# Launch Files & Node Topic Interfaces

Complete guide to launch scripts, controller configurations, and topic interfaces in `mdp_ros`.

## Workspace Packages

| Package | Purpose | Primary Files |
| --- | --- | --- |
| **`mdp_description`** | URDF robot models & STL meshes | `mini_akm_robot.urdf` (Simulation), `mini_akm_real_robot.urdf` (Real Hardware) |
| **`mdp_bringup`** | System bringup launch scripts & controller YAMLs | `sim.launch.py`, `real.launch.py`, `ackermann_controller.yaml`, `real_controller.yaml`, `ekf.yaml` |

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
pixi run real
# Equivalent to: ros2 launch mdp_bringup real.launch.py

# The STM32 shows up as different device names depending on host/driver
# (e.g. /dev/ttyACM0 on some machines, /dev/ttyUSB0 on others) - override
# with the serial_port launch argument rather than editing the launch file:
pixi run real serial_port:=/dev/ttyACM0
```

- **Loads:** `mini_akm_real_robot.urdf` with `topic_based_ros2_control/TopicBasedSystem` plugin.
- **Starts Nodes:**
  - `robot_state_publisher`
  - `ros2_control_node` (`controller_manager` configured with `real_controller.yaml`)
  - `spawner` ➔ `joint_state_broadcaster`
  - `spawner` ➔ `ackermann_steering_controller`
  - `mdp_hardware_bridge` (`serial_bridge_node`) — the actual STM32↔host transport, see below.
  - `robot_localization` (`ekf_node`) — fuses wheel odometry + IMU into `/odometry/filtered`, see [Sensor Fusion](#sensor-fusion-architecture-robot_localization) below.

!!! note "Sim doesn't run the EKF yet"
    `sim.launch.py` has no `/imu/data` source (no IMU sensor plugin bridged from Gazebo), so `ekf_node` is only wired into `real.launch.py` for now. Gazebo's own ground-truth state is accurate enough for sim that the EKF isn't solving a problem there yet anyway.

!!! warning "The `/ackermann_steering_controller/reference` remap must go on `ros2_control_node`, not the spawner"
    `real.launch.py` remaps `/ackermann_steering_controller/reference` to `/cmd_vel` on the `controller_manager` (`ros2_control_node`) `Node(...)`. That controller's actual subscriber lives inside `ros2_control_node` itself — the `spawner` process is a short-lived CLI that just calls a service to load/activate the controller and owns none of its topics, so a `remappings=` on the spawner silently does nothing. (This is different from `sim.launch.py`, where the equivalent remap lives in the URDF's `gz_ros2_control` plugin `<ros><remapping>` block instead, since Gazebo owns that controller manager internally.)

### 3. STM32 Serial Bridge (`mdp_hardware_bridge`)
Bridges the ROS2 graph to the STM32 MCU over the custom binary protocol on `USART3` — see [ADR 0002](../architecture.md#0002-custom-binary-serial-protocol-vs-micro-ros-rclc) and [Serial Protocol](../stm32/protocol.md). Launched automatically as part of `pixi run real`; can also be run standalone:

```bash
ros2 run mdp_hardware_bridge serial_bridge_node --ros-args -p serial_port:=/dev/ttyACM0
```

- **Device:** configurable via the `serial_port` parameter (default `/dev/ttyUSB0`).
- **Baud Rate:** `115200` (matching `USART3` on WHEELTEC C30D board).
- **Bridged Topics:** `/joint_commands` ➔ MCU Motor PWM & Servo, MCU Encoders/IMU ➔ `/joint_states_raw`, `/imu/data`, `/estop`, `/battery_state`.

!!! warning "`/joint_states` has two writers if `joint_states_topic` isn't split from `joint_state_broadcaster`'s output"
    `mdp_hardware_bridge` publishes the STM32's raw encoder/steering feedback on `/joint_states_raw`, which `mini_akm_real_robot.urdf`'s `TopicBasedSystem` reads as its hardware state input. `joint_state_broadcaster` then re-publishes those same values (read back out of `ros2_control`'s state interfaces) on `/joint_states` for the rest of the graph. If `TopicBasedSystem`'s `joint_states_topic` param is ever pointed at `/joint_states` directly (as it originally was), that topic gets **two independent publishers** — the bridge's raw feed (fixed order `left_joint, right_joint, lb_joint, rb_joint`) and the broadcaster's aggregated one (alphabetical order `lb_joint, left_joint, rb_joint, right_joint`) — and `ros2 topic echo /joint_states` shows messages from both interleaved, with the field order appearing to randomly swap between reads. Not a data-correctness bug (both report the same underlying values), but any consumer indexing the arrays positionally instead of matching `name[]` would break intermittently. Keep `joint_states_topic` on its own name (`/joint_states_raw`) and remap the bridge node's `Node(...)` (`remappings=[('/joint_states', '/joint_states_raw')]`) rather than editing the C++ source's hardcoded topic string.

!!! note "Superseded micro-ROS Agent"
    An earlier design used the micro-ROS Agent (`pixi run agent`, standalone `Micro-XRCE-DDS-Agent`) for this same link. `mdp_hardware_bridge` replaced it ([ADR 0002](../architecture.md#0002-custom-binary-serial-protocol-vs-micro-ros-rclc)) — the `agent`/`agent-build` pixi tasks still exist but aren't part of the current `real.launch.py` path. See [ROS Workspace docs](index.md#micro-ros-agent-superseded-host-side).

---

## Core Topic Specifications

| Topic Name | Message Type | Direction | Publisher ➔ Subscriber | Description |
| --- | --- | --- | --- | --- |
| **`/cmd_vel`** | `geometry_msgs/TwistStamped` or `Twist` | Input | `teleop_twist_keyboard` / Task Runners ➔ `ackermann_steering_controller` | Velocity command setpoints (linear velocity $v_x$ m/s, steering rate $\omega_z$ rad/s). |
| **`/ackermann_steering_controller/reference`** | `geometry_msgs/TwistStamped` | Input | Remapped from `/cmd_vel` via `<ros><remapping>` in URDF | Internal setpoint reference topic for Ackermann controller. |
| **`/joint_commands`** | `sensor_msgs/JointState` | Output | `topic_based_ros2_control` ➔ `mdp_hardware_bridge` | Hardware joint target commands (position for steering knuckles `left_joint`/`right_joint`, velocity for rear wheels `lb_joint`/`rb_joint`). |
| **`/joint_states_raw`** | `sensor_msgs/JointState` | Internal | `mdp_hardware_bridge` ➔ `TopicBasedSystem` (real HW only) | STM32's raw encoder/steering feedback, fed into `ros2_control`'s state interfaces. Not the graph-wide topic - see warning below. |
| **`/joint_states`** | `sensor_msgs/JointState` | Output | `joint_state_broadcaster` (real HW) / `gz_ros2_control` (sim) ➔ rest of the graph | Aggregated joint state for RViz, TF, `ackermann_steering_controller`, autonomy nodes. Single publisher on real HW - see warning below. |
| **`/imu/data`** | `sensor_msgs/Imu` | Input | `mdp_hardware_bridge` ➔ `robot_localization` | ICM-20948 IMU orientation quaternion and angular velocity $\omega_z$. |
| **`/odometry/filtered`** | `nav_msgs/Odometry` | Output | `robot_localization` (`ekf_node`) ➔ Autonomy Nodes | Fused, drift-free odometry combining rear wheel encoders ($v_x$) and IMU yaw ($\theta_{\text{yaw}}$). |
| **`/tf` / `/tf_static`** | `tf2_msgs/TFMessage` | Output | `robot_state_publisher` / `ekf_node` ➔ All Nodes | Coordinate transform tree connecting `odom` ➔ `base_link` ➔ wheel/sensor links. |
| **`/clock`** | `rosgraph_msgs/Clock` | Input | `ros_gz_bridge` ➔ All ROS Nodes | Simulation clock synchronization topic (active when `use_sim_time:=true`). |
| **`/estop`** | `std_msgs/Bool` | Output | `mdp_hardware_bridge` | Onboard `PD3` e-stop switch state. Not yet consumed by any node - informational only. |
| **`/battery_state`** | `sensor_msgs/BatteryState` | Output | `mdp_hardware_bridge` | Pack voltage from the STM32's ADC. Not yet consumed by any node - informational only. |
| **`/hardware_bridge/link_ok`** | `std_msgs/Bool` | Output | `mdp_hardware_bridge` | Serial-link watchdog - false if no valid telemetry frame in the last 500ms (mirrors the MCU's own command-timeout window). Not yet consumed by any node - informational only. |
| **`/yolo_result`** | `std_msgs/String` | Output | `yolo_detector` ➔ `task1_runner` / `task2_runner` | Detected target/arrow label string. |
| **`/planned_path`**, **`/waypoint_markers`**, **`/robot_pose`** | `nav_msgs/Path`, `visualization_msgs/MarkerArray`, `geometry_msgs/PoseStamped` | Output | `task2_runner` | RViz-visualization-only topics for Task 2's planned route; no other node subscribes. |

!!! warning "`/joint_commands` field layout - `position[]`/`velocity[]` are grouped by interface type, not indexed by `name[]`"
    `topic_based_ros2_control`'s `TopicBasedSystem` hardware plugin publishes `/joint_commands` with `name` listing **every** joint that has a command interface (`left_joint`, `right_joint`, `lb_joint`, `rb_joint`, in URDF order), but `position[]` and `velocity[]` are **separate, independently-indexed arrays** containing only the joints that actually use that interface type — `position[0..1]` are `left_joint`/`right_joint`'s angles, `velocity[0..1]` are `lb_joint`/`rb_joint`'s speeds. They are **not** one slot per `name[]` entry. A consumer that indexes `position[i]`/`velocity[i]` using the same loop index it used for `name[i]` will silently read past the end of the shorter arrays (or misalign values) for any joint after the first interface-type group — `mdp_hardware_bridge`'s `onJointCommand()` had exactly this bug (rear wheels silently never received a velocity command). Track a separate running index per array as you iterate `name[]`, incrementing it only when you actually consume a value from that specific array.

!!! warning "Front-wheel steering sign convention - ROS says positive = left, the STM32 firmware says positive = right"
    ROS's `left_joint`/`right_joint` (`axis="0 0 1"`) follow REP-103: a positive joint angle is a counter-clockwise rotation about +Z, i.e. **steering left**. `mdp_stm32`'s `servo_set_angle()` (`src/servo.c`) is documented the other way — **positive `angle_deg` = right**. Neither convention is "wrong" in isolation (ROS's matches the rest of the ROS graph; the firmware's presumably matches how the physical servo horn/linkage was tested), but `mdp_hardware_bridge` has to reconcile them: `serial_bridge_node.cpp` negates the angle in both directions (`onJointCommand()` sending `steer_rad` to the MCU, and the telemetry parser converting `pkt.steer_deg` back into `/joint_states_raw`). If you ever touch that conversion, keep it negated both ways, or steering will visibly point the wrong direction from what was commanded.

!!! bug "`mdp_control` topic-name mismatches found during a topic-naming audit"
    Two nodes were publishing/subscribing to topics nobody else used, both silent (no error, no crash, the callback/subscriber just never fired):

    - `task2_runner.py` subscribed to `/arrow_detection`, but `yolo_detector.py` (the only publisher of YOLO results) publishes on `/yolo_result` - the same topic `task1_runner.py` already used correctly. `arrow_callback` never fired. Fixed by pointing the subscription at `/yolo_result`.
    - `task1_runner.py` published every command twice: once to `/cmd_vel` (correct - matches the remap both `real.launch.py` and `sim.launch.py` apply to `ackermann_steering_controller`'s reference subscription) and once to `/ackermann_steering_controller/reference` directly, which nothing subscribes to post-remap - dead traffic. `pure_pursuit_follower.py` had the same leftover pattern gone one step further: it called `self.ref_pub.publish(msg)` in `publish_cmd()` without `self.ref_pub` ever being created in `__init__` - a guaranteed `AttributeError` the first time `publish_cmd()`/`stop()` ran. Fixed by removing the dead second publish in both files; `/cmd_vel` alone is correct and sufficient.

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

Installed as `ros-jazzy-robot-localization` (`pixi.toml`, `robostack-jazzy` channel). Configured in `mdp_bringup/config/ekf.yaml`, launched as `ekf_filter_node` in `real.launch.py`.

#### How an EKF fuses odometry, conceptually

`ekf_node` is a generic multi-sensor Extended Kalman Filter — it doesn't know or care that one input is "wheel encoders" and another is "IMU". It just keeps a running estimate of a 15-element state vector (`x, y, z, roll, pitch, yaw, vx, vy, vz, vroll, vpitch, vyaw, ax, ay, az`) and repeats two steps:

1. **Predict** — at `frequency` Hz (30Hz here), advance the state estimate using its own motion model (e.g. "if vx was 0.5 m/s and yaw was 10°, where is the robot 33ms later") and grow the uncertainty (covariance) a little, since dead-reckoning alone drifts.
2. **Correct** — whenever a subscribed topic delivers a new message, compare what that sensor reports against the predicted state and nudge the estimate toward it, weighted by that sensor's own reported covariance (confident sensor = bigger nudge) and the filter's current uncertainty (Kalman gain).

The key design choice per sensor is `<sensor>_config`: a 15-element boolean mask picking *which* of those 15 state variables that sensor is allowed to correct. This is what lets you say "trust the encoders for speed but not heading" and "trust the IMU for heading but not position" — both feed the *same* state vector, so the filter reconciles them automatically rather than you having to average anything by hand.

#### This robot's specific fusion

Only two of the fifteen state variables actually get outside correction here — `vx` (from wheel odometry) and `yaw`/`vyaw` (from the IMU). `x`/`y` position is **not** corrected by anything; it's purely the EKF's own integral of `vx` and `yaw` over time. That's the entire point: it replaces `ackermann_steering_controller`'s own dead-reckoned position estimate (which inherits error from the single-servo Ackermann approximation — see the warning above on `/joint_commands`) with one integrated from a heading source the STM32-side IMU derives independently of the drivetrain.

| Input | Topic | Fused fields | Why only these |
| --- | --- | --- | --- |
| Wheel odometry | `/ackermann_steering_controller/odometry` | `vx` only | `x/y/yaw` from this source already bakes in the single-servo Ackermann approximation - not double-counted here. |
| IMU | `/imu/data` | `yaw`, `vyaw` | Both come from the STM32's gyro-Z (see `mdp_stm32/src/imu.c`) - `yaw` is bias-corrected gyro integration, `vyaw` is the instantaneous bias-corrected rate. Roll/pitch aren't fused: the bridge sets their covariance to `1e6` (`serial_bridge_node.cpp`) since they're never estimated, and `two_d_mode: true` discards them from the state entirely regardless. |

Other notable settings in `ekf.yaml`:

- **`two_d_mode: true`** — locks `z, roll, pitch, vz, vroll, vpitch, az` to zero. This is a ground vehicle on a flat competition arena floor; letting those drift on noise would only hurt.
- **`world_frame: odom`, single `ekf_node` instance** — `robot_localization`'s usual two-instance pattern (one EKF in `odom` frame fused only with continuous sensors like wheels/IMU, a second in `map` frame additionally fused with an absolute source like GPS/AMCL) collapses to just the first instance here, since there's no absolute localization source yet — Task 1/2 navigation is dead-reckoning + vision, not GPS.
- **`publish_tf: true`** — `ekf_node` becomes the sole broadcaster of `odom` → `base_link`. `real_controller.yaml` sets `enable_odom_tf: false` on `ackermann_steering_controller` for exactly this reason — it still publishes `/ackermann_steering_controller/odometry` as the EKF's input, it just no longer broadcasts the TF itself (two nodes broadcasting the same transform is a conflict, not additive).
- **`sensor_timeout: 0.5`** — matches the 500ms fail-safe window already used by the STM32 command timeout and the bridge's `/hardware_bridge/link_ok` watchdog, so the whole stack degrades on a consistent timescale if the serial link drops.

Fused pose is published on `/odometry/filtered` and consumed by autonomy nodes in place of raw wheel odometry.

#### Verification Checklist (post-bringup)

**No driving needed (wheels can be off the ground):**

1. `pixi run real serial_port:=/dev/ttyACM0` (adjust device as needed) and confirm no node exits/crashes on startup.
2. `ros2 topic hz /imu/data` and `ros2 topic hz /ackermann_steering_controller/odometry` — should read ~10Hz and ~50Hz respectively (matching the MCU telemetry rate and `controller_manager`'s `update_rate`). If either is 0, the EKF has nothing to fuse — go fix that input first, not the EKF config.
3. `ros2 topic echo /odometry/filtered --once` — check `pose.pose.position`/`orientation` are real numbers, not `nan`. A `nan` here usually means `frequency`/`sensor_timeout` never saw a first message from one of the inputs.
4. **TF ownership** — confirm `ekf_node` is the only `odom`→`base_link` broadcaster: `ros2 topic echo /tf` and check the source, or watch the terminal for `TF_REPEATED_DATA`/multiple-authority warnings, which would mean `real_controller.yaml`'s `enable_odom_tf: false` didn't take (e.g. stale `install/` from before the config change — rebuild and re-source).
5. **IMU branch in isolation** — with wheels stationary (0 encoder ticks), rotate the whole chassis by hand. `/odometry/filtered`'s yaw should track the rotation via the IMU while `vx` stays ~0. If yaw doesn't move, the IMU input isn't reaching the filter (check `/imu/data` is actually populated, not zeros — `imu_ready` must be 1).
6. **Wheel branch in isolation** — chassis stationary/level, spin one rear wheel by hand. `/odometry/filtered`'s `twist.linear.x` should respond without a spurious yaw jump (yaw only comes from the IMU input, per the fusion table above).
7. `ros2 run rviz2 rviz2`, add a TF display (or Odometry display on `/odometry/filtered`) — visually confirm `odom`→`base_link` moves smoothly, without snapping or jitter, as you manually rotate/roll the chassis.

**Needs actual driving:**

8. Drive a short known path with `pixi run teleop` (e.g. forward 1m, turn 90°, forward 1m) and compare `/odometry/filtered`'s reported displacement against a tape-measure ground truth. Some drift is expected (no absolute correction source yet — see `world_frame` note above); the goal is confirming the fused estimate is *closer* to ground truth than raw `/ackermann_steering_controller/odometry` alone, not perfect.

## Not Yet Verified On Hardware

- EKF fusion (`ekf.yaml`) — config-validated only (`ekf_node` runs cleanly against the params file, launch file parses); not yet run against the live serial bridge + physical IMU/encoders per the checklist above.

## Known Issue: `/odometry/filtered`'s `y` and `yaw` covariance grow unbounded

First real-hardware run of the EKF (stationary robot, ~5 min) showed `pose.covariance`'s `x` term behaving reasonably (~34 → ~52, slow growth) but `y` and `yaw` diverging continuously and never settling:

| | t+0 | t+21s |
| --- | --- | --- |
| `x` covariance | 34.4 | 51.7 |
| `y` covariance | 7.7×10¹⁰ | 5.9×10¹¹ |
| `yaw` covariance | 2.2×10⁶ | 7.4×10⁶ |

**Ruled out:**

- Not a startup-transient / "hasn't converged yet" thing — covariance keeps climbing indefinitely across multiple snapshots minutes apart, never plateaus.
- Not `ekf_node` falling behind (`Failed to meet update rate!`) — that run showed zero such warnings from `ekf_node`.
- Not a serial/IMU dropout — `serial_bridge_node`'s diagnostic showed a steady `~3600 bytes, 25-26 OK frames, 0 bad frames` every 3s the entire run; `/imu/data` was flowing continuously.

**Working theory:** `imu0_config`'s `yaw: true` isn't actually correcting the filter's yaw estimate — if it were, yaw's own covariance should plateau near the IMU's reported yaw covariance (`0.05`, set in `serial_bridge_node.cpp`'s `onTelemetry()`), not grow unboundedly. Unfused/uncorrected yaw uncertainty compounding into `y` position uncertainty through the motion model would explain why `y` is ~5 orders of magnitude worse than `x` (which *is* corrected every cycle via `vx` from wheel odometry).

**Next diagnostic step (not yet done):** `ros2 topic echo /diagnostics --once` while `pixi run real` is running - `ekf_node` (`print_diagnostics: true` in `ekf.yaml`) publishes a per-input health entry (e.g. `ekf_filter_node: imu0 (/imu/data)`) that should say directly whether `imu0` is being read/accepted or timing out/rejected, rather than inferring it from covariance numbers alone.
