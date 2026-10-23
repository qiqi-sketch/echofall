package com.echofall.game;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.PriorityQueue;
import java.util.Random;

/**
 * 96x96 的开放式爆破地图（约旧图 5.7 倍面积）。
 * 0 = 地面，1 = 矮掩体（挡住走位但挡不住视线），2 = 高墙（挡视线和子弹）。
 */
public class MapGrid {
    public static final int W = 96;
    public static final int H = 96;

    public static final byte FLOOR = 0;
    public static final byte COVER = 1;
    public static final byte WALL = 2;

    public static final float COVER_H = 1.25f;
    public static final float WALL_H = 3.2f;

    // 每个点位：x0, z0, x1, z1（包含边界）
    public static final int[] SITE_A = {68, 32, 88, 48};
    public static final int[] SITE_B = {6, 32, 26, 48};

    public static final float A_CENTER_X = 78f, A_CENTER_Z = 40f;
    public static final float B_CENTER_X = 16f, B_CENTER_Z = 40f;

    public final byte[][] kind = new byte[W][H];
    private final Random rnd = new Random(20240607);
    // A* 复用缓冲，避免每帧大量分配
    private final float[] gArr = new float[W * H];
    private final int[] parentArr = new int[W * H];
    private final boolean[] closedArr = new boolean[W * H];

    public MapGrid() {
        build();
    }

    private void build() {
        // 外墙
        for (int z = 0; z < H; z++) {
            kind[0][z] = WALL;
            kind[1][z] = WALL;
            kind[W - 2][z] = WALL;
            kind[W - 1][z] = WALL;
        }
        for (int x = 0; x < W; x++) {
            kind[x][0] = WALL;
            kind[x][1] = WALL;
            kind[x][H - 2] = WALL;
            kind[x][H - 1] = WALL;
        }

        // ---------------- 建筑 ----------------
        // 中路大掩体（把中路一分为二，避免出生就能对穿）
        wallRect(43, 43, 53, 53);

        // 右侧仓库（可进出，南北各一个门）
        building(70, 60, 86, 72, new int[]{74, 78}, new int[]{80, 84}, 64, 68);
        // 左侧仓库
        building(9, 60, 25, 72, new int[]{18, 22}, new int[]{11, 15}, 64, 68);

        // 中路两侧的长墙，形成右路 / 左路两条通道
        wallRect(64, 16, 65, 34, 24, 28);
        wallRect(64, 56, 65, 80, 62, 66);
        wallRect(30, 16, 31, 34, 24, 28);
        wallRect(30, 56, 31, 80, 62, 66);

        // ---------------- A 点 ----------------
        wallRect(66, 30, 90, 31, 78, 82);   // 上墙
        wallRect(66, 50, 90, 51, 72, 76);   // 下墙
        wallRect(66, 30, 67, 51, 38, 42);   // 靠中路一侧
        wallRect(90, 30, 91, 51, 44, 48);   // 靠外墙一侧

        // ---------------- B 点 ----------------
        wallRect(4, 30, 28, 31, 14, 18);
        wallRect(4, 50, 28, 51, 20, 24);
        wallRect(4, 30, 5, 51, 44, 48);
        wallRect(28, 30, 29, 51, 38, 42);

        // ---------------- 矮掩体 ----------------
        coverRect(40, 20, 42, 22);
        coverRect(54, 20, 56, 22);
        coverRect(46, 36, 48, 38);
        coverRect(48, 58, 50, 60);
        coverRect(44, 74, 48, 76);
        coverRect(44, 16, 48, 18);

        coverRect(70, 34, 72, 36);
        coverRect(84, 34, 86, 36);
        coverRect(76, 44, 78, 46);
        coverRect(82, 44, 84, 46);
        coverRect(62, 40, 64, 42);

        coverRect(10, 34, 12, 36);
        coverRect(22, 34, 24, 36);
        coverRect(16, 44, 18, 46);
        coverRect(10, 44, 12, 46);
        coverRect(32, 40, 34, 42);

        coverRect(68, 66, 70, 68);
        coverRect(26, 66, 28, 68);
        coverRect(68, 78, 72, 80);
        coverRect(24, 78, 28, 80);

        // 出生点附近的掩体
        coverRect(38, 84, 40, 86);
        coverRect(56, 84, 58, 86);
        coverRect(38, 8, 40, 10);
        coverRect(56, 8, 58, 10);
    }

