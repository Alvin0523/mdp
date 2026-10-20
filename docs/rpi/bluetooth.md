---
icon: lucide/bluetooth
---

# Tablet link (Bluetooth)

The Android tablet talks to the Pi over Bluetooth Classic, as a serial port (RFCOMM). On the Pi,
`bluetooth_bridge_node` (`mdp_ros/src/mdp_bridge`) reads and writes `/dev/rfcomm0`; it only
translates, the runners decide. Source of truth for the protocol:
[`bluetooth_bridge_node.cpp`](https://github.com/Alvin0523/mdp_ros/blob/main/src/mdp_bridge/src/bluetooth_bridge_node.cpp).
The app itself: [Android](../android/index.md).

## Run flow

1. Pi: `pixi run pi task:=1` (or `task:=2`). Tablet: **Connect** - `LINK UP` in `pixi run btlog`.
2. Car in the start box, then **RESET** (or `pixi run reset`) - the tablet's RESET indicator goes `DONE`.
3. Task 1: place the obstacles and press send (the `OBSTACLE` lines, then `DONE`) - PLAN goes
   `PLANNING`, then `DONE`. Steps 2 and 3 work in either order.
4. STATUS shows `Ready` (= PLAN `DONE` and RESET `DONE`). Press **START**.
5. The car runs; STATUS follows it, then `Finished` and `FINISHED`.
6. **STOP** = emergency stop. To run again: car back in the box, RESET, START. New obstacles only
   if the layout changed.

## Conventions

- Every message is one text line ending in `\n`. A line without `\n` is never processed (the
  bridge logs `discarding N bytes with no newline`).
- The arena is 20 x 20 cells of 10 cm, `(0,0)` bottom-left, columns then rows, `0-19`.
- Directions are `N` / `E` / `S` / `W` everywhere.
- `OBSTACLE` sends **cell numbers** (since 2026-10-08; before that the corner in cm). The Pi
  places the block at the cell's centre.
- `ROBOT` is the cell under the middle of the rear axle (`base_link`) and the way the car faces.
- The start pose is a launch argument: `start_cell:=COL,ROW start_dir:=N|E|S|W`.

## Tablet → Pi

| Line | What the Pi does |
| --- | --- |
| `OBSTACLE,<n>,<col>,<row>,<N\|E\|S\|W>` | Collects obstacle `n` (image side `N/E/S/W`). An `OBSTACLE` after a `DONE` starts a new set |
| `DONE` | Publishes the set on `/obstacle_setup` - task 1 plans it |
| `START` | Calls `/start_run` (= `pixi run start`, Foxglove START). Rejected unless Ready. `START` only - `BEGIN` / `go` are no longer accepted |
| `STOP` | Calls `/stop_run`: emergency stop, zero velocity, reset before the next run |
| `RESET` | Calls `/reset_pose`: the car is at the start pose |
| `CLEAR` | Resends the current `ROBOT` line (the tablet cleared its map; the Pi keeps obstacles and plan) |
| `f` `b` `fl` `fr` `bl` `br` | Manual drive on `/manual_drive`: one short burst per tap (forward, back, forward/back left/right) |

Anything else is logged as `unrecognised line from the tablet` and ignored.

## Pi → tablet

| Line | When |
| --- | --- |
| `ROBOT,<col>,<row>,<N\|E\|S\|W>` | On change and every 2 s (`robot_pose_feedback`) |
| `TARGET,<n>,<id>` | Task 1: the image read on obstacle `n` (`UNKNOWN` if none) |
| `PLAN:<WAITING\|PLANNING\|DONE>` | When the plan state changes |
| `RESET:<WAITING\|DONE>` | `DONE` once the car is confirmed at the start pose |
| `STATUS:<text>` | `Waiting`, `Ready`, `Going to obstacle n`, `Scanning obstacle n` (task 1) / `Going to checkpoint n` (task 2), `Finished`, `Stopped`. Each replaces the last |
| `FINISHED` | The run is complete and its timer stopped (both tasks; not after `STOP`) |

The latest `ROBOT` / `PLAN` / `RESET` / `STATUS` line is resent whenever the link comes up.

## Known app mismatches (2026-10-20, MDP-APP `main`)

- **The app sends `BEGIN` to start a run; the Pi only accepts `START`.** `ControlFragment.java`
  sends `BEGIN` in five places - change them to `START`, or START from the tablet does nothing
  (the Pi logs `unrecognised line`).
- Fixed: `Home.printMessage` now ends every line with `\n` (the drive buttons used to send `f`
  with no newline and never arrived).

## Pi setup (once per Pi)

1. **Pair** from the Pi, with the tablet's Bluetooth settings screen open so it is visible:

    ```bash
    bluetoothctl
    ```

    then, inside it: `agent off`, `agent NoInputNoOutput`, `default-agent`, `scan on`,
    `pair <TABLET_MAC>`, `trust <TABLET_MAC>`, `scan off`. With `NoInputNoOutput` the tablet only
    asks to tap **Pair**.

2. **Offer a Serial Port service.** Out of the box the Pi offers only audio profiles and the app
   finds nothing to connect to. Run `bluetoothd` in compatibility mode (`-C`, a systemd drop-in for
   `bluetooth.service`) and register the service after every start:

    ```bash
    sudo sdptool add SP
    ```

3. **Create `/dev/rfcomm0`** when the tablet connects - before each session, in a terminal left
   open:

    ```bash
    sudo rfcomm listen /dev/rfcomm0 1
    ```

    or, always on, `sudo rfcomm watch hci0 1` as a systemd service (it recreates the device every
    time the tablet connects). The bridge keeps retrying to open the device, so start order does
    not matter.

Another device: `pixi run pi bluetooth_device:=/dev/rfcomm1`.

## Testing

- **Tablet → Pi:** `pixi run btlog` shows `LINK UP/DOWN` and every line both ways
  (`TABLET -> RPI`, `RPI -> TABLET`; `--all` adds the 2 s repeats). Raw lines are also on
  `/bluetooth_rx`.
- **Pi → tablet:** send any line by hand:

    ```bash
    pixi run -- ros2 topic pub --once /bluetooth_tx std_msgs/msg/String "{data: 'STATUS:Ready'}"
    ```

- **App development without the Pi** (a Linux laptop with Bluetooth): pair and register the
  service as above, keep `sudo rfcomm watch hci0 1` running, then run the bridge alone and watch
  what arrives:

    ```bash
    pixi run -- ros2 run mdp_bridge bluetooth_bridge_node
    ```

    ```bash
    pixi run -- ros2 topic echo /bluetooth_rx
    ```

    ```bash
    pixi run -- ros2 topic echo /manual_drive
    ```

    A drive button shows on both. Nothing on `/bluetooth_rx` while the bridge logs
    `discarding ... no newline` = the app is not ending its lines with `\n`.

## Troubleshooting

| Symptom | Cause |
| --- | --- |
| The app's device list has the Pi but **Connect** fails | No Serial Port service: `sudo sdptool add SP` (needs `bluetoothd -C`) |
| Connects, but `btlog` stays `LINK DOWN` | No `/dev/rfcomm0`: start `rfcomm listen` / `rfcomm watch` |
| Buttons pressed, nothing in `btlog` | Lines without `\n` (bridge: `discarding ... no newline`) |
| START does nothing | The app sent `BEGIN` (see above), or the car is not Ready (PLAN and RESET both `DONE`) |
| `OBSTACLE ... outside the grid` | The app sent cm, not cells (`0-19`) |
