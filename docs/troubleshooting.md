---
icon: lucide/wrench
---

# Troubleshooting

Real gotchas we've actually hit, so nobody has to rediscover them. Click a title to expand.

## :cpu: STM32 Firmware (`mdp_stm32`)

??? question "`pixi run flash` on Raspberry Pi (64-bit OS): `UnknownPackageError: ... toolchain-gccarmnoneeabi @ >=1.60301.0,<1.80000.0 ... for your system 'linux_aarch64'`"

    PlatformIO's `ststm32` platform pins `toolchain-gccarmnoneeabi` to a version range that has no `linux_aarch64` (64-bit ARM, e.g. Raspberry Pi OS 64-bit) build in the registry — only newer versions (e.g. `1.90301.0`) publish an aarch64 toolchain.

    **Fix**: override the toolchain version in `mdp_stm32/platformio.ini` under the board's `[env:...]` section:
    ```ini
    platform_packages =
        toolchain-gccarmnoneeabi@~1.90301.0
    ```
    See [platform-ststm32 issue #274](https://github.com/platformio/platform-ststm32/issues/274).

??? question "`pixi run flash` / `pixi run probe` fails with `Error: open failed` or `Error: reset device failed`"

    Both come from OpenOCD (bundled with PlatformIO) failing to talk to the target over SWD, not a firmware bug:

    - `open failed` — OpenOCD can't even open a USB connection to the ST-LINK probe itself. Check `lsusb` for the ST-LINK, reseat/replug its USB cable.
    - `reset device failed` — the ST-LINK is reachable over USB, but the target STM32 doesn't respond to reset over SWD. Most often this just means **the target board has no power** — some ST-LINK/V2 clones supply a small 3.3V rail to the target over the SWD header (sourced from the host's USB port), which is enough to run the bare MCU + OLED + IMU, but nowhere near enough once motor/servo PWM is actually driving real actuators (H-bridge + servo current draw browns out that rail). Power the C30D board from its own supply (battery/barrel jack) rather than relying on ST-LINK-sourced power once motors/servos are in the loop.

??? question "Firmware flashes and verifies OK, but nothing runs — no UART output, no LED blink, status LED frozen after reset"

    The board is booting into the STM32F407's built-in **system-memory bootloader** (`0x1FFF0000` region) instead of your flashed firmware (`0x08000000`), even though the flash itself is correct — confirm by halting the core over SWD (`openocd ... -c "init; reset halt; reg pc; exit"`) and checking whether `pc` lands in `0x1FFF...`.

    This board has no physical `BOOT0` switch/jumper — `BOOT0` only has a fairly weak 10k pull-down resistor holding it low (normal boot) by default, opposed by an auto-bootload transistor circuit wired to the **other** USB-C port (silkscreened `UART1`, separate from the `UART3` port used for the normal serial link), meant for a one-click flashing tool that asserts the port's RTS/DTR lines. In practice this net can behave a bit marginal/soft (no strong active driver either way at idle) — **keeping the ST-Link connected while resetting the board has been observed to reliably tip it into the bootloader**, even with nothing plugged into `UART1`.

    **Workaround that's worked reliably:** flash via ST-Link, then **physically unplug the ST-Link** before power-cycling/resetting the board to actually run the firmware. If it's still stuck, connect to the `UART1` port directly (e.g. via `pyserial`) and pulse `dtr` while holding `rts` deasserted to force a clean reset through that port's own reset line instead of the ST-Link's.

    !!! failure "Deauthorizing the ST-Link at the USB level (`authorized=0` sysfs toggle) does NOT fix this"
        Tried this as a no-cable-touching alternative to physically unplugging. It doesn't reliably work, because USB deauthorization only stops the **host computer** from talking to the ST-Link over USB — it does **not** physically disconnect the SWD wires (`SWCLK`/`SWDIO`/`3.3V`/`GND`) between the probe and the target board. Those stay electrically connected the whole time regardless of USB authorization state, so if the actual mechanism is electrical (see below), a USB-level toggle can't reach it. A physical unplug is the only workaround confirmed to work reliably.

    **Possible contributing factor:** per [Hardware Components](hardware.md), this board's ST-Link header is only 4 pins — `3.3V`, `SWCLK`, `GND`, `SWDIO` — **no `NRST` wire**. Without a real `NRST` connection, OpenOCD can't issue a true hardware pin reset and falls back to a software-requested reset over the debug port itself, which may not re-sample `BOOT0` the same way a genuine pin/power-on reset does. Not confirmed as the root cause — worth investigating if you want to fix this properly instead of working around it (e.g. checking whether the ST-Link cable/board expose a spare pin `NRST` could be wired to).

## :robot: ROS2 (`mdp_ros`)

??? question "`pixi run real`: `serial_bridge_node` dies with `Failed to open serial port /dev/ttyUSB0`"

    `real.launch.py`'s `serial_port` parameter defaults to `/dev/ttyUSB0`, but the STM32's USB-serial chip enumerates under a different device name depending on host/driver — e.g. `/dev/ttyACM0` on some machines (`lsusb`/`ls /dev/serial/by-id/` to check which one you actually have).

    **Fix**: override it at launch time instead of hardcoding:
    ```bash
    pixi run real serial_port:=/dev/ttyACM0
    ```

??? question "Teleop moves the rear wheels but front-wheel steering points the wrong direction"

    Sign convention mismatch between two independently-authored subsystems: ROS's `left_joint`/`right_joint` (REP-103) treat positive angle as **left**, while `mdp_stm32`'s `servo_set_angle()` treats positive as **right**. `mdp_bridge`'s `serial_bridge_node.cpp` negates the angle at the boundary (both command and telemetry directions) to reconcile them — see [STM32: Serial Protocol](stm32/serial_protocol.md#serial-protocol) (Command Packet section). If this ever breaks again, check that negation is still present and applied symmetrically both ways, not just one.

??? question "Teleop moves front steering but the rear wheels never spin, even though `/joint_commands` shows nonzero `velocity`"

    `topic_based_ros2_control`'s `TopicBasedSystem` publishes `/joint_commands` with `name[]` listing every joint, but `position[]`/`velocity[]` as **separate, independently-indexed arrays** holding only the joints that use that interface type — not one slot per `name[]` entry. Code that reuses the same loop index across `name[]` and `position[]`/`velocity[]` will silently break for joints after the first interface-type group. See the full writeup in [RPi: ROS2 Jazzy](rpi/ros2_jazzy.md#core-topic-specifications).

??? question "A `remappings=` on a controller `spawner` node doesn't seem to do anything"

    `spawner` is a short-lived CLI tool that calls a service to load/activate a controller inside `ros2_control_node` — it doesn't own that controller's actual topics, so remapping *it* has no effect. The controller's real subscriber/publisher topics live inside `ros2_control_node` (`controller_manager`) itself; put `remappings=` on that `Node(...)` instead. (In `sim.launch.py`, the equivalent remap correctly lives in the URDF's `gz_ros2_control` plugin `<ros><remapping>` block, since Gazebo owns that controller manager internally — that path was never affected by this.)

## :twisted_rightwards_arrows: Git / submodules

??? question "Cloned the repo but `mdp_ros/`/`mdp_stm32/` are empty"

    Need `--recurse-submodules` on clone, or afterward:

    ```bash
    git submodule update --init --recursive
    ```

    See [Quickstart](quickstart.md).
