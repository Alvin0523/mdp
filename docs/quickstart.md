---
icon: lucide/play
---

# Quickstart

Everything needed to run the car, in sim or for real, with or without the tablet: the
commands, the calibration, and every number we measured. For *why* things work, see the
[RPi docs](rpi/index.md) and the [STM32 docs](stm32/index.md).

All `pixi run …` commands below run **from `mdp_ros/`**. Positions are always **tablet
cells**: column, row `0`–`19`, 10 cm each, `(0,0)` bottom-left, plus a direction
`N` `E` `S` `W`.

!!! tip "This docs site locally"
    From the `mdp` repo root: `pixi install`, then `pixi run serve` → `http://127.0.0.1:8000`.

---

## 1. One-time setup

=== "💻 Laptop (sim)"

    ```bash
    git clone --recurse-submodules https://github.com/Alvin0523/mdp.git
    cd mdp/mdp_ros
    pixi install
    pixi run build
    ```

=== "🤖 Raspberry Pi (real car)"

    SSH in from the laptop: `ssh grp14@10.42.0.1` over the Ethernet cable, `ssh grp14@10.43.0.1`
    on the Pi's hotspot ([Network](rpi/network.md)). [Tailscale](https://tailscale.com/) is off
    (the Pi has no internet on its hotspot, and SSH through it cost ~25% CPU). For remote access
    while the Pi is on a WiFi with internet: `sudo systemctl enable --now tailscaled`
    (off again: `sudo systemctl disable --now tailscaled`).

    ```bash
    git clone --recurse-submodules https://github.com/Alvin0523/mdp.git
    cd mdp/mdp_ros
    pixi install
    pixi run build
    pixi run realtime   # once: real-time priority for the controller loop (sudo; log in again after)
    ```

    **Flash the STM32** (ST-Link on the board's SWD header, ST-Link USB into the Pi):

    ```bash
    cd ../mdp_stm32
    pixi install
    pixi run probe      # ST-LINK/V2 + STM32F407 detected?
    pixi run build
    pixi run flash
    pixi run monitor    # optional: boot banner, LED blink, OLED pages
    pixi run udev       # once, board plugged in: /dev/stm32_serial (car link) + /dev/stm32_flash (asks for sudo)
    ```

    ARM64 hosts (the Pi) may need a `platform_packages` override if `build` fails, see
    [Troubleshooting](troubleshooting.md).

!!! warning "After pulling big changes: clean build"
    If a package changed type or files were renamed (e.g. `mdp_bringup` became a Python package
    on 2026-09-28), old build files get in the way:

    ```bash
    pixi run clean
    pixi run build
    ```

---

## 2. Pick your run

Every run is **two halves**, sim and real alike (since 2026-10-16): the car's half and the
laptop's. Nothing runs alone.

| Command | Where | What |
| --- | --- | --- |
| **`pixi run pi`** | Pi | The car's half on the real car: STM32, tablet, camera, EKF, the task runner ([Pi + laptop](#pi-laptop-yolo-and-planning-on-the-laptop)) |
| **`pixi run sim`** | Laptop | The car's half in Gazebo |
| **`pixi run laptop`** | Laptop | The laptop's half: YOLO, the leg planner, `/setup_obstacles`, the monitors, bags, snapshots. Next to `sim`: `pixi run laptop sim:=true` |
| **`pixi run car`** / **`sim-car`** | Laptop | Task 2, both halves + Foxglove's bridge in one zellij window: real car (SSHes into the Pi) / Gazebo |

The rest are [launch arguments](#launch-arguments) added after them (`pi` and `sim` take the same
ones; `laptop` takes `sim:=`, `task:=` and `model:=`).

| You want | Car's half | + Laptop's half | Obstacles come from |
| --- | --- | --- | --- |
| **Sim, no tablet** | `pixi run sim task:=1` | `pixi run laptop sim:=true task:=1` | `config/tasks.yaml`, sent automatically, plan starts by itself |
| **Sim + tablet** | `pixi run sim task:=1 obstacles:=tablet` | `pixi run laptop sim:=true task:=1` | The tablet (DONE). Gazebo's blocks change to match. |
| **Real + tablet** (the real run) | Pi `pixi run pi task:=1` | `pixi run laptop task:=1` | The tablet (DONE) |
| **Real, no tablet** | Pi `pixi run pi task:=1 obstacles:=yaml` | `pixi run laptop task:=1` | `config/tasks.yaml` |
| **Task 2, real** | `pixi run car` (one window), or Pi `pixi run pi task:=2` | `pixi run laptop task:=2` | Measured on the way (`tasks.yaml` `task2`: course rules) |
| **Task 2, sim** | `pixi run sim-car` (one window), or `pixi run sim task:=2` | `pixi run laptop sim:=true task:=2` | Gazebo's layout: `tasks.yaml` `task2` → `sim` |
| **Bare car** (manual drive, calibration) | `pixi run sim` / `pixi run pi` | `pixi run laptop` (`sim:=true` in sim) | none (`task:=0`) |

- **The tablet link is always on.** Even in "no tablet" runs a tablet can connect and send
  obstacles, START, STOP, RESET or the arrow buttons. Without one, the bridge just keeps retrying
  quietly.
- **Camera + YOLO are on by default.** `vision:=false` turns them off.
- **The car starts** with the middle of its rear axle over cell **(1,1) facing N**
  (`start_cell:=` / `start_dir:=` to change). Task 2 starts in the carpark facing E.

### Pi + laptop (YOLO and planning on the laptop)

YOLO is slow on the Pi (~0.7 frames/s), and so is Hybrid A*. With a laptop on the same
network both run there; everything that drives the car stays on the Pi, so nothing
time-critical crosses the WiFi.

| Machine | Command | Runs |
| --- | --- | --- |
| Pi (`role:=pi`, the default) | `pixi run pi task:=1` | Everything that touches the car: STM32, tablet, camera (sends JPEG frames), EKF, the task runner |
| Laptop (`role:=laptop`) | `pixi run laptop task:=1` | YOLO on those frames → `/yolo_result` back to the Pi; the legs' Hybrid A* (`leg_planner`, both tasks) → back to the Pi once, each as soon as it is found; `/setup_obstacles` (`pixi run setup`, from the laptop's `tasks.yaml`); `health_monitor` + `bt_monitor` (they only read topics); Foxglove REC bags and task 1 scan snapshots (saved on the laptop) |
| Sim, the same split on one laptop | `pixi run sim task:=1` + `pixi run laptop sim:=true task:=1` | YOLO reads Gazebo's camera directly |

