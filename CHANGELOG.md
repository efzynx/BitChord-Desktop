# Changelog

All notable changes to **BitChord Desktop** will be documented in this file.

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
