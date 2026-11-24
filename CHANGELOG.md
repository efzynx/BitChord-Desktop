# Changelog

All notable changes to **BitChord Desktop** will be documented in this file.

---

## [1.10.0] - 2026-11-24

BitChord Desktop v1.10.0 is a major milestone release that synchronizes with upstream BitChord 1.10, introduces a redesigned Listen Together party system with zero-setup Quick Party mode, animated artist banners, Best Part scrubber highlights, and enhanced playlist management alongside significant stability and performance optimizations.

### What's New & Highlights

#### Upstream BitChord 1.10 Synchronization
- **AutoMix & Canvas Transitions**: Seamless track blending with organic artwork transitions, Spotify Canvas handling, mixing badges, and nested settings controls.
- **Enhanced Queue & Playlists**: Queue multi-select mode with bulk playlist actions, swipe gestures, undo snackbars, and automatic upcoming songs view.
- **Timed Lyrics Synchronization**: Real-time line and syllable synchronization from YouTube Music, LRCLIB, and Musixmatch with extended glow feathering.
- **Library & Download Improvements**: Isolated artwork per local audio track, auto-download for liked tracks, and audio fade-out for sleep timers.

#### Listen Together & Party Mode Overhaul
- **Zero-Setup Quick Party**: Instant one-click party room creation and joining powered by a dedicated high-performance VPS (`party.bitchord.my.id`), eliminating manual server configuration.
- **Custom Party & Cloudflare Quick Tunnel**: Flexible support for self-hosted VPS and local one-click `cloudflared` tunnel hosting without port forwarding.
- **Integrated Server Distribution**: Bundled `party-server` binaries into Linux AppImage and Windows distributions with automated local loopback resolution.

#### Interface, Visuals & Desktop Polish
- **Animated Artist Banners**: High-definition HLS video banners for artist profile headers with user opt-in toggle in settings.
- **Best Part Scrubber Highlights**: Visual markers for popular song segments directly integrated into the liquid glass scrubber timeline.
- **AutoMix Artwork Controls**: Fine-grained settings for mini-player art transitions and background canvas blending.
- **Desktop Branding**: Unified all application surfaces with clean BitChord Desktop identifiers.
- **System Tray Polish**: Refined tray menu typography and clean system labels without legacy icon glyphs.

#### Playlist Management & Desktop Controls
- **Full Playlist Actions**: Direct renaming, deletion, and editing for both local and account playlists from the sidebar context menu.
- **Shortcut Protection**: Keyboard shortcuts now gracefully yield during active search field text input to prevent accidental playback triggering.

#### Performance, Packaging & Infrastructure
- **Universal Linux AppImage Task**: Added native `:desktopApp:packageAppImage` Gradle build task.
- **Windows Blur Stability**: Pinned Windows Haze dependency to 1.7.3 to prevent runtime `NoSuchMethodError` crashes.
- **Memory & CPU Optimizations**: Reduced fullscreen lyrics rendering overhead and background resource footprint.
- **Static Landing Portal**: Launched official GitHub Pages portal with liquid glass styling, ambient running glow, language selector (EN/ID), and live release download tracking.

---

## [1.9.2] - 2026-11-20

BitChord Desktop v1.9.2 is a major stable release bringing a refined Apple Music-inspired player experience, native Listen Together party hosting via Cloudflare Quick Tunnel, comprehensive liquid-glass visual enhancements, and full multi-language localization.

### What's New & Highlights

#### Apple Music-Inspired Player Bar & Navigation
- **Interactive Blurred Scrubber**: Refined bottom bar timeline with hover-activated time display, soft frosted blur backdrop, and precise seek scrubbing.
- **Auto-Scrolling Marquee**: Smooth horizontal marquee scroll for overflowing song titles and artist/album credits when the window is compact.
- **Multi-Artist & Album Jump**: Independent clickable links for each artist on multi-artist tracks, plus direct jump to the active album.
- **Dynamic Play/Pause Synchronization**: Collection headers (Albums, Playlists) and Artist view play buttons now accurately reflect active playback status with instant toggle.
- **Maximized Player Polish**: Restored full shuffle, repeat, and queue controls with smooth backdrop haze sampling.

#### Listen Together & Cloudflare Quick Tunnel
- **Instant Public Party Hosting**: Integrated Cloudflare Quick Tunnel (`trycloudflare.com`) allows hosting local party rooms without manual port forwarding or static IPs.
- **System Checker**: In-app detection of the `cloudflared` CLI with one-click installation copy commands for Windows (PowerShell/CMD) and Linux (Terminal).
- **Server Telemetry**: Real-time status reporting and latency monitoring for both built-in and custom party servers.

#### Interface Polish & Liquid Frosted Glass
- **Elevated Liquid Glass**: Consistent frosted-glass blur with specular highlights across lyrics, queue side panels, song context menus, and tooltips.
- **Smart Search Input**: Instant clear button (`x`) in the sidebar search field to quickly reset search queries.
- **Sidebar & Rail Mode**: Improved back-navigation, inward dropdown positioning, and sidebar rail mode tooltips.

#### System, Localization & Infrastructure
- **Dynamic OS Detection**: Added Windows 11 build detection via NT kernel build queries, avoiding legacy fallback to Windows 10.
- **Multi-Language Localization**: Full dynamic internationalization support (English and Indonesian) across all new features, error dialogs, system diagnostics, and uninstaller hints.
- **Brand & Documentation Refresh**: Unified project branding as **BitChord Desktop**, updated documentation ([DESKTOP.md](DESKTOP.md), [CONTRIBUTING.md](CONTRIBUTING.md)), and established an isolated `assets` branch for media files to keep repository clone sizes ultra-lightweight.

---

For detailed installation guides and shortcuts, visit the [BitChord Desktop Guide](DESKTOP.md).
