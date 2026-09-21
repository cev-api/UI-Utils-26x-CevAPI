package com.ui_utils.uiutils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.network.HashedStack;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundContainerClosePacket;
import net.minecraft.network.protocol.game.ClientboundContainerSetContentPacket;
import net.minecraft.network.protocol.game.ClientboundContainerSetDataPacket;
import net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket;
import net.minecraft.network.protocol.game.ClientboundOpenScreenPacket;
import net.minecraft.network.protocol.game.ClientboundSetCursorItemPacket;
import net.minecraft.network.protocol.game.ServerboundContainerButtonClickPacket;
import net.minecraft.network.protocol.game.ServerboundContainerClickPacket;
import net.minecraft.network.protocol.game.ServerboundContainerClosePacket;
import net.minecraft.network.protocol.game.ServerboundSetCreativeModeSlotPacket;
import net.minecraft.world.item.ItemStack;

/**
 * Focused logger for container (GUI) traffic only. Every packet is normalized
 * to one row shape so slot clicks, window updates and open/close events can be
 * compared side by side while debugging plugin GUIs.
 * <p>
 * Records can arrive on the netty thread, so captured rows are kept in a
 * synchronized ring buffer and file writes are performed from {@link #flush()}
 * on the client thread.
 */
public final class UiUtilsGuiPacketLog {
	public static final String HEADER = "timestamp | direction | packet | syncId | "
		+ "revision | slot | button | action | cursor/item";

	private static final Object LOCK = new Object();
	private static final DateTimeFormatter TIME =
		DateTimeFormatter.ofPattern("HH:mm:ss.SSS");
	private static final int MAX_LINES = 600;

	private static final Deque<String> LINES = new ArrayDeque<>();
	private static final Queue<String> PENDING_FILE_LINES =
		new ConcurrentLinkedQueue<>();

	private static Path logFile;
	private static boolean headerPending;

	private UiUtilsGuiPacketLog() {
	}

	public record Row(String time, String direction, String packet, String syncId,
		String revision, String slot, String button, String action, String item) {
		public String format() {
			return time + " | " + direction + " | " + packet + " | " + syncId + " | "
				+ revision + " | " + slot + " | " + button + " | " + action + " | "
				+ item;
		}
	}

	public static boolean isEnabled() {
		return UiUtilsSettings.get().guiPacketLogEnabled;
	}

	public static void setEnabled(boolean enabled) {
		UiUtilsSettings.get().guiPacketLogEnabled = enabled;
		UiUtilsSettings.save();
		if (!enabled)
			closeFile();
	}

	public static boolean isFileLoggingEnabled() {
		return UiUtilsSettings.get().guiPacketLogToFile;
	}

	public static void setFileLoggingEnabled(boolean enabled) {
		UiUtilsSettings.get().guiPacketLogToFile = enabled;
		UiUtilsSettings.save();
		if (!enabled)
			closeFile();
	}

	public static ArrayList<String> lines() {
		synchronized (LOCK) {
			return new ArrayList<>(LINES);
		}
	}

	public static int size() {
		synchronized (LOCK) {
			return LINES.size();
		}
	}

	public static void clear() {
		synchronized (LOCK) {
			LINES.clear();
		}
		PENDING_FILE_LINES.clear();
		closeFile();
	}

	public static String asText() {
		StringBuilder builder = new StringBuilder(HEADER).append('\n');
		for (String line : lines())
			builder.append(line).append('\n');
		return builder.toString();
	}

	public static Path currentFile() {
		return logFile;
	}

	public static void recordOutgoing(Packet<?> packet) {
		if (!isEnabled() || packet == null)
			return;
		Row row = describe("OUT", packet);
		if (row != null)
			record(row);
	}

	public static void recordIncoming(Packet<?> packet) {
		if (!isEnabled() || packet == null)
			return;
		Row row = describe("IN", packet);
		if (row != null)
			record(row);
	}

