package com.ui_utils.packettools;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.ui_utils.uiutils.UiUtils;
import java.io.IOException;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;

public final class AdvancedPacketTool {
	private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm:ss.SSS");
	private static final DateTimeFormatter FILE_TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss");
	private static final Path CONFIG_FILE = FabricLoader.getInstance().getConfigDir().resolve("ui-utils-packet-tools.json");
	private static final Path LOG_DIR = FabricLoader.getInstance().getConfigDir().resolve("packet-logger");

	private static boolean initialized;
	private static boolean loggingEnabled;
	private static boolean denyEnabled;
	private static boolean delayEnabled;
	private static boolean fileOutput;
	private static boolean showUnknownPackets;
	private static int delayTicks = 5;

	private static final Set<String> logS2C = new LinkedHashSet<>();
	private static final Set<String> logC2S = new LinkedHashSet<>();
	private static final Set<String> denyS2C = new LinkedHashSet<>();
	private static final Set<String> denyC2S = new LinkedHashSet<>();
	private static final Set<String> delayS2C = new LinkedHashSet<>();
	private static final Set<String> delayC2S = new LinkedHashSet<>();

	private static final ArrayDeque<QueuedPacket> delayedIncoming = new ArrayDeque<>();
	private static final ArrayDeque<QueuedPacket> delayedOutgoing = new ArrayDeque<>();
	private static final Set<Packet<?>> bypassOutput = Collections.newSetFromMap(new IdentityHashMap<>());

