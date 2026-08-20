---
icon: lucide/book-open
---

# References

## External Documentation

Hover a name for a one-line description; click to open its documentation.

| Tool | Used for |
| --- | --- |
| [OpenSpec][ext-openspec] | Spec-driven workflow in `mdp_ros/openspec/`, `mdp_stm32/openspec/` |
| [ROS2 Jazzy][ext-ros2] | Host-side ROS2 distro |
| [ros2_control / ros2_controllers][ext-ros2control] | `ackermann_steering_controller` + hardware_interface framework, see [Architecture](architecture.md) |
| [Gazebo][ext-gazebo] | Simulation, via `gz_ros2_control` and `ros_gz` |
| [micro-ROS][ext-microros] | Transport linking host to the STM32 MCU |
| [PlatformIO][ext-platformio] | Build/flash toolchain for `mdp_stm32` (STM32Cube HAL) |
| [pixi][ext-pixi] | Environment/task manager used across this repo |
| [Zensical][ext-zensical] | This docs site generator |
| [Ultralytics ROS Quickstart][ext-yolo-ros] | YOLO ROS/ROS2 integration guide for object detection nodes |
| [Ultralytics Raspberry Pi Guide][ext-yolo-rpi] | Deploying & optimizing YOLO models on Raspberry Pi / embedded hardware |

  [ext-openspec]: https://github.com/Fission-AI/OpenSpec "Spec-driven change workflow — propose/apply/archive"
  [ext-ros2]: https://docs.ros.org/en/jazzy/ "ROS2 Jazzy Jalisco distro docs"
  [ext-ros2control]: https://control.ros.org/ "Controller framework + hardware_interface plugins"
  [ext-gazebo]: https://gazebosim.org/docs "Gazebo simulator docs"
  [ext-microros]: https://micro.ros.org/ "ROS2 client library for microcontrollers"
  [ext-platformio]: https://docs.platformio.org/ "PlatformIO build/flash toolchain docs"
  [ext-pixi]: https://pixi.sh/ "Conda-based environment/task manager"
  [ext-zensical]: https://zensical.org/docs/ "This site's static-site generator"
  [ext-yolo-ros]: https://docs.ultralytics.com/guides/ros-quickstart "Ultralytics YOLO ROS/ROS2 Integration Guide"
  [ext-yolo-rpi]: https://docs.ultralytics.com/guides/raspberry-pi "Ultralytics YOLO Raspberry Pi Deployment Guide"

---

## MDP Course Assessment Documents

- 📄 [**MDP Assessment and System Checklist (AY26/27 Sem 1)**](attachments/MDP%20assessment%20and%20system%20checklist.pdf) — Official course checklist defining functional specifications for Mobile Robot (Module A), Path Planning (Module B), and Android Remote Controller (Module C).

---

## Local `references/` Directory Guide

The `references/` directory contains vendor manuals, datasheets, and legacy prototypes. It is read-only context used when developing `mdp_ros` and `mdp_stm32`.

### WHEELTEC Vendor Reference Hierarchy (`references/WHEELTEC/`)

Below is the directory map of the WHEELTEC vendor materials, matched against the **MDP Course Component List (Sem 1 AY26/27)**.

```text
references/WHEELTEC/
├── 1.WHEELTEC ROS机器人通用资料/                                 ⚠️ PARTIAL REFERENCE (Theory & ROS2 Guides)
│   ├── 2.运动底盘的控制与运动学解析/
│   │   ├── 8.运动学解析：阿克曼底盘/                       ⭐ USE (Ackermann steering math & formulas)
│   │   └── 轮式移动机器人的运动学模型.pdf                    ⭐ USE (Robot motion kinematics theory)
│   ├── 3.STM32底层源码讲解教程/
│   │   ├── 1.电路原理图介绍/                               ✅ C30D schematic & circuit explanations
│   │   └── 6.运动控制与PID/                               ✅ Read for motor PID & PWM concepts
│   └── 6.ROS2系列教程/                                     ⭐ USE (ROS2 Navigation & Camera Tutorials)
│       ├── 1.ROS2入门与编程开发教程/                         ✅ ROS2 Node & Topic programming
│       ├── 2.ROS2机器人应用视频教程：上手使用、雷达与2D导航/    ✅ ROS2 Nav2 setup
│       └── 3.ROS2机器人应用视频教程：相机与图像处理/          ✅ OpenCV & RPi Camera V2 processing
│
├── 2.WHEELTEC R550-V550 ROS教育机器人运动底盘资料/                 ⭐ PRIMARY REFERENCE (100% Hardware Match)
│   ├── C30D随货手册.pdf                                         ⭐ USE (Board specs & max 17V voltage safety)
│   ├── 1.硬件说明与底盘问题排查/
│   │   └── 3.接线说明、常见问题排查与改装介绍/
│   │       └── 1.Mini小车接线说明.pdf                            ⭐ USE (Mini Car physical wiring)
│   ├── 3.STM32运动底盘源码/
│   │   └── 当前版本代码（适配STM32控制板_D版_2022.05.17-现在）/
│   │       └── C30D-2.0版/
│   │           ├── ..._GMR编码器_...zip                         ❌ IGNORE (GMR encoder version)
│   │           └── R550_C30D(2.0)_Mini小车STM32源码_霍尔编码器...zip  ⭐ USE (Exact Hall encoder firmware zip)
│   └── 4.芯片数据手册与原理图/
│       └── 原理图和资源分配表_D版/
│           ├── STM32F407VET6(C30D-V2.1_V2.2)主板资源分配说明.pdf ⭐ CRITICAL (Verified Pinout Map)
│           └── STM32F407VET6控制器原理图(C30D-V2.1).pdf        ⭐ CRITICAL (C30D Board Schematic)
│
├── 3.WHEELTEC R550A 4自由度机械臂资料/                           ❌ IGNORE (4-DOF Arm - Not in course kit)
└── 4.WHEELTEC R550A 6自由度机械臂资料/                           ❌ IGNORE (6-DOF Arm - Not in course kit)
```

---

### Key Vendor Files Quick Reference

| Resource | Exact Relative Path | Purpose & Usage |
| --- | --- | --- |
| **Mini Car Wiring Guide** | `references/WHEELTEC/2.WHEELTEC R550-V550.../1.硬件说明.../3.接线说明.../1.Mini小车接线说明.pdf` | Motor colors, servo connections, power leads |
| **STM32 Pinout Map** | `references/WHEELTEC/2.WHEELTEC R550-V550.../4.芯片数据手册.../原理图.../STM32F407VET6(C30D-V2.1_V2.2)主板资源分配说明.pdf` | Verified MCU pin numbers, timer channels, USART3 pins |
| **C30D Schematic** | `references/WHEELTEC/2.WHEELTEC R550-V550.../4.芯片数据手册.../原理图.../STM32F407VET6控制器原理图(C30D-V2.1).pdf` | Circuit schematic diagram for C30D V2.1 board |
| **Hall Encoder Source Zip** | `references/WHEELTEC/2.WHEELTEC R550-V550.../3.STM32.../C30D-2.0版/R550_C30D(2.0)_Mini小车STM32源码_霍尔编码器_2025.12.26.zip` | C code reference for motor PWM & Hall encoder CPR |
| **Ackermann Kinematics** | `references/WHEELTEC/1.WHEELTEC ROS.../2.运动底盘.../8.运动学解析：阿克曼底盘/` | Kinematic formulas for Ackermann steering geometry |
