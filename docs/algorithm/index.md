---
icon: lucide/route
---

# Algorithm (`mdp_ros/src/mdp_control`, `wayp_plan_tools`)

Path planning and the Task 1/2 autonomy state machines — running as ROS2 nodes inside `mdp_ros` on
the RPi, consuming Vision's detections and producing `/cmd_vel` for the RPi's kinematics stack.

!!! note "Stub page — fill in as the Algorithm subteam's own detail lands here"
    This page currently only reflects what's actually in the repo. Add the actual Reeds-Shepp/TSP
    formulation, pure-pursuit tuning notes, and Task 1/2 state machine diagrams here as that work
    happens.

## What's actually in the repo

Package: `mdp_ros/src/mdp_control` — description in `setup.py`: *"Autonomy, Vision, and Path Planning
nodes for Mini Ackermann Robot"*.

| File | Role |
| --- | --- |
| `reeds_shepp_planner.py` | Reeds-Shepp Ackermann curve generation + TSP solver (visits all 5 Task 1 targets) |
| `pure_pursuit_follower.py` | Waypoint tracking controller — follows the planned path |
| `spline_planner.py` | Spline-based path planning (alternative/supplementary to Reeds-Shepp) |
| `task1_runner.py` | Task 1 exploration state machine |
| `task2_runner.py` | Task 2 fastest-path decision tree |

Also present: `mdp_ros/src/wayp_plan_tools` — a general-purpose waypoint/planner ROS2 package
(pure pursuit, waypoint save/load, obstacle avoidance) with its own upstream README; check whether
this project's planners depend on it directly or it's a reference/spare-parts package.

## How it fits into the system

Per the [Assessment & Checklist](../assessment_checklist.md) Module B requirements: display the
2.0m×2.0m arena, compute a Hamiltonian path visiting all 5 targets, and solve the TSP via Reeds-Shepp
curves to minimize run time. See [Architecture: System Stack](../architecture.md#1-system-stack--who-talks-to-whom)
for where this sits relative to Vision and RPi.

- **Inputs**: target detections from [Vision](../vision/index.md), robot pose from
  `/odometry/filtered` (see [RPi: Sensor Fusion](../rpi/sensor_fusion.md)).
- **Outputs**: `/cmd_vel`, consumed by `ackermann_steering_controller` on the RPi.
- **Pixi tasks**: `pixi run task1` / `pixi run task2` (run alongside `pixi run real` or `pixi run sim`) — see [RPi: Pixi Task Shortcuts](../rpi/index.md#pixi-task-shortcuts).
