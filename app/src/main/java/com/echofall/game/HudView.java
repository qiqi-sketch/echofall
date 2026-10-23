package com.echofall.game;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.view.MotionEvent;
import android.view.View;

public class HudView extends View {
    private final GameState s;
    private final SharedPreferences prefs;
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);

    private static final int FIRE = 0, JUMP = 1, RELOAD = 2, SWITCH = 3, BUY = 4, GEAR = 5;
    private static final String[] KEY_LABELS = {"开火", "跳", "换弹", "切枪", "商店", "设置"};
    private static final float[] DEF_X = {0.86f, 0.72f, 0.93f, 0.80f, 0.10f, 0.94f};
    private static final float[] DEF_Y = {0.74f, 0.86f, 0.50f, 0.56f, 0.80f, 0.10f};
    private static final String[] SHOP_IDS = {"rifle", "pistol", "armor", "ammo", "sell"};

    private final float[] bx = new float[6];
    private final float[] by = new float[6];

    private boolean shopOpen;
    private boolean editMode;
    private int dragKey = -1;
    private float dragDX, dragDY;

    private int joyId = -1;
    private int lookId = -1;
    private int fireId = -1;
    private float joyBaseX, joyBaseY, joyKnobX, joyKnobY;
    private float lastLookX, lastLookY;

    private final RectF[] shopRects = new RectF[5];
    private final RectF shopClose = new RectF();

    public HudView(Context c, GameState state) {
        super(c);
        s = state;
        prefs = c.getSharedPreferences("echofall_binds", Context.MODE_PRIVATE);
        for (int i = 0; i < 5; i++) shopRects[i] = new RectF();
        loadBinds();
        setFocusable(true);
    }

    private void loadBinds() {
        for (int i = 0; i < 6; i++) {
            bx[i] = prefs.getFloat("x" + i, DEF_X[i]);
            by[i] = prefs.getFloat("y" + i, DEF_Y[i]);
        }
    }

    private void saveBinds() {
        SharedPreferences.Editor e = prefs.edit();
        for (int i = 0; i < 6; i++) {
            e.putFloat("x" + i, bx[i]);
            e.putFloat("y" + i, by[i]);
        }
        e.apply();
    }

    private void resetBinds() {
        for (int i = 0; i < 6; i++) {
            bx[i] = DEF_X[i];
            by[i] = DEF_Y[i];
        }
        saveBinds();
    }

    private float radius(int key) {
        return Math.min(getWidth(), getHeight()) * (key == FIRE ? 0.075f : 0.055f);
    }

    private boolean hitButton(int key, float x, float y) {
        float cx = bx[key] * getWidth();
        float cy = by[key] * getHeight();
        return Math.hypot(x - cx, y - cy) <= radius(key);
    }

    private int hitAnyButton(float x, float y) {
        for (int i = 0; i < 6; i++) {
            if (hitButton(i, x, y)) return i;
        }
        return -1;
    }

    public boolean handleBack() {
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
        if (shopOpen) {
            if (shopClose.contains(x, y)) {
                shopOpen = false;
                return;
            }
            for (int i = 0; i < 5; i++) {
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
            editMode = true;
            return;
        }
        if (hitButton(BUY, x, y)) {
            shopOpen = true;
            return;
        }
        if (hitButton(FIRE, x, y)) {
            fireId = id;
            s.input.firing = true;
            s.input.firePressed = true;
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

    private void move(int id, float x, float y) {
        if (dragKey >= 0) {
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

        drawCrosshair(c, w, h);
        drawStatus(c, w, h);
        drawJoystick(c, w, h);
        drawButtons(c, w, h);

        if (s.damageFlash > 0) {
            p.setColor(0x00000000);
            p.setColor(((int) (Math.min(1f, s.damageFlash) * 110) << 24) | 0xCC1010);
            c.drawRect(0, 0, w, h, p);
        }

        if (shopOpen) drawShop(c, w, h);
        if (editMode) drawEditHint(c, w, h);

        postInvalidateOnAnimation();
    }

    private void drawCrosshair(Canvas c, float w, float h) {
        float cx = w / 2f;
        float cy = h / 2f;
        float gap = h * 0.012f;
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

        p.setTextSize(h * 0.024f);
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

        // 弹药
        WeaponType wt = s.player.current;
        String ammo;
        if (wt == WeaponType.KNIFE) {
            ammo = "刀";
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
            c.drawRoundRect(new RectF(tx - 18, h * 0.155f - h * 0.028f,
                    tx + tw + 18, h * 0.155f + h * 0.016f), 8, 8, p);
            p.setColor(0xFFFFD166);
            c.drawText(s.toast, tx, h * 0.155f, p);
        }

        if (s.player.hp <= 0) {
            p.setTextAlign(Paint.Align.CENTER);
            p.setTextSize(h * 0.055f);
            p.setColor(0xFFFF6A6A);
            c.drawText("阵亡 · " + String.format("%.1f", Math.max(0, s.player.respawnT)) + " 秒后重生",
                    w / 2f, h / 2f - h * 0.08f, p);
            p.setTextAlign(Paint.Align.LEFT);
        }
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
        for (int i = 0; i < 6; i++) {
            float cx = bx[i] * w;
            float cy = by[i] * h;
            float r = radius(i);
            p.setStyle(Paint.Style.FILL);
            if (i == FIRE) p.setColor(0x55E04A4A);
            else if (i == GEAR && editMode) p.setColor(0x66FFD166);
            else p.setColor(0x44303A48);
            c.drawCircle(cx, cy, r, p);

            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(editMode ? 3f : 2f);
            p.setColor(editMode ? 0xFFFFD166 : 0xAA8FA3C0);
            c.drawCircle(cx, cy, r, p);

            p.setStyle(Paint.Style.FILL);
            p.setColor(0xFFEAF2FF);
            p.setTextSize(r * 0.62f);
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
        c.drawText("键位编辑：拖动按钮调整位置，点右上角“设置”完成", w / 2f, h * 0.138f, p);
        p.setTextAlign(Paint.Align.LEFT);
    }

    private void drawShop(Canvas c, float w, float h) {
        p.setColor(0xCC0B0F16);
        c.drawRect(0, 0, w, h, p);

        float pw = w * 0.62f;
        float ph = h * 0.80f;
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
        p.setTextSize(h * 0.038f);
        c.drawText("商店 BUY MENU", left + pw * 0.06f, top + ph * 0.10f, p);

        p.setColor(0xFFEAF2FF);
        p.setTextSize(h * 0.028f);
        p.setTextAlign(Paint.Align.RIGHT);
        c.drawText("资金 $" + s.player.money, left + pw * 0.94f, top + ph * 0.10f, p);
        p.setTextAlign(Paint.Align.LEFT);

        float rowTop = top + ph * 0.15f;
        float rowH = ph * 0.115f;
        float rx0 = left + pw * 0.05f;
        float rx1 = left + pw * 0.95f;

        for (int i = 0; i < 5; i++) {
            shopRects[i].set(rx0, rowTop + i * rowH, rx1, rowTop + (i + 1) * rowH - h * 0.008f);
            boolean owned = false;
            String title;
            String sub;
            switch (i) {
                case 0:
                    owned = s.player.owned.contains(WeaponType.RIFLE);
                    title = "步枪  $2700";
                    sub = "伤害 28 · 弹匣 30 · 全自动";
                    break;
                case 1:
                    owned = s.player.owned.contains(WeaponType.PISTOL);
                    title = "手枪  $500";
                    sub = "伤害 34 · 弹匣 12";
                    break;
                case 2:
                    title = "护甲  $650";
                    sub = "当前护甲 " + s.player.armor;
                    break;
                case 3:
                    title = "弹药  $50";
                    sub = "补充两个弹匣";
                    break;
                default:
                    title = "卖出当前武器";
                    sub = s.player.current == WeaponType.KNIFE ? "刀不能出售"
                            : "+$" + (s.player.current.price / 2) + " · " + s.player.current.label;
                    break;
            }

            p.setColor(owned ? 0x332E4A36 : 0xFF232C3B);
            c.drawRoundRect(shopRects[i], 10, 10, p);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(2f);
            p.setColor(0xFF34455C);
            c.drawRoundRect(shopRects[i], 10, 10, p);
            p.setStyle(Paint.Style.FILL);

            p.setColor(owned ? 0xFF7BE0A0 : 0xFFEAF2FF);
            p.setTextSize(h * 0.029f);
            c.drawText(owned ? title + "  [已拥有]" : title, rx0 + pw * 0.03f,
                    shopRects[i].top + rowH * 0.42f, p);

            p.setColor(0xFF8FA3B8);
            p.setTextSize(h * 0.021f);
            c.drawText(sub, rx0 + pw * 0.03f, shopRects[i].top + rowH * 0.78f, p);
        }

        shopClose.set(rx0, rowTop + 5 * rowH + h * 0.01f, rx1, rowTop + 5 * rowH + h * 0.075f);
        p.setColor(0xFF2C3A4E);
        c.drawRoundRect(shopClose, 12, 12, p);
        p.setColor(0xFFEAF2FF);
        p.setTextSize(h * 0.028f);
        p.setTextAlign(Paint.Align.CENTER);
        c.drawText("关闭商店", (shopClose.left + shopClose.right) / 2f,
                shopClose.top + shopClose.height() * 0.65f, p);
        p.setTextAlign(Paint.Align.LEFT);
    }
}
