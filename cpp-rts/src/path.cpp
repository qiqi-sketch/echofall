#include "path.h"

#include <array>
#include <limits>
#include <queue>

namespace rts {
namespace {

inline bool blockedTile(const BlockGrid& blocked, int tx, int ty) {
    return tx < 0 || ty < 0 || tx >= MAP_W || ty >= MAP_H || blocked[(size_t)ty * MAP_W + tx] != 0;
}

struct OpenNode {
    float f;
    int idx;
    bool operator>(const OpenNode& o) const { return f > o.f; }
};

int nearestOpenTile(const BlockGrid& blocked, int tx, int ty, int maxRing) {
    if (!blockedTile(blocked, tx, ty)) return ty * MAP_W + tx;
    for (int r = 1; r <= maxRing; ++r) {
        for (int dy = -r; dy <= r; ++dy) {
            for (int dx = -r; dx <= r; ++dx) {
                if (std::max(std::abs(dx), std::abs(dy)) != r) continue;
                int nx = tx + dx, ny = ty + dy;
                if (!blockedTile(blocked, nx, ny)) return ny * MAP_W + nx;
            }
        }
    }
    return -1;
}

int nearestOpenTileNear(const BlockGrid& blocked, float wx, float wy, int maxRing) {
    int tx = clampi((int)(wx / TILE), 0, MAP_W - 1);
    int ty = clampi((int)(wy / TILE), 0, MAP_H - 1);
    return nearestOpenTile(blocked, tx, ty, maxRing);
}

}  // namespace

bool circleBlocked(const BlockGrid& blocked, const Vec2& p, float radius) {
    int x0 = clampi((int)std::floor((p.x - radius) / TILE), 0, MAP_W - 1);
    int x1 = clampi((int)std::floor((p.x + radius) / TILE), 0, MAP_W - 1);
    int y0 = clampi((int)std::floor((p.y - radius) / TILE), 0, MAP_H - 1);
    int y1 = clampi((int)std::floor((p.y + radius) / TILE), 0, MAP_H - 1);
    if (p.x - radius < 0 || p.y - radius < 0 || p.x + radius > MAP_PX_W || p.y + radius > MAP_PX_H) {
        return true;
    }
    for (int ty = y0; ty <= y1; ++ty) {
        for (int tx = x0; tx <= x1; ++tx) {
            if (blocked[(size_t)ty * MAP_W + tx]) return true;
        }
    }
    return false;
}

Vec2 nearestFreePoint(const BlockGrid& blocked, const Vec2& p, float radius, int maxRing) {
    if (!circleBlocked(blocked, p, radius)) return p;
    int tx = clampi((int)(p.x / TILE), 0, MAP_W - 1);
    int ty = clampi((int)(p.y / TILE), 0, MAP_H - 1);
    for (int r = 1; r <= maxRing; ++r) {
        for (int dy = -r; dy <= r; ++dy) {
            for (int dx = -r; dx <= r; ++dx) {
                if (std::max(std::abs(dx), std::abs(dy)) != r) continue;
                Vec2 c{(tx + dx + 0.5f) * TILE, (ty + dy + 0.5f) * TILE};
                if (!circleBlocked(blocked, c, radius)) return c;
            }
        }
    }
    return p;
}

bool lineClear(const BlockGrid& blocked, const Vec2& a, const Vec2& b, float radius) {
    Vec2 d = b - a;
    float len = length(d);
    if (len < 1.0f) return true;
    int steps = (int)(len / 7.0f) + 1;
    Vec2 n = normalize(d);
    Vec2 perp{-n.y * radius, n.x * radius};
    for (int i = 0; i <= steps; ++i) {
        Vec2 p = a + d * ((float)i / (float)steps);
        if (circleBlocked(blocked, p, radius)) return false;
        if (circleBlocked(blocked, p + perp, 1.0f)) return false;
        if (circleBlocked(blocked, p - perp, 1.0f)) return false;
    }
    return true;
}

std::vector<Vec2> findPath(const BlockGrid& blocked, const Vec2& start, const Vec2& goal,
                           float radius) {
    std::vector<Vec2> result;
    if (lineClear(blocked, start, goal, radius)) {
        result.push_back(goal);
        return result;
    }

    int sx = clampi((int)(start.x / TILE), 0, MAP_W - 1);
    int sy = clampi((int)(start.y / TILE), 0, MAP_H - 1);
    int gi = nearestOpenTileNear(blocked, goal.x, goal.y, 6);
    if (gi < 0) return result;
    int gx = gi % MAP_W, gy = gi / MAP_W;

    int startIdx = sy * MAP_W + sx;
    if (blockedTile(blocked, sx, sy)) {
        int si = nearestOpenTileNear(blocked, start.x, start.y, 4);
        if (si < 0) return result;
        startIdx = si;
        sx = si % MAP_W;
        sy = si / MAP_W;
    }
    int goalIdx = gy * MAP_W + gx;
    if (startIdx == goalIdx) {
        result.push_back({(gx + 0.5f) * TILE, (gy + 0.5f) * TILE});
        return result;
    }

    constexpr int N = MAP_W * MAP_H;
    static thread_local std::vector<float> gCost;
    static thread_local std::vector<int> parent;
    static thread_local std::vector<uint8_t> closed;
    gCost.assign(N, std::numeric_limits<float>::infinity());
    parent.assign(N, -1);
    closed.assign(N, 0);

    std::priority_queue<OpenNode, std::vector<OpenNode>, std::greater<OpenNode>> open;
    auto heuristic = [&](int idx) {
        int x = idx % MAP_W, y = idx / MAP_W;
        float dx = (float)std::abs(x - gx);
        float dy = (float)std::abs(y - gy);
        return (dx + dy) + (std::sqrt(2.0f) - 2.0f) * std::min(dx, dy);
    };

    gCost[startIdx] = 0.0f;
    open.push({heuristic(startIdx), startIdx});
    static const int DX[8] = {1, -1, 0, 0, 1, 1, -1, -1};
    static const int DY[8] = {0, 0, 1, -1, 1, -1, 1, -1};

    bool found = false;
    while (!open.empty()) {
        OpenNode cur = open.top();
        open.pop();
        if (closed[cur.idx]) continue;
        closed[cur.idx] = 1;
        if (cur.idx == goalIdx) {
            found = true;
            break;
        }
        int cx = cur.idx % MAP_W, cy = cur.idx / MAP_W;
        for (int d = 0; d < 8; ++d) {
            int nx = cx + DX[d], ny = cy + DY[d];
            if (blockedTile(blocked, nx, ny)) continue;
            if (d >= 4) {
                // 禁止贴着墙角走斜线
                if (blockedTile(blocked, cx + DX[d], cy) || blockedTile(blocked, cx, cy + DY[d])) {
                    continue;
                }
            }
            int ni = ny * MAP_W + nx;
            if (closed[ni]) continue;
            float step = (d >= 4) ? 1.41421356f : 1.0f;
            float ng = gCost[cur.idx] + step;
            if (ng < gCost[ni]) {
                gCost[ni] = ng;
                parent[ni] = cur.idx;
                open.push({ng + heuristic(ni), ni});
            }
        }
    }
    if (!found) return result;

    std::vector<Vec2> raw;
    for (int idx = goalIdx; idx != startIdx && idx >= 0; idx = parent[idx]) {
        raw.push_back({(idx % MAP_W + 0.5f) * TILE, (idx / MAP_W + 0.5f) * TILE});
    }
    std::reverse(raw.begin(), raw.end());

    // 拉直路径
    Vec2 cur = start;
    size_t i = 0;
    while (i < raw.size()) {
        size_t j = raw.size() - 1;
        for (; j > i; --j) {
            if (lineClear(blocked, cur, raw[j], radius)) break;
        }
        result.push_back(raw[j]);
        cur = raw[j];
        i = j + 1;
    }
    return result;
}

}  // namespace rts
