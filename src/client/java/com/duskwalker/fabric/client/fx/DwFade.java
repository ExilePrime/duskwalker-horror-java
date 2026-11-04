package com.duskwalker.fabric.client.fx;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

/** camera.fade: tam ekran renkli bindirme (in / hold / out). Renk 0..1 araligina kirpilir (Bedrock gibi). */
public final class DwFade {
    private static boolean active;
    private static float r, g, b;
    private static float start, in, hold, out;

    private DwFade() {
    }

    public static void start(double rr, double gg, double bb, double inSec, double holdSec, double outSec) {
        r = clamp01(rr);
        g = clamp01(gg);
        b = clamp01(bb);
        in = (float) Math.max(0.0, inSec) * 20.0F;
        hold = (float) Math.max(0.0, holdSec) * 20.0F;
        out = (float) Math.max(0.0, outSec) * 20.0F;
        start = DwClock.now(0.0F);
        active = true;
    }

    public static void clear() {
        active = false;
    }

    private static float clamp01(double v) {
        return (float) Math.max(0.0, Math.min(1.0, v));
    }

    public static void render(GuiGraphics graphics, float partialTick) {
        if (!active) return;
        float t = DwClock.now(partialTick) - start;
        float alpha;
        if (t < in) alpha = in <= 0 ? 1.0F : t / in;
        else if (t < in + hold) alpha = 1.0F;
        else if (t < in + hold + out) alpha = 1.0F - (t - in - hold) / out;
        else {
            active = false;
            return;
        }
        int a = Math.round(Math.max(0.0F, Math.min(1.0F, alpha)) * 255.0F);
        if (a <= 0) return;
        int color = (a << 24) | (Math.round(r * 255.0F) << 16) | (Math.round(g * 255.0F) << 8) | Math.round(b * 255.0F);
        int w = Minecraft.getInstance().getWindow().getGuiScaledWidth();
        int h = Minecraft.getInstance().getWindow().getGuiScaledHeight();
        graphics.fill(0, 0, w, h, 1000, color);
    }
}
