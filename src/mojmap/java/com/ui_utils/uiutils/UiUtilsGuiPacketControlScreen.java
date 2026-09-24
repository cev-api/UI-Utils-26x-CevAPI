package com.ui_utils.uiutils;

import java.util.List;
import com.ui_utils.uiutils.ui.UiButton;
import com.ui_utils.uiutils.ui.UiContent;
import com.ui_utils.uiutils.ui.UiModernScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Per-packet Allow / Drop / Delay rules for outgoing container packets.
 */
public final class UiUtilsGuiPacketControlScreen extends UiModernScreen {
	private final Screen parent;
	private UiButton delayButton;

	public UiUtilsGuiPacketControlScreen(Screen parent) {
		super(Component.literal("GUI Packet Control"));
		this.parent = parent;
	}

	@Override
	protected int naturalWidth() {
		return 300;
	}

	@Override
	protected void buildContent(UiContent c) {
		c.note("Click a packet to cycle Allow > Drop > Delay. Changes are saved immediately.");

		List<UiUtilsGuiPacketControl.Entry> entries = UiUtilsGuiPacketControl.entries();
		for(UiUtilsGuiPacketControl.Entry entry : entries) {
			UiUtilsGuiPacketControl.Mode initial =
				UiUtilsGuiPacketControl.mode(entry.id());
			UiButton button = UiButton.of(entry.label() + ": " + initial.label(), null);
			button.tint(tintFor(initial));
			button.action(() -> {
				UiUtilsGuiPacketControl.Mode mode =
					UiUtilsGuiPacketControl.cycleMode(entry.id());
				button.setMessage(Component.literal(
					entry.label() + ": " + mode.label()));
				button.tint(tintFor(mode));
				UiUtils.chatIfEnabled("GUI packet " + entry.label() + ": "
					+ mode.label());
			});
			c.row(UiContent.of(button));
		}

		c.section("Tick delay");
		delayButton = UiButton.of(delayLabel(), null);
		delayButton.action(null);
		c.row(UiContent.of(UiButton.of("Delay -", () -> {
			UiUtilsGuiPacketControl.adjustDelay(-1);
			updateDelayLabel();
		})), UiContent.of(delayButton), UiContent.of(UiButton.of("Delay +", () -> {
			UiUtilsGuiPacketControl.adjustDelay(1);
			updateDelayLabel();
		})));

		c.footerButton("Reset All", UiButton.Kind.DANGER, () -> {
			UiUtilsGuiPacketControl.resetAndPersist();
			Minecraft.getInstance().execute(this::rebuildWidgets);
		});
		c.footerButton("Done", UiButton.Kind.PRIMARY, this::onClose);
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

	@Override
	public void onClose() {
		McCompat.setScreen(this.minecraft, parent);
	}
}
