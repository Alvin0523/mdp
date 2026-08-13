---
icon: lucide/bot
---

# ROS Workspace (`mdp_ros`)

Documentation for the `mdp_ros` submodule containing ROS2 Jazzy packages, URDF robot description, launch files, and Gazebo simulation integration.

## Quick Links

- [Launch & Nodes](launch.md) — Launch files, node graph, and topic definitions.
- [Simulation](simulation.md) — Gazebo simulation setup and `ros2_control` configuration.

## Environment & Build

The workspace runs under the root `pixi` environment with ROS2 Jazzy packages.

```bash
# Source environment and build workspace
pixi run build-ros
```

## System Architecture Integration

The host ROS2 workspace handles high-level autonomy, visualization, and kinematics computation via `ackermann_steering_controller`. It communicates with the STM32 MCU over `topic_based_ros2_control` via the `micro-ROS Agent`.
