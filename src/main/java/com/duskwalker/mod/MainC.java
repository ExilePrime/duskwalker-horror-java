package com.duskwalker.mod;

import com.duskwalker.bedrock.*;

import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Consumer;
import java.util.regex.Pattern;

/**
 * main.js 2291-sonu (MainC): av (hunt), kapi/mesale/tabela/fisilti/sohbet, yansima, Aylin sohbeti ve adak,
 * ele gecirme, v9.9 ozellikleri (olum yankisi, calinti kelimeler, 03:33, taklitci, ev ziyareti),
 * lav, av sisi, dusuk saglamlik efektleri, !dwmenu ayar formu.
 * MainA / MainB'deki semboller sinif adiyla cagrilir.
 */
public final class MainC {
    private MainC() {}

    // ================================================================== AV (HUNT)
    // huntEndCallbacks = MainB.huntEndCallbacks (entityDie dinleyicisi MainB'de)

    public static void doHunt(Player player, boolean fast) {
        try {
            MainA.loseSanity(player, fast ? 22 : 18);
            MainA.addEvidence(player, 2);
            try { World.sendMessage(fast ? "§4Duskwalker§r: KOSUYORUM." : "§4Duskwalker§r: Kacamazsin."); } catch (Throwable e) { /* yut */ }
            spawnHunter(player, fast, 0);
        } catch (Throwable e) { /* yut */ }
    }

    // OZELLIK: FAZ 5 "COKUS" - IKIZ AVCI
    public static void doTwinHunt(Player player) {
        try {
            MainA.loseSanity(player, 28);
            MainA.addEvidence(player, 3);
            try { World.sendMessage("§4Duskwalker§r: BIZ IKIMIZ VARIZ."); } catch (Throwable e) { /* yut */ }
            spawnHunter(player, true, -0.9);
            Sys.runTimeout(() -> spawnHunter(player, true, 0.9), 14);
        } catch (Throwable e) { /* yut */ }
    }

    // Tek bir "avci" Duskwalker'i belirtilen aci sapmasiyla dogurup gercek kovalama AI'sini baglayan ortak yardimci.
    public static void spawnHunter(Player player, boolean fast, double angleJitter) {
        try {
            final Dimension dimension = player.dimension();
            Vec3 view = player.getViewDirection();
            double dx = view.x() + angleJitter;
            double dz = view.z() + angleJitter * -1;
            Vec3 pl = player.location();
            Vec3 center = new Vec3(pl.x() + dx * 10, pl.y(), pl.z() + dz * 10);
            final Vec3 pos = MainA.findSpawnSpot(dimension, center, 0, 3);
            final int huntVariant = 0;
            MainA.smokePuff(dimension, pos, 10);
            try { dimension.playSound("mob.wither.spawn", pos, Opts.Sound.of(1.0, (fast ? 0.35 : 0.5) * MainA.getVariantPitch(huntVariant))); } catch (Throwable e) { /* yut */ }
            Sys.runTimeout(() -> {
                final Entity monster;
                try { monster = dimension.spawnEntity("duskwalker:duskwalker", pos); } catch (Throwable e) { return; }
                MainA.applyDominant(monster);
                MainA.applyVariant(monster, huntVariant);
                MainA.corruptGround(dimension, pos);
                try { monster.triggerEvent(MainA.getHuntEventName(huntVariant, fast)); } catch (Throwable e) { /* yut */ }

                final String monsterId = monster.id();
                Consumer<Boolean> endHunt = (killed) -> {
                    if (!MainB.huntEndCallbacks.containsKey(monsterId)) return;
                    MainB.huntEndCallbacks.remove(monsterId);
                    huntFogPop(player);
                    try { monster.triggerEvent("duskwalker:hunt_end"); } catch (Throwable e) { /* yut */ }
                    try { MainA.smokePuff(dimension, monster.location(), 10); } catch (Throwable e) { /* yut */ }
                    try { monster.triggerEvent("duskwalker:vanish"); } catch (Throwable e) { /* yut */ }
                    if (!killed) { try { World.sendMessage("§4Duskwalker§r: Bu sefer... sansliydin."); } catch (Throwable e) { /* yut */ } }
                };
                MainB.huntEndCallbacks.put(monsterId, endHunt);
                huntFogPush(player);
                final int[] farTicks = { 0 };
                final int[] leash = { 0 };
                leash[0] = Sys.runInterval(() -> {
                    try {
                        if (!MainB.huntEndCallbacks.containsKey(monsterId) || !entityOk(monster) || !entityOk(player)) { Sys.clearRun(leash[0]); return; }
                        if (!monster.dimension().id().equals(player.dimension().id())) { farTicks[0] += 40; }
                        else {
                            double dd = Math.hypot(monster.location().x() - player.location().x(), monster.location().z() - player.location().z());
                            farTicks[0] = dd > 70 ? farTicks[0] + 40 : 0;
                        }
                        if (farTicks[0] >= 400) {
                            farTicks[0] = 0;
                            Vec3 v = player.getViewDirection();
                            Vec3 pp = player.location();
                            Vec3 spot = MainA.findSpawnSpot(player.dimension(), new Vec3(pp.x() - v.x() * 24, pp.y(), pp.z() - v.z() * 24), 0, 3);
                            monster.teleport(spot, Opts.Teleport.dim(player.dimension()));
                        }
                    } catch (Throwable e) { try { Sys.clearRun(leash[0]); } catch (Throwable e2) { /* yut */ } }
                }, 40);
                // guvenlik siniri: oyuncu saklanip kacarsa sonsuza kadar takilip kalmasin
                Sys.runTimeout(() -> endHunt.accept(false), 3600);
            }, 24);
        } catch (Throwable e) { /* yut */ }
    }

    // ================================================================== Door / Torch / Sign / Whisper / Chat
    public static void doDoor(Player player) {
        try {
            Dimension dimension = player.dimension();
            Vec3 base = player.location();
            for (int attempt = 0; attempt < 10; attempt++) {
                Vec3 pos = new Vec3(Math.floor(base.x()) + Core.randInt(-8, 8), Math.floor(base.y()) + Core.randInt(-2, 2), Math.floor(base.z()) + Core.randInt(-8, 8));
                Block block;
                try { block = dimension.getBlock(pos); } catch (Throwable e) { continue; }
                if (block == null || !block.typeId().contains("door")) continue;
                try {
                    Perm perm = block.permutation();
                    Map<String, Object> states = perm.getAllStates();
                    if (states.containsKey("open_bit")) {
                        block.setPermutation(perm.withState("open_bit", !MainA.truthy(states.get("open_bit"))));
                        try { dimension.playSound("open.wooden_door", pos, Opts.Sound.of(0.8, 1)); } catch (Throwable e) { /* yut */ }
                        return;
                    }
                } catch (Throwable e) { /* yut */ }
            }
        } catch (Throwable e) { /* yut */ }
    }

    public static void doTorch(Player player) {
        try {
            Dimension dimension = player.dimension();
            Vec3 base = player.location();
            for (int attempt = 0; attempt < 12; attempt++) {
                Vec3 pos = new Vec3(Math.floor(base.x()) + Core.randInt(-6, 6), Math.floor(base.y()) + Core.randInt(-2, 3), Math.floor(base.z()) + Core.randInt(-6, 6));
                Block block;
                try { block = dimension.getBlock(pos); } catch (Throwable e) { continue; }
                if (block == null || !block.typeId().contains("torch")) continue;
                try {
                    block.setType("minecraft:air");
                    dimension.spawnParticle("minecraft:large_smoke_particle", new Vec3(pos.x() + 0.5, pos.y() + 0.5, pos.z() + 0.5));
                    try { player.sendMessage("§7(bir mesale sondu...)§r"); } catch (Throwable e) { /* yut */ }
                    return;
                } catch (Throwable e) { /* yut */ }
            }
        } catch (Throwable e) { /* yut */ }
    }

    public static void doSign(Player player) {
        try {
            Dimension dimension = player.dimension();
            Vec3 view = player.getViewDirection();
            Vec3 loc = player.location();
            for (int d = 3; d <= 7; d++) {
                Vec3 pos = new Vec3(Math.floor(loc.x() + view.x() * d), Math.floor(loc.y()), Math.floor(loc.z() + view.z() * d));
                Block block, below;
                try { block = dimension.getBlock(pos); below = dimension.getBlock(new Vec3(pos.x(), pos.y() - 1, pos.z())); } catch (Throwable e) { continue; }
                if (block == null || below == null || !block.typeId().equals("minecraft:air") || below.typeId().equals("minecraft:air")) continue;
                try {
                    block.setType("minecraft:standing_sign");
                    block.setSignText(MainA.SIGN_MESSAGES[Core.randInt(0, MainA.SIGN_MESSAGES.length - 1)]);
                    MainA.smokePuff(dimension, new Vec3(pos.x() + 0.5, pos.y() + 0.5, pos.z() + 0.5), 4);
                    return;
                } catch (Throwable e) { /* yut */ }
            }
        } catch (Throwable e) { /* yut */ }
    }

    public static void doWhisper(Player player) {
        try {
            String sound = MainA.WHISPER_SOUNDS[Core.randInt(0, MainA.WHISPER_SOUNDS.length - 1)];
            player.playSound(sound, Opts.Sound.of(0.6, 0.5 + Math.random() * 0.5));
        } catch (Throwable e) { /* yut */ }
    }

