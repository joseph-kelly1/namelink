package io.github.josephkelly.namelink;

import java.util.*;
import java.util.regex.Pattern;

/** Pure identity policy. UUIDs own assignments; historical names never become aliases. */
public final class NicknameState {
    private static final Pattern USERNAME = Pattern.compile("[A-Za-z0-9_]{1,16}");
    private static final Pattern NAME = Pattern.compile("[A-Za-z0-9_]{3,16}");
    public record Identity(UUID id, String username, Set<String> usernames, String nickname) {
        public Identity { usernames = Set.copyOf(usernames); }
    }
    private final Map<UUID, Identity> identities = new LinkedHashMap<>();
    private final Map<String, Set<UUID>> usernames = new HashMap<>();
    private final Map<String, UUID> aliases = new HashMap<>();
    private final Map<String, UUID> reservations = new LinkedHashMap<>();
    private final List<String> notices = new ArrayList<>();
    public static String key(String name) { return name.toLowerCase(Locale.ROOT); }
    public static boolean validUsername(String name) { return name != null && USERNAME.matcher(name).matches(); }
    public static boolean valid(String name) { return name != null && NAME.matcher(name).matches(); }
    public Collection<Identity> identities() { return List.copyOf(identities.values()); }
    public Map<String, UUID> reservations() { return Map.copyOf(reservations); }
    public Identity identity(UUID id) { return identities.get(id); }
    public UUID alias(String name) { return valid(name) ? aliases.get(key(name)) : null; }
    public boolean isUsername(String name) { return usernames.containsKey(key(name)); }
    public List<String> drainNotices() { var result = List.copyOf(notices); notices.clear(); return result; }
    public NicknameState copy() {
        NicknameState result = new NicknameState();
        for (var i : identities.values()) result.insert(i);
        result.reservations.putAll(reservations);
        result.notices.addAll(notices);
        return result;
    }
    private void insert(Identity i) {
        identities.put(i.id, i);
        for (String name : i.usernames) usernames.computeIfAbsent(key(name), ignored -> new HashSet<>()).add(i.id);
        if (i.nickname != null) aliases.put(key(i.nickname), i.id);
    }
    /** Cache/whitelist records reserve names; only a join authoritatively changes a known current name. */
    public boolean observe(UUID id, String name, boolean authoritative) {
        Objects.requireNonNull(id);
        if (!validUsername(name)) throw new IllegalArgumentException("Invalid account name: " + name);
        Identity old = identities.get(id);
        Set<String> names = new HashSet<>(old == null ? Set.of() : old.usernames);
        boolean added = names.add(name);
        String current = old == null || authoritative ? name : old.username;
        if (!added && old != null && old.username.equals(current)) return false;
        UUID conflict = aliases.remove(key(name));
        if (conflict != null) {
            Identity owner = identities.get(conflict);
            identities.put(conflict, new Identity(owner.id, owner.username, owner.usernames, null));
            notices.add("Disabled nickname " + owner.nickname + " for " + owner.username + " (" + owner.id
                    + "): real username " + name + " takes priority.");
        }
        // Fetch again: the conflicting nickname may have belonged to this identity.
        old = identities.get(id);
        identities.put(id, new Identity(id, current, names, old == null ? null : old.nickname));
        usernames.computeIfAbsent(key(name), ignored -> new HashSet<>()).add(id);
        return true;
    }
    public UUID resolve(String token) {
        if (!validUsername(token)) return null;
        String k = key(token);
        Set<UUID> historical = usernames.get(k);
        if (historical != null) {
            var current = historical.stream().filter(id -> key(identities.get(id).username).equals(k)).toList();
            if (current.size() == 1) return current.getFirst();
            // Historical names are reservations, never redirects to former owners.
            return null;
        }
        return aliases.get(k);
    }
    public boolean assign(UUID id, String nickname) {
        Identity target = identities.get(id);
        if (target == null) throw new IllegalArgumentException("Unknown player. They must join or be known to the server first.");
        if (!valid(nickname)) throw new IllegalArgumentException("Nickname must be 3–16 ASCII letters, digits, or underscores.");
        if (target.nickname != null && target.nickname.equals(nickname)) return false;
        String k = key(nickname);
        if (usernames.containsKey(k)) throw new IllegalArgumentException("Nickname is reserved by a known Minecraft username (including username history).");
        UUID owner = reservations.get(k);
        if (owner != null && !owner.equals(id)) throw new IllegalArgumentException("Nickname is assigned or historically reserved by another UUID.");
        if (target.nickname != null) aliases.remove(key(target.nickname));
        identities.put(id, new Identity(id, target.username, target.usernames, nickname));
        reservations.put(k, id);
        aliases.put(k, id);
        return true;
    }
    public boolean clear(UUID id) {
        Identity target = identities.get(id);
        if (target == null) throw new IllegalArgumentException("Unknown player.");
        if (target.nickname == null) return false;
        aliases.remove(key(target.nickname));
        identities.put(id, new Identity(id, target.username, target.usernames, null));
        return true;
    }
    /** Strict structural load; username conflicts disable aliases with an explicit notice. */
    static NicknameState restore(Collection<Identity> records, Map<String, UUID> reserved) {
        NicknameState result = new NicknameState();
        for (Identity i : records) {
            if (i.id == null || !validUsername(i.username) || i.usernames.isEmpty() || !i.usernames.contains(i.username)
                    || i.usernames.stream().anyMatch(n -> !validUsername(n)) || i.nickname != null && !valid(i.nickname)
                    || result.identities.containsKey(i.id)) throw new IllegalArgumentException("Invalid or duplicate identity record.");
            if (i.nickname != null && result.aliases.containsKey(key(i.nickname))) throw new IllegalArgumentException("Duplicate nickname in storage.");
            result.insert(i);
        }
        for (var e : reserved.entrySet()) {
            if (!valid(e.getKey()) || !key(e.getKey()).equals(e.getKey()) || !result.identities.containsKey(e.getValue()))
                throw new IllegalArgumentException("Invalid nickname reservation.");
            result.reservations.put(e.getKey(), e.getValue());
        }
        for (Identity i : List.copyOf(result.identities.values())) if (i.nickname != null) {
            if (!i.id.equals(result.reservations.get(key(i.nickname)))) throw new IllegalArgumentException("Nickname reservation does not match owner.");
            if (result.usernames.containsKey(key(i.nickname))) {
                result.clear(i.id);
                result.notices.add("Disabled nickname " + i.nickname + " for " + i.username + " (" + i.id + "): stored real username takes priority.");
            }
        }
        return result;
    }
}
