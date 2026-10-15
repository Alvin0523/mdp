# Task 2 vision: how it works, and training YOLO for it

## 1. What the car looks at

| Thing | Why | Detected by (now) | Later |
|---|---|---|---|
| Arrow on obstacle 1 | which side round obstacle 1 | YOLO `best_v5.pt` (`Arrow Left` / `Arrow Right`) | same model, more task 2 data |
| Arrow on the bar | which side round the bar | YOLO, the same | same |
| Bullseye on the bar's end | the bar's width (where its end is) | `bullseye_matcher` (OpenCV template matching, no AI) | YOLO class `Bullseye` |

Both publish every box they see (`/yolo_detections`, `/bullseye_detections`, the same format:
`[id, confidence, x1, y1, x2, y2]`). The task 2 runner uses them like this:

- **Arrow 1 or 2?** A box's height says how far away the arrow is. An arrow well beyond
  obstacle 1 is arrow 2. At home the runner measures how big YOLO boxes an arrow (it knows
  obstacle 1's distance from the ultrasonic), so this works whatever box style the model uses.
- **Bar width.** On the straight bit into checkpoint 1: the bullseye's position in the
  picture gives its direction; with the ultrasonic's distance to the bar that places the
  bar's end. Width = 2 x (end's distance from the centre line). The bullseye's *size* is only
  a check on the ultrasonic (which looks past a narrow bar), so a box of the **whole printed
  card** matters (section 4).
- No bullseye seen: the runner assumes 60 cm.

`best_v5.pt` already reads the arrows (36/36 in the sim sweep). The template matcher
works in the sim, whose texture is perfect; a real camera brings blur at 0.7 m/s, glare
and other light. **So: train a `Bullseye` class into YOLO**, and add task 2 views of the
arrows while doing it.

## 2. First: check the matcher on real footage (no labelling)

1. Pi: `pixi run pi task:=2`. Laptop: `pixi run laptop`, then `pixi run bag-all`.
2. Do 3-4 real runs (different d1 / d2 / bar widths). Stop the bag (Ctrl+C).
3. Send the bags (`bags/`). We run the matcher on them offline: if it finds the
   bullseyes reliably, training can wait.

Also **measure the printed bullseye's outer WHITE square** (edge to edge, cm). The code
assumes 4.6 cm (`BULLSEYE_RIM_M` in `mdp_ros/src/mdp_bringup/mdp_bringup/utils/obstacle_layout.py`;
the photo of the print has it at 0.75 of the 6.1 cm card).

## 3. Collecting the data

Record on the laptop while the Pi drives (or while you push the car by hand along the
path, slowly for sharp frames and at full speed for blurred ones):

```bash
pixi run capture                            # camera -> datasets/videos/<time>.mp4, until Ctrl+C
pixi run capture --from-bag bags/rosbag2_...   # a recorded bag -> MP4
```

| Vary | Values |
|---|---|
| d1, d2 | 0.6, 1.0, 1.5 m (and in between) |
| Arrows | all four LEFT / RIGHT pairs |
| Bar width | every bar you have (30-120 cm) |
| Light | room lights on / off / window light; the competition room if you can |
| Speed | slow (sharp) and full speed (motion blur) |

Make sure the footage has:

- **the turn out of the carpark** (arrow 2 shows briefly past obstacle 1);
- **the straight bit into checkpoint 1** (bullseyes 0.5-2 m away, the near end big);
- **arrows cut off at the picture's edge** (arrow 2 is there at d2 = 0.6 m);
- **obstacle 1's side bullseyes** seen at an angle;
- **nothing** (walls, floor, carpark): teaches the model what is not a symbol.

About 10-15 short videos is plenty.

## 4. Labelling on Roboflow

1. **A new, separate project for task 2** - not the task 1 one with its ~30 classes. Task 2
   only needs three: a small model learns them faster and better, and task 1's model stays
   as it is. Task 1 runs `best_v5.pt`, task 2 its own model (`model:=` at launch).
   - Bring the old arrow images along: in the task 1 project, **Generate** a version with
     **Modify Classes** (keep `Arrow Left`, `Arrow Right`, drop every other class), export
     it, and upload it into the new project. Images with only other symbols are fine as
     "nothing here" examples - keep a few hundred, not all.
2. **Upload the videos**: Roboflow asks for a frame rate - take **2-3 frames per second**
   (neighbouring frames are near copies). Aim for ~**600 new images**: ~300 with bullseyes,
   ~200 task 2 arrow views, ~100 with nothing or hard cases.
3. **Classes** - exactly these names (the code maps them):
   - `Arrow Left`, `Arrow Right` (existing)
   - `Bullseye` (new - the detector maps it to id 99)
4. **How to box**
   - **Bullseye: the outer WHITE square** (the biggest white square) - not the black card
     round it: the obstacles are black, so the card's edge can't be seen. Tight on the
     white square's outer edge, every time: the car reads distance from the box size
     (`BULLSEYE_RIM_M`). The template matcher boxes the same square.
   - **Arrows: as your existing labels** (same style as before - the car measures the size
     of your arrow boxes at home, but mixing styles confuses the model).
   - Label **every** symbol in view, the far ones and obstacle 1's side bullseyes too.
   - Cut off at the edge: label it if roughly **a third or more** is in view; box only the
     visible part.
   - Images with nothing: keep them, no boxes (they are "background").
5. **Dataset version** (Generate):
   - Split ~70 / 20 / 10 (train / valid / test). **Include the old images** too, or the
     model forgets the other symbols.
   - Preprocess: Auto-Orient, Resize to 640 (as `best_v5`).
   - Augment: blur up to ~2 px, brightness ±25 %, exposure ±15 %, noise ~2 %, rotation ±5°.
   - **No horizontal flip** - it turns `Arrow Left` into a right-pointing arrow still labelled
     LEFT. No vertical flip either.

## 5. Training

Export the version as **YOLOv8 / Ultralytics** format, then on the laptop (NVIDIA GPU):

```bash
pixi run -- yolo detect train model=src/mdp_vision/models/best_v5.pt \
    data=<export>/data.yaml imgsz=640 epochs=100 patience=20
```

Starting from `best_v5.pt` gives it a head start on arrows (its other classes are
dropped, as the new data has three). Check `runs/detect/train*/`:
`mAP50` for `Bullseye` and the arrows on the valid set should be > 0.9; look at
`val_batch*_pred.jpg` - the bullseye boxes must hug the card.

## 6. Using it

1. Copy `runs/detect/train*/weights/best.pt` to `mdp_ros/src/mdp_vision/models/task2_v1.pt`,
   `pixi run build`.
2. Task 2: `pixi run laptop model:=task2_v1.pt bullseye_matcher:=false` (task 1 keeps
   `best_v5.pt`, the default).
3. Check: `ros2 topic echo /yolo_detections` shows `"99"` boxes on the bar's ends; in a run
   the runner logs `BAR  xx.x cm wide (bullseyes: ...)` with numbers close to the real bar.
4. If the arrow reading gets worse: send the run's bag and the log.
