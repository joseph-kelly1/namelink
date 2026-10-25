package io.github.josephkelly.namelink.mixin;
import io.github.josephkelly.namelink.*;
import io.github.josephkelly.namelink.compat.QuickLockBridge;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.minecraft.commands.CommandSourceStack;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
@Pseudo
@Mixin(targets="io.github.josephkelly.quicklock.LockCommands",remap=false)
public abstract class QuickLockCommandsMixin {
    @ModifyExpressionValue(method="run",at=@At(value="INVOKE",target="Lcom/mojang/brigadier/arguments/StringArgumentType;getString(Lcom/mojang/brigadier/context/CommandContext;Ljava/lang/String;)Ljava/lang/String;"))
    private static String namelink$target(String token) throws CommandSyntaxException { return QuickLockBridge.target(token); }
    @ModifyArg(method="register",at=@At(value="INVOKE",target="Lcom/mojang/brigadier/builder/RequiredArgumentBuilder;suggests(Lcom/mojang/brigadier/suggestion/SuggestionProvider;)Lcom/mojang/brigadier/builder/RequiredArgumentBuilder;"),index=0)
    private static SuggestionProvider<CommandSourceStack> namelink$suggestions(SuggestionProvider<CommandSourceStack> original) {
        return (context,builder) -> {
            var n=NameLink.active;
            var result=original.getSuggestions(context,builder);
            if(n==null || !n.config.quickLock() || builder.getRemaining().startsWith("@")) return result;
            var extra=net.minecraft.commands.SharedSuggestionProvider.suggest(n.state.identities().stream().filter(i->i.nickname()!=null).map(NicknameState.Identity::nickname),builder.createOffset(builder.getStart()));
            return result.thenCombine(extra,(a,b)->com.mojang.brigadier.suggestion.Suggestions.merge(builder.getInput(),java.util.List.of(a,b)));
        };
    }
}
