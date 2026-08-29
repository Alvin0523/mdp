---
icon: lucide/clipboard-check
---

# MDP Assessment & System Checklist

> **Course:** CCDS MDP AY2026/2027 Semester 1  
> **Source:** Transcribed from official course document [`MDP assessment and system checklist.pdf`](attachments/MDP%20assessment%20and%20system%20checklist.pdf).

---

## 1. Assessment Overview & Grade Weightage

| Component | Type | Deadline | Weightage | Status |
| --- | --- | --- | --- | --- |
| **Early-stage Peer Review** | Individual | Week #5 | **5%** | Completed |
| **Project Deliverables Checklist** | Group | Week #7 (Friday) | **20%** | In Progress |
| **Individual Quiz** | Individual | Week #7 (Friday) | **20%** | Scheduled |
| **Image Recognition Evaluation (Task 1)** | Group | Week #8 (Friday) | **12.5%** | In Progress |
| **Fastest Car Evaluation (Task 2)** | Group | Week #9 (Friday) | **12.5%** | In Progress |
| **Video Report Submission** | Group | Week #10 | **15%** | Scheduled |
| **Final-stage Peer Review** | Individual | Week #10 | **15%** | Scheduled |
| **Total Marks** | | | **100%** | |

---

## 2. Functional Requirements Checklist

### Module A: Mobile Robot Module (Hardware, Communication & Vision)

| Item | Requirement | Specifications & Details |
| --- | --- | --- |
| **A.1** | Multi-channel Communication | RPi host executes concurrently: (1) Wi-Fi hotspot access for PC/notebook, (2) Bluetooth serial link for Android tablet, and (3) USB-Serial transport to STM32. Must demonstrate end-to-end message relay. |
| **A.2** | Image Detection & Recognition | Detect target image symbols 20–50 cm away from robot midpoint. Draw bounding boxes around detected symbols and display target IDs on host screen. |
| **A.3** | Accurate Straight-Line Motion | Traverse straight line between 80 cm and 120 cm (specified by supervisor) and stop accurate to within **±6%** without lateral drift. |
| **A.4** | Accurate Rotation | Complete rotation turning through 90°–360° taking Ackermann turning radius into account. |
| **A.5** | Obstacle Standoff Navigation | Navigate towards target obstacle and position camera within valid standoff distance. |

---

### Module B: Robot Path Planning Module (Algorithms & Simulation)

| Item | Requirement | Specifications & Details |
| --- | --- | --- |
| **B.1** | Arena Movement Simulator | Display 2.0m × 2.0m grid map, start carpark zone, obstacle positions, target face orientations (`N`, `S`, `E`, `W`), and live robot `(x, y)` heading. |
| **B.2** | Hamiltonian Path Calculation | Compute valid path guiding robot from carpark to visit all 5 target positions once within 6 minutes. |
| **B.3** | Shortest-Time Trajectory | Solve Travelling Salesperson Problem (TSP) using Reeds-Shepp Ackermann curves to minimize overall run time. |

---

### Module C: Android Remote Controller Module (User Interface)

| Item | Requirement | Specifications & Exact Rubric Details |
| --- | --- | --- |
| **C.1** | Bluetooth Serial Communication | Transmit and receive text strings over Bluetooth serial link (verifiable via AMD tool). |
| **C.2** | Bluetooth Connection & Scanner GUI | GUI with Connect/Disconnect button to scan, select, and connect with Bluetooth devices from a list. |
| **C.3** | Interactive Robot Movement Control | Interactive controls (labeled buttons, touch gestures, or device tilt). **Caution:** Manually typing text commands in a text box is strictly invalid. |
| **C.4** | Remote Update & Status Display | `TextView` box displaying selective status updates (e.g., `"ready to start"`, `"looking for target 2"`). Must not stream raw unformatted log text. |
| **C.5** | 2D Exploration Arena Display | 2.0m × 2.0m 2D canvas displaying numbered obstacles (white font `1, 2, 3..n`) and robot icon at `(x, y)` with clear facing direction (`N`, `S`, `E`, `W`). |
| **C.6** | Touch-and-Drag Obstacle Control | Touch-and-drag obstacle placement, movement, and deletion (drag outside arena to delete). Transmits `(x, y)` and assigned obstacle number over Bluetooth on finger lift. |
| **C.7** | Obstacle Target Face Annotation | Touch interaction to set target face orientation (`N`, `S`, `E`, `W`) on obstacle blocks. Transmits target face data over Bluetooth and updates obstacle visual indicator. |
| **C.8** | Robust Bluetooth Connection Recovery | Application must not freeze or crash upon temporary Bluetooth loss (e.g. Disconnect at AMD tool). Auto-reestablishes connection when device reconnects. |
| **C.9** | Display Target ID on Obstacle Blocks | Updates obstacle appearance to display large white Target ID and thick colored target face line upon receiving `TARGET, <Obstacle Number>, <Target ID>`. |
| **C.10** | Update Robot Location & Facing | Updates robot icon position and facing direction (`N`, `S`, `E`, `W`) on 2D map upon receiving `ROBOT, <x>, <y>, <direction>`. |

---

## 3. Task 1 & Task 2 Specifications

Moved here from the architecture guide — this is what the course actually requires the robot to do,
not architectural detail.

### 🎯 Task 1: Automatic Exploration & Image Recognition (6-Min Limit)
- **Arena:** 2.0m × 2.0m grid arena with 5 goal obstacles placed at supervisor-specified (x, y) coordinates.
- **Preparation (2 Mins):** Android Tablet receives obstacle coordinates and target faces via Bluetooth and transmits setup to RPi4B.
- **Autonomous Run:**
  - Starts in Carpark Zone.
  - Computes Hamiltonian Path / TSP Reeds-Shepp trajectory visiting all 5 targets (standoff distance: 20–50 cm).
  - Captures images via **RPi Camera Module V2**.
  - Runs YOLO26 image recognition to identify target symbol IDs.
  - Streams updates to Android Tablet (`TARGET, <Obstacle_ID>, <Target_ID>`).
  - Auto-stops within 6 minutes.

### ⚡ Task 2: Fastest Path Challenge (3-Min Limit)
- **Goal:** Robot navigates automatically from Carpark Zone to Goal Obstacle.
- **Symbol Recognition:** Identifies Left Arrow (←) or Right Arrow (→).
- **Navigation:**
  - **Right Arrow:** Loops around the right side of the obstacle.
  - **Left Arrow:** Loops around the left side of the obstacle.
- **Return & Stop:** Returns to Carpark Zone and auto-stops within 3 minutes.

---

## 4. Video Report Guidelines (Week #10 Submission — 15%)

!!! tip "Video Report Requirements (Max 5 Minutes)"
    - **Teamwork & Roles (20%):** Introduction of team members and individual responsibilities.
    - **Android UI Design (20%):** Showcase UI layout, ease of use, visual aesthetics, and responsiveness.
    - **System Content & Achievements (20% Content + 20% Presentation):** Highlight path planning performance, YOLO recognition, and hardware control stability.
    - **Creativity (20%):** Engaging storytelling, editing quality, and video flow.
