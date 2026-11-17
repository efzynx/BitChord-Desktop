<div align="center">

<br/>

<img src="Logo.png" alt="BitChord Logo" width="180" />

# BitChord Desktop

### High-Performance YouTube Music Client for Linux and Windows

<br/>

[![Latest Release](https://img.shields.io/github/v/release/efzynx/BitChord-Desktop?style=for-the-badge&logo=github&logoColor=white&color=161b22)](https://github.com/efzynx/BitChord-Desktop/releases)
[![Platform](https://img.shields.io/badge/Platform-Linux_%7C_Windows-24292e?style=for-the-badge&logo=linux&logoColor=white)](https://github.com/efzynx/BitChord-Desktop/releases)
[![Compose Multiplatform](https://img.shields.io/badge/Framework-Compose_Multiplatform-7F52FF?style=for-the-badge&logo=kotlin&logoColor=white)](https://www.jetbrains.com/lp/compose-multiplatform/)
[![License](https://img.shields.io/github/license/efzynx/BitChord-Desktop?style=for-the-badge&logo=opensourceinitiative&logoColor=white&color=161b22)](LICENSE)
[![Downloads](https://img.shields.io/github/downloads/efzynx/BitChord-Desktop/total?style=for-the-badge&color=161b22)](https://github.com/efzynx/BitChord-Desktop/releases)

<br/>

[**Download**](#download) &bull; [**About This Project**](#about-this-project) &bull; [**Desktop Architecture**](#desktop-architecture) &bull; [**Features**](#features) &bull; [**Desktop Guide**](DESKTOP.md) &bull; [**Contributing**](#contributing) &bull; [**Disclaimer**](#disclaimer)

<br/>

<a href="https://fmhy.net/mobile#youtube-music" target="_blank" rel="noopener noreferrer"><img src="https://cdn.jsdelivr.net/gh/JMcrafter26/badges@main/src/assets/available/fmhy/cozy.svg" alt="Featured on FMHY" height="50"/></a>
<a href="https://trendshift.io/repositories/177639?utm_source=repository-badge&amp;utm_medium=badge&amp;utm_campaign=badge-repository-177639" target="_blank" rel="noopener noreferrer"><img src="https://trendshift.io/api/badge/repositories/177639" alt="BitChord | Trendshift" width="220" height="50"/></a>

</div>

> [!IMPORTANT]
> BitChord is an independent open-source audio client and is not affiliated with, endorsed by, or connected to YouTube, Google, or any of their parent companies.

---

<div align="center">

<img src="Banner.png" alt="BitChord Banner" width="100%" />

</div>

## About This Project

Originally initiated by [Kushagra Singh](https://github.com/kushagrasinghx) as an Android application, earlier desktop prototypes were discontinued in the upstream repository.

**This project ([efzynx/BitChord-Desktop](https://github.com/efzynx/BitChord-Desktop)) is a dedicated continuation that reconstructs, modernizes, and actively maintains native desktop support for Linux and Windows.**

BitChord Desktop is built upon **Compose Multiplatform (Jetpack Compose on Desktop JVM)** and an integrated **C++ / JavaCPP FFmpeg 7.1 audio pipeline**, delivering a refined frosted-glass interface with low-latency playback, native system integrations, and word-by-word synced lyrics.

---

## Desktop Architecture & Native Integrations

### Linux Platform Integration
- **MPRIS D-Bus Specification**: Native integration with `org.mpris.MediaPlayer2` and `org.mpris.MediaPlayer2.Player`. Fully compatible with GNOME Media, KDE Plasma Media Player, `playerctl`, and desktop lock screens.
- **Hardware Multimedia Keys**: Global media key listeners for Play, Pause, Next, Previous, and Stop.
- **FreeDesktop Standards**: Compliant `.desktop` entry with desktop context menu actions (Play/Pause, Next, Previous) and high-resolution vector icons.
- **Comprehensive Packaging**: Production builds available in universal **AppImage**, Debian/Ubuntu **DEB**, Fedora/RHEL **RPM**, and standalone portable tarballs.
- **Local Installer Scripts**: Automated user installation via `scripts/install-desktop.sh` registering icons and application launchers to `~/.local`.

### Windows Platform Integration
- **System Media Transport Controls (SMTC)**: Native Windows media transport controls displayed in the volume overlay, taskbar thumbnail, and lock screen.
- **Portable Distribution**: Clean portable `.zip` packaging featuring a silent runner (`BitChord.vbs`) that executes the player in the background without terminal prompts.
- **Native Installers**: Automated builds for Windows Installer (**MSI**) and setup executable (**EXE**).

### Discord Rich Presence (RPC)
- Direct local Inter-Process Communication (IPC) via **Unix Domain Sockets** on Linux and **Named Pipes** on Windows.
- Eliminates the need for third-party bots, user account tokens, or login credentials. Automatically publishes playing status, album art, elapsed time, and metadata.

### High-Fidelity Streaming & Audio Engine
- **Native FFmpeg Decoding**: Powered by JavaCPP libavcodec, libavformat, and libswresample for lossless Hi-Res audio (FLAC, ALAC, WAV) and standard web formats (Opus, AAC, MP3, WebM).
- **Windowed Range Streaming**: Overcomes Google Video HTTP 403 Forbidden constraints using custom chunked range requests.
- **Headless PoToken Minter**: Employs headless JavaFX WebKit WebEngine to process Google BotGuard scripts and mint integrity tokens without user intervention.

### Synchronized Lyrics & Local Media
- **Apple-Style Word-by-Word Lyrics**: Synchronized syllable animations driven by continuous per-frame clocks on the desktop JVM.
- **Multi-Source Fetching**: Seamless fallback across LRCLib, Musixmatch, YouTube Captions, KuGou, and local embedded tags (ID3v2 / Vorbis comments).
- **Local Library & File Watching**: Real-time directory monitoring (`~/Music` and `~/Downloads`) via Java NIO `WatchService` with offline download capabilities.

### In-App Diagnostics & Updater
- Integrated **Updates & System Information** dashboard in Settings.
- Automatically determines runtime Java version, CPU architecture, host OS distribution, and packaging method (AppImage, DEB, RPM, Portable, MSI).
- Direct update verification against the `efzynx/BitChord-Desktop` release endpoint.

---

## Features

<table>
  <tr>
    <td width="50%" valign="top">

### Playback & Core Engine
- **Search, Browse, and Stream**: Access the complete YouTube Music catalog.
- **Hi-Res Lossless Audio**: FLAC/ALAC playback from configured sources with YouTube Music fallback.
- **Gapless Playback & Crossfade**: Configurable 0–12 second transition buffers.
- **Automix Transition Engine**: Dynamic DJ-style transitions with tempo stretching.
- **Offline Downloads**: High-speed track downloads with embedded artwork and tags.
- **Local Library Integration**: Unified browsing of device music libraries.
- **Background Media Controls**: Native foreground audio playback with MPRIS and SMTC.
- **Apple-Style Lyrics Engine**: Continuous frame-based syllable animations (credit: [binimum](https://github.com/binimum/am-lyrics)).

### Connectivity & Services
- **Google Account Authentication**: Optional sign-in for personalized playlists and recommendations.
- **Spotify Integration**: Import and play Spotify playlists and saved collections.
- **Native Discord RPC**: Local IPC communication without authentication tokens.
- **Audio Scrobbling**: Built-in support for Last.fm and ListenBrainz.
- **Custom Media Sources**: Extensible plugin system for user-defined audio backends.

    </td>
    <td width="50%" valign="top">

### Interface & User Experience
- **Animated Album Canvas**: Visual motion artwork on the player screen.
- **Word-Synced Lyrics**: Precise timing from providers including [LRCLIB](https://lrclib.net), [Musixmatch](https://www.musixmatch.com), [lrc.red](https://lrc.red), [BiniLyrics](https://github.com/binimum), [BetterLyrics](https://github.com/better-lyrics/better-lyrics), [PaxSenix](https://lyrics.paxsenix.org), [KuGou](https://www.kugou.com), and [Genius](https://genius.com).
- **Dynamic Color Palette**: Real-time Material color extraction from album covers.
- **Translucent Glass UI**: Frosted-glass chrome via GPU shader backdrops.
- **Responsive Layouts**: Desktop wide-screen view and compact floating navigation mode.

### Controls & System Tools
- **Global Keyboard Hotkeys**: Hotkey controls for Play/Pause, Seek, Volume, Search, and Settings.
- **System Tray Integration**: Minimize to tray with continuous background audio.
- **Custom Quality Ceilings**: Independent bitrate limits per network type.
- **Playback Speed Control**: 0.5x to 2.0x playback rate adjustment and silence skipping.
- **Sleep Timer**: Timed presets or automated shutdown at track end.
- **Bit-Perfect Exclusive Output**: Direct USB DAC output stream passthrough (credit: [Mornye](https://github.com/mornyeapp/Mornye)).
- **Technical Stats Dashboard**: Codec format, sample rate, bit depth, and bitrate telemetry.

    </td>
  </tr>
</table>

---

## Download

### Desktop (Linux & Windows)

<p>
  <a href="https://github.com/efzynx/BitChord-Desktop/releases" target="_blank" rel="noopener noreferrer">
    <img src="https://img.shields.io/badge/Linux-AppImage_%7C_DEB_%7C_RPM_%7C_Tar.gz-24292e?style=for-the-badge&logo=linux&logoColor=white" alt="Download for Linux" height="42" />
  </a>
  &nbsp;&nbsp;
  <a href="https://github.com/efzynx/BitChord-Desktop/releases" target="_blank" rel="noopener noreferrer">
    <img src="https://img.shields.io/badge/Windows-Portable_%7C_MSI_%7C_EXE-0078D6?style=for-the-badge&logo=windows&logoColor=white" alt="Download for Windows" height="42" />
  </a>
</p>

### Android & Android TV

<p>
  <a href="https://github.com/efzynx/BitChord-Desktop/releases" target="_blank" rel="noopener noreferrer">
    <img src="https://img.shields.io/badge/Android-APK_Releases-3DDC84?style=for-the-badge&logo=android&logoColor=white" alt="Download for Android" height="42" />
  </a>
</p>

Download prebuilt binary releases directly from the [**Releases**](https://github.com/efzynx/BitChord-Desktop/releases) page.  
For keyboard shortcuts, installation procedures, and building from source, consult the [**DESKTOP.md**](DESKTOP.md) guide.

---

## Keyboard Shortcuts

| Shortcut | Action |
| :--- | :--- |
| **`Space`** | Play / Pause |
| **`Media Play/Pause`** | System media toggle |
| **`Media Next / Prev`** | Next track / Previous track |
| **`Left Arrow` (`<-`)** | Seek backward 5 seconds |
| **`Right Arrow` (`->`)** | Seek forward 5 seconds |
| **`Up Arrow` (`^`)** | Volume up (+5%) |
| **`Down Arrow` (`v`)** | Volume down (-5%) |
| **`M`** | Mute / Unmute |
| **`Ctrl + F` / `Cmd + F`** | Focus search input field |
| **`Ctrl + ,` / `Cmd + ,`** | Open Settings dashboard |
| **`F11` / `F`** | Toggle Fullscreen mode |
| **`Esc`** | Dismiss Now Playing / Modals / Go back |

---

## Contributing

Contributions are welcome. Please read our [Contributing Guide](CONTRIBUTING.md) and [Code of Conduct](CODE_OF_CONDUCT.md) prior to submitting pull requests.

[**Contributing Guide**](CONTRIBUTING.md) &bull; [**Code of Conduct**](CODE_OF_CONDUCT.md) &bull; [**Maintainers**](MAINTAINERS.md) &bull; [**Desktop Docs**](DESKTOP.md) &bull; [**Additional Docs**](ADDITIONAL.md)

### Credits & Upstream Authors
- **Original BitChord Author**: [Kushagra Singh](https://github.com/kushagrasinghx) ([kushagrasinghx/BitChord](https://github.com/kushagrasinghx/BitChord))
- **Desktop Port & Maintenance**: [efzynx](https://github.com/efzynx) ([efzynx/BitChord-Desktop](https://github.com/efzynx/BitChord-Desktop))
- Upstream contributors and libraries acknowledged in this project.

<a href="https://github.com/kushagrasinghx/BitChord/graphs/contributors">
  <img src="https://raw.githubusercontent.com/kushagrasinghx/BitChord/contributors/contributors.svg" alt="Contributors" />
</a>

---

## Support

BitChord is free and open-source software. To support the original upstream creator:

<a href="https://ko-fi.com/kushagrasinghx" target="_blank" rel="noopener noreferrer"><img src="https://cdn.jsdelivr.net/gh/JMcrafter26/badges@main/src/assets/donate/kofi-singular-alt/cozy.svg" alt="Support on Ko-fi" height="50"/></a>
&nbsp;&nbsp;
<a href="https://paypal.me/kuxhagrasingh" target="_blank" rel="noopener noreferrer"><img src="https://cdn.jsdelivr.net/gh/JMcrafter26/badges@main/src/assets/donate/paypal-plural/cozy.svg" alt="Support on PayPal" height="50"/></a>

---

## Disclaimer & Legal Notice

BitChord is an independent third-party client. It is not affiliated with, endorsed by, or associated with Google LLC, YouTube, Deezer, or Telegram.

- **No Media Hosting**: BitChord does not host, upload, or store copyrighted audio content. It functions as an interface to index local storage or stream via public-facing APIs.
- **Fair Use**: This project is developed for educational and personal research purposes. Users are responsible for ensuring compliance with applicable regional laws and third-party terms of service.
- **License**: Distributed under the **GNU General Public License v3.0 (GPLv3)**. See [LICENSE](LICENSE) for details.

---

<div align="center">

&copy; 2026 BitChord Contributors. Licensed under GPL-3.0.

</div>
