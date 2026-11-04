package com.duskwalker.fabric;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import com.duskwalker.fabric.entity.DwSounds;

import net.minecraft.core.Holder;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageSources;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Bedrock <-> Java kimlik cevirileri (blok, esya, varlik turu, parcacik, ses, efekt, hasar nedeni).
 * Bilinmeyen kimlikte null doner (cagiran karar verir). Kapsam kontrol listesi: docs/ID_COVERAGE.md
 */
public final class IdMap {
    private IdMap() {}

    private static final String MC = "minecraft:";

    // ------------------------------------------------------------------ genel
    public static String strip(String id) {
        if (id == null) return "";
        return id.startsWith(MC) ? id.substring(MC.length()) : id;
    }

    private static ResourceLocation rl(String id) {
        return ResourceLocation.tryParse(id.contains(":") ? id : MC + id);
    }

    // ------------------------------------------------------------------ blok
    /** Bedrock blok adi (oneksiz) -> Java blok adi (oneksiz). */
    private static final Map<String, String> BLOCK_ALIAS = new HashMap<>();
    static {
        BLOCK_ALIAS.put("web", "cobweb");
        BLOCK_ALIAS.put("standing_sign", "oak_sign");
        BLOCK_ALIAS.put("wall_sign", "oak_wall_sign");
        BLOCK_ALIAS.put("flowing_water", "water");
        BLOCK_ALIAS.put("flowing_lava", "lava");
        BLOCK_ALIAS.put("grass", "grass_block");
        BLOCK_ALIAS.put("stonebrick", "stone_bricks");
        BLOCK_ALIAS.put("sea_lantern", "sea_lantern");
        BLOCK_ALIAS.put("unlit_redstone_torch", "redstone_torch");
        BLOCK_ALIAS.put("lit_pumpkin", "jack_o_lantern");
        BLOCK_ALIAS.put("reeds", "sugar_cane");
        BLOCK_ALIAS.put("tallgrass", "short_grass");
        BLOCK_ALIAS.put("short_grass", "short_grass");
        BLOCK_ALIAS.put("grass_path", "dirt_path");
        BLOCK_ALIAS.put("portal", "nether_portal");
        BLOCK_ALIAS.put("end_bricks", "end_stone_bricks");
        BLOCK_ALIAS.put("stone_brick_stairs", "stone_brick_stairs");
        BLOCK_ALIAS.put("mob_spawner", "spawner");
        BLOCK_ALIAS.put("quartz_ore", "nether_quartz_ore");
        BLOCK_ALIAS.put("log", "oak_log");
        BLOCK_ALIAS.put("planks", "oak_planks");
    }

    /** "standing_sign" ve "wall_sign" disinda "<tur>_standing_sign" -> "<tur>_sign" */
    public static String blockJavaName(String bedrockId) {
        String n = strip(bedrockId);
        String a = BLOCK_ALIAS.get(n);
        if (a != null) return a;
        if (n.endsWith("_standing_sign")) return n.substring(0, n.length() - "_standing_sign".length()) + "_sign";
        return n;
    }

    /** Komutlar icin tam Java blok kimligi ("minecraft:cobweb"). */
    public static String blockJavaId(String bedrockId) {
        if (bedrockId != null && bedrockId.contains(":") && !bedrockId.startsWith(MC)) return bedrockId;
        return MC + blockJavaName(bedrockId);
    }

    public static Block block(String bedrockId) {
        ResourceLocation r = bedrockId != null && bedrockId.contains(":") && !bedrockId.startsWith(MC)
                ? ResourceLocation.tryParse(bedrockId) : rl(blockJavaName(bedrockId));
        if (r == null) return null;
        Optional<Block> o = BuiltInRegistries.BLOCK.getOptional(r);
        return o.orElse(null);
    }

