package com.duskwalker.fabric.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.duskwalker.fabric.client.fx.DwShake;

import net.minecraft.client.Camera;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.phys.Vec3;

/** camera.shake: kamera hazirlandiktan sonra kucuk aci/konum sapmasi ekler. */
@Mixin(Camera.class)
public abstract class CameraMixin {
    @Shadow
    protected abstract void setRotation(float yRot, float xRot);

    @Shadow
    protected abstract void setPosition(double x, double y, double z);

    @Shadow
    public abstract float getXRot();

    @Shadow
    public abstract float getYRot();

    @Shadow
    public abstract Vec3 getPosition();

    @Inject(method = "setup", at = @At("TAIL"))
    private void duskwalker$shake(BlockGetter level, Entity entity, boolean detached, boolean thirdPersonReverse,
                                  float partialTick, CallbackInfo ci) {
        float[] r = DwShake.rotation(partialTick);
        if (r != null) {
            float pitch = Math.max(-90.0F, Math.min(90.0F, this.getXRot() + r[1]));
            this.setRotation(this.getYRot() + r[0], pitch);
        }
        double[] p = DwShake.position(partialTick);
        if (p != null) {
            Vec3 pos = this.getPosition();
            this.setPosition(pos.x + p[0], pos.y + p[1], pos.z + p[2]);
        }
    }
}
