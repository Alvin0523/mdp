# AGENTS.md - operating the `mdp` workspace

NTU MDP Group 14: an Ackermann robot car (Raspberry Pi 4 + STM32 + Android tablet) for task 1
(read every image) and task 2 (fastest car), plus a Gazebo simulation running the same code. This
repo holds the docs and the three code repos as submodules. Human docs: `docs/` (site:
https://alvin0523.github.io/mdp/).

## Layout

| Folder | What | Own rules |
| --- | --- | --- |
| `mdp_ros/` (submodule, Alvin0523/mdp_ros) | ROS 2 Jazzy: task runners, planning, vision, bridges, Gazebo | **`mdp_ros/AGENTS.md`** - read it before touching anything there |
| `mdp_stm32/` (submodule, Alvin0523/mdp_stm32) | STM32F407 firmware (PlatformIO) | below |
| `mdp_android/` (submodule, RashI0192/MDP-APP) | The tablet app (Java) | Rashi's / Albert's repo: don't change it; report mismatches instead |
| `docs/` | The Zensical docs site | below |
| `hardware/` | 3D-print STLs | - |

## Environment

Everything runs through **pixi**, from the folder whose `pixi.toml` it is. Never `pip install` or
`source /opt/ros`.

| Where | Commands |
| --- | --- |
| `mdp/` (docs) | `pixi run serve` (live preview, port 8000) · `pixi run build` (must say `No issues found`) |
| `mdp_ros/` | `pixi run build` · `pixi run test` · the runs: `docs/quickstart.md`, `mdp_ros/AGENTS.md` |
| `mdp_stm32/` | `pixi run build` · `flash` · `monitor` · `probe` · `udev` - flash **only when asked** |

The repos are locked for `osx-arm64`, `linux-64` and `linux-aarch64` (the Pi). Windows: WSL.

## Helping someone get started

New to the project? Walk them through `docs/quickstart.md` in this order:

| Step | What | Where |
| --- | --- | --- |
| 1 | Install pixi, clone with submodules, `pixi install` + `pixi run build` in `mdp_ros/` | Quickstart §1 |
| 2 | Run task 1 in the **sim** (no car needed): Gazebo + the laptop's half + Foxglove | Quickstart §2, §4 |
| 3 | ROS 2 basics: at least one team member should know ROS 2 | `docs/index.md`, `docs/rpi/` |
| 4 | Their **own car**: calibrate it, then put the numbers in the URDF / `navigation.yaml` - every car differs, even the same model | Quickstart §7, §8, §9 |
| 5 | The real car: network, flash the STM32, the checklist | `docs/rpi/network.md`, Quickstart §1, §3 |
| 6 | What to improve next | Quickstart §11 |

Our measured numbers are for our car only: never tell someone to trust them on theirs.

## How a run works (one picture)

Every run = **the car** (`pixi run pi` on the Pi, or `pixi run sim` = Gazebo on the laptop) +
**the laptop** (`pixi run laptop`: YOLO, Hybrid A*) + **Foxglove** (`pixi run foxglove`, buttons
RESET POSE / SETUP / GO / STOP / REC). `pixi run car` / `sim-car <launch args>` start all three in
one zellij window. Pi ↔ laptop: Tailscale + Cyclone DDS (`docs/rpi/network.md`). Tablet: Bluetooth
to the Pi (`docs/rpi/bluetooth.md`).

## Docs (`docs/`)

- Navigation and page list: `zensical.toml` `nav`. Every page starts with `icon: lucide/<name>`
  front matter.
- Write for a reader who doesn't know ROS: short sentences, a diagram (mermaid) or a table before
  paragraphs, no history, no dates in prose (dates only on measurements), no TODO lists, no vendor
  (WHEELTEC) references.
- One fact in one place: commands in `quickstart.md`, how the tasks work in `tasks.md`, the tablet
  protocol in `rpi/bluetooth.md`, the network in `rpi/network.md`, problems in `troubleshooting.md`,
  what to improve next in `quickstart.md` §11.
- Check what you write against the code (and the app's code in `mdp_android/`) - not against older
  docs.
- Images go in `docs/images/`. Build (`pixi run build`) before committing.

## Git

- Commit or push **only when the user asks**. Branch `main` everywhere.
- Submodule change: commit and push inside the submodule first, then commit the pointer bump here.
- **No `Co-Authored-By` or other AI attribution** in commit messages.
- Don't commit `.claude/`, `datasets/`, `.DS_Store`, `site/`, or files the user didn't ask for (e.g.
  their local edits to `mdp_ros/src/mdp_bringup/config/tasks.yaml`); ask about untracked files you
  didn't create.

## Safety

- A command that drives (`start`, `calib`, `teleop`, `around-obstacle`, GO) moves the **real** car
  when `pixi run pi` is up - only when the user asks.
- Test sims must be isolated from the user's (separate `ROS_DOMAIN_ID` and `GZ_PARTITION`, see
  `mdp_ros/AGENTS.md`); never `pkill ros2` / `gz`.

## Working with the user

- Agree the design before implementing or running sims; small fixes inside the task are fine.
- Answer with tables and diagrams, short. When sim and real (or two options) differ, list the
  differences and let the user choose.