    /** Java blok durumu -> Bedrock kimligi ("minecraft:standing_sign", "minecraft:web", "minecraft:water" / "minecraft:flowing_water"). */
    public static String blockToBedrock(BlockState state) {
        if (state.isAir()) return MC + "air";
        ResourceLocation key = BuiltInRegistries.BLOCK.getKey(state.getBlock());
        if (!"minecraft".equals(key.getNamespace())) return key.toString();
        String p = key.getPath();
        switch (p) {
            case "cobweb": return MC + "web";
            case "oak_sign": return MC + "standing_sign";
            case "oak_wall_sign": return MC + "wall_sign";
            case "wall_torch": return MC + "torch";
            case "soul_wall_torch": return MC + "soul_torch";
            case "redstone_wall_torch": return MC + "redstone_torch";
            case "cave_air": case "void_air": return MC + "air";
            case "dirt_path": return MC + "grass_path";
            case "nether_portal": return MC + "portal";
            case "spawner": return MC + "mob_spawner";
            case "water": {
                var lvl = state.getValues().entrySet().stream().filter(e -> e.getKey().getName().equals("level")).findFirst();
                boolean source = lvl.isEmpty() || Integer.valueOf(0).equals(lvl.get().getValue());
                return MC + (source ? "water" : "flowing_water");
            }
            case "lava": {
                var lvl = state.getValues().entrySet().stream().filter(e -> e.getKey().getName().equals("level")).findFirst();
                boolean source = lvl.isEmpty() || Integer.valueOf(0).equals(lvl.get().getValue());
                return MC + (source ? "lava" : "flowing_lava");
            }
            default:
                if (p.endsWith("_sign") && !p.endsWith("_wall_sign") && !p.endsWith("_hanging_sign")) {
                    return MC + p.substring(0, p.length() - "_sign".length()) + "_standing_sign";
                }
                return MC + p;
        }
    }

    // ------------------------------------------------------------------ esya
    private static final Map<String, String> ITEM_ALIAS = new HashMap<>();
    static {
        ITEM_ALIAS.put("web", "cobweb");
        ITEM_ALIAS.put("standing_sign", "oak_sign");
        ITEM_ALIAS.put("wall_sign", "oak_sign");
        ITEM_ALIAS.put("reeds", "sugar_cane");
        ITEM_ALIAS.put("lit_pumpkin", "jack_o_lantern");
        ITEM_ALIAS.put("stonebrick", "stone_bricks");
        ITEM_ALIAS.put("fireworks", "firework_rocket");
        ITEM_ALIAS.put("netherbrick", "nether_brick");
        ITEM_ALIAS.put("grass", "grass_block");
        ITEM_ALIAS.put("tallgrass", "short_grass");
    }

    public static String itemJavaName(String bedrockId) {
        String n = strip(bedrockId);
        String a = ITEM_ALIAS.get(n);
        if (a != null) return a;
        if (n.endsWith("_standing_sign")) return n.substring(0, n.length() - "_standing_sign".length()) + "_sign";
        return n;
    }

    public static String itemJavaId(String bedrockId) {
        if (bedrockId != null && bedrockId.contains(":") && !bedrockId.startsWith(MC)) return bedrockId;
        return MC + itemJavaName(bedrockId);
    }

    public static Item item(String bedrockId) {
        ResourceLocation r = bedrockId != null && bedrockId.contains(":") && !bedrockId.startsWith(MC)
                ? ResourceLocation.tryParse(bedrockId) : rl(itemJavaName(bedrockId));
        if (r == null) return null;
        return BuiltInRegistries.ITEM.getOptional(r).orElse(null);
    }

    public static String itemToBedrock(Item item) {
        ResourceLocation key = BuiltInRegistries.ITEM.getKey(item);
        if (!"minecraft".equals(key.getNamespace())) return key.toString();
        String p = key.getPath();
        switch (p) {
            case "cobweb": return MC + "web";
            case "firework_rocket": return MC + "fireworks";
            case "nether_brick": return MC + "netherbrick";
            default:
                if (p.endsWith("_sign") && !p.endsWith("_hanging_sign")) {
                    // Bedrock esya kimligi oak icin "oak_sign" dir (standing_sign blok kimligidir)
                    return MC + p;
                }
                return MC + p;
        }
    }

