---
icon: lucide/cpu
---

# STM32 Firmware (`mdp_stm32`)

Documentation for the `mdp_stm32` submodule containing the Zephyr RTOS firmware and micro-ROS node for the WHEELTEC C30D V2.1 board (STM32F407VET6 MCU).

## Quick Links

- [Pinouts & Peripherals](pinouts.md) — Timer mappings, USART configuration, and verified pin assignments.
- [Drivers & Sensors](drivers.md) — Motor PWM (AT8236), HWZ020 steering servo, Hall encoders, and ICM-20948 IMU implementation details.

## Build & Flash (Pixi Workflow)

All toolchain dependencies (`west`, `pyocd`, ARM cross-compiler SDK, `ninja`) are managed reproducible via `pixi.toml`.

```bash
cd mdp_stm32

# 1. One-time setup: install target pack, fetch Zephyr modules & ARM SDK
pixi run setup

# 2. Check connected ST-LINK/V2 probe and STM32F407 chip
pixi run probe

# 3. Build firmware
pixi run build

# 4. Flash firmware to the board via ST-LINK/V2 (pyocd)
pixi run flash

# 5. Stream RTT debug logs
pixi run rtt
```

## System Role

The MCU operates as a pure hardware I/O controller ([Host-Side Kinematics Architecture](../architecture.md#0004-hardware-interface-topic_based_ros2_control-host-side-kinematics)). It receives wheel speed and steering commands via micro-ROS over serial (USART3 @ 115200 baud on `PD8`/`PD9`) and publishes raw encoder telemetry back to the Raspberry Pi 4B host.
