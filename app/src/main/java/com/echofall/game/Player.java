package com.echofall.game;

import java.util.ArrayList;

public class Player {
    public float x = 3.5f, z = 3.5f, y = 0f, vy = 0f;
    public float yaw = 0f, pitch = 0f;
    public int hp = 100, armor = 0, money = 800;
    public int kills = 0, deaths = 0;

    public WeaponType current = WeaponType.PISTOL;
    public final ArrayList<WeaponType> owned = new ArrayList<>();
    public final int[] mag = new int[3];
    public final int[] reserve = new int[3];

    public float fireCd = 0f;
    public float reloadT = 0f;
    public float switchT = 0f;
    public float knifeT = 0f;
    public float muzzleT = 0f;
    public float bob = 0f;
    public float respawnT = 0f;
}
