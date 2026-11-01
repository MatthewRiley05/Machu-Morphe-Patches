package io.github.matthewriley05.machu.patches.earthwallpaper

import app.morphe.patcher.extensions.InstructionExtensions.addInstruction
import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.extensions.InstructionExtensions.removeInstruction
import app.morphe.patcher.extensions.InstructionExtensions.replaceInstruction
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.util.returnEarly
import app.morphe.patcher.util.smali.ExternalLabel
import com.android.tools.smali.dexlib2.builder.BuilderOffsetInstruction
import com.android.tools.smali.dexlib2.iface.instruction.FiveRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.FieldReference
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import io.github.matthewriley05.machu.patches.earthwallpaper.Constants.COMPATIBILITY_EARTH_SUPER_WALLPAPER

private const val EARTH_POSITION_CLASS =
    "Lio/github/matthewriley05/machu/extension/earth/EarthPosition;"

@Suppress("unused")
val earthSuperWallpaperPatch = bytecodePatch(
    name = "Earth Super Wallpaper (AOSP)",
    description = "Makes the official Earth Super Wallpaper work on non-MIUI Android: " +
        "removes MIUI framework crashes, fixes ambient-mode and weather settings, adds the " +
        "five desk positions, and restores the unlock animation.",
    default = true,
) {
    compatibleWith(COMPATIBILITY_EARTH_SUPER_WALLPAPER)

    // Applied with this patch but not listed separately.
    dependsOn(earthSuperWallpaperResourcePatch)

    // Adds EarthSuperWallpaper1..4 and the position helper.
    extendWith("extensions/extension.mpe")

    execute {
        if (classDefByOrNull(BASE_EARTH_SUPER_WALLPAPER) == null) {
            throw PatchException(
                "This patch requires the official Filament build (ALPHA-2.6.575 or newer).",
            )
        }

        // ------------------------------------------------------------------
        // AOSP compatibility: remove MIUI framework dependencies.
        // ------------------------------------------------------------------

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

        // Use AOSP-safe defaults for MIUI settings that have no equivalent.
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
        // supportVideo24Wallpaper().
        val video24Match = SupportVideo24WallpaperFingerprint.matchOrNull()
            ?: throw PatchException("Failed to match $SupportVideo24WallpaperFingerprint")

        val video24Method = video24Match.method
        val video24Instructions = video24Method.implementation!!.instructions

        var replacedDeviceReads = 0
        for (index in video24Instructions.indices.reversed()) {
            val instruction = video24Instructions[index]
            val field = (instruction as? ReferenceInstruction)?.reference as? FieldReference

            if (field != null && field.definingClass == MIUI_BUILD && field.name == "DEVICE") {
                val register = (instruction as OneRegisterInstruction).registerA
                video24Method.replaceInstruction(index, "const-string v$register, \"\"")
                replacedDeviceReads++
            }
        }

        if (replacedDeviceReads == 0) {
            throw PatchException(
                "supportVideo24Wallpaper() no longer reads miui.os.Build.DEVICE; " +
                    "update the AOSP compatibility patch.",
            )
        }

        // ------------------------------------------------------------------
        // Weather/day-night settings: remove unguarded Settings.Global writes.
        // ------------------------------------------------------------------

        val weatherMatch = WeatherSettingsWritesFingerprint.matchOrNull()
            ?: throw PatchException("Failed to match $WeatherSettingsWritesFingerprint")

        val weatherMethod = weatherMatch.method
        val weatherInstructions = weatherMethod.implementation!!.instructions

        var removedWrites = 0
        for (index in weatherInstructions.indices.reversed()) {
            val instruction = weatherInstructions[index]
            val reference = (instruction as? ReferenceInstruction)?.reference as? MethodReference

            if (reference != null &&
                reference.definingClass == SETTINGS_GLOBAL &&
                (reference.name == "putLong" || reference.name == "putInt")
            ) {
                weatherMethod.replaceInstruction(index, "nop")
                removedWrites++
            }
        }

        if (removedWrites == 0) {
            throw PatchException(
                "WeatherUtils.onPostExecute() no longer writes Settings.Global; " +
                    "update the weather-settings patch.",
            )
        }

        // ------------------------------------------------------------------
        // Desk positions: derive the position from the service class name.
        // ------------------------------------------------------------------

        val positionMatch = SuperWallpaperHomePositionFingerprint.matchOrNull()
            ?: throw PatchException("Failed to match $SuperWallpaperHomePositionFingerprint")

        val positionMethod = positionMatch.method
        val originalFirstInstruction = positionMethod.getInstruction(0)

        positionMethod.addInstructionsWithLabels(
            0,
            """
                invoke-static { p1 }, $EARTH_POSITION_CLASS->get(Ljava/lang/String;)I
                move-result v0
                if-ltz v0, :original_position_lookup
                return v0
            """,
            ExternalLabel("original_position_lookup", originalFirstInstruction),
        )

        // ------------------------------------------------------------------
        // Unlock animation + pause handling.
        // ------------------------------------------------------------------

        // Do not pause Filament while the wallpaper is visible. The screen-off
        // and hidden-wallpaper pause paths are untouched.
        listOf(
            FilamentDeskPauseDelayFingerprint,
            FilamentAod2LockPauseDelayFingerprint,
            FilamentWallPaperOffsetDelayFingerprint,
        ).forEach { fingerprint ->
            val match = fingerprint.matchOrNull()
                ?: throw PatchException("Failed to match $fingerprint")

            val instructionMatch = match.instructionMatches.first()
            val register = instructionMatch.getInstruction<OneRegisterInstruction>().registerA

            match.method.replaceInstruction(
                instructionMatch.index,
                "const v$register, 0x7fffffff",
            )
        }

        // Always continue the USER_PRESENT branch, even when the cached state
        // says "Desk" or the engine still thinks it is a preview.
        val userPresentMatch = FilamentUserPresentBranchFingerprint.matchOrNull()
            ?: throw PatchException("Failed to match $FilamentUserPresentBranchFingerprint")

        val userPresentMethod = userPresentMatch.method
        listOf(2, 4, 5).forEach { filterIndex ->
            userPresentMethod.replaceInstruction(
                userPresentMatch.instructionMatches[filterIndex].index,
                "nop",
            )
        }

        // Do not discard the unlock while the display state is changing.
        val displayStateMatch = FilamentDisplayStateCheckFingerprint.matchOrNull()
            ?: throw PatchException("Failed to match $FilamentDisplayStateCheckFingerprint")
        val branchIndex = displayStateMatch.instructionMatches[3].index
        val displayStateMethod = displayStateMatch.method
        val branch = displayStateMethod.getInstruction<BuilderOffsetInstruction>(branchIndex)
        val target = branch.target.location.instruction!!

        displayStateMethod.addInstructionsWithLabels(
            branchIndex,
            "goto :run_desk_action",
            ExternalLabel("run_desk_action", target),
        )
        displayStateMethod.removeInstruction(branchIndex + 1)

        // Post the "Desk" Filament message immediately instead of 100 ms later.
        val deskMessageMatch = FilamentDeskMessagePostFingerprint.matchOrNull()
            ?: throw PatchException("Failed to match $FilamentDeskMessagePostFingerprint")

        val deskMessageCall = deskMessageMatch.instructionMatches.last()
        // invoke-virtual lists the receiver first: C = Handler, D = Runnable,
        // E and F = the long delay.
        val delayRegister =
            deskMessageCall.getInstruction<FiveRegisterInstruction>().registerE

        deskMessageMatch.method.addInstruction(
            deskMessageCall.index,
            "const-wide/16 v$delayRegister, 0x0",
        )
    }
}
