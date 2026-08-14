---
icon: lucide/bot
---

# ROS2 Workspace (`mdp_ros`)

Documentation for the `mdp_ros` workspace containing ROS2 Jazzy packages, URDF robot description, launch files, simulation, and real hardware interfaces.

## Sub-Documentation Pages

- [**Launch Files & Node Interfaces**](launch.md) — Launch entry points (`sim.launch.py`, `real.launch.py`), controller parameters, and topic specifications.
- [**Gazebo Simulation & Controllers**](simulation.md) — Gazebo Sim integration, `gz_ros2_control`, joint physics limits, REP-103 joint axes, and Foxglove visualization.

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
    ├── mdp_control/                      # Autonomy, Vision, and Path Planning Python Nodes
    │   └── scripts/
    │       ├── yolo_arrow_detector.py   # Ultralytics YOLO26 arrow detection node
    │       ├── reeds_shepp_planner.py   # Reeds-Shepp Ackermann curve & TSP solver
    │       ├── pure_pursuit_follower.py # Waypoint tracking controller
    │       ├── task1_runner.py          # Task 1 exploration state machine
    │       └── task2_runner.py          # Task 2 fastest path decision tree
    └── mdp_bringup/                      # System launch scripts & controller YAMLs
        ├── config/                       # ROS2 Jazzy Ackermann controller YAMLs
        └── launch/                       # sim.launch.py, task2_sim.launch.py, real.launch.py
```

---

## Pixi Task Shortcuts

| Task Command | Execution Command | Description |
| --- | --- | --- |
| `pixi run sim` | `ros2 launch mdp_bringup sim.launch.py` | Launches Gazebo Sim, spawns robot URDF, starts Ackermann controllers. |
| `pixi run sim-task2` | `ros2 launch mdp_bringup task2_sim.launch.py` | Launches Gazebo Task 2 Arena world (`task2_arena.sdf`), YOLO detector & Task 2 runner. |
| `pixi run hardware` | `ros2 launch mdp_bringup real.launch.py` | Launches host-side `topic_based_ros2_control` bringup for real robot. |
| `pixi run agent-build` | `bash scripts/build_microxrce_agent.sh` | One-time (idempotent): clones + builds the standalone eProsima Micro-XRCE-DDS-Agent `v3.0.1` into `../Micro-XRCE-DDS-Agent`. See below for why this isn't the ROS2 `micro_ros_agent` colcon package. |
| `pixi run agent` | `../Micro-XRCE-DDS-Agent/build/MicroXRCEAgent serial --dev /dev/ttyUSB0 -b 115200` | Launches the micro-ROS Agent daemon connecting host ROS2 to the STM32 MCU over serial. Run `pixi run agent-build` first. |
| `pixi run task1` | `ros2 run mdp_bringup task1_runner.py` | Runs Task 1 Exploration state machine, TSP Ackermann planner, YOLO26 callback & BT relay. |
| `pixi run task2` | `ros2 run mdp_bringup task2_runner.py` | Runs Task 2 Fastest Path decision tree node (YOLO26 arrow sweep & return). |
| `pixi run teleop` | `ros2 run teleop_twist_keyboard teleop_twist_keyboard --ros-args -p stamped:=true` | Drives the car via keyboard with active timestamped messages. |
| `pixi run foxglove` | `ros2 launch foxglove_bridge foxglove_bridge_launch.xml use_sim_time:=true` | Launches Foxglove WebSocket visualization bridge on port `8765`. |
| `pixi run build` | `colcon build --symlink-install` | Compiles workspace packages (`mdp_description`, `mdp_bringup`). |

---

## micro-ROS Agent (host side)

The Agent runs standalone (`Micro-XRCE-DDS-Agent`, `v3.0.1`, built directly from [eProsima's repo](https://github.com/eProsima/Micro-XRCE-DDS-Agent) — cloned as a sibling of `mdp_ros`/`mdp_stm32` in `mdp/`), **not** as the ROS2 `micro_ros_agent` colcon package. That package's CMake SuperBuild pins an old (`v2.4.3`, ~2022) Micro-XRCE-DDS-Agent that fails to compile against this workspace's `fmt 12.x` (spdlog/fmt API changes since). See [Troubleshooting](../troubleshooting.md) for the full story.

The standalone binary still bridges cleanly into the ROS2 graph — its default middleware is Fast DDS (ROS2's own default), so topics published from the MCU show up via normal `ros2 topic echo` etc. without needing the colcon wrapper's ROS2-message XML generation.

```bash
cd mdp_ros
pixi run agent-build   # one-time
pixi run agent           # starts the serial agent on /dev/ttyUSB0 @ 115200
```

`-b 115200` matches `mdp_stm32`'s `USART3` config (`app/boards/stm32f4_disco.overlay`) — don't change one without the other.
