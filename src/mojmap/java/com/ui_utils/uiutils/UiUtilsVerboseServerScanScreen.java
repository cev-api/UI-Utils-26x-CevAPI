package com.ui_utils.uiutils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import com.ui_utils.uiutils.ui.UiButton;
import com.ui_utils.uiutils.ui.UiContent;
import com.ui_utils.uiutils.ui.UiModernScreen;
import com.ui_utils.uiutils.ui.UiTheme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Scrollable, bounded presentation of the passive server fingerprint snapshot. */
public final class UiUtilsVerboseServerScanScreen extends UiModernScreen {
	private final Screen parent;
	private int renderedLineCount;

	public UiUtilsVerboseServerScanScreen(Screen parent) {
		super(Component.literal("Verbose Server Scan"));
		this.parent = parent;
	}

	@Override
	protected int naturalWidth() {
		return 460;
	}

	@Override
	protected boolean expandWidth() {
		return true;
	}

	@Override
	protected int maxWidth() {
		return 1100;
	}

	@Override
	protected void buildContent(UiContent c) {
		startMissingScans();
		int lineHeight = lineHeight();
		List<String> lines = reportLines();
		renderedLineCount = lines.size();
		// Painted directly in the overlay, so its extent is declared for the same
		// reason: text the screen draws is not covered by any widget.
		int reportHeight = lines.size() * lineHeight + 4;
		c.reportBottom(reportHeight);
		c.space(reportHeight);
		c.footerButton("Refresh", UiButton.Kind.SECONDARY, () -> {
			startMissingScans();
			Minecraft.getInstance().execute(this::rebuildWidgets);
		});
		c.footerButton("Copy Report", UiButton.Kind.SECONDARY, () -> {
			if (this.minecraft != null)
				this.minecraft.keyboardHandler.setClipboard(buildReport());
		});
		c.footerButton("Done", UiButton.Kind.PRIMARY, this::onClose);
	}

	@Override
	public void tick() {
		super.tick();
		startMissingScans();
		int lineCount = reportLines().size();
		if (lineCount != renderedLineCount)
			rebuildWidgetsPreservingScroll();
	}

	@Override
	protected void drawContentOverlay(GuiGraphicsExtractor graphics, Font font,
		int mouseX, int mouseY) {
		List<String> lines = reportLines();
		int lineHeight = lineHeight();
		int top = (int)Math.round(scrollOffset());
		int bottom = top + contentHeightUnits();
		for (int i = 0; i < lines.size(); i++) {
			int y = i * lineHeight;
			if (y + lineHeight < top || y > bottom)
				continue;
			String line = lines.get(i);
			int color = line.startsWith("[") ? 0xFFFFDE7A
				: line.startsWith("  ") ? 0xFFB8D8FF : 0xFFEAEAEA;
			UiTheme.text(graphics, font,
				UiTheme.ellipsize(font, line, contentWidthUnits()), 0, y + 1, color);
		}
	}

	private static int lineHeight() {
		return Minecraft.getInstance().font.lineHeight + 2;
	}

	// Verbose Scan is a combined view; run missing active scans once for this server.
	private static void startMissingScans() {
		if (!UiUtilsPluginScanner.isActive() && !UiUtilsPluginScanner.hasResultsForCurrentServer())
			UiUtilsPluginScanner.startScan();
		if (!UiUtilsCommandScanner.isActive() && !UiUtilsCommandScanner.hasResultsForCurrentServer())
			UiUtilsCommandScanner.startScan();
	}

	@Override
	public void onClose() {
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
