#include "game.h"

#include <cmath>
#include <limits>
#include <random>

#include "path.h"

namespace rts {
namespace {

Vec2 standPointForRect(const BlockGrid& blocked, const Rect& r, const Vec2& from, float radius) {
    Vec2 best = r.center();
    float bestD = 1e9f;
    bool found = false;
    const int samples = 4;
    for (int side = 0; side < 4; ++side) {
        for (int i = 0; i < samples; ++i) {
            float t = (i + 0.5f) / (float)samples;
            Vec2 p;
            if (side == 0) p = {r.x + r.w * t, r.y - 14.0f};
            else if (side == 1) p = {r.x + r.w * t, r.y + r.h + 14.0f};
            else if (side == 2) p = {r.x - 14.0f, r.y + r.h * t};
            else p = {r.x + r.w + 14.0f, r.y + r.h * t};
            if (circleBlocked(blocked, p, radius)) continue;
            float d = distance(p, from);
            if (d < bestD) {
                bestD = d;
                best = p;
                found = true;
            }
        }
    }
    if (!found) return nearestFreePoint(blocked, r.center(), radius, 8);
    return best;
}

int tileIndex(int tx, int ty) { return ty * MAP_W + tx; }

}  // namespace

void Game::reset(uint32_t s) {
    seed = s;
    nextId_ = 1;
    time = 0.0f;
    state = 0;
    gold[0] = 250;
    gold[1] = 250;
    units.clear();
    buildings.clear();
    resources.clear();
    projectiles.clear();
    particles.clear();
    units.reserve(420);
    buildings.reserve(90);
    resources.reserve(32);
    ai_[0] = AIData{};
    ai_[1] = AIData{};
    ai_[0].attackCd = 30.0f;
    ai_[1].attackCd = 20.0f;

    generateMap(s);

    auto addHQ = [&](int team, int tx, int ty) {
        Building b;
        b.id = allocId();
        b.team = team;
        b.type = BldType::HQ;
        b.tx = tx;
        b.ty = ty;
        b.maxHp = bldInfo(BldType::HQ).hp;
        b.hp = b.maxHp;
        b.complete = true;
        b.progress = 1.0f;
        Vec2 toCenter = Vec2{MAP_PX_W * 0.5f, MAP_PX_H * 0.5f} - b.center();
        b.rally = b.center() + normalize(toCenter) * 110.0f;
        buildings.push_back(b);
        markBuilding(buildings.back(), true);
    };
    addHQ(TEAM_PLAYER, 6, 38);
    addHQ(TEAM_ENEMY, 38, 6);

    // 双方各 3 个农民
    for (int team = 0; team < 2; ++team) {
        Building* hq = teamHQ(team);
        if (!hq) continue;
        Vec2 dir = normalize(Vec2{MAP_PX_W * 0.5f, MAP_PX_H * 0.5f} - hq->center());
        for (int i = 0; i < 3; ++i) {
            Vec2 p = hq->center() + dir * (3.0f * TILE) + Vec2{(i - 1) * 28.0f, (i - 1) * 6.0f};
            p = nearestFreePoint(blocked, p, unitInfo(UnitType::Worker).radius, 6);
            spawnUnit(team, UnitType::Worker, p, p);
        }
    }
    mapVersion++;
}

void Game::generateMap(uint32_t s) {
    const int N = MAP_W * MAP_H;
    terrain.assign(N, (uint8_t)Terrain::Grass);
    mineTile.assign(N, 0);
    blocked.assign(N, 0);
    resources.clear();

    auto addMine = [&](int tx, int ty, int goldAmount) {
        Resource r;
        r.id = allocId();
        r.tx = tx;
        r.ty = ty;
        r.w = 2;
        r.h = 2;
        r.gold = goldAmount;
        resources.push_back(r);
        for (int y = ty; y < ty + r.h; ++y) {
            for (int x = tx; x < tx + r.w; ++x) {
                if (x < 0 || y < 0 || x >= MAP_W || y >= MAP_H) continue;
                blocked[tileIndex(x, y)] = 1;
                mineTile[tileIndex(x, y)] = (uint8_t)r.id;
            }
        }
    };
    addMine(13, 36, 3200);
    addMine(33, 10, 3200);
    addMine(18, 28, 2600);
    addMine(28, 18, 2600);
    addMine(22, 22, 4000);
    addMine(24, 24, 4000);

    auto nearBaseTile = [](int x, int y) {
        return (std::abs(x - 8) <= 7 && std::abs(y - 40) <= 6) ||
               (std::abs(x - 39) <= 7 && std::abs(y - 8) <= 6);
    };
    auto nearMine = [&](int x, int y) {
        for (const Resource& r : resources) {
            if (x >= r.tx - 3 && x <= r.tx + r.w + 2 && y >= r.ty - 3 && y <= r.ty + r.h + 2) {
                return true;
            }
        }
        return false;
    };

    // 镜像生成树林（保留出生点与矿区周围空地）
    std::mt19937 rng(s ^ 0x9e3779b9u);
    for (int y = 0; y < MAP_H; ++y) {
        for (int x = 0; x < MAP_W / 2; ++x) {
            int mx = MAP_W - 1 - x;
            int my = MAP_H - 1 - y;
            if (blocked[tileIndex(x, y)]) continue;
            if (nearBaseTile(x, y) || nearMine(x, y)) continue;
            float v = std::sin((x + (float)(s & 7u)) * 0.61f) * std::cos(y * 0.47f) +
                      0.65f * std::sin((x + y) * 0.23f + (float)(s % 11u));
            if (v > 0.32f && (rng() % 100u) < 62u) {
                terrain[tileIndex(x, y)] = (uint8_t)Terrain::Tree;
                terrain[tileIndex(mx, my)] = (uint8_t)Terrain::Tree;
            }
        }
    }

    // 两处对称小湖
    auto addPond = [&](int cx, int cy, float rx, float ry) {
        for (int y = 0; y < MAP_H; ++y) {
            for (int x = 0; x < MAP_W; ++x) {
                float dx = (x + 0.5f - cx) / rx;
                float dy = (y + 0.5f - cy) / ry;
                if (dx * dx + dy * dy <= 1.0f && !nearBaseTile(x, y) && !nearMine(x, y)) {
                    terrain[tileIndex(x, y)] = (uint8_t)Terrain::Water;
                }
            }
        }
    };
    addPond(19, 14, 4.5f, 2.8f);
    addPond(28, 33, 4.5f, 2.8f);

    // 基地与矿区附近的土路
    for (int y = 0; y < MAP_H; ++y) {
        for (int x = 0; x < MAP_W; ++x) {
            if (terrain[tileIndex(x, y)] != (uint8_t)Terrain::Grass) continue;
            bool dirt = false;
            if ((std::abs(x - 8) <= 5 && std::abs(y - 40) <= 4) ||
                (std::abs(x - 39) <= 5 && std::abs(y - 8) <= 4)) {
                dirt = true;
            }
            for (const Resource& r : resources) {
                float dx = x + 0.5f - (r.tx + r.w * 0.5f);
                float dy = y + 0.5f - (r.ty + r.h * 0.5f);
                if (dx * dx + dy * dy < 7.5f) dirt = true;
            }
            if (dirt) terrain[tileIndex(x, y)] = (uint8_t)Terrain::Dirt;
        }
    }

    rebuildBlocked();
}

void Game::rebuildBlocked() {
    const int N = MAP_W * MAP_H;
    blocked.assign(N, 0);
    for (int i = 0; i < N; ++i) {
        Terrain t = (Terrain)terrain[(size_t)i];
        if (t == Terrain::Tree || t == Terrain::Water) blocked[(size_t)i] = 1;
        if (mineTile[(size_t)i]) blocked[(size_t)i] = 1;
    }
    for (const Building& b : buildings) {
        if (b.alive) markBuilding(b, true);
    }
}

void Game::markBuilding(const Building& b, bool blockedValue) {
    const BldInfo& info = bldInfo(b.type);
    for (int y = b.ty; y < b.ty + info.h; ++y) {
        for (int x = b.tx; x < b.tx + info.w; ++x) {
            if (x < 0 || y < 0 || x >= MAP_W || y >= MAP_H) continue;
            blocked[tileIndex(x, y)] = blockedValue ? 1 : 0;
        }
    }
}

Unit* Game::unitById(int id) {
    for (Unit& u : units) {
        if (u.id == id && u.alive) return &u;
    }
    return nullptr;
}
const Unit* Game::unitById(int id) const {
    for (const Unit& u : units) {
        if (u.id == id && u.alive) return &u;
    }
    return nullptr;
}
Building* Game::buildingById(int id) {
    for (Building& b : buildings) {
        if (b.id == id && b.alive) return &b;
    }
    return nullptr;
}
const Building* Game::buildingById(int id) const {
    for (const Building& b : buildings) {
        if (b.id == id && b.alive) return &b;
    }
    return nullptr;
}
Resource* Game::resourceById(int id) {
    for (Resource& r : resources) {
        if (r.id == id) return &r;
    }
    return nullptr;
}
const Resource* Game::resourceById(int id) const {
    for (const Resource& r : resources) {
        if (r.id == id) return &r;
    }
    return nullptr;
}

int Game::countUnits(int team, UnitType type) const {
    int n = 0;
    for (const Unit& u : units) {
        if (u.alive && u.team == team && u.type == type) ++n;
    }
    return n;
}
int Game::countUnits(int team) const {
    int n = 0;
    for (const Unit& u : units) {
        if (u.alive && u.team == team) ++n;
    }
    return n;
}
int Game::popUsed(int team) const {
    int n = countUnits(team);
    for (const Building& b : buildings) {
        if (b.alive && b.team == team) n += (int)b.queue.size();
    }
    return n;
}
int Game::countBuildings(int team, BldType type) const {
    int n = 0;
    for (const Building& b : buildings) {
        if (b.alive && b.team == team && b.type == type) ++n;
    }
    return n;
}
Building* Game::teamHQ(int team) {
    for (Building& b : buildings) {
        if (b.alive && b.team == team && b.type == BldType::HQ) return &b;
    }
    return nullptr;
}
int Game::enemyHQId(int team) const {
    for (const Building& b : buildings) {
        if (b.alive && b.team != team && b.type == BldType::HQ) return b.id;
    }
    return 0;
}
Vec2 Game::hqCenter(int team) {
    Building* hq = teamHQ(team);
    return hq ? hq->center() : Vec2{MAP_PX_W * 0.5f, MAP_PX_H * 0.5f};
}

bool Game::canPlace(int team, BldType type, int tx, int ty, int builderId,
                    std::string* err) const {
    auto fail = [&](const char* msg) {
        if (err) *err = msg;
        return false;
    };
    if (type == BldType::HQ) return fail("CANNOT BUILD HQ");
    const BldInfo& info = bldInfo(type);
    if (tx < 0 || ty < 0 || tx + info.w > MAP_W || ty + info.h > MAP_H) {
        return fail("OUT OF BOUNDS");
    }
    for (int y = ty; y < ty + info.h; ++y) {
        for (int x = tx; x < tx + info.w; ++x) {
            if (blocked[(size_t)tileIndex(x, y)]) return fail("AREA BLOCKED");
        }
    }
    Rect r{tx * (float)TILE, ty * (float)TILE, info.w * (float)TILE, info.h * (float)TILE};
    for (const Unit& u : units) {
        if (!u.alive || u.id == builderId) continue;
        float rad = unitInfo(u.type).radius;
        if (distance(u.pos, r.closestPoint(u.pos)) < rad + 2.0f) return fail("UNITS IN THE WAY");
    }
    const Unit* builder = unitById(builderId);
    if (builderId != 0) {
        if (!builder || builder->team != team || builder->type != UnitType::Worker) {
            return fail("SELECT A WORKER");
        }
        if (distance(builder->pos, r.closestPoint(builder->pos)) > 430.0f) {
            return fail("TOO FAR AWAY");
        }
    }
    if (gold[team] < info.cost) return fail("NOT ENOUGH GOLD");
    return true;
}

bool Game::placeBuilding(int team, BldType type, int tx, int ty, int builderId,
                         std::string* err) {
    if (!canPlace(team, type, tx, ty, builderId, err)) return false;
    const BldInfo& info = bldInfo(type);
    gold[team] -= info.cost;

    Building b;
    b.id = allocId();
    b.team = team;
    b.type = type;
    b.tx = tx;
    b.ty = ty;
    b.maxHp = info.hp;
    b.hp = b.maxHp * 0.2f;
    b.complete = false;
    b.progress = 0.0f;
    Vec2 toCenter = Vec2{MAP_PX_W * 0.5f, MAP_PX_H * 0.5f} - b.center();
    b.rally = b.center() + normalize(toCenter) * 100.0f;
    buildings.push_back(b);
    markBuilding(buildings.back(), true);

    Unit* worker = unitById(builderId);
    if (worker) {
        worker->state = UnitState::Build;
        worker->buildTarget = b.id;
        worker->targetId = 0;
        worker->targetKind = TargetKind::None;
        worker->autoTarget = false;
        worker->path.clear();
        worker->wp = 0;
        requestPath(*worker, b.center(), true);
    }
    return true;
}

bool Game::trainUnit(int buildingId, UnitType type, std::string* err) {
    auto fail = [&](const char* msg) {
        if (err) *err = msg;
        return false;
    };
    Building* b = buildingById(buildingId);
    if (!b || !b->complete) return fail("BUILDING NOT READY");
    if (type == UnitType::Worker && b->type != BldType::HQ) return fail("ONLY HQ MAKES WORKERS");
    if (type != UnitType::Worker && b->type != BldType::Barracks) return fail("NEED A BARRACKS");
    if ((int)b->queue.size() >= 5) return fail("QUEUE FULL");
    const UnitInfo& info = unitInfo(type);
    if (gold[b->team] < info.cost) return fail("NOT ENOUGH GOLD");
    if (popUsed(b->team) >= POP_CAP) return fail("POPULATION FULL");
    gold[b->team] -= info.cost;
    if (b->queue.empty()) b->trainTimer = info.trainTime;
    b->queue.push_back(type);
    return true;
}

void Game::orderMove(const std::vector<int>& ids, Vec2 target) {
    std::vector<Unit*> sel;
    for (int id : ids) {
        Unit* u = unitById(id);
        if (u) sel.push_back(u);
    }
    if (sel.empty()) return;
    std::sort(sel.begin(), sel.end(), [&](const Unit* a, const Unit* b) {
        return dist2(a->pos, target) < dist2(b->pos, target);
    });

    int n = (int)sel.size();
    int side = (int)std::ceil(std::sqrt((double)n));
    std::vector<Vec2> offsets;
    offsets.reserve(n);
    for (int i = 0; i < n; ++i) {
        int col = i % side;
        int row = i / side;
        offsets.push_back({(col - (side - 1) * 0.5f) * 31.0f, (row - (side - 1) * 0.5f) * 31.0f});
    }
    std::sort(offsets.begin(), offsets.end(),
              [](const Vec2& a, const Vec2& b) { return length(a) < length(b); });

    for (int i = 0; i < n; ++i) {
        Unit* u = sel[(size_t)i];
        Vec2 dest = target + offsets[(size_t)i];
        dest = nearestFreePoint(blocked, dest, unitInfo(u->type).radius, 6);
        u->state = UnitState::Move;
        u->dest = dest;
        u->targetId = 0;
        u->targetKind = TargetKind::None;
        u->autoTarget = false;
        u->buildTarget = 0;
        u->path.clear();
        u->wp = 0;
        u->stuckTimer = 0.0f;
        requestPath(*u, dest, true);
    }
}

void Game::issueMoveGroup(const std::vector<int>& ids, const Vec2& target) { orderMove(ids, target); }

void Game::orderAttack(const std::vector<int>& ids, int targetId, bool targetBuilding) {
    for (int id : ids) {
        Unit* u = unitById(id);
        if (!u) continue;
        startAttack(*u, targetId, targetBuilding ? TargetKind::Building : TargetKind::Unit, false);
    }
}

void Game::orderGather(const std::vector<int>& ids, int resourceId) {
    Resource* r = resourceById(resourceId);
    if (!r || r->gold <= 0) return;
    for (int id : ids) {
        Unit* u = unitById(id);
        if (!u || u->type != UnitType::Worker) continue;
        u->state = UnitState::Gather;
        u->gatherResource = r->id;
        u->gatherPhase = 0;
        u->targetId = 0;
        u->targetKind = TargetKind::None;
        u->autoTarget = false;
        u->buildTarget = 0;
        u->path.clear();
        u->wp = 0;
        requestPath(*u, resourceStandPoint(*r, u->pos), true);
    }
}

void Game::orderStop(const std::vector<int>& ids) {
    for (int id : ids) {
        Unit* u = unitById(id);
        if (!u) continue;
        u->state = UnitState::Idle;
        u->targetId = 0;
        u->targetKind = TargetKind::None;
        u->autoTarget = false;
        u->buildTarget = 0;
        u->dest = u->pos;
        u->path.clear();
        u->wp = 0;
    }
}

Resource* Game::nearestResource(const Vec2& p, float maxDist) const {
    Resource* best = nullptr;
    float bestD = maxDist;
    for (const Resource& r : resources) {
        if (!r.alive || r.gold <= 0) continue;
        float d = distance(p, r.center());
        if (d < bestD) {
            bestD = d;
            best = const_cast<Resource*>(&r);
        }
    }
    return best;
}

Vec2 Game::findBuildSpot(int team, BldType type) const {
    Vec2 c{MAP_PX_W * 0.5f, MAP_PX_H * 0.5f};
    for (const Building& b : buildings) {
        if (b.alive && b.team == team && b.type == BldType::HQ) {
            c = b.center();
            break;
        }
    }
    const BldInfo& info = bldInfo(type);
    int cx = (int)(c.x / TILE);
    int cy = (int)(c.y / TILE);
    for (int r = 2; r <= 11; ++r) {
        for (int dy = -r; dy <= r; ++dy) {
            for (int dx = -r; dx <= r; ++dx) {
                if (std::max(std::abs(dx), std::abs(dy)) != r) continue;
                int tx = cx + dx - info.w / 2;
                int ty = cy + dy - info.h / 2;
                if (canPlace(team, type, tx, ty, 0, nullptr)) {
                    return {(tx + info.w * 0.5f) * TILE, (ty + info.h * 0.5f) * TILE};
                }
            }
        }
    }
    return {-1.0f, -1.0f};
}

// ---------------------------------------------------------------------------
// 主循环
// ---------------------------------------------------------------------------

void Game::update(float dt) {
    dt = clampf(dt, 0.0f, 0.05f);
    if (state != 0) {
        updateParticles(dt);
        return;
    }
    time += dt;
    updateBuildings(dt);
    updateUnits(dt);
    updateProjectiles(dt);
    separateUnits(dt);
    updateParticles(dt);
    if (aiEnabled[0]) updateAI(0, dt);
    if (aiEnabled[1]) updateAI(1, dt);
    removeDead();
    checkGameOver();
}

void Game::updateBuildings(float dt) {
    for (Building& b : buildings) {
        if (!b.alive) continue;
        b.flash = std::max(0.0f, b.flash - dt * 4.0f);

        if (!b.complete) {
            int builders = 0;
            Rect r = b.rect();
            for (const Unit& u : units) {
                if (!u.alive || u.team != b.team || u.state != UnitState::Build) continue;
                if (u.buildTarget != b.id) continue;
                if (distance(u.pos, r.closestPoint(u.pos)) < 92.0f) ++builders;
            }
            if (builders > 0) {
                float rate = 1.0f / std::max(1.0f, bldInfo(b.type).buildTime);
                float speedup = 1.0f + 0.5f * (float)(builders - 1);
                b.progress = std::min(1.0f, b.progress + dt * rate * speedup);
                b.hp = b.maxHp * (0.2f + 0.8f * b.progress);
                if (b.progress >= 1.0f) {
                    b.complete = true;
                    b.hp = b.maxHp;
                    explode(b.center(), rgba(255, 214, 120), 10, 0.8f);
                }
            }
            continue;
        }

        if (!b.queue.empty()) {
            b.trainTimer -= dt;
            if (b.trainTimer <= 0.0f) {
                UnitType t = b.queue.front();
                b.queue.erase(b.queue.begin());
                Rect r = b.rect();
                Vec2 spawn = nearestFreePoint(
                    blocked, {r.x + r.w * 0.5f, r.y + r.h + 18.0f},
                    unitInfo(t).radius, 8);
                spawnUnit(b.team, t, spawn, b.rally);
                if (!b.queue.empty()) {
                    b.trainTimer = unitInfo(b.queue.front()).trainTime;
                } else {
                    b.trainTimer = 0.0f;
                }
            }
        }

        if (b.type == BldType::Tower) {
            b.attackCd -= dt;
            if (b.attackCd <= 0.0f) {
                TargetKind kind = TargetKind::None;
                float dist = 0.0f;
                int id = findNearestEnemy(b.center(), bldInfo(BldType::Tower).range, b.team, true,
                                          &kind, &dist);
                if (id) {
                    Projectile p;
                    p.pos = b.center();
                    p.targetId = id;
                    p.targetKind = kind;
                    p.damage = bldInfo(BldType::Tower).damage;
                    p.speed = 380.0f;
                    p.team = b.team;
                    projectiles.push_back(p);
                    b.attackCd = bldInfo(BldType::Tower).cooldown;
                }
            }
        }
    }
}

void Game::updateUnits(float dt) {
    for (size_t i = 0; i < units.size(); ++i) {
        Unit& u = units[i];
        if (!u.alive) continue;
        u.anim += dt;
        u.attackCd = std::max(0.0f, u.attackCd - dt);
        u.scanCd -= dt;
        u.repathTimer -= dt;

        switch (u.state) {
            case UnitState::Idle: updateIdle(u, dt); break;
            case UnitState::Move: updateMoveOrder(u, dt); break;
            case UnitState::Attack: updateAttackOrder(u, dt); break;
            case UnitState::Gather: updateGatherOrder(u, dt); break;
            case UnitState::Build: updateBuildOrder(u, dt); break;
        }

        if ((u.state == UnitState::Idle || u.state == UnitState::Move) &&
            u.type != UnitType::Worker && u.scanCd <= 0.0f) {
            u.scanCd = 0.4f;
            acquireTarget(u, true);
        }
    }
}

void Game::updateIdle(Unit& u, float /*dt*/) {
    if (u.type != UnitType::Worker) return;
    if (u.scanCd > 0.0f) return;
    u.scanCd = 0.9f;
    Resource* r = nearestResource(u.pos, 1000.0f);
    if (r) {
        u.state = UnitState::Gather;
        u.gatherResource = r->id;
        u.gatherPhase = 0;
        u.path.clear();
        u.wp = 0;
        requestPath(u, resourceStandPoint(*r, u.pos), true);
    }
}

void Game::updateMoveOrder(Unit& u, float dt) {
    float d = distance(u.pos, u.dest);
    if (d < 16.0f) {
        u.state = UnitState::Idle;
        u.path.clear();
        u.wp = 0;
        return;
    }
    if (u.path.empty() || distance(u.pathGoal, u.dest) > 44.0f) {
        if (u.repathTimer <= 0.0f) {
            requestPath(u, u.dest, true);
            u.repathTimer = 0.55f;
            if (u.path.empty()) {
                u.state = UnitState::Idle;
                u.dest = u.pos;
                return;
            }
        }
    }
    followPath(u, dt);
    if (u.stuckTimer > 1.4f) {
        u.path.clear();
        u.wp = 0;
        requestPath(u, u.dest, true);
        u.stuckTimer = 0.0f;
        if (u.path.empty()) {
            u.state = UnitState::Idle;
            u.dest = u.pos;
        }
    }
}

void Game::updateAttackOrder(Unit& u, float dt) {
    const UnitInfo& info = unitInfo(u.type);
    Unit* tu = nullptr;
    Building* tb = nullptr;
    Vec2 targetPos;
    float reach = info.range;
    if (u.targetKind == TargetKind::Unit) {
        tu = unitById(u.targetId);
        if (!tu || !tu->alive) {
            disengage(u);
            return;
        }
        targetPos = tu->pos;
        reach += unitInfo(tu->type).radius;
    } else if (u.targetKind == TargetKind::Building) {
        tb = buildingById(u.targetId);
        if (!tb || !tb->alive) {
            disengage(u);
            return;
        }
        targetPos = tb->rect().closestPoint(u.pos);
        reach += 4.0f;
    } else {
        disengage(u);
        return;
    }

    if (u.autoTarget && distance(u.pos, u.returnDest) > 400.0f) {
        disengage(u);
        return;
    }

    float dist = distance(u.pos, targetPos);
    if (dist > reach * 0.92f) {
        if (u.repathTimer <= 0.0f) {
            if (u.path.empty() || distance(u.pathGoal, targetPos) > 34.0f) {
                requestPath(u, targetPos, true);
            }
            u.repathTimer = 0.55f;
        }
        if (u.path.empty()) {
            moveDirect(u, targetPos, dt);
        } else {
            followPath(u, dt);
        }
        return;
    }

    u.path.clear();
    u.wp = 0;
    Vec2 dir = targetPos - u.pos;
    if (length(dir) > 0.5f) u.facing = std::atan2(dir.y, dir.x);
    if (u.attackCd <= 0.0f) {
        u.attackCd = info.cooldown;
        if (info.ranged) {
            Projectile p;
            p.pos = u.pos + normalize(dir) * 10.0f;
            p.targetId = u.targetId;
            p.targetKind = u.targetKind;
            p.damage = info.damage;
            p.speed = 470.0f;
            p.team = u.team;
            projectiles.push_back(p);
            explode(p.pos, rgba(255, 238, 170), 3, 0.4f);
        } else {
            if (tu) {
                damageUnit(*tu, info.damage, u.team);
                spawnHitParticles(tu->pos);
            } else if (tb) {
                damageBuilding(*tb, info.damage, u.team);
                spawnHitParticles(targetPos);
            }
        }
    }
}

void Game::updateGatherOrder(Unit& u, float dt) {
    Resource* r = resourceById(u.gatherResource);
    if (!r || !r->alive || r->gold <= 0) {
        Resource* nr = nearestResource(u.pos, 1400.0f);
        if (!nr) {
            u.state = UnitState::Idle;
            return;
        }
        u.gatherResource = nr->id;
        u.gatherPhase = 0;
        u.path.clear();
        u.wp = 0;
        requestPath(u, resourceStandPoint(*nr, u.pos), true);
        r = nr;
    }

    Vec2 mine = r->center();
    if (u.gatherPhase == 0) {
        Vec2 stand = resourceStandPoint(*r, u.pos);
        if (distance(u.pos, mine) < 55.0f || distance(u.pos, stand) < 14.0f) {
            u.gatherPhase = 1;
            u.workTimer = 2.0f;
            u.path.clear();
            u.wp = 0;
            u.facing = std::atan2(mine.y - u.pos.y, mine.x - u.pos.x);
            return;
        }
        if (u.path.empty() && u.repathTimer <= 0.0f) {
            requestPath(u, stand, true);
            u.repathTimer = 0.8f;
        }
        if (!followPath(u, dt) && distance(u.pos, stand) > 26.0f) {
            if (u.repathTimer <= 0.0f) {
                requestPath(u, stand, true);
                u.repathTimer = 0.9f;
            }
        }
        return;
    }

    if (u.gatherPhase == 1) {
        u.workTimer -= dt;
        if (u.workTimer <= 0.0f) {
            int amount = std::min(10, r->gold);
            if (amount <= 0) {
                u.gatherPhase = 0;
                return;
            }
            r->gold -= amount;
            u.carry = amount;
            u.gatherPhase = 2;
            Building* hq = teamHQ(u.team);
            if (!hq) {
                u.state = UnitState::Idle;
                return;
            }
            u.path.clear();
            u.wp = 0;
            requestPath(u, standPointForRect(blocked, hq->rect(), u.pos,
                                            unitInfo(u.type).radius),
                        true);
            if (r->gold <= 0) depleteResource(*r);
        }
        return;
    }

    Building* hq = teamHQ(u.team);
    if (!hq) {
        u.state = UnitState::Idle;
        return;
    }
    if (distance(u.pos, hq->rect().closestPoint(u.pos)) < 58.0f) {
        gold[u.team] += u.carry;
        u.carry = 0;
        u.gatherPhase = 0;
        u.path.clear();
        u.wp = 0;
        Resource* nr = nearestResource(u.pos, 1400.0f);
        if (nr) {
            u.gatherResource = nr->id;
            requestPath(u, resourceStandPoint(*nr, u.pos), true);
        } else {
            u.state = UnitState::Idle;
        }
        return;
    }
    if (u.path.empty() && u.repathTimer <= 0.0f) {
        requestPath(u, standPointForRect(blocked, hq->rect(), u.pos, unitInfo(u.type).radius),
                    true);
        u.repathTimer = 0.8f;
    }
    followPath(u, dt);
}

void Game::updateBuildOrder(Unit& u, float dt) {
    Building* b = buildingById(u.buildTarget);
    if (!b || !b->alive || b->complete) {
        u.state = UnitState::Idle;
        u.buildTarget = 0;
        return;
    }
    Rect r = b->rect();
    Vec2 closest = r.closestPoint(u.pos);
    if (distance(u.pos, closest) < 72.0f) {
        u.path.clear();
        u.wp = 0;
        Vec2 dir = b->center() - u.pos;
        if (length(dir) > 0.5f) u.facing = std::atan2(dir.y, dir.x);
        return;
    }
    if (u.path.empty() && u.repathTimer <= 0.0f) {
        requestPath(u, b->center(), true);
        u.repathTimer = 0.8f;
    }
    followPath(u, dt);
}

void Game::requestPath(Unit& u, const Vec2& goal, bool force) {
    if (!force && !u.path.empty() && distance(u.pathGoal, goal) < 26.0f) return;
    u.path = findPath(blocked, u.pos, goal, unitInfo(u.type).radius);
    u.wp = 0;
    u.pathGoal = goal;
    u.stuckTimer = 0.0f;
}

bool Game::followPath(Unit& u, float dt) {
    if (u.path.empty()) return false;
    const UnitInfo& info = unitInfo(u.type);
    while (u.wp < (int)u.path.size()) {
        Vec2 target = u.path[(size_t)u.wp];
        float d = distance(u.pos, target);
        if (d < 9.0f) {
            ++u.wp;
            continue;
        }
        Vec2 dir = normalize(target - u.pos);
        Vec2 delta = dir * (info.speed * dt);
        if (length(delta) > d) delta = dir * d;
        Vec2 before = u.pos;
        resolveMove(u, delta);
        u.facing = std::atan2(dir.y, dir.x);
        if (distance(before, u.pos) < 0.15f) {
            u.stuckTimer += dt;
        } else {
            u.stuckTimer = 0.0f;
        }
        return true;
    }
    u.path.clear();
    u.wp = 0;
    return false;
}

void Game::moveDirect(Unit& u, const Vec2& goal, float dt) {
    Vec2 dir = normalize(goal - u.pos);
    if (length(dir) < 0.001f) return;
    resolveMove(u, dir * (unitInfo(u.type).speed * dt));
    u.facing = std::atan2(dir.y, dir.x);
}

void Game::resolveMove(Unit& u, const Vec2& delta) {
    float r = unitInfo(u.type).radius;
    Vec2 np = u.pos;
    np.x += delta.x;
    if (!circleBlocked(blocked, np, r)) u.pos.x = np.x;
    np = u.pos;
    np.y += delta.y;
    if (!circleBlocked(blocked, np, r)) u.pos.y = np.y;
    u.pos.x = clampf(u.pos.x, r, MAP_PX_W - r);
    u.pos.y = clampf(u.pos.y, r, MAP_PX_H - r);
}

bool Game::acquireTarget(Unit& u, bool includeBuildings) {
    TargetKind kind = TargetKind::None;
    float dist = 0.0f;
    int id = findNearestEnemy(u.pos, 205.0f, u.team, includeBuildings, &kind, &dist);
    if (!id) return false;
    startAttack(u, id, kind, true);
    return true;
}

void Game::startAttack(Unit& u, int targetId, TargetKind kind, bool autoAcquired) {
    if (u.state != UnitState::Attack) {
        u.returnDest = (u.state == UnitState::Move) ? u.dest : u.pos;
    }
    u.state = UnitState::Attack;
    u.targetId = targetId;
    u.targetKind = kind;
    u.autoTarget = autoAcquired;
    u.buildTarget = 0;
    u.path.clear();
    u.wp = 0;
    u.repathTimer = 0.0f;
}

void Game::disengage(Unit& u) {
    u.targetId = 0;
    u.targetKind = TargetKind::None;
    bool wasAuto = u.autoTarget;
    u.autoTarget = false;
    u.path.clear();
    u.wp = 0;
    if (wasAuto && distance(u.pos, u.returnDest) > 18.0f) {
        u.state = UnitState::Move;
        u.dest = u.returnDest;
        requestPath(u, u.dest, true);
    } else {
        u.state = UnitState::Idle;
        u.dest = u.pos;
    }
}

int Game::findNearestEnemy(const Vec2& p, float maxDist, int team, bool includeBuildings,
                           TargetKind* kindOut, float* distOut) const {
    float best = maxDist;
    int bestId = 0;
    TargetKind bestKind = TargetKind::None;
    for (const Unit& u : units) {
        if (!u.alive || u.team == team) continue;
        float d = distance(p, u.pos) - unitInfo(u.type).radius;
        if (d < best) {
            best = d;
            bestId = u.id;
            bestKind = TargetKind::Unit;
        }
    }
    if (includeBuildings) {
        for (const Building& b : buildings) {
            if (!b.alive || b.team == team) continue;
            float d = distance(p, b.rect().closestPoint(p));
            if (d < best) {
                best = d;
                bestId = b.id;
                bestKind = TargetKind::Building;
            }
        }
    }
    if (kindOut) *kindOut = bestKind;
    if (distOut) *distOut = best;
    return bestId;
}

void Game::damageUnit(Unit& target, float dmg, int fromTeam) {
    if (!target.alive) return;
    target.hp -= dmg;
    if (target.hp <= 0.0f) {
        target.alive = false;
        Color c = target.team == TEAM_PLAYER ? rgba(90, 150, 235) : rgba(235, 95, 80);
        explode(target.pos, c, 12, 1.0f);
    }
    (void)fromTeam;
}

void Game::damageBuilding(Building& target, float dmg, int fromTeam) {
    if (!target.alive) return;
    target.hp -= dmg;
    target.flash = 1.0f;
    if (target.hp <= 0.0f) destroyBuilding(target);
    (void)fromTeam;
}

void Game::destroyBuilding(Building& b) {
    if (!b.alive) return;
    b.alive = false;
    markBuilding(b, false);
    explode(b.center(), rgba(255, 160, 70), 26, 1.6f);
}

void Game::depleteResource(Resource& r) {
    if (!r.alive) return;
    r.alive = false;
    for (int y = r.ty; y < r.ty + r.h; ++y) {
        for (int x = r.tx; x < r.tx + r.w; ++x) {
            if (x < 0 || y < 0 || x >= MAP_W || y >= MAP_H) continue;
            if (mineTile[tileIndex(x, y)] == r.id) {
                mineTile[tileIndex(x, y)] = 0;
                blocked[tileIndex(x, y)] = 0;
            }
        }
    }
    explode(r.center(), rgba(220, 190, 90), 8, 0.7f);
}

void Game::explode(const Vec2& p, Color color, int count, float power) {
    std::uniform_real_distribution<float> angle(0.0f, 6.2831853f);
    std::uniform_real_distribution<float> speed(30.0f, 130.0f * power);
    std::uniform_real_distribution<float> life(0.35f, 0.9f);
    for (int i = 0; i < count; ++i) {
        Particle q;
        float a = angle(rng_);
        float s = speed(rng_);
        q.pos = p;
        q.vel = {std::cos(a) * s, std::sin(a) * s};
        q.maxLife = life(rng_);
        q.life = q.maxLife;
        q.size = 2.0f + 2.0f * power;
        q.gravity = 45.0f;
        q.color = color;
        particles.push_back(q);
    }
}

void Game::spawnHitParticles(const Vec2& p) {
    std::uniform_real_distribution<float> angle(0.0f, 6.2831853f);
    std::uniform_real_distribution<float> speed(20.0f, 90.0f);
    for (int i = 0; i < 4; ++i) {
        Particle q;
        float a = angle(rng_);
        float s = speed(rng_);
        q.pos = p;
        q.vel = {std::cos(a) * s, std::sin(a) * s};
        q.maxLife = 0.25f;
        q.life = 0.25f;
        q.size = 2.0f;
        q.color = rgba(255, 210, 120);
        particles.push_back(q);
    }
}

Unit* Game::spawnUnit(int team, UnitType type, Vec2 pos, Vec2 rally) {
    Unit u;
    u.id = allocId();
    u.team = team;
    u.type = type;
    u.maxHp = unitInfo(type).hp;
    u.hp = u.maxHp;
    u.pos = nearestFreePoint(blocked, pos, unitInfo(type).radius, 8);
    u.dest = rally;
    u.state = UnitState::Move;
    Vec2 dir = Vec2{MAP_PX_W * 0.5f, MAP_PX_H * 0.5f} - u.pos;
    if (length(dir) > 0.5f) u.facing = std::atan2(dir.y, dir.x);
    u.path.clear();
    u.wp = 0;
    requestPath(u, rally, true);
    units.push_back(u);
    return &units.back();
}

Vec2 Game::resourceStandPoint(const Resource& r, const Vec2& from) const {
    return standPointForRect(blocked, r.rect(), from, 9.0f);
}

void Game::updateProjectiles(float dt) {
    for (Projectile& p : projectiles) {
        if (!p.alive) continue;
        Vec2 tp;
        if (p.targetKind == TargetKind::Unit) {
            Unit* t = unitById(p.targetId);
            if (!t || !t->alive) {
                p.alive = false;
                continue;
            }
            tp = t->pos;
        } else {
            Building* t = buildingById(p.targetId);
            if (!t || !t->alive) {
                p.alive = false;
                continue;
            }
            tp = t->rect().closestPoint(p.pos);
        }
        Vec2 d = tp - p.pos;
        float len = length(d);
        if (len < 12.0f) {
            if (p.targetKind == TargetKind::Unit) {
                Unit* t = unitById(p.targetId);
                if (t) {
                    damageUnit(*t, p.damage, p.team);
                    spawnHitParticles(t->pos);
                }
            } else {
                Building* t = buildingById(p.targetId);
                if (t) {
                    damageBuilding(*t, p.damage, p.team);
                    spawnHitParticles(tp);
                }
            }
            p.alive = false;
            continue;
        }
        Vec2 dir = normalize(d);
        float step = p.speed * dt;
        if (step > len) step = len;
        p.pos += dir * step;
        p.angle = std::atan2(dir.y, dir.x);
    }
}

void Game::updateParticles(float dt) {
    for (Particle& p : particles) {
        if (!p.alive) continue;
        p.life -= dt;
        if (p.life <= 0.0f) {
            p.alive = false;
            continue;
        }
        p.vel.y += p.gravity * dt;
        p.pos += p.vel * dt;
    }
}

void Game::separateUnits(float dt) {
    (void)dt;
    int n = (int)units.size();
    for (int i = 0; i < n; ++i) {
        Unit& a = units[(size_t)i];
        if (!a.alive) continue;
        float ra = unitInfo(a.type).radius;
        for (int j = i + 1; j < n; ++j) {
            Unit& b = units[(size_t)j];
            if (!b.alive) continue;
            float rb = unitInfo(b.type).radius;
            float minD = ra + rb;
            float d2 = dist2(a.pos, b.pos);
            if (d2 >= minD * minD || d2 < 0.0001f) continue;
            float d = std::sqrt(d2);
            Vec2 push = normalize(a.pos - b.pos) * ((minD - d) * 0.5f);
            Vec2 pa = a.pos + push;
            Vec2 pb = b.pos - push;
            if (!circleBlocked(blocked, pa, ra)) a.pos = pa;
            if (!circleBlocked(blocked, pb, rb)) b.pos = pb;
        }
    }
}

void Game::removeDead() {
    units.erase(std::remove_if(units.begin(), units.end(),
                               [](const Unit& u) { return !u.alive; }),
                units.end());
    buildings.erase(std::remove_if(buildings.begin(), buildings.end(),
                                   [](const Building& b) { return !b.alive; }),
                    buildings.end());
    resources.erase(std::remove_if(resources.begin(), resources.end(),
                                   [](const Resource& r) { return !r.alive; }),
                    resources.end());
    projectiles.erase(std::remove_if(projectiles.begin(), projectiles.end(),
                                     [](const Projectile& p) { return !p.alive; }),
                      projectiles.end());
    particles.erase(std::remove_if(particles.begin(), particles.end(),
                                   [](const Particle& p) { return !p.alive; }),
                    particles.end());
}

void Game::checkGameOver() {
    if (state != 0) return;
    if (countBuildings(TEAM_ENEMY, BldType::HQ) == 0) {
        state = 1;
    } else if (countBuildings(TEAM_PLAYER, BldType::HQ) == 0) {
        state = 2;
    }
}

void Game::updateAI(int team, float dt) {
    AIData& ai = ai_[team];
    ai.attackCd -= dt;
    ai.think -= dt;
    if (ai.think > 0.0f) return;
    ai.think = 0.65f;

    Building* hq = teamHQ(team);
    if (!hq) return;
    const Vec2 base = hq->center();

    // 基地遇袭时全军回防
    TargetKind threatKind = TargetKind::None;
    float threatDist = 0.0f;
    int threat = findNearestEnemy(base, 480.0f, team, false, &threatKind, &threatDist);
    if (threat) {
        std::vector<int> defenders;
        for (const Unit& u : units) {
            if (!u.alive || u.team != team) continue;
            if (u.type == UnitType::Worker) continue;
            if (u.state == UnitState::Attack && u.targetId == threat) continue;
            defenders.push_back(u.id);
        }
        if (!defenders.empty()) orderAttack(defenders, threat, false);
    }

    int workers = countUnits(team, UnitType::Worker);
    int army = countUnits(team, UnitType::Soldier) + countUnits(team, UnitType::Archer);
    int barracks = countBuildings(team, BldType::Barracks);
    int towers = countBuildings(team, BldType::Tower);

    // 经济：优先补农民
    if (workers < 6 && hq->complete && gold[team] >= 50 && (int)hq->queue.size() < 2) {
        trainUnit(hq->id, UnitType::Worker, nullptr);
    }

    // 第一个兵营
    if (barracks == 0 && gold[team] >= 200) {
        int builder = 0;
        for (const Unit& u : units) {
            if (!u.alive || u.team != team || u.type != UnitType::Worker) continue;
            if (u.state == UnitState::Idle || u.state == UnitState::Gather) {
                builder = u.id;
                if (u.state == UnitState::Idle) break;
            }
        }
        Vec2 spot = findBuildSpot(team, BldType::Barracks);
        if (builder && spot.x >= 0.0f) {
            const BldInfo& bi = bldInfo(BldType::Barracks);
            int tx = (int)std::lround(spot.x / TILE - bi.w * 0.5f);
            int ty = (int)std::lround(spot.y / TILE - bi.h * 0.5f);
            placeBuilding(team, BldType::Barracks, tx, ty, builder, nullptr);
        }
    }

    // 兵营持续出兵
    if (barracks > 0 && gold[team] >= 70 && army < 20) {
        for (const Building& b : buildings) {
            if (!b.alive || b.team != team || b.type != BldType::Barracks) continue;
            if (!b.complete || !b.queue.empty()) continue;
            UnitType t = ai.targetType == 1 ? UnitType::Soldier : UnitType::Archer;
            if (trainUnit(b.id, t, nullptr)) {
                ai.targetType = (t == UnitType::Soldier) ? 2 : 1;
            }
            break;
        }
    }

    // 造一座防御塔
    if (barracks > 0 && towers == 0 && gold[team] >= 110) {
        int builder = 0;
        for (const Unit& u : units) {
            if (!u.alive || u.team != team || u.type != UnitType::Worker) continue;
            if (u.state == UnitState::Idle) {
                builder = u.id;
                break;
            }
        }
        Vec2 spot = findBuildSpot(team, BldType::Tower);
        if (builder && spot.x >= 0.0f) {
            const BldInfo& bi = bldInfo(BldType::Tower);
            int tx = (int)std::lround(spot.x / TILE - bi.w * 0.5f);
            int ty = (int)std::lround(spot.y / TILE - bi.h * 0.5f);
            placeBuilding(team, BldType::Tower, tx, ty, builder, nullptr);
        }
    }

    // 集结进攻
    if (ai.attackCd <= 0.0f && army >= 5 + ai.wave * 2) {
        int ehq = enemyHQId(team);
        if (ehq) {
            std::vector<int> attackers;
            for (const Unit& u : units) {
                if (!u.alive || u.team != team) continue;
                if (u.type == UnitType::Worker) continue;
                if (u.state == UnitState::Attack) continue;
                attackers.push_back(u.id);
            }
            if ((int)attackers.size() >= 3) {
                Building* target = buildingById(ehq);
                if (target) {
                    orderMove(attackers, target->center());
                    ai.attackCd = 26.0f;
                    ++ai.wave;
                }
            }
        }
    }
}

}  // namespace rts