    // ------------------------------------------------------------------ varlik turu
    private static final Map<String, String> ENTITY_ALIAS = new HashMap<>();
    private static final Map<String, String> ENTITY_REVERSE = new HashMap<>();
    static {
        String[][] pairs = {
                { "xp_orb", "experience_orb" },
                { "xp_bottle", "experience_bottle" },
                { "evocation_illager", "evoker" },
                { "vindication_illager", "vindicator" },
                { "zombie_pigman", "zombified_piglin" },
                { "ender_crystal", "end_crystal" },
                { "fireworks_rocket", "firework_rocket" },
                { "lightning_bolt", "lightning_bolt" },
                { "thrown_trident", "trident" },
                { "snowball", "snowball" },
                { "villager_v2", "villager" },
                { "zombie_villager_v2", "zombie_villager" },
                { "moving_block", "falling_block" },
                { "fishing_hook", "fishing_bobber" },
                { "chest_boat", "oak_chest_boat" },
                { "boat", "oak_boat" },
        };
        for (String[] p : pairs) { ENTITY_ALIAS.put(p[0], p[1]); }
        ENTITY_REVERSE.put("experience_orb", "xp_orb");
        ENTITY_REVERSE.put("experience_bottle", "xp_bottle");
        ENTITY_REVERSE.put("evoker", "evocation_illager");
        ENTITY_REVERSE.put("vindicator", "vindication_illager");
        ENTITY_REVERSE.put("zombified_piglin", "zombie_pigman");
        ENTITY_REVERSE.put("end_crystal", "ender_crystal");
        ENTITY_REVERSE.put("firework_rocket", "fireworks_rocket");
        ENTITY_REVERSE.put("trident", "thrown_trident");
        ENTITY_REVERSE.put("fishing_bobber", "fishing_hook");
    }

    public static EntityType<?> entityType(String bedrockId) {
        if (bedrockId == null) return null;
        ResourceLocation r;
        if (bedrockId.contains(":") && !bedrockId.startsWith(MC)) r = ResourceLocation.tryParse(bedrockId);
        else {
            String n = strip(bedrockId);
            String a = ENTITY_ALIAS.get(n);
            r = rl(a != null ? a : n);
        }
        if (r == null) return null;
        Optional<EntityType<?>> o = BuiltInRegistries.ENTITY_TYPE.getOptional(r);
        return o.orElse(null);
    }

    public static String entityToBedrock(EntityType<?> type) {
        ResourceLocation key = BuiltInRegistries.ENTITY_TYPE.getKey(type);
        if (!"minecraft".equals(key.getNamespace())) return key.toString();
        String p = key.getPath();
        String r = ENTITY_REVERSE.get(p);
        if (r != null) return MC + r;
        if (p.endsWith("_chest_boat") || p.endsWith("_chest_raft")) return MC + "chest_boat";
        if (p.endsWith("_boat") || p.endsWith("_raft")) return MC + "boat";
        return MC + p;
    }

    // ------------------------------------------------------------------ parcacik
    private static final Map<String, String> PARTICLE_ALIAS = new HashMap<>();
    static {
        PARTICLE_ALIAS.put("large_smoke_particle", "large_smoke");
        PARTICLE_ALIAS.put("soul_particle", "soul");
        PARTICLE_ALIAS.put("basic_smoke_particle", "smoke");
        PARTICLE_ALIAS.put("lava_particle", "lava");
        PARTICLE_ALIAS.put("note_particle", "note");
        PARTICLE_ALIAS.put("villager_happy", "happy_villager");
        PARTICLE_ALIAS.put("villager_angry", "angry_villager");
        PARTICLE_ALIAS.put("water_splash_particle", "splash");
        PARTICLE_ALIAS.put("blue_flame_particle", "soul_fire_flame");
        PARTICLE_ALIAS.put("basic_flame_particle", "flame");
        PARTICLE_ALIAS.put("heart_particle", "heart");
        PARTICLE_ALIAS.put("totem_particle", "totem_of_undying");
        PARTICLE_ALIAS.put("critical_hit_emitter", "crit");
        PARTICLE_ALIAS.put("sculk_soul_particle", "sculk_soul");
        PARTICLE_ALIAS.put("ink_particle", "squid_ink");
        PARTICLE_ALIAS.put("explosion_particle", "explosion");
        PARTICLE_ALIAS.put("huge_explosion_emitter", "explosion_emitter");
        PARTICLE_ALIAS.put("snowflake_particle", "snowflake");
        PARTICLE_ALIAS.put("bubble_particle", "bubble");
        PARTICLE_ALIAS.put("endrod", "end_rod");
        PARTICLE_ALIAS.put("falling_dust_particle", "ash");
    }

