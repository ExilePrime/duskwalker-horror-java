# Bolum 1 raporu - Entities + Net + Client

Derleme yapilamadi; API adlari yerel referanslar (fabric-docs 1.21.1, fabric-api, yarn imzalari) ve bellekteki 1.21.1 Mojang adlariyla yazildi. Dogrulanamayanlar `docs/UNVERIFIED_API.md` icinde.

## Dosyalar
Ortak (`src/main/java/com/duskwalker/fabric`)
* `entity/DwKind` - 6 turun sabit ozellikleri (boyut, scale, saglik, hiz, isim, bakis, despawn, zamanlayici).
* `entity/DwEntity` (PathfinderMob + `DwEntityApi`), `entity/DwEntities` (6 EntityType, `DUSKWALKER|ECHO|SEEP|WATCHER|EFFIGY|AYLIN`, `register()`, 5 spawn egg, yaratici sekme `itemGroup.duskwalker`),
  `entity/DwSounds` (`register()`, `byBedrockId`, sabitler HEARTBEAT..RING), `entity/DwMeleeGoal`, `entity/DwEntityApi` (onceden vardi).
* `net/DwNetwork` (`registerPayloads()` tekrar-guvenli, `sendFade/sendShake/sendFogPush/sendFogPop`; ic record payload'lar).
* Kaynaklar: `assets/duskwalker/models/item/*_spawn_egg.json` (5 adet, template_spawn_egg).

Istemci (`src/client/java/com/duskwalker/fabric/client`)
* `DuskwalkerClient`, `DwRenderer`, `DwModel`, `DwGeometry`, `DwAnimations`, `fx/DwClock|DwFade|DwShake|DwFog`, `mixin/CameraMixin`, `mixin/FogRendererMixin` (`duskwalker.client.mixins.json`'a kayitli).

## Eslemeler
* bp ozellikleri: collision_box -> `sized`, minecraft:scale -> `Attributes.SCALE`, health/movement/knockback_resistance -> nitelikler, pushable false -> `isPushable=false` + piston IGNORE,
  damage_sensor -> `isInvulnerableTo` (yalniz Duskwalker LAVA gecer; /kill, void ve yaratici oyuncu gecer), her turde dusme hasari/ses/adim sesi kapali.
* Isim etiketi always_show: echo/aylin; digerleri vanilla davranisi.
* Despawn: duskwalker/echo/seep/watcher vanilla `removeWhenFarAway=true` (Bedrock despawn_from_distance ile ayni: 32/128 blok mantigi); Effigy ve Aylin kalici. Natural spawn yok, MobCategory.MISC (barisci modda silinmez).
* Av: `hunt_start` hiz 0.34, carpan 1.3; `hunt_start_fast` 0.52 / 1.7; saldiri 6, `NearestAttackableTargetGoal<Player>` (gorus gerekmez, FOLLOW_RANGE 48), `DwMeleeGoal` (reach 1.3), `OpenDoorGoal`. `hunt_end` hepsini kaldirir, hizi 0.3'e dondurur. Av modu NBT'de saklanir. Su uzerinde yol: PathType.WATER malus 0, kapi acma navigasyonda acik.
* `vanish` = discard (tum turler). `skin_1..4` Effigy. Effigy 150 tik sonra kendini siler (sayac NBT'de).
* Ozellikler: `SynchedEntityData` (variant 0..2, dominant, skin 0..4), NBT'ye yazilir; seep varsayilan variant 1, watcher 2. Turde olmayan ozellik: set yoksayilir, get null.
* `dwPlayAnimation("...attack")` -> entity event 99 -> istemci 1.1 sn saldiri animasyonu. Duskwalker dogal saldirisi (`doHurtTarget`) da animasyonu tetikler.
* Model: humanoid.custom (Bedrock standart oyuncu duzeni + hat), elongated, hunched tek agacta; Bedrock->Java donusu x,z ayni, y->24-y, pivotlar ebeveyne goreli, kup `py-oy-sy`, derece->radyan ayni isaret; 64x64 UV mantigi 256x256 dokuda normalize.
* Animasyon: keyframe lerp, anim toplamsal (Bedrock gibi), locomotion durum makinesi 0.05/0.45 esikleri; `query.modified_move_speed` = Java `limbSwingAmount` (walkAnimation hizi) varsayildi. Aylin/Effigy yalniz idle_sway.
* Render: variant -> grup, dominant -> `duskwalker_dominant.png`, Echo `entityTranslucent`, Effigy skin dokulari, Aylin `aylin.png`.
* Efektler: fade (HudRenderCallback, z=1000 fill), shake (Camera.setup mixin; rotational derece = strength*2.5, positional blok = strength*0.08, lineer sonum; sabitler DwShake'te), fog (yigin userKey bazli, hava, FogRenderer mixin; vanilla'dan yalniz sikilastirir, 1.5 sn gecis). Bilinmeyen fog kimligi yoksayilir.

## Bilinen farklar / bosluklar
* Fade renk degerleri Bedrock gibi 0..1'e KIRPILIR: `MainB`'deki `Fade.of(120,5,5,...)` beyaz flas verir (script kaynakli; kirmizi istenirse DwFade.start'ta >1 degerleri /255 yapilabilir).
* Duskwalker'in kafasi Java'da hedefe/oyuncuya doner (Bedrock modelinde kafa bakis animasyonu yok); Aylin'in bakis hedefi gorunur olsun diye eklendi.
* Fog yalniz hava (su/lav istege gore uygulanmadi); fog sinirlari vanilla'yi gevsetmez.
* Fade yalniz HUD'da cizilir; acik ekran (envanter) uzerinde gorunmez.
* Mixin alan/metot adlari (`fogRed/fogGreen/fogBlue`, `setupColor`, `setupFog`, `Camera.setup/setRotation/setPosition`) bellekten; derlemede/ilk calistirmada dogrulanmali.
* DwSounds `entity` paketinde (Platform `com.duskwalker.fabric.entity.DwSounds` olarak import etmeli).

## UNVERIFIED_API ozeti
Ana belirsizler: Mojang adlari (PathType, GroundPathNavigation.setCanOpenDoors, Attributes.SCALE, FogRenderer ozel alanlari, Camera metotlari, MobRenderer.getRenderType), `ServerPlayer.hasDisconnected`. Ayrintilar `docs/UNVERIFIED_API.md`.
