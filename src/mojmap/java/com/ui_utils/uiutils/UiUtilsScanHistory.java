package com.ui_utils.uiutils;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;

/** Persists one latest snapshot per scan type and annotates it against the prior result. */
public final class UiUtilsScanHistory {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final String DIRECTORY = "ui-utils-scan-history";

	private UiUtilsScanHistory() {}

	public static String serverKey(Minecraft mc) {
		if (mc == null) return "singleplayer";
		try {
			if (mc.getCurrentServer() != null && mc.getCurrentServer().ip != null)
				return mc.getCurrentServer().ip.trim().toLowerCase(Locale.ROOT);
		} catch (Throwable ignored) {}
		try {
			if (mc.getConnection() != null && mc.getConnection().getConnection() != null
				&& mc.getConnection().getConnection().getRemoteAddress() != null)
				return mc.getConnection().getConnection().getRemoteAddress().toString();
		} catch (Throwable ignored) {}
		return "singleplayer";
	}

	public static void recordPlugins(String serverKey, String scanType,
		List<UiUtilsPluginScanner.PluginResultRow> rows) {
		Map<String, Entry> current = new LinkedHashMap<>();
		for (UiUtilsPluginScanner.PluginResultRow row : rows) {
			if (row == null || row.plugin() == null || row.plugin().isBlank())
				continue;
			current.put(normalize(row.plugin()), new Entry(row.plugin(), row.evidence(),
				new ArrayList<>(row.commands())));
		}
		record(serverKey, scanType, current);
	}

	// ### ADDED ### Compact verbose fingerprint history; no raw packet or NBT data is persisted.
	public static void recordVerboseFingerprint(String serverKey,
		UiUtilsServerFingerprintCollector.Snapshot snapshot) {
		Map<String, Entry> current = new LinkedHashMap<>();
		for (UiUtilsServerFingerprintCollector.KnownPackInfo pack : snapshot.knownPacks())
			current.put("pack:" + normalize(pack.namespace() + ":" + pack.id()),
				new Entry("Known Pack " + pack.namespace() + ":" + pack.id() + " " + pack.version(),
					"KNOWN_PACK", List.of()));
		for (UiUtilsServerFingerprintCollector.ChannelInfo channel : snapshot.payloads())
			current.put("channel:" + normalize(channel.id()),
				new Entry("Server channel " + channel.id(), "SERVER_CUSTOM_PAYLOAD", List.of()));
		for (String dimension : snapshot.dimensions())
			current.put("dimension:" + normalize(dimension), new Entry("Dimension " + dimension, "DIMENSION", List.of()));
		for (UiUtilsServerFingerprintCollector.RegistryInfo registry : snapshot.registries())
			for (UiUtilsServerFingerprintCollector.RegistryEntryInfo entry : registry.entries())
				current.put("registry:" + normalize(registry.registry() + "/" + entry.id()),
					new Entry("Registry " + registry.registry() + " " + entry.id(), "CUSTOM_REGISTRY", List.of()));
		if (!snapshot.brand().isBlank())
			current.put("brand", new Entry("Platform / Brand " + snapshot.brand(), "SERVER_BRAND", List.of()));
		for (UiUtilsPluginScanner.PluginResultRow row : UiUtilsPluginScanner.getResultsSnapshot())
			current.put("software:" + normalize(row.plugin()), new Entry("Software " + row.plugin(),
				"PLUGIN_" + row.evidence(), new ArrayList<>(row.commands())));
		for (String advancement : snapshot.advancements())
			current.put("advancement:" + normalize(advancement), new Entry("Advancement " + advancement,
				"ADVANCEMENT_NAMESPACE", List.of()));
		for (String objective : snapshot.objectives())
			current.put("objective:" + normalize(objective), new Entry("Scoreboard objective " + objective,
				"SCOREBOARD_SIGNATURE", List.of()));
		for (String tab : snapshot.tabText())
			current.put("tab:" + normalize(tab), new Entry("Tab text " + tab, "TAB_TEXT_HINT", List.of()));
		for (Map.Entry<String, String> config : snapshot.serverConfig().entrySet())
			current.put("config:" + normalize(config.getKey()), new Entry("Configuration " + config.getKey()
				+ " = " + config.getValue(), "SERVER_CONFIGURATION", List.of()));
		if (snapshot.chatCompletionCount() > 0)
			current.put("chat-completions", new Entry("Chat completions total=" + snapshot.chatCompletionCount()
				+ ", emoji=" + snapshot.emojiCompletionCount() + ", formatting/action="
				+ snapshot.formattingCompletionCount(), "CHAT_COMPLETION_METADATA", List.of()));
		record(serverKey, "verbose_server", current);
	}
	public static void recordCommands(String serverKey, String scanType, List<String> commands) {
		Map<String, Entry> current = new LinkedHashMap<>();
		for (String command : commands) {
			if (command == null || command.isBlank())
				continue;
			String name = command.trim();
			current.put(normalize(name), new Entry(name, null, List.of()));
		}
		record(serverKey, scanType, current);
	}

