package com.echofall.game;

import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.opengl.Matrix;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

public class GameRenderer implements GLSurfaceView.Renderer {
    private final GameState s;
    private long last;

    private int program;
    private int aPos;
    private int aNor;
    private int uMvp;
    private int uColor;
    private FloatBuffer cube;

    private final float[] proj = new float[16];
    private final float[] view = new float[16];
    private final float[] model = new float[16];
    private final float[] tmp = new float[16];
    private final float[] mvp = new float[16];
    private final float[] vm = new float[16];

    private static final String VS =
            "uniform mat4 uMvp;\n" +
            "attribute vec3 aPos;\n" +
            "attribute vec3 aNor;\n" +
            "varying mediump vec3 vNor;\n" +
            "void main(){\n" +
            "  vNor = aNor;\n" +
            "  gl_Position = uMvp * vec4(aPos, 1.0);\n" +
            "}\n";

    private static final String FS =
            "precision mediump float;\n" +
            "uniform vec4 uColor;\n" +
            "varying mediump vec3 vNor;\n" +
            "void main(){\n" +
            "  vec3 n = normalize(vNor);\n" +
            "  float d = max(dot(n, normalize(vec3(0.45, 0.9, 0.35))), 0.0);\n" +
            "  vec3 c = uColor.rgb * (0.38 + 0.62 * d);\n" +
            "  gl_FragColor = vec4(c, uColor.a);\n" +
            "}\n";

    // 简化着色器：部分老设备的 GPU 驱动对 varying / 精度比较挑剔，
    // 光照版本链接失败时退回这个纯色版本，保证还能玩。
    private static final String FLAT_VS =
            "uniform mat4 uMvp;\n" +
            "attribute vec3 aPos;\n" +
            "void main(){\n" +
            "  gl_Position = uMvp * vec4(aPos, 1.0);\n" +
            "}\n";

    private static final String FLAT_FS =
            "precision mediump float;\n" +
            "uniform vec4 uColor;\n" +
            "void main(){\n" +
            "  gl_FragColor = uColor;\n" +
            "}\n";

    public GameRenderer(GameState state) {
        s = state;
        last = System.nanoTime();
        cube = makeCube();
    }

    private FloatBuffer makeCube() {
        float[] v = {
                // -Z
                -.5f, -.5f, -.5f, 0, 0, -1, .5f, -.5f, -.5f, 0, 0, -1, .5f, .5f, -.5f, 0, 0, -1,
                -.5f, -.5f, -.5f, 0, 0, -1, .5f, .5f, -.5f, 0, 0, -1, -.5f, .5f, -.5f, 0, 0, -1,
                // +Z
                -.5f, -.5f, .5f, 0, 0, 1, -.5f, .5f, .5f, 0, 0, 1, .5f, .5f, .5f, 0, 0, 1,
                -.5f, -.5f, .5f, 0, 0, 1, .5f, .5f, .5f, 0, 0, 1, .5f, -.5f, .5f, 0, 0, 1,
                // -X
                -.5f, -.5f, -.5f, -1, 0, 0, -.5f, .5f, -.5f, -1, 0, 0, -.5f, .5f, .5f, -1, 0, 0,
                -.5f, -.5f, -.5f, -1, 0, 0, -.5f, .5f, .5f, -1, 0, 0, -.5f, -.5f, .5f, -1, 0, 0,
                // +X
                .5f, -.5f, -.5f, 1, 0, 0, .5f, -.5f, .5f, 1, 0, 0, .5f, .5f, .5f, 1, 0, 0,
                .5f, -.5f, -.5f, 1, 0, 0, .5f, .5f, .5f, 1, 0, 0, .5f, .5f, -.5f, 1, 0, 0,
                // -Y
                -.5f, -.5f, -.5f, 0, -1, 0, -.5f, -.5f, .5f, 0, -1, 0, .5f, -.5f, .5f, 0, -1, 0,
                -.5f, -.5f, -.5f, 0, -1, 0, .5f, -.5f, .5f, 0, -1, 0, .5f, -.5f, -.5f, 0, -1, 0,
                // +Y
                -.5f, .5f, -.5f, 0, 1, 0, .5f, .5f, -.5f, 0, 1, 0, .5f, .5f, .5f, 0, 1, 0,
                -.5f, .5f, -.5f, 0, 1, 0, .5f, .5f, .5f, 0, 1, 0, -.5f, .5f, .5f, 0, 1, 0
        };
        FloatBuffer b = ByteBuffer.allocateDirect(v.length * 4)
                .order(ByteOrder.nativeOrder()).asFloatBuffer();
        b.put(v).position(0);
        return b;
    }

