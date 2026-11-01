package io.github.matthewriley05.machu.patches.earthwallpaper

import app.morphe.patcher.extensions.InstructionExtensions.replaceInstruction
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import io.github.matthewriley05.machu.patches.earthwallpaper.Constants.COMPATIBILITY_EARTH_SUPER_WALLPAPER

/**
 * `WeatherUtils$1.onPostExecute()` writes the sunrise/sunset values to
 * `Settings.Global` without a permission check. `WRITE_SECURE_SETTINGS` is a
 * signature/privileged permission, so a sideloaded APK on AOSP throws
 * `SecurityException` during a weather or day/night update and takes the
 * wallpaper process down with it.
 *
 * The writes are not read back by the renderer in the same session; the fetched
 * values are passed directly to the consumer. Removing the writes is enough to
 * keep the wallpaper alive.
 */
@Suppress("unused")
val earthSuperWallpaperWeatherSettingsPatch = bytecodePatch(
    name = "Fix weather settings on AOSP",
    description = "Prevents the unguarded Settings.Global writes from crashing the wallpaper " +
        "process during weather or day/night updates on non-MIUI ROMs.",
    default = true,
) {
    compatibleWith(COMPATIBILITY_EARTH_SUPER_WALLPAPER)

    execute {
        if (classDefByOrNull(BASE_EARTH_SUPER_WALLPAPER) == null) {
            throw PatchException(
                "This patch requires the official Filament build (ALPHA-2.6.575 or newer).",
            )
        }

        val match = WeatherSettingsWritesFingerprint.matchOrNull()
            ?: throw PatchException("Failed to match $WeatherSettingsWritesFingerprint")

        val method = match.method
        val instructions = method.implementation!!.instructions

        for (index in instructions.indices.reversed()) {
            val instruction = instructions[index]
            val reference = (instruction as? ReferenceInstruction)?.reference as? MethodReference

            if (reference != null &&
                reference.definingClass == SETTINGS_GLOBAL &&
                (reference.name == "putLong" || reference.name == "putInt")
            ) {
                method.replaceInstruction(index, "nop")
            }
        }
    }
}
