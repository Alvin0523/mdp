---
icon: lucide/layers
---

# Firmware Architecture (`mdp_stm32`)

Interrupt/timing design, driver internals, and pin allocations. For the high-level data-flow
picture and build/flash/tuning steps, see [Overview](index.md).

---

## Interrupt / Timing Architecture

`mdp_stm32` coordinates several independent periodic/event-driven things by hand (NVIC priority +
shared `volatile` globals) instead of an RTOS scheduler + task priorities — same *shape* of problem
WHEELTEC solves with FreeRTOS tasks (see below), just without a scheduler enforcing correctness for
you. The decision to stay bare-metal (bring-up speed, vendor-HAL compatibility, course timeline)
still holds — this isn't a recommendation to migrate. It IS a place mistakes are easy: an NVIC
priority mix-up here already let a button press theoretically preempt the motor safety loop (found
and fixed below).

**Current ownership table** (NVIC priority number — lower runs first / can preempt higher numbers):

| Priority | Interrupt | Rate | Owns | File |
| --- | --- | --- | --- | --- |
| 1 (highest) | `TIM7` | 100 Hz, fixed period | Motor PID, encoder reads (`encoder_get_delta_a/b`), PWM output | `motor.c` |
| 2 | `USART3` (RX) | per-byte, ~115200 baud | Command packet framing (`g_last_command`) | `usart.c` |
| 3 (lowest) | `EXTI0` | on press | Button debounce flag | `button.c` |
| — (not an IRQ) | Main loop, fast tier | 100 Hz, tick-interval | Command/safety gating, IMU read, telemetry TX (interrupt-driven) | `main.c` |
| — (not an IRQ) | Main loop, slow tier | ~5 Hz, tick-interval | OLED render, debug `printf`, battery ADC, heartbeat LED | `main.c` |

**Rule for anything new**: actuator-driving/safety-relevant work gets the lowest priority *number*
(highest urgency); best-effort/human-facing work gets the highest number. If a new peripheral needs
its own precise timing (the ultrasonic/IR sensors on the roadmap are the obvious next candidate) and
it has to coordinate with the motor loop or encoders the way `selftest.c` now has to
(`motor_pid_pause()`/`resume()`), that's the point where hand-rolled coordination starts
multiplying — treat that as the signal to reconsider, not any point before it.

### Main loop timing allocation

The main loop used to bundle everything (IMU read, telemetry TX, OLED render, debug print, battery
ADC) into one `HAL_Delay(100)`-paced 10Hz pass — meaning IMU/encoder data reaching the Pi was
throttled to 10Hz even though the PID ISR samples encoders at 100Hz internally. Split into two
independent tick-interval tiers (`FAST_PERIOD_MS`/`SLOW_PERIOD_MS` in `main.c`, no blocking delay,
no RTOS):

| Tier | Rate | What's in it | Why this rate |
| --- | --- | --- | --- |
| Fast | **100 Hz** (`FAST_PERIOD_MS = 10`) | `imu_update()`, PID target/enable + safety gate, `servo_set_angle()`, telemetry build+send | Matches the motor PID's own 100Hz encoder sampling 1:1 - no information the PID measures gets throttled down before reaching the Pi. Only viable at this rate because `uart_send_telemetry()` is interrupt-driven (`HAL_UART_Transmit_IT`, `usart.c`) rather than blocking - a blocking transmit of the 54-byte telemetry packet takes ~4.7ms at 115200 baud, which would eat ~47% of a 10ms period on its own. Also shrinks worst-case motor-switch-off reaction time from ~100ms (pre-tiering) to ~20ms |
| Slow | ~5 Hz (`SLOW_PERIOD_MS = 200`) | OLED render, debug `printf`, battery ADC, heartbeat LED toggle | All either human-facing (no benefit refreshing faster than the eye uses) or slow-changing (battery voltage drifts over seconds/minutes) - also the two slowest operations (bit-banged OLED, blocking USART1 printf) |
| Always | every raw loop pass | Button/self-test-request check, OLED auto-page-advance check | Cheap enough not to need tiering |

