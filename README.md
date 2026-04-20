# D-plugins

`D-plugins` is an Android Studio plugin designed as a container for multiple development utilities.

Currently it ships two bundled capabilities:

| Tool | Purpose |
|---|---|
| **App Logs** | Records per-run Logcat output into files stored inside the project |
| **App Screenshots** | Captures device screenshots via ADB and saves them to any folder you choose |

---

## App Logs

### What It Does

`App Logs` focuses on one workflow:

- Automatically starts a recording when an Android app **Run** or **Debug** session begins
- Creates a separate `.txt` log file for each launch
- Stores logs under `AppLogs/` inside the current project root
- Captures only lines emitted by the target app package, using UID-based filtering with a process-name fallback when UID lookup is unavailable
- Stops recording automatically when the session ends, the plugin is disabled, logs are deleted, or the project closes

### Tool Window Actions

| Button | Effect |
|---|---|
| Record toggle | Enable or disable automatic recording for future runs |
| Open Folder | Reveal the `AppLogs/` directory in the system file manager |
| Delete Logs | Stop all active recordings and delete every file inside `AppLogs/` |

### Why It Exists

Android Studio Logcat is excellent for live inspection, but it is less convenient when you want:

- A clean per-run artifact
- Session-specific logs stored inside the project repository
- A file you can send to a teammate or attach to a bug report
- Reproducible logs tied to a specific device and launch timestamp

### How Recording Works

For each supported Android app launch, the plugin:

1. Resolves the target application id from the run configuration
2. Resolves the launched device(s)
3. Resolves the app UID on the device when available
4. Creates one log file per device
5. Starts an `adb logcat` process for that device
6. Filters output to the app package via UID when possible, otherwise falls back to matching app processes and sub-processes
7. Saves matching lines into the session file

The generated files contain raw Logcat lines in `threadtime/year` style, making them directly comparable with regular `adb logcat` output.

### Log Storage

Log files are written to:

```
<project-root>/AppLogs/
```

Filename format:

```
<sanitized-device-name>_yyyy-MM-dd_HH-mm-ss.txt
```

Example:

```
samsung-sm_s928b-RFCX80MA49M_2026-04-20_11-19-40.txt
```

---

## App Screenshots

### What It Does

`App Screenshots` lets you capture a screenshot from any connected Android device without leaving Android Studio:

- Detects all ADB-connected devices automatically
- Lets you pick a target folder on your machine (remembered across projects)
- Saves each capture as a `.png` file with a timestamped filename
- Tracks how many plugin-captured screenshots are already in the target folder
- Lets you delete all plugin-captured screenshots from the target folder in one click

### Tool Window Actions

| Button | Effect |
|---|---|
| Device selector | Choose which connected device to capture from |
| Choose Folder | Set (or change) the target folder for saved screenshots |
| Capture Screenshot | Take an immediate screenshot from the selected device |
| Delete Screenshots | Delete all plugin-captured screenshots from the target folder |

### Screenshot Storage

Screenshots are saved to any folder you choose.  
The settings (target folder) are stored at IDE application level and persist across projects.

Filename format:

```
<sanitized-device-name>_yyyy-MM-dd_HH-mm-ss.png
```

Example:

```
Pixel_8_Pro_2026-04-20_11-45-30.png
```

---

## Compatibility

| Property | Value |
|---|---|
| Target IDE | Android Studio Ladybug 2024.2+ |
| Supported build range | `242` – `253.*` |
| JVM toolchain | 21 |
| Plugin version | 1.0.5 |

---

## Installation

### Install From Disk

1. Open Android Studio.
2. Go to **Settings / Preferences → Plugins**.
3. Click the gear icon ⚙️.
4. Choose **Install Plugin from Disk…**
5. Select the generated ZIP file:

```
build/distributions/D-plugins-<version>.zip
```

---

## How To Use

### App Logs

1. Open the **App Logs** tool window from the right side of Android Studio.
2. Turn the **Record** toggle on.
3. Run or debug an Android application.
4. After the session ends, open the `AppLogs/` folder inside your project to find the generated `.txt` file.

### App Screenshots

1. Open the **App Logs** tool window (App Screenshots is in the same panel).
2. Click **Choose Folder** to set where screenshots should be saved.
3. Connect an Android device and click **Capture Screenshot**.
4. The `.png` file is saved immediately and a notification confirms the path.

---

## Project Structure

```
src/main/kotlin/com/d/h/plugins/applogs/
├── logcat/          # Logcat line parsing and UID/process-name filtering
├── model/           # Immutable snapshot and view data classes
├── service/         # Session lifecycle, ADB interaction, state topics
│   ├── LogSessionManager.kt       # Manages App Logs recording sessions
│   ├── AdbLogcatRecorder.kt       # Runs and streams adb logcat
│   ├── AppScreenshotsManager.kt   # Manages screenshot capture and device list
│   ├── ConnectedDevicesParser.kt  # Parses adb devices output
│   └── DeviceAndAppResolver.kt    # Resolves run-target device and app id
├── startup/         # Project startup activity
├── state/           # Persistent settings (App Logs & App Screenshots)
├── ui/              # Tool window factory and panel (Swing)
└── util/            # Path helpers for logs and screenshots
```

---

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

```
build/distributions/D-plugins-<version>.zip
```

---

## Release Workflow

1. Update the plugin version in `build.gradle.kts`
2. Run tests: `./gradlew test`
3. Build the ZIP: `./gradlew buildPlugin`
4. Install the ZIP from `build/distributions/` or share it

---

## Roadmap Direction

`D-plugins` is intentionally named as a broader plugin container so additional Android Studio utilities can be added later without renaming the plugin or changing the packaging model.

Current out-of-scope items for App Logs:

- Non-Android run configurations
- Test configurations
- Replacing the Android Studio Logcat tool window
- Aggregating multiple runs into a single rolling file
