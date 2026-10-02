---
icon: lucide/camera
---

# Vision (`mdp_ros/src/mdp_vision`)

Reads the symbols on the blocks: image IDs in task 1, left/right arrows in task 2.

| Node | What |
| --- | --- |
| `rpi_cam_publisher` | Pi Camera V2 (IMX219) via `rpicam-vid`, 640 × 480 @ 10 fps on `/image_raw` (plus a JPEG copy on `/image_raw/compressed` while something watches it, e.g. Foxglove). Real car only; in sim Gazebo's camera publishes `/camera/image_raw`. |
| `yolo_detector` | YOLO (NCNN export) on the camera image → the symbol ID on `/yolo_result` (e.g. `20`; arrows `38` right / `39` left), and boxes drawn on `/yolo_result/image_annotated` |

**Models** (`mdp_vision/models/`): `mdp_v2_ncnn_model` (default) and `mdp_v1_ncnn_model` (older).
Pick one with `model:=mdp_v1_ncnn_model`. The `_ncnn_model` folder suffix is required by Ultralytics.

**Settings:** `mdp_bringup/config/vision.yaml` (image size, frame rate, JPEG quality, topics).

## Running

| Command | What runs |
| --- | --- |
| `pixi run sim …` / `pixi run real …` | Camera + YOLO are on by default (`vision:=false` for off). In sim only YOLO runs, on Gazebo's camera. |
| `pixi run vision` | Pi camera + YOLO alone, no car (`mdp_bringup/launch/vision.launch.py`) |

## How the runners use it

- **Task 1:** at each stop the car waits 3 s. Every ID YOLO reports is counted, and the most frequent
  one is sent to the tablet as `TARGET,<obstacle>,<id>` (`UNKNOWN` if none).
- **Task 2:** arrow 1 is read on the way to obstacle 1, arrow 2 on the way to obstacle 2 (the car
  keeps closing in until it's read). **Known problem:** both models often read a LEFT arrow as
  RIGHT (tested on a sim camera frame). The likely cause is training with left-right flip
  augmentation (Ultralytics' default `fliplr=0.5`), so retrain with `fliplr=0.0`.

## Where the camera sits

In the URDF, at the middle of the chassis: 84 mm ahead of the rear axle, on the centre line,
looking **left** in task 1 (so the car parks alongside each image) and forward in task 2. The
runner reads that direction from the URDF, so moving the camera needs no code change.

At a task 1 stop the rear axle is 20 cm from the block centre, so the image is about 29° to the side
of the camera's view centre. The camera sees ±31°, so the image is in view but near the edge. In
sim it reads 4 of 4. **TODO:** mount the real camera there and measure it
([Quickstart → Measured car numbers](../quickstart.md#7-measured-car-numbers)).

Still to write up: training data and model accuracy.
