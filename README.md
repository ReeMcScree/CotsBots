# COTSBOTS Navigation

A senior capstone project bridging low-cost robotics hardware with LLM-driven
autonomous navigation. An Android phone acts as the robot's eyes and brain: it
photographs what's ahead, asks a vision LLM how to proceed, and relays the
resulting motion command to an Arduino-based robot over Bluetooth Low Energy.

## The vision-navigation loop

1. **Capture** — CameraX grabs a frame from the phone's rear camera.
2. **Annotate** — OpenCV runs Canny edge detection and splits the frame into
   FAR / MID / CLOSE zones, counting edge density as a proximity estimate. A
   local safety rule stops the robot immediately if the CLOSE zone is too dense,
   without waiting for the network.
3. **Ask the LLM** — the annotated image plus a navigation prompt go to Gemini,
   which replies with strict JSON: `{"command":"FORWARD","reason":"path clear"}`.
4. **Parse** — the app validates the command against FORWARD / BACK / LEFT /
   RIGHT / STOP.
5. **Relay** — the command is translated to the firmware's motion protocol and
   written to the robot's BLE serial characteristic.

## Command protocol

App and firmware communicate over BLE serial using CSV motion commands:

```
left,right,duration
```

- `left`, `right`: motor speeds, −255 to 255 (negative = reverse)
- `duration`: milliseconds to run, then auto-stop (0 = continuous)
- `0,0,0` = stop immediately

Example: `200,200,1000` drives both motors forward at speed 200 for one second.
The firmware parser tolerates stray whitespace, which resolves the earlier
phone-over-Bluetooth parsing issue.

## Project layout

| Path | What it is |
|------|-----------|
| `app/` | The Android app (Java, CameraX + OpenCV + Gemini + BLE). |
| `firmware/` | Arduino sketch for the robot (DFRobot Bluno). |
| `docs/` | Setup and restore notes. |

## Building

See [`docs/RESTORE.md`](docs/RESTORE.md) — a couple of large binary pieces
(the OpenCV `sdk` module and the Gradle wrapper jar) are intentionally kept out
of Git and must be restored once locally.

## Security

The Gemini API key is **not** committed. It is read at build time from
`local.properties` into `BuildConfig.GEMINI_API_KEY`. See `docs/RESTORE.md`.

---
*Senior Capstone Project — COTSBOTS Navigation*
