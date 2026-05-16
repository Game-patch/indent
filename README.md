# Indentation Checker Plugin

A minimal JetBrains IDE plugin that auto-checks indentation across all languages by registering a generic inspection.

## Features

- Inspects all open files in the editor
- Detects mixed tabs and spaces in leading indentation
- Warns when indentation uses a different style than the file's preferred indentation pattern
- Automatically detects the preferred indentation style using majority-based analysis
- Provides in-editor quick fixes to normalize indentation to spaces or tabs
- Offers an auto-fix action to normalize indentation across the whole file
- Highlights indentation with color-coded whitespace markers for spaces and tabs
- Adds stability improvements with cached file reads and safer file handling

## Build

1. Open this folder in IntelliJ IDEA.
2. Run the `runPlugin` Gradle task.
3. Install the plugin in the sandbox IDE.

### Jar Packaging

Run the Gradle jar task to build a standalone JAR artifact:

```bash
./gradlew jar
```

Or use the package helper task:

```bash
./gradlew packageJar
```

You can also build a dedicated plugin jar artifact directly:

```bash
./gradlew pluginJar
```

The resulting JAR is located in `build/libs/indentation-checker-0.4.0.jar`.

## Python Indentation Checker

The Python checker is now packaged as `lstest` and also remains runnable via the legacy script.

Install locally using:

```bash
pip install -e .
```

Run as a package:

```bash
python -m lstest path/to/file.py
python -m lstest path/to/project/
```

Or use the console script after installation:

```bash
lstest path/to/file.py
lstest path/to/project/
```

Options:

- `--tab-width`: validate space indentation as a multiple of this width (default: 4)
- `--quiet`: suppress individual issue output
- `--fix`: normalize leading indentation in-place for Python files
- `--style`: target indentation style for `--fix` (`spaces` or `tabs`)
- `--dry-run`: preview changes without modifying files
- `--exclude`: skip files or directories matching glob patterns

## Changelog

See `CHANGELOG.md` for release notes and stability improvements.
