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
| :--- | :--- | :--- | :--- | :--- |
| 1 (highest) | `TIM7` | 100 Hz, fixed period | Motor PID, encoder reads (`encoder_get_delta_a/b`), PWM output | `motor.c` |
| 2 | `USART3` (RX) | per-byte, ~115200 baud | Command packet framing (`g_last_command`) | `usart.c` |
| 3 | `TIM5` | on echo edge | Ultrasonic input capture (`PA3` echo pulse measurement) | `ultrasonic.c` |
| 3 (lowest) | `EXTI0` | on press | Button debounce flag | `button.c` |
| — (not an IRQ) | Main loop, fast tier | 100 Hz, tick-interval | Command/safety gating, IMU read, 2x IR (8x oversampled), ultrasonic distance read, telemetry TX (interrupt-driven) | `main.c` |
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
| Fast | **100 Hz** (`FAST_PERIOD_MS = 10`) | `imu_update()`, PID target/enable + safety gate, `servo_set_angle()`, 2x IR read (8x oversample), ultrasonic fetch, telemetry build+send | Matches the PID's own 100Hz sampling 1:1 |
| Slow | ~5 Hz (`SLOW_PERIOD_MS = 200`) | OLED render, debug `printf`, battery ADC polling, heartbeat LED | Human-facing / slow-changing; also the 2 slowest ops (bit-banged OLED, blocking printf) |
| Always | every raw loop pass | Button/self-test-request check, OLED page-advance check, `ultrasonic_update()` ping trigger | Cheap enough not to need tiering |

!!! note "Fast tier only works because telemetry TX is interrupt-driven"
    `uart_send_telemetry()` uses `HAL_UART_Transmit_IT`, not blocking. A blocking send of the
    80-byte packet would take ~6.9ms at 115200 baud — ~69% of the 10ms period on its own. Also
    drops worst-case motor-switch-off reaction time from ~100ms (pre-tiering) to ~20ms.

!!! tip "Why the PID stays at 100Hz, not higher"
    - Encoder: 1560 ticks/rev
    - Max speed (~5.5 rev/s) → 100Hz sample ≈ 85 ticks
    - Gets smaller/noisier at the low speeds used during careful arena maneuvering
    - Raising the rate trades real resolution for a bigger number, not better control

**Bandwidth** (`USART3` @ 115200 baud ≈ 11520 B/s):

- Telemetry: 80 B/packet @ 100Hz
- Commands: 16 B/packet @ ~50Hz
- TX telemetry: 8000 B/s (~69% of TX capacity); RX commands: 800 B/s (~7% of RX capacity). UART is full-duplex.
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
| `servo.c` internal | **microseconds** pulse width (840-2400 us operating; 600-2500 us calibration) | STM32, after conversion |

