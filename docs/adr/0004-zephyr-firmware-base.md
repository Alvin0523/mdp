# 0004. Keep Zephyr as the firmware base

**Status:** :material-check-circle: Accepted
**Date:** 2026-08-07

## Context

The earlier `mdp_mcu` firmware already has working STM32F407VET6 board bring-up and a working AT8236 motor PWM driver (`motor.c`), built on Zephyr. Replacing the transport (zenoh → micro-ROS, [0001](0001-microros-transport.md)) doesn't require throwing that away — the board overlay and motor driver don't depend on which ROS transport sits above them.

## Decision

Keep Zephyr as the RTOS/firmware base for `mdp_stm32`. Reuse the existing board bring-up and motor PWM driver; only replace the transport layer.

## Consequences

Saves re-doing board bring-up and a working, tested motor driver. Firmware work for `mdp_stm32` is scoped to: swap the transport, then add micro-ROS integration, steering servo, encoders, and IMU (all currently stubs per `docs/architecture.md`'s status table) — not a firmware rewrite from scratch.
