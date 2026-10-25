package io.github.josephkelly.namelink;

import com.google.gson.*;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** Fail closed on malformed data; never overwrite a file that could not be loaded. */
public final class StateStore {
    private static final Gson JSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private final Path path;
    public StateStore(Path path) { this.path = path; }
    public NicknameState load() throws IOException {
        if (!Files.exists(path)) return new NicknameState();
        try {
            if (Files.size(path) > 8 * 1024 * 1024) throw new IllegalArgumentException("NameLink storage exceeds 8 MiB limit.");
            JsonObject root = JsonParser.parseString(Files.readString(path)).getAsJsonObject();
            if (!root.get("version").getAsString().equals("1")) throw new IllegalArgumentException("Unsupported NameLink schema version.");
            List<NicknameState.Identity> records = new ArrayList<>();
            for (JsonElement element : root.getAsJsonArray("identities")) {
                JsonObject o = element.getAsJsonObject();
                Set<String> names = new LinkedHashSet<>();
                for (var n : o.getAsJsonArray("usernames")) names.add(n.getAsString());
                records.add(new NicknameState.Identity(uuid(o.get("uuid").getAsString()), o.get("username").getAsString(), names,
                        o.has("nickname") ? o.get("nickname").getAsString() : null));
            }
            Map<String, UUID> reservations = new HashMap<>();
            for (var e : root.getAsJsonObject("reservations").entrySet()) reservations.put(e.getKey(), uuid(e.getValue().getAsString()));
            return NicknameState.restore(records, reservations);
        } catch (RuntimeException e) { throw new IOException("Invalid NameLink data at " + path + "; restore a backup or repair it before startup.", e); }
    }
    private static UUID uuid(String value) {
        UUID id = UUID.fromString(value);
        if (!id.toString().equals(value)) throw new IllegalArgumentException("Noncanonical UUID.");
        return id;
    }
    public void save(NicknameState state) throws IOException {
        JsonObject root = new JsonObject(); root.addProperty("version", 1);
        JsonArray records = new JsonArray();
        for (var i : state.identities().stream().sorted(Comparator.comparing(n -> n.id().toString())).toList()) {
            JsonObject o = new JsonObject(); o.addProperty("uuid", i.id().toString()); o.addProperty("username", i.username());
            JsonArray names = new JsonArray(); i.usernames().stream().sorted().forEach(names::add); o.add("usernames", names);
            if (i.nickname() != null) o.addProperty("nickname", i.nickname());
            records.add(o);
        }
        root.add("identities", records);
        JsonObject reservations = new JsonObject();
        new TreeMap<>(state.reservations()).forEach((name, id) -> reservations.addProperty(name, id.toString()));
        root.add("reservations", reservations);
        Files.createDirectories(path.getParent());
        Path temp = Files.createTempFile(path.getParent(), "namelink-", ".tmp");
        try {
            byte[] bytes = (JSON.toJson(root) + "\n").getBytes(StandardCharsets.UTF_8);
            try (FileChannel channel = FileChannel.open(temp, StandardOpenOption.WRITE)) {
                ByteBuffer buffer = ByteBuffer.wrap(bytes);
                while (buffer.hasRemaining()) channel.write(buffer);
                channel.force(true);
            }
            Files.move(temp, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } finally { Files.deleteIfExists(temp); }
    }
}
