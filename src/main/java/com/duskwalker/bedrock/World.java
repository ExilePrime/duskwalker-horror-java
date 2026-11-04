package com.duskwalker.bedrock;

import java.util.List;

/** Bedrock "world" nesnesinin statik karsiligi. */
public final class World {
    private World() {}

    public static List<Player> getPlayers() { return Platform.get().getPlayers(); }
    public static Dimension getDimension(String id) { return Platform.get().getDimension(id); }
    public static Dimension overworld() { return Platform.get().getDimension("overworld"); }

    /** String / Double / Boolean ya da null */
    public static Object getDynamicProperty(String key) { return Platform.get().getWorldProperty(key); }
    public static void setDynamicProperty(String key, Object value) { Platform.get().setWorldProperty(key, value); }

    public static int getTimeOfDay() { return Platform.get().timeOfDay(); }
    public static void setTimeOfDay(int t) { Platform.get().setTimeOfDay(t); }
    public static long getAbsoluteTime() { return Platform.get().absoluteTime(); }
    public static int getDay() { return Platform.get().day(); }
    public static int getMoonPhase() { return Platform.get().moonPhase(); }
    public static boolean gameRule(String name) { return Platform.get().gameRule(name); }
    public static void setGameRule(String name, boolean v) { Platform.get().setGameRule(name, v); }
    public static void sendMessage(String text) { Platform.get().broadcast(text); }
}
