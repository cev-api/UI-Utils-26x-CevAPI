package com.ui_utils.uiutils;
import com.ui_utils.packettools.AdvancedPacketTool;
import com.google.gson.Gson;
import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.serialization.JsonOps;
import java.util.Locale;
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
	private static boolean initialized;
	private static boolean restoreKeyDown;
	private static boolean packetToolsKeyDown;
	private static boolean delayToggleKeyDown;

	private UiUtils() {}

	public static void init() {
		if (initialized)
			return;
		UiUtilsPluginScanner.init();
		initialized = true;
	}

	public static void onClientTick(Minecraft mc) {
		refreshQueueCounterButtons();
		UiUtilsCommandScanner.onTick();
		UiUtilsDisconnect.onClientTick(mc);
		if (mc == null || mc.getWindow() == null)
			return;

		InputConstants.Key packetKey = getPacketToolsKey();
		InputConstants.Key delayKey = getDelayToggleKey();
		boolean packetDown = packetKey != null
			&& InputConstants.isKeyDown(mc.getWindow(), packetKey.getValue());
		boolean delayDown = delayKey != null
			&& InputConstants.isKeyDown(mc.getWindow(), delayKey.getValue());
		boolean restoreDown = false;
		InputConstants.Key restoreKey = null;
		if (UiUtilsState.isUiEnabled()) {
			restoreKey = getRestoreKey();
			restoreDown = restoreKey != null
				&& InputConstants.isKeyDown(mc.getWindow(), restoreKey.getValue());
		}

		// Don't fire hotkeys while typing in chat/text fields.
		if (isTypingIntoTextField(mc)) {
			packetToolsKeyDown = packetDown;
			delayToggleKeyDown = delayDown;
			restoreKeyDown = restoreDown;
			return;
		}

		// Allow Advanced Packet Tool key even if the UI overlay is disabled.
		if (packetDown && !packetToolsKeyDown)
			com.ui_utils.packettools.AdvancedPacketTool.openScreen(mc.screen);
		packetToolsKeyDown = packetDown;

		// Global delay toggle key.
		if (delayDown && !delayToggleKeyDown)
			toggleDelay(mc);
		delayToggleKeyDown = delayDown;

		if (!UiUtilsState.isUiEnabled())
			return;

		if (restoreKey == null) {
			restoreKeyDown = false;
			return;
		}
		if (restoreDown && !restoreKeyDown)
			restoreScreen(mc);
		restoreKeyDown = restoreDown;
	}

	private static boolean isTypingIntoTextField(Minecraft mc) {
		if (mc.screen == null)
			return false;
		if (mc.screen instanceof ChatScreen)
			return true;
		if (mc.screen instanceof AbstractSignEditScreen
			|| mc.screen instanceof BookEditScreen)
			return true;
		try {
			Object focused = mc.screen.getFocused();
			if (focused instanceof EditBox editBox && editBox.isFocused())
				return true;
			if (focused != null && isLikelyTextInputWidget(focused))
				return true;
		} catch (Throwable ignored) {}
		for (Object child : mc.screen.children())
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

	private static InputConstants.Key getRestoreKey() {
		String key = UiUtilsSettings.get().restoreKey;
		if (key == null || key.isBlank())
			return InputConstants.getKey("key.keyboard.v");
		try {
			InputConstants.Key parsed = InputConstants.getKey(key);
			return parsed != null ? parsed : InputConstants.getKey("key.keyboard.v");
		} catch (Exception ignored) {
			return InputConstants.getKey("key.keyboard.v");
		}
	}
		private static InputConstants.Key getDelayToggleKey() {
			String key = UiUtilsSettings.get().delayToggleKey;
			if (key == null || key.isBlank())
				return InputConstants.getKey("key.keyboard.o");
			try {
				InputConstants.Key parsed = InputConstants.getKey(key);
				return parsed != null ? parsed : InputConstants.getKey("key.keyboard.o");
			} catch (Exception ignored) {
				return InputConstants.getKey("key.keyboard.o");
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


	private static InputConstants.Key getPacketToolsKey() {
		String key = UiUtilsSettings.get().packetToolsKey;
		if (key == null || key.isBlank())
			return InputConstants.getKey("key.keyboard.p");
		try {
			InputConstants.Key parsed = InputConstants.getKey(key);
			return parsed != null ? parsed : InputConstants.getKey("key.keyboard.p");
		} catch (Exception ignored) {
			return InputConstants.getKey("key.keyboard.p");
		}
	}

	private static void restoreScreen(Minecraft mc) {
		if (UiUtilsState.storedScreen == null || UiUtilsState.storedMenu == null || mc.player == null)
			return;
		mc.setScreen(UiUtilsState.storedScreen);
		mc.player.containerMenu = UiUtilsState.storedMenu;
		try {
			String title = UiUtilsState.storedScreen.getTitle().getString();
			chatIfEnabled("Loaded GUI: title=\"" + title + "\", syncId="
				+ UiUtilsState.storedMenu.containerId + ", revision=" + UiUtilsState.storedMenu.getStateId());
		} catch (Throwable ignored) {}
	}

	public static void chatIfEnabled(String msg) {
		if (!UiUtilsState.isUiEnabled() || !UiUtilsSettings.get().logToChat)
			return;
		Minecraft mc = Minecraft.getInstance();
		if (mc.player != null)
			mc.player.sendSystemMessage(Component.literal("[UI-Utils] " + msg));
	}

	public static void renderSyncInfo(Minecraft mc, GuiGraphicsExtractor graphics,
		AbstractContainerMenu menu) {
		if (menu == null)
			return;
		graphics.text(mc.font, "Sync Id: " + menu.containerId, 200, 5, 0xFFFFFF, false);
		graphics.text(mc.font, "Revision: " + menu.getStateId(), 200, 35, 0xFFFFFF, false);
	}

	public static int getUiWidgetRows() {
		return 16;
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
		UiUtilsState.storedScreen = mc.screen;
		UiUtilsState.storedMenu = mc.player.containerMenu;
		UiUtilsState.savedScreens.put(key, mc.screen);
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
		mc.setScreen(screen);
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

	public static int addUiWidgets(Minecraft mc, int baseX, int baseY, int spacing,
		Consumer<AbstractWidget> adder) {
		final int fullWidth = 160;
		final int halfWidth = (fullWidth - spacing) / 2;
		final String defaultSlot = "default";

		adder.accept(styledButton("Settings", b -> {
			mc.setScreen(new UiUtilsSettingsScreen(mc.screen));
		}, baseX, baseY, fullWidth, 20));
		int y = baseY + 20 + spacing;

		adder.accept(styledButton("Command scanner", b -> {
			mc.setScreen(new UiUtilsCommandScannerScreen(mc.screen));
		}, baseX, y, fullWidth, 20));
		y += 20 + spacing;

		adder.accept(styledButton("Advanced Packet Tool", b -> {
			AdvancedPacketTool.openScreen(mc.screen);
		}, baseX, y, fullWidth, 20));
		y += 20 + spacing;

		adder.accept(styledButton("Close without packet", b -> {
			mc.setScreen(null);
			chatIfEnabled("Closed GUI without packet");
		}, baseX, y, fullWidth, 20));
		y += 20 + spacing;

		adder.accept(styledButton("De-sync", b -> {
			if (mc.getConnection() != null && mc.player != null)
				mc.getConnection().send(new ServerboundContainerClosePacket(mc.player.containerMenu.containerId));
			else
				LOGGER.warn("Minecraft connection or player was null while using 'De-sync'.");
			chatIfEnabled("De-synced (sent close packet)");
		}, baseX, y, fullWidth, 20));
		y += 20 + spacing;

		adder.accept(styledButton("Send packets: " + UiUtilsState.sendUiPackets, b -> {
			UiUtilsState.sendUiPackets = !UiUtilsState.sendUiPackets;
			b.setMessage(Component.literal("Send packets: " + UiUtilsState.sendUiPackets));
			chatIfEnabled("Send packets: " + UiUtilsState.sendUiPackets);
		}, baseX, y, fullWidth, 20));
		y += 20 + spacing;

		adder.accept(styledButton("Delay packets: " + UiUtilsState.delayUiPackets, b -> {
			UiUtilsState.delayUiPackets = !UiUtilsState.delayUiPackets;
			b.setMessage(Component.literal("Delay packets: " + UiUtilsState.delayUiPackets));
			if (!UiUtilsState.delayUiPackets && !UiUtilsState.delayedUiPackets.isEmpty() && mc.getConnection() != null) {
				for (Packet<?> packet : UiUtilsState.delayedUiPackets)
					mc.getConnection().send(packet);
				if (mc.player != null)
					mc.player.sendSystemMessage(Component.literal("Sent " + UiUtilsState.delayedUiPackets.size() + " packets."));
				UiUtilsState.delayedUiPackets.clear();
				refreshQueueCounterButtons();
			}
			chatIfEnabled("Delay packets: " + UiUtilsState.delayUiPackets);
		}, baseX, y, fullWidth, 20));
		y += 20 + spacing;


		adder.accept(styledButton("Leave & send packets", b -> {
			int sent = sendQueuedPackets(mc, 1);
			UiUtilsState.delayUiPackets = false;
			UiUtilsState.delayedUiPackets.clear();
			refreshQueueCounterButtons();
			mc.setScreen(null);
			chatIfEnabled("Left GUI and sent queued packets (" + sent + ")");
		}, baseX, y, fullWidth, 20));
		y += 20 + spacing;

		adder.accept(styledButton("Disconnect & send packets", b -> {
			int sent = sendQueuedPackets(mc, 1);
			UiUtilsState.delayUiPackets = false;
			UiUtilsState.delayedUiPackets.clear();
			refreshQueueCounterButtons();
			UiUtilsDisconnect.disconnectWithConfiguredMethod(mc);
			chatIfEnabled("Disconnected and sent queued packets (" + sent + ")");
		}, baseX, y, fullWidth, 20));
		y += 20 + spacing;

		adder.accept(styledButton("Fabricate packet", b -> {
			if (mc.screen instanceof net.minecraft.client.gui.screens.inventory.AbstractContainerScreen) {
				UiUtilsState.fabricateOverlayOpen = !UiUtilsState.fabricateOverlayOpen;
				chatIfEnabled("Fabricate overlay: " + (UiUtilsState.fabricateOverlayOpen ? "opened" : "closed"));
			} else {
				chatIfEnabled("Fabricate packet works inside container screens.");
			}
		}, baseX, y, fullWidth, 20));
		y += 20 + spacing;

		adder.accept(styledButton("Copy GUI Title JSON", b -> {
			try {
				if (mc.screen == null)
					throw new IllegalStateException("Minecraft screen was null.");
				String json = new Gson().toJson(ComponentSerialization.CODEC.encodeStart(JsonOps.INSTANCE, mc.screen.getTitle()).getOrThrow());
				mc.keyboardHandler.setClipboard(json);
				chatIfEnabled("Copied GUI title JSON to clipboard");
			} catch (IllegalStateException e) {
				LOGGER.error("Error while copying title JSON to clipboard", e);
				chatIfEnabled("Failed to copy GUI title JSON");
			}
		}, baseX, y, fullWidth, 20));
		y += 20 + spacing;

		adder.accept(styledButton("Save GUI", b -> {
			if (saveCurrentGuiToSlot(mc, defaultSlot))
				chatIfEnabled("Saved GUI to slot \"" + defaultSlot + "\"");
		}, baseX, y, halfWidth, 20));

		adder.accept(styledButton("Load GUI", b -> {
			if (loadGuiFromSlot(mc, defaultSlot))
				chatIfEnabled("Loaded GUI from slot \"" + defaultSlot + "\"");
			else
				chatIfEnabled("No saved GUI in slot \"" + defaultSlot + "\"");
		}, baseX + halfWidth + spacing, y, halfWidth, 20));
		y += 20 + spacing;

		adder.accept(styledButton("Clear Queue", b -> {
			int cleared = clearQueuedPackets();
			chatIfEnabled("Cleared queued packets (" + cleared + ")");
		}, baseX, y, halfWidth, 20));

		UiUtilsColoredButton queueButton = styledButton("Queue: " + UiUtilsState.delayedUiPackets.size(),
			b -> b.setMessage(Component.literal("Queue: " + UiUtilsState.delayedUiPackets.size())),
			baseX + halfWidth + spacing, y, halfWidth, 20);
		adder.accept(queueButton);
		queueCounterButtons.put(queueButton, Boolean.TRUE);
		y += 20 + spacing;

		adder.accept(styledButton("Resync Inv", b -> {
			if (mc.player != null && tryResyncInventory(mc.player.containerMenu))
				chatIfEnabled("Inventory resynced");
			else
				chatIfEnabled("Failed to resync inventory");
		}, baseX, y, halfWidth, 20));

		adder.accept(styledButton("Disconnect", b -> {
			UiUtilsDisconnect.disconnectWithConfiguredMethod(mc);
		}, baseX + halfWidth + spacing, y, halfWidth, 20));
		y += 20 + spacing;

		UiUtilsColoredButton spamButton = styledButton("Spam (x" + UiUtilsState.spamCount + ")", b -> {
			int sent = sendQueuedPackets(mc, UiUtilsState.spamCount);
			chatIfEnabled("Spammed queued packets (" + sent + ")");
		}, baseX + 32, y, fullWidth - 64, 20);
		adder.accept(styledButton("-", b -> {
			if (UiUtilsState.spamCount > 1)
				UiUtilsState.spamCount--;
			spamButton.setMessage(Component.literal("Spam (x" + UiUtilsState.spamCount + ")"));
		}, baseX, y, 30, 20));
		adder.accept(spamButton);
		adder.accept(styledButton("+", b -> {
			if (UiUtilsState.spamCount < 100)
				UiUtilsState.spamCount++;
			spamButton.setMessage(Component.literal("Spam (x" + UiUtilsState.spamCount + ")"));
		}, baseX + fullWidth - 30, y, 30, 20));
		y += 20 + spacing;

		adder.accept(styledButton("Send One", b -> {
			boolean sent = sendOneQueuedPacket(mc);
			chatIfEnabled(sent ? "Sent one queued packet" : "No queued packets to send");
		}, baseX, y, halfWidth, 20));

		adder.accept(styledButton("Pop Last", b -> {
			boolean popped = popLastQueuedPacket();
			chatIfEnabled(popped ? "Removed last queued packet" : "No queued packets to remove");
		}, baseX + halfWidth + spacing, y, halfWidth, 20));
		return y + 20;
	}

	public static UiUtilsColoredButton styledButton(String text,
		UiUtilsColoredButton.PressAction onPress, int x, int y, int width,
		int height) {
		return UiUtilsColoredButton.of(x, y, width, height, text, onPress);
	}

	public static EditBox createChatField(Minecraft mc, Font font, int x, int y) {
		EditBox field = new EditBox(font, x, y, 160, 20, Component.literal("Chat ...")) {
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
							mc.getConnection().sendCommand(text.replaceFirst(Pattern.quote("/"), ""));
						else
							mc.getConnection().sendChat(text);
					} else {
						LOGGER.warn("Minecraft player/connection was null while sending chat.");
					}
					setValue("");
					return true;
				}
				return super.keyPressed(keyEvent);
			}
		};
		field.setMaxLength(256);
		field.setHint(Component.literal("Chat ..."));
		return field;
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









