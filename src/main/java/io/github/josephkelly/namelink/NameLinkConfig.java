package io.github.josephkelly.namelink;

import com.google.gson.*;
import java.io.IOException;
import java.nio.file.*;

public record NameLinkConfig(boolean chat, boolean tab, boolean overhead, boolean vanillaPlayerArguments, boolean quickLock) {
    public static NameLinkConfig load(Path path) throws IOException {
        if (!Files.exists(path)) {
            Files.createDirectories(path.getParent());
            Files.writeString(path, "{\n  \"chat\": true,\n  \"tab\": true,\n  \"overhead\": true,\n  \"vanillaPlayerArguments\": true,\n  \"quickLock\": true\n}\n");
        }
        try {
            var o = JsonParser.parseString(Files.readString(path)).getAsJsonObject();
            for (var e : o.entrySet()) if (!java.util.Set.of("chat", "tab", "overhead", "vanillaPlayerArguments", "quickLock").contains(e.getKey())
                    || !e.getValue().isJsonPrimitive() || !e.getValue().getAsJsonPrimitive().isBoolean())
                throw new IllegalArgumentException("Unknown option or non-boolean: " + e.getKey());
            return new NameLinkConfig(value(o,"chat"), value(o,"tab"), value(o,"overhead"), value(o,"vanillaPlayerArguments"), value(o,"quickLock"));
        } catch (RuntimeException e) { throw new IOException("Invalid NameLink config " + path, e); }
    }
    private static boolean value(JsonObject o, String key) { return !o.has(key) || o.get(key).getAsBoolean(); }
}