	private static final TreeSet<String> discoveredS2C = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
	private static final TreeSet<String> discoveredC2S = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
	private static final TreeSet<String> discoveredUnknownS2C = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
	private static final TreeSet<String> discoveredUnknownC2S = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);

	private static boolean lastDelayEnabledState;
	private static boolean lastLoggingState;
	private static Path currentLogFile;

	private AdvancedPacketTool() {}

	public static void init() {
		if (initialized)
			return;
		loadSelectionConfig();
		lastDelayEnabledState = delayEnabled;
		lastLoggingState = loggingEnabled;
		initialized = true;
	}

	public static void openScreen(net.minecraft.client.gui.screens.Screen parent) {
		// Prefer an external Swing UI to avoid version-specific rendering issues
		// and allow a more flexible layout matching the requested design.
		AdvancedPacketToolFrame.open();
	}

	public static void onTick() {
		if (!initialized)
			init();

		boolean delayActive = delayEnabled && delayTicks > 0;
		if (!delayActive && lastDelayEnabledState)
			flushQueues(true);
		else if (delayActive)
			flushQueues(false);

		lastDelayEnabledState = delayEnabled;

		if (lastLoggingState && !loggingEnabled)
			currentLogFile = null;
		lastLoggingState = loggingEnabled;
	}

	public static boolean onOutgoing(Packet<?> packet) {
		if (!initialized)
			init();
		if (packet == null)
			return true;

		if (bypassOutput.remove(packet))
			return true;

		String name = PacketCatalog.formatPacketName(packet);
		discoveredC2S.add(name);
		recordUnknown(packet.getClass().getSimpleName(), PacketDirection.C2S);

		if (loggingEnabled && logC2S.contains(name))
			logPacket(name, "C2S", packet);

		if (denyEnabled && denyC2S.contains(name))
			return false;

		if (delayEnabled && delayTicks > 0 && delayC2S.contains(name)) {
			delayedOutgoing.addLast(new QueuedPacket(packet, getCurrentTick() + delayTicks));
			return false;
		}

		return true;
	}

	public static boolean onIncoming(Packet<?> packet) {
		if (!initialized)
			init();
		if (packet == null)
			return true;

		String name = PacketCatalog.formatPacketName(packet);
		discoveredS2C.add(name);
		recordUnknown(packet.getClass().getSimpleName(), PacketDirection.S2C);

		if (loggingEnabled && logS2C.contains(name))
			logPacket(name, "S2C", packet);

		if (denyEnabled && denyS2C.contains(name))
			return false;

		if (delayEnabled && delayTicks > 0 && delayS2C.contains(name)) {
			delayedIncoming.addLast(new QueuedPacket(packet, getCurrentTick() + delayTicks));
			return false;
		}

		return true;
	}

	public static boolean isLoggingEnabled() {
		return loggingEnabled;
	}

	public static boolean isDenyEnabled() {
		return denyEnabled;
	}

	public static boolean isDelayEnabled() {
		return delayEnabled;
	}

	public static boolean isFileOutput() {
		return fileOutput;
	}

	public static boolean isShowUnknownPackets() {
		return showUnknownPackets;
	}

	public static int getDelayTicks() {
		return delayTicks;
	}

	public static void setLoggingEnabled(boolean value) {
		loggingEnabled = value;
		saveSelectionConfig();
	}

	public static void setDenyEnabled(boolean value) {
		denyEnabled = value;
		saveSelectionConfig();
	}

	public static void setDelayEnabled(boolean value) {
		delayEnabled = value;
		saveSelectionConfig();
	}

	public static void setFileOutput(boolean value) {
		fileOutput = value;
		saveSelectionConfig();
	}

	public static void setShowUnknownPackets(boolean value) {
		showUnknownPackets = value;
		saveSelectionConfig();
	}

	public static void setDelayTicks(int value) {
		delayTicks = Math.max(0, Math.min(9999, value));
		saveSelectionConfig();
	}

	public static Set<String> getLogSet(PacketDirection direction) {
		return direction == PacketDirection.S2C ? logS2C : logC2S;
	}

	public static Set<String> getDenySet(PacketDirection direction) {
		return direction == PacketDirection.S2C ? denyS2C : denyC2S;
	}

	public static Set<String> getDelaySet(PacketDirection direction) {
		return direction == PacketDirection.S2C ? delayS2C : delayC2S;
	}

	public static List<String> getAvailablePackets(PacketDirection direction) {
		boolean includeUnknown = showUnknownPackets;
		Set<String> merged = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
		if (direction == PacketDirection.S2C) {
			merged.addAll(PacketCatalog.getS2CNames());
			merged.addAll(discoveredS2C);
			merged.addAll(logS2C);
			merged.addAll(denyS2C);
			merged.addAll(delayS2C);
			if (includeUnknown)
				merged.addAll(discoveredUnknownS2C);
		} else {
			merged.addAll(PacketCatalog.getC2SNames());
			merged.addAll(discoveredC2S);
			merged.addAll(logC2S);
			merged.addAll(denyC2S);
			merged.addAll(delayC2S);
			if (includeUnknown)
				merged.addAll(discoveredUnknownC2S);
		}

		ArrayList<String> list = new ArrayList<>();
		for (String name : merged)
			if (includeUnknown || isUsablePacketName(name))
				list.add(name);
		list.sort(Comparator.naturalOrder());
		return list;
	}

	public static void updateSelection(PacketMode mode, PacketDirection direction, Set<String> selected) {
		Set<String> target = getSet(mode, direction);
		target.clear();
		target.addAll(selected);
		saveSelectionConfig();
	}

	public static Set<String> getSelection(PacketMode mode, PacketDirection direction) {
		return new LinkedHashSet<>(getSet(mode, direction));
	}

	private static Set<String> getSet(PacketMode mode, PacketDirection direction) {
		Objects.requireNonNull(mode);
		Objects.requireNonNull(direction);

		return switch (mode) {
			case LOG -> getLogSet(direction);
			case DENY -> getDenySet(direction);
			case DELAY -> getDelaySet(direction);
		};
	}

	private static void recordUnknown(String className, PacketDirection direction) {
		if (!isUsablePacketName(className)) {
			if (direction == PacketDirection.S2C)
				discoveredUnknownS2C.add(className);
			else
				discoveredUnknownC2S.add(className);
		}
	}

	private static boolean isUsablePacketName(String name) {
		if (name == null || name.isBlank())
			return false;
		String lower = name.toLowerCase();
		return !lower.startsWith("class_");
	}

	private static void flushQueues(boolean forceAll) {
		long now = getCurrentTick();
		flushIncoming(now, forceAll);
		flushOutgoing(now, forceAll);
	}

	private static void flushIncoming(long now, boolean forceAll) {
		if (delayedIncoming.isEmpty())
			return;

		Minecraft mc = Minecraft.getInstance();
		ClientPacketListener connection = mc.getConnection();
		if (connection == null) {
			delayedIncoming.clear();
			return;
		}

		while (!delayedIncoming.isEmpty()) {
			QueuedPacket queued = delayedIncoming.peekFirst();
			if (!forceAll && queued.releaseTick > now)
				break;

			delayedIncoming.removeFirst();
			applyIncomingPacket(queued.packet);
		}
	}

	private static void flushOutgoing(long now, boolean forceAll) {
		if (delayedOutgoing.isEmpty())
			return;

		Minecraft mc = Minecraft.getInstance();
		ClientPacketListener connection = mc.getConnection();
		if (connection == null) {
			delayedOutgoing.clear();
			return;
		}

		while (!delayedOutgoing.isEmpty()) {
			QueuedPacket queued = delayedOutgoing.peekFirst();
			if (!forceAll && queued.releaseTick > now)
				break;

			delayedOutgoing.removeFirst();
			bypassOutput.add(queued.packet);
			connection.send(queued.packet);
		}
	}

	private static void applyIncomingPacket(Packet<?> packet) {
		Minecraft mc = Minecraft.getInstance();
		ClientPacketListener connection = mc.getConnection();
		if (connection == null)
			return;

		@SuppressWarnings("unchecked")
		Packet<ClientPacketListener> typed = (Packet<ClientPacketListener>)packet;
		typed.handle(connection);
	}

	private static void logPacket(String name, String direction, Packet<?> packet) {
		String data = String.valueOf(packet);
		String timestamp = LocalDateTime.now().format(TIME_FORMAT);
		String line = "[" + timestamp + "] [" + direction + "] " + name + " " + data;

		if (fileOutput) {
			appendToLogFile(line + System.lineSeparator());
			return;
		}

		Minecraft mc = Minecraft.getInstance();
		if (mc.player != null)
			mc.execute(() -> mc.player.sendSystemMessage(Component.literal("[PacketTools] " + line)));
	}

	private static void appendToLogFile(String line) {
		try {
			Path file = getCurrentLogFile();
			Files.writeString(file, line, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
		} catch (IOException e) {
			UiUtils.LOGGER.warn("PacketTools: failed writing log file.", e);
		}
	}

	private static Path getCurrentLogFile() throws IOException {
		if (currentLogFile != null)
			return currentLogFile;

		Files.createDirectories(LOG_DIR);
		String name = "packets_" + LocalDateTime.now().format(FILE_TIME_FORMAT) + ".log";
		currentLogFile = LOG_DIR.resolve(name);
		return currentLogFile;
	}

	private static long getCurrentTick() {
		Minecraft mc = Minecraft.getInstance();
		if (mc.level != null)
			return mc.level.getGameTime();
		if (mc.player != null)
			return mc.player.tickCount;
		return System.currentTimeMillis() / 50L;
	}

	public static synchronized void saveSelectionConfig() {
		JsonObject root = new JsonObject();
		root.addProperty("loggingEnabled", loggingEnabled);
		root.addProperty("denyEnabled", denyEnabled);
		root.addProperty("delayEnabled", delayEnabled);
		root.addProperty("fileOutput", fileOutput);
		root.addProperty("showUnknownPackets", showUnknownPackets);
		root.addProperty("delayTicks", delayTicks);
		root.add("logS2C", toJsonArray(logS2C));
		root.add("logC2S", toJsonArray(logC2S));
		root.add("denyS2C", toJsonArray(denyS2C));
		root.add("denyC2S", toJsonArray(denyC2S));
		root.add("delayS2C", toJsonArray(delayS2C));
		root.add("delayC2S", toJsonArray(delayC2S));

		try {
			Files.createDirectories(CONFIG_FILE.getParent());
			try (Writer writer = Files.newBufferedWriter(CONFIG_FILE)) {
				com.google.gson.GsonBuilder gb = new com.google.gson.GsonBuilder().setPrettyPrinting();
				gb.create().toJson(root, writer);
			}
		} catch (IOException e) {
			UiUtils.LOGGER.warn("Failed to save packet tool config", e);
		}
	}

	private static synchronized void loadSelectionConfig() {
		if (!Files.exists(CONFIG_FILE))
			return;

		try {
			JsonObject root = JsonParser.parseReader(Files.newBufferedReader(CONFIG_FILE)).getAsJsonObject();
			loggingEnabled = getBoolean(root, "loggingEnabled", false);
			denyEnabled = getBoolean(root, "denyEnabled", false);
			delayEnabled = getBoolean(root, "delayEnabled", false);
			fileOutput = getBoolean(root, "fileOutput", false);
			showUnknownPackets = getBoolean(root, "showUnknownPackets", false);
			delayTicks = Math.max(0, Math.min(9999, getInt(root, "delayTicks", 5)));
			loadSet(root, "logS2C", logS2C);
			loadSet(root, "logC2S", logC2S);
			loadSet(root, "denyS2C", denyS2C);
			loadSet(root, "denyC2S", denyC2S);
			loadSet(root, "delayS2C", delayS2C);
			loadSet(root, "delayC2S", delayC2S);
		} catch (Exception e) {
			UiUtils.LOGGER.warn("Failed to load packet tool config", e);
		}
	}

	private static boolean getBoolean(JsonObject root, String key, boolean fallback) {
		if (!root.has(key) || !root.get(key).isJsonPrimitive())
			return fallback;
		try {
			return root.get(key).getAsBoolean();
		} catch (Exception ignored) {
			return fallback;
		}
	}

	private static int getInt(JsonObject root, String key, int fallback) {
		if (!root.has(key) || !root.get(key).isJsonPrimitive())
			return fallback;
		try {
			return root.get(key).getAsInt();
		} catch (Exception ignored) {
			return fallback;
		}
	}

	private static void loadSet(JsonObject root, String key, Set<String> set) {
		set.clear();
		if (!root.has(key) || !root.get(key).isJsonArray())
			return;
		root.getAsJsonArray(key).forEach(e -> {
			if (e.isJsonPrimitive() && e.getAsJsonPrimitive().isString()) {
				String name = e.getAsString().trim();
				if (!name.isEmpty())
					set.add(name);
			}
		});
	}

	private static JsonArray toJsonArray(Set<String> values) {
		JsonArray array = new JsonArray();
		for (String value : values)
			array.add(value);
		return array;
	}

	private static final class QueuedPacket {
		private final Packet<?> packet;
		private final long releaseTick;

		private QueuedPacket(Packet<?> packet, long releaseTick) {
			this.packet = packet;
			this.releaseTick = releaseTick;
		}
	}

	public enum PacketMode {
		LOG("Log"),
		DENY("Deny"),
		DELAY("Delay");

		private final String label;

		PacketMode(String label) {
			this.label = label;
		}

		public String getLabel() {
			return label;
		}

		public PacketMode next() {
			return values()[(ordinal() + 1) % values().length];
		}
	}

	public enum PacketDirection {
		S2C,
		C2S
	}
}
