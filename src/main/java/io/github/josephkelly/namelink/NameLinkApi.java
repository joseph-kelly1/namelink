package io.github.josephkelly.namelink;

import java.util.Optional;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;

/** Server-thread-only API. Empty when NameLink is absent/not ready or a name is unknown/ambiguous. */
public final class NameLinkApi {
    private NameLinkApi() {}
    public static Optional<UUID> resolve(MinecraftServer server, String token) {
        var n = NameLink.active;
        return n == null || n.server != server ? Optional.empty() : Optional.ofNullable(n.resolve(token));
    }
    public static Optional<String> nickname(MinecraftServer server, UUID id) {
        var n = NameLink.active;
        return n == null || n.server != server ? Optional.empty() : Optional.ofNullable(n.nickname(id));
    }
    public static Optional<String> displayName(MinecraftServer server, UUID id) {
        var n = NameLink.active;
        if (n == null || n.server != server) return Optional.empty();
        var i = n.state.identity(id);
        return i == null ? Optional.empty() : Optional.of(i.nickname() == null ? i.username() : i.nickname());
    }
}
