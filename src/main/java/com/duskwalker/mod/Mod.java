package com.duskwalker.mod;

/** Mod giris noktasi: scripts/main.js + v10/index.js yukleme sirasinin Java karsiligi. */
public final class Mod {
    private Mod() {}
    private static boolean done = false;

    /** Platform kurulduktan sonra (Platform.install) bir kez cagrilir. */
    public static void init() {
        if (done) return;
        done = true;
        // v10/index.js sirasi (Core dahil)
        Core.init();
        EventsAudio.init();
        EventsWorld.init();
        LightRefuge.init();
        Atmosphere.init();
        Stats.init();
        Commands.init();
        Presets.init();
        Lore.init();
        EventsExtra.init();
        Diag.init();
        Nights.init();
        Weeping.init();
        Haunt.init();
        Mind.init();
        Noise.init();
        Quests.init();
        Dreams.init();
        Oracle.init();
        Codex.init();
        Comfort.init();
        Adaptive.init();
        Graves.init();
        Vision.init();
        Ruins.init();
        House.init();
        Storm.init();
        Ambience2.init();
        Perks.init();
        Chronicle.init();
        Buddy.init();
        Forecast.init();
        Hints.init();
        Procession.init();
        HauntedZones.init();
        Paradox.init();
        Uncanny.init();
        Heart.init();
        Oaths.init();
        Omens.init();
        Psyche.init();
        Ruins2.init();
        Sky.init();
        Crowd.init();
        Extras.init();
        Learn.init();
        Sanctuary.init();
        PastSelves.init();
        Depths.init();
        Pacts.init();
        Controls.init();
        // main.js (import "./v10/index.js"'ten SONRA calisir)
        MainA.init();
        MainB.init();
        MainC.init();
    }
}
