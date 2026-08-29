---
icon: lucide/wrench
---

# Drivers & Sensors

Overview of device drivers and sensor interfaces running on the STM32 MCU inside `mdp_stm32` for the **MDP Sem 1 AY26/27** course kit. Firmware is bare-metal STM32Cube HAL, built/flashed via PlatformIO ([ADR 0001](../architecture.md#0001-stm32cube-hal-bare-metal-via-platformio-vs-zephyr-rtos)).

## Implementation Status

| Driver / Module | Target Hardware | Status | File Path |
| --- | --- | --- | --- |
| **Motor PWM Driver** | AT8236 Dual Full-Bridge (2 Rear Motors) | Implemented :white_check_mark: | `src/motor.c` |
| **Closed-Loop Wheel-Speed PID** | 100Hz `TIM7` ISR, hybrid feedforward+incremental-PI | Implemented, **not bench-tuned/hardware-tested** :warning: | `src/motor.c` (`motor_pid_*`) - see [Closed-Loop Control & Sensor Fusion](control_loop.md) |
| **Wheel Encoders** | Hall Encoders on 2× `MG513P3012V` Motors (TIM2/TIM3) | Implemented :white_check_mark: | `src/encoder.c` |
| **Steering Servo** | HWZ020 Servo PWM (50 Hz on `PB15` / TIM12_CH2) | Implemented :white_check_mark: | `src/servo.c` |
| **Motor ON/OFF Switch** | Onboard switch (`SW3`), `PD3` | Implemented :white_check_mark: | `src/motor.c` (`motor_estop_engaged`) - gates both self-test and the main drive loop |
| **IMU Sensor** | ICM-20948, bit-banged I2C (`PB10`/`PB11`) | Implemented :white_check_mark: | `src/imu.c` |
| **OLED Display** | 0.96" SSD1306, bit-banged (`PD11`-`PD14`) | Implemented :white_check_mark: | `src/oled.c` |
| **User Button** | `PE0`, EXTI0 | Implemented :white_check_mark: | `src/button.c` - dual-purpose (self-test trigger / OLED page flip), gated on the motor switch |
| **Self-Test Sequence** | Drive/steer/servo-sweep smoke test | Implemented :white_check_mark: | `src/selftest.c` - see [Serial Protocol](protocol.md) for the trigger/blink-pattern reference |
| **Host Serial Protocol** | USART3 @ 115200 (`PD8`/`PD9`) | Implemented :white_check_mark: | `src/usart.c`, `include/protocol.h` (see [Serial Protocol](protocol.md)) |
| **Battery Voltage ADC** | `PB0` / ADC1_CH8 | Implemented :white_check_mark: | `src/battery.c` |
| **Ultrasonic Sensor** | HC-SR04 | **Not Started** :x: | — |
| **IR Range Sensors** | Sharp GP2Y0A21YK ×2 | **Not Started** :x: | — |

## AT8236 Motor Driver (`motor.c`)

Drives the 2 rear DC motors (`MG513P3012V`) via PWM on `TIM9`/`TIM10`/`TIM11` (`motor_init`, `motor_set_speed`). Pin/timer assignments verified against the WHEELTEC C30D resource-allocation PDF and ported from the vendor's Hall-encoder reference firmware.

Wheel speed is **closed-loop** as of the closed-loop PID work (see [Closed-Loop Control & Sensor Fusion](control_loop.md)): the main loop no longer calls `motor_set_speed_rad_s()` directly to drive - it calls `motor_pid_set_target()`/`motor_pid_enable()`, and a 100Hz `TIM7` ISR (`motor_pid_*` functions) reads the encoders, runs a hybrid feedforward+incremental-PI controller (`rad_s_to_pct()` supplies the feedforward baseline, same formula as before), and writes the corrected PWM via `motor_set_speed()`. `motor_set_speed_rad_s()` itself is unchanged and still available as a standalone open-loop primitive, just no longer the live control path from `main.c`. **PID gains are untuned placeholders** - not yet bench-tested on hardware.

!!! warning "Must use locked-antiphase drive, not a naive 0%/duty% scheme"
    `motor_drive()` uses a **locked-antiphase** scheme, ported from WHEELTEC's own vendor reference firmware (`BALANCE/balance.c`'s `Set_Pwm`): both `IN1`/`IN2` sit at ~100% duty (electrical brake) at rest, and one side is pulled down from 100% by the commanded speed magnitude to produce a direction. A simpler-looking "stopped side = 0%, driven side = duty%" scheme was tried first and left the wheels **completely dead despite correct-looking PWM commands and zero errors** — the AT8236 needs continuous switching activity on both legs to keep its internal high-side gate drive/charge-pump alive, and a leg held statically low never actually turns the output on. If you're debugging "motors don't spin but everything else looks right," check this first before suspecting wiring or power.

## Hall Encoder Driver (`encoder.c`)

Reads Hall encoder ticks on the 2 rear drive motors using STM32 hardware timer encoder mode (`TIM2`/`TIM3`). Exposes both a per-call delta read (`encoder_get_delta_a`/`_b`, resets the hardware counter each call — used for a tick-rate speed estimate) and a running cumulative count (`encoder_get_count_a`/`_b`). Ticks-per-revolution is physically confirmed (below); ticks-to-real-distance is not — wheel diameter/effective rolling radius hasn't been independently measured on this board, so `encoder_get_count_a/_b` gives an accurate tick count but not yet an accurate meters value.

!!! note "Motor B (rear right) counts are negated to match Motor A's sign convention"
    Motor B is mounted as a mirror image of Motor A, so its raw Hall A/B phase order comes out reversed relative to "forward" even though `motor_set_speed()` drives both sides with the same positive=forward PWM convention. `encoder_get_delta_b`/`encoder_get_count_b` negate the raw timer count before returning it, so both motors honor the "positive = forward" contract documented in `encoder.h` — without this, the rear-right encoder read negative while driving forward (visible on the OLED's `Enc R` field and in `/joint_states` telemetry).

### Ticks-per-revolution — physically confirmed on hardware

The `1560` ticks/rev figure used throughout (`selftest.c`'s drive-1-revolution test, `mdp_hardware_bridge`'s `kRadPerTick` conversion) comes from WHEELTEC's vendor formula for this exact motor/encoder combo: `EncoderMultiples(4) × Hall_13(13 pulses/motor-rev) × HALL_30F(30:1 gear ratio) = 1560`. This had never been independently checked on this specific board until now.

**Method**: a reference mark was placed on the wheel and a fixed reference mark on the chassis right next to it. With the wheel free-spinning (car on blocks, motor switch OFF), the wheel was rotated by hand exactly 1 full turn at a time (marks realigned each time), reading the cumulative tick count off OLED page 3 (`Enc L`/`Enc R`) after each turn — 10 consecutive full rotations per wheel.

**Left wheel** — cumulative counts after each of 10 rotations: `1560, 3121, 4680, 6241, 7800, 9361, 10921, 12481, 14041, 15600`.

| Round | Cumulative | Delta (ticks this rev) |
| --- | --- | --- |
| 1 | 1560 | 1560 |
| 2 | 3121 | 1561 |
| 3 | 4680 | 1559 |
| 4 | 6241 | 1561 |
| 5 | 7800 | 1559 |
| 6 | 9361 | 1561 |
| 7 | 10921 | 1560 |
| 8 | 12481 | 1560 |
| 9 | 14041 | 1560 |
| 10 | 15600 | 1559 |

Average: `15600 / 10 = 1560.0` ticks/rev exactly. Per-round spread is ±1 tick (std dev ≈0.77), consistent with hand-alignment noise rather than any real deviation.

**Right wheel** — cumulative counts after each of 10 rotations: `1560, 3121, 4680, 6241, 7800, 9355, 10921, 12480, 14041, 15600`.

| Round | Cumulative | Delta (ticks this rev) |
| --- | --- | --- |
| 1 | 1560 | 1560 |
| 2 | 3121 | 1561 |
| 3 | 4680 | 1559 |
| 4 | 6241 | 1561 |
| 5 | 7800 | 1559 |
| 6 | 9355 | 1555 |
| 7 | 10921 | 1566 |
| 8 | 12480 | 1559 |
| 9 | 14041 | 1561 |
| 10 | 15600 | 1559 |

Average: `15600 / 10 = 1560.0` ticks/rev exactly — same as left. Rounds 6-7 show a paired counting glitch (round 6 five ticks short, round 7 six ticks over, netting back to the same cumulative left wheel hit at round 7) — the signature of one mistimed hand-alignment rather than a real deviation; excluding that pair, the scatter matches left's tight ±1 tick pattern.

**Conclusion**: `1560` ticks/rev is confirmed accurate on physical hardware for both wheels, with no systematic left/right discrepancy — the WHEELTEC vendor formula holds for this board.

## Steering Servo Driver (`servo.c`)

Drives the HWZ020 Ackermann steering servo on `PB15` (`TIM12_CH2`, 50Hz PWM). `SERVO_ANGLE_MAX_DEG` is a placeholder estimate — needs tuning against the actual mechanical steering-lock limit.

## Motor ON/OFF Switch (`motor.c`)

Reads the onboard motor ON/OFF switch (`SW3`) on `PD3` (input, pull-up) via `motor_estop_engaged()` — schematic: `3V3 -> R35 (100R) -> PD3`, `SW3` grounds it when switched OFF, so it idles HIGH (ON) and reads LOW when OFF; this matches the code's existing polarity assumption. **Gates both self-test** (refuses to run while OFF) **and the main drive loop** (`main.c` forces the wheels to 0% PWM whenever this reads OFF, regardless of what the host commands). Not yet cross-checked against a multimeter on the physical board, only confirmed functionally via self-test's refusal behavior.

## Battery Voltage ADC (`battery.c`)

Reads `PB0` (`ADC1_CH8`) via `HAL_ADC` polling (`battery_init`, `battery_read_voltage`). The sense-resistor divider ratio couldn't be confirmed from the schematic PDF text extraction (schematic is a visual layout, not linear text), so instead of guessing, the conversion formula (`raw / 4095.0 * 3.3 * 11.0`) was taken from WHEELTEC's own C30D basic-example firmware (`references/WHEELTEC/.../03-ADC采集电压值与电位器值/ADC.zip`), which targets this exact board line and channel (`Battery_Ch = 8`). Not yet cross-checked against a multimeter on this specific board.

## Not Yet Implemented

- **Ultrasonic Sensor (HC-SR04):** No driver exists.
- **IR Range Sensors (Sharp GP2Y0A21YK ×2):** No driver exists.
