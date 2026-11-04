package com.duskwalker.fabric;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import com.duskwalker.bedrock.BedrockException;
import com.duskwalker.bedrock.Dimension;
import com.duskwalker.bedrock.Entity;
import com.duskwalker.bedrock.Opts;
import com.duskwalker.bedrock.Rot;
import com.duskwalker.bedrock.Vec3;
import com.duskwalker.fabric.entity.DwEntity;
import com.duskwalker.fabric.entity.DwEntityApi;

import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.RelativeMovement;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.horse.AbstractHorse;
import net.minecraft.world.entity.monster.AbstractIllager;
import net.minecraft.world.entity.monster.AbstractSkeleton;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.monster.Zombie;

/** Bedrock Entity -> Minecraft Entity sarmalayicisi. Kimlik UUID'dir; gerekirse canli nesne UUID ile yeniden bulunur. */
public class McEntity implements Entity {
    protected final UUID uuid;
    protected net.minecraft.world.entity.Entity ref;

    public McEntity(net.minecraft.world.entity.Entity e) {
        this.uuid = e.getUUID();
        this.ref = e;
    }

    // ---------------------------------------------------------------- cozumleme
    /** Gecerli canli Minecraft varligi; yoksa BedrockException. */
    public net.minecraft.world.entity.Entity mc() {
        net.minecraft.world.entity.Entity e = find();
        if (e == null) throw new BedrockException("Entity is not valid: " + uuid);
        return e;
    }

    private net.minecraft.world.entity.Entity find() {
        net.minecraft.world.entity.Entity e = ref;
        if (e instanceof ServerPlayer) {
            ServerPlayer sp = Wrap.server().getPlayerList().getPlayer(uuid);
            if (sp != null) ref = sp;
            return sp;
        }
        if (e != null && !e.isRemoved() && e.level() instanceof ServerLevel) return e;
        for (ServerLevel l : Wrap.server().getAllLevels()) {
            net.minecraft.world.entity.Entity f = l.getEntity(uuid);
            if (f != null && !f.isRemoved()) { ref = f; return f; }
        }
        return null;
    }

    @Override public Object raw() { return mc(); }

    // ---------------------------------------------------------------- kimlik
    @Override public String id() { return uuid.toString(); }
    @Override public String typeId() { return IdMap.entityToBedrock(mc().getType()); }
    @Override public boolean isValid() { return find() != null; }

    @Override
    public Vec3 location() {
        net.minecraft.world.entity.Entity e = mc();
        return new Vec3(e.getX(), e.getY(), e.getZ());
    }

    @Override
    public Dimension dimension() { return Wrap.dim((ServerLevel) mc().level()); }

    @Override
    public String nameTag() {
        net.minecraft.world.entity.Entity e = mc();
        Component c = e.getCustomName();
        return c == null ? "" : c.getString();
    }

    @Override
    public void setNameTag(String name) {
        net.minecraft.world.entity.Entity e = mc();
        e.setCustomName(name == null || name.isEmpty() ? null : Component.literal(name));
    }

    // ---------------------------------------------------------------- hareket
    @Override
    public void teleport(Vec3 loc, Opts.Teleport opts) {
        net.minecraft.world.entity.Entity e = mc();
        ServerLevel target = (ServerLevel) e.level();
        if (opts != null && opts.dimension != null) target = Wrap.level(opts.dimension);
        float yaw = e.getYRot();
        float pitch = e.getXRot();
        if (opts != null && opts.rotation != null) {
            pitch = (float) opts.rotation.x();
            yaw = (float) opts.rotation.y();
        } else if (opts != null && opts.facingLocation != null) {
            Vec3 f = opts.facingLocation;
            double dx = f.x() - loc.x();
            double dz = f.z() - loc.z();
            double dy = f.y() - (loc.y() + e.getEyeHeight());
            yaw = (float) (Mth.atan2(dz, dx) * 180.0 / Math.PI) - 90.0F;
            pitch = (float) -(Mth.atan2(dy, Math.sqrt(dx * dx + dz * dz)) * 180.0 / Math.PI);
        }
        boolean keep = opts != null && opts.keepVelocity;
        net.minecraft.world.phys.Vec3 vel = e.getDeltaMovement();
        e.teleportTo(target, loc.x(), loc.y(), loc.z(), Set.<RelativeMovement>of(), yaw, pitch);
        net.minecraft.world.entity.Entity after = find();
        if (after != null) {
            after.setDeltaMovement(keep ? vel : net.minecraft.world.phys.Vec3.ZERO);
        }
    }

    @Override public void kill() { mc().kill(); }

    @Override
    public void remove() {
        net.minecraft.world.entity.Entity e = mc();
        if (e instanceof ServerPlayer) throw new BedrockException("Player cannot be removed");
        e.discard();
        DynData.get().clearEntity(uuid);
    }

    @Override
    public void applyImpulse(Vec3 v) {
        net.minecraft.world.entity.Entity e = mc();
        e.push(v.x(), v.y(), v.z());
        e.hurtMarked = true;
    }

    @Override
    public Vec3 getVelocity() {
        net.minecraft.world.entity.Entity e = mc();
        if (e instanceof ServerPlayer) return Wrap.playerVelocity(uuid);
        net.minecraft.world.phys.Vec3 d = e.getDeltaMovement();
        return new Vec3(d.x, d.y, d.z);
    }

    @Override
    public Vec3 getViewDirection() {
        net.minecraft.world.phys.Vec3 d = mc().getLookAngle();
        return new Vec3(d.x, d.y, d.z);
    }

    @Override
    public Vec3 getHeadLocation() {
        net.minecraft.world.phys.Vec3 p = mc().getEyePosition();
        return new Vec3(p.x, p.y, p.z);
    }

