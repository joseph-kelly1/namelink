# NameLink

Original MIT server-side Fabric nickname mod for Minecraft Java **26.3**.
Admins assign recognizable plain names; players can use them in supported
player-target arguments. Clients need no mod or resource pack.

NameLink never changes authenticated GameProfiles, UUIDs, skins, inventories,
advancements, bans, whitelist ownership, or other mods' ownership records.
Authentication and signed chat remain enabled. Nicknames are display labels
and UUID lookups, not Minecraft accounts.

## Install

1. Stop the server and back up its world, player data, and mod data together.
2. Use **Java 25**, Minecraft **26.3**, Fabric Loader **0.20.1+**, and Fabric API
   **0.165.0+26.3** (tested versions). In DiscoPanel, select a Java 25 runtime
   and the matching Fabric server installation.
3. Put `namelink-0.1.0-alpha.1.jar` in the server's `mods/` directory alongside
   Fabric API. Do not install the sources jar or another nickname mod.
4. Restart. In the console: `nick set BlockMaster42 Alex`. The account must be
   known from a previous join, available whitelist record, or profile cache.

NameLink adds no network port. BlossomTpa, BlossomLib, and QuickLock are optional.
No live server was deployed during development. DiscoPanel itself still needs
an installation acceptance check.

## Commands and permissions

| Command | Access | Behavior |
| --- | --- | --- |
| `/nick set <player> <nickname>` | Admin | Assign/update; accepts current known usernames or nicknames, including offline players |
| `/nick clear <player>` | Admin | Remove display nickname; identity and trust stay unchanged |
| `/nick list` | Admin | Privately list account names and assignments |
| `/whois <nickname-or-username>` | Everyone | Privately show current account name and nickname |
| `/namelink help` | Everyone | Relevant commands and explanation |

Default admin permission is native operator level **2** (gamemaster) or higher;
server console works. Optional permission providers implementing Fabric's
**permission API v1** can override boolean node **`namelink:admin`**. No permission
mod required. This is not the older third-party `fabric-permissions-api-v0`
node format; support depends on the installed provider. The native fallback and
unauthorized command rejection are automated tests; a third-party permission
provider has not been tested.

Feedback is private; assignment lists do not broadcast to other players. Public
`/whois` completion lists online identities only; admins can complete known offline
identities. `/whois` is an identity lookup, not a privacy/anonymous identity feature.

## Names and identity policy

Nicknames contain **3–16 ASCII letters, digits, or underscores**. Display keeps
chosen capitalization; all comparisons use `Locale.ROOT` case normalization.
Selectors, UUID strings, spaces, colors, JSON, and formatting are not aliases.
A nickname cannot equal the owner's username, another known username, an active
nickname, or a historical reservation held by another UUID. Exact repeat assignment
returns an informative no-op; changing capitalization updates display.

The boundary is online players, previously observed joins, available whitelist
identities, and identities already in the server profile cache. NameLink makes
**no external account lookup**. This cannot guarantee worldwide uniqueness.

Every observed username remains reserved permanently. Old usernames are **not
aliases**: use the current username or active nickname. Only a join changes a
previously recorded current username; stale cache/whitelist records add reservations
without reverting it. Two offline UUIDs recorded with the same current username
are ambiguous and rejected; an online actual username takes priority.

Every assigned nickname is permanently reserved to its UUID, including after
clear or rename. Its owner may reuse it if no known username now occupies it.
Retired nicknames no longer resolve. This prevents old nicknames silently targeting
someone else. Real usernames always outrank even retired reservations.

Conflicts detected at startup, profile-cache update, whitelist reconciliation, or
join disable the affected nickname, restore ordinary display naming, log the UUID
and reason, and privately notify online admins. Whitelist entries are reconciled
every 100 ticks and immediately before assignments. Real usernames never transfer
nickname ownership or any account permissions/data. Profile-cache changes are
observed in memory; cache records present when starting are imported as reservations.

## Display and targeting

Chat uses the ordinary display-name decoration around the original signed message;
NameLink does not rewrite message contents or signatures. Tab uses vanilla player
list display names. Other messages that call `Player.getDisplayName()` (such as
join/leave/death messages) may also use the nickname. Hover/click chat identity
continues to refer to the actual account. Chat config controls this display-name hook.

Overhead names use per-viewer vanilla text-display packets. Native account name tags
are hidden through client-only team visibility projections. Server scoreboard
membership and team gameplay data are never changed. A real team containing a
nicknamed online member has native labels hidden and virtual labels for all its
online members; members without nicknames receive their account name. Other team
properties and original visibility rules are retained. Unteamed nicknamed players
receive individual temporary client-only teams. No display entities are saved to chunks.

