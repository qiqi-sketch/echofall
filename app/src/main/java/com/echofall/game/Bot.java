package com.echofall.game;

import java.util.ArrayList;
import java.util.List;

public class Bot {
    public float x, z, yaw;
    public int hp = 100;
    public boolean alive = true;
    public float respawnT = 0f;
    public float thinkT = 0f;
    public float shootT = 0f;
    public float hitFlash = 0f;
    public int state = 0; // 0=patrol 1=chase 2=attack

    public int team = GameState.TEAM_T;
    public float aimYaw = 0f;
    public float aimPitch = 0f;
    public float skill = 0.5f;      // 命中率系数
    public float reactionT = 0f;    // 反应延迟
    public float strafeT = 0f;
    public int strafeDir = 1;
    public float targetX, targetZ, targetDist;
    public boolean targetIsPlayer = false;
    public int targetBot = -1;
    public float holdX, holdZ;      // 目标点位（A/B 点或炸弹）
    public boolean planting = false;
    public boolean defusing = false;

    public final List<float[]> path = new ArrayList<>();
    public int pathIndex = 0;
}
