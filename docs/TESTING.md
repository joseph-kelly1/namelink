# Testing evidence and multiplayer acceptance

Local validation: **October 25, 2026**, macOS arm64, Temurin **25.0.5+8**,
Minecraft **26.3**, Loader **0.20.1**, Fabric API **0.165.0+26.3**,
Gradle **9.6.0**, Loom **1.17.21**. No live Minecraft client was available.
The automated results below establish server behavior and packets, not pixels.

## Automated checks

| Check | Result |
| --- | --- |
| JUnit identity, storage and config policy | **26 passed**, zero failures/errors |
| Baseline dedicated-server game tests | **15 reported passed**; includes one Fabric framework test and two optional integration methods that explicitly log no integration assertions when their mods are absent |
| Dedicated server with QuickLock 0.1.0-alpha.1, BlossomTpa 2.2.15+26.3, BlossomLib 2.6.1+26.3 | **15 passed**, both integration paths exercised |
| Same integration stack plus Lithium 0.26.2+mc26.3 | **15 passed**, both integration paths exercised |
| Packaged production jar, Fabric API only | **Passed** independent server starts, console assignment/whois, restart persistence, clear, malformed-data startup refusal |
| Packaged jar with all three integration mods plus Lithium | **Passed** the same three-process smoke checks |
| Forced rebuild of production and sources jars | SHA-256 comparison performed; both archives byte-identical |
| Download verification | Released QuickLock and Blossom jars checked against pinned SHA-256 digests |
| Repository hygiene | `git diff --check`; generated worlds, runtime jars, logs and Python caches ignored |

`./gradlew build` runs JUnit and the dedicated game test task. The game test count
is **14 NameLink methods plus one Fabric framework test**. In a baseline run, the
two optional integration methods return after logging that their assertions were
not exercised. These are not evidence for the absent mods. The integration runs
actually load the released jars and execute their commands.

JUnit checks format and case normalization, own/other account collisions, offline
and whitelist identities, join/rename conflicts, username history, retired nickname
reservations, UUID continuity, legacy short account names, restarts, atomic snapshot
failure behavior, duplicate JSON keys, malformed schema/types, invalid records,
incompatible versions and config parsing. It also checks that rejected malformed
sources remain unchanged.

Game tests check admin rejection and console access, private output, actual username
and nickname lookup, online constraints, offline profile aliases, selector/cardinality
permissions, non-replacement of filters/arbitrary strings, separate entity arguments,
autocomplete, and the actual client command packet's `minecraft:ask_server` provider.
They check original GameProfiles, scoreboard account names, ChatType sender decoration,
Tab updates, whitelist and real profile-cache conflicts. GameTestServer's built-in
resolver is a mock, so the cache test temporarily installs an actual vanilla cache,
restoring the original services immediately afterward.

Overhead checks capture vanilla packets and policy: tracked spawn, position updates,
clear, invisibility, untracking, death, spectator mode, dimension separation,
respawn reset, disconnect, shutdown, range, crouch range, a solid line-of-sight wall,
and original server team membership/visibility/collision/friendly-fire properties.
They do not run a client renderer or reproduce latency between two real machines.

Blossom tests inspect all five registered player arguments and autocomplete. Actual
requests are created and inspected for the intended receiver UUID; duplicate-request
policy, targeted acceptance/denial/cancellation and optional-target paths are checked.
QuickLock tests alter actual container and global trust through all four commands,
assert the exact target UUID, rename/clear without moving trust, target an offline
known account, reject an ambiguous cache without changing trust, and import an
otherwise QuickLock-only username conflict. No QuickLock repository files are edited.
Timed cooldown/timeout behavior remains upstream and is still a live acceptance item.

## Reproduce

```sh
# Java 25 must be available to Gradle.
./gradlew clean build
python3 scripts/fetch-test-mods.py
./gradlew build -PwithIntegrations
./gradlew build -PwithIntegrations -PwithLithium
```

