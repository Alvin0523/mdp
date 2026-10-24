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
│   ├── visiting_order.py     one checkpoint per obstacle + the visit order (visit.mode square)
│   ├── visit_plan.py         anchors, drive-bys and an angled last stop (visit.mode anchors)
│   ├── reeds_shepp.py        Reeds-Shepp curves (heuristic, final "shot", order distances)
│   └── task2_paths.py        task 2's clean shapes (straight, smooth S, half circle)
└── control/
    └── path_follower.py      PathFollower (pure pursuit / feedback / LQR steering)
```

## How a run is planned

1. **Checkpoints** (`visiting_order.py`): for each obstacle, the car's `base_link` (rear-axle centre)
   stops `planner.checkpoint_standoff` (0.30 m) from the block centre, straight out from the image
   face, turned so the **camera** faces the image (its direction is read from the URDF: left in
   task 1). Snapped to a cell centre. Where the car does not fit, the stop slides along the face
   (up to 10 cm either way), then back along the car's heading (up to 13 cm, as far as the image
   stays whole in the camera's view). No spot at all: skipped as unreachable.
2. **Order** (`visiting_order.py`): every permutation of the reachable obstacles, scored by
   Reeds-Shepp distance between checkpoints (exact for ≤ 8 obstacles). Cheap, so the route shows at
   once. Reeds-Shepp ignores obstacles - a known simplification.
3. **Legs** (`hybrid_astar.py`, via `plan_leg()`): the runner plans every leg back to back in a
   background thread while the car already drives the first one.

### `visit.mode: anchors` (`visit_plan.py`, not the default)

Fewer stops, and every image visited. Steps 1 and 2 change; step 3 is the same.

| Kind | What | Position fix |
| --- | --- | --- |
| **Anchor** | Square-on stop, as above | IR pair |
| **Drive-by** | The image is in view (`visit.view_*`: ≤ 50 cm, ≤ 25° off the camera's axis, face ≤ 45° turned) for ≥ `visit.drive_by_min_view` (15 cm) of a path between two stops - read while driving at `visit.drive_by_speed`. If the plain path does not see it, the leg is bent through a **via** point that does (passed through in one gear) | - |
| **Angled** | An image no square stop fits (another block or the table's edge in the way): a stop further back and/or turned (up to 60°, 25-45 cm) where the camera still sees it | - |
| **Last** | Angled stop (≤ 35° off square): nothing is driven after it | - |

How it is chosen (Reeds-Shepp estimates only, ~0.2-0.5 s for 8 images):

1. every image an anchor; order = shortest tour over the anchors (Held-Karp);
2. try turning each anchor into a drive-by; keep the one that saves the most time; repeat until
   none saves time. At most `visit.max_drive_bys_per_leg` (2) drive-bys between two stops, so the
   position fixes stay close;
3. the last stop angled, if that is quicker.

The runner plans the legs with Hybrid A* (a via = two legs joined into one), drives slower while a
drive-by image is in view, and matches each YOLO box (`/yolo_detections`) to the image the camera
expects in that direction. A drive-by not read on its leg is visited square after the last stop.
Example: a layout of 8 images became 4 stops (3 anchors + the last) and 4 drive-bys.

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

## Following (`path_follower.py`)

Both tasks steer with **LQR** (`follower.path_tracking: lqr` / `task2_runner` `path_tracking: lqr`
in `navigation.yaml`). The rest of this section describes the pure pursuit mode (`pure_pursuit`).

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

`task2_runner` doesn't know the distances in advance (d1, d2 = 0.60–1.50 m): it measures them with
the front ultrasonic and reads the arrows and the bar's width with YOLO. Four checkpoints:

| Leg | How it is planned |
| --- | --- |
| Home → CP1 (beside obstacle 1, arrow 1's side) | Before START: a clean straight + smooth S + straight (`task2_paths.py`) |
| CP1 → CP2 (the bar's end, arrow 2's side) | Looked up in the route table built offline (`task2_routes.py`, `pixi run task2-table`: 7220 routes) |
| CP2 → CP3 (round the bar's back) | Half a circle, clean geometry |
| CP3 → home | An S onto the centre line, straight in; finishes on the ultrasonic at the back wall (15 cm) |

Hybrid A* (on the laptop's `leg_planner`, on the Pi if it does not answer in
`remote_plan_timeout`) only plans what none of that covers. The costmap is the task 2 course
(5 × 2.4 m: carpark walls, both blocks, and the side walls that may stand 50 cm out from the bar).
Settings: `navigation.yaml` (`task2_runner`); course rules: `tasks.yaml` (`task2`). Details:
[Quickstart → Task 2](../quickstart.md#5-task-2-fastest-car).
