package com.echofall.game;

public enum WeaponType {
    KNIFE("刀", 0, 55, 0.50f, 2.4f, 0, 0, false),
    PISTOL("手枪", 500, 34, 0.30f, 30f, 12, 60, false),
    RIFLE("步枪", 2700, 28, 0.105f, 60f, 30, 90, true);

    public final String label;
    public final int price;
    public final int damage;
    public final float interval;
    public final float range;
    public final int magSize;
    public final int reserveSize;
    public final boolean auto;

    WeaponType(String label, int price, int damage, float interval, float range,
               int magSize, int reserveSize, boolean auto) {
        this.label = label;
        this.price = price;
        this.damage = damage;
        this.interval = interval;
        this.range = range;
        this.magSize = magSize;
        this.reserveSize = reserveSize;
        this.auto = auto;
    }
}
