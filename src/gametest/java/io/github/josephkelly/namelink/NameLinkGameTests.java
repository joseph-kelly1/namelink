package io.github.josephkelly.namelink;

import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.*;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.commands.*;
import net.minecraft.commands.arguments.*;
import net.minecraft.core.BlockPos;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.*;
import net.minecraft.network.protocol.*;
import net.minecraft.network.protocol.game.*;
import net.minecraft.server.level.*;
import net.minecraft.server.network.*;
import net.minecraft.server.permissions.PermissionSet;
import net.minecraft.server.players.*;
import net.minecraft.world.level.GameType;
import net.minecraft.world.scores.Team;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import io.netty.channel.ChannelFutureListener;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import static net.minecraft.commands.Commands.*;

public final class NameLinkGameTests {
    private static String unique(String prefix) { return prefix+UUID.randomUUID().toString().replace("-","").substring(0,8); }
    private static void check(GameTestHelper h,boolean condition,String message) { h.assertTrue(condition,message); }
    private static final class Client implements AutoCloseable {
        final ServerPlayer player;
        final List<Packet<?>> packets=new ArrayList<>();
        Client(GameTestHelper h) {
            var n=NameLink.active;
            player=new ServerPlayer(n.server,h.getLevel(),new GameProfile(UUID.randomUUID(),unique("Acct")),ClientInformation.createDefault());
            Connection connection=new Connection(PacketFlow.SERVERBOUND) {
                @Override public void send(Packet<?> packet,ChannelFutureListener listener,boolean flush) { packets.add(packet); }
            };
            new ServerGamePacketListenerImpl(n.server,connection,player,CommonListenerCookie.createInitial(player.getGameProfile(),false));
            player.gameMode.changeGameModeForPlayer(GameType.SURVIVAL);
            n.server.getPlayerList().getPlayers().add(player);
            n.server.getPlayerList().getPlayersByUUID().put(player.getUUID(),player);
            n.observe(player.nameAndId(),true);
            n.labels.join(player);
        }
        CommandSourceStack source(boolean admin) { return player.createCommandSourceStack().withPermission(admin?PermissionSet.ALL_PERMISSIONS:PermissionSet.NO_PERMISSIONS); }
        @Override public void close() {
            var n=NameLink.active;
            n.labels.disconnect(player);
            n.server.getPlayerList().getPlayers().remove(player);
            n.server.getPlayerList().getPlayersByUUID().remove(player.getUUID());
            if(player.getTeam()!=null) n.server.getScoreboard().removePlayerFromTeam(player.getScoreboardName());
        }
    }
    private static final class Scope implements AutoCloseable {
        final NicknameState before=NameLink.active.state.copy();
        @Override public void close() { NameLink.active.state=before; NameLink.active.refreshTab(); }
    }
    private static int execute(String command,CommandSourceStack source) throws Exception { return NameLink.active.server.getCommands().getDispatcher().execute(command,source); }
    private static List<String> suggestions(String command,CommandSourceStack source) throws Exception {
        var d=NameLink.active.server.getCommands().getDispatcher();
        return d.getCompletionSuggestions(d.parse(command,source)).get().getList().stream().map(com.mojang.brigadier.suggestion.Suggestion::getText).toList();
    }
    @GameTest public void displayAndUuidStaySeparate(GameTestHelper h) throws Exception {
        try(Scope s=new Scope();Client c=new Client(h)) {
            var n=NameLink.active; var p=c.player; var profile=p.getGameProfile(); String alias=unique("Nick");
            n.assign(p.getUUID(),alias);
            check(h,p.getDisplayName().getString().equals(alias),"Chat display missing");
            check(h,ChatType.bind(ChatType.CHAT,p).name().getString().equals(alias),"Signed-chat decoration sender incorrect");
            check(h,p.getTabListDisplayName().getString().equals(alias),"Tab display missing");
            check(h,p.getGameProfile()==profile && p.getName().getString().equals(profile.name()) && p.getScoreboardName().equals(profile.name()),"Account identity changed");
            check(h,c.packets.stream().anyMatch(packet->packet instanceof ClientboundPlayerInfoUpdatePacket),"Tab refresh not sent");
            n.clear(p.getUUID()); check(h,p.getTabListDisplayName()==null && p.getDisplayName().getString().equals(profile.name()),"Normal names not restored");
        } h.succeed();
    }
    @GameTest public void adminsOnlyAndPrivateFeedback(GameTestHelper h) throws Exception {
        try(Scope s=new Scope();Client owner=new Client(h);Client other=new Client(h)) {
            String alias=unique("Nick"); String command="nick set "+other.player.getGameProfile().name()+" "+alias;
            try { execute(command,owner.source(false)); throw new AssertionError("Nonadmin set was accepted"); } catch(com.mojang.brigadier.exceptions.CommandSyntaxException expected) {}
            execute(command,owner.source(true));
            check(h,alias.equals(NameLink.active.nickname(other.player.getUUID())),"Admin set failed");
            other.packets.clear(); execute("nick list",owner.source(true));
            check(h,other.packets.isEmpty(),"Admin list leaked to another player");
            execute("whois "+alias,owner.source(false));
            try { execute("nick clear "+alias,owner.source(false)); throw new AssertionError("Nonadmin clear accepted"); } catch(com.mojang.brigadier.exceptions.CommandSyntaxException expected) {}
            try { execute("nick list",owner.source(false)); throw new AssertionError("Nonadmin list accepted"); } catch(com.mojang.brigadier.exceptions.CommandSyntaxException expected) {}
            execute("nick clear "+alias,NameLink.active.server.createCommandSourceStack());
            check(h,NameLink.active.nickname(other.player.getUUID())==null,"Console clear failed");
        } h.succeed();
    }
    @GameTest public void playerArgumentsKeepSemantics(GameTestHelper h) throws Exception {
        try(Scope s=new Scope();Client c=new Client(h)) {
            var n=NameLink.active; String alias=unique("Nick"); n.assign(c.player.getUUID(),alias);
            var d=new CommandDispatcher<CommandSourceStack>(); var found=new AtomicReference<UUID>();
            d.register(literal("one").then(argument("target",EntityArgument.player()).executes(ctx->{found.set(EntityArgument.getPlayer(ctx,"target").getUUID());return 1;})));
            d.register(literal("many").then(argument("target",EntityArgument.players()).executes(ctx->EntityArgument.getPlayers(ctx,"target").size())));
            d.execute("one "+alias,c.source(false)); check(h,c.player.getUUID().equals(found.get()),"Alias resolved wrong UUID");
            d.execute("one "+c.player.getGameProfile().name(),c.source(false)); check(h,c.player.getUUID().equals(found.get()),"Username failed");
            // Vanilla 26.3 player-only arguments reject UUID tokens as entity selectors. Preserve that behavior.
            try { d.execute("one "+c.player.getUUID(),c.source(true)); throw new AssertionError("Player-only UUID behavior changed"); } catch(com.mojang.brigadier.exceptions.CommandSyntaxException expected) {}
            check(h,d.execute("many @a",c.source(true))>=1,"Multi-target selector failed");
            try { d.execute("one @a",c.source(true)); throw new AssertionError("Single-target accepted multi-selector"); } catch(com.mojang.brigadier.exceptions.CommandSyntaxException expected) {}
            try { d.execute("many @a",c.source(false)); throw new AssertionError("Selector permission bypass"); } catch(com.mojang.brigadier.exceptions.CommandSyntaxException expected) {}
            try { d.execute("many @a[name="+alias+"]",c.source(true)); throw new AssertionError("Selector filter was rewritten"); } catch(com.mojang.brigadier.exceptions.CommandSyntaxException expected) {}
        } h.succeed();
    }
    @GameTest public void unrelatedArgumentsAndEntityTargetsUntouched(GameTestHelper h) throws Exception {
        try(Scope s=new Scope();Client c=new Client(h)) {
            String alias=unique("Nick"); NameLink.active.assign(c.player.getUUID(),alias);
            var d=new CommandDispatcher<CommandSourceStack>(); var text=new AtomicReference<String>();
            d.register(literal("text").then(argument("text",StringArgumentType.greedyString()).executes(ctx->{text.set(StringArgumentType.getString(ctx,"text"));return 1;})));
            d.register(literal("entity").then(argument("target",EntityArgument.entity()).executes(ctx->{EntityArgument.getEntity(ctx,"target");return 1;})));
            String raw=alias+" {name:\""+alias+"\"} /path/"+alias+" @a[name="+alias+"]";
            d.execute("text "+raw,c.source(true)); check(h,raw.equals(text.get()),"Unrelated strings changed");
            try { d.execute("entity "+alias,c.source(true)); throw new AssertionError("Arbitrary entity argument accepted alias"); } catch(com.mojang.brigadier.exceptions.CommandSyntaxException expected) {}
            check(h,d.execute("entity "+c.player.getGameProfile().name(),c.source(true))==1,"Entity username changed");
        } h.succeed();
    }
    @GameTest public void knownOfflineProfileAliasAndOnlineConstraint(GameTestHelper h) throws Exception {
        try(Scope s=new Scope();Client c=new Client(h)) {
            var n=NameLink.active; UUID id=UUID.randomUUID(); String name=unique("Offline"),alias=unique("Nick");
            n.observe(new NameAndId(id,name),false); n.assign(id,alias);
            var d=new CommandDispatcher<CommandSourceStack>(); var target=new AtomicReference<UUID>();
            d.register(literal("profile").then(argument("target",GameProfileArgument.gameProfile()).executes(ctx->{target.set(GameProfileArgument.getGameProfiles(ctx,"target").iterator().next().id());return 1;})));
            d.register(literal("online").then(argument("target",EntityArgument.player()).executes(ctx->{EntityArgument.getPlayer(ctx,"target");return 1;})));
            d.execute("profile "+alias,c.source(true)); check(h,id.equals(target.get()),"Offline profile alias lost UUID");
            try { d.execute("online "+alias,c.source(true)); throw new AssertionError("Offline alias accepted as online target"); } catch(com.mojang.brigadier.exceptions.CommandSyntaxException expected) {}
            execute("nick clear "+name,c.source(true)); check(h,n.nickname(id)==null,"Offline admin clear failed");
        } h.succeed();
    }
    @GameTest public void autocompleteShowsAliasesAndActualNames(GameTestHelper h) throws Exception {
        try(Scope s=new Scope();Client c=new Client(h)) {
            var n=NameLink.active; String alias=unique("Nick"); n.assign(c.player.getUUID(),alias);
            var names=suggestions("msg ",c.source(false));
            check(h,names.contains(alias) && names.contains(c.player.getGameProfile().name()),"Player autocomplete lost alias or username");
            UUID offline=UUID.randomUUID();String offlineName=unique("Offline"),offlineNick=unique("Secret");n.observe(new NameAndId(offline,offlineName),false);n.assign(offline,offlineNick);
            check(h,!suggestions("whois ",c.source(false)).contains(offlineNick),"Public completion enumerated offline identities");
            check(h,suggestions("nick clear ",c.source(true)).contains(offlineNick),"Admin completion missing offline alias");
            check(h,!suggestions("msg @a[name=",c.source(true)).contains(alias),"Selector suggestions polluted");
        } h.succeed();
    }
    @GameTest public void clientCommandTreeRequestsNicknameSuggestions(GameTestHelper h) throws Exception {
        try(Scope s=new Scope();Client c=new Client(h)) {
            var n=NameLink.active; n.server.getCommands().sendCommands(c.player);
            var packet=c.packets.stream().filter(p->p instanceof ClientboundCommandsPacket).map(p->(ClientboundCommandsPacket)p).findFirst().orElseThrow();
            var tree=packet.getRoot(CommandBuildContext.simple(n.server.registryAccess(),n.server.getWorldData().enabledFeatures()),new ClientboundCommandsPacket.NodeBuilder<CommandSourceStack>() {
                public com.mojang.brigadier.builder.ArgumentBuilder<CommandSourceStack,?> createLiteral(String id) { return literal(id); }
                public com.mojang.brigadier.builder.ArgumentBuilder<CommandSourceStack,?> createArgument(String id,com.mojang.brigadier.arguments.ArgumentType<?> type,net.minecraft.resources.Identifier suggestionId) {
                    var builder=argument(id,type);
                    if(suggestionId!=null) builder.suggests(net.minecraft.commands.synchronization.SuggestionProviders.getProvider(suggestionId));
                    return builder;
                }
                public com.mojang.brigadier.builder.ArgumentBuilder<CommandSourceStack,?> configure(com.mojang.brigadier.builder.ArgumentBuilder<CommandSourceStack,?> input,boolean executable,boolean restricted) { return input; }
            });
            var targets=(com.mojang.brigadier.tree.ArgumentCommandNode<CommandSourceStack,?>)tree.getChild("msg").getChild("targets");
            check(h,targets.getCustomSuggestions()!=null && net.minecraft.commands.synchronization.SuggestionProviders.getName(targets.getCustomSuggestions()).toString().equals("minecraft:ask_server"),"Vanilla client would not request nickname completion");
            check(h,tree.getChild("nick")==null,"Restricted command leaked into ordinary player tree");
        } h.succeed();
    }
    @GameTest public void loginAndWhitelistConflictsRevokeAliases(GameTestHelper h) throws Exception {
        try(Scope s=new Scope();Client c=new Client(h)) {
            var n=NameLink.active;String alias=unique("Nick");n.assign(c.player.getUUID(),alias);
            UUID id=UUID.randomUUID();var whitelist=n.server.getPlayerList().getWhiteList();var entry=new NameAndId(id,alias);
            whitelist.add(new UserWhiteListEntry(entry));
            try { n.scanWhitelist();check(h,n.nickname(c.player.getUUID())==null && id.equals(n.resolve(alias)),"Whitelist conflict not reconciled"); }
            finally { whitelist.remove(entry); }
            String next=unique("Nick");n.assign(c.player.getUUID(),next);n.observe(new NameAndId(c.player.getUUID(),next),true);
            check(h,n.nickname(c.player.getUUID())==null,"Login rename conflict not disabled");
        } h.succeed();
    }
    @GameTest public void overheadTrackingMovementAndCleanup(GameTestHelper h) throws Exception {
        try(Scope s=new Scope();Client viewer=new Client(h);Client target=new Client(h)) {
            var n=NameLink.active;String alias=unique("Nick");n.assign(target.player.getUUID(),alias);
            var base=h.absolutePos(new BlockPos(2,3,2));viewer.player.setPos(Vec3.atCenterOf(base));target.player.setPos(Vec3.atCenterOf(base).add(2,0,0));
            n.labels.track(viewer.player,target.player);viewer.packets.clear();n.labels.tick(n);
            check(h,viewer.packets.stream().anyMatch(p->p instanceof ClientboundAddEntityPacket),"No tracked display spawn");
            check(h,viewer.packets.stream().anyMatch(p->p instanceof ClientboundSetPlayerTeamPacket t && t.getParameters().isPresent() && t.getParameters().get().nameTagVisibility()==Team.Visibility.NEVER),"Account label not hidden");
            viewer.packets.clear();target.player.setPos(target.player.position().add(.2,0,0));n.labels.tick(n);
            check(h,viewer.packets.stream().anyMatch(p->p instanceof ClientboundEntityPositionSyncPacket),"Label did not follow movement");
            viewer.packets.clear();target.player.setInvisible(true);n.labels.tick(n);
            check(h,viewer.packets.stream().anyMatch(p->p instanceof ClientboundRemoveEntitiesPacket),"Invisible label not removed");
            target.player.setInvisible(false);n.labels.tick(n);viewer.packets.clear();n.labels.untrack(viewer.player,target.player);
            check(h,viewer.packets.stream().anyMatch(p->p instanceof ClientboundRemoveEntitiesPacket),"Untracking leaked display");
            n.labels.track(viewer.player,target.player);n.labels.tick(n);n.clear(target.player.getUUID());viewer.packets.clear();n.labels.tick(n);
            check(h,viewer.packets.stream().anyMatch(p->p instanceof ClientboundRemoveEntitiesPacket),"Clear leaked display");
            check(h,n.labels.labelCount()==0,"Labels retained after clear");
        } h.succeed();
    }
    @GameTest public void overheadTeamRulesAndRespawn(GameTestHelper h) throws Exception {
        try(Scope s=new Scope();Client viewer=new Client(h);Client target=new Client(h)) {
            var n=NameLink.active;var board=n.server.getScoreboard();var team=board.addPlayerTeam(unique("Team"));
            try {
                board.addPlayerToTeam(target.player.getScoreboardName(),team); team.setAllowFriendlyFire(false);team.setCollisionRule(Team.CollisionRule.NEVER);
                n.assign(target.player.getUUID(),unique("Nick"));
                var base=h.absolutePos(new BlockPos(2,3,2));viewer.player.setPos(Vec3.atCenterOf(base));target.player.setPos(Vec3.atCenterOf(base).add(2,0,0));
                n.labels.track(viewer.player,target.player);n.labels.tick(n);
                check(h,target.player.getTeam()==team && !team.isAllowFriendlyFire() && team.getNameTagVisibility()==Team.Visibility.ALWAYS,"Server team was overwritten");
                var packet=(ClientboundSetPlayerTeamPacket)n.labels.project(n,ClientboundSetPlayerTeamPacket.createAddOrModifyPacket(team,false));
                check(h,packet.getParameters().get().collisionRule()==Team.CollisionRule.NEVER && packet.getParameters().get().nameTagVisibility()==Team.Visibility.NEVER,"Projection lost team rules");
                team.setNameTagVisibility(Team.Visibility.NEVER);viewer.packets.clear();n.labels.tick(n);
                check(h,viewer.packets.stream().anyMatch(p->p instanceof ClientboundRemoveEntitiesPacket),"Team visibility ignored");
                team.setNameTagVisibility(Team.Visibility.ALWAYS);n.labels.tick(n);viewer.packets.clear();n.labels.resetPlayer(target.player);
                check(h,viewer.packets.stream().anyMatch(p->p instanceof ClientboundRemoveEntitiesPacket),"Respawn reset retained label");
                n.labels.tick(n);viewer.packets.clear();n.labels.disconnect(target.player);
                check(h,viewer.packets.stream().anyMatch(p->p instanceof ClientboundRemoveEntitiesPacket),"Disconnect retained label");
            } finally { board.removePlayerTeam(team); }
        } h.succeed();
    }
    @GameTest public void profileCacheUpdatesReconcileUsernameConflicts(GameTestHelper h) throws Exception {
        try(Scope s=new Scope();Client c=new Client(h)) {
            var n=NameLink.active;String alias=unique("Nick");n.assign(c.player.getUUID(),alias);UUID cached=UUID.randomUUID();
            // GameTestServer supplies a mock resolver. Temporarily use the actual cache implementation to test its mixin.
            var oldServices=n.server.services();var servicesField=net.minecraft.server.MinecraftServer.class.getDeclaredField("services");servicesField.setAccessible(true);
            var cacheFile=java.nio.file.Files.createTempFile("namelink-cache-test-",".json");
            var realCache=new CachedUserNameToIdResolver(null,cacheFile.toFile());
            try {
                servicesField.set(n.server,new net.minecraft.server.Services(oldServices.sessionService(),oldServices.servicesKeySet(),oldServices.profileRepository(),realCache,oldServices.profileResolver()));
                realCache.add(new NameAndId(cached,alias));
            } finally { servicesField.set(n.server,oldServices);java.nio.file.Files.deleteIfExists(cacheFile); }
            check(h,n.nickname(c.player.getUUID())==null && cached.equals(n.resolve(alias)),"Profile-cache identity did not override nickname");
            check(h,c.player.getTabListDisplayName()==null,"Collision did not restore ordinary Tab name");
        } h.succeed();
    }
    @GameTest public void overheadPrivacyDistanceDeathDimensionAndShutdown(GameTestHelper h) throws Exception {
        try(Scope s=new Scope();Client viewer=new Client(h);Client target=new Client(h)) {
            var n=NameLink.active; n.assign(target.player.getUUID(),unique("Nick"));
            var base=h.absolutePos(new BlockPos(2,103,2));viewer.player.setPos(Vec3.atCenterOf(base));target.player.setPos(Vec3.atCenterOf(base).add(20,0,0));n.labels.track(viewer.player,target.player);
            n.labels.tick(n);check(h,n.labels.labelCount()==1,"Ordinary in-range label missing; labels="+n.labels.labelCount()+" LOS="+viewer.player.hasLineOfSight(target.player)+" distance="+viewer.player.distanceToSqr(target.player));
            target.player.setPose(Pose.CROUCHING);n.labels.tick(n);check(h,n.labels.labelCount()==0,"Crouching label leaked beyond 16 blocks");
            target.player.setPose(Pose.STANDING);n.labels.tick(n);check(h,n.labels.labelCount()==1,"Standing label not restored");
            var wall=base.offset(10,0,0);for(int y=0;y<4;y++) h.getLevel().setBlockAndUpdate(wall.above(y),Blocks.STONE.defaultBlockState());
            check(h,!viewer.player.hasLineOfSight(target.player),"Wall fixture did not block eye ray");
            n.labels.tick(n);check(h,n.labels.labelCount()==0,"Label leaked through wall");for(int y=0;y<4;y++) h.getLevel().setBlockAndUpdate(wall.above(y),Blocks.AIR.defaultBlockState());
            target.player.setPos(Vec3.atCenterOf(base).add(40,0,0));n.labels.tick(n);check(h,n.labels.labelCount()==0,"Label leaked beyond 32 blocks");
            target.player.setPos(Vec3.atCenterOf(base).add(2,0,0));n.labels.tick(n);target.player.setHealth(0);n.labels.tick(n);check(h,n.labels.labelCount()==0,"Dead subject retained label");target.player.setHealth(20);
            target.player.gameMode.changeGameModeForPlayer(GameType.SPECTATOR);n.labels.tick(n);check(h,n.labels.labelCount()==0,"Spectator retained label");target.player.gameMode.changeGameModeForPlayer(GameType.SURVIVAL);
            n.labels.tick(n);check(h,n.labels.labelCount()==1,"Live subject label missing");
            target.player.setServerLevel(n.server.getLevel(net.minecraft.world.level.Level.NETHER));n.labels.tick(n);check(h,n.labels.labelCount()==0,"Cross-dimension label leaked");target.player.setServerLevel(h.getLevel());
            n.labels.tick(n);viewer.packets.clear();n.labels.stop(n);check(h,n.labels.labelCount()==0 && viewer.packets.stream().anyMatch(p->p instanceof ClientboundRemoveEntitiesPacket),"Shutdown leaked label");
        } h.succeed();
    }
    @GameTest public void requiredBlossomCommandArguments(GameTestHelper h) throws Exception {
        if(!FabricLoader.getInstance().isModLoaded("blossom-tpa")) { NameLink.LOGGER.info("Blossom integration assertions not exercised: optional mod absent"); h.succeed(); return; }
        try(Scope s=new Scope();Client initiator=new Client(h);Client receiver=new Client(h)) {
            var n=NameLink.active;String alias=unique("Nick"),senderAlias=unique("Nick");n.assign(receiver.player.getUUID(),alias);n.assign(initiator.player.getUUID(),senderAlias);
            for(String root:List.of("tpa","tpahere","tpaaccept","tpadeny","tpacancel")) {
                var parse=n.server.getCommands().getDispatcher().parse(root+" "+alias,initiator.source(false));
                var context=parse.getContext().build(root+" "+alias);
                check(h,EntityArgument.getPlayer(context,"target").getUUID().equals(receiver.player.getUUID()),root+" resolved wrong UUID");
                check(h,suggestions(root+" ",initiator.source(false)).contains(alias),root+" suggestions missing alias");
            }
            Object blossom=FabricLoader.getInstance().getEntrypoints("main",net.fabricmc.api.ModInitializer.class).stream().filter(e->e.getClass().getName().equals("dev.codedsakura.blossom.tpa.BlossomTpa")).findFirst().orElseThrow();
            var requestsField=blossom.getClass().getDeclaredField("activeTpas");requestsField.setAccessible(true);
            var requests=(List<?>)requestsField.get(blossom);int initial=requests.size();
            execute("tpa "+alias,initiator.source(false));check(h,requests.size()==initial+1,"Alias did not create actual request");
            Object request=requests.getLast();var receiverField=request.getClass().getDeclaredField("receiver");receiverField.setAccessible(true);
            check(h,((ServerPlayer)receiverField.get(request)).getUUID().equals(receiver.player.getUUID()),"Request receiver UUID changed");
            execute("tpa "+receiver.player.getGameProfile().name(),initiator.source(false));check(h,requests.size()==initial+1,"Alias bypassed duplicate-request policy");
            execute("tpadeny "+senderAlias,receiver.source(false));check(h,requests.size()==initial,"Targeted denial failed");
            execute("tpahere "+alias,initiator.source(false));check(h,requests.size()==initial+1,"Tpahere request missing");
            execute("tpacancel "+alias,initiator.source(false));check(h,requests.size()==initial,"Targeted cancellation failed");
            execute("tpa "+alias,initiator.source(false));execute("tpaaccept "+senderAlias,receiver.source(false));check(h,requests.size()==initial,"Targeted acceptance failed");
            execute("tpa "+alias,initiator.source(false));execute("tpadeny",receiver.source(false));check(h,requests.size()==initial,"Optional denial failed");
            execute("tpa "+alias,initiator.source(false));execute("tpacancel",initiator.source(false));check(h,requests.size()==initial,"Optional cancellation failed");
            execute("tpa "+alias,initiator.source(false));execute("tpaaccept",receiver.source(false));check(h,requests.size()==initial,"Optional acceptance failed");
        } h.succeed();
    }
    @GameTest public void quickLockTrustUsesStableUuid(GameTestHelper h) throws Exception {
        if(!FabricLoader.getInstance().isModLoaded("quicklock")) { NameLink.LOGGER.info("QuickLock integration assertions not exercised: optional mod absent"); h.succeed(); return; }
        try(Scope s=new Scope();Client owner=new Client(h);Client friend=new Client(h)) {
            var n=NameLink.active;String alias=unique("Nick");n.assign(friend.player.getUUID(),alias);
            Object protection=Class.forName("io.github.josephkelly.quicklock.Protection").getField("active").get(null);
            Object lockState=protection.getClass().getField("state").get(protection);
            @SuppressWarnings("unchecked") Map<UUID,String> names=(Map<UUID,String>)lockState.getClass().getField("names").get(lockState);
            names.put(owner.player.getUUID(),owner.player.getGameProfile().name());names.put(friend.player.getUUID(),friend.player.getGameProfile().name());
            @SuppressWarnings("unchecked") Map<UUID,Set<UUID>> trust=(Map<UUID,Set<UUID>>)lockState.getClass().getField("globalTrust").get(lockState);
            execute("trust-all "+alias,owner.source(false));
            check(h,trust.getOrDefault(owner.player.getUUID(),Set.of()).contains(friend.player.getUUID()),"QuickLock trust-all did not use UUID");
            n.assign(friend.player.getUUID(),unique("Renamed"));n.clear(friend.player.getUUID());
            check(h,trust.get(owner.player.getUUID()).contains(friend.player.getUUID()),"Nickname rename/clear corrupted trust");
            execute("untrust-all "+friend.player.getGameProfile().name(),owner.source(false));
            check(h,!trust.get(owner.player.getUUID()).contains(friend.player.getUUID()),"QuickLock username untrust failed");
            n.assign(friend.player.getUUID(),alias);execute("trust-all "+alias,owner.source(false));execute("untrust-all "+alias,owner.source(false));
            check(h,!trust.get(owner.player.getUUID()).contains(friend.player.getUUID()),"QuickLock alias untrust failed");
            BlockPos pos=h.absolutePos(new BlockPos(1,2,1));h.getLevel().setBlockAndUpdate(pos,Blocks.BARREL.defaultBlockState());
            String key=h.getLevel().dimension().identifier()+"|"+pos.asLong();
            lockState.getClass().getMethod("placed",String.class,UUID.class,String.class).invoke(lockState,key,owner.player.getUUID(),"minecraft:barrel");
            Object lock=lockState.getClass().getMethod("protect",Collection.class,UUID.class,boolean.class,boolean.class).invoke(lockState,List.of(key),owner.player.getUUID(),false,false);
            owner.player.setPos(pos.getX()+.5,pos.getY(),pos.getZ()+3);
            owner.player.lookAt(EntityAnchorArgument.Anchor.EYES,Vec3.atCenterOf(pos));
            @SuppressWarnings("unchecked") Set<UUID> localTrust=(Set<UUID>)lock.getClass().getField("trusted").get(lock);
            for(String command:List.of("trust","untrust")) {
                check(h,suggestions(command+" ",owner.source(false)).contains(alias),command+" missing alias completion");
                execute(command+" "+alias,owner.source(false));
                check(h,localTrust.contains(friend.player.getUUID())==command.equals("trust"),command+" did not change container trust for UUID");
            }
            lockState.getClass().getMethod("unlock",lock.getClass()).invoke(lockState,lock);
            // Offline known QuickLock cache must resolve the exact UUID as well.
            n.server.getPlayerList().getPlayersByUUID().remove(friend.player.getUUID());n.server.getPlayerList().getPlayers().remove(friend.player);
            execute("trust-all "+alias,owner.source(false));
            check(h,trust.get(owner.player.getUUID()).contains(friend.player.getUUID()),"QuickLock offline alias did not use UUID");
            trust.get(owner.player.getUUID()).remove(friend.player.getUUID());
            UUID conflicting=UUID.randomUUID();names.put(conflicting,friend.player.getGameProfile().name());
            try { execute("trust-all "+alias,owner.source(false));throw new AssertionError("Ambiguous QuickLock cache accepted alias"); }
            catch(com.mojang.brigadier.exceptions.CommandSyntaxException expected) {}
            check(h,!trust.get(owner.player.getUUID()).contains(friend.player.getUUID()) && !trust.get(owner.player.getUUID()).contains(conflicting),"Rejected alias changed trust");
            names.remove(conflicting);
            String reserved=unique("Cache");n.assign(owner.player.getUUID(),reserved);names.put(conflicting,reserved);
            io.github.josephkelly.namelink.compat.QuickLockBridge.importIdentities(n);
            check(h,n.nickname(owner.player.getUUID())==null && conflicting.equals(n.resolve(reserved)),"QuickLock-only username did not reserve identity");
            trust.remove(owner.player.getUUID());names.remove(owner.player.getUUID());names.remove(friend.player.getUUID());names.remove(conflicting);
        } h.succeed();
    }
}
