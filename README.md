<div align="center">

<br/>
<br/>

<img src="Logo.png" alt="BitChord app icon" width="200" />

# BitChord Desktop

### Aesthetic YouTube Music Client — Now for Desktop (Linux & Windows) & Mobile

<br/>

[![Latest release](https://img.shields.io/github/v/release/efzynx/BitChord-Desktop?style=for-the-badge&labelColor=0d1117)](https://github.com/efzynx/BitChord-Desktop/releases)
[![License](https://img.shields.io/github/license/efzynx/BitChord-Desktop?style=for-the-badge&labelColor=0d1117)](https://github.com/efzynx/BitChord-Desktop/blob/main/LICENSE)
[![Downloads](https://img.shields.io/github/downloads/efzynx/BitChord-Desktop/total?style=for-the-badge&labelColor=0d1117)](https://github.com/efzynx/BitChord-Desktop/releases)
[![Listening now](https://api.bitchord.kushagrasingh.in/api/stats/live/badge.svg)](https://api.bitchord.kushagrasingh.in/api/stats/live)

<br/>

[**Download**](#download) · [**Desktop Edition**](#about-desktop-edition) · [**Features**](#features) · [**Desktop Guide**](DESKTOP.md) · [**Contributing**](#contributing) · [**Disclaimer**](#disclaimer)

<br/>

<a href="https://fmhy.net/mobile#youtube-music" target="_blank" rel="noopener noreferrer"><img src="https://cdn.jsdelivr.net/gh/JMcrafter26/badges@main/src/assets/available/fmhy/cozy.svg" alt="Featured on FMHY" height="55"/></a>
<a href="https://trendshift.io/repositories/177639?utm_source=repository-badge&amp;utm_medium=badge&amp;utm_campaign=badge-repository-177639" target="_blank" rel="noopener noreferrer"><img src="https://trendshift.io/api/badge/repositories/177639" alt="kushagrasinghx%2FBitChord | Trendshift" width="250" height="55"/></a>

</div>

> [!IMPORTANT]
> BitChord is an independent open-source player and is not affiliated with, endorsed by, or connected to YouTube or Google in any way. Use it at your own discretion.

---

<div align="center">

<img src="Banner.png" alt="BitChord banner" width="100%" />

</div>

## 🖥️ About BitChord Desktop Edition

Originally created by [Kushagra Singh](https://github.com/kushagrasinghx) as an Android / Android TV application, BitChord's early desktop prototype was previously removed upstream.

**This project ([efzynx/BitChord-Desktop](https://github.com/efzynx/BitChord-Desktop)) is a dedicated fork that revives, reconstructs, modernizes, and actively continues the native Desktop edition for Linux and Windows.**

Built using **Compose Multiplatform (Jetpack Compose on Desktop JVM)** and a high-performance **FFmpeg C++/JavaCPP audio pipeline**, BitChord Desktop brings the full aesthetic frosted-glass player experience directly to your PC:

- 🐧 **Linux First-Class Citizen**:
  - Full **MPRIS D-Bus** integration (GNOME Media, KDE Plasma Media Player, `playerctl`, and multimedia keys).
  - FreeDesktop standard desktop entry (`bitchord.desktop`) with quick launcher actions and high-resolution icons.
  - One-click local installation script (`./scripts/install-desktop.sh`) and multi-format packaging (**AppImage**, **DEB**, **RPM**, **Tar.gz**).
- 🪟 **Native Windows Experience**:
  - System Media Transport Controls (**SMTC**) integration in taskbar and lockscreen.
  - Windows Portable package with a silent VBS launcher (`BitChord.vbs`) running cleanly without terminal windows.
  - Windows Installer packages (**MSI** and **EXE**).
- 🎮 **Native Discord Rich Presence (RPC)**:
  - Direct local IPC socket connection (**Unix Domain Sockets** on Linux, **Named Pipes** on Windows).
  - No bot tokens, no logins, and zero privacy concerns — just plug-and-play Discord activity status.
- ⚡ **High-Fidelity Audio & Streaming Engine**:
  - **FFmpeg 7.1 (JavaCPP)** decoding supporting Hi-Res Lossless (FLAC, ALAC, WAV) and standard streams (Opus, AAC).
  - **Headless JavaFX WebKit PoToken Generator** ensuring bot-free YouTube Music stream resolution.
  - **Windowed Range Streaming** preventing HTTP 403 Forbidden throttling on Google Video media endpoints.
- 🎤 **Apple-like Word-by-Word Synced Lyrics**:
  - Continuous per-frame clock on desktop JVM for ultra-smooth syllable animations.
  - Multi-source provider fallback (LRCLib, Musixmatch, YouTube Captions, KuGou, etc.) plus local embedded tag reading.
- 🔄 **In-App Updater & System Diagnostics**:
  - Dedicated **Pembaruan & Info Sistem** settings page.
  - Auto-detects OS distro, CPU architecture, Java runtime, and installation type (AppImage, DEB, RPM, Portable, MSI).
  - Direct release check against `efzynx/BitChord-Desktop`.
- 📐 **Adaptive Desktop Layout**:
  - Full wide layout with collapsible sidebar and compact mode (`maxWidth < 980dp`) with floating bottom chrome and auto-revealing search bar.
  - Global keyboard shortcuts (`Space` play/pause, seek, volume, mute `M`, `Ctrl+F` search, `Ctrl+,` settings, `F11`/`F` fullscreen).

---

<div align="center">

<h1><a id="features"></a>Features</h1>

<table>
  <tr>
    <td width="50%" valign="top">

#### Playback
- **Search, browse and play** anything available on YouTube Music.
- **Hi-Res lossless audio** — FLAC/ALAC from a configured module source, with YouTube Music as fallback.
- **Gapless playback with true crossfade**, adjustable 0–12s.
- **Automix [Beta]** — DJ-style transitions with beat-matching and tempo-stretching.
- **Offline downloads** — save tracks with embedded metadata.
- **Local music library** integration with automatic directory watching.
- **Background playback** via a proper OS media session (MPRIS on Linux, SMTC on Windows).
- **Apple-like lyrics animation** — credit to [binimum](https://github.com/binimum/am-lyrics).

#### Connectivity & Accounts
- **Sign in with your Google account** for personalized content.
- **Spotify integration** — connect your account to play your playlists and Liked Songs.
- **Discord Rich Presence** — native desktop IPC socket without account tokens.
- **Scrobbling** to Last.fm and ListenBrainz.
- **Pluggable sources** — add, edit, test and health-check module sources.

    </td>
    <td width="50%" valign="top">

#### Experience
- **Animated album canvas** — motion artwork on the now-playing screen.
- **Word-synced lyrics** — word/syllable-level highlighting from multiple sources.
- **Lyrics providers** — credit to [lrc.red](https://lrc.red), [BiniLyrics](https://github.com/binimum), [BetterLyrics](https://github.com/better-lyrics/better-lyrics), [PaxSenix](https://lyrics.paxsenix.org), [LyricsPlus](https://github.com/ibratabian17/YouLyPlus), [SimpMusic](https://github.com/maxrave-dev/SimpMusic), [Unison](https://unison.boidu.dev), [Megalobiz](https://www.megalobiz.com), [KuGou](https://www.kugou.com), [LRCLIB](https://lrclib.net), [Musixmatch](https://www.musixmatch.com) and [Genius](https://genius.com).
- **Dynamic, artwork-driven theming** — Material palette extracted from album art.
- **Frosted-glass UI** — translucent bars via Haze, Material 3 theming.

#### Controls & Tweaks
- **Desktop Keyboard Shortcuts** — full hotkey control for play/pause, seek, volume, search, and settings.
- **System Tray support** — minimize to tray with background audio playback.
- **Per-network audio quality** — separate quality ceilings.
- **Playback speed control** (0.5×–2.0×) and **skip silence**.
- **Sleep timer** — fixed presets or "stop after this track".
- **System equalizer** integration.
- **Bit-perfect USB exclusive output** — thanks to [Mornye](https://github.com/mornyeapp/Mornye) for the reference.
- **Stats for nerds** — codec, bit depth, sample rate, and more on the now-playing screen.

    </td>
  </tr>
</table>

</div>

---

<div align="center">

<h1><a id="download"></a>Download</h1>

### 💻 Desktop (Linux & Windows)
<p>
  <a href="https://github.com/efzynx/BitChord-Desktop/releases" target="_blank" rel="noopener noreferrer">
    <img src="https://img.shields.io/badge/Linux-AppImage%20%7C%20DEB%20%7C%20RPM%20%7C%20Tar.gz-FCC624?style=for-the-badge&logo=linux&logoColor=black" alt="Download for Linux" height="42" />
  </a>
  &nbsp;&nbsp;
  <a href="https://github.com/efzynx/BitChord-Desktop/releases" target="_blank" rel="noopener noreferrer">
    <img src="https://img.shields.io/badge/Windows-Portable%20%7C%20MSI%20%7C%20EXE-0078D6?style=for-the-badge&logo=windows&logoColor=white" alt="Download for Windows" height="42" />
  </a>
</p>

### 📱 Android & Android TV
<p>
  <a href="https://github.com/efzynx/BitChord-Desktop/releases" target="_blank" rel="noopener noreferrer">
    <img src="https://cdn.jsdelivr.net/gh/JMcrafter26/badges@main/src/assets/supported/android/cozy.svg" alt="Download for Android" height="48" />
  </a>
</p>

Grab the latest releases from the [**Releases**](https://github.com/efzynx/BitChord-Desktop/releases) page.  
For comprehensive keyboard shortcuts, installation guides, and building from source, see [**DESKTOP.md**](DESKTOP.md).

</div>

---

<div align="center">

<h1><a id="contributing"></a>Contributing</h1>

We welcome contributions to BitChord! Please review our [Contributing Guide](CONTRIBUTING.md) and [Code of Conduct](CODE_OF_CONDUCT.md) before submitting a Pull Request.

[**Contributing Guide**](CONTRIBUTING.md) · [**Code of Conduct**](CODE_OF_CONDUCT.md) · [**Maintainers**](MAINTAINERS.md) · [**Desktop Docs**](DESKTOP.md) · [**Additional Docs**](ADDITIONAL.md)

### Credits & Upstream Authors ❤
- **Original BitChord Creator**: [Kushagra Singh](https://github.com/kushagrasinghx) ([kushagrasinghx/BitChord](https://github.com/kushagrasinghx/BitChord))
- **Desktop Port & Maintenance**: [efzynx](https://github.com/efzynx) ([efzynx/BitChord-Desktop](https://github.com/efzynx/BitChord-Desktop))
- All upstream contributors and open-source projects cited in this repository.

<a href="https://github.com/kushagrasinghx/BitChord/graphs/contributors">
  <img src="https://raw.githubusercontent.com/kushagrasinghx/BitChord/contributors/contributors.svg" />
</a>

</div>

---

<div align="center">

<h1><a id="support"></a>Support</h1>

BitChord is free and always will be — if it's earned a spot in your rotation, you can chip in to the original project here:

<a href="https://ko-fi.com/kushagrasinghx" target="_blank" rel="noopener noreferrer"><img src="https://cdn.jsdelivr.net/gh/JMcrafter26/badges@main/src/assets/donate/kofi-singular-alt/cozy.svg" alt="Support me on Ko-fi" height="55"/></a>
<a href="https://paypal.me/kuxhagrasingh" target="_blank" rel="noopener noreferrer"><img src="https://cdn.jsdelivr.net/gh/JMcrafter26/badges@main/src/assets/donate/paypal-plural/cozy.svg" alt="Support us on PayPal" height="55"/></a>

<br/>
<img src="upi_support.jpg" alt="UPI Support" width="250" />

</div>

---

<div align="center">

<h1><a id="disclaimer"></a>Disclaimer & Legal Notice</h1>

BitChord is an independent, community-driven third-party audio player and client. It is **not** associated with Google LLC, YouTube Music, Deezer, Telegram, or any of their parent companies.

* **No Media Hosting:** BitChord does not host, upload, or store copyrighted music files. It operates strictly as an interface to scan local device storage or stream media directly from public, public-facing, or user-authenticated APIs.
* **Fair Use & API Usage:** This software is created solely for personal research, educational, and fair-use purposes. The user is entirely responsible for ensuring their usage aligns with their local copyright laws and YouTube Terms of Service.
* **No Ad-Blocking Guarantee:** While BitChord focuses on providing a clean listening environment, it does not guarantee permanent bypasses or modifications to commercial third-party platform conditions.
* **Copyleft:** BitChord is free software under the GPLv3. The license does not let anyone forbid others from selling or redistributing copies, but any distribution must come with the Corresponding Source under the same license.

</div>

---

<div align="center">

<h1><a id="license"></a>License</h1>

This project is licensed under the **GNU General Public License v3.0 (GPLv3)**. See the [LICENSE](LICENSE) file for details.

</div>
