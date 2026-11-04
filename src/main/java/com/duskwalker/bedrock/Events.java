package com.duskwalker.bedrock;

/**
 * world.beforeEvents / world.afterEvents karsiligi. Olaylari binding (fabric paketi) tetikler,
 * mod modulleri subscribe eder.
 *   Bedrock: world.afterEvents.playerLeave.subscribe(ev => ...)
 *   Java:    Events.After.playerLeave.subscribe(ev -> ...)
 */
public final class Events {
    private Events() {}

    // ---- Olay veri siniflari (alan adlari Bedrock ile ayni) ----
    public static final class ChatSend { public Player sender; public String message; public boolean cancel; }
    public static final class PlayerLeaveBefore { public Player player; }
    public static final class PlayerLeave { public String playerId; public String playerName; }
    public static final class PlayerSpawn { public Player player; public boolean initialSpawn; }
    public static final class PlayerPlaceBlock { public Player player; public Block block; }
    public static final class PlayerBreakBlock { public Player player; public Block block; public Perm brokenBlockPermutation; public ItemStack itemStack; }
    public static final class PlayerInteractWithBlock { public Player player; public Block block; public ItemStack itemStack; }
    public static final class PlayerInteractWithEntity { public Player player; public Entity target; public ItemStack itemStack; }
    public static final class EntityDie { public Entity deadEntity; public Opts.DamageSource damageSource; }
    public static final class EntityHurt { public Entity hurtEntity; public Opts.DamageSource damageSource; public double damage; }
    public static final class ItemUse { public Player source; public ItemStack itemStack; }
    public static final class Explosion { public Entity source; public Dimension dimension; }

    public static final class Before {
        private Before() {}
        /** Dinleyici ev.cancel = true yaparsa mesaj sohbete gonderilmez. */
        public static final Signal<ChatSend> chatSend = new Signal<>();
        public static final Signal<PlayerLeaveBefore> playerLeave = new Signal<>();
    }

    public static final class After {
        private After() {}
        public static final Signal<PlayerLeave> playerLeave = new Signal<>();
        public static final Signal<PlayerSpawn> playerSpawn = new Signal<>();
        public static final Signal<PlayerPlaceBlock> playerPlaceBlock = new Signal<>();
        public static final Signal<PlayerBreakBlock> playerBreakBlock = new Signal<>();
        public static final Signal<PlayerInteractWithBlock> playerInteractWithBlock = new Signal<>();
        public static final Signal<PlayerInteractWithEntity> playerInteractWithEntity = new Signal<>();
        public static final Signal<EntityDie> entityDie = new Signal<>();
        public static final Signal<EntityHurt> entityHurt = new Signal<>();
        public static final Signal<ItemUse> itemUse = new Signal<>();
        public static final Signal<Explosion> explosion = new Signal<>();
    }
}
