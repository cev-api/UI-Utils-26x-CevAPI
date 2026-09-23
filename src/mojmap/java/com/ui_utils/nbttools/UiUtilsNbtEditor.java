/*
 * Copyright (c) 2014-2026 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package com.ui_utils.nbttools;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Stream;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.NumericTag;
import net.minecraft.nbt.SnbtPrinterTagVisitor;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.nbt.TagParser;
import net.minecraft.network.protocol.game.ServerboundSetCreativeModeSlotPacket;
import net.minecraft.resources.RegistryOps;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import com.ui_utils.uiutils.McCompat;
import com.ui_utils.uiutils.UiUtils;

/**
 * Reads, edits and applies item, block and entity NBT.
 * <p>
 * State lives in static fields so the editor screen and the main UI panel can
 * share one editing session. Nothing here is Wurst-specific: the target toggles
 * and the world-editing switch are driven from the UI rather than a settings
 * system.
 */
public final class UiUtilsNbtEditor {
	private static final String EMPTY_ITEM =
		"{id:\"minecraft:cod\",count:1,components:{}}";
	private static final List<String> BUILT_IN_PRESET_NAMES = List.of(
		"Charged Creeper Wand", "Fireball Wand", "Speed Hack Rod", "TNT Dropper",
		"Wither Wand", "Wurst7-CevAPI Legit OP Kit", "Wurst7-CevAPI OP Kit");
	/** NBT keys that /data merge cannot change, so they stay hidden. */
	private static final String[] POSITION_AND_IDENTITY_KEYS =
		{"id", "UUID", "Pos", "Motion", "Rotation", "PortalCooldown"};
	private static final Path PRESET_FOLDER =
		FabricLoader.getInstance().getConfigDir().resolve("ui-utils-nbt-presets");

	private static String editorText = formatNbt(EMPTY_ITEM);
	private static String lastEditorMessage = "";
	private static ReadFrom readFrom = ReadFrom.HELD_ITEM;
	private static boolean worldEdits;
	private static BlockPos readBlock;
	private static Entity readEntity;
	private static String containerTitle = "";

	private UiUtilsNbtEditor() {
	}

	/** Where the editor loads its NBT from. */
	public enum ReadFrom {
		HELD_ITEM("Held item", "Held"),
		BLOCK("Looked-at block", "Block"),
		ENTITY("Looked-at entity", "Entity"),
		CONTAINER("Open container", "Chest");

		private final String label;
		private final String shortLabel;

		ReadFrom(String label, String shortLabel) {
			this.label = label;
			this.shortLabel = shortLabel;
		}

		public String shortLabel() {
			return shortLabel;
		}

		@Override
		public String toString() {
			return label;
		}
	}

	/** Whether an actual container GUI (not the player inventory) is open. */
	public static boolean isContainerOpen(Minecraft mc) {
		if (mc == null || mc.player == null)
			return false;
		AbstractContainerMenu menu = mc.player.containerMenu;
		return menu != null && menu != mc.player.inventoryMenu;
	}

	/**
	 * Opens the editor. When a container GUI is open its contents become the
	 * editor text, since that is what the container panel's "Open NBT" button
	 * is for.
	 */
	public static void openEditor(Minecraft mc) {
		if (mc == null)
			return;
		if (isContainerOpen(mc))
			readFrom = ReadFrom.CONTAINER;
		else if (readFrom == ReadFrom.CONTAINER)
			readFrom = ReadFrom.HELD_ITEM;
		// Commands arrive from a chat callback, so queue the work on the client
		// executor to avoid the chat screen replacing the editor again.
		mc.execute(() -> {
			if (mc.getConnection() == null || mc.player == null) {
				lastEditorMessage = "No player is available.";
				return;
			}
			editorText = readTarget();
			McCompat.setScreen(mc, new NbtEditorScreen(McCompat.getScreen(mc)));
		});
	}

	// ### Target selection ###

	public static ReadFrom getReadFrom() {
		return readFrom;
	}

