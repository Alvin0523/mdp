---
icon: lucide/cpu
---

# STM32 (`mdp_stm32`)

PlatformIO/STM32Cube HAL firmware for the WHEELTEC C30D V2.1 board (STM32F407VET6 MCU) — one
physical device, one submodule. Pure hardware I/O controller — all kinematics live on the RPi host.
It receives wheel speed and steering commands over a fixed-size binary serial protocol (USART3 @
115200 baud on `PD8`/`PD9`) and publishes raw encoder telemetry back to the Raspberry Pi 4B host.

## STM32 System Overview

Expanding the "STM32" box from the [System Overview](../index.md) diagram — the driver modules
behind the serial link, grouped by what they do:

```mermaid
graph TD
  subgraph ACTUATION["Actuation"]
    MOTOR["motor.c<br/>Velocity PID + PWM<br/>(AT8236 driver)"]
    SERVO["servo.c<br/>Steering PWM<br/>(HWZ020 servo)"]
  end

  subgraph SENSING["Sensing"]
    ENC["encoder.c<br/>Hall encoders"]
    IMUD["imu.c<br/>ICM-20948"]
    BATT["battery.c<br/>Battery ADC"]
  end

  subgraph SAFETY["Safety & UI"]
    BTN["button.c<br/>User button +<br/>motor ON/OFF switch"]
    OLED["oled.c<br/>Status display"]
  end

  subgraph COMM["usart.c / protocol.c"]
    RX["CommandPacket RX"]
    TX["TelemetryPacket TX"]
  end

  BRIDGE(("mdp_bridge<br/>(Pi)"))

  BRIDGE -.->|"Serial UART<br/>USART3, CommandPacket"| RX
  RX --> MOTOR
  RX --> SERVO
  BTN -->|"gates drive loop<br/>(motor OFF = 0% PWM)"| MOTOR
  BTN --> OLED

  ENC -.->|"encoder ticks<br/>(speed feedback)"| MOTOR
  ENC --> TX
  IMUD --> TX
  BATT --> TX
  TX -.->|"Serial UART<br/>USART3, TelemetryPacket"| BRIDGE
```
<p align="center"><strong>Fig. 1</strong> — STM32 System Overview</p>

## Architecture & Design Decisions

- **No RTOS** — bare-metal HAL. Timing is hand-coordinated via NVIC interrupt priorities + shared
  `volatile` globals, not scheduler tasks.
- **Priority rule**: the actuator-driving loop (motor PID, `TIM7`) always gets the highest priority
  in the system.
- **Two main-loop rate tiers**: fast (100Hz — command/safety/IMU/telemetry) and slow (~5Hz —
  OLED/battery/debug), not one shared 10Hz loop.
- **Closed-loop wheel-speed PID runs on the STM32**, not the Pi/EKF — only place with low-latency
  access to both PWM timers and encoder counters in the same cycle.
- **Custom binary protocol over `USART3`** — see [Serial Protocol](serial_protocol.md).

See [Firmware Architecture](architecture.md) for the full reasoning/implementation, and
[Control Tuning & Calibration](tuning.md) for PID/servo control theory.

## Folder & File Hierarchy

```text
mdp_stm32/
├── platformio.ini            # Board/toolchain config, build flags
├── include/                  # Headers - one per driver
│   ├── motor.h, servo.h, encoder.h, imu.h, battery.h, button.h, oled.h
│   ├── usart.h, protocol.h   # Serial link to mdp_ros
│   └── selftest.h
└── src/                      # Implementation - one .c per driver, matches include/
    ├── main.c                # Boot sequence + main loop (fast/slow rate tiers)
    ├── motor.c, servo.c, encoder.c, imu.c, battery.c, button.c, oled.c
    ├── usart.c                # Binary protocol framing/TX/RX
    └── selftest.c             # Scripted drive/steer self-test
```

## Learn More

| Topic | What it covers |
| --- | --- |
| [**Firmware Architecture**](architecture.md) | Interrupt/timing design, driver internals, pin allocations. |
| [**Control Tuning & Calibration**](tuning.md) | Closed-loop wheel-speed PID theory, servo range/steering calibration, IMU → EKF data flow. |
| [**Serial Protocol**](serial_protocol.md) | Binary framing, telemetry/command packet layout, stale-link fail-safe. |