- The Pi still works out the visiting order and checkpoints itself (milliseconds); only
  the paths come from the laptop. While driving nothing crosses the WiFi; small re-plans
  (a leg that went wrong) are done on the Pi.
- **Laptop not answering?** If it doesn't answer within 2 s (`remote_plan_timeout`), the Pi plans
  the leg itself (log: `no answer from the laptop planner - planning here`). Without YOLO the
  scans report `UNKNOWN`. YOLO never runs on the Pi (PyTorch crashed there).
- Network: Ethernet cable on the bench, the Pi's 5 GHz hotspot on the arena; `ROS_STATIC_PEERS`
  and `ROS_DOMAIN_ID` (14) in `pixi.toml`, the Pi's clock follows the laptop (chrony). Setup:
  [Network](rpi/network.md).

### Connecting the tablet

Pair the tablet with the machine (laptop for sim, Pi for real) once in Bluetooth settings, then
open the link before starting (keep this terminal open):

```bash
sudo rfcomm listen /dev/rfcomm0 1
```

The app connects; `LINK UP` shows in `pixi run btlog` and the tablet gets the car's position.

---

## 3. Task 1 (explore + recognise)

Steps are the same in sim and real; the **tablet** and **no-tablet** columns do the same thing.

| Step | With the tablet | Without (second terminal, `mdp_ros/`) | You'll see |
| --- | --- | --- | --- |
| 1. Obstacles | Place them, press **DONE** | nothing (`tasks.yaml` is sent at start), or `pixi run setup` to send it again | `OBSTACLES #1 (5,10)S …` |
| 2. Wait for the plan | tablet **PLAN: DONE** | | `PLAN      done, 4 legs - waiting for START` |
| 3. Car in the start box, reset | **RESET** | `pixi run reset` | `RESET     done - at the start`, tablet **RESET: DONE** |
| 4. Start | **START** (needs STATUS *Ready*) | `pixi run start` (Foxglove **START**) | `START     1 -> 2 -> 4 -> 3` |
| 5. Stop any time | **STOP** | `pixi run stop` | `STOP      at (9,5)E - reset before the next run` |

