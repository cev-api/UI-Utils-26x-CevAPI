package com.ui_utils.uiutils;

import com.ui_utils.uiutils.ui.UiButton;
import net.minecraft.client.input.InputWithModifiers;
import net.minecraft.network.chat.Component;

/**
 * Button with a press callback that receives the button itself, so call sites can
 * relabel it. It renders through {@link UiButton}, which owns the themed look.
 */
public final class UiUtilsColoredButton extends UiButton {
	@FunctionalInterface
	public interface PressAction {
		void onPress(UiUtilsColoredButton button);
	}

	private final PressAction press;

	public UiUtilsColoredButton(int x, int y, int width, int height,
		Component message, PressAction onPress) {
		super(x, y, width, height, message, null);
		this.press = onPress;
	}

	public static UiUtilsColoredButton of(int x, int y, int width, int height,
		String label, PressAction onPress) {
		return new UiUtilsColoredButton(x, y, width, height,
			Component.literal(label), onPress);
	}

	@Override
	public void onPress(InputWithModifiers input) {
		press.onPress(this);
	}

	@Override
	public UiUtilsColoredButton tint(int rgb) {
		super.tint(rgb);
		return this;
	}

	@Override
	public UiUtilsColoredButton uiScale(float value) {
		super.uiScale(value);
		return this;
	}
}
