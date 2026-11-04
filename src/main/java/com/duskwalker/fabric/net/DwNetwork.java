package com.duskwalker.fabric.net;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

/** Sunucu -> istemci efekt paketleri: fade, shake, fog push/pop. */
public final class DwNetwork {
    private static boolean registered;

    private DwNetwork() {
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath("duskwalker", path);
    }

    public record FadePayload(double r, double g, double b, double inSec, double holdSec, double outSec) implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<FadePayload> TYPE = new CustomPacketPayload.Type<>(id("fade"));
        public static final StreamCodec<RegistryFriendlyByteBuf, FadePayload> CODEC = StreamCodec.composite(
                ByteBufCodecs.DOUBLE, FadePayload::r,
                ByteBufCodecs.DOUBLE, FadePayload::g,
                ByteBufCodecs.DOUBLE, FadePayload::b,
                ByteBufCodecs.DOUBLE, FadePayload::inSec,
                ByteBufCodecs.DOUBLE, FadePayload::holdSec,
                ByteBufCodecs.DOUBLE, FadePayload::outSec,
                FadePayload::new);

        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record ShakePayload(double strength, double seconds, boolean positional) implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<ShakePayload> TYPE = new CustomPacketPayload.Type<>(id("shake"));
        public static final StreamCodec<RegistryFriendlyByteBuf, ShakePayload> CODEC = StreamCodec.composite(
                ByteBufCodecs.DOUBLE, ShakePayload::strength,
                ByteBufCodecs.DOUBLE, ShakePayload::seconds,
                ByteBufCodecs.BOOL, ShakePayload::positional,
                ShakePayload::new);

        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record FogPushPayload(String fogId, String userKey) implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<FogPushPayload> TYPE = new CustomPacketPayload.Type<>(id("fog_push"));
        public static final StreamCodec<RegistryFriendlyByteBuf, FogPushPayload> CODEC = StreamCodec.composite(
                ByteBufCodecs.STRING_UTF8, FogPushPayload::fogId,
                ByteBufCodecs.STRING_UTF8, FogPushPayload::userKey,
                FogPushPayload::new);

        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record FogPopPayload(String userKey) implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<FogPopPayload> TYPE = new CustomPacketPayload.Type<>(id("fog_pop"));
        public static final StreamCodec<RegistryFriendlyByteBuf, FogPopPayload> CODEC = StreamCodec.composite(
                ByteBufCodecs.STRING_UTF8, FogPopPayload::userKey,
                FogPopPayload::new);

        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** Her iki tarafta cagrilabilir; tekrar cagri zararsizdir. */
    public static void registerPayloads() {
        if (registered) return;
        registered = true;
        PayloadTypeRegistry.playS2C().register(FadePayload.TYPE, FadePayload.CODEC);
        PayloadTypeRegistry.playS2C().register(ShakePayload.TYPE, ShakePayload.CODEC);
        PayloadTypeRegistry.playS2C().register(FogPushPayload.TYPE, FogPushPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(FogPopPayload.TYPE, FogPopPayload.CODEC);
    }

    private static void send(ServerPlayer p, CustomPacketPayload payload) {
        if (p == null || p.hasDisconnected()) return;
        if (!ServerPlayNetworking.canSend(p, payload.type())) return; // modsuz istemci
        ServerPlayNetworking.send(p, payload);
    }

    public static void sendFade(ServerPlayer p, double r, double g, double b, double inSec, double holdSec, double outSec) {
        send(p, new FadePayload(r, g, b, inSec, holdSec, outSec));
    }

    public static void sendShake(ServerPlayer p, double strength, double seconds, boolean positional) {
        send(p, new ShakePayload(strength, seconds, positional));
    }

    public static void sendFogPush(ServerPlayer p, String fogId, String userKey) {
        send(p, new FogPushPayload(fogId, userKey));
    }

    public static void sendFogPop(ServerPlayer p, String userKey) {
        send(p, new FogPopPayload(userKey));
    }
}
