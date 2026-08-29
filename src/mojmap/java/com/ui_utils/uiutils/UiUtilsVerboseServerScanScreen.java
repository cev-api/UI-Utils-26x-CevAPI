package com.ui_utils.uiutils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/** Scrollable, bounded presentation of the passive server fingerprint snapshot. */
public final class UiUtilsVerboseServerScanScreen extends Screen {
	private final Screen parent;
	private int scroll;
	private int scrollbarX;
	private int scrollbarTop;
	private int scrollbarBottom;
	private int scrollbarThumbY;
	private int scrollbarThumbHeight;
	private int scrollbarMaxScroll;

	public UiUtilsVerboseServerScanScreen(Screen parent) {
		super(Component.literal("Verbose Server Scan"));
		this.parent = parent;
	}

	@Override
	protected void init() {
		startMissingScans();
		int width = Math.min(360, this.width - 24);
		int left = (this.width - width) / 2;
		addRenderableWidget(UiUtils.styledButton("Refresh", b -> startMissingScans(), left, this.height - 30, 86, 20));
		addRenderableWidget(UiUtils.styledButton("Copy Report", b -> {
			if (this.minecraft != null) this.minecraft.keyboardHandler.setClipboard(buildReport());
		}, left + 91, this.height - 30, 100, 20));
		addRenderableWidget(UiUtils.styledButton("Done", b -> McCompat.setScreen(this.minecraft, parent),
			left + 196, this.height - 30, width - 196, 20));
	}

