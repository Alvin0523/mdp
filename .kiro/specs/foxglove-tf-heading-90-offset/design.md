# Foxglove TF Heading 90-Degree Offset Bugfix Design

## Overview

Arena-referenced data (occupancy grid, grid lines, placement-zone and start-box outlines, obstacle
cubes/labels, checkpoint arrows/labels, planned path, path markers, search progress) is published with
`frame_id: 'odom'` while carrying arena-origin coordinates, and nothing publishes a transform relating
the arena to `odom`. Because the Task 1 sim spawns the robot at `(0.15, 0.15, pi/2)`, `odom` is created
rotated 90 degrees and offset 0.15 m diagonally from the arena, so Foxglove draws the arena rotated
relative to the robot. The same conflation reaches control: `task1_runner` feeds `/odometry/filtered`
(an `odom`-frame pose) straight into `PurePursuitController`, whose path is in arena coordinates.

The fix reconciles the frames rather than relabelling markers:

1. Adopt `map` as the arena frame (arena bottom-left corner, axes aligned with the planner's
   coordinates), keeping the REP-105 chain `map -> odom -> base_link`.
2. Publish a single static `map -> odom` transform derived from the robot's known start pose in the
   arena. One owner per environment: the sim launch (start pose = the spawn arguments) and the
   hardware launch (start pose = where the car is placed in the start box).
3. Re-stamp every arena-referenced publisher with the arena frame instead of `odom`. Coordinates are
   unchanged - only `frame_id` changes.
4. Give `task1_runner` an arena-frame pose by transforming the incoming `/odometry/filtered` pose
   through TF into the arena frame, instead of consuming it raw.
5. Add the missing `ekf_node` to `task1_sim.launch.py`. Without it `/odometry/filtered` has no
   publisher in sim at all, so the pose the follower uses is frozen at its constructor default and
   neither the bug nor the fix can be validated end to end.

No production code is changed by this document; it specifies what the fix must do.

## Glossary

- **Bug_Condition (C)**: Arena-referenced data is published in a frame that is not the arena frame
  (`frame_id: 'odom'` with arena coordinates), no transform in the TF tree relates the arena frame to
  `odom`, and the robot's start pose in the arena is not the identity pose.
- **Property (P)**: The rendered robot pose expressed in the arena frame equals the robot's true
  arena pose - for the reported start pose, heading along arena +Y at `(0.15, 0.15)` inside the drawn
  start box.
- **Preservation**: Gazebo rendering, planner numeric output, the single `odom -> base` broadcaster,
  continuity of dead reckoning, `/obstacle_setup` interpretation, and the vision/Bluetooth/`/start_run`
  paths must all be unchanged.
- **arena frame**: The frame whose origin is the arena's bottom-left corner with +X along the arena's
  east edge and +Y along its north edge. This is the coordinate system `mdp_algorithm`'s
  `occupancy_map.py` / `hybrid_astar.py` already work in (in centimetres) and that `/obstacle_setup`
  positions are given in (in metres). Realised as the TF frame named `map`.
- **`odom` frame**: The dead-reckoning frame created at the robot's spawn/power-on pose with identity
  orientation. Broadcast to base by `ackermann_steering_controller` in sim (`enable_odom_tf: true`,
  `base_frame_id: base_footprint`, `mdp_bringup/config/ackermann_controller.yaml`) and by
  `ekf_node` on hardware (`publish_tf: true`, `base_link_frame: base_link`,
  `mdp_bringup/config/ekf.yaml`, with `enable_odom_tf: false` in `real_controller.yaml`).
- **`start_pose`**: `(0.15, 0.15, pi/2)` in the arena frame - `task1_runner.control_loop`'s planning
  start pose and the `-x -y -Y` spawn arguments in `task1_sim.launch.py`. Documented there as a
  placeholder for the real start-box convention.
- **`T_map_odom`**: The static transform this fix introduces, equal to the robot's arena-frame start
  pose composed with the inverse of its `odom`-frame pose at `odom` creation (identity), i.e. numerically
  the start pose itself.

