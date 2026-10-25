# Compatibility matrix

Test target: Minecraft Java 26.3, Java 25, Fabric Loader 0.20.1, Fabric API
0.165.0+26.3. Only NameLink is required beyond Fabric API/Loader.

| Component / command | Support | Evidence / limits |
| --- | --- | --- |
| Vanilla chat | Nickname sender display | Automated display/ChatType tests; signed bodies and account profiles unchanged; live signed-client session still unverified |
| Tab | Nickname display and updates | Automated display and update-packet tests; live rendering unverified |
| Overhead | Private text displays and native label suppression | Tracking, movement, lifecycle and visibility tests; actual pixels and smoothing unverified |
| Real scoreboard teams | Gameplay identity preserved; visibility projection | Membership remains server-owned; visibility/collision/friendly-fire tests; visual team/score layouts still manual |
| Vanilla player-only arguments | Nickname + actual username | Single/multiple, selector permissions, filter non-replacement and online constraints tested; explicit custom suggestion providers may need separate adaptation |
| Vanilla GameProfile arguments | Known offline nickname UUIDs | Offline alias test; ordinary actual-name resolution remains vanilla |
| `/tp`, `/teleport`, `/kill`, other general entity arguments | Actual names/selectors/UUIDs only | Alias support intentionally excluded; arbitrary entity parser unchanged |
| Score-holder arguments, `@a[name=...]`, JSON/SNBT, messages, paths, item names | No nickname substitution | Unrelated string/filter behavior tested |
| BlossomTpa **2.2.15+26.3** + BlossomLib **2.6.1+26.3** | All five required command paths | Released jars exercised: UUID targeting, suggestions, actual request create/accept/deny/cancel, optional targets; timers/cooldowns remain upstream and need live timed acceptance |
| QuickLock **0.1.0-alpha.1** | All four required trust paths | Released jar exercised for container and global UUID trust, rename/clear stability, offline cache targeting and autocomplete |
| Other QuickLock versions | Adapter disabled with warning | Actual usernames continue through QuickLock unchanged; version must be reviewed before enabling aliases |
| QuickLock / Blossom absent | NameLink remains usable | Baseline dedicated-server and packaged-JAR startup checks |
| Lithium **0.26.2+mc26.3** | Tested together with required integrations | Dedicated-server suite; representative load still unmeasured |
| Simple Voice Chat **2.6.26+26.3** | Naming adapter not provided | Inspected 26.3 sources use authenticated profile name for voice state. Voice groups/UI keep account names; live coexistence and icon placement unverified |
| BlueMap **5.29-fabric** | Naming adapter not provided | Fabric source uses ordinary `getName()`. Map naming may remain account name; runtime coexistence and map refresh unverified |
| Universal Graves | UUID ownership intended unchanged | No identity mutation; actual death/recovery stack not tested |
| Polymer | No custom types or packs introduced | Server-only vanilla packet design; runtime packet interaction not tested |
| Fast Leaf Decay | No integration needed by design | Exact project/version not supplied; matching release and runtime coexistence unverified |
| DiscoPanel | Ordinary Fabric installation | Java 25 and server file access required; actual panel operation unverified |
| Permission managers | Optional Fabric permission API v1 node `namelink:admin` | Native fallback tested; third-party provider not tested; v0 providers are not automatically compatible |
| Other nickname/chat/team/vanish mods | No blanket compatibility promise | Do not install another nickname mod. Client-only scoreboard writers can conflict; disable `overhead` if another mod owns that surface |

## Known server-only visual limits

Labels are limited to tracked subjects in the same dimension, line of sight, 32
blocks standing / 16 crouching. Invisible, dead and spectator subjects are hidden,
including invisible friendly teammates. Text has depth testing and no see-through
flag. Display movement is tick-driven and can look different from native player
interpolation. Client HUD-hidden mode and third-person self labels are not available
to the server. Existing team prefixes/colors are not applied to plain virtual labels.
Below-name scoreboard objectives and voice-chat name-tag icons are not reproduced.
Temporary client-only teams can affect vanilla Tab sorting for unteamed subjects.

When a real team contains a nicknamed member, native labels are suppressed for
that whole team and replaced for all its online members, preserving their server
visibility rules. Team gameplay is unchanged; actual client behavior with team
changes must pass the manual checklist. Unassigned players outside those affected
teams retain ordinary native overhead names.

For another command integration, inspect its actual argument type, authorization
and UUID lookup first. NameLink's API is available for UUID resolution. Never add
a global command-string replacement as a shortcut.
