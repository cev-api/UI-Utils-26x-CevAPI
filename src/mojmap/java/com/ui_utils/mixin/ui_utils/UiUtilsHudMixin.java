package com.ui_utils.mixin.ui_utils;

import com.ui_utils.uiutils.PacketHud;
import com.ui_utils.uiutils.UiUtilsAutoduper;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "net.minecraft.client.gui.Hud")
public class UiUtilsHudMixin {
	@Inject(method = "extractRenderState(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/client/DeltaTracker;)V", at = @At("TAIL"))
	private void uiutils$renderHud(GuiGraphicsExtractor graphics, DeltaTracker tickCounter, CallbackInfo ci) {
		PacketHud.render(graphics);
		UiUtilsAutoduper.renderAbortOverlay(graphics);
	}
}
