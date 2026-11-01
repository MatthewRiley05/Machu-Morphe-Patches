package io.github.matthewriley05.machu.patches.earthwallpaper

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.replaceInstruction
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import com.android.tools.smali.dexlib2.AccessFlags
import io.github.matthewriley05.machu.patches.earthwallpaper.Constants.COMPATIBILITY_EARTH_SUPER_WALLPAPER

/**
 * Delays the AOD zoom-out when the screen turns off. Some devices, including
 * Pixel phones, run a closing animation; without the delay the wallpaper starts
 * its zoom-out while the animation is still playing.
 */
@Suppress("unused")
val earthSuperWallpaperAodDelayPatch = bytecodePatch(
    name = "Delay screen-off zoom-out",
    description = "Delays the AOD zoom-out by 500 ms on screen-off so the " +
        "device's closing animation can finish first.",
    default = false,
) {
    compatibleWith(COMPATIBILITY_EARTH_SUPER_WALLPAPER)

    // The AodDelay helper is merged by the main patch.
    dependsOn(earthSuperWallpaperPatch)

    execute {
        if (classDefByOrNull(BASE_EARTH_SUPER_WALLPAPER) == null) {
            throw PatchException(
                "This patch requires the official Filament build (ALPHA-2.6.575 or newer).",
            )
        }

        // AodDelay is in the same package, so package-private access is enough.
        val aodStateMatch = AodStateMethodFingerprint.matchOrNull()
            ?: throw PatchException("Failed to match $AodStateMethodFingerprint")

        val aodStateMethod = aodStateMatch.method
        aodStateMethod.setAccessFlags(
            aodStateMethod.accessFlags and AccessFlags.PRIVATE.value.inv(),
        )

        val callMatch = AodStateCallFingerprint.matchOrNull()
            ?: throw PatchException("Failed to match $AodStateCallFingerprint")

        val index = callMatch.instructionMatches.first().index
        val method = callMatch.method

        method.replaceInstruction(
            index,
            "iget-object v1, p0, $BASE_EARTH_ENGINE->this\$0:$BASE_EARTH_SUPER_WALLPAPER",
        )
        method.addInstructions(
            index + 1,
            """
                invoke-static { v1 }, $BASE_EARTH_SUPER_WALLPAPER->access\$100($BASE_EARTH_SUPER_WALLPAPER)Landroid/os/Handler;
                move-result-object v1
                invoke-static { v1, p0, v0 }, Lcom/miui/miwallpaper/earth/superwallpaper/AodDelay;->post(Landroid/os/Handler;$BASE_EARTH_ENGINE;I)V
            """,
        )
    }
}
