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
    private float aspect = 16f / 9f;

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
                -.5f, -.5f, -.5f, 0, 0, -1, .5f, -.5f, -.5f, 0, 0, -1, .5f, .5f, -.5f, 0, 0, -1,
                -.5f, -.5f, -.5f, 0, 0, -1, .5f, .5f, -.5f, 0, 0, -1, -.5f, .5f, -.5f, 0, 0, -1,
                -.5f, -.5f, .5f, 0, 0, 1, -.5f, .5f, .5f, 0, 0, 1, .5f, .5f, .5f, 0, 0, 1,
                -.5f, -.5f, .5f, 0, 0, 1, .5f, .5f, .5f, 0, 0, 1, .5f, -.5f, .5f, 0, 0, 1,
                -.5f, -.5f, -.5f, -1, 0, 0, -.5f, .5f, -.5f, -1, 0, 0, -.5f, .5f, .5f, -1, 0, 0,
                -.5f, -.5f, -.5f, -1, 0, 0, -.5f, .5f, .5f, -1, 0, 0, -.5f, -.5f, .5f, -1, 0, 0,
                .5f, -.5f, -.5f, 1, 0, 0, .5f, -.5f, .5f, 1, 0, 0, .5f, .5f, .5f, 1, 0, 0,
                .5f, -.5f, -.5f, 1, 0, 0, .5f, .5f, .5f, 1, 0, 0, .5f, .5f, -.5f, 1, 0, 0,
                -.5f, -.5f, -.5f, 0, -1, 0, -.5f, -.5f, .5f, 0, -1, 0, .5f, -.5f, .5f, 0, -1, 0,
                -.5f, -.5f, -.5f, 0, -1, 0, .5f, -.5f, .5f, 0, -1, 0, .5f, -.5f, -.5f, 0, -1, 0,
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
            aspect = w / (float) h;
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
        GLES20.glEnable(GLES20.GL_BLEND);
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA);

        Matrix.perspectiveM(proj, 0, s.fov(), aspect, 0.08f, 160f);

        Player p = s.player;
        float cp = (float) Math.cos(p.pitch);
        float dx = (float) (Math.sin(p.yaw) * cp);
        float dy = (float) Math.sin(p.pitch);
        float dz = (float) (-Math.cos(p.yaw) * cp);
        float ex = p.x;
        float ey = p.y + p.eyeHeight();
        float ez = p.z;
        Matrix.setLookAtM(view, 0, ex, ey, ez, ex + dx, ey + dy, ez + dz, 0f, 1f, 0f);

        // 地面
        box(view, MapGrid.W / 2f, -0.05f, MapGrid.H / 2f, MapGrid.W, 0.1f, MapGrid.H,
                0.28f, 0.29f, 0.31f, 1f);

        // A / B 点地面标记
        drawSiteMarkers();

        // 墙体
        for (int x = 0; x < MapGrid.W; x++) {
            for (int z = 0; z < MapGrid.H; z++) {
                if (!s.map.wall[x][z]) continue;
                if ((x + z) % 2 == 0) {
                    box(view, x + 0.5f, 1.5f, z + 0.5f, 1f, 3f, 1f, 0.44f, 0.45f, 0.48f, 1f);
                } else {
                    box(view, x + 0.5f, 1.5f, z + 0.5f, 1f, 3f, 1f, 0.37f, 0.38f, 0.41f, 1f);
                }
            }
        }

        // 炸弹
        if (s.bombPlanted) {
            box(view, s.bombX, 0.12f, s.bombZ, 0.55f, 0.24f, 0.42f, 0.16f, 0.16f, 0.18f, 1f);
            float blink = (float) (Math.sin(s.time * 9f) > 0 ? 1f : 0.2f);
            box(view, s.bombX, 0.30f, s.bombZ, 0.14f, 0.12f, 0.14f, blink, 0.12f, 0.08f, 1f);
        }

        // 角色
        for (Bot b : s.bots) {
            if (!b.alive) continue;
            drawActor(b);
        }

        // 弹道
        for (Tracer t : s.tracers) {
            float a = Math.min(1f, t.life / 0.13f);
            beam(t.x0, t.y0, t.z0, t.x1, t.y1, t.z1, 0.032f, 1f, 0.94f, 0.66f, a);
        }

        drawViewModel();
    }

    private void drawSiteMarkers() {
        int[] a = MapGrid.SITE_A;
        int[] b = MapGrid.SITE_B;
        box(view, (a[0] + a[2] + 1) / 2f, 0.012f, (a[1] + a[3] + 1) / 2f,
                a[2] - a[0] + 1, 0.02f, a[3] - a[1] + 1, 0.52f, 0.24f, 0.18f, 0.75f);
        box(view, (b[0] + b[2] + 1) / 2f, 0.012f, (b[1] + b[3] + 1) / 2f,
                b[2] - b[0] + 1, 0.02f, b[3] - b[1] + 1, 0.18f, 0.30f, 0.55f, 0.75f);
    }

    private void drawActor(Bot b) {
        float flash = b.hitFlash > 0f ? 0.45f : 0f;
        if (b.team == GameState.TEAM_CT) {
            float r = 0.28f + flash;
            float g = 0.48f + flash;
            float bl = 0.86f + flash;
            box(view, b.x, 0.42f, b.z, 0.55f, 0.84f, 0.40f, r * 0.55f, g * 0.55f, bl * 0.55f, 1f);
            box(view, b.x, 1.10f, b.z, 0.52f, 0.60f, 0.36f, r, g, bl, 1f);
            box(view, b.x, 1.62f, b.z, 0.30f, 0.30f, 0.30f, 0.86f, 0.72f, 0.62f, 1f);
            box(view, b.x, 1.60f, b.z, 0.34f, 0.16f, 0.34f, r * 0.7f, g * 0.7f, bl * 0.7f, 1f);
        } else {
            float r = 0.80f + flash;
            float g = 0.55f + flash;
            float bl = 0.22f + flash;
            box(view, b.x, 0.42f, b.z, 0.55f, 0.84f, 0.40f, 0.30f, 0.26f, 0.20f, 1f);
            box(view, b.x, 1.10f, b.z, 0.52f, 0.60f, 0.36f, r, g, bl, 1f);
            box(view, b.x, 1.62f, b.z, 0.30f, 0.30f, 0.30f, 0.88f, 0.74f, 0.63f, 1f);
            box(view, b.x, 1.60f, b.z, 0.34f, 0.16f, 0.34f, r * 0.7f, g * 0.7f, bl * 0.7f, 1f);
        }
        // 枪
        boxYaw(view, b.x + (float) Math.sin(b.aimYaw) * 0.36f, 1.16f,
                b.z - (float) Math.cos(b.aimYaw) * 0.36f, b.aimYaw,
                0.10f, 0.10f, 0.56f, 0.13f, 0.13f, 0.15f, 1f);
    }

    private void drawViewModel() {
        Player p = s.player;
        if (p.hp <= 0) return;
        if (p.adsT > 0.85f && p.current.scoped) return;

        GLES20.glClear(GLES20.GL_DEPTH_BUFFER_BIT);

        float drop = 0f;
        if (p.switchT > 0f) {
            float t = 1f - p.switchT / 0.35f;
            drop = (float) Math.sin(t * Math.PI) * 0.42f;
        }
        float bobY = (float) Math.sin(p.bob) * 0.008f;
        float bobX = (float) Math.cos(p.bob * 0.5f) * 0.010f;
        float recoil = p.recoilT > 0f ? p.recoilT * 0.012f : 0f;

        Matrix.setIdentityM(vm, 0);
        if (p.adsT > 0.5f) {
            Matrix.translateM(vm, 0, 0.0f, -0.12f, -0.42f);
        } else {
            Matrix.translateM(vm, 0, 0.30f + bobX, -0.27f - drop + bobY, -0.55f + recoil);
        }
        Matrix.rotateM(vm, 0, -drop * 130f, 1f, 0f, 0f);

        WeaponType w = p.current;
        if (w == WeaponType.RIFLE) {
            box(vm, 0f, 0f, -0.10f, 0.09f, 0.10f, 0.55f, 0.16f, 0.17f, 0.19f, 1f);
            box(vm, 0f, 0.02f, -0.46f, 0.04f, 0.04f, 0.30f, 0.10f, 0.10f, 0.11f, 1f);
            box(vm, 0f, -0.10f, -0.02f, 0.06f, 0.20f, 0.12f, 0.10f, 0.10f, 0.11f, 1f);
            box(vm, 0f, -0.02f, 0.19f, 0.06f, 0.08f, 0.18f, 0.24f, 0.15f, 0.08f, 1f);
            box(vm, 0f, 0.08f, -0.06f, 0.05f, 0.05f, 0.16f, 0.08f, 0.08f, 0.09f, 1f);
            muzzle(w, 0f, 0.02f, -0.64f);
        } else if (w == WeaponType.SMG) {
            box(vm, 0f, 0f, -0.08f, 0.10f, 0.11f, 0.38f, 0.15f, 0.15f, 0.17f, 1f);
            box(vm, 0f, 0.01f, -0.30f, 0.05f, 0.05f, 0.18f, 0.09f, 0.09f, 0.10f, 1f);
            box(vm, 0f, -0.13f, -0.04f, 0.05f, 0.20f, 0.09f, 0.09f, 0.09f, 0.10f, 1f);
            box(vm, 0f, 0.06f, 0.14f, 0.06f, 0.06f, 0.16f, 0.30f, 0.30f, 0.32f, 1f);
            muzzle(w, 0f, 0.01f, -0.40f);
        } else if (w == WeaponType.SHOTGUN) {
            box(vm, 0f, 0f, -0.14f, 0.10f, 0.10f, 0.62f, 0.20f, 0.14f, 0.10f, 1f);
            box(vm, 0f, 0.05f, -0.50f, 0.09f, 0.09f, 0.34f, 0.13f, 0.13f, 0.14f, 1f);
            box(vm, 0f, -0.02f, 0.06f, 0.12f, 0.12f, 0.24f, 0.22f, 0.15f, 0.10f, 1f);
            box(vm, 0f, -0.04f, 0.20f, 0.08f, 0.10f, 0.30f, 0.26f, 0.18f, 0.10f, 1f);
            muzzle(w, 0f, 0.05f, -0.68f);
        } else if (w == WeaponType.AWP) {
            box(vm, 0f, 0f, -0.18f, 0.09f, 0.10f, 0.78f, 0.22f, 0.30f, 0.20f, 1f);
            box(vm, 0f, 0.03f, -0.62f, 0.045f, 0.045f, 0.42f, 0.10f, 0.10f, 0.11f, 1f);
            box(vm, 0f, 0.12f, -0.18f, 0.10f, 0.10f, 0.36f, 0.11f, 0.11f, 0.13f, 1f);
            box(vm, 0f, 0.12f, -0.36f, 0.13f, 0.13f, 0.05f, 0.08f, 0.10f, 0.14f, 1f);
            box(vm, 0f, -0.14f, 0.02f, 0.06f, 0.18f, 0.12f, 0.09f, 0.09f, 0.10f, 1f);
            box(vm, 0f, -0.03f, 0.28f, 0.07f, 0.10f, 0.34f, 0.19f, 0.24f, 0.17f, 1f);
            muzzle(w, 0f, 0.03f, -0.84f);
        } else if (w == WeaponType.DEAGLE) {
            box(vm, 0f, 0.03f, -0.16f, 0.085f, 0.09f, 0.34f, 0.20f, 0.20f, 0.22f, 1f);
            box(vm, 0f, -0.08f, 0.02f, 0.07f, 0.20f, 0.10f, 0.12f, 0.12f, 0.13f, 1f);
            box(vm, 0f, 0.10f, -0.30f, 0.03f, 0.03f, 0.08f, 0.10f, 0.10f, 0.11f, 1f);
            muzzle(w, 0f, 0.03f, -0.34f);
        } else if (w == WeaponType.PISTOL) {
            box(vm, 0f, 0.02f, -0.12f, 0.07f, 0.07f, 0.26f, 0.14f, 0.15f, 0.17f, 1f);
            box(vm, 0f, -0.09f, 0.00f, 0.06f, 0.17f, 0.08f, 0.10f, 0.10f, 0.11f, 1f);
            muzzle(w, 0f, 0.02f, -0.26f);
        } else {
            float slash = p.knifeT > 0f ? (float) Math.sin(p.knifeT * Math.PI) : 0f;
            Matrix.rotateM(vm, 0, slash * 70f, 0f, 0f, 1f);
            Matrix.rotateM(vm, 0, -slash * 45f, 1f, 0f, 0f);
            Matrix.translateM(vm, 0, slash * 0.16f, slash * 0.05f, 0f);
            box(vm, 0f, 0.01f, -0.24f, 0.035f, 0.014f, 0.36f, 0.78f, 0.80f, 0.84f, 1f);
            box(vm, 0f, 0.00f, 0.00f, 0.05f, 0.06f, 0.12f, 0.20f, 0.14f, 0.09f, 1f);
        }
    }

    private void muzzle(WeaponType w, float x, float y, float z) {
        if (s.player.muzzleT > 0f) {
            float k = 0.09f + w.recoil * 0.012f;
            box(vm, x, y, z, k, k, 0.10f, 1f, 0.85f, 0.35f, 1f);
            box(vm, x, y, z, k * 0.5f, k * 2.2f, 0.08f, 1f, 0.75f, 0.25f, 1f);
        }
    }

    private void box(float[] base, float x, float y, float z,
                     float sx, float sy, float sz, float r, float g, float b, float a) {
        cube(base, x, y, z, 0f, 0f, sx, sy, sz, r, g, b, a);
    }

    private void boxYaw(float[] base, float x, float y, float z, float yaw,
                        float sx, float sy, float sz, float r, float g, float b, float a) {
        cube(base, x, y, z, (float) Math.toDegrees(yaw), 0f, sx, sy, sz, r, g, b, a);
    }

    private void beam(float x0, float y0, float z0, float x1, float y1, float z1,
                      float width, float r, float g, float b, float a) {
        float dx = x1 - x0;
        float dy = y1 - y0;
        float dz = z1 - z0;
        float len = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (len < 0.08f) return;
        float yaw = (float) Math.toDegrees(Math.atan2(dx, dz));
        float pitch = (float) Math.toDegrees(Math.asin(Math.max(-1f, Math.min(1f, -dy / len))));
        cube(view, (x0 + x1) / 2f, (y0 + y1) / 2f, (z0 + z1) / 2f,
                yaw, pitch, width, width, len, r, g, b, a);
    }

    private void cube(float[] base, float x, float y, float z,
                      float yawDeg, float pitchDeg,
                      float sx, float sy, float sz, float r, float g, float b, float a) {
        Matrix.setIdentityM(model, 0);
        Matrix.translateM(model, 0, x, y, z);
        if (yawDeg != 0f) Matrix.rotateM(model, 0, yawDeg, 0f, 1f, 0f);
        if (pitchDeg != 0f) Matrix.rotateM(model, 0, pitchDeg, 1f, 0f, 0f);
        Matrix.scaleM(model, 0, sx, sy, sz);
        Matrix.multiplyMM(tmp, 0, base, 0, model, 0);
        Matrix.multiplyMM(mvp, 0, proj, 0, tmp, 0);
        GLES20.glUniformMatrix4fv(uMvp, 1, false, mvp, 0);
        GLES20.glUniform4f(uColor, r, g, b, a);
        GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, 36);
    }
}
