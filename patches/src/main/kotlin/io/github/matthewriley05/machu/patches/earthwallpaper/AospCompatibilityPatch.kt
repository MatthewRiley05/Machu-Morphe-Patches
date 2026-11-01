package io.github.matthewriley05.machu.patches.earthwallpaper

import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.extensions.InstructionExtensions.replaceInstruction
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.util.returnEarly
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.FieldReference
import io.github.matthewriley05.machu.patches.earthwallpaper.Constants.COMPATIBILITY_EARTH_SUPER_WALLPAPER

/**
 * Removes the MIUI framework dependencies that the official Filament builds
 * still have. Without this the wallpaper service crashes while loading
 * `MiuiWallpaperManager` on any non-MIUI ROM, which leaves the preview blank
 * and makes selecting the wallpaper silently fail.
 *
 * The legacy Unity port already contains these changes, so the patch is a
 * no-op on that build.
 */
@Suppress("unused")
val earthSuperWallpaperAospCompatibilityPatch = bytecodePatch(
    name = "Make Earth Super Wallpaper work on AOSP",
    description = "Replaces MIUI-only framework calls so the official Earth Super Wallpaper " +
        "can be previewed, selected, and rendered on non-MIUI Android ROMs.",
    default = true,
) {
    compatibleWith(COMPATIBILITY_EARTH_SUPER_WALLPAPER)

    execute {
        if (classDefByOrNull(BASE_EARTH_SUPER_WALLPAPER) != null) {
            // miui.os.UserHandle -> android.os.UserHandle
            val userHandleMatch = MiuiUserHandleFingerprint.matchOrNull()
                ?: throw PatchException("Failed to match $MiuiUserHandleFingerprint")

            userHandleMatch.method.replaceInstruction(
                userHandleMatch.instructionMatches.first().index,
                "invoke-static {}, Landroid/os/UserHandle;->myUserId()I",
            )

            // miui.os.Build reads -> false
            listOf(
                IsInternationalBuildFingerprint,
                IsTabletFingerprint,
                MiuiWallpaperManagerIsTabletFingerprint,
            ).forEach { fingerprint ->
                val match = fingerprint.matchOrNull()
                    ?: throw PatchException("Failed to match $fingerprint")

                val index = match.instructionMatches.first().index
                val register =
                    match.instructionMatches.first()
                        .getInstruction<OneRegisterInstruction>().registerA

                match.method.replaceInstruction(index, "const v$register, 0x0")
            }

            // android.provider.MiuiSettings.System -> false
            val darkModeMatch = DarkModeMiuiSettingsFingerprint.matchOrNull()
                ?: throw PatchException("Failed to match $DarkModeMiuiSettingsFingerprint")

            val darkModeMethod = darkModeMatch.method
            val miuiSettingsIndex = darkModeMatch.instructionMatches.first().index
            val moveResult =
                darkModeMethod.getInstruction<OneRegisterInstruction>(miuiSettingsIndex + 1)

            darkModeMethod.replaceInstruction(
                miuiSettingsIndex,
                "const/4 v${moveResult.registerA}, 0x0",
            )
            darkModeMethod.replaceInstruction(miuiSettingsIndex + 1, "nop")

            // Match the port's behaviour for MIUI settings that have no AOSP
            // equivalent.
            listOf(
                IsThemeLockLiveWallpaperFingerprint to true,
                IsAodOpenedFingerprint to true,
                IsAodUsingSuperWallpaperStyleFingerprint to true,
            ).forEach { (fingerprint, value) ->
                val match = fingerprint.matchOrNull()
                    ?: throw PatchException("Failed to match $fingerprint")

                match.method.returnEarly(value)
            }

            // Remove the remaining `miui.os.Build.DEVICE` loads from
            // supportVideo24Wallpaper(). The method is unreachable after the
            // isThemeLockLiveWallpaper() stub, but removing the references
            // keeps the dex free of MIUI framework types.
            val video24Match = SupportVideo24WallpaperFingerprint.matchOrNull()
                ?: throw PatchException("Failed to match $SupportVideo24WallpaperFingerprint")

            val video24Method = video24Match.method
            val video24Instructions = video24Method.implementation!!.instructions

            for (index in video24Instructions.indices.reversed()) {
                val instruction = video24Instructions[index]
                val field = (instruction as? ReferenceInstruction)?.reference as? FieldReference

                if (field != null && field.definingClass == MIUI_BUILD && field.name == "DEVICE") {
                    val register = (instruction as OneRegisterInstruction).registerA
                    video24Method.replaceInstruction(index, "const-string v$register, \"\"")
                }
            }
        }
    }
}
