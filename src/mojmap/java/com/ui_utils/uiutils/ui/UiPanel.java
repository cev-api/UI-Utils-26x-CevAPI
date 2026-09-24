package com.ui_utils.uiutils.ui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;

/**
 * Flat panel chrome for widget stacks that are not hosted by a
 * {@link UiModernScreen}, such as the in-game UI-Utils button panel.
 */
public class UiPanel extends AbstractWidget implements UiScalable {
	private final boolean header;
	private boolean centered;
	private float uiScale = 1F;

	public UiPanel(int x, int y, int width, int height, boolean header) {
		super(x, y, width, height, Component.empty());
		this.header = header;
		this.active = false;
	}

	/** Centres the title inside the header bar. */
	public UiPanel centered(boolean value) {
		this.centered = value;
		return this;
	}

	/**
	 * Design-unit scale for panels whose widget sizes are already in screen pixels.
	 * Without this the title would be drawn at full size inside a scaled panel.
	 */
	public UiPanel uiScale(float value) {
		this.uiScale = value <= 0F ? 1F : value;
		return this;
	}

	@Override
	public void applyUiScale(float value) {
		uiScale(value);
	}

	@Override
	protected void extractWidgetRenderState(GuiGraphicsExtractor graphics,
		int mouseX, int mouseY, float partialTicks) {
		boolean scaled = UiTheme.pushWidgetScale(graphics, getX(), getY(), uiScale);
		int x = scaled ? 0 : getX();
		int y = scaled ? 0 : getY();
		int w = UiTheme.localSize(getWidth(), uiScale, scaled);
		int h = UiTheme.localSize(getHeight(), uiScale, scaled);
		if (header) {
			graphics.fill(x, y, x + w, y + h, UiTheme.SURFACE_HEADER);
			// The header covers the body's outline, so restore the outer frame
			// here before drawing the accent separator along its bottom edge.
			UiTheme.border(graphics, x, y, w, h, UiTheme.BORDER);
			Font font = Minecraft.getInstance().font;
			int textY = UiTheme.textY(font, y, h);
			String title = UiTheme.ellipsize(font, getMessage().getString(), w - 10);
			if (centered)
				UiTheme.textCentered(graphics, font, title, x + w / 2, textY,
					UiTheme.TEXT);
			else
				UiTheme.text(graphics, font, title, x + 5, textY, UiTheme.TEXT);
			graphics.fill(x, y + h - 1, x + w, y + h,
				UiTheme.withAlpha(UiTheme.accent(), 0x99));
			UiTheme.popWidgetScale(graphics, scaled);
			return;
		}
		graphics.fill(x, y, x + w, y + h, UiTheme.WINDOW);
		UiTheme.border(graphics, x, y, w, h, UiTheme.BORDER);
		UiTheme.popWidgetScale(graphics, scaled);
	}

	@Override
	protected void updateWidgetNarration(NarrationElementOutput narration) {
	}
}
