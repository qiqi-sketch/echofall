#pragma once

#include <SDL.h>

#include <string>

namespace rts {

// 内置 5x7 像素字体，启动时烘成一张纹理图集，避免依赖字体文件。
class Font {
public:
    bool init(SDL_Renderer* ren);
    void destroy();

    void draw(SDL_Renderer* ren, const std::string& text, float x, float y, float height,
              SDL_Color color) const;
    float width(const std::string& text, float height) const;

private:
    SDL_Texture* tex_ = nullptr;
    int cellW_ = 10;
    int cellH_ = 14;
};

}  // namespace rts