What the launch terminal prints during a run (one line per event, cells not metres):

```text
LEG 1/4   -> #1 (5,8)E  fwd REV fwd
ARRIVED   #1 at (5,8)E (target (5,8)E, heading -8deg) - scanning
TARGET    #1 = 20   (YOLO saw 20x22)
...
FINISHED  all obstacles visited
```

- At each stop the car waits up to 3 s while YOLO looks, then sends `TARGET,<obstacle>,<id>` to the
  tablet (`UNKNOWN` if it saw nothing). What YOLO reads for that image in the last 35 cm of the
  approach already counts, so a stop can be over sooner.
- **Two ways to visit the images** (`navigation.yaml` → `task1_runner` → `visit.mode`):

    | `visit.mode` | Stops | Position fix |
    | --- | --- | --- |
    | `square` (default) | A square-on stop at every image | IR at every stop |
    | `anchors` | **Anchors** (square-on stops) + **drive-bys** (images read while driving past, at `visit.drive_by_speed`) + an angled **last** stop | IR at every anchor |

    In `anchors` mode the plan picks the anchors (log `PLAN      #13 anchor, drive-by #16 on the
    way`); a drive-by not read by the end is visited square after the last stop
    (`DRIVE-BY  #16 not read - a stop for it now`). Change it with `ros2 param set /task1_runner
    visit.mode anchors`; it takes effect at the next obstacle set (DONE / `pixi run setup`).
- **Run again:** put the car back in the start box → RESET → START. The obstacles and plan are kept.
  A reset during a run stops the run first.
- **Step through** (testing): add `step:=true` to `pixi run pi` / `sim`, then `pixi run step`. SPACE
  starts the run; at each stop the car does everything a real run does (IR approach, creep and
  position fix, scan, `TARGET`), logs `STEP      SPACE for leg 2/4 -> #3`, and holds until the
  next SPACE. The run timer keeps running.
- **In sim, reset also puts the Gazebo car back at the start** (it teleports it), so you can run
  again without restarting the sim: `pixi run reset`, then `pixi run start`.

## 4. Task 2 (fastest car)

```bash
pixi run car        # real: Pi + laptop + Foxglove in one zellij window
pixi run sim-car    # sim:  the same split in Gazebo
pixi run start      # or the tablet's START
```