## Bug Details

### Bug Condition

The bug manifests whenever arena-referenced visualization and the planned path are published with
`frame_id: 'odom'` while holding arena-origin coordinates, no `arena -> odom` transform exists, and the
robot's start pose in the arena is not `(0, 0, 0)`. In `task1_runner.py` there are ten such publish
sites (`_publish_occupancy_grid`, `_publish_grid_lines` x3 markers, `_publish_obstacle_markers` x2,
`_publish_checkpoint_markers` x2, `_publish_current_path`, `_publish_path_markers`,
`_publish_search_progress`), each hardcoding `'odom'`.

**Formal Specification:**

```
FUNCTION isBugCondition(input)
  INPUT: input of type VisualizationState
    // input.robot_start_pose_in_arena : (x, y, yaw) of the robot at odom-frame creation
    // input.arena_marker_frame        : frame_id used for arena-referenced markers/grid/path
    // input.tf_tree                   : set of published transforms
  OUTPUT: boolean

  RETURN input.arena_marker_frame = 'odom'
         AND NOT existsTransform(input.tf_tree, arena_frame, 'odom')
         AND (input.robot_start_pose_in_arena.yaw != 0
              OR input.robot_start_pose_in_arena.x != 0
              OR input.robot_start_pose_in_arena.y != 0)
END FUNCTION
```

### Examples

- Task 1 sim, spawn `(0.15, 0.15, pi/2)`: Gazebo shows the car facing the arena's +Y edge; Foxglove
  shows it facing the drawn arena's +X edge. Expected: both agree on +Y.
- Same run, position: the robot's TF origin renders at the drawn arena's `(0, 0)` corner, outside the
  40 cm start-box outline. Expected: at `(0.15, 0.15)`, inside the box.
- Obstacle at `/obstacle_setup` `(1.0, 1.0)`: the cube marker renders at a point that is `(1.0, 1.0)`
  in `odom`, which is arena `(0.15 - 1.0, 0.15 + 1.0) = (-0.85, 1.15)` - a 1.6 m error against the
  physical Gazebo obstacle. Expected: coincident with the Gazebo obstacle.
- Task runner control: `/odometry/filtered` reports `~(0, 0, 0)` at rest while the follower's first
  path waypoint is arena `(0.15, 0.15, pi/2)`, so pure pursuit's lookahead bearing is wrong by 90
  degrees from the first tick. Expected: zero tracking error at the start pose.
- Edge case, spawn `(0, 0, 0)`: `T_map_odom` is identity, arena and `odom` coincide, and rendering is
  already correct today. Expected: unchanged output (this is the `NOT C` branch, requirement 3.1).

## Expected Behavior

### Preservation Requirements

**Unchanged Behaviors:**

- Gazebo's own rendering of robot and arena (requirement 3.2) - Gazebo is the reference, untouched.
- `mdp_algorithm` planner numerics: occupancy grid contents, visiting order, checkpoints and Hybrid A*
  legs for a given obstacle setup (3.3). The planner keeps working purely in arena centimetres; no
  frame concept enters `mdp_algorithm`.
- Exactly one broadcaster of `odom -> base_footprint` / `odom -> base_link`:
  `ackermann_steering_controller` in sim, `ekf_node` on hardware (3.4). The new `map -> odom`
  broadcaster must not touch that edge, and adding `ekf_node` to the sim launch must not create a
  second one.
- Continuous, jump-free `odom -> base` TF and `/odometry/filtered` output (3.5).
- `/obstacle_setup` metres interpreted as arena coordinates with obstacle centres at the given point
  (3.6).
- Camera, YOLO detection, Bluetooth reporting and `/start_run` gating (3.7).

**Scope:**

All inputs that do NOT satisfy the bug condition must be completely unaffected. This includes:

