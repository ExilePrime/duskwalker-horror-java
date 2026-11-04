package com.duskwalker.fabric;

import java.util.ArrayList;
import java.util.List;

import com.duskwalker.bedrock.BedrockException;

import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.component.ItemLore;

/**
 * Script ItemStack (typeId, amount, nameTag, lore) <-> Minecraft ItemStack (CUSTOM_NAME / LORE bilesenleri).
 * Metinler (§ renk kodlari, sifir genislikli isaretler dahil) Component.literal icinde aynen saklanir.
 */
public final class ItemConv {
    private ItemConv() {}

    private static Component name(String s) {
        return Component.literal(s).withStyle(st -> st.withItalic(false));
    }

    /** Yeni Minecraft ItemStack olusturur. Bilinmeyen esyada BedrockException. */
    public static net.minecraft.world.item.ItemStack toMc(com.duskwalker.bedrock.ItemStack s) {
        Item item = IdMap.item(s.typeId);
        if (item == null) throw new BedrockException("unknown item: " + s.typeId);
        net.minecraft.world.item.ItemStack st = new net.minecraft.world.item.ItemStack(item, 1);
        st.setCount(Math.max(1, Math.min(s.amount, st.getMaxStackSize())));
        applyMeta(st, s);
        return st;
    }

    private static void applyMeta(net.minecraft.world.item.ItemStack st, com.duskwalker.bedrock.ItemStack s) {
        if (s.nameTag != null) st.set(DataComponents.CUSTOM_NAME, name(s.nameTag));
        else st.remove(DataComponents.CUSTOM_NAME);
        List<String> lore = s.getLore();
        if (lore.isEmpty()) st.remove(DataComponents.LORE);
        else {
            List<Component> lines = new ArrayList<>();
            for (String l : lore) lines.add(Component.literal(l));
            st.set(DataComponents.LORE, new ItemLore(lines));
        }
    }

    /**
     * Mevcut yigin ayni turdeyse onun bilesenlerini (buyu, dayaniklilik ...) koruyarak ad/lore/miktari uygular;
     * degilse yeni yigin olusturur. (getItem -> degistir -> setItem dongusunde veri kaybini onler.)
     */
    public static net.minecraft.world.item.ItemStack merge(net.minecraft.world.item.ItemStack existing, com.duskwalker.bedrock.ItemStack s) {
        Item item = IdMap.item(s.typeId);
        if (item == null) throw new BedrockException("unknown item: " + s.typeId);
        if (existing != null && !existing.isEmpty() && existing.getItem() == item) {
            net.minecraft.world.item.ItemStack c = existing.copy();
            c.setCount(Math.max(1, Math.min(s.amount, c.getMaxStackSize())));
            applyMeta(c, s);
            return c;
        }
        return toMc(s);
    }

    /** Bos yigin icin null doner. */
    public static com.duskwalker.bedrock.ItemStack fromMc(net.minecraft.world.item.ItemStack st) {
        if (st == null || st.isEmpty()) return null;
        com.duskwalker.bedrock.ItemStack out = new com.duskwalker.bedrock.ItemStack(IdMap.itemToBedrock(st.getItem()), st.getCount());
        Component n = st.get(DataComponents.CUSTOM_NAME);
        if (n != null) out.nameTag = n.getString();
        ItemLore lore = st.get(DataComponents.LORE);
        if (lore != null) {
            List<String> lines = new ArrayList<>();
            for (Component c : lore.lines()) lines.add(c.getString());
            out.setLore(lines);
        }
        return out;
    }
}
