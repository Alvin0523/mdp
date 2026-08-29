---
icon: lucide/bot
---

# ROS2 Workspace (`mdp_ros`)

Documentation for the `mdp_ros` workspace containing ROS2 Jazzy packages, URDF robot description, launch files, simulation, and real hardware interfaces.

## Sub-Documentation Pages

- [**Launch Files & Node Interfaces**](launch.md) — Launch entry points (`sim.launch.py`, `real.launch.py`), controller parameters, and topic specifications.
- [**Gazebo Simulation & Controllers**](simulation.md) — Gazebo Sim integration, `gz_ros2_control`, joint physics limits, REP-103 joint axes, and Foxglove visualization.

## Setup

```bash
cd mdp_ros
pixi install       # ROS2 Jazzy (robostack-jazzy channel) + all Python/C++ deps, managed via pixi.toml
```

For OpenSpec CLI setup (`openspec init`) inside this submodule, see [Dev Workflow & OpenSpec Setup](../dev_workflow.md).

---

## Workspace Structure

```text
mdp_ros/
├── pixi.toml                             # Pixi task & dependency definitions (robostack-jazzy)
├── openspec/                             # OpenSpec change proposals & specs
└── src/
    ├── mdp_description/                  # URDF models, 3D meshes, and Gazebo world models
    │   ├── meshes/mini_akm_robot_meshes/ # STL 3D model meshes
    │   ├── urdf/                         # URDF robot models (Sim & Real Hardware)
    │   └── worlds/task2_arena.sdf        # Gazebo Task 2 Arena world model
    ├── mdp_control/                      # Autonomy & Path Planning Python Nodes
    │   └── mdp_control/
    │       ├── reeds_shepp_planner.py   # Reeds-Shepp Ackermann curve & TSP solver
    │       ├── pure_pursuit_follower.py # Waypoint tracking controller
    │       ├── task1_runner.py          # Task 1 exploration state machine
    │       └── task2_runner.py          # Task 2 fastest path decision tree
    ├── vision/mdp_vision/                 # Vision stack (moved out from under mdp_control)
    │   └── mdp_vision/
    │       ├── camera_publisher.py      # Standalone webcam publisher (pixi run vision, dev-only)
    │       └── yolo_detector.py         # Ultralytics YOLO detector
    ├── mdp_hardware_bridge/               # STM32 <-> ROS2 serial bridge (C++)
    │   ├── include/mdp_hardware_bridge/protocol.hpp  # Mirrors mdp_stm32/include/protocol.h
    │   └── src/serial_bridge_node.cpp     # /joint_commands <-> USART3 <-> /joint_states_raw, /imu/data, /estop, /battery_state
    └── mdp_bringup/                      # System launch scripts & controller YAMLs
        ├── config/                       # Ackermann controller YAMLs, ekf.yaml
        └── launch/                       # sim.launch.py, task2_sim.launch.py, real.launch.py
```

---

## Pixi Task Shortcuts

| Task Command | Execution Command | Description |
| --- | --- | --- |
| `pixi run sim` | `ros2 launch mdp_bringup sim.launch.py` | Launches Gazebo Sim, spawns robot URDF, starts Ackermann controllers. |
| `pixi run sim-task2` | `ros2 launch mdp_bringup task2_sim.launch.py` | Launches Gazebo Task 2 Arena world (`task2_arena.sdf`), YOLO detector & Task 2 runner. |
| `pixi run real` | `ros2 launch mdp_bringup real.launch.py` | Launches host-side `topic_based_ros2_control` bringup for real robot, including the `mdp_hardware_bridge` serial bridge node and `robot_localization` EKF. |
| `pixi run task1` | `ros2 run mdp_control task1_runner.py` | Runs Task 1 Exploration state machine, TSP Ackermann planner, YOLO callback & BT relay. Run alongside `pixi run real` or `pixi run sim`. |
| `pixi run task2` | `ros2 run mdp_control task2_runner.py` | Runs Task 2 Fastest Path decision tree node (YOLO arrow sweep & return). Run alongside `pixi run real` or `pixi run sim`. |
| `pixi run teleop` | `ros2 run teleop_twist_keyboard teleop_twist_keyboard --ros-args -p stamped:=true` | Drives the car via keyboard with active timestamped messages. |
| `pixi run vision` | `ros2 launch mdp_vision vision.launch.py` | Standalone webcam + YOLO test path (dev machine, no RPi camera needed) - separate from `pixi run real`'s `camera_ros` path. |
| `pixi run foxglove` | `ros2 launch foxglove_bridge foxglove_bridge_launch.xml` | Launches Foxglove WebSocket visualization bridge on port `8765`. |
| `pixi run bag` | `ros2 bag record -a -o bags/rosbag2_<timestamp>` | Records every topic into a timestamped directory under `bags/`. |
| `pixi run build` | `colcon build --symlink-install` | Compiles all workspace packages. |

