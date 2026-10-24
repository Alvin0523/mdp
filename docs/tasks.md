---
icon: lucide/flag
---

# How the tasks are solved

The two tasks end to end: which sensors, which algorithms, in what order. The commands to run
them: [Quickstart](quickstart.md). The algorithms in detail: [Algorithm](rpi/algorithm.md),
[Vision](rpi/vision.md).

## Sensors per task

=== "Task 1 setup"

    ![The car set up for task 1: camera on a mast looking left](images/car-task1.jpg){ width="420" }

=== "Task 2 setup"

    ![The car set up for task 2: camera forward, ultrasonic at the front](images/car-task2.jpg){ width="420" }


| Sensor | Task 1 | Task 2 |
| --- | --- | --- |
| Wheel encoders + IMU (fused by the EKF) | The pose, all the time | The pose, all the time |
| Camera + YOLO | **Looks left**: reads each image's ID at its stop | **Looks forward**: reads the two arrows and the bar's bullseyes |
| IR pair | **Left side**: the position fix at each stop, against the block | Not used |
| Ultrasonic (front) | Not used | The distances to obstacle 1 and the bar, and the finish (the carpark's back wall) |

The camera moves between the tasks (left in task 1, forward in task 2); the URDF has both and the
runners read its position from it.

## The two tasks

=== "Task 1: find and read every image"

    The tablet sends up to 8 blocks (cell, image side). Goal: read every image, as fast as possible.

    ```mermaid
    graph TD
      A["📱 Obstacles from the tablet"] --> B["Checkpoints<br/>a stop in front of each image"]
      B --> C["Order: shortest tour<br/>every order, Reeds-Shepp lengths"]
      C --> D["Paths between stops<br/>Hybrid A* on the costmap"]
      D --> E["Drive<br/>LQR steering"]
      E --> F{"At the stop"}
      F --> G["IR position fix"]
      G --> H["YOLO reads the image<br/>→ TARGET to the tablet"]
      H -->|"next stop"| E
      H -->|"all read"| I["🏁 FINISHED"]
    ```

    1. **Checkpoints.** For each image, one square-on stop: 30 cm out from the image face, turned so
       the left-looking camera faces it, snapped to a 10 cm cell. Where the car does not fit there
       (another block, the table's edge), the stop slides along the face up to 10 cm either way; still
       no room, it moves back along the car's heading up to 13 cm, as far as the image stays whole in
       the camera's view. No spot at all: that image is skipped.
    2. **In which order** (a travelling-salesman problem). Every order of the stops is scored by the
       length of its **Reeds-Shepp** paths (the shortest path for a car that can reverse, ignoring
       obstacles - cheap enough to score all 8! = 40320 orders); the shortest wins.
    3. **Paths.** Each leg between stops is planned with **Hybrid A\*** on a **costmap** (the blocks
       and the arena's edge, padded by the car's size): a search over the car's real motions (forward
       and reverse arcs at its turning limits), with Reeds-Shepp shots to finish, penalising reversing
       and gear changes. The laptop plans the legs (`leg_planner`); the Pi does it itself if the laptop
       does not answer within 2 s. Leg 2 is planned while the car drives leg 1.
    4. **Driving.** An **LQR** controller steers along the path (it keeps the sideways and heading
       errors small with the steering's lag included); the wheel speeds are held by the STM32's 100 Hz
       PI loop.
    5. **At each stop.** The car creeps until both left IRs see the block's face: their distance and
       which IR sees the face fix the car's position (encoder and IMU drift, a few cm a leg). A stop
       moved more than 3 cm off the block's centre gets no IR fix (the IRs miss the face). Then YOLO
       looks for up to 3 s; the most frequent ID goes to the tablet as `TARGET,<obstacle>,<id>`.
    6. **Finish.** Every image visited: `FINISHED` to the tablet, the timer stops.

=== "Task 2: round two obstacles, as fast as possible"

    Out of the carpark, past obstacle 1 on its arrow's side, round the bar (obstacle 2) on its arrow's
    side, back into the carpark. Nothing is known in advance: obstacle 1 is 0.60-1.50 m out, the bar
    another 0.60-1.50 m, and the bar's width varies (0.30-1.20 m).

    ```mermaid
    graph TD
      A["In the carpark<br/>ultrasonic → obstacle 1's distance<br/>YOLO → arrow 1"] --> B["Plan the path to checkpoint 1<br/>straight · S · straight"]
      B -->|"START"| C["Drive to checkpoint 1"]
      C --> D["On the way<br/>ultrasonic → the bar's distance<br/>YOLO → arrow 2<br/>box gap arrow 2 ↔ bullseye → the bar's width"]
      D --> E["At checkpoint 1<br/>checkpoints 2, 3, 4<br/>route table + clean geometry"]
      E --> F["Round the bar, back home<br/>LQR steering"]
      F --> G["🏁 Ultrasonic sees the back wall → FINISHED"]
    ```

    1. **In the carpark, before START.** The ultrasonic measures obstacle 1's distance; YOLO reads
       arrow 1. The path to **checkpoint 1** (beside obstacle 1, on arrow 1's side) is planned at once:
       a straight, a smooth S (its curvature rises and falls gradually) and a straight into checkpoint 1.
    2. **On the way to checkpoint 1.** Level with obstacle 1, the camera and the ultrasonic look past
       it:
         - the **ultrasonic** measures the distance to the bar;
         - **YOLO reads arrow 2** - told apart from arrow 1 by how far away it is: the size of its
           bounding box against arrow 1's, measured at home at a known distance;
         - the **bar's width**: arrow 2 (on the bar's middle) and the **bullseye** on the bar's end in
           one frame - the pixel gap between their boxes gives the angle between them, × the
           ultrasonic's distance = half the bar's width (× 1.065 for the real lens' distortion). Rounded
           to the nearest 5 cm. No such frame: the usual 60 cm. Bullseyes come from YOLO and from a
           template matcher, counted once when both box the same one;
         - a **bullseye's box size** also gives its distance - a check on the ultrasonic, which can
           look straight past a narrow bar.
    3. **At checkpoint 1: checkpoints 2, 3, 4.** 2 beside the bar's end on arrow 2's side, 3 beside
       its other end, 4 home. Checkpoint 1 → 2 comes from a **route table** planned offline for every
       case (bar distance and width every 5 cm × the 4 arrow pairs: 7220 routes), so it is ready the
       instant the car arrives; 2 → 3 is half a circle round the bar's back, 3 → home an S onto the
       centre line - clean geometry in milliseconds. Hybrid A\* only for a case none of that covers.
    4. **Round the bar and home**, LQR steering as in task 1.
    5. **Finish.** The ultrasonic sees the carpark's back wall at 15 cm (or, in a taped carpark, the
       whole car is inside it): `FINISHED`.

## Testing a task step by step

`step:=true` + `pixi run step`: the car stops at every checkpoint and waits for SPACE. Task 2 also
re-reads and re-plans live, at home and at checkpoint 1 - move the obstacles and watch.
[Quickstart → Run](quickstart.md#2-run).

## Doing it better

What we would do next, and what to watch out for: [Quickstart → Doing it better](quickstart.md#11-doing-it-better).
