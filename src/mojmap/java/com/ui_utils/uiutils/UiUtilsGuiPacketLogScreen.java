package com.ui_utils.uiutils;

import java.util.List;
import com.ui_utils.uiutils.ui.UiButton;
import com.ui_utils.uiutils.ui.UiContent;
import com.ui_utils.uiutils.ui.UiModernScreen;
import com.ui_utils.uiutils.ui.UiTheme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Read-only view of the GUI packet log. One row per container packet with the
 * columns: timestamp | direction | packet | syncId | revision | slot | button |
 * action | cursor/item.
 */
public final class UiUtilsGuiPacketLogScreen extends UiModernScreen {
	private final Screen parent;
	private UiButton logToggleButton;

	public UiUtilsGuiPacketLogScreen(Screen parent) {
		super(Component.literal("GUI Packet Log"));
		this.parent = parent;
	}

	@Override
	protected int naturalWidth() {
		return 560;
	}

	@Override
	protected boolean expandWidth() {
		return true;
	}

	@Override
	protected int maxWidth() {
		return 1500;
	}

	@Override
	protected void buildContent(UiContent c) {
		int lineHeight = lineHeight();
		List<String> lines = UiUtilsGuiPacketLog.lines();
		// One header line plus one line per entry, all painted in the overlay.
		int drawnLines = lines.size() + 1;
		c.reportBottom(drawnLines * lineHeight);
		c.space(drawnLines * lineHeight);

		logToggleButton = c.footerButton("", UiButton.Kind.SECONDARY, () -> {
			UiUtilsGuiPacketLog.setEnabled(!UiUtilsGuiPacketLog.isEnabled());
			refreshLogButton();
		});
		refreshLogButton();
		c.footerButton("Copy Log", UiButton.Kind.SECONDARY, () -> {
			this.minecraft.keyboardHandler.setClipboard(UiUtilsGuiPacketLog.asText());
			UiUtils.chatIfEnabled("Copied GUI packet log to clipboard");
		});
		c.footerButton("Clear", UiButton.Kind.DANGER, () -> {
			UiUtilsGuiPacketLog.clear();
			Minecraft.getInstance().execute(this::rebuildWidgets);
		});
		c.footerButton("Done", UiButton.Kind.PRIMARY, this::onClose);
	}

	@Override
	protected void drawContentOverlay(GuiGraphicsExtractor graphics, Font font,
		int mouseX, int mouseY) {
		int lineHeight = lineHeight();
		int top = (int)Math.round(scrollOffset());
		int bottom = top + contentHeightUnits();

		UiTheme.text(graphics, font, UiUtilsGuiPacketLog.HEADER, 0, 1,
			0xFFFFDE7A);
		List<String> lines = UiUtilsGuiPacketLog.lines();
		if (lines.isEmpty()) {
			UiTheme.text(graphics, font,
				"Nothing captured yet. Open a container to record GUI traffic.",
				0, lineHeight + 1, UiTheme.TEXT_MUTED);
			return;
		}
		for (int i = 0; i < lines.size(); i++) {
			int y = (i + 1) * lineHeight;
			if (y + lineHeight < top || y > bottom)
				continue;
			String line = lines.get(i);
			int color = line.contains("| IN |") ? 0xFF9BD8FF : 0xFFEAEAEA;
			if (line.contains("| DROP") || line.contains("CLOSE"))
				color = 0xFFFF9B6B;
			UiTheme.text(graphics, font,
				UiTheme.ellipsize(font, line, contentWidthUnits()), 0, y + 1, color);
		}
	}

	private void refreshLogButton() {
		if (logToggleButton != null)
			logToggleButton.setMessage(Component.literal(
				"Log: " + (UiUtilsGuiPacketLog.isEnabled() ? "ON" : "OFF")));
	}

	private static int lineHeight() {
		return Minecraft.getInstance().font.lineHeight + 2;
	}

	@Override
	public void onClose() {
		McCompat.setScreen(this.minecraft, parent);
	}
}
