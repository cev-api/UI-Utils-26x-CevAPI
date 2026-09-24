package com.ui_utils.uiutils.ui;

import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/**
 * Setting row: a label on the left and a small switch on the right. Clicking
 * anywhere on the row flips it, matching the Wurst7 modern style.
 */
public class UiToggle extends AbstractWidget implements UiScalable {
	private static final int SWITCH_WIDTH = 22;
	private static final int SWITCH_HEIGHT = 10;

	private final String label;
	private final BooleanSupplier getter;
	private final Consumer<Boolean> onChange;
	private int onColor = -1;
	private String detail = "";
	private float uiScale = 1F;

	public UiToggle(String label, BooleanSupplier getter,
		Consumer<Boolean> onChange) {
		super(0, 0, 10, 10, Component.literal(label));
		this.label = label;
		this.getter = getter;
		this.onChange = onChange;
	}

	/** Text shown on the right of the switch, such as the current value. */
	public UiToggle detail(String value) {
		this.detail = value == null ? "" : value;
		return this;
	}

	public UiToggle onColor(int argb) {
		this.onColor = argb;
		return this;
	}

	public UiToggle uiScale(float value) {
		this.uiScale = value <= 0F ? 1F : value;
		return this;
	}

	@Override
	public void applyUiScale(float value) {
		uiScale(value);
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (!active || !visible || !isValidClickButton(event.buttonInfo())
			|| !isMouseOver(event.x(), event.y()))
			return false;
		onChange.accept(!getter.getAsBoolean());
		return true;
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
		boolean on = active && getter.getAsBoolean();
		int accent = onColor >= 0 ? onColor : UiTheme.accent();
		boolean hot = isHoveredOrFocused() && active;

		int fill = on ? UiTheme.accentTint(0x2E) : UiTheme.SURFACE_ROW;
		if (hot)
			fill = on ? UiTheme.accentTint(0x44) : UiTheme.SURFACE_ROW_HOVER;
		graphics.fill(x, y, x + w, y + h, fill);
		UiTheme.border(graphics, x, y, w, h,
			on ? UiTheme.withAlpha(accent, 0x80) : UiTheme.BORDER);
		if (on)
			graphics.fill(x, y, x + 2, y + h, accent);

		int switchX = x + w - SWITCH_WIDTH - 5;
		int switchY = y + (h - SWITCH_HEIGHT) / 2;
		UiTheme.switchPill(graphics, switchX, switchY, SWITCH_WIDTH, SWITCH_HEIGHT,
			on, accent, UiTheme.offTrack(), on ? UiTheme.accentText()
				: UiTheme.TEXT_DIM);
		UiTheme.border(graphics, switchX, switchY, SWITCH_WIDTH, SWITCH_HEIGHT,
			on ? UiTheme.scaleRgb(accent, 0.7F) : UiTheme.BORDER);

		int textY = UiTheme.textY(font, y, h);
		int detailWidth = detail.isEmpty() ? 0 : font.width(detail) + 6;
		int labelSpace = w - 12 - SWITCH_WIDTH - 10 - detailWidth;
		UiTheme.text(graphics, font,
			UiTheme.ellipsize(font, label, Math.max(10, labelSpace)), x + 6, textY,
			active ? UiTheme.TEXT : UiTheme.TEXT_MUTED);
		if (!detail.isEmpty())
			UiTheme.textRight(graphics, font, detail, switchX - 4, textY,
				on ? UiTheme.OK : UiTheme.TEXT_DIM);

		UiTheme.popWidgetScale(graphics, scaled);
	}

	private void fillRound(GuiGraphicsExtractor graphics, int x, int y, int w,
		int h, int color) {
		graphics.fill(x, y, x + w, y + h, color);
	}

	@Override
	protected void updateWidgetNarration(NarrationElementOutput narration) {
		defaultButtonNarrationText(narration);
	}
}
