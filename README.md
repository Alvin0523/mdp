# 🏎️ MDP: Autonomous Mini Ackermann Robot (Group 14)

**🏆 First approved ROS2 Team in MDP**

[![Docs](https://img.shields.io/badge/Docs-Zensical-blue?logo=readthedocs)](https://alvin0523.github.io/mdp/)
[![Course](https://img.shields.io/badge/NTU%20CCDS-MDP%20AY26%2F27%20S1-c8102e)](https://alvin0523.github.io/mdp/)
[![Platform](https://img.shields.io/badge/Platform-Linux%20%7C%20macOS%20%7C%20Raspberry%20Pi%204-blue?logo=raspberrypi)](https://pixi.sh)
[![Pixi](https://img.shields.io/badge/Pixi-Package%20Manager-brightgreen?logo=conda-forge)](https://pixi.sh)

[![ROS2](https://img.shields.io/badge/ROS2-Jazzy-22314E?logo=ros)](https://docs.ros.org/en/jazzy/)
[![ros2_control](https://img.shields.io/badge/ros2__control-Ackermann%20controller-22314E?logo=ros)](https://control.ros.org/jazzy/)
[![robot_localization](https://img.shields.io/badge/robot__localization-EKF%20(wheels%20%2B%20IMU)-22314E?logo=ros)](https://docs.ros.org/en/jazzy/p/robot_localization/)
[![Cyclone DDS](https://img.shields.io/badge/Middleware-Cyclone%20DDS-22314E)](https://cyclonedds.io/)
[![Gazebo](https://img.shields.io/badge/Sim-Gazebo%20Harmonic-F58113)](https://gazebosim.org/)
[![Foxglove](https://img.shields.io/badge/Viz-Foxglove-6F3BE8)](https://foxglove.dev/)
[![Planning](https://img.shields.io/badge/Planning-Hybrid%20A*%20%C2%B7%20Reeds--Shepp%20%C2%B7%20LQR-informational)](https://alvin0523.github.io/mdp/rpi/algorithm/)

[![YOLO](https://img.shields.io/badge/Vision-YOLO26%20(Ultralytics)-111F68)](https://docs.ultralytics.com/)
[![PyTorch](https://img.shields.io/badge/PyTorch-CUDA%20%7C%20CoreML-EE4C2C?logo=pytorch)](https://pytorch.org/)
[![Python](https://img.shields.io/badge/Python-3-3776AB?logo=python&logoColor=white)](https://www.python.org/)
[![Tailscale](https://img.shields.io/badge/Network-Tailscale-242424?logo=tailscale)](https://tailscale.com/)

[![STM32](https://img.shields.io/badge/Embedded-STM32F407-03234C?logo=stmicroelectronics)](https://github.com/Alvin0523/mdp_stm32)
[![PlatformIO](https://img.shields.io/badge/PlatformIO-STM32Cube%20HAL-F5822A?logo=platformio)](https://platformio.org/)
[![C](https://img.shields.io/badge/Firmware-C-A8B9CC?logo=c)](https://github.com/Alvin0523/mdp_stm32)
[![Android](https://img.shields.io/badge/Tablet-Android%20(Java)-3DDC84?logo=android&logoColor=white)](https://github.com/RashI0192/MDP-APP)

> An Ackermann-steered robot car that finds and reads image cards around an arena (task 1) and races
> round two obstacles and back to its carpark (task 2), with ROS 2 Jazzy on a Raspberry Pi, STM32
> firmware, YOLO vision and an Android tablet app. The same code drives the real car and a Gazebo sim.

### [📖 Read the Docs →](https://alvin0523.github.io/mdp/) · [🚀 Quickstart →](https://alvin0523.github.io/mdp/quickstart/)

> [!NOTE]
> While task 2 was being tuned, some parts of the car may have been adjusted (a sensor moved, a
> setting changed) without the docs catching up. If something does not match what you see, contact
> **Wei Ming** ([@Alvin0523](https://github.com/Alvin0523)).

---

## 📖 About

This repository is the workspace for the whole robot, set up with [Pixi](https://pixi.sh). The code
lives in three **git submodules** (each its own repository); the rest is in this repository.

**Submodules** (`git clone --recurse-submodules` fetches them):

| Folder | What |
| --- | --- |
| [`mdp_ros/`](https://github.com/Alvin0523/mdp_ros) | ROS 2 Jazzy on the Pi (and the laptop): task runners, Hybrid A* / Reeds-Shepp planning, LQR path following, EKF localisation, YOLO26 vision, the STM32 and tablet bridges, Gazebo sim, Foxglove |
| [`mdp_stm32/`](https://github.com/Alvin0523/mdp_stm32) | STM32F407 firmware (STM32Cube HAL, PlatformIO): 100 Hz wheel-speed PI control, Ackermann steering servo, encoders, ICM-20948 IMU, IR and ultrasonic sensors, binary serial protocol to the Pi |
| [`mdp_android/`](https://github.com/RashI0192/MDP-APP) | Android tablet app (Java): obstacle setup on a 20 × 20 map, Bluetooth link to the Pi, task timers and practice-run analyser |

**In this repository:**

| Folder | What |
| --- | --- |
| [`docs/`](docs) | The [documentation site](https://alvin0523.github.io/mdp/) ([Zensical](https://zensical.org)) |
| [`hardware/`](hardware) | 3D-print files (STL): camera mounts and stands, the "halo" |

## 📷 The Car

| Task 1 setup | Task 2 setup |
| :---: | :---: |
| <img src="docs/images/car-task1.jpg" width="320" alt="The car set up for task 1: camera looking left"> | <img src="docs/images/car-task2.jpg" width="320" alt="The car set up for task 2: camera and ultrasonic forward"> |
| Camera on a mast, looking left - reads the images while parked beside or driving past them | Camera forward at the arrows, ultrasonic at the front for the distances |

## 🏁 Results

| Task | Goal | Result | Time | Video |
| --- | --- | --- | --- | --- |
| **Task 1** - image recognition | Read every image on the obstacles | 7 / 8 images (rank 6, fastest in the top 10) | 52 s | _TBD_ |
| **Task 2** - fastest car | Round both obstacles and back to the carpark | Rank 1 | 8 s | _TBD_ |

## 🎬 Videos

The same code in Gazebo and on the real car, both watched in Foxglove (the real runs played back from
their bags). Sim and real are example runs, not the same obstacle layout. Previews sped up; click for
the full video.

| | Simulation (Gazebo) | Real car (bag playback) |
| :---: | :---: | :---: |
| **Task 1** - every image (4x) | [<img src="docs/images/sim-task1.gif" width="400" alt="Task 1 in Gazebo and Foxglove: the car stops at each obstacle and reads its image">](docs/images/sim-task1.mp4) | [<img src="docs/images/real-task1.gif" width="400" alt="Task 1 on the real car, played back in Foxglove: the car stops at each obstacle and reads its image">](docs/images/real-task1.mp4) |
| **Task 2** - fastest car (1.5x) | [<img src="docs/images/sim-task2.gif" width="400" alt="Task 2 in Gazebo and Foxglove: out of the carpark, round both obstacles and back">](docs/images/sim-task2.mp4) | [<img src="docs/images/real-task2.gif" width="400" alt="Task 2 on the real car, played back in Foxglove: out of the carpark, round both obstacles and back">](docs/images/real-task2.mp4) |

## 🏗️ System Architecture

```mermaid
graph LR
  TAB["📱 Android tablet<br/>obstacle setup · START / STOP · live map"]
  PI["🖥️ Raspberry Pi 4 · ROS 2 Jazzy<br/>task runners · planning · LQR follower<br/>EKF · camera · bridges"]
  LAP["💻 Laptop · ROS 2 Jazzy<br/>YOLO26 · task 1 path planning<br/>Foxglove · bag recording"]
  STM["⚡ STM32F407<br/>100 Hz motor PI · steering servo<br/>encoders · IMU · IR · ultrasonic"]

  TAB <-->|"Bluetooth RFCOMM<br/>text lines"| PI
  PI <-->|"USB serial<br/>binary frames, 100 Hz"| STM
  PI <-->|"Ethernet / WiFi<br/>ROS 2 topics"| LAP
```

| Subsystem | What it does | Docs |
| --- | --- | --- |
| 📱 Android | Place obstacles, send them, START / STOP / RESET, show the robot and the images it read | [Android](https://alvin0523.github.io/mdp/android/) · [Tablet link](https://alvin0523.github.io/mdp/rpi/bluetooth/) |
| 🖥️ Raspberry Pi | Runs the tasks: plans the route, follows it, fuses wheel odometry and the IMU (EKF), talks to the STM32 and the tablet | [RPi](https://alvin0523.github.io/mdp/rpi/) |
| 👁️ Vision | YOLO26 on the Pi camera's frames (on the laptop): image IDs for task 1, arrows and bullseyes for task 2 | [Vision](https://alvin0523.github.io/mdp/rpi/vision/) |
| 🧭 Algorithm | Visiting order (every order, scored by Reeds-Shepp lengths), Hybrid A* paths on a costmap, LQR tracking | [Algorithm](https://alvin0523.github.io/mdp/rpi/algorithm/) |
| ⚡ STM32 | Wheel-speed control, steering, sensors, the serial link | [STM32](https://alvin0523.github.io/mdp/stm32/) |

## 🚀 Quick Start

1. Install Pixi (once):

    ```bash
    curl -fsSL https://pixi.sh/install.sh | bash
    ```

2. Clone with the submodules and build the ROS workspace:

    ```bash
    git clone --recurse-submodules https://github.com/Alvin0523/mdp.git
    ```

    ```bash
    cd mdp/mdp_ros && pixi install && pixi run build
    ```

    Already cloned without `--recurse-submodules`? Run `git submodule update --init --recursive`.

3. Try task 1 in the sim (from `mdp_ros/`, second terminal for `start`):

    ```bash
    pixi run sim task:=1
    ```

    ```bash
    pixi run start
    ```

👉 The real car (Pi + laptop + tablet), calibration, task 2 and every launch argument:
[Quickstart](https://alvin0523.github.io/mdp/quickstart/). Firmware build and flashing:
[STM32](https://alvin0523.github.io/mdp/stm32/).

The docs site locally, from this folder: `pixi install`, then `pixi run serve` (http://127.0.0.1:8000).

## 📁 Repository Structure

```
mdp/
├── mdp_ros/          # submodule - ROS 2 workspace (Pi + laptop + sim)
├── mdp_stm32/        # submodule - STM32 firmware
├── mdp_android/      # submodule - the tablet app (RashI0192/MDP-APP)
├── hardware/         # 3D-print files (STL): camera mounts, halo
├── docs/             # documentation site
│   ├── quickstart.md          # commands: setup, sim, real car, calibration
│   ├── assessment_checklist.md
│   ├── android/  rpi/  stm32/ # per subsystem
│   └── hardware.md  dev_workflow.md  troubleshooting.md  references.md
├── pixi.toml         # docs site tasks (serve, build)
└── zensical.toml     # docs site config
```

## 👥 Team

**Group 14** - NTU College of Computing & Data Science, Multidisciplinary Project, AY2026/27 Semester 1

[<img src="https://github.com/Alvin0523.png" width="80" style="border-radius:50%" alt="Wong Wei Ming">](https://github.com/Alvin0523)
[<img src="https://github.com/ymkatie.png" width="80" style="border-radius:50%" alt="Wang Li Ling, Katie">](https://github.com/ymkatie)
[<img src="https://github.com/RashI0192.png" width="80" style="border-radius:50%" alt="Ojha Rashi">](https://github.com/RashI0192)
[<img src="https://github.com/Albert-Lin227.png" width="80" style="border-radius:50%" alt="Lin Lihong Albert">](https://github.com/Albert-Lin227)
[<img src="https://github.com/kekgan.png" width="80" style="border-radius:50%" alt="Keagan Kong Kai Yi">](https://github.com/kekgan)
[<img src="https://github.com/Yeet94.png" width="80" style="border-radius:50%" alt="Cheong Hoi Chun">](https://github.com/Yeet94)
[<img src="https://github.com/seahsongli.png" width="80" style="border-radius:50%" alt="Seah Song Li">](https://github.com/seahsongli)

| Name | Area | GitHub |
| --- | --- | --- |
| Wong Wei Ming | Raspberry Pi, Algorithm | [@Alvin0523](https://github.com/Alvin0523) |
| Wang Li Ling, Katie | Raspberry Pi | [@ymkatie](https://github.com/ymkatie) |
| Ojha Rashi | Algorithm, Android | [@RashI0192](https://github.com/RashI0192) |
| Lin Lihong Albert | Android | [@Albert-Lin227](https://github.com/Albert-Lin227) |
| Keagan Kong Kai Yi | Vision | [@kekgan](https://github.com/kekgan) |
| Cheong Hoi Chun | STM32 | [@Yeet94](https://github.com/Yeet94) |
| Seah Song Li | STM32 | [@seahsongli](https://github.com/seahsongli) |