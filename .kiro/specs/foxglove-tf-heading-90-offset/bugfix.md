# Bugfix Requirements Document

## Introduction

In Gazebo the car sits in the start zone facing the arena's +Y direction ("North"), which is the
orientation the planner assumes. Viewing the same instant in Foxglove, the car's TF frame and its
heading arrow point 90 degrees clockwise (to the right) of that: the arena outline, grid, obstacle
cubes and checkpoint arrows all appear rotated 90 degrees relative to the robot.

The two views disagree because the robot's TF/pose data and the arena visualization are expressed in
two different frames that are treated as if they were the same one. The robot's only pose source is
the dead-reckoned `odom` frame, which is created at the robot's spawn/power-on pose with identity
orientation. The arena visualization (occupancy grid, grid lines, placement-zone outline, start-box
outline, obstacle cubes and labels, checkpoint arrows, planned path) is published with
`frame_id: odom` but carries arena-origin coordinates (0..2 m measured from the arena's bottom-left
corner). Nothing publishes a transform that relates the arena frame to `odom`, so the renderer
overlays arena coordinates directly onto the dead-reckoning frame.

Because the sim spawns the robot with yaw = pi/2, the mismatch between "arena frame" and "odom frame"
is exactly a 90 degree rotation, which is what shows up as the heading offset. The same mismatch also
applies a small position offset (the 0.15, 0.15 m spawn offset), so the robot renders at the arena's
exact corner rather than inside the start box.

Impact is not limited to visualization. The task runner consumes `/odometry/filtered` (an `odom`-frame
pose) as if it were an arena-frame pose and feeds it to the path follower, whose reference path was
planned in arena coordinates. Any nonzero start yaw therefore corrupts path tracking with the same
90 degree error, so a fix must reconcile the frames rather than only relabel markers.

Reproduction: launch the Task 1 simulation (arena world + robot spawned at x=0.15, y=0.15,
yaw=pi/2), connect Foxglove, and compare the robot's TF axes / heading arrow against the arena
outline and obstacle markers. Gazebo shows the car facing along the arena's +Y axis; Foxglove shows it
facing along the rendered arena's +X axis.

## Bug Analysis

### Current Behavior (Defect)

1.1 WHEN the robot is spawned or powered on with a nonzero heading relative to the arena frame (yaw = pi/2 in the Task 1 sim) THEN the system renders the robot's TF frame and heading arrow rotated 90 degrees clockwise relative to the arena outline and obstacle markers in Foxglove, while Gazebo shows the correct orientation
1.2 WHEN arena-referenced visualization (occupancy grid, grid lines, placement-zone outline, start-box outline, obstacle cubes/labels, checkpoint arrows, planned path) is published THEN the system stamps it with `frame_id: odom` while filling in arena-origin coordinates, so the arena is drawn in a frame it does not belong to
1.3 WHEN the TF tree is inspected THEN the system provides no transform linking the arena/world frame to `odom`, so no consumer can convert between arena coordinates and the robot's dead-reckoned pose
1.4 WHEN the robot is spawned at an offset inside the start box (0.15, 0.15) THEN the system renders the robot at the arena's exact (0, 0) corner, because `odom` is anchored at the spawn pose and treated as the arena origin
1.5 WHEN the task runner reads `/odometry/filtered` and drives a path planned in arena coordinates THEN the system tracks that path with the same frame rotation applied, so commanded motion is rotated relative to the intended arena path

### Expected Behavior (Correct)

