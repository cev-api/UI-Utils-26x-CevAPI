package com.ui_utils.uiutils;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.util.HashMap;
import java.util.Map;
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
			if (loaded != null && !loaded.packetHudEnabled
				&& loaded.packetHudPosition == PacketHudPosition.TOP_LEFT) {
				loaded.packetHudPosition = PacketHudPosition.OFF;
			}
			if (loaded != null) {
				loaded.packetHudEnabled = loaded.packetHudPosition.isEnabled();
			}
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
		public boolean showResourcePackButtons = true;
		public boolean logToChat = true;
		public boolean antiCheatDetectorEnabled = true;
		public String restoreKey = "key.keyboard.v";
		public String packetToolsKey = "key.keyboard.p";
		public boolean packetHudEnabled = true;
		public PacketHudPosition packetHudPosition = PacketHudPosition.TOP_LEFT;
		public int packetHudColor = 0xFFFFFF;
		public String delayToggleKey = "key.keyboard.o";
		public Map<String, String> keyBinds = new HashMap<>();
		public int uiCloseDelayTicks = 0;
		public int uiCommandDelayTicks = 0;
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

		// Autoduper private plugin-GUI test options
		public String autoduperOpenCommand = "/pv 1";
		public String autoduperPrepareCommand = "";
		public int autoduperTargetSlot = 54;
		public int autoduperMaxAttempts = 220;
		public int autoduperStepDelayTicks = 10;
		public boolean autoduperDropValidation = true;
		public boolean autoduperVerboseMode = false;
		public int autoduperSingleAttempt = 0;
		public boolean autoduperAbortHoldEnabled = true;
		public String autoduperAbortKey = "key.keyboard.space";
		public boolean autoduperMoveNone = true;
		public boolean autoduperMovePickup = true;
		public boolean autoduperMoveQuickMove = true;
		public boolean autoduperMoveOffhandSwap = true;
		public boolean autoduperMoveDelayed = true;
		public boolean autoduperCloseKeepOpen = true;
		public boolean autoduperCloseSoftClose = true;
		public boolean autoduperClosePacketKeepScreen = true;
		public boolean autoduperClosePacketLeave = true;
		public boolean autoduperReopenNone = true;
		public boolean autoduperReopenCommand = true;
		public boolean autoduperReopenDoubleCommand = true;
		public boolean autoduperReopenInteract = true;
		public boolean autoduperReopenStaleRestore = true;
		public boolean autoduperReopenPrepareCommand = true;
		public boolean autoduperPacketDelayVariants = true;
		public boolean autoduperHybridOpen = false;
		public boolean autoduperFinishLeaveSend = false;
		public boolean autoduperFinishDisconnectSend = false;
		public String macroRunLastKey = "key.keyboard.m";
		public String macroStopKey = "";
		public String lastMacroName = "";
	}

	public enum PacketHudPosition {
		TOP_LEFT("Top left"),
		TOP_RIGHT("Top right"),
		BOTTOM_LEFT("Bottom left"),
		BOTTOM_RIGHT("Bottom right"),
		OFF("Off");

		private final String label;

		PacketHudPosition(String label) {
			this.label = label;
		}

		public String label() {
			return label;
		}

		public PacketHudPosition next() {
			return switch (this) {
				case TOP_LEFT -> TOP_RIGHT;
				case TOP_RIGHT -> BOTTOM_LEFT;
				case BOTTOM_LEFT -> BOTTOM_RIGHT;
				case BOTTOM_RIGHT -> OFF;
				case OFF -> TOP_LEFT;
			};
		}

		public boolean isEnabled() {
			return this != OFF;
		}
	}
}
