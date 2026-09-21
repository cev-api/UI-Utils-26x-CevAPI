package com.ui_utils.uiutils;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ServerboundContainerButtonClickPacket;
import net.minecraft.network.protocol.game.ServerboundContainerClickPacket;
import net.minecraft.network.protocol.game.ServerboundContainerClosePacket;
import net.minecraft.network.protocol.game.ServerboundContainerSlotStateChangedPacket;
import net.minecraft.network.protocol.game.ServerboundPlaceRecipePacket;
import net.minecraft.network.protocol.game.ServerboundRecipeBookChangeSettingsPacket;
import net.minecraft.network.protocol.game.ServerboundRecipeBookSeenRecipePacket;
import net.minecraft.network.protocol.game.ServerboundSelectTradePacket;
import net.minecraft.network.protocol.game.ServerboundSetBeaconPacket;
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket;
import net.minecraft.network.protocol.game.ServerboundSetCreativeModeSlotPacket;
import net.minecraft.network.protocol.game.ServerboundSignUpdatePacket;

/**
 * Per-packet Allow / Drop / Delay control for the outgoing container (GUI)
 * packets. Unlike the blanket "Delay Packets" switch this lets a single packet
 * class be held back while everything else keeps flowing.
 */
public final class UiUtilsGuiPacketControl {
	public enum Mode {
		ALLOW("Allow"),
		DROP("Drop"),
		DELAY("Delay");

		private final String label;

		Mode(String label) {
			this.label = label;
		}

		public String label() {
			return label;
		}

		public Mode next() {
			return switch (this) {
				case ALLOW -> DROP;
				case DROP -> DELAY;
				case DELAY -> ALLOW;
			};
		}
	}

	public record Entry(String id, String label,
		Class<? extends Packet<?>> type) {
	}

	private static final List<Entry> ENTRIES = List.of(
		new Entry("click_slot", "Click Slot",
			ServerboundContainerClickPacket.class),
		new Entry("button_click", "Button Click",
			ServerboundContainerButtonClickPacket.class),
		new Entry("close_screen", "Close Screen",
			ServerboundContainerClosePacket.class),
		new Entry("creative_slot", "Creative Slot",
			ServerboundSetCreativeModeSlotPacket.class),
		new Entry("slot_state", "Slot State",
			ServerboundContainerSlotStateChangedPacket.class),
		new Entry("carried_item", "Held Slot",
			ServerboundSetCarriedItemPacket.class),
		new Entry("place_recipe", "Place Recipe",
			ServerboundPlaceRecipePacket.class),
		new Entry("select_trade", "Select Trade",
			ServerboundSelectTradePacket.class),
		new Entry("set_beacon", "Set Beacon",
			ServerboundSetBeaconPacket.class),
		new Entry("sign_update", "Sign Update",
			ServerboundSignUpdatePacket.class),
		new Entry("recipe_book", "Recipe Book",
			ServerboundRecipeBookChangeSettingsPacket.class),
		new Entry("recipe_seen", "Recipe Seen",
			ServerboundRecipeBookSeenRecipePacket.class));

	private static final Map<Class<? extends Packet<?>>, String> ID_BY_TYPE =
		new LinkedHashMap<>();
	private static final Map<String, Class<? extends Packet<?>>> TYPE_BY_ID =
		new LinkedHashMap<>();
	private static final Map<String, Mode> MODES = new LinkedHashMap<>();

	static {
		for (Entry entry : ENTRIES) {
			ID_BY_TYPE.put(entry.type(), entry.id());
			TYPE_BY_ID.put(entry.id(), entry.type());
		}
		reset();
	}

	private UiUtilsGuiPacketControl() {
	}

	/** Restores every entry to ALLOW, keeping the configured delay. */
	public static void reset() {
		MODES.clear();
		for (Entry entry : ENTRIES)
			MODES.put(entry.id(), Mode.ALLOW);
	}

