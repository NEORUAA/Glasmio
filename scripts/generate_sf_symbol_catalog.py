#!/usr/bin/env python3
"""Generate the complete official SF Symbols address catalog, retaining host aliases."""

from __future__ import annotations

import argparse
import concurrent.futures
import json
import os
from pathlib import Path
import re
import subprocess


REPOSITORY = Path(__file__).resolve().parents[1]
DEFAULT_CATALOG = REPOSITORY / (
    "glasmio-icons/src/main/java/io/github/neoruaa/glasmio/icons/SfSymbolCatalog.kt"
)
DEFAULT_CLI = Path("/Applications/SF Symbols Beta.app/Contents/Executables/sfsymbols")
EXCLUDED_DIRECTORIES = {"build", ".gradle", ".git", ".idea", ".local-research", ".venv", "venv"}
SYMBOL_ALIASES = {"mic": "microphone"}
STRING_LITERAL = re.compile(r'"([a-z0-9][a-z0-9.]*)"')
ICON_CALL = re.compile(
    r'\b(?:SfIcon|GlasmioIcon)\s*\(\s*(?:systemName\s*=\s*)?"([a-z0-9][a-z0-9.]*)"'
)
CATALOG_ENTRY = re.compile(
    r'"([a-z0-9][a-z0-9.]*)"\s*(?:to\s*|]\s*=\s*)0x([0-9A-Fa-f]+)'
)
ALIAS_ENTRY = re.compile(r'"([a-z0-9][a-z0-9.]*)"\s*=\s*"([a-z0-9][a-z0-9.]*)";')
CHUNK_SIZE = 256
ENUM_ENTRY = re.compile(
    r'^\s*[A-Za-z][A-Za-z0-9_]*\(\s*"([a-z0-9][a-z0-9.]*)"\s*,\s*0x([0-9A-Fa-f]+)',
    re.MULTILINE,
)


def read_entries(path: Path) -> dict[str, int]:
    if not path.is_file():
        return {}
    return {name: int(code_point, 16) for name, code_point in CATALOG_ENTRY.findall(path.read_text())}


def scan_sources(roots: list[Path]) -> tuple[set[str], set[str], dict[str, int]]:
    """Collect direct icon calls, candidate forwarded names, and enum defaults."""
    candidates: set[str] = set()
    required: set[str] = set()
    enum_defaults: dict[str, int] = {}
    visited: set[Path] = set()
    for root in roots:
        def kotlin_files():
            for directory, children, files in os.walk(root):
                children[:] = [name for name in children if name not in EXCLUDED_DIRECTORIES]
                yield from (Path(directory) / name for name in files if name.endswith(".kt"))
        paths = [root] if root.is_file() else kotlin_files()
        for path in paths:
            if path.suffix != ".kt" or EXCLUDED_DIRECTORIES.intersection(path.parts):
                continue
            path = path.resolve()
            if path in visited:
                continue
            visited.add(path)
            text = path.read_text(encoding="utf-8")
            candidates.update(STRING_LITERAL.findall(text))
            required.update(ICON_CALL.findall(text))
            for enum in re.finditer(r'\benum\s+class\s+SfSymbol\b[\s\S]*?\n}', text):
                defaults = {
                    name: int(code_point, 16)
                    for name, code_point in ENUM_ENTRY.findall(enum.group())
                }
                enum_defaults.update(defaults)
                required.update(defaults)
    return candidates, required, enum_defaults


def resolve_symbol(cli: Path, name: str) -> tuple[str, int] | None:
    lookup = SYMBOL_ALIASES.get(name, name)
    for _ in range(3):
        result = subprocess.run(
            [str(cli), "search", "--match-style", "exactName", "--json", lookup],
            capture_output=True,
            text=True,
            check=False,
        )
        if result.returncode:
            continue
        try:
            records = json.loads(result.stdout)
        except json.JSONDecodeError:
            continue
        if len(records) != 1 or records[0].get("name") != lookup:
            return None
        code_point = records[0].get("codepoint")
        if code_point:
            return name, int(code_point.removeprefix("U+"), 16)
    return None


def official_entries(cli: Path) -> tuple[dict[str, int], int, int]:
    """An empty contains query exports all font-addressable names in one CLI call."""
    result = subprocess.run(
        [str(cli), "search", "--match-style", "nameContains", "--limit", "0", "--json", ""],
        capture_output=True, text=True, check=True,
    )
    records = json.loads(result.stdout)
    entries: dict[str, int] = {}
    for record in records:
        if not record.get("codepoint"):
            continue
        name = record["name"]
        code_point = int(record["codepoint"].removeprefix("U+"), 16)
        if not (0xE000 <= code_point <= 0xF8FF or
                0xF0000 <= code_point <= 0xFFFFD or 0x100000 <= code_point <= 0x10FFFD):
            raise ValueError(f"Non-PUA symbol address: {name} U+{code_point:X}")
        if name in entries and entries[name] != code_point:
            raise ValueError(f"Conflicting official address for {name}")
        entries[name] = code_point
    if not entries:
        raise ValueError("SF Symbols CLI returned no font-addressable names")
    canonical_count = len(entries)
    metadata = cli.resolve().parent.parent / "Resources/Metadata"
    aliases = dict(SYMBOL_ALIASES)
    for filename in ("name_aliases.strings", "legacy_aliases.strings"):
        path = metadata / filename
        if path.is_file():
            aliases.update(ALIAS_ENTRY.findall(path.read_text(encoding="utf-8")))
    # Some localized variants have no standalone font address. Resolve only aliases
    # whose target really occurs in the CLI export; never manufacture code points.
    pending = aliases
    while pending:
        remaining = {}
        for alias, target in pending.items():
            if target in entries:
                if alias in entries and entries[alias] != entries[target]:
                    raise ValueError(f"Conflicting alias address for {alias}")
                entries[alias] = entries[target]
            else:
                remaining[alias] = target
        if len(remaining) == len(pending):
            break
        pending = remaining
    return entries, canonical_count, len(entries) - canonical_count


