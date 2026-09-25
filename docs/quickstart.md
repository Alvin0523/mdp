---
icon: lucide/play
---

# Quickstart

Commands-only runbook to get the robot driving — for *why* any of this works, see
[RPi docs](rpi/index.md) and [STM32 docs](stm32/index.md).

!!! tip "Viewing this docs site locally"
    From the `mdp` repo root: `pixi install` then `pixi run serve` — live-reloading preview at
    `http://127.0.0.1:8000`.

!!! note "Sim vs. Real Hardware"
    Same kinematics, swappable hardware plugin:

    ```mermaid
    graph LR
      ASC["ros2_control<br/>(ackermann_steering_controller)"]
      ASC --> GZ["gz_ros2_control<br/>(Gazebo Sim)"]
      ASC --> RT["topic_based_ros2_control<br/>(Real STM32)"]
    ```
    <p align="center"><strong>Fig. 1</strong> — Sim vs. Real Hardware</p>

    Full sequence: [RPi](rpi/index.md#end-to-end-control-telemetry-sequence).

=== "⚡ Real Hardware"

    ### 1. Clone — on the Raspberry Pi

    Everything below runs **on the robot's own Raspberry Pi** — it's both the ROS2 host and, if the
    ST-Link is plugged into its USB, where you flash the STM32 from.

    !!! tip "Connecting to the Pi"
        SSH in over [Tailscale](https://tailscale.com/) — no need to be on the same LAN, or to know
        the Pi's local IP.

    ```bash
    git clone --recurse-submodules https://github.com/Alvin0523/mdp.git
    cd mdp
    pixi install
    ```

    ### 2. Flash the STM32 firmware

    Wire it up first: ST-Link to the STM32 board over SWD, then the ST-Link's own USB into the Pi —
    `pixi run probe` (and `flash`) talk to the STM32 through that ST-Link, not through the board's
    own USART3 serial port.

    ```bash
    cd mdp_stm32
    pixi install       # PlatformIO + toolchain

    pixi run probe      # confirm ST-LINK/V2 + STM32F407 are detected
    pixi run build
    pixi run flash
    pixi run monitor    # optional: confirm boot banner + PE8 LED blink + OLED page cycling
    ```

    ARM64 Linux hosts (e.g. RPi 64-bit OS) need a `platform_packages` override in `platformio.ini`
    for the pinned toolchain — see [Troubleshooting](troubleshooting.md) if `pixi run build` fails there.

    ### 3. Pre-flight checks *(TBD — not yet performed)*

    !!! tip "User button (`PE0`)"
        Dual-purpose, depending on the motor switch:

        - Motor **OFF** → press cycles the OLED page.
        - Motor **ON** → press runs the self-test sequence (drives the motors — keep the wheels off
          the ground).

    Full checklist: [STM32: Verification Checklist](stm32/index.md#verification-checklist-post-flash-bring-up).

    ### 4. Build the ROS2 side

    ```bash
    cd mdp_ros
    pixi install
    pixi run build
    ```

    ### 5. Launch

    Put the car in the start box facing North (arena +Y), flip the `PD3` motor switch **ON**, and find
    the STM32's serial device (varies by host):

    ```bash
    ls /dev/ttyACM* /dev/ttyUSB* 2>/dev/null
    ```

    Then, from `mdp_ros`, bring up the robot. All options are `name:=value` arguments appended to the
    command, see [Launch arguments](#launch-arguments):

    ```bash
    pixi run real task:=1                  # task 1 runner, obstacles from the tablet
    pixi run real task:=1 vision:=true     # ...plus the Pi camera and YOLO
    pixi run real task:=2 vision:=true     # task 2 runner
    pixi run drive                         # bare car: no runner, no camera (motion tests)
    ```

    `pixi run real` uses `/dev/ttyACM0`; add `serial_port:=/dev/ttyUSB0` if yours differs.

    ### 6. Run

    === "🤖 Task 1"

        1. **Obstacles** — place them on the tablet and send. No tablet? `pixi run setup` publishes
           `mdp_ros/src/mdp_bringup/config/test_obstacles.yaml` instead.
        2. **Wait for the plan** — tablet `PLAN:DONE`, or `All legs planned.` in the launch terminal.
        3. **Reset** — tablet Reset, or `pixi run reset`. Needed before every run.
        4. **Go** — tablet BEGIN, or `pixi run go`.
        5. **Stop** any time — tablet STOP, or `pixi run stop`. To run again: put the car back, reset, go.

        At each checkpoint the car stops for 3 s while YOLO looks, then sends `TARGET,<obstacle>,<id>`.
        If `go` does nothing, the launch terminal says why (still planning / reset first / no obstacles).

    === "🏁 Task 2"

        ```bash
        pixi run go
        ```

        No obstacles and no reset needed.

    === "🕹️ Manual"

        With `pixi run drive` (or `task:=0`): the tablet's arrow buttons drive the car, or:

        ```bash
        pixi run teleop     # key legend prints in this terminal
        ```

    ### 7. Watch

    See [Watching a run](#watching-a-run).

    Known issues and TODOs: [RPi](rpi/index.md#todo) and
    [STM32](stm32/index.md#todo).

=== "▶️ Simulation"

    ### 1. Clone — on your dev machine

    ```bash
    git clone --recurse-submodules https://github.com/Alvin0523/mdp.git
    cd mdp
    pixi install
    ```

    ### 2. Build

    ```bash
    cd mdp_ros
    pixi install
    pixi run build
    ```

    ### 3. Launch

    From `mdp_ros`, same arguments as the real robot, see [Launch arguments](#launch-arguments):

    ```bash
    pixi run sim task:=1 vision:=true      # task 1: Gazebo arena, obstacles from test_obstacles.yaml
    pixi run sim task:=2 vision:=true      # task 2 arena
    pixi run sim                           # bare car in the task 1 arena (drive it yourself)
    pixi run sim task:=1 gui:=false        # no Gazebo window (lighter; watch in Foxglove)
    ```

    In sim the obstacles are loaded automatically from
    `mdp_ros/src/mdp_bringup/config/test_obstacles.yaml` — the same file places them in Gazebo and
    sends them to the planner. Edit it (tablet cells `cell_x`/`cell_y` 0–19, `facing`, `symbol`) to
    change the layout, or use the real tablet with `obstacles:=tablet` (pair it with the laptop so
    `/dev/rfcomm0` exists).

    ### 4. Run

    === "🤖 Task 1"

        Wait for `All legs planned.` in the launch terminal (about a minute), then in a second terminal:

        ```bash
        pixi run reset
        pixi run go
        ```

        `pixi run stop` stops it. To run again: restart `pixi run sim …` (the car can't be put back by hand).

    === "🏁 Task 2"

        ```bash
        pixi run go
        ```

    === "🕹️ Manual"

        ```bash
        pixi run teleop     # key legend prints in this terminal
        ```

    ### 5. Watch

    See [Watching a run](#watching-a-run). Gazebo's true car pose is on `/sim/ground_truth` — plot it
    against `/odometry/filtered` to see odometry drift.

=== "✅ Pre-run & Connection Tests"

    ### Pre-run checklist

    Before every run, in this order:

    1. **Battery** — OLED page 1 shows `B:xx.xV`; charge if low.
    2. **E-stop (motor switch)** — the STM32's onboard `PD3` switch must be **ON**. The OLED shows
       `ES:RDY` and `ros2 topic echo /estop` prints `data: false`. Engaged (`ES:ENG`, `data: true`)
       means the firmware refuses to drive, the runner won't start and the tablet shows `ESTOP:ON`.
       (Switch polarity is still an unverified assumption, see [STM32](stm32/index.md).)
    3. **STM32 plugged in** — `ls /dev/ttyACM*` shows the device.
    4. **Stack up** — `pixi run real task:=1` running with no errors (it starts the serial bridge, the
       Bluetooth bridge and the runner).
    5. **Tablet connected** — the app shows *Connected to …*, and
       `ros2 topic echo /bluetooth_bridge/link_ok` prints `data: true`.
    6. **Car at the start position**, then `pixi run reset` → tablet **Reset** indicator `DONE`.
    7. **Obstacles sent** from the tablet → **Plan** indicator `PLANNING` then `DONE`.
    8. Tablet status says **`Ready`** → press start. (`pixi run go` does the same from the Pi.)

    Stop at any time with the tablet's STOP toggle or `pixi run stop`; after a stop, put the car back and
    `pixi run reset` — the plan is kept.

    ### Connection tests

    Run these from `mdp_ros` on the Pi with the stack up.

    **Pi → tablet** — `bt-send` writes any raw line straight to the tablet:

    ```bash
    pixi run bt-send "STATUS:Ready"     # tablet status text changes
    pixi run bt-send "ROBOT,4,4,N"      # robot icon moves (needs the new ROBOT handler in the app)
    pixi run bt-send "TARGET,1,11"      # obstacle 1 shows target ID 11
    pixi run bt-send "ESTOP:ON"         # e-stop indicator, once the app has one
    ```

    A generic Bluetooth terminal app shows these as plain text. Our own app only reacts to lines it
    recognises; anything else logs *unknown message received*. Lines sent this way are **not** replayed
    on reconnect.

    **Tablet → Pi** — `bt-rx` prints every line the tablet sends:

    ```bash
    pixi run bt-rx      # then press buttons / place obstacles on the tablet
    ```

    Expect `OBSTACLE,n,x,y,F`, `DONE`, `BEGIN`, `STOP`, `f`/`b`/`fl`/`fr`/`bl`/`br`. Nothing appearing
    usually means the app isn't ending its lines with `\n`.

    **STM32 link** — wheels off the ground first:

    ```bash
    pixi run teleop     # i = forward, , = backward, k = stop
    ```

    **E-stop** — flip the motor switch and watch `ros2 topic echo /estop` change; with a run in
    progress the runner aborts (`Stopped`).

    **Wi-Fi** *(if the Pi is set up as the hotspot)* — join it from a laptop and open the Pi's fixed IP
    in a browser; `ssh` to the same address to run the tasks.

    ### Tablet ↔ Pi message reference

    One `\n`-terminated line per message. Grid cells are 10 cm, `0`–`19`, origin bottom-left.

    | Direction | Line | Meaning |
    | --- | --- | --- |
    | tablet → Pi | `OBSTACLE,<n>,<x>,<y>,<N/E/S/W>` | Obstacle *n*; `x`,`y` = cell × 10; facing `-1` = removed |
    | tablet → Pi | `DONE` | All obstacles sent — plan now |
    | tablet → Pi | `BEGIN` | Start the car (rejected unless *Ready*) |
    | tablet → Pi | `STOP` | Stop everything (`pixi run stop`) |
    | tablet → Pi | `f b fl fr bl br` | Manual drive, one short burst per tap |
    | tablet → Pi | `CLEAR` | Tablet cleared its map (Pi resends the robot pose only) |
    | Pi → tablet | `ROBOT,<x>,<y>,<N/E/S/W>` | Robot's bottom-left cell (0–18) and facing |
    | Pi → tablet | `TARGET,<n>,<id>` | Image ID found on obstacle *n* |
    | Pi → tablet | `PLAN:<WAITING\|PLANNING\|DONE>` | Obstacles received and planned |
    | Pi → tablet | `RESET:<WAITING\|DONE>` | Car pose is at the start pose |
    | Pi → tablet | `ESTOP:<ON\|OFF>` | STM32 motor switch engaged |
    | Pi → tablet | `STATUS:<text>` | `Ready`, `Going to obstacle n`, `Scanning obstacle n`, `Finished`, `Stopped`, `Not ready` |

    *Ready* = plan `DONE` **and** reset `DONE` **and** e-stop `OFF`.

---

## Launch arguments

`pixi run real` and `pixi run sim` start the same launch file (`mdp_bringup/launch/mdp.launch.py`);
append any of these:

| Argument | Values | Default | What it does |
| --- | --- | --- | --- |
| `task:=` | `0` `1` `2` | `0` | `0` bare car (tablet manual drive, no runner) · `1` explore + recognise · `2` slalom |
| `vision:=` | `true` `false` | `false` | Camera + YOLO (`/yolo_result`, annotated image) |
| `obstacles:=` | `tablet` `yaml` | real `tablet`, sim `yaml` | `yaml` also publishes `layout` once at start, like `pixi run setup`. The tablet link is up either way. |
| `layout:=` | path | `config/test_obstacles.yaml` | Obstacle file (tablet cells). In sim it also places the Gazebo obstacles. |
| `start_x:=` `start_y:=` `start_yaw:=` | metres, rad | task 0/1 `0.15 0.15 1.5708`, task 2 `0 0 0` | Where the car starts in the arena |
| `gui:=` | `true` `false` | `true` | Sim only: Gazebo window |
| `model:=` | model dir | `best_ncnn_model_v2` | YOLO model under `mdp_vision/models/` |
| `serial_port:=` | device | `/dev/ttyACM0` (via `pixi run real`) | Real only: STM32 USART3 |
| `bluetooth_device:=` | device | `/dev/rfcomm0` | Tablet RFCOMM link |

Shortcuts: `pixi run sim1` / `sim2` = `sim task:=1` / `task:=2`; `pixi run drive` = `real task:=0 vision:=false`.

---

## Watching a run

| What | How |
| --- | --- |
| Everything, visually | `pixi run foxglove`, then open `ws://localhost:8765` (sim) or `ws://<pi>:8765` (real) in Foxglove and import `mdp_ros/foxglove/mdp_layout.json` once |
| What the car is deciding | `pixi run runlog` — GO / leg → obstacle & checkpoint / arrived / YOLO / TARGET / next / finished |
| Live numbers | `pixi run status` — state, checkpoint, distance left, chased waypoint, FWD/REV, speed, scan timer |
| Tablet traffic | `pixi run btlog` — LINK UP/DOWN, `TABLET -> RPI …`, `RPI -> TABLET …` |
| Everything else | `/rosout` (Foxglove Log panel) |
| Record for later | `pixi run bag` (Ctrl+C to stop; saved in `mdp_ros/bags/`) |

---

## Pixi Task Reference (`mdp_ros`)

**Workspace**

| Task | What it does |
| --- | --- |
| `pixi run build` | Build all packages (`colcon build --symlink-install`) |
| `pixi run test` | Run the package tests |
| `pixi run clean` | Delete `build/ install/ log/` |

**Bring-up** (append [launch arguments](#launch-arguments))

| Task | What it does |
| --- | --- |
| `pixi run real` | Real robot (serial `/dev/ttyACM0`) |
| `pixi run drive` | Real robot, bare car, no camera — motion tests |
| `pixi run sim` | Gazebo |
| `pixi run sim1` / `pixi run sim2` | Gazebo task 1 / task 2 |
| `pixi run vision` | Camera + YOLO only, no robot |

**Run control** (same for real and sim)

| Task | What it does |
| --- | --- |
| `pixi run setup` | Send the obstacles in `test_obstacles.yaml` (instead of the tablet) |
| `pixi run reset` | Reset the pose to the start pose — before every task 1 run |
| `pixi run go` | Start the run (like the tablet's BEGIN) |
| `pixi run stop` | Stop and hold zero speed |
| `pixi run target <obstacle> <id>` | Debug: send `TARGET,<obstacle>,<id>` to the tablet |
| `pixi run teleop` | Keyboard driving |
| `pixi run dist <m>` / `rotate <deg>` / `circle` | Motion tests (bare car) |

**Watching**

| Task | What it does |
| --- | --- |
| `pixi run foxglove` | Foxglove bridge on port 8765 (display frame `map`) |
| `pixi run status` / `runlog` / `btlog` | Live state / task events / tablet traffic |
| `pixi run bag` / `bag-all` | Record all topics except / including camera images |

---

## STM32 Build & Flash Reference (`mdp_stm32`)

Same commands as [step 2 of the Real Hardware walkthrough](#2-flash-the-stm32-firmware) above —
no separate reference needed.
