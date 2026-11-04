package com.duskwalker.fabric.client.fx;

/**
 * camera.shake: sonen kucuk kamera sarsintisi. rotational: bakis acisi (derece), positional: kamera konumu (blok).
 * Genlik = strength x olcek x kalan sure orani. Olcekler ayarlanabilir sabitlerdir.
 */
public final class DwShake {
    /** strength 1.0 -> 2.5 derece */
    private static final double ROT_DEGREES_PER_STRENGTH = 2.5;
    /** strength 1.0 -> 0.08 blok */
    private static final double POS_BLOCKS_PER_STRENGTH = 0.08;

    private static boolean rotActive, posActive;
    private static double rotStrength, posStrength;
    private static float rotStart, rotDur, posStart, posDur;

    private DwShake() {
    }

    public static void start(double strength, double seconds, boolean positional) {
        double s = Math.max(0.0, Math.min(4.0, strength));
        float dur = (float) Math.max(0.05, seconds) * 20.0F;
        if (positional) {
            posActive = true; posStrength = s; posStart = DwClock.now(0.0F); posDur = dur;
        } else {
            rotActive = true; rotStrength = s; rotStart = DwClock.now(0.0F); rotDur = dur;
        }
    }

    public static void clear() {
        rotActive = false;
        posActive = false;
    }

    private static double noise(float t, double seed) {
        return Math.sin(t * 1.7 + seed) * 0.55 + Math.sin(t * 2.9 + seed * 2.3) * 0.3 + Math.sin(t * 5.3 + seed * 0.7) * 0.15;
    }

    /** {yawOffset, pitchOffset} derece; sarsinti yoksa null. */
    public static float[] rotation(float partialTick) {
        if (!rotActive) return null;
        float t = DwClock.now(partialTick) - rotStart;
        if (t >= rotDur) { rotActive = false; return null; }
        double amp = rotStrength * ROT_DEGREES_PER_STRENGTH * (1.0 - t / rotDur);
        return new float[] {(float) (noise(t, 1.0) * amp), (float) (noise(t, 7.0) * amp)};
    }

    /** {dx, dy, dz} blok; sarsinti yoksa null. */
    public static double[] position(float partialTick) {
        if (!posActive) return null;
        float t = DwClock.now(partialTick) - posStart;
        if (t >= posDur) { posActive = false; return null; }
        double amp = posStrength * POS_BLOCKS_PER_STRENGTH * (1.0 - t / posDur);
        return new double[] {noise(t, 3.0) * amp, noise(t, 11.0) * amp, noise(t, 19.0) * amp};
    }
}
