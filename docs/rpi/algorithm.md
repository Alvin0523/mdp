---
icon: lucide/route
---

# Algorithm (`mdp_ros/src/mdp_algorithm`)

Path planning and path following for Task 1, as a plain Python library (no ROS nodes) used by
`mdp_bringup/task1_runner.py` (and `pixi run calib goto`). It is built the Nav2 way: a costmap of the arena, a Hybrid-A*
planner that checks the car's real footprint against it, and a pure-pursuit follower.

## Layout

```
mdp_algorithm/
├── utils/
│   ├── params.py             settings object - values from the URDF + mdp_bringup/config/navigation.yaml
│   ├── geometry.py           angle wrap, distances, Reeds-Shepp helpers
│   └── motion_primitives.py  Gear (forward/reverse), Steering (left/straight/right)
├── planning/
│   ├── planner.py            ENTRY: plan_visiting_order(), plan_leg()  (metres)
│   ├── costmap.py            arena, Obstacle, car footprint, Nav2-style costmap
│   ├── hybrid_astar.py       Hybrid A* on the costmap
│   ├── visiting_order.py     one checkpoint per obstacle + the visit order
│   ├── reeds_shepp.py        Reeds-Shepp curves (heuristic, final "shot", order distances)
│   └── spline_planner.py     (unused since task 2 moved to Hybrid A*)
└── control/
    └── pure_pursuit_follower.py   PurePursuitController
```

## How a run is planned

1. **Checkpoints** (`visiting_order.py`): for each obstacle, the car's `base_link` (rear-axle centre)
   stops `planner.checkpoint_standoff` (0.20 m) from the block centre, straight out from the image
   face, turned so the **camera** faces the image (its direction is read from the URDF: left in
   task 1). Snapped to a cell centre. An obstacle
   whose checkpoint the car cannot fit into is skipped as unreachable.
2. **Order** (`visiting_order.py`): every permutation of the reachable obstacles, scored by
   Reeds-Shepp distance between checkpoints (exact for ≤ 8 obstacles). Cheap, so the route shows at
   once. Reeds-Shepp ignores obstacles - a known simplification.
3. **Legs** (`hybrid_astar.py`, via `plan_leg()`): the runner plans every leg back to back in a
   background thread while the car already drives the first one.

## The costmap (`costmap.py`)

One 1 cm costmap of the 2 × 2 m arena, published on `/occupancy_grid` (Foxglove colour mode
"costmap"). Values are Nav2's:

| Cost | Meaning | On `/occupancy_grid` |
| --- | --- | --- |
| 254 LETHAL | inside a block, or outside the arena | 100 |
| 253 INSCRIBED | `base_link` this close means the body touches | 99 |
| 252 → 1 | inflation: fades with distance to `costmap.inflation_radius` | 98 → 1 |
| 0 | free | 0 |

A pose collides when the car's footprint (`robot.footprint_*` + `costmap.footprint_padding`) covers a
lethal cell - checked on the outline, as Nav2 does, and also inside it (a 10 cm block fits inside the
20 cm car).

## Hybrid A* (`hybrid_astar.py`)

From every pose: forward/reverse × left/straight/right, `planner.step_size` long, on the **measured**
turning circle of each side (`robot.minimum_turning_radius_*` × `planner.turning_radius_margin`). The
turning radius is measured, not `wheelbase / tan(angle)`: with one servo and a tie rod both front wheels
have the same angle, the tyres scrub, and the car turns wider than that formula - planning with it
gave paths the car could not follow. Step cost = length × (reverse penalty) × (1 + cost_penalty × cost / 252)
+ direction/steering change penalties. Near the goal a Reeds-Shepp "shot" lands the exact checkpoint.

## Following (`pure_pursuit_follower.py`)

Pure pursuit with a 0.10 m lookahead (it must stay well below the turning radius or it cuts corners).
The path is driven in same-gear segments; at a direction change the car drives **to** the change point
before switching gear (as Nav2's Regulated Pure Pursuit). Arrived = within `follower.xy_goal_tolerance`.
Steering is clamped per side (43° left, 32.5° right).

## Settings

Each number lives in one file (SI units, Nav2 names):

- the car's wheelbase and steering limits: the URDF (`mdp_description/urdf/mdp_robot.urdf.xacro`);
- everything else (footprint, turning circles, costmap, planner, follower, speeds):
  `mdp_bringup/config/navigation.yaml`.

`mdp.launch.py` passes both to `task1_runner` as parameters. Without ROS (tests, offline tools)
`params.load()` reads the same two files; there are no defaults in the code. Speeds and
lookahead can be changed live:

```bash
pixi run -- ros2 param set /task1_runner follower.desired_linear_vel 0.3
```

`mdp_algorithm/test/test_planner.py` plans the `tasks.yaml` task 1 layout end to end.

## Frames

The planner has no notion of TF: arena coordinates, origin at the arena's bottom-left corner,
centimetres inside `planning/`, metres at `planner.py`. In ROS the arena frame is `map`. The runner
converts `/odometry/filtered` (`odom`) into `map` through TF before handing the pose to the follower,
and stamps everything it draws in `map`.

## Known limitations

- **Turning circles are measured in sim only** (0.177 m left / 0.257 m right). Measure the real car
  with `pixi run calib turn left` / `right` and put them in `navigation.yaml`.
- **One checkpoint per obstacle.** If it's blocked (wall, another block), the obstacle is skipped.
  Trying several distances and angles per image would reach more of them.
- **Dead reckoning drifts** about 2–5 % of the distance driven (wheel odometry reads short in turns),
  so late checkpoints can be missed by 5–8 cm. The fix is correcting the pose from something external
  (e.g. the camera seeing blocks) - not done yet.
- The visit order ignores obstacles between checkpoints.

## Task 2

`task2_runner` doesn't know the distances in advance. It measures them with the front ultrasonic:
straight until obstacle 1 is `swerve_trigger_dist` ahead, a full-lock swerve to arrow 1's side, then
a measurement of obstacle 2 (the 60 cm bar) from beside obstacle 1. With both positions known it plans
three legs with the same Hybrid A* and costmap as task 1: **A** past the bar's end on arrow 2's side,
**B** round its back to the other end, **HOME** into the carpark. The costmap is the task 2 course
(5 × 2.4 m: carpark walls, both blocks, and the side walls that may stand 50 cm out from the bar).
Settings: `navigation.yaml` (`task2_runner`); course rules: `tasks.yaml` (`task2`). In sim, all four
arrow combinations at d1 / d2 = 0.6–1.5 m finish in 21–36 s with ≥ 9.8 cm clearance.
