# Contributing to BitChord Desktop

Thank you for contributing to BitChord Desktop. Follow these guidelines to ensure an efficient development and review process.

## Proposing Contributions
- Substantial features, refactors, or UI redesigns should normally be discussed before implementation. Open an issue on GitHub to outline your proposal.
- Straightforward bug fixes, documentation improvements, or minor corrections can be submitted directly as a Pull Request.

## Branch and Pull Request Workflow
Always check the repository for the current active version or development branch before starting work.

New work should normally be based on the latest active version or development branch rather than `main`, unless maintainers explicitly instruct otherwise.

When contributing:
- Fork the repository and create a descriptive branch (e.g., `fix/streaming-buffer`, `feat/new-mpris-property`).
- Keep pull requests focused on a single change or bug fix. Avoid mixing unrelated refactors, cosmetic tweaks, or mass reformatting with functional changes.
- Keep your branch reasonably synchronized with the target base branch.
- Provide a clear PR description explaining what was changed and why.
- Include relevant testing and validation details with your PR.
- Address review feedback promptly and keep discussions focused on technical merits.

## Development Setup
The project requires the following tools:
- **JDK**: Java Development Kit 17 or 21 (Eclipse Temurin or OpenJDK recommended).
- **Git**
- **C/C++ Tools (Optional)**: For native audio components under `desktopApp/native/` if modifying native JNI bindings.
- **Listen Together Backend (Optional)**: Go 1.22+ if developing or testing the party server (`backend/`).

## Build and Test Commands
Run Gradle commands from the repository root.

Run the desktop application:
```bash
./gradlew :desktopApp:run
```

Windows:
```powershell
.\gradlew.bat :desktopApp:run
```

Run test suite:
```bash
./gradlew :desktopApp:test
```

Windows:
```powershell
.\gradlew.bat :desktopApp:test
```

Build standalone distributable:
```bash
./gradlew :desktopApp:createDistributable
```

Package universal Linux AppImage:
```bash
./gradlew :desktopApp:packageAppImage
```

Package Windows Portable:
```bash
./gradlew :desktopApp:windowsPortable -Pbitchord.target=windows
```

## Code Quality
- Follow idiomatic Kotlin and Jetpack Compose conventions.
- Maintain the project's existing architecture and separation of concerns (UI, domain logic, playback services, and data repositories).
- Write unit tests for new logic, fixes, and edge cases.
- Contributors should run relevant tests and must not introduce regressions.
- Keep diffs focused and minimal. Avoid unnecessary external dependencies.

## Review Process
All pull requests require review and approval by repository maintainers before merging. Maintainers review changes for correctness, architecture fit, and maintainability.

## Dependencies and Licensing
BitChord Desktop is licensed under the **GNU General Public License v3.0 (GPLv3)**.

All contributed code and dependencies must be strictly compatible with GPLv3. Avoid introducing external dependencies unless strictly necessary; any new dependency must be evaluated for necessity, binary size, and license compliance.