	/** Same as {@link #reset()} but also writes the cleared state to disk. */
	public static void resetAndPersist() {
		reset();
		persist();
	}

	public static List<Entry> entries() {
		return ENTRIES;
	}

	public static Mode mode(String id) {
		return MODES.getOrDefault(id, Mode.ALLOW);
	}

	public static void setMode(String id, Mode mode) {
		MODES.put(id, mode == null ? Mode.ALLOW : mode);
		persist();
	}

	public static Mode cycleMode(String id) {
		Mode next = mode(id).next();
		setMode(id, next);
		return next;
	}

	public static int delayTicks() {
		return Math.max(0, UiUtilsSettings.get().guiPacketDelayTicks);
	}

	public static void adjustDelay(int delta) {
		int value = Math.max(0, Math.min(200, delayTicks() + delta));
		UiUtilsSettings.get().guiPacketDelayTicks = value;
		UiUtilsSettings.save();
	}

	/** True when the packet class is covered by a per-packet rule. */
	public static boolean handles(Packet<?> packet) {
		return packet != null && ID_BY_TYPE.containsKey(packet.getClass());
	}

	public static String describe(Packet<?> packet) {
		if (packet == null)
			return "?";
		String id = ID_BY_TYPE.get(packet.getClass());
		if (id == null)
			return packet.getClass().getSimpleName();
		for (Entry entry : ENTRIES)
			if (entry.id().equals(id))
				return entry.label();
		return id;
	}

	public static boolean shouldDrop(Packet<?> packet) {
		return modeFor(packet) == Mode.DROP;
	}

	public static boolean shouldDelay(Packet<?> packet) {
		return modeFor(packet) == Mode.DELAY;
	}

	/** A compact "Click Slot=Delayed(2), Button Click=Dropped" style summary. */
	public static String summary() {
		StringBuilder builder = new StringBuilder();
		for (Entry entry : ENTRIES) {
			Mode mode = mode(entry.id());
			if (mode == Mode.ALLOW)
				continue;
			if (builder.length() > 0)
				builder.append(", ");
			builder.append(entry.label()).append('=').append(mode.label());
			if (mode == Mode.DELAY)
				builder.append('(').append(delayTicks()).append("t)");
		}
		return builder.length() == 0 ? "all allowed" : builder.toString();
	}

	public static String rulesText() {
		StringBuilder builder = new StringBuilder();
		for (Entry entry : ENTRIES)
			builder.append(entry.label()).append(": ").append(mode(entry.id()).label())
				.append('\n');
		return builder.toString();
	}

	private static Mode modeFor(Packet<?> packet) {
		if (packet == null)
			return Mode.ALLOW;
		// Exact class lookup only: subclasses must opt in explicitly so we never
		// accidentally hold back an unrelated packet that happens to extend one.
		String id = ID_BY_TYPE.get(packet.getClass());
		return id == null ? Mode.ALLOW : mode(id);
	}

	private static void persist() {
		Map<String, String> stored = new LinkedHashMap<>();
		for (Entry entry : ENTRIES)
			stored.put(entry.id(), mode(entry.id()).name().toLowerCase(Locale.ROOT));
		UiUtilsSettings.get().guiPacketModes = stored;
		UiUtilsSettings.save();
	}

	/** Re-applies the persisted modes (called once after settings load). */
	public static void loadFromSettings() {
		Map<String, String> stored = UiUtilsSettings.get().guiPacketModes;
		reset();
		if (stored == null)
			return;
		for (Map.Entry<String, String> entry : stored.entrySet()) {
			if (!TYPE_BY_ID.containsKey(entry.getKey()))
				continue;
			try {
				setModeSilently(entry.getKey(),
					Mode.valueOf(entry.getValue().toUpperCase(Locale.ROOT)));
			} catch (IllegalArgumentException ignored) {
			}
		}
	}

	private static void setModeSilently(String id, Mode mode) {
		MODES.put(id, mode);
	}
}
