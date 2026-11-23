# Agent Guidelines for BitChord Desktop

This document defines the architecture principles, workflow conventions, and strict rules for AI agents operating in the **BitChord Desktop** repository. Every agent working in this project must adhere to these directives.

---

## 1. Project Identity & Scope

* **Repository:** `efzynx/BitChord-Desktop`
* **Maintainer:** efzynx ([@efzynx](https://github.com/efzynx))
* **Nature:** Independent, community-driven desktop port of BitChord by Kushagra Singh.
* **Core Technology:** Kotlin Multiplatform (KMP), Compose Multiplatform for Desktop (Skiko/JVM), Kotlin Coroutines, StateFlow.
* **Target Platforms:** Linux (AppImage, DEB, RPM, local install), Windows (Portable ZIP, Installer EXE/MSI), and macOS.
* **Scope Focus:** **Desktop only** (`desktopApp` module, `sharedUi`, and `shared`). Mobile (Android) and TV targets/workflows are intentionally omitted to maximize desktop performance, dedicated packaging, and focused maintenance.
* **License:** GNU General Public License v3.0 (GPLv3). All contributions, modifications, and dependencies must strictly comply with GPLv3. Attribution to upstream creator Kushagra Singh must always be preserved.
* **Support / Donations:** Pointed exclusively to the maintainer's personal link (`https://ko-fi.com/efzyn`) alongside credit to the base repository.

---

## 2. User Communication Rules

When interacting with the repository maintainer (`efzyn`):
1. **Response Language:** Always use **Bahasa Indonesia** for step-by-step guides, implementation plans, explanations, task updates, and chat interactions.
2. **Repository Documentation Language:** All in-repo markdown files (`README.md`, `DESKTOP.md`, `CONTRIBUTING.md`, `CHANGELOG.md`, `AGENTS.md`) must be written in **English**.
3. **No Emojis or Emoticons:** Under no circumstances should emojis or emoticons be included in conversational replies, commit messages, or technical documentation.
4. **Tone & Style:** Concise, direct, technical, and objective. Avoid verbose storytelling or flowery narration.
5. **Clickable File Links:** Always format referenced file paths as clickable Markdown links with the file scheme and basename: `[basename](file:///absolute/path)`.

---

## 3. Git & Asset Management Rules

### 3.1. Zero Binary Bloat in Code Branches (The `assets` Branch Rule)
* **NEVER commit binary media files** (screenshots, mockup PNGs, promotional banners, app icons, badges) directly into the `main` branch or feature branches (`feat/*`, `fix/*`).
* All media assets must be stored exclusively on the dedicated orphan branch: **`assets`**.
* Images referenced in `README.md` and documentation must use raw GitHub URLs pointing to the `assets` branch:
  ```markdown
  https://raw.githubusercontent.com/efzynx/BitChord-Desktop/assets/path/to/image.png
  ```
* Media files must be compressed (e.g., WebP or optimized PNG) before uploading to the `assets` branch.

### 3.2. Branching & PR Workflows
* **Target Remote:** Pull Requests must always target `efzynx/BitChord-Desktop` (`main`). Never submit or redirect pull requests to the upstream repository (`kushagrasinghx/BitChord`).
* **Branch Strategy:**
  * `main`: Protected production branch.
  * `feat/**` or `fix/**`: Feature or update branches (e.g., `feat/desktop-v1.9.2`).
  * `assets`: Dedicated orphan branch for media assets.
* **Commit Messages:** Follow Conventional Commits (`feat(desktop): ...`, `fix(desktop): ...`, `docs: ...`, `chore: ...`).

### 3.3. Phased Execution & Verification
* Do not skip phases or prematurely bump versions without user testing and approval.
* Confirm features locally before tagging or opening releases.

---

## 4. Localization & i18n Rules (Critical CI Invariant)

BitChord Desktop uses XML-based string catalogues located under:
`desktopApp/src/main/resources/strings/`

### The `DesktopStringsTest` Rule
`DesktopStringsTest.kt` enforces that **every single string key defined in `desktop-en.xml` must also be translated and present in every other supported language catalogue**:
* Supported languages: `de`, `es`, `fr`, `he`, `hi`, `id`, `it`, `ja`, `pl`, `pt`, `ru`, `tr`, `zh-Hans`, `zh-Hant`, and `zh`.
* Whenever a new string key is added:
  1. Add the key and English text to `desktop-en.xml`.
  2. Add the Indonesian translation to `desktop-id.xml`.
  3. **Immediately add accurate translations for that key into all other 12+ language XML files.**
  4. Ensure XML validity and exact format specifiers (`%s`, `%d`, `%1$s`, etc.).
  5. Validate locally before pushing:
     ```bash
     ./gradlew :desktopApp:test --tests "com.music.bitchord.desktop.DesktopStringsTest"
     ```
  6. **Never push new keys to only `en` and `id`**, as this will immediately break the GitHub Actions CI pipeline.

---

## 5. UI/UX & Architecture Standards

### 5.1. Apple Music-Inspired Player Bar
* **Interactive Blurred Scrubber:** Bottom bar timeline with hover-activated time preview, smooth seek scrubbing, and frosted-glass blur background.
* **Auto-Scrolling Marquee:** Long song titles, artists, and album names must smoothly marquee-scroll horizontally when overflowing in compact window widths.
* **Multi-Artist Chips:** Clickable links for individual artists on collaborating tracks.
* **State Synchronization:** Play/pause states on collection headers (Album, Playlist) and Artist views must dynamically reflect active playback.

### 5.2. Liquid Frosted Glass Aesthetic
* Unified frosted-glass blur (`Skia` / backdrop blur with specular highlights) across lyrics panels, side drawer, contextual popups, and tooltips.
* Support both Dark and Light themes cleanly.
* Maintain performance: optimize recompositions and avoid continuous canvas repaints when static.

### 5.3. Audio Pipeline & Automix
* Audio playback handled via `DesktopAudioPipeline`.
* Beat tracking and feature extraction utilize Aubio JNI bindings and ONNX Runtime machine learning models (`BeatTracker`).
* Background track analysis must yield thread priority (`Thread.MIN_PRIORITY`) during active playback to prevent audio stuttering.

### 5.4. Cloudflare Quick Tunnel (Party Server)
* Embedded support for `cloudflared` CLI to expose the local party server over `trycloudflare.com` without port forwarding.
* System detection check in-app with copyable install commands for Linux and Windows.
* Real-time server telemetry and latency measurement (`%d ms`).

---

## 6. Pre-Push Verification Checklist

Before pushing any commit or opening a Pull Request, an agent must execute:

1. **Compile Verification:**
   ```bash
   ./gradlew :desktopApp:compileKotlin
   ```
2. **String Catalogue Verification:**
   ```bash
   ./gradlew :desktopApp:test --tests "com.music.bitchord.desktop.DesktopStringsTest"
   ```
3. **Working Tree Cleanliness:**
   ```bash
   git status
   ```
   Ensure no temporary test files, audio dumps, mockups, or untracked binary files remain in the working tree.
4. **CI Watch:**
   After pushing, monitor GitHub Actions CI using:
   ```bash
   gh pr checks <PR_NUMBER> --watch
   ```
   Do not report a task as complete if CI checks fail.

---

## 7. Platform Packaging & Installer Reference

* **Universal Linux AppImage:**
  ```bash
  ./gradlew :desktopApp:packageAppImage
  ```
* **Debian Package (.deb):**
  ```bash
  ./gradlew :desktopApp:packageDeb
  ```
* **RedHat Package (.rpm):**
  ```bash
  ./gradlew :desktopApp:packageRpm
  ```
* **Windows Portable (.zip):**
  ```bash
  ./gradlew :desktopApp:windowsPortable -Pbitchord.target=windows
  ```
* **Local Linux Install / Uninstall:**
  * Install script: `scripts/install-desktop.sh`
  * Uninstall script: `scripts/uninstall-desktop.sh` or removal of `~/.local/share/bitchord`.
