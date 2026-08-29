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

**Vision & debugging**

| Task | Command |
| --- | --- |
| `pixi run vision` | `ros2 launch mdp_yolo vision.launch.py` — standalone webcam + YOLO, no RPi camera needed |
| `pixi run foxglove` | `ros2 launch foxglove_bridge foxglove_bridge_launch.xml` |
| `pixi run bag` | `ros2 bag record -a -o bags/rosbag2_<timestamp>` |

`mdp_bridge`'s `serial_bridge_node` normally launches as part of `pixi run real`; to run it standalone instead:

```bash
ros2 run mdp_bridge serial_bridge_node --ros-args -p serial_port:=/dev/ttyUSB0
```

---

## STM32 Build & Flash Reference (`mdp_stm32`)

Covered in step 2 of the Real Hardware walkthrough above. As standalone commands:

```bash
cd mdp_stm32
pixi install       # PlatformIO + toolchain
pixi run probe      # confirm ST-LINK/V2 + STM32F407 are detected
pixi run build
pixi run flash
pixi run monitor    # confirm boot banner + PE8 LED blink + OLED page cycling
```
