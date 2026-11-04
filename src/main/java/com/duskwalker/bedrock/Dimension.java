package com.duskwalker.bedrock;

import java.util.List;

/** Bedrock Dimension karsiligi. id(): "minecraft:overworld" | "minecraft:nether" | "minecraft:the_end" (Bedrock adlari). */
public interface Dimension {
    String id();

    /** Yuklu degilse veya sinir disindaysa null. Koordinatlar floor edilir. */
    Block getBlock(Vec3 loc);
    default Block getBlock(int x, int y, int z) { return getBlock(new Vec3(x, y, z)); }
    /** (x,z) sutunundaki en ust katı blok (hava/sivi olmayan degil; Bedrock getTopmostBlock gibi yukseklik haritasi). Yoksa null. */
    Block getTopmostBlock(int x, int z);

    List<Entity> getEntities(Opts.EntityQuery query);
    default List<Entity> getEntities() { return getEntities(new Opts.EntityQuery()); }
    List<Player> getPlayers();

    /** Bedrock tur kimligiyle olusturur (ornek "duskwalker:duskwalker", "minecraft:wolf"). Basarisizsa firlatir. */
    Entity spawnEntity(String typeId, Vec3 loc);
    /** Yere esya dusurur; donen Entity item varligidir. */
    Entity spawnItem(ItemStack item, Vec3 loc);
    /** Bedrock parcacik kimligi ("minecraft:large_smoke_particle" ...); bilinmeyen kimlikler sessizce yok sayilir. */
    void spawnParticle(String particleId, Vec3 loc);
    /** Konumdan herkese ses. */
    void playSound(String soundId, Vec3 loc, Opts.Sound opts);

    /** "Clear" | "Rain" | "Thunder" */
    String getWeather();

    /** Bedrock komutunu (gerekirse Java'ya cevirerek) bu boyutta calistirir. Basarili mi doner; hata firlatmaz. */
    boolean runCommand(String command);
}