    public static void doChat(Player player) {
        try { player.sendMessage(MainA.CHAT_LINES[Core.randInt(0, MainA.CHAT_LINES.length - 1)]); } catch (Throwable e) { /* yut */ }
    }

    // ================================================================== OZELLIK: SU YANSIMASI
    public static void doReflection(Player player) {
        try {
            Opts.BlockHit hit;
            try { hit = player.getBlockFromViewDirection(14); } catch (Throwable e) { hit = null; }
            if (hit == null || hit.block == null) return;
            String id = hit.block.typeId();
            if (!id.equals("minecraft:water") && !id.equals("minecraft:flowing_water")) return;

            try { player.cameraFade(Opts.Fade.of(0, 0, 0, 0.15, 0.25, 0.4)); } catch (Throwable e) { /* yut */ }
            try { player.playSound("mob.endermen.stare", Opts.Sound.of(0.5, 1.5)); } catch (Throwable e) { /* yut */ }
            Sys.runTimeout(() -> {
                try { player.sendMessage("§7(yansimanda... o degildin.)§r"); } catch (Throwable e) { /* yut */ }
            }, 10);
            MainA.loseSanity(player, 5);
        } catch (Throwable e) { /* yut */ }
    }

    // ================================================================== OZELLIK: AYLIN'LE GERCEK SOHBET
    public static final String[] AYLIN_TOPICS = { "Kardesin hakkinda", "Bekci ne demek?", "Saglamligimi nasil korurum?", "Kurtlar hakkinda", "Kendin hakkinda", "Vedalas" };
    public static final String[][] AYLIN_DIALOGUE = {
        { "§dAylin§r: Adini artik soylemiyorum. O kelime artik ona ait degil.", "§dAylin§r: Once sadece yalnizdi. Ben onu burada birakip kendi hayatimin pesinden kostum.", "§dAylin§r: Dondugumde... o yoktu artik. Onun yerinde baska bir sey vardi." },
        { "§dAylin§r: Koy halki bana boyle diyor. Onu tutan, ona kimsenin ulasmasini engelleyen kisi.", "§dAylin§r: Ama gercek su ki - ben onu tutmuyorum. Kendimi ona karsi tutuyorum. Fark ince ama onemli." },
        { "§dAylin§r: Yalniz kalma. Isigi hic sondurme. Ve en onemlisi - ona asla uzun sure bakma.", "§dAylin§r: Saglamligin dustukce, o senin gercekligine daha rahat sizabiliyor. Dikkatli ol." },
        { "§dAylin§r: Kurtlar onu hissediyor, biz hissetmeden once. Evcillestirdigin her sey ona bir yol aciyor gibi.", "§dAylin§r: Bunu bedelini odeyerek ogrendim. Benden once de cok kisi ogrenmis." },
        { "§dAylin§r: Ben mi? Once sadece bir kiz kardestim. Simdi... bir bekciyim sanirim.", "§dAylin§r: Pismanlik seni yerinde tutar ama kurtarmaz. Bunu ogrenmem uzun surdu." },
        { "§dAylin§r: Git. Ama uzaklasma - yakinlarda ol. Elli gun dolunca, yaninda olacagim." }
    };

    /** lines[i]'den baslayarak her satiri delay tick arayla gonderir. */
    private static void sendLines(Player player, String[] lines, int i, int delay) {
        if (i >= lines.length) return;
        try { player.sendMessage(lines[i]); } catch (Throwable e) { /* yut */ }
        Sys.runTimeout(() -> sendLines(player, lines, i + 1, delay), delay);
    }

    public static void openAylinDialogue(Player player) {
        try {
            Forms.Action form = new Forms.Action()
                .title("Aylin")
                .body("§7Onunla ne konusmak istersin?§r");
            for (String t : AYLIN_TOPICS) form.button(t);
            form.show(player, res -> {
                try {
                    if (res == null || res.canceled) return;
                    if (res.selection < 0 || res.selection >= AYLIN_DIALOGUE.length) return;
                    String[] lines = AYLIN_DIALOGUE[res.selection];
                    sendLines(player, lines, 0, 40);
                    // "Vedalas" disinda, sohbeti tekrar acabilmesi icin menu tekrar sunulabilir
                    if (res.selection != AYLIN_TOPICS.length - 1) {
                        Sys.runTimeout(() -> openAylinDialogue(player), lines.length * 40 + 20);
                    }
                } catch (Throwable e) { /* yut */ }
            });
        } catch (Throwable e) { /* yut */ }
    }

    // ================================================================== BEKCI/ABLA (Aylin) - Zindanda ozgur birakilan dost
    public static final String SISTER_FREED = "duskwalker:sisterFreed";

    // ---- Ele gecirme (possession) ----
    private static final class Possessed {
        final Entity entity; final long until;
        Possessed(Entity e, long u) { entity = e; until = u; }
    }
    private static final List<Possessed> possessed = new ArrayList<>();

    public static void doPossession(Player player) {
        try {
            Dimension dimension = player.dimension();
            List<Entity> nearby = new ArrayList<>();
            for (Entity e : dimension.getEntities(Opts.EntityQuery.any().near(player.location(), 14))) {
                if (!e.typeId().equals("minecraft:player") && !e.typeId().equals("duskwalker:duskwalker")) nearby.add(e);
            }
            if (nearby.isEmpty()) return;
            int count = Math.min(nearby.size(), Core.randInt(1, 3));
            for (int i = 0; i < count; i++) {
                Entity target = nearby.get(Core.randInt(0, nearby.size() - 1));
                try {
                    target.addEffect("glowing", 600, 0, false);
                    target.addEffect("speed", 600, 1, false);
                } catch (Throwable e) { /* yut */ }
                possessed.add(new Possessed(target, Sys.currentTick() + 600));
            }
            try { player.sendMessage("§7(bir seyler etrafinda degisti...)§r"); } catch (Throwable e) { /* yut */ }
            MainA.addEvidence(player, 1);
        } catch (Throwable e) { /* yut */ }
    }

    // ================================================================== v9.9 - 5 OZGUN OZELLIK
    public static final String NEXT_STOLEN = "duskwalker:nextStolen";
    public static final String NEXT_MIMIC = "duskwalker:nextMimic";
    public static final String NEXT_HOME = "duskwalker:nextHome";
    public static final String STOLEN_LOG = "duskwalker:stolenLog";
    public static final String HOME_CELLS = "duskwalker:homeCells";
    public static final String HOME_PENDING = "duskwalker:homePending";
    public static final String CLOCK_DAY = "duskwalker:clockDay";
    public static final String ECHO_PREFIX = "duskwalker:deathEcho_";
    public static final String MIMIC_LEFTOVER = "duskwalker:mimicBlocks";
    public static final String OVERWORLD_ID = "minecraft:overworld";

    public static boolean v99Enabled(String key) {
        try { return !MainA.cfgIsFalse(MainA.getConfig(), key); } catch (Throwable e) { return true; }
    }

    public static boolean entityOk(Entity e) {
        try { return e != null && e.isValid(); } catch (Throwable err) { return false; }
    }

    public static double r1(double n) { return Math.round(n * 10) / 10.0; }
    public static double dist2d(Vec3 a, Vec3 b) { return Math.hypot(a.x() - b.x(), a.z() - b.z()); }

    // ---------------------------------------------------------
    //  1) OLUM YANKISI
    // ---------------------------------------------------------
    private static final class PathPt {
        final double x, y, z; final String d;
        PathPt(double x, double y, double z, String d) { this.x = x; this.y = y; this.z = z; this.d = d; }
    }
    private static final Map<String, List<PathPt>> pathBuffers = new HashMap<>();   // oyuncu id -> son konumlar (10 tikte bir)
    private static final Map<String, Long> echoLastTick = new HashMap<>();           // oyuncu id -> son yanki baslangic tiki

    /** Kayit: { d: boyut, n: tekrar sayisi, p: [[x,y,z],...] } veya null */
    public static Map<String, Object> readEchoRecord(Player player) {
        try {
            Object raw = World.getDynamicProperty(ECHO_PREFIX + player.name());
            if (raw instanceof String s) return Json.asMap(Json.parse(s));
        } catch (Throwable e) { /* yut */ }
        return null;
    }

    private static double[] ptOf(Object o) {
        List<Object> l = Json.asList(o);
        return new double[] { Json.num(l.get(0), 0), Json.num(l.get(1), 0), Json.num(l.get(2), 0) };
    }

