package com.echofall.game;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.PriorityQueue;
import java.util.Random;

public class MapGrid {
    public static final int W = 40;
    public static final int H = 40;
    public final boolean[][] wall = new boolean[W][H];
    private final Random rnd = new Random(12345);

    public MapGrid() {
        for (int x = 0; x < W; x++) {
            wall[x][0] = true;
            wall[x][H - 1] = true;
        }
        for (int z = 0; z < H; z++) {
            wall[0][z] = true;
            wall[W - 1][z] = true;
        }

        Random r = new Random(20261023);
        for (int i = 0; i < 70; i++) {
            int bx = 2 + r.nextInt(W - 4);
            int bz = 2 + r.nextInt(H - 4);
            int bw = 1 + r.nextInt(3);
            int bh = 1 + r.nextInt(3);
            for (int x = bx; x < Math.min(W - 1, bx + bw); x++) {
                for (int z = bz; z < Math.min(H - 1, bz + bh); z++) {
                    wall[x][z] = true;
                }
            }
        }

        clearArea(3, 3);
        clearArea(W - 4, 3);
        clearArea(3, H - 4);
        clearArea(W - 4, H - 4);

        // 中央通道，保证 AI 能绕图走动
        for (int x = W / 2 - 1; x <= W / 2 + 1; x++) {
            for (int z = 2; z < H - 2; z++) {
                wall[x][z] = false;
            }
        }
    }

    private void clearArea(int cx, int cz) {
        for (int x = cx - 2; x <= cx + 2; x++) {
            for (int z = cz - 2; z <= cz + 2; z++) {
                if (x > 0 && x < W - 1 && z > 0 && z < H - 1) {
                    wall[x][z] = false;
                }
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

    public float[] randomFree() {
        for (int i = 0; i < 300; i++) {
            int x = 1 + rnd.nextInt(W - 2);
            int z = 1 + rnd.nextInt(H - 2);
            if (!wall[x][z]) return new float[]{x + 0.5f, z + 0.5f};
        }
        return new float[]{W / 2f, H / 2f};
    }

    public List<float[]> findPath(float sx, float sz, float tx, float tz) {
        List<float[]> result = new ArrayList<>();
        int startX = clamp((int) Math.floor(sx), 0, W - 1);
        int startZ = clamp((int) Math.floor(sz), 0, H - 1);
        int goalX = clamp((int) Math.floor(tx), 0, W - 1);
        int goalZ = clamp((int) Math.floor(tz), 0, H - 1);
        if (wall[startX][startZ] || wall[goalX][goalZ]) return result;

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

    private int h(int x, int z, int gx, int gz) {
        return Math.abs(x - gx) + Math.abs(z - gz);
    }

    private int clamp(int v, int lo, int hi) {
        return Math.max(lo, Math.min(hi, v));
    }
}
