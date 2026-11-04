package com.duskwalker.fabric.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.duskwalker.fabric.EventHooks;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;

/** Bedrock explosion olayi: patlama bloklari yok ettikten sonra tetikler. */
@Mixin(Explosion.class)
public abstract class ExplosionMixin {
    @Shadow @Final private Level level;

    @Shadow public abstract Entity getDirectSourceEntity();

    @Inject(method = "finalizeExplosion(Z)V", at = @At("TAIL"))
    private void duskwalker$afterExplosion(boolean spawnParticles, CallbackInfo ci) {
        EventHooks.onExplosion(this.level, this.getDirectSourceEntity());
    }
}
