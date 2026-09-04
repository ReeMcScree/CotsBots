# Restore & Setup

This repo excludes a few large or secret files by design. After cloning, restore
these three things once.

## 1. The Gemini API key (required, do this first)

The key is read from `local.properties` (which Git ignores) into
`BuildConfig.GEMINI_API_KEY`.

1. **Rotate your key** at https://aistudio.google.com — the previously used key
   was exposed and should be considered compromised. Delete it, create a new one.
2. Open `local.properties` and set:
   ```properties
   GEMINI_API_KEY=your_new_key_here
   ```
3. `local.properties` is gitignored — never commit it.

For anything beyond classroom/personal use, put a small backend between the app
and Gemini so the key never ships inside the APK, and restrict the key to your
app's package + signing certificate in Google Cloud Console.

## 2. The OpenCV `sdk` module (required to build)

`settings.gradle` includes `:sdk` and the app depends on `project(':sdk')`. This
is the OpenCV Android SDK, kept out of Git because of its size. To restore:

1. Download the OpenCV Android SDK (this project used **OpenCV 4.x** — match the
   version you originally imported).
2. In Android Studio: **File → New → Import Module**, point it at the OpenCV
   SDK's `sdk` folder, and name the module `sdk`.
3. Confirm `settings.gradle` still has `include ':sdk'` and that `app/build.gradle`
   keeps `implementation project(':sdk')`.
4. Sync Gradle.

`MainActivity` calls `OpenCVLoader.initLocal()`, so no separate OpenCV Manager
app is needed once the module is linked.

## 3. The Gradle wrapper jar (required to build from CLI)

`gradle/wrapper/gradle-wrapper.jar` is a binary that is not included here.
Regenerate it once:

```bash
gradle wrapper --gradle-version 8.11.1
```

Or simply open the project in Android Studio, which restores the wrapper
automatically. `gradle/wrapper/gradle-wrapper.properties` and
`gradle/libs.versions.toml` are already present.

## 4. Flash the firmware

1. Open `firmware/cotsbots_firmware.ino` in the Arduino IDE.
2. Install DFRobot Bluno board support if needed.
3. Confirm the motor pins (`E1=5, E2=6, M1=4, M2=7`) match your wiring.
4. Upload over micro-USB. Open Serial Monitor at 115200 baud, newline ending.
5. Test: type `200,200,1000` and press enter. Type `Z` for the help text.

## Tuning

- **Speed**: `MOVE_SPEED` in `MainActivity.java` (0-255).
- **Move duration**: `MOVE_DURATION_MS` (default 1000 ms).
- **Turn style**: LEFT/RIGHT pivot in place. For gentler turns, keep one motor
  forward and slow the other instead of reversing, e.g. return `"60,200," + dur`.
- **API pacing**: `MIN_API_INTERVAL_MS` (default 6000 ms).

## Note on blocking stops

The firmware's `executeInstruction()` uses `delay(duration)`, which blocks during
a timed move. Since the app waits >=6s between commands and moves are ~1s, a STOP
never needs to interrupt a move in progress. For instant-interrupt stops, switch
the firmware to non-blocking `millis()` timing.
