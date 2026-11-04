package com.duskwalker.bedrock;

/** Envanter / sandik kabi (player.getComponent("minecraft:inventory").container karsiligi). */
public interface Container {
    int size();
    /** Bos slot icin null doner. Donen nesne bir KOPYADIR; degistirirsen setItem ile geri yaz. */
    ItemStack getItem(int slot);
    /** item == null slotu bosaltir. */
    void setItem(int slot, ItemStack item);
    /** Sigmayan kisim varsa onu dondurur, hepsi sigdiysa null. */
    ItemStack addItem(ItemStack item);
    int emptySlotsCount();
}
