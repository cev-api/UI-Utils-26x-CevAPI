package com.ui_utils.uiutils;

import com.mojang.brigadier.suggestion.Suggestion;
import com.mojang.brigadier.suggestion.Suggestions;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundCommandSuggestionsPacket;
import net.minecraft.network.protocol.game.ServerboundCommandSuggestionPacket;

public final class UiUtilsPluginScanner {
	private static final Set<String> ANTICHEAT_WORDS = Set.of("nocheatplus",
		"negativity", "vulcan", "spartan", "matrix", "grim", "themis", "kauri",
		"godseye", "anticheat", "exploit", "illegal");
	private static final Random RANDOM = new Random();

	private static boolean scanning;
	private static int requestId = -1;
	private static final List<String> foundPlugins = new ArrayList<>();
	private static final Set<String> dedupe = new HashSet<>();

	private UiUtilsPluginScanner() {}

	public static void init() {
		// packet callbacks come from mixin
	}

	public static String startScan() {
		Minecraft mc = Minecraft.getInstance();
		if (mc.getConnection() == null || mc.player == null)
			return "[UI-Utils] Not connected.";
		if (scanning)
			return "[UI-Utils] Plugin scan already in progress.";

		requestId = RANDOM.nextInt(100000);
		mc.getConnection().send(new ServerboundCommandSuggestionPacket(requestId, "ver "));
		foundPlugins.clear();
		dedupe.clear();
		scanning = true;
		return "[UI-Utils] Scanning plugins...";
	}

	public static void onSuggestionsPacket(ClientboundCommandSuggestionsPacket packet) {
		if (!scanning)
			return;
		if (packet.id() != requestId)
			return;

		try {
			Suggestions suggestions = packet.toSuggestions();
			if (suggestions != null) {
				for (Suggestion suggestion : suggestions.getList()) {
					addPluginName(suggestion.getText());
				}
			}
		} catch (Exception e) {
			UiUtils.LOGGER.warn("Failed to parse plugin scan response.", e);
		}

		finishAndPrint();
	}

	private static void addPluginName(String rawText) {
		if (rawText == null)
			return;
		String text = rawText.trim();
		if (text.isEmpty())
			return;
		if (text.startsWith("/"))
			text = text.substring(1);

		String normalized = text.toLowerCase(Locale.ROOT);
		if (!dedupe.add(normalized))
			return;
		foundPlugins.add(text);
	}

	private static void finishAndPrint() {
		scanning = false;
		requestId = -1;

		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null) {
			foundPlugins.clear();
			dedupe.clear();
			return;
		}

		if (foundPlugins.isEmpty()) {
			mc.player.sendSystemMessage(Component.literal("[UI-Utils] No plugins found or blocked."));
			return;
		}

		foundPlugins.sort(String.CASE_INSENSITIVE_ORDER);
		StringBuilder line = new StringBuilder("[UI-Utils] Plugins (")
			.append(foundPlugins.size()).append("): ");
		for (int i = 0; i < foundPlugins.size(); i++) {
			String plugin = foundPlugins.get(i);
			String lower = plugin.toLowerCase(Locale.ROOT);
			boolean flagged = ANTICHEAT_WORDS.stream().anyMatch(lower::contains);
			line.append(flagged ? "!" : "").append(plugin);
			if (i < foundPlugins.size() - 1)
				line.append(", ");
		}
		mc.player.sendSystemMessage(Component.literal(line.toString()));
		foundPlugins.clear();
		dedupe.clear();
	}
}
