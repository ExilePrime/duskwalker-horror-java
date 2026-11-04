package com.duskwalker.bedrock;

/** JS {x,y,z} nesnesinin karsiligi. Blok koordinatlari da bununla (tam sayi degerli double) tasinir. */
public record Vec3(double x, double y, double z) {
    public static Vec3 of(double x, double y, double z) { return new Vec3(x, y, z); }

    public Vec3 add(Vec3 o) { return new Vec3(x + o.x, y + o.y, z + o.z); }
    public Vec3 add(double dx, double dy, double dz) { return new Vec3(x + dx, y + dy, z + dz); }
    public Vec3 sub(Vec3 o) { return new Vec3(x - o.x, y - o.y, z - o.z); }
    public Vec3 scale(double k) { return new Vec3(x * k, y * k, z * k); }
    public Vec3 withY(double ny) { return new Vec3(x, ny, z); }
    public double length() { return Math.sqrt(x * x + y * y + z * z); }
    /** Yatay (x,z) uzaklik: Math.hypot(a.x-b.x, a.z-b.z) */
    public double hdist(Vec3 o) { return Math.hypot(x - o.x, z - o.z); }
    /** 3B uzaklik */
    public double dist(Vec3 o) { return Math.sqrt((x - o.x) * (x - o.x) + (y - o.y) * (y - o.y) + (z - o.z) * (z - o.z)); }
    /** Her bileseni Math.floor ile tam sayiya indirir (blok koordinati). */
    public Vec3 floor() { return new Vec3(Math.floor(x), Math.floor(y), Math.floor(z)); }
    public int bx() { return (int) Math.floor(x); }
    public int by() { return (int) Math.floor(y); }
    public int bz() { return (int) Math.floor(z); }
}
