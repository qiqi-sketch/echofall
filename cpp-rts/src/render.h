#pragma once

#include <SDL.h>

#include <string>
#include <vector>

#include "common.h"
#include "font.h"
#include "game.h"

namespace rts {

struct Camera {
    Vec2 center{768.0f, 768.0f};
    float zoom = 1.2f;
};

struct UIButton {
    Rect rect;
    std::string label;
    std::string sub;
    UIAction action = UIAction::None;
    bool enabled = true;
    int iconUnit = -1;  // UnitType, -1 表示无图标
    int iconBld = -1;   // BldType, -1 表示无图标
    bool small = false;
};

struct UIState {
    std::vector<int> selected;
    int selectedBuilding = 0;
    bool placement = false;
    BldType placeType = BldType::Barracks;
    bool placeShow = false;
    int placeTx = 0;
    int placeTy = 0;
    bool placeValid = false;
    bool boxActive = false;
    Vec2 boxStart;
    Vec2 boxEnd;
    std::string toast;
    float toastTimer = 0.0f;
    bool paused = false;
    bool intro = true;
    bool help = false;
    float resultTimer = 0.0f;
    float time = 0.0f;
};

class Renderer {
public:
    bool init(SDL_Window* window);
    void shutdown();
    void refreshSize();
    void draw(Game& game, const Camera& cam, UIState& ui);

    Vec2 windowToVirtual(float wx, float wy) const;
    Vec2 screenToWorld(const Vec2& screen, const Camera& cam) const;
    Vec2 worldToScreen(const Vec2& world, const Camera& cam) const;

    float vw() const { return vw_; }
    float vh() const { return vh_; }
    float scale() const { return scale_; }

    bool inHud(const Vec2& p) const;
    bool inMinimap(const Vec2& p) const;
    Vec2 minimapToWorld(const Vec2& p) const;

    const std::vector<UIButton>& buttons() const { return buttons_; }

private:
    SDL_Window* window_ = nullptr;
    SDL_Renderer* ren_ = nullptr;
    Font font_;

    int winW_ = 1280;
    int winH_ = 720;
    int outW_ = 1280;
    int outH_ = 720;
    float scale_ = 1.0f;
    float vw_ = 1280.0f;
    float vh_ = 720.0f;

    SDL_Texture* terrainTex_ = nullptr;
    int terrainVersion_ = -1;
    SDL_Texture* unitTex_[2][3] = {{nullptr, nullptr, nullptr}, {nullptr, nullptr, nullptr}};
    SDL_Texture* bldTex_[2][3] = {{nullptr, nullptr, nullptr}, {nullptr, nullptr, nullptr}};
    SDL_Texture* mineTex_ = nullptr;

    std::vector<UIButton> buttons_;
    Rect minimapRect_;
    Rect topBar_{0, 0, 0, 46};
    Rect bottomBar_{0, 0, 0, 108};

    void buildSprites();
    void buildTerrain(Game& game);
    void buildButtons(Game& game, UIState& ui);

    void drawWorld(Game& game, const Camera& cam, const UIState& ui);
    void drawHud(Game& game, const Camera& cam, UIState& ui);
    void drawOverlays(Game& game, UIState& ui, const Camera& cam);
    void drawButton(const UIButton& b);

    void setColor(Color c);
    void fillRect(const Rect& r, Color c);
    void strokeRect(const Rect& r, Color c, float thickness);
    void fillCircle(float cx, float cy, float radius, Color c);
    void strokeCircle(float cx, float cy, float radius, Color c, float thickness);
    void drawText(const std::string& text, float x, float y, float h, Color c, bool centered = false);
    void drawBar(const Rect& r, float t, Color fg, Color bg);
};

}  // namespace rts