---

## Overview: motor, servo & telemetry flow

Per-signal detail behind Fig. 1's boxes — 2 driven wheels + 1 steering servo, with the servo path's
cubic correction stage marked as planned (see
[Servo Range & Steering Calibration](tuning.md#servo-range-steering-calibration)) since it isn't
implemented yet. Telemetry packs in IMU, battery voltage, and the motor switch state alongside
encoder feedback every 100Hz tick:

```mermaid
flowchart LR
    CMD["/cmd_vel<br/>(linear.x, angular.z)"] --> KIN["ackermann_steering_controller<br/>kinematics (Pi)"]

    KIN -->|"left/right wheel<br/>target rad/s"| MPID
    KIN -->|"steer_rad<br/>(target road wheel angle)"| SCUBIC

    subgraph MOTOR["Motor control - per wheel (mdp_stm32)"]
        direction LR
        MPID["Velocity PID<br/>(motor_pid_*, TIM7 100Hz ISR)"] --> MPWM["PWM<br/>(motor_set_speed)"]
        MPWM --> MDRV["AT8236<br/>H-bridge driver"]
        MDRV --> MVOLT["Voltage"]
        MVOLT --> MMOTOR["DC motor<br/>+ Hall encoder"]
        MMOTOR -.->|"encoder ticks<br/>(speed feedback)"| MPID
    end

    subgraph SERVO["Steering control (mdp_stm32)"]
        direction LR
        SCUBIC["Cubic correction<br/>(PLANNED - not yet<br/>implemented, see below)"] --> SPWM["PWM<br/>(servo_set_angle)"]
        SPWM --> SHW["HWZ020 servo"]
        SHW --> SLINK["Tie-rod linkage<br/>(mechanical, asymmetric)"]
        SLINK --> SANGLE["Front wheel<br/>steering angle"]
    end

    subgraph TELEM["Telemetry - packed every 100Hz (mdp_stm32)"]
        direction LR
        IMUSENS["ICM-20948 IMU<br/>(imu_update)"] --> TPKT
        BATT["Battery ADC<br/>(battery_read_voltage)"] --> TPKT
        SW["Motor ON/OFF switch<br/>(motor_estop_engaged)"] --> TPKT
        TPKT["TelemetryPacket<br/>(usart_send_telemetry)"]
    end

    MMOTOR -.->|"encoder ticks"| TPKT
    TPKT -->|"Serial UART<br/>USART3, 115200 baud"| HOST(("mdp_bridge<br/>(Pi)"))
```
<p align="center"><strong>Fig. 2</strong> — Motor, Servo & Telemetry Flow</p>

!!! note "Ackermann-only: 2 motors, 1 servo"
    This project only uses motors A/B + one steering servo — no C/D motors or omni/mecanum paths.

!!! warning "PID not yet bench-tested — see [TODO](#todo)"
    Untuned placeholder gains, never run on hardware. Test/tune this before building anything else
    on top of it — see [Bench-Tuning the Motor PID](#bench-tuning-the-motor-pid) below.

---

## TODO

- [x] Bringup (LED/printf) — verified on hardware
- [x] AT8236 motor PWM — verified on hardware; locked-antiphase drive required, see [AT8236 Motor Driver](architecture.md#at8236-motor-driver-motorc)
- [x] HWZ020 steering servo — verified on hardware; calibrated 1° resolution: right 41°, left 27° (commanded unit), see [Servo Range & Steering Calibration](tuning.md#servo-range-steering-calibration)
- [x] URDF steering limits — updated to `±0.5672 rad` (32.5°), replaces datasheet spec, see [tuning.md](tuning.md#resolved-urdf-steering-limit-now-matches-the-real-measured-steering-angle); not yet re-validated on hardware
- [ ] Closed-loop wheel-speed PID — implemented, **not bench-tuned or hardware-tested**. `MOTOR_PID_KP=4.0f`/`KI=0.5f` are untuned placeholders. **Next priority** — see [Bench-Tuning the Motor PID](#bench-tuning-the-motor-pid)
- [x] `PD3` motor switch gating — implemented, functionally confirmed; polarity not yet cross-checked with a multimeter
- [x] NVIC interrupt priorities — verified on hardware; motor PID (`TIM7`) now highest-priority, see [Interrupt / Timing Architecture](architecture.md#interrupt-timing-architecture)
- [ ] Main loop rate tiers (100Hz/5Hz) — implemented, not yet hardware-tested, see [Main loop timing allocation](architecture.md#main-loop-timing-allocation)
- [x] Hall encoder driver + ticks/rev — verified on hardware; 1560 ticks/rev confirmed both wheels, see [Ticks-per-revolution](architecture.md#ticks-per-revolution-physically-confirmed-on-hardware). Wheel diameter/rolling radius for ticks→distance still unconfirmed
- [x] ICM-20948 IMU driver — implemented
- [x] Serial protocol + `mdp_bridge` — full round-trip verified on hardware (`pixi run real` + `pixi run teleop`), see [Serial Protocol](serial_protocol.md#serial-protocol)
- [x] Battery voltage ADC — implemented; divider ratio (11x) from vendor firmware, not cross-checked with a multimeter
- [x] Automated self-test (`selftest.c`) — verified on hardware
- [ ] Ultrasonic (HC-SR04) driver — not started
- [ ] IR distance sensors (Sharp GP2Y0A21YK ×2) driver — not started

---

See [Quickstart](../quickstart.md) for every `pixi run` command (setup, flash, monitor, teleop) —
this page stays theory-only from here on.

## Bench-Tuning the Motor PID

Do this before anything else builds on top of the wheel PID — see
[Control Tuning: Closed-Loop Wheel Speed Control](tuning.md#closed-loop-wheel-speed-control) for why
it's designed this way.

1. Flash (`pixi run flash`), get the robot up on blocks so the wheels spin free.
2. Send a step target via ROS (`ros2 topic pub /joint_commands ...` with a fixed `velocity` for
   `lb_joint`/`rb_joint`) or a temporary direct test in firmware — watch actual wheel behavior.
3. Start from `MOTOR_PID_KI = 0` (pure P + feedforward). Raise `MOTOR_PID_KP` until the wheel
   tracks a step target quickly with acceptable overshoot (a little overshoot then settle is fine;
   visible oscillation/buzzing means back off).
4. Reintroduce `MOTOR_PID_KI` in small steps, just enough to kill any remaining steady-state error
   (commanded rad/s vs. `motor_pid_get_measured_rad_s()`'s reading not converging) — too much causes
   slow oscillation/overshoot growing over time.
5. Repeat for both wheels — since they're never perfectly matched, it's plausible (though not
   certain) the same gains work fine for both; watch for one wheel behaving worse than the other,
   which would call for asymmetric tuning instead of a shared `MOTOR_PID_KP`/`MOTOR_PID_KI`.
6. Once tuned on blocks, re-verify on the ground with the actual chassis weight/friction before
   trusting it for real runs — this is a meaningfully different load than free-spinning wheels.

If, after tuning, the car still curves during a straight `/cmd_vel` command, see
[Control Tuning: Wheel-speed trim](tuning.md#wheel-speed-trim).

---

## Verification Checklist (post-flash bring-up)

!!! note "`printf` debug text and the binary protocol share `USART3`"
    Since the protocol went in, `pixi run monitor` shows readable boot-banner/telemetry-line text interleaved with raw binary garbage from the packet stream — expected, not a bug. The framed protocol resyncs on sync bytes and rejects bad checksums regardless.

**No host needed:**

1. `pixi run flash` then `pixi run monitor` — confirm the boot banner prints (amid binary noise), PE8 LED blinks, OLED cycles pages via the button.
2. **Encoders:** spin a rear wheel by hand, watch OLED page 3 (`Enc L`/`Enc R`) — counts should change and sign should flip with direction.
3. **Motor switch (`PD3`):** toggle it, watch OLED page 3's `ESTOP` field flip READY/ENGAGED. Polarity is an *assumption*, not yet physically verified — if it reads backwards, flip the comparison in `motor_estop_engaged()` (`motor.c`).
4. **Servo:** should visibly center on boot. Real steering needs a host command (see below).
5. **Motors:** will **not** spin at all without an active host link — `uart_command_is_stale()` forces `motor_set_speed(0, 0)` within 500ms of boot if no command has ever arrived. This is the fail-safe working as intended, not a problem.

**Automated drive/steer self-test (no host needed):** runs a scripted sequence — forward 1 wheel revolution, backward 1 wheel revolution, steer left, steer right, return to center. Implemented in `mdp_stm32/src/selftest.c` (`selftest_run_if_requested()`).

`PE0` (the user button) is dual-purpose, depending on *when* you press it and, during normal
operation, the state of the `PD3` motor ON/OFF switch:

| When you press it | `PD3` state | What happens |
| --- | --- | --- |
| Held down at the exact moment the board resets/powers on | either | Skips normal startup, runs the self-test instead |
| Board already running normally | Motor ON (ready) | Runs the self-test sequence (can actually drive the motors) |
| Board already running normally | Motor OFF (disabled) | Cycles the OLED page instead (self-test would just refuse anyway) |

`selftest_run_if_requested()` checks the pin's level **once**, right after peripheral init and
*before* the main loop starts — it is not watching for a long-press while the firmware is already
running. To trigger it: hold `PE0` down, power-cycle (or hit physical reset) *while still holding
it*, and keep holding for a second or two after power returns — you'll see the LED start its
1-2-3-4-5-6 blink pattern once the sequence begins, at which point you can let go. Refuses to run
if the `PD3` motor switch is in the OFF (disabled) position.

Only `PE8` is a GPIO-controllable LED on this board (confirmed against the resource-allocation PDF — the `LED1`-`LED4` silkscreen labels on the schematic are hardwired power-rail/SWD indicators, not firmware-drivable). So each phase blinks `PE8` a distinct number of times instead of lighting separate LEDs:

| Blinks | Phase |
| --- | --- |
| 1 | Forward, 1 wheel revolution |
| 2 | Backward, 1 wheel revolution |
| 3 | Full-range servo sweep, **left limit first**, then slowly ascending to the right limit (confirmed asymmetric: left 27°, right 41°) — see [Control Tuning: Servo Range & Steering Calibration](tuning.md#servo-range-steering-calibration). Supersedes the old fixed ±20° steer-left/steer-right phases, which were dropped |
| 4 | Done |

Refusal (motor switch OFF) is a *separate* standalone 5-blink pattern with different timing
(100ms on/off vs. the phases' 150ms on/off) — not part of the numbered sequence above, so it can't
be confused with any of these phase counts.

The OLED also prints the current phase. Each drive phase has a 5s safety timeout (`SELFTEST_DRIVE_TIMEOUT_MS`) in case the wheels aren't actually turning (e.g. propped up wrong, or a real motor/encoder fault) — it won't hang forever waiting for encoder ticks that never arrive.

**With the ROS2 bridge running** (wheels off the ground first):

```bash
# find which /dev/ttyUSBx is USART3 - unplug/replug and check dmesg, or just try one
ros2 run mdp_bridge serial_bridge_node --ros-args -p serial_port:=/dev/ttyUSB0

# in other terminals:
ros2 topic echo /joint_states   # live encoder-derived position/velocity
ros2 topic echo /imu/data       # live accel/gyro
ros2 topic echo /estop          # should match the PD3 switch state

ros2 topic pub /joint_commands sensor_msgs/msg/JointState \
  "{name: ['lb_joint','rb_joint','left_joint','right_joint'], velocity: [2.0,2.0,0,0], position: [0,0,0.2,0.2]}" --once
```
Wheels should spin slowly and the servo should turn — confirms the full STM32 -> Pi -> STM32 loop.

## Not Yet Verified On Hardware

- Closed-loop wheel-speed PID — implemented, not bench-tuned or hardware-tested (see [Bench-Tuning the Motor PID](#bench-tuning-the-motor-pid)).
- `PD3` motor switch polarity — only confirmed functionally (self-test refusal), not with a multimeter.
- Battery voltage ADC divider ratio (11x) — sourced from vendor firmware, not cross-checked with a multimeter.
- Wheel diameter / effective rolling radius for ticks→real-distance conversion — unconfirmed.
- Main loop rate tiers (100Hz/5Hz) and URDF steering limits — implemented/updated, not yet re-validated on hardware.

