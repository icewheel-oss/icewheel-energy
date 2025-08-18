# Scheduling and Reconciliation

This document explains how IceWheel Energy's scheduling and reconciliation system works in a way that is easy to
understand.

At its core, the system has two main responsibilities:

1. **Executing schedules on time.**
2. **Ensuring the Powerwall's state is always correct, even if things go wrong.**

## The Key Players

Imagine you have a team of diligent robots working for you. In IceWheel Energy, these robots are different components in
the software:

* **The `PowerwallScheduleExecutor` (The Punctual Assistant)**: This is the most active robot. Every single minute, it
  wakes up and checks its list of tasks (your schedules). If it finds a schedule that is due *right now*, it immediately
  sends the command to your Powerwall. For example, at 7:00 PM, it might tell your Powerwall to start discharging (using
  on-peak settings). It then makes a note in its logbook (the execution history) about what it did.

* **The `PowerwallStateReconciler` (The Supervisor)**: This robot is the supervisor. It doesn't wait for a specific time
  on a schedule. Instead, every 15 minutes, it does a patrol. It asks your Powerwall, "What is your current backup
  reserve percentage?" Then, it looks at your schedules to determine what the percentage *should* be at that moment. If
  the numbers don't match, the Supervisor steps in and corrects the Powerwall's setting. This is the system's
  self-healing capability.

* **The `MisfireHandlingService` (The Startup Inspector)**: This robot only works once, right when the application
  starts up. Its job is to immediately call the Supervisor (`PowerwallStateReconciler`) to do a full check. This is
  important because if the application was turned off or restarting, it might have missed a scheduled change. The
  Startup Inspector ensures that the first thing the application does is make sure your Powerwall is in the correct
  state.

* **The `TokenRefreshScheduler` (The Key Master)**: To talk to your Powerwall, the application needs a special key (an
  API token). These keys expire after a while. The Key Master's job is to periodically check all the keys and get new
  ones before they expire. This ensures that the other robots can always communicate with your Powerwall.

## How It Works in Practice: A Layman's Analogy

Think of it like setting the thermostat in your house.

1. **Creating a Schedule**: You tell the system, "Every weekday, I want the house to be 20°C from 7:00 PM to 10:00 PM (
   on-peak), and 15°C at all other times (off-peak)."

2. **Execution**: At exactly 7:00 PM, the **Punctual Assistant** (`PowerwallScheduleExecutor`) sends the command to the
   thermostat: "Set temperature to 20°C." At 10:00 PM, it sends another command: "Set temperature to 15°C."

3. **Reconciliation (Self-Healing)**: Now, imagine someone in your family manually turns the thermostat up to 22°C at 8:
   00 PM. The Punctual Assistant doesn't know about this because it only acts at the scheduled times.

   This is where the **Supervisor** (`PowerwallStateReconciler`) comes in. At its next 15-minute check-in (say, at 8:15
   PM), it will see that the thermostat is at 22°C, but the schedule says it should be 20°C. The Supervisor will then
   correct the setting back to 20°C. This ensures your desired temperature is maintained, even if there are manual
   changes or other unexpected events.

4. **Handling Downtime**: If there's a power outage and your thermostat reboots at 7:30 PM, it might have missed the 7:
   00 PM command to switch to 20°C. The **Startup Inspector** (`MisfireHandlingService`) handles this. The moment the
   system comes back online, it immediately checks what the temperature *should* be and sets it correctly.

This combination of precise execution and robust self-healing makes the scheduling system reliable and ensures that your
Powerwall operates according to your plan.
