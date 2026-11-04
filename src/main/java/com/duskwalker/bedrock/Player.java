package com.duskwalker.bedrock;

/** Bedrock Player karsiligi. */
public interface Player extends Entity {
    String name();
    /** "survival" | "creative" | "adventure" | "spectator" */
    String getGameMode();
    /** Sohbet satiri olarak mesaj (sadece bu oyuncuya). § renk kodlari calisir. */
    void sendMessage(String text);

    /** playSound: Bedrock ses kimligi ("duskwalker.heartbeat", "ambient.cave", "mob.ghast.moan" ...). Yalnizca bu oyuncu duyar. */
    void playSound(String soundId, Opts.Sound opts);
    default void playSound(String soundId) { playSound(soundId, null); }

    void setActionBar(String text);
    void setTitle(String title, Opts.Title opts);
    /** Ekran karartma/solma (Bedrock camera.fade). */
    void cameraFade(Opts.Fade fade);
    /** camerashake add: strength 0..4, sure saniye, type "rotational"|"positional" */
    void cameraShake(double strength, double seconds, String type);
    /** fog push/pop: fogId = "duskwalker:hunt_fog" gibi, userKey = "duskwalker_hunt" gibi yigin anahtari. */
    void fogPush(String fogId, String userKey);
    void fogPop(String userKey);

    /** Ana envanter (36 slot: 0-8 hotbar). */
    Container inventory();
    /** Ana el; bos ise null. Donen kopyadir. */
    ItemStack mainhand();
    /** null = eli bosalt. */
    void setMainhand(ItemStack item);
    int selectedSlotIndex();
    /** ADDED(events_world): player.isFlying (yaratici/izleyici ucusu). */
    default boolean isFlying() { return false; }
    /** ADDED(mind): player.selectedSlotIndex = n (hotbar 0-8). */
    default void setSelectedSlotIndex(int slot) {}

    /** Yatak/dogma noktasi; yoksa null. */
    Vec3 getSpawnPoint();
    /** Bakilan blok (maxDistance blok). Yoksa null. */
    Opts.BlockHit getBlockFromViewDirection(double maxDistance);
    /** player.runCommand(...): Bedrock komutu; binding gerekli cevirileri yapar. Basarili mi doner. */
    boolean runCommand(String command);
}
