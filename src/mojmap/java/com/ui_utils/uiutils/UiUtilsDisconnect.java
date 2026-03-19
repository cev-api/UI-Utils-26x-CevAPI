package com.ui_utils.uiutils;

import it.unimi.dsi.fastutil.ints.Int2ObjectArrayMap;
import java.time.Instant;
import java.util.Locale;
import java.util.Random;
import net.minecraft.client.Minecraft;
import net.minecraft.network.HashedStack;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.ServerboundClientInformationPacket;
import net.minecraft.network.protocol.common.ServerboundKeepAlivePacket;
import net.minecraft.network.protocol.game.ServerboundChatPacket;
import net.minecraft.network.protocol.game.ServerboundContainerClickPacket;
import net.minecraft.network.protocol.game.ServerboundInteractPacket;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket;
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket;
import net.minecraft.network.protocol.game.ServerboundSwingPacket;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.phys.Vec3;

public final class UiUtilsDisconnect {
	private static volatile boolean timeoutWaitingForKeepAlive;
	private static volatile boolean timeoutBlockingKeepAlive;
	private static volatile boolean timeoutCountdownStarted;

	private UiUtilsDisconnect() {}

	public enum Method {
		QUIT,
		TIMEOUT,
		HURT,
		CHARS,
		CLIENTSETTINGS,
		MOVE_NAN,
		MOVE_INF,
		MOVE_OOB,
		CLICK_INVALID,
		INVALID_SLOT,
		LAG_SWING,
		LAG_DIG,
		LAG_SLOT
	}

	public enum LagMethod {
		BOAT_NBT,
		CLICKSLOT,
		INVENTORY_SPAM,
		CHAT_FLOOD,
		SWING_SPAM,
		DIG_SPAM,
		SLOT_SPAM
	}

	public static Method getConfiguredMethod() {
		String raw = UiUtilsSettings.get().disconnectMethod;
		if(raw == null || raw.isBlank())
			return Method.QUIT;
		try {
			return Method.valueOf(raw.toUpperCase(Locale.ROOT));
		} catch(Exception ignored) {
			return Method.QUIT;
		}
	}

	public static void setConfiguredMethod(Method method) {
		UiUtilsSettings.get().disconnectMethod = method.name();
		UiUtilsSettings.save();
	}

	public static LagMethod getConfiguredLagMethod() {
		String raw = UiUtilsSettings.get().disconnectLagMethod;
		if(raw == null || raw.isBlank())
			return LagMethod.SLOT_SPAM;
		try {
			return LagMethod.valueOf(raw.toUpperCase(Locale.ROOT));
		} catch(Exception ignored) {
			return LagMethod.SLOT_SPAM;
		}
	}

	public static void setConfiguredLagMethod(LagMethod method) {
		UiUtilsSettings.get().disconnectLagMethod = method.name();
		UiUtilsSettings.save();
	}

	public static int getConfiguredTimeoutSeconds() {
		return Math.max(1, UiUtilsSettings.get().disconnectTimeoutSeconds);
	}

	public static void setConfiguredTimeoutSeconds(int seconds) {
		UiUtilsSettings.get().disconnectTimeoutSeconds = Math.max(1, seconds);
		UiUtilsSettings.save();
	}

	public static void disconnectWithConfiguredMethod(Minecraft mc) {
		execute(mc, getConfiguredMethod());
	}

	public static void onClientTick(Minecraft mc) {
		if(mc == null || mc.getConnection() == null) {
			timeoutWaitingForKeepAlive = false;
			timeoutBlockingKeepAlive = false;
			timeoutCountdownStarted = false;
		}
	}

	public static boolean shouldCancelOutgoing(Packet<?> packet) {
		if(!(packet instanceof ServerboundKeepAlivePacket))
			return false;

		if(timeoutWaitingForKeepAlive) {
			timeoutWaitingForKeepAlive = false;
			timeoutBlockingKeepAlive = true;
			if(!timeoutCountdownStarted) {
				timeoutCountdownStarted = true;
				int delayMs = getConfiguredTimeoutSeconds() * 1000;
				UiUtils.queueTask(UiUtilsDisconnect::runTimeoutAction, delayMs);
			}
			return true;
		}

		return timeoutBlockingKeepAlive;
	}

