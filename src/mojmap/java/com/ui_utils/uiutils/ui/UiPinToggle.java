package com.ui_utils.uiutils.ui;

import java.util.function.BooleanSupplier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.InputWithModifiers;
import net.minecraft.network.chat.Component;

/** Minimal hamburger-style pin toggle shared by floating UI panels. */
public final class UiPinToggle extends AbstractButton implements UiScalable {
	private final BooleanSupplier pinned;
	private final Runnable toggle;
	private float uiScale = 1F;

	public UiPinToggle(int x, int y, int width, int height,
		BooleanSupplier pinned, Runnable toggle) {
		super(x, y, width, height, Component.literal("Pin panel"));
		this.pinned = pinned;
		this.toggle = toggle;
	}

	@Override
	public void onPress(InputWithModifiers input) {
		toggle.run();
	}

	public UiPinToggle uiScale(float value) {
		uiScale = value <= 0F ? 1F : value;
		return this;
	}

	@Override
	public void applyUiScale(float value) {
		uiScale(value);
	}

	@Override
	protected void extractContents(GuiGraphicsExtractor graphics, int mouseX,
		int mouseY, float partialTicks) {
		boolean scaled = UiTheme.pushWidgetScale(graphics, getX(), getY(), uiScale);
		int x = scaled ? 0 : getX();
		int y = scaled ? 0 : getY();
		int w = UiTheme.localSize(getWidth(), uiScale, scaled);
		int h = UiTheme.localSize(getHeight(), uiScale, scaled);
		int color = !active ? UiTheme.TEXT_MUTED : pinned.getAsBoolean()
			? UiTheme.DANGER : isHoveredOrFocused() ? UiTheme.TEXT : UiTheme.TEXT_DIM;
		int barWidth = Math.max(5, Math.min(w - 4, 10));
		int barHeight = 1;
		int left = x + (w - barWidth) / 2;
		int top = y + (h - 5) / 2;
		for(int i = 0; i < 3; i++)
			graphics.fill(left, top + i * 2, left + barWidth,
				top + i * (barHeight + 1) + barHeight, color);
		UiTheme.popWidgetScale(graphics, scaled);
	}

	@Override
	protected void updateWidgetNarration(NarrationElementOutput narration) {
		defaultButtonNarrationText(narration);
	}
}
