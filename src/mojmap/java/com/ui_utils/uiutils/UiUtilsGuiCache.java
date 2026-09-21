package com.ui_utils.uiutils;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.resources.RegistryOps;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * Saved-GUI lifecycle helpers. On top of the plain save/load pair this tracks
 * whether a saved GUI is still the server-backed container or has become
 * clientside-only, and it can snapshot the whole GUI (not just the title).
 */
public final class UiUtilsGuiCache {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	private UiUtilsGuiCache() {
	}

	public record Status(boolean present, String name, int syncId, int revision,
		boolean active) {
		public String label() {
			if (!present)
				return "none";
			return name + " | syncId=" + syncId + " | revision=" + revision + " | "
				+ (active ? "LIVE (server-backed)" : "STALE (clientside-only)");
		}
	}

	/** Saves the currently open GUI without closing it. */
	public static boolean save(Minecraft mc) {
		if (mc == null || mc.player == null)
			return false;
		return UiUtils.saveCurrentGuiToSlot(mc, "default", currentGuiName(mc));
	}

	/** Saves the currently open GUI and closes it without telling the server. */
	public static boolean saveAndClose(Minecraft mc) {
		if (!save(mc))
			return false;
		McCompat.setScreen(mc, null);
		return true;
	}

	public static boolean load(Minecraft mc) {
		return UiUtils.loadGuiFromSlot(mc, "default");
	}

	public static boolean hasSaved() {
		return UiUtilsState.storedScreen != null && UiUtilsState.storedMenu != null;
	}

	public static boolean clear() {
		boolean had = hasSaved() || !UiUtilsState.savedScreens.isEmpty()
			|| !UiUtilsState.savedMenus.isEmpty();
		UiUtilsState.storedScreen = null;
		UiUtilsState.storedMenu = null;
		UiUtilsState.storedGuiName = "";
		UiUtilsState.savedScreens.clear();
		UiUtilsState.savedMenus.clear();
		return had;
	}

	public static Status status(Minecraft mc) {
		if (!hasSaved())
			return new Status(false, "-", -1, -1, false);
		String name = UiUtilsState.storedGuiName == null
			|| UiUtilsState.storedGuiName.isBlank() ? "unnamed"
				: UiUtilsState.storedGuiName;
		int syncId = UiUtilsState.storedMenu.containerId;
		int revision = UiUtilsState.storedMenu.getStateId();
		boolean active = mc != null && mc.player != null
			&& mc.player.containerMenu == UiUtilsState.storedMenu
			&& mc.getConnection() != null;
		return new Status(true, name, syncId, revision, active);
	}

	public static Status currentStatus(Minecraft mc) {
		if (mc == null || mc.player == null || mc.player.containerMenu == null)
			return new Status(false, "-", -1, -1, false);
		AbstractContainerMenu menu = mc.player.containerMenu;
		return new Status(true, currentGuiName(mc), menu.containerId,
			menu.getStateId(), mc.getConnection() != null);
	}

	public static String currentGuiName(Minecraft mc) {
		if (mc == null)
			return "unknown";
		try {
			Screen screen = McCompat.getScreen(mc);
			if (screen != null && screen.getTitle() != null)
				return screen.getTitle().getString();
			if (mc.player != null && mc.player.containerMenu != null)
				return mc.player.containerMenu.getClass().getSimpleName();
		} catch (Throwable ignored) {
		}
		return "unknown";
	}

	/** Pretty JSON snapshot of the whole currently open GUI. */
	public static String buildSnapshotJson(Minecraft mc) {
		JsonObject root = new JsonObject();
		Screen screen = McCompat.getScreen(mc);
		root.addProperty("guiTitle",
			screen == null ? "none" : screen.getTitle().getString());
		root.addProperty("screenClass",
			screen == null ? "none" : screen.getClass().getName());

		Status current = currentStatus(mc);
		root.addProperty("syncId", current.syncId());
		root.addProperty("revision", current.revision());
		root.addProperty("serverBacked", current.active());

		Status saved = status(mc);
		JsonObject savedGui = new JsonObject();
		savedGui.addProperty("present", saved.present());
		savedGui.addProperty("name", saved.name());
		savedGui.addProperty("syncId", saved.syncId());
		savedGui.addProperty("revision", saved.revision());
		savedGui.addProperty("serverBacked", saved.active());
		root.add("savedGui", savedGui);

		if (screen != null) {
			JsonElement title = encode(ComponentSerialization.CODEC,
				screen.getTitle());
			if (title != null)
				root.add("titleComponents", title);
		}

		if (mc == null || mc.player == null || mc.player.containerMenu == null)
			return GSON.toJson(root);

		AbstractContainerMenu menu = mc.player.containerMenu;
		HolderLookup.Provider registries = registryProvider(mc);

		JsonObject cursor = new JsonObject();
		cursor.addProperty("item", itemId(menu.getCarried()));
		cursor.addProperty("count", menu.getCarried().getCount());
		root.add("cursor", cursor);

		JsonArray slots = new JsonArray();
		for (int i = 0; i < menu.slots.size(); i++) {
			ItemStack stack = menu.slots.get(i).getItem();
			if (stack.isEmpty())
				continue;
			JsonObject entry = new JsonObject();
			entry.addProperty("index", i);
			entry.addProperty("item", itemId(stack));
			entry.addProperty("count", stack.getCount());
			JsonElement data = registries == null ? null
				: encodeStack(stack, registries);
			if (data != null)
				entry.add("data", data);
			slots.add(entry);
		}
		root.add("slots", slots);
		root.addProperty("occupiedSlots", slots.size());
		root.addProperty("totalSlots", menu.slots.size());
		return GSON.toJson(root);
	}

	public static String buildTitleJson(Minecraft mc) {
		Screen screen = McCompat.getScreen(mc);
		if (screen == null)
			return null;
		JsonElement encoded = encode(ComponentSerialization.CODEC,
			screen.getTitle());
		return encoded == null ? null : GSON.toJson(encoded);
	}

	public static List<Slot> slots(Minecraft mc) {
		if (mc == null || mc.player == null || mc.player.containerMenu == null)
			return List.of();
		return mc.player.containerMenu.slots;
	}

	private static String itemId(ItemStack stack) {
		if (stack == null || stack.isEmpty())
			return "air";
		try {
			return BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
		} catch (Throwable ignored) {
			return String.valueOf(stack.getItem());
		}
	}

	private static <T> JsonElement encode(Codec<T> codec, T value) {
		try {
			return codec.encodeStart(JsonOps.INSTANCE, value).result().orElse(null);
		} catch (Throwable ignored) {
			return null;
		}
	}

	private static JsonElement encodeStack(ItemStack stack,
		HolderLookup.Provider registries) {
		try {
			return ItemStack.CODEC
				.encodeStart(RegistryOps.create(JsonOps.INSTANCE, registries), stack)
				.result().orElse(null);
		} catch (Throwable ignored) {
			return null;
		}
	}

	private static HolderLookup.Provider registryProvider(Minecraft mc) {
		if (mc == null)
			return null;
		if (mc.level != null)
			return mc.level.registryAccess();
		if (mc.getConnection() != null)
			return mc.getConnection().registryAccess();
		return null;
	}
}
