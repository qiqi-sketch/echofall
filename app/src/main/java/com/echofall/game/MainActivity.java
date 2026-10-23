package com.echofall.game;

import android.app.Activity;
import android.os.Build;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.PrintWriter;
import java.io.StringWriter;

public class MainActivity extends Activity {
    private GameView gameView;
    private HudView hud;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        try {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

            FrameLayout root = new FrameLayout(this);
            gameView = new GameView(this);
            hud = new HudView(this, gameView.state);

            root.addView(gameView, new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT));
            root.addView(hud, new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT));

            setContentView(root);
            hideSystemUi();
        } catch (Throwable e) {
            showStartupError(e);
        }
    }

    /**
     * 启动阶段出现异常时不再让进程直接消失，而是把错误画在屏幕上，
     * 用户截图即可反馈，避免“打开就闪退”无从排查。
     */
    private void showStartupError(Throwable e) {
        try {
            StringWriter sw = new StringWriter();
            PrintWriter pw = new PrintWriter(sw);
            pw.println("设备: " + Build.MANUFACTURER + " " + Build.MODEL
                    + " / Android " + Build.VERSION.RELEASE + " (API " + Build.VERSION.SDK_INT + ")");
            pw.println("版本: " + BuildConfig.VERSION_NAME);
            e.printStackTrace(pw);
            pw.flush();

            TextView tv = new TextView(this);
            tv.setText("启动失败\n\n" + sw.toString());
            tv.setTextColor(0xFFFF9A9A);
            tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f);
            tv.setGravity(Gravity.START | Gravity.TOP);
            tv.setPadding(40, 60, 40, 40);

            ScrollView sv = new ScrollView(this);
            sv.setBackgroundColor(0xFF0B0F16);
            sv.addView(tv, new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT));
            setContentView(sv);
        } catch (Throwable ignored) {
            // 兜底失败就只能交给系统了
        }
    }

    private void hideSystemUi() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            WindowInsetsController controller = getWindow().getInsetsController();
            if (controller != null) {
                controller.hide(WindowInsets.Type.systemBars());
                controller.setSystemBarsBehavior(
                        WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
            }
        } else {
            getWindow().getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                            | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
        }
    }

    @Override
    public void onBackPressed() {
        if (hud != null && hud.handleBack()) return;
        super.onBackPressed();
    }

    @Override
    protected void onResume() {
        super.onResume();
        hideSystemUi();
        if (gameView != null) gameView.onResume();
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (gameView != null) gameView.onPause();
    }
}
