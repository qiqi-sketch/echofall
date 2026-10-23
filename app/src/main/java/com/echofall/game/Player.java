package com.echofall.game;

public class Player {
    public static final int STANCE_STAND = 0;
    public static final int STANCE_CROUCH = 1;
    public static final int STANCE_PRONE = 2;

    public float x = 20.5f, z = 34.5f, y = 0f, vy = 0f;
    public float yaw = 0f, pitch = 0f;
    public int hp = 100, armor = 0, money = 800;
    public int kills = 0, deaths = 0;
    public int team = GameState.TEAM_CT;

    public WeaponType primary = null;
    public WeaponType secondary = WeaponType.PISTOL;
    public WeaponType current = WeaponType.PISTOL;

    public final int[] mag = new int[WeaponType.count()];
    public final int[] reserve = new int[WeaponType.count()];

    public int stance = STANCE_STAND;
    public boolean ads = false;
    public float adsT = 0f;

    public float fireCd = 0f;
    public float reloadT = 0f;
    public float switchT = 0f;
    public float knifeT = 0f;
    public float muzzleT = 0f;
    public float bob = 0f;
    public float respawnT = 0f;
    public float recoilT = 0f;
    public boolean planting = false;
    public boolean defusing = false;
    public boolean hasBomb = false;

    public boolean owns(WeaponType w) {
        if (w == WeaponType.KNIFE) return true;
        return primary == w || secondary == w;
    }

    public float eyeHeight() {
        if (planting || defusing) return 0.88f;
        switch (stance) {
            case STANCE_CROUCH:
                return 1.02f;
            case STANCE_PRONE:
                return 0.46f;
            default:
                return 1.55f;
        }
    }

    public float stanceSpeed() {
        switch (stance) {
            case STANCE_CROUCH:
                return 0.5f;
            case STANCE_PRONE:
                return 0.22f;
            default:
                return 1f;
        }
    }
}
