package com.duskwalker.bedrock;

import java.util.Map;

/**
 * Bedrock BlockPermutation karsiligi (blok + durum). Durum adlari Bedrock adlaridir
 * ("open_bit", "upper_block_bit", "direction" ...); binding bunlari Java blockstate ozelliklerine cevirir.
 * Perm degistirilemez (immutable): withState yeni nesne dondurur.
 */
public interface Perm {
    String typeId();
    Perm withState(String name, Object value);
    Object getState(String name);
    Map<String, Object> getAllStates();
}
