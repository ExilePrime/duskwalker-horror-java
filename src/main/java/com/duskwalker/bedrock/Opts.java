package com.duskwalker.bedrock;

import java.util.ArrayList;
import java.util.List;

/** Bedrock scripting API'sindeki secenek nesnelerinin (options objects) Java karsiliklari. */
public final class Opts {
    private Opts() {}

    /** entity.teleport(loc, { dimension, facingLocation, rotation, keepVelocity }) */
    public static final class Teleport {
        public Dimension dimension;      // null = ayni boyut
        public Vec3 facingLocation;      // null = degistirme
        public Rot rotation;             // null = degistirme
        public boolean keepVelocity;

        public static Teleport dim(Dimension d) { Teleport t = new Teleport(); t.dimension = d; return t; }
        public Teleport facing(Vec3 v) { this.facingLocation = v; return this; }
        public Teleport rot(double pitch, double yaw) { this.rotation = new Rot(pitch, yaw); return this; }
        public Teleport dimension(Dimension d) { this.dimension = d; return this; }
    }

    /** dimension.getEntities({ type, families, excludeFamilies, location, maxDistance, minDistance, tags }) */
    public static final class EntityQuery {
        public String type;                                   // "duskwalker:duskwalker" gibi Bedrock kimligi
        public List<String> families = new ArrayList<>();
        public List<String> excludeFamilies = new ArrayList<>();
        public List<String> excludeTypes = new ArrayList<>();
        public List<String> tags = new ArrayList<>();
        public Vec3 location;                                 // maxDistance/minDistance icin merkez
        public Double maxDistance;
        public Double minDistance;

        public static EntityQuery type(String t) { EntityQuery q = new EntityQuery(); q.type = t; return q; }
        public static EntityQuery family(String f) { EntityQuery q = new EntityQuery(); q.families.add(f); return q; }
        public static EntityQuery any() { return new EntityQuery(); }
        public EntityQuery near(Vec3 loc, double maxDist) { this.location = loc; this.maxDistance = maxDist; return this; }
        public EntityQuery minDist(double d) { this.minDistance = d; return this; }
        public EntityQuery excludeFamily(String f) { this.excludeFamilies.add(f); return this; }
        public EntityQuery excludeType(String t) { this.excludeTypes.add(t); return this; }
        public EntityQuery tag(String t) { this.tags.add(t); return this; }
    }

    /** playSound secenekleri */
    public static final class Sound {
        public double volume = 1;
        public double pitch = 1;
        public static Sound of(double volume, double pitch) { Sound s = new Sound(); s.volume = volume; s.pitch = pitch; return s; }
    }

    /** onScreenDisplay.setTitle secenekleri (sureler TICK cinsinden) */
    public static final class Title {
        public int fadeInDuration = 10;
        public int stayDuration = 70;
        public int fadeOutDuration = 20;
        public String subtitle = "";
        public static Title of(int fadeIn, int stay, int fadeOut, String subtitle) {
            Title t = new Title(); t.fadeInDuration = fadeIn; t.stayDuration = stay; t.fadeOutDuration = fadeOut; t.subtitle = subtitle == null ? "" : subtitle; return t;
        }
    }

    /** camera.fade: renk 0..1, sureler SANIYE */
    public static final class Fade {
        public double red, green, blue;
        public double fadeInTime, holdTime, fadeOutTime;
        public static Fade of(double r, double g, double b, double in, double hold, double out) {
            Fade f = new Fade(); f.red = r; f.green = g; f.blue = b; f.fadeInTime = in; f.holdTime = hold; f.fadeOutTime = out; return f;
        }
    }

    /** applyDamage secenekleri: { cause: "entityAttack", damagingEntity } */
    public static final class Damage {
        public String cause;                 // null = genel (magic)
        public Entity damagingEntity;        // null olabilir
        public static Damage of(String cause, Entity by) { Damage d = new Damage(); d.cause = cause; d.damagingEntity = by; return d; }
    }

    /** Olaylardaki damageSource */
    public static final class DamageSource {
        public String cause;                 // "entityAttack", "lava", "fall", ... (Bedrock adlari)
        public Entity damagingEntity;        // null olabilir
    }

    /** player.getBlockFromViewDirection sonucu */
    public static final class BlockHit {
        public Block block;
        public String face;                  // "Up","Down","North","South","East","West"
    }
}