	public static void setReadFrom(ReadFrom target) {
		if (target == null || target == readFrom)
			return;
		readFrom = target;
		readBlock = null;
		readEntity = null;
		lastEditorMessage = "Reading from: " + readFrom + ".";
	}

	/** Switches to the next target and returns it. */
	public static ReadFrom cycleReadFrom() {
		ReadFrom[] values = ReadFrom.values();
		setReadFrom(values[(readFrom.ordinal() + 1) % values.length]);
		return readFrom;
	}

	/** Whether Apply sends /data merge at the looked-at block or entity. */
	public static boolean isWorldTarget() {
		return readFrom == ReadFrom.BLOCK || readFrom == ReadFrom.ENTITY;
	}

	/** Container contents can be read and copied, but not written back. */
	public static boolean isReadOnlyTarget() {
		return readFrom == ReadFrom.CONTAINER;
	}

	public static boolean isWorldEditsEnabled() {
		return worldEdits;
	}

	public static void setWorldEdits(boolean enabled) {
		worldEdits = enabled;
	}

	public static boolean toggleWorldEdits() {
		worldEdits = !worldEdits;
		return worldEdits;
	}

	// ### Reading ###

	/** Loads the editor from whichever target the "Read from" setting picks. */
	public static String readTarget() {
		return switch(readFrom) {
			case HELD_ITEM -> readHeldItem();
			case BLOCK -> readTargetBlock();
			case ENTITY -> readTargetEntity();
			case CONTAINER -> readOpenContainer();
		};
	}

