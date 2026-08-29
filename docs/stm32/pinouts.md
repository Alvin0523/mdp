---
icon: lucide/binary
---

# Pinouts & Peripherals

Verified pin allocations and hardware peripheral assignments for the WHEELTEC C30D (Revisions 2.0 / 2.1 / 2.2) control board based on official vendor schematics (`references/`).

## Serial Communication (Host↔MCU Transport)

| Peripheral | Pins | Function | Default Baud |
| --- | --- | --- | --- |
| `USART3` | `PD8` (TX3), `PD9` (RX3) | Custom Binary Protocol Host Serial Link (Type-C USB Port 3 via CH9102F) - see [Serial Protocol](protocol.md). Not micro-ROS - that transport was superseded ([ADR 0002](../architecture.md#0002-custom-binary-serial-protocol-vs-micro-ros-rclc)) | 115200 |
| `USART1` | `PA9` (TX1), `PA10` (RX1) | Alternate Serial Link (Type-C USB Port 1 via CH9102F) | 115200 |
| `USART2` | `PD5` (TX2), `PD6` (RX2) | Bluetooth / Wireless Module Interface | 9600 / 115200 |
| `CAN1` | `PD0` (RX), `PD1` (TX) | Onboard CAN Bus Transceiver (VP230) | — |

> [!NOTE]
> **Ackermann Drivetrain Layout:** The MDP Ackermann course kit drives **2 Rear Wheels** (Motor A = Rear Left, Motor B = Rear Right) with Hall Encoders on TIM2 & TIM3. Front wheels are unpowered, free-rolling steering knuckles actuated by **1 HWZ020 Steering Servo** (`PB15` / TIM12_CH2).

## Motor PWM Outputs (AT8236 Driver)

| Motor Channel | Physical Drivetrain Location | PWM Pins | Hardware Timer | Status / Notes |
| --- | --- | --- | --- | --- |
| **Motor A** | Rear Left (BL) | `PB8`, `PB9` | TIM10_CH1 (`PB8`), TIM11_CH1 (`PB9`) | **Active** (Rear-Left Drive Motor) |
| **Motor B** | Rear Right (BR) | `PE5`, `PE6` | TIM9_CH1 (`PE5`), TIM9_CH2 (`PE6`) | **Active** (Rear-Right Drive Motor) |
| **Motor C** | — | `PE9`, `PE11` | TIM1_CH1 (`PE9`), TIM1_CH2 (`PE11`) | *Unused* (Unpowered Front Wheels) |
| **Motor D** | — | `PE13`, `PE14` | TIM1_CH3 (`PE13`), TIM1_CH4 (`PE14`) | *Unused* (Unpowered Front Wheels) |
| **Motor ON/OFF Switch** | `SW3` (`BSI-10`) | `PD3` | Digital **Input**, pull-up | `3V3 -> R35 (100R) -> PD3`, `SW3` grounds it when switched OFF - idles HIGH (motor ON/ready), reads LOW when switched OFF. **MCU reads this, does not drive it** - it's the physical switch's state coming in, not an MCU-controlled enable output. Read via `motor_estop_engaged()` (`motor.c`); gates both self-test and the main drive loop (`main.c`) - motors are forced to 0% PWM whenever this reads OFF, regardless of host commands |
| **Wheel-Speed PID Control Loop** | — (internal timer, no pin) | `TIM7` | Update-interrupt timer, no output | 100Hz fixed-period ISR (`motor_pid_init()`/`motor.c`) running the closed-loop wheel-speed PID that ultimately drives Motor A/B above - see [Closed-Loop Control & Sensor Fusion](control_loop.md) |

## Wheel Encoders (Hall Encoders)

| Encoder Channel | Location | Pins | Hardware Timer (Quadrature Mode) | Status / Notes |
| --- | --- | --- | --- | --- |
| **Encoder A** | Rear Left (BL) | `PA15` (A), `PB3` (B) | TIM2_CH1 / CH2 | **Active** (Rear-Left Encoder) |
| **Encoder B** | Rear Right (BR) | `PB4` (A), `PB5` (B) | TIM3_CH1 / CH2 | **Active** (Rear-Right Encoder) |
| **Encoder C** | — | `PB6` (A), `PB7` (B) | TIM4_CH1 / CH2 | *Unused* |
| **Encoder D** | — | `PA0` (A), `PA1` (B) | TIM5_CH1 / CH2 | *Unused* |

## Steering Servo & Sensors

| Function | Pins | Peripheral | Signal / Details |
| --- | --- | --- | --- |
| **Steering Servo** | `PB15` | TIM12_CH2 | 50 Hz PWM (1.0ms–2.0ms pulse width) |
| **IMU Sensor** | `PB10` (SCL), `PB11` (SDA) | Bit-banged software I2C (GPIO, `GPIO_MODE_OUTPUT_OD`) - **not** the hardware `I2C2` peripheral | ICM-20948 (9-DOF Gyro/Accel/Mag - only accel+gyro registers are read, magnetometer unused). Polled (`imu_update()`), not interrupt-driven - no `INT` pin connected in firmware |
| **Battery AD** | `PB0` | ADC1_CH8 | Voltage measurement via resistor divider |
| **Car Type Select** | `PB1` | ADC1_CH9 | Potentiometer voltage reading |
| **OLED Display** | `PD11`, `PD12`, `PD13`, `PD14` | GPIO | 0.96" OLED SPI Bit-banged display |
| **Status LED** | `PE8` | GPIO | Board status LED |
| **User Button** | `PE0` | GPIO | Onboard push button |
| **Buzzer** | `PA8` | GPIO | Onboard buzzer |
