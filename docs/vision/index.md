---
icon: lucide/camera
---

# Vision (`mdp_ros/src/vision`)

Camera-based target/arrow recognition for Task 1 (image recognition) and Task 2 (arrow detection),
running as ROS2 nodes inside `mdp_ros` on the RPi.

!!! note "Stub page — fill in as the Vision subteam's own detail lands here"
    This page currently only reflects what's actually in the repo. Add architecture rationale, model
    training notes, accuracy/false-positive tracking, and camera calibration detail here as that work
    happens.

## What's actually in the repo

Package: `mdp_ros/src/vision/mdp_vision` — description in `setup.py`: *"Vision stack for MDP robot"*.

| File | Role |
| --- | --- |
| `mdp_vision/yolo_detector.py` | Ultralytics YOLO detector node |
| `mdp_vision/camera_publisher.py` | Standalone webcam publisher — dev-only, for testing without the RPi camera (`pixi run vision`) |
| `launch/vision.launch.py` | Launches the standalone dev-machine vision test path |
| `models/yolo26n_ncnn_model/` | Exported/converted YOLO26 model (NCNN format, for embedded inference) |

## How it fits into the system

Per the [Assessment & Checklist](../assessment_checklist.md), Task 1 requires detecting target
symbols 20-50cm from the robot and identifying target IDs; Task 2 requires detecting Left/Right arrow
symbols. See [Architecture: System Stack](../architecture.md#1-system-stack--who-talks-to-whom) for
where this sits relative to Algorithm and RPi.

- **Real hardware path**: `pixi run real` uses the RPi Camera Module V2 via `camera_ros` (not
  `camera_publisher.py`, which is the dev-only webcam substitute).
- **Sim/dev path**: `pixi run vision` launches `camera_publisher.py` + `yolo_detector.py` standalone,
  for testing detection logic without the actual RPi camera attached.
- **Consumers**: detections are read by the Algorithm subteam's `task1_runner.py`/`task2_runner.py`
  (see [Algorithm](../algorithm/index.md)) to drive the Task 1/2 state machines.

## References

- [Ultralytics ROS Quickstart](../references.md) and [Ultralytics Raspberry Pi Guide](../references.md) — external docs linked from the main References page.
