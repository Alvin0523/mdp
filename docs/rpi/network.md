---
icon: lucide/wifi
---

# Network (Pi ↔ laptop)

For the [Pi + laptop split](../quickstart.md): the
Pi drives, the laptop runs YOLO and plans. **They talk over [Tailscale](https://tailscale.com/)**:
both join a WiFi with internet (NTUSECURE, or any router), and Tailscale gives each a fixed
address that works on any network.

| | Pi (`mdp-g14`) | Laptop |
| --- | --- | --- |
| Tailscale address | `100.102.151.31` | `100.87.142.86` |
| SSH | `ssh grp14@100.102.151.31` | - |
| Clock | from the internet (NTP) | from the internet |

## 1. Tailscale

Both machines log in to the same Tailscale account once; after that the addresses above stay
the same. On the Pi:

```bash
sudo systemctl enable --now tailscaled     # on, and at every boot
sudo systemctl disable --now tailscaled    # off again
```

Check from the laptop: `tailscale status` lists `mdp-g14` as `active`, and
`ssh grp14@100.102.151.31` logs in.

!!! warning "On NTUSECURE the link is often relayed"
    `tailscale status` showing `relay "sin"` means no direct path: every packet goes through
    Tailscale's Singapore server. It works, but with the camera stream on it topics arrived in
    bursts 0.1-0.7 s late (bags, 2026-10-15). A router both machines join (e.g. "scott1") gives a
    direct path - Tailscale finds it by itself, `tailscale status` then shows `direct`.

## 2. ROS discovery

**Middleware: Cyclone DDS**, set by `mdp_ros/scripts/activate_rmw.sh` on every `pixi run`.
Fast DDS and the Pi's own hotspot were tried and are slower / unreliable:
[Troubleshooting → Network](../troubleshooting.md#network-pi-laptop).

No multicast discovery (unreliable over WiFi, and it would find other groups' robots on campus
WiFi): each machine reaches the other directly, at its Tailscale address. In `mdp_ros/pixi.toml`,
the same on both:

```toml
[activation.env]
ROS_DOMAIN_ID = "14"
ROS_AUTOMATIC_DISCOVERY_RANGE = "LOCALHOST"
ROS_STATIC_PEERS = "100.102.151.31;100.87.142.86"   # Pi; laptop (listing yourself is harmless)
```

Cyclone sends on one network interface, picked "arbitrarily" when left alone - on the Pi that was
`wlan0`, which the laptop cannot reach over NTUSECURE. So `activate_rmw.sh` binds it to this
machine's Tailscale address (`100.64.0.0/10`), and only that (with loopback as well it flooded
`ddsi_udp_conn_write … failed`, 2026-10-15). With Tailscale off, Cyclone picks an interface itself:
the robot still runs on its own, without the laptop.

After any change: `pixi run ros2 daemon stop`. Check on the laptop: `pixi run ros2 topic list`
shows the Pi's topics (with `pixi run pi` on the Pi).
