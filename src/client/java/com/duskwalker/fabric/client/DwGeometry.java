package com.duskwalker.fabric.client;

import java.util.HashMap;
import java.util.Map;

import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;

/**
 * rp/models/entity/duskwalker_variants.geo.json (elongated, hunched) + Bedrock'un standart geometry.humanoid.custom
 * (64x64 oyuncu duzenli insan) modellerinin Java LayerDefinition karsiligi. Uc geometri tek agacta uc "grup" olarak durur;
 * DwModel varyanta gore yalniz birini gorunur kilar.
 *
 * Donusum (Bedrock -> Java model uzayi): x ve z ayni, y -> 24 - y. Bone pivotlari Bedrock'ta mutlak, Java'da ebeveyne goreli
 * oldugundan fark alinir (y isareti cevrilir). Kup: javaRel = (ox - px, py - oy - sy, oz - pz). Donme dereceleri ayni isaretle radyana cevrilir
 * (Bedrock baş eğme +x = asagi bakma = Java xRot ile ayni). Doku 64x64 UV mantigiyla tanimlanir; 256x256 dokuda normalize olur.
 */
public final class DwGeometry {
    public static final String[] GROUPS = {"humanoid", "elongated", "hunched"};

    /** Tek bone: ad, ebeveyn (null = kok), pivot, baslangic donusu (derece), kup (origin, size, uv), sisirme. */
    public record Bone(String name, String parent, float px, float py, float pz, float rx, float ry, float rz,
                       float ox, float oy, float oz, float sx, float sy, float sz, int u, int v, float inflate) {
    }

    private static Bone b(String name, String parent, float px, float py, float pz, float rx, float ry, float rz,
                          float ox, float oy, float oz, float sx, float sy, float sz, int u, int v) {
        return new Bone(name, parent, px, py, pz, rx, ry, rz, ox, oy, oz, sx, sy, sz, u, v, 0.0F);
    }

    private static final Bone[] HUMANOID = {
            b("body", null, 0, 24, 0, 0, 0, 0, -4, 12, -2, 8, 12, 4, 16, 16),
            b("head", null, 0, 24, 0, 0, 0, 0, -4, 24, -4, 8, 8, 8, 0, 0),
            new Bone("hat", "head", 0, 24, 0, 0, 0, 0, -4, 24, -4, 8, 8, 8, 32, 0, 0.5F),
            b("rightArm", null, -5, 22, 0, 0, 0, 0, -8, 12, -2, 4, 12, 4, 40, 16),
            b("leftArm", null, 5, 22, 0, 0, 0, 0, 4, 12, -2, 4, 12, 4, 32, 48),
            b("rightLeg", null, -1.9F, 12, 0, 0, 0, 0, -3.9F, 0, -2, 4, 12, 4, 0, 16),
            b("leftLeg", null, 1.9F, 12, 0, 0, 0, 0, -0.1F, 0, -2, 4, 12, 4, 16, 48),
    };

    private static final Bone[] ELONGATED = {
            b("body", null, 0, 34, 0, 0, 0, 0, -3.5F, 18, -1.5F, 7, 16, 3, 16, 16),
            b("head", "body", 0, 34, 0, 0, 0, 0, -3.5F, 34, -3.5F, 7, 7, 7, 0, 0),
            b("rightArm", "body", -5, 32, 0, 0, 0, 0, -6.5F, 15, -1.5F, 3, 17, 3, 40, 16),
            b("leftArm", "body", 5, 32, 0, 0, 0, 0, 3.5F, 15, -1.5F, 3, 17, 3, 32, 48),
            b("rightLeg", "body", -1.5F, 18, 0, 0, 0, 0, -3, 0, -1.5F, 3, 18, 3, 0, 16),
            b("leftLeg", "body", 1.5F, 18, 0, 0, 0, 0, 0, 0, -1.5F, 3, 18, 3, 16, 48),
    };

    private static final Bone[] HUNCHED = {
            b("body", null, 0, 20, 0, 18, 0, 0, -5.5F, 10, -2.5F, 11, 10, 5, 16, 16),
            b("head", "body", 0, 20, 0, -10, 0, 0, -4.5F, 20, -4.5F, 9, 9, 9, 0, 0),
            b("rightArm", "body", -8, 18, 0, 0, 0, 0, -10.5F, 6, -2.5F, 5, 12, 5, 40, 16),
            b("leftArm", "body", 8, 18, 0, 0, 0, 0, 5.5F, 6, -2.5F, 5, 12, 5, 32, 48),
            b("rightLeg", "body", -3, 10, 0, 0, 0, 0, -5.5F, 0, -2.5F, 5, 10, 5, 0, 16),
            b("leftLeg", "body", 3, 10, 0, 0, 0, 0, 0.5F, 0, -2.5F, 5, 10, 5, 16, 48),
    };

    private DwGeometry() {
    }

    public static Bone[] bones(int group) {
        return switch (group) {
            case 1 -> ELONGATED;
            case 2 -> HUNCHED;
            default -> HUMANOID;
        };
    }

    public static LayerDefinition createLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        for (int g = 0; g < GROUPS.length; g++) {
            PartDefinition groupDef = root.addOrReplaceChild(GROUPS[g], CubeListBuilder.create(), PartPose.ZERO);
            Map<String, PartDefinition> defs = new HashMap<>();
            Map<String, Bone> byName = new HashMap<>();
            for (Bone bone : bones(g)) {
                PartDefinition parentDef = groupDef;
                float ppx = 0, ppy = 24, ppz = 0;
                if (bone.parent() != null) {
                    parentDef = defs.get(bone.parent());
                    Bone p = byName.get(bone.parent());
                    ppx = p.px();
                    ppy = p.py();
                    ppz = p.pz();
                }
                PartPose pose = PartPose.offsetAndRotation(
                        bone.px() - ppx, -(bone.py() - ppy), bone.pz() - ppz,
                        (float) Math.toRadians(bone.rx()), (float) Math.toRadians(bone.ry()), (float) Math.toRadians(bone.rz()));
                CubeListBuilder cube = CubeListBuilder.create().texOffs(bone.u(), bone.v());
                float rx = bone.ox() - bone.px();
                float ry = bone.py() - bone.oy() - bone.sy();
                float rz = bone.oz() - bone.pz();
                if (bone.inflate() != 0.0F) {
                    cube.addBox(rx, ry, rz, bone.sx(), bone.sy(), bone.sz(), new CubeDeformation(bone.inflate()));
                } else {
                    cube.addBox(rx, ry, rz, bone.sx(), bone.sy(), bone.sz());
                }
                defs.put(bone.name(), parentDef.addOrReplaceChild(bone.name(), cube, pose));
                byName.put(bone.name(), bone);
            }
        }
        return LayerDefinition.create(mesh, 64, 64);
    }

    /** Pisirilmis agactan bone'a ModelPart yolu ile ulas. */
    public static ModelPart resolve(ModelPart root, int group, String boneName) {
        Bone[] bs = bones(group);
        Map<String, Bone> byName = new HashMap<>();
        for (Bone x : bs) byName.put(x.name(), x);
        java.util.ArrayDeque<String> chain = new java.util.ArrayDeque<>();
        Bone cur = byName.get(boneName);
        while (cur != null) {
            chain.push(cur.name());
            cur = cur.parent() == null ? null : byName.get(cur.parent());
        }
        ModelPart part = root.getChild(GROUPS[group]);
        while (!chain.isEmpty()) part = part.getChild(chain.pop());
        return part;
    }
}
