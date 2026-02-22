# Media Remote

A simple Android app with a giant play/pause button, skip controls, and brightness slider. Perfect for when your hands are full and you need big targets to tap.

## Features

- **Giant play/pause toggle** — fills the centre of the screen
- **Skip back / forward buttons** — configurable duration (default 20s)
- **Brightness slider** — at the bottom of the screen
- **Screen stays awake** while the app is open
- **Dark theme** throughout

## How to build

### Requirements
- Android Studio Hedgehog (2023.1.1) or newer
- JDK 17

### Steps

1. Open Android Studio
2. **File → Open** and select the `MediaRemote` folder
3. Let Gradle sync finish
4. Connect your phone (USB debugging on) or use an emulator
5. Click **Run ▶**

### Optional: System brightness control

By default the brightness slider adjusts brightness only while the app is on screen. To also change the system brightness permanently:

1. Open the app
2. Tap the ⚙ settings icon (top right)
3. Tap **Allow system brightness control**
4. Grant the permission in the system dialog

## How it works

The play/pause and skip buttons send **media key events** via `AudioManager.dispatchMediaKeyEvent()`. This is the same mechanism that Bluetooth headphones and headset buttons use, so it works with any app that responds to media keys — Pocket Casts, AntennaPod, Audible, Spotify, YouTube, etc.

The skip buttons send `KEYCODE_MEDIA_FAST_FORWARD` / `KEYCODE_MEDIA_REWIND`. Note that not all apps map these to a fixed skip duration — some apps define their own skip amount and ignore the system key. If your podcast app has its own skip duration setting, that will take precedence.

## Settings

| Setting | Default | Description |
|---|---|---|
| Skip duration | 20s | How many seconds the skip buttons jump |

## Notes

- The play/pause button tracks state locally — if you pause from another app it won't auto-sync. Just tap it to toggle; the underlying app will respond correctly regardless of the button's visual state.
- `FLAG_KEEP_SCREEN_ON` is used (not a wake lock) so no extra permissions are needed to keep the screen on.