    @Override
    public void onSurfaceCreated(GL10 gl, EGLConfig config) {
        try {
            s.glInfo = "GL " + GLES20.glGetString(GLES20.GL_VERSION);
            program = buildProgram(VS, FS);
            if (program == 0) {
                program = buildProgram(FLAT_VS, FLAT_FS);
                if (program == 0) {
                    s.glError = "着色器编译失败，无法渲染";
                    return;
                }
                android.util.Log.w("Echofall", "使用简化着色器");
            }

            aNor = -1;
            aPos = GLES20.glGetAttribLocation(program, "aPos");
            aNor = GLES20.glGetAttribLocation(program, "aNor");
            uMvp = GLES20.glGetUniformLocation(program, "uMvp");
            uColor = GLES20.glGetUniformLocation(program, "uColor");

            if (aPos >= 0) {
                cube.position(0);
                GLES20.glEnableVertexAttribArray(aPos);
                GLES20.glVertexAttribPointer(aPos, 3, GLES20.GL_FLOAT, false, 24, cube);
            }
            if (aNor >= 0) {
                cube.position(3);
                GLES20.glEnableVertexAttribArray(aNor);
                GLES20.glVertexAttribPointer(aNor, 3, GLES20.GL_FLOAT, false, 24, cube);
            }

            GLES20.glClearColor(0.07f, 0.08f, 0.11f, 1f);
            s.glError = null;
            last = System.nanoTime();
        } catch (Throwable e) {
            s.glError = "GL 初始化失败: " + e;
            android.util.Log.e("Echofall", "onSurfaceCreated", e);
        }
    }

    private int buildProgram(String vsSrc, String fsSrc) {
        int vs = compile(GLES20.GL_VERTEX_SHADER, vsSrc);
        int fs = compile(GLES20.GL_FRAGMENT_SHADER, fsSrc);
        if (vs == 0 || fs == 0) return 0;
        int p = GLES20.glCreateProgram();
        GLES20.glAttachShader(p, vs);
        GLES20.glAttachShader(p, fs);
        GLES20.glLinkProgram(p);
        int[] linked = new int[1];
        GLES20.glGetProgramiv(p, GLES20.GL_LINK_STATUS, linked, 0);
        GLES20.glDeleteShader(vs);
        GLES20.glDeleteShader(fs);
        if (linked[0] == 0) {
            android.util.Log.e("Echofall", "Program link failed: "
                    + GLES20.glGetProgramInfoLog(p));
            GLES20.glDeleteProgram(p);
            return 0;
        }
        return p;
    }

    private int compile(int type, String src) {
        int sh = GLES20.glCreateShader(type);
        GLES20.glShaderSource(sh, src);
        GLES20.glCompileShader(sh);
        int[] ok = new int[1];
        GLES20.glGetShaderiv(sh, GLES20.GL_COMPILE_STATUS, ok, 0);
        if (ok[0] == 0) {
            android.util.Log.e("Echofall", "Shader compile failed: "
                    + GLES20.glGetShaderInfoLog(sh) + "\n---\n" + src);
            GLES20.glDeleteShader(sh);
            return 0;
        }
        return sh;
    }