Labels require tracking, same dimension, line of sight, and at most 32 blocks
(16 while crouching). Invisible, dead, and spectator subjects have no virtual label.
No see-through flag is sent. These conservative limits can hide more labels than
vanilla, including friendly invisible teammates. Movement updates each server tick;
rename, clear, untracking, respawn, dimension changes, disconnect and shutdown clean
up displays. Client HUD hiding, third-person self labels, camera mods, below-name
scoreboard objectives, and unrelated virtual-team/vanish mods cannot be fully
replicated by server-only text entities. See [compatibility](docs/COMPATIBILITY.md).
**Packet tests do not prove actual vanilla-client rendering.**

Supported player-only arguments include vanilla `/msg` (`/tell`, `/w`), `/give`,
`/gamemode`, `/kick`, and other uses of `EntityArgument.player()` or `players()`.
`GameProfileArgument` additionally resolves known offline nickname UUIDs in commands
such as `/ban`, `/op`, `/deop`, `/pardon`, and `/whitelist add/remove`.
Each command retains its own permission checks and constraints. Vanilla's actual
username lookup behavior (including any vanilla cache/network behavior) remains intact.

General entity arguments are deliberately left vanilla: **`/tp Alex` and `/kill Alex`
do not gain alias support**. Neither do score-holder arguments, arbitrary strings,
selector filters such as `@a[name=Alex]`, JSON/SNBT, paths, item names, or message
contents. Selectors remain selectors; single/multiple and online-only constraints
remain native. In 26.3 vanilla player-only arguments reject UUID tokens; entity
arguments that support UUIDs remain unchanged.

Supported arguments without a custom provider tell vanilla clients to ask the
server for completion. The completion menu shows and inserts the **literal nickname**,
with actual usernames also available; there are no decorative replacement strings.
Entity-only arguments keep vanilla completion. Commands with their own custom
suggestion provider may need a separate integration. The server command tree still
filters commands by the viewer's permissions.

Required integrations supported and tested:

- **BlossomTpa 2.2.15+26.3 / BlossomLib 2.6.1+26.3**: `/tpa`, `/tpahere`,
  `/tpaaccept [target]`, `/tpadeny [target]`, `/tpacancel [target]`. Uses its existing
  vanilla player arguments; requests, permissions, optional target logic, timers
  and cooldown implementations remain Blossom's.
- **QuickLock 0.1.0-alpha.1**: `/trust`, `/untrust`, `/trust-all`, `/untrust-all`.
  A small version-gated adapter translates only the parsed player argument into
  a username that QuickLock resolves to the same UUID. Offline targets must also
  be known to QuickLock, with an unambiguous matching cache name; otherwise it
  fails explicitly. Changes and clears never alter existing trust. Other QuickLock
  versions disable the adapter with a warning until reviewed.

Arbitrary mod commands are not automatically supported. BlueMap and Simple Voice
Chat have separate naming paths and retain account names; no extra adapters shipped.

## Configuration and storage

Generated `config/namelink.json` (restart to apply):

```json
{
  "chat": true,
  "tab": true,
  "overhead": true,
  "vanillaPlayerArguments": true,
  "quickLock": true
}
```

Unknown options or non-boolean values fail startup explicitly. Disable an individual
surface or the corresponding compatibility hook if another mod owns it.

Assignments and history live in **`<level-name>/data/namelink.json`**, schema 1.
UUIDs are canonical keys. Small identity updates are written only when changed;
assign/clear writes succeed before publishing a new assignment. Temporary writes
are flushed and atomically replaced on the same filesystem. Atomic-move failure
is an error; there is no non-atomic fallback. Malformed, oversized, or incompatible
storage stops startup without overwriting the source. Detected runtime identity
save failure is fatal; assignment save failure leaves the prior assignment intact.
Back up the whole stopped world and this file together. Atomic replacement is not
one transaction with Minecraft's separate world/player saves or a power-loss guarantee.
Do not delete history to recycle names without an explicit identity audit.

## Build and verification

Use Java 25; dependencies and wrapper are pinned. Gradle **9.6.0**, Loom **1.17.21**.

```sh
./gradlew build
python3 scripts/fetch-test-mods.py
./gradlew build -PwithIntegrations
./gradlew build -PwithIntegrations -PwithLithium
```

The build includes JUnit and dedicated-server game tests. Optional-mod tests only
exercise their integration paths when those mods are present; the baseline verifies
successful operation without them. `test-mods/`, generated worlds, logs, and build
outputs are ignored. Downloads are checksum verified. No integration mods are
packaged in NameLink. Installable output: `build/libs/namelink-0.1.0-alpha.1.jar`.

See [test evidence and two-player checklist](docs/TESTING.md),
[compatibility matrix](docs/COMPATIBILITY.md), [architecture/API](docs/ARCHITECTURE.md),
and [release draft](docs/RELEASE_NOTES.md). GitHub Actions runs baseline and integration
builds, retains artifacts, and does not create public releases.
