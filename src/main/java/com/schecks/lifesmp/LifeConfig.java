package com.schecks.lifesmp;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Function;

/**
 * All tunable LifeSMP mechanics (everything outside the /lives op tree).
 *
 * Persisted as {server-root}/config/lifesmp/config.json. Editable on disk or
 * live via /lives config. A single {@link #KEYS} registry drives both the
 * JSON file format and the command, so the two never drift.
 *
 * Access the live values through {@link #get()}. Before the server has
 * started (config not yet loaded) {@code get()} returns a fresh all-defaults
 * instance, so callers never NPE.
 */
public final class LifeConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static volatile LifeConfig instance;
    private static volatile Path path;

    // ---- tunable settings (field initialisers are the defaults) ----
    public int defaultLives = 10;
    public int maxLives = 15;
    public int craftLimit = 10;
    public int revivalCrystalLives = 3;
    public int lifeLossPerDeath = 1;
    public int lifeGainPerKill = 1;
    public int minLivesAfterWithdraw = 1;
    public boolean banOnZero = true;
    public boolean killRewards = true;
    public boolean tablistDisplay = true;
    public String banMessage = "You ran out of lives.";
    public String updateRepo = "TheMin3s/lifesmp";
    public boolean updateCheckOnBoot = true;

    public enum Type { INT, BOOL, TEXT }

    /** One configurable setting: its name, type, bounds, and field accessors. */
    public static final class Key {
        public final String name;
        public final String description;
        public final Type type;
        public final int min, max;                       // INT only
        public final Function<LifeConfig, Object> getter;
        public final BiConsumer<LifeConfig, Object> setter;

        Key(String name, String description, Type type, int min, int max,
            Function<LifeConfig, Object> getter, BiConsumer<LifeConfig, Object> setter) {
            this.name = name;
            this.description = description;
            this.type = type;
            this.min = min;
            this.max = max;
            this.getter = getter;
            this.setter = setter;
        }

        /** Parse a raw command/file string into the right typed value, or throw. */
        public Object parse(String raw) {
            return switch (type) {
                case BOOL -> {
                    if (raw.equalsIgnoreCase("true"))  yield Boolean.TRUE;
                    if (raw.equalsIgnoreCase("false")) yield Boolean.FALSE;
                    throw new IllegalArgumentException("expected true or false");
                }
                case INT -> {
                    int v;
                    try { v = Integer.parseInt(raw.trim()); }
                    catch (NumberFormatException e) { throw new IllegalArgumentException("expected a whole number"); }
                    if (v < min || v > max) {
                        throw new IllegalArgumentException("must be between " + min + " and " + max);
                    }
                    yield v;
                }
                case TEXT -> raw;
            };
        }

        public String display(LifeConfig cfg) {
            return String.valueOf(getter.apply(cfg));
        }
    }

    private static Key intKey(String n, String d, int min, int max,
                              Function<LifeConfig, Object> g, BiConsumer<LifeConfig, Object> s) {
        return new Key(n, d, Type.INT, min, max, g, s);
    }
    private static Key boolKey(String n, String d,
                               Function<LifeConfig, Object> g, BiConsumer<LifeConfig, Object> s) {
        return new Key(n, d, Type.BOOL, 0, 0, g, s);
    }
    private static Key textKey(String n, String d,
                               Function<LifeConfig, Object> g, BiConsumer<LifeConfig, Object> s) {
        return new Key(n, d, Type.TEXT, 0, 0, g, s);
    }

    public static final List<Key> KEYS = List.of(
        intKey("default-lives", "Lives a new player starts with", 1, 1000,
            c -> c.defaultLives, (c, v) -> c.defaultLives = (Integer) v),
        intKey("max-lives", "Hard cap on active lives", 1, 1000,
            c -> c.maxLives, (c, v) -> c.maxLives = (Integer) v),
        intKey("craft-limit", "Lifetime cap on crafting Life Shards per player", 0, 100000,
            c -> c.craftLimit, (c, v) -> c.craftLimit = (Integer) v),
        intKey("revival-crystal-lives", "Lives granted when a Revival Crystal is used", 1, 1000,
            c -> c.revivalCrystalLives, (c, v) -> c.revivalCrystalLives = (Integer) v),
        intKey("life-loss-per-death", "Lives lost on each death", 0, 1000,
            c -> c.lifeLossPerDeath, (c, v) -> c.lifeLossPerDeath = (Integer) v),
        intKey("life-gain-per-kill", "Lives the killer gains for a player kill", 0, 1000,
            c -> c.lifeGainPerKill, (c, v) -> c.lifeGainPerKill = (Integer) v),
        intKey("min-lives-after-withdraw", "Minimum lives a player must keep after /life withdraw", 0, 1000,
            c -> c.minLivesAfterWithdraw, (c, v) -> c.minLivesAfterWithdraw = (Integer) v),
        boolKey("ban-on-zero", "Ban players when they reach 0 lives",
            c -> c.banOnZero, (c, v) -> c.banOnZero = (Boolean) v),
        boolKey("kill-rewards", "Whether killing another player grants lives",
            c -> c.killRewards, (c, v) -> c.killRewards = (Boolean) v),
        boolKey("tablist-display", "Show each player's lives count in the tab list",
            c -> c.tablistDisplay, (c, v) -> c.tablistDisplay = (Boolean) v),
        textKey("ban-message", "Kick / ban message shown when a player runs out of lives",
            c -> c.banMessage, (c, v) -> c.banMessage = (String) v),
        textKey("update-repo", "GitHub owner/repo the mod checks for updates",
            c -> c.updateRepo, (c, v) -> c.updateRepo = (String) v),
        boolKey("update-check-on-boot", "Check GitHub for a newer mod version on server start",
            c -> c.updateCheckOnBoot, (c, v) -> c.updateCheckOnBoot = (Boolean) v)
    );

    public static Key keyByName(String name) {
        for (Key k : KEYS) {
            if (k.name.equalsIgnoreCase(name)) return k;
        }
        return null;
    }

    /** Live config. Never null — returns an all-defaults instance pre-load. */
    public static LifeConfig get() {
        LifeConfig i = instance;
        return i != null ? i : new LifeConfig();
    }

    /** Load (or create) the config file. Call once at server start. */
    public static synchronized void init(Path serverDir) {
        path = serverDir.resolve("config").resolve("lifesmp").resolve("config.json");
        instance = readFromDisk();
        writeToDisk(instance); // rewrites the file so any newly added keys appear
    }

    /** Re-read the file from disk (for /lives config reload). Returns true on success. */
    public static synchronized boolean reload() {
        if (path == null) return false;
        instance = readFromDisk();
        return true;
    }

    /** Persist current values to disk. */
    public static synchronized void save() {
        if (instance != null) writeToDisk(instance);
    }

    private static LifeConfig readFromDisk() {
        LifeConfig cfg = new LifeConfig();
        if (path == null || !Files.exists(path)) return cfg;
        try {
            String json = Files.readString(path, StandardCharsets.UTF_8);
            JsonObject obj = JsonParser.parseString(json).getAsJsonObject();
            for (Key k : KEYS) {
                if (!obj.has(k.name)) continue;
                try {
                    JsonElement el = obj.get(k.name);
                    Object v = switch (k.type) {
                        case BOOL -> el.getAsBoolean();
                        case INT  -> Math.max(k.min, Math.min(k.max, el.getAsInt()));
                        case TEXT -> el.getAsString();
                    };
                    k.setter.accept(cfg, v);
                } catch (Exception ignoredKey) {
                    // malformed value for this key — keep the default
                }
            }
        } catch (Exception e) {
            // unreadable file — fall back to all defaults
            LifeLog.warn("[lifesmp] config.json unreadable ({}), using defaults", e.getMessage());
        }
        return cfg;
    }

    private static void writeToDisk(LifeConfig cfg) {
        if (path == null) return;
        try {
            Files.createDirectories(path.getParent());
            JsonObject obj = new JsonObject();
            for (Key k : KEYS) {
                Object v = k.getter.apply(cfg);
                switch (k.type) {
                    case BOOL -> obj.addProperty(k.name, (Boolean) v);
                    case INT  -> obj.addProperty(k.name, (Integer) v);
                    case TEXT -> obj.addProperty(k.name, (String) v);
                }
            }
            Path tmp = Files.createTempFile(path.getParent(), ".config-", ".tmp");
            Files.writeString(tmp, GSON.toJson(obj), StandardCharsets.UTF_8);
            Files.move(tmp, path, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            LifeLog.warn("[lifesmp] failed to write config.json: {}", e.getMessage());
        }
    }

    public static List<String> keyNames() {
        List<String> names = new ArrayList<>(KEYS.size());
        for (Key k : KEYS) names.add(k.name);
        return names;
    }
}