Reports: `build/reports/tests/test/index.html`, `build/test-results/test/`, and
`build/run/gameTest/logs/latest.log`. The game server log gives exact counts and
records optional assertions not exercised. The checked-in workflow runs baseline,
integrations, and integrations plus Lithium as separate jobs and retains reports
and jars. Refer to [GitHub Actions](https://github.com/joseph-kelly1/namelink/actions)
for its actual remote status; local results above are independently executed checks.

For a packaged server test, obtain the matching Fabric API jar and read Minecraft's
EULA, then run:

```sh
python3 scripts/production-smoke.py \
  --fabric-api /absolute/path/fabric-api-0.165.0+26.3.jar \
  --accept-eula
```

The script downloads the official pinned Fabric launcher if `--launcher` is omitted.
It creates a new temporary directory, binds to loopback and an ephemeral port,
keeps authentication and secure profiles enabled, and creates only synthetic test
identities. It never reads a live world. Optional `--mod /path/mod.jar` arguments
repeat for QuickLock, BlossomTpa, BlossomLib and Lithium. Logs stay in that disposable
directory, whose path is printed. It starts three separate JVMs: assignment and stop;
restart and clear; malformed-data refusal without ready state or file overwrite.
Minecraft can exit with code 0 after a startup failure, so the last check uses the
refusal log and absence of a ready message rather than trusting exit status alone.

## Two-player vanilla-client checklist — all UNVERIFIED

Use a disposable staging copy and two real authenticated accounts, **A** and **B**,
on unmodified Minecraft 26.3 clients, with no resource pack. Keep B non-op; make A
an operator at level 2 or use the console. Record their actual usernames and UUIDs.
Use `Alex` and `Sam` only if those spellings are not already known usernames or
reserved nicknames on this test world. Use other valid 3–16 character names if needed.
For rollback and uninstallation, stop and restore the complete staging backup.

1. **Install/login:** Both accounts join with Fabric API and NameLink server-side
   only. Confirm skins, inventories, advancements, whitelist and UUIDs match before
   installation. No client installation/resource-pack prompt should appear.
2. **Permissions/private output:** B cannot set, clear or list nicknames. Both can
   use help/whois. A's `/nick list` and command feedback appear only for A; console
   commands work. If using a permission provider, separately verify explicit grant
   and denial of Fabric API v1 node `namelink:admin` and refresh command permissions.
3. **Assignment/identity:** Assign A → Alex. A sends a signed chat message. B sees
   Alex in chat and Tab with A's original skin. Chat hover/click still identifies
   A's account. `/whois Alex` and `/whois <A-username>` show the same pairing.
   Message text containing `Alex` remains exactly what was typed.
4. **Native commands/completion:** As B, type `/msg Al` and Tab; nickname appears
   and choosing it inserts `Alex`. A's actual username is still available and
   targets A. `/msg Alex hello Alex` sends the unchanged text to A. As A, use an
   ordinary player-only admin command with both spellings. Confirm selectors keep
   permission/multi-target behavior; `@a[name=Alex]` does not act as a nickname filter.
   `/tp Alex` is intentionally unsupported; use A's real username there.
5. **Overhead/no duplicate:** B sees exactly one Alex label above A, with no second
   account-name label. Walk, sprint, jump, swim, glide and ride across chunk edges.
   Label follows correctly without stale labels. Check readable distance and motion
   smoothing at normal latency; pixels and smoothness are not proven by packet tests.
6. **Visibility:** A crouches beyond 16 blocks; label disappears. Standing label
   disappears beyond 32 blocks. A goes behind solid walls, becomes invisible, dies,
   and enters spectator mode; B must see no inappropriate label or through-wall text.
   Friendly invisibility is conservatively hidden too. Check bright/dark locations.
7. **Rename/clear:** Rename A → another valid name while B watches, then clear it.
   Chat/Tab/overhead update; native account label returns once, with no ghost. Repeat
   immediately after respawn, reconnect and dimension change; returning to the same
   dimension/range restores exactly one expected label.
8. **Tracking/shutdown:** Separate until A leaves tracking range, reunite, switch
   dimensions separately, then together. Disconnect/reconnect either account. Restart
   the server with a nickname assigned. No stale display remains at an old position;
   current assignment persists. Repeat clear after restart and verify ordinary names.
9. **Teams:** Put A and B on the same real scoreboard team, then different teams,
   including one unassigned member. Test `always`, `never`, `hideForOtherTeams`,
   `hideForOwnTeam`, and a viewer with no team. Confirm collision and friendly-fire
   gameplay stay as configured. Change membership while a nickname is active. Only
   one appropriate label should appear. Team gameplay data must remain unchanged.
   Plain labels omit team colors/prefixes; below-name scores and voice icons are not
   reproduced. Client-only teams may change Tab ordering for unteamed subjects.
10. **Blossom requests:** With the pinned Blossom jars, B uses `/tpa Alex` and
    `/tpahere Alex`. A accepts/denies using B's nickname and actual username. Exercise
    targetless accept/deny/cancel with one pending request, then ambiguity with two.
    B cancels by both spellings. Wait for real timeout/cooldown intervals, move during
    stand-still delay, and confirm Blossom's normal refusal/expiry behavior remains.
11. **QuickLock trust:** B places and locks a barrel. B runs `/trust Alex`; A can
    use it. `/untrust Alex` removes local access. Check `/trust-all Alex` and
    `/untrust-all Alex` on existing and future locks, with independent local/global
    grants. Rename and clear A while trust is active: A's existing UUID grant stays;
    another account cannot inherit it. Disconnect A and repeat against its known
    offline nickname/cache. Actual usernames must still work.
12. **Collision/restart:** Reject own and other real usernames, case-only duplicates,
    short/long/Unicode/formatted names, and selector/UUID syntax. Restart to confirm
    assignments and reservations persist. Automated tests cover simulated login,
    whitelist and cache conflicts; do not pollute a live whitelist with fake account
    records merely to reproduce those scenarios. Check admin warnings and native
    naming restoration in an isolated identity-update test if real accounts permit it.
13. **Companion stack:** Add the exact intended Universal Graves, Polymer, Simple
    Voice Chat, BlueMap and Fast Leaf Decay builds one at a time. Verify death/recovery,
    voice groups/icons and map player entries. Voice/map account names are expected
    without extra adapters. Check logs, player tracking, performance and operation in
    DiscoPanel. This complete live stack has not been validated by the automated suite.

Client HUD-hidden state and third-person self labels cannot be reproduced exactly
server-side; text displays can remain visible in HUD-hidden views. Use the documented
limitations when deciding whether overhead labels suit the server. Do not mark this
checklist complete based on compilation, server startup or packet captures.
