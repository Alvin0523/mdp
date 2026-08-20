---
icon: lucide/wrench
---

# Troubleshooting

Real gotchas we've actually hit, so nobody has to rediscover them. Click a title to expand.

## :gear: OpenSpec

??? question "`openspec update --tools claude` fails: `error: unknown option '--tools'`"

    That flag was removed in a CLI update. Tool selection is now an interactive picker on both `openspec init` and `openspec update` — run the command with no flag and pick from the list. See [Dev Workflow & OpenSpec Setup](dev_workflow.md).

??? question "Not sure whether to run `init` or `update`"

    | Command | Use it for |
    | --- | --- |
    | `openspec init` | First time setting up your local `.claude/` adapter (new machine, new teammate, fresh clone). Safe to re-run; never touches `config.yaml`/`specs/`/`changes/`. |
    | `openspec update` | Refreshing an adapter you *already* have, after upgrading the CLI. Can't create one from scratch. |

??? question "`npm install -g @fission-ai/openspec@latest` warns about `allow-scripts`"

    ```
    npm warn allow-scripts 1 package has install scripts not yet covered by allowScripts
    ```

    npm blocking the package's `postinstall` script by default — a safety feature, not an error.

    !!! tip
        Usually harmless, try `openspec` first. Only run the suggested `--allow-scripts` command if something's actually missing/broken afterward.

??? question "Forgot to check `.gitignore` before trying a new AI tool"

    Setting up a tool other than Claude Code (Cursor, Codex, ...) for the first time? Its generated folder might not be in `.gitignore` yet. Check `mdp_ros/.gitignore` / `mdp_stm32/.gitignore` and add the folder name before your next `git add`.

## :satellite: micro-ROS Agent

??? question "`ros2 run micro_ros_agent ...` / building the `micro_ros_agent` colcon package fails with `fmt`/`spdlog` errors"

    Two different build failures, both from the same root cause: the ROS2 `micro_ros_agent` package's CMake SuperBuild pins an old (`v2.4.3`, ~2022) `Micro-XRCE-DDS-Agent`, and this workspace's conda environment resolves a very modern `fmt 12.1.0`.

    1. **Default build** (vendored `spdlog 1.9.2`, fetched fresh by the SuperBuild): fails with
        ```
        error: 'basic_runtime' is not a member of 'fmt'
        ```
        `fmt::basic_runtime` was reorganized away between fmt 8 (when spdlog 1.9.2 was written) and fmt 12 (what's actually on the include path here).

    2. **`-DUAGENT_USE_SYSTEM_LOGGER=ON`** (uses the conda-installed `spdlog 1.17.0` instead — compatible with `fmt 12` on its own): gets past that error, but then the *Micro-XRCE-DDS-Agent C++ source itself* (`Processor.cpp`, etc.) fails, because `fmt 12` removed the implicit `operator<<`-based formatting fallback for custom types (`IPv4EndPoint`, `SerialEndPoint`, ...) that this ~2022-era code relies on.

    Neither is fixable with another flag — it's a real version wall between an unmaintained-feeling vendored dependency and a modern `fmt`.

    **Fix**: don't build the ROS2-wrapped package at all. Build the standalone `Micro-XRCE-DDS-Agent` directly from [eProsima's own repo](https://github.com/eProsima/Micro-XRCE-DDS-Agent) at the current release (`v3.0.1` — plain `cmake`/`make`, no colcon/ament, no `micro_ros_msgs` dependency) — it compiles clean against `fmt 12` and still bridges into the ROS2 DDS graph (default middleware is Fast DDS). See [ROS Workspace docs](ros/index.md) for the actual commands (`pixi run agent-build` / `pixi run agent` in `mdp_ros`).

## :cpu: STM32 Firmware (`mdp_stm32`)

??? question "`pixi run flash` on Raspberry Pi (64-bit OS): `UnknownPackageError: ... toolchain-gccarmnoneeabi @ >=1.60301.0,<1.80000.0 ... for your system 'linux_aarch64'`"

    PlatformIO's `ststm32` platform pins `toolchain-gccarmnoneeabi` to a version range that has no `linux_aarch64` (64-bit ARM, e.g. Raspberry Pi OS 64-bit) build in the registry — only newer versions (e.g. `1.90301.0`) publish an aarch64 toolchain.

    **Fix**: override the toolchain version in `mdp_stm32/platformio.ini` under the board's `[env:...]` section:
    ```ini
    platform_packages =
        toolchain-gccarmnoneeabi@~1.90301.0
    ```
    See [platform-ststm32 issue #274](https://github.com/platformio/platform-ststm32/issues/274).

??? question "`pixi run flash` / `pixi run probe` fails with `Error: open failed` or `Error: reset device failed`"

    Both come from OpenOCD (bundled with PlatformIO) failing to talk to the target over SWD, not a firmware bug:

    - `open failed` — OpenOCD can't even open a USB connection to the ST-LINK probe itself. Check `lsusb` for the ST-LINK, reseat/replug its USB cable.
    - `reset device failed` — the ST-LINK is reachable over USB, but the target STM32 doesn't respond to reset over SWD. Most often this just means **the target board has no power** — some ST-LINK/V2 clones supply a small 3.3V rail to the target over the SWD header (sourced from the host's USB port), which is enough to run the bare MCU + OLED + IMU, but nowhere near enough once motor/servo PWM is actually driving real actuators (H-bridge + servo current draw browns out that rail). Power the C30D board from its own supply (battery/barrel jack) rather than relying on ST-LINK-sourced power once motors/servos are in the loop.

## :twisted_rightwards_arrows: Git / submodules

??? question "Cloned the repo but `mdp_ros/`/`mdp_stm32/` are empty"

    Need `--recurse-submodules` on clone, or afterward:

    ```bash
    pixi run clone-all
    ```

    See [Developer Setup](index.md#developer-setup).
