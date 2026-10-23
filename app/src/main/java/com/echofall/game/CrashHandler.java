package com.echofall.game;

import android.content.Context;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;

public class CrashHandler implements Thread.UncaughtExceptionHandler {
    private final Context ctx;
    private final Thread.UncaughtExceptionHandler next;

    public CrashHandler(Context ctx, Thread.UncaughtExceptionHandler next) {
        this.ctx = ctx;
        this.next = next;
    }

    @Override
    public void uncaughtException(Thread t, Throwable e) {
        try {
            StringWriter sw = new StringWriter();
            PrintWriter pw = new PrintWriter(sw);
            pw.println("Thread: " + t.getName());
            pw.println("Device: " + android.os.Build.MANUFACTURER + " " + android.os.Build.MODEL
                    + " / Android " + android.os.Build.VERSION.RELEASE
                    + " (API " + android.os.Build.VERSION.SDK_INT + ")");
            e.printStackTrace(pw);
            pw.flush();
            String text = sw.toString();
            Log.e("Echofall", text);
            File file = new File(ctx.getFilesDir(), "crash.log");
            FileOutputStream fos = new FileOutputStream(file);
            fos.write(text.getBytes("UTF-8"));
            fos.close();
        } catch (Throwable ignored) {
            // 崩溃处理本身不能再抛异常
        }
        if (next != null) next.uncaughtException(t, e);
    }
}
