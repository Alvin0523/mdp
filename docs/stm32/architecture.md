---
icon: lucide/layers
---

# Firmware Architecture (`mdp_stm32`)

Interrupt/timing design, driver internals, and pin allocations. For the high-level data-flow
picture and build/flash/tuning steps, see [Overview](index.md).

---

## Interrupt / Timing Architecture

- No RTOS — timing is hand-coordinated via **NVIC interrupt priorities** + shared **`volatile`**
  globals (no task scheduler, no mutexes; a shared global is the hand-off between an ISR and the
  main loop).
- Trade-off: nothing catches a priority mistake the way a scheduler would — already happened once
  (an NVIC mix-up let a button press theoretically preempt the motor safety loop; fixed, see table).

!!! tip "Rule"
    The actuator-driving loop always gets the **highest priority** in the system.

**Ownership table** (NVIC priority number — lower runs first / can preempt higher numbers):

| Priority | Interrupt | Rate | Owns | File |
| --- | --- | --- | --- | --- |
| 1 (highest) | `TIM7` | 100 Hz, fixed period | Motor PID, encoder reads (`encoder_get_delta_a/b`), PWM output | `motor.c` |
| 2 | `USART3` (RX) | per-byte, ~115200 baud | Command packet framing (`g_last_command`) | `usart.c` |
| 3 (lowest) | `EXTI0` | on press | Button debounce flag | `button.c` |
| — (not an IRQ) | Main loop, fast tier | 100 Hz, tick-interval | Command/safety gating, IMU read, telemetry TX (interrupt-driven) | `main.c` |
| — (not an IRQ) | Main loop, slow tier | ~5 Hz, tick-interval | OLED render, debug `printf`, battery ADC, heartbeat LED | `main.c` |

**Adding something new:**

- Actuator-driving / safety-relevant → lowest priority *number* (highest urgency).
- Best-effort / human-facing → highest priority number.
- Needs its own precise timing and has to coordinate with the motor loop/encoders (like
  `selftest.c` does via `motor_pid_pause()`/`resume()`) → that's the signal hand-rolled
  coordination is multiplying. Reconsider bare-metal at that point, not before.

### Main loop timing allocation

Two independent tick-interval tiers (`FAST_PERIOD_MS`/`SLOW_PERIOD_MS` in `main.c`, no blocking
delay). Previously one `HAL_Delay(100)`-paced 10Hz loop bottlenecked everything, including
IMU/encoder data to the Pi, even though the PID ISR already samples at 100Hz internally.

| Tier | Rate | What's in it | Why this rate |
| --- | --- | --- | --- |
| Fast | **100 Hz** (`FAST_PERIOD_MS = 10`) | `imu_update()`, PID target/enable + safety gate, `servo_set_angle()`, telemetry build+send | Matches the PID's own 100Hz sampling 1:1 |
| Slow | ~5 Hz (`SLOW_PERIOD_MS = 200`) | OLED render, debug `printf`, battery ADC, heartbeat LED | Human-facing / slow-changing; also the 2 slowest ops (bit-banged OLED, blocking printf) |
| Always | every raw loop pass | Button/self-test-request check, OLED page-advance check | Cheap enough not to need tiering |

!!! note "Fast tier only works because telemetry TX is interrupt-driven"
    `uart_send_telemetry()` uses `HAL_UART_Transmit_IT`, not blocking. A blocking send of the
    54-byte packet would take ~4.7ms at 115200 baud — ~47% of the 10ms period on its own. Also
    drops worst-case motor-switch-off reaction time from ~100ms (pre-tiering) to ~20ms.

!!! tip "Why the PID stays at 100Hz, not higher"
    - Encoder: 1560 ticks/rev
    - Max speed (~5.5 rev/s) → 100Hz sample ≈ 85 ticks
    - Gets smaller/noisier at the low speeds used during careful arena maneuvering
    - Raising the rate trades real resolution for a bigger number, not better control

**Bandwidth** (`USART3` @ 115200 baud ≈ 11520 B/s):

