/*
 * Copyright (c) 2014-2026 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package com.ui_utils.uiutils;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ConcurrentLinkedQueue;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import net.fabricmc.loader.api.FabricLoader;

/**
 * Refreshes a separate DupeDB cache without replacing bundled or user plugin
 * data.
 */
public final class UiUtilsDupeDbUpdater {
	private static final String SEARCH_URL = "https://dupedb.net/api/exploits/search";
	private static final int PAGE_SIZE = 100;
	private static final int MAX_PAGES = 10_000;
	private static final int MAX_RATE_LIMIT_RETRIES = 4;
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final AtomicBoolean STARTED = new AtomicBoolean();
	private static final AtomicBoolean RUNNING = new AtomicBoolean();
	private static final AtomicBoolean CANCELLED = new AtomicBoolean();
	private static final Object UPDATE_LOCK = new Object();
	private static volatile Thread worker;
	private static volatile boolean finished = true;
	private static volatile String status = "Not refreshed";
	private static final ConcurrentLinkedQueue<String> CHAT_MESSAGES =
		new ConcurrentLinkedQueue<>();
	private static final ExecutorService EXECUTOR = Executors
		.newSingleThreadExecutor(task -> {
			Thread thread = new Thread(task, "UI-Utils DupeDB refresh");
			thread.setDaemon(true);
			return thread;
		});

	private UiUtilsDupeDbUpdater() {}

	/** Starts one automatic refresh per launch once a key is configured. */
	public static void startOnce(String apiKey) {
		if (!UiUtilsSettings.get().dupeDbEnabled
			|| !UiUtilsSettings.get().dupeDbScanOnLaunch || apiKey == null
			|| apiKey.isBlank() || !STARTED.compareAndSet(false, true))
			return;
		refreshNow(apiKey);
	}

	public static String getStatus()
	{
		return status;
	}

	/**
	 * Called on the client thread; launch results wait until a world is joined.
	 */
	public static void onClientTick(Minecraft mc)
	{
		if(mc == null || mc.player == null)
			return;
		String message;
		while((message = CHAT_MESSAGES.poll()) != null)
			mc.player
				.sendSystemMessage(Component.literal("[DupeDB] " + message));
	}

	private static void announce(String message)
	{
		CHAT_MESSAGES.add(message);
	}

	public static boolean isActive()
	{
		return RUNNING.get() && !finished;
	}

	public static void toggleRefresh()
	{
		if(isActive()) stopScan();
		else refreshNow(UiUtilsSettings.get().dupeDbApiKey);
	}

	public static void stopScan()
	{
		synchronized(UPDATE_LOCK)
		{
			if(!isActive()) return;
			CANCELLED.set(true);
			status = "Stopping DupeDB update...";
			if(worker != null) worker.interrupt();
		}
	}

	private static void checkCancelled() throws InterruptedException
	{
		if(CANCELLED.get() || Thread.currentThread().isInterrupted())
			throw new InterruptedException("DupeDB update stopped");
	}

	/** Manual refresh also works after entering a key during this launch. */
	public static void refreshNow(String apiKey)
	{
		synchronized(UPDATE_LOCK)
		{
			if(RUNNING.get()) return;
			if(!UiUtilsSettings.get().dupeDbEnabled)
			{
				status = "DupeDB is disabled";
				announce(status);
				return;
			}
			String token = apiKey == null ? "" : apiKey.trim();
			if(!token.matches("dupe_pat_[a-fA-F0-9]{64}"))
			{
				status = "Enter a valid DupeDB API key";
				announce(status);
				return;
			}
			CANCELLED.set(false);
			finished = false;
			RUNNING.set(true);
			status = "Refreshing DupeDB...";
			announce("Checking database for updates...");
		EXECUTOR.execute(() -> refresh(token));
	}
	}