No obstacles to send and no reset needed. The distances are unknown (d1, d2 each 0.60–1.50 m), so
the car measures them on the way. Four checkpoints: **1** beside obstacle 1 (arrow 1's side),
**2** beside the bar's end on arrow 2's side, **3** beside its other end, **4** home.

| Step | The car | Ends when |
| --- | --- | --- |
| Before START | In the carpark: the ultrasonic measures obstacle 1, YOLO reads arrow 1 → checkpoint 1 and its path planned | START |
| → CP1 | Straight (`straight_speed`), an S to arrow 1's side, straight into checkpoint 1 (`cp1_speed` over the last `line_up_dist`). On the way: YOLO reads arrow 2, the ultrasonic measures the bar, the camera sees arrow 2 + the bullseye on the bar's near end | At checkpoint 1 (`cp1_back` behind obstacle 1, `lane_offset` out) |
| At CP1 | The bar's width ([below](#bar-width)); checkpoints 2–4 planned; CP1 → CP2 from the [route table](#route-table). A reading missing: it stops and looks | Planned |
| → CP2 → CP3 | An S to checkpoint 2 (`side_clearance` out from the bar's end), half a circle round the back of the bar | At checkpoint 3 |
| → Home | An S onto the centre line (`home_straight_dist` out), straight in | [Home](#home) |

The terminal: `READY`, `START`, `CP1 …`, `OBSTACLE 2  …`, `LEG 1/3` … `3/3`, `FINISHED  23.6 s
(the back wall)`. Foxglove shows it like task 1: costmap, blocks, walls, checkpoints and the path.

### Bar width

| Reading | Width planned with |
| --- | --- |
| Arrow 2 → the near bullseye in **one frame** × the ultrasonic's distance (× `bar_gap_scale` 1.065 on the real car: lens distortion; 1.0 in sim) | That, to the **nearest** 5 cm (not up: the side walls stand off the bar's real ends) |
| No such frame | The usual **60 cm** (`tasks.yaml` `task2` `obstacle_2` size) |

The "pose" method (one bullseye + the car's own pose) was dropped on 2026-10-16: it read 42–52 cm
for a 76 cm bar.

### Home

The home checkpoint is `HOME_DEEPER_M` (5 cm) past the carpark's middle, toward the back wall.
The run finishes at the first of:

| # | Rule | Log |
| --- | --- | --- |
| 1 | The ultrasonic reads the back wall at ≤ `home_stop_dist` (15 cm), once past the carpark's opening | `FINISHED  … (the back wall)` |
| 2 | Only if no wall is seen within the carpark's depth (a taped carpark): the whole outline 10 cm inside the carpark (`IN_CARPARK_MARGIN_M`) | `FINISHED  …` |
| 3 | The end of the path | `FINISHED  … (HOME)` |

### Route table

CP1 → CP2 must be ready the instant the car reaches checkpoint 1, so every route is planned
offline into `config/task2_routes.json.gz`. Rebuild with `pixi run task2-table` after changing the
car or a setting it depends on (`lane_offset`, `cp1_back`, the paddings, `side_clearance`,
`line_up_dist`, `straight_speed`, `max_lateral_accel`); on a mismatch the runner plans the leg live.

| Axis | Values |
| --- | --- |
| d2 (obstacle 1's far face → the bar) | 0.60–1.50 m, every 5 cm |
| Bar width | 0.30–1.20 m, every 5 cm |
| Arrows | all 4 pairs (left and right turning circles differ) |
| Checkpoint 1 back | 0.4 / 0.3 / 0.2 / 0.1 / 0.0 m (short courses put it nearer) |
| Total | 7220 routes, none missing |

**In sim**, the layout comes from `config/tasks.yaml` → `task2` → `sim` (d1, d2, arrows, side
walls). The runner never reads that part; it measures, like on the real run. After a run:
`pixi run reset`, then `start` again. In sim the reset also moves the car back into the carpark.
`pixi run setup` sends `task2` → `setup` as known (no measuring); `step:=true` + `pixi run step`
drives one checkpoint per SPACE.

## 5. Bare car: manual driving

Start the bare car (`pixi run sim` / `pixi run pi`, no `task:=`, + `pixi run laptop`). Then:

- **Tablet arrow buttons** (f, b, fl, fr, bl, br): one short burst per tap.
- **Keyboard:** `pixi run teleop` (the key legend prints in the terminal).

---

## 6. Calibration: `pixi run calib`

The driving ones run on the **bare car** (`pixi run sim` / `pi` + `laptop`, no `task:=`); `calib`
refuses to drive while task 1 or 2 is running. `pixi run calib <what> -h` lists each one's options.

**The log:** at the end of every run the terminal asks for the tape value (blank = skip; in sim
Gazebo's true pose is used, nothing to type) and adds one row to **`mdp_ros/calibration_log.csv`**:
`time, where (sim/real), test, setting, speed_mps, car, true, error, unit, notes`. It is history
only. **Nothing is changed for you:** put the number you settle on into the file in the last
column by hand.

| Command | Checks (one run gives all of these) | You type in | Where the result goes (by hand) |
| --- | --- | --- | --- |
| `calib straight 2.0` · `--speed 0.9 --accel 0.5` for task 2's speed | **Wheel size** (correction = tape ÷ car) · **roll-past** after the stop command · **sideways drift** (servo centre) · the peak speed it reached | Tape distance; sideways drift (+ = left) | URDF `wheel_radius` × correction; STM32 `servo.h` centre if it drifts |
| `calib rotate 90` (… `360`, `--direction right`) | **Heading (IMU / EKF)** vs a protractor (assessment A.4) · **gyro drift** in 3 s standing still | Degrees it really turned | EKF / IMU settings |
| `calib turn left` / `right` · `--speed 0.35` for task 2 | **Turning circle** at full lock · **steering delay** (command → full turn rate) | Mark the floor under the middle of the rear axle before and after: half a circle, so the marks are **one diameter** apart | `navigation.yaml` `minimum_turning_radius_left` / `_right` (m, tape ÷ 2) |
| `calib ultrasonic 60` (car stays put) | **Ultrasonic** median, spread and dropouts at 60 cm. Do 30 / 60 / 100 / 150, and a block turned ~20° | Nothing: the distance is the argument (tape from the sensor face) | Tell the task 2 settings (`US_VALID`, trigger distances) if it's off |
| `calib goto 5 8 E` | **Planner + follower end to end** around the `tasks.yaml` blocks (`--no-obstacles` for none) | Rear-axle middle → cell centre (cm) | `navigation.yaml` follower settings |
| `pixi run around-obstacle` | Checklist demo: approach a block on the ultrasonic, drive a square round it | — | — |

In sim, `calib turn` agrees with Gazebo's true circle to within 3 mm (2026-09-30: 18.0 / 18.0 cm left).

---

## 7. Measured car numbers

Every number measured on the car, where it lives, and how to re-measure it. Each lives in **one**
file only; the launch copies the URDF's numbers into the controller and planner.

| What | Value | How / when measured | Lives in | Re-measure with |
| --- | --- | --- | --- | --- |
| Wheelbase (rear axle → front axle) | **143 mm** | Tape, 2026-09-27 | URDF `wheelbase` | Tape |
| Rear track (wheel centre to centre) | **162 mm** | Tape, 2026-09-27 | URDF `rear_track` | Tape |
| Front track | **164 mm** | Tape, 2026-09-27 | URDF `front_track` | Tape |
| Tyre rolling diameter (front = rear) | **66.2 mm** (radius 33.1 mm) | `calib straight`, 4 × 2.0 m: car went 1.018× what 65 mm predicted | URDF `wheel_radius` | `calib straight` |
| Body outline | **230 × 190 mm**, 31 mm behind the rear axle, 199 mm ahead | Tape, 2026-09-27 | `navigation.yaml` `footprint_*` | Tape |
| Steering limit left | **43.0°** at 850 µs (chassis contact) | Protractor at the wheel, 2026-09-18 | URDF `steer_left` + STM32 `servo.h` | Protractor |
| Steering limit right | **32.5°** at 2400 µs (mechanical stop) | Protractor at the wheel, 2026-09-18 | URDF `steer_right` + STM32 `servo.h` | Protractor |
| Servo centre (straight) | **1490 µs** | Pushing the car by hand at candidate pulses | STM32 `servo.c` | Same |
| Turning radius left (full lock, rear-axle middle) | config **17.7 cm** (sim) · real tape **17.5 cm** at 0.15 m/s | Gazebo circle fit 2026-09-27 · tape 2026-10-01 (not yet in the config) | `navigation.yaml` `minimum_turning_radius_left` | `calib turn left` |
| Turning radius right | config **25.7 cm** (sim) · real tape **24.35 cm** at 0.15 m/s | Gazebo circle fit 2026-09-27 · tape 2026-10-01 (not yet in the config) | `navigation.yaml` `minimum_turning_radius_right` | `calib turn right` |
| IMU position | 12 mm ahead of the rear axle, 26 mm right of centre, 95.5 mm above the table | Tape, 2026-09-28 | URDF `imu_joint` | Tape |
| Camera position | Task 1: in line with the rear axle, **55 mm right** of the centre line, lens **175 mm** above the table, looking **left**. Task 2: **171 mm** ahead of the rear axle on the centre line, **150 mm** high, looking forward | Tape, 2026-10-08 / 2026-10-14 | URDF `camera_joint` | Tape |
| Battery | 3S Li-ion, 12.6 V full, 3400 mAh | Spec | — | OLED `B:xx.xV` |

Both front wheels always have the **same** angle: one servo drives the right wheel and a tie rod
the left. That is why the controller's `steering_track_width` is ~0, and why the real turning circle
is wider than wheelbase ÷ tan(angle) (tyre scrub): **measure it**, don't calculate it.

!!! warning "STM32 firmware"
    The 43.0° / 32.5° limits must also be in the firmware on the car (`mdp_stm32/include/servo.h`).
    If the car was flashed before 2026-09-27, [flash it again](#1-one-time-setup).

### Settings you'll tune: `config/navigation.yaml`

Everything that drives the car is in `mdp_ros/src/mdp_bringup/config/navigation.yaml`.

| Setting | Now | What |
| --- | --- | --- |
| `task1_runner` `follower.desired_linear_vel` | 0.3 m/s | Task 1 speed, with `follower.path_tracking: lqr` (0.5 touched blocks in sim) |
| `task1_runner` `planner.checkpoint_standoff` | 0.30 m | Rear axle this far from the block centre at each stop |
| `task1_runner` `costmap.footprint_padding` | 0.06 m | Safety margin round the car (blocks) |
| `task1_runner` `follower.xy_goal_tolerance` | 0.03 m | "Arrived" |
| `task1_runner` `visit.mode` · `visit.drive_by_speed` | `square` · 0.2 m/s | How images are visited (above) · speed while a drive-by image is in view |
| `task1_runner` `visit.view_max_dist` · `view_max_bearing` · `view_max_incidence` | 0.50 m · 25° · 45° | "In view" for a drive-by (anchors mode) |
| `manual_speed_mps` | 0.15 m/s | Tablet arrow buttons |

Task 2 (`task2_runner`):

| Setting | Now | What |
| --- | --- | --- |
| `straight_speed` / `path_speed` / `cp1_speed` | 0.90 / 0.50 / 0.60 m/s | Straights / the slowest (gear changes, arriving) / the straight into checkpoint 1 |
| `max_lateral_accel` · `brake_decel` | 1.00 · 1.20 m/s² | Curve speed = √(this × radius) · braking for curves and path ends (1.4 tried: CP2 12 cm off vs 7) |
| `cp1_back` · `lane_offset` · `line_up_dist` | 0.40 · 0.30 · 0.30 m | Checkpoint 1 behind obstacle 1's centre · out from the centre line · the straight into checkpoints 1 and 2 |
| `side_clearance` | 0.25 m | Checkpoints 2 and 3 out from the bar's ends |
| `obstacle_padding` · `obstacle_1_padding` · `wall_padding` | 0.10 · 0.15 · 0.06 m | Kept from the obstacles · before/behind obstacle 1 · from the walls |
| `home_straight_dist` · `home_margin` · `home_stop_dist` | 0.45 · 0.08 · 0.15 m | Straight on the centre line before home · extra room on the way home · ultrasonic stop at the back wall |
| `bar_gap_scale` | 1.065 | Real car's lens correction for the bar width |
| `swerve_trigger_dist` | 0.45 m | Fallback only (arrow 1 not read at home): the latest swerve past obstacle 1 |

Speeds can be changed **while running**, e.g.:

```bash
pixi run -- ros2 param set /task1_runner follower.desired_linear_vel 0.3
```

A route-table setting changed → `pixi run task2-table`.

The turning circles were measured at 0.2 m/s. Faster means wider turns than planned, so test in sim
first.

The other config files, all in the same folder: `tasks.yaml` (obstacle layouts, task 1 + 2),
`bridges.yaml` (STM32 serial port, tablet device), `vision.yaml` (camera, YOLO),
`controller.yaml` and `ekf.yaml` (ros2_control, localisation).

---

## 8. Watching a run

| What | How |
| --- | --- |
| **Everything, visually** | `pixi run foxglove`, then in Foxglove open `ws://localhost:8765` (sim) or `ws://<pi>:8765` (real) and import `mdp_ros/foxglove/MDP_Grp14.json` (again after it changes). Tabs **Task 1** / **Task 2**: 3D arena, camera, state timeline, lights (STM32, motors, tablet, **● REC**), battery, buttons **RESET · SETUP · START · E-STOP · REC**, logs (runner only, tablet traffic only). **Health & tuning**: diagnostics (incl. the Pi's CPU/memory/temperature), all logs, speed / steering / yaw-rate / range / Pi plots. |
| The run, step by step | The launch terminal (lines like `LEG`, `ARRIVED`, `TARGET` above) |
| Tablet traffic | `pixi run btlog`: `LINK UP/DOWN`, `TABLET -> RPI …`, `RPI -> TABLET …` |
| Live numbers | `pixi run status`: state, target, distance left, gear, speed, scan |
| Record for later | Foxglove **REC**, or `pixi run bag`: starts, and the next press stops (saved in `mdp_ros/bags/`, camera images left out) |
| The Pi's load | Foxglove's **Pi** light and plot, or `pixi run -- ros2 topic echo /pi/status`: CPU per core, memory, temperature, throttling. Above ~78 C it warns; the Pi 4 slows itself down at 80 C. |

---

## 9. Before a real run

1. **Battery**: OLED page 1 `B:xx.xV`; charge if low.
2. **Motor switch** (`PD3`) **ON**: OLED `ES:RDY`; Foxglove light *Motors ON*.
3. **STM32 plugged in**: the Foxglove light *STM32 OK* once running.
4. **Tablet connected**: *Tablet OK* light, `LINK UP` in `pixi run btlog`.
5. **Car in the start box**, cell (1,1) facing N → RESET → tablet **RESET: DONE**.
6. **Obstacles sent** → **PLAN: DONE** → tablet status **Ready** → START.

---

## Launch arguments

Added after `pixi run sim`, `pi` or `laptop` (all start `mdp_bringup/launch/mdp.launch.py`).

| Argument | Values | Default | What |
| --- | --- | --- | --- |
| `task:=` | `0` `1` `2` | `0` | `0` bare car (manual drive, calibration) · `1` explore + recognise · `2` fastest car |
| `vision:=` | `true` `false` | `true` | Camera + YOLO |
| `model:=` | model file | `best_v5.pt`; `task:=2`: `task2_yolo26s.pt` | YOLO model under `mdp_vision/models/`, or a full path to a `.pt`. On a Mac its `.mlpackage` (CoreML) is used when there (`pixi run export-coreml`). Give the laptop the same `task:=` |
| `obstacles:=` | `tablet` `yaml` | sim `yaml`, real `tablet` | `yaml` also sends `layout` once at start. The tablet link is on either way. |
| `layout:=` | path | `config/tasks.yaml` | Obstacle layouts (task 1 in cells, task 2 in metres); in sim also Gazebo's blocks |
| `start_cell:=` | `COL,ROW` | `1,1` | Cell under the middle of the rear axle at start |
| `start_dir:=` | `N` `E` `S` `W` | `N` | Facing at start (task 2 without `start_cell`: carpark, facing E) |
| `gui:=` | `true` `false` | `true` | Sim only: Gazebo window |
| `log:=` | `quiet` `full` | `quiet` | `full` shows every node's output |
| `serial_port:=` | device | `/dev/stm32_serial` (`bridges.yaml`, udev rule from `mdp_stm32`: `pixi run udev`) | Real only: the STM32 |
| `bluetooth_device:=` | device | `/dev/rfcomm0` | The tablet link |
| `role:=` | `pi` `laptop` | `pi` | The car's half (real or Gazebo) / the laptop's half. Set by `pi` / `sim` / `laptop`; always both |
| `step:=` | `true` `false` | `false` | Task 1 / 2 testing: stop at every checkpoint, `pixi run step` (SPACE) drives on |

## All pixi tasks (`mdp_ros/`)

| Task | What |
| --- | --- |
| `pixi run build` / `test` / `clean` | Build / run the tests / delete `build install log` |
| `pixi run pi` / `laptop` | The car's half on the Pi / YOLO, planning, setup, monitors, bags on the laptop ([Pi + laptop](#pi-laptop-yolo-and-planning-on-the-laptop)) |
| `pixi run sim` | The car's half in Gazebo (+ [launch arguments](#launch-arguments)), with `pixi run laptop sim:=true` |
| `pixi run car` / `sim-car` | Task 2 in one zellij window: Pi + laptop + Foxglove / the same in sim |
| `pixi run task2-table` | Rebuild task 2's route table (`config/task2_routes.json.gz`) |
| `pixi run step` | Task 1 / 2 with `step:=true`: SPACE = next checkpoint (the first = START), `s` = E-STOP |
| `pixi run vision` | Camera + YOLO only, no car |
| `pixi run setup` | Send the obstacles in `tasks.yaml`: task 1 like the tablet's DONE; task 2 the `task2.setup` layout as known (no ultrasonic / YOLO measuring) |
| `pixi run reset` / `start` / `stop` | Reset to the start pose / start the run / **emergency stop** (reset before the next run) |
| `pixi run calib …` | [Calibration](#6-calibration-pixi-run-calib): `straight` · `rotate` · `turn` · `goto` · `ultrasonic` (rows to `calibration_log.csv`) |
| `pixi run around-obstacle` | Checklist: drive round a block |
| `pixi run teleop` | Keyboard driving |
| `pixi run foxglove` | Foxglove bridge, port 8765 |
| `pixi run status` / `btlog` | Live numbers / tablet traffic |
| `pixi run bag` / `bag-all` | Start/stop recording everything except camera images / record everything from this terminal |
| `pixi run panels` / `import-symbols` | Regenerate the sim's symbol images |

## Tablet ↔ Pi messages

One line per message, ending in `\n`. Cells `0`–`19`, origin bottom-left.

| Direction | Line | Meaning |
| --- | --- | --- |
| tablet → Pi | `OBSTACLE,<n>,<col>,<row>,<N/E/S/W>` | Obstacle *n* in cell `col`,`row` (0–19); facing = the side with the image |
| tablet → Pi | `DONE` | All obstacles sent: plan now |
| tablet → Pi | `START` | Start the run (same as `pixi run start` / Foxglove **START**) |
| tablet → Pi | `STOP` | **Emergency stop**: the run stops at once (same as `pixi run stop` / Foxglove **E-STOP**); RESET before the next run |
| tablet → Pi | `RESET` | Car is back at the start: reset the pose (like `pixi run reset`) |
| tablet → Pi | `f` `b` `fl` `fr` `bl` `br` | Manual drive, one short burst per tap |
| tablet → Pi | `CLEAR` | Tablet cleared its map (Pi resends the car's position) |
| Pi → tablet | `ROBOT,<x>,<y>,<N/E/S/W>` | Cell under the middle of the rear axle, and facing (every 2 s and on change) |
| Pi → tablet | `TARGET,<n>,<id>` | Image ID read on obstacle *n* (`UNKNOWN` if none) |
| Pi → tablet | `PLAN:<WAITING\|PLANNING\|DONE>` | Plan state |
| Pi → tablet | `RESET:<WAITING\|DONE>` | Car confirmed at the start pose |
| Pi → tablet | `FINISHED` | The run is complete and its timer stopped (task 1 and 2; not after STOP) |
| Pi → tablet | `STATUS:<text>` | `Waiting`, `Ready`, `Going to obstacle n`, `Scanning obstacle n` (task 1) / `Going to checkpoint n` (task 2), `Finished`, `Stopped` |

*Ready* = PLAN `DONE` and RESET `DONE`.

**Test the link by hand** (stack running): the tablet → Pi direction shows in `pixi run btlog`
as you press buttons. For Pi → tablet, send any line:

```bash
pixi run -- ros2 topic pub --once /bluetooth_tx std_msgs/msg/String "{data: 'STATUS:Ready'}"
```
