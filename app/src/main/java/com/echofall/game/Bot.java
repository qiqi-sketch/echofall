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
    public final List<float[]> path = new ArrayList<>();
    public int pathIndex = 0;
}
