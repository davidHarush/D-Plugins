# D-plugins

An Android Studio plugin that keeps your development flow uninterrupted.  
No switching apps. No context loss. Everything you need, right inside the IDE.

---

## App Logs — Never Lose a Logcat Session Again

Every time you run or debug your Android app, **App Logs** automatically captures the full Logcat output and saves it as a file inside your project.

**Why you'll love it:**
- 🗂 One file per run, organized by device and timestamp
- 🔍 Only your app's logs — no noise from other processes
- 📁 Lives inside your project — commit it, share it, or attach it to a bug report
- ⚡ Zero setup — just enable the toggle and run

> Open the **App Logs** panel → toggle **Record** → run your app. That's it.

### 🤖 GitHub Copilot Skill

Install the built-in Copilot Skill directly from the **App Logs** panel.  
Once installed, GitHub Copilot learns how to read and analyze the log files this plugin creates — so you can just ask Copilot about your logs and get instant answers.

The skill is placed at:
```
.github/skills/android-logcat-reader/SKILL.md
```

If the skill is already installed, the panel shows a **✓ Installed** badge instead of the button.

---

## App Screenshots — One-Click Device Capture

Take a screenshot from any connected Android device directly from Android Studio.

**Why you'll love it:**
- 📱 Detects connected devices automatically
- 💾 Saves to any folder you choose — remembered across projects
- 🏷 Files are named by device and timestamp, so they stay organized
- 🗑 Clean up all captured screenshots in one click

> Open the **App Logs** panel → choose a folder → hit **Capture Screenshot**.

---

## Compatibility

- **Android Studio** Ladybug 2024.2 and later
- **Plugin version** 1.0.5

---

## Installation

1. Go to **Settings → Plugins → ⚙️ → Install Plugin from Disk…**
2. Select `build/distributions/D-plugins-<version>.zip`
3. Restart Android Studio

---

## Build From Source

```bash
./gradlew buildPlugin
```

Output: `build/distributions/D-plugins-<version>.zip`


Take a screenshot from any connected Android device directly from Android Studio.

**Why you'll love it:**
- 📱 Detects connected devices automatically
- 💾 Saves to any folder you choose — remembered across projects
- 🏷 Files are named by device and timestamp, so they stay organized
- 🗑 Clean up all captured screenshots in one click

> Open the **App Logs** panel → choose a folder → hit **Capture Screenshot**.

---

## Compatibility

- **Android Studio** Ladybug 2024.2 and later
- **Plugin version** 1.0.5

---

## Installation

1. Go to **Settings → Plugins → ⚙️ → Install Plugin from Disk…**
2. Select `build/distributions/D-plugins-<version>.zip`
3. Restart Android Studio

---

## Build From Source

```bash
./gradlew buildPlugin
```

Output: `build/distributions/D-plugins-<version>.zip`