!!! note "micro-ROS Agent tasks removed"
    `agent`/`agent-build` pixi tasks existed for the originally-planned micro-ROS transport ([ADR 0002](../architecture.md#0002-custom-binary-serial-protocol-vs-micro-ros-rclc)) and have since been removed from `pixi.toml` now that `mdp_hardware_bridge` is the only host↔MCU transport. The standalone-agent build instructions below are kept as historical reference only - the commands shown won't run as `pixi run ...` anymore, see [Troubleshooting](../troubleshooting.md) for the full story if ever revisiting micro-ROS.

---

## `mdp_hardware_bridge` (host side)

The actual STM32↔host transport ([ADR 0002](../architecture.md#0002-custom-binary-serial-protocol-vs-micro-ros-rclc)). `serial_bridge_node` opens `/dev/ttyUSB0` @ 115200 baud, subscribes `/joint_commands` (from `topic_based_ros2_control`) and sends it to the MCU as a `CommandPacket`, and parses `TelemetryPacket` frames from the MCU into `/joint_states_raw`, `/imu/data`, `/estop`, `/battery_state`, and `/hardware_bridge/link_ok`. See [Serial Protocol](../stm32/protocol.md) for the packet format and [Launch Files](launch.md#sensor-fusion-architecture-robot_localization) for why the joint-state topic isn't named `/joint_states` directly.

```bash
ros2 run mdp_hardware_bridge serial_bridge_node --ros-args -p serial_port:=/dev/ttyUSB0
```

Launched automatically as part of `pixi run real` (`real.launch.py`).

## micro-ROS Agent (superseded, host side)

!!! note "Superseded by `mdp_hardware_bridge`"
    This was the originally planned transport ([ADR 0002](../architecture.md#0002-custom-binary-serial-protocol-vs-micro-ros-rclc) reversed this decision). Kept here since the build work (and the `fmt`/`spdlog` fix below) is still real and could be revisited if the project moves back to micro-ROS.

The Agent runs standalone (`Micro-XRCE-DDS-Agent`, `v3.0.1`, built directly from [eProsima's repo](https://github.com/eProsima/Micro-XRCE-DDS-Agent) — cloned as a sibling of `mdp_ros`/`mdp_stm32` in `mdp/`), **not** as the ROS2 `micro_ros_agent` colcon package. That package's CMake SuperBuild pins an old (`v2.4.3`, ~2022) Micro-XRCE-DDS-Agent that fails to compile against this workspace's `fmt 12.x` (spdlog/fmt API changes since). See [Troubleshooting](../troubleshooting.md) for the full story.

The standalone binary still bridges cleanly into the ROS2 graph — its default middleware is Fast DDS (ROS2's own default), so topics published from the MCU show up via normal `ros2 topic echo` etc. without needing the colcon wrapper's ROS2-message XML generation.

```bash
cd mdp_ros
pixi run agent-build   # one-time
pixi run agent           # starts the serial agent on /dev/ttyUSB0 @ 115200
```

## Implementation Status

- [x] `mdp_description` + `mdp_bringup` — URDF models, meshes, launch files, and controller YAMLs
- [x] Gazebo Simulation — `mini_akm_robot.urdf` with `gz_ros2_control`
- [x] Real Hardware Integration — `mini_akm_real_robot.urdf` with `topic_based_ros2_control`
- [x] Autonomy & Planning Nodes — Reeds-Shepp planner, Dubins pure pursuit follower, YOLO arrow detector, Task 1 & 2 state machines (see [Algorithm](../algorithm/index.md), [Vision](../vision/index.md))
- [x] `robot_localization` EKF Node — `ekf.yaml` + `real.launch.py` wiring fusing `/ackermann_steering_controller/odometry` (v_x) + `/imu/data` (yaw, ω_z) into `/odometry/filtered`; not yet driven on physical hardware, only config-validated (see [Launch Files](launch.md#sensor-fusion-architecture-robot_localization))
- [x] `ekf.yaml` `imu0_config` switched to fuse only `angular_velocity` (yaw rate), not `orientation` — previously fused both, double-counting the MCU's own uncorrected gyro-integrated yaw as if it were an independent measurement; not yet re-validated on physical hardware after the change. See [Closed-Loop Control & Sensor Fusion](../stm32/control_loop.md#3-imu-ros2_control-robot_localization-proper-data-flow)
- [x] `ekf.yaml` `frequency` raised 30Hz → 100Hz to match `mdp_stm32`'s telemetry rate — not yet re-validated on hardware
