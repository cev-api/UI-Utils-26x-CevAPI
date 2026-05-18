package com.ui_utils.uiutils;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

public final class UiUtilsCommandScannerScreen extends Screen {
	private final Screen parent;
	private EditBox searchField;
	private EditBox packetCommandsField;
	private UiUtilsColoredButton scannerModeButton;
	private int resultsScroll;
	private int resultsTop;
	private int resultsBottom;
	private int panelLeft;
	private int panelWidth;
	private final Set<String> expandedPlugins = new HashSet<>();
	private final Set<String> expandedCommandLetters = new HashSet<>();
	private final List<ClickTargetRow> clickableRows = new ArrayList<>();
	private boolean vulnerableListExpanded;
	private boolean draggingScrollbar;
	private int scrollbarGrabOffset;
	private ScrollbarMetrics lastScrollbar = ScrollbarMetrics.none();

	public UiUtilsCommandScannerScreen(Screen parent) {
		super(Component.literal("UI-Utils Scanner Dashboard"));
		this.parent = parent;
	}

	@Override
	protected void init() {
		panelWidth = 420;
		int left = this.width / 2 - panelWidth / 2;
		panelLeft = left;
		int rowH = 20;
		int gap = 4;

		int topRows = 5;
		int controlsHeight = (rowH * topRows) + (gap * (topRows - 1));
		int footerHeight = rowH + gap;
		int desiredResultsHeight = Math.min(320, Math.max(180, this.height - 150));
		int totalBlockHeight = controlsHeight + 8 + desiredResultsHeight + 8 + footerHeight;
		int blockTop = Math.max(8, (this.height - totalBlockHeight) / 2);
		int y = blockTop;

		scannerModeButton = addRenderableWidget(UiUtils.styledButton("", b -> {
			boolean packet = !"CLIENT_SIDE_ENUMERATION".equalsIgnoreCase(UiUtilsSettings.get().commandScannerMode);
			UiUtilsSettings.get().commandScannerMode = packet ? "CLIENT_SIDE_ENUMERATION" : "PACKET_PROBING";
			UiUtilsSettings.save();
			refreshScannerModeLabel();
		}, left, y, 205, rowH));
		refreshScannerModeLabel();

		addRenderableWidget(UiUtils.styledButton("Run command scan", b -> UiUtilsCommandScanner.startScan(),
			left + 215, y, 205, rowH));
		y += rowH + gap;

		addRenderableWidget(UiUtils.styledButton("Command debug: "
			+ (UiUtilsSettings.get().commandScannerDebugProbe ? "ON" : "OFF"), b -> {
				UiUtilsSettings.get().commandScannerDebugProbe = !UiUtilsSettings.get().commandScannerDebugProbe;
				UiUtilsSettings.save();
				b.setMessage(Component.literal("Command debug: "
					+ (UiUtilsSettings.get().commandScannerDebugProbe ? "ON" : "OFF")));
			}, left, y, 205, rowH));

		addRenderableWidget(UiUtils.styledButton("Run plugin scan", b -> UiUtilsPluginScanner.startScan(),
			left + 215, y, 205, rowH));
		y += rowH + gap;

		searchField = new EditBox(this.font, left, y, 205, rowH, Component.literal("Search results"));
		searchField.setMaxLength(64);
		searchField.setHint(Component.literal("Search results..."));
		addRenderableWidget(searchField);

		addRenderableWidget(UiUtils.styledButton("Run found cmds: "
			+ (UiUtilsSettings.get().commandScannerRunFoundCommands ? "ON" : "OFF"), b -> {
				UiUtilsSettings.get().commandScannerRunFoundCommands = !UiUtilsSettings.get().commandScannerRunFoundCommands;
				UiUtilsSettings.save();
				b.setMessage(Component.literal("Run found cmds: "
					+ (UiUtilsSettings.get().commandScannerRunFoundCommands ? "ON" : "OFF")));
			}, left + 215, y, 205, rowH));
		y += rowH + gap;

		packetCommandsField = new EditBox(this.font, left, y, 205, rowH, Component.literal("Packet commands"));
		packetCommandsField.setMaxLength(256);
		packetCommandsField.setValue(UiUtilsSettings.get().commandScannerPacketCommands);
		addRenderableWidget(packetCommandsField);
		addRenderableWidget(UiUtils.styledButton("Send packet cmds", b -> {
			UiUtilsSettings.get().commandScannerPacketCommands = packetCommandsField.getValue();
			UiUtilsSettings.save();
			UiUtilsCommandScanner.sendManualPacketCommands();
		}, left + 215, y, 205, rowH));
		y += rowH + gap;

		resultsTop = y + 4;
		resultsBottom = resultsTop + desiredResultsHeight;

		int footerY = resultsBottom + 8;
		addRenderableWidget(UiUtils.styledButton("Clear results", b -> clearResults(),
			left, footerY, 205, rowH));
		addRenderableWidget(UiUtils.styledButton("Done", b -> this.minecraft.setScreen(parent),
			left + 215, footerY, 205, rowH));
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
		expandedPlugins.clear();
		expandedCommandLetters.clear();
		vulnerableListExpanded = false;
		resultsScroll = 0;
		draggingScrollbar = false;
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX,
		int mouseY, float partialTicks) {
		super.extractRenderState(graphics, mouseX, mouseY, partialTicks);

		int left = panelLeft;
		int top = resultsTop;
		int bottom = Math.max(top + 40, resultsBottom);

		graphics.fill(left, top, left + panelWidth, bottom, 0xAA000000);
		graphics.fill(left, top, left + panelWidth, top + 1, 0xFF2A2A2A);
		graphics.fill(left, bottom - 1, left + panelWidth, bottom, 0xFF2A2A2A);

		List<Line> lines = buildLines();
		clickableRows.clear();
		int lineHeight = this.font.lineHeight + 1;
		int visibleRows = Math.max(1, (bottom - top - 8) / lineHeight);
		int maxScroll = Math.max(0, lines.size() - visibleRows);
		if (resultsScroll > maxScroll)
			resultsScroll = maxScroll;

		int y = top + 4;
		for (int i = resultsScroll; i < lines.size() && y + lineHeight <= bottom - 4; i++) {
			Line line = lines.get(i);
			graphics.text(this.font, line.text, left + 6, y, line.color, false);
			if (line.clickKey != null) {
				clickableRows.add(new ClickTargetRow(line.clickKey, left + 4,
					y - 1, panelWidth - 10, lineHeight + 1));
			}
			y += lineHeight;
		}

		lastScrollbar = computeScrollbar(left + panelWidth - 5, top + 2, bottom - 2,
			lines.size(), visibleRows, resultsScroll);
		renderScrollbar(graphics, lastScrollbar);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		int left = panelLeft;
		if (mouseX < left || mouseX > left + panelWidth || mouseY < resultsTop || mouseY > resultsBottom)
			return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);

