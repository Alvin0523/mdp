---
icon: lucide/play
---

# Run: Real Hardware

End-to-end steps to get the physical robot driving. This page is a runbook (commands only) — for
*why* any of this works the way it does, see [STM32 docs](stm32/index.md),
[Serial Protocol](stm32/protocol.md), and [RPi: Launch Files](rpi/launch.md).

## 1. One-time setup

```bash
git clone --recurse-submodules <this-repo-url>
cd mdp
pixi install
```

## 2. Flash the STM32 firmware

```bash
cd mdp_stm32
pixi install       # PlatformIO + toolchain

pixi run probe      # confirm ST-LINK/V2 + STM32F407 are detected
pixi run build
pixi run flash
pixi run monitor    # optional: confirm boot banner + PE8 LED blink + OLED page cycling
```

ARM64 Linux hosts (e.g. RPi 64-bit OS) need a `platform_packages` override in `platformio.ini` for
the pinned toolchain — see [Troubleshooting](troubleshooting.md) if `pixi run build` fails there.

## 3. Pre-flight checks (no host needed yet)

- Spin a rear wheel by hand — OLED page 3's `Enc L`/`Enc R` counts should change and flip sign with direction.
- Toggle the `PD3` motor ON/OFF switch — OLED page 3's status field should flip.
- Servo should visibly center on boot.
- Motors will **not** spin yet — that's expected, see step 5 below.

Full checklist: [Serial Protocol: Verification Checklist](stm32/protocol.md#verification-checklist-post-flash-bring-up).

## 4. Launch the ROS2 side

```bash
cd mdp_ros
pixi install
pixi run build
```

Find the STM32's serial device (varies by host):

```bash
ls /dev/ttyACM* /dev/ttyUSB* 2>/dev/null
```

```bash
pixi run real serial_port:=/dev/ttyACM0   # substitute the device found above
```

Brings up `topic_based_ros2_control`, `ackermann_steering_controller`, `mdp_hardware_bridge`
(STM32 serial link), and `robot_localization` (EKF).

## 5. Turn on the motor switch and drive

Flip the `PD3` motor ON/OFF switch to ON — the wheels stay locked at 0% PWM until an active host
link exists (`pixi run real` must already be running) **and** the switch is ON.

```bash
cd mdp_ros
pixi run teleop
```

## Notes

- **EKF not yet validated on real driving** — config-checked only; a stationary-robot run showed
  `y`/`yaw` covariance growing unbounded. See [RPi: Known Issue](rpi/launch.md#known-issue-odometryfiltereds-y-and-yaw-covariance-grow-unbounded)
  before trusting `/odometry/filtered` for anything beyond short manual test drives.
- **Steering nonlinearity** — commanded steering angle vs. actual road-wheel angle is not linear
  (vendor-documented Ackermann servo behavior); see [STM32: Servo range](stm32/control_loop.md#2-servo-range-the-real-vendor-solution-is-an-empirically-fit-curve-not-a-linear-gain).
- **Stale-link fail-safe**: if the serial link drops or the Pi crashes, the STM32 stops the motors
  and centers the servo within 500ms regardless of the last command — this is intended, not a bug.