	private static synchronized void record(String serverKey, String scanType,
		Map<String, Entry> current) {
		String resolvedServerKey = serverKey == null || serverKey.isBlank() ? "singleplayer" : serverKey;
		Path path = historyPath(resolvedServerKey);
		JsonObject root = read(path);
		JsonArray history = root.has("scans") && root.get("scans").isJsonArray()
			? root.getAsJsonArray("scans") : new JsonArray();
		Map<String, Entry> previous = previousEntries(history, scanType);
		Set<String> allKeys = new LinkedHashSet<>(previous.keySet());
		allKeys.addAll(current.keySet());

		// ### MODIFIED ### Keep one bounded result per scan type instead of appending every scan.
		JsonArray retained = new JsonArray();
		for (JsonElement element : history) {
			if (!element.isJsonObject() || !scanType.equals(getString(element.getAsJsonObject(), "type")))
				retained.add(element);
		}

		JsonArray snapshotEntries = new JsonArray();
		List<String> orderedKeys = new ArrayList<>(allKeys);
		Collections.sort(orderedKeys);
		for (String key : orderedKeys) {
			Entry entry = current.get(key);
			Entry old = previous.get(key);
			JsonObject item = new JsonObject();
			item.addProperty("key", key);
			if (entry != null) {
				item.addProperty("name", entry.name());
				if (entry.evidence() != null)
					item.addProperty("evidence", entry.evidence());
				JsonArray commands = new JsonArray();
				for (String command : entry.commands()) commands.add(command);
				item.add("commands", commands);
			} else {
				item.addProperty("name", old.name());
				if (old.evidence() != null) item.addProperty("evidence", old.evidence());
			}
			String status = entry == null ? "removed" : old == null ? "added"
				: entry.equals(old) ? "unchanged" : "changed";
			item.addProperty("change", status);
			snapshotEntries.add(item);
		}

		JsonObject snapshot = new JsonObject();
		snapshot.addProperty("timestamp", Instant.now().toString());
		snapshot.addProperty("type", scanType);
		snapshot.add("entries", snapshotEntries);
		retained.add(snapshot);
		root.addProperty("server", resolvedServerKey);
		root.add("scans", retained);
		try {
			Files.createDirectories(path.getParent());
			Files.writeString(path, GSON.toJson(root), StandardCharsets.UTF_8);
		} catch (IOException e) {
			UiUtils.LOGGER.warn("Failed to save {} scan history to {}", scanType, path, e);
		}
	}

	private static Map<String, Entry> previousEntries(JsonArray history, String scanType) {
		for (int i = history.size() - 1; i >= 0; i--) {
			JsonElement element = history.get(i);
			if (!element.isJsonObject() || !scanType.equals(getString(element.getAsJsonObject(), "type")))
				continue;
			JsonArray entries = element.getAsJsonObject().has("entries")
				? element.getAsJsonObject().getAsJsonArray("entries") : new JsonArray();
			Map<String, Entry> result = new LinkedHashMap<>();
			for (JsonElement item : entries) {
				if (!item.isJsonObject()) continue;
				JsonObject object = item.getAsJsonObject();
				String name = getString(object, "name");
				if (name == null || name.isBlank()) continue;
				List<String> commands = new ArrayList<>();
				if (object.has("commands") && object.get("commands").isJsonArray())
					for (JsonElement command : object.getAsJsonArray("commands")) commands.add(command.getAsString());
				String storedKey = getString(object, "key");
				result.put(storedKey == null || storedKey.isBlank() ? normalize(name) : storedKey,
					new Entry(name, getString(object, "evidence"), commands));
			}
			return result;
		}
		return Map.of();
	}

	private static JsonObject read(Path path) {
		if (!Files.exists(path)) return new JsonObject();
		try {
			JsonElement parsed = JsonParser.parseString(Files.readString(path, StandardCharsets.UTF_8));
			return parsed != null && parsed.isJsonObject() ? parsed.getAsJsonObject() : new JsonObject();
		} catch (Exception e) {
			UiUtils.LOGGER.warn("Failed to read scan history from {}", path, e);
			return new JsonObject();
		}
	}

	private static Path historyPath(String serverKey) {
		String safe = serverKey.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9._-]+", "_");
		if (safe.isBlank()) safe = "server";
		return FabricLoader.getInstance().getConfigDir().resolve(DIRECTORY)
			.resolve(safe + "-" + Integer.toHexString(serverKey.hashCode()) + ".json");
	}

	private static String normalize(String value) { return value.trim().toLowerCase(Locale.ROOT); }
	private static String getString(JsonObject object, String key) {
		return object.has(key) && object.get(key).isJsonPrimitive() ? object.get(key).getAsString() : null;
	}

	private record Entry(String name, String evidence, List<String> commands) {}
}