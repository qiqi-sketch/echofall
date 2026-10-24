#pragma once

#include <algorithm>
#include <cmath>
#include <cstdint>
#include <string>
#include <vector>

namespace rts {

constexpr int TILE = 32;                 // 单个地块像素
constexpr int MAP_W = 48;
constexpr int MAP_H = 48;
constexpr int MAP_PX_W = MAP_W * TILE;
constexpr int MAP_PX_H = MAP_H * TILE;
constexpr int POP_CAP = 50;
constexpr int TEAM_PLAYER = 0;
constexpr int TEAM_ENEMY = 1;

struct Vec2 {
    float x = 0.0f;
    float y = 0.0f;

    Vec2() = default;
    Vec2(float x_, float y_) : x(x_), y(y_) {}

    Vec2 operator+(const Vec2& o) const { return {x + o.x, y + o.y}; }
    Vec2 operator-(const Vec2& o) const { return {x - o.x, y - o.y}; }
    Vec2 operator*(float s) const { return {x * s, y * s}; }
    Vec2 operator/(float s) const { return {x / s, y / s}; }
    Vec2& operator+=(const Vec2& o) { x += o.x; y += o.y; return *this; }
    Vec2& operator-=(const Vec2& o) { x -= o.x; y -= o.y; return *this; }
};

inline float length(const Vec2& v) { return std::sqrt(v.x * v.x + v.y * v.y); }
inline float distance(const Vec2& a, const Vec2& b) { return length(a - b); }
inline float dot(const Vec2& a, const Vec2& b) { return a.x * b.x + a.y * b.y; }
inline Vec2 normalize(const Vec2& v) {
    float l = length(v);
    return l > 0.0001f ? Vec2{v.x / l, v.y / l} : Vec2{};
}
inline Vec2 lerp(const Vec2& a, const Vec2& b, float t) { return a + (b - a) * t; }

inline float clampf(float v, float lo, float hi) { return v < lo ? lo : (v > hi ? hi : v); }
inline int clampi(int v, int lo, int hi) { return v < lo ? lo : (v > hi ? hi : v); }
inline float dist2(const Vec2& a, const Vec2& b) {
    float dx = a.x - b.x, dy = a.y - b.y;
    return dx * dx + dy * dy;
}

struct Rect {
    float x = 0.0f, y = 0.0f, w = 0.0f, h = 0.0f;

    bool contains(float px, float py) const {
        return px >= x && py >= y && px <= x + w && py <= y + h;
    }
    bool contains(const Vec2& p) const { return contains(p.x, p.y); }
    bool intersects(const Rect& o) const {
        return x < o.x + o.w && x + w > o.x && y < o.y + o.h && y + h > o.y;
    }
    Vec2 center() const { return {x + w * 0.5f, y + h * 0.5f}; }
    Vec2 closestPoint(const Vec2& p) const {
        return {clampf(p.x, x, x + w), clampf(p.y, y, y + h)};
    }
};

struct Color {
    uint8_t r = 255, g = 255, b = 255, a = 255;
};

constexpr Color rgba(uint8_t r, uint8_t g, uint8_t b, uint8_t a = 255) { return Color{r, g, b, a}; }

using BlockGrid = std::vector<uint8_t>;

enum class UnitType : uint8_t { Worker = 0, Soldier = 1, Archer = 2 };
enum class BldType : uint8_t { HQ = 0, Barracks = 1, Tower = 2 };
enum class Terrain : uint8_t { Grass = 0, Dirt = 1, Water = 2, Tree = 3 };

struct UnitInfo {
    const char* name;
    float hp;
    float speed;
    float range;
    float damage;
    float cooldown;
    float radius;
    int cost;
    float trainTime;
    bool ranged;
};

inline const UnitInfo& unitInfo(UnitType t) {
    static const UnitInfo table[3] = {
        {"WORKER", 45.0f, 80.0f, 26.0f, 4.0f, 1.0f, 8.5f, 50, 6.0f, false},
        {"SOLDIER", 130.0f, 82.0f, 30.0f, 13.0f, 1.05f, 9.5f, 60, 7.0f, false},
        {"ARCHER", 75.0f, 76.0f, 155.0f, 9.0f, 1.55f, 8.5f, 70, 8.0f, true},
    };
    return table[(int)t];
}

struct BldInfo {
    const char* name;
    int w;
    int h;
    float hp;
    int cost;
    float buildTime;
    float range;
    float damage;
    float cooldown;
};

inline const BldInfo& bldInfo(BldType t) {
    static const BldInfo table[3] = {
        {"HEADQUARTERS", 4, 4, 1600.0f, 0, 0.0f, 0.0f, 0.0f, 0.0f},
        {"BARRACKS", 3, 3, 750.0f, 150, 11.0f, 0.0f, 0.0f, 0.0f},
        {"TOWER", 2, 2, 520.0f, 100, 8.0f, 190.0f, 15.0f, 1.2f},
    };
    return table[(int)t];
}

// UI 动作由渲染层布局、输入层执行
enum class UIAction : uint8_t {
    None = 0,
    TrainWorker,
    TrainSoldier,
    TrainArcher,
    BuildBarracks,
    BuildTower,
    Stop,
    CancelPlacement,
    Pause,
    Help,
    Restart,
};

}  // namespace rts
