#pragma once

#include "common.h"

namespace rts {

bool circleBlocked(const BlockGrid& blocked, const Vec2& p, float radius);
Vec2 nearestFreePoint(const BlockGrid& blocked, const Vec2& p, float radius = 9.0f, int maxRing = 10);
bool lineClear(const BlockGrid& blocked, const Vec2& a, const Vec2& b, float radius);

// A* 八方向寻路；返回世界坐标路径点（不含起点），目标不可达时返回空。
std::vector<Vec2> findPath(const BlockGrid& blocked, const Vec2& start, const Vec2& goal,
                           float radius = 9.0f);

}  // namespace rts
