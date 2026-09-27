---
icon: lucide/network
---

# Launch & Topics

One launch file, `mdp_bringup/launch/mdp.launch.py`, starts everything for sim and real.
`pixi run sim` = `sim:=true`, `pixi run real` = `sim:=false`; the other arguments are in
[Quickstart → Launch arguments](../quickstart.md#launch-arguments).

## What gets started

| Node | Sim | Real | What it does |
| --- | --- | --- | --- |
| `gz sim` (server + window) | ✓ | | Gazebo with the arena; the blocks are baked in from `tasks.yaml` |
| `create` · `parameter_bridge` | ✓ | | Spawn the car; bridge `/clock`, camera, IMU and `/sim/ground_truth` from Gazebo |
| `ros2_control_node` | (inside Gazebo) | ✓ | The controller manager |
| `serial_bridge_node` | | ✓ | STM32 over USART3: wheel/steer commands out; encoders, IMU, battery, sensors in |
| `robot_state_publisher` | ✓ | ✓ | The URDF's fixed transforms |
| `spawner` | ✓ | ✓ | Starts `joint_state_broadcaster` + `ackermann_steering_controller` |
| `ekf_filter_node` | ✓ | ✓ | Pose estimate, `odom → base_footprint` |
| `map_to_odom_static_tf` | ✓ | ✓ | `map → odom` = the start pose |
| `bluetooth_bridge_node` | ✓ | ✓ | The tablet link (always on) |
| `robot_pose_feedback` | ✓ | ✓ | `ROBOT,x,y,d` to the tablet; `/reset_pose` |
| `bt_monitor` | ✓ | ✓ | Tablet traffic as log lines (`pixi run btlog`) |
| `health_monitor` | ✓ | ✓ | `/diagnostics`: links, rates, runner |
| `rpi_cam_publisher` | | `vision:=true` | The Pi camera |
| `yolo_detector` | `vision:=true` | `vision:=true` | YOLO on the camera image |
| `manual_drive` | `task:=0` | `task:=0` | Tablet arrow buttons |
| `task1_runner` | `task:=1` | `task:=1` | Task 1 |
| `task2_runner` | `task:=2` | `task:=2` | Task 2 |
| `publish_obstacles` | `task:=1` (default `obstacles:=yaml`) | `task:=1 obstacles:=yaml` | Sends the `tasks.yaml` layout once |
| `sim_obstacles` | `task:=1` | | Replaces Gazebo's blocks when the tablet sends a different layout |

Camera and YOLO come from `launch/vision.launch.py`, included (also runs alone: `pixi run vision`).

## Topics

| Topic | Type | From → to |
| --- | --- | --- |
| `/cmd_vel` | `TwistStamped` | runner / manual_drive / calib → controller |
| `/joint_commands` · `/joint_states_raw` | `JointState` | controller ⇄ serial_bridge (real) |
| `/joint_states` | `JointState` | joint_state_broadcaster |
| `/ackermann_steering_controller/odometry` | `Odometry` | controller → EKF (speed) |
| `/imu/data` | `Imu` | serial_bridge / Gazebo → EKF (turn rate) |
| `/odometry/filtered` | `Odometry` | EKF → runners, pose feedback |
| `/set_pose` | `PoseWithCovarianceStamped` | robot_pose_feedback → EKF (reset) |
| `/obstacle_setup` | `String` `id:x,y,F\|…` (metres) | bluetooth bridge / publish_obstacles → task1_runner, sim_obstacles |
| `/manual_drive` | `String` `f b fl fr bl br` | bluetooth bridge → manual_drive / task1_runner |
| `/bluetooth_rx` · `/bluetooth_tx` | `String` | tablet lines in / out |
| `/bluetooth_bridge/link_ok` · `/hardware_bridge/link_ok` | `Bool` | tablet link · STM32 link |
| `/estop` | `Bool` | motor switch (`false` = motors on) |
| `/battery_state` | `BatteryState` | STM32 |
| `/ultrasonic` · `/ir` · `/ir2` | `Range` | STM32 |
| `/image_raw` (real) · `/camera/image_raw` (sim) | `Image` | camera → YOLO |
| `/yolo_result` | `String` (symbol id) | YOLO → runners |
| `/yolo_result/image_annotated` | `Image` | YOLO → Foxglove |
| `/run_status` | `mdp_interfaces/RunStatus` | task1_runner, 2 Hz: state, target, distance, gear, scan |
| `/diagnostics` | `DiagnosticArray` | health_monitor, EKF |
| `/occupancy_grid` · `/grid_markers` · `/obstacle_markers` · `/checkpoint_markers` · `/path_markers` · `/search_progress` · `/planned_path` | drawings | task1_runner → Foxglove (frame `map`) |
| `/sim/ground_truth` | `TFMessage` | Gazebo's true pose (sim only), for checking the estimate |
| `/rosout` | `Log` | every node's log (runner events, `bt_monitor`'s tablet traffic) |

## Services

| Service | Called by | Does |
| --- | --- | --- |
| `/start_run` | tablet BEGIN, `pixi run go` | Start the run |
| `/stop_run` | tablet STOP, `pixi run stop` | Stop, hold zero speed |
| `/reset_pose` | tablet RESET, `pixi run reset` | Pose back to the start pose (stops a run first) |

## Log output

`log:=quiet` (default) shows the runners' and bridges' lines plus everyone's warnings; `log:=full`
shows everything. Every node's full log is also saved under `~/.ros/log/`.
