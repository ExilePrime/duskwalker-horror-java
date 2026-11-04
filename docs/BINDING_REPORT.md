# Bolum 2 raporu - Platform (com.duskwalker.fabric)

Derleme yapilamadi (Maven/Mojang yok); javac ile yalniz bagimlilik kaynakli hatalar goruldu. API adlari referanslar + bellek. Belirsizler: `docs/UNVERIFIED_API.md`.

## Dosyalar
* `DuskwalkerMod` (main entry): payload/ses/varlik kaydi, `Platform.install(new FabricPlatform())`, `EventHooks.register()`, SERVER_STARTED -> `DynData.load` + `Mod.init()`, END_SERVER_TICK -> `Wrap.trackPlayers()` + `Sys.tick()`, SERVER_STOPPING -> leave olaylari, `Sys.reset()`, dinleyici temizligi, `Mod.done` sifirlama (yansima), SERVER_STOPPED -> temizlik.
* `FabricPlatform` (Platform): dunya, zaman (`day = dayTime/24000+1`), ay evresi, gamerule (Bedrock adi -> GameRules), broadcast, runServerCommand, formlar.
* `Wrap` (fabrika, sunucu referansi, oyuncu hizi takibi), `McEntity`, `McPlayer`, `McDimension`, `McBlock`, `McPerm`, `McContainer` (UUID/konum esitligi, gecersizde BedrockException, SHIM: isFlying/setSelectedSlotIndex uygulandi).
* `IdMap` (blok, esya, varlik, parcacik, ses, efekt, hasar, boyut), `ItemConv` (CUSTOM_NAME/LORE, § ve ozel isaretler aynen), `DynData` (SavedData, "s:/d:/b:" onekli NBT), `CommandTranslator`, `FormGui`, `EventHooks`.
* Mixin: `mixin/BlockItemMixin` (playerPlaceBlock), `mixin/ExplosionMixin` (explosion); `duskwalker.mixins.json`'a kayitli.
* `docs/ID_COVERAGE.md`: scriptlerde gecen 85 minecraft:* kimligi + 44 ses + efekt/hasar kontrol listesi.

## Eslemeler / kararlar
* Komutlar: fill (durum cevirisi, `replace`, 32768 blok siniri icin otomatik bolme), setblock, `replaceitem block ... slot.container N` -> `item replace block ... container.N with`, function (`duskwalker/x` -> `duskwalker:duskwalker/x`), execute (positioned/in/if block ... run), summon, gamerule, effect, time; digerleri aynen. Kaynak: izin 4, sessiz.
* Blok durumlari: upper_block_bit->half, open_bit->open, direction/facing_direction->facing (yon tablolari), vb.
* Formlar: Action = sandik menusu (govde = kitap, dugme = kagit, 54 slotu asarsa sayfalama); Modal = toggle (yesil/kirmizi cam), slider (-10/-1/+1/+10), dropdown (tikla/sag tikla), Kaydet/Iptal. Cevaplar sonraki tikte verilir. Menudeyken `UserBusy`.
* playerSpawn: JOIN'de initialSpawn=true (sonraki tik), AFTER_RESPAWN'da false. playerLeave before = DISCONNECT, after = sonraki tik.
* Dinamik ozellikler varlik oldugunde 200 tik sonra silinir; `remove()` aninda siler.

## Bilinen bosluklar
* Modal `textField` GUI'de duzenlenemez (varsayilan deger doner).
* playerInteractWithBlock Java'da iptal edilemez/etkisizdir; olay sonraki tikte tetiklenir.
* Bedrock facing/direction tablolari kapi/cit kapisi icin; diger bloklarda yaklasik.
* Parcacik `blue_flame_particle` -> soul_fire_flame, `redstone_ore_dust_particle` -> kirmizi toz (yaklasik).
* `getTopmostBlock` WORLD_SURFACE kullanir (sivi dahil olabilir).
* Mixin hedef adlari (Explosion.finalizeExplosion, alan `level`) 1.21.1'de dogrulanmali.
* Mod.done yansima ile sifirlanir; sifirlanamazsa mod JVM basina bir kez baslar.
