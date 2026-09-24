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

Only two of the fifteen state variables actually get outside correction here — `vx` (wheel odometry only) and `vyaw` (fused redundantly from **both** wheel odometry and the IMU). `x`/`y`/`yaw` position is **not** corrected by anything; it's purely the EKF's own integral of `vx` and `vyaw` over time. That's the entire point: it replaces `ackermann_steering_controller`'s own dead-reckoned position estimate (which inherits error from the single-servo Ackermann approximation — see [ROS2 Jazzy: Core Topic Specifications](ros2_jazzy.md#core-topic-specifications)) with one integrated from a heading rate the STM32-side IMU derives independently of the drivetrain.

| Input | Topic | Fused fields | Why |
| --- | --- | --- | --- |
| Wheel odometry | `/ackermann_steering_controller/odometry` | `vx`, `vyaw` | `x/y/yaw` from this source already bakes in the single-servo Ackermann approximation - not fused. `vyaw` here is the controller's own kinematic estimate from wheel speeds. |
| IMU | `/imu/data` | `vyaw` only | Bias-corrected raw gyro-Z rate (see `mdp_stm32/src/imu.c`). Orientation (`yaw`) is deliberately **not** fused from either source - the MCU's own gyro-integrated `yaw` has no drift correction, so treating it as a position measurement would just hand the EKF that same uncorrected integration a second time. Roll/pitch aren't fused: the bridge sets their covariance to `1e6` (`serial_bridge_node.cpp`) since they're never estimated, and `two_d_mode: true` discards them from the state entirely regardless. |

**Why `vyaw` comes from both sources, not just the IMU:** an earlier revision fused `vyaw` from the IMU alone on the reasoning that it was the better source. A hardware finding showed that made the IMU link a single point of failure - if it ever reported invalid/dead data, the EKF had *zero* yaw-rate input at all, and the robot would dead-reckon a straight line regardless of actual steering. `ekf.yaml` now fuses `vyaw` from both `odom0` and `imu0` simultaneously, weighted by each source's own covariance, so a dead IMU degrades yaw tracking instead of removing it entirely.

Other notable settings in `ekf.yaml`:

- **`two_d_mode: true`** — locks `z, roll, pitch, vz, vroll, vpitch, az` to zero. This is a ground vehicle on a flat competition arena floor; letting those drift on noise would only hurt.
- **`world_frame: odom`, single `ekf_node` instance** — `robot_localization`'s usual two-instance pattern (one EKF in `odom` frame fused only with continuous sensors like wheels/IMU, a second in `map` frame additionally fused with an absolute source like GPS/AMCL) collapses to just the first instance here, since there's no absolute localization source yet — Task 1 & 2 navigation is dead-reckoning + vision, not GPS.
- **`publish_tf: true`** — `ekf_node` becomes the sole broadcaster of `odom` → `base_link`. `real_controller.yaml` sets `enable_odom_tf: false` on `ackermann_steering_controller` for exactly this reason — it still publishes `/ackermann_steering_controller/odometry` as the EKF's input, it just no longer broadcasts the TF itself (two nodes broadcasting the same transform is a conflict, not additive).
- **`sensor_timeout: 0.5`** — matches the 500ms fail-safe window already used by the STM32 command timeout and the bridge's `/hardware_bridge/link_ok` watchdog, so the whole stack degrades on a consistent timescale if the serial link drops.

Fused pose is published on `/odometry/filtered` and consumed by autonomy nodes in place of raw wheel odometry.

For the verification checklist and the known EKF covariance issue, see [RPi: EKF Verification Checklist](index.md#ekf-verification-checklist-post-bringup).

## The `map` → `odom` → `base` frame chain

`/odometry/filtered` is an `odom`-frame message, and `odom` is created wherever the robot happened to
start, with identity orientation. The arena is a different frame: origin at the arena's bottom-left
corner, axes along the arena walls, which is the coordinate system the planner and every arena-referenced
marker already used. Those two frames coincide only if the robot starts at the arena origin facing
`+X`, which it never does, so they need an explicit edge between them — without one, drawing arena
coordinates in `odom` rotates the whole arena by the start yaw and offsets it by the start position
(that was the 90° Foxglove heading offset).

Per [REP-105](https://www.ros.org/reps/rep-0105.html) the arena frame is `map`, giving one chain:

```
map ──(static, = start pose)──> odom ──(dead reckoning)──> base_footprint / base_link
```

Each edge has exactly one owner, and the owners differ between sim and hardware:

| Edge | Sim (`task1_sim.launch.py`) | Hardware (`real.launch.py`) |
| --- | --- | --- |
| `map` → `odom` | `static_transform_publisher` (`map_to_odom_static_tf`), value = the Gazebo spawn pose | `static_transform_publisher` (`map_to_odom_static_tf`), value = where the car is placed in the start box |
| `odom` → base | `ackermann_steering_controller` (`ackermann_controller.yaml`, `enable_odom_tf: true`) | `ekf_filter_node` (`ekf.yaml`, `publish_tf: true`); the controller stands down via `enable_odom_tf: false` in `real_controller.yaml` |
| `/odometry/filtered` publisher | `ekf_filter_node` with `config/ekf_sim.yaml`, `publish_tf: false` — estimator only, broadcasts no TF | `ekf_filter_node` with `config/ekf.yaml` |

Two details that trip people up:

- **The base link differs between the two.** The sim URDF (`mini_akm_robot.urdf`) roots at
  `base_footprint`, the hardware URDF (`mini_akm_real_robot.urdf`) roots at `base_link`, so the child of
  `odom` is not the same link in both graphs. `ekf_sim.yaml` sets `base_link_frame: base_footprint` to
  match the sim, and its `base_frame_id` agrees with `ackermann_controller.yaml`.
- **`ekf_sim.yaml` is a separate file, not an edit of `ekf.yaml`.** Sim needs `publish_tf: false` (the
  controller already owns `odom` → `base_footprint` there, and one edge gets one broadcaster),
  `use_sim_time: true`, the sim base link, and **no** `imu0` — nothing bridges an IMU out of Gazebo, and a
  configured-but-silent input is not the same thing to `robot_localization` as an absent one: it
  interacts with `sensor_timeout` and leaves the filter running prediction-only in stretches. Hardware
  localization is untouched by any of this.

`map` → `odom` is static because there is no absolute localization source to correct it with. All drift
therefore accumulates in `odom` → `base`, which is where REP-105 wants it. If an absolute source is
added later (AMCL against a LiDAR scan, a fiducial, an overhead camera), the usual
`robot_localization` two-instance pattern applies: a second EKF with `world_frame: map` takes over
broadcasting `map` → `odom` and the static publisher is deleted. Nothing else changes — no `frame_id`
in any node moves, because the arena frame is already `map` and consumers already look the transform
up in TF rather than assuming it.

Consumers should do exactly that. `task1_runner.py` transforms each incoming `/odometry/filtered` pose
into its `arena_frame` parameter with `lookup_transform` + `do_transform_pose` before it feeds the path
follower, and drops the update (keeping the previous pose, warning at a throttled rate) if the lookup
fails. It deliberately has no raw-pose fallback: falling back would silently reintroduce the full
start-pose error for as long as TF was unavailable.
