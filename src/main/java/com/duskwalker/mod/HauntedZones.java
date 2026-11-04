package com.duskwalker.mod;

import com.duskwalker.bedrock.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.duskwalker.mod.Core.*;

/**
 * DUSKWALKER v11 - LANETLI BOLGELER (haunted_zones.js)
 * 32x32'lik bolgede 2+ oyuncu olumu olursa bolge kalici olarak lanetlenir. !dwzones listeler.
 */
public final class HauntedZones {
    private HauntedZones() {}

    private static final String ZONE_KEY = "duskwalker:v11zones";
    private static final int CELL = 32;

    private static String cellOf(Vec3 loc, String dimId) {
        return (long) Math.floor(loc.x() / CELL) + "," + (long) Math.floor(loc.z() / CELL) + "," + dimId;
    }

    private static Map<String, Object> readZones() {
        Map<String, Object> z = Json.asMap(worldGet(ZONE_KEY, null));
        return z != null ? z : Json.newMap();
    }

    private static final Map<String, String> inside = new HashMap<>(); // oyuncu id -> bulundugu lanetli bolge anahtari

    private static final String[] ZONE_EVENTS = { "phantom_sound", "delayed_steps", "eyes_dark", "cold_spot" };

    public static void init() {
        try {
            Events.After.entityDie.subscribe(ev -> {
                try {
                    Entity d = ev.deadEntity;
                    if (d == null || !d.typeId().equals("minecraft:player") || !isEnabled("zones")) return;
                    Map<String, Object> zones = readZones();
                    Vec3 loc = d.location();
                    String dimId = d.dimension().id();
                    String key = cellOf(loc, dimId);
                    Map<String, Object> z = Json.asMap(zones.get(key));
                    if (z == null) {
                        z = Json.newMap();
                        z.put("c", 0.0);
                        z.put("x", Math.floor(loc.x() / CELL) * CELL + CELL / 2.0);
                        z.put("z", Math.floor(loc.z() / CELL) * CELL + CELL / 2.0);
                        z.put("d", dimId);
                    }
                    z.put("c", Json.num(z.get("c"), 0) + 1);
                    zones.put(key, z);
                    worldSet(ZONE_KEY, zones);
                } catch (Throwable e) { /* yut */ }
            });
        } catch (Throwable e) { /* yut */ }

        Sys.runInterval(() -> {
            if (isDefeated() || !isEnabled("zones")) return;
            Map<String, Object> zones = readZones();
            for (Player p : World.getPlayers()) {
                try {
                    if (!isSurvival(p)) continue;
                    String key = cellOf(p.location(), p.dimension().id());
                    Map<String, Object> z = Json.asMap(zones.get(key));
                    if (z == null || Json.num(z.get("c"), 0) < 2) { inside.remove(p.id()); continue; }
                    if (!key.equals(inside.get(p.id()))) {
                        inside.put(p.id(), key);
                        title(p, tx("§8Lanetli Zemin", "§8Haunted Ground"), tx("§7burada cok kisi oldu", "§7many have died here"), 60);
                        addStat(p, "hauntedEntered");
                    }
                    // gorsel: yerden yukselen ruh dumani
                    Vec3 l = p.location();
                    for (int i = 0; i < 3; i++) particle(p.dimension(), "minecraft:soul_particle", new Vec3(l.x() + rand(-4, 4), l.y() + 0.2, l.z() + rand(-4, 4)));
                    if (!isInRefuge(p)) {
                        loseSanity(p, 1);
                        if (chance(0.25)) {
                            List<String> pool = new ArrayList<>();
                            for (String e : ZONE_EVENTS) if (isNight() || !e.equals("eyes_dark")) pool.add(e);
                            String id = pick(pool);
                            triggerEvent(id, p, true);
                        }
                    }
                } catch (Throwable e) { /* yut */ }
            }
        }, TICKS_PER_SEC * 20);

        try {
            Events.After.playerLeave.subscribe(ev -> { inside.remove(ev.playerId); });
        } catch (Throwable e) { /* yut */ }

        try {
            Events.Before.chatSend.subscribe(ev -> {
                if (!"!dwzones".equals(ev.message)) return;
                ev.cancel = true;
                Player p = ev.sender;
                Sys.run(() -> {
                    Map<String, Object> zones = readZones();
                    Vec3 pl = p.location();
                    String pd = p.dimension().id();
                    List<Map<String, Object>> list = new ArrayList<>();
                    for (Object o : zones.values()) {
                        Map<String, Object> z = Json.asMap(o);
                        if (z != null && Json.num(z.get("c"), 0) >= 2 && pd.equals(z.get("d"))) list.add(z);
                    }
                    list.sort((a, b) -> Double.compare(
                        Math.hypot(Json.num(a.get("x"), 0) - pl.x(), Json.num(a.get("z"), 0) - pl.z()),
                        Math.hypot(Json.num(b.get("x"), 0) - pl.x(), Json.num(b.get("z"), 0) - pl.z())));
                    if (list.size() > 5) list = new ArrayList<>(list.subList(0, 5));
                    if (list.isEmpty()) { say(p, tx("§7Bilinen lanetli bolge yok.§r", "§7No known haunted ground.§r")); return; }
                    StringBuilder sb = new StringBuilder();
                    for (int i = 0; i < list.size(); i++) {
                        Map<String, Object> z = list.get(i);
                        double zx = Json.num(z.get("x"), 0), zz = Json.num(z.get("z"), 0);
                        if (i > 0) sb.append("\n");
                        sb.append("§7").append(Math.round(zx)).append(", ").append(Math.round(zz))
                          .append(" §8(").append((long) Json.num(z.get("c"), 0)).append("x, ")
                          .append(Math.round(Math.hypot(zx - pl.x(), zz - pl.z()))).append("m)");
                    }
                    say(p, tx("§6Lanetli bolgeler:§r\n", "§6Haunted ground:§r\n") + sb);
                });
            });
        } catch (Throwable e) { /* yut */ }
    }
}
