# 🏎️ MDP: Autonomous Mini Ackermann Robot Platform

[![Docs](https://img.shields.io/badge/Docs-Zensical-blue?logo=readthedocs)](https://alvin0523.github.io/mdp/)
[![License](https://img.shields.io/badge/License-Apache%202.0-green.svg)](LICENSE)
[![Platform](https://img.shields.io/badge/Platform-Linux%20%7C%20macOS-blue)](https://pixi.sh)
[![Pixi](https://img.shields.io/badge/Pixi-Package%20Manager-brightgreen?logo=conda-forge)](https://pixi.sh)
[![ROS2](https://img.shields.io/badge/ROS2-Jazzy-22314E?logo=ros)](https://docs.ros.org/en/jazzy/)
[![STM32](https://img.shields.io/badge/Embedded-STM32-03234C?logo=stmicroelectronics)](mdp_stm32)
[![Robot](https://img.shields.io/badge/Robot-Mini%20Ackermann-0ea5e9)](mdp_ros)

### [📖 View Full Documentation →](https://alvin0523.github.io/mdp/)

> Top-level workspace for the **Multidisciplinary Project (MDP)** autonomous mini Ackermann steering robot — integrating ROS 2 Jazzy autonomy, STM32 embedded firmware, Ultralytics YOLO vision, Gazebo simulation, and Zensical documentation.

---

## 📖 About

This repository serves as the central meta-workspace for the **Mini Ackermann Robot**, combining the high-level autonomy stack, low-level microcontroller firmware, a custom binary serial bridge, and full technical documentation into a unified [Pixi](https://pixi.sh)-managed development workflow.

### Submodules & Components

| Component / Submodule | Role & Description | Submodule / Repository Link |
|---|---|---|
| [`mdp_ros/`](mdp_ros) | ROS 2 Jazzy autonomy suite: `ackermann_steering_controller`, `mdp_hardware_bridge` (STM32↔host serial bridge), spline path planning, adaptive Pure Pursuit, YOLO vision, Gazebo Sim & Foxglove bridge | [Alvin0523/mdp_ros](https://github.com/Alvin0523/mdp_ros) |
| [`mdp_stm32/`](mdp_stm32) | STM32 bare-metal (STM32Cube HAL via PlatformIO) firmware: AT8236 motor PWM, HWZ020 servo steering, Hall encoder odometry, ICM-20948 IMU, and a custom binary serial protocol over `USART3` | [Alvin0523/mdp_stm32](https://github.com/Alvin0523/mdp_stm32) |
| `docs/` | Comprehensive technical documentation site managed via [Zensical](https://zensical.org) | Built with Zensical |

---

## 🧰 Tech Stack

| Layer | Technologies |
|---|---|
| **Host (`mdp_ros`)** | ROS 2 Jazzy, `ros2_control` / `ackermann_steering_controller`, `topic_based_ros2_control`, `robot_localization` (EKF), Gazebo Sim (`gz_ros2_control`), Foxglove Bridge |
| **Firmware (`mdp_stm32`)** | STM32F407VET6, bare-metal STM32Cube HAL via PlatformIO, custom binary serial protocol |
| **Vision & Planning** | Ultralytics YOLO26, Reeds-Shepp / Pure Pursuit path planning |
| **Tooling** | [Pixi](https://pixi.sh) (`robostack-jazzy`), [Zensical](https://zensical.org) docs site |

See [System Architecture & ADRs](https://alvin0523.github.io/mdp/architecture/) for the full breakdown and the reasoning behind each choice.

---

## 📁 Repository Structure

```
mdp/
├── docs/                    # Zensical documentation site & guides
│   ├── index.md             # Site overview & quick links
│   ├── architecture.md      # End-to-end hardware & software architecture
│   ├── hardware.md          # Electronics, pinout & mechanical specs
│   ├── dev_workflow.md      # Development, compilation & debugging workflows
│   ├── ros/                 # ROS 2 autonomy stack documentation
│   ├── stm32/               # Embedded firmware documentation
│   └── troubleshooting.md   # Common errors and hardware troubleshooting
├── mdp_ros/                 # ROS 2 Jazzy Autonomy, Vision & Simulation Stack (Git Submodule)
├── mdp_stm32/               # STM32 Microcontroller Firmware Stack (Git Submodule)
├── pixi.toml                # Top-level Pixi task runner & dependency environment
└── zensical.toml            # Zensical documentation site configuration
```

---

## ⚡ Quick Start

### 1. Clone Workspace & Submodules

```bash
# Clone repository recursively
git clone --recursive https://github.com/Alvin0523/mdp.git
cd mdp

# If already cloned without submodules, run:
pixi run clone-all
```

### 2. Install Pixi Dependencies

```bash
pixi install
```

### 3. Top-Level Pixi Tasks

| Command | Action |
|---|---|
| `pixi run serve` | Launch local live-reloading preview server for Zensical documentation site |
| `pixi run build` | Build static production assets for Zensical documentation |
| `pixi run clone-all` | Initialize and update all git submodules recursively |
| `pixi run clone-ros` | Initialize and update `mdp_ros` submodule |
| `pixi run clone-stm32` | Initialize and update `mdp_stm32` submodule |

---

## 🛠️ Submodule Quick Workflows

### 🏎️ ROS 2 Autonomy & Simulation (`mdp_ros`)
Navigate to `mdp_ros` for autonomy, path planning, and simulation:
```bash
cd mdp_ros
pixi run build        # Build ROS 2 workspace
pixi run sim-task2    # Run integrated Task 2 Gazebo simulation
```

### ⚡ STM32 Embedded Firmware (`mdp_stm32`)
Navigate to `mdp_stm32` for microcontroller firmware compilation and flashing:
```bash
cd mdp_stm32
pixi run build        # Compile STM32 firmware
```

---

## 📚 Technical Documentation

Documentation is authored in Markdown and built with **Zensical**.

- **Local Preview**: `pixi run serve` (opens documentation server locally at `http://127.0.0.1:8000`)
- **Topics Covered**:
  - [`docs/architecture.md`](docs/architecture.md): System architecture, serial protocol, and architectural decisions.
  - [`docs/hardware.md`](docs/hardware.md): Chassis dimensions, HWZ020 servo yaw limits, motor encoders.
  - [`docs/dev_workflow.md`](docs/dev_workflow.md): Step-by-step development guidelines and test workflows.

---

## 📄 License

This project is licensed under the **Apache-2.0 License**. See [LICENSE](LICENSE) for details.
