package com.duskwalker.fabric.entity;

import java.util.LinkedHashMap;
import java.util.Map;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;

/** assets/duskwalker/sounds.json anahtarlari icin SoundEvent kayitlari. */
public final class DwSounds {
    private static final Map<String, ResourceLocation> IDS = new LinkedHashMap<>();
    private static final Map<String, SoundEvent> EVENTS = new LinkedHashMap<>();

    public static final SoundEvent HEARTBEAT = event("heartbeat");
    public static final SoundEvent BREATH = event("breath");
    public static final SoundEvent CREAK = event("creak");
    public static final SoundEvent DRONE = event("drone");
    public static final SoundEvent TICK = event("tick");
    public static final SoundEvent WHISPER = event("whisper");
    public static final SoundEvent RING = event("ring");

    private static boolean registered;

    private DwSounds() {
    }

    private static SoundEvent event(String name) {
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath("duskwalker", name);
        SoundEvent e = SoundEvent.createVariableRangeEvent(id);
        IDS.put(name, id);
        EVENTS.put(name, e);
        return e;
    }

    public static void register() {
        if (registered) return;
        registered = true;
        for (Map.Entry<String, SoundEvent> en : EVENTS.entrySet()) {
            Registry.register(BuiltInRegistries.SOUND_EVENT, IDS.get(en.getKey()), en.getValue());
        }
    }

    /** "duskwalker.heartbeat" / "duskwalker:heartbeat" -> SoundEvent; bilinmiyorsa null. */
    public static SoundEvent byBedrockId(String id) {
        if (id == null) return null;
        String n;
        if (id.startsWith("duskwalker.")) n = id.substring("duskwalker.".length());
        else if (id.startsWith("duskwalker:")) n = id.substring("duskwalker:".length());
        else return null;
        return EVENTS.get(n);
    }
}
