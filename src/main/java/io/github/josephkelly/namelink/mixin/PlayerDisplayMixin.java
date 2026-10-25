package io.github.josephkelly.namelink.mixin;
import io.github.josephkelly.namelink.NameLink;
import net.minecraft.world.entity.player.Player;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
@Mixin(Player.class)
public abstract class PlayerDisplayMixin {
    @ModifyArg(method="getDisplayName",at=@At(value="INVOKE",target="Lnet/minecraft/world/entity/player/Player;decorateDisplayNameComponent(Lnet/minecraft/network/chat/MutableComponent;)Lnet/minecraft/network/chat/MutableComponent;"),index=0)
    private MutableComponent namelink$display(MutableComponent original) {
        var n=NameLink.active;
        if(n!=null && n.config.chat() && (Object)this instanceof ServerPlayer p) {
            String nickname=n.nickname(p.getUUID());
            if(nickname!=null) return Component.literal(nickname);
        }
        return original;
    }
}