    // force=true: test icin (kayit yoksa anlik konum gecmisini kullanir)
    public static boolean startEcho(Player player, boolean force) {
        try {
            Map<String, Object> rec = readEchoRecord(player);
            List<double[]> path = null;
            String dimId = player.dimension().id();
            List<Object> recP = rec == null ? null : Json.asList(rec.get("p"));
            if (rec != null && recP != null && recP.size() >= 6 && (force || Json.num(rec.get("n"), 0) < 2)) {
                path = new ArrayList<>();
                for (Object o : recP) path.add(ptOf(o));
                dimId = Json.str(rec.get("d"), dimId);
            } else if (force) {
                List<PathPt> buf = pathBuffers.getOrDefault(player.id(), new ArrayList<>());
                if (buf.size() >= 6) {
                    path = new ArrayList<>();
                    for (PathPt q : buf) path.add(new double[] { q.x, q.y, q.z });
                }
            }
            if (path == null) return false;
            if (!player.dimension().id().equals(dimId)) return false;
            if (rec != null && !force) {
                rec.put("n", Json.num(rec.get("n"), 0) + 1);
                World.setDynamicProperty(ECHO_PREFIX + player.name(), Json.stringify(rec));
            }
            Dimension dimension = player.dimension();
            Vec3 start = new Vec3(path.get(0)[0], path.get(0)[1], path.get(0)[2]);
            Entity entity;
            try { entity = dimension.spawnEntity("duskwalker:echo", start); } catch (Throwable e) { return false; }
            try { entity.setNameTag("§7" + player.name()); } catch (Throwable e) { /* yut */ }
            Map<String, Object> extra = new HashMap<>();
            extra.put("target", player);
            extra.put("path", path);
            extra.put("seg", 0.0);
            extra.put("t", 0.0);
            extra.put("lastTick", (double) Sys.currentTick());
            MainA.enterCreatureState(entity, "replay", extra);
            echoLastTick.put(player.id(), Sys.currentTick());
            MainA.loseSanity(player, 3);
            try { player.sendMessage("§7(uzakta tanidik bir siluet yuruyor...)§r"); } catch (Throwable e) { /* yut */ }
            return true;
        } catch (Throwable e) { return false; }
    }

    @SuppressWarnings("unchecked")
    private static void echoBehavior(Entity entity, MainA.CreatureState state, Dimension dimension) {
        Player player = state.get("target") instanceof Player p ? p : null;
        List<double[]> path = state.get("path") instanceof List<?> l ? (List<double[]>) l : null;
        if (!entityOk(player) || path == null) {
            try { entity.triggerEvent("duskwalker:vanish"); } catch (Throwable e) { /* yut */ }
            MainA.clearCreatureState(entity);
            return;
        }
        if ("replay".equals(state.mode)) {
            long tick = Sys.currentTick();
            double lastTick = state.num("lastTick", tick);
            if (lastTick == 0) lastTick = tick; // JS: (state.lastTick || tick)
            double dt = Math.max(0, tick - lastTick);
            state.put("lastTick", (double) tick);
            double t = state.num("t", 0) + dt / 10; // her kayit noktasi arasi 10 tik
            int seg = (int) state.num("seg", 0);
            while (t >= 1) { t -= 1; seg++; }
            state.put("t", t);
            state.put("seg", (double) seg);
            if (seg >= path.size() - 1) {
                Map<String, Object> extra = new HashMap<>();
                extra.put("target", player);
                extra.put("path", path);
                MainA.enterCreatureState(entity, "ending", extra);
                return;
            }
            double[] a = path.get(seg), b = path.get(seg + 1);
            Vec3 pos = new Vec3(a[0] + (b[0] - a[0]) * t, a[1] + (b[1] - a[1]) * t, a[2] + (b[2] - a[2]) * t);
            try { entity.teleport(pos, Opts.Teleport.dim(dimension).facing(new Vec3(b[0], b[1] + 1, b[2]))); } catch (Throwable e) { /* yut */ }
            if (state.stateTicks > 20 * 90) {
                try { entity.triggerEvent("duskwalker:vanish"); } catch (Throwable e) { /* yut */ }
                MainA.clearCreatureState(entity);
            }
            return;
        }
        if ("ending".equals(state.mode)) {
            if (state.stateTicks == 1) {
                try {
                    Vec3 loc = entity.location();
                    for (int i = 0; i < 10; i++) {
                        dimension.spawnParticle("minecraft:soul_particle", new Vec3(loc.x() + (Math.random() - 0.5), loc.y() + Math.random() * 1.8, loc.z() + (Math.random() - 0.5)));
                    }
                    dimension.playSound("mob.ghast.death", loc, Opts.Sound.of(0.7, 0.6));
                    double d = Math.hypot(player.location().x() - loc.x(), player.location().z() - loc.z());
                    if (d <= 24) {
                        MainA.loseSanity(player, 8);
                        MainA.addEvidence(player, 2);
                        player.sendMessage("§8(kendi son adimlarini izledin...)§r");
                    }
                } catch (Throwable e) { /* yut */ }
            }
            if (state.stateTicks >= 8) {
                try { entity.triggerEvent("duskwalker:vanish"); } catch (Throwable e) { /* yut */ }
                MainA.clearCreatureState(entity);
            }
        }
    }

    // ---------------------------------------------------------
    //  2) CALINTI KELIMELER
    // ---------------------------------------------------------
    public static String twistWords(String text) {
        String[] words = text.trim().split("\\s+");
        if (words.length >= 2) {
            List<String> l = new ArrayList<>(Arrays.asList(words));
            Collections.reverse(l);
            return String.join(" ", l);
        }
        return new StringBuilder(text).reverse().toString();
    }

    public static boolean doStolenWords(Player player) {
        try {
            Object raw = player.getDynamicProperty(STOLEN_LOG);
            if (!(raw instanceof String s)) return false;
            List<Object> list = Json.asList(Json.parse(s));
            if (list == null || list.isEmpty()) return false;
            String text = String.valueOf(list.get(Core.randInt(0, list.size() - 1)));
            String twisted = twistWords(text);
            player.sendMessage("<" + player.name() + "> " + twisted);
            Sys.runTimeout(() -> {
                try { player.sendMessage("§8<" + player.name() + "> ...§r"); } catch (Throwable e) { /* yut */ }
            }, 80);
            try { player.playSound("ambient.cave", Opts.Sound.of(0.4, 0.6)); } catch (Throwable e) { /* yut */ }
            MainA.loseSanity(player, 4);
            return true;
        } catch (Throwable e) { return false; }
    }

    // ---------------------------------------------------------
    //  3) GERCEK SAAT 03:33
    // ---------------------------------------------------------
    private static final class RealClock { int h, m; String key; }

    public static RealClock getRealClock() {
        try {
            LocalDateTime d = LocalDateTime.now();
            RealClock c = new RealClock();
            c.h = d.getHour(); c.m = d.getMinute();
            c.key = d.getYear() + "-" + d.getMonthValue() + "-" + d.getDayOfMonth();
            return c;
        } catch (Throwable e) { return null; }
    }

    public static void doClockEvent(Player player) {
        try {
            try { player.setTitle("§403:33§r", Opts.Title.of(5, 60, 30, "§8bu saatte kimse uyanik olmamali§r")); } catch (Throwable e) { /* yut */ }
            try { player.playSound("mob.wither.ambient", Opts.Sound.of(0.7, 0.5)); } catch (Throwable e) { /* yut */ }
            try { player.sendMessage("§4Duskwalker§r: Su an gercek dunyada saat 03:33. Sen de uyaniksin."); } catch (Throwable e) { /* yut */ }
            MainA.loseSanity(player, 10);
            MainA.addEvidence(player, 1);
            MainB.nudgeNextVisible(player, 0.5);
            Sys.runTimeout(() -> { try { doTorch(player); } catch (Throwable e) { /* yut */ } }, 40);
            Sys.runTimeout(() -> { try { doDoor(player); } catch (Throwable e) { /* yut */ } }, 80);
            Sys.runTimeout(() -> { try { doTorch(player); } catch (Throwable e) { /* yut */ } }, 120);
            Sys.runTimeout(() -> { try { MainB.doStalk(player); } catch (Throwable e) { /* yut */ } }, 160);
        } catch (Throwable e) { /* yut */ }
    }

    // ---------------------------------------------------------
    //  4) TAKLITCI
    // ---------------------------------------------------------
    private static final Pattern MIMIC_EXCLUDE = Pattern.compile("(chest|barrel|furnace|smoker|shulker|sign|bed|door|banner|skull|head|hopper|dropper|dispenser|lectern|brewing|campfire|command|piston|lever|button|torch|ladder|vine|redstone|water|lava|fire|tnt|bell|chain|lantern|candle|pot|sapling|beacon|spawner|jukebox|note_block|cauldron|loom|anvil|enchanting|crafting|stonecutter|grindstone|composter|frame|rail|plate|tripwire|comparator|repeater|observer|daylight|target|sculk|conduit|crafter|bundle)");
    private static final Pattern GROUND_OK = Pattern.compile("(grass|dirt|stone|sand|gravel|snow|podzol|mycelium|terracotta|deepslate|mud|clay)");

    private static final class MBlock {
        final int x, y, z; final Perm perm;
        MBlock(int x, int y, int z, Perm perm) { this.x = x; this.y = y; this.z = z; this.perm = perm; }
    }
    private static final class Cluster {
        List<MBlock> blocks = new ArrayList<>(); int lx, ly, lz; long tick; String d;
    }
    private static final class Placed {
        final int x, y, z; final String id;
        Placed(int x, int y, int z, String id) { this.x = x; this.y = y; this.z = z; this.id = id; }
    }
    private static final class Mimic {
        String dimId; List<Placed> blocks; long placedTick; String ownerId; boolean seen; Vec3 center;
    }

    private static final Map<String, Cluster> buildClusters = new HashMap<>(); // oyuncu id -> { blocks, last, tick, d }
    private static final Map<String, List<MBlock>> lastBuilds = new HashMap<>(); // oyuncu id -> son tamamlanmis yapi (>= 8 blok)
    private static Mimic activeMimic = null;

