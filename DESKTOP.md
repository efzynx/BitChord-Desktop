# BitChord Desktop Guide (Linux & Windows)

BitChord Desktop is a native, high-performance YouTube Music client for desktop platforms (**Linux & Windows**), built using **Compose Multiplatform** (Jetpack Compose on Desktop JVM) with an aesthetic *frosted-glass* interface, low-latency audio processing, and deep operating system integrations.

---

## Core Desktop Features

### 1. Native Operating System Integrations
- **Linux MPRIS & Hardware Media Keys**: Directly interfaces with Linux media subsystems (`org.mpris.MediaPlayer2` and `org.mpris.MediaPlayer2.Player`), fully compatible with GNOME Media, KDE Plasma Media Player, `playerctl`, lock screens, and hardware keyboard multimedia keys.
- **Windows SMTC**: Native Windows System Media Transport Controls integrated into the taskbar thumbnail preview, volume overlay, and lock screen.
- **System Tray**: Modern status notifier support (`org.kde.StatusNotifierItem` for KDE/GNOME and AWT Tray fallback on Windows/Linux) allowing background playback while minimized.
- **FreeDesktop Desktop Entry**: Standard `.desktop` launcher (`bitchord.desktop`) with high-resolution vector icons and context menu quick actions (Play/Pause, Next, Previous).

### 2. Native Discord Rich Presence (RPC)
- Automatic local Inter-Process Communication (IPC) without requiring third-party bots, developer tokens, or user credentials.
- Connects via **Unix Domain Sockets** on Linux and **Named Pipes** on Windows (`\\.\pipe\discord-ipc-*`).
- Broadcasts real-time track metadata: title, artist, album art URL, playback state, and elapsed duration.

### 3. High-Fidelity Audio Engine & Lyrics
- **FFmpeg 7.1 Decoding (JavaCPP)**: Hardware-accelerated decoding supporting Lossless Hi-Res audio (FLAC, ALAC, WAV) and standard web codecs (Opus, AAC, MP3, WebM).
- **Windowed Streaming (`googlevideo.com`)**: Bypasses YouTube HTTP 403 Forbidden constraints using chunked byte-range requests.
- **Headless PoToken Minter**: Executes Google BotGuard JavaScript via headless JavaFX WebKit WebEngine to mint integrity tokens without user intervention.
- **Apple-Style Word-by-Word Lyrics**: Syllable-level real-time animations powered by continuous per-frame clocks on the desktop JVM, seamlessly fetching from LRCLib, Musixmatch, YouTube Captions, KuGou, and local embedded tags (ID3v2 / Vorbis comments).
- **Aesthetic Apple Music Bottom Bar**: Interactive liquid blur progress scrub on hover, auto-scrolling horizontal marquee for long track and artist titles, and clickable multi-artist links.

### 4. Listen Together (Party Mode)
- **Real-Time Synchronized Playback**: Listen to music simultaneously with friends in a shared room.
- **Cloudflare Quick Tunnel Integration**: Host a local party server and generate a free public link (`trycloudflare.com`) without opening router ports or configuring static IPs.
- Built-in checker and installation guidance for the `cloudflared` CLI on both Windows and Linux.

### 5. In-App Updater & System Diagnostics
- Dedicated **Updates & System Info** dashboard in Settings:
  - Automatically identifies host operating system, CPU architecture, and Java runtime environment.
  - Detects current packaging type (AppImage, DEB, RPM, Windows Portable, Windows Installer, etc.).
  - Contextual uninstallation instructions tailored to the active install method.
  - Direct update checks against the official `efzynx/BitChord-Desktop` GitHub releases endpoint.

---

## Keyboard Shortcuts

| Shortcut | Function |
| :--- | :--- |
| **`Space`** | Play / Pause |
| **`Media Play/Pause`** | Hardware media toggle |
| **`Media Next / Prev`** | Next track / Previous track |
| **`Left Arrow` (`<-`)** | Seek backward 5 seconds |
| **`Right Arrow` (`->`)** | Seek forward 5 seconds |
| **`Up Arrow` (`^`)** | Volume up (+5%) |
| **`Down Arrow` (`v`)** | Volume down (-5%) |
| **`M`** | Mute / Unmute |
| **`Ctrl + F` / `Cmd + F`** | Focus search input field |
| **`Ctrl + ,` / `Cmd + ,`** | Open Settings & System Info dashboard |
| **`F11` / `F`** | Toggle Fullscreen mode |
| **`Esc`** | Dismiss Now Playing / Dialogs / Return to previous view |

---

## Installation Guide

### Linux

#### 1. AppImage (Recommended - Universal for All Distributions)
1. Download the `BitChord-*.AppImage` file from the [Releases](https://github.com/efzynx/BitChord-Desktop/releases) page.
2. Grant executable permissions and launch:
   ```bash
   chmod +x BitChord-*.AppImage
   ./BitChord-*.AppImage
   ```

#### 2. Debian / Ubuntu / Linux Mint (`.deb`)
```bash
sudo dpkg -i BitChord_*_amd64.deb
sudo apt-get install -f # If resolving missing dependencies
```

#### 3. Fedora / RHEL / openSUSE (`.rpm`)
```bash
sudo rpm -i BitChord-*.rpm
# Or via dnf:
sudo dnf install ./BitChord-*.rpm
```

#### 4. Portable Tar.gz / Local Installation Script
If downloading the tarball or building from source, use the provided installation scripts:
```bash
# Run installer (automatically registers to Linux Application Menu & adds ~/.local/bin/bitchord):
./scripts/install-desktop.sh

# Uninstall:
./scripts/uninstall-desktop.sh
```

---

### Windows

#### 1. Windows Installer (`.msi` / `.exe`)
1. Download `BitChord-*.msi` or `BitChord-*.exe` from the Releases page.
2. Run the installer and follow the on-screen prompts. Start Menu and Desktop shortcuts will be created automatically.

#### 2. Windows Portable (`.zip`)
1. Download `BitChord-*-windows-portable.zip`.
2. Extract the archive to any desired directory (e.g. `C:\Apps\BitChord` or Documents).
3. Run **`BitChord.vbs`** to launch the player silently without an unnecessary Command Prompt console window.
4. You can create a desktop shortcut to `BitChord.vbs` for easy access.

---

## Building from Source

### Prerequisites
- **Java JDK 17 or 21** (Eclipse Temurin or OpenJDK recommended).
- Git

### Linux Build Commands
```bash
# 1. Run the desktop application locally:
./gradlew :desktopApp:run

# 2. Build Standalone Linux Executable:
./gradlew :desktopApp:createDistributable
# Output binary: desktopApp/build/compose/binaries/main/app/BitChord/bin/BitChord

# 3. Package universal AppImage:
./gradlew :desktopApp:packageAppImage

# 4. Package .deb and .rpm (requires fakeroot & dpkg-dev / rpm):
./gradlew :desktopApp:packageDeb :desktopApp:packageRpm

# 5. Execute desktop unit test suite:
./gradlew :desktopApp:test
```

### Windows Build Commands
```bash
# Build Windows Portable Zip (can be built from Linux or Windows):
./gradlew :desktopApp:windowsPortable -Pbitchord.target=windows
# Output package: desktopApp/build/distributions/BitChord-*-windows-portable.zip

# Build Windows MSI / EXE (on Windows):
gradlew.bat :desktopApp:packageMsi :desktopApp:packageExe
```
