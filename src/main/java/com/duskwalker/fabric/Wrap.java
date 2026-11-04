package com.duskwalker.fabric;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import com.duskwalker.bedrock.BedrockException;
import com.duskwalker.bedrock.Dimension;
import com.duskwalker.bedrock.Vec3;

import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/** Sarmalayici fabrikasi, sunucu referansi ve oyuncu hareket (hiz) takibi. */
public final class Wrap {
    private Wrap() {}

    private static MinecraftServer server;
    private static final Map<UUID, Vec3> lastPos = new HashMap<>();
    private static final Map<UUID, Vec3> velocity = new HashMap<>();

    public static void setServer(MinecraftServer s) { server = s; }

    public static MinecraftServer server() {
        if (server == null) throw new BedrockException("Server not running");
        return server;
    }

    public static boolean hasServer() { return server != null; }

    public static McEntity entity(net.minecraft.world.entity.Entity e) {
        if (e == null) return null;
        if (e instanceof ServerPlayer sp) return new McPlayer(sp);
        return new McEntity(e);
    }

    public static McPlayer player(ServerPlayer p) { return p == null ? null : new McPlayer(p); }

    public static McDimension dim(ServerLevel l) { return l == null ? null : new McDimension(l); }

    public static McBlock block(ServerLevel l, BlockPos p) { return new McBlock(l, p.immutable()); }

    /** Bedrock Dimension -> ServerLevel */
    public static ServerLevel level(Dimension d) {
        if (d instanceof McDimension m) return m.level();
        throw new BedrockException("foreign Dimension implementation");
    }

    // ---------------------------------------------------------------- oyuncu hizi (blok/tik)
    /** Her sunucu tikinin sonunda cagrilir. */
    public static void trackPlayers() {
        if (server == null) return;
        Map<UUID, Vec3> seen = new HashMap<>();
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            Vec3 now = new Vec3(p.getX(), p.getY(), p.getZ());
            Vec3 prev = lastPos.get(p.getUUID());
            velocity.put(p.getUUID(), prev == null ? new Vec3(0, 0, 0) : new Vec3(now.x() - prev.x(), now.y() - prev.y(), now.z() - prev.z()));
            seen.put(p.getUUID(), now);
        }
        lastPos.clear();
        lastPos.putAll(seen);
        velocity.keySet().retainAll(seen.keySet());
    }

    public static Vec3 playerVelocity(UUID id) {
        Vec3 v = velocity.get(id);
        return v == null ? new Vec3(0, 0, 0) : v;
    }

    public static void reset() {
        server = null;
        lastPos.clear();
        velocity.clear();
    }
}
