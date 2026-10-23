package com.echofall.game;

public class Tracer {
    public float x0, y0, z0, x1, y1, z1;
    public float life;

    public Tracer(float x0, float y0, float z0, float x1, float y1, float z1) {
        this.x0 = x0;
        this.y0 = y0;
        this.z0 = z0;
        this.x1 = x1;
        this.y1 = y1;
        this.z1 = z1;
        this.life = 0.13f;
    }
}
