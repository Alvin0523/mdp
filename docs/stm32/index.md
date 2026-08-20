---
icon: lucide/cpu
---

# STM32 Firmware (`mdp_stm32`)

Documentation for the `mdp_stm32` submodule containing the PlatformIO/STM32Cube HAL firmware for the WHEELTEC C30D V2.1 board (STM32F407VET6 MCU).

## Quick Links

- [Pinouts & Peripherals](pinouts.md) — Timer mappings, USART configuration, and verified pin assignments.
- [Drivers & Sensors](drivers.md) — Motor PWM (AT8236), HWZ020 steering servo, Hall encoders, and ICM-20948 IMU implementation details.

## Build & Flash (PlatformIO + Pixi Workflow)

Firmware is built with PlatformIO (`platformio.ini`), driven via `pixi.toml` tasks. Flashing and probing use PlatformIO's bundled OpenOCD talking to the ST-LINK/V2 over SWD; on ARM64 Linux hosts (e.g. Raspberry Pi 64-bit OS) the pinned `toolchain-gccarmnoneeabi` version needs a `platform_packages` override in `platformio.ini` (see `../troubleshooting.md`).

```bash
cd mdp_stm32

# 1. Check connected ST-LINK/V2 probe and STM32F407 chip
pixi run probe

# 2. Build firmware
pixi run build

# 3. Flash firmware to the board via ST-LINK/V2
pixi run flash

# 4. Open serial monitor (baud/port from platformio.ini)
pixi run monitor
```

## System Role

The MCU operates as a pure hardware I/O controller ([Host-Side Kinematics Architecture](../architecture.md#0004-hardware-interface-topic_based_ros2_control-host-side-kinematics)). It receives wheel speed and steering commands over a fixed-size binary serial protocol (USART3 @ 115200 baud on `PD8`/`PD9`) and publishes raw encoder telemetry back to the Raspberry Pi 4B host.
