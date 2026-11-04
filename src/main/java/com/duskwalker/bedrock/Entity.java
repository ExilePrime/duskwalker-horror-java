package com.duskwalker.bedrock;

import java.util.List;

/**
 * Bedrock Entity karsiligi. Gercek uygulama fabric paketindedir. Gecersiz (kaldirilmis) varliklarda
 * metotlar BedrockException (RuntimeException) firlatir; Core.safe(...) bunu yakalar.
 * Esitlik id() uzerinden yapilir (ayni varlik icin farkli sarmalayici nesneler olabilir).
 */
public interface Entity {
    /** Kalici benzersiz kimlik (UUID metni). JS'teki entity.id */
    String id();
    /** Bedrock tur kimligi: "minecraft:player", "minecraft:wolf", "duskwalker:duskwalker" ... */
    String typeId();
    Vec3 location();
    Dimension dimension();
    /** JS'te hem isValid() hem isValid ozelligi vardi; burada tek bicim. */
    boolean isValid();

    String nameTag();
    void setNameTag(String name);

    /** teleport(loc, opts): opts null olabilir. */
    void teleport(Vec3 loc, Opts.Teleport opts);
    default void teleport(Vec3 loc) { teleport(loc, null); }
    /** Oldurur (oyuncuda olum ekranina yol acar). */
    void kill();
    /** Varligi aninda siler (instant_despawn). */
    void remove();

    /** Bedrock entity olayi: "duskwalker:hunt_start", "duskwalker:vanish", "duskwalker:skin_2" ... */
    void triggerEvent(String event);
    /** Bedrock entity property: "duskwalker:variant" (int), "duskwalker:dominant" (bool), "duskwalker:skin" (int) */
    void setProperty(String name, Object value);
    Object getProperty(String name);

    void applyImpulse(Vec3 v);
    Vec3 getVelocity();
    /** Birim bakis vektoru */
    Vec3 getViewDirection();
    Vec3 getHeadLocation();
    /** Bedrock rotasyonu: x = pitch, y = yaw (derece) */
    Rot getRotation();

    /** effectId Bedrock adi: "slowness", "darkness", "blindness", "speed" ... */
    void addEffect(String effectId, int durationTicks, int amplifier, boolean showParticles);
    void removeEffect(String effectId);

    /** opts null olabilir. Donus: hasar uygulandi mi. */
    boolean applyDamage(double amount, Opts.Damage opts);
    default boolean applyDamage(double amount) { return applyDamage(amount, null); }

    /** Istemcide animasyonu oynatir (ornegin "animation.duskwalker.attack"). Desteklenmeyen varliklarda sessizce yok sayilir. */
    void playAnimation(String animationName);

    boolean isOnGround();
    boolean isSprinting();
    boolean isSneaking();
    boolean isSleeping();
    boolean isTamed();               // minecraft:is_tamed bileseni
    /** minecraft:health bileseni var mi (canli mi)? */
    boolean hasHealth();
    double health();
    double maxHealth();

    List<String> getTags();
    void addTag(String tag);
    void removeTag(String tag);
    boolean hasTag(String tag);
    /** type_family ("monster", "player", "duskwalker_creature" ...) */
    boolean hasFamily(String family);

    /** Dinamik ozellikler: deger String / Double / Boolean ya da null (yok). Set'te Number verilirse Double saklanir. */
    Object getDynamicProperty(String key);
    void setDynamicProperty(String key, Object value);
    void clearDynamicProperties();

    /** Binding ici ham Minecraft nesnesine erisim (yalnizca fabric paketi kullanir). */
    Object raw();
}
