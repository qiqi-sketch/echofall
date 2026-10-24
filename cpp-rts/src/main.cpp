// FRONTLINE - 手机横屏 RTS
// 入口：窗口、输入（触摸/鼠标/键盘）、固定步长主循环。

#include <SDL.h>

#include <algorithm>
#include <cmath>
#include <cstdio>
#include <string>
#include <vector>

#include "game.h"
#include "render.h"

namespace {

using namespace rts;

constexpr float kMinZoom = 0.62f;
constexpr float kMaxZoom = 2.6f;
constexpr float kTapSlop = 14.0f;   // 虚拟像素，小于该位移视为点击
constexpr float kTapTime = 0.65f;   // 秒

struct Pointer {
    SDL_FingerID id = 0;
    Vec2 pos;
};

class App {
public:
    bool init();
    void run();
    void shutdown();

private:
    Game game_;
    Renderer renderer_;
    Camera cam_;
    UIState ui_;
    int builderId_ = 0;

    bool running_ = true;
    bool mouseDown_ = false;
    bool mouseDragging_ = false;
    Vec2 mouseStart_;
    Vec2 mousePos_;

    std::vector<Pointer> fingers_;
    Vec2 tapStart_;
    Uint32 tapStarted_ = 0;
    bool gestureMoved_ = false;
    bool gestureIsTap_ = false;
    bool multiGesture_ = false;
    bool panningMinimap_ = false;

    float lastPinchDist_ = 0.0f;
    Vec2 lastCentroid_;
    bool pinchValid_ = false;

    void handleEvent(const SDL_Event& e);
    void handleFingerDown(const SDL_TouchFingerEvent& f);
    void handleFingerMotion(const SDL_TouchFingerEvent& f);
    void handleFingerUp(const SDL_TouchFingerEvent& f);

    void handleTap(const Vec2& p);
    void handlePress(const Vec2& p);
    void handleDrag(const Vec2& p);
    void handleRelease(const Vec2& p, bool wasTap);
    void performAction(UIAction action);

    void selectAtWorld(const Vec2& w);
    void selectBox(const Rect& boxScreen);
    void beginPlacement(BldType type);
    void tryPlace(const Vec2& world);
    void updatePlacementPreview(const Vec2& world);

