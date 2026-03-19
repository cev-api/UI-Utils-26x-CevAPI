package com.ui_utils.uiutils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.protocol.Packet;
import net.minecraft.world.inventory.AbstractContainerMenu;

public final class UiUtilsState {
	public static boolean sendUiPackets = true;
	public static boolean delayUiPackets = false;
	public static boolean shouldEditSign = true;

	public static final List<Packet<?>> delayedUiPackets = new ArrayList<>();

	public static Screen storedScreen;
	public static AbstractContainerMenu storedMenu;
	public static final Map<String, Screen> savedScreens = new HashMap<>();
	public static final Map<String, AbstractContainerMenu> savedMenus = new HashMap<>();

	public static boolean enabled = true;
	public static boolean skipNextContainerRemoval = false;
	public static boolean fabricateOverlayOpen = false;
	public static int fabricateOverlayX = -1;
	public static int fabricateOverlayY = -1;
	public static int spamCount = 1;
	public static boolean settingsPanelOpen = false;

	private UiUtilsState() {}

	public static boolean isUiEnabled() {
		return enabled;
	}
}
