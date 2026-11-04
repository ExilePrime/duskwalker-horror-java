# Duskwalker Horror v14.1 – Java (Fabric 1.21.1) portu

Bedrock addon'unun (`DuskwalkerHorror_v14_1.mcaddon`) Java karşılığı. Tüm script mantığı (main.js + 51 v10 modülü,
~12.600 satır JS) Java'ya çevrildi; entity'ler, modeller, animasyonlar, sesler, sis/ekran efektleri ve yapı fonksiyonları dahil.

## Derleme
Gereksinim: JDK 21, internet (Gradle/Loom Minecraft'ı indirir).
    ./gradlew build        ->  build/libs/duskwalker-horror-14.1.0.jar
Jar'ı hem sunucuya hem istemciye (Fabric Loader 0.20.1+, Fabric API 0.116.17+1.21.1) koyun.

## Mimari
* `com.duskwalker.bedrock`  : Bedrock Script API'sinin Java arayüzleri (Entity, Player, Dimension, Forms, Events, Sys ...)
* `com.duskwalker.mod`      : JS modüllerinin birebir Java çevirisi (Core, MainA/B/C, Stats, Omens, Paradox ...). Minecraft'a bağımlı DEĞİL.
* `com.duskwalker.fabric`   : Fabric 1.21.1 (Mojang eşlemeleri) bağlama katmanı: varlıklar, ağ, olaylar, komut/kimlik çevirisi, form menüleri.
* `src/client`              : modeller, animasyonlar, fade/shake/fog efektleri.

## Önemli
Bu paket üretildiği ortamda Maven/Mojang erişimi olmadığından **Minecraft'a karşı derlenemedi**. Yalnızca `bedrock` ve `mod`
paketleri (~%85 kod) javac ile doğrulandı. `fabric` ve `client` paketlerindeki Minecraft API adları dokümantasyon örneklerine göre
yazıldı; `docs/UNVERIFIED_API.md` doğrulanamayan adların listesidir. İlk `./gradlew build`'de küçük isim/imza düzeltmeleri gerekebilir.
Bedrock formları Java'da sunucu tarafı sandık menüsü olarak gösterilir.
