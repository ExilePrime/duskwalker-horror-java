package com.duskwalker.bedrock;

import java.util.ArrayList;
import java.util.List;

/**
 * Bedrock ItemStack karsiligi (degisebilir veri sinifi). Gercek Minecraft ItemStack'e cevirme isi
 * binding (fabric paketi) tarafindadir. typeId Bedrock kimligi ("minecraft:paper").
 */
public final class ItemStack {
    public String typeId;
    public int amount;
    public String nameTag;                       // null = ozel isim yok
    private List<String> lore = new ArrayList<>();

    public ItemStack(String typeId) { this(typeId, 1); }
    public ItemStack(String typeId, int amount) { this.typeId = typeId; this.amount = amount; }

    public List<String> getLore() { return new ArrayList<>(lore); }
    public void setLore(List<String> lines) { this.lore = lines == null ? new ArrayList<>() : new ArrayList<>(lines); }
    public void setLore(String... lines) { this.lore = new ArrayList<>(List.of(lines)); }

    public ItemStack copy() {
        ItemStack c = new ItemStack(typeId, amount);
        c.nameTag = nameTag;
        c.lore = new ArrayList<>(lore);
        return c;
    }
}
