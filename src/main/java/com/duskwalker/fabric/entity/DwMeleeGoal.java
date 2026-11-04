package com.duskwalker.fabric.entity;

import java.util.EnumSet;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;

/**
 * Bedrock {@code minecraft:behavior.melee_attack} karsiligi (speed_multiplier, reach_multiplier, track_target).
 * Mob'un dogal saldirisini ({@link Mob#doHurtTarget}) kullanir; hasar ATTACK_DAMAGE ozelliginden gelir.
 * Kendi hedef-izleme/yol bulma dongusunu tasir (vanilla MeleeAttackGoal'in menzil hesabi surume gore degistigi icin).
 */
public class DwMeleeGoal extends Goal {
    private static final int ATTACK_COOLDOWN = 20;

    private final Mob mob;
    private final double speedMultiplier;
    private final double reachMultiplier;
    private int pathRecalc;
    private int attackCooldown;

    public DwMeleeGoal(Mob mob, double speedMultiplier, double reachMultiplier) {
        this.mob = mob;
        this.speedMultiplier = speedMultiplier;
        this.reachMultiplier = reachMultiplier;
        this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
    }

    private static boolean valid(LivingEntity t) {
        if (t == null || !t.isAlive()) return false;
        if (t instanceof Player p && (p.isSpectator() || p.isCreative())) return false;
        return true;
    }

    @Override
    public boolean canUse() {
        return valid(this.mob.getTarget());
    }

    @Override
    public boolean canContinueToUse() {
        return valid(this.mob.getTarget());
    }

    @Override
    public void start() {
        this.mob.setAggressive(true);
        this.pathRecalc = 0;
        this.attackCooldown = 0;
    }

    @Override
    public void stop() {
        LivingEntity t = this.mob.getTarget();
        if (t instanceof Player p && (p.isSpectator() || p.isCreative())) this.mob.setTarget(null);
        this.mob.setAggressive(false);
        this.mob.getNavigation().stop();
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    /** Bedrock/vanilla taban menzil karesi x reach_multiplier^2. */
    private double reachSqr(LivingEntity target) {
        double w = this.mob.getBbWidth() * 2.0F;
        double base = w * w + target.getBbWidth();
        return base * this.reachMultiplier * this.reachMultiplier;
    }

    @Override
    public void tick() {
        LivingEntity target = this.mob.getTarget();
        if (target == null) return;
        this.mob.getLookControl().setLookAt(target, 30.0F, 30.0F);
        if (this.pathRecalc > 0) this.pathRecalc--;
        if (this.attackCooldown > 0) this.attackCooldown--;

        double distSqr = this.mob.distanceToSqr(target.getX(), target.getY(), target.getZ());
        if (this.pathRecalc <= 0) {
            this.pathRecalc = 4 + this.mob.getRandom().nextInt(7);
            if (distSqr > 1024.0D) this.pathRecalc += 10;
            else if (distSqr > 256.0D) this.pathRecalc += 5;
            if (!this.mob.getNavigation().moveTo(target, this.speedMultiplier)) this.pathRecalc += 15;
        }
        if (distSqr <= reachSqr(target) && this.attackCooldown <= 0 && this.mob.getSensing().hasLineOfSight(target)) {
            this.attackCooldown = ATTACK_COOLDOWN;
            this.mob.swing(InteractionHand.MAIN_HAND);
            this.mob.doHurtTarget(target);
        }
    }

    /** Bedrock {@code minecraft:behavior.look_at_target}: yalnizca hedef varken hedefe bakar. */
    public static class LookAtTarget extends Goal {
        private final Mob mob;

        public LookAtTarget(Mob mob) {
            this.mob = mob;
            this.setFlags(EnumSet.of(Goal.Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            LivingEntity t = this.mob.getTarget();
            return t != null && t.isAlive();
        }

        @Override
        public boolean canContinueToUse() {
            return canUse();
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        @Override
        public void tick() {
            LivingEntity t = this.mob.getTarget();
            if (t != null) this.mob.getLookControl().setLookAt(t, 30.0F, 30.0F);
        }
    }
}
