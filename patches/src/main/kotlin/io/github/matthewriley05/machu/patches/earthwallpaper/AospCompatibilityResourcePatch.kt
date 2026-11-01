package io.github.matthewriley05.machu.patches.earthwallpaper

import app.morphe.patcher.patch.resourcePatch
import io.github.matthewriley05.machu.patches.earthwallpaper.Constants.COMPATIBILITY_EARTH_SUPER_WALLPAPER

/**
 * The official wallpaper XML declares `android:supportsAmbientMode="true"`.
 * AOSP's `WallpaperManagerService` then requires the selected wallpaper
 * service to hold `android.permission.AMBIENT_WALLPAPER`, which is a
 * signature/privileged permission a sideloaded APK can never get. The result
 * is a `SecurityException` in the system wallpaper picker.
 *
 * The legacy port avoided this by resolving `supportsAmbientMode` to `false`
 * on Android 10+. Match that behaviour.
 */
@Suppress("unused")
val earthSuperWallpaperAospAmbientResourcePatch = resourcePatch(
    name = "Allow Earth Super Wallpaper on AOSP",
    description = "Disables the ambient-mode declaration that requires the " +
        "signature-only AMBIENT_WALLPAPER permission on non-MIUI ROMs.",
    default = true,
) {
    compatibleWith(COMPATIBILITY_EARTH_SUPER_WALLPAPER)

    execute {
        document("res/xml/super_wallpaper.xml").use { doc ->
            doc.documentElement.setAttributeNS(
                "http://schemas.android.com/apk/res/android",
                "android:supportsAmbientMode",
                "false",
            )
        }
    }
}
