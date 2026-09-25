package com.ui_utils.uiutils;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import com.ui_utils.uiutils.ui.UiButton;
import com.ui_utils.uiutils.ui.UiContent;
import com.ui_utils.uiutils.ui.UiInput;
import com.ui_utils.uiutils.ui.UiModernScreen;
import com.ui_utils.uiutils.ui.UiTheme;
import com.ui_utils.uiutils.ui.UiToggle;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public final class UiUtilsCommandScannerScreen extends UiModernScreen {
	private static final int OUTPUT_LINES = 3;

	private final Screen parent;
	private UiInput searchField;
	private UiInput packetCommandsField;
	private UiButton scannerModeButton;
	private boolean commandOutputVisible;
	private int resultsFlowTop;
	/** Lines built for the last layout, shared by the measure and draw passes. */
	private List<Line> resultLines = List.of();
	private final Set<String> expandedPlugins = new HashSet<>();
	private final Set<String> expandedCommandLetters = new HashSet<>();
	private final List<ClickTargetRow> clickableRows = new ArrayList<>();
	private boolean vulnerableListExpanded;

	public UiUtilsCommandScannerScreen(Screen parent) {
		super(Component.literal("UI-Utils Scanner Dashboard"));
		this.parent = parent;
	}

	@Override
	protected int naturalWidth() {
		return 480;
	}

	@Override
	protected boolean expandHeight() {
		return true;
	}

	@Override
	protected void buildContent(UiContent c) {
		scannerModeButton = c.button("", () -> {
			boolean packet = !"CLIENT_SIDE_ENUMERATION"
				.equalsIgnoreCase(UiUtilsSettings.get().commandScannerMode);
			UiUtilsSettings.get().commandScannerMode = packet
				? "CLIENT_SIDE_ENUMERATION" : "PACKET_PROBING";
			UiUtilsSettings.save();
			refreshScannerModeLabel();
		});
		refreshScannerModeLabel();

		c.row(UiContent.of(UiButton.of("Run command scan",
				UiUtilsCommandScanner::startScan)),
			UiContent.of(UiButton.of("Run plugin scan",
				UiUtilsPluginScanner::startScan)));
		c.slider("Scanner speed", UiUtilsSettings.Data.MIN_PROBES_PER_SECOND,
			UiUtilsSettings.Data.MAX_PROBES_PER_SECOND,
			UiUtilsSettings.getProbesPerSecond(), value -> {
				UiUtilsSettings.get().scannerProbesPerSecond = value;
				UiUtilsSettings.save();
			}).suffix(" probes/s");
		c.button("Verbose Server Scan", () -> {
			UiUtilsScanHistory.recordVerboseFingerprint(
				UiUtilsScanHistory.serverKey(this.minecraft),
				UiUtilsServerFingerprintCollector.snapshot());
			McCompat.setScreen(this.minecraft,
				new UiUtilsVerboseServerScanScreen(this));
		});
		c.row(UiContent.of(toggle("Command debug",
				() -> UiUtilsSettings.get().commandScannerDebugProbe,
				v -> {
					UiUtilsSettings.get().commandScannerDebugProbe = v;
					UiUtilsSettings.save();
				})),
			UiContent.of(toggle("Run found cmds",
				() -> UiUtilsSettings.get().commandScannerRunFoundCommands,
				v -> {
					UiUtilsSettings.get().commandScannerRunFoundCommands = v;
					UiUtilsSettings.save();
				})));
		c.button("Legacy Plugin Scan (Safer)",
			UiUtilsLegacyPluginScanner::startScan);
		c.section("Commands");
		searchField = c.input("", value ->
			Minecraft.getInstance().execute(this::rebuildWidgets));
		searchField.setMaxLength(64);
		searchField.setHint(Component.literal("Search results..."));
		packetCommandsField = c.inputSlot(
			UiUtilsSettings.get().commandScannerPacketCommands, value -> {});
		packetCommandsField.setMaxLength(256);
		c.row(UiContent.of(packetCommandsField, 3F),
			UiContent.of(UiButton.of("Send packet cmds", this::sendPacketCommands),
				2F));

		c.section("Results");
		resultsFlowTop = c.cursor();
		int lineHeight = lineHeight();
		// The results and the command output are painted directly, not as widgets, so
		// their exact extent is declared here. Without this the scroll system would
		// only see the widgets above and could never reach the bottom of the list.
		resultLines = buildLines(true);
		int drawnBottom = resultsFlowTop + 2 + resultLines.size() * lineHeight;
		if (commandOutputVisible)
			drawnBottom += (OUTPUT_LINES + 2) * lineHeight + 6;
		c.reportBottom(drawnBottom);
		c.space(drawnBottom - c.cursor());
		setStatus("");

		c.footerButton("Clear results", UiButton.Kind.DANGER, this::clearResults);
		c.footerButton("Done", UiButton.Kind.PRIMARY, this::onClose);
	}

	@Override
	protected void drawContentOverlay(GuiGraphicsExtractor graphics, Font font,
		int mouseX, int mouseY) {
		int lineHeight = lineHeight();
		List<Line> lines = resultLines;
		clickableRows.clear();
		int y = resultsFlowTop + 2;
		for (Line line : lines) {
			UiTheme.text(graphics, font,
				UiTheme.ellipsize(font, line.text, contentWidthUnits()), 0, y,
				line.color);
			if (line.clickKey != null)
				clickableRows.add(new ClickTargetRow(line.clickKey, 0, y - 1,
					contentWidthUnits(), lineHeight + 1));
			y += lineHeight;
		}
		if (commandOutputVisible)
			drawCommandOutput(graphics, font, y + 4);
	}

	@Override
	protected boolean onContentClick(int x, int y, boolean doubleClick) {
		for (ClickTargetRow row : clickableRows) {
			if (!row.contains(x, y))
				continue;
			if (row.clickKey.startsWith("plugin:")) {
				if (expandedPlugins.contains(row.clickKey))
					expandedPlugins.remove(row.clickKey);
				else
					expandedPlugins.add(row.clickKey);
			} else if (row.clickKey.startsWith("letter:")) {
				if (expandedCommandLetters.contains(row.clickKey))
					expandedCommandLetters.remove(row.clickKey);
				else
					expandedCommandLetters.add(row.clickKey);
			} else if (row.clickKey.startsWith("command:")) {
				selectCommand(row.clickKey.substring("command:".length()));
			} else if ("vuln:list".equals(row.clickKey)) {
				vulnerableListExpanded = !vulnerableListExpanded;
			} else {
				continue;
			}
			return true;
		}
		return false;
	}

	private void sendPacketCommands() {
		UiUtilsSettings.get().commandScannerPacketCommands =
			packetCommandsField.getValue();
		UiUtilsSettings.save();
		UiUtilsCommandScanner.sendManualPacketCommands();
		commandOutputVisible = true;
		Minecraft.getInstance().execute(this::rebuildWidgets);
	}

	/**
	 * Keeps the dashboard live while a scan runs. The result text is rebuilt every
	 * tick, and the screen is re-laid out whenever the number of lines changes so
	 * the space reserved for the list (and therefore the scroll range) matches what
	 * is actually drawn.
	 */
	@Override
	public void tick() {
		super.tick();
		if (scanning()) {
			refreshResults();
			return;
		}
		// One final refresh after a scan finishes, so the last results are shown.
		refreshResults();
	}

	private boolean scanning() {
		return UiUtilsCommandScanner.isActive() || UiUtilsPluginScanner.isActive()
			|| UiUtilsLegacyPluginScanner.isActive();
	}

	private void refreshResults() {
		List<Line> fresh = buildLines(true);
		boolean sizeChanged = fresh.size() != resultLines.size();
		resultLines = fresh;
		// Growing results must not re-scale the screen, so the panel is only
		// re-laid out to widen its scroll range. The scale stays put.
		if (sizeChanged)
			Minecraft.getInstance().execute(this::rebuildWidgets);
		setStatus(scanning() ? "Scanning..." : "");
	}

	private void drawCommandOutput(GuiGraphicsExtractor graphics, Font font,
		int top) {
		int lineHeight = lineHeight();
		int outputWidth = Math.max(1, contentWidthUnits() - 8);
		graphics.fill(0, top, contentWidthUnits(), top + 1, UiTheme.BORDER);
		UiTheme.text(graphics, font, "Command output (after Send packet cmds)", 4,
			top + 3, 0xFFFFDE7A);
		List<String> output = UiUtilsCommandScanner.getManualCommandOutputSnapshot();
		if (output.isEmpty()) {
			UiTheme.text(graphics, font,
				"Select a command, then press Send packet cmds.", 4,
				top + 3 + lineHeight + 2, UiTheme.TEXT_MUTED);
			return;
		}
		int y = top + 3 + lineHeight + 2;
		int first = Math.max(0, output.size() - OUTPUT_LINES);
		for (int i = first; i < output.size(); i++) {
			UiTheme.text(graphics, font,
				UiTheme.ellipsize(font, output.get(i), outputWidth), 4, y,
				UiTheme.TEXT_DIM);
			y += lineHeight;
		}
	}

	private static int lineHeight() {
		return Minecraft.getInstance().font.lineHeight + 1;
	}

	/** Toggle that saves the setting, for rows that pair two of them. */
	private static UiToggle toggle(String label,
		java.util.function.BooleanSupplier getter,
		java.util.function.Consumer<Boolean> setter) {
		return new UiToggle(label, getter, value -> {
			setter.accept(value);
			UiUtilsSettings.save();
		});
	}

	private void refreshScannerModeLabel() {
		if (scannerModeButton == null)
			return;
		boolean packet = !"CLIENT_SIDE_ENUMERATION".equalsIgnoreCase(UiUtilsSettings.get().commandScannerMode);
		scannerModeButton.setMessage(Component.literal("Scanner mode: " + (packet ? "Packet" : "Client")));
	}

	private void clearResults() {
		UiUtilsCommandScanner.clearResultsForUi();
		UiUtilsPluginScanner.clearResultsForUi();
		UiUtilsLegacyPluginScanner.clearResultsForUi();
		UiUtilsCommandScanner.clearManualCommandOutput();
		expandedPlugins.clear();
		expandedCommandLetters.clear();
		vulnerableListExpanded = false;
		commandOutputVisible = false;
		Minecraft.getInstance().execute(this::rebuildWidgets);
	}

	@Override
	public void onClose() {
		McCompat.setScreen(this.minecraft, parent);
	}

	private List<Line> buildLines() {
		return buildLines(true);
	}

	private List<Line> buildLines(boolean applySearch) {		List<Line> lines = new ArrayList<>();
		lines.add(new Line("Command Scanner", 0xFFFFB347));
		lines.add(new Line("Status: " + UiUtilsCommandScanner.getStatusLine(), 0xFFEAEAEA));
		List<String> commands = UiUtilsCommandScanner.getFoundCommandsSnapshot();
		boolean commandResultsTruncated = UiUtilsCommandScanner.hasTruncatedResponses();
		lines.add(new Line("Found commands: " + commands.size()
			+ (commandResultsTruncated ? "+" : ""), 0xFFB8B8B8));
		lines.add(new Line("Essentials commands/aliases skipped; other e... commands retained.", 0xFF909090));
		if (commandResultsTruncated)
			lines.add(new Line("! Some responses hit the server limit; counts are lower bounds.", 0xFFFFA8A8));
		if (commands.size() > 50) {
			Map<Character, List<String>> byLetter = new LinkedHashMap<>();
			for (char c = 'a'; c <= 'z'; c++)
				byLetter.put(c, new ArrayList<>());
			byLetter.put('#', new ArrayList<>());
			for (String cmd : commands) {
				char first = cmd.isEmpty() ? '#' : Character.toLowerCase(cmd.charAt(0));
				if (first < 'a' || first > 'z')
					first = '#';
				byLetter.get(first).add(cmd);
			}
			for (Map.Entry<Character, List<String>> entry : byLetter.entrySet()) {
				if (entry.getValue().isEmpty())
					continue;
				String key = "letter:" + entry.getKey();
				boolean expanded = expandedCommandLetters.contains(key);
				String count = String.valueOf(entry.getValue().size())
					+ (UiUtilsCommandScanner.wasResponseTruncated(entry.getKey()) ? "+" : "");
				lines.add(new Line((expanded ? "v " : "> ") + "[" + Character.toUpperCase(entry.getKey())
					+ "] (" + count + ")", 0xFFFFFFFF, key));
				if (expanded)
					for (String cmd : entry.getValue())
						lines.add(new Line("    /" + cmd, commandColor(cmd), "command:" + cmd));
			}
		} else {
			for (String cmd : commands)
				lines.add(new Line("  /" + cmd, commandColor(cmd), "command:" + cmd));
		}

		lines.add(new Line("", 0xFFFFFFFF));
		lines.add(new Line("Plugin Scanner", 0xFFFFDE7A));
		lines.add(new Line("Status: " + UiUtilsPluginScanner.getStatusLine(), 0xFFEAEAEA));
		List<UiUtilsPluginScanner.PluginResultRow> plugins = UiUtilsPluginScanner.getResultsSnapshot();
		lines.add(new Line("Detected plugins: " + plugins.size(), 0xFFB8B8B8));
		Map<String, VulnerableHit> vulnerableHits = collectVulnerableHits(plugins);
		lines.add(new Line((vulnerableListExpanded ? "v " : "> ") + "Vulnerable Plugins (" + vulnerableHits.size() + ")",
			vulnerableHits.isEmpty() ? 0xFFB8B8B8 : 0xFFFF7A7A, "vuln:list"));
		if (vulnerableListExpanded) {
			for (VulnerableHit hit : vulnerableHits.values()) {
				String versionText = hit.versions.isEmpty() ? "" : " (" + String.join(", ", hit.versions) + ")";
				lines.add(new Line("  - " + hit.displayName + versionText, 0xFFFFA8A8));
			}
		}

		String currentEvidence = "";
		for (UiUtilsPluginScanner.PluginResultRow row : plugins) {
			if (!row.evidence().equalsIgnoreCase(currentEvidence)) {
				currentEvidence = row.evidence();
				lines.add(new Line("[" + currentEvidence + "]", 0xFFF5F5F5));
			}
			String pluginKey = "plugin:" + row.evidence().toLowerCase() + "|" + row.plugin().toLowerCase();
			boolean expanded = expandedPlugins.contains(pluginKey);
			String caret = expanded ? "v " : "> ";
			String flag = row.anticheatFlagged() ? " ! " : " - ";
			boolean vulnerable = UiUtilsVulnerablePlugins.entriesByKey()
				.containsKey(UiUtilsVulnerablePlugins.normalizeKey(row.plugin()));
			lines.add(new Line(caret + flag + row.plugin() + " (" + row.commandCount() + " cmds)",
				vulnerable ? 0xFFFF7A7A : (row.anticheatFlagged() ? 0xFFFFA8A8 : 0xFF93F7A4), pluginKey));
			if (expanded) {
				List<String> details = UiUtilsServerFingerprintCollector.detailsForSoftware(row.plugin());
				for (String detail : details)
					lines.add(new Line("    " + detail, 0xFFB8D8FF));
				if (row.commands().isEmpty()) {
					if (details.isEmpty())
						lines.add(new Line("    (no commands or cached details)", 0xFF909090));
				} else {
					for (String cmd : row.commands())
						lines.add(new Line("    /" + cmd, commandColor(cmd), "command:" + cmd));
				}
			}
		}

		lines.add(new Line("", 0xFFFFFFFF));
		lines.add(new Line("Legacy Plugin Scanner", 0xFFFFB347));
		lines.add(new Line("Status: " + UiUtilsLegacyPluginScanner.getStatusLine(), 0xFFEAEAEA));
		List<UiUtilsPluginScanner.PluginResultRow> legacyPlugins = UiUtilsLegacyPluginScanner.getResultsSnapshot();
		lines.add(new Line("Detected plugins: " + legacyPlugins.size(), 0xFFB8B8B8));
		for (UiUtilsPluginScanner.PluginResultRow row : legacyPlugins) {
			String flag = row.anticheatFlagged() ? " ! " : " - ";
			boolean vulnerable = UiUtilsVulnerablePlugins.entriesByKey()
				.containsKey(UiUtilsVulnerablePlugins.normalizeKey(row.plugin()));
			lines.add(new Line(flag + row.plugin(),
				vulnerable ? 0xFFFF7A7A : (row.anticheatFlagged() ? 0xFFFFA8A8 : 0xFF93F7A4)));
		}

		lines.add(new Line("", 0xFFFFFFFF));
		lines.add(new Line("Recent events", 0xFFD8D8D8));
		List<String> commandEvents = UiUtilsCommandScanner.getRecentEventsSnapshot();
		List<String> pluginEvents = UiUtilsPluginScanner.getRecentEventsSnapshot();
		for (int i = Math.max(0, commandEvents.size() - 8); i < commandEvents.size(); i++)
			lines.add(new Line("CMD: " + commandEvents.get(i), 0xFFAAAAAA));
		for (int i = Math.max(0, pluginEvents.size() - 8); i < pluginEvents.size(); i++)
			lines.add(new Line("PLG: " + pluginEvents.get(i), 0xFFAAAAAA));
		List<String> legacyEvents = UiUtilsLegacyPluginScanner.getRecentEventsSnapshot();
		for (int i = Math.max(0, legacyEvents.size() - 8); i < legacyEvents.size(); i++)
			lines.add(new Line("LGC: " + legacyEvents.get(i), 0xFFAAAAAA));

		String query = !applySearch || searchField == null ? ""
			: searchField.getValue().trim().toLowerCase();
		if (query.isEmpty())
			return lines;

		List<Line> filtered = new ArrayList<>();
		for (Line line : lines)
			if (line.text.toLowerCase().contains(query))
				filtered.add(line);
		return filtered;
	}

	private static int commandColor(String command) {
		return UiUtilsCommandScanner.isCommandHiddenToUser(command) ? 0xFFFF5555 : 0xFFAEEBFF;
	}

	private void selectCommand(String command) {
		if (packetCommandsField == null)
			return;
		String value = command;
		if (value.startsWith("trigger (") && value.endsWith(")"))
			value = "trigger " + value.substring("trigger (".length(), value.length() - 1);
		packetCommandsField.setValue("/" + value);
		UiUtilsSettings.get().commandScannerPacketCommands = packetCommandsField.getValue();
		UiUtilsSettings.save();
		UiUtilsCommandScanner.clearManualCommandOutput();
		commandOutputVisible = false;
		rebuildWidgets();
	}

	private static Map<String, VulnerableHit> collectVulnerableHits(List<UiUtilsPluginScanner.PluginResultRow> plugins) {
		Map<String, VulnerableHit> hits = new LinkedHashMap<>();
		Map<String, UiUtilsVulnerablePlugins.VulnerableEntry> vulnEntries = UiUtilsVulnerablePlugins.entriesByKey();
		for (UiUtilsPluginScanner.PluginResultRow row : plugins) {
			String key = UiUtilsVulnerablePlugins.normalizeKey(row.plugin());
			UiUtilsVulnerablePlugins.VulnerableEntry vuln = vulnEntries.get(key);
			if (vuln == null)
				continue;
			VulnerableHit hit = hits.computeIfAbsent(key, ignored -> new VulnerableHit(row.plugin()));
			hit.versions.addAll(vuln.versions());
		}
		return hits;
	}

	private record Line(String text, int color, String clickKey) {
		private Line(String text, int color) {
			this(text, color, null);
		}
	}

	private record ClickTargetRow(String clickKey, int x, int y, int w, int h) {
		private boolean contains(double mx, double my) {
			return mx >= x && mx <= x + w && my >= y && my <= y + h;
		}
	}

	private static final class VulnerableHit {
		private final String displayName;
		private final Set<String> versions = new LinkedHashSet<>();

		private VulnerableHit(String displayName) {
			this.displayName = displayName;
		}
	}
}
