#!/usr/bin/env python3
import argparse
import fnmatch
import os
import re
import sys
from dataclasses import dataclass
from pathlib import Path
from typing import Iterable, List, Optional, Sequence

@dataclass
class IndentationIssue:
    path: Path
    line: int
    column: int
    message: str


def leading_whitespace(text: str) -> str:
    return re.match(r"^[ \t]*", text).group(0)


_FILE_CONTENT_CACHE: dict[Path, Optional[str]] = {}


def _read_text_uncached(path: Path) -> Optional[str]:
    try:
        return path.read_text(encoding="utf-8")
    except UnicodeDecodeError:
        try:
            return path.read_text(encoding="utf-8", errors="replace")
        except OSError:
            return None
    except OSError:
        return None


def safe_read_text(path: Path) -> Optional[str]:
    normalized_path = path.resolve()
    if normalized_path in _FILE_CONTENT_CACHE:
        return _FILE_CONTENT_CACHE[normalized_path]

    text = _read_text_uncached(normalized_path)
    _FILE_CONTENT_CACHE[normalized_path] = text
    return text


def clear_file_cache(path: Path) -> None:
    _FILE_CONTENT_CACHE.pop(path.resolve(), None)


def clear_all_file_cache() -> None:
    _FILE_CONTENT_CACHE.clear()


def detect_indent_style(lines: Iterable[str]) -> Optional[str]:
    for line in lines:
        stripped = line.lstrip(" \t")
        if not stripped or stripped.startswith("#"):
            continue
        indent = leading_whitespace(line)
        if indent:
            if " " in indent and "\t" in indent:
                return "mixed"
            if "\t" in indent:
                return "tabs"
            if " " in indent:
                return "spaces"
    return None


def check_python_file(path: Path, tab_width: int = 4) -> List[IndentationIssue]:
    issues: List[IndentationIssue] = []
    text = safe_read_text(path)
    if text is None:
        issues.append(IndentationIssue(path, 0, 0, "Could not read file or unsupported encoding."))
        return issues

    lines = text.splitlines()
    style = detect_indent_style(lines)

    for index, line in enumerate(lines, start=1):
        if not line.strip():
            continue

        indent = leading_whitespace(line)
        if not indent:
            continue

        if " " in indent and "\t" in indent:
            issues.append(
                IndentationIssue(path, index, 1, "Mixed tabs and spaces in indentation.")
            )
            continue

        if style == "spaces" and "\t" in indent:
            issues.append(
                IndentationIssue(path, index, 1, "Tab indentation found, but spaces appear to be the file style.")
            )

        if style == "tabs" and " " in indent:
            issues.append(
                IndentationIssue(path, index, 1, "Space indentation found, but tabs appear to be the file style.")
            )

        if style != "tabs" and " " in indent:
            if len(indent) % tab_width != 0:
                issues.append(
                    IndentationIssue(
                        path,
                        index,
                        len(indent),
                        f"Indentation width {len(indent)} is not a multiple of {tab_width} spaces.",
                    )
                )

    return issues


def normalize_indentation(line: str, target_style: str, tab_width: int) -> str:
    indent = leading_whitespace(line)
    if not indent:
        return line

    if target_style == "spaces":
        normalized = indent.replace("\t", " " * tab_width)
        return normalized + line[len(indent) :]

    if target_style == "tabs":
        spaces = indent.count(" ")
        tabs = indent.count("\t")
        converted = "\t" * (spaces // tab_width) + " " * (spaces % tab_width) + "\t" * tabs
        return converted + line[len(indent) :]

    return line


def should_exclude(path: Path, exclude_patterns: Sequence[str]) -> bool:
    normalized = str(path).replace("\\", "/")
    return any(fnmatch.fnmatch(normalized, pattern.replace("\\", "/")) for pattern in exclude_patterns)


def find_python_files(paths: Iterable[Path], exclude_patterns: Sequence[str]) -> Iterable[Path]:
    for path in paths:
        if path.is_dir():
            for root, dirs, files in os.walk(path):
                dirs[:] = [d for d in dirs if d != "__pycache__"]
                for name in files:
                    if name.endswith(".py"):
                        candidate = Path(root) / name
                        if not should_exclude(candidate, exclude_patterns):
                            yield candidate
        elif path.is_file() and path.suffix == ".py":
            if not should_exclude(path, exclude_patterns):
                yield path


def print_issue(issue: IndentationIssue) -> None:
    print(f"{issue.path}:{issue.line}:{issue.column}: {issue.message}")


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Check Python files for indentation issues: mixed tabs/spaces and inconsistent widths."
    )
    parser.add_argument(
        "paths",
        nargs="+",
        help="File or directory paths to check.",
    )
    parser.add_argument(
        "--tab-width",
        type=int,
        default=4,
        help="Space width to validate for indentation (default: 4).",
    )
    parser.add_argument(
        "--quiet",
        action="store_true",
        help="Show only summary information without individual issue details.",
    )
    parser.add_argument(
        "--fix",
        action="store_true",
        help="Normalize leading indentation in place for Python files.",
    )
    parser.add_argument(
        "--style",
        choices=("spaces", "tabs"),
        help="Target indentation style for --fix. If omitted, uses the file's detected style or spaces.",
    )
    parser.add_argument(
        "--dry-run",
        action="store_true",
        help="Print what would change without modifying any files.",
    )
    parser.add_argument(
        "--exclude",
        action="append",
        default=[],
        help="Exclude files or directories matching glob patterns.",
    )
    return parser.parse_args()


def normalize_file(path: Path, style: str, tab_width: int, dry_run: bool) -> bool:
    text = safe_read_text(path)
    if text is None:
        print(f"Skipping {path}: unreadable file or unsupported encoding.")
        return False

    lines = text.splitlines(keepends=True)
    normalized = [normalize_indentation(line, style, tab_width) for line in lines]
    new_text = "".join(normalized)

    if new_text != text:
        if dry_run:
            print(f"Would normalize indentation in: {path}")
            return True
        path.write_text(new_text, encoding="utf-8")
        _FILE_CONTENT_CACHE[path.resolve()] = new_text
        print(f"Rewrote indentation in: {path}")
        return True

    return False


def main() -> int:
    args = parse_args()
    paths = [Path(p) for p in args.paths]
    issues: List[IndentationIssue] = []
    fixed_count = 0

    for python_file in find_python_files(paths, args.exclude):
        issues.extend(check_python_file(python_file, tab_width=args.tab_width))

    if args.fix:
        for python_file in find_python_files(paths, args.exclude):
            style = args.style
            text = safe_read_text(python_file)
            if text is None:
                continue
            if style is None:
                detected = detect_indent_style(text.splitlines())
                style = detected if detected in ("spaces", "tabs") else "spaces"
            if normalize_file(python_file, style, args.tab_width, args.dry_run):
                fixed_count += 1
        clear_all_file_cache()

    if not args.quiet:
        for issue in issues:
            print_issue(issue)

    if fixed_count and not args.quiet:
        print(f"\nNormalized indentation in {fixed_count} file(s).")

    if issues:
        if not args.quiet:
            print(f"\nFound {len(issues)} indentation issue(s).")
        return 1

    if fixed_count:
        return 0

    if not args.quiet:
        print("No indentation issues found.")
    return 0


if __name__ == "__main__":
    from lstest import main
    raise SystemExit(main())
