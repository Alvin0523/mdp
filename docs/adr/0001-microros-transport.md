# 0001. Use micro-ROS for the host↔MCU transport

**Status:** :material-check-circle: Accepted
**Date:** 2026-08-07

## Context

The MCU (STM32F407VET6, Zephyr) needs to exchange ROS2 topics with the host over a serial/UART link. The earlier `mdp_mcu` attempt used zenoh-pico + Pico-ROS. Its own debugging notes document a lot of bespoke pain — UART overrun, lease timeouts, reconnect bugs — for a much smaller, newer ecosystem than the alternative.

## Decision

Use micro-ROS (rclc) as the transport connecting a micro-ROS Agent on the host to the MCU, instead of zenoh-pico/Pico-ROS.

## Consequences

micro-ROS is the official ROS2-on-MCU stack with a maintained Zephyr module and a much larger community, so less time goes into fighting the transport itself. The tradeoff: the existing zenoh-based `mdp_mcu` firmware's transport layer is discarded and has to be rebuilt on micro-ROS from scratch (the board bring-up and motor driver underneath it are unaffected, see [0004](0004-zephyr-firmware-base.md)).
