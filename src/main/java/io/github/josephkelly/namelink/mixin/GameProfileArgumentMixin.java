package io.github.josephkelly.namelink.mixin;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import io.github.josephkelly.namelink.NameLink;
import net.minecraft.commands.arguments.GameProfileArgument;
import net.minecraft.server.players.NameAndId;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
@Mixin(GameProfileArgument.class)
public abstract class GameProfileArgumentMixin {
    @Inject(method="parse(Lcom/mojang/brigadier/StringReader;Z)Lnet/minecraft/commands/arguments/GameProfileArgument$Result;",at=@At("HEAD"),cancellable=true)
    private static void namelink$profile(StringReader reader,boolean allow,CallbackInfoReturnable<GameProfileArgument.Result> cir) {
        int start=reader.getCursor();
        int end=start;
        while(end<reader.getTotalLength() && reader.getString().charAt(end)!=' ') end++;
        String token=reader.getString().substring(start,end);
        // Defer identity lookup until execution, as vanilla does; Brigadier parse results can be cached.
        if(io.github.josephkelly.namelink.NicknameState.valid(token) && NameLink.active!=null && NameLink.active.config.vanillaPlayerArguments() && NameLink.active.state.alias(token)!=null) {
            reader.setCursor(end);
            cir.setReturnValue(source -> {
                var n=NameLink.active;
                UUID id=n!=null && source.getServer()==n.server && n.config.vanillaPlayerArguments() ? n.resolve(token) : null;
                if(id==null) throw GameProfileArgument.ERROR_UNKNOWN_PLAYER.create();
                var i=n.state.identity(id);
                return List.of(new NameAndId(id,i.username()));
            });
        }
    }
    @Inject(method="listSuggestions",at=@At("RETURN"),cancellable=true)
    private <S> void namelink$suggest(CommandContext<S> context,SuggestionsBuilder builder,CallbackInfoReturnable<CompletableFuture<Suggestions>> cir) {
        cir.setReturnValue(io.github.josephkelly.namelink.Suggestions.merge(context,builder,cir.getReturnValue(),true));
    }
}