- **Motors**: `rad_s_to_pct()` converts target rad/s → PWM% via `pct = (rad_s / 34.56) × 100`
  (34.56 rad/s = the motor's rated max speed post-gearbox). That's the *feedforward* baseline — the
  PID's incremental correction is added on top, still in PWM% terms, before being written to the timer.
- **Servo**: a **real** wheel angle in rad → microseconds, by linear interpolation between measured
  endpoints with a **separate slope per side** (center `1490µs`; `+43.0°` → `850µs`, `−32.5°` →
  `2400µs`, re-measured 2026-09-18). No PWM% intermediate step, since there's no PID on the servo at all (open-loop, see
  [Steering Servo Driver](#steering-servo-driver-servoc)) - see
  [Servo Range & Steering Calibration](tuning.md#servo-range-steering-calibration).

### AT8236 Motor Driver (`motor.c`)

- Drives the 2 rear DC motors (`MG513P3012V`) via PWM on `TIM9`/`TIM10`/`TIM11` (`motor_init`,
  `motor_set_speed`).
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
- Ticks-per-rev: 1560 (below).

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

### Battery Voltage ADC (`battery.c`)

- `PB0` / `ADC1_CH8`, `HAL_ADC` polling (`battery_init`, `battery_read_voltage`).
- Formula: `raw / 4095.0 × 3.3 × 11.0` (12-bit ADC, 3.3V ref, 11x divider ratio).

### IR Distance Sensors (`ir.c`)

- Hardware: Two analog Sharp GP2Y0A21YK distance sensors wired to `PC2` (`ADC1_CH12`) and `PC1` (`ADC1_CH11`).
- Powered via the board's 5V rail; analog output (0.0 to 3.1V) is sampled by the 3.3V-tolerant 12-bit ADC (`HAL_ADC` polling with channel switching).
- Both channels share one ADC1 peripheral with `battery.c` — `ir_read_channel()` selects the channel, starts, polls for conversion, and stops again on every single read rather than leaving the ADC running continuously.
- Sampled in the 100 Hz fast main-loop tier with **8× hardware oversampling** per read (`IR_OVERSAMPLE = 8U`, taking ~0.3 ms per sensor). Immediate hardware averaging filters out electrical noise without the phase lag of an inter-frame moving average.
- **Decoupled distance curves**: The definitive raw $\to$ cm curves live on the Raspberry Pi (`mdp_ros config/bridges.yaml`), allowing sensor recalibration (`pixi run calib ir`) without reflashing the STM32. Telemetry streams raw ADC values (`ir_raw`, `ir2_raw`), while `ir_distance_cm` and `ir2_distance_cm` are set to `-1.0f`.
- **OLED readout**: OLED page 2 renders `cm` using local display copies of the calibrated curves (2026-10-14 parameters: $6.69 / (\text{raw}/4095)^{1.111}$ for IR1, $6.95 / (\text{raw}/4095)^{1.077}$ for IR2, clamped to 4–80 cm).

### Ultrasonic Distance Sensor (`ultrasonic.c`)

- Hardware: HC-SR04 ultrasonic rangefinder with `PA2` as TRIG (GPIO output) and `PA3` as ECHO (through a 5V-to-3V resistor divider into `TIM5_CH4`, AF2).
- Hardware input capture: `TIM5` captures both the rising edge (CH4) and falling edge (CH3) of the echo pulse at 1 MHz (1 µs resolution). Hardware timestamps remain microsecond-accurate even when higher-priority ISRs (`TIM7`) preempt the CPU.
- Measurement schedule: Triggered every 100 ms with a 30 ms echo timeout and 300 ms freshness limit. Stale or invalid readings return a negative value.
- Streamed in telemetry (`ultrasonic_cm`) and displayed on OLED page 2. Can be disabled at build time with `-D ULTRASONIC_ENABLED=0`.

---

## Pinouts & Peripherals

The board's pin allocations.

### Serial Communication (Host↔MCU Transport)

| Peripheral | Pins | Function | Default Baud |
| :--- | :--- | :--- | :--- |
| `USART3` | `PD8` (TX3), `PD9` (RX3) | Custom Binary Protocol Host Serial Link (Type-C USB Port 3 via CH9102F) - see [Serial Protocol](serial_protocol.md) | 115200 |
| `USART1` | `PA9` (TX1), `PA10` (RX1) | Alternate Serial Link (Type-C USB Port 1 via CH9102F) | 115200 |
| `USART2` | `PD5` (TX2), `PD6` (RX2) | Bluetooth / Wireless Module Interface | 9600 / 115200 |
| `CAN1` | `PD0` (RX), `PD1` (TX) | Onboard CAN Bus Transceiver (VP230) | — |

!!! note "Ackermann Drivetrain Layout"
    The MDP Ackermann course kit drives **2 Rear Wheels** (Motor A = Rear Left, Motor B = Rear Right) with Hall Encoders on TIM2 & TIM3. Front wheels are unpowered, free-rolling steering knuckles actuated by **1 HWZ020 Steering Servo** (`PB15` / TIM12_CH2).

### Motor PWM Outputs (AT8236 Driver)

| Motor Channel | Physical Drivetrain Location | PWM Pins | Hardware Timer | Status / Notes |
| :--- | :--- | :--- | :--- | :--- |
| **Motor A** | Rear Left (BL) | `PB8`, `PB9` | TIM10_CH1 (`PB8`), TIM11_CH1 (`PB9`) | **Active** (Rear-Left Drive Motor) |
| **Motor B** | Rear Right (BR) | `PE5`, `PE6` | TIM9_CH1 (`PE5`), TIM9_CH2 (`PE6`) | **Active** (Rear-Right Drive Motor) |
| **Motor C** | — | `PE9`, `PE11` | TIM1_CH1 (`PE9`), TIM1_CH2 (`PE11`) | *Unused* (Unpowered Front Wheels) |
| **Motor D** | — | `PE13`, `PE14` | TIM1_CH3 (`PE13`), TIM1_CH4 (`PE14`) | *Unused* (Unpowered Front Wheels) |
| **Motor ON/OFF Switch** | `SW3` (`BSI-10`) | `PD3` | Digital **Input**, pull-up | MCU reads (doesn't drive) the physical switch state — see [Motor ON/OFF Switch](#motor-onoff-switch-motorc) above |
| **Wheel-Speed PID Control Loop** | — (internal timer, no pin) | `TIM7` | Update-interrupt timer, no output | 100Hz fixed-period ISR (`motor_pid_init()`/`motor.c`) running the closed-loop wheel-speed PID that ultimately drives Motor A/B above - see [Closed-Loop Wheel Speed Control](tuning.md#closed-loop-wheel-speed-control) |

### Wheel Encoders (Hall Encoders)

| Encoder Channel | Location | Pins | Hardware Timer (Quadrature Mode) | Status / Notes |
| :--- | :--- | :--- | :--- | :--- |
| **Encoder A** | Rear Left (BL) | `PA15` (A), `PB3` (B) | TIM2_CH1 / CH2 | **Active** (Rear-Left Encoder) |
| **Encoder B** | Rear Right (BR) | `PB4` (A), `PB5` (B) | TIM3_CH1 / CH2 | **Active** (Rear-Right Encoder) |
| **Encoder C** | — | `PB6` (A), `PB7` (B) | TIM4_CH1 / CH2 | *Unused* |
| **Encoder D** | — | `PA0` (A), `PA1` (B) | TIM5_CH1 / CH2 | *Unused* |

### Steering Servo & Sensors

| Function | Pins | Peripheral | Signal / Details |
| :--- | :--- | :--- | :--- |
| **Steering Servo** | `PB15` | TIM12_CH2 | 50 Hz PWM (600–2500µs calibration bound, **840–2400µs** measured operating span, center **1490µs** — see [Command Units](#command-units) / [Servo Range & Steering Calibration](tuning.md#servo-range-steering-calibration)) |
| **IMU Sensor** | `PB10` (SCL), `PB11` (SDA) | Bit-banged software I2C (GPIO, `GPIO_MODE_OUTPUT_OD`) - **not** the hardware `I2C2` peripheral | ICM-20948 (9-DOF Gyro/Accel/Mag - only accel+gyro registers are read, magnetometer unused). Polled (`imu_update()`), not interrupt-driven - no `INT` pin connected in firmware |
| **IR distance sensors** | `PC2` (IR1), `PC1` (IR2) | ADC1_CH12, ADC1_CH11 | Two channels with 8× hardware oversampling; sampled at 100 Hz in fast tier, raw streamed in telemetry |
| **Ultrasonic Sensor** | `PA2` (TRIG), `PA3` (ECHO) | TIM5_CH4 / CH3 | HC-SR04 input capture (1 µs hardware resolution, 100 ms ping interval) |
| **Battery AD** | `PB0` | ADC1_CH8 | Voltage measurement via resistor divider |
| **Car Type Select** | `PB1` | ADC1_CH9 | Potentiometer voltage reading |
| **OLED Display** | `PD11`, `PD12`, `PD13`, `PD14` | GPIO | 0.96" OLED SPI Bit-banged display |
| **Status LED** | `PE8` | GPIO | Board status LED |
| **User Button** | `PE0` | GPIO | Onboard push button |
| **Buzzer** | `PA8` | GPIO | Onboard buzzer |
