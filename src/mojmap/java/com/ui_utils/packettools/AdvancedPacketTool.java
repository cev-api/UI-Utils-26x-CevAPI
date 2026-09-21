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
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundRemoveEntitiesPacket;

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

	// ---- Verbose mode (engine ported from Wurst's packet tools) ----
	// Dumps the selected packet types at full detail. On by default; the per-type
	// Log selection decides which types are monitored.
	private static boolean verboseEnabled = true;
	private static boolean verboseHumanReadable;
	private static boolean verboseOutsideGame = true;
	private static int verboseFlushInterval = 20;

	private static final PacketDecodeCoverage decodeCoverage = new PacketDecodeCoverage();
	private static final EntityLifecycleTracker lifecycleTracker = new EntityLifecycleTracker();
	private static final PacketFilter packetFilter = new PacketFilter();
	private static PacketDumper packetDumper;

	private static Path currentVerboseJsonlFile;
	private static Path currentVerboseHumanFile;

	private static final List<String> verboseJsonlBuffer = new ArrayList<>(512);
	private static final List<String> verboseHumanBuffer = new ArrayList<>(512);
	private static int verboseFlushTickCounter;

	/**
	 * Handle for {@link PacketToolsScreen}. All tool state is static; this exists
	 * so the ported screen keeps its original constructor shape.
	 */
	public static final AdvancedPacketTool INSTANCE = new AdvancedPacketTool();

	private AdvancedPacketTool() {}

	public static void init() {
		if (initialized)
			return;
		loadSelectionConfig();
		lastDelayEnabledState = delayEnabled;
		lastLoggingState = loggingEnabled;
		initialized = true;
	}

	/** Opens the in-game packet tool screen. */
	public static void openScreen(net.minecraft.client.gui.screens.Screen parent) {
		Minecraft mc = Minecraft.getInstance();
		mc.execute(() -> com.ui_utils.uiutils.McCompat.setScreen(mc,
			new PacketToolsScreen(parent, INSTANCE)));
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

		if (lastLoggingState != loggingEnabled) {
			currentLogFile = null;
			// Start a fresh verbose log next time logging is switched on, and drop
			// anything buffered when it is switched off.
			currentVerboseJsonlFile = null;
			currentVerboseHumanFile = null;
			discardVerboseBuffers();
		}
		lastLoggingState = loggingEnabled;

		updateVerbose();
	}

	private static void updateVerbose() {
		if (!loggingEnabled)
			return;
		lifecycleTracker.tick();
		if (++verboseFlushTickCounter >= Math.max(1, verboseFlushInterval)) {
			verboseFlushTickCounter = 0;
			flushVerboseBuffers();
		}
	}

	private static void discardVerboseBuffers() {
		synchronized (verboseJsonlBuffer) {
			verboseJsonlBuffer.clear();
		}
		synchronized (verboseHumanBuffer) {
			verboseHumanBuffer.clear();
		}
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

		if (loggingEnabled && verboseEnabled
			&& (logC2S.contains(name) || (verboseOutsideGame && isOutsideGame())))
			verboseDump("C2S", packet);

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

		if (loggingEnabled) {
			if (verboseEnabled && (logS2C.contains(name)
				|| (verboseOutsideGame && isOutsideGame())))
				verboseDump("S2C", packet);
			trackEntityPacket(packet);
		}

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
		if (value) {
			// Surfaces the two common reasons a verbose log looks empty: nothing
			// selected, and verbose switched off.
			UiUtils.chatIfEnabled("Packet logging ON - " + logS2C.size() + " S2C / "
				+ logC2S.size() + " C2S types selected, verbose=" + verboseEnabled
				+ ", logs: " + LOG_DIR);
		}
	}

	public static boolean isVerboseEnabled() {
		return verboseEnabled;
	}

	public static void setVerboseEnabled(boolean value) {
		if (!value)
			discardVerboseBuffers();
		verboseEnabled = value;
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

	public static boolean isVerboseHumanReadable() {
		return verboseHumanReadable;
	}

	public static void setVerboseHumanReadable(boolean value) {
		verboseHumanReadable = value;
		saveSelectionConfig();
	}

	public static boolean isVerboseOutsideGame() {
		return verboseOutsideGame;
	}

	public static void setVerboseOutsideGame(boolean value) {
		verboseOutsideGame = value;
		saveSelectionConfig();
	}

	public static int getVerboseFlushInterval() {
		return verboseFlushInterval;
	}

	public static void setVerboseFlushInterval(int value) {
		verboseFlushInterval = Math.max(1, Math.min(200, value));
		saveSelectionConfig();
	}

	public static PacketDecodeCoverage getDecodeCoverage() {
		return decodeCoverage;
	}

	public static EntityLifecycleTracker getLifecycleTracker() {
		return lifecycleTracker;
	}

	public static PacketFilter getPacketFilter() {
		return packetFilter;
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

	/**
	 * True while the client is connecting or before the world/player exists.
	 */
	private static boolean isOutsideGame() {
		Minecraft mc = Minecraft.getInstance();
		return mc.level == null || mc.player == null;
	}

	private static void verboseDump(String direction, Packet<?> packet) {
		try {
			if (packetDumper == null)
				packetDumper = new PacketDumper(decodeCoverage, packetFilter, lifecycleTracker);
			List<String> lines = packetDumper.dumpRecursive(packet, direction, null);
			if (lines == null || lines.isEmpty())
				return;
			synchronized (verboseJsonlBuffer) {
				verboseJsonlBuffer.addAll(lines);
			}
			if (verboseHumanReadable)
				synchronized (verboseHumanBuffer) {
					for (String line : lines)
						verboseHumanBuffer.add(toHumanReadable(line));
				}
		} catch (Throwable t) {
			UiUtils.LOGGER.warn("Verbose packet dump failed for {}",
				packet.getClass().getSimpleName(), t);
		}
	}

	/** Flattens a dumped JSON line into a readable "Time DIR Class key=value ..." line. */
	private static String toHumanReadable(String jsonLine) {
		try {
			JsonObject obj = JsonParser.parseString(jsonLine).getAsJsonObject();
			StringBuilder sb = new StringBuilder();
			if (obj.has("timestamp"))
				sb.append(obj.get("timestamp").getAsString()).append(' ');
			if (obj.has("direction"))
				sb.append(obj.get("direction").getAsString()).append(' ');
			sb.append(obj.has("simpleName")
				? obj.get("simpleName").getAsString() : "Packet");
			if (obj.has("packetId"))
				sb.append(" [").append(obj.get("packetId").getAsString()).append(']');
			if (obj.has("bundlePath"))
				sb.append(" bundle=").append(obj.get("bundlePath").getAsString());
			if (obj.has("fields") && obj.get("fields").isJsonObject())
				for (var entry : obj.getAsJsonObject("fields").entrySet())
					sb.append(' ').append(entry.getKey()).append('=')
						.append(entry.getValue());
			return sb.toString();
		} catch (Exception e) {
			return jsonLine;
		}
	}

	private static void flushVerboseBuffers() {
		// Defensive: never write verbose output while logging is switched off.
		if (!loggingEnabled) {
			discardVerboseBuffers();
			return;
		}
		try {
			List<String> jsonl;
			synchronized (verboseJsonlBuffer) {
				jsonl = new ArrayList<>(verboseJsonlBuffer);
				verboseJsonlBuffer.clear();
			}
			if (!jsonl.isEmpty()) {
				if (currentVerboseJsonlFile == null)
					currentVerboseJsonlFile = LOG_DIR.resolve("verbose-"
						+ LocalDateTime.now().format(FILE_TIME_FORMAT) + ".jsonl");
				Files.createDirectories(LOG_DIR);
				writeLines(currentVerboseJsonlFile, jsonl);
			}

			if (verboseHumanReadable) {
				List<String> human;
				synchronized (verboseHumanBuffer) {
					human = new ArrayList<>(verboseHumanBuffer);
					verboseHumanBuffer.clear();
				}
				if (!human.isEmpty()) {
					if (currentVerboseHumanFile == null)
						currentVerboseHumanFile = LOG_DIR.resolve("verbose-"
							+ LocalDateTime.now().format(FILE_TIME_FORMAT) + ".log");
					Files.createDirectories(LOG_DIR);
					writeLines(currentVerboseHumanFile, human);
				}
			}
		} catch (IOException e) {
			UiUtils.LOGGER.warn("Failed to flush verbose packet log", e);
		}
	}

	private static void writeLines(Path file, List<String> lines) throws IOException {
		try (Writer writer = Files.newBufferedWriter(file,
			StandardOpenOption.CREATE, StandardOpenOption.APPEND)) {
			for (String line : lines) {
				writer.write(line);
				writer.write("\n");
			}
		}
	}

	private static void trackEntityPacket(Packet<?> packet) {
		try {
			if (packet instanceof ClientboundAddEntityPacket add) {
				lifecycleTracker.onAddEntity(add);
				return;
			}
			if (packet instanceof ClientboundRemoveEntitiesPacket remove) {
				lifecycleTracker.onRemoveEntities(remove);
				return;
			}
			if (packet instanceof ClientboundPlayerInfoUpdatePacket info) {
				lifecycleTracker.onPlayerInfoUpdate(info);
				return;
			}
			String simple = packet.getClass().getSimpleName();
			if (simple.startsWith("Clientbound") && simple.contains("Entity"))
				lifecycleTracker.onEntityPacket(packet, simple);
		} catch (Throwable ignored) {
		}
	}

	public static void printCoverageReport() {
		for (String line : decodeCoverage.buildReport().split("\n"))
			UiUtils.chatIfEnabled(line);
	}

	public static void printEntitySummary() {
		UiUtils.chatIfEnabled(lifecycleTracker.buildSummary());
	}

	public static synchronized void saveSelectionConfig() {
		JsonObject root = new JsonObject();
		root.addProperty("loggingEnabled", loggingEnabled);
		root.addProperty("denyEnabled", denyEnabled);
		root.addProperty("delayEnabled", delayEnabled);
		root.addProperty("fileOutput", fileOutput);
		root.addProperty("showUnknownPackets", showUnknownPackets);
		root.addProperty("delayTicks", delayTicks);
		root.addProperty("verboseEnabled", verboseEnabled);
		root.addProperty("verboseHumanReadable", verboseHumanReadable);
		root.addProperty("verboseOutsideGame", verboseOutsideGame);
		root.addProperty("verboseFlushInterval", verboseFlushInterval);
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
		if (!Files.exists(CONFIG_FILE)) {
			// First run: monitor every packet type by default.
			seedLogSelection();
			return;
		}

		try {
			JsonObject root = JsonParser.parseReader(Files.newBufferedReader(CONFIG_FILE)).getAsJsonObject();
			loggingEnabled = getBoolean(root, "loggingEnabled", false);
			denyEnabled = getBoolean(root, "denyEnabled", false);
			delayEnabled = getBoolean(root, "delayEnabled", false);
			fileOutput = getBoolean(root, "fileOutput", false);
			showUnknownPackets = getBoolean(root, "showUnknownPackets", false);
			delayTicks = Math.max(0, Math.min(9999, getInt(root, "delayTicks", 5)));
			verboseEnabled = getBoolean(root, "verboseEnabled", true);
			verboseHumanReadable = getBoolean(root, "verboseHumanReadable", false);
			verboseOutsideGame = getBoolean(root, "verboseOutsideGame", true);
			verboseFlushInterval = Math.max(1, Math.min(200, getInt(root, "verboseFlushInterval", 20)));
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

	/**
	 * Defaults the Log selection to every known packet type. Deny and Delay are
	 * deliberately left empty - defaulting those to "all" would block or hold
	 * every packet.
	 */
	private static void seedLogSelection() {
		logS2C.addAll(PacketCatalog.getS2CNames());
		logC2S.addAll(PacketCatalog.getC2SNames());
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