    /** Bilinmeyen parcacik kimliginde null. */
    public static ParticleOptions particle(String bedrockId) {
        String n = strip(bedrockId);
        if (n.equals("redstone_ore_dust_particle") || n.equals("colored_flame_particle") || n.equals("redstone_wire_dust_particle")) {
            return DustParticleOptions.REDSTONE;
        }
        String j = PARTICLE_ALIAS.get(n);
        if (j == null) j = n.endsWith("_particle") ? n.substring(0, n.length() - "_particle".length()) : n;
        ResourceLocation r = rl(j);
        if (r == null) return null;
        Optional<ParticleType<?>> o = BuiltInRegistries.PARTICLE_TYPE.getOptional(r);
        if (o.isPresent() && o.get() instanceof SimpleParticleType s) return s;
        return null;
    }

    // ------------------------------------------------------------------ ses
    private static final Map<String, String> SOUND_ALIAS = new HashMap<>();
    private static final Map<String, SoundEvent> SOUND_CACHE = new HashMap<>();
    static {
        String[][] s = {
                { "ambient.cave", "ambient.cave" },
                { "ambient.weather.thunder", "entity.lightning_bolt.thunder" },
                { "ambient.weather.rain", "weather.rain" },
                { "block.bell.hit", "block.bell.use" },
                { "close.wooden_door", "block.wooden_door.close" },
                { "open.wooden_door", "block.wooden_door.open" },
                { "close.iron_door", "block.iron_door.close" },
                { "open.iron_door", "block.iron_door.open" },
                { "dig.grass", "block.grass.break" },
                { "dig.gravel", "block.gravel.break" },
                { "dig.stone", "block.stone.break" },
                { "dig.wood", "block.wood.break" },
                { "dig.sand", "block.sand.break" },
                { "dig.cloth", "block.wool.break" },
                { "dig.snow", "block.snow.break" },
                { "step.grass", "block.grass.step" },
                { "step.gravel", "block.gravel.step" },
                { "step.stone", "block.stone.step" },
                { "step.wood", "block.wood.step" },
                { "step.sand", "block.sand.step" },
                { "step.cloth", "block.wool.step" },
                { "step.snow", "block.snow.step" },
                { "game.player.hurt", "entity.player.hurt" },
                { "game.player.die", "entity.player.death" },
                { "liquid.water", "block.water.ambient" },
                { "liquid.lava", "block.lava.ambient" },
                { "liquid.lavapop", "block.lava.pop" },
                { "mob.bat.idle", "entity.bat.ambient" },
                { "mob.creeper.say", "entity.creeper.hurt" },
                { "mob.endermen.idle", "entity.enderman.ambient" },
                { "mob.endermen.portal", "entity.enderman.teleport" },
                { "mob.endermen.stare", "entity.enderman.stare" },
                { "mob.ghast.death", "entity.ghast.death" },
                { "mob.ghast.moan", "entity.ghast.ambient" },
                { "mob.ghast.scream", "entity.ghast.scream" },
                { "mob.husk.ambient", "entity.husk.ambient" },
                { "mob.skeleton.say", "entity.skeleton.ambient" },
                { "mob.spider.say", "entity.spider.ambient" },
                { "mob.warden.heartbeat", "entity.warden.heartbeat" },
                { "mob.wither.ambient", "entity.wither.ambient" },
                { "mob.wither.death", "entity.wither.death" },
                { "mob.wither.spawn", "entity.wither.spawn" },
                { "mob.wolf.growl", "entity.wolf.growl" },
                { "mob.wolf.whine", "entity.wolf.whine" },
                { "mob.zombie.say", "entity.zombie.ambient" },
                { "note.bass", "block.note_block.bass" },
                { "note.harp", "block.note_block.harp" },
                { "random.anvil_land", "block.anvil.land" },
                { "random.chestclosed", "block.chest.close" },
                { "random.chestopen", "block.chest.open" },
                { "random.click", "ui.button.click" },
                { "random.fizz", "block.fire.extinguish" },
                { "random.levelup", "entity.player.levelup" },
                { "random.orb", "entity.experience_orb.pickup" },
                { "random.door_open", "block.wooden_door.open" },
                { "random.door_close", "block.wooden_door.close" },
        };
        for (String[] p : s) SOUND_ALIAS.put(p[0], p[1]);
    }