    private static void saveMimicRecord() {
        try {
            if (activeMimic == null) { World.setDynamicProperty(MIMIC_LEFTOVER, null); return; }
            List<Object> arr = Json.newList();
            for (Placed q : activeMimic.blocks) arr.add(Arrays.asList((Object) (double) q.x, (double) q.y, (double) q.z, q.id));
            Map<String, Object> rec = Json.newMap();
            rec.put("d", activeMimic.dimId);
            rec.put("b", arr);
            World.setDynamicProperty(MIMIC_LEFTOVER, Json.stringify(rec));
        } catch (Throwable e) { /* yut */ }
    }

    public static boolean tryMimic(Player player) {
        try {
            if (activeMimic != null) return false;
            List<MBlock> snap = lastBuilds.get(player.id());
            if (snap == null || snap.size() < 8) return false;
            Dimension dimension = player.dimension();
            if (!dimension.id().equals(OVERWORLD_ID)) return false;

            int minX = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE, maxZ = Integer.MIN_VALUE;
            for (MBlock q : snap) {
                minX = Math.min(minX, q.x); maxX = Math.max(maxX, q.x);
                minY = Math.min(minY, q.y);
                minZ = Math.min(minZ, q.z); maxZ = Math.max(maxZ, q.z);
            }
            if (maxX - minX > 24 || maxZ - minZ > 24) return false;
            List<MBlock> rel = new ArrayList<>(); // X ekseninde aynalanmis
            for (MBlock q : snap) rel.add(new MBlock(maxX - q.x, q.y - minY, q.z - minZ, q.perm));

            for (int attempt = 0; attempt < 6; attempt++) {
                double angle = Math.random() * Math.PI * 2;
                double dist = 30 + Math.random() * 18;
                int ox = (int) Math.floor(player.location().x() + Math.cos(angle) * dist);
                int oz = (int) Math.floor(player.location().z() + Math.sin(angle) * dist);
                Block top;
                try { top = dimension.getTopmostBlock(ox, oz); } catch (Throwable e) { continue; }
                if (top == null || !GROUND_OK.matcher(top.typeId()).find()) continue;
                int baseY = top.y() + 1;
                boolean clear = true;
                for (MBlock q : rel) {
                    Block blk;
                    try { blk = dimension.getBlock(new Vec3(ox + q.x, baseY + q.y, oz + q.z)); } catch (Throwable e) { clear = false; break; }
                    if (blk == null || !blk.typeId().equals("minecraft:air")) { clear = false; break; }
                }
                if (!clear) continue;

                int skip = Core.randInt(0, rel.size() - 1); // bir blok eksik: "yanlis" kopya
                List<Placed> placed = new ArrayList<>();
                for (int i = 0; i < rel.size(); i++) {
                    if (i == skip) continue;
                    MBlock q = rel.get(i);
                    try {
                        Block blk = dimension.getBlock(new Vec3(ox + q.x, baseY + q.y, oz + q.z));
                        blk.setPermutation(q.perm);
                        placed.add(new Placed(ox + q.x, baseY + q.y, oz + q.z, blk.typeId()));
                    } catch (Throwable e) { /* yut */ }
                }
                if (placed.isEmpty()) continue;

                // yaninda kisa bir tabela
                try {
                    int sx = ox - 2, sz = oz + (int) Math.floor((maxZ - minZ) / 2.0);
                    Block sTop = dimension.getTopmostBlock(sx, sz);
                    if (sTop != null && GROUND_OK.matcher(sTop.typeId()).find()) {
                        Block sb = dimension.getBlock(new Vec3(sx, sTop.y() + 1, sz));
                        if (sb != null && sb.typeId().equals("minecraft:air")) {
                            sb.setType("minecraft:standing_sign");
                            sb.setSignText("BUNU SEN YAPTIN");
                            placed.add(new Placed(sx, sTop.y() + 1, sz, sb.typeId()));
                        }
                    }
                } catch (Throwable e) { /* yut */ }

                Mimic m = new Mimic();
                m.dimId = dimension.id(); m.blocks = placed; m.placedTick = Sys.currentTick(); m.ownerId = player.id(); m.seen = false;
                m.center = new Vec3(ox + (maxX - minX) / 2.0, baseY, oz + (maxZ - minZ) / 2.0);
                activeMimic = m;
                saveMimicRecord();
                try { player.sendMessage("§7(uzakta bir seyin sesi duyuldu... bir sey insa ediliyor)§r"); } catch (Throwable e) { /* yut */ }
                return true;
            }
        } catch (Throwable e) { /* yut */ }
        return false;
    }

    public static void dissolveMimic(Dimension dimension) {
        if (activeMimic == null) return;
        try {
            for (Placed q : activeMimic.blocks) {
                try {
                    Block blk = dimension.getBlock(new Vec3(q.x, q.y, q.z));
                    if (blk != null && blk.typeId().equals(q.id)) blk.setType("minecraft:air");
                } catch (Throwable e) { /* yut */ }
            }
            try { MainA.smokePuff(dimension, activeMimic.center, 8); } catch (Throwable e) { /* yut */ }
            try { dimension.playSound("random.fizz", activeMimic.center, Opts.Sound.of(0.8, 0.5)); } catch (Throwable e) { /* yut */ }
        } catch (Throwable e) { /* yut */ }
        activeMimic = null;
        saveMimicRecord();
    }

    // ---------------------------------------------------------
    //  5) EV ZIYARETI
    // ---------------------------------------------------------
    /** { "hx,hz": { s, x, y, z } } haritasi */
    public static Map<String, Object> readHomeCells(Player player) {
        try {
            Object raw = player.getDynamicProperty(HOME_CELLS);
            if (raw instanceof String s) {
                Map<String, Object> m = Json.asMap(Json.parse(s));
                if (m != null) return m;
            }
        } catch (Throwable e) { /* yut */ }
        return new LinkedHashMap<>();
    }

    /** En cok vakit gecirilen hucre { s, x, y, z } veya null */
    public static Map<String, Object> getHome(Player player) {
        Map<String, Object> cells = readHomeCells(player);
        Map<String, Object> best = null;
        for (String k : cells.keySet()) {
            Map<String, Object> c = Json.asMap(cells.get(k));
            if (c == null) continue;
            if (Json.num(c.get("s"), 0) >= 24000 && (best == null || Json.num(c.get("s"), 0) > Json.num(best.get("s"), 0))) best = c; // en az ~20 dk
        }
        return best;
    }

    public static final String[] VISIT_SIGNS = { "EVDEYDIM", "SENI BEKLEDIM", "BURASI ARTIK BENIM", "GEC KALDIN" };

    public static void applyHomeVisit(Player player, Map<String, Object> home) {
        try {
            Dimension dimension = player.dimension();
            int cx = (int) Math.floor(Json.num(home.get("x"), 0)), cy = (int) Math.floor(Json.num(home.get("y"), 0)), cz = (int) Math.floor(Json.num(home.get("z"), 0));
            int doors = 0, torches = 0;
            for (int i = 0; i < 70 && (doors < 3 || torches < 2); i++) {
                Vec3 pos = new Vec3(cx + Core.randInt(-12, 12), cy + Core.randInt(-4, 5), cz + Core.randInt(-12, 12));
                Block blk;
                try { blk = dimension.getBlock(pos); } catch (Throwable e) { continue; }
                if (blk == null) continue;
                if (doors < 3 && blk.typeId().contains("door") && !blk.typeId().contains("trapdoor")) {
                    try {
                        Perm perm = blk.permutation();
                        Map<String, Object> st = perm.getAllStates();
                        if (!st.containsKey("open_bit")) continue;
                        boolean nv = !MainA.truthy(st.get("open_bit"));
                        blk.setPermutation(perm.withState("open_bit", nv));
                        Vec3 partnerPos = MainA.truthy(st.get("upper_block_bit")) ? new Vec3(pos.x(), pos.y() - 1, pos.z()) : new Vec3(pos.x(), pos.y() + 1, pos.z());
                        try {
                            Block pb = dimension.getBlock(partnerPos);
                            if (pb != null && pb.typeId().equals(blk.typeId())) pb.setPermutation(pb.permutation().withState("open_bit", nv));
                        } catch (Throwable e) { /* yut */ }
                        try { dimension.playSound("open.wooden_door", pos, Opts.Sound.of(0.8, 1)); } catch (Throwable e) { /* yut */ }
                        doors++;
                    } catch (Throwable e) { /* yut */ }
                } else if (torches < 2 && blk.typeId().contains("torch")) {
                    try {
                        blk.setType("minecraft:air");
                        dimension.spawnParticle("minecraft:large_smoke_particle", new Vec3(pos.x() + 0.5, pos.y() + 0.5, pos.z() + 0.5));
                        torches++;
                    } catch (Throwable e) { /* yut */ }
                }
            }
            // evin yakinina bir tabela
            for (int i = 0; i < 25; i++) {
                Vec3 pos = new Vec3(cx + Core.randInt(-5, 5), cy + Core.randInt(-2, 3), cz + Core.randInt(-5, 5));
                Block blk, below;
                try { blk = dimension.getBlock(pos); below = dimension.getBlock(new Vec3(pos.x(), pos.y() - 1, pos.z())); } catch (Throwable e) { continue; }
                if (blk == null || below == null || !blk.typeId().equals("minecraft:air") || below.typeId().equals("minecraft:air") || below.typeId().contains("water") || below.typeId().contains("lava")) continue;
                try {
                    blk.setType("minecraft:standing_sign");
                    blk.setSignText(VISIT_SIGNS[Core.randInt(0, VISIT_SIGNS.length - 1)]);
                    MainA.smokePuff(dimension, new Vec3(pos.x() + 0.5, pos.y() + 0.5, pos.z() + 0.5), 5);
                    break;
                } catch (Throwable e) { /* yut */ }
            }
            try { player.playSound("mob.endermen.stare", Opts.Sound.of(0.6, 0.5)); } catch (Throwable e) { /* yut */ }
            try { player.sendMessage("§7(evinde biri vardi...)§r"); } catch (Throwable e) { /* yut */ }
            MainA.loseSanity(player, 12);
            MainA.addEvidence(player, 1);
            MainB.nudgeNextVisible(player, 0.6);
        } catch (Throwable e) { /* yut */ }
    }

