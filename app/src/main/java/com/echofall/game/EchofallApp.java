package com.echofall.game;

import android.app.Application;

public class EchofallApp extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        // 尽早注册，保证任何线程的未捕获异常都能落盘，方便定位真机闪退
        Thread.setDefaultUncaughtExceptionHandler(
                new CrashHandler(this, Thread.getDefaultUncaughtExceptionHandler()));
    }
}
