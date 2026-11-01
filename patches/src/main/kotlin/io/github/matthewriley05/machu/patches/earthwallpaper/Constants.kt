package io.github.matthewriley05.machu.patches.earthwallpaper

import app.morphe.patcher.patch.ApkFileType
import app.morphe.patcher.patch.AppTarget
import app.morphe.patcher.patch.Compatibility

object Constants {
    val COMPATIBILITY_EARTH_SUPER_WALLPAPER = Compatibility(
        name = "Earth Super Wallpaper",
        packageName = "com.miui.miwallpaper.earth",
        apkFileType = ApkFileType.APK,
        appIconColor = 0x000000,
        targets = listOf(
            // Official Filament release (versionCode 206000575). This is the
            // preferred base: it is smaller, current, and does not require a
            // third-party Unity port.
            AppTarget(version = "ALPHA-2.6.575-09041449-ogl-64"),
            // Legacy Unity port used by the original patch (versionCode
            // 206000561). Kept so existing setups keep working.
            AppTarget(version = "ALPHA-2.6.561-11211547-ogl-64-fix1"),
            // Other ports/versions may work, but the smali context must match.
            AppTarget(version = null, isExperimental = true),
        ),
    )
}
