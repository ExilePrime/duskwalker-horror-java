package com.duskwalker.fabric;

import com.duskwalker.bedrock.BedrockException;
import com.duskwalker.bedrock.Container;
import com.duskwalker.bedrock.ItemStack;
import com.duskwalker.bedrock.Opts;
import com.duskwalker.bedrock.Player;
import com.duskwalker.bedrock.Vec3;
import com.duskwalker.fabric.net.DwNetwork;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetActionBarTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetCarriedItemPacket;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

/** Bedrock Player -> ServerPlayer sarmalayicisi. */
public final class McPlayer extends McEntity implements Player {
    private final String name;

    public McPlayer(ServerPlayer p) {
        super(p);
        this.name = p.getGameProfile().getName();
    }

    public ServerPlayer sp() {
        net.minecraft.world.entity.Entity e = mc();
        if (e instanceof ServerPlayer p) return p;
        throw new BedrockException("Player is not valid: " + uuid);
    }

    @Override public String name() { return name; }

    @Override
    public String nameTag() { return name; }

    @Override
    public String getGameMode() { return sp().gameMode.getGameModeForPlayer().getName(); }

    @Override
    public void sendMessage(String text) { sp().sendSystemMessage(Component.literal(text)); }

    // ---------------------------------------------------------------- ses
    @Override
    public void playSound(String soundId, Opts.Sound opts) {
        SoundEvent ev = IdMap.sound(soundId);
        if (ev == null) return;
        ServerPlayer p = sp();
        float vol = opts == null ? 1.0F : (float) opts.volume;
        float pitch = opts == null ? 1.0F : (float) opts.pitch;
        p.connection.send(new ClientboundSoundPacket(IdMap.soundHolder(ev), IdMap.soundSource(soundId),
                p.getX(), p.getY(), p.getZ(), vol, pitch, p.level().getRandom().nextLong()));
    }

    // ---------------------------------------------------------------- ekran
    @Override
    public void setActionBar(String text) {
        sp().connection.send(new ClientboundSetActionBarTextPacket(Component.literal(text)));
    }

    @Override
    public void setTitle(String title, Opts.Title opts) {
        ServerPlayer p = sp();
        Opts.Title o = opts == null ? new Opts.Title() : opts;
        p.connection.send(new ClientboundSetTitlesAnimationPacket(o.fadeInDuration, o.stayDuration, o.fadeOutDuration));
        if (o.subtitle != null && !o.subtitle.isEmpty()) {
            p.connection.send(new ClientboundSetSubtitleTextPacket(Component.literal(o.subtitle)));
        }
        p.connection.send(new ClientboundSetTitleTextPacket(Component.literal(title == null ? "" : title)));
    }

    @Override
    public void cameraFade(Opts.Fade f) {
        if (f == null) return;
        DwNetwork.sendFade(sp(), f.red, f.green, f.blue, f.fadeInTime, f.holdTime, f.fadeOutTime);
    }

    @Override
    public void cameraShake(double strength, double seconds, String type) {
        DwNetwork.sendShake(sp(), strength, seconds, "positional".equals(type));
    }

    @Override
    public void fogPush(String fogId, String userKey) { DwNetwork.sendFogPush(sp(), fogId, userKey); }

    @Override
    public void fogPop(String userKey) { DwNetwork.sendFogPop(sp(), userKey); }

    // ---------------------------------------------------------------- envanter
    @Override
    public Container inventory() { return new McContainer(sp().getInventory(), 36); }

    @Override
    public ItemStack mainhand() { return ItemConv.fromMc(sp().getMainHandItem()); }

    @Override
    public void setMainhand(ItemStack item) {
        ServerPlayer p = sp();
        net.minecraft.world.item.ItemStack st = item == null || item.amount <= 0
                ? net.minecraft.world.item.ItemStack.EMPTY
                : ItemConv.merge(p.getMainHandItem(), item);
        p.setItemInHand(InteractionHand.MAIN_HAND, st);
    }

    @Override
    public int selectedSlotIndex() { return sp().getInventory().selected; }

    @Override
    public void setSelectedSlotIndex(int slot) {
        ServerPlayer p = sp();
        int s = Math.max(0, Math.min(8, slot));
        p.getInventory().selected = s;
        p.connection.send(new ClientboundSetCarriedItemPacket(s));
    }

    @Override
    public boolean isFlying() { return sp().getAbilities().flying; }

    // ---------------------------------------------------------------- dunya
    @Override
    public Vec3 getSpawnPoint() {
        BlockPos pos = sp().getRespawnPosition();
        return pos == null ? null : new Vec3(pos.getX(), pos.getY(), pos.getZ());
    }

    @Override
    public Opts.BlockHit getBlockFromViewDirection(double maxDistance) {
        ServerPlayer p = sp();
        HitResult hr = p.pick(maxDistance, 1.0F, false);
        if (hr.getType() != HitResult.Type.BLOCK || !(hr instanceof BlockHitResult bhr)) return null;
        Opts.BlockHit out = new Opts.BlockHit();
        out.block = Wrap.block((net.minecraft.server.level.ServerLevel) p.level(), bhr.getBlockPos());
        String d = bhr.getDirection().getName();
        out.face = Character.toUpperCase(d.charAt(0)) + d.substring(1);
        return out;
    }

    @Override
    public boolean runCommand(String command) {
        ServerPlayer p = sp();
        return CommandTranslator.run(p.getServer(), p.createCommandSourceStack(), command);
    }
}
