---
icon: lucide/camera
---

# Vision (`mdp_ros/src/mdp_vision`)

Reads the symbols on the blocks: image IDs in task 1, left/right arrows in task 2.

| Node | What |
| --- | --- |
| `rpi_cam_publisher` | Pi Camera V2 (IMX219) via `rpicam-vid`, 640 × 480 @ 10 fps on `/image_raw` (plus a JPEG copy on `/image_raw/compressed` while something watches it, e.g. Foxglove). Real car only; in sim Gazebo's camera publishes `/camera/image_raw`. |
| `yolo_detector` | YOLO (PyTorch, on the laptop's GPU: `pixi run laptop`) on the camera's JPEG stream → the symbol ID on `/yolo_result` (e.g. `20`; arrows `38` right / `39` left), and boxes drawn on `/yolo_result/image_annotated` |

**Models** (`mdp_vision/models/`): `best_v5.pt` (YOLO26m trained at 640, task 1 default), `task2_yolo26s.pt` (Arrow Left / Right + Bullseye, task 2 default). The older `best_v4.pt` / `best.pt` were removed on 2026-10-17 (git history has them). On a Mac the detector uses `<name>.mlpackage` (CoreML) next to the `.pt` when it is there (`pixi run export-coreml`).
Pick one with `model:=task2_yolo26s.pt`, or a full path to any `.pt`. YOLO runs on the laptop only: PyTorch
crashed on the Pi, and the Pi's NCNN exports were removed (2026-10-08).

**Settings:** `mdp_bringup/config/vision.yaml` (image size, frame rate, JPEG quality, topics).

## Running

| Command | What runs |
| --- | --- |
| `pixi run pi …` + `pixi run laptop task:=N` | Camera on the Pi (JPEG on `/image_raw/compressed`), YOLO on the laptop. On by default (`vision:=false` for off) |
| `pixi run sim …` + `pixi run laptop sim:=true task:=N` | YOLO on the laptop, on Gazebo's camera |
| `pixi run vision` | Pi camera + YOLO alone, no car (`mdp_bringup/launch/vision.launch.py`) |

## How the runners use it

- **Task 1:** at each stop the car waits 3 s. Every ID YOLO reports is counted, and the most frequent
  one is sent to the tablet as `TARGET,<obstacle>,<id>` (`UNKNOWN` if none).
- **Task 2** (`task2_yolo26s`): arrow 1 is read in the carpark before START, arrow 2 on the way to
  checkpoint 1. Near checkpoint 1 the gap between arrow 2 and the bar's near bullseye in one frame
  × the ultrasonic's distance gives the bar's width (`bar_gap_scale` 1.065 on the real car; no such
  frame: the usual 60 cm) - [Quickstart → Bar width](../quickstart.md#bar-width).
- **Known problem (task 1 models):** `best_v5` (and the older `best_v4`) often read a LEFT arrow as RIGHT (sim
  camera frame). Likely cause: left-right flip augmentation (Ultralytics' default `fliplr=0.5`);
  retrain with `fliplr=0.0`.

## Where the camera sits

In the URDF (`camera_joint`, measured on the car):

| Task | Position | Looks |
| --- | --- | --- |
| 1 | In line with the rear axle, 55 mm right of the centre line, lens 175 mm above the table (2026-10-08) | **Left**, so the car parks alongside each image |
| 2 | 171 mm ahead of the rear axle (28 mm behind the chassis front), on the centre line, 150 mm high (2026-10-14) | Forward, at the arrows |

The runner reads the position from the URDF, so moving the camera needs no code change.

Still to write up: training data and model accuracy.