- Runs where the robot starts at arena `(0, 0, 0)`, where `T_map_odom` is identity.
- Everything downstream of the planner's arena-coordinate math (numbers, logs, Bluetooth strings).
- Data that is legitimately robot-referenced and stays so: `/cmd_vel` (`frame_id: 'base_link'`),
  `/joint_states`, `/imu/data` (`imu_link`), camera topics, and `/odometry/filtered` itself, which
  remains an `odom`-frame message.

The expected correct behavior is stated in Correctness Properties below.

## Hypothesized Root Cause

The root cause is already localised with high confidence: two distinct frames are treated as one. The
open questions are about which sub-causes contribute how much, and they decompose as follows.

1. **Missing arena frame in the TF tree**: `ekf.yaml` declares `map_frame: map` but marks it unused
   (`world_frame: odom`, no absolute localization source), and nothing else broadcasts anything above
   `odom`. `odom` is therefore the tree root, so a renderer has no choice but to draw
   `frame_id: 'odom'` data directly in the dead-reckoning frame. This is the primary cause of both the
   rotation and the position offset.

2. **Arena data mislabelled**: every arena-referenced publisher in `task1_runner.py` hardcodes
   `'odom'`. Even after `map -> odom` exists, the arena would still be drawn in the wrong place until
   these are re-stamped. `task2_runner.py`'s `/planned_path` has the same hardcoded `'odom'`; its
   waypoints are carpark-relative rather than arena-origin, so it is a separate convention question
   (see Fix Implementation, out-of-scope note).

3. **Control-side frame conflation**: `task1_runner.odom_callback` assigns the raw
   `/odometry/filtered` pose to `self.current_pose` and to `follower.update_pose`, with no transform.
   Since the planner's paths are arena coordinates, this applies the same rotation/translation error to
   tracking (requirement 1.5). This is an independent instance of the same root cause, not a
   consequence of the visualization one.

4. **`/odometry/filtered` has no publisher in the Task 1 sim**: `real.launch.py` launches `ekf_node`;
   `task1_sim.launch.py` does not, and nothing else publishes `/odometry/filtered` (the sim controller
   publishes `/ackermann_steering_controller/odometry`). `odom_callback` therefore never fires in sim,
   leaving `current_pose` at its constructor value `(0.0, 0.0, pi/2)` and the follower's pose at
   `(0, 0, 0)` forever. This does not cause the visual bug, but it means the sim currently cannot
   demonstrate or validate cause 3 at all: the pose is not merely rotated, it is absent. It must be
   fixed before any control-side fix checking is meaningful.

5. **Start-pose duplication**: the arena start pose exists as three independent literals - the spawn
   arguments in `task1_sim.launch.py`, `start_pose` in `task1_runner.control_loop`, and (after the fix)
   `T_map_odom`. If they disagree, the offset returns in a subtler form. The fix should make
   `T_map_odom` and the planning start pose read from one declared source.

## Correctness Properties

Property 1: Bug Condition - Arena and robot render in agreement

_For any_ input where the bug condition holds (`isBugCondition` returns true), the fixed system SHALL
publish a TF chain and frame labels such that the robot's pose expressed in the arena frame equals its
true arena pose: heading equal to `robot_start_pose_in_arena.yaw` (arena +Y for a `pi/2` start) and
position equal to `(robot_start_pose_in_arena.x, robot_start_pose_in_arena.y)`, inside the drawn start
box, with obstacle/checkpoint/path geometry coincident with the physical Gazebo arena.

**Validates: Requirements 2.1, 2.2, 2.3, 2.4**

Property 2: Preservation - Non-buggy inputs render and behave identically

_For any_ input where the bug condition does NOT hold (`isBugCondition` returns false - in particular a
robot starting at arena `(0, 0, 0)`, where `T_map_odom` is identity), the fixed system SHALL produce the
same rendered scene and the same numeric behavior as the original system, preserving Gazebo rendering,
planner output, `odom -> base` continuity and single-broadcaster ownership, `/obstacle_setup`
interpretation, and the vision/Bluetooth/`/start_run` paths.

