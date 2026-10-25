package io.github.josephkelly.namelink.mixin;
import java.util.*;
import net.minecraft.network.protocol.game.ClientboundSetPlayerTeamPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.*;
@Mixin(ClientboundSetPlayerTeamPacket.class)
public interface TeamPacketAccessor {
    @Accessor("method") int namelink$method();
    @Invoker("<init>") static ClientboundSetPlayerTeamPacket namelink$create(String name,int method,Optional<ClientboundSetPlayerTeamPacket.Parameters> parameters,Collection<String> players) { throw new AssertionError(); }
}
