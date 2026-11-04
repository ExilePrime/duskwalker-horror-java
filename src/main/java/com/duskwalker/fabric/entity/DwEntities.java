package com.duskwalker.fabric.entity;

import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SpawnEggItem;

/** 6 EntityType + spawn egg'ler + yaratici sekme. Kimlikler: duskwalker:duskwalker|echo|seep|watcher|effigy|aylin. */
public final class DwEntities {
    public static final EntityType<DwEntity> DUSKWALKER = create(DwKind.DUSKWALKER);
    public static final EntityType<DwEntity> ECHO = create(DwKind.ECHO);
    public static final EntityType<DwEntity> SEEP = create(DwKind.SEEP);
    public static final EntityType<DwEntity> WATCHER = create(DwKind.WATCHER);
    public static final EntityType<DwEntity> EFFIGY = create(DwKind.EFFIGY);
    public static final EntityType<DwEntity> AYLIN = create(DwKind.AYLIN);

    public static final ResourceKey<CreativeModeTab> TAB_KEY = ResourceKey.create(BuiltInRegistries.CREATIVE_MODE_TAB.key(),
            ResourceLocation.fromNamespaceAndPath("duskwalker", "duskwalker"));

    private static Item duskwalkerEgg, echoEgg, seepEgg, watcherEgg, effigyEgg;
    private static boolean registered;

    private DwEntities() {
    }

    private static EntityType<DwEntity> create(DwKind kind) {
        EntityType.Builder<DwEntity> b = EntityType.Builder.<DwEntity>of((type, level) -> new DwEntity(type, level, kind), MobCategory.MISC)
                .sized(kind.width, kind.height)
                .clientTrackingRange(10)
                .updateInterval(2);
        if (kind.fireImmune) b.fireImmune();
        return b.build(kind.id);
    }

    public static EntityType<DwEntity> byKind(DwKind kind) {
        return switch (kind) {
            case DUSKWALKER -> DUSKWALKER;
            case ECHO -> ECHO;
            case SEEP -> SEEP;
            case WATCHER -> WATCHER;
            case EFFIGY -> EFFIGY;
            case AYLIN -> AYLIN;
        };
    }

    private static void reg(DwKind kind) {
        EntityType<DwEntity> t = byKind(kind);
        Registry.register(BuiltInRegistries.ENTITY_TYPE, ResourceLocation.fromNamespaceAndPath("duskwalker", kind.id), t);
        FabricDefaultAttributeRegistry.register(t, DwEntity.createAttributes(kind));
    }

    private static Item egg(DwKind kind) {
        Item item = new SpawnEggItem(byKind(kind), kind.eggBase, kind.eggOverlay, new Item.Properties());
        return Registry.register(BuiltInRegistries.ITEM, ResourceLocation.fromNamespaceAndPath("duskwalker", kind.id + "_spawn_egg"), item);
    }

    public static void register() {
        if (registered) return;
        registered = true;
        for (DwKind k : DwKind.values()) reg(k);
        duskwalkerEgg = egg(DwKind.DUSKWALKER);
        echoEgg = egg(DwKind.ECHO);
        seepEgg = egg(DwKind.SEEP);
        watcherEgg = egg(DwKind.WATCHER);
        effigyEgg = egg(DwKind.EFFIGY);
        CreativeModeTab tab = FabricItemGroup.builder()
                .title(Component.translatable("itemGroup.duskwalker"))
                .icon(() -> new ItemStack(duskwalkerEgg))
                .displayItems((params, output) -> {
                    output.accept(duskwalkerEgg);
                    output.accept(echoEgg);
                    output.accept(seepEgg);
                    output.accept(watcherEgg);
                    output.accept(effigyEgg);
                })
                .build();
        Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB, TAB_KEY, tab);
    }
}
