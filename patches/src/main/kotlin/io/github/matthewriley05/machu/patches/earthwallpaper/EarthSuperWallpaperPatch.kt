package io.github.matthewriley05.machu.patches.earthwallpaper

import app.morphe.patcher.extensions.InstructionExtensions.addInstruction
import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.extensions.InstructionExtensions.removeInstruction
import app.morphe.patcher.extensions.InstructionExtensions.replaceInstruction
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.util.smali.ExternalLabel
import com.android.tools.smali.dexlib2.builder.BuilderOffsetInstruction
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import io.github.matthewriley05.machu.patches.earthwallpaper.Constants.COMPATIBILITY_EARTH_SUPER_WALLPAPER

@Suppress("unused")
val earthSuperWallpaperUnlockPatch = bytecodePatch(
    name = "Unlock Earth Super Wallpaper",
    description = "Restores the lock-to-home unlock animation and prevents the wallpaper " +
        "from pausing while it is visible on AOSP-based ROMs.",
    default = true,
) {
    compatibleWith(COMPATIBILITY_EARTH_SUPER_WALLPAPER)

    execute {
        if (classDefByOrNull(BASE_EARTH_SUPER_WALLPAPER) == null) {
            throw PatchException(
                "This patch requires the official Filament build (ALPHA-2.6.575 or newer).",
            )
        }

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

        // Do not discard the unlock while the display state is changing.
        val displayStateMatch = FilamentDisplayStateCheckFingerprint.matchOrNull()
            ?: throw PatchException("Failed to match $FilamentDisplayStateCheckFingerprint")

        // The last filter matches the Filament message call after the branch,
        // so the IF_EQ match (fourth filter) is the branch itself.
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

        deskMessageMatch.method.addInstruction(
            deskMessageMatch.instructionMatches.last().index,
            "const-wide/16 v3, 0x0",
        )
    }
}
