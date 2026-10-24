#pragma once

#include <cstdint>
#include <random>
#include <string>
#include <vector>

#include "common.h"

namespace rts {

enum class UnitState : uint8_t { Idle = 0, Move, Attack, Gather, Build };
enum class TargetKind : uint8_t { None = 0, Unit, Building };

struct Unit {
    int id = 0;
    int team = TEAM_PLAYER;
    UnitType type = UnitType::Worker;
    Vec2 pos;
    Vec2 vel;
    float hp = 1.0f;
    float maxHp = 1.0f;
    float facing = -1.5707963f;

    UnitState state = UnitState::Idle;
    int targetId = 0;
    TargetKind targetKind = TargetKind::None;
    bool autoTarget = false;
    Vec2 dest;
    Vec2 returnDest;

    std::vector<Vec2> path;
    int wp = 0;
    Vec2 pathGoal;

    float attackCd = 0.0f;
    float scanCd = 0.0f;
    float repathTimer = 0.0f;
    float workTimer = 0.0f;
    float stuckTimer = 0.0f;
    Vec2 lastPos;

    int gatherResource = 0;
    int gatherPhase = 0;  // 0 去矿点 1 采矿 2 运回
    int carry = 0;
    int buildTarget = 0;

    float anim = 0.0f;
    bool alive = true;
};

struct Building {
    int id = 0;
    int team = TEAM_PLAYER;
    BldType type = BldType::HQ;
    int tx = 0;
    int ty = 0;
    float hp = 1.0f;
    float maxHp = 1.0f;
    bool complete = true;
    float progress = 1.0f;
    std::vector<UnitType> queue;
    float trainTimer = 0.0f;
    Vec2 rally;
    float attackCd = 0.0f;
    float flash = 0.0f;
    bool alive = true;

    Rect rect() const {
        const BldInfo& info = bldInfo(type);
        return {tx * (float)TILE, ty * (float)TILE, info.w * (float)TILE, info.h * (float)TILE};
    }
    Vec2 center() const { return rect().center(); }
};

struct Resource {
    int id = 0;
    int tx = 0;
    int ty = 0;
    int w = 2;
    int h = 2;
    int gold = 0;
    bool alive = true;

    Rect rect() const {
        return {tx * (float)TILE, ty * (float)TILE, w * (float)TILE, h * (float)TILE};
    }
    Vec2 center() const { return rect().center(); }
};

struct Projectile {
    Vec2 pos;
    Vec2 dir;
    float speed = 400.0f;
    float angle = 0.0f;
    int targetId = 0;
    TargetKind targetKind = TargetKind::None;
    float damage = 0.0f;
    int team = 0;
    bool alive = true;
};

struct Particle {
    Vec2 pos;
    Vec2 vel;
    float life = 0.0f;
    float maxLife = 1.0f;
    float size = 2.0f;
    float gravity = 0.0f;
    Color color = {255, 255, 255, 255};
    bool alive = true;
};

class Game {
public:
    Game() { reset(20261024u); }
    explicit Game(uint32_t seed) { reset(seed); }

    void reset(uint32_t seed);
    void update(float dt);

    Unit* unitById(int id);
    const Unit* unitById(int id) const;
    Building* buildingById(int id);
    const Building* buildingById(int id) const;
    Resource* resourceById(int id);
    const Resource* resourceById(int id) const;

    int countUnits(int team, UnitType type) const;
    int countUnits(int team) const;
    int popUsed(int team) const;
    int countBuildings(int team, BldType type) const;
    Building* teamHQ(int team);
    int enemyHQId(int team) const;
    Vec2 hqCenter(int team);

    bool canPlace(int team, BldType type, int tx, int ty, int builderId, std::string* err) const;
    bool placeBuilding(int team, BldType type, int tx, int ty, int builderId, std::string* err);
    bool trainUnit(int buildingId, UnitType type, std::string* err);
    void orderMove(const std::vector<int>& ids, Vec2 target);
    void orderAttack(const std::vector<int>& ids, int targetId, bool targetBuilding);
    void orderGather(const std::vector<int>& ids, int resourceId);
    void orderStop(const std::vector<int>& ids);

    // AI 需要的查询
    Resource* nearestResource(const Vec2& p, float maxDist) const;
    Vec2 findBuildSpot(int team, BldType type) const;

    std::vector<Unit> units;
    std::vector<Building> buildings;
    std::vector<Resource> resources;
    std::vector<Projectile> projectiles;
    std::vector<Particle> particles;
    BlockGrid blocked;
    std::vector<uint8_t> terrain;   // Terrain
    std::vector<uint8_t> mineTile;  // 0 = 无, 其它 = resource id
    int gold[2] = {250, 250};
    int mapVersion = 0;
    uint32_t seed = 1;
    float time = 0.0f;
    int state = 0;  // 0 进行中, 1 胜利, 2 失败
    bool aiEnabled[2] = {false, true};

private:
    int nextId_ = 1;
    std::mt19937 rng_{1337u};
    struct AIData {
        float think = 0.0f;
        float attackCd = 20.0f;
        int wave = 0;
        int targetType = 1;
    };
    AIData ai_[2];

    void generateMap(uint32_t seed);
    void rebuildBlocked();
    void markBuilding(const Building& b, bool blockedValue);
    int allocId() { return nextId_++; }

    void updateBuildings(float dt);
    void updateUnits(float dt);
    void updateProjectiles(float dt);
    void updateParticles(float dt);
    void separateUnits(float dt);
    void updateAI(int team, float dt);
    void checkGameOver();
    void removeDead();

    void updateUnit(Unit& u, float dt);
    void updateIdle(Unit& u, float dt);
    void updateMoveOrder(Unit& u, float dt);
    void updateAttackOrder(Unit& u, float dt);
    void updateGatherOrder(Unit& u, float dt);
    void updateBuildOrder(Unit& u, float dt);

    void requestPath(Unit& u, const Vec2& goal, bool force = false);
    bool followPath(Unit& u, float dt);
    void moveDirect(Unit& u, const Vec2& goal, float dt);
    void resolveMove(Unit& u, const Vec2& delta);

    bool acquireTarget(Unit& u, bool includeBuildings);
    void startAttack(Unit& u, int targetId, TargetKind kind, bool autoAcquired);
    void disengage(Unit& u);
    int findNearestEnemy(const Vec2& p, float maxDist, int team, bool includeBuildings,
                         TargetKind* kindOut, float* distOut) const;
    float distanceToTarget(const Unit& u) const;

    void damageUnit(Unit& target, float dmg, int fromTeam);
    void damageBuilding(Building& target, float dmg, int fromTeam);
    void destroyBuilding(Building& b);
    void depleteResource(Resource& r);
    void explode(const Vec2& p, Color color, int count, float power);
    void spawnHitParticles(const Vec2& p);

    Unit* spawnUnit(int team, UnitType type, Vec2 pos, Vec2 rally);
    Vec2 resourceStandPoint(const Resource& r, const Vec2& from) const;
    void issueMoveGroup(const std::vector<int>& ids, const Vec2& target);

    friend struct AITestProbe;
};

}  // namespace rts
