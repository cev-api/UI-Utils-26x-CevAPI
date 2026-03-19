package com.ui_utils.uiutils;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class UiUtilsSettings {
	private static final Logger LOGGER = LoggerFactory.getLogger("ui-utils");
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final Path SETTINGS_PATH =
		FabricLoader.getInstance().getConfigDir().resolve("ui-utils.json");

	private static Data data = new Data();

	private UiUtilsSettings() {}

	public static Data get() {
		return data;
	}

	public static void load() {
		if (!Files.exists(SETTINGS_PATH)) {
			save();
			return;
		}

		try (Reader reader = Files.newBufferedReader(SETTINGS_PATH)) {
			Data loaded = GSON.fromJson(reader, Data.class);
			data = loaded != null ? loaded : new Data();
		} catch (Exception e) {
			LOGGER.warn("Failed to load UI Utils settings, using defaults.", e);
			data = new Data();
		}
	}

	public static void save() {
		try {
			Files.createDirectories(SETTINGS_PATH.getParent());
			try (Writer writer = Files.newBufferedWriter(SETTINGS_PATH)) {
				GSON.toJson(data, writer);
			}
		} catch (IOException e) {
			LOGGER.warn("Failed to save UI Utils settings.", e);
		}
	}

	public static final class Data {
		public boolean bypassResourcePack = false;
		public boolean resourcePackForceDeny = false;
		public boolean logToChat = true;
		public String restoreKey = "key.keyboard.v";
		public String packetToolsKey = "key.keyboard.p";
		public boolean packetHudEnabled = true;
		public int packetHudColor = 0xFFFFFF;
		public String delayToggleKey = "key.keyboard.o";
		public String disconnectMethod = "QUIT";
		public int disconnectTimeoutSeconds = 30;
		public String disconnectLagMethod = "SLOT_SPAM";
		public int disconnectLagPackets = 300;

		public boolean slotOverlayEnabled = true;
		public boolean slotOverlayHoverOnly = false;
		public int slotOverlayColor = 918256;
		public int slotOverlayAlpha = 255;
		public int slotOverlayOffsetX = -1;
		public int slotOverlayOffsetY = -1;

		// UI-Utils overlay button background color (RGB)
		public int uiButtonColor = 0x4A90E2;
		// UI-Utils overlay button text color (RGB)
		public int uiButtonTextColor = 0xFFFFFF;

		public int fabricateOverlayBgAlpha = 120;

		// Wurst-style command scanner options
		public String commandScannerMode = "PACKET_PROBING";
		public boolean commandScannerDebugProbe = false;
		public boolean commandScannerRunFoundCommands = false;
		public String commandScannerDontSendFilter = "";
		public String commandScannerPacketCommands = "I Love Cevapcici!";
	}
}
