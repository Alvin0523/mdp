---
icon: lucide/wifi
---

# Network (Pi ↔ laptop)

For the [Pi + laptop split](../quickstart.md#pi-laptop-yolo-and-planning-on-the-laptop): the
Pi drives, the laptop runs YOLO and plans. They talk over a direct link: the Ethernet cable
on the bench, the Pi's own 5 GHz hotspot on the arena. The laptop is the clock both go by.

| | Pi | Laptop |
| --- | --- | --- |
| Ethernet cable (bench, debugging) | `10.42.0.1` | `10.42.0.10` |
| Hotspot `MDP-Grp14` (5 GHz, channel 36) | `10.43.0.1` | `10.43.0.10` |
| Internet | none while the hotspot is on | its own WiFi |
| Clock | follows the laptop (chrony) | chrony server |

!!! warning "The Pi has no internet once the hotspot is on"
    Its one WiFi chip can't be a hotspot and join NTUSECURE at the same time. Do everything
    that needs internet first (`git pull`, `pixi install`, `apt install`). To get it back for
    a while: `sudo nmcli con up NTUSECURE` (the hotspot goes off until the next reboot or
    `sudo nmcli con up MDP-Hotspot`).

## 1. Ethernet cable

Both sides have a fixed address and no gateway, so the laptop keeps its internet on its WiFi.

=== "Pi"

    ```bash
    sudo nmcli con mod "Wired connection 1" ipv4.method manual ipv4.addresses 10.42.0.1/24
    sudo nmcli con up "Wired connection 1"
    ```

=== "Laptop"

    ```bash
    nmcli con show                    # find the wired connection's name
    sudo nmcli con mod "<wired>" ipv4.method manual ipv4.addresses 10.42.0.10/24 ipv4.never-default yes
    sudo nmcli con up "<wired>"
    ```

Check: `ssh grp14@10.42.0.1` from the laptop.

## 2. Clock sync (chrony)

The Pi 4 has no real-time clock: without internet it boots with the wrong time. Camera frames,
YOLO results and sensor data carry timestamps, so the Pi follows the laptop's clock.

=== "Laptop (server)"

    ```bash
    sudo apt install -y chrony
    sudo nano /etc/chrony/conf.d/mdp.conf
    ```
    ```
    allow 10.42.0.0/24
    allow 10.43.0.0/24
    local stratum 10
    ```
    ```bash
    sudo systemctl restart chrony
    sudo ufw allow from 10.42.0.0/24    # only if `sudo ufw status` is active
    sudo ufw allow from 10.43.0.0/24
    ```
    `allow`: serve time to the Pi on the cable and the hotspot. `local stratum 10`: keep
    serving when the laptop itself is offline.

=== "Pi (client)"

    ```bash
    sudo apt install -y chrony          # replaces systemd-timesyncd
    sudo nano /etc/chrony/sources.d/laptop.sources
    ```
    ```
    server 10.42.0.10 iburst prefer
    server 10.43.0.10 iburst prefer
    ```
    ```bash
    sudo nano /etc/chrony/conf.d/mdp.conf
    ```
    ```
    makestep 1 -1
    ```
    ```bash
    sudo systemctl restart chrony
    ```
    Both laptop addresses (cable, hotspot): chrony uses whichever answers. `makestep 1 -1`:
    jump the clock whenever it is more than 1 s off, not only at boot.

Check on the Pi:

| Command | Good |
| --- | --- |
| `chronyc sources` | `^*` in front of `10.42.0.10` (or `10.43.0.10` on the hotspot) |
| `chronyc tracking` | `Reference ID` = the laptop, `System time` < 0.005 s |

`^?` with Reach `0` = no answer yet. chrony then waits up to minutes between tries; after
fixing the laptop, `sudo chronyc burst 4/4` asks again straight away.

## 3. ROS discovery

No multicast discovery (unreliable over WiFi, and it would find other groups' robots on campus
WiFi): each machine reaches the other directly. In `mdp_ros/pixi.toml`, the same on both:

```toml
[activation.env]
ROS_DOMAIN_ID = "14"
ROS_AUTOMATIC_DISCOVERY_RANGE = "LOCALHOST"
ROS_STATIC_PEERS = "10.42.0.1;10.42.0.10"     # Ethernet cable
# ROS_STATIC_PEERS = "10.43.0.1;10.43.0.10"   # Pi's WiFi hotspot MDP-Grp14
```

Moving to the hotspot: swap which `ROS_STATIC_PEERS` line is commented, on both machines. After
any change: `pixi run ros2 daemon stop`.

Check on the laptop: `pixi run ros2 topic list` shows the Pi's topics (with `pixi run car` on the Pi).

## 4. Pi hotspot

Do this last: the Pi loses its internet here.

```bash
sudo raspi-config nonint do_wifi_country SG     # 5 GHz needs the country set
sudo nmcli con add type wifi ifname wlan0 con-name MDP-Hotspot ssid MDP-Grp14 \
  802-11-wireless.mode ap 802-11-wireless.band a 802-11-wireless.channel 36 \
  wifi-sec.key-mgmt wpa-psk wifi-sec.psk "<password>" \
  ipv4.method shared ipv4.addresses 10.43.0.1/24 \
  connection.autoconnect yes connection.autoconnect-priority 100
sudo nmcli con mod NTUSECURE connection.autoconnect no   # don't take wlan0 back at boot
sudo nmcli con up MDP-Hotspot
```

| Setting | Why |
| --- | --- |
| `band a`, channel 36 | 5 GHz: faster, and away from the tablet's Bluetooth (2.4 GHz). 36 is not a radar (DFS) channel, so a hotspot may use it |
| `ipv4.method shared` | The Pi hands out addresses (DHCP) on `10.43.0.x` |
| `autoconnect-priority 100` | The hotspot comes up on every boot |

## 5. Moving the laptop to the hotspot

1. Laptop: join `MDP-Grp14`, then give it a fixed address on it:
   ```bash
   sudo nmcli con mod MDP-Grp14 ipv4.method manual ipv4.addresses 10.43.0.10/24 ipv4.never-default yes
   sudo nmcli con up MDP-Grp14
   ```
2. Both machines: switch `ROS_STATIC_PEERS` to the hotspot line ([3](#3-ros-discovery)).
3. Unplug the cable. Check: `ping 10.43.0.1` from the laptop < 20 ms, also with the car at the
   far side of the arena; `chronyc sources` on the Pi shows `^*` on `10.43.0.10`.