    private void wallRect(int x0, int z0, int x1, int z1) {
        for (int x = x0; x <= x1; x++) {
            for (int z = z0; z <= z1; z++) {
                if (inside(x, z)) kind[x][z] = WALL;
            }
        }
    }

    /** 竖直/水平墙，中间开一个缺口 gap0..gap1 */
    private void wallRect(int x0, int z0, int x1, int z1, int gap0, int gap1) {
        boolean horizontal = (x1 - x0) >= (z1 - z0);
        for (int x = x0; x <= x1; x++) {
            for (int z = z0; z <= z1; z++) {
                int along = horizontal ? x : z;
                if (along >= gap0 && along <= gap1) continue;
                if (inside(x, z)) kind[x][z] = WALL;
            }
        }
    }

    private void coverRect(int x0, int z0, int x1, int z1) {
        for (int x = x0; x <= x1; x++) {
            for (int z = z0; z <= z1; z++) {
                if (inside(x, z) && kind[x][z] == FLOOR) kind[x][z] = COVER;
            }
        }
    }

    /** 四面围墙、内部空地的建筑；南北各开一个门，西侧再开一个门 */
    private void building(int x0, int z0, int x1, int z1,
                          int[] northDoor, int[] southDoor,
                          int westDoor0, int westDoor1) {
        wallRect(x0, z0, x1, z0);
        wallRect(x0, z1, x1, z1);
        wallRect(x0, z0, x0, z1);
        wallRect(x1, z0, x1, z1);
        for (int x = x0 + 1; x < x1; x++) {
            for (int z = z0 + 1; z < z1; z++) {
                if (inside(x, z)) kind[x][z] = FLOOR;
            }
        }
        carve(x0, z0, x1, z0, northDoor[0], northDoor[1]);
        carve(x0, z1, x1, z1, southDoor[0], southDoor[1]);
        carve(x0, z0, x0, z1, westDoor0, westDoor1);
    }

    private void carve(int x0, int z0, int x1, int z1, int gap0, int gap1) {
        boolean horizontal = (x1 - x0) >= (z1 - z0);
        for (int x = x0; x <= x1; x++) {
            for (int z = z0; z <= z1; z++) {
                int along = horizontal ? x : z;
                if (along < gap0 || along > gap1) continue;
                if (inside(x, z)) kind[x][z] = FLOOR;
            }
        }
    }

    private boolean inside(int x, int z) {
        return x > 0 && z > 0 && x < W - 1 && z < H - 1;
    }

    public byte kindAt(int x, int z) {
        if (x < 0 || z < 0 || x >= W || z >= H) return WALL;
        return kind[x][z];
    }

    public boolean isWall(int x, int z) {
        return kindAt(x, z) == WALL;
    }

    public boolean blocksMove(int x, int z) {
        return kindAt(x, z) != FLOOR;
    }

    public float heightAt(int x, int z) {
        byte k = kindAt(x, z);
        if (k == WALL) return WALL_H;
        if (k == COVER) return COVER_H;
        return 0f;
    }

    public boolean blocked(float x, float z, float r) {
        return blocksMove((int) Math.floor(x - r), (int) Math.floor(z - r))
                || blocksMove((int) Math.floor(x + r), (int) Math.floor(z - r))
                || blocksMove((int) Math.floor(x - r), (int) Math.floor(z + r))
                || blocksMove((int) Math.floor(x + r), (int) Math.floor(z + r));
    }

    public boolean lineOfSight(float x0, float z0, float x1, float z1) {
        float dx = x1 - x0;
        float dz = z1 - z0;
        float d = (float) Math.hypot(dx, dz);
        if (d < 0.001f) return true;
        int steps = (int) (d / 0.35f) + 1;
        for (int i = 1; i < steps; i++) {
            float t = i / (float) steps;
            if (isWall((int) Math.floor(x0 + dx * t), (int) Math.floor(z0 + dz * t))) {
                return false;
            }
        }
        return true;
    }

    public boolean inRect(int[] r, float x, float z) {
        return x >= r[0] && x <= r[2] + 1 && z >= r[1] && z <= r[3] + 1;
    }

    public boolean inSiteA(float x, float z) {
        return inRect(SITE_A, x, z);
    }

    public boolean inSiteB(float x, float z) {
        return inRect(SITE_B, x, z);
    }

    public boolean inAnySite(float x, float z) {
        return inSiteA(x, z) || inSiteB(x, z);
    }

