# 🏎️ MDP: Autonomous Mini Ackermann Robot Platform

[![Docs](https://img.shields.io/badge/Docs-Zensical-blue?logo=readthedocs)](https://alvin0523.github.io/mdp/)
[![License](https://img.shields.io/badge/License-Apache%202.0-green.svg)](LICENSE)
[![Platform](https://img.shields.io/badge/Platform-Linux%20%7C%20macOS%20%7C%20Linux%20ARM-blue)](https://pixi.sh)
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
| [`mdp_ros/`](mdp_ros) | ROS 2 Jazzy autonomy suite: `ackermann_steering_controller`, `mdp_bridge` (STM32 serial + Android Bluetooth bridge), `mdp_algorithm` (Reeds-Shepp/Dubins TSP planning, spline planning, Pure Pursuit), `mdp_vision` (YOLO26 detection), Gazebo Sim & Foxglove bridge | [Alvin0523/mdp_ros](https://github.com/Alvin0523/mdp_ros) |
| [`mdp_stm32/`](mdp_stm32) | STM32 bare-metal (STM32Cube HAL via PlatformIO) firmware: AT8236 motor PWM, HWZ020 servo steering, Hall encoder odometry, ICM-20948 IMU, and a custom binary serial protocol over `USART3` | [Alvin0523/mdp_stm32](https://github.com/Alvin0523/mdp_stm32) |
| `docs/` | Comprehensive technical documentation site managed via [Zensical](https://zensical.org) | Built with Zensical |

---

## 🧰 Tech Stack

| Layer | Technologies |
|---|---|
| **Host (`mdp_ros`)** | ROS 2 Jazzy, `ros2_control` / `ackermann_steering_controller`, `topic_based_ros2_control`, `robot_localization` (EKF), Gazebo Sim (`gz_ros2_control`), Foxglove Bridge |
| **Firmware (`mdp_stm32`)** | STM32F407VET6, bare-metal STM32Cube HAL via PlatformIO, custom binary serial protocol |
| **Vision & Planning** | Ultralytics YOLO26 (`mdp_yolo`), Reeds-Shepp/Dubins TSP solver, spline planning, Pure Pursuit (`mdp_algorithm`) |
| **Tooling** | [Pixi](https://pixi.sh) (`robostack-jazzy`), [Zensical](https://zensical.org) docs site |

See [System Architecture](https://alvin0523.github.io/mdp/) for the full breakdown.

---

## 📁 Repository Structure

```
mdp/
├── docs/                    # Zensical documentation site & guides
│   ├── index.md             # Site overview & quick links
│   ├── quickstart.md        # Commands-only runbook: clone, build, launch, drive (sim & real)
│   ├── assessment_checklist.md  # Official course deliverables & Task 1/2 requirements
│   ├── hardware.md          # Electronics, pinout & mechanical specs
│   ├── dev_workflow.md      # Development, compilation & debugging workflows
│   ├── troubleshooting.md   # Common errors and hardware troubleshooting
│   ├── references.md        # External docs links & WHEELTEC vendor file map
│   ├── android/             # Android app interface contract
│   ├── rpi/                 # RPi: ROS 2 launch/topics, ros2_control, EKF, Vision, Algorithm
│   └── stm32/               # STM32: overview, firmware architecture, tuning, serial protocol
├── mdp_ros/                 # ROS 2 Jazzy Autonomy, Vision & Simulation Stack (Git Submodule)
├── mdp_stm32/               # STM32 Microcontroller Firmware Stack (Git Submodule)
├── pixi.toml                # Top-level Pixi task runner & dependency environment
└── zensical.toml            # Zensical documentation site configuration
```

---

## 📚 Technical Documentation

Full setup, build, and run instructions live on the [documentation site](https://alvin0523.github.io/mdp/) —
start at [Quickstart](https://alvin0523.github.io/mdp/quickstart/). To preview the docs site locally:

```bash
pixi install
pixi run serve   # live-reloading preview at http://127.0.0.1:8000
```

---

## 👥 Authors

**Group 14**

- Cheong Hoi Chun
- Lin Lihong Albert
- Wong Wei Ming
- Keagan Kong Kai Yi (Kaiyi)
- Ojha Rashi
- Wang Li Ling, Katie
- Seah Song Li

---

## 📄 License

This project is licensed under the **Apache-2.0 License**. See [LICENSE](LICENSE) for details.