	private static void refresh(String apiKey) {
		worker = Thread.currentThread();
		try {
			checkCancelled();
			List<PluginVersion> versions = fetchAll(apiKey);
			if (versions.isEmpty())
				throw new IOException("DupeDB returned no plugin/version pairs");
			synchronized(UPDATE_LOCK)
			{
				checkCancelled();
				if(!UiUtilsSettings.get().dupeDbEnabled)
				{
					status = "DupeDB is disabled; update cancelled";
					announce(status);
					return;
				}
				String summary = updateSummary(versions);
				writeAndReload(versions);
				status = summary;
				announce(summary);
				finished = true;
			}
			UiUtils.LOGGER.info("Updated vulnerable plugin data from DupeDB ({} entries).",
				versions.size());
		} catch (Exception e) {
			if(CANCELLED.get() || e instanceof InterruptedException)
				status = "DupeDB update stopped; database unchanged.";
			else
			{
				String message = e.getMessage();
				status = message != null && message.matches("DupeDB returned HTTP [0-9]{3}")
					? message + "; keeping current database"
					: "DupeDB refresh failed; keeping current database";
				UiUtils.LOGGER.warn("{} ({})", status,
				e.getClass().getSimpleName());
		}
			announce(status);
		}finally
		{
			synchronized(UPDATE_LOCK)
			{
				worker = null;
				finished = true;
				Thread.interrupted();
				RUNNING.set(false);
			}
		}
	}

	/**
	 * Compare normalized names and versions against the currently usable
	 * database.
	 */
	private static String updateSummary(List<PluginVersion> versions)
	{
		var existing = UiUtilsVulnerablePlugins.entriesByKey();
		Set<String> knownPairs = new LinkedHashSet<>();
		for(var entry : existing.entrySet())
			for(String version : entry.getValue().versions())
				knownPairs.add(entry.getKey() + "\u0000"
					+ version.trim().toLowerCase(Locale.ROOT));
		Set<String> addedPlugins = new LinkedHashSet<>();
		int addedVersions = 0;
		for(PluginVersion version : versions)
		{
			String key = UiUtilsVulnerablePlugins.normalizeKey(version.name());
			if(knownPairs.add(key + "\u0000"
				+ version.version().trim().toLowerCase(Locale.ROOT)))
			{
				addedVersions++;
				if(!existing.containsKey(key))
					addedPlugins.add(key);
			}
		}
		if(addedVersions == 0)
			return "Database checked: no new plugin/version entries ("
				+ versions.size() + " checked).";
		return "Database checked: added " + addedVersions
			+ " new plugin/version entries (" + addedPlugins.size()
			+ " new plugins; " + versions.size() + " checked).";
	}

	private static List<PluginVersion> fetchAll(String apiKey) throws Exception {
		HttpClient client = HttpClient.newBuilder()
			.connectTimeout(Duration.ofSeconds(15)).build();
		Set<PluginVersion> found = new LinkedHashSet<>();
		for (int page = 1; page <= MAX_PAGES; page++) {
			checkCancelled();
			String url = SEARCH_URL + "?q=&type=dupe&page=" + page
				+ "&limit=" + PAGE_SIZE
				+ "&sort=date_submitted&order=desc";
			JsonObject data = requestJson(client, URI.create(url), apiKey);
			JsonElement exploitElement = data.get("exploits");
			if (exploitElement == null || !exploitElement.isJsonArray())
				throw new IOException("Unexpected DupeDB response: exploits is not a list");
			JsonArray exploits = exploitElement.getAsJsonArray();
			for (JsonElement exploit : exploits)
				if (exploit != null && exploit.isJsonObject())
					extractExploit(exploit.getAsJsonObject(), found);

			JsonElement paginationElement = data.get("pagination");
			boolean hasMore = paginationElement != null
				&& paginationElement.isJsonObject()
				&& paginationElement.getAsJsonObject().has("hasMore")
				&& paginationElement.getAsJsonObject().get("hasMore").getAsBoolean();
			if (!hasMore || exploits.isEmpty())
				break;
			if (page == MAX_PAGES)
				throw new IOException("DupeDB pagination exceeded the page limit");
		}

		List<PluginVersion> sorted = new ArrayList<>(found);
		sorted.sort(Comparator.comparing(PluginVersion::name,
			String.CASE_INSENSITIVE_ORDER).thenComparing(PluginVersion::version,
				String.CASE_INSENSITIVE_ORDER));
		return sorted;
	}

