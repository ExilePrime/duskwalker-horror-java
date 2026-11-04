package com.duskwalker.bedrock;

import java.util.List;
import java.util.function.Consumer;

/**
 * Bedrock scripting API'sinin Minecraft Java tarafindaki arka ucu. fabric paketindeki FabricPlatform
 * bunu uygular; mod modulleri yalnizca World / Sys / Events / Forms uzerinden bu arayuze ulasir.
 */
public interface Platform {
    // ---- dunya ----
    List<Player> getPlayers();
    /** "overworld" | "minecraft:overworld" | "nether" | "minecraft:nether" | "the_end" | "minecraft:the_end" */
    Dimension getDimension(String id);
    Object getWorldProperty(String key);
    void setWorldProperty(String key, Object value);

    int timeOfDay();                 // 0..23999
    void setTimeOfDay(int t);
    long absoluteTime();             // toplam tick
    int day();                       // 1'den baslar (Bedrock world.getDay() gibi)
    int moonPhase();                 // 0 = dolunay
    boolean gameRule(String bedrockRuleName);                   // "doDayLightCycle" ...
    void setGameRule(String bedrockRuleName, boolean value);
    void broadcast(String text);
    void runServerCommand(String command);

    // ---- formlar ----
    void showActionForm(Player player, Forms.Action form, Consumer<Forms.ActionResponse> handler);
    void showModalForm(Player player, Forms.Modal form, Consumer<Forms.ModalResponse> handler);

    // ---- statik tutucu ----
    final class Holder { private static Platform instance; private Holder() {} }
    static Platform get() {
        if (Holder.instance == null) throw new BedrockException("Platform not installed");
        return Holder.instance;
    }
    static void install(Platform p) { Holder.instance = p; }
    static boolean isInstalled() { return Holder.instance != null; }
}