**Why the motor PID itself stays at 100Hz** (i.e. why "maximize everything" doesn't mean raising
every rate): the encoder is 1560 ticks/rev, so at max speed (~5.5 rev/s) a 100Hz sample already only
covers ~85 ticks, and that number only gets *smaller* — and the measured velocity noisier — at the
low speeds this robot actually spends most of its time at during careful arena maneuvering. Raising
the PID rate further would trade real velocity-estimate resolution for a bigger number, not improve
control quality.

**Bandwidth check at 100Hz** (`USART3`, 115200 baud ≈ 11520 B/s): telemetry (54 bytes/packet) +
commands (16 bytes/packet, ~50Hz from the host) ≈ 6200 B/s combined, about 54% of capacity - real
margin remains. `USART1` (debug `printf`) is a physically separate UART peripheral and doesn't
compete for this bandwidth at all.

Battery voltage and measured wheel rad/s are cached in persistent locals so the slow tier can display
whatever the fast tier (rad/s) or the slow tier itself (battery) last read, without either tier
reading a resource it doesn't own out of turn.

### WHEELTEC's actual reference: FreeRTOS, task-based, not ad-hoc ISRs

Extracted directly from their firmware zip (not the marketing docs) - `BALANCE/balance.c`'s
`Balance_task` is a genuine periodic FreeRTOS task (`vTaskDelayUntil`, not an ISR) at the *highest*
priority in their whole system:

| Task | Rate | Priority | Role |
| --- | --- | --- | --- |
| `Balance_task` | 100 Hz (`vTaskDelayUntil`) | **4 (highest)** | Velocity PI + `Set_Pwm` - the motor control loop |
| `Check_task` | 100 Hz | 4 | Safety/battery monitoring |
| `ICM20948_task` | 100 Hz | 3 | IMU read |
| `show_task` | 10 Hz | 3 | OLED display |
| `pstwo_task` / `data_task` | - | 4 | Gamepad / serial I/O |
| `start_task` | once, deletes itself | 1 (lowest) | Bootstrap - spawns every other task |

The pattern worth taking away even without adopting RTOS: **the actuator-driving loop gets the
highest priority in the whole system**, `vTaskDelayUntil` gives it a jitter-free fixed period
regardless of what else runs, and everything lower-priority (display, telemetry) genuinely cannot
starve it - the scheduler enforces that structurally. This project's NVIC-priority table above is
the bare-metal approximation of the same idea, manually maintained instead of scheduler-enforced.

---

## Drivers

### AT8236 Motor Driver (`motor.c`)

Drives the 2 rear DC motors (`MG513P3012V`) via PWM on `TIM9`/`TIM10`/`TIM11` (`motor_init`, `motor_set_speed`). Pin/timer assignments verified against the WHEELTEC C30D resource-allocation PDF and ported from the vendor's Hall-encoder reference firmware.

Wheel speed is **closed-loop** as of the closed-loop PID work above: the main loop no longer calls `motor_set_speed_rad_s()` directly to drive - it calls `motor_pid_set_target()`/`motor_pid_enable()`, and a 100Hz `TIM7` ISR (`motor_pid_*` functions) reads the encoders, runs a hybrid feedforward+incremental-PI controller (`rad_s_to_pct()` supplies the feedforward baseline, same formula as before), and writes the corrected PWM via `motor_set_speed()`. `motor_set_speed_rad_s()` itself is unchanged and still available as a standalone open-loop primitive, just no longer the live control path from `main.c`. **PID gains are untuned placeholders** - not yet bench-tested on hardware.

!!! warning "Must use locked-antiphase drive, not a naive 0%/duty% scheme"
    `motor_drive()` uses a **locked-antiphase** scheme, ported from WHEELTEC's own vendor reference firmware (`BALANCE/balance.c`'s `Set_Pwm`): both `IN1`/`IN2` sit at ~100% duty (electrical brake) at rest, and one side is pulled down from 100% by the commanded speed magnitude to produce a direction. A simpler-looking "stopped side = 0%, driven side = duty%" scheme was tried first and left the wheels **completely dead despite correct-looking PWM commands and zero errors** — the AT8236 needs continuous switching activity on both legs to keep its internal high-side gate drive/charge-pump alive, and a leg held statically low never actually turns the output on. If you're debugging "motors don't spin but everything else looks right," check this first before suspecting wiring or power.

