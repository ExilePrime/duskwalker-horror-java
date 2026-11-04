package com.duskwalker.fabric;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

import com.duskwalker.bedrock.BedrockException;
import com.duskwalker.bedrock.Dimension;
import com.duskwalker.bedrock.Forms;
import com.duskwalker.bedrock.Platform;
import com.duskwalker.bedrock.Player;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;

/** Bedrock Platform arayuzunun Fabric 1.21.1 uygulamasi. Sunucu referansi {@link Wrap} uzerindendir. */
public final class FabricPlatform implements Platform {
    private static final long DAY = 24000L;

    private MinecraftServer server() { return Wrap.server(); }

    private ServerLevel overworld() { return server().overworld(); }

    // ---------------------------------------------------------------- dunya
    @Override
    public List<Player> getPlayers() {
        List<Player> out = new ArrayList<>();
        for (ServerPlayer p : server().getPlayerList().getPlayers()) out.add(new McPlayer(p));
        return out;
    }

    @Override
    public Dimension getDimension(String id) {
        if (id == null) throw new BedrockException("dimension id is null");
        String n = IdMap.strip(id);
        ResourceKey<Level> key;
        switch (n) {
            case "overworld": key = Level.OVERWORLD; break;
            case "nether": case "the_nether": key = Level.NETHER; break;
            case "the_end": case "end": key = Level.END; break;
            default: {
                ResourceLocation rl = ResourceLocation.tryParse(id);
                if (rl == null) throw new BedrockException("invalid dimension id: " + id);
                key = ResourceKey.create(Registries.DIMENSION, rl);
            }
        }
        ServerLevel l = server().getLevel(key);
        if (l == null) throw new BedrockException("dimension not found: " + id);
        return new McDimension(l);
    }

    @Override public Object getWorldProperty(String key) { return DynData.get().getWorld(key); }

    @Override public void setWorldProperty(String key, Object value) { DynData.get().setWorld(key, value); }

    // ---------------------------------------------------------------- zaman
    @Override public int timeOfDay() { return (int) Math.floorMod(overworld().getDayTime(), DAY); }

    @Override
    public void setTimeOfDay(int t) {
        long cur = overworld().getDayTime();
        long target = Math.floorDiv(cur, DAY) * DAY + Math.floorMod((long) t, DAY);
        for (ServerLevel l : server().getAllLevels()) l.setDayTime(target);
    }

    @Override public long absoluteTime() { return overworld().getGameTime(); }

    @Override public int day() { return (int) (Math.floorDiv(overworld().getDayTime(), DAY) + 1); }

    @Override public int moonPhase() { return overworld().getMoonPhase(); }

    // ---------------------------------------------------------------- oyun kurallari
    private static GameRules.Key<GameRules.BooleanValue> ruleKey(String bedrockName) {
        switch (bedrockName.toLowerCase(Locale.ROOT)) {
            case "dodaylightcycle": return GameRules.RULE_DAYLIGHT;
            case "doweathercycle": return GameRules.RULE_WEATHER_CYCLE;
            case "domobspawning": return GameRules.RULE_DOMOBSPAWNING;
            case "mobgriefing": return GameRules.RULE_MOBGRIEFING;
            case "keepinventory": return GameRules.RULE_KEEPINVENTORY;
            case "dofiretick": return GameRules.RULE_DOFIRETICK;
            default: return null;
        }
    }

    @Override
    public boolean gameRule(String bedrockRuleName) {
        GameRules.Key<GameRules.BooleanValue> k = ruleKey(bedrockRuleName);
        return k != null && server().getGameRules().getBoolean(k);
    }

    @Override
    public void setGameRule(String bedrockRuleName, boolean value) {
        GameRules.Key<GameRules.BooleanValue> k = ruleKey(bedrockRuleName);
        if (k != null) server().getGameRules().getRule(k).set(value, server());
        else runServerCommand("gamerule " + bedrockRuleName + " " + value);
    }

    // ---------------------------------------------------------------- mesaj / komut
    @Override
    public void broadcast(String text) {
        server().getPlayerList().broadcastSystemMessage(Component.literal(text), false);
    }

    @Override
    public void runServerCommand(String command) {
        MinecraftServer s = server();
        CommandTranslator.run(s, CommandTranslator.dimensionSource(s.overworld()), command);
    }

    // ---------------------------------------------------------------- formlar
    @Override
    public void showActionForm(Player player, Forms.Action form, Consumer<Forms.ActionResponse> handler) {
        FormGui.showAction(player, form, handler);
    }

    @Override
    public void showModalForm(Player player, Forms.Modal form, Consumer<Forms.ModalResponse> handler) {
        FormGui.showModal(player, form, handler);
    }
}
