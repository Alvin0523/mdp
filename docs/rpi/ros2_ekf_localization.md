---
icon: lucide/crosshair
---

# EKF Localization

The car has no absolute position sensor, so its pose is **dead reckoning**: speed and turn rate added
up over time. `robot_localization`'s EKF (`ekf_filter_node`) does the adding up, from two sources.
Same node and same `mdp_bringup/config/ekf.yaml` in sim and real.

| Input | Topic | Used for | Why |
| --- | --- | --- | --- |
| Wheel odometry | `/ackermann_steering_controller/odometry` | **speed** (`vx`), and turn rate as a weak backup | The encoders measure distance well |
| IMU (ICM-20948 gyro) | `/imu/data` | **turn rate** (`vyaw`) | Wheel odometry gets turns wrong: it assumes the commanded steering angle is the real one (a 180° turn read 9 % too much on the car; in sim it drifted up to 119° in tight turns while the IMU stayed within 2°) |

- Neither source's position or heading is used directly. `x`, `y` and heading are the EKF's own sum
  of speed and turn rate.
- The controller marks its own turn rate as untrustworthy (covariance 1.0), so the IMU dominates.
  It is still fused, so a dead IMU degrades turning instead of losing it completely.
- `two_d_mode: true`: flat floor; height, roll and pitch are held at zero.
- `publish_tf: true`: the EKF alone publishes `odom → base_footprint` (the controller's
  `enable_odom_tf` is off).
- `frequency: 100` Hz, matching the STM32's telemetry rate. `sensor_timeout: 0.5` s, the same
  window as the STM32 command timeout and the serial-link watchdog.

## Frames

```text
map ──(static = start pose)──▶ odom ──(EKF)──▶ base_footprint ──▶ base_link (middle of the rear axle)
```

`odom` starts wherever the car starts, so `map → odom` (a static transform from the launch, built from
`start_cell` / `start_dir`) is what puts the pose onto the arena and its cells. The runners, the
tablet's `ROBOT` line and `calib goto` all look the pose up in `map`.

## Reset

`/reset_pose` (`pixi run reset`, tablet RESET) sends the start pose to the EKF's `/set_pose`. The
runner then waits until `/odometry/filtered` reports the start pose (RESET: DONE). Reset with the
car physically back in the start box. In sim, `robot_pose_feedback` first moves the Gazebo car
back to the start pose itself (Gazebo's `set_pose` service), so a run can be repeated without
restarting the sim.

## How good is it

In sim, compared with Gazebo's true pose over a full task 1 run: 2–6 cm off at worst. On the real car it
hasn't been measured yet. Errors grow with distance driven, so later checkpoints are hit less
exactly. Correcting the pose from something outside the car (e.g. the camera seeing blocks) is not
done.