    @Override
    public void onSurfaceChanged(GL10 gl, int width, int height) {
        try {
            int w = Math.max(1, width);
            int h = Math.max(1, height);
            GLES20.glViewport(0, 0, w, h);
            Matrix.perspectiveM(proj, 0, 70f, w / (float) h, 0.1f, 120f);
        } catch (Throwable e) {
            s.glError = "GL 尺寸设置失败: " + e;
            android.util.Log.e("Echofall", "onSurfaceChanged", e);
        }
    }

    @Override
    public void onDrawFrame(GL10 gl) {
        try {
            long now = System.nanoTime();
            float dt = (now - last) / 1_000_000_000f;
            last = now;
            s.update(dt);
            drawWorld();
            s.glFrames++;
        } catch (Throwable e) {
            if (s.glError == null) {
                s.glError = "渲染异常: " + e;
                android.util.Log.e("Echofall", "onDrawFrame", e);
            }
        }
    }

    private void drawWorld() {
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT | GLES20.GL_DEPTH_BUFFER_BIT);
        if (program == 0) return;
        GLES20.glUseProgram(program);
        GLES20.glEnable(GLES20.GL_DEPTH_TEST);
        GLES20.glDisable(GLES20.GL_CULL_FACE);

        float cp = (float) Math.cos(s.player.pitch);
        float dx = (float) (Math.sin(s.player.yaw) * cp);
        float dy = (float) Math.sin(s.player.pitch);
        float dz = (float) (-Math.cos(s.player.yaw) * cp);
        float ex = s.player.x;
        float ey = s.player.y + 1.55f;
        float ez = s.player.z;
        Matrix.setLookAtM(view, 0, ex, ey, ez, ex + dx, ey + dy, ez + dz, 0f, 1f, 0f);

        // 地面
        box(view, MapGrid.W / 2f, -0.05f, MapGrid.H / 2f, MapGrid.W, 0.1f, MapGrid.H,
                0.29f, 0.30f, 0.32f);

        // 墙体
        for (int x = 0; x < MapGrid.W; x++) {
            for (int z = 0; z < MapGrid.H; z++) {
                if (!s.map.wall[x][z]) continue;
                if ((x + z) % 2 == 0) {
                    box(view, x + 0.5f, 1.5f, z + 0.5f, 1f, 3f, 1f, 0.44f, 0.45f, 0.48f);
                } else {
                    box(view, x + 0.5f, 1.5f, z + 0.5f, 1f, 3f, 1f, 0.37f, 0.38f, 0.41f);
                }
            }
        }

        // BOT
        for (Bot b : s.bots) {
            if (!b.alive) continue;
            float flash = b.hitFlash > 0 ? 0.35f : 0f;
            float br = 0.78f + flash;
            float bg = 0.20f + flash;
            float bb = 0.16f + flash * 0.5f;
            box(view, b.x, 0.42f, b.z, 0.18f, 0.84f, 0.22f, 0.22f, 0.23f, 0.26f);
            box(view, b.x + (float) Math.cos(b.yaw) * 0.14f, 0.42f, b.z + (float) Math.sin(b.yaw) * 0.14f,
                    0.18f, 0.84f, 0.22f, 0.22f, 0.23f, 0.26f);
            box(view, b.x, 1.10f, b.z, 0.52f, 0.95f, 0.40f, br, bg, bb);
            box(view, b.x, 1.72f, b.z, 0.32f, 0.34f, 0.32f, br * 0.95f, bg * 1.1f, bb);
            boxRot(view, b.x + (float) Math.sin(b.yaw) * 0.36f, 1.16f,
                    b.z - (float) Math.cos(b.yaw) * 0.36f, b.yaw,
                    0.10f, 0.10f, 0.52f, 0.15f, 0.15f, 0.17f);
        }

