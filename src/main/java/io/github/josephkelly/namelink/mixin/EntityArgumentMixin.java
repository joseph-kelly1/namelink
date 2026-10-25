package io.github.josephkelly.namelink.mixin;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.*;
import java.util.concurrent.CompletableFuture;
import io.github.josephkelly.namelink.AliasSelector;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.selector.EntitySelector;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
@Mixin(EntityArgument.class)
public abstract class EntityArgumentMixin implements io.github.josephkelly.namelink.PlayerArgumentKind {
    @Override public boolean namelink$isPlayersOnly() { return playersOnly; }
    @Shadow @Final private boolean playersOnly;
    @Inject(method="parse(Lcom/mojang/brigadier/StringReader;Z)Lnet/minecraft/commands/arguments/selector/EntitySelector;",at=@At("RETURN"))
    private void namelink$mark(StringReader reader,boolean allow,CallbackInfoReturnable<EntitySelector> cir) {
        if(playersOnly) ((AliasSelector)cir.getReturnValue()).namelink$enableAliases();
    }
    @Inject(method="listSuggestions",at=@At("RETURN"),cancellable=true)
    private <S> void namelink$suggest(CommandContext<S> context,SuggestionsBuilder builder,CallbackInfoReturnable<CompletableFuture<Suggestions>> cir) {
        if(playersOnly) cir.setReturnValue(io.github.josephkelly.namelink.Suggestions.merge(context,builder,cir.getReturnValue(),false));
    }
}
