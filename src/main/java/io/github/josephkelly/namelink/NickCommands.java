package io.github.josephkelly.namelink;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.*;
import net.minecraft.network.chat.Component;
import net.minecraft.server.permissions.Permissions;
import java.io.IOException;
import java.util.*;

public final class NickCommands {
    private NickCommands() {}
    private static final java.util.function.Predicate<CommandSourceStack> ADMIN = net.fabricmc.fabric.api.permission.v1.PermissionPredicates.require(
            net.fabricmc.fabric.api.permission.v1.PermissionNode.of("namelink", "admin"), net.minecraft.server.permissions.PermissionLevel.GAMEMASTERS);
    public static boolean admin(CommandSourceStack source) { return ADMIN.test(source); }
    public static void register(CommandDispatcher<CommandSourceStack> d) {
        d.register(Commands.literal("nick").requires(NickCommands::admin)
                .then(Commands.literal("set").then(player().then(Commands.argument("nickname", StringArgumentType.word()).executes(c -> modify(c, true)))))
                .then(Commands.literal("clear").then(player().executes(c -> modify(c, false))))
                .then(Commands.literal("list").executes(c -> {
                    var n = NameLink.active;
                    if (n == null) return reply(c, "NameLink is not ready.");
                    var entries = n.state.identities().stream().filter(i -> i.nickname() != null).sorted(Comparator.comparing(NicknameState.Identity::username)).toList();
                    if (entries.isEmpty()) return reply(c, "No nicknames assigned.");
                    for (var i : entries) reply(c, i.username() + " → " + i.nickname());
                    return entries.size();
                })));
        d.register(Commands.literal("whois").then(Commands.argument("name", StringArgumentType.word())
                .suggests((c,b) -> SharedSuggestionProvider.suggest(suggestions(c.getSource()),b))
                .executes(c -> {
                    var n = NameLink.active;
                    if (n == null) return reply(c,"NameLink is not ready.");
                    UUID id = n.resolve(StringArgumentType.getString(c,"name"));
                    if (id == null) return reply(c,"Unknown or ambiguous name. Use the current Minecraft username.");
                    var i = n.state.identity(id);
                    return reply(c, i == null ? "Player identity not yet recorded." : "Minecraft username: " + i.username() + "; nickname: " + (i.nickname() == null ? "none" : i.nickname()) + ".");
                })));
        d.register(Commands.literal("namelink").then(Commands.literal("help").executes(c -> reply(c,
                "NameLink: /whois <nickname-or-username>. Nicknames work in supported player arguments; real usernames still work. "
                + "Your account, skin and data stay unchanged. " + (admin(c.getSource()) ? "/nick set <player> <nickname>; /nick clear <player>; /nick list. Names: 3–16 ASCII letters, digits, underscores." : "Ask an admin to assign or change your nickname.")))));
    }
    private static com.mojang.brigadier.builder.RequiredArgumentBuilder<CommandSourceStack,String> player() {
        return Commands.argument("player",StringArgumentType.word()).suggests((c,b) -> SharedSuggestionProvider.suggest(suggestions(c.getSource()),b));
    }
    static List<String> suggestions(CommandSourceStack source) {
        var n = NameLink.active;
        if (n == null) return List.of();
        // Only admins enumerate offline identities. Public whois can query a name already known to its caller.
        return n.state.identities().stream().filter(i -> admin(source) || n.server.getPlayerList().getPlayer(i.id()) != null)
                .flatMap(i -> i.nickname() == null ? java.util.stream.Stream.of(i.username()) : java.util.stream.Stream.of(i.username(),i.nickname()))
                .distinct().sorted().toList();
    }
    private static int modify(CommandContext<CommandSourceStack> c, boolean assign) {
        if (!admin(c.getSource())) return 0;
        var n = NameLink.active;
        if (n == null) return reply(c,"NameLink is not ready.");
        n.scanWhitelist();
        UUID id = n.resolve(StringArgumentType.getString(c,"player"));
        if (id == null) return reply(c,"Unknown or ambiguous player. Use a current known Minecraft username.");
        try {
            boolean changed = assign ? n.assign(id,StringArgumentType.getString(c,"nickname")) : n.clear(id);
            if (!changed) return reply(c,assign ? "That nickname is already assigned to this player." : "This player has no nickname.");
            return reply(c,assign ? "Nickname assigned to " + n.state.identity(id).username() + ": " + n.nickname(id) + "." : "Nickname cleared for " + n.state.identity(id).username() + ".");
        } catch (IllegalArgumentException e) { c.getSource().sendFailure(Component.literal(e.getMessage())); return 0; }
        catch (IOException e) { NameLink.LOGGER.error("Nickname change was not saved",e); c.getSource().sendFailure(Component.literal("Storage write failed; nickname was not changed. Check server logs.")); return 0; }
    }
    private static int reply(CommandContext<CommandSourceStack> c, String text) { c.getSource().sendSuccess(() -> Component.literal(text),false); return 1; }
}
