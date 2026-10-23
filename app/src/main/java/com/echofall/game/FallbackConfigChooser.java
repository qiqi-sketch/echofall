package com.echofall.game;

import android.opengl.GLSurfaceView;

import javax.microedition.khronos.egl.EGL10;
import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.egl.EGLDisplay;

/**
 * 逐级降低要求的 EGL 配置选择器。
 * 默认的 EGLConfigChooser 在某些真机 GPU 上会找不到配置，直接抛
 * "No config chosen"，表现为一打开就闪退。这里从高要求逐级回退。
 */
public class FallbackConfigChooser implements GLSurfaceView.EGLConfigChooser {

    private static final int[][] TIERS = {
            // r, g, b, a, depth, stencil
            {8, 8, 8, 8, 16, 0},
            {8, 8, 8, 0, 16, 0},
            {5, 6, 5, 0, 16, 0},
            {5, 6, 5, 0, 0, 0},
            {0, 0, 0, 0, 0, 0}
    };

    @Override
    public EGLConfig chooseConfig(EGL10 egl, EGLDisplay display) {
        for (int[] t : TIERS) {
            int[] attrib = new int[]{
                    EGL10.EGL_RED_SIZE, t[0],
                    EGL10.EGL_GREEN_SIZE, t[1],
                    EGL10.EGL_BLUE_SIZE, t[2],
                    EGL10.EGL_ALPHA_SIZE, t[3],
                    EGL10.EGL_DEPTH_SIZE, t[4],
                    EGL10.EGL_STENCIL_SIZE, t[5],
                    EGL10.EGL_RENDERABLE_TYPE, 4, // EGL_OPENGL_ES2_BIT
                    EGL10.EGL_NONE
            };
            int[] num = new int[1];
            if (!egl.eglChooseConfig(display, attrib, null, 0, num)) continue;
            if (num[0] <= 0) continue;

            EGLConfig[] configs = new EGLConfig[num[0]];
            if (!egl.eglChooseConfig(display, attrib, configs, configs.length, num)) continue;
            if (num[0] > 0 && configs[0] != null) return configs[0];
        }

        // 最后的兜底：枚举设备上任意一个配置，避免返回 null 导致原生层异常
        int[] total = new int[1];
        egl.eglGetConfigs(display, null, 0, total);
        if (total[0] > 0) {
            EGLConfig[] all = new EGLConfig[total[0]];
            if (egl.eglGetConfigs(display, all, all.length, total) && total[0] > 0) {
                return all[0];
            }
        }
        return null;
    }
}
