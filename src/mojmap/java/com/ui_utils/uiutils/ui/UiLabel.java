package com.ui_utils.uiutils.ui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;

/** Static label used as a column next to a control inside a content row. */
public class UiLabel extends AbstractWidget implements UiScalable {
	private int color = UiTheme.TEXT;
	private boolean dim;
	private boolean centered;
	private float uiScale = 1F;

	public UiLabel(String text) {
		super(0, 0, 10, 10, Component.literal(text == null ? "" : text));
		this.active = false;
	}

	public UiLabel color(int argb) {
		this.color = argb;
		return this;
	}

	public UiLabel dim() {
		this.dim = true;
		return this;
	}

	public UiLabel text(String value) {
		setMessage(Component.literal(value == null ? "" : value));
		return this;
	}

	public UiLabel centered() {
		this.centered = true;
		return this;
	}

	public UiLabel size(int width, int height) {
		setWidth(width);
		setHeight(height);
		return this;
	}

	public UiLabel uiScale(float value) {
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
		Font font = Minecraft.getInstance().font;
		boolean scaled = UiTheme.pushWidgetScale(graphics, getX(), getY(), uiScale);
		int x = scaled ? 0 : getX();
		int y = scaled ? 0 : getY();
		int w = UiTheme.localSize(getWidth(), uiScale, scaled);
		int h = UiTheme.localSize(getHeight(), uiScale, scaled);
		String shown = UiTheme.ellipsize(font, getMessage().getString(), w - 2);
		int textY = UiTheme.textY(font, y, h);
		if (centered)
			UiTheme.textCentered(graphics, font, shown, x + w / 2, textY,
				dim ? UiTheme.TEXT_DIM : color);
		else
			UiTheme.text(graphics, font, shown, x, textY,
				dim ? UiTheme.TEXT_DIM : color);
		UiTheme.popWidgetScale(graphics, scaled);
	}

	@Override
	protected void updateWidgetNarration(NarrationElementOutput narration) {
	}
}