    /** ZAMANLAYICI TARAMASI (tick butcesi ile): yanki / calinti kelime / taklitci / ev ziyareti */
    private static void v99Process(Player player) {
        try {
            Map<String, Object> cfg = MainA.getConfig();
            long tick = Sys.currentTick();
            int day = MainA.getDay();
            Set<String> unlocked = MainA.getUnlockedEventSet(day);
            double intensity = MainA.getIntensity(cfg);
            final int TPM = MainA.TICKS_PER_MIN;

            // 2) calinti kelimeler
            if (!MainA.cfgIsFalse(cfg, "stolen") && unlocked.contains("stolen")) {
                Object next = player.getDynamicProperty(NEXT_STOLEN);
                if (next == null) player.setDynamicProperty(NEXT_STOLEN, (double) (tick + Core.randInt(4 * TPM, 8 * TPM)));
                else if (next instanceof Number n && tick >= n.doubleValue()) {
                    boolean ok = doStolenWords(player);
                    double gap = ok ? MainA.lerp(16 * TPM, 6 * TPM, intensity) : 2 * TPM;
                    player.setDynamicProperty(NEXT_STOLEN, (double) (tick + MainA.jitter(gap, 0.4)));
                }
            }

            // 1) olum yankisi
            if (!MainA.cfgIsFalse(cfg, "echo") && unlocked.contains("echo")) {
                Map<String, Object> rec = readEchoRecord(player);
                long last = echoLastTick.getOrDefault(player.id(), 0L);
                List<Object> recP = rec == null ? null : Json.asList(rec.get("p"));
                if (rec != null && recP != null && recP.size() >= 6 && Json.num(rec.get("n"), 0) < 2 && tick - last > 6000 && player.dimension().id().equals(rec.get("d"))) {
                    double[] p0 = ptOf(recP.get(0));
                    double d = Math.hypot(player.location().x() - p0[0], player.location().z() - p0[2]);
                    if (d >= 10 && d <= 40 && (MainB.isNight() || MainB.isUnderground(player))) startEcho(player, false);
                }
            }

            // 4) taklitci
            if (!MainA.cfgIsFalse(cfg, "mimic") && unlocked.contains("mimic")) {
                Object next = player.getDynamicProperty(NEXT_MIMIC);
                if (next == null) player.setDynamicProperty(NEXT_MIMIC, (double) (tick + Core.randInt(10 * TPM, 18 * TPM)));
                else if (next instanceof Number n && tick >= n.doubleValue() && MainB.isNight()) {
                    boolean ok = tryMimic(player);
                    player.setDynamicProperty(NEXT_MIMIC, (double) (tick + (ok ? Core.randInt(18 * TPM, 35 * TPM) : 3 * TPM)));
                }
            }

            // 5) ev ziyareti
            if (!MainA.cfgIsFalse(cfg, "homevisit") && unlocked.contains("homevisit") && player.dimension().id().equals(OVERWORLD_ID)) {
                Map<String, Object> home = getHome(player);
                if (home != null) {
                    double d = Math.hypot(player.location().x() - Json.num(home.get("x"), 0), player.location().z() - Json.num(home.get("z"), 0));
                    Object pending = player.getDynamicProperty(HOME_PENDING);
                    if (pending instanceof Number pn) {
                        if (d <= 25 && tick - pn.doubleValue() < 4 * TPM) player.setDynamicProperty(HOME_PENDING, null);
                        else if (d <= 40 && tick - pn.doubleValue() >= 4 * TPM) {
                            player.setDynamicProperty(HOME_PENDING, null);
                            player.setDynamicProperty(NEXT_HOME, (double) (tick + Core.randInt(15 * TPM, 30 * TPM)));
                            applyHomeVisit(player, home);
                        }
                    } else {
                        Object next = player.getDynamicProperty(NEXT_HOME);
                        if (next == null) player.setDynamicProperty(NEXT_HOME, (double) (tick + Core.randInt(8 * TPM, 15 * TPM)));
                        else if (next instanceof Number n && tick >= n.doubleValue() && d >= 70) player.setDynamicProperty(HOME_PENDING, (double) tick);
                    }
                }
            }
        } catch (Throwable e) { /* yut */ }
    }

    // ================================================================== v9.10 - LAV
    private static final Map<String, Integer> lavaTicksMap = new HashMap<>(); // varlik id -> lavda gecen tik

    private static boolean isLavaBlock(Block b) {
        return b != null && (b.typeId().equals("minecraft:lava") || b.typeId().equals("minecraft:flowing_lava"));
    }

    private static void lavaTick() {
        for (String dimName : new String[] { "overworld", "nether", "the_end" }) {
            Dimension dim; List<Entity> list;
            try { dim = World.getDimension(dimName); list = dim.getEntities(Opts.EntityQuery.type("duskwalker:duskwalker")); } catch (Throwable e) { continue; }
            for (Entity ent : list) {
                try {
                    Vec3 l = ent.location();
                    int bx = (int) Math.floor(l.x()), bz = (int) Math.floor(l.z());
                    boolean inLava = false;
                    for (double dy : new double[] { 0.1, 1.0 }) {
                        try { if (isLavaBlock(dim.getBlock(new Vec3(bx, Math.floor(l.y() + dy), bz)))) { inLava = true; break; } } catch (Throwable e) { /* yut */ }
                    }
                    int t = lavaTicksMap.getOrDefault(ent.id(), 0);
                    if (inLava) {
                        t += 10;
                        if (t % 20 == 10) {
                            try { dim.spawnParticle("minecraft:lava_particle", new Vec3(l.x(), l.y() + 1, l.z())); } catch (Throwable e) { /* yut */ }
                            try { dim.playSound("random.fizz", l, Opts.Sound.of(0.8, 0.6)); } catch (Throwable e) { /* yut */ }
                        }
                        if (t >= 200) {
                            lavaTicksMap.remove(ent.id());
                            try { dim.playSound("mob.wither.death", l, Opts.Sound.of(0.8, 0.5)); } catch (Throwable e) { /* yut */ }
                            try { MainA.smokePuff(dim, new Vec3(l.x(), l.y() + 1, l.z()), 10); } catch (Throwable e) { /* yut */ }
                            try { ent.kill(); } catch (Throwable e) { try { ent.triggerEvent("duskwalker:vanish"); } catch (Throwable e2) { /* yut */ } }
                            continue;
                        }
                    } else if (t > 0) {
                        t = Math.max(0, t - 5);
                    }
                    if (t > 0) lavaTicksMap.put(ent.id(), t); else lavaTicksMap.remove(ent.id());
                } catch (Throwable e) { /* yut */ }
            }
        }
    }

    // ================================================================== v10 - ATMOSFER, GUVENILIRLIK, MENU
    // ---- Av sirasinda sis (kendi sis tanimi: duskwalker:hunt_fog) ----
    private static final Map<String, Integer> huntFogCount = new HashMap<>();

    public static void huntFogPush(Player player) {
        try {
            int n = huntFogCount.getOrDefault(player.id(), 0) + 1;
            huntFogCount.put(player.id(), n);
            if (n == 1) player.fogPush("duskwalker:hunt_fog", "duskwalker_hunt");
        } catch (Throwable e) { /* yut */ }
    }

    public static void huntFogPop(Player player) {
        try {
            int n = Math.max(0, huntFogCount.getOrDefault(player.id(), 1) - 1);
            if (n == 0) { huntFogCount.remove(player.id()); player.fogPop("duskwalker_hunt"); }
            else huntFogCount.put(player.id(), n);
        } catch (Throwable e) { /* yut */ }
    }

