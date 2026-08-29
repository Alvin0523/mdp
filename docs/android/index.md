---
icon: lucide/smartphone
---

# Android (Remote Controller App)

The Android app itself is **not part of this repository** — it's built and maintained separately by
the Android subteam. This page documents the *interface contract* other subteams need to know: what
the app sends/receives, since that's the part that has to stay in sync across repos.

!!! warning "`android_bridge_node` is referenced in architecture docs but not yet found in `mdp_ros/src`"
    The system architecture (see [Architecture: System Stack](../architecture.md#1-system-stack--who-talks-to-whom))
    describes a planned `android_bridge_node` (ROS2 Python node, `pyserial`/RFCOMM) bridging
    `/dev/rfcomm0` Bluetooth serial strings into ROS2 topics (`/cmd_vel`, `/odometry/filtered`) — a
    search of `mdp_ros/src` did not find this node implemented yet. Treat it as a planned interface
    until confirmed otherwise; update this warning once it exists.

## Interface contract (per [Assessment & Checklist](../assessment_checklist.md), Module C)

**App → Robot** (over Bluetooth RFCOMM serial):

- Interactive movement commands (buttons/gestures/tilt — manual text entry is explicitly disallowed by the rubric).
- Obstacle placement: `(x, y)` + assigned obstacle number, sent on touch-drag release.
- Target face orientation per obstacle (`N`/`S`/`E`/`W`).

**Robot → App** (over the same link):

- `TARGET, <Obstacle_ID>, <Target_ID>` — sent when a target symbol is identified (Task 1).
- `ROBOT, <x>, <y>, <direction>` — robot position/heading updates for the app's live 2D map.
- Status text updates (e.g. `"ready to start"`, `"looking for target 2"`) — must be selective/formatted, not raw log streaming (rubric C.4).

## How it fits into the system

See [Architecture: System Stack](../architecture.md#1-system-stack--who-talks-to-whom) for where this
sits relative to the RPi. The RPi is the only subsystem that talks to the Android app directly — STM32,
Vision, and Algorithm never see Bluetooth traffic, they only see the RPi's own ROS topics.
