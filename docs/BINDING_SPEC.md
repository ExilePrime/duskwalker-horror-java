# Fabric 1.21.1 baglama (binding) katmani - gorev bolumu ve sozlesme

Hedef: Minecraft Java 1.21.1, Fabric Loader 0.20.1, Fabric API 0.116.17+1.21.1, **Mojang (resmi) esleme adlari**
(`net.minecraft.server.level.ServerPlayer`, `net.minecraft.world.entity.Entity`, `ResourceLocation`, `BuiltInRegistries` ...).
Java 21. Proje: `/home/claude/DuskwalkerHorror-Java` (Gradle/Loom sablonu hazir: build.gradle, gradle.properties,
`src/main/resources/fabric.mod.json`, `duskwalker.mixins.json`, `duskwalker.client.mixins.json`).
`splitEnvironmentSourceSets()` acik: `src/main/java` = ortak/sunucu kodu (client siniflarina ERISEMEZ),
`src/client/java` = yalniz istemci. Girisler: `com.duskwalker.fabric.DuskwalkerMod` (main) ve
`com.duskwalker.fabric.client.DuskwalkerClient` (client).

**Derleme bu ortamda YAPILAMAZ** (Maven/Mojang indirilemiyor). Bu yuzden her API cagrisini su yerel referanslarla DOGRULA:
* `/tmp/claude-0/-home-claude/2a5a248e-3afb-5be6-8eba-285f4b7ba2f6/scratchpad/ref/fabric-docs/reference/1.21.1/src/` (Mojang adli, 1.21.1 ornek kodlari: entity, networking payload, sound, event, mixin, block entity, command ...)
* `.../scratchpad/ref/fabric-api/` (Fabric API 1.21.1 kaynaklari: event arayuzleri, networking, rendering API'leri)
* `.../scratchpad/ref/ex1211/` (sablon)
Emin olmadigin her Minecraft sinifi/metodu icin once bu referanslarda `grep` ile gercek kullanim ara; bulamazsan en
muhafazakar/yaygin bilinen 1.21.1 Mojang adini kullan ve `docs/UNVERIFIED_API.md` dosyasina
`- Dosya: sinif.metot (neden emin degilim)` satiri ekle (append). Tahminle uydurma isim kullanma.
`com.duskwalker.bedrock.*` ve `com.duskwalker.mod.*` paketlerini DEGISTIRME (eksik arayuz metodu gerekirse
`docs/SHIM_REQUESTS.md`'ye bak, orada listelenenler dahil hepsi uygulanmalidir). Bedrock davranisini koru: kaynaklar
`/tmp/claude-0/-home-claude/2a5a248e-3afb-5be6-8eba-285f4b7ba2f6/scratchpad/addon/{bp,rp}/` (entities/*.json, rp/entity,
models, animations, render_controllers, fogs, sounds). Script tarafi `docs/PORTING_GUIDE.md`.

## Bolum 1 - "Entities+Client" ajani (paketler: com.duskwalker.fabric.entity, .net, .client)
Sunucu/ortak (src/main/java/com/duskwalker/fabric/entity, .../net):
* `DwEntities`: 6 EntityType, kimlikler **aynen** `duskwalker:duskwalker|echo|seep|watcher|effigy|aylin`
  (`/summon duskwalker:duskwalker` calismali; BP json'larindaki `is_summonable:true, is_spawnable:false`). Ozellikler bp/entities/*.json'dan:
  boyut (collision_box), `minecraft:scale`, saglik, hareket hizi (movement), `knockback_resistance 1`, itilemez
  (pushable false), `damage_sensor` (hepsi hasarsiz; yalnizca Duskwalker LAVA hasari alir), isim etiketi (always_show: echo/aylin true, digerleri false),
  `minecraft:timer` (Effigy 150 tick sonra `duskwalker:vanish`), echo/seep/watcher/duskwalker `despawn_from_distance`
  (uzaktaysa yok olabilir; ama Aylin ve scriptli varliklar icin kalicilik: gereksiz ise kapat, davranisi aciklayarak).
  Bedrock `minecraft:behavior.look_at_player/look_at_target/random_look_around` = ilgili LookAtPlayerGoal / RandomLookAroundGoal.
* Ortak taban sinif `DwEntity` (PathfinderMob alt sinifi) + arayuz:
  ```java
  public interface DwEntityApi {
      void dwTriggerEvent(String bedrockEvent);          // "duskwalker:hunt_start", "hunt_start_fast", "hunt_end", "vanish", "skin_1..4"
      void dwSetProperty(String name, Object value);     // "duskwalker:variant"(int) / "duskwalker:dominant"(bool) / "duskwalker:skin"(int)
      Object dwGetProperty(String name);
      void dwPlayAnimation(String animationName);        // "animation.duskwalker.attack" -> istemcide 1.1 sn saldiri animasyonu (entity event ile)
  }
  ```
  Ozellikler `SynchedEntityData` ile istemciye senkron ve NBT'ye kaydedilir. Olaylar: Duskwalker `hunt_start` = hareket 0.34 + saldiri 6 hasar + en yakin oyuncuyu
  hedefle (yaricap 48, gorus gerekmez) + MeleeAttackGoal(hiz carpani 1.3, menzil x1.3) + kapi acma; `hunt_start_fast` = hareket 0.52, hiz carpani 1.7; `hunt_end` = bu hedefleri/hareket artisini kaldir;
  `vanish` = aninda sil (discard). Vanish tum varliklarda calisir. Ayrica `can_climb`, `can_open_doors`, su uzerinde yol bulma (can_path_over_water). Oyuncuya vuran Duskwalker dogal
  Mob saldirisi kullanir (varsayilan hasar 6).
* Dogal doğum YOK. Spawn egg'ler: duskwalker (#0b0b12/#e6f0ff), echo (#3a4a66/#b8c8e8), seep (#1a3d2e/#8fffb0), watcher (#1a1a2e/#c9c9ff), effigy (#202028/#a0a0b0); Aylin'in yumurtasi yok. Yaratici sekme/ogeler icin bir ItemGroup (lang: itemGroup.duskwalker).
* `DwSounds`: res `assets/duskwalker/sounds.json` hazir; anahtarlar: heartbeat, breath, creak, drone, tick, whisper, ring (SoundEvent kaydi + `public static SoundEvent byBedrockId(String)` icin
  "duskwalker.heartbeat" -> duskwalker:heartbeat).
* `com.duskwalker.fabric.net.DwNetwork` (ayrica sunucu->istemci CustomPacketPayload'lar + codec kaydi, `registerPayloads()` her iki tarafta cagrilir):
  `static void sendFade(ServerPlayer p, double r, double g, double b, double inSec, double holdSec, double outSec)`,
  `sendShake(ServerPlayer p, double strength, double seconds, boolean positional)`, `sendFogPush(ServerPlayer p, String fogId, String userKey)`, `sendFogPop(ServerPlayer p, String userKey)`.
Istemci (src/client/java/com/duskwalker/fabric/client):
* `DuskwalkerClient` (ClientModInitializer): renderer kaydi, model katmanlari, payload alicilari, HUD, tik olaylari.
* Modeller: `rp/models/entity/duskwalker_variants.geo.json` 3 geometri: `humanoid.custom` (=player-biçimli 64x64 insan: `PlayerModel`/ayni UV duzeni) , `duskwalker.elongated`, `duskwalker.hunched`
  (kupleri/pivotlari/UV'leri JSON'dan birebir `LayerDefinition`/`MeshDefinition`'a cevir; Bedrock pivot (y yukari, +y) -> Java model uzayi (y asagi, 24 ofset) donusumunu dikkatle yap;
  texture 256x256, UV 64x64 mantigi normalize edilir). Render secimi: `variant` ozelligi (0 humanoid, 1 elongated, 2 hunched), doku `dominant` ? `duskwalker_dominant.png` : `duskwalker.png`.
  Echo: `duskwalker_echo.png` + saydam (alphablend) render; Effigy: `skin` 0..4 -> duskwalker / effigy_pale / effigy_robed / effigy_charred / effigy_drowned; Aylin: `aylin.png`, humanoid;
  Seep/Watcher: duskwalker dokulari, variant ozelligi ile (seep varsayilan variant 1, watcher 2).
* Animasyonlar `rp/animations/duskwalker.animation.json` (idle_sway, walk, run, attack) ve `animation_controllers` (idle/walk/run gecisleri `modified_move_speed` esikleri 0.05/0.45):
  kodla prosedurel uygula (keyframe lerp). Aylin/Effigy yalniz idle_sway. Attack animasyonu `dwPlayAnimation` ile tetiklenir (1.1 s).
* Efektler: `fade` (HudRenderCallback ile renkli tam ekran bindirme: in/hold/out), `shake` (kucuk, sonen kamera/bakis sarsintisi), `fog` (yigin userKey bazinda; en son push edilen gecerli;
  `docs/original/duskwalker_*.json` fog degerleri: air.fog_start/fog_end/fog_color; su/lav icin de var ama yalniz hava siz uygula) + istemci mixin (`duskwalker.client.mixins.json`'a ekle; sinifi
  `com.duskwalker.fabric.client.mixin` altina yaz) ile sis uzakligi/rengini uygula. fog kimlikleri: duskwalker:sanity_fog_1..4, night_fog, ash_fog, paradox_fog, hunt_fog.
* Ay dokusu zaten `assets/minecraft/textures/environment/moon_phases.png` olarak yerinde.

## Bolum 2 - "Platform" ajani (paket: com.duskwalker.fabric, src/main/java)
* `DuskwalkerMod implements ModInitializer`: `DwNetwork.registerPayloads()`, `DwEntities.register()`, `DwSounds.register()` (Bolum 1'in siniflari; imzalari yukaridaki gibi varsay),
  `Platform.install(new FabricPlatform(...))`, sunucu baslayinca `Mod.init()` (bir kez; `ServerLifecycleEvents.SERVER_STARTED`), her sunucu tikinde `Sys.tick()` (`ServerTickEvents.END_SERVER_TICK`),
  durdurulurken `Sys.reset()`.
* `FabricPlatform implements Platform` ve tum sarmalayicilar: `McEntity implements Entity`, `McPlayer implements Player`, `McDimension implements Dimension`, `McBlock implements Block`,
  `McPerm implements Perm`, `McContainer implements Container`; kimlik esitligi UUID ile (equals/hashCode id'ye gore). `bedrock` paketindeki TUM metotlar eksiksiz uygulanmali
  (`docs/SHIM_REQUESTS.md` dahil). Sarmalayicilar onbellekli (UUID->wrapper WeakHashMap) olabilir; gecersiz varlikta `BedrockException` firlat.
* `IdMap`: Bedrock<->Java kimlik cevirileri: blok ("minecraft:web"->cobweb, "standing_sign"->oak_sign vb.; scriptlerde kullanilan TUM kimlikleri `grep -rhoE '"minecraft:[a-z_]+"' src/main/java/com/duskwalker/mod | sort -u` ile cikar
  ve her birini karsila), esya, varlik turu ("minecraft:xp_orb"->experience_orb, "falling_block", "item"...), parcacik ("minecraft:large_smoke_particle"->large_smoke, soul_particle->soul, basic_smoke_particle->smoke,
  lava_particle->lava, note_particle->note, villager_happy->happy_villager, villager_angry->angry_villager, redstone_ore_dust_particle->dust/ uygun, water_splash_particle->splash ...),
  ses (Bedrock `mob.ghast.moan`, `ambient.cave`, `random.anvil_land`, `random.chestopen` vb.; scriptlerde gecen HER ses kimligi: `grep -rhoE 'playSound\\|playAt\\|playTo' ...` ile bul ve Java SoundEvent'e esle; `duskwalker.*` -> DwSounds),
  efekt ("slowness","darkness","blindness","speed","nausea","weakness"... -> MobEffects), hasar nedeni ("entityAttack","lava","fall","magic"...), blok durumu adlari (`open_bit`->open, `upper_block_bit`->half UPPER/LOWER, `direction`, `facing_direction` ...).
  Scriptlerde kullanilan her kimligin eslestigini dogrulayan bir kucuk kontrol listesi `docs/ID_COVERAGE.md` olarak yaz.
* `CommandTranslator`: Bedrock komutu -> Java komutu (`runCommand`): `fill/setblock/summon/tp/weather/execute positioned ... run function X/replaceitem block ... slot.container N item count/title/gamerule/...`
  Yalnizca scriptlerde gercekten kullanilan komutlari (grep `runCommand(` tum mod/ icinde) destekle; blok durumu `["upper_block_bit"=true]` -> Java `[half=upper]` cevirisi dahil; `function duskwalker/structure_01` -> `function duskwalker:duskwalker/structure_01`;
  komutlar sunucu kaynagi (permission 4, sessiz) ile calissin, `execute at/positioned` baglami boyut+konum ver.
* `DynData extends SavedData` (overworld DataStorage): UUID anahtarli varlik/oyuncu dinamik ozellikleri + dunya ozellikleri; deger String/Double/Boolean; `null` = sil. Kalici (NBT).
* `EventHooks`: Fabric API olaylarindan `Events.*` tetikle: chatSend (once: `ServerMessageEvents.ALLOW_CHAT_MESSAGE`; iptal edilirse mesaj gitmesin; sender'a ait ev.cancel), playerLeave before/after, playerSpawn (ilk girisi `initialSpawn` ile ayir; Bedrock playerSpawn her respawn'da da tetiklenir:
  initialSpawn yalniz ilk katilimda true), playerBreakBlock (`PlayerBlockBreakEvents.AFTER`; kirilan blok Perm + item), playerPlaceBlock (BlockItem yerlestirme sonrasi: `UseBlockCallback` + sonraki tik dogrulama VEYA mixin; mixin'i `duskwalker.mixins.json`'a ekle),
  playerInteractWithBlock/Entity (`UseBlockCallback`/`UseEntityCallback`; el esyasi `ItemStack` ceviri), entityDie (`ServerLivingEntityEvents.AFTER_DEATH` + DamageSource -> Opts.DamageSource, Bedrock neden adlari), entityHurt (`AFTER_DAMAGE`), itemUse (`UseItemCallback`), explosion (mixin: Explosion/ServerLevel.explode sonrasi).
* `FormGui`: Forms.Action ve Forms.Modal'i **sunucu tarafli sandik menusu** (`MenuProvider` + `ChestMenu`/`SimpleContainer`) olarak goster: Action = her dugme bir esya (ad = dugme metni, baslik = form basligi, govde metni lore ya da ust sira kitap), tiklayinca `selection`;
  Modal = toggle (yesil/kirmizi cam, tikla degistir), slider (-/+ dugmeleri), dropdown (sirayla degistir), "Kaydet" dugmesi -> `formValues`; kapatma = canceled ("UserClosed"). Oyuncu zaten bir menudeyse `UserBusy`. 54 slottan fazla dugme icin sayfalama.
  Menu esyalari alinamaz/tasinamaz (click iptal). Tek cagri basina yalnizca bir handler cagrilir.
* Oyuncu islevleri: `playSound` (yalniz o oyuncuya: `ClientboundSoundPacket`/`player.connection.send`), `setTitle`/`setActionBar` (`ClientboundSetTitlesAnimationPacket`, `SetTitleText`, `SetSubtitleText`, `SetActionBarText`), `cameraFade/Shake/fogPush/fogPop` = `DwNetwork.send*`,
  `getBlockFromViewDirection` (ray cast), `getSpawnPoint`, `mainhand/setMainhand`, `inventory()` (36 ana slot), `isFlying`, `setSelectedSlotIndex`, `runCommand`, `addEffect/removeEffect`, `applyDamage` (cause -> DamageSource), `kill`, `teleport` (boyut degistirme, facingLocation/rotation), `getViewDirection`, `getHeadLocation`, `getVelocity`, `applyImpulse`, `isSleeping`, `isTamed` (TamableAnimal).
* Dimension: `getEntities` sorgulari (type/families/excludeFamilies/tags/konum+mesafe), `getTopmostBlock` (heightmap, Bedrock anlami: en ust katı blok), `spawnEntity` (kimlik cevirisi + NBT yok), `spawnItem`, `spawnParticle` (`ServerLevel.sendParticles`), `playSound` (konum), `getWeather`, `runCommand`.
  `world.getDay()` = `(dayTime / 24000) + 1`; `moonPhase` Java ayi ile ayni sira (0 = dolunay); `gameRule` ("doDayLightCycle" -> GameRules.RULE_DAYLIGHT).
* Esya cevirisi `ItemConv`: scripts `ItemStack` (typeId, amount, nameTag, lore) <-> `net.minecraft.world.item.ItemStack` (CUSTOM_NAME, LORE bilesenleri; 1.21 DataComponents). `minecraft:written_book`/`paper` vb. kitap iceriklerini scriptlerin kullandigi sekliyle koru (lore isaretleri `Haunt.HAUNT_MARK` icindeki `​` dahil kaybolmamali; renk kodlari `§`).
Cikti sonunda `docs/BINDING_REPORT.md`: neyi nasil esledigin, bilinen bosluklar, `UNVERIFIED_API.md` ozeti.
