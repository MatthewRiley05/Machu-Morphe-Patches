package io.github.matthewriley05.machu.patches.snowmountain

import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.extensions.InstructionExtensions.removeInstruction
import app.morphe.patcher.extensions.InstructionExtensions.replaceInstruction
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.util.smali.ExternalLabel
import com.android.tools.smali.dexlib2.builder.BuilderOffsetInstruction
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import io.github.matthewriley05.machu.patches.snowmountain.Constants.COMPATIBILITY_SNOWMOUNTAIN_SUPER_WALLPAPER

@Suppress("unused")
val snowmountainUnlockPatch = bytecodePatch(
    name = "Snowmountain unlock fix",
    description = "Restores the lock to home transition of the Snowmountain Super Wallpaper " +
        "on non-MIUI ROMs and stops the scene from freezing while the wallpaper is visible.",
    default = true,
) {
    compatibleWith(COMPATIBILITY_SNOWMOUNTAIN_SUPER_WALLPAPER)

    execute {
        if (classDefByOrNull(SNOW_BASE_SUPER_WALLPAPER) == null) {
            throw PatchException(
                "This is not the Snowmountain Super Wallpaper (com.miui.miwallpaper.snowmountain).",
            )
        }

        // Do not pause the Unity scene while the wallpaper is visible. The
        // screen-off and hidden-wallpaper pause paths are untouched.
        listOf(
            SnowDeskPauseDelayFingerprint,
            SnowBaseAod2LockPauseDelayFingerprint,
            SnowAod2LockPauseDelayFingerprint,
            SnowOffsetDelayFingerprint,
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

        // Do not discard the "Desk" unlock message while the display state is
        // still changing: always take the branch that sends it.
        val displayStateMatch = SnowDisplayStateCheckFingerprint.matchOrNull()
            ?: throw PatchException("Failed to match $SnowDisplayStateCheckFingerprint")
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
    }
}