### Hall Encoder Driver (`encoder.c`)

Reads Hall encoder ticks on the 2 rear drive motors using STM32 hardware timer encoder mode (`TIM2`/`TIM3`). Exposes both a per-call delta read (`encoder_get_delta_a`/`_b`, resets the hardware counter each call — used for a tick-rate speed estimate) and a running cumulative count (`encoder_get_count_a`/`_b`). Ticks-per-revolution is physically confirmed (below); ticks-to-real-distance is not — wheel diameter/effective rolling radius hasn't been independently measured on this board, so `encoder_get_count_a/_b` gives an accurate tick count but not yet an accurate meters value.

!!! note "Motor B (rear right) counts are negated to match Motor A's sign convention"
    Motor B is mounted as a mirror image of Motor A, so its raw Hall A/B phase order comes out reversed relative to "forward" even though `motor_set_speed()` drives both sides with the same positive=forward PWM convention. `encoder_get_delta_b`/`encoder_get_count_b` negate the raw timer count before returning it, so both motors honor the "positive = forward" contract documented in `encoder.h` — without this, the rear-right encoder read negative while driving forward (visible on the OLED's `Enc R` field and in `/joint_states` telemetry).

#### Ticks-per-revolution — physically confirmed on hardware

The `1560` ticks/rev figure used throughout (`selftest.c`'s drive-1-revolution test, `mdp_bridge`'s `kRadPerTick` conversion) comes from WHEELTEC's vendor formula for this exact motor/encoder combo: `EncoderMultiples(4) × Hall_13(13 pulses/motor-rev) × HALL_30F(30:1 gear ratio) = 1560`. This had never been independently checked on this specific board until now.

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

### Steering Servo Driver (`servo.c`)

Drives the HWZ020 Ackermann steering servo on `PB15` (`TIM12_CH2`, 50Hz PWM). Operating angle limits (`SERVO_ANGLE_MAX_LEFT/RIGHT_RAD`) are calibrated against the actual mechanical steering lock — see [Servo Range & Steering Calibration](tuning.md#servo-range-steering-calibration) above.

### Motor ON/OFF Switch (`motor.c`)

Reads the onboard motor ON/OFF switch (`SW3`) on `PD3` (input, pull-up) via `motor_estop_engaged()` — schematic: `3V3 -> R35 (100R) -> PD3`, `SW3` grounds it when switched OFF, so it idles HIGH (ON) and reads LOW when OFF; this matches the code's existing polarity assumption. **Gates both self-test** (refuses to run while OFF) **and the main drive loop** (`main.c` forces the wheels to 0% PWM whenever this reads OFF, regardless of what the host commands). Not yet cross-checked against a multimeter on the physical board, only confirmed functionally via self-test's refusal behavior.

### Battery Voltage ADC (`battery.c`)

Reads `PB0` (`ADC1_CH8`) via `HAL_ADC` polling (`battery_init`, `battery_read_voltage`). The sense-resistor divider ratio couldn't be confirmed from the schematic PDF text extraction (schematic is a visual layout, not linear text), so instead of guessing, the conversion formula (`raw / 4095.0 * 3.3 * 11.0`) was taken from WHEELTEC's own C30D basic-example firmware (`references/WHEELTEC/.../03-ADC采集电压值与电位器值/ADC.zip`), which targets this exact board line and channel (`Battery_Ch = 8`). Not yet cross-checked against a multimeter on this specific board.

---

## Pinouts & Peripherals

Verified pin allocations and hardware peripheral assignments for the WHEELTEC C30D (Revisions 2.0 / 2.1 / 2.2) control board based on official vendor schematics (`references/`).

### Serial Communication (Host↔MCU Transport)

| Peripheral | Pins | Function | Default Baud |
| --- | --- | --- | --- |
| `USART3` | `PD8` (TX3), `PD9` (RX3) | Custom Binary Protocol Host Serial Link (Type-C USB Port 3 via CH9102F) - see [Serial Protocol](serial_protocol.md#serial-protocol) | 115200 |
| `USART1` | `PA9` (TX1), `PA10` (RX1) | Alternate Serial Link (Type-C USB Port 1 via CH9102F) | 115200 |
| `USART2` | `PD5` (TX2), `PD6` (RX2) | Bluetooth / Wireless Module Interface | 9600 / 115200 |
| `CAN1` | `PD0` (RX), `PD1` (TX) | Onboard CAN Bus Transceiver (VP230) | — |

!!! note "Ackermann Drivetrain Layout"
    The MDP Ackermann course kit drives **2 Rear Wheels** (Motor A = Rear Left, Motor B = Rear Right) with Hall Encoders on TIM2 & TIM3. Front wheels are unpowered, free-rolling steering knuckles actuated by **1 HWZ020 Steering Servo** (`PB15` / TIM12_CH2).

### Motor PWM Outputs (AT8236 Driver)

| Motor Channel | Physical Drivetrain Location | PWM Pins | Hardware Timer | Status / Notes |
| --- | --- | --- | --- | --- |
| **Motor A** | Rear Left (BL) | `PB8`, `PB9` | TIM10_CH1 (`PB8`), TIM11_CH1 (`PB9`) | **Active** (Rear-Left Drive Motor) |
| **Motor B** | Rear Right (BR) | `PE5`, `PE6` | TIM9_CH1 (`PE5`), TIM9_CH2 (`PE6`) | **Active** (Rear-Right Drive Motor) |
| **Motor C** | — | `PE9`, `PE11` | TIM1_CH1 (`PE9`), TIM1_CH2 (`PE11`) | *Unused* (Unpowered Front Wheels) |
| **Motor D** | — | `PE13`, `PE14` | TIM1_CH3 (`PE13`), TIM1_CH4 (`PE14`) | *Unused* (Unpowered Front Wheels) |
| **Motor ON/OFF Switch** | `SW3` (`BSI-10`) | `PD3` | Digital **Input**, pull-up | `3V3 -> R35 (100R) -> PD3`, `SW3` grounds it when switched OFF - idles HIGH (motor ON/ready), reads LOW when switched OFF. **MCU reads this, does not drive it** - it's the physical switch's state coming in, not an MCU-controlled enable output. Read via `motor_estop_engaged()` (`motor.c`); gates both self-test and the main drive loop (`main.c`) - motors are forced to 0% PWM whenever this reads OFF, regardless of host commands |
| **Wheel-Speed PID Control Loop** | — (internal timer, no pin) | `TIM7` | Update-interrupt timer, no output | 100Hz fixed-period ISR (`motor_pid_init()`/`motor.c`) running the closed-loop wheel-speed PID that ultimately drives Motor A/B above - see [Closed-Loop Wheel Speed Control](tuning.md#closed-loop-wheel-speed-control) |

### Wheel Encoders (Hall Encoders)

| Encoder Channel | Location | Pins | Hardware Timer (Quadrature Mode) | Status / Notes |
| --- | --- | --- | --- | --- |
| **Encoder A** | Rear Left (BL) | `PA15` (A), `PB3` (B) | TIM2_CH1 / CH2 | **Active** (Rear-Left Encoder) |
| **Encoder B** | Rear Right (BR) | `PB4` (A), `PB5` (B) | TIM3_CH1 / CH2 | **Active** (Rear-Right Encoder) |
| **Encoder C** | — | `PB6` (A), `PB7` (B) | TIM4_CH1 / CH2 | *Unused* |
| **Encoder D** | — | `PA0` (A), `PA1` (B) | TIM5_CH1 / CH2 | *Unused* |

### Steering Servo & Sensors

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