		if (scrollY < 0)
			resultsScroll++;
		else if (scrollY > 0)
			resultsScroll = Math.max(0, resultsScroll - 1);
		return true;
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent context, boolean doubleClick) {
		if (context.button() == 0) {
			double mouseX = context.x();
			double mouseY = context.y();

			if (lastScrollbar.hasScroll && lastScrollbar.contains(mouseX, mouseY)) {
				if (mouseY >= lastScrollbar.thumbY && mouseY <= lastScrollbar.thumbY + lastScrollbar.thumbH) {
					draggingScrollbar = true;
					scrollbarGrabOffset = (int)Math.max(0, Math.round(mouseY) - lastScrollbar.thumbY);
				} else {
					jumpScrollToMouse((int)Math.round(mouseY), 0);
				}
				return true;
			}

			for (ClickTargetRow row : clickableRows) {
				if (!row.contains(mouseX, mouseY))
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
				} else if ("vuln:list".equals(row.clickKey)) {
					vulnerableListExpanded = !vulnerableListExpanded;
				}
				return true;
			}
		}
		return super.mouseClicked(context, doubleClick);
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent context, double dragX, double dragY) {
		if (draggingScrollbar && context.button() == 0 && lastScrollbar.hasScroll) {
			jumpScrollToMouse((int)Math.round(context.y()), scrollbarGrabOffset);
			return true;
		}
		return super.mouseDragged(context, dragX, dragY);
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent context) {
		if (context.button() == 0 && draggingScrollbar) {
			draggingScrollbar = false;
			return true;
		}
		return super.mouseReleased(context);
	}

	private void jumpScrollToMouse(int mouseY, int grabOffset) {
		if (!lastScrollbar.hasScroll)
			return;
		int maxScroll = Math.max(1, lastScrollbar.totalRows - lastScrollbar.visibleRows);
		int travel = Math.max(1, lastScrollbar.trackBottom - lastScrollbar.trackTop - lastScrollbar.thumbH);
		int thumbTop = Math.max(lastScrollbar.trackTop,
			Math.min(lastScrollbar.trackBottom - lastScrollbar.thumbH, mouseY - grabOffset));
		double ratio = (thumbTop - lastScrollbar.trackTop) / (double)travel;
		resultsScroll = Math.max(0, Math.min(maxScroll, (int)Math.round(ratio * maxScroll)));
	}

	@Override
	public void onClose() {
		this.minecraft.setScreen(parent);
	}

	private List<Line> buildLines() {
		List<Line> lines = new ArrayList<>();
		lines.add(new Line("Command Scanner", 0xFF8CC8FF));
		lines.add(new Line("Status: " + UiUtilsCommandScanner.getStatusLine(), 0xFFEAEAEA));
		List<String> commands = UiUtilsCommandScanner.getFoundCommandsSnapshot();
		lines.add(new Line("Found commands: " + commands.size(), 0xFFB8B8B8));
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
				lines.add(new Line((expanded ? "v " : "> ") + "[" + Character.toUpperCase(entry.getKey())
					+ "] (" + entry.getValue().size() + ")", 0xFFFFFFFF, key));
				if (expanded)
					for (String cmd : entry.getValue())
						lines.add(new Line("    /" + cmd, 0xFFAEEBFF));
			}
		} else {
			for (String cmd : commands)
				lines.add(new Line("  /" + cmd, 0xFFAEEBFF));
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
				if (row.commands().isEmpty()) {
					lines.add(new Line("    (no commands captured)", 0xFF909090));
				} else {
					for (String cmd : row.commands())
						lines.add(new Line("    /" + cmd, 0xFFAEEBFF));
				}
			}
		}

		lines.add(new Line("", 0xFFFFFFFF));
		lines.add(new Line("Recent events", 0xFFD8D8D8));
		List<String> commandEvents = UiUtilsCommandScanner.getRecentEventsSnapshot();
		List<String> pluginEvents = UiUtilsPluginScanner.getRecentEventsSnapshot();
		for (int i = Math.max(0, commandEvents.size() - 8); i < commandEvents.size(); i++)
			lines.add(new Line("CMD: " + commandEvents.get(i), 0xFFAAAAAA));
		for (int i = Math.max(0, pluginEvents.size() - 8); i < pluginEvents.size(); i++)
			lines.add(new Line("PLG: " + pluginEvents.get(i), 0xFFAAAAAA));

		String query = searchField == null ? "" : searchField.getValue().trim().toLowerCase();
		if (query.isEmpty())
			return lines;

		List<Line> filtered = new ArrayList<>();
		for (Line line : lines)
			if (line.text.toLowerCase().contains(query))
				filtered.add(line);
		return filtered;
	}

	private ScrollbarMetrics computeScrollbar(int x, int top, int bottom, int totalRows,
		int visibleRows, int scroll) {
		int trackH = Math.max(1, bottom - top);
		if (totalRows <= visibleRows)
			return new ScrollbarMetrics(x, top, bottom, top, trackH, false, totalRows, visibleRows);
		double ratio = visibleRows / (double)Math.max(1, totalRows);
		int thumbH = Math.max(12, (int)Math.round(trackH * ratio));
		int maxScroll = Math.max(1, totalRows - visibleRows);
		int travel = Math.max(1, trackH - thumbH);
		int thumbY = top + (int)Math.round((Math.max(0, Math.min(scroll, maxScroll)) / (double)maxScroll) * travel);
		return new ScrollbarMetrics(x, top, bottom, thumbY, thumbH, true, totalRows, visibleRows);
	}

	private void renderScrollbar(GuiGraphicsExtractor graphics, ScrollbarMetrics m) {
		graphics.fill(m.x, m.trackTop, m.x + 3, m.trackBottom, 0xFF353535);
		if (m.hasScroll)
			graphics.fill(m.x, m.thumbY, m.x + 3, m.thumbY + m.thumbH,
				draggingScrollbar ? 0xFFFFFFFF : 0xFFCFCFCF);
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

	private record ScrollbarMetrics(int x, int trackTop, int trackBottom, int thumbY,
		int thumbH, boolean hasScroll, int totalRows, int visibleRows) {
		private static ScrollbarMetrics none() {
			return new ScrollbarMetrics(0, 0, 0, 0, 0, false, 0, 0);
		}

		private boolean contains(double mx, double my) {
			return mx >= x && mx <= x + 3 && my >= trackTop && my <= trackBottom;
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
