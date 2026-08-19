---
icon: lucide/cpu
---

# Hardware Components & Physics Specifications

This page documents the verified hardware components and physical specifications for the **MDP Course (Semester 1 AY26/27)** based on the official course component list (`docs/attachments/MDP student component list_ Sem1_2627.pdf`) and WHEELTEC C30D Rev 2.1 schematics.

## MDP Course Hardware Specifications

| Component | Course Specification | Physical & URDF Physics Details |
| --- | --- | --- |
| **Control Board** | **WHEELTEC C30D V2.1 / V2.2** | STM32F407VET6 MCU (168 MHz CPU, 8 MHz HSE external crystal oscillator) |
| **Drive Motors** | `MG513P3012V` (×2) | 12V DC geared motors (1:30 reduction ratio), 330 RPM max speed, driving rear wheels (`lb_joint`, `rb_joint`) |
| **Wheel Encoders** | **Hall Encoders** (2.54mm pitch, 6-pin) | 2 units mounted on drive motors (Model: `MG513P3012V`) |
| **Steering Servo** | Model `HWZ020` (4.8V – 7.4V) | Front Ackermann steering servo (`left_joint`, `right_joint`). **Stall Torque:** 1.96 N·m (20 kg·cm). **Max Speed:** 6.54 rad/s (0.16s / 60°). **Mechanical Limits:** $\pm 22.35^\circ$ ($\pm 0.39\text{ rad}$) |
| **Motor Driver** | Dual AT8236 H-Bridge | Board-integrated motor driver (`src/motor.c`) |
| **Onboard SBC (Host)** | **Raspberry Pi 4 Model B (4GB)** | Runs ROS2 Jazzy + ros2_control hardware interface over USB serial (`USART3` @ 115200 baud) |
| **Camera** | **RPi Camera Module V2** | Sony IMX219 8MP sensor connected via CSI flexi cable. Driver: `ros-jazzy-v4l2-camera` (`v4l2_camera_node` publishing `/image_raw`) + `ros-jazzy-compressed-image-transport` (`/image_raw/compressed` for streaming) + `ros-jazzy-cv-bridge` |
| **IR Range Sensors** | **Sharp GP2Y0A21YK** (×2) | Analog IR distance sensors with 3D printed brackets |
| **Ultrasonic Sensor** | **HC-SR04** (×1) | Distance measurement sensor |
| **IMU Sensor** | **ICM-20948** (Onboard) | 9-DOF Motion Sensor via `I2C2` (`PB10`/`PB11`) |
| **Debugger** | **ST-LINK/V2 (SWD)** | Connected via 4-pin header: 3.3V (Red), SWCLK (Black - PA14), GND (Blue), SWDIO (Yellow - PA13) |
| **Battery Pack** | 12.6 V 3400 mAh Li-ion Pack | 3× NCR18650B cells (12.6V max, right-angle DC connector) |
| **Tablet / UI** | Samsung Galaxy Tab A7 Lite (SM-T220) | User control / Android app tablet interface |

> [!IMPORTANT]
> **External Crystal (`HSE_VALUE = 8000000L`):**  
> The WHEELTEC C30D board uses an **8.000 MHz external quartz crystal**. ST HAL defaults to `25000000L` (25 MHz) if not explicitly specified, which causes USART baud rate miscalculations and garbled serial output. Always include `-D HSE_VALUE=8000000L` in `platformio.ini` `build_flags`.


---

## Physical Drivetrain Geometry & URDF Physics Parameters

| Parameter | Nominal Physical Value | URDF / Controller Config | Purpose & Notes |
| --- | --- | --- | --- |
| **Wheel Radius ($R$)** | `0.0325 m` (65 mm diameter) | `traction_wheels_radius: 0.0325` | Used by `ackermann_steering_controller` odometry calculation |
| **Wheelbase ($L$)** | `0.1433 m` (143.3 mm) | `wheelbase: 0.1433` | Measured front-to-rear axle center distance matching URDF CAD mesh |
| **Steering Kingpin Width ($W_s$)** | `0.1040 m` (104 mm) | `steering_track_width: 0.104` | Distance between left and right steering kingpin pivot axes (used for Ackermann angle kinematics) |
| **Traction Track Width ($W_t$)** | `0.1600 m` (160 mm) | `traction_track_width: 0.16` | Distance between left and right rear wheel centers |
| **Steering Joint Limits** | $\pm 22.35^\circ$ ($\pm 0.39\text{ rad}$) | `lower="-0.39" upper="0.39"` | Mechanical steering knuckle range limit in URDF |
| **Steering Max Effort** | 1.96 N·m (Stall Torque) | `effort="10.0"` | URDF physics joint limit (prevents solver contact locking in Gazebo) |
| **Steering Max Speed** | ~6.54 rad/s (375°/s) | `velocity="5.0"` | URDF physics max turn rate for `left_joint` & `right_joint` |
| **Front Wheel Friction ($\mu_1$ / $\mu_2$)** | *unmeasured — provisional* | `mu1: 2.0`, `mu2: 0.4` on `lf_link`/`rf_link` | **Must be tuned against measured real wheel-to-floor grip when available, never detuned just to make Gazebo's visible turning match `/tf`** — the controller's odometry ignores the front wheels' kingpin-to-contact offset, so some TF/visual mismatch under turning is expected model error, not a friction bug. See [troubleshooting](troubleshooting.md). |

---

## Key Differences from Standard WHEELTEC Stock Models

1. **Hall Encoders vs. GMR Encoders**:
   - The official MDP course kit uses **Hall Encoders** on `MG513P3012V` motors (6-pin 2.54mm pitch connector).
   - Firmware encoder decode logic (`app/src/encoder.c`) uses Hall encoder pulse resolution.

2. **Drivetrain Layout**:
   - **2 Driven Rear Wheels** (Left Motor A, Right Motor B) + **1 Steering Servo** (`HWZ020`) controlling front wheels in an Ackermann geometry.

3. **Onboard Computing & Perception**:
   - **Raspberry Pi 4B (4GB)** serves as the main host computer running ROS2 Jazzy and micro-ROS Agent over USB serial (`USART3`).
   - Perception hardware includes **RPi Camera V2**, **2× Sharp GP2Y0A21YK IR sensors**, and **1× HC-SR04 Ultrasonic sensor**.
