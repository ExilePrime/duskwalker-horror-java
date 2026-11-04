package com.duskwalker.fabric.client.fx;

import net.minecraft.client.Minecraft;

/** Efektler icin oyun-tik tabanli saat (duraklatmada donar). Birim: tik (20 = 1 sn). */
public final class DwClock {
    private static long ticks;

    private DwClock() {
    }

    public static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (!mc.isPaused()) ticks++;
    }

    public static float now(float partialTick) {
        return ticks + (Minecraft.getInstance().isPaused() ? 0.0F : partialTick);
    }

    public static void reset() {
        ticks = 0;
    }
}
