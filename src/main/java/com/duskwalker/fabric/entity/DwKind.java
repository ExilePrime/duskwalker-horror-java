package com.duskwalker.fabric.entity;

/**
 * Alti Duskwalker varlik turunun bp/entities/*.json ve rp/entity/*.json'dan cikarilan sabit ozellikleri.
 */
public enum DwKind {
    //            id           w     h      scale  hp     speed  variant defVar skin  nameAlways look              despawn vanish lava  loco   transl hunter fireImm egg bg/fg
    DUSKWALKER("duskwalker", 0.6f, 1.95f, 1.00, 200.0, 0.30, true, 0, false, false, Look.TARGET, true, 0, true, true, false, true, false, 0x0b0b12, 0xe6f0ff),
    ECHO("echo", 0.6f, 1.95f, 1.02, 60.0, 0.0, true, 0, false, true, Look.NONE, true, 0, false, true, true, false, true, 0x3a4a66, 0xb8c8e8),
    SEEP("seep", 0.5f, 1.6f, 0.82, 40.0, 0.0, true, 1, false, false, Look.NONE, true, 0, false, true, false, false, true, 0x1a3d2e, 0x8fffb0),
    WATCHER("watcher", 0.6f, 1.95f, 1.02, 60.0, 0.0, true, 2, false, false, Look.NONE, true, 0, false, true, false, false, true, 0x1a1a2e, 0xc9c9ff),
    EFFIGY("effigy", 0.6f, 1.9f, 1.00, 20.0, 0.0, false, 0, true, false, Look.NONE, false, 150, false, false, false, false, true, 0x202028, 0xa0a0b0),
    AYLIN("aylin", 0.6f, 1.9f, 1.00, 100.0, 0.0, false, 0, false, true, Look.PLAYER, false, 0, false, false, false, false, true, -1, -1);

    /** Bakis davranisi: look_at_target / look_at_player + random_look_around. */
    public enum Look { NONE, TARGET, PLAYER }

    public final String id;
    public final float width, height;
    public final double scale, maxHealth, moveSpeed;
    public final boolean hasVariant;
    public final int defaultVariant;
    public final boolean hasSkin;
    public final boolean alwaysShowName;
    public final Look look;
    public final boolean despawnFromDistance;
    public final int vanishTicks;
    public final boolean takesLavaDamage;
    /** walk/run locomotion denetleyicisi var mi (yoksa yalniz idle_sway). */
    public final boolean locomotion;
    public final boolean translucent;
    public final boolean hunter;
    public final boolean fireImmune;
    public final int eggBase, eggOverlay;

    DwKind(String id, float w, float h, double scale, double hp, double speed, boolean hasVariant, int defVar,
           boolean hasSkin, boolean alwaysShowName, Look look, boolean despawn, int vanishTicks, boolean lava,
           boolean locomotion, boolean translucent, boolean hunter, boolean unused, int eggBase, int eggOverlay) {
        this.id = id;
        this.width = w;
        this.height = h;
        this.scale = scale;
        this.maxHealth = hp;
        this.moveSpeed = speed;
        this.hasVariant = hasVariant;
        this.defaultVariant = defVar;
        this.hasSkin = hasSkin;
        this.alwaysShowName = alwaysShowName;
        this.look = look;
        this.despawnFromDistance = despawn;
        this.vanishTicks = vanishTicks;
        this.takesLavaDamage = lava;
        this.locomotion = locomotion;
        this.translucent = translucent;
        this.hunter = hunter;
        this.fireImmune = !lava;
        this.eggBase = eggBase;
        this.eggOverlay = eggOverlay;
    }
}