- Telemetry: 54 B/packet @ 100Hz
- Commands: 16 B/packet @ ~50Hz
- Combined ≈ 6200 B/s (~54% of capacity) — margin remains
- `USART1` (debug printf) is separate hardware, doesn't compete

Battery voltage / measured rad/s are cached in persistent locals — the slow tier displays whatever
the fast/slow tier last read, without either reading a resource it doesn't own.

---

## Drivers

### Command Units

Units don't change crossing the Pi↔STM32 wire — rad/s stays rad/s, rad stays rad, all the way from
`ackermann_steering_controller` to the STM32's command-handling code. Conversion only happens right
before hardware is actually driven, one step per driver:

| Stage | Unit | Where |
| --- | --- | --- |
| `ackermann_steering_controller` output | **rad/s** per wheel, **rad** for steering | Pi, `/joint_commands` |
| `CommandPacket` (wire, `USART3`) | **rad/s** (`left_wheel_rad_s`/`right_wheel_rad_s`), **rad** (`steer_rad`) | crosses Pi↔STM32 |
| `motor.c` internal | **PWM %** (0–100, signed for direction) | STM32, after conversion |
| Motor hardware PWM output | raw timer compare-register count (duty cycle) | STM32, electrical signal |
| `servo.c` internal | **microseconds** pulse width (600–2400µs) | STM32, after conversion |

