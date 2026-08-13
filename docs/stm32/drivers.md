---
icon: lucide/wrench
---

# Drivers & Sensors

Overview of device drivers and sensor interfaces running on the STM32 MCU inside `mdp_stm32` for the **MDP Sem 1 AY26/27** course kit.

## Implementation Status

| Driver / Module | Target Hardware | Status | File Path |
| --- | --- | --- | --- |
| **Motor PWM Driver** | AT8236 Dual Full-Bridge (2 Rear Motors) | Implemented :white_check_mark: | `app/src/motor.c` |
| **micro-ROS Transport** | USART3 @ 115200 (`PD8`/`PD9`) | TODO :hourglass_flowing_sand: | `app/src/main.c` |
| **Steering Servo** | HWZ020 Servo PWM (50 Hz on `PB15`) | Stub :hourglass_flowing_sand: | `app/src/servo.c` |
| **Hall Encoders** | Hall Encoders on 2× `MG513P3012V` Motors | Stub :hourglass_flowing_sand: | `app/src/encoder.c` |
| **IMU Sensor** | ICM-20948 (I2C2 `PB10`/`PB11`) | Stub :hourglass_flowing_sand: | `app/src/imu.c` |

## AT8236 Motor Driver (`motor.c`)

Drives the 2 rear DC motors (`MG513P3012V`) via PWM duty cycles using Zephyr's `pwm_set_pulse_dt` API. Reused from earlier firmware bring-up per [ADR 0004](../adr/0004-zephyr-firmware-base.md).

## Hall Encoder Driver (`encoder.c`)

Reads 6-pin Hall encoder pulse ticks on the 2 rear drive motors using STM32 hardware timer encoder mode (TIM2 / TIM3). Decodes counts into rear wheel rotation and linear travel distance.
