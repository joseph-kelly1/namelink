package io.github.josephkelly.namelink.mixin;
import io.github.josephkelly.namelink.*;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.mojang.brigadier.builder.*;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.*;
import net.minecraft.commands.synchronization.SuggestionProviders;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
@Mixin(Commands.class)
public abstract class CommandsMixin {
    /** Client-side player argument completion otherwise sees only authenticated profile names. */
    @SuppressWarnings({"rawtypes","unchecked"})
    @ModifyExpressionValue(method="fillUsableCommands",at=@At(value="INVOKE",target="Lcom/mojang/brigadier/tree/CommandNode;createBuilder()Lcom/mojang/brigadier/builder/ArgumentBuilder;"))
    private static ArgumentBuilder namelink$requestServerSuggestions(ArgumentBuilder builder) {
        var n=NameLink.active;
        if(n!=null && n.config.vanillaPlayerArguments() && builder instanceof RequiredArgumentBuilder argument && argument.getSuggestionsProvider()==null
                && (argument.getType() instanceof GameProfileArgument || argument.getType() instanceof PlayerArgumentKind kind && kind.namelink$isPlayersOnly()))
            argument.suggests((SuggestionProvider)SuggestionProviders.ASK_SERVER);
        return builder;
    }
}
