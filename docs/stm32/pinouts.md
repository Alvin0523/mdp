---
icon: lucide/binary
---

# Pinouts & Peripherals

Verified pin allocations and hardware peripheral assignments for the WHEELTEC C30D (Revisions 2.0 / 2.1 / 2.2) control board based on official vendor schematics (`references/`).

## Serial Communication (Host↔MCU Transport)

| Peripheral | Pins | Function | Default Baud |
| --- | --- | --- | --- |
| `USART3` | `PD8` (TX3), `PD9` (RX3) | Micro-ROS Host Serial Link (Type-C USB Port 3 via CH9102F) | 115200 |
| `USART1` | `PA9` (TX1), `PA10` (RX1) | Alternate Serial Link (Type-C USB Port 1 via CH9102F) | 115200 |
| `USART2` | `PD5` (TX2), `PD6` (RX2) | Bluetooth / Wireless Module Interface | 9600 / 115200 |
| `CAN1` | `PD0` (RX), `PD1` (TX) | Onboard CAN Bus Transceiver (VP230) | — |

## Motor PWM Outputs (AT8236 Driver)

| Motor Channel | Location | PWM Pins | Hardware Timer |
| --- | --- | --- | --- |
| **Motor A** | Front Left (FL) | `PB8`, `PB9` | TIM10_CH1 (`PB8`), TIM11_CH1 (`PB9`) |
| **Motor B** | Front Right (FR) | `PE5`, `PE6` | TIM9_CH1 (`PE5`), TIM9_CH2 (`PE6`) |
| **Motor C** | Rear Left (BL) | `PE9`, `PE11` | TIM1_CH1 (`PE9`), TIM1_CH2 (`PE11`) |
| **Motor D** | Rear Right (BR) | `PE13`, `PE14` | TIM1_CH3 (`PE13`), TIM1_CH4 (`PE14`) |
| **Motor Enable** | Drivers | `PD3` | Digital Output (`HIGH` = Enabled) |

## Wheel Encoders (GMR Quadrature)

| Encoder Channel | Location | Pins | Hardware Timer (Quadrature Mode) |
| --- | --- | --- | --- |
| **Encoder A** | Front Left (FL) | `PA15` (A), `PB3` (B) | TIM2_CH1 / CH2 |
| **Encoder B** | Front Right (FR) | `PB4` (A), `PB5` (B) | TIM3_CH1 / CH2 |
| **Encoder C** | Rear Left (BL) | `PB6` (A), `PB7` (B) | TIM4_CH1 / CH2 |
| **Encoder D** | Rear Right (BR) | `PA0` (A), `PA1` (B) | TIM5_CH1 / CH2 |

## Steering Servo & Sensors

| Function | Pins | Peripheral | Signal / Details |
| --- | --- | --- | --- |
| **Steering Servo** | `PB15` | TIM12_CH2 | 50 Hz PWM (1.0ms–2.0ms pulse width) |
| **IMU Sensor** | `PB10` (SCL), `PB11` (SDA), `PB12` (INT) | I2C2 + EXTI | ICM-20948 (9-DOF Gyro/Accel/Mag) |
| **Battery AD** | `PB0` | ADC1_CH8 | Voltage measurement via resistor divider |
| **Car Type Select** | `PB1` | ADC1_CH9 | Potentiometer voltage reading |
| **OLED Display** | `PD11`, `PD12`, `PD13`, `PD14` | GPIO | 0.96" OLED SPI Bit-banged display |
| **Status LED** | `PE8` | GPIO | Board status LED |
| **User Button** | `PE0` | GPIO | Onboard push button |
| **Buzzer** | `PA8` | GPIO | Onboard buzzer |
