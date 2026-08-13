---
icon: lucide/terminal
---

# Launch & Node Interfaces

Launch entry points and node topic interfaces for the `mdp_ros` workspace.

## Packages

| Package | Purpose |
| --- | --- |
| `mdp_description` | Robot URDF model (`mini_akm_robot.urdf`), meshes, and RViz display launches |
| `mdp_bringup` | System launch scripts for real robot and Gazebo simulation |

## Core Topics

| Topic Name | Type | Direction | Description |
| --- | --- | --- | --- |
| `/cmd_vel` | `geometry_msgs/Twist` | Subscriber | Velocity commands from teleop or navigation stack |
| `/joint_commands` | `sensor_msgs/JointState` | Publisher | Commands published by `topic_based_ros2_control` to micro-ROS |
| `/joint_states` | `sensor_msgs/JointState` | Subscriber | Hardware encoder feedback from MCU |
| `/imu` | `sensor_msgs/Imu` | Subscriber | IMU sensor telemetry |
