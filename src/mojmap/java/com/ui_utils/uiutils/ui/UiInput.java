package com.ui_utils.uiutils.ui;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;

/**
 * Text field with the modern frame. It keeps every vanilla editing behaviour and
 * only replaces the background, so scaled canvases render its text like any
 * other label.
 */
public class UiInput extends EditBox implements UiScalable {
	/** Horizontal and vertical inset vanilla uses for a bordered field. */
	private static final int INNER_X = 4;

	private boolean focusedFrame;
	private float uiScale = 1F;
	private int accent = UiTheme.ACCENT;

	public UiInput(Font font, int width, String value, Component hint) {
		super(font, 0, 0, width, UiTheme.ROW_HEIGHT, hint);
		setBordered(false);
		setTextColor(UiTheme.TEXT);
		setTextColorUneditable(UiTheme.TEXT_MUTED);
		setTextShadow(false);
		setValue(value == null ? "" : value);
	}

	public UiInput accent(int argb) {
		this.accent = argb;
		return this;
	}

	public UiInput uiScale(float value) {
		this.uiScale = value <= 0F ? 1F : value;
		return this;
	}

	@Override
	public void applyUiScale(float value) {
		uiScale(value);
	}

	/** Recompute the visible text window after the layout assigns its real width. */
	public void refreshVisibleText() {
		int cursor = getCursorPosition();
		setCursorPosition(cursor);
	}

	@Override
	public void setFocused(boolean focused) {
		super.setFocused(focused);
		focusedFrame = focused;
	}

	@Override
	public void extractWidgetRenderState(GuiGraphicsExtractor graphics,
		int mouseX, int mouseY, float partialTicks) {
		int savedX = getX();
		int savedY = getY();
		int savedWidth = getWidth();
		int savedHeight = getHeight();
		boolean scaled = UiTheme.pushWidgetScale(graphics, savedX, savedY, uiScale);
		int x = scaled ? 0 : savedX;
		int y = scaled ? 0 : savedY;
		int w = UiTheme.localSize(savedWidth, uiScale, scaled);
		int h = UiTheme.localSize(savedHeight, uiScale, scaled);
		boolean hot = focusedFrame || isHoveredOrFocused();

		graphics.fill(x, y, x + w, y + h, UiTheme.SURFACE_SUNK);
		UiTheme.border(graphics, x, y, w, h,
			hot ? UiTheme.mix(UiTheme.BORDER, accent, 0.6F) : UiTheme.BORDER);

		// An unbordered EditBox draws its text at the widget origin, which would put
		// it on top of the frame, so the field is temporarily inset while vanilla
		// draws the text, the hint and the caret.
		int insetY = Math.max(0, (h - 8) / 2);
		setX(x + INNER_X);
		setY(y + insetY);
		setWidth(Math.max(4, w - INNER_X * 2));
		setHeight(Math.max(8, h - insetY));
		try {
			super.extractWidgetRenderState(graphics, mouseX, mouseY, partialTicks);
		} finally {
			setX(savedX);
			setY(savedY);
			setWidth(savedWidth);
			setHeight(savedHeight);
			UiTheme.popWidgetScale(graphics, scaled);
		}
	}
}
