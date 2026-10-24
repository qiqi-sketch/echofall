cmake_minimum_required(VERSION 3.18)
project(frontline LANGUAGES C CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_CXX_EXTENSIONS OFF)

set(RTS_SOURCES
    src/main.cpp
    src/game.cpp
    src/path.cpp
    src/render.cpp
    src/font.cpp)

if(ANDROID)
    # ---- Android: 用 NDK 构建 libmain.so，SDL2 从源码编译 ----
    if(NOT SDL2_SOURCE_DIR AND DEFINED ENV{SDL2_SOURCE_DIR})
        set(SDL2_SOURCE_DIR "$ENV{SDL2_SOURCE_DIR}")
    endif()

    # Gradle 会为每个 ABI 单独 configure，这里只编译共享库
    set(SDL_SHARED ON CACHE BOOL "" FORCE)
    set(SDL_STATIC OFF CACHE BOOL "" FORCE)
    set(SDL_TEST OFF CACHE BOOL "" FORCE)
    set(SDL2_DISABLE_SDL2MAIN ON CACHE BOOL "" FORCE)

    if(SDL2_SOURCE_DIR)
        add_subdirectory(${SDL2_SOURCE_DIR} ${CMAKE_BINARY_DIR}/SDL2)
    else()
        include(FetchContent)
        FetchContent_Declare(
            SDL2
            URL https://github.com/libsdl-org/SDL/releases/download/release-2.30.9/SDL2-2.30.9.tar.gz
            URL_HASH SHA256=24b574f71c87a763f50704bbb630cbe38298d544a1f890f099a4696b1d6beba4
            DOWNLOAD_EXTRACT_TIMESTAMP TRUE)
        FetchContent_MakeAvailable(SDL2)
    endif()

    # 输出名必须是 libmain.so，SDLActivity 会加载它
    add_library(main SHARED ${RTS_SOURCES})
    target_include_directories(main PRIVATE src)
    target_link_libraries(main PRIVATE SDL2::SDL2 log android)

    # 只保留横屏
    set_target_properties(main PROPERTIES OUTPUT_NAME "main")
else()
    # ---- 桌面端（Linux/macOS）：本地调试用 ----
    find_package(PkgConfig REQUIRED)
    pkg_check_modules(SDL2 REQUIRED IMPORTED_TARGET sdl2)

    add_executable(frontline ${RTS_SOURCES})
    target_include_directories(frontline PRIVATE src)
    target_link_libraries(frontline PRIVATE PkgConfig::SDL2 m)

    add_executable(selftest src/game.cpp src/path.cpp src/selftest.cpp)
    target_include_directories(selftest PRIVATE src)
    target_link_libraries(selftest PRIVATE m)
endif()
