package io.github.josephkelly.namelink.mixin;
import io.github.josephkelly.namelink.NameLink;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
@Mixin(ServerPlayer.class)
public abstract class TabDisplayMixin {
    @Inject(method="getTabListDisplayName",at=@At("HEAD"),cancellable=true)
    private void namelink$tab(CallbackInfoReturnable<Component> cir) {
        var n=NameLink.active;
        if(n!=null && n.config.tab()) {
            String nickname=n.nickname(((ServerPlayer)(Object)this).getUUID());
            if(nickname!=null) cir.setReturnValue(Component.literal(nickname));
        }
    }
}
