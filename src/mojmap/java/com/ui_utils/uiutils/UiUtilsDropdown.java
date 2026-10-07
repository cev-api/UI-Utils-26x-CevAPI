package com.ui_utils.uiutils;

import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import com.ui_utils.uiutils.ui.UiScalable;
import com.ui_utils.uiutils.ui.UiTheme;

/**
 * Small dropdown used by the packet fabricator to pick the click action.
 * <p>
 * The header behaves like a normal widget and handles its own clicks. While the
 * list is open its hit test includes the list without changing its layout height, which
 * lets the normal widget dispatch route list clicks here without any global
 * mouse hook. The list itself is painted by the owning panel at the end of the
 * frame so it stays above the other controls.
 */
public final class UiUtilsDropdown extends AbstractWidget implements UiScalable {
	private final Font font;
	private final List<String> options;
	private final String label;
	private int selected;
	private boolean expanded;
	private float uiScale = 1F;

	public UiUtilsDropdown(Font font, int x, int y, int width, int height,
		String label, List<String> options) {
		super(x, y, width, height, Component.literal(label));
		this.font = font;
		this.label = label;
		this.options = List.copyOf(options);
	}

	public int selectedIndex() {
		return selected;
	}

	public String selectedOption() {
		return options.isEmpty() ? "" : options.get(selected);
	}

	public void setSelectedIndex(int index) {
		selected = Mth.clamp(index, 0, Math.max(0, options.size() - 1));
		// Collapse through setExpanded so the widget shrinks back to the header.
		setExpanded(false);
	}

	public boolean isExpanded() {
		return expanded;
	}

	public void setExpanded(boolean value) {
		expanded = value;
	}

	public void toggleExpanded() {
		setExpanded(!expanded);
	}

	public boolean isOverHeader(double mouseX, double mouseY) {
		return visible && mouseX >= getX() && mouseX <= getX() + getWidth()
			&& mouseY >= getY() && mouseY < getY() + getHeight();
	}

	@Override
	public boolean isMouseOver(double mouseX, double mouseY) {
		return visible && (isOverHeader(mouseX, mouseY) || indexAt(mouseX, mouseY) >= 0);
	}

	public int itemHeight() {
		return getHeight();
	}

	public int listTop() {
		return getY() + getHeight() + 1;
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (!visible || !active || event.button() != McCompat.LEFT_BUTTON
			|| !isMouseOver(event.x(), event.y()))
			return false;
		if (isOverHeader(event.x(), event.y())) {
			toggleExpanded();
			return true;
		}
		int index = indexAt(event.x(), event.y());
		if (index < 0)
			return false;
		setSelectedIndex(index);
		return true;
	}

	/** Option index under the cursor while expanded, or -1. */
	public int indexAt(double mouseX, double mouseY) {
		if (!expanded || options.isEmpty())
			return -1;
		if (mouseX < getX() || mouseX > getX() + getWidth())
			return -1;
		int top = listTop();
		if (mouseY < top || mouseY > top + options.size() * itemHeight())
			return -1;
		int index = (int)((mouseY - top) / itemHeight());
		return index < 0 || index >= options.size() ? -1 : index;
	}

	/** Draws the expanded option list; call after all other overlay widgets. */
	public void renderExpandedList(GuiGraphicsExtractor graphics, int mouseX,
		int mouseY) {
		if (!expanded || options.isEmpty())
			return;
		int top = listTop();
		int height = itemHeight();
		int width = getWidth();
		int x = getX();
		graphics.fill(x - 1, top - 1, x + width + 1, top + options.size() * height + 1,
			0xFF101010);
		for (int i = 0; i < options.size(); i++) {
			int itemTop = top + i * height;
			boolean hovered = mouseX >= x && mouseX <= x + width
				&& mouseY >= itemTop && mouseY <= itemTop + height;
			boolean isSelected = i == selected;
			int fill = isSelected ? UiTheme.accent()
				: hovered ? UiTheme.SURFACE_ROW_HOVER : UiTheme.SURFACE_ROW;
			graphics.fill(x, itemTop, x + width, itemTop + height, fill);
			UiTheme.border(graphics, x, itemTop, width, height, UiTheme.BORDER);
			UiTheme.text(graphics, font, UiTheme.ellipsize(font, options.get(i), width - 8),
				x + 4, UiTheme.textY(font, itemTop, height),
				isSelected ? UiTheme.accentText() : UiTheme.TEXT);
		}
	}

	@Override
	protected void extractWidgetRenderState(GuiGraphicsExtractor graphics,
		int mouseX, int mouseY, float partialTicks) {
		int x = getX();
		int y = getY();
		int w = getWidth();
		// Only the header is painted here; the open list is drawn later by the
		// owning panel so it ends up above the other controls.
		boolean scaled = UiTheme.pushWidgetScale(graphics, x, y, uiScale);
		int h = UiTheme.localSize(getHeight(), uiScale, scaled);
		int localW = UiTheme.localSize(w, uiScale, scaled);
		int originX = scaled ? 0 : x;
		int originY = scaled ? 0 : y;
		int accent = UiTheme.accent();
		graphics.fill(originX, originY, originX + localW, originY + h, accent);
		UiTheme.border(graphics, originX, originY, localW, h,
			UiTheme.scaleRgb(accent, 0.7F));
		String text = label + ": " + selectedOption() + (expanded ? " \u25B4"
			: " \u25BE");
		Font font = Minecraft.getInstance().font;
		int textY = UiTheme.textY(font, originY, h);
		UiTheme.text(graphics, font,
			UiTheme.ellipsize(font, text, localW - 8), originX + 4, textY,
			UiTheme.accentText());
		UiTheme.popWidgetScale(graphics, scaled);
	}

	@Override
	public void applyUiScale(float value) {
		this.uiScale = value <= 0F ? 1F : value;
	}

	@Override
	protected void updateWidgetNarration(NarrationElementOutput narration) {
	}
}
