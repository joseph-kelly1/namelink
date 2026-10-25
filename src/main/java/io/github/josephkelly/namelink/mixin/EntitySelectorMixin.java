package io.github.josephkelly.namelink.mixin;
import io.github.josephkelly.namelink.*;
import java.util.*;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.selector.EntitySelector;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
@Mixin(EntitySelector.class)
public abstract class EntitySelectorMixin implements AliasSelector {
    @Shadow @Final private String playerName;
    @Shadow @Final private boolean usesSelector;
    @Unique private boolean namelink$aliases;
    @Override public void namelink$enableAliases() { namelink$aliases=true; }
    @Inject(method="findPlayers",at=@At("HEAD"),cancellable=true)
    private void namelink$players(CommandSourceStack source,CallbackInfoReturnable<List<ServerPlayer>> cir) {
        var n=NameLink.active;
        if(n!=null && namelink$aliases && !usesSelector && playerName!=null && source.getServer()==n.server) {
            ServerPlayer target=n.aliasPlayer(playerName);
            if(target!=null) cir.setReturnValue(List.of(target));
        }
    }
}
