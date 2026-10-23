package com.echofall.game;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.View;

public class HudView extends View {
    private final GameState s;
    private final SharedPreferences prefs;
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);

    private static final int FIRE = 0, JUMP = 1, RELOAD = 2, SWITCH = 3, BUY = 4, GEAR = 5,
            CROUCH = 6, PRONE = 7, ADS = 8, CHEAT = 9;
    private static final int N_BTN = 10;
    private static final String[] KEY_LABELS = {"开火", "跳", "换弹", "切枪", "商店", "设置", "蹲", "趴", "开镜", "外挂"};
    private static final float[] DEF_X = {0.870f, 0.735f, 0.930f, 0.845f, 0.100f, 0.945f, 0.645f, 0.575f, 0.735f, 0.100f};
    private static final float[] DEF_Y = {0.755f, 0.900f, 0.535f, 0.620f, 0.800f, 0.100f, 0.745f, 0.895f, 0.600f, 0.700f};

    private static final String[] SHOP_IDS =
            {"rifle", "smg", "shotgun", "awp", "deagle", "pistol", "armor", "ammo", "sell"};
    private static final int N_SHOP = SHOP_IDS.length;

    private static final int SET_EDIT = 0, SET_SENS_D = 1, SET_SENS_U = 2, SET_GYRO = 3,
            SET_GSENS_D = 4, SET_GSENS_U = 5, SET_MODE = 6, SET_TEAM = 7, SET_CLOSE = 8;
    private static final int N_SET = 9;

    private static final int CH_ESP = 0, CH_TRACK = 1, CH_LOCK = 2, CH_JUMP = 3,
            CH_RECOIL = 4, CH_AMMO = 5, CH_CLOSE = 6;
    private static final int N_CHEAT = 7;

    private final float[] bx = new float[N_BTN];
    private final float[] by = new float[N_BTN];

    private boolean shopOpen;
    private boolean settingsOpen;
    private boolean cheatOpen;
    private boolean editMode;
    private int dragKey = -1;
    private float dragDX, dragDY;

    private int joyId = -1;
    private int lookId = -1;
    private int fireId = -1;
    private float joyBaseX, joyBaseY, joyKnobX, joyKnobY;
    private float lastLookX, lastLookY;
    private float lastFireX, lastFireY;

    private final RectF[] shopRects = new RectF[N_SHOP];
    private final RectF shopClose = new RectF();
    private final RectF[] setRects = new RectF[N_SET];
    private final RectF[] cheatRects = new RectF[N_CHEAT];
    private final long createdAt = SystemClock.uptimeMillis();

    // 小地图
    private Bitmap radar;
    private final Paint radarPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final RectF radarDst = new RectF();
    private final Path radarPath = new Path();
    private final Paint radarDot = new Paint(Paint.ANTI_ALIAS_FLAG);

    public HudView(Context c, GameState state) {
        super(c);
        s = state;
        prefs = c.getSharedPreferences("echofall_binds", Context.MODE_PRIVATE);
        for (int i = 0; i < N_SHOP; i++) shopRects[i] = new RectF();
        for (int i = 0; i < N_SET; i++) setRects[i] = new RectF();
        for (int i = 0; i < N_CHEAT; i++) cheatRects[i] = new RectF();
        loadBinds();
        loadSettings();
        loadCheats();
        setFocusable(true);
    }

    private void loadBinds() {
        for (int i = 0; i < N_BTN; i++) {
            bx[i] = prefs.getFloat("x" + i, DEF_X[i]);
            by[i] = prefs.getFloat("y" + i, DEF_Y[i]);
        }
    }

    private void saveBinds() {
        SharedPreferences.Editor e = prefs.edit();
        for (int i = 0; i < N_BTN; i++) {
            e.putFloat("x" + i, bx[i]);
            e.putFloat("y" + i, by[i]);
        }
        e.apply();
    }

    private void resetBinds() {
        for (int i = 0; i < N_BTN; i++) {
            bx[i] = DEF_X[i];
            by[i] = DEF_Y[i];
        }
        saveBinds();
    }

    private void loadSettings() {
        s.sensitivity = prefs.getFloat("sensitivity", 1.0f);
        s.gyroSens = prefs.getFloat("gyroSens", 1.0f);
        s.gyroOn = prefs.getBoolean("gyroOn", false);
    }

    private void saveSettings() {
        prefs.edit()
                .putFloat("sensitivity", s.sensitivity)
                .putFloat("gyroSens", s.gyroSens)
                .putBoolean("gyroOn", s.gyroOn)
                .apply();
    }

    private void loadCheats() {
        s.cheatEsp = prefs.getBoolean("cheatEsp", false);
        s.cheatTrack = prefs.getBoolean("cheatTrack", false);
        s.cheatLock = prefs.getBoolean("cheatLock", false);
        s.cheatAutoJump = prefs.getBoolean("cheatAutoJump", false);
        s.cheatNoRecoil = prefs.getBoolean("cheatNoRecoil", false);
        s.cheatInfAmmo = prefs.getBoolean("cheatInfAmmo", false);
    }

    private void saveCheats() {
        prefs.edit()
                .putBoolean("cheatEsp", s.cheatEsp)
                .putBoolean("cheatTrack", s.cheatTrack)
                .putBoolean("cheatLock", s.cheatLock)
                .putBoolean("cheatAutoJump", s.cheatAutoJump)
                .putBoolean("cheatNoRecoil", s.cheatNoRecoil)
                .putBoolean("cheatInfAmmo", s.cheatInfAmmo)
                .apply();
    }

    private boolean anyCheatOn() {
        return s.cheatEsp || s.cheatTrack || s.cheatLock
                || s.cheatAutoJump || s.cheatNoRecoil || s.cheatInfAmmo;
    }

    private float radius(int key) {
        float base = Math.min(getWidth(), getHeight());
        if (key == FIRE) return base * 0.076f;
        if (key == ADS) return base * 0.062f;
        return base * 0.056f;
    }

    private boolean hitButton(int key, float x, float y) {
        float cx = bx[key] * getWidth();
        float cy = by[key] * getHeight();
        return Math.hypot(x - cx, y - cy) <= radius(key);
    }

    private int hitAnyButton(float x, float y) {
        for (int i = 0; i < N_BTN; i++) {
            if (hitButton(i, x, y)) return i;
        }
        return -1;
    }

    public boolean handleBack() {
        if (cheatOpen) {
            cheatOpen = false;
            return true;
        }
        if (settingsOpen) {
            settingsOpen = false;
            return true;
        }
        if (shopOpen) {
            shopOpen = false;
            return true;
        }
        if (editMode) {
            editMode = false;
            saveBinds();
            return true;
        }
        return false;
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        int action = e.getActionMasked();
        if (action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_POINTER_DOWN) {
            int idx = e.getActionIndex();
            down(e.getPointerId(idx), e.getX(idx), e.getY(idx));
        } else if (action == MotionEvent.ACTION_MOVE) {
            for (int i = 0; i < e.getPointerCount(); i++) {
                move(e.getPointerId(i), e.getX(i), e.getY(i));
            }
        } else if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_POINTER_UP) {
            int idx = e.getActionIndex();
            up(e.getPointerId(idx));
        } else if (action == MotionEvent.ACTION_CANCEL) {
            resetInput();
        }
        return true;
    }

    private void down(int id, float x, float y) {
        if (cheatOpen) {
            for (int i = 0; i < N_CHEAT; i++) {
                if (cheatRects[i].contains(x, y)) {
                    handleCheat(i);
                    return;
                }
            }
            return;
        }

        if (settingsOpen) {
            for (int i = 0; i < N_SET; i++) {
                if (setRects[i].contains(x, y)) {
                    handleSetting(i);
                    return;
                }
            }
            return;
        }

        if (shopOpen) {
            if (shopClose.contains(x, y)) {
                shopOpen = false;
                return;
            }
            for (int i = 0; i < N_SHOP; i++) {
                if (shopRects[i].contains(x, y)) {
                    s.buy(SHOP_IDS[i]);
                    return;
                }
            }
            return;
        }

        if (editMode) {
            if (hitButton(GEAR, x, y)) {
                editMode = false;
                saveBinds();
                return;
            }
            int k = hitAnyButton(x, y);
            if (k >= 0) {
                dragKey = k;
                dragDX = bx[k] * getWidth() - x;
                dragDY = by[k] * getHeight() - y;
            }
            return;
        }

        if (hitButton(GEAR, x, y)) {
            settingsOpen = true;
            return;
        }
        if (hitButton(CHEAT, x, y)) {
            cheatOpen = true;
            return;
        }
        if (hitButton(BUY, x, y)) {
            shopOpen = true;
            return;
        }
        if (hitButton(FIRE, x, y)) {
            fireId = id;
            lastFireX = x;
            lastFireY = y;
            s.input.firing = true;
            s.input.firePressed = true;
            return;
        }
        if (hitButton(ADS, x, y)) {
            s.input.adsPressed = true;
            return;
        }
        if (hitButton(CROUCH, x, y)) {
            s.input.crouchPressed = true;
            return;
        }
        if (hitButton(PRONE, x, y)) {
            s.input.pronePressed = true;
            return;
        }
        if (hitButton(JUMP, x, y)) {
            s.input.jumpPressed = true;
            return;
        }
        if (hitButton(RELOAD, x, y)) {
            s.input.reloadPressed = true;
            return;
        }
        if (hitButton(SWITCH, x, y)) {
            s.input.switchPressed = true;
            return;
        }

        if (x < getWidth() * 0.45f) {
            joyId = id;
            joyBaseX = x;
            joyBaseY = y;
            joyKnobX = x;
            joyKnobY = y;
        } else {
            lookId = id;
            lastLookX = x;
            lastLookY = y;
        }
    }

    private void handleSetting(int i) {
        switch (i) {
            case SET_EDIT:
                settingsOpen = false;
                editMode = true;
                break;
            case SET_SENS_D:
                s.sensitivity = clamp(Math.round((s.sensitivity - 0.1f) * 10f) / 10f, 0.3f, 2.5f);
                saveSettings();
                break;
            case SET_SENS_U:
                s.sensitivity = clamp(Math.round((s.sensitivity + 0.1f) * 10f) / 10f, 0.3f, 2.5f);
                saveSettings();
                break;
            case SET_GYRO:
                s.gyroOn = !s.gyroOn;
                s.toast = s.gyroOn ? "陀螺仪已开启" : "陀螺仪已关闭";
                s.toastT = 1.6f;
                saveSettings();
                break;
            case SET_GSENS_D:
                s.gyroSens = clamp(Math.round((s.gyroSens - 0.1f) * 10f) / 10f, 0.3f, 3.0f);
                saveSettings();
                break;
            case SET_GSENS_U:
                s.gyroSens = clamp(Math.round((s.gyroSens + 0.1f) * 10f) / 10f, 0.3f, 3.0f);
                saveSettings();
                break;
            case SET_MODE:
                s.setMode(s.mode == GameState.MODE_TRAINING
                        ? GameState.MODE_DEFUSE : GameState.MODE_TRAINING);
                break;
            case SET_TEAM:
                s.setTeam(s.player.team == GameState.TEAM_CT
                        ? GameState.TEAM_T : GameState.TEAM_CT);
                break;
            default:
                settingsOpen = false;
                break;
        }
    }

    private void handleCheat(int i) {
        switch (i) {
            case CH_ESP:
                s.cheatEsp = !s.cheatEsp;
                toast(s.cheatEsp ? "ESP 透视：开" : "ESP 透视：关");
                break;
            case CH_TRACK:
                s.cheatTrack = !s.cheatTrack;
                toast(s.cheatTrack ? "子弹追踪：开" : "子弹追踪：关");
                break;
            case CH_LOCK:
                s.cheatLock = !s.cheatLock;
                toast(s.cheatLock ? "枪锁：开（对准自动开火）" : "枪锁：关");
                break;
            case CH_JUMP:
                s.cheatAutoJump = !s.cheatAutoJump;
                toast(s.cheatAutoJump ? "自动连跳：开" : "自动连跳：关");
                break;
            case CH_RECOIL:
                s.cheatNoRecoil = !s.cheatNoRecoil;
                toast(s.cheatNoRecoil ? "无后坐力：开" : "无后坐力：关");
                break;
            case CH_AMMO:
                s.cheatInfAmmo = !s.cheatInfAmmo;
                toast(s.cheatInfAmmo ? "无限弹药：开" : "无限弹药：关");
                break;
            default:
                cheatOpen = false;
                break;
        }
        saveCheats();
    }

    private void toast(String text) {
        s.toast = text;
        s.toastT = 1.4f;
    }

    private void move(int id, float x, float y) {
        if (editMode && dragKey >= 0) {
            bx[dragKey] = clamp((x + dragDX) / getWidth(), 0.04f, 0.96f);
            by[dragKey] = clamp((y + dragDY) / getHeight(), 0.06f, 0.94f);
            return;
        }
        if (id == joyId) {
            float dx = x - joyBaseX;
            float dy = y - joyBaseY;
            float limit = Math.min(getWidth(), getHeight()) * 0.11f;
            float d = (float) Math.hypot(dx, dy);
            if (d > limit) {
                dx = dx / d * limit;
                dy = dy / d * limit;
            }
            joyKnobX = joyBaseX + dx;
            joyKnobY = joyBaseY + dy;
            s.input.moveX = dx / limit;
            s.input.moveY = -dy / limit;
        } else if (id == lookId) {
            s.input.lookDX += x - lastLookX;
            s.input.lookDY += y - lastLookY;
            lastLookX = x;
            lastLookY = y;
        } else if (id == fireId) {
            // 按住开火键的同时滑动，也能转动视角
            s.input.lookDX += (x - lastFireX) * 0.85f;
            s.input.lookDY += (y - lastFireY) * 0.85f;
            lastFireX = x;
            lastFireY = y;
        }
    }

    private void up(int id) {
        if (id == fireId) {
            s.input.firing = false;
            fireId = -1;
        }
        if (id == joyId) {
            joyId = -1;
            s.input.moveX = 0f;
            s.input.moveY = 0f;
        }
        if (id == lookId) {
            lookId = -1;
        }
        if (dragKey >= 0) {
            dragKey = -1;
            saveBinds();
        }
    }

    private void resetInput() {
        s.input.firing = false;
        s.input.moveX = 0f;
        s.input.moveY = 0f;
        fireId = -1;
        joyId = -1;
        lookId = -1;
        dragKey = -1;
    }

    private float clamp(float v, float lo, float hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    @Override
    protected void onDraw(Canvas c) {
        super.onDraw(c);
        float w = getWidth();
        float h = getHeight();
        if (w == 0 || h == 0) return;

        boolean scoped = s.player.current.scoped && s.player.adsT > 0.8f && s.player.hp > 0;
        if (scoped) drawScope(c, w, h);
        if (!scoped) drawCrosshair(c, w, h);
        drawStatus(c, w, h);
        drawRound(c, w, h);
        drawTeamList(c, w, h);
        drawRadar(c, w, h);
        drawDiagnostics(c, w, h);
        drawJoystick(c, w, h);
        drawButtons(c, w, h);

        if (s.damageFlash > 0) {
            p.setColor(0x00000000);
            p.setColor(((int) (Math.min(1f, s.damageFlash) * 110) << 24) | 0xCC1010);
            c.drawRect(0, 0, w, h, p);
        }

        if (shopOpen) drawShop(c, w, h);
        if (settingsOpen) drawSettings(c, w, h);
        if (cheatOpen) drawCheat(c, w, h);
        if (editMode) drawEditHint(c, w, h);

        postInvalidateOnAnimation();
    }

    private void drawScope(Canvas c, float w, float h) {
        float cx = w / 2f;
        float cy = h / 2f;
        float r = h * 0.44f;

        Path path = new Path();
        path.addRect(0, 0, w, h, Path.Direction.CW);
        path.addCircle(cx, cy, r, Path.Direction.CCW);
        path.setFillType(Path.FillType.EVEN_ODD);
        p.setStyle(Paint.Style.FILL);
        p.setColor(0xFF05070A);
        c.drawPath(path, p);

        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(2.5f);
        p.setColor(0xFF6FE08A);
        c.drawCircle(cx, cy, r, p);
        p.setStrokeWidth(1.6f);
        c.drawLine(cx - r, cy, cx - h * 0.03f, cy, p);
        c.drawLine(cx + h * 0.03f, cy, cx + r, cy, p);
        c.drawLine(cx, cy - r, cx, cy - h * 0.03f, p);
        c.drawLine(cx, cy + h * 0.03f, cx, cy + r, p);
        p.setStyle(Paint.Style.FILL);
        p.setColor(0xCC6FE08A);
        c.drawCircle(cx, cy, 2.5f, p);
    }

    private void drawCrosshair(Canvas c, float w, float h) {
        float cx = w / 2f;
        float cy = h / 2f;
        float ads = s.player.adsT;
        float gap = h * (0.012f - 0.006f * ads);
        float len = h * 0.018f;
        p.setColor(0xDD7FE7A0);
        p.setStrokeWidth(Math.max(2f, h * 0.003f));
        p.setStyle(Paint.Style.STROKE);
        c.drawLine(cx - gap - len, cy, cx - gap, cy, p);
        c.drawLine(cx + gap, cy, cx + gap + len, cy, p);
        c.drawLine(cx, cy - gap - len, cx, cy - gap, p);
        c.drawLine(cx, cy + gap, cx, cy + gap + len, p);
        p.setStyle(Paint.Style.FILL);
    }

    private void drawStatus(Canvas c, float w, float h) {
        p.setStyle(Paint.Style.FILL);
        p.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        p.setTextSize(h * 0.030f);
        p.setColor(0xFFEAF2FF);
        c.drawText("$" + s.player.money, w * 0.025f, h * 0.085f, p);

        p.setTextSize(h * 0.023f);
        p.setColor(0xFF9DB2C8);
        c.drawText("护甲 " + s.player.armor + "   击杀 " + s.player.kills + "   死亡 " + s.player.deaths,
                w * 0.025f, h * 0.125f, p);

        // 血条
        float barW = w * 0.22f;
        float barH = h * 0.022f;
        float bx0 = w * 0.025f;
        float by0 = h * 0.90f;
        p.setColor(0xAA000000);
        c.drawRoundRect(new RectF(bx0 - 2, by0 - 2, bx0 + barW + 2, by0 + barH + 2), 4, 4, p);
        float hp = Math.max(0, s.player.hp) / 100f;
        p.setColor(hp > 0.5f ? 0xFF4CD07A : (hp > 0.25f ? 0xFFE0B84A : 0xFFE04A4A));
        c.drawRect(bx0, by0, bx0 + barW * hp, by0 + barH, p);
        p.setColor(0xFFEAF2FF);
        p.setTextSize(h * 0.021f);
        c.drawText("HP " + Math.max(0, s.player.hp), bx0 + barW + h * 0.015f, by0 + barH, p);

        // 姿态
        String stance = s.player.stance == Player.STANCE_CROUCH ? "蹲"
                : (s.player.stance == Player.STANCE_PRONE ? "趴" : "站");
        p.setColor(0xFF90B4D8);
        p.setTextSize(h * 0.019f);
        c.drawText("姿态 " + stance + (s.player.ads ? " · 开镜" : ""), bx0, by0 - h * 0.010f, p);

        if (s.player.hasBomb && s.mode == GameState.MODE_DEFUSE && !s.bombPlanted) {
            p.setColor(0xFFFFD166);
            p.setTextSize(h * 0.020f);
            c.drawText("携带炸弹 · 前往 " + (s.bombSite == 0 ? "A" : "B") + " 点安放",
                    bx0, by0 - h * 0.032f, p);
        } else if (s.bombPlanted && s.player.team == GameState.TEAM_CT && s.player.hp > 0) {
            p.setColor(0xFF5CC8FF);
            p.setTextSize(h * 0.020f);
            c.drawText("前往炸弹处拆除", bx0, by0 - h * 0.032f, p);
        }

        // 弹药
        WeaponType wt = s.player.current;
        String ammo;
        if (wt == WeaponType.KNIFE) {
            ammo = "刀 · 移速 x" + String.format("%.2f", wt.moveMult);
        } else {
            int oi = wt.ordinal();
            ammo = wt.label + "  " + s.player.mag[oi] + " / " + s.player.reserve[oi];
        }
        p.setTextSize(h * 0.030f);
        p.setTextAlign(Paint.Align.RIGHT);
        p.setColor(0xFFEAF2FF);
        c.drawText(ammo, w * 0.975f, h * 0.90f, p);
        if (s.player.reloadT > 0) {
            p.setTextSize(h * 0.024f);
            p.setColor(0xFFFFC84A);
            c.drawText("换弹中...", w * 0.975f, h * 0.855f, p);
        }
        p.setTextAlign(Paint.Align.LEFT);

        // 提示
        if (s.toastT > 0 && s.toast.length() > 0) {
            p.setTextSize(h * 0.028f);
            float tw = p.measureText(s.toast);
            float tx = w / 2f - tw / 2f;
            p.setColor(0xAA000000);
            c.drawRoundRect(new RectF(tx - 18, h * 0.205f - h * 0.028f,
                    tx + tw + 18, h * 0.205f + h * 0.016f), 8, 8, p);
            p.setColor(0xFFFFD166);
            c.drawText(s.toast, tx, h * 0.205f, p);
        }

        if (s.player.hp <= 0) {
            p.setTextAlign(Paint.Align.CENTER);
            p.setTextSize(h * 0.050f);
            p.setColor(0xFFFF6A6A);
            String t = s.mode == GameState.MODE_TRAINING
                    ? "阵亡 · " + String.format("%.1f", Math.max(0, s.player.respawnT)) + " 秒后重生"
                    : "阵亡 · 等待回合结束";
            c.drawText(t, w / 2f, h / 2f - h * 0.08f, p);
            p.setTextAlign(Paint.Align.LEFT);
        }
    }

    private void drawRound(Canvas c, float w, float h) {
        p.setTextAlign(Paint.Align.CENTER);
        if (s.mode == GameState.MODE_DEFUSE) {
            String line = "第 " + s.round + " 回合   CT " + s.scoreCT + " : " + s.scoreT + " T";
            p.setTextSize(h * 0.026f);
            p.setColor(0xFFEAF2FF);
            c.drawText(line, w / 2f, h * 0.055f, p);

            String timer;
            if (s.roundEndT > 0) {
                timer = s.roundTitle;
                p.setColor(0xFFFFD166);
            } else {
                int t = (int) Math.max(0f, s.roundTime);
                timer = String.format("%d:%02d", t / 60, t % 60);
                p.setColor(s.roundTime < 20f ? 0xFFFF8A6A : 0xFF9DB2C8);
            }
            p.setTextSize(h * 0.022f);
            c.drawText(timer, w / 2f, h * 0.088f, p);

            if (s.bombPlanted && s.roundEndT <= 0) {
                p.setColor((((int) (s.bombTimer * 3) % 2 == 0) ? 0xFFFF3B3B : 0xFFFFB03B));
                p.setTextSize(h * 0.030f);
                c.drawText("炸弹 " + String.format("%.1f", Math.max(0f, s.bombTimer)) + " 秒",
                        w / 2f, h * 0.125f, p);
            }
            if (s.plantProgress > 0.05f && !s.bombPlanted) {
                drawProgress(c, w, h, s.plantProgress / GameState.PLANT_TIME, 0xFFFFB03B, "安放中");
            }
            if (s.bombPlanted && s.defuseProgress > 0.05f) {
                drawProgress(c, w, h, s.defuseProgress / GameState.DEFUSE_TIME, 0xFF5CC8FF, "拆除中");
            }
        } else {
            p.setTextSize(h * 0.024f);
            p.setColor(0xFF9DB2C8);
            c.drawText("训练场 · 无限重生", w / 2f, h * 0.055f, p);
        }
        p.setTextAlign(Paint.Align.LEFT);
    }

    private void drawProgress(Canvas c, float w, float h, float k, int color, String label) {
        k = clamp(k, 0f, 1f);
        float bw = w * 0.20f;
        float bh = h * 0.016f;
        float x0 = w / 2f - bw / 2f;
        float y0 = h * 0.142f;
        p.setColor(0xAA000000);
        c.drawRect(x0 - 2, y0 - 2, x0 + bw + 2, y0 + bh + 2, p);
        p.setColor(color);
        c.drawRect(x0, y0, x0 + bw * k, y0 + bh, p);
        p.setTextSize(h * 0.018f);
        p.setTextAlign(Paint.Align.CENTER);
        p.setColor(0xFFEAF2FF);
        c.drawText(label, w / 2f, y0 - h * 0.006f, p);
        p.setTextAlign(Paint.Align.LEFT);
    }

    private void drawTeamList(Canvas c, float w, float h) {
        float x0 = w * 0.025f;
        float y0 = h * 0.185f;
        float rowH = h * 0.031f;
        float barW = w * 0.055f;
        float barH = h * 0.010f;
        p.setTextSize(h * 0.018f);
        p.setTextAlign(Paint.Align.LEFT);
        int i = 0, allyN = 0, enemyN = 0;
        for (Bot b : s.bots) {
            boolean ally = b.team == s.player.team;
            float y = y0 + i * rowH;
            p.setColor(ally ? 0xFF6FA8FF : 0xFFFF9A7A);
            c.drawText((ally ? "队友" : "敌人") + (ally ? ++allyN : ++enemyN), x0, y, p);
            if (b.alive) {
                float bx0 = x0 + w * 0.045f;
                p.setColor(0x99000000);
                c.drawRect(bx0, y - barH, bx0 + barW, y, p);
                p.setColor(ally ? 0xFF4CD07A : 0xFFE04A4A);
                c.drawRect(bx0, y - barH, bx0 + barW * Math.max(0, b.hp) / 100f, y, p);
            } else {
                p.setColor(0xFF7A8899);
                c.drawText("阵亡", x0 + w * 0.045f, y, p);
            }
            i++;
        }
    }

    private void drawDiagnostics(Canvas c, float w, float h) {
        p.setStyle(Paint.Style.FILL);
        p.setTextAlign(Paint.Align.LEFT);
        p.setTextSize(h * 0.017f);
        p.setColor(0x8890A8C0);
        c.drawText("v" + BuildConfig.VERSION_NAME + "  " + s.glInfo, w * 0.025f, h * 0.030f, p);

        String err = s.glError;
        if (err == null && s.glFrames == 0 && SystemClock.uptimeMillis() - createdAt > 4000) {
            err = "渲染未启动（黑屏请截图反馈）";
        }
        if (err != null) {
            p.setTextSize(h * 0.020f);
            p.setColor(0xFFFF8A8A);
            c.drawText(err, w * 0.025f, h * 0.058f, p);
        }
    }

    private void drawRadar(Canvas c, float w, float h) {
        if (radar == null) {
            int[] px = s.map.radarPixels();
            radar = Bitmap.createBitmap(px, MapGrid.W, MapGrid.H, Bitmap.Config.ARGB_8888);
        }
        float size = Math.min(w, h) * 0.30f;
        float left = w - size - w * 0.018f;
        float top = h * 0.17f;
        radarDst.set(left, top, left + size, top + size);

        p.setStyle(Paint.Style.FILL);
        p.setColor(0xCC0B0F16);
        c.drawRoundRect(new RectF(left - 4, top - 4, left + size + 4, top + size + 4), 8, 8, p);

        radarPaint.setAlpha(235);
        c.drawBitmap(radar, null, radarDst, radarPaint);

        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(2f);
        p.setColor(0xAA8FA3C0);
        c.drawRoundRect(radarDst, 5, 5, p);
        p.setStyle(Paint.Style.FILL);

        float sx = size / MapGrid.W;
        float sz = size / MapGrid.H;

        p.setTextSize(size * 0.13f);
        p.setTextAlign(Paint.Align.CENTER);
        p.setColor(0xFFFF8A6A);
        c.drawText("A", left + MapGrid.A_CENTER_X * sx, top + MapGrid.A_CENTER_Z * sz, p);
        p.setColor(0xFF7FB4FF);
        c.drawText("B", left + MapGrid.B_CENTER_X * sx, top + MapGrid.B_CENTER_Z * sz, p);
        p.setTextAlign(Paint.Align.LEFT);

        // 队友始终可见，敌人只在 26 米内且有视线时显示（开启 ESP 后全显）
        for (Bot b : s.bots) {
            if (!b.alive) continue;
            boolean ally = b.team == s.player.team;
            if (!ally && !s.cheatEsp) {
                float d = (float) Math.hypot(b.x - s.player.x, b.z - s.player.z);
                if (d > 26f || !s.map.lineOfSight(s.player.x, s.player.z, b.x, b.z)) continue;
            }
            radarDot.setColor(ally ? 0xFF6FE08A : 0xFFFF5A4A);
            c.drawCircle(left + b.x * sx, top + b.z * sz,
                    Math.max(2f, size * 0.018f), radarDot);
        }

        if (s.bombPlanted) {
            boolean on = (int) (s.time * 4f) % 2 == 0;
            radarDot.setColor(on ? 0xFFFF3B3B : 0xFFFFB03B);
            c.drawCircle(left + s.bombX * sx, top + s.bombZ * sz,
                    Math.max(3f, size * 0.024f), radarDot);
        }

        float px = left + s.player.x * sx;
        float py = top + s.player.z * sz;
        c.save();
        c.rotate((float) Math.toDegrees(s.player.yaw), px, py);
        radarPath.reset();
        float a = Math.max(4f, size * 0.032f);
        radarPath.moveTo(px, py - a * 1.6f);
        radarPath.lineTo(px - a, py + a);
        radarPath.lineTo(px + a, py + a);
        radarPath.close();
        p.setColor(0xFFFFFFFF);
        c.drawPath(radarPath, p);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(1.5f);
        p.setColor(0xFF101820);
        c.drawPath(radarPath, p);
        p.setStyle(Paint.Style.FILL);
        c.restore();

        if (s.player.hasBomb) {
            radarDot.setColor(0xFFFFD166);
            c.drawCircle(px, py, Math.max(3f, size * 0.020f), radarDot);
        }

        p.setTextSize(size * 0.10f);
        p.setColor(0x99AFC4DA);
        c.drawText("N", left + 3, top + size * 0.12f, p);
    }

    private void drawJoystick(Canvas c, float w, float h) {
        if (joyId < 0) return;
        float r = Math.min(w, h) * 0.11f;
        p.setStyle(Paint.Style.FILL);
        p.setColor(0x33000000);
        c.drawCircle(joyBaseX, joyBaseY, r, p);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(2.5f);
        p.setColor(0x8890A8C0);
        c.drawCircle(joyBaseX, joyBaseY, r, p);
        p.setStyle(Paint.Style.FILL);
        p.setColor(0xBB3A4A60);
        c.drawCircle(joyKnobX, joyKnobY, r * 0.42f, p);
    }

    private void drawButtons(Canvas c, float w, float h) {
        for (int i = 0; i < N_BTN; i++) {
            float cx = bx[i] * w;
            float cy = by[i] * h;
            float r = radius(i);
            boolean active = (i == FIRE && s.input.firing) || (i == ADS && s.player.ads);
            p.setStyle(Paint.Style.FILL);
            if (i == FIRE) p.setColor(active ? 0x88E04A4A : 0x55E04A4A);
            else if (i == ADS && active) p.setColor(0x8830A0C8);
            else if (i == GEAR && editMode) p.setColor(0x66FFD166);
            else if (i == CHEAT && anyCheatOn()) p.setColor(0x66D03030);
            else p.setColor(0x44303A48);
            c.drawCircle(cx, cy, r, p);

            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(editMode ? 3f : 2f);
            p.setColor(editMode ? 0xFFFFD166 : 0xAA8FA3C0);
            c.drawCircle(cx, cy, r, p);

            p.setStyle(Paint.Style.FILL);
            p.setColor(0xFFEAF2FF);
            p.setTextSize(r * 0.60f);
            p.setTextAlign(Paint.Align.CENTER);
            Paint.FontMetrics fm = p.getFontMetrics();
            c.drawText(KEY_LABELS[i], cx, cy - (fm.ascent + fm.descent) / 2f, p);
            p.setTextAlign(Paint.Align.LEFT);
        }
    }

    private void drawEditHint(Canvas c, float w, float h) {
        p.setColor(0xAA000000);
        c.drawRoundRect(new RectF(w * 0.18f, h * 0.10f, w * 0.82f, h * 0.155f), 10, 10, p);
        p.setColor(0xFFFFD166);
        p.setTextSize(h * 0.026f);
        p.setTextAlign(Paint.Align.CENTER);
        c.drawText("键位编辑：拖动按钮调整位置，点“设置”完成", w / 2f, h * 0.138f, p);
        p.setTextAlign(Paint.Align.LEFT);
    }

    private void drawShop(Canvas c, float w, float h) {
        p.setColor(0xCC0B0F16);
        c.drawRect(0, 0, w, h, p);

        float pw = w * 0.66f;
        float ph = h * 0.90f;
        float left = (w - pw) / 2f;
        float top = (h - ph) / 2f;
        p.setColor(0xF21A2230);
        c.drawRoundRect(new RectF(left, top, left + pw, top + ph), 18, 18, p);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(3f);
        p.setColor(0xFF3A4A62);
        c.drawRoundRect(new RectF(left, top, left + pw, top + ph), 18, 18, p);
        p.setStyle(Paint.Style.FILL);

        p.setColor(0xFF7FD1FF);
        p.setTextSize(h * 0.034f);
        c.drawText("商店 BUY MENU", left + pw * 0.05f, top + ph * 0.075f, p);

        p.setColor(0xFFEAF2FF);
        p.setTextSize(h * 0.026f);
        p.setTextAlign(Paint.Align.RIGHT);
        c.drawText("资金 $" + s.player.money, left + pw * 0.95f, top + ph * 0.075f, p);
        p.setTextAlign(Paint.Align.LEFT);

        float rowTop = top + ph * 0.115f;
        float rowH = ph * 0.082f;
        float rx0 = left + pw * 0.04f;
        float rx1 = left + pw * 0.96f;

        for (int i = 0; i < N_SHOP; i++) {
            shopRects[i].set(rx0, rowTop + i * rowH, rx1, rowTop + (i + 1) * rowH - h * 0.006f);
            boolean owned = false;
            String title;
            String sub;
            if (i < 6) {
                WeaponType wt = shopWeapon(i);
                owned = s.player.owns(wt);
                title = wt.label + "   $" + wt.price;
                String kind = wt.scoped ? "开镜狙击" : (wt.auto ? "全自动" : "单发");
                sub = "伤害 " + wt.damage + " · 弹匣 " + wt.magSize
                        + " · " + kind + (wt.pellets > 1 ? " · " + wt.pellets + " 弹丸" : "");
            } else if (i == 6) {
                title = "护甲   $650";
                sub = "当前护甲 " + s.player.armor;
            } else if (i == 7) {
                title = "弹药   $50";
                sub = "补充两个弹匣";
            } else {
                title = "卖出当前武器";
                sub = s.player.current == WeaponType.KNIFE ? "刀不能出售"
                        : "+$" + (s.player.current.price / 2) + " · " + s.player.current.label;
            }

            p.setColor(owned ? 0x332E4A36 : 0xFF232C3B);
            c.drawRoundRect(shopRects[i], 10, 10, p);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(2f);
            p.setColor(0xFF34455C);
            c.drawRoundRect(shopRects[i], 10, 10, p);
            p.setStyle(Paint.Style.FILL);

            p.setColor(owned ? 0xFF7BE0A0 : 0xFFEAF2FF);
            p.setTextSize(h * 0.025f);
            c.drawText(owned ? title + "  [已拥有]" : title, rx0 + pw * 0.02f,
                    shopRects[i].top + rowH * 0.40f, p);

            p.setColor(0xFF8FA3B8);
            p.setTextSize(h * 0.018f);
            c.drawText(sub, rx0 + pw * 0.02f, shopRects[i].top + rowH * 0.76f, p);
        }

        shopClose.set(rx0, rowTop + N_SHOP * rowH + h * 0.008f, rx1,
                rowTop + N_SHOP * rowH + h * 0.068f);
        p.setColor(0xFF2C3A4E);
        c.drawRoundRect(shopClose, 12, 12, p);
        p.setColor(0xFFEAF2FF);
        p.setTextSize(h * 0.026f);
        p.setTextAlign(Paint.Align.CENTER);
        c.drawText("关闭商店", (shopClose.left + shopClose.right) / 2f,
                shopClose.top + shopClose.height() * 0.68f, p);
        p.setTextAlign(Paint.Align.LEFT);
    }

    private WeaponType shopWeapon(int i) {
        switch (i) {
            case 0: return WeaponType.RIFLE;
            case 1: return WeaponType.SMG;
            case 2: return WeaponType.SHOTGUN;
            case 3: return WeaponType.AWP;
            case 4: return WeaponType.DEAGLE;
            default: return WeaponType.PISTOL;
        }
    }

    private void drawSettings(Canvas c, float w, float h) {
        p.setColor(0xCC0B0F16);
        c.drawRect(0, 0, w, h, p);

        float pw = w * 0.56f;
        float ph = h * 0.92f;
        float left = (w - pw) / 2f;
        float top = (h - ph) / 2f;
        p.setColor(0xF21A2230);
        c.drawRoundRect(new RectF(left, top, left + pw, top + ph), 18, 18, p);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(3f);
        p.setColor(0xFF3A4A62);
        c.drawRoundRect(new RectF(left, top, left + pw, top + ph), 18, 18, p);
        p.setStyle(Paint.Style.FILL);

        p.setColor(0xFF7FD1FF);
        p.setTextSize(h * 0.030f);
        c.drawText("设置 SETTINGS", left + pw * 0.06f, top + ph * 0.075f, p);

        float rowTop = top + ph * 0.115f;
        float rowH = ph * 0.088f;
        float rx0 = left + pw * 0.05f;
        float rx1 = left + pw * 0.95f;

        for (int i = 0; i < N_SET; i++) {
            setRects[i].set(rx0, rowTop + i * rowH, rx1, rowTop + (i + 1) * rowH - h * 0.008f);
            String label;
            switch (i) {
                case SET_EDIT: label = "键位编辑（拖动按钮）"; break;
                case SET_SENS_D: label = "灵敏度 -"; break;
                case SET_SENS_U: label = "灵敏度 +   " + String.format("%.1f", s.sensitivity); break;
                case SET_GYRO: label = "陀螺仪：" + (s.gyroOn ? "开" : "关"); break;
                case SET_GSENS_D: label = "陀螺仪灵敏度 -"; break;
                case SET_GSENS_U:
                    label = "陀螺仪灵敏度 +   " + String.format("%.1f", s.gyroSens); break;
                case SET_MODE:
                    label = "模式：" + (s.mode == GameState.MODE_TRAINING ? "训练场" : "经典爆破"); break;
                case SET_TEAM:
                    label = "阵营：" + (s.player.team == GameState.TEAM_CT ? "警方 CT" : "土匪 T"); break;
                default: label = "关闭"; break;
            }

            boolean highlight = (i == SET_GYRO && s.gyroOn)
                    || (i == SET_CLOSE);
            p.setColor(highlight ? 0xFF2E4A5E : 0xFF232C3B);
            c.drawRoundRect(setRects[i], 10, 10, p);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(2f);
            p.setColor(0xFF34455C);
            c.drawRoundRect(setRects[i], 10, 10, p);
            p.setStyle(Paint.Style.FILL);

            p.setColor(i == SET_CLOSE ? 0xFF9DB2C8 : 0xFFEAF2FF);
            p.setTextSize(h * 0.024f);
            p.setTextAlign(Paint.Align.CENTER);
            c.drawText(label, (rx0 + rx1) / 2f,
                    setRects[i].top + setRects[i].height() * 0.66f, p);
            p.setTextAlign(Paint.Align.LEFT);
        }
    }

    private void drawCheat(Canvas c, float w, float h) {
        p.setStyle(Paint.Style.FILL);
        p.setColor(0xCC0B0F16);
        c.drawRect(0, 0, w, h, p);

        float pw = w * 0.62f;
        float ph = h * 0.92f;
        float left = (w - pw) / 2f;
        float top = (h - ph) / 2f;
        p.setColor(0xF21A1216);
        c.drawRoundRect(new RectF(left, top, left + pw, top + ph), 18, 18, p);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(3f);
        p.setColor(0xFF7A2A34);
        c.drawRoundRect(new RectF(left, top, left + pw, top + ph), 18, 18, p);
        p.setStyle(Paint.Style.FILL);

        p.setColor(0xFFFF6B6B);
        p.setTextSize(h * 0.030f);
        c.drawText("外挂 / 辅助 CHEATS", left + pw * 0.06f, top + ph * 0.072f, p);
        p.setColor(0x88AFC4DA);
        p.setTextSize(h * 0.016f);
        c.drawText("仅作用于本机单机对局", left + pw * 0.06f, top + ph * 0.100f, p);

        float rowTop = top + ph * 0.135f;
        float rowH = ph * 0.102f;
        float rx0 = left + pw * 0.05f;
        float rx1 = left + pw * 0.95f;

        for (int i = 0; i < N_CHEAT; i++) {
            cheatRects[i].set(rx0, rowTop + i * rowH, rx1, rowTop + (i + 1) * rowH - h * 0.008f);
            String label;
            boolean on = false;
            switch (i) {
                case CH_ESP: label = "ESP 透视（穿墙显示敌人）"; on = s.cheatEsp; break;
                case CH_TRACK: label = "子弹追踪（自动命中最近敌人）"; on = s.cheatTrack; break;
                case CH_LOCK: label = "枪锁（自动瞄准 + 自动开火）"; on = s.cheatLock; break;
                case CH_JUMP: label = "自动连跳"; on = s.cheatAutoJump; break;
                case CH_RECOIL: label = "无后坐力"; on = s.cheatNoRecoil; break;
                case CH_AMMO: label = "无限弹药"; on = s.cheatInfAmmo; break;
                default: label = "关闭"; break;
            }

            p.setColor(on ? 0xFF6E2028 : 0xFF232C3B);
            c.drawRoundRect(cheatRects[i], 10, 10, p);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(2f);
            p.setColor(on ? 0xFFB0414C : 0xFF34455C);
            c.drawRoundRect(cheatRects[i], 10, 10, p);
            p.setStyle(Paint.Style.FILL);

            p.setTextSize(h * 0.023f);
            p.setTextAlign(Paint.Align.LEFT);
            p.setColor(i == CH_CLOSE ? 0xFF9DB2C8 : 0xFFEAF2FF);
            c.drawText(label, cheatRects[i].left + pw * 0.045f,
                    cheatRects[i].top + cheatRects[i].height() * 0.66f, p);

            if (i != CH_CLOSE) {
                p.setTextAlign(Paint.Align.RIGHT);
                p.setColor(on ? 0xFFFF8080 : 0xFF6F8299);
                c.drawText(on ? "开" : "关", cheatRects[i].right - pw * 0.045f,
                        cheatRects[i].top + cheatRects[i].height() * 0.66f, p);
                p.setTextAlign(Paint.Align.LEFT);
            }
        }
    }
}