    public float[] randomFree() {
        for (int i = 0; i < 500; i++) {
            int x = 2 + rnd.nextInt(W - 4);
            int z = 2 + rnd.nextInt(H - 4);
            if (kind[x][z] == FLOOR) return new float[]{x + 0.5f, z + 0.5f};
        }
        return new float[]{48.5f, 48.5f};
    }

    public List<float[]> findPath(float sx, float sz, float tx, float tz) {
        List<float[]> result = new ArrayList<>();
        int startX = clamp((int) Math.floor(sx), 0, W - 1);
        int startZ = clamp((int) Math.floor(sz), 0, H - 1);
        int goalX = clamp((int) Math.floor(tx), 0, W - 1);
        int goalZ = clamp((int) Math.floor(tz), 0, H - 1);

        if (blocksMove(startX, startZ)) {
            int[] f = nearestFree(startX, startZ);
            if (f == null) return result;
            startX = f[0];
            startZ = f[1];
        }
        if (blocksMove(goalX, goalZ)) {
            int[] f = nearestFree(goalX, goalZ);
            if (f == null) return result;
            goalX = f[0];
            goalZ = f[1];
        }

        int n = W * H;
        final int BASE = 16384;
        float[] g = gArr;
        int[] parent = parentArr;
        boolean[] closed = closedArr;
        Arrays.fill(g, Float.MAX_VALUE);
        Arrays.fill(parent, -1);
        Arrays.fill(closed, false);

        int start = startX * H + startZ;
        int goal = goalX * H + goalZ;
        g[start] = 0f;

        PriorityQueue<Integer> open = new PriorityQueue<>();
        open.add((int) (h(startX, startZ, goalX, goalZ) * 10f) * BASE + start);

        final int[][] dirs = {{1, 0}, {-1, 0}, {0, 1}, {0, -1},
                {1, 1}, {1, -1}, {-1, 1}, {-1, -1}};
        boolean found = false;

        while (!open.isEmpty()) {
            int cur = open.poll() % BASE;
            if (closed[cur]) continue;
            closed[cur] = true;
            if (cur == goal) {
                found = true;
                break;
            }
            int cx = cur / H;
            int cz = cur % H;
            for (int[] d : dirs) {
                int nx = cx + d[0];
                int nz = cz + d[1];
                if (blocksMove(nx, nz)) continue;
                if (d[0] != 0 && d[1] != 0) {
                    // 斜向不能穿墙角
                    if (blocksMove(cx + d[0], cz) || blocksMove(cx, cz + d[1])) continue;
                }
                int ni = nx * H + nz;
                if (closed[ni]) continue;
                float step = (d[0] != 0 && d[1] != 0) ? 1.4142f : 1f;
                float ng = g[cur] + step;
                if (ng < g[ni]) {
                    g[ni] = ng;
                    parent[ni] = cur;
                    open.add(((int) ((ng + h(nx, nz, goalX, goalZ)) * 10f)) * BASE + ni);
                }
            }
        }

        if (!found) return result;
        int cur = goal;
        while (cur != start && cur >= 0) {
            result.add(0, new float[]{cur / H + 0.5f, cur % H + 0.5f});
            cur = parent[cur];
        }
        return result;
    }

    private int[] nearestFree(int cx, int cz) {
        for (int r = 1; r <= 6; r++) {
            for (int x = cx - r; x <= cx + r; x++) {
                for (int z = cz - r; z <= cz + r; z++) {
                    if (x > 0 && z > 0 && x < W - 1 && z < H - 1 && kind[x][z] == FLOOR) {
                        return new int[]{x, z};
                    }
                }
            }
        }
        return null;
    }

    private float h(int x, int z, int gx, int gz) {
        int ax = Math.abs(x - gx);
        int az = Math.abs(z - gz);
        int mn = Math.min(ax, az);
        return (ax + az) + (1.4142f - 2f) * mn;
    }

    private int clamp(int v, int lo, int hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    /** 给小地图用的像素色块 */
    public int[] radarPixels() {
        int[] px = new int[W * H];
        for (int z = 0; z < H; z++) {
            for (int x = 0; x < W; x++) {
                int c;
                switch (kind[x][z]) {
                    case WALL:
                        c = 0xFF2A3542;
                        break;
                    case COVER:
                        c = 0xFF44566A;
                        break;
                    default:
                        c = 0xFF141C26;
                        break;
                }
                px[z * W + x] = c;
            }
        }
        return px;
    }
}
