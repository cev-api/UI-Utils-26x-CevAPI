package com.ui_utils.uiutils;

import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public final class UiUtilsCommandScannerScreen extends Screen {
	private final Screen parent;
	private EditBox dontSendFilterField;
	private EditBox packetCommandsField;
	private UiUtilsColoredButton scannerModeButton;

	public UiUtilsCommandScannerScreen(Screen parent) {
		super(Component.literal("UI-Utils Command Scanner"));
		this.parent = parent;
	}

	@Override
	protected void init() {
		int panelWidth = 420;
		int left = this.width / 2 - panelWidth / 2;
		int y = this.height / 2 - 110;
		int rowH = 20;
		int gap = 4;

		scannerModeButton = addRenderableWidget(UiUtils.styledButton("", b -> {
			boolean packet = !"CLIENT_SIDE_ENUMERATION".equalsIgnoreCase(UiUtilsSettings.get().commandScannerMode);
			UiUtilsSettings.get().commandScannerMode = packet ? "CLIENT_SIDE_ENUMERATION" : "PACKET_PROBING";
			UiUtilsSettings.save();
			refreshScannerModeLabel();
		}, left, y, 205, rowH));
		refreshScannerModeLabel();

		addRenderableWidget(UiUtils.styledButton("Run scanner", b -> {
			String result = UiUtilsCommandScanner.startScan();
			if (minecraft.player != null && !result.isEmpty())
				minecraft.player.sendSystemMessage(Component.literal(result));
		}, left + 215, y, 205, rowH));
		y += rowH + gap;

		addRenderableWidget(UiUtils.styledButton(
			"Scanner debug probe: " + (UiUtilsSettings.get().commandScannerDebugProbe ? "ON" : "OFF"),
			b -> {
				UiUtilsSettings.get().commandScannerDebugProbe = !UiUtilsSettings.get().commandScannerDebugProbe;
				UiUtilsSettings.save();
				b.setMessage(Component.literal("Scanner debug probe: "
					+ (UiUtilsSettings.get().commandScannerDebugProbe ? "ON" : "OFF")));
			}, left, y, 205, rowH));
		addRenderableWidget(UiUtils.styledButton(
			"Run found commands: " + (UiUtilsSettings.get().commandScannerRunFoundCommands ? "ON" : "OFF"),
			b -> {
				UiUtilsSettings.get().commandScannerRunFoundCommands = !UiUtilsSettings.get().commandScannerRunFoundCommands;
				UiUtilsSettings.save();
				b.setMessage(Component.literal("Run found commands: "
					+ (UiUtilsSettings.get().commandScannerRunFoundCommands ? "ON" : "OFF")));
			}, left + 215, y, 205, rowH));
		y += rowH + gap;

		dontSendFilterField = new EditBox(this.font, left, y, 205, rowH, Component.literal("Don't send filter"));
		dontSendFilterField.setMaxLength(128);
		dontSendFilterField.setValue(UiUtilsSettings.get().commandScannerDontSendFilter);
		addRenderableWidget(dontSendFilterField);
		addRenderableWidget(UiUtils.styledButton("Apply filter", b -> {
			UiUtilsSettings.get().commandScannerDontSendFilter = dontSendFilterField.getValue();
			UiUtilsSettings.save();
		}, left + 215, y, 205, rowH));
		y += rowH + gap;

		packetCommandsField = new EditBox(this.font, left, y, 205, rowH, Component.literal("Packet commands"));
		packetCommandsField.setMaxLength(128);
		packetCommandsField.setValue(UiUtilsSettings.get().commandScannerPacketCommands);
		addRenderableWidget(packetCommandsField);
		addRenderableWidget(UiUtils.styledButton("Send packet cmds", b -> {
			UiUtilsSettings.get().commandScannerPacketCommands = packetCommandsField.getValue();
			UiUtilsSettings.save();
			String result = UiUtilsCommandScanner.sendManualPacketCommands();
			if (minecraft.player != null && !result.isEmpty())
				minecraft.player.sendSystemMessage(Component.literal(result));
		}, left + 215, y, 205, rowH));
		y += rowH + gap;

		addRenderableWidget(UiUtils.styledButton("Get plugins", b -> {
			String result = UiUtilsPluginScanner.startScan();
			if (minecraft.player != null && !result.isEmpty())
				minecraft.player.sendSystemMessage(Component.literal(result));
		}, left, y, 205, rowH));

		addRenderableWidget(UiUtils.styledButton("Done",
			b -> this.minecraft.setScreen(parent), left + 215, y, 205, rowH));
	}

	private void refreshScannerModeLabel() {
		if (scannerModeButton == null)
			return;
		boolean packet = !"CLIENT_SIDE_ENUMERATION".equalsIgnoreCase(UiUtilsSettings.get().commandScannerMode);
		scannerModeButton.setMessage(Component.literal("Scanner mode: " + (packet ? "Packet" : "Client")));
	}

	@Override
	public void onClose() {
		this.minecraft.setScreen(parent);
	}
}
