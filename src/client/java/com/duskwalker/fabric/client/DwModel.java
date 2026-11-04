package com.duskwalker.fabric.client;

import com.duskwalker.fabric.entity.DwEntity;
import com.duskwalker.fabric.entity.DwKind;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.RenderType;

/** Uc geometriyi (humanoid / elongated / hunched) tasiyan tek model; varyanta gore grup secilir, animasyon kodla uygulanir. */
public class DwModel extends EntityModel<DwEntity> {
    private static final String[] BONES = {"body", "head", "hat", "rightArm", "leftArm", "rightLeg", "leftLeg"};

    private final ModelPart root;
    private final ModelPart[] groups = new ModelPart[3];
    /** [grup][bone] -> parca (yoksa null) */
    private final ModelPart[][] parts = new ModelPart[3][BONES.length];
    private final float[][][] base = new float[3][BONES.length][3];

    public DwModel(ModelPart root) {
        super(RenderType::entityCutoutNoCull);
        this.root = root;
        for (int g = 0; g < 3; g++) {
            groups[g] = root.getChild(DwGeometry.GROUPS[g]);
            for (DwGeometry.Bone bone : DwGeometry.bones(g)) {
                int idx = indexOf(bone.name());
                parts[g][idx] = DwGeometry.resolve(root, g, bone.name());
                base[g][idx][0] = (float) Math.toRadians(bone.rx());
                base[g][idx][1] = (float) Math.toRadians(bone.ry());
                base[g][idx][2] = (float) Math.toRadians(bone.rz());
            }
        }
    }

    private static int indexOf(String bone) {
        for (int i = 0; i < BONES.length; i++) if (BONES[i].equals(bone)) return i;
        throw new IllegalArgumentException(bone);
    }

    @Override
    public void setupAnim(DwEntity entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch) {
        DwKind kind = entity.getKind();
        int g = kind.hasVariant ? Math.max(0, Math.min(2, entity.getVariant())) : 0;
        for (int i = 0; i < 3; i++) groups[i].visible = (i == g);

        // locomotion denetleyicisi (yalniz walk/run'li turler), digerleri yalniz idle_sway
        DwAnimations.Anim main;
        float mainStart;
        if (kind.locomotion) {
            int next = DwAnimations.nextState(entity.clientAnimState, limbSwingAmount);
            if (next != entity.clientAnimState || entity.clientAnimStartAge > ageInTicks) {
                entity.clientAnimState = next;
                entity.clientAnimStartAge = ageInTicks;
            }
            main = DwAnimations.forState(entity.clientAnimState);
            mainStart = entity.clientAnimStartAge;
        } else {
            main = DwAnimations.IDLE_SWAY;
            mainStart = 0.0F;
        }
        float mainTime = (ageInTicks - mainStart) / 20.0F;
        float attackTime = (ageInTicks - entity.clientAttackStartAge) / 20.0F;
        boolean attacking = kind.locomotion && attackTime >= 0.0F && attackTime < DwAnimations.ATTACK.length();

        float[] rot = new float[3];
        for (int i = 0; i < BONES.length; i++) {
            ModelPart p = parts[g][i];
            if (p == null) continue;
            rot[0] = 0; rot[1] = 0; rot[2] = 0;
            main.addRotation(BONES[i], mainTime, rot);
            if (attacking) DwAnimations.ATTACK.addRotation(BONES[i], attackTime, rot);
            p.xRot = base[g][i][0] + (float) Math.toRadians(rot[0]);
            p.yRot = base[g][i][1] + (float) Math.toRadians(rot[1]);
            p.zRot = base[g][i][2] + (float) Math.toRadians(rot[2]);
        }
        // Bakis yonu (Java'da bakis hedefleri yalniz kafa donusunu degistirir)
        ModelPart head = parts[g][1];
        if (head != null) {
            head.yRot += (float) Math.toRadians(netHeadYaw);
            head.xRot += (float) Math.toRadians(headPitch);
        }
    }

    @Override
    public void renderToBuffer(PoseStack poseStack, VertexConsumer buffer, int packedLight, int packedOverlay, int color) {
        this.root.render(poseStack, buffer, packedLight, packedOverlay, color);
    }
}
