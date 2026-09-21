package com.ui_utils.uiutils;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

/**
 * Focused replacement for the old all-or-nothing "Delay Packets" switch: every
 * outgoing container packet class gets its own Allow / Drop / Delay rule.
 */
public final class UiUtilsGuiPacketControlScreen extends Screen {
	private final Screen parent;
	private final List<UiUtilsColoredButton> rowButtons = new ArrayList<>();
	private UiUtilsColoredButton delayButton;

	public UiUtilsGuiPacketControlScreen(Screen parent) {
		super(Component.literal("GUI Packet Control"));
		this.parent = parent;
	}

	@Override
	protected void init() {
		rowButtons.clear();
		List<UiUtilsGuiPacketControl.Entry> entries = UiUtilsGuiPacketControl.entries();
		int columnWidth = Math.min(330, this.width - 20);
		int left = (this.width - columnWidth) / 2;
		int top = 46;
		int footerHeight = 52;
		int available = Math.max(80, this.height - top - footerHeight);
		int rowHeight = Mth.clamp(available / Math.max(1, entries.size()), 12, 22);
		int gap = Math.max(1, Math.min(3, rowHeight / 8));

		int y = top;
		for (UiUtilsGuiPacketControl.Entry entry : entries) {
			UiUtilsGuiPacketControl.Mode initialMode =
				UiUtilsGuiPacketControl.mode(entry.id());
			UiUtilsColoredButton button = UiUtils.styledButton(
				entry.label() + ": " + initialMode.label(),
				b -> {
					UiUtilsGuiPacketControl.Mode mode =
						UiUtilsGuiPacketControl.cycleMode(entry.id());
					b.setMessage(Component.literal(
						entry.label() + ": " + mode.label()));
					b.tint(tintFor(mode));
					UiUtils.chatIfEnabled("GUI packet " + entry.label() + ": "
						+ mode.label());
				}, left, y, columnWidth, rowHeight);
			button.tint(tintFor(initialMode));
			rowButtons.add(button);
			addRenderableWidget(button);
			y += rowHeight + gap;
		}

		int footerY = this.height - footerHeight + 4;
		int third = (columnWidth - 12) / 3;
		delayButton = UiUtils.styledButton(delayLabel(), b -> {
		}, left, footerY, third, 20);
		addRenderableWidget(delayButton);
		addRenderableWidget(UiUtils.styledButton("Delay -", b -> {
			UiUtilsGuiPacketControl.adjustDelay(-1);
			updateDelayLabel();
		}, left + third + 6, footerY, third, 20));
		addRenderableWidget(UiUtils.styledButton("Delay +", b -> {
			UiUtilsGuiPacketControl.adjustDelay(1);
			updateDelayLabel();
		}, left + third * 2 + 12, footerY, columnWidth - third * 2 - 12, 20));

		int bottomY = footerY + 24;
		addRenderableWidget(UiUtils.styledButton("Reset All", b -> {
			UiUtilsGuiPacketControl.resetAndPersist();
			reopen();
		}, left, bottomY, (columnWidth - 6) / 2, 20));
		addRenderableWidget(UiUtils.styledButton("Done",
			b -> McCompat.setScreen(this.minecraft, parent),
			left + (columnWidth - 6) / 2 + 6, bottomY,
			columnWidth - (columnWidth - 6) / 2 - 6, 20));
	}

	private static int tintFor(UiUtilsGuiPacketControl.Mode mode) {
		return switch (mode) {
			case ALLOW -> 0;
			case DROP -> 0xA8453C;
			case DELAY -> 0xB08A2E;
		};
	}

	private static String delayLabel() {
		return "Tick delay: " + UiUtilsGuiPacketControl.delayTicks();
	}

	private void updateDelayLabel() {
		if (delayButton != null)
			delayButton.setMessage(Component.literal(delayLabel()));
	}

	private void reopen() {
		McCompat.setScreen(this.minecraft, new UiUtilsGuiPacketControlScreen(parent));
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX,
		int mouseY, float partialTicks) {
		super.extractRenderState(graphics, mouseX, mouseY, partialTicks);
		graphics.centeredText(this.font, this.title, this.width / 2, 14,
			0xFFFFFFFF);
		graphics.centeredText(this.font,
			Component.literal("Click a packet to cycle Allow > Drop > Delay"),
			this.width / 2, 28, 0xFFB8D8FF);
	}
}
