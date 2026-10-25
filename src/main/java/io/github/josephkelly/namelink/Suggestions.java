package io.github.josephkelly.namelink;

import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import net.minecraft.commands.*;

public final class Suggestions {
    private Suggestions() {}
    public static <S> CompletableFuture<com.mojang.brigadier.suggestion.Suggestions> merge(
            CommandContext<S> context, SuggestionsBuilder builder,
            CompletableFuture<com.mojang.brigadier.suggestion.Suggestions> original, boolean offline) {
        var n = NameLink.active;
        if (n == null || !n.config.vanillaPlayerArguments() || !(context.getSource() instanceof CommandSourceStack source)
                || source.getServer() != n.server || builder.getRemaining().startsWith("@")) return original;
        var names = offline && NickCommands.admin(source) ? NickCommands.suggestions(source) : n.onlineAliases();
        var extra = SharedSuggestionProvider.suggest(names,builder.createOffset(builder.getStart()));
        return original.thenCombine(extra,(a,b) -> com.mojang.brigadier.suggestion.Suggestions.merge(builder.getInput(),List.of(a,b)));
    }
}
