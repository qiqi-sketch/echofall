#include "render.h"

#include <algorithm>
#include <cmath>
#include <cstdio>
#include <cstring>

namespace rts {
namespace {

uint32_t pack(Color c) {
    return ((uint32_t)c.a << 24) | ((uint32_t)c.r << 16) | ((uint32_t)c.g << 8) | (uint32_t)c.b;
}

struct Canvas {
    int w;
    int h;
    int ss;
    std::vector<uint32_t> px;

    Canvas(int ww, int hh, int super)
        : w(ww), h(hh), ss(super), px((size_t)ww * super * hh * super, 0) {}

    void blend(int x, int y, uint32_t c, float alpha) {
        if (x < 0 || y < 0 || x >= w * ss || y >= h * ss) return;
        float sa = ((c >> 24) & 0xFF) / 255.0f * alpha;
        if (sa <= 0.0001f) return;
        uint32_t& d = px[(size_t)y * w * ss + x];
        float da = ((d >> 24) & 0xFF) / 255.0f;
        float oa = sa + da * (1.0f - sa);
        if (oa <= 0.0001f) {
            d = 0;
            return;
        }
        float sr = ((c >> 16) & 0xFF) / 255.0f;
        float sg = ((c >> 8) & 0xFF) / 255.0f;
        float sb = (c & 0xFF) / 255.0f;
        float dr = ((d >> 16) & 0xFF) / 255.0f;
        float dg = ((d >> 8) & 0xFF) / 255.0f;
        float db = (d & 0xFF) / 255.0f;
        float rr = (sr * sa + dr * da * (1.0f - sa)) / oa;
        float gg = (sg * sa + dg * da * (1.0f - sa)) / oa;
        float bb = (sb * sa + db * da * (1.0f - sa)) / oa;
        d = ((uint32_t)(oa * 255.0f + 0.5f) << 24) | ((uint32_t)(rr * 255.0f) << 16) |
            ((uint32_t)(gg * 255.0f) << 8) | (uint32_t)(bb * 255.0f);
    }

    void rect(float x, float y, float rw, float rh, Color c, float a = 1.0f) {
        int x0 = (int)std::floor(x * ss);
        int y0 = (int)std::floor(y * ss);
        int x1 = (int)std::ceil((x + rw) * ss);
        int y1 = (int)std::ceil((y + rh) * ss);
        uint32_t col = pack(c);
        for (int py = y0; py < y1; ++py) {
            for (int pxx = x0; pxx < x1; ++pxx) blend(pxx, py, col, a);
        }
    }

    void disc(float cx, float cy, float r, Color c, float a = 1.0f) {
        int x0 = (int)std::floor((cx - r) * ss);
        int y0 = (int)std::floor((cy - r) * ss);
        int x1 = (int)std::ceil((cx + r) * ss);
        int y1 = (int)std::ceil((cy + r) * ss);
        uint32_t col = pack(c);
        float r2 = r * r;
        for (int py = y0; py <= y1; ++py) {
            for (int pxx = x0; pxx <= x1; ++pxx) {
                float wx = (pxx + 0.5f) / ss;
                float wy = (py + 0.5f) / ss;
                float dx = wx - cx, dy = wy - cy;
                if (dx * dx + dy * dy <= r2) blend(pxx, py, col, a);
            }
        }
    }

    void ellipse(float cx, float cy, float rx, float ry, Color c, float a = 1.0f) {
        int x0 = (int)std::floor((cx - rx) * ss);
        int y0 = (int)std::floor((cy - ry) * ss);
        int x1 = (int)std::ceil((cx + rx) * ss);
        int y1 = (int)std::ceil((cy + ry) * ss);
        uint32_t col = pack(c);
        for (int py = y0; py <= y1; ++py) {
            for (int pxx = x0; pxx <= x1; ++pxx) {
                float wx = (pxx + 0.5f) / ss;
                float wy = (py + 0.5f) / ss;
                float nx = (wx - cx) / rx;
                float ny = (wy - cy) / ry;
                if (nx * nx + ny * ny <= 1.0f) blend(pxx, py, col, a);
            }
        }
    }

    void line(float x0, float y0, float x1, float y1, float thick, Color c, float a = 1.0f) {
        float dx = x1 - x0, dy = y1 - y0;
        float len = std::sqrt(dx * dx + dy * dy);
        int steps = std::max(1, (int)(len * ss * 1.6f));
        for (int i = 0; i <= steps; ++i) {
            float t = (float)i / (float)steps;
            disc(x0 + dx * t, y0 + dy * t, thick * 0.5f, c, a);
        }
    }

    void ring(float cx, float cy, float r, float thick, Color c, float a = 1.0f) {
        int x0 = (int)std::floor((cx - r) * ss);
        int y0 = (int)std::floor((cy - r) * ss);
        int x1 = (int)std::ceil((cx + r) * ss);
        int y1 = (int)std::ceil((cy + r) * ss);
        uint32_t col = pack(c);
        float outer = r * r;
        float inner = (r - thick) * (r - thick);
        for (int py = y0; py <= y1; ++py) {
            for (int pxx = x0; pxx <= x1; ++pxx) {
                float wx = (pxx + 0.5f) / ss;
                float wy = (py + 0.5f) / ss;
                float dx = wx - cx, dy = wy - cy;
                float d2 = dx * dx + dy * dy;
                if (d2 <= outer && d2 >= inner) blend(pxx, py, col, a);
            }
        }
    }

