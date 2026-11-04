package com.duskwalker.fabric;

import com.duskwalker.bedrock.BedrockException;
import com.duskwalker.bedrock.Block;
import com.duskwalker.bedrock.Container;
import com.duskwalker.bedrock.Dimension;
import com.duskwalker.bedrock.Perm;
import com.duskwalker.bedrock.Vec3;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.entity.SignText;
import net.minecraft.world.level.block.state.BlockState;

/** Bedrock Block = (ServerLevel, BlockPos) cifti; durum her okumada canli okunur. */
public final class McBlock implements Block {
    private final ServerLevel level;
    private final BlockPos pos;

    public McBlock(ServerLevel level, BlockPos pos) {
        this.level = level;
        this.pos = pos;
    }

    private BlockState state() { return level.getBlockState(pos); }

    @Override public String typeId() { return IdMap.blockToBedrock(state()); }
    @Override public boolean isAir() { return state().isAir(); }
    @Override public int x() { return pos.getX(); }
    @Override public int y() { return pos.getY(); }
    @Override public int z() { return pos.getZ(); }
    @Override public Vec3 location() { return new Vec3(pos.getX(), pos.getY(), pos.getZ()); }
    @Override public Dimension dimension() { return Wrap.dim(level); }

    @Override
    public void setType(String typeId) {
        net.minecraft.world.level.block.Block b = IdMap.block(typeId);
        if (b == null) throw new BedrockException("unknown block: " + typeId);
        level.setBlock(pos, b.defaultBlockState(), net.minecraft.world.level.block.Block.UPDATE_ALL);
    }

    @Override public Perm permutation() { return new McPerm(state()); }

    @Override
    public void setPermutation(Perm perm) {
        if (!(perm instanceof McPerm m)) throw new BedrockException("foreign Perm implementation");
        level.setBlock(pos, m.state(), net.minecraft.world.level.block.Block.UPDATE_ALL);
    }

    // ---------------------------------------------------------------- tabela
    @Override
    public boolean setSignText(String text) {
        BlockEntity be = level.getBlockEntity(pos);
        if (!(be instanceof SignBlockEntity sign)) return false;
        String[] lines = (text == null ? "" : text).split("\n", -1);
        SignText st = sign.getFrontText();
        for (int i = 0; i < 4; i++) {
            st = st.setMessage(i, Component.literal(i < lines.length ? lines[i] : ""));
        }
        sign.setText(st, true);
        sign.setChanged();
        level.sendBlockUpdated(pos, state(), state(), net.minecraft.world.level.block.Block.UPDATE_ALL);
        return true;
    }

    @Override
    public String getSignText() {
        BlockEntity be = level.getBlockEntity(pos);
        if (!(be instanceof SignBlockEntity sign)) return null;
        Component[] msgs = sign.getFrontText().getMessages(false);
        int last = msgs.length - 1;
        while (last >= 0 && msgs[last].getString().isEmpty()) last--;
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i <= last; i++) {
            if (i > 0) sb.append('\n');
            sb.append(msgs[i].getString());
        }
        return sb.toString();
    }

    @Override
    public Container container() {
        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof net.minecraft.world.Container c) return new McContainer(c);
        return null;
    }

    @Override public boolean equals(Object o) {
        return o instanceof McBlock b && b.pos.equals(pos) && b.level.dimension().equals(level.dimension());
    }
    @Override public int hashCode() { return pos.hashCode() * 31 + level.dimension().hashCode(); }
}
