---
icon: lucide/wrench
---

# Drivers & Sensors

Overview of device drivers and sensor interfaces running on the STM32 MCU inside `mdp_stm32` for the **MDP Sem 1 AY26/27** course kit. Firmware is bare-metal STM32Cube HAL, built/flashed via PlatformIO ([ADR 0001](../architecture.md#0001-stm32cube-hal-bare-metal-via-platformio-vs-zephyr-rtos)).

## Implementation Status

| Driver / Module | Target Hardware | Status | File Path |
| --- | --- | --- | --- |
| **Motor PWM Driver** | AT8236 Dual Full-Bridge (2 Rear Motors) | Implemented :white_check_mark: | `src/motor.c` |
| **Wheel Encoders** | Hall Encoders on 2× `MG513P3012V` Motors (TIM2/TIM3) | Implemented :white_check_mark: | `src/encoder.c` |
| **Steering Servo** | HWZ020 Servo PWM (50 Hz on `PB15` / TIM12_CH2) | Implemented :white_check_mark: | `src/servo.c` |
| **Enable / E-Stop Switch** | Onboard switch, `PD3` | Implemented :white_check_mark: | `src/motor.c` (`motor_estop_engaged`) |
| **IMU Sensor** | ICM-20948 (I2C2 `PB10`/`PB11`) | Implemented :white_check_mark: | `src/imu.c` |
| **OLED Display** | 0.96" SSD1306, bit-banged (`PD11`-`PD14`) | Implemented :white_check_mark: | `src/oled.c` |
| **Host Serial Protocol** | USART3 @ 115200 (`PD8`/`PD9`) | Implemented :white_check_mark: | `src/usart.c`, `include/protocol.h` (see [Serial Protocol](protocol.md)) |
| **Battery Voltage ADC** | `PB0` / ADC1_CH8 | **Not Started** :x: | — |
| **Ultrasonic Sensor** | HC-SR04 | **Not Started** :x: | — |
| **IR Range Sensors** | Sharp GP2Y0A21YK ×2 | **Not Started** :x: | — |

## AT8236 Motor Driver (`motor.c`)

Drives the 2 rear DC motors (`MG513P3012V`) via PWM on `TIM9`/`TIM10`/`TIM11` (`motor_init`, `motor_set_speed`). Pin/timer assignments verified against the WHEELTEC C30D resource-allocation PDF and ported from the vendor's Hall-encoder reference firmware. `motor_set_speed_rad_s` converts a target wheel angular velocity (rad/s) to a PWM percent open-loop, using the motor's rated 330 RPM max output speed — see [Serial Protocol](protocol.md) for where that command comes from.

!!! warning "Must use locked-antiphase drive, not a naive 0%/duty% scheme"
    `motor_drive()` uses a **locked-antiphase** scheme, ported from WHEELTEC's own vendor reference firmware (`BALANCE/balance.c`'s `Set_Pwm`): both `IN1`/`IN2` sit at ~100% duty (electrical brake) at rest, and one side is pulled down from 100% by the commanded speed magnitude to produce a direction. A simpler-looking "stopped side = 0%, driven side = duty%" scheme was tried first and left the wheels **completely dead despite correct-looking PWM commands and zero errors** — the AT8236 needs continuous switching activity on both legs to keep its internal high-side gate drive/charge-pump alive, and a leg held statically low never actually turns the output on. If you're debugging "motors don't spin but everything else looks right," check this first before suspecting wiring or power.

## Hall Encoder Driver (`encoder.c`)

Reads Hall encoder ticks on the 2 rear drive motors using STM32 hardware timer encoder mode (`TIM2`/`TIM3`). Exposes both a per-call delta read (`encoder_get_delta_a`/`_b`, resets the hardware counter each call — used for a tick-rate speed estimate) and a running cumulative count (`encoder_get_count_a`/`_b`). Not yet calibrated to real distance/speed units (gear ratio and wheel diameter constants unconfirmed for this board).

!!! note "Motor B (rear right) counts are negated to match Motor A's sign convention"
    Motor B is mounted as a mirror image of Motor A, so its raw Hall A/B phase order comes out reversed relative to "forward" even though `motor_set_speed()` drives both sides with the same positive=forward PWM convention. `encoder_get_delta_b`/`encoder_get_count_b` negate the raw timer count before returning it, so both motors honor the "positive = forward" contract documented in `encoder.h` — without this, the rear-right encoder read negative while driving forward (visible on the OLED's `Enc R` field and in `/joint_states` telemetry).

## Steering Servo Driver (`servo.c`)

Drives the HWZ020 Ackermann steering servo on `PB15` (`TIM12_CH2`, 50Hz PWM). `SERVO_ANGLE_MAX_DEG` is a placeholder estimate — needs tuning against the actual mechanical steering-lock limit.

## Enable / E-Stop Switch (`motor.c`)

Reads the onboard enable/e-stop switch on `PD3` (input, pull-up) via `motor_estop_engaged()`. Polarity (switch-to-GND = engaged) is assumed from the vendor reference and not yet physically verified — flip the comparison in `motor.c` if it reads backwards.

## Not Yet Implemented

- **Battery Voltage ADC (`PB0` / ADC1_CH8):** No driver written. The sense-resistor divider ratio couldn't be confirmed from the schematic PDF text extraction (schematic is a visual layout, not linear text) — implementing this without a confirmed ratio would produce a plausible-looking but potentially wrong battery voltage reading, so it's left as a placeholder in `main.c` rather than guessed.
- **Ultrasonic Sensor (HC-SR04):** No driver exists.
- **IR Range Sensors (Sharp GP2Y0A21YK ×2):** No driver exists.