    /** Bedrock ses kimligi -> Java SoundEvent; bilinmiyorsa null. */
    public static synchronized SoundEvent sound(String bedrockId) {
        if (bedrockId == null) return null;
        String id = strip(bedrockId);
        if (id.startsWith("duskwalker.") || id.startsWith("duskwalker:")) return DwSounds.byBedrockId(id);
        SoundEvent cached = SOUND_CACHE.get(id);
        if (cached != null) return cached;
        String j = SOUND_ALIAS.get(id);
        if (j == null) {
            // Zaten Java kimligi olabilir ("entity.ghast.ambient" ya da "minecraft:...")
            if (bedrockId.contains(":") && !bedrockId.startsWith(MC)) j = bedrockId;
            else return null;
        }
        ResourceLocation r = ResourceLocation.tryParse(j.contains(":") ? j : MC + j);
        if (r == null) return null;
        SoundEvent ev = SoundEvent.createVariableRangeEvent(r);
        SOUND_CACHE.put(id, ev);
        return ev;
    }

    public static Holder<SoundEvent> soundHolder(SoundEvent ev) {
        return BuiltInRegistries.SOUND_EVENT.wrapAsHolder(ev);
    }

    public static SoundSource soundSource(String bedrockId) {
        String id = strip(bedrockId);
        if (id.startsWith("mob.")) return SoundSource.HOSTILE;
        if (id.startsWith("step.")) return SoundSource.PLAYERS;
        if (id.startsWith("dig.") || id.startsWith("note.") || id.startsWith("block.") || id.startsWith("open.") || id.startsWith("close.")) return SoundSource.BLOCKS;
        if (id.startsWith("game.player")) return SoundSource.PLAYERS;
        if (id.startsWith("ambient.weather")) return SoundSource.WEATHER;
        return SoundSource.AMBIENT;
    }

    // ------------------------------------------------------------------ efekt
    private static final Map<String, String> EFFECT_ALIAS = new HashMap<>();
    static {
        EFFECT_ALIAS.put("fatal_poison", "poison");
        EFFECT_ALIAS.put("village_hero", "hero_of_the_village");
        EFFECT_ALIAS.put("jump_boost", "jump_boost");
        EFFECT_ALIAS.put("damage_resistance", "resistance");
        EFFECT_ALIAS.put("mining_fatigue", "mining_fatigue");
        EFFECT_ALIAS.put("fire_resistance", "fire_resistance");
    }

    /** Bedrock efekt adi -> Holder<MobEffect>; bilinmiyorsa null. */
    public static Holder<MobEffect> effect(String bedrockId) {
        String n = strip(bedrockId);
        String j = EFFECT_ALIAS.getOrDefault(n, n);
        ResourceLocation r = rl(j);
        if (r == null) return null;
        Optional<? extends Holder<MobEffect>> h = BuiltInRegistries.MOB_EFFECT.getHolder(r);
        return h.map(x -> (Holder<MobEffect>) x).orElse(null);
    }

