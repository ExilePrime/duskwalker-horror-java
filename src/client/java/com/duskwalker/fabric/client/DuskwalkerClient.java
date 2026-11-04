package com.duskwalker.fabric.client;

import com.duskwalker.fabric.client.fx.DwClock;
import com.duskwalker.fabric.client.fx.DwFade;
import com.duskwalker.fabric.client.fx.DwFog;
import com.duskwalker.fabric.client.fx.DwShake;
import com.duskwalker.fabric.entity.DwEntities;
import com.duskwalker.fabric.net.DwNetwork;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.EntityModelLayerRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;

/** Istemci girisi: render kayitlari, model katmani, payload alicilari, HUD (fade) ve tik olaylari. */
public class DuskwalkerClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        DwNetwork.registerPayloads();

        EntityModelLayerRegistry.registerModelLayer(DwRenderer.LAYER, DwGeometry::createLayer);
        EntityRendererRegistry.register(DwEntities.DUSKWALKER, DwRenderer::new);
        EntityRendererRegistry.register(DwEntities.ECHO, DwRenderer::new);
        EntityRendererRegistry.register(DwEntities.SEEP, DwRenderer::new);
        EntityRendererRegistry.register(DwEntities.WATCHER, DwRenderer::new);
        EntityRendererRegistry.register(DwEntities.EFFIGY, DwRenderer::new);
        EntityRendererRegistry.register(DwEntities.AYLIN, DwRenderer::new);

        ClientPlayNetworking.registerGlobalReceiver(DwNetwork.FadePayload.TYPE, (p, ctx) ->
                DwFade.start(p.r(), p.g(), p.b(), p.inSec(), p.holdSec(), p.outSec()));
        ClientPlayNetworking.registerGlobalReceiver(DwNetwork.ShakePayload.TYPE, (p, ctx) ->
                DwShake.start(p.strength(), p.seconds(), p.positional()));
        ClientPlayNetworking.registerGlobalReceiver(DwNetwork.FogPushPayload.TYPE, (p, ctx) ->
                DwFog.push(p.fogId(), p.userKey()));
        ClientPlayNetworking.registerGlobalReceiver(DwNetwork.FogPopPayload.TYPE, (p, ctx) ->
                DwFog.pop(p.userKey()));

        HudRenderCallback.EVENT.register((graphics, delta) ->
                DwFade.render(graphics, delta.getGameTimeDeltaPartialTick(false)));

        ClientTickEvents.END_CLIENT_TICK.register(client -> DwClock.tick());

        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            DwFade.clear();
            DwShake.clear();
            DwFog.clear();
            DwClock.reset();
        });
    }
}
