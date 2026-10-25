package io.github.josephkelly.namelink.mixin;
import io.github.josephkelly.namelink.NameLink;
import net.minecraft.server.network.ServerCommonPacketListenerImpl;
import net.minecraft.network.protocol.Packet;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
@Mixin(ServerCommonPacketListenerImpl.class)
public abstract class PacketListenerMixin {
    @ModifyVariable(method="send(Lnet/minecraft/network/protocol/Packet;Lio/netty/channel/ChannelFutureListener;)V",at=@At("HEAD"),argsOnly=true)
    private Packet<?> namelink$teams(Packet<?> packet) {
        var n=NameLink.active;
        return n==null ? packet : n.labels.project(n,packet);
    }
}
