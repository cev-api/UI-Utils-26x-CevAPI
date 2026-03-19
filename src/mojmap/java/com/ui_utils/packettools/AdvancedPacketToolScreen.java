package com.ui_utils.packettools;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

// Minimal placeholder screen kept for backward compatibility. The actual
// advanced UI is provided by the Swing frame (AdvancedPacketToolFrame).
public final class AdvancedPacketToolScreen extends Screen {
	private final Screen parent;

	public AdvancedPacketToolScreen(Screen parent) {
		super(Component.literal("Advanced Packet Tool"));
		this.parent = parent;
	}

	@Override
	public void onClose() {
		if (minecraft != null)
			minecraft.setScreen(parent);
	}

	@Override
	public boolean isPauseScreen() { return false; }
}
