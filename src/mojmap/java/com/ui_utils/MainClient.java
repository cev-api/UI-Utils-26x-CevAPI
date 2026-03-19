package com.ui_utils;

import com.ui_utils.packettools.AdvancedPacketTool;
import com.ui_utils.uiutils.UiUtils;
import com.ui_utils.uiutils.UiUtilsSettings;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;

public final class MainClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		// Ensure Swing UIs can open on non-mac systems
		try { System.setProperty("java.awt.headless", "false"); } catch (Throwable ignored) {}
		UiUtilsSettings.load();
		UiUtils.init();
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			Minecraft mc = Minecraft.getInstance();
			UiUtils.onClientTick(mc);
			com.ui_utils.uiutils.PacketHud.onTick();
			com.ui_utils.packettools.AdvancedPacketTool.onTick();
		});
	}
}
