#include <cmath>
#include <cstdio>
#include <string>
#include <vector>

#include "game.h"

using namespace rts;

namespace {

bool checkInvariants(const Game& g, const char* tag) {
    for (const Unit& u : g.units) {
        if (!std::isfinite(u.pos.x) || !std::isfinite(u.pos.y)) {
            std::printf("[FAIL] %s: unit %d has non-finite position\n", tag, u.id);
            return false;
        }
        if (u.pos.x < 0 || u.pos.y < 0 || u.pos.x > MAP_PX_W || u.pos.y > MAP_PX_H) {
            std::printf("[FAIL] %s: unit %d out of bounds (%.1f, %.1f)\n", tag, u.id, u.pos.x,
                        u.pos.y);
            return false;
        }
        if (u.hp <= 0.0f) {
            std::printf("[FAIL] %s: dead unit %d still alive\n", tag, u.id);
            return false;
        }
    }
    for (const Building& b : g.buildings) {
        if (b.hp <= 0.0f) {
            std::printf("[FAIL] %s: dead building %d still alive\n", tag, b.id);
            return false;
        }
        if (b.tx < 0 || b.ty < 0 || b.tx + bldInfo(b.type).w > MAP_W ||
            b.ty + bldInfo(b.type).h > MAP_H) {
            std::printf("[FAIL] %s: building %d out of map\n", tag, b.id);
            return false;
        }
    }
    int queued = 0;
    for (const Building& b : g.buildings) queued += (int)b.queue.size();
    if (g.popUsed(0) > POP_CAP || g.popUsed(1) > POP_CAP) {
        std::printf("[FAIL] %s: population over cap\n", tag);
        return false;
    }
    if (queued > 40) {
        std::printf("[FAIL] %s: absurd queue size %d\n", tag, queued);
        return false;
    }
    return true;
}

struct Result {
    bool ok = true;
    int state = 0;
    float seconds = 0.0f;
    int gold0 = 0, gold1 = 0;
    int units0 = 0, units1 = 0;
    int bld0 = 0, bld1 = 0;
    int minesLeft = 0;
};

Result runScenario(uint32_t seed, float maxSeconds) {
    Game game(seed);
    game.aiEnabled[0] = true;
    game.aiEnabled[1] = true;

    Result r;
    const float dt = 1.0f / 60.0f;
    int steps = (int)(maxSeconds / dt);
    for (int i = 0; i < steps; ++i) {
        game.update(dt);
        if (i % 600 == 0 && !checkInvariants(game, "tick")) {
            r.ok = false;
            break;
        }
        if (game.state != 0) break;
    }
    r.ok = r.ok && checkInvariants(game, "final");
    r.state = game.state;
    r.seconds = game.time;
    r.gold0 = game.gold[0];
    r.gold1 = game.gold[1];
    r.units0 = (int)game.countUnits(0);
    r.units1 = (int)game.countUnits(1);
    r.bld0 = (int)game.buildings.size();
    r.bld1 = 0;
    for (const Building& b : game.buildings) {
        if (b.team == 1) ++r.bld1;
    }
    for (const Resource& res : game.resources) {
        if (res.alive && res.gold > 0) ++r.minesLeft;
    }
    return r;
}

}  // namespace

int main() {
    std::printf("Frontline self-test (headless)\n");
    std::printf("%-6s %-8s %-7s %-7s %-7s %-7s %-7s %-7s\n", "seed", "result", "time", "gold0",
                "gold1", "units0", "units1", "mines");

    bool allOk = true;
    int finished = 0;
    const uint32_t seeds[3] = {1u, 20261024u, 777u};
    for (uint32_t seed : seeds) {
        Result r = runScenario(seed, 900.0f);
        const char* state = r.state == 1 ? "P1_WIN" : (r.state == 2 ? "P2_WIN" : "TIMEOUT");
        std::printf("%-6u %-8s %-7.1f %-7d %-7d %-7d %-7d %-7d\n", seed, state, r.seconds, r.gold0,
                    r.gold1, r.units0, r.units1, r.minesLeft);
        if (!r.ok) allOk = false;
        if (r.state != 0) ++finished;

        // 经济必须有进展：至少有一方攒到过第二座建筑或 500 金币
        if (r.gold0 + r.gold1 < 200 && r.units0 + r.units1 < 8) {
            std::printf("[FAIL] seed %u: no economic progress\n", seed);
            allOk = false;
        }
    }

    if (finished == 0) {
        std::printf("[WARN] no match reached a decisive result within 15 minutes\n");
    }
    if (!allOk) {
        std::printf("SELFTEST FAILED\n");
        return 1;
    }
    std::printf("SELFTEST OK\n");
    return 0;
}
