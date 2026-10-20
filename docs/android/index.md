# Android (Remote Controller App)

Source: [RashI0192/MDP-APP](https://github.com/RashI0192/MDP-APP), the `mdp_android/` submodule
of this repo.

## Protocol with the RPi

The lines both ways, setup and testing: [Tablet link (Bluetooth)](../rpi/bluetooth.md). Rubric
requirements: [Assessment & Checklist](../assessment_checklist.md), Module C.

## How it fits into the system

See [Subsystems](../index.md#subsystems) for where this sits relative to the RPi. The RPi is the only subsystem that talks to the Android app directly — STM32, Vision, and Algorithm never see Bluetooth traffic, they only see the RPi's own ROS topics.

## Features implemented in the app

- Android remote-controller app with Home, Map Configuration, Bluetooth, and Chat/Communications tabs.
- Bluetooth enable/disable control.
- Bluetooth device discovery and scanning.
- Paired-device and discovered-device selection.
- Bluetooth RFCOMM connection to the RPi.
- Bluetooth disconnect handling.
- Centralized Bluetooth message transmission through the existing connection.
- Bluetooth connection and link-health status display.
- Incoming Bluetooth message reception and display.
- Persistent scrolling communication/message log.
- Manual text-message entry and sending through Bluetooth.
- Chat view and detected-images view switching.
- Detected-image message parsing for `TARGET`, `IMAGE`, `IMAGE_DETECTED`, and `DETECTED_IMAGE` formats.
- Detected-image message formatting with obstacle number and target ID.
- Persistent detected-image history.
- Duplicate detected-image prevention.
- Clear detected-images action.
- Six-direction robot movement pad: forward, backward, forward-left, forward-right, backward-left, and backward-right.
- Local robot-position updates after movement commands.
- Robot movement blocked until a valid start point is configured.
- Local movement validity and obstacle-collision checking.
- Robot direction and coordinate display.
- Live robot status text display.
- 20-by-20 user-facing map coordinate system with X and Y values from 0 to 19.
- Grid rendering with coordinate labels and an internal border.
- Robot rendering on the grid.
- Robot start-position selection.
- Robot direction selection.
- Robot footprint handling over a 2-by-2 grid area.
- Obstacle placement mode.
- Obstacle occupancy checking before placement.
- Obstacle coordinate storage.
- Obstacle direction/target-face metadata storage.
- Obstacle rendering on the grid.
- Obstacle numbering.
- Live current-obstacle list.
- Manual obstacle input screen.
- Manual X-coordinate selection from 0 to 19.
- Manual Y-coordinate selection from 0 to 19.
- Manual North, South, East, and West direction selection.
- Manual obstacle selection from the current obstacle list.
- Manual obstacle coordinate editing.
- Manual obstacle direction editing.
- Manual obstacle deletion.
- Obstacle-number renumbering after deletions and additions.
- Drag-and-drop obstacle movement.
- Obstacle numbering displayed on dragged obstacles.
- Obstacle direction-marker alignment inside the correct grid cell.
- Change-obstacle mode for changing obstacle image/direction metadata.
- Delete-obstacle mode for quick tap-to-delete editing.
- Mutually exclusive obstacle add, drag, and delete modes.
- Saved obstacle-map persistence using shared preferences.
- Loading saved obstacle maps.
- Redrawing loaded obstacles on the grid.
- Reset-map confirmation dialog.
- Full local map reset.
- `CLEAR` command transmission when the map is reset.
- Obstacle-list serialization for transmission to the RPi.
- Obstacle protocol formatting with obstacle number, grid coordinates, and direction.
- `DONE` transmission after obstacle data is sent.
- Task 1 timer.
- Task 1 start, pause/resume, stop, and reset controls.
- Task 2 timer.
- Task 2 start, pause/resume, stop, and reset controls.
- Start RPi run control.
- Practice Tasks mode for Task 1.
- Practice Tasks mode for Task 2.
- Editable practice-lapse descriptions.
- Practice-run history persistence.
- Results-analyser dialog for stored practice runs.
- Start, pause/resume, stop, and lapse actions for practice runs.
- Robot status updates for movement, task, start, stop, and completion states.
- Formatting and handling of robot status, position, target, arrow, movement, acknowledgement, and finish messages.
- Live map updates from robot position messages.
- Target/image association with obstacles.
- Arrow-direction updates for Task 2.
- Path translation for robot movement and route commands.
- White grid background.
- Soft pale-gold explored-cell rendering.
- Gold/yellow button theme.
- Smoother Bluetooth, cache, sleep-timer, and grid-loading behavior.
- Cancel-button handling.
- Removed crashing compass icon and click handler.
- Removed manual obstacle switch/slider.
- Removed obsolete Load icon/action from the simplified map controls.
- Removed hidden emergency control from the final visible app.
- Removed Toast notifications from repeated add, drag, and delete map-editing actions.
