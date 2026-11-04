# Duskwalker Horror: JS (Bedrock Script API) -> Java port kilavuzu

Amac: `scripts/**.js` dosyalarini DAVRANIS OLARAK EKSIKSIZ ve birebir Java'ya cevirmek. Hicbir ozellik,
olay, metin, sayi, olasilik, sure, esik atlanmayacak/basitlestirilmeyecek. Orijinal kaynak:
`/tmp/claude-0/-home-claude/2a5a248e-3afb-5be6-8eba-285f4b7ba2f6/scratchpad/addon/bp/scripts/` (main.js ve v10/*.js).
Cikti: `/home/claude/DuskwalkerHorror-Java/src/main/java/com/duskwalker/mod/`.

## Mimari
* `com.duskwalker.bedrock.*` = Bedrock API'sinin Java karsiliklari (arayuzler: Entity, Player, Dimension, Block, Perm,
  Container, ItemStack, World, Sys, Events, Forms, Json, Opts, Vec3 ...). ELINIZE GECENLERI OKUYUN: hepsi
  `src/main/java/com/duskwalker/bedrock/`. Modul kodu YALNIZCA bu paket + `Core` uzerinden dunyaya erisir;
  `net.minecraft.*` veya `net.fabricmc.*` import etmek YASAK. (Binding'i baska ajan yazacak.)
* `com.duskwalker.mod.Core` = core.js'in portu (HAZIR, okuyun). Diger modullerde `import static com.duskwalker.mod.Core.*;`
  kullanin (safe, tx, chance, pick, randInt, loseSanity, getSanity, showForm, addStat ... hepsi orada).
* Her JS dosyasi tek bir Java sinifi olur: `snake_case.js` -> `PascalCase` (`events_audio.js` -> `EventsAudio`,
  `light_refuge.js` -> `LightRefuge`, `past_selves.js` -> `PastSelves`, `haunted_zones.js` -> `HauntedZones`).
  Sinif `public final class X { private X(){} ... }`, yalniz static uyeler.
* JS dosyasinin en ustundeki yan etkiler (subscribe, runInterval, registerEvent, registerCreatureBehavior ...) =
  `public static void init()` icine konur. Mod giris noktasi `Mod.init()` hepsini sirayla cagiracak
  (sirayi `v10/index.js` belirler). Import edilip yalnizca yan etki icin kullanilan modullerde de `init()` olmali.
* `export`'lu her sey `public static`. Disa acilmayan her sey `private static`.

## Tip kurallari
* JS number: yerel degiskenlerde `int`/`long`/`double` serbest (tick sayaclari `long`, indeksler `int`).
  Moduller ARASI (public) imzalarda ve JSON/Map icinde sayilar `double` / `Double`. Tam sayi gerektiren yerde
  `(int)` donusumu yapin. Dikkat: JS'te `/` ondalikli bolmedir; Java'da int/int tamsayi bolmesidir -> `/ 2.0` yazin.
  JS `Math.floor(x/y)` = `Math.floor(x / (double) y)`. `%` negatif sayilarda JS ve Java ayni (isaret soldan).
* JS `{...}` rastgele nesne verisi ve kalici JSON = `Map<String,Object>` (Json.newMap()), diziler `List<Object>`;
  okurken `Json.num/str/bool/asMap/asList`. Sabit sekilli, yalniz bellekte tutulan nesneler icin ic `static final class` yazin.
* JS `undefined`/`null` = `null`. `x || 0` = null-guvenli yardimci. `typeof v === "number"` = `v instanceof Number`.
* `Map`/`Set` -> `HashMap`/`HashSet`/`LinkedHashMap`. Anahtar oyuncu id'si (`player.id()`).
* Konum `{x,y,z}` = `Vec3`. Blok koordinatlari Vec3 (tam sayi degerli) veya int uclusu.
* Rastgelelik: `Math.random()` aynen; `Core.randInt/rand/chance/pick`.
* Dizi sabitleri `String[]`/`List<String>`; iki dilli metin `tx([tr,en])` -> `tx(tr, en)`; `tx(LINES[i])` icin `String[][]`
  kullanip `tx(pair)`. TUM METINLER (renk kodlari §, bosluklar, noktalama, buyuk/kucuk harf) AYNEN korunur.
* JS try/catch ile yutulan her seyi Java'da da yutun (`Core.safe`, ya da try/catch(Throwable)). Orijinal
  davranis "hata olursa sessizce devam" ise bunu bozmayin.
* Closure ile paylasilan degiskenleri lambda icinde kullanmak icin `final`/etkin-final olmali: gerekirse tek elemanli dizi
  veya kucuk sinif kullanin.

## API esleme tablosu
| Bedrock JS | Java |
|---|---|
| `world.getPlayers()` | `World.getPlayers()` (List<Player>) |
| `world.getDimension("overworld")` | `World.getDimension("overworld")` / `World.overworld()` |
| `world.setDynamicProperty(k,v)` | `World.setDynamicProperty(k, v)` (String/Double/Boolean/null); sayiyi `(double)` ver |
| `system.runInterval/runTimeout/run/clearRun/currentTick` | `Sys.runInterval(Runnable, ticks)` / `Sys.runTimeout` / `Sys.run` / `Sys.clearRun(int)` / `Sys.currentTick()` (long) |
| `world.afterEvents.X.subscribe(ev=>...)` | `Events.After.X.subscribe(ev -> ...)`; `beforeEvents.chatSend` = `Events.Before.chatSend` (ev.cancel = true) |
| `entity.id`, `.typeId`, `.location`, `.dimension`, `.nameTag` | `entity.id()`, `.typeId()`, `.location()`, `.dimension()`, `.nameTag()` / `.setNameTag(..)` |
| `entity.isValid()` / `.isValid` | `entity.isValid()` (veya `Core.isValid(entity)` null-guvenli) |
| `entity.teleport(loc, {dimension, facingLocation, rotation})` | `entity.teleport(loc, Opts.Teleport.dim(d).facing(v).rot(pitch,yaw))` ; opts yoksa `teleport(loc)` |
| `entity.triggerEvent("duskwalker:hunt_start")` | `entity.triggerEvent(...)` (ayni olay adlari) |
| `entity.getComponent("minecraft:inventory").container` | `((Player) p).inventory()` (Container) |
| `getComponent("minecraft:equippable").getEquipment("Mainhand")` / `setEquipment("Mainhand", x)` | `player.mainhand()` / `player.setMainhand(x)` (null = bos) |
| `getComponent("minecraft:is_tamed")` / `"minecraft:health"` / `is_sleeping` | `entity.isTamed()` / `entity.hasHealth()` / `entity.isSleeping()` |
| `block.getComponent("minecraft:sign").setText(t)` | `block.setSignText(t)` |
| `block.getComponent("minecraft:inventory").container` | `block.container()` (null olabilir) |
| `block.permutation.withState("open_bit", v)`, `perm.getAllStates()` | `block.permutation().withState(..)` / `.getAllStates()` |
| `new ItemStack("minecraft:paper", n)`, `.nameTag`, `.setLore([...])`, `.getLore()`, `.amount` | `new ItemStack(id, n)`, `item.nameTag = ..`, `item.setLore(List/varargs)`, `item.getLore()`, `item.amount` |
| `player.sendMessage`, `playSound`, `onScreenDisplay.setActionBar/setTitle`, `camera.fade` | `player.sendMessage`, `player.playSound(id, Opts.Sound.of(vol,pitch))`, `player.setActionBar`, `player.setTitle(t, Opts.Title.of(in,stay,out,sub))`, `player.cameraFade(Opts.Fade.of(...))` (Core.fade/Core.title/Core.say/Core.actionbar/playTo/playAt/shake hazir) |
| `player.runCommand("camerashake add @s s t rotational")` | `player.cameraShake(strength, seconds, "rotational"|"positional")` (komut YAZMAYIN) |
| `player.runCommand("fog @s push duskwalker:hunt_fog duskwalker_hunt")` / `pop` | `player.fogPush("duskwalker:hunt_fog", "duskwalker_hunt")` / `player.fogPop("duskwalker_hunt")` |
| `dimension.runCommand(...)`, `player.runCommand(...)` (fill, setblock, summon, tp, weather, gamerule, execute..function) | AYNI BEDROCK SOZDIZIMI ile `runCommand(String)`; binding Java'ya cevirir. `world.getDimension("overworld").runCommand("gamerule dodaylightcycle false")` yerine `World.setGameRule("doDayLightCycle", false)` kullanin. `function duskwalker/structure_01` Bedrock yolunu aynen verin. |
| `dimension.getEntities({type, families, location, maxDistance})` | `dim.getEntities(Opts.EntityQuery.type("..").near(loc, d))`, `EntityQuery.family("..")` |
| `dimension.spawnEntity(id, loc)` / `spawnItem(item, loc)` / `spawnParticle(id, loc)` / `playSound(id, loc, {volume,pitch})` | ayni adlar (spawnEntity firlatabilir; Core.safe veya try/catch) |
| `entity.applyDamage(n, {cause:"entityAttack", damagingEntity})` | `entity.applyDamage(n, Opts.Damage.of("entityAttack", by))` |
| `world.gameRules.doDayLightCycle` | `World.gameRule("doDayLightCycle")` / `World.setGameRule(...)` |
| `world.getTimeOfDay/setTimeOfDay/getDay/getAbsoluteTime/getMoonPhase` | `World.` ayni adlar |
| `dimension.getWeather()` ("Clear"/"Rain"/"Thunder") | `dim.getWeather()` ayni dizgeler |
| `ev.damageSource.damagingEntity/.cause` | `ev.damageSource.damagingEntity` / `.cause` (Bedrock neden adlari: "entityAttack","lava",...) |
| `new ActionFormData().title().body().button().show(p).then(res=>...)` | `new Forms.Action().title(..).body(..).button(..)`, `Core.showForm(player, form, res -> { res.selection ... })`; ModalFormData -> `Forms.Modal` (`res.formValues`: toggle Boolean, slider Double, dropdown Integer, text String) |
| `JSON.parse / JSON.stringify` | `Json.parse / Json.stringify` |
| `Date` / `getHours()...` | `java.time.LocalDateTime.now()` (yerel saat) |
| Bedrock kimlikleri ("minecraft:standing_sign", "minecraft:web", "minecraft:soul_particle", "mob.ghast.moan" ...) | AYNEN Bedrock kimligini yazin. Java'ya cevirisini binding yapar; kimlik degistirmeyin |

## Eksik API
Gerekli bir sey `bedrock` paketinde yoksa:
1. Arayuze/sinifa MINIMAL bir yontem ekleyin (mevcutlari bozmadan, `// ADDED(<modul>)` yorumu ile),
2. `docs/SHIM_REQUESTS.md` dosyasina (append) tek satir ekleyin: `- <Arayuz>.<yontem>: <anlam> (<modul>)`.
Binding ajani bunlari uygulayacak. Baska bir ajanin dosyasini DUZENLEMEYIN.

## Dogrulama
Yalniz kendi dosyalarinizi derleyin (digerlerinin yarim kalmis dosyalari derlemeyi bozmasin):
```
cd /home/claude/DuskwalkerHorror-Java && mkdir -p /tmp/dwout_$$ && javac -Xlint:unchecked -sourcepath src/main/java -d /tmp/dwout_$$ src/main/java/com/duskwalker/mod/<DosyaniZ>.java 2>&1 | grep -v "^Picked up"
```
Temiz (hatasiz) derlenmeli. Baska modullere olan basvurular yazilmamissa (henuz yoksa) o modulun JS'ine bakarak
beklenen imzayi cikarin; derleme hatasi YALNIZCA "baska ajanin henuz yazmadigi sinif" yuzundense tolere edilir ama raporlayin.

## Rapor
Bittiginde su basliklarla KISA rapor verin: (1) yazilan dosyalar ve JS satir sayisi karsiligi, (2) bilincli sapmalar /
Java'da farkli davranabilecek yerler, (3) SHIM_REQUESTS'e eklenenler, (4) baska modullere beklenen/verilen public imzalar.
Orijinal davranisi korumak her seyden onemlidir: emin olmadiginiz yerde JS'i tekrar okuyun, tahmin etmeyin.
