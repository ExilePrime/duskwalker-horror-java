package com.duskwalker.fabric.client;

import java.util.Map;

/**
 * rp/animations/duskwalker.animation.json (idle_sway, walk, run, attack) ve
 * rp/animation_controllers/duskwalker.animation_controller.json (locomotion) kodla yeniden uygulamasi.
 * Her animasyon: bone -> anahtar kareler {zaman(sn), x, y, z} (derece, dogrusal ara deger).
 */
public final class DwAnimations {
    public static final int STATE_IDLE = 0;
    public static final int STATE_WALK = 1;
    public static final int STATE_RUN = 2;

    public static final class Anim {
        final float length;
        final boolean loop;
        final Map<String, float[][]> bones;

        Anim(float length, boolean loop, Map<String, float[][]> bones) {
            this.length = length;
            this.loop = loop;
            this.bones = bones;
        }

        public float length() {
            return length;
        }

        /** Toplanabilir donus (derece) icin out[0..2] degerlerine ekler. */
        public void addRotation(String bone, float timeSec, float[] out) {
            float[][] keys = bones.get(bone);
            if (keys == null) return;
            float t = loop ? (timeSec % length) : Math.min(timeSec, length);
            if (t < 0) t = 0;
            float[] first = keys[0];
            if (t <= first[0]) {
                out[0] += first[1]; out[1] += first[2]; out[2] += first[3];
                return;
            }
            for (int i = 1; i < keys.length; i++) {
                float[] a = keys[i - 1];
                float[] c = keys[i];
                if (t <= c[0]) {
                    float f = (t - a[0]) / (c[0] - a[0]);
                    out[0] += a[1] + (c[1] - a[1]) * f;
                    out[1] += a[2] + (c[2] - a[2]) * f;
                    out[2] += a[3] + (c[3] - a[3]) * f;
                    return;
                }
            }
            float[] last = keys[keys.length - 1];
            out[0] += last[1]; out[1] += last[2]; out[2] += last[3];
        }
    }

    private static float[] k(float t, float x, float y, float z) {
        return new float[] {t, x, y, z};
    }

    public static final Anim IDLE_SWAY = new Anim(3.0F, true, Map.of(
            "head", new float[][] {k(0, 0, 0, 0), k(1.5F, 3, 6, 0), k(3.0F, 0, 0, 0)},
            "body", new float[][] {k(0, 0, 0, 0), k(1.5F, 1.5F, 0, 0), k(3.0F, 0, 0, 0)}));

    public static final Anim WALK = new Anim(0.9F, true, Map.of(
            "rightLeg", new float[][] {k(0, 0, 0, 0), k(0.225F, -35, 0, 0), k(0.45F, 0, 0, 0), k(0.675F, 35, 0, 0), k(0.9F, 0, 0, 0)},
            "leftLeg", new float[][] {k(0, 0, 0, 0), k(0.225F, 35, 0, 0), k(0.45F, 0, 0, 0), k(0.675F, -35, 0, 0), k(0.9F, 0, 0, 0)},
            "rightArm", new float[][] {k(0, 0, 0, 0), k(0.225F, 30, 0, 4), k(0.45F, 0, 0, 0), k(0.675F, -30, 0, -4), k(0.9F, 0, 0, 0)},
            "leftArm", new float[][] {k(0, 0, 0, 0), k(0.225F, -30, 0, -4), k(0.45F, 0, 0, 0), k(0.675F, 30, 0, 4), k(0.9F, 0, 0, 0)},
            "body", new float[][] {k(0, 0, 0, 0), k(0.225F, 0, 3, 0), k(0.45F, 0, 0, 0), k(0.675F, 0, -3, 0), k(0.9F, 0, 0, 0)}));

    public static final Anim RUN = new Anim(0.55F, true, Map.of(
            "rightLeg", new float[][] {k(0, 0, 0, 0), k(0.1375F, -55, 0, 0), k(0.275F, 0, 0, 0), k(0.4125F, 55, 0, 0), k(0.55F, 0, 0, 0)},
            "leftLeg", new float[][] {k(0, 0, 0, 0), k(0.1375F, 55, 0, 0), k(0.275F, 0, 0, 0), k(0.4125F, -55, 0, 0), k(0.55F, 0, 0, 0)},
            "rightArm", new float[][] {k(0, -10, 0, 0), k(0.1375F, 45, 0, 6), k(0.275F, -10, 0, 0), k(0.4125F, -55, 0, -6), k(0.55F, -10, 0, 0)},
            "leftArm", new float[][] {k(0, -10, 0, 0), k(0.1375F, -55, 0, -6), k(0.275F, -10, 0, 0), k(0.4125F, 45, 0, 6), k(0.55F, -10, 0, 0)},
            "body", new float[][] {k(0, 8, 0, 0), k(0.275F, 10, 4, 0), k(0.55F, 8, 0, 0)}));

    public static final Anim ATTACK = new Anim(1.1F, false, Map.of(
            "rightArm", new float[][] {k(0, 0, 0, 0), k(0.25F, -140, -10, 0), k(0.45F, 60, 0, 10), k(0.7F, -10, 0, 0), k(1.1F, 0, 0, 0)},
            "body", new float[][] {k(0, 0, 0, 0), k(0.25F, -10, 0, 0), k(0.45F, 15, 0, 0), k(1.1F, 0, 0, 0)},
            "head", new float[][] {k(0, 0, 0, 0), k(0.45F, 10, 0, 0), k(1.1F, 0, 0, 0)}));

    private DwAnimations() {
    }

    /**
     * controller.animation.duskwalker.locomotion gecisleri. moveSpeed = query.modified_move_speed
     * (Java'da yurume animasyonu hizi: limbSwingAmount).
     */
    public static int nextState(int state, float moveSpeed) {
        boolean walkRange = moveSpeed >= 0.05F && moveSpeed < 0.45F;
        boolean runRange = moveSpeed >= 0.45F;
        return switch (state) {
            case STATE_IDLE -> walkRange ? STATE_WALK : runRange ? STATE_RUN : STATE_IDLE;
            case STATE_WALK -> moveSpeed < 0.05F ? STATE_IDLE : runRange ? STATE_RUN : STATE_WALK;
            default -> moveSpeed < 0.05F ? STATE_IDLE : walkRange ? STATE_WALK : STATE_RUN;
        };
    }

    public static Anim forState(int state) {
        return switch (state) {
            case STATE_WALK -> WALK;
            case STATE_RUN -> RUN;
            default -> IDLE_SWAY;
        };
    }
}
