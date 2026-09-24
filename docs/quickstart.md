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

    Find the STM32's serial device (varies by host):

    ```bash
    ls /dev/ttyACM* /dev/ttyUSB* 2>/dev/null
    ```

    ```bash
    pixi run real serial_port:=/dev/ttyACM0   # substitute the device found above
    ```

    ### 6. Drive & Autonomy

    Flip the `PD3` motor ON/OFF switch to ON — the wheels stay locked at 0% PWM until an active host
    link exists (`pixi run real` must already be running) **and** the switch is ON. In a second terminal:

    === "🕹️ Manual (teleop)"

        ```bash
        cd mdp_ros
        pixi run teleop
        ```

        Key legend prints in that terminal.

    === "🤖 Autonomous (Task Runner)"

        ```bash
        cd mdp_ros
        pixi run task1   # Task 1: exploration + TSP planner
        # or
        pixi run task2   # Task 2: fastest path
        ```

        See [Algorithm](rpi/algorithm.md).

    ### 7. (Optional) Visualize

    ```bash
    cd mdp_ros
    pixi run foxglove   # foxglove_bridge on ws://localhost:8765
    ```

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

    ```bash
    pixi run sim
    ```

    ### 4. Drive & Autonomy

    In a second terminal:

    === "🕹️ Manual (teleop)"

        ```bash
        cd mdp_ros
        pixi run teleop
        ```

        Key legend prints in that terminal.

    === "🤖 Autonomous (Task Runner)"

        ```bash
        cd mdp_ros
        pixi run task1   # Task 1: exploration + TSP planner
        # or
        pixi run task2   # Task 2: fastest path
        ```

        See [Algorithm](rpi/algorithm.md).

    ### 5. (Optional) Visualize

    ```bash
    cd mdp_ros
    pixi run foxglove   # foxglove_bridge on ws://localhost:8765
    ```

=== "✅ Pre-run & Connection Tests"

    ### Pre-run checklist

    Before every run, in this order:

    1. **Battery** — OLED page 1 shows `B:xx.xV`; charge if low.
    2. **E-stop (motor switch)** — the STM32's onboard `PD3` switch must be **ON**. The OLED shows
       `ES:RDY` and `ros2 topic echo /estop` prints `data: false`. Engaged (`ES:ENG`, `data: true`)
       means the firmware refuses to drive, the runner won't start and the tablet shows `ESTOP:ON`.
       (Switch polarity is still an unverified assumption, see [STM32](stm32/index.md).)
    3. **STM32 plugged in** — `ls /dev/ttyACM*` shows the device.
    4. **Stack up** — `pixi run real1` running with no errors (it starts the serial bridge, the
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

## Pixi Task Reference (`mdp_ros`)

Every task above, plus the ones the walkthroughs don't use directly:

**Workspace**

| Task | Command |
| --- | --- |
| `pixi run build` | `colcon build --symlink-install` |
| `pixi run test` | `colcon test` |
| `pixi run clean` | `rm -rf build install log` |

**Simulation (Gazebo)**

| Task | Command |
| --- | --- |
| `pixi run sim` | `ros2 launch mdp_bringup sim.launch.py` |
| `pixi run sim-task2` | `ros2 launch mdp_bringup task2_sim.launch.py` — Gazebo Task 2 arena, YOLO detector & Task 2 runner |

**Real hardware (Pi + STM32)**

| Task | Command |
| --- | --- |
| `pixi run real` | `ros2 launch mdp_bringup real.launch.py serial_port:=/dev/ttyACM0` |
| `pixi run task1` | `ros2 run mdp_bringup task1_runner.py` — run alongside `real`/`sim`, not instead of it |
| `pixi run task2` | `ros2 run mdp_bringup task2_runner.py` — run alongside `real`/`sim`, not instead of it |
| `pixi run teleop` | `ros2 run teleop_twist_keyboard ...` — key legend prints in the terminal it runs in |
| `pixi run go` | Call `/start_run` — start the planned run (same as the tablet's start) |
| `pixi run stop` | Call `/stop_run` — halt the follower and planning, hold zeros |
| `pixi run reset` | Call `/reset_run` — pose/odometry back to the start pose; obstacles and plan are kept |
| `pixi run bt-send "<line>"` | Send a raw line to the tablet over Bluetooth |
| `pixi run bt-rx` | Print every line the tablet sends |

**Vision & debugging**

| Task | Command |
| --- | --- |
| `pixi run vision` | `ros2 launch mdp_vision vision.launch.py` — standalone webcam + YOLO, no RPi camera needed |
| `pixi run foxglove` | `ros2 launch foxglove_bridge foxglove_bridge_launch.xml` |
| `pixi run bag` | `ros2 bag record -a -o bags/rosbag2_<timestamp>` |

`mdp_bridge`'s `serial_bridge_node` normally launches as part of `pixi run real`; to run it standalone instead:

```bash
ros2 run mdp_bridge serial_bridge_node --ros-args -p serial_port:=/dev/ttyUSB0
```

---

## STM32 Build & Flash Reference (`mdp_stm32`)

Same commands as [step 2 of the Real Hardware walkthrough](#2-flash-the-stm32-firmware) above —
no separate reference needed.