**Validates: Requirements 3.1, 3.2, 3.3, 3.4, 3.5, 3.6, 3.7**

Property 3: Bug Condition - Path tracking is frame-consistent

_For any_ input where the bug condition holds, the pose the path follower consumes SHALL be expressed
in the same frame as the planned path, so that the cross-track and heading error measured at the start
pose contains no constant rotation or translation offset.

**Validates: Requirements 2.5**

Property 4: Preservation - Single `odom -> base` broadcaster

_For any_ configuration (sim or hardware, bug condition or not), the TF tree SHALL contain exactly one
broadcaster of `odom -> base_footprint`/`base_link`, and the newly added `map -> odom` broadcaster SHALL
be the only publisher of that edge.

**Validates: Requirements 3.4, 3.5**

## Fix Implementation

### Decision 1: frame convention

Use `map` as the arena frame name, with the arena's bottom-left corner as origin and axes matching the
planner's arena coordinates. Chain: `map -> odom -> base_footprint`/`base_link` -> robot links.

Rationale:

- REP-105 assigns exactly this role to `map`: a fixed, non-drifting world frame above the continuous
  `odom` frame. `ekf.yaml` already declares `map_frame: map` and documents it as reserved-but-unused,
  so this fills a slot the config anticipated instead of inventing a parallel convention.
- `map` is the default fixed frame in Foxglove and RViz, so the arena becomes the natural display root
  with no per-user panel configuration.
- It keeps the door open for the second `robot_localization` instance
  (`docs/rpi/ros2_ekf_localization.md`) if an absolute localization source is ever added: that instance
  would publish `map -> odom` as a correction and would simply replace the static broadcaster, with no
  change to any `frame_id`.

Alternative considered and rejected: initialise `odom` at the arena pose (effectively renaming `odom`
to the arena frame). That would need an initial-pose facility that neither
`ackermann_steering_controller` nor the current EKF setup provides, it would make a drifting frame
pretend to be a fixed one, and it would break requirements 3.4/3.5 rather than satisfy 2.3. Also
rejected: a separate name such as `arena`, which buys nothing over `map` while diverging from REP-105
and from the existing `ekf.yaml` comment.

### Decision 2: who broadcasts `map -> odom`

`T_map_odom` = (robot's arena-frame start pose) composed with (robot's `odom`-frame pose at `odom`
creation)^-1. `odom` is created at the spawn/power-on pose with identity, so `T_map_odom` is numerically
the start pose: translation `(0.15, 0.15, 0)`, yaw `pi/2`.

Because there is no absolute localization source, this transform is static for the whole run. All dead
reckoning error accumulates inside `odom -> base`, which is exactly where REP-105 puts it; `map -> odom`
would only need to become dynamic once something can correct absolute pose.

Ownership, one broadcaster per environment:

| Environment | Owner | Start pose source |
| --- | --- | --- |
| Task 1 sim (`task1_sim.launch.py`) | `static_transform_publisher` node added to that launch file | The same declared start-pose values used for the `create` spawn arguments (`-x -y -Y`) |
| Hardware (`real.launch.py`, `task:=1`) | `static_transform_publisher` node added to that launch file | Launch arguments (e.g. `start_x`, `start_y`, `start_yaw`) defaulting to the documented start pose, overridable per run to match actual placement in the 40 cm start box |

Notes:

- Do not give this to `ekf_node`. `world_frame` stays `odom` and `publish_tf` keeps owning
  `odom -> base_link` on hardware; making the EKF also emit `map -> odom` would require the two-instance
  pattern and an absolute source it does not have.
- Do not give this to `robot_state_publisher` or the Gazebo TF bridge; both already own other edges.
- The start pose must be declared once per launch file and reused for both the spawn arguments and the
  static transform, addressing root cause 5. `task1_runner`'s planning `start_pose` should read the same
  values via parameters (`start_x`, `start_y`, `start_yaw`) rather than keeping its own literal.

