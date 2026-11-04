package com.duskwalker.fabric;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * Bedrock dynamic property deposu (kalici). Overworld DataStorage'inda "duskwalker_dynamic" adiyla saklanir.
 * Anahtar: varlik/oyuncu UUID metni, dunya ozellikleri icin WORLD. Deger: String / Double / Boolean (null = sil).
 * NBT'de tur oneki: "s:" String, "d:" Double, "b:" Boolean.
 */
public final class DynData extends SavedData {
    public static final String NAME = "duskwalker_dynamic";
    public static final String WORLD = "#world";

    private static DynData instance;

    private final Map<String, Map<String, Object>> data = new HashMap<>();

    public DynData() {}

    // ---------------------------------------------------------------- yasam dongusu
    public static DynData load(MinecraftServer server) {
        instance = server.overworld().getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(DynData::new, DynData::fromTag, null), NAME);
        return instance;
    }

    public static void unload() { instance = null; }

    public static DynData get() {
        if (instance == null) throw new com.duskwalker.bedrock.BedrockException("DynData not loaded");
        return instance;
    }

    public static boolean isLoaded() { return instance != null; }

    // ---------------------------------------------------------------- NBT
    private static DynData fromTag(CompoundTag tag, HolderLookup.Provider registries) {
        DynData d = new DynData();
        for (String owner : tag.getAllKeys()) {
            CompoundTag t = tag.getCompound(owner);
            Map<String, Object> m = new LinkedHashMap<>();
            for (String k : t.getAllKeys()) {
                if (k.length() < 3) continue;
                String name = k.substring(2);
                switch (k.charAt(0)) {
                    case 's' -> m.put(name, t.getString(k));
                    case 'd' -> m.put(name, t.getDouble(k));
                    case 'b' -> m.put(name, t.getBoolean(k));
                    default -> { }
                }
            }
            if (!m.isEmpty()) d.data.put(owner, m);
        }
        return d;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        for (Map.Entry<String, Map<String, Object>> e : data.entrySet()) {
            CompoundTag t = new CompoundTag();
            for (Map.Entry<String, Object> p : e.getValue().entrySet()) {
                Object v = p.getValue();
                if (v instanceof String s) t.putString("s:" + p.getKey(), s);
                else if (v instanceof Boolean b) t.putBoolean("b:" + p.getKey(), b);
                else if (v instanceof Number n) t.putDouble("d:" + p.getKey(), n.doubleValue());
            }
            tag.put(e.getKey(), t);
        }
        return tag;
    }

    // ---------------------------------------------------------------- erisim
    public Object getValue(String owner, String key) {
        Map<String, Object> m = data.get(owner);
        return m == null ? null : m.get(key);
    }

    /** value: String / Number (Double saklanir) / Boolean / null (sil). */
    public void setValue(String owner, String key, Object value) {
        if (value == null) {
            Map<String, Object> m = data.get(owner);
            if (m != null && m.remove(key) != null) {
                if (m.isEmpty()) data.remove(owner);
                setDirty();
            }
            return;
        }
        Object v;
        if (value instanceof String || value instanceof Boolean) v = value;
        else if (value instanceof Number n) v = n.doubleValue();
        else throw new com.duskwalker.bedrock.BedrockException("unsupported dynamic property type: " + value.getClass().getSimpleName());
        data.computeIfAbsent(owner, k -> new LinkedHashMap<>()).put(key, v);
        setDirty();
    }

    public void clear(String owner) {
        if (data.remove(owner) != null) setDirty();
    }

    public Object getEntity(UUID id, String key) { return getValue(id.toString(), key); }
    public void setEntity(UUID id, String key, Object value) { setValue(id.toString(), key, value); }
    public void clearEntity(UUID id) { clear(id.toString()); }
    public Object getWorld(String key) { return getValue(WORLD, key); }
    public void setWorld(String key, Object value) { setValue(WORLD, key, value); }
}
