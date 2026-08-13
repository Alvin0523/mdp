---
icon: lucide/scale
---

# Architecture Decision Records

!!! note "ADR vs. OpenSpec proposal"
    A proposal says *what we're building right now* — archived once it ships. An ADR says *why we chose X over Y* — stays true even after the code around it changes.

Write one when a decision would be expensive to undo (transport protocol, control-split pattern, hardware/firmware base). Routine implementation choices belong in the OpenSpec change itself, not here.

## Log

| # | Decision | Status |
|---|---|---|
| [0001](0001-microros-transport.md) | micro-ROS for the host↔MCU transport | :material-check-circle: Accepted |
| [0002](0002-topic-based-hw-interface.md) | `topic_based_ros2_control` for the hardware interface | :material-check-circle: Accepted |
| [0003](0003-pattern-b-kinematics.md) | Kinematics on host, not MCU (Pattern B) | :material-check-circle: Accepted |
| [0004](0004-zephyr-firmware-base.md) | Keep Zephyr as the firmware base | :material-check-circle: Accepted |

## Adding a new one

- [ ] Copy [`template.md`](template.md) to `docs/adr/000N-short-title.md` (next number, 2-4 word kebab-case slug)
- [ ] Fill in Context / Decision / Consequences
- [ ] Add a row to the table above
