package com.duskwalker.fabric.client.fx;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Ozel sis yigini. userKey basina bir kayit; en son push edilen gecerli. Degerler docs/original/duskwalker_*.json dosyalarinin
 * "air" bolumunden alinmistir (su/lav yalniz Bedrock tarafinda; burada uygulanmaz). Gecisler ~1.5 sn'de yumusatilir.
 */
public final class DwFog {
    public record Def(float start, float end, float r, float g, float b) {
    }

    private static final Map<String, Def> DEFS = new LinkedHashMap<>();

    private static void def(String id, float start, float end, int rgb) {
        DEFS.put(id, new Def(start, end, ((rgb >> 16) & 255) / 255.0F, ((rgb >> 8) & 255) / 255.0F, (rgb & 255) / 255.0F));
    }

    static {
        def("duskwalker:sanity_fog_1", 40, 110, 0x16181e);
        def("duskwalker:sanity_fog_2", 32, 90, 0x12141a);
        def("duskwalker:sanity_fog_3", 24, 70, 0x0e1015);
        def("duskwalker:sanity_fog_4", 16, 52, 0x090a0d);
        def("duskwalker:night_fog", 24, 96, 0x12151c);
        def("duskwalker:ash_fog", 14, 64, 0x2a2a2d);
        def("duskwalker:paradox_fog", 3, 26, 0x1a1208);
        def("duskwalker:hunt_fog", 12, 60, 0x07070c);
    }

    private static final float FADE_TICKS = 30.0F;

    /** userKey -> fogId (ekleme sirasi = yigin sirasi). */
    private static final LinkedHashMap<String, String> STACK = new LinkedHashMap<>();
    private static float weight;
    private static float lastNow = Float.NaN;
    private static Def lastDef;

    private DwFog() {
    }

    public static void push(String fogId, String userKey) {
        if (!DEFS.containsKey(fogId)) return;
        STACK.remove(userKey);
        STACK.put(userKey, fogId);
    }

    public static void pop(String userKey) {
        STACK.remove(userKey);
    }

    public static void clear() {
        STACK.clear();
        weight = 0.0F;
        lastNow = Float.NaN;
        lastDef = null;
    }

    private static Def current() {
        Def d = null;
        for (String id : STACK.values()) d = DEFS.get(id);
        return d;
    }

    /** Agirligi gunceller; sis uygulanacaksa hedef tanimi, yoksa null dondurur. */
    private static Def update(float partialTick) {
        float now = DwClock.now(partialTick);
        float dt = Float.isNaN(lastNow) ? 0.0F : Math.max(0.0F, Math.min(5.0F, now - lastNow));
        lastNow = now;
        Def d = current();
        if (d != null) lastDef = d;
        float step = dt / FADE_TICKS;
        weight = d != null ? Math.min(1.0F, weight + step) : Math.max(0.0F, weight - step);
        if (weight <= 0.0F && d == null) {
            lastDef = null;
            return null;
        }
        return d != null ? d : lastDef;
    }

    /** {start, end}: vanilla degerlerini sikilastirir; etki yoksa null. */
    public static float[] distance(float partialTick, float vanillaStart, float vanillaEnd) {
        Def d = update(partialTick);
        if (d == null) return null;
        float ts = Math.min(vanillaStart, d.start());
        float te = Math.min(vanillaEnd, d.end());
        if (te < ts + 1.0F) te = ts + 1.0F;
        return new float[] {vanillaStart + (ts - vanillaStart) * weight, vanillaEnd + (te - vanillaEnd) * weight};
    }

    /** {r, g, b}: vanilla rengi ile hedef renk arasinda; etki yoksa null. */
    public static float[] color(float partialTick, float vr, float vg, float vb) {
        Def d = update(partialTick);
        if (d == null) return null;
        return new float[] {vr + (d.r() - vr) * weight, vg + (d.g() - vg) * weight, vb + (d.b() - vb) * weight};
    }
}
