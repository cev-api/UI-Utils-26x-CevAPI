package com.ui_utils.uiutils;

import java.util.ArrayDeque;
import java.util.Queue;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.ShulkerBoxMenu;
import net.minecraft.world.inventory.Slot;

/**
 * Queued Steal / Store / Dump transfers for container screens.
 * <p>
 * Steal moves the container into the player inventory, Store moves the player
 * inventory into the container, and Dump throws the container contents on the
 * ground. Clicks are spaced by the configured delay instead of being fired as
 * one burst, which servers reject or throttle.
 */
public final class UiUtilsContainerTransfer {
	/** Fallback container size when the menu type does not say: the last 36. */
	private static final int PLAYER_SLOTS = 36;

	private static final Queue<Integer> PENDING = new ArrayDeque<>();
	private static AbstractContainerScreen<?> targetScreen;
	private static ContainerInput input = ContainerInput.QUICK_MOVE;
	private static long nextClickAt;
	private static boolean movedItems;
	private static String label = "";

	private UiUtilsContainerTransfer() {
	}

	public static boolean isBusy() {
		return targetScreen != null;
	}

	public static String status() {
		return isBusy() ? label + ": " + PENDING.size() + " left" : "idle";
	}

	public static int delayMs() {
		return Math.max(0, UiUtilsSettings.get().stealStoreDumpDelayMs);
	}

	public static void adjustDelay(int delta) {
		UiUtilsSettings.get().stealStoreDumpDelayMs =
			Math.max(0, Math.min(1000, delayMs() + delta));
		UiUtilsSettings.save();
	}

	public static void steal(Minecraft mc) {
		start(mc, true, false);
	}

	public static void store(Minecraft mc) {
		start(mc, false, false);
	}

	public static void dump(Minecraft mc) {
		start(mc, true, true);
	}

	public static void cancel() {
		PENDING.clear();
		targetScreen = null;
	}

	private static void start(Minecraft mc, boolean steal, boolean dump) {
		cancel();
		if (mc == null || mc.player == null || mc.gameMode == null)
			return;

		String action = dump ? "dump" : steal ? "steal" : "store";
		Screen screen = McCompat.getScreen(mc);
		if (!(screen instanceof AbstractContainerScreen<?> container)) {
			UiUtils.chatIfEnabled("Open a container first to " + action);
			return;
		}
		if (mc.player.containerMenu != container.getMenu()) {
			UiUtils.chatIfEnabled("That container is no longer open");
			return;
		}
		if (!container.getMenu().getCarried().isEmpty()) {
			UiUtils.chatIfEnabled("Put down the item on your cursor first");
			return;
		}

		AbstractContainerMenu menu = container.getMenu();
		int containerSlots = containerSlots(menu);
		if (containerSlots <= 0) {
			UiUtils.chatIfEnabled("There is nothing to " + action + " here");
			return;
		}

		int total = menu.slots.size();
		int from = steal ? 0 : containerSlots;
		int to = steal ? containerSlots : total;
		for (int i = from; i < to && i < total; i++) {
			if (menu.slots.get(i).hasItem())
				PENDING.add(i);
		}
		if (PENDING.isEmpty()) {
			UiUtils.chatIfEnabled("No items to " + action);
			return;
		}

		targetScreen = container;
		input = dump ? ContainerInput.THROW : ContainerInput.QUICK_MOVE;
		movedItems = false;
		label = dump ? "Dumping" : steal ? "Stealing" : "Storing";
		nextClickAt = 0L;
		UiUtils.chatIfEnabled(label + " " + PENDING.size() + " stack(s)...");
		onClientTick(mc);
	}

	public static void onClientTick(Minecraft mc) {
		if (targetScreen == null)
			return;

		if (mc == null || mc.player == null || mc.gameMode == null
			|| McCompat.getScreen(mc) != targetScreen
			|| mc.player.containerMenu != targetScreen.getMenu()
			|| !targetScreen.getMenu().getCarried().isEmpty()) {
			cancel();
			return;
		}

		long now = System.currentTimeMillis();
		while (!PENDING.isEmpty() && now >= nextClickAt) {
			int index = PENDING.remove();
			AbstractContainerMenu menu = targetScreen.getMenu();
			if (index >= menu.slots.size())
				continue;
			Slot slot = menu.slots.get(index);
			if (!slot.hasItem())
				continue;
			int before = slot.getItem().getCount();
			// button 0 = one item for PICKUP style clicks, 1 = whole stack for THROW
			mc.gameMode.handleContainerInput(menu.containerId, index,
				input == ContainerInput.THROW ? 1 : 0, input, mc.player);
			movedItems |= slot.getItem().getCount() < before;
			nextClickAt = System.currentTimeMillis() + delayMs();
			now = System.currentTimeMillis();
		}

		if (PENDING.isEmpty()) {
			if (movedItems)
				UiUtils.chatIfEnabled(label + " finished");
			else
				UiUtils.chatIfEnabled(label
					+ " finished: nothing moved (the destination may be full or reject these items)");
			cancel();
		}
	}

	/** Container side slot count, falling back to the standard 36-slot layout. */
	private static int containerSlots(AbstractContainerMenu menu) {
		if (menu instanceof ChestMenu chest)
			return chest.getRowCount() * 9;
		if (menu instanceof ShulkerBoxMenu)
			return 27;
		return Math.max(0, menu.slots.size() - PLAYER_SLOTS);
	}
}
