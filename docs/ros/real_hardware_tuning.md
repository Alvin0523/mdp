---
icon: lucide/wrench
---

# Real Hardware Tuning Guide

How to carry the simulation work (unified `mini_akm_robot.urdf.xacro`, [ADR 0007](../architecture.md#0007-imu-fused-odometry-via-robot_localization-ekf) EKF fusion) over to the physical robot: measuring and tuning wheel friction, and activating IMU-fused odometry once `mdp_stm32` firmware supports it.

## 1. Tuning Gazebo Wheel Friction (`mu1` / `mu2`) Against Real Grip

**Where it lives**: `mdp_ros/src/mdp_description/urdf/mini_akm_robot.urdf.xacro`, the `<gazebo reference="lb_link">` / `rb_link` / `lf_link` / `rf_link` blocks (`mu1: 2.0`, `mu2: 0.4` currently — provisional, unmeasured). Also tracked in the [drivetrain physics table](../hardware.md#physical-drivetrain-geometry-urdf-physics-parameters).

**Rule**: these values must be tuned to match the *real* wheel-to-floor friction of the actual mini-AKM tires on the actual competition floor surface. Never detune them just to make Gazebo's visible turning cosmetically match `/tf` in Foxglove — that mismatch is expected odometry model error (see [troubleshooting](../troubleshooting.md)), not a friction bug, and "fixing" it by lowering friction makes the simulation behave *less* like the real robot, not more.

### 1.1 Measuring real static/kinetic friction

You don't need lab equipment — a simple tilt or drag test is enough for ODE's Coulomb friction model:

1. **Tilt (inclined plane) test** — place the robot (or a single wheel + known weight) on a sample of the actual competition floor material. Slowly raise one end until the wheel just starts to slide (not roll — block rotation, e.g. tape the wheel so it can't spin, to measure sliding friction rather than rolling resistance). Measure the tilt angle $\theta$ at the slip point:
   $$\mu = \tan(\theta)$$
2. **Drag (fish-scale) test** — with the wheel loaded at its normal operating weight $N$ (from the mass table in `docs/hardware.md`), pull it horizontally with a spring scale at constant slow speed just past the point it starts sliding. Read the pull force $F$ at the moment of sliding:
   $$\mu = \frac{F}{N}$$
3. Do this for both the **rolling/forward direction** (this maps to `mu1`, ODE's primary friction direction) and, if you can rig it, the **lateral/sideways direction** (`mu2`, secondary friction direction — relevant to the front wheels' scrub during steering, see the [kingpin-offset note](../troubleshooting.md)). If you can't isolate a lateral measurement, a reasonable starting ratio (consistent with the current provisional `mu2 = mu1 * 0.2`) is fine until you can validate it.

### 1.2 Applying the measured value

Update **all four** wheel blocks in the xacro consistently (`lb_link`, `rb_link`, `lf_link`, `rf_link`) — real hardware doesn't have per-wheel differences worth modeling separately here:

```xml
<gazebo reference="lf_link">
  <mu1>{measured forward μ}</mu1>
  <mu2>{measured lateral μ, or mu1 * 0.2 as a starting ratio}</mu2>
  <kp>1000000.0</kp>   <!-- contact stiffness, leave as-is unless you see penetration/bounce -->
  <kd>1.0</kd>          <!-- contact damping, leave as-is unless contacts feel too springy -->
</gazebo>
```

Then update the friction row in [`docs/hardware.md`](../hardware.md#physical-drivetrain-geometry-urdf-physics-parameters) from "*unmeasured — provisional*" to the measured value and how/when it was measured, so the next person doesn't have to re-derive it.

### 1.3 Validating the change

1. `pixi run build && pixi run sim`
2. Drive a straight line with `pixi run teleop` — confirm this still tracks (it should, translation doesn't depend on friction unless slip is introduced).
3. Drive a fixed, repeatable turn (e.g., hold a constant steering + speed for N seconds) in both sim and on the real robot; compare the resulting arc radius/heading change. You're not trying to make Foxglove's TF match Gazebo's visual (see Section 2 for that) — you're trying to make **Gazebo's physical turning behavior match the real robot's physical turning behavior** under the same commanded inputs. That's the actual sim-to-real fidelity goal.

---

## 2. Activating IMU-Fused Odometry (EKF) on Real Hardware

### 2.1 Current status

The EKF is implemented and running in simulation ([ADR 0007](../architecture.md#0007-imu-fused-odometry-via-robot_localization-ekf), see [Gazebo Simulation & Controllers](simulation.md#imu-fused-odometry-ekf)). It is **not yet active on real hardware** because `mdp_stm32` firmware doesn't publish either input the EKF needs:

- Hall encoder → `/joint_states` velocity/position feedback: **stub only**
- ICM-20948 IMU → `/imu/data`: **stub only**

Check `docs/architecture.md` Section 5 for current firmware status before starting this section — if those are still unchecked, there's nothing for the EKF to consume yet.

### 2.2 Prerequisites (firmware side)

Before any of the ROS-side steps below make sense, `mdp_stm32` needs to actually be publishing:

1. `/joint_states` (or whatever `topic_based_ros2_control`/the eventual hardware interface expects) with real position/velocity feedback for `left_joint`, `right_joint`, `lb_joint`, `rb_joint` from the Hall encoders.
2. `/imu/data` (`sensor_msgs/msg/Imu`) from the ICM-20948 over I2C2, at a reasonable rate (aim for ≥50Hz to comfortably feed a 50Hz EKF — the sim IMU runs at 100Hz).
3. Confirm both with `ros2 topic hz /joint_states` and `ros2 topic hz /imu/data` while running `pixi run hardware` (`real.launch.py`), *before* touching the EKF wiring.

### 2.3 Wiring `ekf_node` into `real.launch.py`

`src/mdp_bringup/config/ekf_real.yaml` already exists (prep-only) with `base_link_frame: base_link` matching `real_controller.yaml`'s `base_frame_id`. Once Section 2.2's prerequisites are met, wire it into `mdp_ros/src/mdp_bringup/launch/real.launch.py` the same way `sim.launch.py` does it:

```python
ekf_config = os.path.join(pkg_mdp_bringup, 'config', 'ekf_real.yaml')

ekf_node = Node(
    package='robot_localization',
    executable='ekf_node',
    name='ekf_filter_node',
    output='screen',
    parameters=[ekf_config, {'use_sim_time': False}]
)
```

Add `ekf_node` to the `LaunchDescription([...])` list, and set `enable_odom_tf: false` in `src/mdp_bringup/config/real_controller.yaml` (mirroring what was done for `ackermann_controller.yaml` in sim) so `ekf_node` becomes the sole `odom→base_link` TF publisher instead of the controller.

### 2.4 Tuning the EKF for real sensor noise

`ekf.yaml`/`ekf_real.yaml` were written with reasonable defaults for a simulated, low-noise IMU. Real ICM-20948 data will be noisier, so expect to tune:

- **`imu0_config`** — currently only fuses yaw (`orientation.z`) and yaw rate (`angular_velocity.z`). Leave it this way initially; adding accelerometer-derived velocity is usually *more* noise than signal for a ground vehicle unless you've validated it.
- **Process noise covariance** (`process_noise_covariance` in the yaml, not currently overridden from `robot_localization`'s defaults) — if the fused estimate lags behind real motion, increase the yaw/yaw-rate process noise terms; if it's jittery, decrease them.
- **`imu0_remove_gravitational_acceleration: true`** — keep this on; the ICM-20948's raw accelerometer will include gravity and this setting is required for `robot_localization` to compensate correctly, assuming the IMU driver publishes proper orientation-aware acceleration.

Iterate by watching `/diagnostics` and `ros2 topic hz /odometry/filtered` — the same "Failed to meet update rate!" warning seen during sim startup bursts (harmless if transient) becomes worth investigating if it's *persistent* on real hardware, since it usually means the EKF's `frequency` (50Hz) is set higher than what your actual sensor rates can sustain.

### 2.5 Validating against ground truth

Real hardware has no ground-truth pose feed (unlike the earlier temptation to bridge Gazebo's true pose in sim — see [ADR 0007](../architecture.md#0007-imu-fused-odometry-via-robot_localization-ekf)'s rationale for why that was explicitly avoided). Validate the fused pose the low-tech way instead:

1. Mark a start point and a known straight-line distance on the floor (tape measure). Drive it, compare `/odometry/filtered`'s reported displacement.
2. Mark a known turn (e.g., a 90° pivot or a fixed-radius arc using a string/compass on the floor). Drive it, compare the fused heading change (`tf2_echo odom base_link`) against the marked angle.
3. Repeat a few times and look at consistency, not just single-run accuracy — noisy IMU data can make one run look great and the next look worse.

---

## 3. Related Reading

- [Gazebo Simulation & Controllers](simulation.md) — how the EKF is wired in sim today.
- [Architecture Guide, ADR 0007](../architecture.md#0007-imu-fused-odometry-via-robot_localization-ekf) — why IMU fusion, not friction tuning, is the actual fix for heading drift.
- [Hardware Specs & Wiring](../hardware.md) — drivetrain geometry and physics parameter table (wheelbase, track widths, friction).
- [Troubleshooting](../troubleshooting.md) — the `/tf`-vs-Gazebo-turning gotcha this whole guide traces back to, plus a note on stray leftover ROS nodes if the robot ever "moves on its own."
