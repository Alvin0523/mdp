---
icon: lucide/route
---

# Algorithm (`mdp_ros/src/mdp_algorithm`)

Path planning and the Task 1 & 2 autonomy state machines — running as ROS2 nodes inside `mdp_ros` on
the RPi, consuming Vision's detections and producing `/cmd_vel` for the RPi's kinematics stack.

!!! note "Stub page — fill in as the Algorithm subsystem's own detail lands here"
    This page currently only reflects what's actually in the repo. Add the actual Reeds-Shepp/TSP
    formulation, pure-pursuit tuning notes, and Task 1 & 2 state machine diagrams here as that work
    happens.

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
    - **Min turning radius is `43.34cm`** (`planning_constants.py`), derived from the STM32
      firmware's *current* right-side steering clamp (`18.3°`, confirmed conservative — see
      [Firmware Architecture](../stm32/tuning.md#servo-range-steering-calibration)), not the
      theoretical `22.5cm` a full `32.5°` lock would give. Confirmed consequence: **routes near
      tight arena corners can fail to find any path at all** — a real kinematic limit (a turning
      circle that wide can't escape a tight corner within the wall margin), not a bug. Revisit
      once the right-side servo fine-sweep happens and the clamp widens.
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
