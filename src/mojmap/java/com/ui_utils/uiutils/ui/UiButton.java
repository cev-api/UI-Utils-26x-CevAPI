package com.ui_utils.uiutils.ui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.InputWithModifiers;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/**
 * Themed button. It never rescales its own label: the owning canvas keeps text at
 * one size for the whole screen and long labels are ellipsized instead.
 */
public class UiButton extends AbstractButton implements UiScalable {
	public enum Kind {
		PRIMARY,
		SECONDARY,
		DANGER,
		GHOST
	}

	private Runnable action;
	private Kind kind = Kind.PRIMARY;
	private int tint;
	private boolean selected;
	private boolean pressed;
	private float uiScale = 1F;
	private int pad = 5;

	public UiButton(int x, int y, int width, int height, Component message,
		Runnable action) {
		super(x, y, width, height, message);
		this.action = action;
	}

	public static UiButton of(String label, Runnable action) {
		return new UiButton(0, 0, 10, 10, Component.literal(label), action);
	}

	public static UiButton kind(Kind kind, String label, Runnable action) {
		return of(label, action).style(kind);
	}

	public UiButton style(Kind kind) {
		this.kind = kind;
		return this;
	}

	public Kind kind() {
		return kind;
	}

	/** Replaces the press action, for buttons that must update themselves. */
	public UiButton action(Runnable value) {
		this.action = value;
		return this;
	}

	/** Explicit fill colour, used for state-tinted buttons. */
	public UiButton tint(int rgb) {
		this.tint = rgb & 0xFFFFFF;
		return this;
	}

	public int tint() {
		return tint;
	}

	public UiButton selected(boolean value) {
		this.selected = value;
		return this;
	}

	public boolean selected() {
		return selected;
	}

	public UiButton padding(int value) {
		this.pad = value;
		return this;
	}

	/** Design-unit scale applied when the button is not inside a scaled panel. */
	public UiButton uiScale(float value) {
		this.uiScale = value <= 0F ? 1F : value;
		return this;
	}

	public float uiScale() {
		return uiScale;
	}

	@Override
	public void applyUiScale(float value) {
		uiScale(value);
	}

	@Override
	public void onPress(InputWithModifiers input) {
		if (action != null)
			action.run();
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (active && visible && isValidClickButton(event.buttonInfo())
			&& isMouseOver(event.x(), event.y()))
			pressed = true;
		return super.mouseClicked(event, doubleClick);
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent event) {
		pressed = false;
		return super.mouseReleased(event);
	}

	@Override
	protected void extractContents(GuiGraphicsExtractor graphics, int mouseX,
		int mouseY, float partialTicks) {
		Font font = Minecraft.getInstance().font;
		boolean scaled = UiTheme.pushWidgetScale(graphics, getX(), getY(), uiScale);
		int x = scaled ? 0 : getX();
		int y = scaled ? 0 : getY();
		int w = UiTheme.localSize(getWidth(), uiScale, scaled);
		int h = UiTheme.localSize(getHeight(), uiScale, scaled);

		int accent = UiTheme.accent();
		int fill = fillColor(accent);
		int text = textColor();
		if (!active) {
			fill = 0x30000000;
			text = UiTheme.TEXT_MUTED;
		} else if (pressed) {
			fill = UiTheme.mix(fill, 0xFF000000, 0.35F);
		}

		graphics.fill(x, y, x + w, y + h, fill);
		UiTheme.border(graphics, x, y, w, h,
			selected ? accent : (isHoveredOrFocused() && active
				? UiTheme.mix(UiTheme.BORDER, accent, 0.6F) : UiTheme.BORDER));
		if (selected)
			graphics.fill(x, y, x + 2, y + h, accent);

		String label = UiTheme.ellipsize(font, getMessage().getString(),
			w - pad * 2 - (selected ? 2 : 0));
		UiTheme.textCentered(graphics, font, label,
			x + w / 2 + (selected ? 1 : 0), UiTheme.textY(font, y, h), text);
		UiTheme.popWidgetScale(graphics, scaled);
	}

	private int fillColor(int accent) {
		if (tint != 0) {
			int base = 0xFF000000 | tint;
			return isHoveredOrFocused() && active
				? UiTheme.scaleRgb(base, 1.2F) : base;
		}
		boolean hot = isHoveredOrFocused() && active;
		return switch (kind) {
			case PRIMARY -> hot ? UiTheme.scaleRgb(accent, 1.25F) : accent;
			case DANGER -> hot ? UiTheme.scaleRgb(UiTheme.DANGER, 1.2F)
				: UiTheme.DANGER;
			case SECONDARY -> hot ? UiTheme.SURFACE_ROW_HOVER
				: UiTheme.SURFACE_ROW;
			case GHOST -> hot ? UiTheme.SURFACE_ROW : 0x00000000;
		};
	}

	private int textColor() {
		return switch (kind) {
			case PRIMARY, DANGER -> UiTheme.accentText();
			case SECONDARY, GHOST -> UiTheme.TEXT;
		};
	}

	@Override
	protected void updateWidgetNarration(NarrationElementOutput narration) {
		defaultButtonNarrationText(narration);
	}
}
