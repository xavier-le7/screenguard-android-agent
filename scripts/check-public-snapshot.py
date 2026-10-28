#!/usr/bin/env python3
"""Check a prospective public source snapshot for accidental private literals.

This is a limited preflight, not a secret scanner or a history audit.
Publishing still requires human review of all sources and a clean repository.
"""
from __future__ import annotations

import re
import subprocess
import sys
from pathlib import Path
from urllib.parse import urlsplit

ROOT = Path(__file__).resolve().parents[1]
CHECK_SUFFIXES = {".kt", ".kts", ".xml", ".md", ".yml", ".yaml", ".properties", ".py", ".txt"}
PUBLIC_HOSTS = {
    "github.com",
    "raw.githubusercontent.com",
    "api.github.com",
    "example.com",
    "example.org",
    "example.net",
    "schemas.android.com",
    "developer.android.com",
    "host",
    "hôte",  # translated placeholder, not a real default server
}
PATTERNS = (
    ("private IPv4 address", re.compile(
        r"\b(?:10|192\.168|172\.(?:1[6-9]|2\d|3[01]))\."
        r"(?:\d{1,3}\.){1,2}\d{1,3}\b"
    )),
    ("personal email address", re.compile(
        r"\b[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}\b"
    )),
    ("private hostname", re.compile(
        r"\b(?:[A-Za-z0-9-]+\.)+(?:fr|lan|local|internal)\b",
        re.IGNORECASE,
    )),
    ("local account home path", re.compile(
        r"(?:/Users/|/home/|/var/home/)[A-Za-z0-9._-]+/"
    )),
    ("embedded credential", re.compile(
        r"""(?i)\b(?:auth_token|api_key|client_secret|password)\s*[:=]\s*["'][^"']{8,}["']"""
    )),
    ("credential fingerprint", re.compile(
        r"\b(?:ghp_[A-Za-z0-9]{20,}|github_pat_[A-Za-z0-9_]{20,}|"
        r"glpat-[A-Za-z0-9_-]{20,}|AKIA[A-Z0-9]{16})\b"
    )),
)
URLS = re.compile(r"\b(?:https?|wss?)://[^\s\"'<>)]+")


def scan() -> list[str]:
    files = subprocess.check_output(["git", "ls-files", "-z"], cwd=ROOT).split(b"\0")
    findings: list[str] = []
    for raw_path in files:
        if not raw_path:
            continue
        path = ROOT / raw_path.decode("utf-8")
        if path.suffix not in CHECK_SUFFIXES or not path.is_file():
            continue
        try:
            content = path.read_text(encoding="utf-8")
        except (OSError, UnicodeError):
            continue
        for number, line in enumerate(content.splitlines(), start=1):
            for reason, pattern in PATTERNS:
                # Never print a matched secret or private address into CI logs.
                if pattern.search(line):
                    findings.append(f"{path.relative_to(ROOT)}:{number}: {reason}")
            for url_match in URLS.finditer(line):
                host = urlsplit(url_match.group(0).rstrip(".,;")).hostname
                if host and host.lower() not in PUBLIC_HOSTS:
                    findings.append(
                        f"{path.relative_to(ROOT)}:{number}: unreviewed endpoint"
                    )

    agent_store = ROOT / "app/src/main/java/fr/sevenskies/screenguard/storage/AgentStore.kt"
    config = agent_store.read_text(encoding="utf-8")
    default = re.search(r'DEFAULT_URL\s*=\s*"([^"]*)"', config)
    if default is None or default.group(1):
        findings.append("AgentStore.kt: server URL must have an empty default")
    return findings


if __name__ == "__main__":
    problems = scan()
    if problems:
        print("Privacy preflight failed; review these source locations:", file=sys.stderr)
        for item in problems:
            print(f"  {item}", file=sys.stderr)
        sys.exit(1)
    print("Privacy preflight passed (tracked files only; manual/history review required).")
