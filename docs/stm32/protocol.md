---
icon: lucide/cable
---

# Serial Protocol (`mdp_stm32` <-> `mdp_ros`)

Custom lightweight binary protocol over `USART3` (Type-C Port 3, `PD8`/`PD9`, 115200 baud) linking the STM32 firmware to the `mdp_hardware_bridge` ROS2 node on the Pi. Supersedes the originally planned micro-ROS transport — see [ADR 0002](../architecture.md#0002-custom-binary-serial-protocol-vs-micro-ros-rclc).

Implementations:

- **STM32 side:** `mdp_stm32/include/protocol.h`, `mdp_stm32/src/usart.c`
- **ROS2 side:** `mdp_ros/src/mdp_hardware_bridge/include/mdp_hardware_bridge/protocol.hpp`, `mdp_ros/src/mdp_hardware_bridge/src/serial_bridge_node.cpp`

These two headers are **hand-mirrored, not shared at build time** — the two repos have separate build systems (PlatformIO vs colcon). If you change one, update the other.

## Framing

Both packet types start with 2 sync bytes and a type byte, so a receiver can resync after a dropped/corrupted byte:

```
[0xAA] [0x55] [type] [payload...] [checksum]
```

- `checksum` is an XOR of every byte from `type` through the last payload byte (sync bytes excluded).
- Both sides are little-endian (Cortex-M4 and aarch64/x86_64 Pi) — no byte-swapping.

## Telemetry Packet (STM32 -> Pi)

Sent once per firmware main-loop iteration (currently 10Hz).

| Field | Type | Meaning |
| --- | --- | --- |
| `enc_left` | `int32` | Cumulative Motor A (rear left) encoder ticks |
| `enc_right` | `int32` | Cumulative Motor B (rear right) encoder ticks |
| `steer_deg` | `float` | Steering angle currently applied to the servo (deg) |
| `accel_x/y/z` | `float` | Accelerometer, m/s^2 |
| `gyro_x/y/z` | `float` | Gyroscope, deg/s |
| `yaw_deg` | `float` | Complementary-filter yaw (deg) — roll/pitch are not estimated (no magnetometer fusion) |
| `imu_ready` | `uint8` | 1 = IMU detected and operational |
| `estop` | `uint8` | 1 = onboard `PD3` e-stop switch engaged |
| `uptime_ms` | `uint32` | Firmware `HAL_GetTick()` at send time |

The bridge node differentiates `enc_left`/`enc_right` against the previous packet's value and `uptime_ms` delta to compute wheel angular velocity — no separate delta field is sent.

**Ticks -> radians:** `2*pi / 1560` per tick. `1560 = EncoderMultiples(4) * Hall_13(13 pulses/motor-rev) * HALL_30F(30:1 gear ratio)`, sourced from WHEELTEC's vendor reference firmware's Ackermann-car config for this exact motor (`MG513P3012V` + Hall encoder) — see `references/WHEELTEC/.../robot_select_init.h`.

## Command Packet (Pi -> STM32)

Sent whenever `/joint_commands` updates.

| Field | Type | Meaning |
| --- | --- | --- |
| `left_wheel_rad_s` | `float` | Target Motor A (rear left) angular velocity |
| `right_wheel_rad_s` | `float` | Target Motor B (rear right) angular velocity |
| `steer_rad` | `float` | Target steering angle (radians) |

**rad/s -> PWM percent:** open-loop, `pct = (rad_s / 34.56) * 100`, clamped to ±100%. `34.56 rad/s` = `MG513P3012V`'s rated max output speed (330 RPM, already post-1:30-gearbox per `docs/hardware.md`). There is no closed-loop encoder-based speed regulation yet — actual speed at a given commanded rad/s varies with battery voltage and load.

**One servo, two steering joints:** `ackermann_steering_controller` commands `left_joint` and `right_joint` independently (true Ackermann geometry has slightly different inner/outer wheel angles), but this chassis has only **one physical steering servo**. The bridge node averages the two commanded angles into the single `steer_rad` sent to the MCU — a small-angle approximation, not exact per-wheel Ackermann steering.

## Safety: stale-link fail-safe

If the STM32 hasn't received a checksum-valid command packet within `PROTOCOL_COMMAND_TIMEOUT_MS` (500ms, `mdp_stm32/src/main.c`), it stops the motors and centers the servo regardless of the last-received setpoint — protects against a Pi crash, USB disconnect, or bridge node hang leaving the robot driving on a stale command.