2.1 WHEN the robot is spawned or powered on with a nonzero heading relative to the arena frame THEN the system SHALL render the robot's TF frame and heading arrow with the same orientation relative to the arena that Gazebo shows, i.e. facing arena +Y for a yaw = pi/2 spawn
2.2 WHEN arena-referenced visualization is published THEN the system SHALL publish it in a frame whose origin and axes match the arena's bottom-left corner and axes, so the drawn arena coincides with the physical arena
2.3 WHEN the TF tree is inspected THEN the system SHALL contain a transform chain that relates the arena/world frame to `odom` (and therefore to `base_footprint`/`base_link`), reflecting the robot's known start pose in the arena
2.4 WHEN the robot is spawned at an offset inside the start box THEN the system SHALL render the robot at that same offset position inside the drawn start box, not at the arena corner
2.5 WHEN the task runner consumes the robot pose for path following THEN the system SHALL express that pose in the same frame the planned path is expressed in, so tracking error contains no constant rotation or translation offset

### Unchanged Behavior (Regression Prevention)

3.1 WHEN the robot is spawned with zero yaw at the arena origin THEN the system SHALL CONTINUE TO render robot and arena in agreement, exactly as it does today
3.2 WHEN Gazebo renders the robot and arena THEN the system SHALL CONTINUE TO show the current, correct relative orientation (Gazebo is the reference, not the thing being changed)
3.3 WHEN the planner computes the occupancy grid, visiting order, checkpoints and Hybrid A* legs THEN the system SHALL CONTINUE TO produce the same arena-coordinate numeric results for the same obstacle setup
3.4 WHEN `odom` -> `base_footprint`/`base_link` is broadcast by the odometry source (`ackermann_steering_controller` in sim, `ekf_node` on hardware) THEN the system SHALL CONTINUE TO have exactly one broadcaster of that transform
3.5 WHEN the robot drives and dead reckoning accumulates THEN the system SHALL CONTINUE TO produce continuous, jump-free `odom` -> base transforms and `/odometry/filtered` output
3.6 WHEN obstacle setup arrives on `/obstacle_setup` in metres THEN the system SHALL CONTINUE TO be interpreted in arena coordinates with obstacle centres at the given coordinates
3.7 WHEN the camera, YOLO detection, Bluetooth reporting and `/start_run` gating run THEN the system SHALL CONTINUE TO behave as they do today

## Bug Condition

```pascal
FUNCTION isBugCondition(X)
  INPUT: X of type VisualizationState
    // X.robot_start_pose_in_arena : (x, y, yaw) of the robot at odom-frame creation
    // X.arena_marker_frame        : frame_id used for arena-referenced markers/grid/path
    // X.tf_tree                   : set of published transforms
  OUTPUT: boolean

  // Arena-referenced data is drawn in a frame that is not the arena frame,
  // and nothing in the TF tree relates the two.
  RETURN (X.arena_marker_frame = 'odom')
         AND NOT existsTransform(X.tf_tree, arena_frame, 'odom')
         AND (X.robot_start_pose_in_arena.yaw ≠ 0
              OR X.robot_start_pose_in_arena.x ≠ 0
              OR X.robot_start_pose_in_arena.y ≠ 0)
END FUNCTION
```

For the reported case, `X.robot_start_pose_in_arena = (0.15, 0.15, pi/2)`, giving the observed
90 degree heading offset plus a 0.15 m diagonal position offset.

```pascal
// Property: Fix Checking - arena/robot orientation agreement
FOR ALL X WHERE isBugCondition(X) DO
  rendered ← renderScene'(X)
  ASSERT rendered.robot_heading_in_arena_frame = X.robot_start_pose_in_arena.yaw
     AND rendered.robot_position_in_arena_frame = (X.robot_start_pose_in_arena.x,
                                                   X.robot_start_pose_in_arena.y)
END FOR
```

```pascal
// Property: Preservation Checking
FOR ALL X WHERE NOT isBugCondition(X) DO
  ASSERT renderScene(X) = renderScene'(X)
END FOR
```

Counterexample that demonstrates the bug: robot spawned at (0.15, 0.15) with yaw = pi/2, arena
markers published with `frame_id: odom` and no arena-to-`odom` transform published. Foxglove renders
the robot heading along the arena's +X axis instead of +Y.
