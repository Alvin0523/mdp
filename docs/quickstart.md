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

    SSH in over [Tailscale](https://tailscale.com/) (no need to be on the same network).

    ```bash
    git clone --recurse-submodules https://github.com/Alvin0523/mdp.git
    cd mdp/mdp_ros
    pixi install
    pixi run build
    ```

    **Flash the STM32** (ST-Link on the board's SWD header, ST-Link USB into the Pi):

    ```bash
    cd ../mdp_stm32
    pixi install
    pixi run probe      # ST-LINK/V2 + STM32F407 detected?
    pixi run build
    pixi run flash
    pixi run monitor    # optional: boot banner, LED blink, OLED pages
    pixi run udev       # once: the STM32's USB serial shows up as /dev/stm32 (asks for sudo)
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

Named after the machine you type them on:

| Command | Where | What |
| --- | --- | --- |
| **`pixi run pi`** | Pi | The car, with a laptop running `pixi run laptop` ([Pi + laptop](#pi-laptop-yolo-and-planning-on-the-laptop)) |
| **`pixi run laptop`** | Laptop | YOLO, task 1 path planning and the monitors, for the Pi |
| **`pixi run pi-solo`** | Pi | The car alone, no laptop: everything on the Pi, YOLO too (slower) |
| **`pixi run sim`** | Laptop | Gazebo |

The rest are [launch arguments](#launch-arguments) added after them (`pi`, `pi-solo` and `sim`
take the same ones).

| You want | Command | Obstacles come from |
| --- | --- | --- |
| **Sim, no tablet** | `pixi run sim task:=1` | `config/tasks.yaml`, sent automatically, plan starts by itself |
| **Sim + tablet** | `pixi run sim task:=1 obstacles:=tablet` | The tablet (DONE). Gazebo's blocks change to match. |
| **Real + tablet** (the real run) | Pi `pixi run pi task:=1` + laptop `pixi run laptop` | The tablet (DONE) |
| **Real, no tablet** | `pixi run pi task:=1 obstacles:=yaml` (+ laptop) | `config/tasks.yaml` |
| **Real, no laptop** | `pixi run pi-solo task:=1` | The tablet (DONE) |
| Task 2 | `pixi run sim task:=2` / `pixi run pi task:=2` (+ laptop) | `config/tasks.yaml` (`task2`) |
| **Bare car** (manual drive, calibration) | `pixi run sim` / `pixi run pi` / `pixi run pi-solo` | none (`task:=0`) |

- **The tablet link is always on.** Even in "no tablet" runs a tablet can connect and send
  obstacles, BEGIN, STOP, RESET or the arrow buttons. Without one, the bridge just keeps retrying
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
| Pi | `pixi run pi task:=1` | Everything that touches the car: STM32, tablet, camera (sends JPEG frames), EKF, the task runner |
| Laptop | `pixi run laptop` | YOLO on those frames → `/yolo_result` back to the Pi; task 1's paths between checkpoints → back to the Pi once, each as soon as it is found; `health_monitor` + `bt_monitor` (they only read topics) |
| Sim, the same split on one laptop | `pixi run sim task:=1 role:=pi` + `pixi run laptop sim:=true` | YOLO reads Gazebo's camera directly |

- The Pi still works out the visiting order and checkpoints itself (milliseconds); only
  the paths come from the laptop. While driving nothing crosses the WiFi; small re-plans
  (a leg that went wrong) are done on the Pi.
- **No laptop?** If it doesn't answer within 2 s (`remote_plan_timeout`), the Pi plans the
  paths itself (log: `no answer from the laptop planner - planning here`). Without YOLO the
  scans report `UNKNOWN`. `pixi run pi-solo` runs everything on the Pi alone, YOLO too.
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
| 2. Wait for the plan | tablet **PLAN: DONE** | | `PLAN      done, 4 legs - waiting for GO` |
| 3. Car in the start box, reset | **RESET** | `pixi run reset` | `RESET     done - at the start`, tablet **RESET: DONE** |
| 4. Go | **BEGIN** (needs STATUS *Ready*) | `pixi run go` | `GO        1 -> 2 -> 4 -> 3` |
| 5. Stop any time | **STOP** | `pixi run stop` | `STOP      at (9,5)E - reset before the next run` |

What the launch terminal prints during a run (one line per event, cells not metres):

```text
LEG 1/4   -> #1 (5,8)E  fwd REV fwd
ARRIVED   #1 at (5,8)E (target (5,8)E, heading -8deg) - scanning
TARGET    #1 = 20   (YOLO saw 20x22)
...
FINISHED  all obstacles visited
```

- At each stop the car waits 3 s while YOLO looks, then sends `TARGET,<obstacle>,<id>` to the
  tablet (`UNKNOWN` if it saw nothing).
- **Run again:** put the car back in the start box → RESET → BEGIN. The obstacles and plan are kept.
  A reset during a run stops the run first.
- **In sim, reset also puts the Gazebo car back at the start** (it teleports it), so you can run
  again without restarting the sim: `pixi run reset`, then `pixi run go`.

## 4. Task 2 (fastest car)

```bash
pixi run go
```

No obstacles to send and no reset needed. The distances to the two obstacles are unknown, so the car
measures them with the front ultrasonic on the way:

| Step | The car | Ends when |
| --- | --- | --- |
| 1 | Straight out of the carpark, fast; YOLO reads arrow 1 | Ultrasonic reads obstacle 1 at `swerve_trigger_dist` (0.45 m) |
| 2 | Swerves to arrow 1's side and straightens up, beside obstacle 1 | Heading straight again |
| 3 | Straight on toward obstacle 2 (the 60 cm bar): measures it, reads arrow 2 | Both known |
| 4 | Planned path (the task 1 planner) past the bar's end on arrow 2's side, round its back, past the other end, home | In the carpark |

The terminal shows each step (`SWERVE LEFT …`, `OBSTACLE 2 at (28,12), arrow 2 RIGHT -> A … B … HOME …`,
`FINISHED  in the carpark …, 23.6 s`). Foxglove shows it like task 1: costmap, blocks, walls,
waypoints A / B / HOME and the path.

**In sim**, the layout comes from `config/tasks.yaml` → `task2` → `sim` (d1, d2, arrows, side
walls). The runner never reads that part; it measures, like on the real run. After a run:
`pixi run reset`, then `go` again. In sim the reset also moves the car back into the carpark.

## 5. Bare car: manual driving

Start with `pixi run sim`, `pixi run pi` or `pixi run pi-solo` (no `task:=`). Then:

- **Tablet arrow buttons** (f, b, fl, fr, bl, br): one short burst per tap.
- **Keyboard:** `pixi run teleop` (the key legend prints in the terminal).

---

## 6. Calibration: `pixi run calib`

The driving ones run on the **bare car** (`pixi run sim` / `pi` / `pi-solo`, no `task:=`); `calib`
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
| Turning radius left (full lock, 0.2 m/s) | **17.7 cm** (sim) · real: **TODO** | Gazebo circle fit, 2026-09-27 | `navigation.yaml` `minimum_turning_radius_left` | `calib turn left` |
| Turning radius right | **25.7 cm** (sim) · real: **TODO** | Gazebo circle fit, 2026-09-27 | `navigation.yaml` `minimum_turning_radius_right` | `calib turn right` |
| IMU position | 12 mm ahead of the rear axle, 26 mm right of centre, 95.5 mm above the table | Tape, 2026-09-28 | URDF `imu_joint` | Tape |
| Camera position | Middle of the chassis: 84 mm ahead of the rear axle, on the centre line, 93 mm above the table, looking **left** in task 1, forward in task 2 · **TODO: mount + measure** | Placed 2026-09-28 (not yet measured on the car) | URDF `camera_joint` | Tape |
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
| `task1_runner` `follower.desired_linear_vel` | 0.4 m/s | Task 1 speed, with `follower.path_tracking: lqr` (0.5 touched blocks in sim) |
| `task2_runner` `straight_speed` / `path_speed` | 0.90 / 0.35 m/s | Task 2: straights / curves (speed in a curve also capped by `max_lateral_accel`) |
| `task2_runner` `swerve_trigger_dist` | 0.45 m | Task 2: ultrasonic distance to obstacle 1 that starts the swerve |
| `manual_speed_mps` | 0.15 m/s | Tablet arrow buttons |
| `planner.checkpoint_standoff` | 0.20 m | Rear axle this far from the block centre at each stop |
| `costmap.footprint_padding` | 0.03 m | Safety margin round the car |
| `follower.xy_goal_tolerance` | 0.05 m | "Arrived" |

Speeds can be changed **while running**, e.g.:

```bash
pixi run -- ros2 param set /task1_runner follower.desired_linear_vel 0.3
```

The turning circles were measured at 0.2 m/s. Faster means wider turns than planned, so test in sim
first.

The other config files, all in the same folder: `tasks.yaml` (obstacle layouts, task 1 + 2),
`bridges.yaml` (STM32 serial port, tablet device), `vision.yaml` (camera, YOLO),
`controller.yaml` and `ekf.yaml` (ros2_control, localisation).

---

## 8. Watching a run

| What | How |
| --- | --- |
| **Everything, visually** | `pixi run foxglove`, then in Foxglove open `ws://localhost:8765` (sim) or `ws://<pi>:8765` (real) and import `mdp_ros/foxglove/MDP_Grp14.json` once. Tab **Run**: 3D arena, camera, state timeline, link lights, battery, GO/STOP/RESET buttons, logs. Tab **Health & tuning**: diagnostics, speed / steering / yaw-rate plots. |
| The run, step by step | The launch terminal (lines like `LEG`, `ARRIVED`, `TARGET` above) |
| Tablet traffic | `pixi run btlog`: `LINK UP/DOWN`, `TABLET -> RPI …`, `RPI -> TABLET …` |
| Live numbers | `pixi run status`: state, target, distance left, gear, speed, scan |
| Record for later | `pixi run bag` (Ctrl+C to stop, saved in `mdp_ros/bags/`) |

---

## 9. Before a real run

1. **Battery**: OLED page 1 `B:xx.xV`; charge if low.
2. **Motor switch** (`PD3`) **ON**: OLED `ES:RDY`; Foxglove light *Motors ON*.
3. **STM32 plugged in**: the Foxglove light *STM32 OK* once running.
4. **Tablet connected**: *Tablet OK* light, `LINK UP` in `pixi run btlog`.
5. **Car in the start box**, cell (1,1) facing N → RESET → tablet **RESET: DONE**.
6. **Obstacles sent** → **PLAN: DONE** → tablet status **Ready** → BEGIN.

---

## Launch arguments

Added after `pixi run sim`, `pi` or `pi-solo` (all start `mdp_bringup/launch/mdp.launch.py`).

| Argument | Values | Default | What |
| --- | --- | --- | --- |
| `task:=` | `0` `1` `2` | `0` | `0` bare car (manual drive, calibration) · `1` explore + recognise · `2` fastest car |
| `vision:=` | `true` `false` | `true` | Camera + YOLO |
| `model:=` | model folder | `mdp_v2_ncnn_model` | YOLO model under `mdp_vision/models/` (`mdp_v1_ncnn_model` = older) |
| `obstacles:=` | `tablet` `yaml` | sim `yaml`, real `tablet` | `yaml` also sends `layout` once at start. The tablet link is on either way. |
| `layout:=` | path | `config/tasks.yaml` | Obstacle layouts (task 1 in cells, task 2 in metres); in sim also Gazebo's blocks |
| `start_cell:=` | `COL,ROW` | `1,1` | Cell under the middle of the rear axle at start |
| `start_dir:=` | `N` `E` `S` `W` | `N` | Facing at start (task 2 without `start_cell`: carpark, facing E) |
| `gui:=` | `true` `false` | `true` | Sim only: Gazebo window |
| `log:=` | `quiet` `full` | `quiet` | `full` shows every node's output |
| `serial_port:=` | device | `/dev/stm32` (`bridges.yaml`, udev rule from `mdp_stm32`: `pixi run udev`) | Real only: the STM32 |
| `bluetooth_device:=` | device | `/dev/rfcomm0` | The tablet link |
| `role:=` | `solo` `pi` `laptop` | `solo` | Set by `pi-solo` / `pi` / `laptop`; by hand only for the split in sim (`pixi run sim role:=pi`) |

## All pixi tasks (`mdp_ros/`)

| Task | What |
| --- | --- |
| `pixi run build` / `test` / `clean` | Build / run the tests / delete `build install log` |
| `pixi run pi` / `laptop` | The car on the Pi / YOLO, planning and monitors on the laptop ([Pi + laptop](#pi-laptop-yolo-and-planning-on-the-laptop)) |
| `pixi run pi-solo` | The car on the Pi alone, no laptop |
| `pixi run sim` | Gazebo (+ [launch arguments](#launch-arguments)) |
| `pixi run vision` | Camera + YOLO only, no car |
| `pixi run setup` | Send the task 1 obstacles in `tasks.yaml`, like the tablet's DONE |
| `pixi run reset` / `go` / `stop` | Reset to the start pose / start the run / stop |
| `pixi run calib …` | [Calibration](#6-calibration-pixi-run-calib): `straight` · `rotate` · `turn` · `goto` · `ultrasonic` (rows to `calibration_log.csv`) |
| `pixi run around-obstacle` | Checklist: drive round a block |
| `pixi run teleop` | Keyboard driving |
| `pixi run foxglove` | Foxglove bridge, port 8765 |
| `pixi run status` / `btlog` | Live numbers / tablet traffic |
| `pixi run bag` / `bag-all` | Record everything except / including camera images |
| `pixi run panels` / `import-symbols` | Regenerate the sim's symbol images |

## Tablet ↔ Pi messages

One line per message, ending in `\n`. Cells `0`–`19`, origin bottom-left.

| Direction | Line | Meaning |
| --- | --- | --- |
| tablet → Pi | `OBSTACLE,<n>,<x>,<y>,<N/E/S/W>` | Obstacle *n*; `x`,`y` = cell × 10 |
| tablet → Pi | `DONE` | All obstacles sent: plan now |
| tablet → Pi | `BEGIN` | Start the run |
| tablet → Pi | `STOP` | Stop |
| tablet → Pi | `RESET` | Car is back at the start: reset the pose (like `pixi run reset`) |
| tablet → Pi | `f` `b` `fl` `fr` `bl` `br` | Manual drive, one short burst per tap |
| tablet → Pi | `CLEAR` | Tablet cleared its map (Pi resends the car's position) |
| Pi → tablet | `ROBOT,<x>,<y>,<N/E/S/W>` | Cell under the middle of the rear axle, and facing (every 2 s and on change) |
| Pi → tablet | `TARGET,<n>,<id>` | Image ID read on obstacle *n* (`UNKNOWN` if none) |
| Pi → tablet | `PLAN:<WAITING\|PLANNING\|DONE>` | Plan state |
| Pi → tablet | `RESET:<WAITING\|DONE>` | Car confirmed at the start pose |
| Pi → tablet | `STATUS:<text>` | `Waiting`, `Ready`, `Going to obstacle n`, `Scanning obstacle n`, `Finished`, `Stopped` |

*Ready* = PLAN `DONE` and RESET `DONE`.

**Test the link by hand** (stack running): the tablet → Pi direction shows in `pixi run btlog`
as you press buttons. For Pi → tablet, send any line:

```bash
pixi run -- ros2 topic pub --once /bluetooth_tx std_msgs/msg/String "{data: 'STATUS:Ready'}"
```
