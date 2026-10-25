package io.github.josephkelly.namelink;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.*;
import net.fabricmc.fabric.api.networking.v1.*;
import net.fabricmc.fabric.api.entity.event.v1.*;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.NameAndId;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket;
import net.minecraft.world.level.storage.LevelResource;
import io.github.josephkelly.namelink.mixin.*;
import org.slf4j.*;
import java.io.IOException;
import java.util.*;

public final class NameLink implements ModInitializer {
    public static final Logger LOGGER = LoggerFactory.getLogger("namelink");
    public static volatile NameLink active;
    public MinecraftServer server;
    public NicknameState state;
    public NameLinkConfig config;
    private StateStore store;
    public final OverheadLabels labels = new OverheadLabels();
    @Override public void onInitialize() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registry, environment) -> NickCommands.register(dispatcher));
        ServerLifecycleEvents.SERVER_STARTING.register(s -> {
            try {
                server = s;
                config = NameLinkConfig.load(FabricLoader.getInstance().getConfigDir().resolve("namelink.json"));
                store = new StateStore(s.getWorldPath(LevelResource.ROOT).resolve("data/namelink.json"));
                state = store.load();
                active = this;
                if (s.services().nameToIdCache() instanceof ProfileCacheAccessor cache) {
                    for (Object value : cache.namelink$profiles().values()) {
                        var p = ((ProfileInfoAccessor) value).namelink$identity();
                        state.observe(p.id(), p.name(), false);
                    }
                }
                scanWhitelist();
                store.save(state);
                notices();
                LOGGER.info("NameLink loaded {} identities; authentication profiles remain unchanged", state.identities().size());
            } catch (Exception e) { active = null; throw new IllegalStateException("NameLink cannot safely load identity data; refusing startup", e); }
        });
        ServerLifecycleEvents.SERVER_STARTED.register(s -> { scanWhitelist(); notices(); });
        ServerPlayConnectionEvents.JOIN.register((handler, sender, s) -> {
            observe(handler.player.nameAndId(), true);
            labels.join(handler.player);
            refreshTab();
        });
        EntityTrackingEvents.START_TRACKING.register((entity, viewer) -> { if (entity instanceof ServerPlayer target) labels.track(viewer, target); });
        EntityTrackingEvents.STOP_TRACKING.register((entity, viewer) -> { if (entity instanceof ServerPlayer target) labels.untrack(viewer, target); });
        ServerPlayConnectionEvents.DISCONNECT.register((handler, s) -> labels.disconnect(handler.player));
        ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> labels.resetPlayer(newPlayer));
        ServerEntityLevelChangeEvents.AFTER_PLAYER_CHANGE_LEVEL.register((player, origin, destination) -> labels.resetPlayer(player));
        ServerTickEvents.END_SERVER_TICK.register(s -> {
            if (active != this) return;
            if (s.getTickCount() % 100 == 0) scanWhitelist();
            labels.tick(this);
        });
        ServerLifecycleEvents.SERVER_STOPPING.register(s -> { if (active == this) labels.stop(this); });
        ServerLifecycleEvents.SERVER_STOPPED.register(s -> { if (active == this) active = null; labels.reset(); });
    }
    public void scanWhitelist() {
        if (server.getPlayerList() == null) return;
        for (var entry : server.getPlayerList().getWhiteList().getEntries()) if (entry.getUser() != null) observe(entry.getUser(), false);
    }
    public void observe(NameAndId profile, boolean authoritative) {
        if (!server.isSameThread()) { server.execute(() -> observe(profile, authoritative)); return; }
        if (!NicknameState.validUsername(profile.name())) { LOGGER.warn("Ignoring invalid cached account name for {}", profile.id()); return; }
        // Small identity writes occur only when a record changes, never every tick/command.
        var known = state.identity(profile.id());
        if (known != null && known.usernames().contains(profile.name()) && (!authoritative || known.username().equals(profile.name()))) return;
        var next = state.copy();
        if (next.observe(profile.id(), profile.name(), authoritative)) {
            // Real usernames must win in memory even if disk fails; stop rather than continue with divergent data.
            state = next;
            try { store.save(next); }
            catch (IOException e) { throw new IllegalStateException("Cannot persist NameLink identity update; stop and repair storage", e); }
            notices(); refreshTab();
        }
    }
    public boolean assign(UUID id, String nickname) throws IOException {
        scanWhitelist();
        var next = state.copy();
        if (!next.assign(id, nickname)) return false;
        store.save(next); state = next; refreshTab(); return true;
    }
    public boolean clear(UUID id) throws IOException {
        var next = state.copy();
        if (!next.clear(id)) return false;
        store.save(next); state = next; refreshTab(); return true;
    }
    public void refreshTab() {
        if (server == null || server.getPlayerList() == null || config == null || !config.tab()) return;
        var players = server.getPlayerList().getPlayers();
        if (!players.isEmpty()) server.getPlayerList().broadcastAll(new ClientboundPlayerInfoUpdatePacket(EnumSet.of(ClientboundPlayerInfoUpdatePacket.Action.UPDATE_DISPLAY_NAME), players));
    }
    private void notices() {
        for (String notice : state.drainNotices()) {
            LOGGER.warn(notice);
            if (server.getPlayerList() != null) for (var p : server.getPlayerList().getPlayers()) if (NickCommands.admin(p.createCommandSourceStack())) p.sendSystemMessage(Component.literal("[NameLink] " + notice));
        }
    }
    public String nickname(UUID id) { var i = state.identity(id); return i == null ? null : i.nickname(); }
    public UUID resolve(String token) {
        if (!NicknameState.validUsername(token)) return null;
        var online = server.getPlayerList().getPlayerByName(token);
        return online == null ? state.resolve(token) : online.getUUID();
    }
    public ServerPlayer aliasPlayer(String token) {
        UUID id = config.vanillaPlayerArguments() ? state.alias(token) : null;
        return id == null ? null : server.getPlayerList().getPlayer(id);
    }
    public List<String> onlineAliases() {
        return server.getPlayerList().getPlayers().stream().map(p -> nickname(p.getUUID())).filter(Objects::nonNull).sorted().toList();
    }
}
