---
icon: lucide/play
---

# Run: Simulation

End-to-end steps to get the robot driving in Gazebo. This page is a runbook (commands only) — for
*why* any of this works the way it does, see [RPi: Launch Files](rpi/launch.md) and
[RPi: Gazebo Simulation](rpi/simulation.md).

## 1. One-time setup

```bash
git clone --recurse-submodules <this-repo-url>
cd mdp
pixi install

cd mdp_ros
pixi install
pixi run build
```

## 2. Launch

```bash
cd mdp_ros
pixi run sim
```

Starts Gazebo, spawns the robot (`mini_akm_robot.urdf`), and brings up `joint_state_broadcaster` +
`ackermann_steering_controller`.

## 3. Drive

In a second terminal:

```bash
cd mdp_ros
pixi run teleop
```

Keep that terminal focused: `i`/`,` forward/back, `j`/`l` steer left/right, `u`/`o` forward+turn,
`k` stop. Full key list in [RPi: Gazebo Simulation](rpi/simulation.md#driving-via-keyboard-teleop).

## 4. (Optional) Visualize

```bash
pixi run foxglove   # foxglove_bridge on ws://localhost:8765
# or
ros2 run rviz2 rviz2
```

## Notes

- No EKF in sim — Gazebo's own state is ground truth, so `robot_localization` only runs on real
  hardware. See [RPi: Launch Files](rpi/launch.md#2-real-hardware-mode-reallaunchpy).
- Sim's steering limits (`±0.39 rad` / `22.35°`, the HWZ020 datasheet spec) are **not** the same
  value as the real robot's measured limit (`±0.5672 rad` / `32.5°`) — the two URDFs are
  intentionally different here. See [STM32: Servo range](stm32/control_loop.md#2-servo-range-the-real-vendor-solution-is-an-empirically-fit-curve-not-a-linear-gain).
