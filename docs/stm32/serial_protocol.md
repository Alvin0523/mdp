---
icon: lucide/cable
---

# Serial Protocol (`mdp_stm32` ↔ `mdp_bridge`)

## Serial Protocol

Custom lightweight binary protocol over `USART3` (Type-C Port 3, `PD8`/`PD9`, 115200 baud) linking the STM32 firmware to the `mdp_bridge` ROS2 node on the Pi.

Implementations:

- **STM32 side:** `mdp_stm32/include/protocol.h`, `mdp_stm32/src/usart.c`
- **ROS2 side:** `mdp_ros/src/mdp_bridge/include/mdp_bridge/protocol.hpp`, `mdp_ros/src/mdp_bridge/src/serial_bridge_node.cpp`

These two headers are **hand-mirrored, not shared at build time** — the two repos have separate build systems (PlatformIO vs colcon). If you change one, update the other.

### Framing

Both packet types start with 2 sync bytes and a type byte, so a receiver can resync after a dropped/corrupted byte:

```
[0xAA] [0x55] [type] [payload...] [checksum]
```

- `checksum` is an XOR of every byte from `type` through the last payload byte (sync bytes excluded).
- Both sides are little-endian (Cortex-M4 and aarch64/x86_64 Pi) — no byte-swapping.

### Telemetry Packet (STM32 -> Pi)

Sent from `main.c`'s "fast" tier, **100Hz** (`FAST_PERIOD_MS`), via interrupt-driven TX (`HAL_UART_Transmit_IT`, not blocking) — matches the motor PID's own 100Hz encoder sampling 1:1, see [Main loop timing allocation](architecture.md#main-loop-timing-allocation) for the full rate-tier breakdown and why a blocking transmit wouldn't have been viable at this rate. Was 10Hz (tied to OLED/debug-print rate, blocking TX) before that split.

| Field | Type | Meaning |
| --- | --- | --- |
| `enc_left` | `int32` | Cumulative Motor A (rear left) encoder ticks |
| `enc_right` | `int32` | Cumulative Motor B (rear right) encoder ticks |
| `steer_deg` | `float` | Steering angle currently applied to the servo (deg) |
| `accel_x/y/z` | `float` | Accelerometer, m/s^2 |
| `gyro_x/y/z` | `float` | Gyroscope, deg/s |
| `yaw_deg` | `float` | Bias-corrected gyro-Z integration (deg) — no accel/mag fusion, so it will still drift over time; roll/pitch are not estimated at all |
| `imu_ready` | `uint8` | 1 = IMU detected and operational |
| `estop` | `uint8` | 1 = onboard `PD3` motor switch engaged |
| `battery_v` | `float` | Battery pack voltage (V), `PB0`/ADC1_CH8 |
| `uptime_ms` | `uint32` | Firmware `HAL_GetTick()` at send time |

The bridge node differentiates `enc_left`/`enc_right` against the previous packet's value and `uptime_ms` delta to compute wheel angular velocity — no separate delta field is sent.

**Ticks -> radians:** `2*pi / 1560` per tick. `1560 = EncoderMultiples(4) * Hall_13(13 pulses/motor-rev) * HALL_30F(30:1 gear ratio)`, sourced from WHEELTEC's vendor reference firmware's Ackermann-car config for this exact motor (`MG513P3012V` + Hall encoder) — see `references/WHEELTEC/.../robot_select_init.h`.

**ADC raw -> volts:** `raw / 4095.0 * 3.3 * 11.0` (12-bit ADC, 3.3V reference, 11x resistor-divider ratio), sourced from WHEELTEC's C30D basic-example firmware (`references/WHEELTEC/.../03-ADC采集电压值与电位器值/ADC.zip` → `bsp_adc.c`/`main.c`, `Battery_Ch = 8`) for this exact board line — the divider ratio couldn't be confirmed from the schematic PDF's visual layout, so it's taken from the vendor's own working example instead of guessed.

### Command Packet (Pi -> STM32)

Sent whenever `/joint_commands` updates.

| Field | Type | Meaning |
| --- | --- | --- |
| `left_wheel_rad_s` | `float` | Target Motor A (rear left) angular velocity |
| `right_wheel_rad_s` | `float` | Target Motor B (rear right) angular velocity |
| `steer_rad` | `float` | Target steering angle (radians). **Positive = right, negative = left** — this matches `servo_set_angle()`'s convention (`mdp_stm32/src/servo.c`), which is the **opposite** sign from ROS's `left_joint`/`right_joint` (REP-103: positive = left). `mdp_bridge`'s `serial_bridge_node.cpp` negates the angle when converting in both directions — see the note in [RPi: ROS2 Jazzy](../rpi/ros2_jazzy.md#core-topic-specifications). |

**rad/s -> PWM percent:** open-loop, `pct = (rad_s / 34.56) * 100`, clamped to ±100%. `34.56 rad/s` = `MG513P3012V`'s rated max output speed (330 RPM, already post-1:30-gearbox per `docs/hardware.md`). There is no closed-loop encoder-based speed regulation yet — actual speed at a given commanded rad/s varies with battery voltage and load.

**One servo, two steering joints:** `ackermann_steering_controller` commands `left_joint` and `right_joint` independently (true Ackermann geometry has slightly different inner/outer wheel angles), but this chassis has only **one physical steering servo**. The bridge node averages the two commanded angles into the single `steer_rad` sent to the MCU — a small-angle approximation, not exact per-wheel Ackermann steering.

!!! warning "`/joint_commands`'s `position[]`/`velocity[]` are grouped by interface type, not indexed by `name[]`"
    The bridge's `onJointCommand()` originally indexed `msg->position[i]`/`msg->velocity[i]` using the same loop index it used for `msg->name[i]` — but `topic_based_ros2_control` publishes those as separate, shorter arrays containing only the joints that use that interface type, not one slot per `name[]` entry. This silently left the rear wheels' commanded velocity at `0.0` forever (out-of-range guard always failed for `lb_joint`/`rb_joint`), even though the packet, checksum, and everything downstream looked completely correct. Fixed with separate per-array running indices — see the full writeup in [RPi: ROS2 Jazzy](../rpi/ros2_jazzy.md#core-topic-specifications).

### Safety: stale-link fail-safe and motor switch override

If the STM32 hasn't received a checksum-valid command packet within `PROTOCOL_COMMAND_TIMEOUT_MS` (500ms, `mdp_stm32/src/main.c`), it stops the motors and centers the servo regardless of the last-received setpoint — protects against a Pi crash, USB disconnect, or bridge node hang leaving the robot driving on a stale command.

Independently of link freshness, the `PD3` motor ON/OFF switch also gates motor output directly in the main loop: while it's OFF, the wheels are held at 0% PWM (motors won't move) no matter what the host commands; switching it ON lets both wheels drive normally again per the host's `left_wheel_rad_s`/`right_wheel_rad_s`. Either condition alone is enough to stop the motors — the check is `stale OR switch-off`, not both required.

### Two Motor-Related Switches — Don't Confuse Them

1. **"Motor ON/OFF" toggle** (near the battery/power terminal blocks) — a pure **hardware power cutoff** for the motor driver ICs. **Not GPIO-readable**, firmware cannot see it. Use it when you want motors guaranteed dead for bench work.
2. **`PD3` enable/e-stop switch** — GPIO-readable, wired as `motor_estop_engaged()`, reflected on the OLED and `/estop`.
