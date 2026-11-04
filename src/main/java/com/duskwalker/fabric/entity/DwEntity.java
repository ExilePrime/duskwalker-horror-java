package com.duskwalker.fabric.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.OpenDoorGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.PathType;

/**
 * Duskwalker, Echo, Seep, Watcher, Effigy ve Aylin icin ortak taban sinif (tur: {@link DwKind}).
 * Bedrock bp/entities davranisi: hasarsizlik (yalniz Duskwalker lavdan hasar alir), itilmezlik, ozellikler (SynchedEntityData),
 * Duskwalker av olaylari (hunt_start / hunt_start_fast / hunt_end), vanish, Effigy 150 tik zamanlayicisi.
 */
public class DwEntity extends PathfinderMob implements DwEntityApi {
    /** Istemciye "saldiri animasyonunu baslat" bildiren entity event kimligi. */
    public static final byte EVENT_ATTACK_ANIM = 99;

    private static final EntityDataAccessor<Integer> DATA_VARIANT = SynchedEntityData.defineId(DwEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> DATA_DOMINANT = SynchedEntityData.defineId(DwEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Integer> DATA_SKIN = SynchedEntityData.defineId(DwEntity.class, EntityDataSerializers.INT);

    private final DwKind kind;
    private int huntMode;
    private int vanishTimer;
    private Goal huntTargetGoal;
    private Goal huntMeleeGoal;
    private Goal huntDoorGoal;

    // Yalniz istemci animasyon durumu (render sirasinda DwModel tarafindan kullanilir).
    public int clientAnimState;
    public float clientAnimStartAge;
    public float clientAttackStartAge = -10000.0F;

    public DwEntity(EntityType<? extends DwEntity> type, Level level, DwKind kind) {
        super(type, level);
        this.kind = kind;
        this.entityData.set(DATA_VARIANT, kind.defaultVariant);
        this.refreshDimensions();
        if (kind.hunter) {
            // can_path_over_water / avoid_water=false
            this.setPathfindingMalus(PathType.WATER, 0.0F);
            this.setPathfindingMalus(PathType.WATER_BORDER, 0.0F);
            // can_open_doors / can_pass_doors
            if (this.getNavigation() instanceof GroundPathNavigation nav) {
                nav.setCanOpenDoors(true);
            }
        }
        if (!level.isClientSide()) {
            if (kind.look == DwKind.Look.TARGET) {
                this.goalSelector.addGoal(4, new DwMeleeGoal.LookAtTarget(this));
                this.goalSelector.addGoal(8, new RandomLookAroundGoal(this));
            } else if (kind.look == DwKind.Look.PLAYER) {
                this.goalSelector.addGoal(4, new LookAtPlayerGoal(this, Player.class, 8.0F));
                this.goalSelector.addGoal(8, new RandomLookAroundGoal(this));
            }
        }
    }

    public static AttributeSupplier.Builder createAttributes(DwKind k) {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, k.maxHealth)
                .add(Attributes.MOVEMENT_SPEED, k.moveSpeed)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0D)
                .add(Attributes.ATTACK_DAMAGE, 6.0D)
                .add(Attributes.ATTACK_KNOCKBACK, 0.0D)
                .add(Attributes.FOLLOW_RANGE, 48.0D)
                .add(Attributes.SCALE, k.scale);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_VARIANT, 0);
        builder.define(DATA_DOMINANT, false);
        builder.define(DATA_SKIN, 0);
    }

    public DwKind getKind() {
        return this.kind;
    }

    public int getVariant() {
        return this.entityData.get(DATA_VARIANT);
    }

    public boolean isDominant() {
        return this.entityData.get(DATA_DOMINANT);
    }

    public int getSkin() {
        return this.entityData.get(DATA_SKIN);
    }

    // ------------------------------------------------------------------ kaydetme

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("DwVariant", getVariant());
        tag.putBoolean("DwDominant", isDominant());
        tag.putInt("DwSkin", getSkin());
        tag.putInt("DwHunt", this.huntMode);
        tag.putInt("DwVanishTimer", this.vanishTimer);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.contains("DwVariant")) this.entityData.set(DATA_VARIANT, clamp(tag.getInt("DwVariant"), 0, 2));
        if (tag.contains("DwDominant")) this.entityData.set(DATA_DOMINANT, tag.getBoolean("DwDominant"));
        if (tag.contains("DwSkin")) this.entityData.set(DATA_SKIN, clamp(tag.getInt("DwSkin"), 0, 4));
        this.vanishTimer = tag.getInt("DwVanishTimer");
        int hunt = tag.getInt("DwHunt");
        if (hunt > 0) setHunt(hunt);
    }

    // ------------------------------------------------------------------ DwEntityApi

    private static String normalize(String name) {
        if (name == null) return "";
        return name.startsWith("duskwalker:") ? name.substring("duskwalker:".length()) : name;
    }

    private static int clamp(int v, int lo, int hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    @Override
    public void dwTriggerEvent(String bedrockEvent) {
        String ev = normalize(bedrockEvent);
        if (this.level().isClientSide()) return;
        switch (ev) {
            case "vanish" -> this.discard();
            case "hunt_start" -> setHunt(1);
            case "hunt_start_fast" -> setHunt(2);
            case "hunt_end" -> setHunt(0);
            case "skin_1", "skin_2", "skin_3", "skin_4" -> {
                if (kind.hasSkin) this.entityData.set(DATA_SKIN, ev.charAt(5) - '0');
            }
            default -> { /* bilinmeyen olay: Bedrock gibi sessizce yoksay */ }
        }
    }

    @Override
    public void dwSetProperty(String name, Object value) {
        switch (normalize(name)) {
            case "variant" -> {
                if (kind.hasVariant) this.entityData.set(DATA_VARIANT, clamp(toInt(value), 0, 2));
            }
            case "dominant" -> {
                if (kind.hasVariant) this.entityData.set(DATA_DOMINANT, toBool(value));
            }
            case "skin" -> {
                if (kind.hasSkin) this.entityData.set(DATA_SKIN, clamp(toInt(value), 0, 4));
            }
            default -> { }
        }
    }

    @Override
    public Object dwGetProperty(String name) {
        return switch (normalize(name)) {
            case "variant" -> kind.hasVariant ? (Object) getVariant() : null;
            case "dominant" -> kind.hasVariant ? (Object) isDominant() : null;
            case "skin" -> kind.hasSkin ? (Object) getSkin() : null;
            default -> null;
        };
    }

    @Override
    public void dwPlayAnimation(String animationName) {
        if (animationName == null || !animationName.endsWith("attack")) return;
        if (!this.level().isClientSide()) this.level().broadcastEntityEvent(this, EVENT_ATTACK_ANIM);
    }

    private static int toInt(Object v) {
        if (v instanceof Number n) return n.intValue();
        if (v instanceof Boolean b) return b ? 1 : 0;
        if (v instanceof String s) {
            try { return (int) Double.parseDouble(s.trim()); } catch (NumberFormatException e) { return 0; }
        }
        return 0;
    }

    private static boolean toBool(Object v) {
        if (v instanceof Boolean b) return b;
        if (v instanceof Number n) return n.doubleValue() != 0.0D;
        if (v instanceof String s) return Boolean.parseBoolean(s.trim());
        return false;
    }

    // ------------------------------------------------------------------ av (hunt)

    private void clearHuntGoals() {
        if (huntTargetGoal != null) this.targetSelector.removeGoal(huntTargetGoal);
        if (huntMeleeGoal != null) this.goalSelector.removeGoal(huntMeleeGoal);
        if (huntDoorGoal != null) this.goalSelector.removeGoal(huntDoorGoal);
        huntTargetGoal = null;
        huntMeleeGoal = null;
        huntDoorGoal = null;
    }

    /** 0 = av yok, 1 = hunt_start (0.34 / x1.3), 2 = hunt_start_fast (0.52 / x1.7). */
    private void setHunt(int mode) {
        if (this.level().isClientSide() || !kind.hunter) return;
        clearHuntGoals();
        this.huntMode = mode;
        AttributeInstance speed = this.getAttribute(Attributes.MOVEMENT_SPEED);
        AttributeInstance dmg = this.getAttribute(Attributes.ATTACK_DAMAGE);
        if (mode == 0) {
            if (speed != null) speed.setBaseValue(kind.moveSpeed);
            this.setTarget(null);
            this.setAggressive(false);
            this.getNavigation().stop();
            return;
        }
        double base = mode == 2 ? 0.52D : 0.34D;
        double mult = mode == 2 ? 1.7D : 1.3D;
        if (speed != null) speed.setBaseValue(base);
        if (dmg != null) dmg.setBaseValue(6.0D);
        // nearest_attackable_target: oyuncu, must_see=false, yaricap 48 (FOLLOW_RANGE)
        huntTargetGoal = new NearestAttackableTargetGoal<>(this, Player.class, false);
        huntMeleeGoal = new DwMeleeGoal(this, mult, 1.3D);
        huntDoorGoal = new OpenDoorGoal(this, false);
        this.targetSelector.addGoal(0, huntTargetGoal);
        this.goalSelector.addGoal(1, huntMeleeGoal);
        this.goalSelector.addGoal(2, huntDoorGoal);
    }

    @Override
    public boolean doHurtTarget(Entity target) {
        dwPlayAnimation("animation.duskwalker.attack");
        return super.doHurtTarget(target);
    }

    // ------------------------------------------------------------------ tik / istemci

    @Override
    public void tick() {
        super.tick();
        if (!this.level().isClientSide() && kind.vanishTicks > 0 && !this.isRemoved()) {
            if (++this.vanishTimer >= kind.vanishTicks) {
                this.discard();
            }
        }
    }

    @Override
    public void handleEntityEvent(byte id) {
        if (id == EVENT_ATTACK_ANIM) {
            this.clientAttackStartAge = this.tickCount;
        } else {
            super.handleEntityEvent(id);
        }
    }

    // ------------------------------------------------------------------ hasar / itme / kalicilik

    @Override
    public boolean isInvulnerableTo(DamageSource source) {
        if (source.is(DamageTypeTags.BYPASSES_INVULNERABILITY) || source.isCreativePlayer()) {
            return super.isInvulnerableTo(source);
        }
        if (kind.takesLavaDamage && source.is(DamageTypes.LAVA)) {
            return super.isInvulnerableTo(source);
        }
        return true;
    }

    @Override
    public boolean causeFallDamage(float fallDistance, float multiplier, DamageSource source) {
        return false;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public PushReaction getPistonPushReaction() {
        return PushReaction.IGNORE;
    }

    @Override
    public boolean removeWhenFarAway(double distanceToClosestPlayer) {
        return kind.despawnFromDistance;
    }

    @Override
    public boolean isCustomNameVisible() {
        return (kind.alwaysShowName && this.hasCustomName()) || super.isCustomNameVisible();
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return null;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return null;
    }

    @Override
    protected void playStepSound(BlockPos pos, BlockState state) {
    }
}
