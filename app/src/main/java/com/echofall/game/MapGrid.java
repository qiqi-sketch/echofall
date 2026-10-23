package com.echofall.game;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.PriorityQueue;
import java.util.Random;

public class MapGrid {
    public static final int W = 40;
    public static final int H = 40;

    // 每个点位：x0, z0, x1, z1（包含边界）
    public static final int[] SITE_A = {27, 8, 35, 16};
    public static final int[] SITE_B = {4, 8, 12, 16};

    public final boolean[][] wall = new boolean[W][H];
    private final Random rnd = new Random(12345);

    public MapGrid() {
        // 先全部填充为墙，再挖出走廊和房间，保证一定连通
        for (int x = 0; x < W; x++) {
            for (int z = 0; z < H; z++) {
                wall[x][z] = true;
            }
        }

        // 房间
        carve(17, 2, 23, 8);      // CT 出生点
        carve(17, 31, 23, 37);    // T 出生点
        carve(SITE_A[0], SITE_A[1], SITE_A[2], SITE_A[3]); // A 点
        carve(SITE_B[0], SITE_B[1], SITE_B[2], SITE_B[3]); // B 点
        carve(17, 18, 23, 22);    // 中路小屋

        // 主干道
        carve(19, 5, 21, 34);     // 中路
        carve(23, 4, 27, 8);      // CT -> A 连接
        carve(12, 4, 17, 8);      // CT -> B 连接
        carve(21, 11, 28, 13);    // 中路 -> A
        carve(11, 11, 19, 13);    // 中路 -> B
        carve(24, 31, 33, 33);    // T -> A 下路
        carve(31, 16, 33, 33);    // A 外侧长走廊
        carve(6, 31, 17, 33);     // T -> B 下路
        carve(6, 16, 8, 33);      // B 外侧长走廊

        // 掩体
        fill(29, 10, 30, 11);
        fill(33, 13, 34, 14);
        fill(31, 9, 31, 10);
        fill(6, 10, 7, 11);
        fill(10, 14, 10, 15);
        fill(5, 13, 5, 14);
        fill(17, 20, 18, 21);
        fill(22, 20, 22, 21);
        fill(18, 3, 19, 4);
        fill(21, 35, 22, 36);
    }

    private void carve(int x0, int z0, int x1, int z1) {
        for (int x = Math.max(1, x0); x <= Math.min(W - 2, x1); x++) {
            for (int z = Math.max(1, z0); z <= Math.min(H - 2, z1); z++) {
                wall[x][z] = false;
            }
        }
    }

    private void fill(int x0, int z0, int x1, int z1) {
        for (int x = Math.max(1, x0); x <= Math.min(W - 2, x1); x++) {
            for (int z = Math.max(1, z0); z <= Math.min(H - 2, z1); z++) {
                wall[x][z] = true;
            }
        }
    }

    public boolean isWall(int x, int z) {
        return x < 0 || z < 0 || x >= W || z >= H || wall[x][z];
    }

    public boolean blocked(float x, float z, float r) {
        return isWall((int) Math.floor(x - r), (int) Math.floor(z - r))
                || isWall((int) Math.floor(x + r), (int) Math.floor(z - r))
                || isWall((int) Math.floor(x - r), (int) Math.floor(z + r))
                || isWall((int) Math.floor(x + r), (int) Math.floor(z + r));
    }

    public boolean lineOfSight(float x0, float z0, float x1, float z1) {
        float dx = x1 - x0;
        float dz = z1 - z0;
        float d = (float) Math.hypot(dx, dz);
        if (d < 0.001f) return true;
        int steps = (int) (d / 0.2f) + 1;
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
        for (int i = 0; i < 300; i++) {
            int x = 1 + rnd.nextInt(W - 2);
            int z = 1 + rnd.nextInt(H - 2);
            if (!wall[x][z]) return new float[]{x + 0.5f, z + 0.5f};
        }
        return new float[]{20.5f, 20.5f};
    }

    public List<float[]> findPath(float sx, float sz, float tx, float tz) {
        List<float[]> result = new ArrayList<>();
        int startX = clamp((int) Math.floor(sx), 0, W - 1);
        int startZ = clamp((int) Math.floor(sz), 0, H - 1);
        int goalX = clamp((int) Math.floor(tx), 0, W - 1);
        int goalZ = clamp((int) Math.floor(tz), 0, H - 1);

        // 起点/终点在墙里时向附近找一个可走的格子
        if (wall[startX][startZ]) {
            int[] f = nearestFree(startX, startZ);
            if (f == null) return result;
            startX = f[0];
            startZ = f[1];
        }
        if (wall[goalX][goalZ]) {
            int[] f = nearestFree(goalX, goalZ);
            if (f == null) return result;
            goalX = f[0];
            goalZ = f[1];
        }

        int n = W * H;
        float[] g = new float[n];
        int[] parent = new int[n];
        boolean[] closed = new boolean[n];
        Arrays.fill(g, Float.MAX_VALUE);
        Arrays.fill(parent, -1);

        int start = startX * H + startZ;
        int goal = goalX * H + goalZ;
        g[start] = 0f;

        PriorityQueue<int[]> open = new PriorityQueue<>((a, b) -> Integer.compare(a[0], b[0]));
        open.add(new int[]{h(startX, startZ, goalX, goalZ) * 10, start});

        final int[][] dirs = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        boolean found = false;

        while (!open.isEmpty()) {
            int[] cur = open.poll();
            int ci = cur[1];
            if (closed[ci]) continue;
            closed[ci] = true;
            if (ci == goal) {
                found = true;
                break;
            }
            int cx = ci / H;
            int cz = ci % H;
            for (int[] d : dirs) {
                int nx = cx + d[0];
                int nz = cz + d[1];
                if (isWall(nx, nz)) continue;
                int ni = nx * H + nz;
                float ng = g[ci] + 1f;
                if (ng < g[ni]) {
                    g[ni] = ng;
                    parent[ni] = ci;
                    int f = (int) ((ng + h(nx, nz, goalX, goalZ)) * 10);
                    open.add(new int[]{f, ni});
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
        for (int r = 1; r <= 4; r++) {
            for (int x = cx - r; x <= cx + r; x++) {
                for (int z = cz - r; z <= cz + r; z++) {
                    if (x > 0 && z > 0 && x < W - 1 && z < H - 1 && !wall[x][z]) {
                        return new int[]{x, z};
                    }
                }
            }
        }
        return null;
    }

    private int h(int x, int z, int gx, int gz) {
        return Math.abs(x - gx) + Math.abs(z - gz);
    }

    private int clamp(int v, int lo, int hi) {
        return Math.max(lo, Math.min(hi, v));
    }
}
