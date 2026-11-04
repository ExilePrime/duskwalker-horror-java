package com.duskwalker.fabric.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.duskwalker.fabric.client.fx.DwFog;
import com.mojang.blaze3d.systems.RenderSystem;

import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.FogRenderer;
import net.minecraft.world.level.material.FogType;

/** Duskwalker sis yigininin hava sisi uzakligini ve rengini uygular (su/lav degismez). */
@Mixin(FogRenderer.class)
public abstract class FogRendererMixin {
    @Shadow
    private static float fogRed;
    @Shadow
    private static float fogGreen;
    @Shadow
    private static float fogBlue;

    @Inject(method = "setupColor", at = @At("TAIL"))
    private static void duskwalker$fogColor(Camera camera, float partialTick, ClientLevel level, int renderDistance,
                                            float darkenWorldAmount, CallbackInfo ci) {
        if (camera.getFluidInCamera() != FogType.NONE) return;
        float[] c = DwFog.color(partialTick, fogRed, fogGreen, fogBlue);
        if (c == null) return;
        fogRed = c[0];
        fogGreen = c[1];
        fogBlue = c[2];
        RenderSystem.clearColor(fogRed, fogGreen, fogBlue, 0.0F);
    }

    @Inject(method = "setupFog", at = @At("TAIL"))
    private static void duskwalker$fogDistance(Camera camera, FogRenderer.FogMode fogMode, float farPlaneDistance,
                                               boolean shouldCreateBossFog, float partialTick, CallbackInfo ci) {
        if (fogMode != FogRenderer.FogMode.FOG_TERRAIN || camera.getFluidInCamera() != FogType.NONE) return;
        float[] se = DwFog.distance(partialTick, RenderSystem.getShaderFogStart(), RenderSystem.getShaderFogEnd());
        if (se == null) return;
        RenderSystem.setShaderFogStart(se[0]);
        RenderSystem.setShaderFogEnd(se[1]);
    }
}