    @Override
    public Rot getRotation() {
        net.minecraft.world.entity.Entity e = mc();
        return new Rot(e.getXRot(), e.getYRot());
    }

    // ---------------------------------------------------------------- olay / ozellik
    @Override
    public void triggerEvent(String event) {
        net.minecraft.world.entity.Entity e = mc();
        if (e instanceof DwEntityApi api) api.dwTriggerEvent(event);
        else if (event != null && event.endsWith("vanish") && !(e instanceof ServerPlayer)) e.discard();
    }

    @Override
    public void setProperty(String name, Object value) {
        if (mc() instanceof DwEntityApi api) api.dwSetProperty(name, value);
    }

    @Override
    public Object getProperty(String name) {
        return mc() instanceof DwEntityApi api ? api.dwGetProperty(name) : null;
    }

    @Override
    public void playAnimation(String animationName) {
        if (mc() instanceof DwEntityApi api) api.dwPlayAnimation(animationName);
    }

    // ---------------------------------------------------------------- efekt / hasar
    private LivingEntity living() {
        if (mc() instanceof LivingEntity l) return l;
        throw new BedrockException("Entity has no health/effects component");
    }

    @Override
    public void addEffect(String effectId, int durationTicks, int amplifier, boolean showParticles) {
        LivingEntity l = living();
        Holder<MobEffect> h = IdMap.effect(effectId);
        if (h == null) throw new BedrockException("unknown effect: " + effectId);
        l.addEffect(new MobEffectInstance(h, Math.max(1, durationTicks), Math.max(0, Math.min(255, amplifier)), false, showParticles));
    }

    @Override
    public void removeEffect(String effectId) {
        LivingEntity l = living();
        Holder<MobEffect> h = IdMap.effect(effectId);
        if (h != null) l.removeEffect(h);
    }

    @Override
    public boolean applyDamage(double amount, Opts.Damage opts) {
        net.minecraft.world.entity.Entity e = mc();
        if (!(e instanceof LivingEntity l)) return false;
        net.minecraft.world.entity.Entity by = opts != null && opts.damagingEntity != null ? (net.minecraft.world.entity.Entity) opts.damagingEntity.raw() : null;
        DamageSource src = IdMap.damageSource((ServerLevel) e.level(), opts == null ? null : opts.cause, by);
        return l.hurt(src, (float) amount);
    }

    // ---------------------------------------------------------------- durum
    @Override public boolean isOnGround() { return mc().onGround(); }
    @Override public boolean isSprinting() { return mc().isSprinting(); }
    @Override public boolean isSneaking() { return mc().isShiftKeyDown(); }
    @Override public boolean isSleeping() { return mc() instanceof LivingEntity l && l.isSleeping(); }

    @Override
    public boolean isTamed() {
        net.minecraft.world.entity.Entity e = mc();
        if (e instanceof TamableAnimal t) return t.isTame();
        if (e instanceof AbstractHorse h) return h.isTamed();
        return false;
    }

    @Override public boolean hasHealth() { return mc() instanceof LivingEntity; }
    @Override public double health() { return mc() instanceof LivingEntity l ? l.getHealth() : 0; }
    @Override public double maxHealth() { return mc() instanceof LivingEntity l ? l.getMaxHealth() : 0; }

    // ---------------------------------------------------------------- etiket / aile
    @Override public List<String> getTags() { return new ArrayList<>(mc().getTags()); }
    @Override public void addTag(String tag) { mc().addTag(tag); }
    @Override public void removeTag(String tag) { mc().removeTag(tag); }
    @Override public boolean hasTag(String tag) { return mc().getTags().contains(tag); }

    @Override
    public boolean hasFamily(String family) {
        net.minecraft.world.entity.Entity e = mc();
        if (e instanceof DwEntity d) {
            String k = d.getKind().id;
            switch (d.getKind()) {
                case DUSKWALKER: return family.equals("duskwalker") || family.equals("monster") || family.equals("mob");
                case ECHO: case EFFIGY: return family.equals("duskwalker_creature") || family.equals(k) || family.equals("mob");
                case SEEP: case WATCHER: return family.equals("duskwalker_creature") || family.equals(k) || family.equals("monster") || family.equals("mob");
                case AYLIN: return family.equals("aylin") || family.equals("mob");
                default: return false;
            }
        }
        switch (family) {
            case "monster": return e instanceof Enemy;
            case "mob": return e instanceof Mob;
            case "player": return e instanceof net.minecraft.world.entity.player.Player;
            case "animal": return e instanceof Animal;
            case "undead": return e.getType().is(EntityTypeTags.UNDEAD);
            case "arthropod": return e.getType().is(EntityTypeTags.ARTHROPOD);
            case "inanimate": return !(e instanceof LivingEntity);
            case "zombie": return e instanceof Zombie;
            case "skeleton": return e instanceof AbstractSkeleton;
            case "illager": return e instanceof AbstractIllager;
            default: return IdMap.strip(typeId()).equals(family);
        }
    }

    // ---------------------------------------------------------------- dinamik ozellikler
    @Override public Object getDynamicProperty(String key) { return DynData.get().getEntity(uuid, key); }
    @Override public void setDynamicProperty(String key, Object value) { DynData.get().setEntity(uuid, key, value); }
    @Override public void clearDynamicProperties() { DynData.get().clearEntity(uuid); }

    // ---------------------------------------------------------------- esitlik
    @Override public boolean equals(Object o) { return o instanceof Entity en && en.id().equals(uuid.toString()); }
    @Override public int hashCode() { return uuid.hashCode(); }
    @Override public String toString() { return "McEntity[" + uuid + "]"; }
}