	public static void execute(Minecraft mc, Method method) {
		if(mc == null || mc.getConnection() == null)
			return;
		if(method != Method.TIMEOUT) {
			timeoutWaitingForKeepAlive = false;
			timeoutBlockingKeepAlive = false;
			timeoutCountdownStarted = false;
		}

		switch(method) {
			case QUIT -> mc.getConnection().getConnection()
				.disconnect(Component.literal("Disconnecting (UI-UTILS)"));
			case TIMEOUT -> startTimeoutMode();
			case HURT -> {
				if(mc.player != null)
					mc.getConnection().send(new ServerboundInteractPacket(
						mc.player.getId(), InteractionHand.MAIN_HAND, Vec3.ZERO,
						mc.player.isShiftKeyDown()));
			}
			case CHARS -> mc.getConnection().sendChat("\u00a7");
			case CLIENTSETTINGS -> {
				var info = mc.options.buildPlayerInformation();
				mc.getConnection()
					.send(new ServerboundClientInformationPacket(info));
			}
			case MOVE_NAN -> {
				if(mc.player != null)
					mc.getConnection().send(new ServerboundMovePlayerPacket.PosRot(
						Double.NaN, Double.NaN, Double.NaN, Float.NaN, Float.NaN,
						mc.player.onGround(), false));
			}
			case MOVE_INF -> {
				if(mc.player != null)
					mc.getConnection().send(new ServerboundMovePlayerPacket.PosRot(
						Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY,
						Double.POSITIVE_INFINITY, Float.POSITIVE_INFINITY,
						Float.NEGATIVE_INFINITY, mc.player.onGround(), false));
			}
			case MOVE_OOB -> {
				if(mc.player != null)
					mc.getConnection().send(new ServerboundMovePlayerPacket.PosRot(
						1.0E308, 1.0E308, -1.0E308, 0.0F, 0.0F,
						mc.player.onGround(), false));
			}
			case CLICK_INVALID -> {
				ServerboundContainerClickPacket packet =
					new ServerboundContainerClickPacket(-1, Integer.MAX_VALUE,
						(short)Short.MAX_VALUE, (byte)127, ContainerInput.PICKUP,
						new Int2ObjectArrayMap<>(), HashedStack.EMPTY);
				mc.getConnection().send(packet);
			}
			case INVALID_SLOT ->
				mc.getConnection().send(new ServerboundSetCarriedItemPacket(-1));
			case LAG_SWING -> sendSwingSpam(mc, getLagPacketCount());
			case LAG_DIG -> sendDigSpam(mc, getLagPacketCount());
			case LAG_SLOT -> sendSlotSpam(mc, getLagPacketCount());
		}
	}

	private static void startTimeoutMode() {
		timeoutWaitingForKeepAlive = true;
		timeoutBlockingKeepAlive = false;
		timeoutCountdownStarted = false;
		UiUtils.chatIfEnabled("Timeout mode armed: waiting for KeepAlive.");
	}

	private static void runTimeoutAction() {
		if(!timeoutBlockingKeepAlive)
			return;
		Minecraft mc = Minecraft.getInstance();
		if(mc == null || mc.getConnection() == null)
			return;

		// "use bundle" step from BundleDupe logic: trigger main-hand use.
		if(mc.player != null && mc.gameMode != null)
			mc.gameMode.useItem(mc.player, InteractionHand.MAIN_HAND);

		executeLagMethod(mc, getConfiguredLagMethod(), getLagPacketCount());
		UiUtils.chatIfEnabled("Timeout action executed; keep-alives blocked.");
	}

	private static int getLagPacketCount() {
		return MthClamp.clamp(UiUtilsSettings.get().disconnectLagPackets, 25,
			2000);
	}

	private static void executeLagMethod(Minecraft mc, LagMethod lagMethod,
		int count) {
		switch(lagMethod) {
			case BOAT_NBT, INVENTORY_SPAM -> sendInventorySpam(mc, count);
			case CLICKSLOT -> sendClickslotPackets(mc, count);
			case CHAT_FLOOD -> sendChatFlood(mc, count);
			case SWING_SPAM -> sendSwingSpam(mc, count);
			case DIG_SPAM -> sendDigSpam(mc, count);
			case SLOT_SPAM -> sendSlotSpam(mc, count);
		}
	}

	private static void sendInventorySpam(Minecraft mc, int count) {
		if(mc.getConnection() == null || mc.player == null)
			return;
		for(int i = 0; i < count; i++)
			mc.getConnection().send(new ServerboundPlayerCommandPacket(mc.player,
				ServerboundPlayerCommandPacket.Action.OPEN_INVENTORY));
	}

	private static void sendClickslotPackets(Minecraft mc, int count) {
		if(mc.getConnection() == null)
			return;
		for(int i = 0; i < count; i++) {
			ServerboundContainerClickPacket packet =
				new ServerboundContainerClickPacket(0, 0, (short)0, (byte)0,
					ContainerInput.PICKUP, new Int2ObjectArrayMap<>(),
					HashedStack.EMPTY);
			mc.getConnection().send(packet);
		}
	}

	private static void sendChatFlood(Minecraft mc, int count) {
		if(mc.getConnection() == null)
			return;
		String junk = "a".repeat(200);
		for(int i = 0; i < count; i++) {
			mc.getConnection().send(new ServerboundChatPacket(junk, Instant.now(),
				0L, null, null));
		}
	}

	private static void sendSwingSpam(Minecraft mc, int count) {
		if(mc.getConnection() == null)
			return;
		for(int i = 0; i < count; i++)
			mc.getConnection()
				.send(new ServerboundSwingPacket(InteractionHand.MAIN_HAND));
	}

	private static void sendDigSpam(Minecraft mc, int count) {
		if(mc.getConnection() == null || mc.player == null)
			return;
		BlockPos pos = mc.player.blockPosition();
		for(int i = 0; i < count; i++)
			mc.getConnection().send(new ServerboundPlayerActionPacket(
				ServerboundPlayerActionPacket.Action.START_DESTROY_BLOCK, pos,
				Direction.UP));
	}

	private static void sendSlotSpam(Minecraft mc, int count) {
		if(mc.getConnection() == null)
			return;
		Random random = new Random();
		for(int i = 0; i < count; i++)
			mc.getConnection()
				.send(new ServerboundSetCarriedItemPacket(random.nextInt(9)));
	}

	private static final class MthClamp {
		private static int clamp(int value, int min, int max) {
			return Math.max(min, Math.min(max, value));
		}
	}
}