### Decision 3: `frame_id` changes

**File**: `mdp_ros/src/mdp_bringup/scripts/task1_runner.py`

Introduce one `arena_frame` parameter (declared in `_declare_viz_params`, default `'map'`, documented
alongside the other visualization parameters in `mdp_bringup/config/occupancy_grid_viz.yaml`) and use it
at every arena-referenced publish site, replacing the hardcoded `'odom'`:

| Publisher | Message | Current `frame_id` | New `frame_id` |
| --- | --- | --- | --- |
| `_publish_occupancy_grid` | `nav_msgs/OccupancyGrid` | `odom` | `arena_frame` |
| `_publish_grid_lines` (`grid_lines`, `placement_zone_outline`, `start_box_outline`) | `Marker` x3 | `odom` | `arena_frame` |
| `_publish_obstacle_markers` (cube + label) | `Marker` x2 per obstacle | `odom` | `arena_frame` |
| `_publish_checkpoint_markers` (arrow + label) | `Marker` x2 per checkpoint | `odom` | `arena_frame` |
| `_publish_current_path` | `nav_msgs/Path` | `odom` | `arena_frame` |
| `_publish_path_markers` | `MarkerArray` | `odom` | `arena_frame` |
| `_publish_search_progress` | `MarkerArray` | `odom` | `arena_frame` |

Coordinate values are untouched - the numbers were always arena coordinates. `/cmd_vel` keeps
`frame_id: 'base_link'` (it is a body-frame twist, not arena data).

Out of scope for this fix, to be flagged rather than silently changed: `task2_runner.py`'s
`/planned_path` also hardcodes `'odom'`, but its slalom waypoints are expressed relative to the carpark
start rather than an arena corner, so the correct label there is a separate convention decision. Its
existing `lookup_transform('odom', 'base_footprint', ...)` ground-truth read stays valid unchanged.

### Decision 4: how the task runner gets an arena-frame pose

Use TF, keeping `/odometry/filtered` as the trigger and timestamp source:

- Add a `tf2_ros.Buffer` + `TransformListener` to `task1_runner` (the same pattern `task2_runner`
  already uses).
- In `odom_callback`, wrap the incoming pose as a `PoseStamped` with the message's own header and
  transform it into `arena_frame` using the buffer at that stamp (`tf2_geometry_msgs`
  `do_transform_pose` with `lookup_transform(arena_frame, msg.header.frame_id, msg.header.stamp)`).
  Assign the transformed `(x, y, yaw)` to `self.current_pose` and `follower.update_pose`.
- On lookup failure (TF not yet available, extrapolation), skip the update and keep the previous pose
  rather than falling back to the untransformed one; a silent raw fallback would reintroduce the bug
  intermittently. Log at throttled warn level.

Why this rather than looking up `arena_frame -> base_footprint` directly: the odometry callback already
sets the control rate and gives a well-defined stamp, `use_sim_time` alignment with `/clock` is
inherited from the message, and the base frame name differs between sim (`base_footprint`) and hardware
(`base_link`) - transforming the message avoids hardcoding either. Because `map -> odom` is static,
this is numerically identical to a direct `map -> base` lookup while being robust to the timing of the
`odom -> base` broadcast.

Why not transform the path into `odom` instead: the path, checkpoints and occupancy grid are all arena
artefacts, and `odom` drifts. Moving the single pose into the arena frame is one conversion; moving
every planned artefact into a drifting frame is many conversions that go stale.

`PurePursuitController` itself needs no change - it is frame-agnostic, and the contract becomes "path
and pose must both be in the arena frame". The standalone `PurePursuitFollower` node keeps its raw
`/odometry/filtered` subscription (it is not in the default launch graph); its docstring should state
that it operates in `odom` and is not arena-frame aware.

### Decision 5: the missing EKF in the Task 1 sim launch

Add `ekf_node` to `task1_sim.launch.py` so `/odometry/filtered` actually exists in sim, with
`use_sim_time: true` and a sim-specific parameter overlay, because `ekf.yaml` as written targets
hardware:

- `publish_tf: false` in sim. `ackermann_controller.yaml` has `enable_odom_tf: true` with
  `base_frame_id: base_footprint`, so the controller already owns `odom -> base_footprint`; letting the
  EKF also publish `odom -> base_link` would create the second broadcaster requirement 3.4 forbids. In
  sim the EKF's job is only to produce `/odometry/filtered` on the topic the runner subscribes to.
- `imu0` is not available in sim (no IMU is bridged from Gazebo), so the sim overlay must drop the
  `imu0` input or accept that the filter runs on `odom0` alone. Leaving a configured-but-silent `imu0`
  interacts with `sensor_timeout: 0.5` and produces prediction-only stretches - noise in any
  validation run.
- `use_sim_time: true` (the shipped `ekf.yaml` sets `false`), matching every other node in that launch.

Implement as a separate `config/ekf_sim.yaml` overlay rather than editing `ekf.yaml`, so hardware
behavior is provably untouched (requirement 3.5).

Effect on validation: until this is in place, sim fix-checking of Property 3 is impossible - the pose
the follower sees is a constant, so tracking error is not merely rotated but meaningless, and a passing
visual check would say nothing about control. Property 1 (visualization) can be checked without the
EKF, since it depends only on TF and `frame_id`. A lighter interim option is relaying
`/ackermann_steering_controller/odometry` to `/odometry/filtered`, which unblocks Property 3 checking
without exercising the filter; it is acceptable as a stopgap but diverges from the hardware graph and
should not be the final state.

### Summary of files touched by the fix

| File | Change |
| --- | --- |
| `mdp_bringup/launch/task1_sim.launch.py` | Declare start pose once; use it for the `create` spawn args; add `static_transform_publisher` for `map -> odom`; add `ekf_node` with the sim overlay; pass `start_*` params to `task1_runner` |
| `mdp_bringup/launch/real.launch.py` | Add `start_x`/`start_y`/`start_yaw` launch arguments; add `static_transform_publisher` for `map -> odom`; pass `start_*` params to `task1_runner` |
| `mdp_bringup/config/ekf_sim.yaml` (new) | Sim EKF overlay: `publish_tf: false`, `use_sim_time: true`, no `imu0` |
| `mdp_bringup/config/occupancy_grid_viz.yaml` | Document the new `arena_frame` parameter (default `map`) |
| `mdp_bringup/scripts/task1_runner.py` | `arena_frame` parameter at all ten arena publish sites; TF buffer/listener; transform `/odometry/filtered` into the arena frame; read `start_pose` from parameters |
| `mdp_bringup/package.xml` | Add `tf2_ros`, `tf2_geometry_msgs` exec dependencies if not already present |
| `docs/rpi/ros2_ekf_localization.md`, `docs/rpi/algorithm.md` | Document the `map -> odom -> base` chain, who broadcasts it in sim vs hardware, and the arena-frame convention for planner/visualization data |

## Testing Strategy

### Validation Approach

Two phases. First surface counterexamples on the UNFIXED code to confirm the root-cause decomposition
(especially causes 3 and 4, which the current sim cannot exercise). Then verify the fix satisfies the
fix-checking properties and that non-buggy inputs are untouched.

Tooling: the workspace has no test suite yet. `pixi run test` maps to `colcon test`, so tests go in
`src/mdp_algorithm/test/` and `src/mdp_bringup/test/` as pytest files. Property-based tests need
`hypothesis`, which is not yet in `pixi.toml` - adding it is part of the test task, not an assumption.
The frame math should be extracted into a small pure helper (compose/invert a 2D pose transform) so it
is testable without a ROS graph; that helper is the PBT surface.

### Exploratory Bug Condition Checking

**Goal**: Surface counterexamples demonstrating the bug before implementing the fix, and confirm or
refute each hypothesized root cause. A refutation sends us back to re-hypothesize.

