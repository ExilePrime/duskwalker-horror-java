package com.duskwalker.fabric;

import java.util.ArrayList;
import java.util.List;

import com.duskwalker.bedrock.BedrockException;
import com.duskwalker.bedrock.Block;
import com.duskwalker.bedrock.Dimension;
import com.duskwalker.bedrock.Entity;
import com.duskwalker.bedrock.ItemStack;
import com.duskwalker.bedrock.Opts;
import com.duskwalker.bedrock.Player;
import com.duskwalker.bedrock.Vec3;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;

/** Bedrock Dimension -> ServerLevel sarmalayicisi. */
public final class McDimension implements Dimension {
    private final ServerLevel level;

    public McDimension(ServerLevel level) { this.level = level; }

    public ServerLevel level() { return level; }

    @Override public String id() { return IdMap.dimensionBedrockId(level.dimension()); }

    // ---------------------------------------------------------------- bloklar
    @Override
    public Block getBlock(Vec3 loc) {
        BlockPos p = BlockPos.containing(loc.x(), loc.y(), loc.z());
        if (!level.isInWorldBounds(p) || !level.hasChunkAt(p)) return null;
        return Wrap.block(level, p);
    }

    @Override
    public Block getTopmostBlock(int x, int z) {
        BlockPos probe = new BlockPos(x, level.getMinBuildHeight(), z);
        if (!level.hasChunkAt(probe)) return null;
        int top = level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z) - 1;
        if (top < level.getMinBuildHeight()) return null;
        return Wrap.block(level, new BlockPos(x, top, z));
    }

    // ---------------------------------------------------------------- varliklar
    @Override
    public List<Entity> getEntities(Opts.EntityQuery q) {
        List<Entity> out = new ArrayList<>();
        Iterable<net.minecraft.world.entity.Entity> source;
        if (q.location != null && q.maxDistance != null) {
            double r = q.maxDistance;
            source = level.getEntities((net.minecraft.world.entity.Entity) null,
                    new AABB(q.location.x() - r, q.location.y() - r, q.location.z() - r,
                            q.location.x() + r, q.location.y() + r, q.location.z() + r),
                    e -> true);
        } else {
            source = level.getAllEntities();
        }
        String wantType = q.type == null ? null : (q.type.contains(":") ? q.type : "minecraft:" + q.type);
        for (net.minecraft.world.entity.Entity e : source) {
            if (e.isRemoved()) continue;
            if (q.location != null) {
                double dx = e.getX() - q.location.x(), dy = e.getY() - q.location.y(), dz = e.getZ() - q.location.z();
                double d = Math.sqrt(dx * dx + dy * dy + dz * dz);
                if (q.maxDistance != null && d > q.maxDistance) continue;
                if (q.minDistance != null && d < q.minDistance) continue;
            }
            McEntity w = Wrap.entity(e);
            String tid = IdMap.entityToBedrock(e.getType());
            if (wantType != null && !wantType.equals(tid)) continue;
            if (!q.excludeTypes.isEmpty()) {
                boolean ex = false;
                for (String t : q.excludeTypes) {
                    String tt = t.contains(":") ? t : "minecraft:" + t;
                    if (tt.equals(tid)) { ex = true; break; }
                }
                if (ex) continue;
            }
            boolean ok = true;
            for (String f : q.families) if (!w.hasFamily(f)) { ok = false; break; }
            if (!ok) continue;
            for (String f : q.excludeFamilies) if (w.hasFamily(f)) { ok = false; break; }
            if (!ok) continue;
            for (String t : q.tags) if (!e.getTags().contains(t)) { ok = false; break; }
            if (!ok) continue;
            out.add(w);
        }
        return out;
    }

    @Override
    public List<Player> getPlayers() {
        List<Player> out = new ArrayList<>();
        for (ServerPlayer p : level.players()) out.add(new McPlayer(p));
        return out;
    }

    @Override
    public Entity spawnEntity(String typeId, Vec3 loc) {
        EntityType<?> type = IdMap.entityType(typeId);
        if (type == null) throw new BedrockException("unknown entity type: " + typeId);
        BlockPos bp = BlockPos.containing(loc.x(), loc.y(), loc.z());
        if (!level.isInWorldBounds(bp) || !level.hasChunkAt(bp)) throw new BedrockException("spawn location is not loaded");
        net.minecraft.world.entity.Entity e = type.create(level);
        if (e == null) throw new BedrockException("cannot create entity: " + typeId);
        e.moveTo(loc.x(), loc.y(), loc.z(), 0.0F, 0.0F);
        if (e instanceof Mob m) {
            m.finalizeSpawn(level, level.getCurrentDifficultyAt(bp), MobSpawnType.COMMAND, null);
        }
        if (!level.addFreshEntity(e)) throw new BedrockException("spawn failed: " + typeId);
        return Wrap.entity(e);
    }

    @Override
    public Entity spawnItem(ItemStack item, Vec3 loc) {
        net.minecraft.world.item.ItemStack st = ItemConv.toMc(item);
        ItemEntity ie = new ItemEntity(level, loc.x(), loc.y(), loc.z(), st);
        ie.setDefaultPickUpDelay();
        if (!level.addFreshEntity(ie)) throw new BedrockException("item spawn failed");
        return Wrap.entity(ie);
    }

    // ---------------------------------------------------------------- efekt
    @Override
    public void spawnParticle(String particleId, Vec3 loc) {
        ParticleOptions p = IdMap.particle(particleId);
        if (p == null) return;
        level.sendParticles(p, loc.x(), loc.y(), loc.z(), 1, 0.0, 0.0, 0.0, 0.0);
    }

    @Override
    public void playSound(String soundId, Vec3 loc, Opts.Sound opts) {
        SoundEvent ev = IdMap.sound(soundId);
        if (ev == null) return;
        float vol = opts == null ? 1.0F : (float) opts.volume;
        float pitch = opts == null ? 1.0F : (float) opts.pitch;
        level.playSound(null, loc.x(), loc.y(), loc.z(), ev, IdMap.soundSource(soundId), vol, pitch);
    }

    @Override
    public String getWeather() {
        if (!level.dimensionType().hasSkyLight()) return "Clear";
        if (level.isThundering()) return "Thunder";
        if (level.isRaining()) return "Rain";
        return "Clear";
    }

    @Override
    public boolean runCommand(String command) {
        return CommandTranslator.run(level.getServer(), CommandTranslator.dimensionSource(level), command);
    }

    @Override public boolean equals(Object o) { return o instanceof McDimension d && d.level.dimension().equals(level.dimension()); }
    @Override public int hashCode() { return level.dimension().hashCode(); }
}