- **Motors**: `rad_s_to_pct()` converts target rad/s → PWM% via `pct = (rad_s / 34.56) × 100`
  (34.56 rad/s = the motor's rated max speed post-gearbox). That's the *feedforward* baseline — the
  PID's incremental correction is added on top, still in PWM% terms, before being written to the timer.
- **Servo**: `SERVO_ANGLE_SCALE_RAD` converts commanded rad → microseconds directly (0.7854 rad / 45°
  maps to 900µs off the 1500µs center) — no PWM% intermediate step, since there's no PID on the servo
  at all (open-loop, see [Steering Servo Driver](#steering-servo-driver-servoc)).

### AT8236 Motor Driver (`motor.c`)

- Drives the 2 rear DC motors (`MG513P3012V`) via PWM on `TIM9`/`TIM10`/`TIM11` (`motor_init`,
  `motor_set_speed`). Pin/timer assignments verified against the WHEELTEC C30D resource-allocation
  PDF.
- Closed-loop: `main.c` calls `motor_pid_set_target()`/`motor_pid_enable()`, not
  `motor_set_speed_rad_s()` directly. A 100Hz `TIM7` ISR (`motor_pid_*`) reads the encoders, runs a
  feedforward + incremental-PI controller, writes the corrected PWM via `motor_set_speed()`.
- `motor_set_speed_rad_s()` still exists standalone (open-loop primitive), just not the live path.
- PID gains: untuned placeholders, not bench-tested on hardware.
- **PI only, no D term** — deliberate, not an oversight. The measured signal (rad/s from tick deltas)
  is inherently noisy at low speed (few ticks per 10ms window), and a D term differentiates that noise
  further. Incremental-PI already rate-limits output changes structurally, covering most of what D
  would otherwise buy. Reconsider only if bench-tuning shows P+I alone can't kill overshoot without
  oscillating.

!!! warning "Must use locked-antiphase drive, not a naive 0%/duty% scheme"
    `motor_drive()` holds both `IN1`/`IN2` at ~100% duty (electrical brake) at rest, pulling one side
    down by the commanded speed magnitude to produce direction. A simpler "stopped side = 0%, driven
    side = duty%" scheme left the wheels **completely dead** despite correct-looking PWM and zero
    errors — the AT8236 needs continuous switching on both legs to keep its gate-drive/charge-pump
    alive. Debugging "motors don't spin but everything looks right"? Check this first.

### Hall Encoder Driver (`encoder.c`)

- Reads Hall ticks on the 2 rear motors via hardware timer encoder mode (`TIM2`/`TIM3`).
- `encoder_get_delta_a`/`_b` — per-call delta, resets counter (tick-rate speed estimate).
- `encoder_get_count_a`/`_b` — running cumulative count.
- Ticks-per-rev: confirmed (below). Ticks→distance: not yet — wheel diameter/rolling radius unmeasured.

!!! note "Motor B (rear right) counts are negated"
    Motor B is mounted mirrored to Motor A, so its raw Hall phase order comes out reversed relative
    to "forward." `encoder_get_delta_b`/`_count_b` negate the raw count so both motors honor
    "positive = forward" — without this, the rear-right encoder read negative while driving forward.

#### Ticks-per-revolution — physically confirmed on hardware

- Formula: `EncoderMultiples(4) × Hall_13(13 pulses/motor-rev) × HALL_30F(30:1 gear ratio) = 1560`
- Method: hand-turned each wheel 10 full rotations (car on blocks, motor switch OFF), reading
  cumulative ticks off OLED page 3 after each turn.
- Result: both wheels averaged exactly `1560.0` ticks/rev, ±1 tick spread (hand-alignment noise) —
  confirmed, no left/right discrepancy.

### Steering Servo Driver (`servo.c`)

Drives the HWZ020 Ackermann steering servo on `PB15` (`TIM12_CH2`, 50Hz PWM). Operating angle limits
(`SERVO_ANGLE_MAX_LEFT/RIGHT_RAD`) are calibrated against the actual mechanical steering lock — see
[Servo Range & Steering Calibration](tuning.md#servo-range-steering-calibration).

### Motor ON/OFF Switch (`motor.c`)

- Pin: `PD3`, input, pull-up. Schematic: `3V3 → R35 (100R) → PD3`; `SW3` grounds it when OFF.
- Idles HIGH (ON), reads LOW when OFF — read via `motor_estop_engaged()`.
- Gates self-test (refuses while OFF) and the main drive loop (`main.c` forces 0% PWM regardless of
  host commands while OFF).
- Not yet cross-checked with a multimeter — only confirmed functionally (self-test refusal).

### Battery Voltage ADC (`battery.c`)

- `PB0` / `ADC1_CH8`, `HAL_ADC` polling (`battery_init`, `battery_read_voltage`).
- Formula: `raw / 4095.0 × 3.3 × 11.0` (12-bit ADC, 3.3V ref, 11x divider ratio).
- Divider ratio sourced from WHEELTEC's C30D ADC example firmware (`Battery_Ch = 8`) — couldn't be
  confirmed from the schematic PDF (visual layout, not extractable text).
- Not yet cross-checked with a multimeter on this board.

---

## Pinouts & Peripherals

Verified pin allocations for the WHEELTEC C30D (Revisions 2.0 / 2.1 / 2.2), per the vendor
schematics (`references/`).

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
| **Motor ON/OFF Switch** | `SW3` (`BSI-10`) | `PD3` | Digital **Input**, pull-up | MCU reads (doesn't drive) the physical switch state — see [Motor ON/OFF Switch](#motor-onoff-switch-motorc) above |
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
| **Steering Servo** | `PB15` | TIM12_CH2 | 50 Hz PWM (600–2400µs configured range, ~960–2320µs actual operating span — see [Command Units](#command-units) / [Servo Range & Steering Calibration](tuning.md#servo-range-steering-calibration)) |
| **IMU Sensor** | `PB10` (SCL), `PB11` (SDA) | Bit-banged software I2C (GPIO, `GPIO_MODE_OUTPUT_OD`) - **not** the hardware `I2C2` peripheral | ICM-20948 (9-DOF Gyro/Accel/Mag - only accel+gyro registers are read, magnetometer unused). Polled (`imu_update()`), not interrupt-driven - no `INT` pin connected in firmware |
| **Battery AD** | `PB0` | ADC1_CH8 | Voltage measurement via resistor divider |
| **Car Type Select** | `PB1` | ADC1_CH9 | Potentiometer voltage reading |
| **OLED Display** | `PD11`, `PD12`, `PD13`, `PD14` | GPIO | 0.96" OLED SPI Bit-banged display |
| **Status LED** | `PE8` | GPIO | Board status LED |
| **User Button** | `PE0` | GPIO | Onboard push button |
| **Buzzer** | `PA8` | GPIO | Onboard buzzer |
