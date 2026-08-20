# STM32 HAL Firmware Architecture & Design Guide

This guide explains how bare-metal / HAL embedded firmware works on the **STM32F407VET6** MCU (WHEELTEC C30D board), starting from hardware startup, system clocking, timer interrupts, to peripheral drivers and serial protocols. For the OLED screen specification and proposal, see [OLED Dashboard Specification](file:///home/wm_u26/dev/school/mdp/docs/stm32/oled_dashboard.md).

---

## 1. Embedded Fundamentals: Why `HAL_Init`, System Clock, and SysTick?

When an ARM Cortex-M4 microcontroller powers on:
1. The hardware loads the **Stack Pointer** and jumps to the **Reset Handler** (defined in startup assembly).
2. The MCU initially runs on a low-speed internal oscillator (HSI @ 16 MHz).
3. The program enters `main()`.

### A. `HAL_Init()`
Before configuring peripherals, `HAL_Init()` must be called to:
- Set up Flash prefetch buffer, instruction cache, and data cache for maximum CPU performance.
- Set priority grouping of NVIC (Nested Vectored Interrupt Controller) to 4 bits for preemption.
- Initialize the **SysTick** timer to generate a 1ms periodic interrupt.

### B. `SystemClock_Config()` (Clock Tree Setup)
By default, the MCU runs slowly on internal HSI (16 MHz). To run at max speed (168 MHz):
- We turn on the **HSE (High-Speed External Oscillator)**, which uses the onboard 8 MHz crystal.
- We configure the **Phase-Locked Loop (PLL)** multiplier and dividers:
  $$\text{SYSCLK} = \frac{\text{HSE}}{\text{PLLM}} \times \text{PLLN} / \text{PLLP} = \frac{8\text{ MHz}}{8} \times 336 / 2 = 168\text{ MHz}$$
- This gives the CPU full processing power to run PID control loops, decode wheel encoders, and process USB serial packets.

> [!WARNING]
> **Crystal Frequency Matching (`-D HSE_VALUE=8000000L`):**  
> ST HAL libraries assume a default crystal frequency of 25 MHz if `HSE_VALUE` is unassigned. Because the WHEELTEC C30D board uses an **8 MHz crystal**, omitting `-D HSE_VALUE=8000000L` in `platformio.ini` causes USART baud rate calculations to be off by $25 / 8 = 3.125\times$, leading to garbled serial terminal text (`␜␀...`).


### C. `SysTick_Handler()`
The Cortex-M core contains a 24-bit down-counter timer called **SysTick**.
Every 1 millisecond, the SysTick hardware triggers an interrupt that calls `SysTick_Handler()`:
```c
void SysTick_Handler(void)
{
    HAL_IncTick(); // Increments internal millisecond counter
}
```
This millisecond counter is required by `HAL_Delay(500)` and timeout logic throughout ST HAL.

---

## 2. Peripheral Clocking Rule

In STM32 microcontrollers, **all hardware peripherals (GPIO ports, Timers, UARTs) have their clock gated OFF by default to save power**.

Before reading or writing to any peripheral register, you **must** enable its clock first using `__HAL_RCC_xxxx_CLK_ENABLE()`:
```c
__HAL_RCC_GPIOD_CLK_ENABLE();  // Enable clock for GPIO Port D
__HAL_RCC_USART3_CLK_ENABLE(); // Enable clock for USART3
```
*Rule of Thumb*: If you forget to enable a peripheral's clock, accessing its registers will cause a **HardFault** crash!

---

## 3. Peripheral Drivers & Pin Mappings on C30D

### A. USB-Serial (`USART3` on `PD8` / `PD9`)
- **TX Pin**: `PD8` (Transmit data to RPi/PC)
- **RX Pin**: `PD9` (Receive data from RPi/PC)
- **Baud Rate**: 115200 baud
- **`printf()` Retargeting**: Overriding the standard C `_write()` function allows standard `printf()` to transmit characters over `USART3`.

### B. AT8236 Dual H-Bridge Motor Drivers (PWM)
- **Rear Left Motor (A)**: `PB8` (`TIM10_CH1`) & `PB9` (`TIM11_CH1`)
- **Rear Right Motor (B)**: `PE5` (`TIM9_CH1`) & `PE6` (`TIM9_CH2`)
- Setting timer PWM pulse width controls the duty cycle (0% to 100%) sent to the motor driver inputs.

### C. Status LED (`PE8`)
- **Pin**: `PE8` (Configured as GPIO Output Push-Pull)
- Toggled in the `while(1)` main loop to indicate system health ("heartbeat").

---

## 4. Communication Protocol Design (Host ↔ STM32)

To keep latency low and determinism high:
- Data is framed into fixed-size binary packets (e.g. 12 bytes).
- A 2-byte header (`0xAA, 0x55`) identifies the start of a frame.
- A 16-bit CRC or checksum verifies data integrity.

```
+----------------+---------------------+---------------------+-------------------+
| Header (2B)    | Target Speed (4B)   | Target Steer (4B)   | Checksum (2B)     |
| 0xAA  0x55     | float (rad/s)       | float (rad)         | uint16            |
+----------------+---------------------+---------------------+-------------------+
```

---

## 5. Firmware File Layout

```
mdp_stm32/
├── platformio.ini              # Build flags, MCU target, upload protocol
├── include/
│   ├── stm32f4xx_hal_conf.h    # HAL module configuration switchboard
│   ├── usart.h                 # USART3 serial header
│   └── motor.h                 # Motor driver header
└── src/
    ├── main.c                  # HAL init, clock setup, main loop
    ├── usart.c                 # USART3 init & printf retargeting
    └── motor.c                 # AT8236 motor PWM pin setup
```