    void toast(const std::string& msg);
    void cleanupSelection();
    void clampCamera();
    void panCamera(const Vec2& screenDelta);
    void zoomAt(const Vec2& screenPoint, float factor);
    void restart();
};

bool App::init() {
    SDL_SetHint(SDL_HINT_ORIENTATIONS, "LandscapeLeft LandscapeRight");
    SDL_SetHint(SDL_HINT_TOUCH_MOUSE_EVENTS, "0");
    SDL_SetHint(SDL_HINT_RENDER_SCALE_QUALITY, "1");
#ifdef __ANDROID__
    SDL_SetHint(SDL_HINT_ANDROID_BLOCK_ON_PAUSE, "0");
#endif

    if (SDL_Init(SDL_INIT_VIDEO | SDL_INIT_TIMER) != 0) {
        std::fprintf(stderr, "SDL_Init failed: %s\n", SDL_GetError());
        return false;
    }

    Uint32 flags = SDL_WINDOW_SHOWN | SDL_WINDOW_ALLOW_HIGHDPI;
#ifdef __ANDROID__
    flags |= SDL_WINDOW_FULLSCREEN;
#else
    flags |= SDL_WINDOW_RESIZABLE;
#endif
    SDL_Window* window = SDL_CreateWindow("FRONTLINE", SDL_WINDOWPOS_CENTERED,
                                          SDL_WINDOWPOS_CENTERED, 1280, 720, flags);
    if (!window) {
        std::fprintf(stderr, "SDL_CreateWindow failed: %s\n", SDL_GetError());
        return false;
    }
    if (!renderer_.init(window)) {
        std::fprintf(stderr, "Renderer init failed: %s\n", SDL_GetError());
        return false;
    }

    game_.reset(20261024u);
    game_.aiEnabled[0] = false;
    game_.aiEnabled[1] = true;

    Building* hq = game_.teamHQ(TEAM_PLAYER);
    if (hq) cam_.center = hq->center() + Vec2{120.0f, -40.0f};
    clampCamera();
    return true;
}

void App::shutdown() { renderer_.shutdown(); }

void App::toast(const std::string& msg) {
    ui_.toast = msg;
    ui_.toastTimer = 1.8f;
}

void App::clampCamera() {
    float halfW = renderer_.vw() / (2.0f * cam_.zoom);
    float halfH = renderer_.vh() / (2.0f * cam_.zoom);
    if (halfW * 2.0f >= MAP_PX_W)
        cam_.center.x = MAP_PX_W * 0.5f;
    else
        cam_.center.x = clampf(cam_.center.x, halfW, MAP_PX_W - halfW);
    if (halfH * 2.0f >= MAP_PX_H)
        cam_.center.y = MAP_PX_H * 0.5f;
    else
        cam_.center.y = clampf(cam_.center.y, halfH, MAP_PX_H - halfH);
    cam_.zoom = clampf(cam_.zoom, kMinZoom, kMaxZoom);
}

void App::panCamera(const Vec2& screenDelta) {
    cam_.center.x -= screenDelta.x / cam_.zoom;
    cam_.center.y -= screenDelta.y / cam_.zoom;
    clampCamera();
}

void App::zoomAt(const Vec2& screenPoint, float factor) {
    Vec2 before = renderer_.screenToWorld(screenPoint, cam_);
    cam_.zoom = clampf(cam_.zoom * factor, kMinZoom, kMaxZoom);
    Vec2 after = renderer_.screenToWorld(screenPoint, cam_);
    cam_.center += before - after;
    clampCamera();
}

void App::restart() {
    game_.reset((uint32_t)SDL_GetTicks() ^ 0x9e3779b9u);
    game_.aiEnabled[0] = false;
    game_.aiEnabled[1] = true;
    ui_ = UIState{};
    builderId_ = 0;
    Building* hq = game_.teamHQ(TEAM_PLAYER);
    if (hq) cam_.center = hq->center() + Vec2{120.0f, -40.0f};
    cam_.zoom = 1.2f;
    clampCamera();
}

// ---------------------------------------------------------------- 输入

void App::handleEvent(const SDL_Event& e) {
    switch (e.type) {
        case SDL_QUIT:
            running_ = false;
            break;
        case SDL_APP_WILLENTERBACKGROUND:
            ui_.paused = true;
            break;
        case SDL_FINGERDOWN:
            handleFingerDown(e.tfinger);
            break;
        case SDL_FINGERMOTION:
            handleFingerMotion(e.tfinger);
            break;
        case SDL_FINGERUP:
            handleFingerUp(e.tfinger);
            break;
        case SDL_MOUSEBUTTONDOWN:
            if (e.button.button == SDL_BUTTON_LEFT) {
                Vec2 p = renderer_.windowToVirtual((float)e.button.x, (float)e.button.y);
                mouseDown_ = true;
                mouseDragging_ = false;
                mouseStart_ = p;
                mousePos_ = p;
                handlePress(p);
            }
            break;
        case SDL_MOUSEMOTION:
            if (mouseDown_) {
                Vec2 p = renderer_.windowToVirtual((float)e.motion.x, (float)e.motion.y);
                mousePos_ = p;
                if (!mouseDragging_ && distance(p, mouseStart_) > kTapSlop) {
                    mouseDragging_ = true;
                    if (!ui_.placement && !panningMinimap_ && !renderer_.inMinimap(mouseStart_) &&
                        !renderer_.inHud(mouseStart_)) {
                        ui_.boxStart = mouseStart_;
                        ui_.boxActive = true;
                    }
                }
                handleDrag(p);
            } else if (ui_.placement && !ui_.intro && !ui_.help && !ui_.paused) {
                Vec2 p = renderer_.windowToVirtual((float)e.motion.x, (float)e.motion.y);
                if (!renderer_.inHud(p))
                    updatePlacementPreview(renderer_.screenToWorld(p, cam_));
            }
            break;
        case SDL_MOUSEBUTTONUP:
            if (e.button.button == SDL_BUTTON_LEFT && mouseDown_) {
                Vec2 p = renderer_.windowToVirtual((float)e.button.x, (float)e.button.y);
                mouseDown_ = false;
                handleRelease(p, !mouseDragging_);
            }
            break;
        case SDL_MOUSEWHEEL: {
            int mx = 0, my = 0;
            SDL_GetMouseState(&mx, &my);
            Vec2 p = renderer_.windowToVirtual((float)mx, (float)my);
            zoomAt(p, e.wheel.y > 0 ? 1.12f : 0.89f);
            break;
        }
        case SDL_WINDOWEVENT:
            if (e.window.event == SDL_WINDOWEVENT_SIZE_CHANGED ||
                e.window.event == SDL_WINDOWEVENT_RESIZED) {
                renderer_.refreshSize();
                clampCamera();
            }
            break;
        case SDL_KEYDOWN:
            switch (e.key.keysym.sym) {
                case SDLK_ESCAPE:
                    if (ui_.placement) {
                        ui_.placement = false;
                        ui_.placeShow = false;
                    } else if (ui_.help) {
                        ui_.help = false;
                    } else if (ui_.intro) {
                        ui_.intro = false;
                    } else if (!ui_.selected.empty() || ui_.selectedBuilding) {
                        ui_.selected.clear();
                        ui_.selectedBuilding = 0;
                    } else {
                        ui_.paused = !ui_.paused;
                    }
                    break;
                case SDLK_SPACE:
                    ui_.paused = !ui_.paused;
                    break;
                case SDLK_r:
                    if (game_.state != 0) restart();
                    break;
                case SDLK_F1:
                    ui_.help = !ui_.help;
                    break;
                default:
                    break;
            }
            break;
        default:
            break;
    }
}

void App::handleFingerDown(const SDL_TouchFingerEvent& f) {
    Vec2 p{f.x * renderer_.vw(), f.y * renderer_.vh()};
    for (Pointer& fp : fingers_) {
        if (fp.id == f.fingerId) {
            fp.pos = p;
            return;
        }
    }
    fingers_.push_back(Pointer{f.fingerId, p});

    if (fingers_.size() == 1) {
        gestureIsTap_ = true;
        gestureMoved_ = false;
        multiGesture_ = false;
        tapStart_ = p;
        tapStarted_ = SDL_GetTicks();
        handlePress(p);
    } else {
        // 多指：取消点击候选，进入平移/缩放
        gestureIsTap_ = false;
        gestureMoved_ = true;
        multiGesture_ = true;
        ui_.boxActive = false;
        panningMinimap_ = false;
        pinchValid_ = false;
    }
}

void App::handleFingerMotion(const SDL_TouchFingerEvent& f) {
    Vec2 p{f.x * renderer_.vw(), f.y * renderer_.vh()};
    for (Pointer& fp : fingers_) {
        if (fp.id == f.fingerId) {
            fp.pos = p;
            break;
        }
    }

    if (fingers_.size() >= 2) {
        Vec2 centroid{0, 0};
        for (const Pointer& fp : fingers_) centroid += fp.pos;
        centroid = centroid / (float)fingers_.size();
        float d = distance(fingers_[0].pos, fingers_[1].pos);

        if (!pinchValid_) {
            lastCentroid_ = centroid;
            lastPinchDist_ = d;
            pinchValid_ = true;
            return;
        }
        Vec2 delta = centroid - lastCentroid_;
        panCamera(delta);
        if (lastPinchDist_ > 1.0f && d > 1.0f) {
            float ratio = d / lastPinchDist_;
            if (std::fabs(ratio - 1.0f) > 0.001f) zoomAt(centroid, ratio);
        }
        lastCentroid_ = centroid;
        lastPinchDist_ = d;
        return;
    }

    // 单指
    if (fingers_.size() == 1) {
        if (gestureIsTap_ && distance(p, tapStart_) > kTapSlop) {
            gestureMoved_ = true;
            if (!ui_.placement && !panningMinimap_) {
                ui_.boxStart = tapStart_;
                ui_.boxActive = true;
            }
        }
        handleDrag(p);
    }
}

void App::handleFingerUp(const SDL_TouchFingerEvent& f) {
    Vec2 p{f.x * renderer_.vw(), f.y * renderer_.vh()};
    size_t before = fingers_.size();
    bool wasLast = false;
    for (size_t i = 0; i < fingers_.size(); ++i) {
        if (fingers_[i].id == f.fingerId) {
            fingers_.erase(fingers_.begin() + (long)i);
            wasLast = (before == 1);
            break;
        }
    }
    if (before >= 2) {
        pinchValid_ = false;
        gestureIsTap_ = false;
        multiGesture_ = true;
        return;
    }
    if (!wasLast) return;

    if (multiGesture_) {
        multiGesture_ = false;
        gestureIsTap_ = false;
        gestureMoved_ = false;
        return;
    }

    Uint32 elapsed = SDL_GetTicks() - tapStarted_;
    bool tap = gestureIsTap_ && !gestureMoved_ && elapsed < (Uint32)(kTapTime * 1000.0f);
    handleRelease(p, tap);
    gestureIsTap_ = false;
    gestureMoved_ = false;
}

void App::handlePress(const Vec2& p) {
    panningMinimap_ = false;

    if (ui_.intro || ui_.help || ui_.paused || game_.state != 0) return;

    if (renderer_.inMinimap(p)) {
        panningMinimap_ = true;
        gestureIsTap_ = true;
        gestureMoved_ = false;
        cam_.center = renderer_.minimapToWorld(p);
        clampCamera();
        return;
    }

    if (ui_.placement) {
        updatePlacementPreview(renderer_.screenToWorld(p, cam_));
    }
}

void App::handleDrag(const Vec2& p) {
    if (ui_.intro || ui_.help || ui_.paused || game_.state != 0) return;

    if (panningMinimap_) {
        cam_.center = renderer_.minimapToWorld(p);
        clampCamera();
        return;
    }

    if (ui_.placement) {
        updatePlacementPreview(renderer_.screenToWorld(p, cam_));
        return;
    }

    if (ui_.boxActive) ui_.boxEnd = p;
}

void App::handleRelease(const Vec2& p, bool wasTap) {
    if (ui_.boxActive) {
        Rect box{std::min(ui_.boxStart.x, ui_.boxEnd.x), std::min(ui_.boxStart.y, ui_.boxEnd.y),
                 std::fabs(ui_.boxEnd.x - ui_.boxStart.x),
                 std::fabs(ui_.boxEnd.y - ui_.boxStart.y)};
        ui_.boxActive = false;
        if (box.w > 8.0f || box.h > 8.0f) {
            selectBox(box);
            return;
        }
    }

    if (panningMinimap_) {
        panningMinimap_ = false;
        return;
    }

    if (wasTap) handleTap(p);
    else if (mouseDown_ == false && ui_.placement) {
        // 拖拽预览后松手也视为放置
        updatePlacementPreview(renderer_.screenToWorld(p, cam_));
        tryPlace(renderer_.screenToWorld(p, cam_));
    }
}

void App::handleTap(const Vec2& p) {
    if (ui_.intro) {
        ui_.intro = false;
        return;
    }
    if (ui_.help) {
        ui_.help = false;
        return;
    }
    if (ui_.paused) {
        ui_.paused = false;
        return;
    }
    if (game_.state != 0) {
        if (ui_.resultTimer > 0.8f) restart();
        return;
    }

    for (const UIButton& b : renderer_.buttons()) {
        if (b.rect.contains(p.x, p.y)) {
            if (b.enabled) {
                performAction(b.action);
            } else {
                toast("NOT AVAILABLE");
            }
            return;
        }
    }

    if (renderer_.inMinimap(p)) {
        cam_.center = renderer_.minimapToWorld(p);
        clampCamera();
        return;
    }
    if (renderer_.inHud(p)) return;

    Vec2 world = renderer_.screenToWorld(p, cam_);
    if (ui_.placement) {
        tryPlace(world);
        return;
    }
    selectAtWorld(world);
}

// ---------------------------------------------------------------- 选择与命令

void App::selectAtWorld(const Vec2& w) {
    Unit* hitUnit = nullptr;
    float best = 1e9f;
    for (Unit& u : game_.units) {
        if (!u.alive) continue;
        float d = distance(u.pos, w);
        float r = unitInfo(u.type).radius + 10.0f;
        if (d < r && d < best) {
            best = d;
            hitUnit = &u;
        }
    }

    Building* hitBld = nullptr;
    for (Building& b : game_.buildings) {
        if (b.alive && b.rect().contains(w.x, w.y)) {
            hitBld = &b;
            break;
        }
    }

    Resource* hitRes = nullptr;
    for (Resource& r : game_.resources) {
        if (!r.alive || r.gold <= 0) continue;
        Rect rr = r.rect();
        if (rr.contains(w.x, w.y) ||
            distance(r.center(), w) < std::max(rr.w, rr.h) * 0.6f) {
            hitRes = &r;
            break;
        }
    }

    bool hasSelection = !ui_.selected.empty();

    if (hasSelection) {
        if (hitBld && hitBld->team != TEAM_PLAYER) {
            game_.orderAttack(ui_.selected, hitBld->id, true);
            return;
        }
        if (hitUnit && hitUnit->team != TEAM_PLAYER) {
            game_.orderAttack(ui_.selected, hitUnit->id, false);
            return;
        }
        if (hitRes) {
            bool anyWorker = false;
            for (int id : ui_.selected) {
                const Unit* u = game_.unitById(id);
                if (u && u->type == UnitType::Worker) anyWorker = true;
            }
            if (anyWorker) {
                game_.orderGather(ui_.selected, hitRes->id);
                return;
            }
        }
    }

    if (hitUnit && hitUnit->team == TEAM_PLAYER) {
        ui_.selected.clear();
        ui_.selectedBuilding = 0;
        ui_.selected.push_back(hitUnit->id);
        return;
    }
    if (hitBld && hitBld->team == TEAM_PLAYER) {
        ui_.selected.clear();
        ui_.selectedBuilding = hitBld->id;
        return;
    }

    if (hasSelection) {
        game_.orderMove(ui_.selected, w);
        return;
    }
    ui_.selected.clear();
    ui_.selectedBuilding = 0;
}

void App::selectBox(const Rect& boxScreen) {
    std::vector<int> picked;
    for (const Unit& u : game_.units) {
        if (!u.alive || u.team != TEAM_PLAYER) continue;
        Vec2 s = renderer_.worldToScreen(u.pos, cam_);
        if (boxScreen.contains(s.x, s.y)) picked.push_back(u.id);
    }
    ui_.selectedBuilding = 0;
    ui_.selected = std::move(picked);
    if (ui_.selected.empty()) toast("NO UNITS IN BOX");
}

void App::beginPlacement(BldType type) {
    int worker = 0;
    for (int id : ui_.selected) {
        const Unit* u = game_.unitById(id);
        if (u && u->type == UnitType::Worker && u->alive) {
            worker = id;
            break;
        }
    }
    if (!worker) {
        toast("SELECT A WORKER");
        return;
    }
    const BldInfo& info = bldInfo(type);
    if (game_.gold[0] < info.cost) {
        toast("NOT ENOUGH GOLD");
        return;
    }
    builderId_ = worker;
    ui_.placement = true;
    ui_.placeType = type;
    ui_.placeShow = false;
    toast("PLACE " + std::string(info.name));
}

void App::updatePlacementPreview(const Vec2& world) {
    if (!ui_.placement) return;
    const BldInfo& info = bldInfo(ui_.placeType);
    int tx = (int)std::floor(world.x / TILE - info.w * 0.5f + 0.5f);
    int ty = (int)std::floor(world.y / TILE - info.h * 0.5f + 0.5f);
    ui_.placeTx = tx;
    ui_.placeTy = ty;
    ui_.placeShow = true;
    std::string err;
    ui_.placeValid = game_.canPlace(TEAM_PLAYER, ui_.placeType, tx, ty, builderId_, &err);
}

void App::tryPlace(const Vec2& world) {
    if (!ui_.placement) return;
    const BldInfo& info = bldInfo(ui_.placeType);
    int tx = (int)std::floor(world.x / TILE - info.w * 0.5f + 0.5f);
    int ty = (int)std::floor(world.y / TILE - info.h * 0.5f + 0.5f);
    std::string err;
    if (game_.placeBuilding(TEAM_PLAYER, ui_.placeType, tx, ty, builderId_, &err)) {
        ui_.placement = false;
        ui_.placeShow = false;
        ui_.selected.clear();
        ui_.selectedBuilding = 0;
        toast(std::string(info.name) + " PLACED");
    } else {
        toast(err.empty() ? "CANNOT BUILD HERE" : err);
    }
}

void App::performAction(UIAction action) {
    switch (action) {
        case UIAction::TrainWorker:
        case UIAction::TrainSoldier:
        case UIAction::TrainArcher: {
            if (!ui_.selectedBuilding) return;
            UnitType type = action == UIAction::TrainWorker
                                ? UnitType::Worker
                                : (action == UIAction::TrainSoldier ? UnitType::Soldier
                                                                    : UnitType::Archer);
            std::string err;
            if (!game_.trainUnit(ui_.selectedBuilding, type, &err))
                toast(err.empty() ? "CANNOT TRAIN" : err);
            break;
        }
        case UIAction::BuildBarracks:
            beginPlacement(BldType::Barracks);
            break;
        case UIAction::BuildTower:
            beginPlacement(BldType::Tower);
            break;
        case UIAction::Stop:
            if (!ui_.selected.empty()) game_.orderStop(ui_.selected);
            break;
        case UIAction::CancelPlacement:
            ui_.placement = false;
            ui_.placeShow = false;
            break;
        case UIAction::Pause:
            ui_.paused = !ui_.paused;
            break;
        case UIAction::Help:
            ui_.help = !ui_.help;
            break;
        case UIAction::Restart:
            restart();
            break;
        case UIAction::None:
        default:
            break;
    }
}

void App::cleanupSelection() {
    ui_.selected.erase(
        std::remove_if(ui_.selected.begin(), ui_.selected.end(),
                       [&](int id) {
                           const Unit* u = game_.unitById(id);
                           return !u || !u->alive || u->team != TEAM_PLAYER;
                       }),
        ui_.selected.end());
    if (ui_.selectedBuilding) {
        const Building* b = game_.buildingById(ui_.selectedBuilding);
        if (!b || !b->alive || b->team != TEAM_PLAYER) ui_.selectedBuilding = 0;
    }
    if (ui_.placement) {
        const Unit* w = game_.unitById(builderId_);
        if (!w || !w->alive) {
            ui_.placement = false;
            ui_.placeShow = false;
        }
    }
}

// ---------------------------------------------------------------- 主循环

void App::run() {
    Uint64 now = SDL_GetPerformanceCounter();
    Uint64 freq = SDL_GetPerformanceFrequency();
    double acc = 0.0;
    const double step = 1.0 / 60.0;

    while (running_) {
        SDL_Event e;
        while (SDL_PollEvent(&e)) handleEvent(e);

        Uint64 t = SDL_GetPerformanceCounter();
        float dt = (float)((double)(t - now) / (double)freq);
        now = t;
        if (dt > 0.25f) dt = 0.25f;

        ui_.time += dt;
        if (ui_.toastTimer > 0.0f) ui_.toastTimer -= dt;

        if (game_.state != 0) {
            ui_.resultTimer += dt;
        } else {
            ui_.resultTimer = 0.0f;
        }

        if (!ui_.paused && !ui_.intro && game_.state == 0) {
            acc += dt;
            int guard = 0;
            while (acc >= step && guard < 6) {
                game_.update((float)step);
                acc -= step;
                ++guard;
            }
            if (guard >= 6) acc = 0.0;
        } else {
            acc = 0.0;
        }

        // 键盘平移
        const Uint8* keys = SDL_GetKeyboardState(nullptr);
        Vec2 pan{0, 0};
        if (keys[SDL_SCANCODE_LEFT] || keys[SDL_SCANCODE_A]) pan.x += 1.0f;
        if (keys[SDL_SCANCODE_RIGHT] || keys[SDL_SCANCODE_D]) pan.x -= 1.0f;
        if (keys[SDL_SCANCODE_UP] || keys[SDL_SCANCODE_W]) pan.y += 1.0f;
        if (keys[SDL_SCANCODE_DOWN] || keys[SDL_SCANCODE_S]) pan.y -= 1.0f;
        if (pan.x != 0.0f || pan.y != 0.0f) {
            panCamera(pan * (620.0f * dt));
        }

        cleanupSelection();
        clampCamera();

        renderer_.draw(game_, cam_, ui_);
    }
}

}  // namespace

int main(int argc, char* argv[]) {
    (void)argc;
    (void)argv;
    App app;
    if (!app.init()) return 1;
    app.run();
    app.shutdown();
    SDL_Quit();
    return 0;
}
