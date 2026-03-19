package com.ui_utils.uiutils;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.suggestion.Suggestion;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.tree.CommandNode;
import com.mojang.brigadier.tree.LiteralCommandNode;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.ClientSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundCommandSuggestionsPacket;
import net.minecraft.network.protocol.game.ServerboundCommandSuggestionPacket;

public final class UiUtilsCommandScanner {
	private static final int RESPONSE_TIMEOUT_TICKS = 20;
	private static final int REQUEST_COOLDOWN_TICKS = 2;
	private static final int EXECUTE_COOLDOWN_TICKS = 4;
	private static final char[] LETTERS = "abcdefghijklmnopqrstuvwxyz".toCharArray();

	private static final Set<String> VANILLA_COMMANDS = new HashSet<>(Arrays.asList(
		"advancement", "attribute", "ban", "ban-ip", "banlist", "bossbar", "clear", "clone",
		"damage", "data", "datapack", "debug", "defaultgamemode", "deop", "difficulty", "effect",
		"enchant", "execute", "experience", "fill", "fillbiome", "forceload", "function", "gamemode",
		"gamerule", "give", "help", "item", "jfr", "kick", "kill", "list", "locate", "loot", "me",
		"msg", "op", "pardon", "pardon-ip", "particle", "perf", "place", "playsound", "publish", "random",
		"recipe", "reload", "return", "ride", "save-all", "save-off", "save-on", "say", "schedule",
		"scoreboard", "seed", "setblock", "setidletimeout", "setworldspawn", "spawnpoint", "spectate",
		"spreadplayers", "stop", "stopsound", "summon", "tag", "team", "teammsg", "teleport", "tell",
		"tellraw", "tick", "time", "title", "tm", "tp", "transfer", "trigger", "w", "weather",
		"whitelist", "worldborder", "xp"));

	private static final Set<String> scannedCommands = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
	private static final ArrayDeque<String> commandsToExecute = new ArrayDeque<>();

	private static boolean awaitingResponse;
	private static int waitTicks;
	private static int cooldownTicks;
	private static int letterIndex;
	private static int requestId;
	private static boolean active;
	private static Phase phase = Phase.IDLE;
	private static ScanMode activeMode = ScanMode.PACKET_PROBING;

	private UiUtilsCommandScanner() {}

	public static String startScan() {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null || mc.player.connection == null)
			return "[UI-Utils] Not connected.";
		if (active)
			return "[UI-Utils] Command scanner already running.";

		active = true;
		scannedCommands.clear();
		commandsToExecute.clear();
		awaitingResponse = false;
		waitTicks = 0;
		cooldownTicks = 0;
		letterIndex = 0;
		requestId = 1;
		phase = Phase.SCANNING;
		activeMode = getScanMode();

		if (activeMode == ScanMode.CLIENT_SIDE_ENUMERATION) {
			runClientSideEnumerationScan();
			return "[UI-Utils] Command scanner started (CLIENT_SIDE_ENUMERATION).";
		}