	/** Writes buffered rows to disk. Called from the client tick. */
	public static void flush() {
		if (!isFileLoggingEnabled()) {
			PENDING_FILE_LINES.clear();
			return;
		}
		if (PENDING_FILE_LINES.isEmpty())
			return;
		StringBuilder batch = new StringBuilder();
		if (headerPending)
			batch.append(HEADER).append('\n');
		headerPending = false;
		String line;
		while ((line = PENDING_FILE_LINES.poll()) != null)
			batch.append(line).append('\n');
		try {
			Files.writeString(file(), batch.toString(), StandardCharsets.UTF_8,
				StandardOpenOption.CREATE, StandardOpenOption.APPEND);
		} catch (IOException ignored) {
		}
	}

	private static Row describe(String direction, Packet<?> packet) {
		String time = LocalTime.now().format(TIME);
		String name = packet.getClass().getSimpleName();

		if (packet instanceof ServerboundContainerClickPacket click)
			return new Row(time, direction, name, String.valueOf(click.containerId()),
				String.valueOf(click.stateId()), String.valueOf(click.slotNum()),
				String.valueOf(click.buttonNum()),
				click.containerInput() == null ? "-" : click.containerInput().name(),
				hashed(click.carriedItem()));

		if (packet instanceof ServerboundContainerButtonClickPacket button)
			return new Row(time, direction, name, String.valueOf(button.containerId()),
				"-", "-", String.valueOf(button.buttonId()), "BUTTON", "-");

		if (packet instanceof ServerboundContainerClosePacket close)
			return new Row(time, direction, name,
				String.valueOf(close.getContainerId()), "-", "-", "-", "CLOSE",
				"-");

		if (packet instanceof ServerboundSetCreativeModeSlotPacket creative)
			return new Row(time, direction, name, "-", "-",
				String.valueOf(creative.slotNum()), "-", "CREATIVE",
				stack(creative.itemStack()));

		if (packet instanceof ClientboundContainerSetSlotPacket slot)
			return new Row(time, direction, name,
				String.valueOf(slot.getContainerId()),
				String.valueOf(slot.getStateId()), String.valueOf(slot.getSlot()),
				"-", "SET_SLOT", stack(slot.getItem()));

		if (packet instanceof ClientboundContainerSetContentPacket content)
			return new Row(time, direction, name,
				String.valueOf(content.containerId()),
				String.valueOf(content.stateId()),
				String.valueOf(content.items().size()), "-", "SET_CONTENT",
				stack(content.carriedItem()));

		if (packet instanceof ClientboundContainerSetDataPacket data)
			return new Row(time, direction, name,
				String.valueOf(data.getContainerId()), "-", "-",
				String.valueOf(data.getValue()), "SET_DATA",
				"property=" + data.getId());

		if (packet instanceof ClientboundContainerClosePacket close)
			return new Row(time, direction, name,
				String.valueOf(close.getContainerId()), "-", "-", "-", "CLOSE",
				"-");

		if (packet instanceof ClientboundOpenScreenPacket open)
			return new Row(time, direction, name,
				String.valueOf(open.getContainerId()), "-", "-", "-", "OPEN",
				open.getTitle() == null ? "-" : open.getTitle().getString());

		if (packet instanceof ClientboundSetCursorItemPacket cursor)
			return new Row(time, direction, name, "-", "-", "-", "-", "CURSOR",
				stack(cursor.contents()));

		return null;
	}

	private static String hashed(HashedStack hashed) {
		return hashed == null ? "-" : hashed.toString();
	}

	private static String stack(ItemStack stack) {
		if (stack == null || stack.isEmpty())
			return "empty";
		return stack.getItem().toString() + " x" + stack.getCount();
	}

	private static void record(Row row) {
		String line = row.format();
		synchronized (LOCK) {
			LINES.addLast(line);
			while (LINES.size() > MAX_LINES)
				LINES.removeFirst();
		}
		if (isFileLoggingEnabled())
			PENDING_FILE_LINES.add(line);
	}

	private static Path file() {
		if (logFile == null) {
			Path dir = FabricLoader.getInstance().getConfigDir()
				.resolve("packet-logger");
			try {
				Files.createDirectories(dir);
			} catch (IOException ignored) {
			}
			logFile = dir.resolve("gui-"
				+ LocalDateTime.now()
					.format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"))
				+ ".log");
			headerPending = true;
		}
		return logFile;
	}

	private static void closeFile() {
		logFile = null;
	}
}
