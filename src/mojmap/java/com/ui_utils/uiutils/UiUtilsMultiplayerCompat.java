package com.ui_utils.uiutils;

/** Tracks equivalent protection controls supplied by other multiplayer-screen mods. */
public final class UiUtilsMultiplayerCompat {
	private static volatile boolean externalResourcePackControls;
	private static volatile boolean externalRegistrySyncControl;

	private UiUtilsMultiplayerCompat() {
	}

	public static void setExternalControls(boolean resourcePack,
		boolean registrySync) {
		externalResourcePackControls = resourcePack;
		externalRegistrySyncControl = registrySync;
	}

	public static boolean hasExternalResourcePackControls() {
		return externalResourcePackControls;
	}

	public static boolean hasExternalRegistrySyncControl() {
		return externalRegistrySyncControl;
	}
}
