---
icon: lucide/network
---

# System Architecture

This page describes the intended architecture for the robot: how the ROS2/Gazebo side (`mdp_ros`) and the STM32 firmware side (`mdp_stm32`) fit together.

!!! warning "Design doc, not a status report"
    This describes the **target** architecture. See [What's implemented vs. TODO](#whats-implemented-vs-todo) — most of this doesn't exist yet.

## The stack

**Host** (`mdp_ros`, `pixi`-managed, `robostack-jazzy` channel):

| Tool | Reason |
| --- | --- |
| ROS2 Jazzy (`ros-base`) | Node graph, topics, the whole pub/sub layer everything else plugs into |
| `xacro`, `robot_state_publisher`, `joint_state_publisher(-gui)` | Build and publish the robot's URDF/TF tree |
| `rviz2` | Visualize URDF/TF/sensor data while developing |
| `teleop_twist_keyboard` | Manual `/cmd_vel` input for testing before autonomy exists |
| `ros2_control`, `ros2_controllers`, `controller_manager` | Standard controller framework — includes `ackermann_steering_controller` for car-like steering |
| `gz_ros2_control`, `ros_gz` | Gazebo hardware_interface + host↔sim bridge |
| `colcon`, `compilers`, `cmake`, `ninja` | Standard ROS2 build toolchain |

**SBC Host** (`mdp_ros` on Raspberry Pi 4B 4GB):
Runs ROS2 Jazzy, vision pipeline (RPi Camera V2), micro-ROS Agent, and `ros2_control` (`ackermann_steering_controller`).

**Link** — micro-ROS over serial (Type-C USB-UART / `USART3` @ 115200 baud), connecting the micro-ROS Agent on RPi4B to the STM32 MCU. See [ADR 0001](adr/0001-microros-transport.md).

**MCU** (`mdp_stm32`):

| Tool | Reason |
| --- | --- |
| STM32F407VET6 (C30D V2.1 board) | Integrated controller board — AT8236 motor PWM driver for 2× MG513P3012V Hall encoder motors + HWZ020 steering servo. See [Hardware Components](hardware.md) |
| Zephyr RTOS | Firmware base — board bring-up done. See [ADR 0004](adr/0004-zephyr-firmware-base.md) |
| micro-ROS (rclc) | ROS2-on-MCU client library — publishes joint state & telemetry, subscribes to motor/steering commands. See [ADR 0001](adr/0001-microros-transport.md) |

**Repo-level tooling** (`mdp/`, outside the robot stack itself):

| Tool | Reason |
| --- | --- |
| `pixi` | One environment manager for docs + both submodules — no per-tool venv/conda juggling |
| Git submodules | `mdp_ros`/`mdp_stm32` are independently developed, versioned, and released — a submodule pins an exact commit rather than always tracking latest |
| OpenSpec | Spec-driven propose/apply/archive workflow — see [Dev Workflow & OpenSpec Setup](dev_workflow.md) |
| Zensical | This docs site generator |

## Why these choices

Full rationale in each ADR — summarized here:

| Choice | Instead of | ADR |
| --- | --- | --- |
| micro-ROS for host↔MCU transport | zenoh-pico / Pico-ROS | [0001](adr/0001-microros-transport.md) |
| `topic_based_ros2_control` hardware_interface | custom hardware_interface | [0002](adr/0002-topic-based-hw-interface.md) |
| Kinematics on host (`ackermann_steering_controller`) | kinematics on MCU | [0003](adr/0003-pattern-b-kinematics.md) |
| Zephyr as firmware base, only swap the transport | rewriting firmware from scratch | [0004](adr/0004-zephyr-firmware-base.md) |

## Diagram

### 1. Layered Architecture: Simulation Mode vs. Real Hardware Mode

```text
========================================================================================
                                     INPUT LAYER
                             [pixi run teleop] / [Nav2]
                                         │
                                         ▼ (/cmd_vel)
                               [ackermann_steering_controller] (ROS2 Host Controller)
========================================================================================
                                HARDWARE INTERFACE LAYER
                                         │
                       ┌─────────────────┴─────────────────┐
                       ▼ (Simulation Mode)                 ▼ (Real Hardware Mode)
                gz_ros2_control                       topic_based_ros2_control
                       │                                   │
                       │                                   │ /joint_commands & /joint_states
                       ▼                                   ▼
========================================================================================
                                   EXECUTION LAYER
              [Gazebo 3D World]                   [micro-ROS Agent] (Host)
           (Simulated Physics & Mesh)                      │
                                                           │ Serial UART (USB Type-C)
                                                           ▼
                                                  [STM32 MCU Firmware]
                                                  - Motor PWM (AT8236)
                                                  - Steering Servo (HWZ020)
                                                  - Encoders & IMU
========================================================================================
```

### 2. Host ↔ MCU Node Graph

```mermaid
flowchart TD
  subgraph INPUT["Input Layer"]
    TELEOP["teleop_twist_keyboard / Nav2"]
  end

  subgraph HOST["Host ROS2 Runtime (pixi env)"]
    ASC["ackermann_steering_controller"]
    
    subgraph HWI["Hardware Interface Choice"]
      GZ_HWI["gz_ros2_control<br/>(Simulation Plugin)"]
      TB_HWI["topic_based_ros2_control<br/>(Real Hardware Plugin)"]
    end

    AGENT["micro-ROS Agent"]
  end

  subgraph TARGETS["Execution Target Layer"]
    GZ_SIM["Gazebo 3D Simulation<br/>(mini_akm_robot.urdf)"]
    
    subgraph MCU["STM32F407VET6 MCU (Zephyr + rclc)"]
      MOTORS["Dual MG513P3012V Motors<br/>(AT8236 Driver)"]
      SERVO["HWZ020 Steering Servo"]
      SENSORS["Hall Encoders & IMU"]
    end
  end

  %% Connections
  TELEOP -->|/cmd_vel| ASC
  ASC -->|Joint Commands| GZ_HWI
  ASC -->|Joint Commands| TB_HWI

  GZ_HWI <-->|Physics Forces / States| GZ_SIM
  TB_HWI <-->|/joint_commands & /joint_states| AGENT
  AGENT <-->|Serial UART / USART3| MCU

  MCU --> MOTORS
  MCU --> SERVO
  SENSORS --> MCU
```

## MDP Competition Tasks & Operational Requirements

Based on the official course documentation (`Rules for the Task 1 and Task 2.docx` and `MDP assessment and system checklist.docx`):

### 🎯 Task 1: Automatic Exploration & Image Recognition (6-Minute Limit)
- **Arena:** Open 2.0m × 2.0m grid arena with 5 goal obstacles placed at supervisor-specified $(x,y)$ coordinates.
- **Preparation (2 Mins):** Android Tablet receives obstacle coordinates and target faces via Bluetooth, sending setup to the RPi4B host.
- **Autonomous Run:**
  - Robot starts in Carpark Zone.
  - Computes Hamiltonian Path to visit each obstacle (approaching within 20–50 cm).
  - Captures images using **RPi Camera Module V2**.
  - Runs Image Recognition model (YOLOv8 / CNN) to identify target symbol IDs.
  - Streams real-time updates to Android Tablet via Bluetooth (`TARGET, <Obstacle_ID>, <Target_ID>`).
  - Automatically stops within 6 minutes.
- **RAW Image Display Rule:** Must present a tiled window on PC/Tablet displaying raw captured images with bounding boxes and identified ID labels.

### ⚡ Task 2: Fastest Path Challenge (3-Minute Limit)
- **Goal:** Robot starts from Carpark Zone, navigates automatically to Goal Obstacle.
- **Symbol Recognition:** Goal Obstacle features a Left Arrow ($\leftarrow$) or Right Arrow ($\rightarrow$) image.
- **Navigation:**
  - If **Right Arrow**: Robot loops around the right side of the obstacle.
  - If **Left Arrow**: Robot loops around the left side of the obstacle.
- **Return & Stop:** Robot returns and automatically stops inside the Carpark Zone within 3 minutes.
- **Penalties:** 10s penalty for touching obstacles. Disqualification for hitting carpark wall or bulldozing obstacles.

---

## Message flow: teleop → motor

One end-to-end path across the host↔MCU boundary — where integration bugs (units, topic names, QoS) actually happen.

```mermaid
sequenceDiagram
  participant T as teleop_twist_keyboard
  participant ASC as ackermann_steering_controller
  participant HWI as topic_based_ros2_control
  participant AGENT as micro-ROS Agent (host)
  participant MCU as rclc node (MCU)
  participant MOT as Motor PWM (AT8236)

  T->>ASC: /cmd_vel (geometry_msgs/Twist)
  ASC->>HWI: per-joint velocity/position commands
  HWI->>AGENT: /joint_commands (topic)
  AGENT->>MCU: serial (micro-ROS transport)
  MCU->>MOT: PWM duty cycle
  MCU-->>AGENT: /joint_states (encoder feedback)
  AGENT-->>HWI: serial (micro-ROS transport)
  HWI-->>ASC: joint state interfaces
```

In Gazebo, same controller talks to `gz_ros2_control` instead of `topic_based_ros2_control`. See [ADR 0003](adr/0003-pattern-b-kinematics.md).

For the live node graph, run `rqt_graph` — don't hand-maintain a diagram that'll go stale.

## Robot operating modes

!!! warning "Placeholder — not designed yet"
    No mode-switching logic exists (see [status table](#whats-implemented-vs-todo)). TODO once mode switching / e-stop is actually implemented — not before.

```mermaid
stateDiagram-v2
  [*] --> Idle
  Idle --> Teleop: TODO
  Teleop --> Autonomous: TODO
  Autonomous --> Teleop: TODO
  Idle --> EStopped: TODO
  Teleop --> EStopped: TODO
  Autonomous --> EStopped: TODO
  EStopped --> Idle: TODO
```

## What's implemented vs. TODO

### `mdp_ros` (host) — build order, each step needs the one above it

1. [x] `mdp_description` + `mdp_bringup` — ported from `references/mdp_ws/`
2. [x] Gazebo simulation — needs step 1
3. [x] `topic_based_ros2_control` + `ackermann_steering_controller` wiring on host — needs step 1

### `mdp_stm32` (MCU)

- [x] West workspace setup (`pixi run setup`) — done and verified; requires a pyocd-runner patch for `stm32f4_disco` since Zephyr v4.0.0 only ships `stm32cubeprogrammer`/`jlink` runners for this board upstream (applied automatically by the `west-patch` task, see `scripts/patch_pyocd_runner.sh`)
- [x] STM32F407VET6 board overlay / Zephyr bring-up — done and **flashed + verified on real hardware** (LED blink + RTT log confirm boot)
- [x] Motor PWM driver (AT8236) — PWM channel numbering bug fixed (`motor.c` channels were 0-indexed; Zephyr's STM32 PWM driver requires 1-indexed, matching `TIM_CHx`) and verified working over RTT
- [!] Motor PWM driver still models 4 motors (A/B/C/D) — the WHEELTEC C30D board wiring is generic (used across Mecanum/4WD/Ackermann/etc. configs), but this course kit's Ackermann chassis only drives 2 (Motor A = rear-left, Motor B = rear-right); confirmed against vendor source (`references/WHEELTEC/.../BALANCE/balance.c`, which explicitly zeroes `MOTOR_C`/`MOTOR_D` as "Out of use" in Ackermann mode). TODO: trim `motor.c`/`motor.h`/the board overlay to 2 motors, and fix `docs/stm32/pinouts.md`'s motor table (currently mislabels A/B as Front-Left/Front-Right implying 4WD)
- [ ] micro-ROS integration on MCU — not started; `app/west.yml` doesn't import a micro-ROS Zephyr module yet (just `cmsis`, `hal_stm32`, `picolibc`, `segger`). A local reference clone of the official [`micro-ROS/micro_ros_zephyr_module`](https://github.com/micro-ROS/micro_ros_zephyr_module) is already available at `references/micro_ros_zephyr_module/`, tested against Zephyr v4.0.0 — the exact version this workspace pins
- [ ] Steering servo driver — stub only
- [ ] Encoder reading — stub only
- [ ] IMU reading — stub only

## Reference

- Old firmware attempt (transport being replaced): `references/mdp_mcu/mdp_firmware`
- Old host-side control architecture notes: `references/mdp_ws/docs/control-architecture.md`
- Vendor stock firmware (reference only, not reused): `references/WHEELTEC/`
