#!/usr/bin/env python3
"""Boot the production JAR in a new disposable, loopback-only server; verify restart and fail-closed behavior."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import queue
import shutil
import subprocess
import tempfile
import threading
import time
import zipfile

ROOT = Path(__file__).resolve().parents[1]


def download(url, target):
    subprocess.run(["curl", "--fail", "--location", "--silent", "--show-error", "--retry", "3", url, "-o", str(target)], check=True)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--jar", type=Path, default=ROOT / "build/libs/namelink-0.1.0-alpha.2.jar")
    parser.add_argument("--fabric-api", type=Path, required=True)
    parser.add_argument("--launcher", type=Path, help="Optional existing official Fabric server launcher (26.3/0.20.1)")
    parser.add_argument("--mod", type=Path, action="append", default=[], help="Extra optional runtime mod; repeat as needed")
    parser.add_argument("--accept-eula", action="store_true", help="Accept Minecraft EULA for this disposable test server")
    args = parser.parse_args()
    if not args.accept_eula:
        parser.error("Read https://aka.ms/MinecraftEULA and supply --accept-eula to run a disposable server")
    java = str(Path(os.environ["JAVA_HOME"]) / "bin/java") if "JAVA_HOME" in os.environ else "java"
    folder = Path(tempfile.mkdtemp(prefix="namelink-production-"))
    print(f"Disposable test server: {folder}", flush=True)
    (folder / "mods").mkdir()
    for source in [args.jar, args.fabric_api, *args.mod]:
        shutil.copy2(source, folder / "mods" / source.name)
    with zipfile.ZipFile(args.jar) as archive:
        metadata = json.loads(archive.read("fabric.mod.json"))
        assert metadata["id"] == "namelink" and metadata["environment"] == "server"
        assert not any("NameLinkGameTests" in name for name in archive.namelist())
    launcher = folder / "fabric-server.jar"
    if args.launcher:
        shutil.copy2(args.launcher, launcher)
    else:
        download("https://meta.fabricmc.net/v2/versions/loader/26.3/0.20.1/1.1.2/server/jar", launcher)
    (folder / "eula.txt").write_text("eula=true\n")
    (folder / "server.properties").write_text(
        "server-ip=127.0.0.1\nserver-port=0\nonline-mode=true\nenforce-secure-profile=true\n"
        "white-list=true\nview-distance=2\nsimulation-distance=2\nlevel-type=minecraft:flat\n"
        "generate-structures=false\nspawn-protection=0\nmax-tick-time=60000\n")
    identity = {"uuid": "11111111-2222-4333-8444-555555555555", "name": "BlockMaster42"}
    (folder / "whitelist.json").write_text(json.dumps([identity]))
    (folder / "usercache.json").write_text(json.dumps([{**identity, "expiresOn": "2030-01-01 00:00:00 +0000"}]))

    def boot(number, commands=(), malformed=False):
        events = queue.Queue()
        log = folder / f"smoke-{number}.log"
        process = subprocess.Popen([java, "-Xms256M", "-Xmx1G", "-jar", str(launcher), "nogui"], cwd=folder,
                                   stdin=subprocess.PIPE, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True, bufsize=1)
        def read():
            with log.open("w") as output:
                for line in process.stdout:
                    output.write(line)
                    output.flush()
                    events.put(line)
            events.put(None)
        reader = threading.Thread(target=read, daemon=True)
        reader.start()
        def wait_for(text, timeout=150):
            deadline = time.monotonic() + timeout
            while time.monotonic() < deadline:
                try:
                    line = events.get(timeout=min(1, max(.01, deadline-time.monotonic())))
                except queue.Empty:
                    if process.poll() is not None:
                        break
                    continue
                if line is None:
                    break
                if text in line:
                    return
            raise RuntimeError(f"Did not see {text!r}; inspect {log}")
        try:
            if malformed:
                wait_for("NameLink cannot safely load identity data; refusing startup")
                process.wait(timeout=45)
                reader.join(timeout=5)
                assert "Done (" not in log.read_text(), f"Malformed-data server reached ready state; {log}"
            else:
                wait_for("Done (")
                for command, expected in commands:
                    process.stdin.write(command + "\n")
                    process.stdin.flush()
                    wait_for(expected, 20)
                process.stdin.write("stop\n")
                process.stdin.flush()
                assert process.wait(timeout=60) == 0, f"Server stop failed; {log}"
            reader.join(timeout=5)
            print(f"Passed packaged server process {number}: {log}", flush=True)
        finally:
            if process.poll() is None:
                process.terminate()
                try:
                    process.wait(timeout=10)
                except subprocess.TimeoutExpired:
                    process.kill()
                    process.wait(timeout=5)

    boot(1, [("nick set BlockMaster42 Alex", "Nickname assigned to BlockMaster42: Alex."),
             ("whois Alex", "Minecraft username: BlockMaster42; nickname: Alex.")])
    state_path = folder / "world/data/namelink.json"
    saved = json.loads(state_path.read_text())
    record = next(r for r in saved["identities"] if r["uuid"] == identity["uuid"])
    assert record["nickname"] == "Alex"
    boot(2, [("whois Alex", "Minecraft username: BlockMaster42; nickname: Alex."),
             ("nick clear Alex", "Nickname cleared for BlockMaster42."),
             ("whois BlockMaster42", "Minecraft username: BlockMaster42; nickname: none.")])
    assert saved["reservations"]["alex"] == identity["uuid"]
    cleared = json.loads(state_path.read_text())
    assert "nickname" not in next(r for r in cleared["identities"] if r["uuid"] == identity["uuid"])
    assert cleared["reservations"]["alex"] == identity["uuid"]
    original = state_path.read_bytes()
    state_path.write_text("{malformed")
    boot(3, malformed=True)
    assert state_path.read_text() == "{malformed", "Malformed source was overwritten"
    state_path.write_bytes(original)
    print(f"PASS: production startup, console administration, independent-process persistence, clear, and malformed-data refusal. JAR SHA256 {hashlib.sha256(args.jar.read_bytes()).hexdigest()}", flush=True)


if __name__ == "__main__":
    main()
