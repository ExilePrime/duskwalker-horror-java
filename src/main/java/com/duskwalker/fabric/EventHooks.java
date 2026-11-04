package com.duskwalker.fabric;

import java.lang.reflect.Field;
import java.util.List;

import com.duskwalker.bedrock.Events;
import com.duskwalker.bedrock.Log;
import com.duskwalker.bedrock.Opts;
import com.duskwalker.bedrock.Signal;
import com.duskwalker.bedrock.Sys;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.fabricmc.fabric.api.message.v1.ServerMessageEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;

/**
 * Fabric API olaylari + mixin kancalari -> Events.* (Bedrock world.before/afterEvents).
 * Kancalar JVM omru boyunca bir kez kaydedilir; {@link #setActive} ile sunucu calisirken etkin tutulur.
 */
public final class EventHooks {
    private EventHooks() {}

    private static boolean registered;
    private static volatile boolean active;

    public static void setActive(boolean a) { active = a; }

    private static boolean on() { return active && Wrap.hasServer(); }

    // ================================================================= kayit
    public static synchronized void register() {
        if (registered) return;
        registered = true;

        // ---- sohbet (ALLOW: iptal edilirse mesaj gitmez)
        ServerMessageEvents.ALLOW_CHAT_MESSAGE.register((message, sender, params) -> {
            if (!on()) return true;
            try {
                Events.ChatSend ev = new Events.ChatSend();
                ev.sender = Wrap.player(sender);
                ev.message = message.signedContent();
                Events.Before.chatSend.fire(ev);
                return !ev.cancel;
            } catch (Throwable t) {
                Log.warn("chatSend hook failed: " + t);
                return true;
            }
        });

        // ---- ayrilma (once: oyuncu hala gecerli; sonra: sonraki tik)
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            if (!on()) return;
            try {
                ServerPlayer p = handler.getPlayer();
                Events.PlayerLeaveBefore b = new Events.PlayerLeaveBefore();
                b.player = Wrap.player(p);
                Events.Before.playerLeave.fire(b);
                final String id = p.getUUID().toString();
                final String name = p.getGameProfile().getName();
                Sys.run(() -> {
                    Events.PlayerLeave a = new Events.PlayerLeave();
                    a.playerId = id;
                    a.playerName = name;
                    Events.After.playerLeave.fire(a);
                });
            } catch (Throwable t) {
                Log.warn("playerLeave hook failed: " + t);
            }
        });

        // ---- katilim (initialSpawn=true) ve yeniden dogus (false)
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            if (!on()) return;
            final ServerPlayer p = handler.getPlayer();
            Sys.run(() -> spawn(p, true));
        });
        ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> {
            if (!on() || alive) return;
            spawn(newPlayer, false);
        });

        // ---- blok kirma (AFTER: blok artik hava)
        PlayerBlockBreakEvents.AFTER.register((world, player, pos, state, be) -> {
            if (!on() || !(world instanceof ServerLevel sl) || !(player instanceof ServerPlayer sp)) return;
            try {
                Events.PlayerBreakBlock ev = new Events.PlayerBreakBlock();
                ev.player = Wrap.player(sp);
                ev.block = Wrap.block(sl, pos);
                ev.brokenBlockPermutation = new McPerm(state);
                ev.itemStack = ItemConv.fromMc(sp.getMainHandItem());
                Events.After.playerBreakBlock.fire(ev);
            } catch (Throwable t) {
                Log.warn("playerBreakBlock hook failed: " + t);
            }
        });

        // ---- blok etkilesimi (ana el; etki gorunsun diye bir sonraki tikte)
        UseBlockCallback.EVENT.register((player, world, hand, hit) -> {
            if (!on() || world.isClientSide || hand != InteractionHand.MAIN_HAND || !(player instanceof ServerPlayer sp) || !(world instanceof ServerLevel sl)) {
                return InteractionResult.PASS;
            }
            try {
                final com.duskwalker.bedrock.ItemStack item = ItemConv.fromMc(sp.getMainHandItem());
                final BlockPos pos = hit.getBlockPos();
                Sys.run(() -> {
                    Events.PlayerInteractWithBlock ev = new Events.PlayerInteractWithBlock();
                    ev.player = Wrap.player(sp);
                    ev.block = Wrap.block(sl, pos);
                    ev.itemStack = item;
                    Events.After.playerInteractWithBlock.fire(ev);
                });
            } catch (Throwable t) {
                Log.warn("playerInteractWithBlock hook failed: " + t);
            }
            return InteractionResult.PASS;
        });

        // ---- varlik etkilesimi
        UseEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
            if (!on() || world.isClientSide || hand != InteractionHand.MAIN_HAND || !(player instanceof ServerPlayer sp)) return InteractionResult.PASS;
            try {
                Events.PlayerInteractWithEntity ev = new Events.PlayerInteractWithEntity();
                ev.player = Wrap.player(sp);
                ev.target = Wrap.entity(entity);
                ev.itemStack = ItemConv.fromMc(sp.getMainHandItem());
                Events.After.playerInteractWithEntity.fire(ev);
            } catch (Throwable t) {
                Log.warn("playerInteractWithEntity hook failed: " + t);
            }
            return InteractionResult.PASS;
        });

        // ---- esya kullanimi
        UseItemCallback.EVENT.register((player, world, hand) -> {
            if (on() && !world.isClientSide && hand == InteractionHand.MAIN_HAND && player instanceof ServerPlayer sp) {
                try {
                    Events.ItemUse ev = new Events.ItemUse();
                    ev.source = Wrap.player(sp);
                    ev.itemStack = ItemConv.fromMc(sp.getItemInHand(hand));
                    Events.After.itemUse.fire(ev);
                } catch (Throwable t) {
                    Log.warn("itemUse hook failed: " + t);
                }
            }
            return InteractionResultHolder.pass(player.getItemInHand(hand));
        });

        // ---- olum
        ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
            if (!on() || !(entity.level() instanceof ServerLevel)) return;
            try {
                Events.EntityDie ev = new Events.EntityDie();
                ev.deadEntity = Wrap.entity(entity);
                ev.damageSource = damageSource(source);
                Events.After.entityDie.fire(ev);
            } catch (Throwable t) {
                Log.warn("entityDie hook failed: " + t);
            }
            if (!(entity instanceof ServerPlayer)) {
                final java.util.UUID id = entity.getUUID();
                Sys.runTimeout(() -> { if (DynData.isLoaded()) DynData.get().clearEntity(id); }, 200);
            }
        });

        // ---- hasar
        ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, baseDamageTaken, damageTaken, blocked) -> {
            if (!on() || blocked || !(entity.level() instanceof ServerLevel)) return;
            try {
                Events.EntityHurt ev = new Events.EntityHurt();
                ev.hurtEntity = Wrap.entity(entity);
                ev.damageSource = damageSource(source);
                ev.damage = damageTaken;
                Events.After.entityHurt.fire(ev);
            } catch (Throwable t) {
                Log.warn("entityHurt hook failed: " + t);
            }
        });
    }

    // ================================================================= yardimcilar
    private static void spawn(ServerPlayer p, boolean initial) {
        try {
            if (p == null || p.hasDisconnected()) return;
            Events.PlayerSpawn ev = new Events.PlayerSpawn();
            ev.player = Wrap.player(p);
            ev.initialSpawn = initial;
            Events.After.playerSpawn.fire(ev);
        } catch (Throwable t) {
            Log.warn("playerSpawn hook failed: " + t);
        }
    }

    private static Opts.DamageSource damageSource(DamageSource s) {
        Opts.DamageSource o = new Opts.DamageSource();
        o.cause = IdMap.damageCause(s);
        o.damagingEntity = Wrap.entity(s.getEntity());
        return o;
    }

    // ================================================================= mixin girisleri
    /** BlockItemMixin: blok yerlestirildi. */
    public static void onBlockPlaced(BlockPlaceContext ctx) {
        if (!on()) return;
        try {
            if (!(ctx.getLevel() instanceof ServerLevel sl) || !(ctx.getPlayer() instanceof ServerPlayer sp)) return;
            Events.PlayerPlaceBlock ev = new Events.PlayerPlaceBlock();
            ev.player = Wrap.player(sp);
            ev.block = Wrap.block(sl, ctx.getClickedPos());
            Events.After.playerPlaceBlock.fire(ev);
        } catch (Throwable t) {
            Log.warn("playerPlaceBlock hook failed: " + t);
        }
    }

    /** ExplosionMixin: patlama tamamlandi. */
    public static void onExplosion(Level level, net.minecraft.world.entity.Entity source) {
        if (!on()) return;
        try {
            if (!(level instanceof ServerLevel sl)) return;
            Events.Explosion ev = new Events.Explosion();
            ev.source = Wrap.entity(source);
            ev.dimension = Wrap.dim(sl);
            Events.After.explosion.fire(ev);
        } catch (Throwable t) {
            Log.warn("explosion hook failed: " + t);
        }
    }

    // ================================================================= dinleyici temizligi (dunya yeniden yuklenirken)
    /** Events.Before/After icindeki tum Signal dinleyicilerini siler; Mod.init() yeniden cagrilabilsin diye. */
    public static void clearListeners() {
        try {
            Field lf = Signal.class.getDeclaredField("listeners");
            lf.setAccessible(true);
            for (Class<?> c : new Class<?>[] { Events.Before.class, Events.After.class }) {
                for (Field f : c.getDeclaredFields()) {
                    if (!Signal.class.isAssignableFrom(f.getType())) continue;
                    Object sig = f.get(null);
                    if (sig != null) ((List<?>) lf.get(sig)).clear();
                }
            }
        } catch (ReflectiveOperationException e) {
            Log.warn("clearListeners failed: " + e);
        }
    }
}
