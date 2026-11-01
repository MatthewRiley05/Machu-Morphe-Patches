package com.miui.miwallpaper.earth.superwallpaper;

import android.os.Handler;

/**
 * Delays the AOD zoom-out on screen-off so the device's closing animation can
 * finish before the wallpaper transitions.
 */
@SuppressWarnings("unused")
public final class AodDelay implements Runnable {

    private static final long DELAY_MS = 500L;

    private final BaseEarthSuperWallpaper.SuperWallpaperEngine engine;
    private final int position;

    private AodDelay(BaseEarthSuperWallpaper.SuperWallpaperEngine engine, int position) {
        this.engine = engine;
        this.position = position;
    }

    public static void post(
            Handler handler,
            BaseEarthSuperWallpaper.SuperWallpaperEngine engine,
            int position
    ) {
        handler.postDelayed(new AodDelay(engine, position), DELAY_MS);
    }

    @Override
    public void run() {
        engine.goToAodState(position);
    }
}
