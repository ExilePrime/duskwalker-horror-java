package com.duskwalker.fabric.entity;

/**
 * Bedrock varlik betikleri (triggerEvent / setProperty / getProperty / playAnimation) icin Java tarafi sozlesmesi.
 * Platform katmani (McEntity) varligi bu arayuze cevirip cagirir.
 */
public interface DwEntityApi {
    /** "duskwalker:hunt_start", "hunt_start_fast", "hunt_end", "vanish", "skin_1..4" (on ek "duskwalker:" istege bagli). */
    void dwTriggerEvent(String bedrockEvent);

    /** "duskwalker:variant" (int) / "duskwalker:dominant" (bool) / "duskwalker:skin" (int). */
    void dwSetProperty(String name, Object value);

    /** Ozelligin su anki degeri: Integer ya da Boolean; bilinmeyen ozellik icin null. */
    Object dwGetProperty(String name);

    /** "animation.duskwalker.attack" -> istemcide 1.1 sn saldiri animasyonu (entity event ile). */
    void dwPlayAnimation(String animationName);
}
