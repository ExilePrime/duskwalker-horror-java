package com.duskwalker.bedrock;

/** dimension.getBlock(...) sonucu. Bayat (stale) olabilir; her kullanimda yeniden getBlock tercih edilir. */
public interface Block {
    /** Bedrock kimligi, ornegin "minecraft:air", "minecraft:standing_sign" */
    String typeId();
    boolean isAir();
    int x();
    int y();
    int z();
    Vec3 location();
    Dimension dimension();

    /** block.setType("minecraft:stone") */
    void setType(String typeId);
    Perm permutation();
    void setPermutation(Perm perm);

    /** minecraft:sign bileseni: tabela degilse false doner. */
    boolean setSignText(String text);
    String getSignText();

    /** Sandik gibi kapsayici bloklarin envanteri; yoksa null. */
    Container container();
}
