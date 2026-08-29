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

`mdp_algorithm/` is a folder holding path-planning packages — the Task 1/2 state machines
themselves are small enough to live in `mdp_bringup/scripts/` instead (see below), not here.

| Package | File | Role |
| --- | --- | --- |
| `mdp_planning` | `reeds_shepp_planner.py` | Reeds-Shepp/Dubins Ackermann curve generation + TSP solver (visits all 5 Task 1 targets) |
| `mdp_planning` | `pure_pursuit_follower.py` | Waypoint tracking controller — follows the planned path |
| `mdp_planning` | `spline_planner.py` | Spline-based path planning (alternative/supplementary to Reeds-Shepp) |

Also present: `mdp_algorithm/wayp_plan_tools` — a general-purpose waypoint/planner ROS2 package
(pure pursuit, waypoint save/load, obstacle avoidance) with its own upstream README; check whether
this project's planners depend on it directly or it's a reference/spare-parts package.

**Task 1 & 2 state machines** — `task1_runner.py` (imports `mdp_planning.reeds_shepp_planner`) and
`task2_runner.py` (imports `mdp_planning.spline_planner`) live in `mdp_bringup/scripts/`, installed
as `mdp_bringup` executables. Run via `pixi run task1` / `pixi run task2`.

## How it fits into the system

Per the [Assessment & Checklist](../assessment_checklist.md) Module B requirements: display the
2.0m×2.0m arena, compute a Hamiltonian path visiting all 5 targets, and solve the TSP via Reeds-Shepp
curves to minimize run time. See [Subsystems](../index.md#subsystems) for
where this sits relative to Vision and RPi.

- **Inputs**: target detections from [Vision](vision.md), robot pose from
  `/odometry/filtered` (see [ROS2 EKF Localization](ros2_ekf_localization.md#this-robots-specific-fusion)).
- **Outputs**: `/cmd_vel`, consumed by `ackermann_steering_controller` on the RPi (see [ROS2 Control](ros2_control.md)).
- **Pixi tasks**: `pixi run task1` / `pixi run task2` (run alongside `pixi run real` or `pixi run sim`) — see [Quickstart: Pixi Task Reference](../quickstart.md#pixi-task-reference-mdp_ros).
