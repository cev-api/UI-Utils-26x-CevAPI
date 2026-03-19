package com.ui_utils.uiutils;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.InputWithModifiers;
import net.minecraft.network.chat.Component;

public final class UiUtilsColoredButton extends AbstractButton {
	@FunctionalInterface
	public interface PressAction {
		void onPress(UiUtilsColoredButton button);
	}

	private final PressAction onPress;

	public UiUtilsColoredButton(int x, int y, int width, int height,
		Component message, PressAction onPress) {
		super(x, y, width, height, message);
		this.onPress = onPress;
	}

	public static UiUtilsColoredButton of(int x, int y, int width, int height,
		String label, PressAction onPress) {
		return new UiUtilsColoredButton(x, y, width, height,
			Component.literal(label), onPress);
	}

	@Override
	public void onPress(InputWithModifiers input) {
		onPress.onPress(this);
	}

	@Override
	protected void extractContents(GuiGraphicsExtractor graphics, int mouseX,
		int mouseY, float partialTicks) {
		int x = getX();
		int y = getY();
		int w = getWidth();
		int h = getHeight();

		int baseRgb = UiUtilsSettings.get().uiButtonColor & 0xFFFFFF;
		float mult = !active ? 0.45F : (isHoveredOrFocused() ? 1.15F : 1.0F);
		int fill = 0xFF000000 | scaleRgb(baseRgb, mult);
		int border = 0xFF000000 | scaleRgb(baseRgb, active ? 0.60F : 0.35F);

		graphics.fill(x, y, x + w, y + h, fill);
		graphics.outline(x, y, w, h, border);

		int textColor = 0xFF000000
			| (UiUtilsSettings.get().uiButtonTextColor & 0xFFFFFF);
		int textY = y + (h - Minecraft.getInstance().font.lineHeight) / 2;
		graphics.centeredText(Minecraft.getInstance().font, getMessage(),
			x + w / 2, textY, textColor);
	}

	@Override
	protected void updateWidgetNarration(NarrationElementOutput narration) {
		defaultButtonNarrationText(narration);
	}

	private static int scaleRgb(int rgb, float factor) {
		int r = (rgb >> 16) & 0xFF;
		int g = (rgb >> 8) & 0xFF;
		int b = rgb & 0xFF;
		r = clamp((int)(r * factor));
		g = clamp((int)(g * factor));
		b = clamp((int)(b * factor));
		return (r << 16) | (g << 8) | b;
	}

	private static int clamp(int value) {
		return Math.max(0, Math.min(255, value));
	}
}
