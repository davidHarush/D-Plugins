# D-plugins

`D-plugins` is an Android Studio plugin designed as a container for multiple development utilities over time.

The first bundled capability is `App Logs`, a tool window that records app-specific Logcat output for each Android `Run` or `Debug` session and stores it inside the current project.

## What App Logs Does

`App Logs` is focused on one workflow:

- Automatically start a recording when an Android app `Run` or `Debug` session begins
- Create a separate log file for each launch
- Store logs under `AppLogs/` in the current project
- Capture only logs emitted by the target app package, with a process-name fallback when UID lookup is unavailable
- Stop recording automatically when the session ends, the plugin is disabled, logs are deleted, or the project closes

The tool window also lets you:

- Turn recording on or off
- Open the `AppLogs` folder
- Delete all saved logs
- See a compact summary of active recording sessions

## Why This Plugin Exists

Android Studio Logcat is excellent for live inspection, but it is less convenient when you want:

- A clean per-run artifact
- Session-specific logs stored inside the project
- A file you can send to a teammate or attach to a bug report
- Reproducible logs from a specific device and launch

`App Logs` solves that by creating one text file per run, with filenames based on the device name and session start time.

## Compatibility

- Android Studio `Ladybug 2024.2+`
- Supported IDE build range: `242` to `253.*`
- Kotlin/JVM target toolchain: `21`

## Installation

### Install From Disk

1. Open Android Studio.
2. Go to `Settings` or `Preferences`.
3. Open `Plugins`.
4. Click the gear icon.
5. Choose `Install Plugin from Disk...`.
6. Select the generated ZIP file from:

`build/distributions/D-plugins-<version>.zip`

## How To Use

1. Open the `App Logs` tool window from the right side of Android Studio.
2. Turn the recording toggle on.
3. Run or debug an Android application.
4. After the session starts, the plugin begins collecting matching Logcat lines.
5. When the run ends, open the `AppLogs/` folder inside your project to inspect the generated `.txt` file.

## How Recording Works

For each supported Android app launch, the plugin:

- Resolves the target application id
- Resolves the launched device or devices
- Resolves the app UID when available
- Creates one log file per device
- Starts an `adb logcat` process for that device
- Filters output to the app package via UID when possible, otherwise falls back to matching app processes and subprocesses
- Saves matching lines into the session file

The generated file contains raw Logcat lines in `threadtime/year` style, making it easy to compare with regular `adb logcat` output.

## Log Storage

Log files are written to:

`<project-root>/AppLogs/`

Filename format:

`<sanitized-device-name>_yyyy-MM-dd_HH-mm-ss.txt`

Example:

`samsung-sm_s928b-RFCX80MA49M_2026-04-20_11-19-40.txt`

## Current Scope

The current implementation starts only for supported Android app `Run` and `Debug` sessions.

It does not currently aim to:

- Record non-Android run configurations
- Record test configurations
- Replace the Android Studio Logcat tool window
- Aggregate multiple runs into a single rolling file

## Project Structure

- `src/main/kotlin/com/d/h/plugins/applogs/service/`:
  session lifecycle, ADB interaction, and state publishing
- `src/main/kotlin/com/d/h/plugins/applogs/ui/`:
  tool window UI
- `src/main/kotlin/com/d/h/plugins/applogs/logcat/`:
  Logcat parsing and filtering
- `src/main/kotlin/com/d/h/plugins/applogs/state/`:
  persistent project-level settings

## Local Development

Run tests:

```bash
./gradlew test
```

Build the plugin ZIP:

```bash
./gradlew buildPlugin
```

The generated artifact is written to:

`build/distributions/`

## Release Workflow

For every plugin change, the expected workflow is:

1. Update the plugin version in `build.gradle.kts`
2. Run tests as needed
3. Generate a fresh plugin ZIP with `./gradlew buildPlugin`
4. Share or install the new ZIP from `build/distributions/`

## Roadmap Direction

`D-plugins` is intentionally named as a broader plugin container so additional Android Studio utilities can be added later without renaming the plugin or changing the packaging model.
