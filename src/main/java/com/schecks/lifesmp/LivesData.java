package com.schecks.lifesmp;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.UUIDUtil;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class LivesData extends SavedData {
    public static final int DEFAULT_LIVES = 10;
    public static final int MAX_LIVES = 15;
    public static final int CRAFT_LIMIT = 10;

    private final Map<UUID, PlayerLifeData> data;

    public LivesData() {
        this.data = new HashMap<>();
    }

    public LivesData(Map<UUID, PlayerLifeData> data) {
        this.data = new HashMap<>(data);
    }

    public static class PlayerLifeData {
        public int lives;
        public int crafted;
        public String lastKnownName;
        public boolean initialised;

        public PlayerLifeData() {
            this(DEFAULT_LIVES, 0, "", false);
        }

        public PlayerLifeData(int lives, int crafted, String lastKnownName, boolean initialised) {
            this.lives = lives;
            this.crafted = crafted;
            this.lastKnownName = lastKnownName;
            this.initialised = initialised;
        }

        public static final Codec<PlayerLifeData> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.INT.fieldOf("lives").orElse(DEFAULT_LIVES).forGetter(d -> d.lives),
            Codec.INT.fieldOf("crafted").orElse(0).forGetter(d -> d.crafted),
            Codec.STRING.fieldOf("name").orElse("").forGetter(d -> d.lastKnownName),
            Codec.BOOL.fieldOf("initialised").orElse(false).forGetter(d -> d.initialised)
        ).apply(i, PlayerLifeData::new));
    }

    public static final Codec<LivesData> CODEC = Codec.unboundedMap(
        UUIDUtil.STRING_CODEC, PlayerLifeData.CODEC
    ).xmap(LivesData::new, d -> d.data);

    public static final SavedDataType<LivesData> TYPE = new SavedDataType<>(
        Identifier.parse("lifesmp:lives"),
        LivesData::new,
        CODEC,
        null
    );

    public static LivesData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(TYPE);
    }

    public PlayerLifeData getOrCreate(UUID id) {
        return data.computeIfAbsent(id, k -> new PlayerLifeData());
    }

    public boolean has(UUID id) {
        return data.containsKey(id);
    }

    public int getLives(UUID id) {
        return getOrCreate(id).lives;
    }

    public void setLives(UUID id, int value) {
        PlayerLifeData d = getOrCreate(id);
        d.lives = Math.max(0, Math.min(MAX_LIVES, value));
        setDirty();
    }

    public int addLives(UUID id, int delta) {
        PlayerLifeData d = getOrCreate(id);
        d.lives = Math.max(0, Math.min(MAX_LIVES, d.lives + delta));
        setDirty();
        return d.lives;
    }

    public int getCrafted(UUID id) {
        return getOrCreate(id).crafted;
    }

    public int remainingCrafts(UUID id) {
        return Math.max(0, CRAFT_LIMIT - getOrCreate(id).crafted);
    }

    public void addCrafted(UUID id, int delta) {
        PlayerLifeData d = getOrCreate(id);
        d.crafted = Math.min(CRAFT_LIMIT, d.crafted + delta);
        setDirty();
    }

    public void updateName(UUID id, String name) {
        PlayerLifeData d = getOrCreate(id);
        if (!name.equals(d.lastKnownName)) {
            d.lastKnownName = name;
            setDirty();
        }
    }

    public void markInitialised(UUID id) {
        PlayerLifeData d = getOrCreate(id);
        if (!d.initialised) {
            d.initialised = true;
            setDirty();
        }
    }

    public UUID findByName(String name) {
        for (Map.Entry<UUID, PlayerLifeData> e : data.entrySet()) {
            if (e.getValue().lastKnownName.equalsIgnoreCase(name)) {
                return e.getKey();
            }
        }
        return null;
    }
}
