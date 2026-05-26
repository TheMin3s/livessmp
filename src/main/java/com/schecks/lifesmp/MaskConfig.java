package com.schecks.lifesmp;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Per-player display-name masks: real UUID -&gt; the name everyone sees in the
 * tab list, above the player's head, in death messages, advancement
 * broadcasts, and most other "this player did X" UI lines.
 *
 * Loaded from {@code config/lifesmp/masks.json} at server start; edit the file
 * and run {@code /lives config reload} to pick up changes without restarting.
 *
 * <h3>Deliberate scope limits</h3>
 * Masks are DISPLAY ONLY. Vanilla chat still shows the player's real account
 * name as the sender (so impersonation in chat isn't possible), and the server
 * console + {@code lifesmp.log} always record the real account — the audit
 * trail is preserved. If you set a mask that happens to match another real
 * Minecraft account's name, that's on you; nothing in this file prevents it.
 */
public final class MaskConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Map<UUID, String> MASKS = new LinkedHashMap<>();
    private static volatile Path path;

    private MaskConfig() {}

    /** Load (or create) the masks file. Call once at server start. */
    public static synchronized void init(Path serverDir) {
        path = serverDir.resolve("config").resolve("lifesmp").resolve("masks.json");
        if (!Files.exists(path)) {
            writeStub();
        }
        reload();
    }

    /** Re-read the file from disk. Returns true if loading succeeded. */
    public static synchronized boolean reload() {
        if (path == null) return false;
        MASKS.clear();
        if (!Files.exists(path)) return true;
        try {
            String json = Files.readString(path, StandardCharsets.UTF_8);
            JsonObject obj = JsonParser.parseString(json).getAsJsonObject();
            for (String key : obj.keySet()) {
                try {
                    UUID id = UUID.fromString(key);
                    String name = obj.get(key).getAsString();
                    if (name != null && !name.isBlank()) {
                        MASKS.put(id, name);
                    }
                } catch (IllegalArgumentException ignored) {
                    // skip malformed entries (e.g. the _example stub)
                }
            }
            return true;
        } catch (Exception e) {
            LifeLog.warn("[lifesmp] masks.json unreadable ({}), using empty masks", e.getMessage());
            return false;
        }
    }

    /** Returns the mask name for {@code id}, or null if the player is unmasked. */
    public static String maskFor(UUID id) {
        if (id == null) return null;
        return MASKS.get(id);
    }

    private static void writeStub() {
        try {
            Files.createDirectories(path.getParent());
            Files.writeString(path,
                "{\n"
              + "  \"_example\": \"Replace this key with a player UUID and the value with the mask name; entries that aren't a valid UUID (like this one) are ignored.\"\n"
              + "}\n",
                StandardCharsets.UTF_8);
        } catch (IOException e) {
            LifeLog.warn("[lifesmp] failed to write masks.json stub: {}", e.getMessage());
        }
    }
}