**Test Plan**: Launch `pixi run sim1` plus `pixi run foxglove use_sim_time:=true` on the unfixed code
and record TF, frame labels and pose values. Pair each observation with the root cause it discriminates.

**Test Cases**:

1. **TF tree has no arena frame** (root cause 1): `ros2 run tf2_tools view_frames` and
   `ros2 topic echo /tf_static` - expect `odom` as root, no `map`, no arena-to-`odom` edge (will fail an
   assertion that such an edge exists).
2. **Arena data mislabelled** (root cause 2): `ros2 topic echo --once /occupancy_grid`,
   `/obstacle_markers`, `/grid_markers`, `/checkpoint_markers`, `/planned_path` - expect
   `frame_id: odom` on all of them alongside arena-origin coordinates (will fail).
3. **Rendered offset is exactly the start pose** (root causes 1+2): compare the robot TF origin against
   the `start_box_outline` marker in Foxglove - expect the robot at the drawn `(0, 0)` corner and its
   heading arrow 90 degrees clockwise of arena +Y, i.e. offset equal to `(0.15, 0.15, pi/2)` (will fail).
4. **`/odometry/filtered` has no publisher in sim** (root cause 4): `ros2 topic info /odometry/filtered`
   and `ros2 topic echo /odometry/filtered` - expect zero publishers and no messages, and `task1_runner`
   holding `current_pose == (0.0, 0.0, pi/2)` (its constructor default) after `go` (will fail).
5. **Tracking error at the start pose** (root cause 3): after fixing case 4 with the relay stopgap,
   compare `follower.current_pose` against the first waypoint of `leg_paths[0]` - expect a heading error
   of about `pi/2` and a position error of about 0.21 m at `t=0` (will fail).
6. **Edge case, zero start pose** (`NOT C`): relaunch with spawn `(0, 0, 0)` and the runner's
   `start_pose` set to `(0, 0, 0)` - expect the arena and robot to already agree. If this case also
   shows a rotation, the root-cause analysis is wrong and must be redone.

**Expected Counterexamples**:

- All arena-referenced topics carry `frame_id: odom` with arena coordinates; no `map` frame exists.
- The robot renders at the arena corner facing arena +X for a `pi/2` spawn.
- `/odometry/filtered` is silent in sim, so the follower's pose never updates.
- Possible causes: missing `map -> odom` broadcaster, hardcoded `'odom'` labels, untransformed
  odometry consumed as an arena pose, `ekf_node` absent from the sim launch.

### Fix Checking

**Goal**: For all inputs where the bug condition holds, the fixed system produces the expected
behavior.

**Pseudocode:**

```
FOR ALL input WHERE isBugCondition(input) DO
  rendered := renderScene_fixed(input)
  ASSERT rendered.robot_heading_in_arena_frame  = input.robot_start_pose_in_arena.yaw
     AND rendered.robot_position_in_arena_frame = (input.robot_start_pose_in_arena.x,
                                                   input.robot_start_pose_in_arena.y)
     AND trackingError_fixed(input).rotation_offset    = 0
     AND trackingError_fixed(input).translation_offset = 0
END FOR
```

Realised as: for a generated start pose `(x, y, yaw)`, assert that composing the published
`T_map_odom` with the `odom -> base` transform at `odom` creation reproduces that arena pose exactly,
and that a pose fed through the runner's arena-frame conversion equals the arena pose the planner used.

### Preservation Checking

**Goal**: For all inputs where the bug condition does NOT hold, the fixed system produces the same
result as the original.

**Pseudocode:**

```
FOR ALL input WHERE NOT isBugCondition(input) DO
  ASSERT renderScene_original(input) = renderScene_fixed(input)
END FOR
```

**Testing Approach**: Property-based testing suits preservation here because the interesting parameter
is a continuous 3-DOF start pose and the `NOT C` branch has a sharp boundary at the identity pose:
generated cases explore it far more cheaply than hand-written examples, and the identity case is where
a regression would be silent.

