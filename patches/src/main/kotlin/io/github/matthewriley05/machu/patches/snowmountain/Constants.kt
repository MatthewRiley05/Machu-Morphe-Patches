package io.github.matthewriley05.machu.patches.snowmountain

import app.morphe.patcher.patch.ApkFileType
import app.morphe.patcher.patch.AppTarget
import app.morphe.patcher.patch.Compatibility

object Constants {
    val COMPATIBILITY_SNOWMOUNTAIN_SUPER_WALLPAPER = Compatibility(
        name = "Snowmountain Super Wallpaper",
        packageName = "com.miui.miwallpaper.snowmountain",
        apkFileType = ApkFileType.APK,
        appIconColor = 0x000000,
        targets = listOf(
            // Unity build, versionCode 206000561.
            AppTarget(version = "ALPHA-2.6.561-11211547-ogl-64-fix1"),
            // Other builds of the same engine may work but are not verified.
            AppTarget(version = null, isExperimental = true),
        ),
    )
}
