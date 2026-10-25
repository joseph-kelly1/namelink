package io.github.josephkelly.namelink.mixin;
import io.github.josephkelly.namelink.NameLink;
import net.minecraft.server.players.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(CachedUserNameToIdResolver.class)
public abstract class ProfileCacheMixin {
    @Inject(method="safeAdd",at=@At("TAIL"))
    private void namelink$identity(@Coerce Object info,CallbackInfo ci) {
        var n=NameLink.active;
        if(n!=null && n.server.services().nameToIdCache()==(Object)this) n.observe(((ProfileInfoAccessor)info).namelink$identity(),false);
    }
}
