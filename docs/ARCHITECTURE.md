# Architecture and small integration API

NameLink is original server-only Java code. Minecraft 26.3 is unobfuscated;
there are no Yarn mappings or remapped nickname GameProfiles. Gradle 9.6.0 and
Loom 1.17.21 compile against the actual 26.3 jar, Fabric Loader 0.20.1 and Fabric
API 0.165.0+26.3 on Java 25. Metadata deliberately targets exactly 26.3; mixins
must be reviewed before widening the version range.

## Identity and persistence

`NicknameState` is a game-independent UUID policy with indexed normalized names.
Identity records contain a current account name, all observed usernames, and an
optional active nickname. A separate normalized alias-to-UUID reservation map
persists retired nicknames. Username history is reserved but never used as a
redirect. Active alias lookup is constant-time. Offline current username lookup
considers only owners of that normalized name; an ambiguity returns no target.

Cache/whitelist/QuickLock observations cannot overwrite the current name of an
existing identity. A join can. Startup imports current profile-cache records first,
then remaining in-memory name-cache records as reservations. Available whitelist
and supported QuickLock identities are reconciled once their services are ready,
before the server begins its normal ticks. Cache updates schedule identity work on
the server thread when necessary. No external identity API is called by NameLink.

Assignments use copy/validate/write/publish. Disk failure before publication leaves
the prior live assignment intact. Real account observations disable conflicting
aliases immediately and require persistence; a failure requests server shutdown
rather than continuing with divergent identity history. No background thread reads
mutable policy state. Rare small writes occur on the server thread; labels never
write to disk. Atomic same-filesystem replacement requires an atomic-move-capable
filesystem. Large networks would need a different storage design; defaults target
a small private server. Schema 1 is limited to 8 MiB; unsupported/malformed data
requires operator repair or backup restoration and is never silently reset.

## Command hooks

- `EntityArgumentMixin` marks only `playersOnly` parsed selectors.
- `EntitySelectorMixin` resolves only a bare player-name token on those marked
  selectors, at execution time. It returns the online `ServerPlayer` with the
  alias owner's UUID. Selectors, filters and arbitrary entity arguments are untouched.
- `GameProfileArgumentMixin` recognizes a known bare alias and defers UUID resolution
  to execution. An account username that later takes that spelling still wins.
  Native non-alias parsing is unchanged.
- `CommandsMixin` adds `minecraft:ask_server` only to eligible copies of argument
  builders sent in the already permission-filtered client tree. It does not change
  the server dispatcher or the command requirement predicates. Existing custom
  suggestion providers are retained.
- Suggestions merge active aliases with native suggestions. Ordinary player-argument
  suggestions include online aliases. Administrative profile arguments may also
  suggest known offline names when the caller is a NameLink admin.
- QuickLock's `run` method receives the canonical cache/account name only at its
  `StringArgumentType.getString` player-argument read. The adapter verifies that
  QuickLock's existing online-first/cache-uniqueness lookup will return the exact
  alias UUID, otherwise rejects explicitly. Its four trust command suggestion
  providers are wrapped. No raw command string is rewritten.

Brigadier permissions, cardinality, online constraints, native errors, selectors,
UUID behavior where supported, and downstream UUID operations stay native.
General entity and score-holder arguments are intentionally unsupported aliases.

## Display hooks

The server `Player.getDisplayName()` hook replaces the team-formatted text with
a plain nickname before vanilla account click/hover/insertion decoration.
Signed message bodies and identity profiles remain original. The server
`getTabListDisplayName()` hook supplies plain display text; assignments and joins
send display-name updates for online players.

`OverheadLabels` uses explicit per-viewer tracking sets from Fabric tracking events.
It constructs vanilla text displays solely to allocate safe entity IDs and metadata;
they never enter a server level or save. Display creation/removal is packet-based,
and changed positions use entity position sync packets. Each tick evaluates online
viewer/subject pairs, distance, line of sight, life state, dimension, invisibility,
spectator mode, and real scoreboard visibility. Cost is O(online players²) per tick
with additional raycasts for nearby tracked visible candidates; small private
servers should measure representative load before broad rollout.