    // ---- !dwmenu : tum ayarlar tek formda ----
    public static final String[][] MENU_TOGGLES = {
        { "stalk", "Gozetleme" },
        { "jumpscare", "Ani korku (jumpscare)" },
        { "quickstrike", "Ani vurus" },
        { "hunt", "Av" },
        { "huntfast", "Hizli av" },
        { "twinhunt", "Ikiz av" },
        { "whisper", "Fisiltilar" },
        { "chat", "Sahte sohbet" },
        { "sign", "Tabelalar" },
        { "torch", "Mesale sondurme" },
        { "door", "Kapilar" },
        { "possession", "Ele gecirme" },
        { "reflection", "Yansima" },
        { "abduction", "Kabus koridorlari" },
        { "wolfRevenge", "Kurt intikami" },
        { "structures", "Yapilar" },
        { "sanity", "Saglamlik sistemi" },
        { "moon", "Dolunay" },
        { "heartbeat", "Kalp atisi" },
        { "evidence", "Kanit sistemi" },
        { "seep", "Sizinti" },
        { "watcher", "Pencere Ruhu" },
        { "echo", "Olum Yankisi" },
        { "mimic", "Taklitci" },
        { "stolen", "Calinti Kelimeler" },
        { "clock", "Gercek saat 03:33" },
        { "homevisit", "Ev Ziyareti" },
        { "delayed_steps", "Gecikmeli Adimlar" },
        { "phantom_sound", "Hayalet Sesler" },
        { "fake_damage", "Sahte Hasar" },
        { "door_knock", "Kapi Vurusu" },
        { "far_bell", "Uzak Can" },
        { "freeze_frame", "Sahte Cokme" },
        { "turning_heads", "Donen Kafalar" },
        { "stand_still", "Durursan Gelir" },
        { "cold_spot", "Soguk Nokta" },
        { "fake_friend", "Sahte Arkadas" },
        { "sign_diary", "Tabela Gunlugu" },
        { "light_flicker", "Titreyen Isik" },
        { "grave_marker", "Mezar Tasi" },
        { "refuge", "Isik Siginagi" },
        { "atmosphere", "Atmosfer (alacakaranlik, ambiyans)" },
        { "achievements", "Basarimlar" },
        { "moonsky", "Ay takvimi (iki ay, aysiz gece...)" },
        { "nightweather", "Gece tipleri (sis, kul, uzun gece)" },
        { "dont_blink", "Don't Blink" },
        { "eyes_dark", "Karanliktaki Gozler" },
        { "shadow_follower", "Golge Takipci" },
        { "haunted_item", "Lanetli Esya" },
        { "chest_bite", "Sandik Isirigi" },
        { "system_glitch", "Sistem Arizasi" },
        { "candle_gamble", "Mum Kumari" },
        { "fake_coords", "Sahte Koordinat" },
        { "slot_drift", "Kayan Slot" },
        { "whisper_title", "Altlik Mesaj" },
        { "false_dawn", "Yalan Safak" },
        { "peripheral_figure", "Yan Gorus Silueti" },
        { "mirror_behind", "Arkadaki Sima" },
        { "tunnel_vision", "Tunel Gorusu" },
        { "water_shadow", "Sudaki Golge" },
        { "lightning_silhouette", "Yildirim Siluet" },
        { "noise", "Gurultu sistemi" },
        { "quests", "Gece Nobeti gorevleri" },
        { "dreams", "Ruyalar" },
        { "oracle", "Kehanet (sohbete tepki)" },
        { "adaptive", "Uyumlu zorluk" },
        { "graves", "Mezarlar" },
        { "ruins", "Harabeler" },
        { "house", "Bos Ev" },
        { "ambience2", "Bolgesel ortam sesleri" },
        { "buddy", "Arkadas sistemi" },
        { "forecast", "Gece tahmini" },
        { "hints", "Ipuclari" },
        { "paradox", "Paradoks Koridoru" },
        { "procession", "Alay ve Bos Gece" },
        { "zones", "Lanetli bolgeler" },
        { "deja_vu", "Deja Vu" },
        { "countdown", "Geri Sayim" },
        { "nape_breath", "Ense Nefesi" },
        { "mirror_twin", "Ayna Ikiz" },
        { "unwritten", "Yazilmamis" },
        { "listening_hour", "Dinleme Saati" },
        { "heart", "Kalp atisi motoru" },
        { "sanity_fog", "Akil sagligi sisi" },
        { "oaths", "Yeminler" },
        { "darkness_fx", "Karanlik efekti (ekrani karartir)" },
        { "controls", "Kontrol esyasi (Cep Saati)" },
        { "learn", "Ogrenen Avci" },
        { "sanctuary", "Siginak ve Kusatma" },
        { "past_selves", "Dunun Sen'i" },
        { "depths", "Bos Derinlikler" },
        { "pacts", "Anlasmalar" },
        { "light_siege", "Isik Kusatmasi" },
        { "digger", "Kazici" },
        { "lantern_thief", "Fener Hirsizi" },
        { "footprint_trail", "Ruh Ayak Izleri" },
        { "bat_omen", "Yarasa Alameti" },
        { "glitch_hud", "Arayuz Arizasi" },
        { "circling_bells", "Donen Canlar" },
        { "music_box", "Muzik Kutusu" },
        { "pit_whisper", "Alttan Fisilti" },
        { "weightless", "Agirliksizlik" },
        { "sign_turn", "Donen Tabela" },
        { "shared_breath", "Paylasilan Nefes" },
        { "numbers_station", "Sayi Istasyonu" },
        { "moths", "Guveler" },
        { "echo_mine", "Yanki Kazma" },
        { "tinnitus", "Kulak Cinlamasi" },
        { "fake_loot", "Yalan Ganimet" },
        { "the_lull", "Yalan Sakinlik" },
        { "adrenaline", "Adrenalin" },
        { "habituation", "Alisma" },
        { "memory_marks", "Hatira Isaretleri" },
        { "shaking_hands", "Titreyen Eller" },
        { "microsleep", "Mikro uyku" },
        { "dark_hunger", "Karanlik Acligi" },
        { "death_scar", "Olum Izi" },
        { "breaking_point", "Cokus Noktasi" },
        { "grounding", "Topraklanma" },
        { "panic_gasp", "Panik Soluğu" },
        { "clarity", "Berraklik" },
        { "nightmare_residue", "Kabus Yankisi" },
        { "ruins2", "Yeni harabe turleri" },
        { "moon_gaze", "Aya Bakis" },
        { "dusk_choir", "Gunbatimi Korosu" },
        { "night_marker", "Gece Isareti" },
        { "gift_at_dawn", "Safak Hediyesi" },
        { "eclipse_vigil", "Tutulma Nobeti" },
        { "ring_of_faces", "Yuzler Halkasi" },
        { "wall_of_faces", "Yuzler Duvari" },
        { "horizon_watchers", "Ufuk Gozculeri" },
        { "death_echo", "Olum Yankisi (coklu)" },
        { "distant_distress", "Uzak Sikinti (coklu)" },
        { "lost_letters", "Kayip Mektuplar" },
        { "ranks", "Unvanlar" },
        { "night_report", "Gece Raporu" },
        { "night_verse", "Gece Dizesi" }
    };

    public static void openMenu(Player player, int tries) {
        try {
            Map<String, Object> cfg = MainA.getConfig();
            Map<String, Object> en = MainA.cfgEnabled(cfg);
            Forms.Modal form = new Forms.Modal().title("Duskwalker Ayarlari");
            form.slider("Genel yogunluk", 0, 20, 1, Math.round(MainA.cfgIntensityMult(cfg) * 10));
            for (String[] t : MENU_TOGGLES) form.toggle(t[1], !Boolean.FALSE.equals(en.get(t[0])));
            form.show(player, res -> {
                try {
                    if (res == null) return;
                    if (res.canceled) {
                        if ("UserBusy".equals(res.cancelationReason) && tries < 40) Sys.runTimeout(() -> openMenu(player, tries + 1), 20);
                        return;
                    }
                    List<Object> v = res.formValues;
                    Map<String, Object> next = MainA.getConfig();
                    double mult = Json.num(v.get(0), 10) / 10;
                    next.put("intensityMult", mult);
                    Map<String, Object> nen = Json.asMap(next.get("enabled"));
                    if (nen == null) { nen = Json.newMap(); next.put("enabled", nen); }
                    for (int i = 0; i < MENU_TOGGLES.length; i++) nen.put(MENU_TOGGLES[i][0], Json.bool(v.get(i + 1), false));
                    MainA.saveConfig(next);
                    player.sendMessage("§7Ayarlar kaydedildi. Yogunluk: x" + String.format(Locale.ROOT, "%.1f", mult) + "§r");
                } catch (Throwable e) { /* yut */ }
            });
        } catch (Throwable e) { /* yut */ }
    }

    private static final String HELP_TEXT = "§6Duskwalker komutlari:§r\n§e!dwmenu§r ayar formu | §e!dwconfig§r yazili ayar | §e!sanity§r saglamlik | §e!journal§r gunluk | §e!evidence§r kanit | §e!phase§r faz | §e!dwstats§r istatistik | §e!dwach§r basarimlar | §e!v10§r yeni olaylar | §e!dwpreset§r zorluk | §e!dwlore§r notlar | §e!dwdiag§r tani | §e!moon§r ay takvimi | §e!night§r gece tipi | §e!dwcodex§r kodeks | §e!quest§r nobet | §e!noise§r gurultu | §e!dwcomfort§r rahatlik | §e!dwdread§r korku seviyesi | §e!dwgrave§r mezar | §e!dwperks§r yetenekler | §e!dwchronicle§r kronik | §e!dwforecast§r tahmin | §e!paradox§r paradoks (§e!paradox exit§r cikis) | §e!dwzones§r lanetli bolgeler | §e!dwoath§r yemin | §e!dwletters§r mektuplar | §e!dwgraph§r grafik | §e!dwrank§r unvan | §e!dwcompass§r pusula | §e!dwdossier§r dosya | §e!dwsanctuary§r siginak | §e!dwpast§r gecmis | §e!dwdescend§r derinlikler | §e!dwpact§r anlasma | §e!dwpay§r borc | §e!dwjournals§r gunlukler\n§7Test: !stalk !hunt !huntfast !twinhunt !jumpscare !possession !seep !watcher !echo !stolen !clock !mimic !homevisit !home§r";