    // ------------------------------------------------------------------ hasar nedeni
    /** Bedrock neden adi -> DamageSource. attacker null olabilir. */
    public static DamageSource damageSource(ServerLevel level, String cause, net.minecraft.world.entity.Entity attacker) {
        DamageSources ds = level.damageSources();
        String c = cause == null ? "magic" : cause;
        LivingEntity living = attacker instanceof LivingEntity l ? l : null;
        switch (c) {
            case "entityAttack":
                if (attacker instanceof net.minecraft.world.entity.player.Player p) return ds.playerAttack(p);
                if (living != null) return ds.mobAttack(living);
                return ds.generic();
            case "lava": return ds.lava();
            case "fall": return ds.fall();
            case "magic": return ds.magic();
            case "fire": case "campfire": case "soulCampfire": return ds.inFire();
            case "fireTicks": return ds.onFire();
            case "drowning": return ds.drown();
            case "suffocation": return ds.inWall();
            case "projectile": return ds.thrown(attacker, attacker);
            case "contact": return ds.cactus();
            case "entityExplosion": case "blockExplosion": return ds.explosion(attacker, attacker);
            case "void": return ds.fellOutOfWorld();
            case "starve": return ds.starve();
            case "wither": return ds.wither();
            case "lightning": return ds.lightningBolt();
            case "freezing": return ds.freeze();
            case "suicide": return ds.genericKill();
            case "thorns": return attacker != null ? ds.thorns(attacker) : ds.generic();
            case "none": case "override": case "temperature": case "piston": default:
                return ds.generic();
        }
    }

    /** Java DamageSource -> Bedrock neden adi. */
    public static String damageCause(DamageSource src) {
        String path = src.typeHolder().unwrapKey().map(k -> k.location().getPath()).orElse("generic");
        switch (path) {
            case "player_attack": case "mob_attack": case "mob_attack_no_aggro": case "sting": case "cramming": case "mace_smash": return "entityAttack";
            case "lava": return "lava";
            case "fall": return "fall";
            case "magic": case "indirect_magic": case "dragon_breath": return "magic";
            case "in_fire": return "fire";
            case "on_fire": return "fireTicks";
            case "drown": return "drowning";
            case "in_wall": return "suffocation";
            case "arrow": case "trident": case "thrown": case "mob_projectile": case "fireball": case "unattributed_fireball": case "wither_skull": case "spit": case "wind_charge": return "projectile";
            case "cactus": case "sweet_berry_bush": return "contact";
            case "explosion": case "player_explosion": case "bad_respawn_point": return src.getEntity() != null || src.getDirectEntity() != null ? "entityExplosion" : "blockExplosion";
            case "fireworks": return "entityExplosion";
            case "fell_out_of_world": case "outside_border": return "void";
            case "starve": return "starve";
            case "wither": return "wither";
            case "lightning_bolt": return "lightning";
            case "freeze": return "freezing";
            case "falling_block": case "falling_anvil": return "fallingBlock";
            case "falling_stalactite": case "stalagmite": return "stalactite";
            case "sonic_boom": return "sonicBoom";
            case "hot_floor": return "magma";
            case "generic_kill": return "suicide";
            case "thorns": return "thorns";
            case "fly_into_wall": return "flyIntoWall";
            case "dry_out": return "temperature";
            default: return "none";
        }
    }

    // ------------------------------------------------------------------ boyut
    public static String dimensionBedrockId(net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> key) {
        String s = key.location().toString();
        switch (s) {
            case "minecraft:overworld": return "minecraft:overworld";
            case "minecraft:the_nether": return "minecraft:nether";
            case "minecraft:the_end": return "minecraft:the_end";
            default: return s;
        }
    }

    public static String lower(String s) { return s == null ? "" : s.toLowerCase(Locale.ROOT); }
}
