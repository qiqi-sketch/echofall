package com.echofall.game;

public enum WeaponType {
    KNIFE("刀", 0, 55, 0.50f, 2.4f, 0, 0, false, false, 1.25f, 0.0f, 0.0f, 1, false),
    PISTOL("手枪", 500, 34, 0.30f, 30f, 12, 60, false, false, 1.00f, 0.8f, 1.0f, 1, false),
    DEAGLE("沙鹰", 700, 58, 0.45f, 45f, 7, 35, false, false, 0.97f, 0.55f, 2.4f, 1, false),
    SMG("冲锋枪", 1250, 24, 0.085f, 28f, 30, 90, true, false, 1.05f, 1.4f, 1.1f, 1, true),
    SHOTGUN("霰弹枪", 1100, 20, 0.85f, 18f, 8, 32, false, false, 1.00f, 3.2f, 2.6f, 9, true),
    RIFLE("步枪", 2700, 33, 0.105f, 60f, 30, 90, true, false, 1.00f, 0.9f, 2.0f, 1, true),
    AWP("AWP", 4750, 120, 1.45f, 120f, 10, 30, false, true, 0.90f, 0.06f, 6.0f, 1, true);

    public final String label;
    public final int price;
    public final int damage;
    public final float interval;
    public final float range;
    public final int magSize;
    public final int reserveSize;
    public final boolean auto;
    public final boolean scoped;
    public final float moveMult;
    public final float spread;
    public final float recoil;
    public final int pellets;
    public final boolean primary;

    WeaponType(String label, int price, int damage, float interval, float range,
               int magSize, int reserveSize, boolean auto, boolean scoped,
               float moveMult, float spread, float recoil, int pellets, boolean primary) {
        this.label = label;
        this.price = price;
        this.damage = damage;
        this.interval = interval;
        this.range = range;
        this.magSize = magSize;
        this.reserveSize = reserveSize;
        this.auto = auto;
        this.scoped = scoped;
        this.moveMult = moveMult;
        this.spread = spread;
        this.recoil = recoil;
        this.pellets = pellets;
        this.primary = primary;
    }

    public static int count() {
        return values().length;
    }
}
