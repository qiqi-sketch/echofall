package com.echofall.game;

import android.content.Context;
import android.opengl.GLSurfaceView;

public class GameView extends GLSurfaceView {
    public final GameState state;
    public final GameRenderer renderer;

    public GameView(Context context) {
        super(context);
        setEGLContextClientVersion(2);
        setEGLConfigChooser(new FallbackConfigChooser());
        state = new GameState();
        renderer = new GameRenderer(state);
        setRenderer(renderer);
        setRenderMode(RENDERMODE_CONTINUOUSLY);
        setPreserveEGLContextOnPause(true);
    }
}
