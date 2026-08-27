#!/usr/bin/env python3
"""Generate the update.json asset consumed by Deep Cleaner."""

from __future__ import annotations

import argparse
import hashlib
import json
import re
from datetime import datetime, timezone
from pathlib import Path


VERSION_RE = re.compile(r"^[0-9A-Za-z][0-9A-Za-z._-]{0,31}$")
TAG_RE = re.compile(r"^v[0-9A-Za-z][0-9A-Za-z._-]{0,31}$")


def sha256_file(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as source:
        for chunk in iter(lambda: source.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Generate a signed-APK update manifest for Deep Cleaner")
    parser.add_argument("--apk", required=True, type=Path)
    parser.add_argument("--output", required=True, type=Path)
    parser.add_argument("--repository", default="Kiowx/Deep_Cleaner")
    parser.add_argument("--tag", required=True)
    parser.add_argument("--version-code", required=True, type=int)
    parser.add_argument("--version-name", required=True)
    parser.add_argument("--min-sdk", type=int, default=26)
    parser.add_argument("--changelog", default="修复问题并提升使用体验。")
    parser.add_argument("--mandatory", action="store_true")
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    if not args.apk.is_file():
        raise SystemExit(f"APK not found: {args.apk}")
    if args.version_code <= 0:
        raise SystemExit("version-code must be positive")
    if not VERSION_RE.fullmatch(args.version_name):
        raise SystemExit("invalid version-name")
    if not TAG_RE.fullmatch(args.tag) or args.tag != f"v{args.version_name}":
        raise SystemExit("tag must exactly match v<version-name>")
    if not re.fullmatch(r"[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+", args.repository):
        raise SystemExit("invalid repository")
    if not 26 <= args.min_sdk <= 100:
        raise SystemExit("min-sdk is outside the supported range")

    changelog = [line.strip()[:300] for line in args.changelog.splitlines() if line.strip()][:12]
    manifest = {
        "format": "deep-cleaner-update",
        "schemaVersion": 1,
        "versionCode": args.version_code,
        "versionName": args.version_name,
        "minSdk": args.min_sdk,
        "apkUrl": f"https://github.com/{args.repository}/releases/download/{args.tag}/{args.apk.name}",
        "sha256": sha256_file(args.apk),
        "changelog": changelog or ["修复问题并提升使用体验。"],
        "mandatory": args.mandatory,
        "publishedAt": datetime.now(timezone.utc).isoformat().replace("+00:00", "Z"),
    }
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(manifest, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
