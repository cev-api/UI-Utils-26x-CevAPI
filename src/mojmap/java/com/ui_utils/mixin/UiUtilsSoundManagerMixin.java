package com.ui_utils.mixin;

import com.ui_utils.uiutils.macro.UiUtilsMacroRuntimeState;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.SoundEngine;
import net.minecraft.client.sounds.SoundManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(SoundManager.class)
public abstract class UiUtilsSoundManagerMixin {
    @Inject(method = "play(Lnet/minecraft/client/resources/sounds/SoundInstance;)Lnet/minecraft/client/sounds/SoundEngine$PlayResult;", at = @At("RETURN"))
    private void uiutils$soundPlayed(SoundInstance sound, CallbackInfoReturnable<SoundEngine.PlayResult> cir) {
        if (cir.getReturnValue() == SoundEngine.PlayResult.STARTED || cir.getReturnValue() == SoundEngine.PlayResult.STARTED_SILENTLY)
            UiUtilsMacroRuntimeState.onSound(sound.getIdentifier().toString(), sound.getX(), sound.getY(), sound.getZ());
    }
}
