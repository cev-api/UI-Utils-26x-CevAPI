package com.ui_utils.uiutils;
import com.ui_utils.packettools.AdvancedPacketTool;
import com.ui_utils.uiutils.macro.UiUtilsMacroExecutor;
import com.ui_utils.uiutils.macro.UiUtilsMacroManager;
import com.google.gson.Gson;
import com.mojang.blaze3d.platform.InputConstants;
import org.joml.Matrix3x2fStack;
import com.mojang.serialization.JsonOps;
import java.util.HashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import java.util.WeakHashMap;
import java.util.function.Consumer;
import java.util.regex.Pattern;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractSignEditScreen;
import net.minecraft.client.gui.screens.inventory.BookEditScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ServerboundContainerClosePacket;
import net.minecraft.world.inventory.AbstractContainerMenu;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class UiUtils {
	public static final String VERSION = "2.4.0";
	public static final Logger LOGGER = LoggerFactory.getLogger("ui-utils");

	private static final WeakHashMap<UiUtilsColoredButton, Boolean> queueCounterButtons = new WeakHashMap<>();
	private static final Map<String, Boolean> keyActionDown = new HashMap<>();
	private static boolean initialized;
	private static EditBox currentChatField;

	private UiUtils() {}

	public static void init() {
		if (initialized)
			return;
		UiUtilsVulnerablePlugins.init();
		UiUtilsPluginScanner.init();
		UiUtilsLegacyPluginScanner.init();
		UiUtilsMacroManager.get();
		initialized = true;
	}

	public static void onClientTick(Minecraft mc) {
		refreshQueueCounterButtons();
		UiUtilsPluginScanner.onTick();
		UiUtilsLegacyPluginScanner.onTick();
		UiUtilsCommandScanner.onTick();
		UiUtilsDisconnect.onClientTick(mc);
		UiUtilsAutoduper.onClientTick(mc);
		if (mc == null || mc.getWindow() == null)
			return;

		// Don't fire hotkeys while typing in chat/text fields.
		if (isTypingIntoTextField(mc)) {
			updateKeybindEdges(mc, false);
			return;
		}
		updateKeybindEdges(mc, true);
	}

	private static void updateKeybindEdges(Minecraft mc, boolean execute) {
		for(KeybindAction action : keybindActions()) {
			InputConstants.Key key = parseKey(getKeybind(action.id, action.defaultKey));
			boolean down = key != null && mc.getWindow() != null
				&& InputConstants.isKeyDown(mc.getWindow(), key.getValue());
			boolean wasDown = keyActionDown.getOrDefault(action.id, false);
			if(execute && down && !wasDown
				&& (action.alwaysAvailable || UiUtilsState.isUiEnabled()))
				executeKeybindAction(action.id, mc);
			keyActionDown.put(action.id, down);
		}
	}

	private static boolean isTypingIntoTextField(Minecraft mc) {
		Screen screen = McCompat.getScreen(mc);
		if (screen == null)
			return false;
		if (screen instanceof ChatScreen)
			return true;
		if (screen instanceof AbstractSignEditScreen
			|| screen instanceof BookEditScreen)
			return true;
		try {
			Object focused = screen.getFocused();
			if (focused instanceof EditBox editBox && editBox.isFocused())
				return true;
			if (focused != null && isLikelyTextInputWidget(focused))
				return true;
		} catch (Throwable ignored) {}
		for (Object child : screen.children())
			if (child instanceof EditBox editBox && editBox.isFocused())
				return true;
			else if (child != null && isLikelyTextInputWidget(child))
				return true;
		return false;
	}

	private static boolean isLikelyTextInputWidget(Object widget) {
		String simple = widget.getClass().getSimpleName().toLowerCase(java.util.Locale.ROOT);
		return simple.contains("editbox") || simple.contains("textfield")
			|| simple.contains("textinput");
	}

	private static InputConstants.Key parseKey(String raw) {
		if(raw == null || raw.isBlank())
			return null;
		try {
			return InputConstants.getKey(raw);
		}catch(Exception ignored) {
			return null;
		}
	}

		private static void toggleDelay(Minecraft mc) {
			UiUtilsState.delayUiPackets = !UiUtilsState.delayUiPackets;
			chatIfEnabled("Delay packets: " + UiUtilsState.delayUiPackets);
			if (!UiUtilsState.delayUiPackets && !UiUtilsState.delayedUiPackets.isEmpty() && mc.getConnection() != null) {
				for (net.minecraft.network.protocol.Packet<?> p : UiUtilsState.delayedUiPackets)
					mc.getConnection().send(p);
				if (mc.player != null)
					mc.player.sendSystemMessage(Component.literal("Sent " + UiUtilsState.delayedUiPackets.size() + " packets."));
				UiUtilsState.delayedUiPackets.clear();
				refreshQueueCounterButtons();
			}
		}

	private static void restoreScreen(Minecraft mc) {
		if (UiUtilsState.storedScreen == null || UiUtilsState.storedMenu == null || mc.player == null)
			return;
		McCompat.setScreen(mc, UiUtilsState.storedScreen);
		mc.player.containerMenu = UiUtilsState.storedMenu;
		try {
			String title = UiUtilsState.storedScreen.getTitle().getString();
			chatIfEnabled("Loaded GUI: title=\"" + title + "\", syncId="
				+ UiUtilsState.storedMenu.containerId + ", revision=" + UiUtilsState.storedMenu.getStateId());
		} catch (Throwable ignored) {}
	}

	public static List<KeybindAction> keybindActions() {
		return List.of(
			new KeybindAction("restore_gui", "Restore GUI", UiUtilsSettings.get().restoreKey, false),
			new KeybindAction("packet_tool", "Advanced Packet Tool", UiUtilsSettings.get().packetToolsKey, true),
			new KeybindAction("command_scanner", "Command Scanner", "", false),
			new KeybindAction("plugin_scanner", "Plugin Scanner", "", false),
			new KeybindAction("legacy_plugin_scanner", "Legacy Plugin Scanner", "", false),
			new KeybindAction("autoduper_start", "Start Autoduper", "", false),
			new KeybindAction("close_no_packet", "Close Without Packet", "", false),
			new KeybindAction("desync", "De-Sync", "", false),
			new KeybindAction("send_packets_toggle", "Send Packets Toggle", "", false),
			new KeybindAction("send_packets_on", "Send Packets True", "", false),
			new KeybindAction("send_packets_off", "Send Packets False", "", false),
			new KeybindAction("delay_packets_toggle", "Delay Packets Toggle", UiUtilsSettings.get().delayToggleKey, true),
			new KeybindAction("delay_packets_on", "Delay Packets True", "", false),
			new KeybindAction("delay_packets_off", "Delay Packets False", "", false),
			new KeybindAction("leave_send", "Leave & Send Packets", "", false),
			new KeybindAction("disconnect_send", "Disconnect & Send Packets", "", false),
			new KeybindAction("save_gui", "Save GUI", "", false),
			new KeybindAction("load_gui", "Load GUI", "", false),
			new KeybindAction("clear_queue", "Clear Queue", "", false),
			new KeybindAction("resync_inv", "Resync Inv", "", false),
			new KeybindAction("disconnect", "Disconnect", "", false),
			new KeybindAction("spam_inc", "Spam Increase", "", false),
			new KeybindAction("spam_dec", "Spam Decrease", "", false),
			new KeybindAction("spam_send", "Spam Send", "", false),
			new KeybindAction("send_one", "Send One", "", false),
			new KeybindAction("pop_last", "Pop Last", "", false),
			new KeybindAction("send_chat_box", "Send Chat Field", "", false)
			,
			new KeybindAction("macro_run_last", "Run Last Macro", UiUtilsSettings.get().macroRunLastKey, true),
			new KeybindAction("macro_stop", "Stop Macro", UiUtilsSettings.get().macroStopKey, true)
		);
	}

	public static String getKeybind(String id, String fallback) {
		Map<String, String> binds = UiUtilsSettings.get().keyBinds;
		if(binds == null) {
			UiUtilsSettings.get().keyBinds = new HashMap<>();
			binds = UiUtilsSettings.get().keyBinds;
		}
		String value = binds.get(id);
		return value == null ? fallback : value;
	}

	public static void setKeybind(String id, String keyName) {
		if(UiUtilsSettings.get().keyBinds == null)
			UiUtilsSettings.get().keyBinds = new HashMap<>();
		if(keyName == null || keyName.isBlank())
			UiUtilsSettings.get().keyBinds.put(id, "");
		else
			UiUtilsSettings.get().keyBinds.put(id, keyName);
		UiUtilsSettings.save();
	}

	public static void executeKeybindAction(String id, Minecraft mc) {
		final String defaultSlot = "default";
		switch(id) {
			case "restore_gui" -> restoreScreen(mc);
			case "packet_tool" -> AdvancedPacketTool.openScreen(McCompat.getScreen(mc));
			case "command_scanner" -> McCompat.setScreen(mc, new UiUtilsCommandScannerScreen(McCompat.getScreen(mc)));
			case "plugin_scanner" -> UiUtilsPluginScanner.startScan();
			case "legacy_plugin_scanner" -> UiUtilsLegacyPluginScanner.startScan();
			case "autoduper_start" -> UiUtilsAutoduper.start();
			case "close_no_packet" -> closeScreenWithConfiguredDelay(mc);
			case "desync" -> sendClosePacketWithConfiguredDelay(mc);
			case "send_packets_toggle" -> setSendPackets(!UiUtilsState.sendUiPackets);
			case "send_packets_on" -> setSendPackets(true);
			case "send_packets_off" -> setSendPackets(false);
			case "delay_packets_toggle" -> toggleDelay(mc);
			case "delay_packets_on" -> setDelayPackets(mc, true);
			case "delay_packets_off" -> setDelayPackets(mc, false);
			case "leave_send" -> leaveAndSendPackets(mc);
			case "disconnect_send" -> disconnectAndSendPackets(mc);
			case "save_gui" -> {
				if(saveCurrentGuiToSlot(mc, defaultSlot))
					chatIfEnabled("Saved GUI to slot \"" + defaultSlot + "\"");
			}
			case "load_gui" -> {
				if(loadGuiFromSlot(mc, defaultSlot))
					chatIfEnabled("Loaded GUI from slot \"" + defaultSlot + "\"");
			}
			case "clear_queue" -> chatIfEnabled("Cleared queued packets (" + clearQueuedPackets() + ")");
			case "resync_inv" -> {
				if(mc.player != null && tryResyncInventory(mc.player.containerMenu))
					chatIfEnabled("Inventory resynced");
			}
			case "disconnect" -> UiUtilsDisconnect.disconnectWithConfiguredMethod(mc);
			case "spam_inc" -> UiUtilsState.spamCount = Math.min(100, UiUtilsState.spamCount + 1);
			case "spam_dec" -> UiUtilsState.spamCount = Math.max(1, UiUtilsState.spamCount - 1);
			case "spam_send" -> chatIfEnabled("Spammed queued packets (" + sendQueuedPackets(mc, UiUtilsState.spamCount) + ")");
			case "send_one" -> chatIfEnabled(sendOneQueuedPacket(mc) ? "Sent one queued packet" : "No queued packets to send");
			case "pop_last" -> chatIfEnabled(popLastQueuedPacket() ? "Removed last queued packet" : "No queued packets to remove");
			case "send_chat_box" -> sendCurrentChatField(mc);
			case "macro_run_last" -> {
				String macroName = UiUtilsSettings.get().lastMacroName;
				if (macroName != null && !macroName.isBlank())
					UiUtilsMacroManager.get().execute(macroName);
			}
			case "macro_stop" -> UiUtilsMacroExecutor.stop();
			default -> {}
		}
	}

	private static void setSendPackets(boolean value) {
		UiUtilsState.sendUiPackets = value;
		chatIfEnabled("Send packets: " + value);
	}

	private static void setDelayPackets(Minecraft mc, boolean value) {
		if(UiUtilsState.delayUiPackets == value)
			return;
		toggleDelay(mc);
	}

	private static void leaveAndSendPackets(Minecraft mc) {
		int sent = sendQueuedPackets(mc, 1);
		UiUtilsState.delayUiPackets = false;
		UiUtilsState.delayedUiPackets.clear();
		refreshQueueCounterButtons();
		McCompat.setScreen(mc, null);
		chatIfEnabled("Left GUI and sent queued packets (" + sent + ")");
	}

	private static void disconnectAndSendPackets(Minecraft mc) {
		int sent = sendQueuedPackets(mc, 1);
		UiUtilsState.delayUiPackets = false;
		UiUtilsState.delayedUiPackets.clear();
		refreshQueueCounterButtons();
		UiUtilsDisconnect.disconnectWithConfiguredMethod(mc);
		chatIfEnabled("Disconnected and sent queued packets (" + sent + ")");
	}

	private static void sendCurrentChatField(Minecraft mc) {
		if(currentChatField == null)
			return;
		String text = currentChatField.getValue();
		if(text == null || text.isBlank())
			return;
		if(text.startsWith("/"))
			sendCommandWithConfiguredDelay(mc, text.substring(1));
		else
			sendChatWithConfiguredDelay(mc, text);
		currentChatField.setValue("");
	}

	public record KeybindAction(String id, String label, String defaultKey,
		boolean alwaysAvailable) {
	}

	public static void chatIfEnabled(String msg) {
		if (!UiUtilsState.isUiEnabled() || !UiUtilsSettings.get().logToChat)
			return;
		Minecraft mc = Minecraft.getInstance();
		if (mc.player != null)
			mc.player.sendSystemMessage(styledUiMessage(msg));
	}

	private static Component styledUiMessage(String msg) {
		// Prefix is always light blue for fast visual scanning in chat.
		var prefix = Component.literal("[UI-Utils] ").withColor(0x55CCFF);
		String safe = msg == null ? "" : msg;
		String lower = safe.toLowerCase(Locale.ROOT);

		int bodyColor = 0xFFFFFF; // default: white
		if (lower.contains("validated")
			|| lower.contains("success")
			|| lower.contains("saved")
			|| lower.contains("loaded"))
			bodyColor = 0x6DE06D; // green
		else if (lower.contains("failed")
			|| lower.contains("error")
			|| lower.contains("rejected")
			|| lower.contains("stopped")
			|| lower.contains("aborted"))
			bodyColor = 0xFF6B6B; // red

		int split = safe.indexOf(':');
		if(split <= 0)
			return Component.literal("").append(prefix)
				.append(Component.literal(safe).withColor(bodyColor));

		String label = safe.substring(0, split);
		String rest = safe.substring(split + 1);
		String labelLower = label.trim().toLowerCase(Locale.ROOT);
		int labelColor = switch(labelLower) {
			case "autoduper" -> 0x8BE8FF;
			case "commandscanner", "command scanner" -> 0xFFE08A;
			case "delay packets", "send packets" -> 0xD9D9D9;
			default -> 0xD9D9D9;
		};

		return Component.literal("").append(prefix)
			.append(Component.literal(label + ":").withColor(labelColor))
			.append(Component.literal(rest).withColor(bodyColor));
	}

	public static void renderSyncInfo(Minecraft mc, GuiGraphicsExtractor graphics,
		AbstractContainerMenu menu) {
		if (menu == null)
			return;
		graphics.text(mc.font, "Sync Id: " + menu.containerId, 200, 5, 0xFFFFFF, false);
		graphics.text(mc.font, "Revision: " + menu.getStateId(), 200, 35, 0xFFFFFF, false);
	}

	public static float fitTextScale(Font font, String text, int maxWidth,
		int maxHeight, float minScale) {
		if (font == null || text == null || text.isEmpty())
			return 1.0F;
		float widthScale = maxWidth <= 0 ? 1.0F
			: maxWidth / (float)Math.max(1, font.width(text));
		float heightScale = maxHeight <= 0 ? 1.0F
			: maxHeight / (float)Math.max(1, font.lineHeight);
		return Math.max(minScale, Math.min(1.0F,
			Math.min(widthScale, heightScale)));
	}

	public static void renderScaledCenteredText(GuiGraphicsExtractor graphics,
		Font font, Component text, int centerX, int topY, int maxWidth,
		int maxHeight, int color, float minScale) {
		float scale = fitTextScale(font, text.getString(), maxWidth, maxHeight,
			minScale);
		Matrix3x2fStack pose = graphics.pose();
		pose.pushMatrix();
		pose.translate(centerX, topY);
		pose.scale(scale, scale);
		graphics.centeredText(font, text, 0, 0, color);
		pose.popMatrix();
	}

	public static void renderScaledText(GuiGraphicsExtractor graphics, Font font,
		String text, int x, int topY, int maxWidth, int maxHeight, int color,
		float minScale) {
		float scale = fitTextScale(font, text, maxWidth, maxHeight, minScale);
		Matrix3x2fStack pose = graphics.pose();
		pose.pushMatrix();
		pose.translate(x, topY);
		pose.scale(scale, scale);
		graphics.text(font, text, 0, 0, color, false);
		pose.popMatrix();
	}

	public static void toggleUiUtils(Minecraft mc) {
		UiUtilsState.enabled = !UiUtilsState.enabled;
		if (mc.player != null)
			mc.player.sendSystemMessage(Component.literal(
				"UI-Utils is now " + (UiUtilsState.enabled ? "enabled" : "disabled") + "."));
	}

	public static boolean saveCurrentGuiToSlot(Minecraft mc, String slot) {
		if (mc.player == null)
			return false;
		String key = slot.toLowerCase(Locale.ROOT);
		UiUtilsState.storedScreen = McCompat.getScreen(mc);
		UiUtilsState.storedMenu = mc.player.containerMenu;
		UiUtilsState.savedScreens.put(key, UiUtilsState.storedScreen);
		UiUtilsState.savedMenus.put(key, mc.player.containerMenu);
		return true;
	}

	public static boolean loadGuiFromSlot(Minecraft mc, String slot) {
		if (mc.player == null)
			return false;
		String key = slot.toLowerCase(Locale.ROOT);
		Screen screen = UiUtilsState.savedScreens.get(key);
		AbstractContainerMenu menu = UiUtilsState.savedMenus.get(key);
		if (screen == null || menu == null)
			return false;
		McCompat.setScreen(mc, screen);
		mc.player.containerMenu = menu;
		UiUtilsState.storedScreen = screen;
		UiUtilsState.storedMenu = menu;
		return true;
	}

	public static int sendQueuedPackets(Minecraft mc, int times) {
		if (mc.getConnection() == null || times < 1 || UiUtilsState.delayedUiPackets.isEmpty())
			return 0;
		boolean prevDelay = UiUtilsState.delayUiPackets;
		UiUtilsState.delayUiPackets = false;
		int sent = 0;
		for (int i = 0; i < times; i++)
			for (Packet<?> packet : UiUtilsState.delayedUiPackets) {
				mc.getConnection().send(packet);
				sent++;
			}
		UiUtilsState.delayUiPackets = prevDelay;
		refreshQueueCounterButtons();
		return sent;
	}

	public static boolean sendOneQueuedPacket(Minecraft mc) {
		if (mc.getConnection() == null || UiUtilsState.delayedUiPackets.isEmpty())
			return false;
		boolean prevDelay = UiUtilsState.delayUiPackets;
		UiUtilsState.delayUiPackets = false;
		Packet<?> packet = UiUtilsState.delayedUiPackets.remove(0);
		mc.getConnection().send(packet);
		UiUtilsState.delayUiPackets = prevDelay;
		refreshQueueCounterButtons();
		return true;
	}

	public static boolean popLastQueuedPacket() {
		if (UiUtilsState.delayedUiPackets.isEmpty())
			return false;
		UiUtilsState.delayedUiPackets.remove(UiUtilsState.delayedUiPackets.size() - 1);
		refreshQueueCounterButtons();
		return true;
	}

	public static int clearQueuedPackets() {
		int count = UiUtilsState.delayedUiPackets.size();
		UiUtilsState.delayedUiPackets.clear();
		refreshQueueCounterButtons();
		return count;
	}

	public static void refreshQueueCounterButtons() {
		String text = "Queue: " + UiUtilsState.delayedUiPackets.size();
		queueCounterButtons.keySet().removeIf(button -> button == null);
		for (UiUtilsColoredButton button : queueCounterButtons.keySet())
			button.setMessage(Component.literal(text));
	}

	private static boolean tryResyncInventory(AbstractContainerMenu menu) {
		String[] methods = {"broadcastFullState", "sendAllDataToRemote", "broadcastChanges", "syncState"};
		for (String name : methods)
			try {
				java.lang.reflect.Method method = menu.getClass().getMethod(name);
				method.invoke(menu);
				return true;
			} catch (ReflectiveOperationException ignored) {}
		return false;
	}

	public static UiWidgetLayout addUiWidgets(Minecraft mc, int baseX, int baseY,
		int spacing, int maxHeight, int maxRight, Consumer<AbstractWidget> adder) {
		final int naturalRowHeight = 20;
		final int naturalSpacing = spacing;
		final int naturalFullWidth = 160;
		final int naturalChatHeight = 20;
		final int rowCount = 20;
		double scale = Math.min(1.0D, Math.min(
			maxHeight / (double)(rowCount * naturalRowHeight
				+ (rowCount - 1) * naturalSpacing + naturalChatHeight + naturalSpacing),
			Math.max(naturalFullWidth, maxRight - baseX) / (double)naturalFullWidth));
		scale *= 0.92D;
		int rowHeight = Math.max(12, (int)Math.floor(naturalRowHeight * scale));
		int compactSpacing = Math.max(1, (int)Math.floor(naturalSpacing * scale));
		int fullWidth = Math.min(maxRight - baseX,
			Math.max(88, (int)Math.floor(naturalFullWidth * scale)));
		int chatHeight = Math.max(12, (int)Math.floor(naturalChatHeight * scale));
		int halfWidth = Math.max(44, (fullWidth - compactSpacing) / 2);
		int spamSideWidth = Math.max(16, (int)Math.floor(30 * scale));
		int spamCenterWidth = Math.max(40, fullWidth - spamSideWidth * 2 - compactSpacing * 2);
		final String defaultSlot = "default";
		List<UiWidgetRow> rows = new ArrayList<>();
		rows.add(UiWidgetRow.label("UI-Utils by CevAPI"));
		rows.add(UiWidgetRow.single("Settings", b -> {
			McCompat.setScreen(mc, new UiUtilsSettingsScreen(McCompat.getScreen(mc)));
		}));
		rows.add(UiWidgetRow.single("Command & Plugin Scanner", b -> {
			McCompat.setScreen(mc, new UiUtilsCommandScannerScreen(McCompat.getScreen(mc)));
		}));
		rows.add(UiWidgetRow.single("Advanced Packet Tool", b -> {
			AdvancedPacketTool.openScreen(McCompat.getScreen(mc));
		}));
		rows.add(UiWidgetRow.single("Start Autoduper", b -> UiUtilsAutoduper.start()));
		rows.add(UiWidgetRow.single("Autoduper Options", b -> {
			McCompat.setScreen(mc, new UiUtilsAutoduperScreen(McCompat.getScreen(mc)));
		}));
		rows.add(UiWidgetRow.single("Macros", b -> {
			McCompat.setScreen(mc, new UiUtilsMacroLibraryScreen(McCompat.getScreen(mc)));
		}));
		rows.add(UiWidgetRow.single("Close Without Packet", b -> closeScreenWithConfiguredDelay(mc)));
		rows.add(UiWidgetRow.single("De-Sync", b -> sendClosePacketWithConfiguredDelay(mc)));
		rows.add(UiWidgetRow.single("Send Packets: " + boolText(UiUtilsState.sendUiPackets), b -> {
			UiUtilsState.sendUiPackets = !UiUtilsState.sendUiPackets;
			b.setMessage(Component.literal("Send Packets: "
				+ boolText(UiUtilsState.sendUiPackets)));
			chatIfEnabled("Send packets: " + UiUtilsState.sendUiPackets);
		}));
		rows.add(UiWidgetRow.single("Delay Packets: " + boolText(UiUtilsState.delayUiPackets), b -> {
			UiUtilsState.delayUiPackets = !UiUtilsState.delayUiPackets;
			b.setMessage(Component.literal("Delay Packets: "
				+ boolText(UiUtilsState.delayUiPackets)));
			if (!UiUtilsState.delayUiPackets && !UiUtilsState.delayedUiPackets.isEmpty() && mc.getConnection() != null) {
				for (Packet<?> packet : UiUtilsState.delayedUiPackets)
					mc.getConnection().send(packet);
				if (mc.player != null)
					mc.player.sendSystemMessage(Component.literal("Sent " + UiUtilsState.delayedUiPackets.size() + " packets."));
				UiUtilsState.delayedUiPackets.clear();
				refreshQueueCounterButtons();
			}
			chatIfEnabled("Delay packets: " + UiUtilsState.delayUiPackets);
		}));
		rows.add(UiWidgetRow.single("Leave & Send Packets", b -> leaveAndSendPackets(mc)));
		rows.add(UiWidgetRow.single("Disconnect & Send Packets", b -> disconnectAndSendPackets(mc)));
		rows.add(UiWidgetRow.single("Fabricate Packet", b -> {
			if (McCompat.getScreen(mc) instanceof net.minecraft.client.gui.screens.inventory.AbstractContainerScreen) {
				UiUtilsState.fabricateOverlayOpen = !UiUtilsState.fabricateOverlayOpen;
				chatIfEnabled("Fabricate overlay: " + (UiUtilsState.fabricateOverlayOpen ? "opened" : "closed"));
			} else {
				chatIfEnabled("Fabricate packet works inside container screens.");
			}
		}));
		rows.add(UiWidgetRow.single("Copy GUI Title JSON", b -> {
			try {
				Screen screen = McCompat.getScreen(mc);
				if (screen == null)
					throw new IllegalStateException("Minecraft screen was null.");
				String json = new Gson().toJson(ComponentSerialization.CODEC.encodeStart(JsonOps.INSTANCE, screen.getTitle()).getOrThrow());
				mc.keyboardHandler.setClipboard(json);
				chatIfEnabled("Copied GUI title JSON to clipboard");
			} catch (IllegalStateException e) {
				LOGGER.error("Error while copying title JSON to clipboard", e);
				chatIfEnabled("Failed to copy GUI title JSON");
			}
		}));
		rows.add(UiWidgetRow.pair(
			new UiWidgetButton("Save GUI", halfWidth, b -> {
				if (saveCurrentGuiToSlot(mc, defaultSlot))
					chatIfEnabled("Saved GUI to slot \"" + defaultSlot + "\"");
			}),
			new UiWidgetButton("Load GUI", halfWidth, b -> {
				if (loadGuiFromSlot(mc, defaultSlot))
					chatIfEnabled("Loaded GUI from slot \"" + defaultSlot + "\"");
				else
					chatIfEnabled("No saved GUI in slot \"" + defaultSlot + "\"");
			})));
		rows.add(UiWidgetRow.pair(
			new UiWidgetButton("Clear Queue", halfWidth, b -> {
				int cleared = clearQueuedPackets();
				chatIfEnabled("Cleared queued packets (" + cleared + ")");
			}),
			new UiWidgetButton("Queue: " + UiUtilsState.delayedUiPackets.size(),
				halfWidth, b -> b.setMessage(Component.literal("Queue: "
					+ UiUtilsState.delayedUiPackets.size())))));
		rows.add(UiWidgetRow.pair(
			new UiWidgetButton("Resync Inv", halfWidth, b -> {
				if (mc.player != null && tryResyncInventory(mc.player.containerMenu))
					chatIfEnabled("Inventory resynced");
				else
					chatIfEnabled("Failed to resync inventory");
			}),
			new UiWidgetButton("Disconnect", halfWidth,
				b -> UiUtilsDisconnect.disconnectWithConfiguredMethod(mc))));
		rows.add(UiWidgetRow.triple(
			new UiWidgetButton("-", spamSideWidth, b -> {
				if (UiUtilsState.spamCount > 1)
					UiUtilsState.spamCount--;
				spamCountButtons().forEach(button -> button.setMessage(
					Component.literal("Spam (X" + UiUtilsState.spamCount + ")")));
			}),
			new UiWidgetButton("Spam (X" + UiUtilsState.spamCount + ")",
				spamCenterWidth, b -> {
					int sent = sendQueuedPackets(mc, UiUtilsState.spamCount);
					chatIfEnabled("Spammed queued packets (" + sent + ")");
				}),
			new UiWidgetButton("+", spamSideWidth, b -> {
				if (UiUtilsState.spamCount < 100)
					UiUtilsState.spamCount++;
				spamCountButtons().forEach(button -> button.setMessage(
					Component.literal("Spam (X" + UiUtilsState.spamCount + ")")));
			})));
		rows.add(UiWidgetRow.pair(
			new UiWidgetButton("Send One", halfWidth, b -> {
				boolean sent = sendOneQueuedPacket(mc);
				chatIfEnabled(sent ? "Sent one queued packet" : "No queued packets to send");
			}),
			new UiWidgetButton("Pop Last", halfWidth, b -> {
				boolean popped = popLastQueuedPacket();
				chatIfEnabled(popped ? "Removed last queued packet" : "No queued packets to remove");
			})));

		List<UiUtilsColoredButton> queueButtons = new ArrayList<>();
		List<UiUtilsColoredButton> spamButtons = new ArrayList<>();
		for (int i = 0; i < rows.size(); i++) {
			int x = baseX;
			int y = baseY + i * (rowHeight + compactSpacing);
			UiWidgetRow row = rows.get(i);
			if (row.labelText != null) {
				adder.accept(new UiUtilsTextLabel(x, y, fullWidth, rowHeight,
					Component.literal(row.labelText)));
				continue;
			}
			int nextX = x;
			int consumed = 0;
			for (int buttonIndex = 0; buttonIndex < row.buttons.size(); buttonIndex++) {
				UiWidgetButton button = row.buttons.get(buttonIndex);
				int width = buttonIndex == row.buttons.size() - 1
					? fullWidth - consumed - compactSpacing * buttonIndex
					: Math.max(18, (int)Math.round(button.width
						* (fullWidth / (double)naturalFullWidth)));
				UiUtilsColoredButton widget = styledButton(button.text, button.action,
					nextX, y, width, rowHeight);
				adder.accept(widget);
				if (button.text.startsWith("Queue: "))
					queueButtons.add(widget);
				if (button.text.startsWith("Spam (X"))
					spamButtons.add(widget);
				nextX += width + compactSpacing;
				consumed += width;
			}
		}
		queueCounterButtons.keySet().removeIf(button -> button == null);
		for (UiUtilsColoredButton button : queueButtons)
			queueCounterButtons.put(button, Boolean.TRUE);
		setSpamCountButtons(spamButtons);

		int chatY = baseY + rows.size() * (rowHeight + compactSpacing);
		return new UiWidgetLayout(baseX, chatY, fullWidth, chatHeight);
	}

	public static UiUtilsColoredButton styledButton(String text,
		UiUtilsColoredButton.PressAction onPress, int x, int y, int width,
		int height) {
		return UiUtilsColoredButton.of(x, y, width, height, text, onPress);
	}

	private static String boolText(boolean value) {
		return value ? "True" : "False";
	}

	private static final class UiUtilsTextLabel extends AbstractWidget {
		private UiUtilsTextLabel(int x, int y, int width, int height,
			Component message) {
			super(x, y, width, height, message);
			this.active = false;
		}

		@Override
		protected void extractWidgetRenderState(GuiGraphicsExtractor graphics,
			int mouseX, int mouseY, float partialTicks) {
			int textColor = 0xFF000000
				| (UiUtilsSettings.get().uiButtonTextColor & 0xFFFFFF);
			int textY = getY() + Math.max(1, (getHeight()
				- Minecraft.getInstance().font.lineHeight) / 2);
			renderScaledCenteredText(graphics, Minecraft.getInstance().font,
				getMessage(), getX() + getWidth() / 2, textY, getWidth() - 6,
				getHeight() - 2, textColor, 0.4F);
		}

		@Override
		protected void updateWidgetNarration(NarrationElementOutput narration) {
		}
	}

	public static EditBox createChatField(Minecraft mc, Font font, int x, int y,
		int width, int height) {
		EditBox field = new EditBox(font, x, y, width, height, Component.literal("Chat ...")) {
			@Override
			public boolean keyPressed(net.minecraft.client.input.KeyEvent keyEvent) {
				if (keyEvent.key() == GLFW.GLFW_KEY_ENTER || keyEvent.key() == GLFW.GLFW_KEY_KP_ENTER) {
					String text = getValue();
					String command = null;
					if (UiUtilsCommandSystem.isUiUtilsCommand(text)) {
						command = UiUtilsCommandSystem.extractCommandBody(text);
					} else {
						String trimmed = text == null ? "" : text.trim();
						if (!trimmed.isEmpty()) {
							String[] parts = trimmed.split("\\s+", 2);
							if (UiUtilsCommandSystem.isKnownSubcommand(parts[0]))
								command = trimmed;
						}
					}
					if (command != null) {
							String result = UiUtilsCommandSystem.execute(command);
							if (mc.player != null && !result.isEmpty())
								for (String line : result.split("\n"))
									mc.player.sendSystemMessage(Component.literal(line));
							setValue("");
							return true;
						}

					if (mc.getConnection() != null && mc.player != null) {
						if (text.startsWith("/"))
							sendCommandWithConfiguredDelay(mc,
								text.replaceFirst(Pattern.quote("/"), ""));
						else
							sendChatWithConfiguredDelay(mc, text);
					} else {
						LOGGER.warn("Minecraft player/connection was null while sending chat.");
					}
					setValue("");
					return true;
				}
				return super.keyPressed(keyEvent);
			}
		};
		currentChatField = field;
		field.setMaxLength(256);
		field.setHint(Component.literal("Chat ..."));
		return field;
	}

	public record UiWidgetLayout(int chatX, int chatY, int chatWidth,
		int chatHeight) {}

	private static final WeakHashMap<UiUtilsColoredButton, Boolean> spamCountButtonMap = new WeakHashMap<>();

	private static List<UiUtilsColoredButton> spamCountButtons() {
		spamCountButtonMap.keySet().removeIf(button -> button == null);
		return List.copyOf(spamCountButtonMap.keySet());
	}

	private static void setSpamCountButtons(List<UiUtilsColoredButton> buttons) {
		spamCountButtonMap.clear();
		for (UiUtilsColoredButton button : buttons)
			spamCountButtonMap.put(button, Boolean.TRUE);
	}

	private record UiWidgetButton(String text, int width,
		UiUtilsColoredButton.PressAction action) {}

	private static final class UiWidgetRow {
		private final String labelText;
		private final List<UiWidgetButton> buttons;

		private UiWidgetRow(String labelText, List<UiWidgetButton> buttons) {
			this.labelText = labelText;
			this.buttons = buttons;
		}

		private static UiWidgetRow label(String text) {
			return new UiWidgetRow(text, List.of());
		}

		private static UiWidgetRow single(String text,
			UiUtilsColoredButton.PressAction action) {
			return new UiWidgetRow(null, List.of(
				new UiWidgetButton(text, 160, action)));
		}

		private static UiWidgetRow pair(UiWidgetButton left,
			UiWidgetButton right) {
			return new UiWidgetRow(null, List.of(left, right));
		}

		private static UiWidgetRow triple(UiWidgetButton left,
			UiWidgetButton center, UiWidgetButton right) {
			return new UiWidgetRow(null, List.of(left, center, right));
		}
	}

	public static Runnable getFabricatePacketRunnable(Minecraft mc, boolean delay, Packet<?> packet) {
		return () -> {
			if (mc.getConnection() == null) {
				LOGGER.warn("Minecraft connection was null while sending packets.");
				return;
			}
			mc.getConnection().send(packet);
		};
	}

	public static void closeScreenWithConfiguredDelay(Minecraft mc) {
		int delayTicks = Math.max(0, UiUtilsSettings.get().uiCloseDelayTicks);
		queueTask(() -> {
			Minecraft current = Minecraft.getInstance();
			McCompat.setScreen(current, null);
			chatIfEnabled("Closed GUI without packet"
				+ delaySuffix(delayTicks));
		}, delayTicks * 50L);
	}

	public static void sendClosePacketWithConfiguredDelay(Minecraft mc) {
		if (mc.getConnection() == null || mc.player == null) {
			LOGGER.warn(
				"Minecraft connection or player was null while using 'De-sync'.");
			return;
		}
		int syncId = mc.player.containerMenu.containerId;
		int delayTicks = Math.max(0, UiUtilsSettings.get().uiCloseDelayTicks);
		queueTask(() -> {
			Minecraft current = Minecraft.getInstance();
			if (current.getConnection() == null || current.player == null)
				return;
			current.getConnection().send(new ServerboundContainerClosePacket(syncId));
			chatIfEnabled("De-synced syncId " + syncId + delaySuffix(delayTicks));
		}, delayTicks * 50L);
	}

	public static void sendCommandWithConfiguredDelay(Minecraft mc, String command) {
		String clean = command == null ? "" : command.trim();
		while(clean.startsWith("/"))
			clean = clean.substring(1).trim();
		if(clean.isBlank())
			return;
		String commandToSend = clean;
		int delayTicks = Math.max(0, UiUtilsSettings.get().uiCommandDelayTicks);
		queueTask(() -> {
			Minecraft current = Minecraft.getInstance();
			if(current.getConnection() != null)
				current.getConnection().sendCommand(commandToSend);
		}, delayTicks * 50L);
	}

	public static void sendChatWithConfiguredDelay(Minecraft mc, String message) {
		if(message == null || message.isBlank())
			return;
		int delayTicks = Math.max(0, UiUtilsSettings.get().uiCommandDelayTicks);
		queueTask(() -> {
			Minecraft current = Minecraft.getInstance();
			if(current.getConnection() != null)
				current.getConnection().sendChat(message);
		}, delayTicks * 50L);
	}

	private static String delaySuffix(int ticks) {
		return ticks > 0 ? " after " + ticks + " tick(s)" : "";
	}

	public static boolean isInteger(String string) {
		try {
			Integer.parseInt(string);
			return true;
		} catch (Exception e) {
			return false;
		}
	}

	public static void queueTask(Runnable runnable, long delayMs) {
		Timer timer = new Timer();
		timer.schedule(new TimerTask() {
			@Override
			public void run() {
				Minecraft.getInstance().execute(runnable);
			}
		}, delayMs);
	}
}