    SDL_Texture* upload(SDL_Renderer* ren) {
        std::vector<uint32_t> out((size_t)w * h, 0);
        int n = ss * ss;
        for (int y = 0; y < h; ++y) {
            for (int x = 0; x < w; ++x) {
                float sa = 0.0f, sr = 0.0f, sg = 0.0f, sb = 0.0f;
                for (int dy = 0; dy < ss; ++dy) {
                    for (int dx = 0; dx < ss; ++dx) {
                        uint32_t c = px[(size_t)(y * ss + dy) * w * ss + (x * ss + dx)];
                        float a = ((c >> 24) & 0xFF) / 255.0f;
                        sa += a;
                        sr += ((c >> 16) & 0xFF) / 255.0f * a;
                        sg += ((c >> 8) & 0xFF) / 255.0f * a;
                        sb += (c & 0xFF) / 255.0f * a;
                    }
                }
                float oa = sa / n;
                uint32_t ac = (uint32_t)(oa * 255.0f + 0.5f);
                uint32_t rc = 0, gc = 0, bc = 0;
                if (sa > 0.0001f) {
                    rc = (uint32_t)(sr / sa * 255.0f + 0.5f);
                    gc = (uint32_t)(sg / sa * 255.0f + 0.5f);
                    bc = (uint32_t)(sb / sa * 255.0f + 0.5f);
                }
                out[(size_t)y * w + x] = (ac << 24) | (rc << 16) | (gc << 8) | bc;
            }
        }
        SDL_Texture* tex = SDL_CreateTexture(ren, SDL_PIXELFORMAT_ARGB8888,
                                             SDL_TEXTUREACCESS_STATIC, w, h);
        if (!tex) return nullptr;
        SDL_UpdateTexture(tex, nullptr, out.data(), w * 4);
        SDL_SetTextureBlendMode(tex, SDL_BLENDMODE_BLEND);
        return tex;
    }
};

void roundRect(Canvas& c, float x, float y, float w, float h, float r, Color col, float a = 1.0f) {
    c.rect(x + r, y, w - 2 * r, h, col, a);
    c.rect(x, y + r, r, h - 2 * r, col, a);
    c.rect(x + w - r, y + r, r, h - 2 * r, col, a);
    c.disc(x + r, y + r, r, col, a);
    c.disc(x + w - r, y + r, r, col, a);
    c.disc(x + r, y + h - r, r, col, a);
    c.disc(x + w - r, y + h - r, r, col, a);
}

Color teamMain(int t) { return t == TEAM_PLAYER ? rgba(62, 132, 232) : rgba(224, 86, 72); }
Color teamDark(int t) { return t == TEAM_PLAYER ? rgba(28, 62, 120) : rgba(128, 36, 32); }
Color teamLight(int t) { return t == TEAM_PLAYER ? rgba(126, 182, 255) : rgba(255, 148, 122); }

Color kText = rgba(236, 228, 210);
Color kTextDim = rgba(150, 158, 166);
Color kGold = rgba(244, 196, 92);
Color kGreen = rgba(116, 226, 146);
Color kRed = rgba(236, 96, 82);
Color kPanel = rgba(12, 16, 21, 224);
Color kPanelLight = rgba(30, 38, 46, 236);
Color kBorder = rgba(96, 112, 128, 190);

SDL_Texture* makeUnitSprite(SDL_Renderer* ren, int team, UnitType type) {
    Canvas c(40, 40, 3);
    Color main = teamMain(team);
    Color dark = teamDark(team);
    Color light = teamLight(team);
    Color steel = rgba(174, 184, 196);
    Color steelDark = rgba(92, 104, 118);
    Color gold = rgba(240, 198, 92);
    Color wood = rgba(112, 80, 48);

    c.ellipse(20.0f, 26.0f, 11.0f, 5.5f, rgba(0, 0, 0), 0.42f);

    // 武器（在身体后方）
    if (type == UnitType::Soldier) {
        c.line(25.0f, 19.0f, 28.0f, 7.0f, 2.4f, steel);
        c.line(23.0f, 16.0f, 31.0f, 16.0f, 1.8f, gold);
        c.disc(13.0f, 20.0f, 4.8f, steelDark);
        c.disc(13.0f, 20.0f, 3.6f, steel);
    } else if (type == UnitType::Archer) {
        for (int i = 0; i <= 10; ++i) {
            float a = -1.15f + i * (2.3f / 10.0f);
            c.disc(26.0f + std::cos(a) * 8.5f, 20.0f + std::sin(a) * 8.5f, 1.0f, wood, 0.95f);
        }
        c.line(26.0f + std::cos(-1.15f) * 8.5f, 20.0f + std::sin(-1.15f) * 8.5f,
               26.0f + std::cos(1.15f) * 8.5f, 20.0f + std::sin(1.15f) * 8.5f, 0.5f,
               rgba(230, 220, 190), 0.8f);
    } else {
        c.line(25.0f, 25.0f, 29.5f, 14.0f, 2.2f, wood);
        c.line(26.0f, 12.5f, 32.0f, 15.5f, 2.8f, steel);
    }

    // 身体
    c.disc(20.0f, 20.0f, 9.6f, dark);
    c.disc(20.0f, 20.0f, 8.4f, main);
    c.disc(14.0f, 21.0f, 4.2f, dark);
    c.disc(26.0f, 21.0f, 4.2f, dark);
    c.disc(14.3f, 20.6f, 3.0f, main);
    c.disc(25.7f, 20.6f, 3.0f, main);
    c.disc(16.5f, 16.0f, 3.2f, rgba(255, 255, 255), 0.10f);

    // 头盔
    Color helmet = type == UnitType::Worker ? gold : steel;
    c.disc(20.0f, 20.0f, 5.4f, steelDark);
    c.disc(20.0f, 20.0f, 4.5f, helmet);
    c.disc(18.6f, 18.6f, 1.6f, rgba(255, 255, 255), 0.28f);
    c.rect(19.2f, 14.6f, 1.6f, 3.6f, rgba(32, 36, 42), 0.85f);
    (void)light;
    return c.upload(ren);
}

SDL_Texture* makeBuildingSprite(SDL_Renderer* ren, int team, BldType type) {
    const BldInfo& info = bldInfo(type);
    int fs = info.w * TILE;
    Canvas c(fs + 16, fs + 16, 3);
    float m = 8.0f;
    Color main = teamMain(team);
    Color dark = teamDark(team);
    Color light = teamLight(team);
    Color stone = rgba(128, 126, 114);
    Color stoneDark = rgba(92, 90, 80);
    Color stoneLight = rgba(160, 158, 146);
    Color roofDark = rgba(34, 40, 46);
    Color door = rgba(62, 46, 32);
    Color gold = rgba(238, 196, 92);

    if (type == BldType::HQ) {
        roundRect(c, m + 3, m + 7, fs, fs, 9, rgba(0, 0, 0), 0.40f);
        roundRect(c, m, m, fs, fs, 9, dark);
        roundRect(c, m + 4, m + 4, fs - 8, fs - 8, 7, stoneDark);
        roundRect(c, m + 10, m + 10, fs - 20, fs - 20, 6, stone);
        // 外墙装饰
        for (int i = 0; i < 5; ++i) {
            c.rect(m + 10 + i * 23, m + 10, 2, fs - 20, stoneDark, 0.45f);
        }
        // 中央主堡
        c.rect(m + 28, m + 32, 72, 66, stoneDark);
        c.rect(m + 32, m + 36, 64, 58, stoneLight);
        c.rect(m + 24, m + 22, 80, 26, roofDark);
        c.rect(m + 27, m + 24, 74, 20, main);
        c.rect(m + 27, m + 24, 74, 3, light, 0.7f);
        for (int i = 0; i < 5; ++i) c.rect(m + 28 + i * 15, m + 14, 8, 10, stoneLight);
        // 四角塔楼
        float corners[4][2] = {{m + 10, m + 10}, {m + fs - 10, m + 10},
                               {m + 10, m + fs - 10}, {m + fs - 10, m + fs - 10}};
        for (auto& co : corners) {
            c.disc(co[0], co[1], 11.0f, stoneDark);
            c.disc(co[0], co[1], 8.5f, stone);
            c.disc(co[0], co[1], 4.0f, main);
        }
        // 门与窗
        c.rect(m + 52, m + 94, 24, 26, door);
        c.disc(m + 64, m + 94, 12.0f, door);
        for (int i = 0; i < 3; ++i) {
            c.rect(m + 38 + i * 20, m + 50, 7, 9, gold, 0.85f);
        }
        // 旗杆
        c.line(m + 102, m + 44, m + 102, m + 10, 2.0f, rgba(52, 48, 44));
        c.rect(m + 103, m + 10, 17, 10, main);
        c.rect(m + 103, m + 10, 17, 3, light);
        // 高光
        c.rect(m, m, fs, 2, rgba(255, 255, 255), 0.13f);
    } else if (type == BldType::Barracks) {
        roundRect(c, m + 3, m + 6, fs, fs, 7, rgba(0, 0, 0), 0.38f);
        roundRect(c, m, m, fs, fs, 6, stoneDark);
        c.rect(m + 5, m + 5, fs - 10, fs - 10, stone);
        // 屋顶
        roundRect(c, m + 9, m + 10, fs - 18, fs - 30, 4, main);
        for (int i = 0; i < 5; ++i) {
            c.rect(m + 11 + i * 15, m + 10, 2, fs - 30, dark, 0.55f);
        }
        c.rect(m + 9, m + 10, fs - 18, 3, light, 0.65f);
        // 门
        c.rect(m + 38, m + 66, 20, 24, door);
        c.disc(m + 48, m + 66, 10.0f, door);
        c.rect(m + 46, m + 72, 4, 8, gold, 0.5f);
        // 武器架
        c.line(m + 14, m + 30, m + 22, m + 46, 1.8f, rgba(120, 90, 56));
        c.line(m + 20, m + 28, m + 28, m + 44, 1.8f, rgba(120, 90, 56));
        c.line(m + 25, m + 68, m + 25, m + 38, 1.6f, rgba(52, 48, 44));
        c.rect(m + 26, m + 33, 13, 8, main);
        c.rect(m, m, fs, 2, rgba(255, 255, 255), 0.12f);
    } else {
        float cx = m + fs * 0.5f;
        float cy = m + fs * 0.5f;
        c.ellipse(cx + 3, cy + 8, 26, 12, rgba(0, 0, 0), 0.38f);
        c.disc(cx, cy, 28.0f, stoneDark);
        c.disc(cx, cy, 24.0f, stone);
        for (int i = 0; i < 6; ++i) {
            float y = cy - 18 + i * 7.0f;
            float half = std::sqrt(std::max(0.0f, 24.0f * 24.0f - (y - cy) * (y - cy)));
            c.line(cx - half + 2, y, cx + half - 2, y, 1.3f, stoneDark, 0.35f);
        }
        // 城垛
        for (int i = 0; i < 8; ++i) {
            float a = i * 0.785398f;
            float bx = cx + std::cos(a) * 24.0f;
            float by = cy + std::sin(a) * 24.0f;
            c.disc(bx, by, 4.5f, stoneDark);
            c.disc(bx, by, 3.2f, stoneLight);
        }
        // 箭孔
        c.rect(cx - 2.5f, cy - 6, 5, 12, rgba(24, 24, 26));
        c.rect(cx - 4.5f, cy + 1, 9, 2, rgba(24, 24, 26));
        // 门
        c.rect(cx - 8, cy + 12, 16, 16, door);
        // 旗帜
        c.line(cx + 18, cy - 18, cx + 18, cy - 36, 2.0f, rgba(52, 48, 44));
        c.rect(cx + 19, cy - 36, 15, 9, main);
        c.rect(cx + 19, cy - 36, 15, 3, light);
    }
    return c.upload(ren);
}

SDL_Texture* makeMineSprite(SDL_Renderer* ren) {
    Canvas c(76, 76, 3);
    c.ellipse(40, 56, 30, 13, rgba(0, 0, 0), 0.38f);
    struct Rock { float x, y, r; Color col; };
    Rock rocks[6] = {
        {24, 48, 15, rgba(96, 92, 82)}, {42, 42, 18, rgba(114, 108, 96)},
        {56, 50, 12, rgba(88, 84, 76)}, {30, 34, 10, rgba(128, 120, 104)},
        {50, 31, 9, rgba(102, 98, 88)}, {38, 52, 11, rgba(74, 72, 66)},
    };
    for (auto& r : rocks) {
        c.disc(r.x, r.y + 2, r.r + 1.5f, rgba(44, 42, 38), 0.7f);
        c.disc(r.x, r.y, r.r, r.col);
        c.disc(r.x - r.r * 0.25f, r.y - r.r * 0.3f, r.r * 0.55f, rgba(255, 255, 255), 0.10f);
    }
    struct Nug { float x, y, r; };
    Nug nuggets[7] = {{26, 44, 3.2f}, {36, 38, 2.6f}, {47, 45, 3.4f},
                      {54, 37, 2.4f}, {31, 53, 2.2f}, {44, 30, 2.0f}, {20, 37, 1.8f}};
    for (auto& n : nuggets) {
        c.disc(n.x, n.y, n.r, rgba(250, 206, 72));
        c.disc(n.x - 0.6f, n.y - 0.8f, n.r * 0.45f, rgba(255, 245, 170));
    }
    return c.upload(ren);
}

uint32_t hashTile(int x, int y, uint32_t seed) {
    uint32_t h = (uint32_t)x * 374761393u + (uint32_t)y * 668265263u + seed * 69069u;
    h = (h ^ (h >> 13)) * 1274126177u;
    return h ^ (h >> 16);
}

}  // namespace

bool Renderer::init(SDL_Window* window) {
    window_ = window;
    ren_ = SDL_CreateRenderer(window, -1, SDL_RENDERER_ACCELERATED | SDL_RENDERER_PRESENTVSYNC);
    if (!ren_) ren_ = SDL_CreateRenderer(window, -1, SDL_RENDERER_SOFTWARE);
    if (!ren_) return false;
    SDL_SetRenderDrawBlendMode(ren_, SDL_BLENDMODE_BLEND);
    font_.init(ren_);
    refreshSize();
    buildSprites();
    return true;
}

void Renderer::shutdown() {
    if (terrainTex_) SDL_DestroyTexture(terrainTex_);
    terrainTex_ = nullptr;
    for (int t = 0; t < 2; ++t) {
        for (int i = 0; i < 3; ++i) {
            if (unitTex_[t][i]) SDL_DestroyTexture(unitTex_[t][i]);
            unitTex_[t][i] = nullptr;
            if (bldTex_[t][i]) SDL_DestroyTexture(bldTex_[t][i]);
            bldTex_[t][i] = nullptr;
        }
    }
    if (mineTex_) SDL_DestroyTexture(mineTex_);
    mineTex_ = nullptr;
    font_.destroy();
    if (ren_) SDL_DestroyRenderer(ren_);
    ren_ = nullptr;
}

void Renderer::buildSprites() {
    for (int t = 0; t < 2; ++t) {
        for (int i = 0; i < 3; ++i) {
            unitTex_[t][i] = makeUnitSprite(ren_, t, (UnitType)i);
            bldTex_[t][i] = makeBuildingSprite(ren_, t, (BldType)i);
        }
    }
    mineTex_ = makeMineSprite(ren_);
}

void Renderer::refreshSize() {
    SDL_GetWindowSize(window_, &winW_, &winH_);
    SDL_GetRendererOutputSize(ren_, &outW_, &outH_);
    if (winW_ <= 0) winW_ = 1;
    if (winH_ <= 0) winH_ = 1;
    if (outW_ <= 0) outW_ = winW_;
    if (outH_ <= 0) outH_ = winH_;
    scale_ = (float)outH_ / 720.0f;
    if (scale_ <= 0.0f) scale_ = 1.0f;
    vh_ = 720.0f;
    vw_ = (float)outW_ / scale_;
}

Vec2 Renderer::windowToVirtual(float wx, float wy) const {
    if (winW_ <= 0 || winH_ <= 0) return {wx, wy};
    return {wx / (float)winW_ * vw_, wy / (float)winH_ * vh_};
}

Vec2 Renderer::screenToWorld(const Vec2& screen, const Camera& cam) const {
    return {(screen.x - vw_ * 0.5f) / cam.zoom + cam.center.x,
            (screen.y - vh_ * 0.5f) / cam.zoom + cam.center.y};
}

Vec2 Renderer::worldToScreen(const Vec2& world, const Camera& cam) const {
    return {(world.x - cam.center.x) * cam.zoom + vw_ * 0.5f,
            (world.y - cam.center.y) * cam.zoom + vh_ * 0.5f};
}

bool Renderer::inHud(const Vec2& p) const {
    if (p.y < topBar_.y + topBar_.h) return true;
    if (p.y > bottomBar_.y) return true;
    if (minimapRect_.contains(p.x, p.y)) return true;
    return false;
}

bool Renderer::inMinimap(const Vec2& p) const { return minimapRect_.contains(p.x, p.y); }

Vec2 Renderer::minimapToWorld(const Vec2& p) const {
    if (minimapRect_.w <= 0) return {MAP_PX_W * 0.5f, MAP_PX_H * 0.5f};
    float u = clampf((p.x - minimapRect_.x) / minimapRect_.w, 0.0f, 1.0f);
    float v = clampf((p.y - minimapRect_.y) / minimapRect_.h, 0.0f, 1.0f);
    return {u * MAP_PX_W, v * MAP_PX_H};
}

void Renderer::setColor(Color c) {
    SDL_SetRenderDrawColor(ren_, c.r, c.g, c.b, c.a);
}

void Renderer::fillRect(const Rect& r, Color c) {
    setColor(c);
    SDL_FRect f{r.x, r.y, r.w, r.h};
    SDL_RenderFillRectF(ren_, &f);
}

void Renderer::strokeRect(const Rect& r, Color c, float thickness) {
    fillRect({r.x, r.y, r.w, thickness}, c);
    fillRect({r.x, r.y + r.h - thickness, r.w, thickness}, c);
    fillRect({r.x, r.y, thickness, r.h}, c);
    fillRect({r.x + r.w - thickness, r.y, thickness, r.h}, c);
}

void Renderer::fillCircle(float cx, float cy, float radius, Color c) {
    setColor(c);
    int steps = std::max(12, (int)(radius * 1.6f));
    for (int i = 0; i < steps; ++i) {
        float a0 = (float)i / steps * 6.2831853f;
        float a1 = (float)(i + 1) / steps * 6.2831853f;
        SDL_FRect r{cx + std::cos(a0) * radius - radius * 0.16f,
                    cy + std::sin(a0) * radius - radius * 0.16f, radius * 0.32f,
                    radius * 0.32f};
        SDL_RenderFillRectF(ren_, &r);
        (void)a1;
    }
}

void Renderer::strokeCircle(float cx, float cy, float radius, Color c, float thickness) {
    setColor(c);
    int steps = std::max(16, (int)(radius * 1.2f));
    Vec2 prev{cx + radius, cy};
    for (int i = 1; i <= steps; ++i) {
        float a = (float)i / steps * 6.2831853f;
        Vec2 cur{cx + std::cos(a) * radius, cy + std::sin(a) * radius};
        SDL_RenderDrawLineF(ren_, prev.x, prev.y, cur.x, cur.y);
        prev = cur;
    }
    (void)thickness;
}

void Renderer::drawText(const std::string& text, float x, float y, float h, Color c, bool centered) {
    if (centered) {
        x -= font_.width(text, h) * 0.5f;
    }
    SDL_Color sc{c.r, c.g, c.b, c.a};
    font_.draw(ren_, text, x, y, h, sc);
}

void Renderer::drawBar(const Rect& r, float t, Color fg, Color bg) {
    fillRect(r, bg);
    t = clampf(t, 0.0f, 1.0f);
    fillRect({r.x, r.y, r.w * t, r.h}, fg);
}

void Renderer::buildTerrain(Game& game) {
    if (terrainTex_) {
        SDL_DestroyTexture(terrainTex_);
        terrainTex_ = nullptr;
    }
    Canvas c(MAP_PX_W, MAP_PX_H, 1);
    for (int y = 0; y < MAP_H; ++y) {
        for (int x = 0; x < MAP_W; ++x) {
            uint32_t h = hashTile(x, y, game.seed);
            int v = (int)((h >> 24) & 0xFF);
            Terrain t = (Terrain)game.terrain[(size_t)y * MAP_W + x];
            Color col;
            if (t == Terrain::Dirt) {
                col = rgba((uint8_t)(120 + v % 7), (uint8_t)(104 + v % 6), (uint8_t)(78 + v % 5));
            } else if (t == Terrain::Water) {
                col = rgba((uint8_t)(44 + v % 5), (uint8_t)(88 + v % 6), (uint8_t)(116 + v % 6));
            } else if (t == Terrain::Tree) {
                col = rgba(62, 98, 55);
            } else {
                col = rgba((uint8_t)(66 + v % 8), (uint8_t)(104 + v % 8), (uint8_t)(58 + v % 7));
            }
            c.rect((float)x * TILE, (float)y * TILE, TILE, TILE, col);
        }
    }
    // 草簇与土路细节
    for (int y = 0; y < MAP_H; ++y) {
        for (int x = 0; x < MAP_W; ++x) {
            uint32_t h = hashTile(x, y, game.seed + 7u);
            Terrain t = (Terrain)game.terrain[(size_t)y * MAP_W + x];
            float px = x * TILE + 6.0f + (h % 18);
            float py = y * TILE + 8.0f + ((h >> 8) % 16);
            if (t == Terrain::Grass) {
                Color g = rgba(52, 88, 49, 150);
                for (int i = 0; i < 3; ++i) {
                    float ox = (float)((h >> (i * 4)) % 14) - 7.0f;
                    c.line(px + ox, py, px + ox + 1.0f, py - 4.0f - (i % 2) * 2.0f, 1.0f, g);
                }
            } else if (t == Terrain::Dirt) {
                c.rect(px, py, 2, 2, rgba(100, 86, 64, 120));
                c.rect(px + 5, py + 6, 1.5f, 1.5f, rgba(140, 124, 96, 120));
            } else if (t == Terrain::Water) {
                c.line(px - 4, py, px + 6, py, 1.2f, rgba(88, 136, 158, 110));
                c.line(px + 2, py + 5, px + 10, py + 5, 1.0f, rgba(88, 136, 158, 80));
            }
        }
    }
    // 水岸
    for (int y = 0; y < MAP_H; ++y) {
        for (int x = 0; x < MAP_W; ++x) {
            if ((Terrain)game.terrain[(size_t)y * MAP_W + x] != Terrain::Water) continue;
            float tx = (float)x * TILE, ty = (float)y * TILE;
            auto isWater = [&](int nx, int ny) {
                if (nx < 0 || ny < 0 || nx >= MAP_W || ny >= MAP_H) return false;
                return (Terrain)game.terrain[(size_t)ny * MAP_W + nx] == Terrain::Water;
            };
            if (!isWater(x, y - 1)) c.rect(tx, ty, TILE, 3, rgba(34, 68, 88), 0.8f);
            if (!isWater(x, y + 1)) c.rect(tx, ty + TILE - 3, TILE, 3, rgba(34, 68, 88), 0.8f);
            if (!isWater(x - 1, y)) c.rect(tx, ty, 3, TILE, rgba(34, 68, 88), 0.8f);
            if (!isWater(x + 1, y)) c.rect(tx + TILE - 3, ty, 3, TILE, rgba(34, 68, 88), 0.8f);
        }
    }
    // 树木
    for (int y = 0; y < MAP_H; ++y) {
        for (int x = 0; x < MAP_W; ++x) {
            if ((Terrain)game.terrain[(size_t)y * MAP_W + x] != Terrain::Tree) continue;
            uint32_t h = hashTile(x, y, game.seed + 31u);
            float cx = x * TILE + TILE * 0.5f + ((h % 9) - 4);
            float cy = y * TILE + TILE * 0.5f + (((h >> 8) % 9) - 4);
            c.ellipse(cx + 3, cy + 7, 14, 7, rgba(0, 0, 0), 0.32f);
            c.rect(cx - 2.0f, cy + 3.0f, 4.0f, 9.0f, rgba(76, 58, 38));
            c.disc(cx, cy, 14.0f, rgba(38, 70, 40));
            c.disc(cx - 4, cy - 4, 10.5f, rgba(54, 92, 52));
            c.disc(cx - 5, cy - 6, 6.5f, rgba(72, 116, 64));
            c.disc(cx + 4, cy + 3, 4.0f, rgba(30, 58, 33), 0.55f);
        }
    }
    terrainTex_ = c.upload(ren_);
    terrainVersion_ = game.mapVersion;
}

void Renderer::drawWorld(Game& game, const Camera& cam, const UIState& ui) {
    if (!terrainTex_ || terrainVersion_ != game.mapVersion) buildTerrain(game);

    float viewW = vw_ / cam.zoom;
    float viewH = vh_ / cam.zoom;
    Vec2 origin{cam.center.x - viewW * 0.5f, cam.center.y - viewH * 0.5f};
    SDL_Rect src{(int)std::floor(origin.x), (int)std::floor(origin.y), (int)std::ceil(viewW),
                 (int)std::ceil(viewH)};
    SDL_FRect dst{0, 0, vw_, vh_};
    SDL_RenderCopyF(ren_, terrainTex_, &src, &dst);

    // 金矿
    for (const Resource& r : game.resources) {
        if (!r.alive) continue;
        Vec2 s = worldToScreen(r.center(), cam);
        float size = 78.0f * cam.zoom;
        SDL_FRect d{s.x - size * 0.5f, s.y - size * 0.5f, size, size};
        if (d.x + d.w < -10 || d.y + d.h < -10 || d.x > vw_ + 10 || d.y > vh_ + 10) continue;
        SDL_RenderCopyF(ren_, mineTex_, nullptr, &d);
    }

    // 建筑
    for (const Building& b : game.buildings) {
        if (!b.alive) continue;
        Vec2 s = worldToScreen(b.center(), cam);
        const BldInfo& info = bldInfo(b.type);
        float size = (info.w * TILE + 16) * cam.zoom;
        SDL_FRect d{s.x - size * 0.5f, s.y - size * 0.5f, size, size};
        if (d.x + d.w < -20 || d.y + d.h < -20 || d.x > vw_ + 20 || d.y > vh_ + 20) continue;
        SDL_SetTextureAlphaMod(bldTex_[b.team][(int)b.type], b.complete ? 255 : 165);
        SDL_RenderCopyF(ren_, bldTex_[b.team][(int)b.type], nullptr, &d);
        SDL_SetTextureAlphaMod(bldTex_[b.team][(int)b.type], 255);

        if (!b.complete) {
            Rect bar{s.x - 30.0f * cam.zoom, d.y + 6.0f, 60.0f * cam.zoom, 5.0f};
            drawBar(bar, b.progress, rgba(110, 210, 255), rgba(10, 14, 18, 200));
        } else if (b.hp < b.maxHp * 0.999f) {
            Rect bar{s.x - 30.0f * cam.zoom, d.y + 6.0f, 60.0f * cam.zoom, 5.0f};
            drawBar(bar, b.hp / b.maxHp, kGreen, rgba(10, 14, 18, 200));
        }

        if (ui.selectedBuilding == b.id) {
            Rect r{b.tx * (float)TILE, b.ty * (float)TILE, info.w * (float)TILE,
                   info.h * (float)TILE};
            Vec2 tl = worldToScreen({r.x, r.y}, cam);
            strokeRect({tl.x - 2, tl.y - 2, r.w * cam.zoom + 4, r.h * cam.zoom + 4},
                       rgba(120, 240, 160, 210), 2.0f);
            // 集结点
            Vec2 rally = worldToScreen(b.rally, cam);
            setColor(rgba(120, 240, 160, 200));
            SDL_RenderDrawLineF(ren_, rally.x, rally.y, rally.x, rally.y - 18.0f);
            setColor(teamMain(b.team));
            SDL_FRect flag{rally.x, rally.y - 18.0f, 11.0f, 7.0f};
            SDL_RenderFillRectF(ren_, &flag);
            strokeCircle(rally.x, rally.y, 3.0f, rgba(120, 240, 160, 220), 1.0f);
        }
    }

    // 建造预览
    if (ui.placement && ui.placeShow) {
        const BldInfo& info = bldInfo(ui.placeType);
        Color tone = ui.placeValid ? rgba(110, 235, 150, 55) : rgba(240, 90, 80, 60);
        for (int y = 0; y < info.h; ++y) {
            for (int x = 0; x < info.w; ++x) {
                int tx = ui.placeTx + x;
                int ty = ui.placeTy + y;
                if (tx < 0 || ty < 0 || tx >= MAP_W || ty >= MAP_H) continue;
                Vec2 s = worldToScreen({tx * (float)TILE, ty * (float)TILE}, cam);
                fillRect({s.x, s.y, TILE * cam.zoom, TILE * cam.zoom}, tone);
            }
        }
        Vec2 center = worldToScreen(
            {(ui.placeTx + info.w * 0.5f) * TILE, (ui.placeTy + info.h * 0.5f) * TILE}, cam);
        float size = (info.w * TILE + 16) * cam.zoom;
        SDL_FRect d{center.x - size * 0.5f, center.y - size * 0.5f, size, size};
        SDL_SetTextureAlphaMod(bldTex_[TEAM_PLAYER][(int)ui.placeType], 170);
        SDL_RenderCopyF(ren_, bldTex_[TEAM_PLAYER][(int)ui.placeType], nullptr, &d);
        SDL_SetTextureAlphaMod(bldTex_[TEAM_PLAYER][(int)ui.placeType], 255);
        Color outline = ui.placeValid ? rgba(120, 240, 160, 230) : rgba(240, 90, 80, 230);
        strokeRect({d.x, d.y, d.w, d.h}, outline, 2.0f);
    }

    // 单位按 y 排序
    std::vector<const Unit*> order;
    order.reserve(game.units.size());
    for (const Unit& u : game.units) {
        if (!u.alive) continue;
        order.push_back(&u);
    }
    std::sort(order.begin(), order.end(),
              [](const Unit* a, const Unit* b) { return a->pos.y < b->pos.y; });

    for (const Unit* up : order) {
        const Unit& u = *up;
        Vec2 s = worldToScreen(u.pos, cam);
        bool selected = false;
        for (int id : ui.selected) {
            if (id == u.id) {
                selected = true;
                break;
            }
        }
        if (selected) {
            strokeCircle(s.x, s.y, 15.0f * cam.zoom, rgba(120, 240, 160, 210), 2.0f);
        }
        float size = 40.0f * cam.zoom;
        SDL_FRect d{s.x - size * 0.5f, s.y - size * 0.5f, size, size};
        if (d.x + d.w < -20 || d.y + d.h < -20 || d.x > vw_ + 20 || d.y > vh_ + 20) continue;
        double angle = (double)u.facing * 57.2957795 + 90.0;
        SDL_FPoint center{size * 0.5f, size * 0.5f};
        SDL_RenderCopyExF(ren_, unitTex_[u.team][(int)u.type], nullptr, &d, angle, &center,
                          SDL_FLIP_NONE);

        if (u.hp < u.maxHp * 0.999f || selected) {
            float w = 24.0f * cam.zoom;
            Rect bar{s.x - w * 0.5f, d.y + 1.0f, w, 3.0f * cam.zoom};
            drawBar(bar, u.hp / u.maxHp, kGreen, rgba(12, 16, 20, 210));
        }
    }

    // 投射物
    setColor(rgba(255, 222, 130, 235));
    for (const Projectile& p : game.projectiles) {
        if (!p.alive) continue;
        Vec2 s = worldToScreen(p.pos, cam);
        Vec2 tail = worldToScreen(p.pos - Vec2{std::cos(p.angle), std::sin(p.angle)} * 12.0f, cam);
        SDL_RenderDrawLineF(ren_, tail.x, tail.y, s.x, s.y);
    }

    // 粒子
    for (const Particle& p : game.particles) {
        if (!p.alive) continue;
        float t = p.maxLife > 0.0f ? clampf(p.life / p.maxLife, 0.0f, 1.0f) : 0.0f;
        Vec2 s = worldToScreen(p.pos, cam);
        Color c = p.color;
        c.a = (uint8_t)(220.0f * t);
        fillRect({s.x - p.size * cam.zoom * 0.5f, s.y - p.size * cam.zoom * 0.5f,
                  p.size * cam.zoom, p.size * cam.zoom},
                 c);
    }

    // 框选
    if (ui.boxActive) {
        Rect box{std::min(ui.boxStart.x, ui.boxEnd.x), std::min(ui.boxStart.y, ui.boxEnd.y),
                 std::abs(ui.boxEnd.x - ui.boxStart.x), std::abs(ui.boxEnd.y - ui.boxStart.y)};
        fillRect(box, rgba(110, 200, 255, 32));
        strokeRect(box, rgba(130, 220, 255, 210), 1.5f);
    }
}

void Renderer::buildButtons(Game& game, UIState& ui) {
    buttons_.clear();
    float y = vh_ - 84.0f;
    float x = 14.0f;
    const float bw = 112.0f;
    const float bh = 72.0f;
    const float gap = 9.0f;

    auto add = [&](const std::string& label, const std::string& sub, UIAction action,
                   bool enabled, int iconUnit, int iconBld) {
        UIButton b;
        b.rect = {x, y, bw, bh};
        b.label = label;
        b.sub = sub;
        b.action = action;
        b.enabled = enabled;
        b.iconUnit = iconUnit;
        b.iconBld = iconBld;
        buttons_.push_back(b);
        x += bw + gap;
    };

    if (ui.placement) {
        add("CANCEL", "BUILD", UIAction::CancelPlacement, true, -1, -1);
    } else if (ui.selectedBuilding) {
        Building* b = game.buildingById(ui.selectedBuilding);
        if (b && b->complete) {
            if (b->type == BldType::HQ) {
                const UnitInfo& wi = unitInfo(UnitType::Worker);
                add("WORKER", std::to_string(wi.cost), UIAction::TrainWorker,
                    game.gold[0] >= wi.cost && game.popUsed(0) < POP_CAP, 0, -1);
            } else if (b->type == BldType::Barracks) {
                const UnitInfo& si = unitInfo(UnitType::Soldier);
                const UnitInfo& ai = unitInfo(UnitType::Archer);
                add("SOLDIER", std::to_string(si.cost), UIAction::TrainSoldier,
                    game.gold[0] >= si.cost && game.popUsed(0) < POP_CAP, 1, -1);
                add("ARCHER", std::to_string(ai.cost), UIAction::TrainArcher,
                    game.gold[0] >= ai.cost && game.popUsed(0) < POP_CAP, 2, -1);
            }
        }
    } else if (!ui.selected.empty()) {
        bool hasWorker = false;
        for (int id : ui.selected) {
            const Unit* u = game.unitById(id);
            if (u && u->type == UnitType::Worker) hasWorker = true;
        }
        if (hasWorker) {
            const BldInfo& bi = bldInfo(BldType::Barracks);
            const BldInfo& ti = bldInfo(BldType::Tower);
            add("BARRACKS", std::to_string(bi.cost), UIAction::BuildBarracks,
                game.gold[0] >= bi.cost, -1, 1);
            add("TOWER", std::to_string(ti.cost), UIAction::BuildTower,
                game.gold[0] >= ti.cost, -1, 2);
        }
        add("STOP", "", UIAction::Stop, true, -1, -1);
    }

    // 顶部小按钮
    UIButton help;
    help.rect = {vw_ - 46.0f, 8.0f, 32.0f, 30.0f};
    help.label = "?";
    help.action = UIAction::Help;
    help.small = true;
    buttons_.push_back(help);

    UIButton pause;
    pause.rect = {vw_ - 84.0f, 8.0f, 32.0f, 30.0f};
    pause.label = "II";
    pause.action = UIAction::Pause;
    pause.small = true;
    buttons_.push_back(pause);
}

void Renderer::drawButton(const UIButton& b) {
    Color bg = b.enabled ? kPanelLight : rgba(24, 28, 33, 215);
    fillRect(b.rect, bg);
    strokeRect(b.rect, b.enabled ? kBorder : rgba(58, 66, 74, 180), 1.0f);
    fillRect({b.rect.x + 1, b.rect.y + 1, b.rect.w - 2, 1.0f}, rgba(255, 255, 255, 22));

    if (b.small) {
        drawText(b.label, b.rect.x + b.rect.w * 0.5f, b.rect.y + 8.0f, 14.0f,
                 b.enabled ? kText : kTextDim, true);
        return;
    }

    float iconSize = 38.0f;
    if (b.iconUnit >= 0) {
        SDL_FRect d{b.rect.x + b.rect.w * 0.5f - iconSize * 0.5f, b.rect.y + 6.0f, iconSize,
                    iconSize};
        SDL_SetTextureAlphaMod(unitTex_[0][b.iconUnit], b.enabled ? 255 : 110);
        SDL_RenderCopyF(ren_, unitTex_[0][b.iconUnit], nullptr, &d);
        SDL_SetTextureAlphaMod(unitTex_[0][b.iconUnit], 255);
    } else if (b.iconBld >= 0) {
        SDL_FRect d{b.rect.x + b.rect.w * 0.5f - iconSize * 0.5f, b.rect.y + 5.0f, iconSize,
                    iconSize};
        SDL_SetTextureAlphaMod(bldTex_[0][b.iconBld], b.enabled ? 255 : 110);
        SDL_RenderCopyF(ren_, bldTex_[0][b.iconBld], nullptr, &d);
        SDL_SetTextureAlphaMod(bldTex_[0][b.iconBld], 255);
    } else {
        drawText(b.label, b.rect.x + b.rect.w * 0.5f, b.rect.y + 18.0f, 14.0f,
                 b.enabled ? kText : kTextDim, true);
        return;
    }

    drawText(b.label, b.rect.x + b.rect.w * 0.5f, b.rect.y + 47.0f, 10.0f,
             b.enabled ? kText : kTextDim, true);
    Color costColor = b.enabled ? kGold : rgba(170, 120, 90);
    drawText(b.sub, b.rect.x + b.rect.w * 0.5f, b.rect.y + 58.0f, 10.0f, costColor, true);
}

void Renderer::drawHud(Game& game, const Camera& cam, UIState& ui) {
    buildButtons(game, ui);

    // 顶部资源栏
    fillRect({0, 0, vw_, 46.0f}, rgba(10, 14, 18, 226));
    fillRect({0, 45.0f, vw_, 1.0f}, rgba(96, 112, 128, 150));
    fillCircle(24.0f, 23.0f, 7.0f, kGold);
    fillCircle(22.0f, 21.0f, 2.4f, rgba(255, 246, 190));
    drawText(std::to_string(game.gold[0]), 38.0f, 16.0f, 16.0f, kText);

    drawText("POP", 118.0f, 17.0f, 11.0f, kTextDim);
    char popBuf[32];
    std::snprintf(popBuf, sizeof(popBuf), "%d/%d", game.popUsed(0), POP_CAP);
    drawText(popBuf, 152.0f, 16.0f, 14.0f, kText);

    int minutes = (int)game.time / 60;
    int seconds = (int)game.time % 60;
    char timeBuf[32];
    std::snprintf(timeBuf, sizeof(timeBuf), "%d:%02d", minutes, seconds);
    drawText(timeBuf, 238.0f, 16.0f, 14.0f, kTextDim);

    drawText("FRONTLINE", vw_ * 0.5f, 13.0f, 18.0f, rgba(244, 196, 92, 220), true);

    // 小地图
    float mm = 172.0f;
    minimapRect_ = {vw_ - mm - 14.0f, 58.0f, mm, mm};
    fillRect({minimapRect_.x - 4, minimapRect_.y - 4, mm + 8, mm + 8}, rgba(8, 12, 16, 220));
    strokeRect({minimapRect_.x - 4, minimapRect_.y - 4, mm + 8, mm + 8}, kBorder, 1.0f);
    if (terrainTex_) {
        SDL_FRect dst{minimapRect_.x, minimapRect_.y, mm, mm};
        SDL_RenderCopyF(ren_, terrainTex_, nullptr, &dst);
    }
    float sx = mm / (float)MAP_PX_W;
    float sy = mm / (float)MAP_PX_H;
    for (const Building& b : game.buildings) {
        if (!b.alive) continue;
        const BldInfo& info = bldInfo(b.type);
        fillRect({minimapRect_.x + b.tx * TILE * sx, minimapRect_.y + b.ty * TILE * sy,
                  info.w * TILE * sx, info.h * TILE * sy},
                 teamMain(b.team));
    }
    for (const Unit& u : game.units) {
        if (!u.alive) continue;
        Color c = u.team == TEAM_PLAYER ? rgba(140, 196, 255) : rgba(255, 150, 130);
        fillRect({minimapRect_.x + u.pos.x * sx - 1.2f,
                  minimapRect_.y + u.pos.y * sy - 1.2f, 2.4f, 2.4f},
                 c);
    }
    float halfW = vw_ / (2.0f * cam.zoom) * sx;
    float halfH = vh_ / (2.0f * cam.zoom) * sy;
    Rect viewRect{minimapRect_.x + cam.center.x * sx - halfW,
                  minimapRect_.y + cam.center.y * sy - halfH, halfW * 2.0f, halfH * 2.0f};
    strokeRect(viewRect, rgba(240, 235, 215, 190), 1.0f);

    // 底部指令栏
    bottomBar_ = {0, vh_ - 108.0f, vw_, 108.0f};
    fillRect(bottomBar_, kPanel);
    fillRect({0, bottomBar_.y, vw_, 1.0f}, rgba(96, 112, 128, 150));

    std::string info;
    Color infoColor = kText;
    if (ui.placement) {
        info = "PLACE " + std::string(bldInfo(ui.placeType).name) + "   TAP A FREE SPOT";
        infoColor = kGold;
    } else if (ui.selectedBuilding) {
        Building* b = game.buildingById(ui.selectedBuilding);
        if (b) {
            char buf[160];
            std::snprintf(buf, sizeof(buf), "%s   HP %d/%d", bldInfo(b->type).name, (int)b->hp,
                          (int)b->maxHp);
            info = buf;
            if (!b->queue.empty()) {
                info += "   QUEUE " + std::to_string(b->queue.size());
            }
            if (!b->complete) {
                char pb[64];
                std::snprintf(pb, sizeof(pb), "   BUILDING %d%%", (int)(b->progress * 100.0f));
                info += pb;
            }
        }
    } else if (!ui.selected.empty()) {
        int workers = 0, soldiers = 0, archers = 0;
        float hp = 0.0f, maxHp = 0.0f;
        for (int id : ui.selected) {
            const Unit* u = game.unitById(id);
            if (!u) continue;
            if (u->type == UnitType::Worker) ++workers;
            else if (u->type == UnitType::Soldier) ++soldiers;
            else ++archers;
            hp += u->hp;
            maxHp += u->maxHp;
        }
        char buf[160];
        std::snprintf(buf, sizeof(buf), "%d UNITS   W%d  S%d  A%d   HP %d/%d", (int)ui.selected.size(),
                      workers, soldiers, archers, (int)hp, (int)maxHp);
        info = buf;
    } else {
        info = "TAP A UNIT OR DRAG TO SELECT";
        infoColor = kTextDim;
    }
    drawText(info, 16.0f, vh_ - 103.0f, 11.0f, infoColor);

    if (ui.selected.empty() && !ui.placement) {
        drawText("2 FINGERS: PAN   PINCH: ZOOM", vw_ - 16.0f, vh_ - 101.0f, 10.0f, kTextDim,
                 false);
    }

    for (const UIButton& b : buttons_) drawButton(b);

    // 提示气泡
    if (ui.toastTimer > 0.0f && !ui.toast.empty()) {
        float alpha = clampf(ui.toastTimer / 0.6f, 0.0f, 1.0f);
        float tw = font_.width(ui.toast, 13.0f) + 28.0f;
        Rect box{vw_ * 0.5f - tw * 0.5f, 62.0f, tw, 30.0f};
        fillRect(box, rgba(10, 14, 18, (uint8_t)(220 * alpha)));
        strokeRect(box, rgba(120, 140, 160, (uint8_t)(190 * alpha)), 1.0f);
        Color c = kText;
        c.a = (uint8_t)(255 * alpha);
        drawText(ui.toast, vw_ * 0.5f, 70.0f, 13.0f, c, true);
    }
}

void Renderer::drawOverlays(Game& game, UIState& ui, const Camera& cam) {
    (void)cam;
    bool showHelp = ui.intro || ui.help;
    if (showHelp) {
        fillRect({0, 0, vw_, vh_}, rgba(6, 9, 12, 232));
        float panelW = std::min(vw_ * 0.86f, 820.0f);
        float panelH = 486.0f;
        Rect panel{vw_ * 0.5f - panelW * 0.5f, vh_ * 0.5f - panelH * 0.5f, panelW, panelH};
        fillRect(panel, rgba(16, 22, 28, 245));
        strokeRect(panel, rgba(120, 140, 160, 200), 1.5f);

        drawText("FRONTLINE", vw_ * 0.5f, panel.y + 26.0f, 46.0f, kGold, true);
        drawText("REAL-TIME STRATEGY", vw_ * 0.5f, panel.y + 82.0f, 11.0f,
                 rgba(160, 172, 184), true);

        const char* left[7] = {"TAP", "DRAG", "TAP GROUND", "TWO-FINGER DRAG",
                               "PINCH", "BOTTOM BUTTONS", "MINIMAP"};
        const char* right[7] = {"SELECT UNIT / BUILDING", "BOX SELECT UNITS",
                                "MOVE / ATTACK / GATHER", "PAN CAMERA", "ZOOM IN / OUT",
                                "TRAIN & BUILD", "JUMP CAMERA"};
        float rowY = panel.y + 126.0f;
        for (int i = 0; i < 7; ++i) {
            drawText(left[i], panel.x + 54.0f, rowY, 12.0f, kGold);
            drawText(right[i], panel.x + 250.0f, rowY, 12.0f, kText);
            rowY += 30.0f;
        }

        drawText("GOAL: DESTROY THE ENEMY HEADQUARTERS", vw_ * 0.5f, panel.y + panelH - 92.0f,
                 12.0f, rgba(255, 140, 120), true);

        float blink = 0.55f + 0.45f * std::sin(ui.time * 4.0f);
        Color hint = kGreen;
        hint.a = (uint8_t)(255 * blink);
        drawText(ui.intro ? "TAP TO BEGIN" : "TAP TO CLOSE", vw_ * 0.5f,
                 panel.y + panelH - 52.0f, 18.0f, hint, true);
        return;
    }

    if (ui.paused) {
        fillRect({0, 0, vw_, vh_}, rgba(4, 6, 9, 170));
        drawText("PAUSED", vw_ * 0.5f, vh_ * 0.5f - 34.0f, 40.0f, kText, true);
        drawText("TAP TO RESUME", vw_ * 0.5f, vh_ * 0.5f + 20.0f, 14.0f, kTextDim, true);
        return;
    }

    if (game.state != 0) {
        fillRect({0, 0, vw_, vh_}, rgba(4, 6, 9, 200));
        if (game.state == 1) {
            drawText("VICTORY", vw_ * 0.5f, vh_ * 0.5f - 80.0f, 54.0f, kGreen, true);
            drawText("ENEMY HEADQUARTERS DESTROYED", vw_ * 0.5f, vh_ * 0.5f - 16.0f, 14.0f,
                     kText, true);
        } else {
            drawText("DEFEAT", vw_ * 0.5f, vh_ * 0.5f - 80.0f, 54.0f, kRed, true);
            drawText("YOUR HEADQUARTERS WAS DESTROYED", vw_ * 0.5f, vh_ * 0.5f - 16.0f, 14.0f,
                     kText, true);
        }
        char buf[64];
        int minutes = (int)game.time / 60;
        int seconds = (int)game.time % 60;
        std::snprintf(buf, sizeof(buf), "BATTLE TIME  %d:%02d", minutes, seconds);
        drawText(buf, vw_ * 0.5f, vh_ * 0.5f + 18.0f, 13.0f, kTextDim, true);
        if (ui.resultTimer > 1.2f) {
            float blink = 0.55f + 0.45f * std::sin(ui.time * 4.0f);
            Color hint = kGold;
            hint.a = (uint8_t)(255 * blink);
            drawText("TAP TO PLAY AGAIN", vw_ * 0.5f, vh_ * 0.5f + 68.0f, 18.0f, hint, true);
        }
    }
}

void Renderer::draw(Game& game, const Camera& cam, UIState& ui) {
    refreshSize();
    SDL_RenderSetScale(ren_, scale_, scale_);
    setColor(rgba(9, 12, 15));
    SDL_RenderClear(ren_);

    drawWorld(game, cam, ui);
    drawHud(game, cam, ui);
    drawOverlays(game, ui, cam);

    SDL_RenderSetScale(ren_, 1.0f, 1.0f);
    SDL_RenderPresent(ren_);
}

}  // namespace rts
