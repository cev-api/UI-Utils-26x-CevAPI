package com.ui_utils.uiutils;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.ClientboundDisconnectPacket;
import net.minecraft.network.protocol.common.ClientboundKeepAlivePacket;
import net.minecraft.network.protocol.common.ClientboundPingPacket;
import net.minecraft.network.protocol.game.ClientboundLoginPacket;
import net.minecraft.network.protocol.login.ClientboundLoginDisconnectPacket;

public final class UiUtilsAntiCheatDetector {
	private static final int REQUIRED_TRANSACTIONS = 5;
	private static final Object LOCK = new Object();

	private static final List<Integer> transactions = new ArrayList<>();
	private static boolean announcedThisJoin;
	private static String lastServerAddress;

	private UiUtilsAntiCheatDetector() {}

	public static void onIncomingPacket(Packet<?> packet) {
		if(packet == null)
			return;

		String simpleName = packet.getClass().getSimpleName();
		boolean isLogin = packet instanceof ClientboundLoginPacket
			|| isLoginPacket(simpleName);
		boolean isDisconnect = packet instanceof ClientboundDisconnectPacket
			|| packet instanceof ClientboundLoginDisconnectPacket
			|| isDisconnectPacket(simpleName);
		boolean isPing = packet instanceof ClientboundKeepAlivePacket
			|| packet instanceof ClientboundPingPacket || isPingPacket(simpleName);

		if(isLogin) {
			synchronized(LOCK) {
				transactions.clear();
				announcedThisJoin = false;
				lastServerAddress = getCurrentServerAddress();
			}
			return;
		}

		if(isDisconnect) {
			reset();
			return;
		}

		if(!isPing)
			return;

		Integer id = extractPingId(packet);
		if(id == null)
			return;

		String detected = null;
		synchronized(LOCK) {
			if(announcedThisJoin)
				return;

			transactions.add(id);
			if(transactions.size() >= REQUIRED_TRANSACTIONS) {
				detected = guessAntiCheatLocked(getCurrentOrLastServerAddressLocked());
				announcedThisJoin = true;
			}
		}

		if(detected != null)
			announceDetection(detected);
	}

	private static void announceDetection(String antiCheat) {
		if(!UiUtilsSettings.get().antiCheatDetectorEnabled)
			return;
		UiUtils.chatIfEnabled("AntiCheat: " + antiCheat);
	}

	private static void reset() {
		synchronized(LOCK) {
			transactions.clear();
			announcedThisJoin = false;
		}
	}

	private static Integer extractPingId(Packet<?> packet) {
		if(packet instanceof ClientboundKeepAlivePacket keepAlive)
			return asInt(keepAlive.getId());
		if(packet instanceof ClientboundPingPacket ping)
			return ping.getId();
		return tryInvokeInt(packet, "parameter", "getParameter", "id",
			"getId", "pingId", "getPingId", "keepAliveId",
			"getKeepAliveId");
	}

	private static String getCurrentOrLastServerAddressLocked() {
		String current = getCurrentServerAddress();
		if(current != null) {
			lastServerAddress = current;
			return current;
		}
		return lastServerAddress;
	}

	private static String getCurrentServerAddress() {
		Minecraft mc = Minecraft.getInstance();
		if(mc == null)
			return null;
		ServerData server = mc.getCurrentServer();
		return server == null ? null : server.ip;
	}

	private static String guessAntiCheatLocked(String address) {
		if(transactions.size() < REQUIRED_TRANSACTIONS)
			return null;

		List<Integer> snapshot = new ArrayList<>(transactions);
		int first = snapshot.get(0);
		List<Integer> diffs = new ArrayList<>();
		for(int i = 1; i < snapshot.size(); i++)
			diffs.add(snapshot.get(i) - snapshot.get(i - 1));

		boolean allSame = diffs.stream().allMatch(d -> d.equals(diffs.get(0)));

		if(address != null
			&& address.toLowerCase(Locale.ROOT).endsWith("hypixel.net"))
			return "Watchdog";

		if(allSame) {
			int diff = diffs.get(0);
			if(diff == 1) {
				if(first >= -23772 && first <= -23762)
					return "Vulcan";
				if((first >= 95 && first <= 105)
					|| (first >= -20005 && first <= -19995))
					return "Matrix";
				if(first >= -32773 && first <= -32762)
					return "Grizzly";
				return "Verus";
			}

			if(diff == -1) {
				if(first >= -8287 && first <= -8280)
					return "Errata";
				if(first < -3000)
					return "Intave";
				if(first >= -5 && first <= 0)
					return "Grim";
				if(first >= -3000 && first <= -2995)
					return "Karhu";
				return "Polar";
			}
		}

		boolean twoEqual = snapshot.get(0).equals(snapshot.get(1));
		boolean restIncremental = true;
		for(int i = 2; i < snapshot.size(); i++)
			if(snapshot.get(i) - snapshot.get(i - 1) != 1) {
				restIncremental = false;
				break;
			}
		if(twoEqual && restIncremental)
			return "Verus";

		if(diffs.size() >= 3) {
			boolean polarPattern = diffs.get(0) >= 100 && diffs.get(1) == -1;
			for(int i = 2; i < diffs.size() && polarPattern; i++)
				if(diffs.get(i) != -1)
					polarPattern = false;

			if(polarPattern)
				return "Polar";
		}

		if(first < -3000 && snapshot.contains(0))
			return "Intave";

		if(snapshot.size() >= 5 && snapshot.get(0) == -30767
			&& snapshot.get(1) == -30766 && snapshot.get(2) == -25767) {
			boolean oldVulcan = true;
			for(int i = 3; i < snapshot.size() && oldVulcan; i++)
				if(snapshot.get(i) - snapshot.get(i - 1) != 1)
					oldVulcan = false;

			if(oldVulcan)
				return "Old Vulcan";
		}

		return "Unknown";
	}

	private static boolean isLoginPacket(String simpleName) {
		return "ClientboundLoginPacket".equals(simpleName)
			|| "ClientboundGameJoinPacket".equals(simpleName);
	}

	private static boolean isDisconnectPacket(String simpleName) {
		return "ClientboundDisconnectPacket".equals(simpleName)
			|| "ClientboundLoginDisconnectPacket".equals(simpleName);
	}

	private static boolean isPingPacket(String simpleName) {
		if(simpleName == null || simpleName.isEmpty())
			return false;
		return simpleName.contains("Ping") || simpleName.contains("Pong")
			|| simpleName.contains("KeepAlive");
	}

	private static Integer tryInvokeInt(Object target, String... methodNames) {
		for(String methodName : methodNames) {
			try {
				Method method = target.getClass().getMethod(methodName);
				Object result = method.invoke(target);
				Integer i = asInt(result);
				if(i != null)
					return i;
			} catch(Exception ignored) {}
		}

		try {
			for(java.lang.reflect.Field field : target.getClass()
				.getDeclaredFields()) {
				Class<?> type = field.getType();
				if(type != int.class && type != Integer.class
					&& type != long.class && type != Long.class)
					continue;

				field.setAccessible(true);
				Object result = field.get(target);
				Integer i = asInt(result);
				if(i != null)
					return i;
			}
		} catch(Exception ignored) {}

		return null;
	}

	private static Integer asInt(Object result) {
		if(result instanceof Integer i)
			return i;
		if(result instanceof Long l
			&& l >= Integer.MIN_VALUE && l <= Integer.MAX_VALUE)
			return (int)(long)l;
		return null;
	}
}
