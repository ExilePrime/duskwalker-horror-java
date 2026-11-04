package com.duskwalker.mod;

import com.duskwalker.bedrock.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.duskwalker.mod.Core.*;

/**
 * DUSKWALKER v11 - MEZARLAR (graves.js)
 * Olum noktasina mezar (yosunlu tas + tabela). Oyuncu basina en fazla 4 mezar. !dwgrave.
 */
public final class Graves {
    private Graves() {}

    private static final String GRAVE_KEY = "duskwalker:v11graves";

    private static final class Death { int x, y, z; String d; int day; String name; }
    private static final Map<String, Death> pendingDeath = new HashMap<>(); // oyuncu id -> olum kaydi

    private static Dimension dimByName(String id) {
        return safe(() -> World.getDimension(id.replace("minecraft:", "")), (Dimension) null);
    }

    private static void removeGrave(Map<String, Object> g) {
        Dimension dim = dimByName(Json.str(g.get("d"), ""));
        if (dim == null) return;
        int gx = Json.intv(g.get("x"), 0), gy = Json.intv(g.get("y"), 0), gz = Json.intv(g.get("z"), 0);
        safe(() -> {
            Block stone = dim.getBlock(new Vec3(gx, gy, gz));
            Block sign = dim.getBlock(new Vec3(gx, gy + 1, gz));
            if (sign != null && sign.typeId().equals("minecraft:standing_sign")) sign.setType("minecraft:air");
            if (stone != null && stone.typeId().equals("minecraft:mossy_cobblestone")) stone.setType("minecraft:air");
        });
    }

    private static void placeGrave(Player player, Death death) {
        Dimension dim = dimByName(death.d);
        if (dim == null) return;
        // olum noktasi hava ise orada, degilse yakinda uygun bir yer
        int sx = death.x, sy = death.y, sz = death.z;
        Block b = safe(() -> dim.getBlock(new Vec3(death.x, death.y, death.z)), (Block) null);
        Block up = safe(() -> dim.getBlock(new Vec3(death.x, death.y + 1, death.z)), (Block) null);
        if (b == null || up == null || !b.typeId().equals("minecraft:air") || !up.typeId().equals("minecraft:air")) {
            Vec3 near = standSpot(dim, new Vec3(death.x + 0.5, death.y, death.z + 0.5), 0, 3, 14);
            if (near == null) return;
            sx = (int) Math.floor(near.x()); sy = (int) Math.floor(near.y()); sz = (int) Math.floor(near.z());
        }
        // zemin yoksa (havada oldu) yere kadar in
        for (int i = 0; i < 20; i++) {
            final int cx = sx, cy = sy, cz = sz;
            Block below = safe(() -> dim.getBlock(new Vec3(cx, cy - 1, cz)), (Block) null);
            if (below != null && below.typeId().equals("minecraft:air")) sy -= 1; else break;
        }
        try {
            Block stone = dim.getBlock(new Vec3(sx, sy, sz));
            Block sign = dim.getBlock(new Vec3(sx, sy + 1, sz));
            if (stone == null || sign == null || !stone.typeId().equals("minecraft:air") || !sign.typeId().equals("minecraft:air")) return;
            stone.setType("minecraft:mossy_cobblestone");
            sign.setType("minecraft:standing_sign");
            String nm = death.name.toUpperCase();
            sign.setSignText(tx("BURADA YATIYOR\n", "HERE LIES\n") + nm.substring(0, Math.min(14, nm.length())) + "\n" + tx("GUN ", "DAY ") + death.day);
        } catch (Throwable e) { return; }
        List<Object> graves = Json.asList(playerGet(player, GRAVE_KEY, null));
        if (graves == null) graves = new ArrayList<>();
        Map<String, Object> g = Json.newMap();
        g.put("x", (double) sx); g.put("y", (double) sy); g.put("z", (double) sz);
        g.put("d", death.d); g.put("day", (double) death.day);
        graves.add(g);
        while (graves.size() > 4) {
            Map<String, Object> old = Json.asMap(graves.remove(0));
            if (old != null) removeGrave(old);
        }
        playerSet(player, GRAVE_KEY, graves);
    }

    public static void init() {
        try {
            Events.After.entityDie.subscribe(ev -> {
                try {
                    Entity d = ev.deadEntity;
                    if (!(d instanceof Player dp) || !d.typeId().equals("minecraft:player")) return;
                    Vec3 l = d.location();
                    Death death = new Death();
                    death.x = (int) Math.floor(l.x()); death.y = (int) Math.floor(l.y()); death.z = (int) Math.floor(l.z());
                    death.d = d.dimension().id();
                    death.day = getDay();
                    death.name = dp.name();
                    pendingDeath.put(d.id(), death);
                } catch (Throwable e) { /* yut */ }
            });
        } catch (Throwable e) { /* yut */ }

        try {
            Events.After.playerSpawn.subscribe(ev -> {
                try {
                    if (ev.initialSpawn || !isEnabled("graves")) return;
                    Player p = ev.player;
                    Death death = pendingDeath.get(p.id());
                    if (death == null) return;
                    pendingDeath.remove(p.id());
                    Sys.runTimeout(() -> { if (isValid(p)) safe(() -> placeGrave(p, death)); }, 40);
                } catch (Throwable e) { /* yut */ }
            });
        } catch (Throwable e) { /* yut */ }

        try {
            Events.After.playerLeave.subscribe(ev -> pendingDeath.remove(ev.playerId));
        } catch (Throwable e) { /* yut */ }

        try {
            Events.Before.chatSend.subscribe(ev -> {
                if (!"!dwgrave".equals(ev.message)) return;
                ev.cancel = true;
                Player p = ev.sender;
                Sys.run(() -> {
                    List<Object> graves = Json.asList(playerGet(p, GRAVE_KEY, null));
                    if (graves == null || graves.isEmpty()) { say(p, tx("§7Henuz bir mezarin yok.§r", "§7You have no graves yet.§r")); return; }
                    Map<String, Object> g = Json.asMap(graves.get(graves.size() - 1));
                    if (g == null) return;
                    say(p, tx("§7Son mezarin: §f", "§7Your last grave: §f") + Json.intv(g.get("x"), 0) + " " + Json.intv(g.get("y"), 0) + " " + Json.intv(g.get("z"), 0)
                        + " §8(" + Json.str(g.get("d"), "").replace("minecraft:", "") + ", " + tx("gun ", "day ") + Json.intv(g.get("day"), 0) + ")§r");
                });
            });
        } catch (Throwable e) { /* yut */ }
    }
}