        drawViewModel();
    }

    private void drawViewModel() {
        Player p = s.player;
        if (p.hp <= 0) return;

        GLES20.glClear(GLES20.GL_DEPTH_BUFFER_BIT);

        float drop = 0f;
        if (p.switchT > 0) {
            float t = 1f - p.switchT / 0.35f;
            drop = (float) Math.sin(t * Math.PI) * 0.42f;
        }
        float bobY = (float) Math.sin(p.bob) * 0.008f;
        float bobX = (float) Math.cos(p.bob * 0.5f) * 0.010f;

        Matrix.setIdentityM(vm, 0);
        Matrix.translateM(vm, 0, 0.30f + bobX, -0.27f - drop + bobY, -0.55f);
        Matrix.rotateM(vm, 0, -drop * 130f, 1f, 0f, 0f);

        WeaponType w = p.current;
        if (w == WeaponType.RIFLE) {
            box(vm, 0f, 0f, -0.10f, 0.09f, 0.10f, 0.55f, 0.16f, 0.17f, 0.19f);
            box(vm, 0f, 0.02f, -0.46f, 0.04f, 0.04f, 0.28f, 0.10f, 0.10f, 0.11f);
            box(vm, 0f, -0.10f, -0.08f, 0.05f, 0.16f, 0.11f, 0.09f, 0.09f, 0.10f);
            box(vm, 0f, -0.02f, 0.19f, 0.06f, 0.08f, 0.18f, 0.22f, 0.14f, 0.08f);
            box(vm, 0f, 0.08f, -0.06f, 0.03f, 0.04f, 0.10f, 0.08f, 0.08f, 0.09f);
            if (p.muzzleT > 0) {
                box(vm, 0f, 0.02f, -0.63f, 0.14f, 0.14f, 0.10f, 1f, 0.85f, 0.35f);
            }
        } else if (w == WeaponType.PISTOL) {
            box(vm, 0f, 0.02f, -0.12f, 0.07f, 0.07f, 0.24f, 0.14f, 0.15f, 0.17f);
            box(vm, 0f, -0.09f, 0.00f, 0.06f, 0.16f, 0.08f, 0.10f, 0.10f, 0.11f);
            if (p.muzzleT > 0) {
                box(vm, 0f, 0.02f, -0.27f, 0.11f, 0.11f, 0.08f, 1f, 0.85f, 0.35f);
            }
        } else {
            float slash = p.knifeT > 0 ? (float) Math.sin(p.knifeT * Math.PI) : 0f;
            Matrix.rotateM(vm, 0, slash * 70f, 0f, 0f, 1f);
            Matrix.rotateM(vm, 0, -slash * 45f, 1f, 0f, 0f);
            Matrix.translateM(vm, 0, slash * 0.16f, slash * 0.05f, 0f);
            box(vm, 0f, 0.01f, -0.22f, 0.03f, 0.012f, 0.34f, 0.78f, 0.80f, 0.84f);
            box(vm, 0f, 0.00f, 0.00f, 0.045f, 0.055f, 0.12f, 0.20f, 0.14f, 0.09f);
        }
    }

    private void box(float[] base, float x, float y, float z,
                     float sx, float sy, float sz, float r, float g, float b) {
        boxRot(base, x, y, z, 0f, sx, sy, sz, r, g, b);
    }

    private void boxRot(float[] base, float x, float y, float z, float yaw,
                        float sx, float sy, float sz, float r, float g, float b) {
        Matrix.setIdentityM(model, 0);
        Matrix.translateM(model, 0, x, y, z);
        if (yaw != 0f) {
            Matrix.rotateM(model, 0, (float) Math.toDegrees(yaw), 0f, 1f, 0f);
        }
        Matrix.scaleM(model, 0, sx, sy, sz);
        Matrix.multiplyMM(tmp, 0, base, 0, model, 0);
        Matrix.multiplyMM(mvp, 0, proj, 0, tmp, 0);
        GLES20.glUniformMatrix4fv(uMvp, 1, false, mvp, 0);
        GLES20.glUniform4f(uColor, r, g, b, 1f);
        GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, 36);
    }
}
