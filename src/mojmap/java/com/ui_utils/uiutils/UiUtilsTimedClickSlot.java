package com.ui_utils.uiutils;

import it.unimi.dsi.fastutil.ints.Int2ObjectArrayMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import net.minecraft.client.Minecraft;
import net.minecraft.network.HashedPatchMap;
import net.minecraft.network.HashedStack;
import net.minecraft.network.protocol.game.ServerboundContainerClickPacket;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;

/**
 * Repeats one container click on a wall-clock interval. This is the piece the
 * plain "send the packet N times" path cannot do: for race and desync testing
 * the clicks have to land ticks (or fractions of a tick) apart.
 */
public final class UiUtilsTimedClickSlot {
	private static final int MAX_SENDS_PER_TICK = 20;

	private static boolean running;
	private static int slot;
	private static byte button;
	private static ContainerInput action = ContainerInput.PICKUP;
	private static int total;
	private static int remaining;
	private static int intervalMs = 50;
	private static long nextSendAt;

	private UiUtilsTimedClickSlot() {
	}

	public static void start(int slotId, int buttonId, ContainerInput clickAction,
		int count, int intervalMillis) {
		slot = slotId;
		button = (byte)buttonId;
		action = clickAction == null ? ContainerInput.PICKUP : clickAction;
		total = Math.max(1, count);
		remaining = total;
		intervalMs = Math.max(0, intervalMillis);
		nextSendAt = System.currentTimeMillis();
		running = true;
	}

	public static void stop() {
		running = false;
		remaining = 0;
	}

	public static boolean isRunning() {
		return running;
	}

	public static int remaining() {
		return remaining;
	}

	public static int sent() {
		return total - remaining;
	}

	public static int total() {
		return total;
	}

	public static int intervalMs() {
		return intervalMs;
	}

	public static String status() {
		if (!running)
			return "Idle";
		return sent() + "/" + total + " sent, " + intervalMs + " ms apart";
	}

	/** Called every client tick; sends as many due clicks as the clock allows. */
	public static void onClientTick(Minecraft mc) {
		if (!running)
			return;
		if (mc == null || mc.player == null || mc.getConnection() == null) {
			stop();
			return;
		}

		AbstractContainerMenu menu = mc.player.containerMenu;
		if (menu == null || slot < 0 || slot >= menu.slots.size()) {
			UiUtils.chatIfEnabled("Timed click stopped: slot " + slot
				+ " is out of range");
			stop();
			return;
		}

		HashedPatchMap.HashGenerator hashGenerator =
			mc.getConnection().decoratedHashOpsGenenerator();

		long now = System.currentTimeMillis();
		int budget = MAX_SENDS_PER_TICK;
		while (running && remaining > 0 && now >= nextSendAt
			&& budget-- > 0) {
			Int2ObjectMap<HashedStack> changed = new Int2ObjectArrayMap<>();
			ServerboundContainerClickPacket packet =
				new ServerboundContainerClickPacket(menu.containerId,
					menu.getStateId(), (short)slot, button, action, changed,
					HashedStack.create(menu.getCarried(), hashGenerator));
			mc.getConnection().send(packet);
			remaining--;
			nextSendAt += Math.max(1, intervalMs);
			now = System.currentTimeMillis();
		}

		if (remaining <= 0) {
			stop();
			UiUtils.chatIfEnabled("Timed click finished (" + total + "x slot "
				+ slot + ")");
		}
	}
}
