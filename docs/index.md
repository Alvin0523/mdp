---
icon: lucide/rocket
---

# mdp

A ROS2 + STM32 Ackermann-steered robot. This is the monorepo: it holds the docs site, a shared pixi env for tooling, and two git submodules that each do the actual work.

## Repo layout

```text
mdp/
├── docs/          this site
├── mdp_ros/       submodule — ROS2 Jazzy / Gazebo, host-side software
├── mdp_stm32/     submodule — Zephyr + micro-ROS firmware for the STM32 MCU
├── references/    old prototypes and vendor docs, kept for reference only
└── pixi.toml      root env: just docs tooling + submodule helper tasks
```

See [Architecture](architecture.md) for how `mdp_ros` and `mdp_stm32` fit together.

## One-time setup

1. Clone with submodules and install the pixi env:

    ```bash title="Clone"
    git clone --recurse-submodules <this-repo-url>
    cd mdp
    pixi install
    ```

    ??? tip "Already cloned without `--recurse-submodules`?"

        ```bash
        pixi run clone-all      # or clone-ros / clone-stm32 individually
        ```

2. Install the OpenSpec CLI and connect your AI tool — see [Dev Workflow & OpenSpec Setup](dev_workflow.md).

Every developer — whether you're setting this repo up for the first time or joining an existing project — runs the same steps above. Nothing is machine-specific or done "once by the first person"; `pixi.toml`, `west.yml`, and `.gitmodules` fully describe the environment, so a fresh clone reproduces it identically for anyone.

### New developer checklist

```bash title="Root env + submodules"
git clone --recurse-submodules <this-repo-url>
cd mdp
pixi install                # docs tooling + submodule helper tasks
```

```bash title="STM32 firmware (mdp_stm32)"
cd mdp_stm32
pixi run setup               # pack, west init/update/export, ARM SDK — one-shot
pixi run probe                # sanity check: ST-LINK/V2 + STM32F407 detected
cd ..
```

```bash title="ROS host software (mdp_ros)"
cd mdp_ros
# see mdp_ros/README.md or docs/ros/index.md for its setup task(s)
cd ..
```

```bash title="OpenSpec (per submodule, per machine)"
cd mdp_stm32   # and separately for mdp_ros
openspec init
cd ..
```

After this, `pixi run build` / `pixi run flash` inside `mdp_stm32` should work. If `pixi run setup` was interrupted partway (e.g. network drop during `west update`), just re-run `pixi run setup` — `pack` and `west-init` no-op safely, and `west update` resumes rather than re-fetching everything.

## Where to go next

Roughly the order you'll actually need these, from first setup to deep reference:

### Common Project Documentation

1. :building_construction: [**Architecture**](architecture.md) — the target system design and what's actually implemented vs. TODO.
2. :twisted_rightwards_arrows: [**Dev Workflow & OpenSpec Setup**](dev_workflow.md) — environment setup, OpenSpec CLI, and the day-to-day loop (branch, propose, apply, archive).
3. :wrench: [**Hardware Components**](hardware.md) — exact board revision, encoder type, motor — for telling this robot apart from a differently-built one.
5. :scroll: **ADRs** — permanent decision records; see [full log](adr/index.md) for context and how to add one.

    | # | Decision |
    |---|---|
    | [0001](adr/0001-microros-transport.md) | micro-ROS for the host↔MCU transport |
    | [0002](adr/0002-topic-based-hw-interface.md) | `topic_based_ros2_control` for the hardware interface |
    | [0003](adr/0003-pattern-b-kinematics.md) | Kinematics on host, not MCU (Pattern B) |
    | [0004](adr/0004-zephyr-firmware-base.md) | Keep Zephyr as the firmware base |

6. :rotating_light: [**Troubleshooting**](troubleshooting.md) — known gotchas and their fixes.
7. :books: [**References**](references.md) — external docs and what's in the local `references/` folder.

### Submodule Workspaces

8. :bot: [**ROS Workspace (`mdp_ros`)**](ros/index.md) — ROS2 Jazzy setup, build tasks, launch files, and Gazebo simulation.
9. :cpu: [**STM32 Firmware (`mdp_stm32`)**](stm32/index.md) — Zephyr RTOS firmware, micro-ROS node, pinouts, and drivers.

### Site Maintenance

10. :pencil: [**Authoring Reference**](authoring_reference.md) — Markdown/Zensical syntax cheatsheet, for editing this docs site itself.

## Docs site commands

```bash title="Preview / build"
pixi run serve    # local live-reload preview
pixi run build    # static build
```