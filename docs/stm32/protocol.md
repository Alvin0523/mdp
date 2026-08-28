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
| `yaw_deg` | `float` | Bias-corrected gyro-Z integration (deg) — no accel/mag fusion, so it will still drift over time; roll/pitch are not estimated at all |
| `imu_ready` | `uint8` | 1 = IMU detected and operational |
| `estop` | `uint8` | 1 = onboard `PD3` e-stop switch engaged |
| `battery_v` | `float` | Battery pack voltage (V), `PB0`/ADC1_CH8 |
| `uptime_ms` | `uint32` | Firmware `HAL_GetTick()` at send time |

The bridge node differentiates `enc_left`/`enc_right` against the previous packet's value and `uptime_ms` delta to compute wheel angular velocity — no separate delta field is sent.

**Ticks -> radians:** `2*pi / 1560` per tick. `1560 = EncoderMultiples(4) * Hall_13(13 pulses/motor-rev) * HALL_30F(30:1 gear ratio)`, sourced from WHEELTEC's vendor reference firmware's Ackermann-car config for this exact motor (`MG513P3012V` + Hall encoder) — see `references/WHEELTEC/.../robot_select_init.h`.

**ADC raw -> volts:** `raw / 4095.0 * 3.3 * 11.0` (12-bit ADC, 3.3V reference, 11x resistor-divider ratio), sourced from WHEELTEC's C30D basic-example firmware (`references/WHEELTEC/.../03-ADC采集电压值与电位器值/ADC.zip` → `bsp_adc.c`/`main.c`, `Battery_Ch = 8`) for this exact board line — the divider ratio couldn't be confirmed from the schematic PDF's visual layout, so it's taken from the vendor's own working example instead of guessed.

## Command Packet (Pi -> STM32)

Sent whenever `/joint_commands` updates.

| Field | Type | Meaning |
| --- | --- | --- |
| `left_wheel_rad_s` | `float` | Target Motor A (rear left) angular velocity |
| `right_wheel_rad_s` | `float` | Target Motor B (rear right) angular velocity |
| `steer_rad` | `float` | Target steering angle (radians). **Positive = right, negative = left** — this matches `servo_set_angle()`'s convention (`mdp_stm32/src/servo.c`), which is the **opposite** sign from ROS's `left_joint`/`right_joint` (REP-103: positive = left). `mdp_hardware_bridge`'s `serial_bridge_node.cpp` negates the angle when converting in both directions — see the note in [Launch Files](../ros/launch.md#core-topic-specifications). |

**rad/s -> PWM percent:** open-loop, `pct = (rad_s / 34.56) * 100`, clamped to ±100%. `34.56 rad/s` = `MG513P3012V`'s rated max output speed (330 RPM, already post-1:30-gearbox per `docs/hardware.md`). There is no closed-loop encoder-based speed regulation yet — actual speed at a given commanded rad/s varies with battery voltage and load.

**One servo, two steering joints:** `ackermann_steering_controller` commands `left_joint` and `right_joint` independently (true Ackermann geometry has slightly different inner/outer wheel angles), but this chassis has only **one physical steering servo**. The bridge node averages the two commanded angles into the single `steer_rad` sent to the MCU — a small-angle approximation, not exact per-wheel Ackermann steering.

!!! warning "`/joint_commands`'s `position[]`/`velocity[]` are grouped by interface type, not indexed by `name[]`"
    The bridge's `onJointCommand()` originally indexed `msg->position[i]`/`msg->velocity[i]` using the same loop index it used for `msg->name[i]` — but `topic_based_ros2_control` publishes those as separate, shorter arrays containing only the joints that use that interface type, not one slot per `name[]` entry. This silently left the rear wheels' commanded velocity at `0.0` forever (out-of-range guard always failed for `lb_joint`/`rb_joint`), even though the packet, checksum, and everything downstream looked completely correct. Fixed with separate per-array running indices — see the full writeup in [Launch Files](../ros/launch.md#core-topic-specifications).

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

**Automated drive/steer self-test (no host needed):** runs a scripted sequence — forward 1 wheel revolution, backward 1 wheel revolution, steer left, steer right, return to center. Implemented in `mdp_stm32/src/selftest.c` (`selftest_run_if_requested()`).

`PE0` (the user button) has two *completely separate* behaviors depending on when you press it — this is not a runtime long-press:

| When you press it | What happens |
| --- | --- |
| Board already running normally | Cycles the OLED page (unchanged EXTI-interrupt behavior, `button.c`) |
| Held down at the exact moment the board resets/powers on | Skips normal startup, runs the self-test instead |

`selftest_run_if_requested()` checks the pin's level **once**, right after peripheral init and *before* the main loop starts — it is not watching for a long-press while the firmware is already running. To trigger it: hold `PE0` down, power-cycle (or hit physical reset) *while still holding it*, and keep holding for a second or two after power returns — you'll see the LED start its 1-2-3-4-5 blink pattern once the sequence begins, at which point you can let go. Refuses to run if the `PD3` e-stop switch is engaged.

Only `PE8` is a GPIO-controllable LED on this board (confirmed against the resource-allocation PDF — the `LED1`-`LED4` silkscreen labels on the schematic are hardwired power-rail/SWD indicators, not firmware-drivable). So each phase blinks `PE8` a distinct number of times instead of lighting separate LEDs:

| Blinks | Phase |
| --- | --- |
| 1 | Forward, 1 wheel revolution |
| 2 | Backward, 1 wheel revolution |
| 3 | Steer left |
| 4 | Steer right |
| 5 | Done (or refused, if e-stop was engaged) |

The OLED also prints the current phase. Each drive phase has a 5s safety timeout (`SELFTEST_DRIVE_TIMEOUT_MS`) in case the wheels aren't actually turning (e.g. propped up wrong, or a real motor/encoder fault) — it won't hang forever waiting for encoder ticks that never arrive.

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

## Verified On Hardware

- STM32-side firmware builds (`pixi run build`) and flashes (`pixi run flash`) cleanly.
- `PD3` e-stop polarity assumption — functionally confirmed (self-test correctly refuses with the quick 5-blink pattern when the switch is in the disabled position, runs normally otherwise). Not yet confirmed with a multimeter against the raw pin voltage.
- Full protocol round-trip (`ackermann_steering_controller` → `topic_based_ros2_control` → `mdp_hardware_bridge` → STM32 → motors/servo, and telemetry back) — confirmed via `pixi run real` + `pixi run teleop`, after fixing two bugs found along the way: the `/joint_commands` array-indexing bug and the steering sign-convention mismatch (both described above).
- The automated self-test sequence (`selftest.c`) — confirmed running on physical hardware, including actual wheel rotation, after fixing the motor driver's PWM scheme (see [Drivers](drivers.md#at8236-motor-driver-motorc)).

## Not Yet Verified On Hardware

- Servo `SERVO_ANGLE_MAX_DEG` (`servo.h`/`servo.c`) is still a placeholder, not tuned to the actual mechanical steering lock.
- `PD3` e-stop polarity has not been confirmed with a multimeter against the raw pin voltage (only functionally, via self-test behavior — see above).