def render_catalog(entries: dict[str, int], package: str) -> str:
    items = sorted(entries.items())
    chunks = [items[start:start + CHUNK_SIZE] for start in range(0, len(items), CHUNK_SIZE)]
    calls = "\n".join(f"        addChunk{index}(this)" for index in range(len(chunks)))
    functions = "\n\n".join(
        f"    private fun addChunk{index}(target: MutableMap<String, Int>) {{\n" +
        "\n".join(f'        target["{name}"] = 0x{code_point:X}' for name, code_point in chunk) +
        "\n    }" for index, chunk in enumerate(chunks)
    )
    return f'''// Generated by python3 scripts/generate_sf_symbol_catalog.py. Do not edit.
package {package}

object SfSymbolCatalog {{
    // Bounded helpers keep JVM/Dex methods below their code-size limits.
    private val codePoints: Map<String, Int> = buildMap({len(entries)}) {{
{calls}
    }}

    val systemNames: Set<String> get() = codePoints.keys

    fun codePointOrNull(systemName: String): Int? = codePoints[systemName]

    fun codePoint(systemName: String): Int = requireNotNull(codePointOrNull(systemName)) {{
        "Unresolved SF Symbol: $systemName"
    }}

{functions}
}}
'''


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--source", action="append", type=Path, help="Kotlin file or source root; repeatable (default: repository).")
    parser.add_argument("--catalog", type=Path, default=DEFAULT_CATALOG, help="Output catalog; its existing entries are always preserved.")
    parser.add_argument("--seed", action="append", type=Path, default=[], help="Additional catalogs whose entries must be retained; repeatable.")
    parser.add_argument("--symbol", action="append", default=[], help="Additional required symbol, including dynamically supplied names; repeatable.")
    parser.add_argument("--cli", type=Path, default=DEFAULT_CLI, help="Official SF Symbols CLI executable.")
    parser.add_argument("--package", default="io.github.neoruaa.glasmio.icons", help="Generated Kotlin package.")
    parser.add_argument("--jobs", type=int, default=4, help="Concurrent exact-name CLI searches (default: 4).")
    parser.add_argument("--used-only", action="store_true", help="Resolve only source-used names instead of exporting the complete official catalog.")
    parser.add_argument("--check", action="store_true", help="Check explicit icon calls and enum coverage without writing or requiring the CLI.")
    parser.add_argument("--check-generated", action="store_true", help="Recreate the complete catalog from the CLI and require byte-identical generated output.")
    args = parser.parse_args()
    if args.jobs < 1:
        parser.error("--jobs must be positive")
    roots = args.source or [REPOSITORY]
    for root in roots:
        if not root.exists():
            parser.error(f"Source does not exist: {root}")
    entries: dict[str, int] = {}
    for seed in [*args.seed, args.catalog]:
        entries.update(read_entries(seed))
    candidates, required, enum_defaults = scan_sources(roots)
    required.update(args.symbol)
    candidates.update(required)
    for name, code_point in enum_defaults.items():
        if name in entries and entries[name] != code_point:
            parser.error(f"SfSymbol enum and catalog disagree for {name}")
    if args.check and not args.check_generated:
        missing = sorted(required - entries.keys())
        if missing:
            parser.exit(1, "Unresolved SF Symbol calls or enum names: " + ", ".join(missing) + "\n")
        print(f"Catalog covers {len(required)} required symbols; {len(entries)} entries retained.")
        return
    before = len(entries)
    entries.update(enum_defaults)
    if not args.cli.is_file():
        parser.error(f"SF Symbols CLI not found: {args.cli}; pass --cli or use --check")
    if not args.used_only:
        official, canonical_count, alias_count = official_entries(args.cli)
        conflicts = [name for name in entries.keys() & official.keys() if entries[name] != official[name]]
        if conflicts:
            parser.error("Existing entries disagree with official addresses: " + ", ".join(sorted(conflicts)))
        entries.update(official)
        print(f"Official export: {canonical_count} names and {alias_count} addressable aliases.")
    unresolved = sorted(candidates - entries.keys()) if args.used_only else sorted(required - entries.keys())
    if unresolved:
        if not args.cli.is_file():
            parser.error(f"SF Symbols CLI not found: {args.cli}; pass --cli or use --check")
        with concurrent.futures.ThreadPoolExecutor(max_workers=args.jobs) as pool:
            entries.update(
                result for result in pool.map(lambda name: resolve_symbol(args.cli, name), unresolved)
                if result is not None
            )
    missing = sorted(required - entries.keys())
    if missing:
        parser.exit(1, "Unresolved SF Symbol calls or enum names: " + ", ".join(missing) + "\n")
    generated = render_catalog(entries, args.package)
    if args.check_generated:
        if not args.catalog.is_file() or args.catalog.read_text(encoding="utf-8") != generated:
            parser.exit(1, f"Generated catalog differs: {args.catalog}\n")
        print(f"Generated output is current: {len(entries)} symbols.")
        return
    args.catalog.parent.mkdir(parents=True, exist_ok=True)
    args.catalog.write_text(generated, encoding="utf-8")
    print(f"Wrote {len(entries)} symbols ({len(entries) - before} added) to {args.catalog}")


if __name__ == "__main__":
    main()
