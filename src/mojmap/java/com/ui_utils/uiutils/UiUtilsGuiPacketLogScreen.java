package com.ui_utils.uiutils;

import java.util.List;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/**
 * Read-only view of the GUI packet log. One row per container packet with the
 * columns: timestamp | direction | packet | syncId | revision | slot | button |
 * action | cursor/item.
 */
public final class UiUtilsGuiPacketLogScreen extends Screen {
	private final Screen parent;
	private int scroll;
	private int scrollbarX;
	private int scrollbarTop;
	private int scrollbarBottom;
	private int scrollbarThumbY;
	private int scrollbarThumbHeight;
	private int scrollbarMaxScroll;
	private boolean showHeaderRow = true;

	public UiUtilsGuiPacketLogScreen(Screen parent) {
		super(Component.literal("GUI Packet Log"));
		this.parent = parent;
	}

	@Override
	protected void init() {
		int columnWidth = columnWidth();
		int left = (this.width - columnWidth) / 2;
		int y = this.height - 28;
		int third = (columnWidth - 8) / 3;
		addRenderableWidget(UiUtils.styledButton("Copy Log", b -> {
			this.minecraft.keyboardHandler.setClipboard(UiUtilsGuiPacketLog.asText());
			UiUtils.chatIfEnabled("Copied GUI packet log to clipboard");
		}, left, y, third, 20));
		addRenderableWidget(UiUtils.styledButton("Clear", b -> {
			UiUtilsGuiPacketLog.clear();
			scroll = 0;
		}, left + third + 4, y, third, 20));
		addRenderableWidget(UiUtils.styledButton(
			"Log: " + (UiUtilsGuiPacketLog.isEnabled() ? "ON" : "OFF"), b -> {
				UiUtilsGuiPacketLog.setEnabled(!UiUtilsGuiPacketLog.isEnabled());
				b.setMessage(Component.literal("Log: "
					+ (UiUtilsGuiPacketLog.isEnabled() ? "ON" : "OFF")));
			}, left + third * 2 + 8, y, columnWidth - third * 2 - 8, 20));
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX,
		int mouseY, float partialTicks) {
		super.extractRenderState(graphics, mouseX, mouseY, partialTicks);
		int columnWidth = columnWidth();
		int left = (this.width - columnWidth) / 2;
		int top = 34;
		int bottom = this.height - 34;
		int lineHeight = this.font.lineHeight + 2;
		graphics.centeredText(this.font, this.title, this.width / 2, 10,
			0xFFFFFFFF);
		graphics.fill(left, top, left + columnWidth, bottom, 0xB0000000);

		List<String> lines = UiUtilsGuiPacketLog.lines();
		int visible = Math.max(1, (bottom - top - 6) / lineHeight);
		scroll = Math.max(0, Math.min(scroll, Math.max(0, lines.size() - visible)));

		int y = top + 3;
		if (showHeaderRow) {
			graphics.text(this.font, UiUtilsGuiPacketLog.HEADER, left + 5, y,
				0xFFFFDE7A, false);
			y += lineHeight;
		}

		if (lines.isEmpty()) {
			graphics.text(this.font,
				"Nothing captured yet. Open a container to record GUI traffic.",
				left + 5, y, 0xFF888888, false);
		}

		for (int i = scroll; i < lines.size() && y < bottom - lineHeight;
			i++, y += lineHeight) {
			String line = lines.get(i);
			int color = line.contains("| IN |") ? 0xFF9BD8FF : 0xFFEAEAEA;
			if (line.contains("| DROP") || line.contains("CLOSE"))
				color = 0xFFFF9B6B;
			graphics.text(this.font, line, left + 5, y, color, false);
		}

		scrollbarX = left + columnWidth - 5;
		scrollbarTop = top + 2;
		scrollbarBottom = bottom - 2;
		scrollbarMaxScroll = Math.max(0, lines.size() - visible);
		int trackHeight = Math.max(1, scrollbarBottom - scrollbarTop);
		scrollbarThumbHeight = scrollbarMaxScroll == 0 ? trackHeight
			: Math.max(12, (int)Math.round(trackHeight
				* (visible / (double)Math.max(1, lines.size()))));
		int travel = Math.max(1, trackHeight - scrollbarThumbHeight);
		scrollbarThumbY = scrollbarTop + (scrollbarMaxScroll == 0 ? 0
			: (int)Math.round((scroll / (double)scrollbarMaxScroll) * travel));
		graphics.fill(scrollbarX, scrollbarTop, scrollbarX + 3, scrollbarBottom,
			0xFF353535);
		graphics.fill(scrollbarX, scrollbarThumbY, scrollbarX + 3,
			scrollbarThumbY + scrollbarThumbHeight, 0xFFCFCFCF);
	}

	/**
	 * The log rows are long, so the panel is at least double the old width and
	 * grows with the window instead of being pinned at a fixed size.
	 */
	private int columnWidth() {
		int target = Math.max(720, (int)(this.width * 0.92D));
		return Math.max(320, Math.min(this.width - 20, target));
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX,
		double scrollY) {
		if (scrollY < 0)
			scroll++;
		else if (scrollY > 0)
			scroll = Math.max(0, scroll - 1);
		return true;
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (event.button() == McCompat.LEFT_BUTTON && event.x() >= scrollbarX
			&& event.x() <= scrollbarX + 4 && event.y() >= scrollbarTop
			&& event.y() <= scrollbarBottom && scrollbarMaxScroll > 0) {
			int travel = Math.max(1,
				scrollbarBottom - scrollbarTop - scrollbarThumbHeight);
			double ratio = Math.max(0.0D, Math.min(1.0D,
				(event.y() - scrollbarTop - scrollbarThumbHeight / 2.0D) / travel));
			scroll = (int)Math.round(ratio * scrollbarMaxScroll);
			return true;
		}
		return super.mouseClicked(event, doubleClick);
	}

	@Override
	public void onClose() {
		McCompat.setScreen(this.minecraft, parent);
	}
}
