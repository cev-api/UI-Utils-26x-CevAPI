package com.ui_utils.uiutils;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.URI;
import java.net.URLConnection;
import java.nio.charset.StandardCharsets;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;

/**
 * Checks the GitHub releases of this project for a newer version.
 * <p>
 * The check runs once on a background thread at startup and the result is
 * announced at most once per game launch, as soon as a player exists. It is not
 * repeated when changing servers or worlds.
 */
public final class UiUtilsVersionChecker {
	private static final String REPO = "cev-api/UI-Utils-26x-CevAPI";
	private static final String MOD_ID = "ui-utils-26x-cevapi";
	private static final String RELEASES_URL = "https://api.github.com/repos/" + REPO
		+ "/releases";
	private static final String RELEASES_PAGE = "https://github.com/" + REPO
		+ "/releases/latest";
	private static final int TIMEOUT_MS = 5000;

	private static volatile String currentVersion = "";
	private static volatile String latestVersion = "";
	private static volatile boolean outOfDate;
	private static boolean announced;

	private UiUtilsVersionChecker() {
	}

	/** Starts the background check. Safe to call more than once. */
	public static void start() {
		if (System.getProperty("fabric.client.gametest") != null)
			return;
		currentVersion = readOwnVersion();
		if (currentVersion.isBlank())
			return;
		Thread thread = new Thread(UiUtilsVersionChecker::check, "UI-Utils-Version");
		thread.setDaemon(true);
		thread.start();
	}

	/** Announces an available update once per launch, from the client tick. */
	public static void onClientTick(Minecraft mc) {
		if (announced || !outOfDate || mc == null || mc.player == null)
			return;
		if (!UiUtilsState.isUiEnabled())
			return;
		announced = true;

		String update = latestVersion;
		String current = currentVersion;
		mc.player.sendSystemMessage(Component.literal("[UI-Utils] ")
			.withColor(0x55CCFF)
			.append(Component
				.literal("Update available: v" + update + " (you have v" + current + ")")
				.withColor(0xFFFFDE7A)));
		mc.player.sendSystemMessage(Component.literal("[UI-Utils] ")
			.withColor(0x55CCFF)
			.append(Component.literal("Download it here")
				.withColor(0x8BE8FF)
				.withStyle(style -> style
					.withUnderlined(true)
					.withClickEvent(new ClickEvent.OpenUrl(URI.create(RELEASES_PAGE))))));
		UiUtils.LOGGER.info(
			"UI-Utils is out of date: latest release is {}, local version is {}",
			update, current);
	}

	private static void check() {
		try {
			String latest = fetchLatestTag();
			if (latest.isBlank())
				return;
			latestVersion = latest;
			if (isNewer(latest, currentVersion))
				outOfDate = true;
		} catch (Exception e) {
			UiUtils.LOGGER.warn("Could not check for UI-Utils updates", e);
		}
	}

	/** Highest version found among the published, non-draft releases. */
	private static String fetchLatestTag() throws Exception {
		URLConnection connection = URI.create(RELEASES_URL).toURL().openConnection();
		connection.setRequestProperty("Accept", "application/vnd.github+json");
		connection.setRequestProperty("User-Agent", "UI-Utils-26x-CevAPI");
		connection.setConnectTimeout(TIMEOUT_MS);
		connection.setReadTimeout(TIMEOUT_MS);

		String best = "";
		try (Reader reader = new InputStreamReader(connection.getInputStream(),
			StandardCharsets.UTF_8)) {
			JsonElement parsed = JsonParser.parseReader(reader);
			if (!parsed.isJsonArray())
				return "";
			JsonArray releases = parsed.getAsJsonArray();
			for (JsonElement element : releases) {
				if (!element.isJsonObject())
					continue;
				JsonObject release = element.getAsJsonObject();
				// Drafts are not public, so they never count as a release.
				if (release.has("draft") && release.get("draft").getAsBoolean())
					continue;
				if (!release.has("tag_name"))
					continue;
				String tag = stripTagPrefix(release.get("tag_name").getAsString());
				if (tag.isBlank())
					continue;
				// Their releases are published as prereleases, so those count.
				if (best.isBlank() || isNewer(tag, best))
					best = tag;
			}
		}
		return best;
	}

	/** Reads the "v0.11" part out of the mod version, e.g. "26.3_v0.11". */
	private static String readOwnVersion() {
		try {
			return FabricLoader.getInstance().getModContainer(MOD_ID)
				.map(container -> stripTagPrefix(container.getMetadata().getVersion()
					.getFriendlyString()))
				.orElse("");
		} catch (Throwable ignored) {
			return "";
		}
	}

	/** Accepts "v0.11", "26.3_v0.11" and plain "0.11". */
	private static String stripTagPrefix(String raw) {
		if (raw == null)
			return "";
		String value = raw.trim();
		int underscore = value.lastIndexOf('_');
		if (underscore >= 0 && underscore + 1 < value.length())
			value = value.substring(underscore + 1);
		while (value.startsWith("v") || value.startsWith("V"))
			value = value.substring(1);
		return value.trim();
	}

	/** Numeric component comparison, so "0.10" is newer than "0.9". */
	private static boolean isNewer(String candidate, String current) {
		int[] left = parse(candidate);
		int[] right = parse(current);
		if (left == null || right == null)
			return false;
		int length = Math.max(left.length, right.length);
		for (int i = 0; i < length; i++) {
			int a = i < left.length ? left[i] : 0;
			int b = i < right.length ? right[i] : 0;
			if (a != b)
				return a > b;
		}
		return false;
	}

	private static int[] parse(String version) {
		if (version == null || version.isBlank())
			return null;
		String[] parts = version.split("\\.");
		int[] numbers = new int[parts.length];
		for (int i = 0; i < parts.length; i++) {
			String part = parts[i].replaceAll("[^0-9]", "");
			if (part.isEmpty())
				return null;
			try {
				numbers[i] = Integer.parseInt(part);
			} catch (NumberFormatException ignored) {
				return null;
			}
		}
		return numbers;
	}
}
