---
icon: lucide/crosshair
---

# ROS2 EKF Localization (`robot_localization`)

An Extended Kalman Filter (EKF) blends several noisy, imperfect position/heading sources into one
better estimate — more accurate than trusting any single sensor alone, and it's how this robot avoids
drifting during a competition run.

Installed as `ros-jazzy-robot-localization` (`pixi.toml`, `robostack-jazzy` channel). Configured in `mdp_bringup/config/ekf.yaml`, launched as `ekf_filter_node` in `real.launch.py`. To prevent position and heading drift during competition runs, three stages feed into it:

1. **Wheel Odometry (v_x):** `ackermann_steering_controller` calculates forward linear velocity from rear wheel encoders (`/ackermann_steering_controller/odometry`).
2. **IMU Heading (ω_z, θ):** The ICM-20948 IMU publishes angular velocity and rotation (`/imu/data`).
3. **EKF Fusion (`ekf_node`):** `robot_localization` fuses both streams to output smooth, drift-free `/odometry/filtered` and updates the dynamic `odom` → `base_link` TF transform.

See [STM32: IMU Data Flow](../stm32/tuning.md#imu-ros2_control-robot_localization-data-flow) for the STM32-side half of this pipeline (what the IMU actually sends, and why `ekf.yaml` fuses `angular_velocity` rather than `orientation`).

## How an EKF fuses odometry, conceptually

`ekf_node` is a generic multi-sensor Extended Kalman Filter — it doesn't know or care that one input is "wheel encoders" and another is "IMU". It just keeps a running estimate of a 15-element state vector (`x, y, z, roll, pitch, yaw, vx, vy, vz, vroll, vpitch, vyaw, ax, ay, az`) and repeats two steps:

1. **Predict** — at `frequency` Hz (30Hz here), advance the state estimate using its own motion model (e.g. "if vx was 0.5 m/s and yaw was 10°, where is the robot 33ms later") and grow the uncertainty (covariance) a little, since dead-reckoning alone drifts.
2. **Correct** — whenever a subscribed topic delivers a new message, compare what that sensor reports against the predicted state and nudge the estimate toward it, weighted by that sensor's own reported covariance (confident sensor = bigger nudge) and the filter's current uncertainty (Kalman gain).

The key design choice per sensor is `<sensor>_config`: a 15-element boolean mask picking *which* of those 15 state variables that sensor is allowed to correct. This is what lets you say "trust the encoders for speed but not heading" and "trust the IMU for heading but not position" — both feed the *same* state vector, so the filter reconciles them automatically rather than you having to average anything by hand.

## This robot's specific fusion

Only two of the fifteen state variables actually get outside correction here — `vx` (from wheel odometry) and `yaw`/`vyaw` (from the IMU). `x`/`y` position is **not** corrected by anything; it's purely the EKF's own integral of `vx` and `yaw` over time. That's the entire point: it replaces `ackermann_steering_controller`'s own dead-reckoned position estimate (which inherits error from the single-servo Ackermann approximation — see [ROS2 Jazzy: Core Topic Specifications](ros2_jazzy.md#core-topic-specifications)) with one integrated from a heading source the STM32-side IMU derives independently of the drivetrain.

| Input | Topic | Fused fields | Why only these |
| --- | --- | --- | --- |
| Wheel odometry | `/ackermann_steering_controller/odometry` | `vx` only | `x/y/yaw` from this source already bakes in the single-servo Ackermann approximation - not double-counted here. |
| IMU | `/imu/data` | `yaw`, `vyaw` | Both come from the STM32's gyro-Z (see `mdp_stm32/src/imu.c`) - `yaw` is bias-corrected gyro integration, `vyaw` is the instantaneous bias-corrected rate. Roll/pitch aren't fused: the bridge sets their covariance to `1e6` (`serial_bridge_node.cpp`) since they're never estimated, and `two_d_mode: true` discards them from the state entirely regardless. |

Other notable settings in `ekf.yaml`:

- **`two_d_mode: true`** — locks `z, roll, pitch, vz, vroll, vpitch, az` to zero. This is a ground vehicle on a flat competition arena floor; letting those drift on noise would only hurt.
- **`world_frame: odom`, single `ekf_node` instance** — `robot_localization`'s usual two-instance pattern (one EKF in `odom` frame fused only with continuous sensors like wheels/IMU, a second in `map` frame additionally fused with an absolute source like GPS/AMCL) collapses to just the first instance here, since there's no absolute localization source yet — Task 1 & 2 navigation is dead-reckoning + vision, not GPS.
- **`publish_tf: true`** — `ekf_node` becomes the sole broadcaster of `odom` → `base_link`. `real_controller.yaml` sets `enable_odom_tf: false` on `ackermann_steering_controller` for exactly this reason — it still publishes `/ackermann_steering_controller/odometry` as the EKF's input, it just no longer broadcasts the TF itself (two nodes broadcasting the same transform is a conflict, not additive).
- **`sensor_timeout: 0.5`** — matches the 500ms fail-safe window already used by the STM32 command timeout and the bridge's `/hardware_bridge/link_ok` watchdog, so the whole stack degrades on a consistent timescale if the serial link drops.

Fused pose is published on `/odometry/filtered` and consumed by autonomy nodes in place of raw wheel odometry.

For the verification checklist and the known EKF covariance issue, see [RPi: EKF Verification Checklist](index.md#ekf-verification-checklist-post-bringup).
