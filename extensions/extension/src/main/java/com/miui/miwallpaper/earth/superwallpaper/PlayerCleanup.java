package com.miui.miwallpaper.earth.superwallpaper;

import com.miui.mrengine.EarthMrePlayer;

import java.lang.reflect.Field;

/**
 * Handles the transition between the preview engine and the applied wallpaper
 * engine.
 *
 * <p>The system previews a live wallpaper with the same service component it
 * later applies, so both engines share one {@link BaseEarthSuperWallpaper}
 * instance and therefore one {@code earthMrePlayer} field. Creating the second
 * player without releasing the first leaves Filament rendering into a
 * destroyed preview surface, which aborts the native engine loop.</p>
 */
@SuppressWarnings("unused")
public final class PlayerCleanup {

    private static final Field PLAYER_FIELD = findPlayerField();

    private PlayerCleanup() {
    }

    private static Field findPlayerField() {
        try {
            Field field = BaseEarthSuperWallpaper.class.getDeclaredField("earthMrePlayer");
            field.setAccessible(true);
            return field;
        } catch (Throwable ignored) {
            return null;
        }
    }

    /**
     * @return whether the service already owns a player, which means a preview
     *         engine created for the same wallpaper must not replace it.
     */
    public static boolean hasPlayer(BaseEarthSuperWallpaper service) {
        return getPlayer(service) != null;
    }

    /**
     * Releases the player owned by a previous engine before the applied
     * wallpaper engine creates its own. The field is left pointing at the old
     * player until the new one replaces it, so it is never null in between.
     */
    public static void quitIfPresent(BaseEarthSuperWallpaper service) {
        EarthMrePlayer player = getPlayer(service);
        if (player != null) {
            player.quit();
        }
    }

    private static EarthMrePlayer getPlayer(BaseEarthSuperWallpaper service) {
        Field field = PLAYER_FIELD;
        if (field == null || service == null) {
            return null;
        }
        try {
            Object player = field.get(service);
            return player instanceof EarthMrePlayer ? (EarthMrePlayer) player : null;
        } catch (Throwable ignored) {
            return null;
        }
    }
}
