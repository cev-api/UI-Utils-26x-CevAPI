package com.ui_utils.mixin.ui_utils;

import com.ui_utils.uiutils.UiUtilsSettings;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AbstractContainerScreen.class)
public abstract class ChestSlotOverlayMixin {
	@Shadow
	protected Slot hoveredSlot;

	@Inject(method = "extractSlot(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/world/inventory/Slot;II)V", at = @At("TAIL"))
	private void uiutils$overlaySlot(GuiGraphicsExtractor graphics, Slot slot, int mouseX, int mouseY,
		CallbackInfo ci) {
		if (!UiUtilsSettings.get().slotOverlayEnabled)
			return;

		// Show either on hover only or always, based on settings.
		if (UiUtilsSettings.get().slotOverlayHoverOnly && slot != hoveredSlot)
			return;

		int alpha = UiUtilsSettings.get().slotOverlayAlpha & 0xFF;
		int color = (alpha << 24) | (UiUtilsSettings.get().slotOverlayColor & 0xFFFFFF);
		int x = slot.x + UiUtilsSettings.get().slotOverlayOffsetX;
		int y = slot.y + UiUtilsSettings.get().slotOverlayOffsetY;
		String text = Integer.toString(slot.index);
		graphics.text(Minecraft.getInstance().font, text, x + 1, y + 1, color, false);
	}
}
