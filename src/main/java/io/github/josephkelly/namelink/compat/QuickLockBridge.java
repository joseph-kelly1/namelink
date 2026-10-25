package io.github.josephkelly.namelink.compat;
import io.github.josephkelly.namelink.*;
import com.mojang.brigadier.exceptions.*;
import net.minecraft.network.chat.Component;
import java.util.*;

/** Only called by a version-gated optional mixin. No QuickLock class in a linked descriptor. */
public final class QuickLockBridge {
    private QuickLockBridge() {}
    public static String target(String token) throws CommandSyntaxException {
        var n=NameLink.active;
        if(n==null || !n.config.quickLock()) return token;
        UUID id=n.state.alias(token);
        if(id==null) return token;
        try {
            Object protection=Class.forName("io.github.josephkelly.quicklock.Protection").getField("active").get(null);
            if(protection==null) throw new ReflectiveOperationException("QuickLock not ready");
            Object state=protection.getClass().getField("state").get(protection);
            @SuppressWarnings("unchecked") Map<UUID,String> names=(Map<UUID,String>)state.getClass().getField("names").get(state);
            var online=n.server.getPlayerList().getPlayer(id);
            String candidate=online!=null ? online.getGameProfile().name() : names.get(id);
            if(candidate!=null) {
                var byName=n.server.getPlayerList().getPlayerByName(candidate);
                if(byName!=null && byName.getUUID().equals(id)) return candidate;
                if(byName==null && id.equals(n.state.resolve(candidate)) && names.entrySet().stream().filter(e->candidate.equalsIgnoreCase(e.getValue())).count()==1
                        && candidate.equalsIgnoreCase(names.get(id))) return candidate;
            }
        } catch(ReflectiveOperationException | RuntimeException e) { NameLink.LOGGER.error("QuickLock alias adapter unavailable",e); }
        throw new SimpleCommandExceptionType(Component.literal("Cannot safely resolve that nickname in QuickLock. Ask the player to join, then use their Minecraft username.")).create();
    }
}
