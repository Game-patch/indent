# Changelog

## [0.4.0] - 2026-05-16

### Added

- Refined indentation style detection to choose the preferred style by majority of indented lines.
- Improved file-wide auto-fix reliability with full file normalization for detected styles.
- Auto-fix indentation action for the JetBrains plugin to normalize indentation across the entire file.
- Converted the Python package to `lstest` with a new console script entrypoint.
- `python_indentation_checker.py` now runs `lstest` package code and remains compatible as a legacy launcher.
- Added JAR packaging support for the Gradle project via `./gradlew jar`.
 - Added dedicated JetBrains plugin jar build task `./gradlew pluginJar`.
- Added `--style` to choose `spaces` or `tabs` during fix operations.
- Added `--dry-run` to preview proposed changes without writing files.
- Added `--exclude` glob patterns to ignore files or directories.
- Added in-editor quick-fix support for the JetBrains plugin to normalize inconsistent indentation directly from the editor.
- Added visual indentation color highlighting for leading spaces and tabs in the editor.
- Added file-read caching and stability improvements for repeated Python file analysis.

### Fixed

- Improved file reading stability with fallback encoding handling.
- Skips `__pycache__` directories automatically during directory scans.
- Normalizes indentation while preserving line endings.
- Reports unreadable or unsupported files instead of failing silently.

## [0.1.0] - 2026-05-16

- Initial JetBrains IDE indentation checker plugin scaffold.
- Simple Python indentation checker for detecting mixed tabs/spaces and width mismatches.
