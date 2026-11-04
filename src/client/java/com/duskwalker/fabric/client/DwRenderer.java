package com.duskwalker.fabric.client;

import com.duskwalker.fabric.entity.DwEntity;
import com.duskwalker.fabric.entity.DwKind;

import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;

/**
 * Render secimi (render_controllers): variant -> geometri (DwModel), dominant -> doku, Effigy skin -> doku, Echo alphablend.
 */
public class DwRenderer extends MobRenderer<DwEntity, DwModel> {
    public static final ModelLayerLocation LAYER = new ModelLayerLocation(
            ResourceLocation.fromNamespaceAndPath("duskwalker", "dw_model"), "main");

    private static ResourceLocation tex(String name) {
        return ResourceLocation.fromNamespaceAndPath("duskwalker", "textures/entity/" + name + ".png");
    }

    private static final ResourceLocation DUSKWALKER = tex("duskwalker");
    private static final ResourceLocation DOMINANT = tex("duskwalker_dominant");
    private static final ResourceLocation ECHO = tex("duskwalker_echo");
    private static final ResourceLocation AYLIN = tex("aylin");
    private static final ResourceLocation[] EFFIGY = {
            DUSKWALKER, tex("effigy_pale"), tex("effigy_robed"), tex("effigy_charred"), tex("effigy_drowned")
    };

    public DwRenderer(EntityRendererProvider.Context context) {
        super(context, new DwModel(context.bakeLayer(LAYER)), 0.35F);
    }

    @Override
    public ResourceLocation getTextureLocation(DwEntity entity) {
        DwKind kind = entity.getKind();
        return switch (kind) {
            case ECHO -> ECHO;
            case AYLIN -> AYLIN;
            case EFFIGY -> EFFIGY[Math.max(0, Math.min(4, entity.getSkin()))];
            default -> entity.isDominant() ? DOMINANT : DUSKWALKER;
        };
    }

    @Override
    protected RenderType getRenderType(DwEntity entity, boolean bodyVisible, boolean translucent, boolean glowing) {
        if (entity.getKind().translucent && bodyVisible && !glowing) {
            return RenderType.entityTranslucent(getTextureLocation(entity));
        }
        return super.getRenderType(entity, bodyVisible, translucent, glowing);
    }
}