**Test Plan**: Record the unfixed behavior for the identity start pose and for the non-frame paths
first, then write tests asserting those recordings still hold after the fix.

**Test Cases**:

1. **Identity start pose renders unchanged**: with start pose `(0, 0, 0)`, `T_map_odom` is identity and
   arena coordinates equal `odom` coordinates, so every published geometry value matches the unfixed
   run byte for byte (only `frame_id` differs, and it now names a frame that coincides with `odom`).
2. **Planner numerics unchanged**: run `plan_visiting_order` / `plan_leg` on
   `config/test_obstacles.yaml` before and after; assert identical visiting order, checkpoints,
   unreachable list and occupancy grid. `mdp_algorithm` must not gain any frame awareness.
3. **Single `odom -> base` broadcaster**: parse `/tf` for the `odom -> base_footprint` (sim) and
   `odom -> base_link` (hardware) edges and assert exactly one publisher each, before and after adding
   `ekf_node` to the sim launch.
4. **Dead-reckoning continuity**: drive a leg and assert no discontinuity in `odom -> base` or
   `/odometry/filtered` beyond the pre-fix baseline; `map -> odom` is static and must never jump.
5. **`/obstacle_setup` interpretation unchanged**: publish the same setup string and assert obstacle
   centres land on the given coordinates in arena metres.
6. **Vision/Bluetooth/`go` unchanged**: assert `/yolo_result` handling, `TARGET,...`/`ROBOT,...`
   Bluetooth strings and `/start_run` accept/reject behavior per state are identical.
7. **Hardware config untouched**: assert `ekf.yaml` is unmodified and that the sim overlay is a separate
   file.

### Unit Tests

- Pose-transform helper: `T_map_odom` from a start pose, composition and inversion, yaw normalisation
  across the `+/-pi` wrap.
- `task1_runner`'s arena-frame conversion: given a synthetic `map -> odom` and an `odom`-frame odometry
  message, the resulting `current_pose` equals the expected arena pose; on a TF lookup failure the
  previous pose is retained and no raw fallback occurs.
- Every arena publisher uses the `arena_frame` parameter: construct the node with
  `arena_frame:='test_frame'` and assert all ten publish sites stamp it (guards against a missed
  hardcoded `'odom'`).
- Start-pose single-source: the runner's planning `start_pose` equals the `start_*` parameters.

### Property-Based Tests

- For random start poses `(x, y, yaw)` in the arena with `yaw` over `[-pi, pi]`: composing the derived
  `T_map_odom` with the identity `odom -> base` reproduces the arena start pose within tolerance
  (fix checking, Property 1).
- For random start poses and random `odom`-frame poses: transforming into the arena frame and back
  returns the original pose (round-trip invariant), and the transform preserves relative distances and
  relative bearings (a rigid transform introduces no scale or shear).
- For the identity start pose and random arena-coordinate marker inputs: fixed and original published
  geometry values are equal (preservation checking, Property 2).
- For random start poses: the number of publishers of the `odom -> base` edge stays exactly one
  (Property 4).

### Integration Tests

- Full `pixi run sim1` + `go` run with the fix: robot and arena agree in Foxglove throughout, the robot
  starts inside the drawn start box, and marker geometry stays coincident with the Gazebo arena while
  driving.
- Task runner end to end: with `ekf_node` in the sim launch, `/odometry/filtered` flows, the arena-frame
  pose tracks the planned path, and tracking error at the start pose is within tolerance instead of 90
  degrees off (Property 3).
- Context/config switching: sim (`base_footprint`, controller-owned `odom -> base`) and hardware
  (`base_link`, EKF-owned `odom -> base`) both resolve the arena-frame pose through the same code path,
  with the `map -> odom` owner differing per launch file.
- Non-default start pose: override `start_x`/`start_y`/`start_yaw` on the hardware launch to a different
  in-box placement and confirm the rendered robot and the planned path both move with it consistently.
