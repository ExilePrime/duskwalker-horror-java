package com.duskwalker.fabric;

import com.duskwalker.bedrock.BedrockException;
import com.duskwalker.bedrock.Container;
import com.duskwalker.bedrock.ItemStack;

/** net.minecraft.world.Container sarmalayicisi. limit: gorunen slot sayisi (oyuncu envanteri icin 36). */
public final class McContainer implements Container {
    private final net.minecraft.world.Container mc;
    private final int size;

    public McContainer(net.minecraft.world.Container mc, int limit) {
        this.mc = mc;
        this.size = Math.min(limit, mc.getContainerSize());
    }

    public McContainer(net.minecraft.world.Container mc) { this(mc, Integer.MAX_VALUE); }

    private void check(int slot) {
        if (slot < 0 || slot >= size) throw new BedrockException("slot out of range: " + slot);
    }

    @Override public int size() { return size; }

    @Override
    public ItemStack getItem(int slot) {
        check(slot);
        return ItemConv.fromMc(mc.getItem(slot));
    }

    @Override
    public void setItem(int slot, ItemStack item) {
        check(slot);
        if (item == null || item.amount <= 0) mc.setItem(slot, net.minecraft.world.item.ItemStack.EMPTY);
        else mc.setItem(slot, ItemConv.merge(mc.getItem(slot), item));
        mc.setChanged();
    }

    @Override
    public ItemStack addItem(ItemStack item) {
        if (item == null || item.amount <= 0) return null;
        net.minecraft.world.item.ItemStack add = ItemConv.toMc(item);
        int remaining = item.amount;
        int max = add.getMaxStackSize();
        // once ayni turdeki yiginlari doldur
        for (int i = 0; i < size && remaining > 0; i++) {
            net.minecraft.world.item.ItemStack ex = mc.getItem(i);
            if (ex.isEmpty() || ex.getCount() >= ex.getMaxStackSize()) continue;
            if (!net.minecraft.world.item.ItemStack.isSameItemSameComponents(ex, add)) continue;
            int can = Math.min(remaining, ex.getMaxStackSize() - ex.getCount());
            ex.grow(can);
            remaining -= can;
        }
        // sonra bos slotlar
        for (int i = 0; i < size && remaining > 0; i++) {
            if (!mc.getItem(i).isEmpty()) continue;
            int put = Math.min(remaining, max);
            net.minecraft.world.item.ItemStack n = add.copy();
            n.setCount(put);
            mc.setItem(i, n);
            remaining -= put;
        }
        mc.setChanged();
        if (remaining <= 0) return null;
        ItemStack rest = item.copy();
        rest.amount = remaining;
        return rest;
    }

    @Override
    public int emptySlotsCount() {
        int n = 0;
        for (int i = 0; i < size; i++) if (mc.getItem(i).isEmpty()) n++;
        return n;
    }
}
