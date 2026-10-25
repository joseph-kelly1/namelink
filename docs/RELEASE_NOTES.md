# NameLink 0.1.0-alpha.1 — release-note draft

Original server-only Fabric nickname mod for Minecraft Java 26.3. Admins assign
recognizable plain names without changing anyone's account or UUID. Clients need
no mod or resource pack.

## Requirements

- Minecraft Java **26.3** and **Java 25**
- Fabric Loader **0.20.1+**
- Fabric API **0.165.0+26.3** tested
- Install **`namelink-0.1.0-alpha.1.jar`** in the server `mods/` directory
- Do not install the sources jar or another nickname mod

## Included

- Admin-only `/nick set`, `/nick clear`, `/nick list`; public `/whois` and help.
- Plain nicknames in chat, Tab, and per-viewer overhead text displays using vanilla
  packets; no authenticated GameProfile or signed message-body replacement.
- Native operator level 2 defaults; optional Fabric permission API v1 admin node.
- UUID-based aliases in player-only and GameProfile arguments; actual usernames
  remain valid. Suggestions insert literal nicknames and retain account names.
- BlossomTpa **2.2.15+26.3**, BlossomLib **2.6.1+26.3**: all five required tpa paths,
  preserving upstream request/optional-target behavior.
- QuickLock **0.1.0-alpha.1**: all four trust paths resolve the intended UUID,
  including safe known offline targeting. Existing trust survives nickname changes.
- Versioned atomic persistence, case-insensitive collisions, permanent username and
  retired nickname reservations, and real-username priority on identity updates.
- Independent display/compatibility configuration, small read-only resolution API,
  reproducible Gradle wrapper, and GitHub Actions build/test matrix.

## Validation and alpha limits

26 unit tests pass. Dedicated-server runs report 15 tests (14 NameLink methods and
one Fabric framework test) with the required integrations and with those integrations
plus Lithium **0.26.2+mc26.3**. Baseline operation without optional mods also passes;
its two optional test methods explicitly omit their integration assertions.

The packaged production jar starts, restarts with saved assignments, accepts console
administration, clears names and refuses malformed data in separate JVMs, both with
Fabric API alone and with the required integration stack plus Lithium. Forced rebuilds
of production and source jars are byte-identical locally. See `TESTING.md` for exact
scope and reproduction steps; consult GitHub Actions for actual remote build status.

**Real vanilla-client rendering and multiplayer acceptance remain unverified.**
Packet tests do not prove smooth overhead rendering or absence of visible duplicates.
Labels intentionally use conservative line-of-sight and distance limits, hide invisible
and spectator subjects, and do not reproduce client HUD/self-view behavior, below-name
score objectives, voice icons or all virtual-team/vanish systems. Real team gameplay
is preserved; client-only synthetic teams may affect Tab sorting for unteamed subjects.

General entity commands such as **`/tp Alex` and `/kill Alex` do not support aliases**;
use actual usernames there. Selector filters, score-holder strings, messages, paths,
JSON/SNBT and unrelated command arguments are never rewritten. BlueMap and Simple
Voice Chat retain account-name paths; no extra naming adapters are included. Live
cooldown/timeout timing, full companion-stack behavior, representative performance,
third-party permission providers and DiscoPanel operation require the documented
manual acceptance checklist.

Back up the entire stopped world and its `data/namelink.json` together. Atomic JSON
replacement does not make all Minecraft world/player saves one transaction. Old
usernames and retired nicknames are permanently reserved but do not redirect;
malformed/incompatible data requires repair or a backup, not silent reset.

MIT © 2026 Joseph Kelly. This file is a draft only. No public GitHub release,
Modrinth publication or live-server deployment has been performed.
