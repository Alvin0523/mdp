---
icon: lucide/cpu
---

# STM32 Firmware (`mdp_stm32`)

Documentation for the `mdp_stm32` submodule containing the PlatformIO/STM32Cube HAL firmware for the WHEELTEC C30D V2.1 board (STM32F407VET6 MCU).

## Quick Links

- [Pinouts & Peripherals](pinouts.md) — Timer mappings, USART configuration, and verified pin assignments.
- [Drivers & Sensors](drivers.md) — Motor PWM (AT8236), HWZ020 steering servo, Hall encoders, and ICM-20948 IMU implementation details.
- [Serial Protocol](protocol.md) — Binary telemetry/command packet format linking this firmware to the `mdp_hardware_bridge` ROS2 node.
- [Closed-Loop Control & Sensor Fusion (Planned)](control_loop.md) — Design plan for closed-loop wheel speed PID (STM32-side), servo range calibration, and the proper IMU → `robot_localization` data flow.

## Build & Flash (PlatformIO + Pixi Workflow)

Firmware is built with PlatformIO (`platformio.ini`), driven via `pixi.toml` tasks. Flashing and probing use PlatformIO's bundled OpenOCD talking to the ST-LINK/V2 over SWD; on ARM64 Linux hosts (e.g. Raspberry Pi 64-bit OS) the pinned `toolchain-gccarmnoneeabi` version needs a `platform_packages` override in `platformio.ini` (see `../troubleshooting.md`).

```bash
cd mdp_stm32

# 1. Install PlatformIO + toolchain (managed via pixi.toml)
pixi install

# 2. Check connected ST-LINK/V2 probe and STM32F407 chip
pixi run probe

# 3. Build firmware
pixi run build

# 4. Flash firmware to the board via ST-LINK/V2
pixi run flash

# 5. Open serial monitor (baud/port from platformio.ini)
pixi run monitor
```

For OpenSpec CLI setup (`openspec init`) inside this submodule, see [Dev Workflow & OpenSpec Setup](../dev_workflow.md).

## System Role

The MCU operates as a pure hardware I/O controller ([Host-Side Kinematics Architecture](../architecture.md#0004-hardware-interface-topic_based_ros2_control-host-side-kinematics)). It receives wheel speed and steering commands over a fixed-size binary serial protocol (USART3 @ 115200 baud on `PD8`/`PD9`) and publishes raw encoder telemetry back to the Raspberry Pi 4B host.

## Implementation Status

- [x] PlatformIO/STM32Cube HAL Project Bringup — LED blink (`PE8`) + `printf` serial logging over `USART3`, verified on physical board
- [x] AT8236 Motor PWM Driver — `TIM9`/`TIM10`/`TIM11` PWM implemented (`motor_init`, `motor_set_speed`); **on-hardware drive test confirmed** (locked-antiphase drive scheme required, see [Drivers](drivers.md#at8236-motor-driver-motorc))
- [x] HWZ020 Steering Servo Driver (`PB15` / TIM12_CH2) — Implemented (`servo_init`, `servo_set_angle`); on-hardware steering confirmed via ROS2 teleop; **mechanical steering-lock commanded values calibrated at 1° resolution: right 41°, left 27°** (firmware-internal pulse-mapped unit, not real angle) — see [Closed-Loop Control & Sensor Fusion](control_loop.md#2-servo-range-the-real-vendor-solution-is-an-empirically-fit-curve-not-a-linear-gain)
- [x] URDF `left_joint`/`right_joint` limits updated to `±0.5672 rad` (32.5°) — the **steering angle** physically measured with a protractor, 8 raw readings across 2 measurement sessions (both wheels, both firmware-clamped commanded extremes). Mode at 0.5° resolution (32.5°, agreed on by 3 of 8 readings, confirmed independently via right-wheel-only readings too) taken as the answer over the mean — see [Closed-Loop Control & Sensor Fusion](control_loop.md#2-servo-range-the-real-vendor-solution-is-an-empirically-fit-curve-not-a-linear-gain). Replaces the servo's bare datasheet spec (`±0.39 rad`/22.35°). Not yet re-validated on hardware after the change
- [ ] Closed-Loop Wheel Speed PID — **Implemented, NOT yet bench-tuned or hardware-tested** (`motor_pid_init/enable/set_target/get_measured_rad_s/pause/resume` in `motor.c`, TIM7 100Hz ISR, hybrid feedforward+incremental-PI). `MOTOR_PID_KP=4.0f`/`MOTOR_PID_KI=0.5f` are untuned placeholder gains. **This is the next priority item** — flash and bench-test (wheels off the ground) before any other `mdp_stm32` work builds on top of it. See [Closed-Loop Control & Sensor Fusion](control_loop.md#1-closed-loop-wheel-speed-lives-on-the-stm32-not-the-piekf)
- [x] Motor switch (`PD3`) now gates the driving loop directly in `main.c` (not just self-test) — switch OFF forces 0% PWM regardless of host commands; not yet hardware-tested
- [x] NVIC interrupt priorities reordered — motor PID (`TIM7`) is now the highest-priority app interrupt (was previously *lowest*, an oversight that let the button/UART preempt the safety-critical motor loop); see the ownership table in [Closed-Loop Control & Sensor Fusion](control_loop.md#0-interrupttiming-architecture-why-bare-metal-is-still-ok-and-where-the-line-is)
- [x] Main loop split into two rate tiers (100Hz command/IMU/telemetry, ~5Hz OLED/battery/debug) instead of one shared 10Hz `HAL_Delay` — telemetry TX converted to interrupt-driven (`HAL_UART_Transmit_IT`) to make 100Hz viable; not yet hardware-tested. See [Main loop timing allocation](control_loop.md#main-loop-timing-allocation)
- [x] Hall Encoder Driver (TIM2 / TIM3) — Implemented (`encoder_init`, delta + cumulative tick reads). **Ticks-per-revolution (1560) physically confirmed on hardware** for both wheels via a 10-rotation hand-turn test (average landed on exactly 1560.0 ticks/rev, both sides, no systematic left/right discrepancy) — see [Drivers](drivers.md#ticks-per-revolution-physically-confirmed-on-hardware). Wheel diameter/effective rolling radius for ticks→real-distance conversion is still unconfirmed
- [x] ICM-20948 IMU Driver (bit-banged software I2C, `PB10`/`PB11`) — Implemented
- [x] Onboard Enable/E-Stop Switch (`PD3`) — Implemented (`motor_estop_engaged`); active-low polarity functionally confirmed via self-test refusal behavior, not yet confirmed with a multimeter
- [x] Serial Protocol + `mdp_hardware_bridge` — Custom binary protocol over `USART3` implemented on both sides (see [Serial Protocol](protocol.md)); replaces the originally planned micro-ROS transport ([ADR 0002](../architecture.md#0002-custom-binary-serial-protocol-vs-micro-ros-rclc)); **full round-trip on-hardware validated** via `pixi run real` + `pixi run teleop` (two bugs found and fixed along the way — see [Launch Files](../rpi/launch.md#core-topic-specifications))
- [x] Battery Voltage ADC (`PB0` / ADC1_CH8) — Implemented (`battery_init`, `battery_read_voltage`); divider ratio (11x) sourced from WHEELTEC's C30D vendor example firmware, not yet cross-checked against a multimeter on this specific board
- [ ] Ultrasonic Distance Sensor (HC-SR04) Driver — Not started
- [ ] IR Distance Sensor (Sharp GP2Y0A21YK ×2) Driver — Not started