	public static String readHeldItem() {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null)
			return editorText;
		DataResult<Tag> encoded = ItemStack.CODEC.encodeStart(
			RegistryOps.create(NbtOps.INSTANCE, mc.player.registryAccess()),
			mc.player.getMainHandItem());
		Tag tag = encoded.result().orElse(null);
		if (tag == null) {
			lastEditorMessage = "Could not serialize held item: " + encoded.error()
				.map(DataResult.Error::message).orElse("unknown error");
			return editorText;
		}
		readBlock = null;
		readEntity = null;
		editorText = formatNbt(new SnbtPrinterTagVisitor().visit(tag));
		lastEditorMessage = "Loaded held item.";
		return editorText;
	}

	/**
	 * Reads the block entity the player is looking at. Only data the server has
	 * synced to this client can be read, so container contents stay empty until
	 * the container has been opened.
	 */
	public static String readTargetBlock() {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null || mc.level == null) {
			lastEditorMessage = "No world is available.";
			return editorText;
		}

		BlockPos pos = resolveBlock();
		if (pos == null) {
			lastEditorMessage = "Look at a block first.";
			return editorText;
		}

		BlockEntity blockEntity = mc.level.getBlockEntity(pos);
		if (blockEntity == null) {
			lastEditorMessage = "That block has no block entity data.";
			return editorText;
		}

		CompoundTag tag;
		try {
			tag = blockEntity.saveWithoutMetadata(mc.level.registryAccess());
		} catch (Exception e) {
			lastEditorMessage =
				"Could not read that block: " + errorMessage(e, "unknown error");
			return editorText;
		}

		readBlock = pos;
		readEntity = null;
		editorText = formatNbt(new SnbtPrinterTagVisitor().visit(tag));
		lastEditorMessage = "Loaded "
			+ mc.level.getBlockState(pos).getBlock().getName().getString() + " at "
			+ pos.getX() + " " + pos.getY() + " " + pos.getZ() + ".";
		return editorText;
	}

	/**
	 * Reads the entity the player is looking at. Position and identity fields
	 * are hidden because /data merge refuses to change them.
	 */
	public static String readTargetEntity() {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null || mc.level == null) {
			lastEditorMessage = "No world is available.";
			return editorText;
		}

		Entity entity = resolveEntity();
		if (entity == null) {
			lastEditorMessage = "Look at an entity first.";
			return editorText;
		}

		CompoundTag tag;
		try {
			TagValueOutput output = TagValueOutput.createWithContext(
				ProblemReporter.DISCARDING, mc.level.registryAccess());
			entity.saveWithoutId(output);
			tag = output.buildResult();
			for (String key : POSITION_AND_IDENTITY_KEYS)
				tag.remove(key);
		} catch (Exception e) {
			lastEditorMessage =
				"Could not read that entity: " + errorMessage(e, "unknown error");
			return editorText;
		}

		readEntity = entity;
		readBlock = null;
		editorText = formatNbt(new SnbtPrinterTagVisitor().visit(tag));
		lastEditorMessage = "Loaded " + entity.getName().getString()
			+ " (position and identity fields are hidden).";
		return editorText;
	}

	/**
	 * Reads the contents of the container GUI the player currently has open.
	 * Only what the server has synced to this client is visible, so this is a
	 * snapshot of the open menu rather than the server-side block entity.
	 */
	public static String readOpenContainer() {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null) {
			lastEditorMessage = "No player is available.";
			return editorText;
		}

		AbstractContainerMenu menu = mc.player.containerMenu;
		if (menu == null || menu == mc.player.inventoryMenu) {
			lastEditorMessage = "No container GUI is open.";
			return editorText;
		}

		// The container's title lives on its screen, not on the menu, so capture
		// it whenever we are still looking at something other than the editor.
		Screen current = McCompat.getScreen(mc);
		if (current != null && !(current instanceof NbtEditorScreen))
			containerTitle = current.getTitle().getString();

		ContainerSnapshot snapshot;
		try {
			snapshot = buildContainerTag(mc, menu);
		} catch (Exception e) {
			lastEditorMessage =
				"Could not read that container: " + errorMessage(e, "unknown error");
			return editorText;
		}

		readBlock = null;
		readEntity = null;
		editorText = formatNbt(new SnbtPrinterTagVisitor().visit(snapshot.tag()));
		lastEditorMessage = "Loaded open container "
			+ (containerTitle.isBlank() ? "" : "\"" + containerTitle + "\" ")
			+ "(" + snapshot.filled() + "/" + snapshot.size() + " slots used).";
		return editorText;
	}

	/**
	 * Builds a compound describing the open menu: its ids, size and the items in
	 * the slots that belong to the container rather than to the player.
	 */
	private static ContainerSnapshot buildContainerTag(Minecraft mc,
		AbstractContainerMenu menu) {
		CompoundTag tag = new CompoundTag();
		tag.putInt("containerId", menu.containerId);
		tag.putInt("stateId", menu.getStateId());
		tag.putString("title", containerTitle == null ? "" : containerTitle);

		// Slots backed by the player's own inventory are not part of the
		// container. If nothing else is found, fall back to every slot so menus
		// built only from player slots still show something.
		List<Integer> containerSlots = new ArrayList<>();
		for (int i = 0; i < menu.slots.size(); i++)
			if (menu.slots.get(i).container != mc.player.getInventory())
				containerSlots.add(i);
		if (containerSlots.isEmpty())
			for (int i = 0; i < menu.slots.size(); i++)
				containerSlots.add(i);

		tag.putInt("containerSize", containerSlots.size());

		ListTag items = new ListTag();
		for (int index : containerSlots) {
			Slot slot = menu.slots.get(index);
			ItemStack stack = slot.getItem();
			if (stack.isEmpty())
				continue;
			CompoundTag entry = new CompoundTag();
			// Index within the open menu, matching the slot the client sees.
			entry.putInt("slot", index);
			Tag encoded = encodeStack(mc, stack);
			if (encoded != null)
				entry.put("item", encoded);
			items.add(entry);
		}
		tag.put("items", items);

		ItemStack carried = menu.getCarried();
		if (!carried.isEmpty()) {
			Tag encoded = encodeStack(mc, carried);
			if (encoded != null)
				tag.put("carried", encoded);
		}
		return new ContainerSnapshot(tag, containerSlots.size(), items.size());
	}

	/** Item stacks use the same encoding as the held-item target. */
	private static Tag encodeStack(Minecraft mc, ItemStack stack) {
		DataResult<Tag> encoded = ItemStack.CODEC.encodeStart(
			RegistryOps.create(NbtOps.INSTANCE, mc.player.registryAccess()), stack);
		return encoded.result().orElse(null);
	}

	private record ContainerSnapshot(CompoundTag tag, int size, int filled) {
	}

	/**
	 * Prefers the target captured by the last read so that Apply still hits the
	 * same block or entity after the crosshair has moved.
	 */
	private static BlockPos resolveBlock() {
		if (readBlock != null)
			return readBlock;
		Minecraft mc = Minecraft.getInstance();
		return mc.hitResult instanceof BlockHitResult hit ? hit.getBlockPos() : null;
	}

	private static Entity resolveEntity() {
		Minecraft mc = Minecraft.getInstance();
		if (readEntity != null && !readEntity.isRemoved())
			return readEntity;

		// Vanilla only picks entities that report isPickable(), which leaves out
		// dropped items and other entities with NBT worth reading.
		if (mc.hitResult instanceof EntityHitResult hit)
			return hit.getEntity();

		EntityHitResult ownHit = raycastEntity();
		return ownHit == null ? null : ownHit.getEntity();
	}

	/** Own raycast that accepts any entity, pickable or not. */
	private static EntityHitResult raycastEntity() {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null || mc.level == null)
			return null;

		Player player = mc.player;
		double reach = Math.max(player.blockInteractionRange(),
			player.entityInteractionRange());
		Vec3 eyes = player.getEyePosition(1F);
		Vec3 look = player.getViewVector(1F);
		Vec3 end = eyes.add(look.scale(reach));

		// Stop the ray at the first block so entities can't be read through walls.
		BlockHitResult blockHit = raycastBlock(eyes, end);
		if (blockHit.getType() != HitResult.Type.MISS)
			end = blockHit.getLocation();

		AABB box = player.getBoundingBox().expandTowards(look.scale(reach))
			.inflate(1.0);
		return ProjectileUtil.getEntityHitResult(player, eyes, end, box,
			UiUtilsNbtEditor::isReadableTarget, reach * reach);
	}

	private static BlockHitResult raycastBlock(Vec3 from, Vec3 to) {
		Minecraft mc = Minecraft.getInstance();
		ClipContext context = new ClipContext(from, to, ClipContext.Block.COLLIDER,
			ClipContext.Fluid.NONE, mc.player);
		return mc.level.clip(context);
	}

	private static boolean isReadableTarget(Entity entity) {
		Minecraft mc = Minecraft.getInstance();
		return entity != mc.player && !entity.isSpectator() && entity.isAlive();
	}

	// ### Editor text helpers ###

	public static String getEditorText() {
		return editorText;
	}

	public static void setEditorText(String text) {
		editorText = text;
	}

	public static String getLastEditorMessage() {
		return lastEditorMessage;
	}

	public static String newItem() {
		editorText = formatNbt(EMPTY_ITEM);
		lastEditorMessage = "Created new item.";
		return editorText;
	}

	public static String formatNbt(String text) {
		StringBuilder result = new StringBuilder();
		int indent = 0;
		boolean quoted = false;
		boolean escaped = false;
		for (int i = 0; i < text.length(); i++) {
			char c = text.charAt(i);
			if (quoted) {
				result.append(c);
				if (escaped)
					escaped = false;
				else if (c == '\\')
					escaped = true;
				else if (c == '"')
					quoted = false;
				continue;
			}
			if (c == '"') {
				quoted = true;
				result.append(c);
			} else if (c == '{' || c == '[') {
				result.append(c).append('\n');
				indent++;
				appendIndent(result, indent);
			} else if (c == '}' || c == ']') {
				trimTrailingSpace(result);
				result.append('\n');
				indent = Math.max(0, indent - 1);
				appendIndent(result, indent);
				result.append(c);
			} else if (c == ',') {
				trimTrailingSpace(result);
				result.append(',').append('\n');
				appendIndent(result, indent);
			} else if (c == ':') {
				trimTrailingSpace(result);
				result.append(": ");
			} else if (!Character.isWhitespace(c))
				result.append(c);
		}
		return result.toString();
	}

	private static void appendIndent(StringBuilder result, int indent) {
		for (int i = 0; i < indent; i++)
			result.append("    ");
	}

	private static void trimTrailingSpace(StringBuilder result) {
		while (result.length() > 0 && (result.charAt(result.length() - 1) == ' '
			|| result.charAt(result.length() - 1) == '\n'))
			result.setLength(result.length() - 1);
	}

	/** Removes whitespace outside quoted SNBT strings without changing values. */
	public static String minifyNbt(String text) {
		if (text == null || text.isEmpty())
			return "";
		StringBuilder result = new StringBuilder(text.length());
		boolean quoted = false;
		boolean escaped = false;
		for (int i = 0; i < text.length(); i++) {
			char c = text.charAt(i);
			if (quoted) {
				result.append(c);
				if (escaped)
					escaped = false;
				else if (c == '\\')
					escaped = true;
				else if (c == '"')
					quoted = false;
			} else if (c == '"') {
				quoted = true;
				result.append(c);
			} else if (!Character.isWhitespace(c))
				result.append(c);
		}
		return result.toString();
	}

	// ### Validation and apply ###

	public static String validate(String text) {
		if (readFrom == ReadFrom.CONTAINER)
			return "Open container contents can be copied, not written back.";

		if (readFrom == ReadFrom.HELD_ITEM) {
			ItemDecodeResult result = decodeItem(text);
			return result.success() ? null : result.error();
		}

		if (!worldEdits)
			return "Enable \"World editing (OP)\" to apply to the looked-at target.";

		return validateWorldEdit(text);
	}

	public static boolean apply(String text) {
		return switch(readFrom) {
			case HELD_ITEM -> applyToHeldItem(text);
			case BLOCK -> applyToBlock(text);
			case ENTITY -> applyToEntity(text);
			case CONTAINER -> false;
		};
	}

	private static boolean applyToHeldItem(String text) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null) {
			lastEditorMessage = "No player is available.";
			return false;
		}
		ItemDecodeResult decoded = decodeItem(text);
		if (!decoded.success()) {
			lastEditorMessage = "Invalid item data: " + decoded.error();
			return false;
		}
		try {
			ItemStack stack = decoded.stack();
			if (!mc.player.hasInfiniteMaterials()) {
				lastEditorMessage = "Creative mode is required to apply item NBT.";
				return false;
			}
			editorText = text;
			lastEditorMessage = "Applied item stack.";
			setCreativeStack(mc.player.getInventory().getSelectedSlot(), stack);
			return true;
		} catch (Exception e) {
			lastEditorMessage =
				"Could not apply item data: " + errorMessage(e, "unknown error");
			return false;
		}
	}

	private static boolean applyToBlock(String text) {
		Minecraft mc = Minecraft.getInstance();
		if (!canEditWorld())
			return false;

		BlockPos pos = resolveBlock();
		if (pos == null) {
			lastEditorMessage = "Look at a block first.";
			return false;
		}

		String error = validateWorldEdit(text);
		if (error != null) {
			lastEditorMessage = "Invalid block data: " + error;
			return false;
		}

		editorText = text;
		mc.getConnection().sendCommand("data merge block " + pos.getX() + " "
			+ pos.getY() + " " + pos.getZ() + " " + minifyNbt(text));
		lastEditorMessage = "Sent /data merge block " + pos.getX() + " " + pos.getY()
			+ " " + pos.getZ() + ".";
		return true;
	}

	private static boolean applyToEntity(String text) {
		Minecraft mc = Minecraft.getInstance();
		if (!canEditWorld())
			return false;

		Entity entity = resolveEntity();
		if (entity == null) {
			lastEditorMessage = "Look at an entity first.";
			return false;
		}

		String error = validateWorldEdit(text);
		if (error != null) {
			lastEditorMessage = "Invalid entity data: " + error;
			return false;
		}

		editorText = text;
		mc.getConnection().sendCommand(
			"data merge entity " + entity.getUUID() + " " + minifyNbt(text));
		lastEditorMessage =
			"Sent /data merge entity for " + entity.getName().getString() + ".";
		return true;
	}

	private static boolean canEditWorld() {
		Minecraft mc = Minecraft.getInstance();
		if (!worldEdits) {
			lastEditorMessage = "Enable \"World editing (OP)\" to apply to the world.";
			return false;
		}
		if (mc.getConnection() == null) {
			lastEditorMessage = "Not connected to a server.";
			return false;
		}
		return true;
	}

	/**
	 * Block entities and entities take a bare NBT compound, so item data must not
	 * be merged into them by mistake.
	 */
	private static String validateWorldEdit(String text) {
		CompoundTag tag;
		try {
			tag = parseCompound(text);
		} catch (Exception e) {
			return errorMessage(e, "invalid SNBT");
		}

		if (tag.get("id") instanceof StringTag
			&& tag.get("count") instanceof NumericTag)
			return "this is item data. Switch \"Read from\" to Held item.";

		return null;
	}

	// ### Item decoding ###

	/**
	 * Parses SNBT first, then JSON, and keeps Mojang's codec diagnostic instead
	 * of replacing it with an empty stack, so registry errors inside nested
	 * components survive.
	 */
	private static ItemDecodeResult decodeItem(String text) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null)
			return new ItemDecodeResult(ItemStack.EMPTY, "No player is available.");

		String snbtError;
		try {
			ItemDecodeResult result =
				decodeTag(TagParser.parseCompoundFully(text));
			if (result.success())
				return result;
			snbtError = result.error();
		} catch (Exception e) {
			snbtError = errorMessage(e, "Invalid SNBT");
		}

		try {
			JsonElement json = JsonParser.parseString(text);
			Tag tag = JsonOps.INSTANCE.convertTo(NbtOps.INSTANCE, json);
			if (tag instanceof CompoundTag compound)
				return decodeTag(compound);
			return new ItemDecodeResult(ItemStack.EMPTY,
				"Item data must be an object.");
		} catch (Exception e) {
			return new ItemDecodeResult(ItemStack.EMPTY,
				snbtError + " | JSON: " + errorMessage(e, "invalid JSON"));
		}
	}

	private static ItemDecodeResult decodeTag(Tag tag) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null)
			return new ItemDecodeResult(ItemStack.EMPTY, "No player is available.");
		DataResult<ItemStack> result = ItemStack.CODEC.parse(
			RegistryOps.create(NbtOps.INSTANCE, mc.player.registryAccess()), tag);
		return result.result().map(stack -> new ItemDecodeResult(stack, null))
			.orElseGet(() -> new ItemDecodeResult(ItemStack.EMPTY, result.error()
				.map(DataResult.Error::message)
				.orElse("Minecraft rejected this item-stack representation.")));
	}

	private static CompoundTag parseCompound(String text) throws Exception {
		try {
			return TagParser.parseCompoundFully(text);
		} catch (Exception snbtError) {
			try {
				JsonElement json = JsonParser.parseString(text);
				Tag converted = JsonOps.INSTANCE.convertTo(NbtOps.INSTANCE, json);
				if (converted instanceof CompoundTag compound)
					return compound;
			} catch (Exception ignored) {
			}
			throw snbtError;
		}
	}

	private static String errorMessage(Exception e, String fallback) {
		String message = e.getMessage();
		return message == null || message.isBlank() ? fallback : message;
	}

	private record ItemDecodeResult(ItemStack stack, String error) {
		private boolean success() {
			return stack != null && !stack.isEmpty() && error == null;
		}
	}

	/** Creative slot indices are offset from the container menu's slot numbers. */
	private static int toNetworkSlot(int slot) {
		if (slot >= 0 && slot < 9)
			return slot + 36;
		if (slot >= 36 && slot < 40)
			return 44 - slot;
		if (slot == 40)
			return 45;
		return slot;
	}

	private static boolean setCreativeStack(int slot, ItemStack stack) {
		Minecraft mc = Minecraft.getInstance();
		if (slot < 0 || mc.player == null || !mc.player.hasInfiniteMaterials())
			return false;
		mc.player.getInventory().setItem(slot, stack);
		if (mc.getConnection() != null)
			mc.getConnection().send(new ServerboundSetCreativeModeSlotPacket(
				toNetworkSlot(slot), stack));
		return true;
	}

	// ### Presets ###

	private static String validatePreset(String text) {
		try {
			CompoundTag tag = parseCompound(text);
			if (!(tag.get("id") instanceof StringTag))
				return "missing a string 'id' field";
			if (!(tag.get("count") instanceof NumericTag))
				return "missing a numeric 'count' field";
			return null;
		} catch (Exception e) {
			String message = e.getMessage();
			return message == null || message.isBlank() ? "invalid SNBT" : message;
		}
	}

	public static Collection<String> presetNames() {
		Set<String> names = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
		names.addAll(BUILT_IN_PRESET_NAMES);
		try (Stream<Path> paths = Files.list(PRESET_FOLDER)) {
			paths.filter(path -> path.getFileName().toString().endsWith(".snbt"))
				.map(path -> path.getFileName().toString().substring(0,
					path.getFileName().toString().length() - 5))
				.forEach(names::add);
		} catch (Exception ignored) {
		}
		return List.copyOf(names);
	}

	public static boolean savePreset(String name, String text) {
		if (name == null || name.trim().isEmpty()) {
			lastEditorMessage = "Enter a preset name first.";
			return false;
		}
		String error = validatePreset(text);
		if (error != null) {
			lastEditorMessage = "Cannot save preset: " + error;
			return false;
		}
		try {
			Files.createDirectories(PRESET_FOLDER);
			Files.writeString(PRESET_FOLDER.resolve(presetFileName(name)), text.trim(),
				StandardCharsets.UTF_8);
			lastEditorMessage = "Saved preset: " + name.trim();
			return true;
		} catch (Exception e) {
			lastEditorMessage = "Could not save preset: " + e.getMessage();
			return false;
		}
	}

	public static void deletePreset(String name) {
		try {
			Files.deleteIfExists(PRESET_FOLDER.resolve(presetFileName(name)));
		} catch (Exception e) {
			UiUtils.LOGGER.warn("Could not delete NBT preset {}", name, e);
		}
	}

	public static String loadPreset(String name) {
		String value = name == null ? null : readPreset(name.trim());
		if (value == null) {
			lastEditorMessage = "No saved preset named \"" + name + "\".";
			return null;
		}
		editorText = value;
		lastEditorMessage = "Loaded preset: " + name.trim();
		return value;
	}

	private static String readPreset(String name) {
		try {
			Path saved = PRESET_FOLDER.resolve(presetFileName(name));
			if (Files.isRegularFile(saved))
				return Files.readString(saved, StandardCharsets.UTF_8);
		} catch (Exception ignored) {
		}
		return readBuiltInPreset(name);
	}

	private static String readBuiltInPreset(String name) {
		if (name == null || !BUILT_IN_PRESET_NAMES.contains(name.trim()))
			return null;
		String resource = "/assets/ui_utils/nbt-presets/" + presetFileName(name);
		try (java.io.InputStream input =
			UiUtilsNbtEditor.class.getResourceAsStream(resource)) {
			return input == null ? null
				: new String(input.readAllBytes(), StandardCharsets.UTF_8);
		} catch (Exception ignored) {
			return null;
		}
	}

	private static String presetFileName(String name) {
		String safe = name.trim().replaceAll("[<>:\"/\\\\|?*]", "_");
		return safe + ".snbt";
	}
}
