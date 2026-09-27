---
icon: lucide/list
---

# Hardware Components & Physics Specifications

This page documents the verified hardware components and physical specifications for the **MDP Course (Semester 1 AY26/27)** based on the official course component list (`docs/attachments/MDP student component list_ Sem1_2627.pdf`) and WHEELTEC C30D Rev 2.1 schematics.

## MDP Course Hardware Specifications

| Component | Course Specification | Physical & URDF Physics Details |
| --- | --- | --- |
| **Control Board** | **WHEELTEC C30D V2.1** | STM32F407VET6 MCU mounted on acrylic protector plate |
| **Drive Motors** | `MG513P3012V` (×2) | 12V DC geared motors (1:30 reduction ratio), 330 RPM max speed, driving rear wheels (`lb_joint`, `rb_joint`) |
| **Wheel Encoders** | **Hall Encoders** (2.54mm pitch, 6-pin) | 2 units mounted on drive motors (Model: `MG513P3012V`) |
| **Steering Servo** | Model `HWZ020` (4.8V – 7.4V) | Front Ackermann steering servo (`left_joint`, `right_joint`). **Stall Torque:** 1.96 N·m (20 kg·cm). **Max Speed:** 6.54 rad/s (0.16s / 60°). **Datasheet travel:** $\pm 22.35^\circ$ ($\pm 0.39\text{ rad}$) — the servo's own internal range, *not* the angle this chassis's linkage achieves at the wheel (measured: left $43.0^\circ$, right $32.5^\circ$, both wheels the same angle) |
| **Motor Driver** | Dual AT8236 H-Bridge | Board-integrated motor driver (`src/motor.c`) |
| **Onboard SBC (Host)** | **Raspberry Pi 4 Model B (4GB)** | Runs ROS2 Jazzy + `mdp_bridge`, connected to STM32 via USB Type-C |
| **Camera** | **RPi Camera Module V2** | Sony IMX219 8MP sensor connected via CSI flexi cable. Driver: `ros-jazzy-v4l2-camera` (`v4l2_camera_node` publishing `/image_raw`) + `ros-jazzy-compressed-image-transport` (`/image_raw/compressed` for streaming) + `ros-jazzy-cv-bridge` |
| **IR Range Sensors** | **Sharp GP2Y0A21YK** (×2) | Analog IR distance sensors with 3D printed brackets |
| **Ultrasonic Sensor** | **HC-SR04** (×1) | Distance measurement sensor |
| **IMU Sensor** | **ICM-20948** (Onboard) | 9-DOF Motion Sensor via bit-banged software I2C on `PB10`/`PB11` (not the hardware `I2C2` peripheral) |
| **Debugger** | **ST-LINK/V2 (SWD)** | Connected via 4-pin header: 3.3V (Red), SWCLK (Black - PA14), GND (Blue), SWDIO (Yellow - PA13) |
| **Battery Pack** | 12.6 V 3400 mAh Li-ion Pack | 3× NCR18650B cells (12.6V max, right-angle DC connector) |
| **Tablet / UI** | Samsung Galaxy Tab A7 Lite (SM-T220) | User control / Android app tablet interface |

---

## Measured Car Numbers

Wheelbase, tracks, tyre size, steering limits, body outline, turning circles and sensor positions
as measured on our car, with where each lives and how to re-measure it:
[Quickstart → Measured car numbers](quickstart.md#7-measured-car-numbers). That table is the one
kept up to date; it is not repeated here.

---

## Key Differences from Standard WHEELTEC Stock Models

1. **Hall Encoders vs. GMR Encoders**:
   - The official MDP course kit uses **Hall Encoders** on `MG513P3012V` motors (6-pin 2.54mm pitch connector).
   - Firmware encoder decode logic (`src/encoder.c`) uses Hall encoder pulse resolution.

2. **Drivetrain Layout**:
   - **2 Driven Rear Wheels** (Left Motor A, Right Motor B) + **1 Steering Servo** (`HWZ020`) controlling front wheels in an Ackermann geometry.

3. **Onboard Computing & Perception**:
   - **Raspberry Pi 4B (4GB)** serves as the main host computer running ROS2 Jazzy and the `mdp_bridge` serial bridge node over USB serial (`USART3`).
   - Perception hardware includes **RPi Camera V2**, **2× Sharp GP2Y0A21YK IR sensors**, and **1× HC-SR04 Ultrasonic sensor**.