		sendNextRequest();
		return "[UI-Utils] Command scanner started (PACKET_PROBING).";
	}

	public static void onTick() {
		if (!active)
			return;

		if (phase == Phase.EXECUTING) {
			runExecutionStep();
			return;
		}

		if (activeMode != ScanMode.PACKET_PROBING)
			return;

		if (awaitingResponse) {
			waitTicks++;
			if (waitTicks >= RESPONSE_TIMEOUT_TICKS) {
				if (UiUtilsSettings.get().commandScannerDebugProbe)
					print("Probe timeout: /" + LETTERS[letterIndex] + " (id=" + requestId + ")");
				awaitingResponse = false;
				letterIndex++;
				cooldownTicks = REQUEST_COOLDOWN_TICKS;
			}
			return;
		}

		if (cooldownTicks > 0) {
			cooldownTicks--;
			return;
		}

		sendNextRequest();
	}

	public static void onSuggestionsPacket(ClientboundCommandSuggestionsPacket packet) {
		if (!active || phase != Phase.SCANNING || activeMode != ScanMode.PACKET_PROBING)
			return;
		if (!awaitingResponse)
			return;
		if (packet.id() != requestId)
			return;

		Suggestions suggestions;
		try {
			suggestions = packet.toSuggestions();
		} catch (Exception e) {
			UiUtils.LOGGER.warn("Command scanner: failed to parse suggestions.", e);
			suggestions = null;
		}

		int count = suggestions == null ? 0 : suggestions.getList().size();
		if (UiUtilsSettings.get().commandScannerDebugProbe)
			print("Probe response: /" + LETTERS[letterIndex] + " (id=" + requestId + ", suggestions=" + count + ")");

		readSuggestions(suggestions);
		awaitingResponse = false;
		letterIndex++;
		cooldownTicks = REQUEST_COOLDOWN_TICKS;
	}

	public static String sendManualPacketCommands() {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null || mc.player.connection == null)
			return "[UI-Utils] Not connected.";

		String raw = UiUtilsSettings.get().commandScannerPacketCommands;
		if (raw == null || raw.isBlank())
			return "[UI-Utils] Packet commands list is empty.";

		String[] parts = raw.split(",");
		int sent = 0;
		for (String part : parts) {
			String cmd = part.trim();
			if (cmd.isEmpty())
				continue;
			if (cmd.startsWith("/"))
				cmd = cmd.substring(1);
			mc.player.connection.sendCommand(cmd);
			sent++;
		}
		return "[UI-Utils] Sent " + sent + " packet command(s).";
	}

	private static void sendNextRequest() {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null || mc.player.connection == null) {
			finish();
			return;
		}

		if (letterIndex >= LETTERS.length) {
			finishScan();
			return;
		}

		char c = LETTERS[letterIndex];
		String input = "/" + c;
		if (UiUtilsSettings.get().commandScannerDebugProbe)
			print("Probe sent: " + input + " (id=" + requestId + ")");

		mc.player.connection.send(new ServerboundCommandSuggestionPacket(requestId, input));
		awaitingResponse = true;
		waitTicks = 0;
		requestId++;
	}

	private static void readSuggestions(Suggestions suggestions) {
		if (suggestions == null)
			return;
		for (Suggestion suggestion : suggestions.getList()) {
			String command = extractRootCommand(suggestion.getText());
			if (command != null)
				scannedCommands.add(command);
		}
	}

	private static String extractRootCommand(String raw) {
		if (raw == null)
			return null;
		String text = raw.trim();
		if (text.isEmpty())
			return null;
		if (text.startsWith("/"))
			text = text.substring(1);
		int space = text.indexOf(' ');
		if (space >= 0)
			text = text.substring(0, space);
		if (text.isBlank())
			return null;
		return text;
	}

	private static void runClientSideEnumerationScan() {
		Minecraft mc = Minecraft.getInstance();
		ClientPacketListener connection = mc.player.connection;
		CommandDispatcher<ClientSuggestionProvider> dispatcher = connection.getCommands();
		if (dispatcher == null || dispatcher.getRoot() == null) {
			print("No command tree available yet.");
			finish();
			return;
		}

		for (CommandNode<ClientSuggestionProvider> node : dispatcher.getRoot().getChildren()) {
			if (node instanceof LiteralCommandNode<ClientSuggestionProvider>) {
				String name = node.getName();
				if (name != null && !name.isBlank())
					scannedCommands.add(name);
			}
		}

		finishScan();
	}

	private static void finishScan() {
		print("Command scanner found " + scannedCommands.size() + " root commands.");
		if (scannedCommands.isEmpty()) {
			finish();
			return;
		}

		sendChunkedCommandList(new ArrayList<>(scannedCommands));

		if (UiUtilsSettings.get().commandScannerRunFoundCommands) {
			commandsToExecute.clear();
			Set<String> denyTerms = parseDenyTerms(UiUtilsSettings.get().commandScannerDontSendFilter);
			for (String cmd : scannedCommands) {
				String lower = cmd.toLowerCase(Locale.ROOT);
				if (VANILLA_COMMANDS.contains(lower))
					continue;
				boolean blocked = false;
				for (String deny : denyTerms) {
					if (lower.contains(deny)) {
						blocked = true;
						break;
					}
				}
				if (!blocked)
					commandsToExecute.add(cmd);
			}
			print("Executing " + commandsToExecute.size() + " found non-vanilla command(s) via packets.");
			phase = Phase.EXECUTING;
			cooldownTicks = 0;
			return;
		}

		finish();
	}

	private static void runExecutionStep() {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null || mc.player.connection == null) {
			finish();
			return;
		}

		if (cooldownTicks > 0) {
			cooldownTicks--;
			return;
		}

		String cmd = commandsToExecute.poll();
		if (cmd == null) {
			finish();
			print("Command execution pass complete.");
			return;
		}

		mc.player.connection.sendCommand(cmd.startsWith("/") ? cmd.substring(1) : cmd);
		print("Sent packet command: /" + cmd);
		cooldownTicks = EXECUTE_COOLDOWN_TICKS;
	}

	private static Set<String> parseDenyTerms(String raw) {
		Set<String> terms = new HashSet<>();
		if (raw == null || raw.isBlank())
			return terms;
		for (String part : raw.split(",")) {
			String term = part.trim().toLowerCase(Locale.ROOT);
			if (!term.isEmpty())
				terms.add(term);
		}
		return terms;
	}

	private static void sendChunkedCommandList(List<String> commands) {
		StringBuilder line = new StringBuilder("Commands: ");
		for (String cmd : commands) {
			String entry = "/" + cmd;
			if (line.length() + entry.length() + 2 > 230) {
				print(line.toString());
				line = new StringBuilder("Commands: ");
			}
			if (line.length() > 10)
				line.append(", ");
			line.append(entry);
		}
		if (line.length() > 10)
			print(line.toString());
	}

	private static void print(String msg) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player != null)
			mc.player.sendSystemMessage(Component.literal("[UI-Utils] " + msg));
	}

	private static ScanMode getScanMode() {
		String raw = UiUtilsSettings.get().commandScannerMode;
		if (raw == null)
			return ScanMode.PACKET_PROBING;
		try {
			return ScanMode.valueOf(raw.toUpperCase(Locale.ROOT));
		} catch (IllegalArgumentException ignored) {
			return ScanMode.PACKET_PROBING;
		}
	}

	private static void finish() {
		active = false;
		awaitingResponse = false;
		phase = Phase.IDLE;
		cooldownTicks = 0;
		waitTicks = 0;
	}

	public enum ScanMode {
		PACKET_PROBING,
		CLIENT_SIDE_ENUMERATION
	}

	private enum Phase {
		IDLE,
		SCANNING,
		EXECUTING
	}
}