	private static JsonObject requestJson(HttpClient client, URI uri, String apiKey)
		throws Exception {
		for (int retry = 0; ; retry++) {
			checkCancelled();
			HttpRequest request = HttpRequest.newBuilder(uri)
				.timeout(Duration.ofSeconds(30))
				.header("Authorization", "Bearer " + apiKey)
				.header("Accept", "application/json")
				.header("User-Agent", "UI-Utils-DupeDB-Updater/1.0")
				.GET().build();
			HttpResponse<String> response = client.send(request,
				HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
			if (response.statusCode() == 429 && retry < MAX_RATE_LIMIT_RETRIES) {
				long waitSeconds = 5;
				try {
					waitSeconds = Long.parseLong(response.headers()
						.firstValue("Retry-After").orElse("5"));
				} catch (NumberFormatException ignored) {}
				Thread.sleep(Math.max(1, Math.min(60, waitSeconds)) * 1000L);
				continue;
			}
			if (response.statusCode() < 200 || response.statusCode() >= 300)
				throw new IOException("DupeDB returned HTTP " + response.statusCode());
			JsonElement parsed;
			try {
				parsed = JsonParser.parseString(response.body());
			} catch (RuntimeException e) {
				throw new IOException("DupeDB returned invalid JSON", e);
			}
			if (parsed == null || !parsed.isJsonObject())
				throw new IOException("Unexpected DupeDB response: root is not an object");
			return parsed.getAsJsonObject();
		}
	}

	private static void extractExploit(JsonObject exploit,
		Set<PluginVersion> found) {
		addPair(first(exploit, "plugin_name"), first(exploit, "plugin_version"),
			found);
		JsonElement plugins = exploit.get("plugins");
		if (plugins != null && plugins.isJsonPrimitive()
			&& plugins.getAsJsonPrimitive().isString()) {
			try {
				plugins = JsonParser.parseString(plugins.getAsString());
			} catch (RuntimeException ignored) {
				return;
			}
		}
		if (plugins == null || plugins.isJsonNull())
			return;
		if (plugins.isJsonArray()) {
			for (JsonElement plugin : plugins.getAsJsonArray())
				extractPlugin(plugin, found);
		} else if (plugins.isJsonObject()) {
			JsonObject object = plugins.getAsJsonObject();
			extractPlugin(object, found);
			for (JsonElement value : object.asMap().values())
				if (value != null && value.isJsonArray())
					for (JsonElement plugin : value.getAsJsonArray())
						extractPlugin(plugin, found);
		}
	}

	private static void extractPlugin(JsonElement element,
		Set<PluginVersion> found) {
		if (element == null || !element.isJsonObject())
			return;
		JsonObject plugin = element.getAsJsonObject();
		addPair(first(plugin, "name", "plugin", "plugin_name", "pluginName"),
			first(plugin, "version", "plugin_version", "pluginVersion"), found);
	}

	private static String first(JsonObject object, String... keys) {
		for (String key : keys) {
			JsonElement value = object.get(key);
			if (value != null && value.isJsonPrimitive()) {
				String text = value.getAsString().trim();
				if (!text.isEmpty())
					return text;
			}
		}
		return null;
	}

	private static void addPair(String name, String version,
		Set<PluginVersion> found) {
		if (name != null && version != null && !name.isBlank() && !version.isBlank())
			found.add(new PluginVersion(name.trim(), version.trim()));
	}

	private static void writeAndReload(List<PluginVersion> versions)
		throws IOException {
		Path target = FabricLoader.getInstance().getConfigDir()
			.resolve("ui-utils-dupedb-plugins.json");
		Files.createDirectories(target.getParent());
		Path temporary = Files.createTempFile(target.getParent(),
			target.getFileName().toString(), ".tmp");
		JsonArray json = new JsonArray();
		for (PluginVersion version : versions) {
			JsonObject entry = new JsonObject();
			entry.addProperty("name", version.name());
			entry.addProperty("version", version.version());
			json.add(entry);
		}
		try {
			Files.writeString(temporary, GSON.toJson(json), StandardCharsets.UTF_8);
			try {
				Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE,
					StandardCopyOption.REPLACE_EXISTING);
			} catch (AtomicMoveNotSupportedException e) {
				Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
			}
		} finally {
			Files.deleteIfExists(temporary);
		}
		UiUtilsVulnerablePlugins.reload();
	}

	private record PluginVersion(String name, String version) {}
}
