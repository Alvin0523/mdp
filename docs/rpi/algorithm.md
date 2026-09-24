---
icon: lucide/route
---

# Algorithm (`mdp_ros/src/mdp_algorithm`)

Path planning and the Task 1 & 2 autonomy state machines — running as ROS2 nodes inside `mdp_ros` on
the RPi, consuming Vision's detections and producing `/cmd_vel` for the RPi's kinematics stack.

!!! note "Still missing: worked Reeds-Shepp/TSP formulation and state machine diagrams"
    This page covers what's actually in the repo file-by-file, but the derivations behind
    `reeds_shepp_curves.py`/`hybrid_astar.py` and a diagram of the Task 1 & 2 state machines
    (`task1_runner.py`/`task2_runner.py`) aren't written up yet.

## What's actually in the repo

`mdp_algorithm/` (renamed 2026-09-03 from `mdp_planning`, flattened to sit directly under `src/` —
it was previously a container folder nesting a `mdp_planning` package one level deeper for no
reason, once `wayp_plan_tools` — see below — was removed there was no reason for the extra level)
is a single ROS2 (ament_python) package holding path planning + path following.

| File | Role |
| --- | --- |
| `collision_aware_planner.py` | `plan_route()` — top-level entry point tying the rest of this table together into a `List[Tuple[x,y,theta]]` (metres/radians) for `pure_pursuit_follower` |
| `occupancy_map.py` | `Obstacle`/`OccupancyMap` — grid representation, obstacle inflation, border inset, collision checking |
| `hamiltonian.py` | Obstacle visit ordering (brute-force/nearest-neighbour TSP, reachability-filtered) + `obstacle_to_checkpoint(_all)` (valid stand-off scan pose per obstacle) |
| `hybrid_astar.py` | `Node`/`HybridAStar` — 6-primitive forward/reverse × L/S/R search, Reeds-Shepp-heuristic-admissible |
| `reeds_shepp_curves.py` | Full 12-path-family Reeds-Shepp length calculation (the heuristic `hybrid_astar.py` uses) |
| `geometry_utils.py` / `motion_primitives.py` / `planning_constants.py` | Shared math + `Gear`/`Steering` enums + reconciled physical constants (see below) |
| `pure_pursuit_follower.py` | `PurePursuitController` (plain, ROS-free) + `PurePursuitFollower(Node)` (standalone wrapper, not launched by default) — tracks the planned path, converts steering angle → `/cmd_vel` angular.z |
| `reeds_shepp_planner.py` | Older Reeds-Shepp/Dubins + TSP planner — **no obstacle/collision awareness** (pure Dubins distance only). Superseded by `collision_aware_planner.py` for Task 1; not yet removed |
| `spline_planner.py` | Spline-based path planning, used by `task2_runner.py` (Task 2 unaffected by this port) |

**Provenance**: `occupancy_map.py`, `hamiltonian.py`, `hybrid_astar.py`, `reeds_shepp_curves.py`
were ported (2026-09-03) from a teammate's standalone `mdp_algo` package (pure-Python, ROS-free,
NUS SC2079-style: `OccupancyMap` → `Hamiltonian` → `HybridAStar`), with `pygame`/`matplotlib` and
the discrete open-loop command-string layer (`pathcommands.py`, `"SF020"`-style strings) dropped —
this project's `ackermann_steering_controller` already streams continuous, encoder-closed-loop
`/cmd_vel`, so re-adding an open-loop dead-reckoning execution layer would be a regression, not a
feature. 4 real bugs were found and fixed during the port (heuristic double-radian-conversion,
divide-by-zero on near-coincident poses, incomplete start-corner obstacle carve-out, unvalidated
checkpoint return point) — see `git log`/commit messages on these files for specifics.

