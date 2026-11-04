package com.duskwalker.fabric;

import java.lang.reflect.Field;

import org.slf4j.Logger;

import com.duskwalker.bedrock.Events;
import com.duskwalker.bedrock.Log;
import com.duskwalker.bedrock.Platform;
import com.duskwalker.bedrock.Sys;
import com.duskwalker.fabric.entity.DwEntities;
import com.duskwalker.fabric.entity.DwSounds;
import com.duskwalker.fabric.net.DwNetwork;
import com.duskwalker.mod.Mod;
import com.mojang.logging.LogUtils;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.level.ServerPlayer;

/** Ana giris: kayitlar, Platform kurulumu, sunucu yasam dongusu. */
public class DuskwalkerMod implements ModInitializer {
    public static final String MOD_ID = "duskwalker";
    private static final Logger LOGGER = LogUtils.getLogger();

    private static volatile boolean running;

    @Override
    public void onInitialize() {
        Log.sink = s -> {
            if (s.startsWith("WARN ")) LOGGER.warn("[Duskwalker] {}", s.substring(5));
            else LOGGER.info("[Duskwalker] {}", s);
        };

        DwNetwork.registerPayloads();
        DwSounds.register();
        DwEntities.register();

        Platform.install(new FabricPlatform());
        EventHooks.register();

        ServerLifecycleEvents.SERVER_STARTING.register(server -> Wrap.setServer(server));

        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            Wrap.setServer(server);
            DynData.load(server);
            EventHooks.setActive(true);
            running = true;
            try {
                Mod.init();
            } catch (Throwable t) {
                LOGGER.error("[Duskwalker] Mod.init failed", t);
            }
        });

        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (!running) return;
            try {
                Wrap.trackPlayers();
            } catch (Throwable t) {
                LOGGER.warn("[Duskwalker] player tracking failed: {}", t.toString());
            }
            Sys.tick();
        });

        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            // Oyuncular kapanista ayrilmis sayilir: scriptler durumlarini kaydedebilsin.
            try {
                for (ServerPlayer p : server.getPlayerList().getPlayers()) {
                    Events.PlayerLeaveBefore b = new Events.PlayerLeaveBefore();
                    b.player = Wrap.player(p);
                    Events.Before.playerLeave.fire(b);
                    Events.PlayerLeave a = new Events.PlayerLeave();
                    a.playerId = p.getUUID().toString();
                    a.playerName = p.getGameProfile().getName();
                    Events.After.playerLeave.fire(a);
                }
            } catch (Throwable t) {
                LOGGER.warn("[Duskwalker] shutdown leave events failed: {}", t.toString());
            }
            running = false;
            EventHooks.setActive(false);
            FormGui.reset();
            Sys.reset();
            EventHooks.clearListeners();
            resetModGuard();
        });

        ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
            DynData.unload();
            Wrap.reset();
        });
    }

    /** Mod.init() ayni JVM'de yeni dunya icin yeniden calisabilsin diye ozel 'done' bayragini sifirlar (yoksa tek seferlik kalir). */
    private static void resetModGuard() {
        try {
            Field f = Mod.class.getDeclaredField("done");
            f.setAccessible(true);
            f.setBoolean(null, false);
        } catch (ReflectiveOperationException e) {
            LOGGER.warn("[Duskwalker] Mod.done could not be reset; mod stays initialised once per JVM: {}", e.toString());
        }
    }
}
