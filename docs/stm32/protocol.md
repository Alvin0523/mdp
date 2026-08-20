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

## Verification Checklist (post-flash bring-up)

!!! note "`printf` debug text and the binary protocol share `USART3`"
    Since the protocol went in, `pixi run monitor` shows readable boot-banner/telemetry-line text interleaved with raw binary garbage from the packet stream — expected, not a bug. The framed protocol resyncs on sync bytes and rejects bad checksums regardless.

**No host needed:**

1. `pixi run flash` then `pixi run monitor` — confirm the boot banner prints (amid binary noise), PE8 LED blinks, OLED cycles pages via the button.
2. **Encoders:** spin a rear wheel by hand, watch OLED page 3 (`Enc L`/`Enc R`) — counts should change and sign should flip with direction.
3. **E-stop switch (`PD3`):** toggle it, watch OLED page 3's `ESTOP` field flip READY/ENGAGED. Polarity is an *assumption*, not yet physically verified — if it reads backwards, flip the comparison in `motor_estop_engaged()` (`motor.c`).
4. **Servo:** should visibly center on boot. Real steering needs a host command (step 6).
5. **Motors:** will **not** spin at all without an active host link — `uart_command_is_stale()` forces `motor_set_speed(0, 0)` within 500ms of boot if no command has ever arrived. This is the fail-safe working as intended, not a problem.

**With the ROS2 bridge running** (wheels off the ground first):

```bash
# find which /dev/ttyUSBx is USART3 - unplug/replug and check dmesg, or just try one
ros2 run mdp_hardware_bridge serial_bridge_node --ros-args -p serial_port:=/dev/ttyUSB0

# in other terminals:
ros2 topic echo /joint_states   # live encoder-derived position/velocity
ros2 topic echo /imu/data       # live accel/gyro
ros2 topic echo /estop          # should match the PD3 switch state

ros2 topic pub /joint_commands sensor_msgs/msg/JointState \
  "{name: ['lb_joint','rb_joint','left_joint','right_joint'], velocity: [2.0,2.0,0,0], position: [0,0,0.2,0.2]}" --once
```
Wheels should spin slowly and the servo should turn — confirms the full STM32 -> Pi -> STM32 loop.

## Two Motor-Related Switches — Don't Confuse Them

1. **"Motor ON/OFF" toggle** (near the battery/power terminal blocks) — a pure **hardware power cutoff** for the motor driver ICs. **Not GPIO-readable**, firmware cannot see it. Use it when you want motors guaranteed dead for bench work.
2. **`PD3` enable/e-stop switch** — GPIO-readable, wired as `motor_estop_engaged()`, reflected on the OLED and `/estop`.

## Not Yet Verified On Hardware

- STM32-side firmware build has not been confirmed to compile (last verified: ROS2/`mdp_hardware_bridge` side only, via `colcon build`).
- `PD3` e-stop polarity assumption (see above).
- Full protocol round-trip (steps under "With the ROS2 bridge running") has not been run yet.
- Servo `SERVO_ANGLE_MAX_DEG` (`servo.h`/`servo.c`) is still a placeholder, not tuned to the actual mechanical steering lock.