    // ================================================================== KAYIT (ust duzey yan etkiler)
    private static boolean initialized = false;

    public static void init() {
        if (initialized) return;
        initialized = true;

        // ---- Bekci/Abla (Aylin) etkilesimi ----
        Events.After.playerInteractWithEntity.subscribe(ev -> {
            try {
                Entity target = ev.target;
                Player player = ev.player;
                if (target == null || !target.typeId().equals("duskwalker:aylin")) return;

                Object alreadyFreed = World.getDynamicProperty(SISTER_FREED);
                if (!MainA.truthy(alreadyFreed)) {
                    World.setDynamicProperty(SISTER_FREED, true);
                    try {
                        Vec3 loc = target.location();
                        Dimension dimension = target.dimension();
                        int fx = (int) Math.floor(loc.x()), fy = (int) Math.floor(loc.y()), fz = (int) Math.floor(loc.z());
                        dimension.runCommand("fill " + (fx - 2) + " " + (fy - 1) + " " + (fz - 2) + " " + (fx + 2) + " " + (fy + 2) + " " + (fz + 2) + " air replace iron_bars");
                    } catch (Throwable e) { /* yut */ }
                    String[] lines = {
                        "§dAylin§r: ...bir insan. Uzun zaman oldu.",
                        "§dAylin§r: O benim kardesim. Bir zamanlar sadece yalnizdi, ben de onu birakip gittim.",
                        "§dAylin§r: Beni burada tuttu ki kimse ona ulasmasin. Ama sen buldun.",
                        "§dAylin§r: Yanindan ayrilmayacagim artik. Onu asil durduracak sey... pismanlik degil, birlik."
                    };
                    sendLines(player, lines, 0, 45);
                } else if (!MainA.isDefeated() && MainA.getDay() >= 50) {
                    MainA.triggerFinale(player);
                } else {
                    // OZELLIK: AYLIN'E ADAK - elde soul torch ile tiklayinca guven artar
                    boolean gifted = false;
                    try {
                        ItemStack held = player.mainhand();
                        if (held != null && held.typeId.equals("minecraft:soul_torch")) {
                            Object tr = player.getDynamicProperty(MainA.AYLIN_TRUST);
                            double trust = (tr instanceof Number tn ? tn.doubleValue() : 0) + 1;
                            player.setDynamicProperty(MainA.AYLIN_TRUST, trust);
                            if (held.amount > 1) { held.amount -= 1; player.setMainhand(held); }
                            else { player.setMainhand(null); }
                            gifted = true;

                            if (trust == 3) {
                                try { player.sendMessage("§dAylin§r: Bunlari bana getirdigin icin tesekkur ederim..."); } catch (Throwable e) { /* yut */ }
                                try { player.sendMessage("§7(Aylin sana guveniyor...)§r"); } catch (Throwable e) { /* yut */ }
                            } else if (trust == 6) {
                                try { player.sendMessage("§dAylin§r: Aramizdaki bag guclendikce, o daha az korkutucu geliyor bana."); } catch (Throwable e) { /* yut */ }
                            } else if (trust >= 10 && !MainA.truthy(player.getDynamicProperty(MainA.AYLIN_BOND))) {
                                player.setDynamicProperty(MainA.AYLIN_BOND, true);
                                try {
                                    player.sendMessage("§dAylin§r: Artik seninle... her zaman. Korku seni eskisi kadar kolay yiyemeyecek.");
                                    player.sendMessage("§7(Aylin ile aranizdaki bag tamamlandi - korku olaylarinin etkisi kalici olarak hafifledi)§r");
                                } catch (Throwable e) { /* yut */ }
                            } else {
                                try { player.sendMessage("§dAylin§r: Tesekkur ederim. (" + (long) trust + "/10)"); } catch (Throwable e) { /* yut */ }
                            }
                        }
                    } catch (Throwable e) { /* yut */ }
                    if (!gifted) openAylinDialogue(player);
                }
            } catch (Throwable e) { /* yut */ }
        });

        // ---- ele gecirilenlerin parcacik / sure takibi ----
        Sys.runInterval(() -> {
            long tick = Sys.currentTick();
            for (int i = possessed.size() - 1; i >= 0; i--) {
                Possessed p = possessed.get(i);
                boolean valid = false;
                try { valid = p.entity.isValid(); } catch (Throwable e) { /* yut */ }
                if (!valid || tick > p.until) { possessed.remove(i); continue; }
                try {
                    Vec3 loc = p.entity.location();
                    p.entity.dimension().spawnParticle("minecraft:soul_particle", new Vec3(loc.x(), loc.y() + 1, loc.z()));
                } catch (Throwable e) { /* yut */ }
            }
        }, 10);

        // ---- 1) olum yankisi: yol tamponu ----
        Sys.runInterval(() -> {
            for (Player player : World.getPlayers()) {
                try {
                    Vec3 l = player.location();
                    String d = player.dimension().id();
                    List<PathPt> buf = pathBuffers.computeIfAbsent(player.id(), k -> new ArrayList<>());
                    if (!buf.isEmpty() && !buf.get(buf.size() - 1).d.equals(d)) buf.clear();
                    buf.add(new PathPt(r1(l.x()), r1(l.y()), r1(l.z()), d));
                    while (buf.size() > 24) buf.remove(0);
                } catch (Throwable e) { /* yut */ }
            }
        }, 10);

        Events.After.entityDie.subscribe(ev -> {
            try {
                Entity dead = ev.deadEntity;
                if (!(dead instanceof Player dp) || !dead.typeId().equals("minecraft:player")) return;
                List<PathPt> buf = pathBuffers.getOrDefault(dead.id(), new ArrayList<>());
                if (buf.size() >= 6) {
                    List<Object> pts = Json.newList();
                    for (PathPt q : buf) pts.add(Arrays.asList((Object) q.x, q.y, q.z));
                    Map<String, Object> rec = Json.newMap();
                    rec.put("d", buf.get(buf.size() - 1).d);
                    rec.put("n", 0.0);
                    rec.put("p", pts);
                    World.setDynamicProperty(ECHO_PREFIX + dp.name(), Json.stringify(rec));
                }
                pathBuffers.put(dead.id(), new ArrayList<>());
            } catch (Throwable e) { /* yut */ }
        });

        MainA.registerCreatureBehavior("duskwalker:echo", MainC::echoBehavior);

        // ---- 2) calinti kelimeler: sohbet kaydi ----
        Events.Before.chatSend.subscribe(ev -> {
            try {
                String msg = ev.message;
                if (msg == null || (!msg.isEmpty() && msg.charAt(0) == '!')) return;
                String text = msg.trim();
                if (text.length() < 6 || text.length() > 80) return;
                Player sender = ev.sender;
                Sys.run(() -> {
                    try {
                        List<Object> list = new ArrayList<>();
                        Object raw = sender.getDynamicProperty(STOLEN_LOG);
                        if (raw instanceof String s) {
                            List<Object> parsed = Json.asList(Json.parse(s));
                            if (parsed != null) list = parsed;
                        }
                        list.add(text);
                        sender.setDynamicProperty(STOLEN_LOG, Json.stringify(list.subList(Math.max(0, list.size() - 8), list.size())));
                    } catch (Throwable e) { /* yut */ }
                });
            } catch (Throwable e) { /* yut */ }
        });

        // ---- 3) gercek saat 03:33 ----
        Sys.runInterval(() -> {
            if (MainA.isDefeated()) return;
            if (!v99Enabled("clock")) return;
            RealClock c = getRealClock();
            if (c == null || c.h != 3 || c.m != 33) return;
            for (Player p : World.getPlayers()) {
                try {
                    if (c.key.equals(p.getDynamicProperty(CLOCK_DAY))) continue;
                    p.setDynamicProperty(CLOCK_DAY, c.key);
                    doClockEvent(p);
                } catch (Throwable e) { /* yut */ }
            }
        }, 100);

        // ---- 4) taklitci: insa takibi ----
        Events.After.playerPlaceBlock.subscribe(ev -> {
            try {
                Player p = ev.player;
                Block b = ev.block;
                if (b == null || MIMIC_EXCLUDE.matcher(b.typeId()).find()) return;
                long tick = Sys.currentTick();
                String d = p.dimension().id();
                Cluster cl = buildClusters.get(p.id());
                boolean far = cl != null && Math.sqrt(Math.pow(b.x() - cl.lx, 2) + Math.pow(b.y() - cl.ly, 2) + Math.pow(b.z() - cl.lz, 2)) > 14;
                if (cl == null || far || tick - cl.tick > 3600 || !cl.d.equals(d)) {
                    cl = new Cluster();
                    cl.lx = b.x(); cl.ly = b.y(); cl.lz = b.z(); cl.tick = tick; cl.d = d;
                    buildClusters.put(p.id(), cl);
                }
                if (cl.blocks.size() < 80) cl.blocks.add(new MBlock(b.x(), b.y(), b.z(), b.permutation()));
                cl.lx = b.x(); cl.ly = b.y(); cl.lz = b.z();
                cl.tick = tick;
                if (cl.blocks.size() >= 8) lastBuilds.put(p.id(), new ArrayList<>(cl.blocks));
            } catch (Throwable e) { /* yut */ }
        });

        Sys.runInterval(() -> {
            try {
                if (activeMimic == null) return;
                Dimension dimension;
                try { dimension = World.getDimension(activeMimic.dimId.replace("minecraft:", "")); } catch (Throwable e) { return; }
                long tick = Sys.currentTick();
                Player owner = null;
                for (Player p : World.getPlayers()) if (p.id().equals(activeMimic.ownerId)) { owner = p; break; }
                if (owner != null && owner.dimension().id().equals(activeMimic.dimId)) {
                    double d = dist2d(owner.location(), activeMimic.center);
                    if (d <= 22 && !activeMimic.seen) {
                        activeMimic.seen = true;
                        MainA.loseSanity(owner, 8);
                        MainA.addEvidence(owner, 1);
                        try { owner.sendMessage("§7(senin yaptigin sey... ama yanlis.)§r"); } catch (Throwable e) { /* yut */ }
                    }
                    if (d <= 7 && tick - activeMimic.placedTick > 100) { dissolveMimic(dimension); return; }
                }
                if (tick - activeMimic.placedTick > 14400) dissolveMimic(dimension);
            } catch (Throwable e) { /* yut */ }
        }, 20);

        // Sunucu yeniden basladiysa geride kalan taklit bloklarini temizle
        Sys.runInterval(() -> {
            try {
                if (activeMimic != null) return;
                Object raw = World.getDynamicProperty(MIMIC_LEFTOVER);
                if (!(raw instanceof String s)) return;
                Map<String, Object> rec = Json.asMap(Json.parse(s));
                Dimension dimension = World.getDimension(Json.str(rec.get("d"), OVERWORLD_ID).replace("minecraft:", ""));
                List<Object> remaining = new ArrayList<>();
                for (Object qo : Json.asList(rec.get("b"))) {
                    List<Object> q = Json.asList(qo);
                    try {
                        Block blk = dimension.getBlock(new Vec3(Json.num(q.get(0), 0), Json.num(q.get(1), 0), Json.num(q.get(2), 0)));
                        if (blk == null) { remaining.add(q); continue; }
                        if (blk.typeId().equals(q.get(3))) blk.setType("minecraft:air");
                    } catch (Throwable e) { remaining.add(q); }
                }
                if (remaining.isEmpty()) World.setDynamicProperty(MIMIC_LEFTOVER, null);
                else {
                    Map<String, Object> out = Json.newMap();
                    out.put("d", rec.get("d"));
                    out.put("b", remaining);
                    World.setDynamicProperty(MIMIC_LEFTOVER, Json.stringify(out));
                }
            } catch (Throwable e) { /* yut */ }
        }, 600);

        // ---- 5) ev ziyareti: Yer ustunde (Y>=40) ve Overworld'de gecirilen vakit 24x24'luk hucrelerde birikir ----
        Sys.runInterval(() -> {
            for (Player p : World.getPlayers()) {
                try {
                    if (!p.dimension().id().equals(OVERWORLD_ID)) continue;
                    Vec3 l = p.location();
                    if (l.y() < 40) continue;
                    Map<String, Object> cells = readHomeCells(p);
                    String key = (long) Math.floor(l.x() / 24) + "," + (long) Math.floor(l.z() / 24);
                    Map<String, Object> c = Json.asMap(cells.get(key));
                    if (c == null) {
                        c = Json.newMap();
                        c.put("s", 0.0); c.put("x", l.x()); c.put("y", l.y()); c.put("z", l.z());
                        cells.put(key, c);
                    }
                    double s = Json.num(c.get("s"), 0);
                    double w = 200 / (s + 200);
                    c.put("x", Json.num(c.get("x"), 0) + (l.x() - Json.num(c.get("x"), 0)) * w);
                    c.put("y", Json.num(c.get("y"), 0) + (l.y() - Json.num(c.get("y"), 0)) * w);
                    c.put("z", Json.num(c.get("z"), 0) + (l.z() - Json.num(c.get("z"), 0)) * w);
                    c.put("s", s + 200);
                    List<String> keys = new ArrayList<>(cells.keySet());
                    if (keys.size() > 10) {
                        keys.sort((a, b) -> Double.compare(Json.num(Json.asMap(cells.get(a)).get("s"), 0), Json.num(Json.asMap(cells.get(b)).get("s"), 0)));
                        cells.remove(keys.get(0));
                    }
                    p.setDynamicProperty(HOME_CELLS, Json.stringify(cells));
                } catch (Throwable e) { /* yut */ }
            }
        }, 200);

        // ---- Zamanlayici taramasi (tick butcesi ile) ----
        Runnable v99Scanner = MainA.<Player>createBudgetedScanner(
            () -> {
                if (MainA.isDefeated()) return new ArrayList<Player>();
                return new ArrayList<Player>(World.getPlayers());
            },
            MainC::v99Process
        );
        Sys.runInterval(v99Scanner, 20);

        // ---- v9.9 TEST KOMUTLARI ----
        Events.Before.chatSend.subscribe(ev -> {
            String msg = ev.message;
            List<String> cmds = Arrays.asList("!echo", "!stolen", "!clock", "!mimic", "!homevisit", "!home");
            if (!cmds.contains(msg)) return;
            ev.cancel = true;
            Player player = ev.sender;
            Sys.run(() -> {
                try {
                    if (msg.equals("!echo")) {
                        if (!startEcho(player, true)) player.sendMessage("§7Yanki icin yeterli yol kaydi yok. Biraz yuru ve tekrar dene.§r");
                    } else if (msg.equals("!stolen")) {
                        if (!doStolenWords(player)) player.sendMessage("§7Henuz kayitli mesajin yok. Sohbete birkac cumle yaz.§r");
                    } else if (msg.equals("!clock")) doClockEvent(player);
                    else if (msg.equals("!mimic")) {
                        if (!tryMimic(player)) player.sendMessage("§7Taklit icin once en az 8 blokluk bir yapi insa et (ve gece/acik alan gerekebilir).§r");
                    } else if (msg.equals("!homevisit")) {
                        Map<String, Object> home = getHome(player);
                        if (home == null) player.sendMessage("§7Henuz bir 'ev' belirlenmedi (bir yerde ~20 dk vakit gecir).§r");
                        else applyHomeVisit(player, home);
                    } else if (msg.equals("!home")) {
                        Map<String, Object> home = getHome(player);
                        if (home == null) player.sendMessage("§7Henuz bir 'ev' belirlenmedi.§r");
                        else player.sendMessage("§7Ev: " + Math.round(Json.num(home.get("x"), 0)) + ", " + Math.round(Json.num(home.get("y"), 0)) + ", " + Math.round(Json.num(home.get("z"), 0))
                            + " (skor " + Math.round(Json.num(home.get("s"), 0) / 20) + " sn)§r");
                    }
                } catch (Throwable e) { /* yut */ }
            });
        });

        // ---- v9.10 LAV ----
        Sys.runInterval(MainC::lavaTick, 10);

        // ---- Dusuk saglamlikta ekran titremesi / karanlik efekti ----
        Sys.runInterval(() -> {
            if (MainA.isDefeated()) return;
            if (MainA.cfgIsFalse(MainA.getConfig(), "sanity")) return;
            for (Player p : World.getPlayers()) {
                try {
                    double sanity = MainA.getSanity(p);
                    if (sanity < 25 && Math.random() < 0.35) {
                        try { p.cameraShake(0.04, 1.2, "rotational"); } catch (Throwable e) { /* yut */ }
                    }
                    if (sanity < 15 && Math.random() < 0.25) {
                        try { if (MainA.cfgIsTrue(MainA.getConfig(), "darkness_fx")) p.addEffect("darkness", 100, 0, false); } catch (Throwable e) { /* yut */ }
                    }
                } catch (Throwable e) { /* yut */ }
            }
        }, 200);

        // ---- Oyuncu cikinca bellek temizligi ----
        Events.After.playerLeave.subscribe(ev -> {
            try {
                String id = ev.playerId;
                pathBuffers.remove(id); echoLastTick.remove(id); buildClusters.remove(id); lastBuilds.remove(id);
                huntFogCount.remove(id); MainA.sleepWatchers.remove(id);
            } catch (Throwable e) { /* yut */ }
        });

        // ---- !dwmenu / !dwhelp ----
        Events.Before.chatSend.subscribe(ev -> {
            String msg = ev.message;
            if (!"!dwmenu".equals(msg) && !"!dwhelp".equals(msg)) return;
            ev.cancel = true;
            Player player = ev.sender;
            Sys.run(() -> {
                try {
                    if (msg.equals("!dwmenu")) {
                        player.sendMessage("§7Menu aciliyor... sohbeti kapat.§r");
                        openMenu(player, 0);
                    } else {
                        player.sendMessage(HELP_TEXT);
                    }
                } catch (Throwable e) { /* yut */ }
            });
        });

        // v14.1: Cep Saati menusu ana dosyadaki ayar formu ve gunluk kutuphanesi ile baglanir
        Controls.setMainHooks(new Controls.Hooks(p -> openMenu(p, 0), MainA::openJournalLibrary));
    }
}