	// ### ADDED ### Verbose Scan is a combined view; run missing active scans once for this server.
	private static void startMissingScans() {
		if (!UiUtilsPluginScanner.isActive() && !UiUtilsPluginScanner.hasResultsForCurrentServer())
			UiUtilsPluginScanner.startScan();
		if (!UiUtilsCommandScanner.isActive() && !UiUtilsCommandScanner.hasResultsForCurrentServer())
			UiUtilsCommandScanner.startScan();
	}
	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTicks) {
		super.extractRenderState(graphics, mouseX, mouseY, partialTicks);
		int width = Math.min(360, this.width - 24), left = (this.width - width) / 2;
		int top = 26, bottom = this.height - 36, lineHeight = this.font.lineHeight + 2;
		graphics.fill(left, top, left + width, bottom, 0xB0000000);
		List<String> lines = reportLines();
		int visible = Math.max(1, (bottom - top - 6) / lineHeight);
		scroll = Math.max(0, Math.min(scroll, Math.max(0, lines.size() - visible)));
		int y = top + 4;
		for (int i = scroll; i < lines.size() && y < bottom - lineHeight; i++, y += lineHeight) {
			String line = lines.get(i);
			int color = line.startsWith("[") ? 0xFFFFDE7A : line.startsWith("  ") ? 0xFFB8D8FF : 0xFFEAEAEA;
			graphics.text(this.font, line, left + 6, y, color, false);
		}
		// ### ADDED ### Visible scrollbar for long verbose reports.
		scrollbarX = left + width - 5;
		scrollbarTop = top + 2;
		scrollbarBottom = bottom - 2;
		scrollbarMaxScroll = Math.max(0, lines.size() - visible);
		int trackHeight = Math.max(1, scrollbarBottom - scrollbarTop);
		scrollbarThumbHeight = scrollbarMaxScroll == 0 ? trackHeight
			: Math.max(12, (int)Math.round(trackHeight * (visible / (double)lines.size())));
		int travel = Math.max(1, trackHeight - scrollbarThumbHeight);
		scrollbarThumbY = scrollbarTop + (scrollbarMaxScroll == 0 ? 0
			: (int)Math.round((scroll / (double)scrollbarMaxScroll) * travel));
		graphics.fill(scrollbarX, scrollbarTop, scrollbarX + 3, scrollbarBottom, 0xFF353535);
		graphics.fill(scrollbarX, scrollbarThumbY, scrollbarX + 3,
			scrollbarThumbY + scrollbarThumbHeight, 0xFFCFCFCF);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		if (scrollY < 0) scroll++; else if (scrollY > 0) scroll = Math.max(0, scroll - 1);
		return true;
	}

	@Override public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (event.button() == 0 && event.x() >= scrollbarX && event.x() <= scrollbarX + 4
			&& event.y() >= scrollbarTop && event.y() <= scrollbarBottom && scrollbarMaxScroll > 0) {
			int travel = Math.max(1, scrollbarBottom - scrollbarTop - scrollbarThumbHeight);
			double ratio = Math.max(0.0D, Math.min(1.0D,
				(event.y() - scrollbarTop - scrollbarThumbHeight / 2.0D) / travel));
			scroll = (int)Math.round(ratio * scrollbarMaxScroll);
			return true;
		}
		return super.mouseClicked(event, doubleClick);
	}
	@Override public void onClose() {
		UiUtilsScanHistory.recordVerboseFingerprint(UiUtilsScanHistory.serverKey(this.minecraft),
			UiUtilsServerFingerprintCollector.snapshot());
		McCompat.setScreen(this.minecraft, parent);
	}

	public static String buildReport() { return String.join("\n", reportLines()); }

	private static List<String> reportLines() {
		UiUtilsServerFingerprintCollector.Snapshot snapshot = UiUtilsServerFingerprintCollector.snapshot();
		List<String> lines = new ArrayList<>();
		lines.add("[Summary]");
		lines.add("Connected snapshot: " + snapshot.connected());
		if (!snapshot.configurationCaptured())
			lines.add("WARNING: Configuration-phase fingerprint unavailable. Reconnect to capture Known Packs and registry synchronization.");
		lines.add("[Detected Server Software]");
		Map<String, String> software = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
		for (UiUtilsServerFingerprintCollector.KnownPackInfo pack : snapshot.knownPacks()) {
			if ("minecraft".equals(pack.namespace()) && "core".equals(pack.id())) continue;
			software.put(UiUtilsServerFingerprintCollector.friendlyName(pack.id()), "Known Pack - exact advertised version " + pack.version());
		}
		for (UiUtilsServerFingerprintCollector.ChannelInfo channel : snapshot.payloads()) {
			String friendly = UiUtilsServerFingerprintCollector.friendlyName(channel.namespace());
			if (!friendly.equals(channel.namespace()) && !software.containsKey(friendly))
				software.put(friendly, "Server custom payload " + channel.id());
		}
		// Do not duplicate a Known Pack with its same active-scan row (for example, MintUtils 1.0.0).
		for (UiUtilsPluginScanner.PluginResultRow row : UiUtilsPluginScanner.getResultsSnapshot()) {
			boolean alreadyCovered = software.keySet().stream().anyMatch(name ->
				row.plugin().equalsIgnoreCase(name) || row.plugin().regionMatches(true, 0,
					name + " ", 0, name.length() + 1));
			if (!alreadyCovered)
				software.put(row.plugin(), "Plugin scan " + row.evidence());
		}
		if (software.isEmpty()) lines.add("  No corroborated software evidence captured.");
		else software.forEach((name, evidence) -> lines.add("  " + name + " - " + evidence));
		lines.add("[Known Packs]");
		for (UiUtilsServerFingerprintCollector.KnownPackInfo pack : snapshot.knownPacks())
			lines.add("  " + pack.namespace() + ":" + pack.id() + ":" + pack.version());
		lines.add("[Platform / Brand]");
		lines.add("  " + (snapshot.brand().isBlank() ? "Unknown" : snapshot.brand()));
		lines.add("[Server Registered Channels / Custom Payload Evidence]");
		Map<String, List<UiUtilsServerFingerprintCollector.ChannelInfo>> channels = new LinkedHashMap<>();
		for (UiUtilsServerFingerprintCollector.ChannelInfo channel : snapshot.payloads())
			channels.computeIfAbsent(channel.namespace(), ignored -> new ArrayList<>()).add(channel);
		if (channels.isEmpty()) lines.add("  No server payload IDs captured.");
		else channels.forEach((namespace, values) -> { lines.add("  " + namespace + ":"); for (var value : values) lines.add("    " + value.id() + " [" + value.phase() + ", " + value.source() + "]"); });
		lines.add("[Custom Registries]");
		for (UiUtilsServerFingerprintCollector.RegistryInfo registry : snapshot.registries()) {
			if (registry.entries().isEmpty()) continue;
			lines.add("  " + registry.registry());
			for (UiUtilsServerFingerprintCollector.RegistryEntryInfo entry : registry.entries())
				lines.add("    " + entry.id() + (entry.hasCustomData() ? " [custom data]" : ""));
		}
		lines.add("[Dimensions]"); for (String value : snapshot.dimensions()) lines.add("  " + value);
		lines.add("[Advancement / Datapack Namespaces]"); for (String value : snapshot.advancements()) lines.add("  " + value);
		lines.add("[Chat Completion Metadata]"); lines.add("  total=" + snapshot.chatCompletionCount() + ", emoji=" + snapshot.emojiCompletionCount() + ", formatting/action=" + snapshot.formattingCompletionCount());
		if (!snapshot.chatSamples().isEmpty()) lines.add("  sample=" + String.join(", ", snapshot.chatSamples()));
		lines.add("[Scoreboard / UI Signatures]"); for (String value : snapshot.objectives()) lines.add("  objective: " + value);
		for (String value : snapshot.tabText()) lines.add("  tab: " + value);
		lines.add("[Server Configuration]"); snapshot.serverConfig().forEach((key, value) -> lines.add("  " + key + " = " + value));
		return lines;
	}
}