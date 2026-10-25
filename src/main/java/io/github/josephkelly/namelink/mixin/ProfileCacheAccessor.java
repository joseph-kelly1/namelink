package io.github.josephkelly.namelink.mixin;
import java.util.*;
import net.minecraft.server.players.CachedUserNameToIdResolver;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
@Mixin(CachedUserNameToIdResolver.class)
public interface ProfileCacheAccessor {
    @Accessor("profilesByUUID") Map<UUID,?> namelink$profiles();
    @Accessor("profilesByName") Map<String,?> namelink$namedProfiles();
}