`mdp_algorithm/wayp_plan_tools` (a general-purpose third-party waypoint/pursuit ROS2 package,
unrelated to the teammate's `mdp_algo`) was **removed 2026-09-03** — never referenced by any launch
file, and its standalone-node-with-its-own-`/cmd_vel`-publisher design doesn't fit this project's
single-coordinated-state-machine model anyway (`task1_runner.py` needs exactly one `/cmd_vel`
publisher so it can synchronize path-following with checkpoint stops/YOLO scans).

!!! warning "Known limitation — not yet safe to trust on the real course"
    - **Min turning radius is `25.3cm`** (`planning_constants.py`), now *derived* from a real
      protractor-measured steering angle rather than hand-set: the right side is the binding
      direction at `−29.5°`, and `14.33cm / tan(29.5°) = 25.3cm` — see
      [Servo Range & Steering Calibration](../stm32/tuning.md#servo-range-steering-calibration).
      This supersedes the earlier `43.34cm` (from `18.3°`) and a later hand-set `25.0cm` override;
      both of those rested on values that were inputs to WHEELTEC's cubic PWM fit, not wheel angles,
      and so were never valid inputs to the Ackermann relation. Consequence, now much reduced but
      not gone: **routes near tight arena corners may still fail to find any path** — a real
      kinematic limit, not a bug. The right-side limit is itself unconfirmed (the wheel was still
      tracking at the calibration ceiling), and if it opens up this radius only gets smaller, so
      planning stays conservative.
    - **Planning is slow**: ~50s confirmed for some individual legs even in open space. Likely too
      slow to run live between checkpoints as-is — not yet profiled/optimized.
    - `task1_runner.py`'s start pose is a placeholder (`(0.15, 0.15)`, one margin-width off both
      walls, replacing the previous `(0.0, 0.0)` — literally the wall corner, confirmed
      unreachable-from) — **not verified against the actual competition start-box convention**.

**Task 1 & 2 state machines** — `task1_runner.py` (now calls `mdp_algorithm.collision_aware_planner.plan_route()`
and drives via `PurePursuitController`, replacing a previous fixed-2.5s blind-forward placeholder
that never actually path-tracked) and `task2_runner.py` (imports `mdp_algorithm.spline_planner`)
live in `mdp_bringup/scripts/`, installed as `mdp_bringup` executables. Run via `pixi run task1` /
`pixi run task2`.

## How it fits into the system

Per the [Assessment & Checklist](../assessment_checklist.md) Module B requirements: display the
2.0m×2.0m arena, compute a Hamiltonian path visiting all 5 targets, and solve the TSP via Reeds-Shepp
curves to minimize run time. See [Subsystems](../index.md#subsystems) for
where this sits relative to Vision and RPi.

- **Inputs**: target detections from [Vision](vision.md), robot pose from
  `/odometry/filtered` (see [ROS2 EKF Localization](ros2_ekf_localization.md#this-robots-specific-fusion)).
- **Outputs**: `/cmd_vel`, consumed by `ackermann_steering_controller` on the RPi (see [ROS2 Control](ros2_control.md)).
- **Pixi tasks**: `pixi run task1` / `pixi run task2` (run alongside `pixi run real` or `pixi run sim`) — see [Quickstart: Pixi Task Reference](../quickstart.md#pixi-task-reference-mdp_ros).

## Coordinate convention: everything the planner touches is in the arena frame

`mdp_algorithm` works in exactly one coordinate system and has no notion of a TF frame at all: arena
coordinates, origin at the arena's bottom-left corner, axes along the arena walls, centimetres inside
`occupancy_map.py`/`hybrid_astar.py` and metres at the `mdp_bringup` boundary. There are no frame-name
strings and no `tf2` imports anywhere in the package, and a property test
(`test_frame_preservation.test_mdp_algorithm_has_no_frame_awareness`) enforces that — which frame the
arena is drawn in depends on how the robot was launched, not on how a route is planned.

`mdp_bringup` owns the frame question. In ROS terms the arena frame is `map` (see
[ROS2 EKF Localization: the `map` → `odom` → `base` chain](ros2_ekf_localization.md#the-map--odom--base-frame-chain)),
and the rules are:

- **Every arena-referenced publisher stamps the arena frame.** `task1_runner.py` reads it once from its
  `arena_frame` parameter (default `map`) and uses it for all eleven arena publish sites: the occupancy
  grid, grid lines, placement-zone and start-box outlines, obstacle cubes and labels, checkpoint arrows
  and labels, the path line strip, the Hybrid A* search-progress markers, and `/planned_path`. Stamping
  these `odom` is what drew the whole arena rotated by the start yaw and offset by the start position.
- **Body-frame data is not arena data.** `/cmd_vel` is a twist in `base_link` and stays that way. A
  blanket rename of every `frame_id` in the runner is a bug, not a fix.
- **Poses coming *in* get converted, not relabelled.** `/odometry/filtered` reports in `odom`. The
  runner looks up `arena_frame` ← `odom` in TF and transforms the pose before handing it to
  `PurePursuitController`, so the pose and the planned path are in one frame and the tracking error at
  `t=0` is zero rather than a constant rotation plus offset. `PurePursuitController` itself is
  frame-agnostic; its contract is just that pose and path agree. (The standalone `PurePursuitFollower`
  node in `pure_pursuit_follower.py` does *not* do this conversion — it tracks in the dead-reckoning
  frame, and its docstring says so.)
- **The start pose is declared once per launch file** and feeds all three of its consumers: the Gazebo
  spawn (or the physical placement it documents), the static `map` → `odom` transform, and the runner's
  `start_x`/`start_y`/`start_yaw` parameters, which are what the planner plans the first leg from. When
  those numbers disagreed, the arena rendered offset from the robot by the difference.

The tablet's obstacles arrive on a separate topic, `/obstacle_cells`, as integer grid cells `id:cx,cy,F`
(e.g. `1:0,1,N|2:9,8,W`; cells 0–19, origin bottom-left, obstacle centred in its cell). The runner handles both.

Obstacle coordinates on `/obstacle_setup` are arena metres, as they always were, and the planner's
numeric output for a given obstacle layout is unchanged by any of the above — the frame work is about
which frame the same numbers are declared to live in.