For unteamed nicknamed subjects, individual private client-only teams hide native
account labels. For real teams containing nicknamed members, only the outgoing
team name-tag visibility parameter becomes NEVER. All affected members receive
virtual plain labels (account names when unassigned), evaluated using the original
server team rule. Membership, color, collision, friendly fire, team prefix/suffix,
and gameplay data remain server-owned. Other outgoing packet fields are copied.
Transition cleanup removes synthetic teams before reasserting real membership.
Shutdown restores original real-team visibility. No scoreboard data is persisted
or mutated by this display layer.

This deliberately conservative server approximation cannot inspect client HUD
settings or exactly reproduce client font metrics, smoothing, self-view behavior,
name-tag-adjacent score objectives, voice icons, or arbitrary virtual team/vanish
systems. Screen rendering remains a manual acceptance item.

## Optional classes

`CompatibilityPlugin` checks installed QuickLock metadata before selecting its
pseudo mixin. Its only supported version is 0.1.0-alpha.1. No production class
signature references a QuickLock or Blossom class. Reflection inside the optional
bridge runs only when the supported mod is present. Blossom needs no custom mixin
because its supported release uses vanilla player arguments. A new QuickLock
version requires source review and integration tests before expanding the gate.

## Public API

Call these static methods on the Minecraft server thread while the server is live:

```java
NameLinkApi.resolve(server, "Alex");       // Optional<UUID>; known nickname/current account name
NameLinkApi.nickname(server, uuid);        // Optional<String>; assigned nickname only
NameLinkApi.displayName(server, uuid);     // Optional<String>; nickname or known account name
```

Import `io.github.josephkelly.namelink.NameLinkApi`. `resolve` honors online real
account priority and returns empty for unsupported syntax, unknown names or
ambiguous offline names. Old usernames and retired aliases do not redirect.
Callers retain all authorization checks and decide whether offline UUIDs are legal.
The API does not grant permissions, provide network lookup, or imply that a player
is online. Consumers must gate optional NameLink linkage through their loader's
optional integration mechanism. No mutation API is exposed; use admin commands.

## Upstream evidence (inspected October 25, 2026)

- [Fabric's Minecraft 26.3 toolchain guidance](https://fabricmc.net/2026/09/15/263.html).
- [Minecraft release](https://www.minecraft.net/en-us/article/minecraft-java-edition-26-3)
  and official launcher manifest: `https://piston-meta.mojang.com/mc/game/version_manifest_v2.json`.
- [BlossomTpa source at 890e38e](https://github.com/BlossomMods/BlossomTpa/blob/890e38eadcb8f31c446613948df3e97c176ba3c6/src/main/java/dev/codedsakura/blossom/tpa/BlossomTpa.java):
  all five required paths use `EntityArgument.player()` and `getPlayer()`;
  release jar 2.2.15+26.3 bytecode was also inspected and tested.
- [QuickLock released command implementation](https://github.com/joseph-kelly1/quicklock/blob/v0.1.0-alpha.1/src/main/java/io/github/josephkelly/quicklock/LockCommands.java)
  at released tag targeting 94410219683440cdddf86569149015cb5fe5db13; released jar
  SHA-256 is pinned in `scripts/fetch-test-mods.py`. No separate QuickLock repository
  was modified.
- [BlueMap FabricPlayer source](https://github.com/BlueMap-Minecraft/BlueMap/blob/51c054002184527239de66fa965e48e729d34958/implementations/fabric/src/main/java/de/bluecolored/bluemap/fabric/FabricPlayer.java)
  reads `player.getName().getString()`, not the display-name hook.
- [Simple Voice Chat 26.3 PlayerStateManager source](https://github.com/henkelmax/simple-voice-chat/blob/5081532076651622c8ac0b0c36b43af4b380c393/common/src/main/java/de/maxhenkel/voicechat/voice/server/PlayerStateManager.java)
  uses `player.getGameProfile().name()` for default voice state names. No documented
  server API rename hook was identified that also handles live updates; no extra
  naming integration is claimed.
