#!/usr/bin/env python3
"""Download pinned optional test dependencies. Never install these on a live server."""
import hashlib
from pathlib import Path
import subprocess

ROOT = Path(__file__).resolve().parents[1]
MODS = [
    ("quicklock-0.1.0-alpha.1.jar", "https://github.com/joseph-kelly1/quicklock/releases/download/v0.1.0-alpha.1/quicklock-0.1.0-alpha.1.jar", "cc67c11854ae80b1963e85cefd8ca60b9e84628a42533dbed7dd933393bd931f"),
    ("blossom-tpa-2.2.15+26.3.jar", "https://cdn.modrinth.com/data/Y25EUH1s/versions/iOzIRqSt/blossom-tpa-2.2.15%2B26.3.jar", "f02e191604d5b2cb4246c3096e3dd5b0d639a80b003d27dbc31488a5b163bbda"),
    ("blossom-lib-2.6.1+26.3.jar", "https://cdn.modrinth.com/data/Xw7lTsbM/versions/BEMYFwoI/blossom-lib-2.6.1%2B26.3.jar", "e58cd73d0b7d24fb3e8f08fd97e1a2842a32028718ebc9751f2db83ae0aed34e"),
]

if __name__ == "__main__":
    folder = ROOT / "test-mods"
    folder.mkdir(exist_ok=True)
    for name, url, expected in MODS:
        target = folder / name
        if not target.exists():
            temp = target.with_suffix(".download")
            try:
                subprocess.run(["curl", "--fail", "--location", "--silent", "--show-error", "--retry", "3", url, "-o", str(temp)], check=True)
                if hashlib.sha256(temp.read_bytes()).hexdigest() != expected:
                    raise SystemExit(f"Checksum mismatch: {name}")
                temp.replace(target)
            finally:
                temp.unlink(missing_ok=True)
        if hashlib.sha256(target.read_bytes()).hexdigest() != expected:
            raise SystemExit(f"Checksum mismatch: {target}; remove it and retry")
        print(f"Verified {name}")
