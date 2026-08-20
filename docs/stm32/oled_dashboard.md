# 📺 WHEELTEC C30D OLED Dashboard Specification & Design Proposal

This document defines the layout, state machine, fault diagnostic triggers, and hardware specifications for the **0.96-inch SSD1306 OLED Dashboard** on the WHEELTEC C30D (STM32F407VET6 Rev 2.2) platform.

---

## 1. ⚙️ Hardware & Display Specification

| Parameter | Specification | Notes |
| :--- | :--- | :--- |
| **Controller IC** | SSD1306 | Bit-bang SPI GPIO driver |
| **Resolution** | $128 \times 64$ pixels | 4 rows $\times$ 16 columns (at $8 \times 16$ font) |
| **Physical Pin Allocations** | `PD14` (SCLK), `PD13` (SDIN), `PD12` (RST), `PD11` (DC) | WHEELTEC C30D Board Standard |
| **User Navigation** | `PE0` Button (Active Low, EXTI0 Interrupt) | Cycles through Pages 1 $\rightarrow$ 2 $\rightarrow$ 3 $\rightarrow$ 4 $\rightarrow$ 1 |
| **Font Standard** | $8 \times 16$ Vertical-Column Packed ASCII | Crisp contrast, high legibility |
| **Bezel Margin Bounds** | **8 pixels left/right padding** (Column 8 to Column 120) | **14 characters max per row** (prevents bezel clipping) |

---

## 2. 📊 Page 1 Layout & State Machine Proposals

Page 1 serves as the primary operational telemetry dashboard for the user and developers.

### Layout Matrix (14 Characters Max, 8px Margins)

#### Case A: Normal Operation (ROS2 Control Mode)
```text
┌────────────────┐
│ SYS:OK   12.4V │  <-- Line 0: Overall System Status & Battery Voltage (12.4V)
│ MODE:ROS2_CTRL │  <-- Line 1: Active Operational Mode (ROS2 Control)
│ L:0.25  R:0.25 │  <-- Line 2: Rear Left & Right Wheel Speeds (m/s)
│ STR:+12  Y:+45 │  <-- Line 3: Front Servo Steering Angle & IMU Yaw Angle
└────────────────┘
```

#### Case B: Serial Link Fault (UART Disconnection / Loss of Frame)
```text
┌────────────────┐
│ E:UART_DISCONN │  <-- Line 0: Active Error Alarm
│ MODE: SAFE_STOP│  <-- Line 1: Fail-Safe Isolation Engaged
│ L:0.00  R:0.00 │  <-- Line 2: Motors Disabled (0 m/s)
│ STR:  0  Y:+45 │  <-- Line 3: Steering Servo Centered (0°)
└────────────────┘
```

#### Case C: Low Battery Protection Warning ($V_{\text{bat}} < 10.5\text{ V}$)
```text
┌────────────────┐
│ E:LOW_BAT 10.2V│  <-- Line 0: Critical Low Voltage Alarm
│ MODE: DIS_MOTOR│  <-- Line 1: Motor Outputs Isolated to Protect Battery
│ L:0.00  R:0.00 │  <-- Line 2: Speeds Locked at 0 m/s
│ STR:  0  Y:+45 │  <-- Line 3: Steering Servo Centered
└────────────────┘
```

#### Case D: Manual Override (Remote Teleop / Joystick Control)
```text
┌────────────────┐
│ SYS:OK   12.4V │  <-- Line 0: System Health & Voltage
│ MODE: MANUAL_RC│  <-- Line 1: Manual Teleop Override Mode
│ L:0.45  R:0.45 │  <-- Line 2: Manual Throttle Speeds (m/s)
│ STR:-15  Y:-22 │  <-- Line 3: Manual Steering Angle & Heading
└────────────────┘
```

---

## 3. 🔍 Secondary Navigation Pages

### Page 2: Distance & Obstacle Perception
```text
┌────────────────┐
│== DISTANCE =   │  <-- Line 0: Header
│Ultra : 18.5    │  <-- Line 1: Front Ultrasonic Distance (cm)
│IR Left: 22.0   │  <-- Line 2: Sharp Left IR Standoff Distance (cm)
│IR Rght: 24.5   │  <-- Line 3: Sharp Right IR Standoff Distance (cm)
└────────────────┘
```

### Page 3: Hardware & Safety Diagnostics
```text
┌────────────────┐
│ESTOP: READY    │  <-- Line 0: Physical Emergency Stop Button State
│Enc L: 14500    │  <-- Line 1: TIM2 Rear Left Encoder Raw Ticks
│Enc R: 14480    │  <-- Line 2: TIM3 Rear Right Encoder Raw Ticks
│Uptime: 01:45   │  <-- Line 3: Firmware Run Time (Minutes:Seconds)
└────────────────┘
```

### Page 4: Bezel Alignment Calibration Test
```text
┌────────────────┐
│1234567890123456│  <-- Line 0: Full 16-character index (Col 0 to Col 15)
│|==============|│  <-- Line 1: '|' at Col 0 & Col 15 (Tests physical glass bezel)
│ABCDEFGHIJKLMNOP│  <-- Line 2: Full 16-character alphabet
│<-------------->│  <-- Line 3: Border arrows
└────────────────┘
```

---

## 4. 🔄 Future Discussion Items
- [ ] Finalize state transition rules between `ROS2_CTRL`, `MANUAL_RC`, and `SAFE_STOP`.
- [ ] Confirm low-voltage warning cut-off thresholds for 3S LiPo vs 3S Li-ion batteries.
- [ ] Determine if IMU Gyro Z integration drift should be reset via `PE0` long-press.
