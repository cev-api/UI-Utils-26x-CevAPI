package com.ui_utils.uiutils;

import com.ui_utils.packettools.AdvancedPacketTool;
import com.ui_utils.uiutils.macro.UiUtilsMacroExecutor;
import com.ui_utils.uiutils.macro.UiUtilsMacroIo;
import com.ui_utils.uiutils.macro.UiUtilsMacroManager;
import java.util.Locale;
import java.util.StringJoiner;
import net.minecraft.client.Minecraft;

public final class UiUtilsCommandSystem {
	private static final String PREFIX = "[UI-Utils] ";
	public static final String ROOT_COMMAND = ".uiutils";
	public static final String ALT_ROOT_COMMAND = "uiutils";
	public static final String[] SUBCOMMANDS = {"help", "enable", "disable",
		"close", "desync", "apt", "advancedpacketscanner",
		"advancedpackettool", "chat", "screen", "plugins", "legacyplugins",
		"lplugins", "commands",
		"commandscan", "cmdscan", "queue", "packethud", "phud", "hud",
		"delay", "sendpackets", "sendui", "autoduper", "duper",
		"closedelay", "cmddelay", "commanddelay", "disconnectmethod",
		"dcmethod", "timeout", "lagmethod", "settings", "macro", "macros"};

	private UiUtilsCommandSystem() {}

	public static String execute(String input) {
		if (input == null || input.isBlank())
			return PREFIX + "No command.";

		String[] parts = input.trim().split("\\s+", 2);
		String command = parts[0].toLowerCase(Locale.ROOT);
		String args = parts.length > 1 ? parts[1] : "";

		return switch (command) {
			case "help" -> help();
			case "enable" -> setEnabled(true);
			case "disable" -> setEnabled(false);
			case "close" -> close();
			case "desync" -> desync();
			case "apt", "advancedpacketscanner", "advancedpackettool" -> openApt();
			case "chat" -> chat(args);
			case "screen" -> screen(args);
			case "plugins" -> UiUtilsPluginScanner.startScan();
			case "legacyplugins", "lplugins" -> UiUtilsLegacyPluginScanner.startScan();
			case "commands", "commandscan", "cmdscan" -> UiUtilsCommandScanner.startScan();
			case "queue" -> queue(args);
			case "packethud", "phud", "hud" -> packetHud(args);
			case "delay" -> delay(args);
			case "closedelay" -> closeDelay(args);
			case "cmddelay", "commanddelay" -> commandDelay(args);
			case "sendpackets", "sendui" -> sendPackets(args);
			case "autoduper", "duper" -> autoduper(args);
			case "disconnectmethod", "dcmethod" -> disconnectMethod(args);
			case "timeout" -> timeout(args);
			case "lagmethod" -> lagMethod(args);
			case "settings" -> openSettings();
			case "macro", "macros" -> macro(args);
			default -> PREFIX + "Unknown command: " + command;
		};
	}

	public static boolean isUiUtilsCommand(String text) {
		if (text == null)
			return false;
		String lower = text.toLowerCase(Locale.ROOT);
		return lower.equals(ROOT_COMMAND) || lower.startsWith(ROOT_COMMAND + " ")
			|| lower.equals(ALT_ROOT_COMMAND)
			|| lower.startsWith(ALT_ROOT_COMMAND + " ");
	}

	public static String extractCommandBody(String text) {
		if (text == null || text.isBlank())
			return "";
		if (text.equalsIgnoreCase(ROOT_COMMAND)
			|| text.equalsIgnoreCase(ALT_ROOT_COMMAND))
			return "help";
		String lower = text.toLowerCase(Locale.ROOT);
		if (lower.startsWith(ROOT_COMMAND + " "))
			return text.substring(ROOT_COMMAND.length()).trim();
		if (lower.startsWith(ALT_ROOT_COMMAND + " "))
			return text.substring(ALT_ROOT_COMMAND.length()).trim();
		return "";
	}
	
	public static boolean isKnownSubcommand(String token) {
		if(token == null || token.isBlank())
			return false;
		String lower = token.toLowerCase(Locale.ROOT);
		for(String command : SUBCOMMANDS)
			if(command.equals(lower))
				return true;
		return false;
	}

	private static String help() {
		return PREFIX + "Usage: .uiutils <command> (or uiutils <command>)\n" + PREFIX
			+ "Commands: help, enable, disable, close, desync, apt, chat, screen, plugins, commands, queue, packethud, delay, closedelay, commanddelay, sendpackets, autoduper, disconnectmethod, timeout, lagmethod, settings, macro";
	}

	private static String macro(String args) {
		String[] parts = args == null ? new String[0] : args.trim().split("\\s+", 3);
		String action = parts.length == 0 || parts[0].isBlank() ? "list" : parts[0].toLowerCase(Locale.ROOT);
		return switch (action) {
			case "list" -> {
				var all = UiUtilsMacroManager.get().getAll();
				if (all.isEmpty()) yield PREFIX + "No macros.";
				StringJoiner joiner = new StringJoiner(", ");
				all.forEach(m -> joiner.add(m.name));
				yield PREFIX + "Macros: " + joiner;
			}
			case "run", "start" -> {
				if (parts.length < 2 || parts[1].isBlank()) yield PREFIX + "Usage: macro run <name>";
				boolean ok = UiUtilsMacroManager.get().execute(parts[1]);
				yield ok ? PREFIX + "Running macro: " + parts[1] : PREFIX + "Macro not found: " + parts[1];
			}
			case "stop" -> {
				UiUtilsMacroExecutor.stop();
				yield PREFIX + "Stopped macro execution.";
			}
			case "delete", "remove" -> {
				if (parts.length < 2 || parts[1].isBlank()) yield PREFIX + "Usage: macro delete <name>";
				boolean ok = UiUtilsMacroManager.get().remove(parts[1]);
				yield ok ? PREFIX + "Deleted macro: " + parts[1] : PREFIX + "Macro not found: " + parts[1];
			}
			case "import" -> {
				if (parts.length < 2 || parts[1].isBlank()) yield PREFIX + "Usage: macro import <path> [name]";
				String preferred = parts.length >= 3 ? parts[2] : "";
				yield PREFIX + UiUtilsMacroIo.importMacro(parts[1], preferred);
			}
			case "export" -> {
				if (parts.length < 3 || parts[1].isBlank() || parts[2].isBlank()) yield PREFIX + "Usage: macro export <name> <path>";
				yield PREFIX + UiUtilsMacroIo.exportMacro(parts[1], parts[2]);
			}
			case "status" -> {
				if (UiUtilsMacroExecutor.isRunning()) yield PREFIX + "Running: " + UiUtilsMacroExecutor.currentName();
				yield PREFIX + "No macro running.";
			}
			default -> PREFIX + "Usage: macro <list|run|stop|delete|import|export|status>";
		};
	}

	private static String setEnabled(boolean enabled) {
		UiUtilsState.enabled = enabled;
		return PREFIX + "UI-Utils is now " + (enabled ? "enabled." : "disabled.");
	}

	private static String close() {
		UiUtils.closeScreenWithConfiguredDelay(Minecraft.getInstance());
		return PREFIX + "Closed current screen"
			+ formatDelay(UiUtilsSettings.get().uiCloseDelayTicks) + ".";
	}

	private static String desync() {
		Minecraft mc = Minecraft.getInstance();
		if (mc.getConnection() == null || mc.player == null)
			return PREFIX + "Not connected.";

		int syncId = mc.player.containerMenu.containerId;
		UiUtils.sendClosePacketWithConfiguredDelay(mc);
		return PREFIX + "Queued close packet for syncId " + syncId
			+ formatDelay(UiUtilsSettings.get().uiCloseDelayTicks) + ".";
	}

	private static String chat(String args) {
		Minecraft mc = Minecraft.getInstance();
		if (args.isBlank())
			return PREFIX + "Usage: chat <message>";
		if (mc.player == null || mc.getConnection() == null)
			return PREFIX + "Not connected.";

		if (args.startsWith("/"))
			UiUtils.sendCommandWithConfiguredDelay(mc, args.substring(1));
		else
			UiUtils.sendChatWithConfiguredDelay(mc, args);

		return PREFIX + "Sent" + formatDelay(UiUtilsSettings.get().uiCommandDelayTicks)
			+ ".";
	}
	
	private static String openApt() {
		Minecraft mc = Minecraft.getInstance();
		AdvancedPacketTool.openScreen(mc.screen);
		return PREFIX + "Opened Advanced Packet Tool.";
	}

	private static String screen(String args) {
		Minecraft mc = Minecraft.getInstance();
		String[] parts = args.split("\\s+", 3);
		if (parts.length == 0 || parts[0].isBlank())
			return PREFIX + "Usage: screen <save|load|list|info> [slot]";

		String action = parts[0].toLowerCase(Locale.ROOT);
		String slot = parts.length > 1 ? parts[1] : "";
		return switch (action) {
			case "save" -> {
				if (slot.isBlank())
					yield PREFIX + "Usage: screen save <slot>";
				boolean ok = UiUtils.saveCurrentGuiToSlot(mc, slot);
				yield ok ? PREFIX + "Saved GUI to slot \"" + slot + "\"."
					: PREFIX + "No GUI to save.";
			}
			case "load" -> {
				if (slot.isBlank())
					yield PREFIX + "Usage: screen load <slot>";
				boolean ok = UiUtils.loadGuiFromSlot(mc, slot);
				yield ok ? PREFIX + "Loaded GUI from slot \"" + slot + "\"."
					: PREFIX + "No GUI in slot \"" + slot + "\".";
			}
			case "list" -> {
				if (UiUtilsState.savedScreens.isEmpty())
					yield PREFIX + "No saved slots.";
				StringJoiner joiner = new StringJoiner(", ");
				UiUtilsState.savedScreens.keySet().forEach(joiner::add);
				yield PREFIX + "Slots: " + joiner;
			}
			case "info" -> {
				if (slot.isBlank())
					yield PREFIX + "Usage: screen info <slot>";
				String key = slot.toLowerCase(Locale.ROOT);
				var screen = UiUtilsState.savedScreens.get(key);
				if (screen == null)
					yield PREFIX + "No GUI in slot \"" + slot + "\".";
				yield PREFIX + "Slot \"" + slot + "\" -> "
					+ screen.getClass().getSimpleName();
			}
			default -> PREFIX + "Usage: screen <save|load|list|info> [slot]";
		};
	}

	private static String queue(String args) {
		Minecraft mc = Minecraft.getInstance();
		String[] parts = args.split("\\s+", 2);
		if (parts.length == 0 || parts[0].isBlank())
			return PREFIX + "Queue size: " + UiUtilsState.delayedUiPackets.size();

		String action = parts[0].toLowerCase(Locale.ROOT);
		return switch (action) {
			case "clear" -> PREFIX + "Cleared " + UiUtils.clearQueuedPackets()
				+ " queued packet(s).";
			case "sendone" -> UiUtils.sendOneQueuedPacket(mc)
				? PREFIX + "Sent one queued packet."
				: PREFIX + "No queued packets.";
			case "poplast" -> UiUtils.popLastQueuedPacket()
				? PREFIX + "Removed last queued packet."
				: PREFIX + "No queued packets.";
			case "spam" -> {
				int times = 1;
				if (parts.length > 1 && UiUtils.isInteger(parts[1]))
					times = Math.max(1, Integer.parseInt(parts[1]));
				int sent = UiUtils.sendQueuedPackets(mc, times);
				yield PREFIX + "Sent " + sent + " packet(s).";
			}
			case "list" -> PREFIX + "Queue size: " + UiUtilsState.delayedUiPackets.size();
			default -> PREFIX + "Usage: queue <list|clear|sendone|poplast|spam [times]>";
		};
	}

	private static String openSettings() {
		Minecraft mc = Minecraft.getInstance();
		var parent = mc.screen;
		mc.execute(() -> mc.setScreen(new UiUtilsSettingsScreen(parent)));
		return PREFIX + "Opened settings.";
	}

	private static String autoduper(String args) {
		String[] parts = args == null ? new String[0] : args.trim().split("\\s+", 2);
		String action = parts.length == 0 || parts[0].isBlank() ? "status"
			: parts[0].toLowerCase(Locale.ROOT);
		String value = parts.length > 1 ? parts[1].trim() : "";
		return switch(action) {
			case "open", "screen" -> {
				Minecraft mc = Minecraft.getInstance();
				var parent = mc.screen;
				mc.execute(() -> mc.setScreen(new UiUtilsAutoduperScreen(parent)));
				yield PREFIX + "Opened Autoduper.";
			}
			case "start" -> {
				UiUtilsAutoduper.start();
				yield PREFIX + UiUtilsAutoduper.getStatus();
			}
			case "stop" -> {
				UiUtilsAutoduper.stop("Stopped by command");
				yield PREFIX + "Stopped Autoduper.";
			}
			case "status" -> PREFIX + UiUtilsAutoduper.summary();
			case "slot" -> {
				if(!UiUtils.isInteger(value))
					yield PREFIX + "Usage: autoduper slot <slotId>";
				UiUtilsSettings.get().autoduperTargetSlot =
					Math.max(0, Integer.parseInt(value));
				UiUtilsSettings.save();
				yield PREFIX + "Autoduper slot set to "
					+ UiUtilsSettings.get().autoduperTargetSlot + ".";
			}
			case "command", "cmd" -> {
				UiUtilsSettings.get().autoduperOpenCommand = value;
				UiUtilsSettings.save();
				yield PREFIX + "Autoduper open command set.";
			}
			case "attempt" -> {
				if(!UiUtils.isInteger(value))
					yield PREFIX + "Usage: autoduper attempt <0|attemptNumber>";
				UiUtilsSettings.get().autoduperSingleAttempt =
					Math.max(0, Integer.parseInt(value));
				UiUtilsSettings.save();
				yield PREFIX + "Autoduper single attempt set to "
					+ UiUtilsSettings.get().autoduperSingleAttempt + ".";
			}
			case "hybrid" -> {
				if(!value.isBlank())
					UiUtilsSettings.get().autoduperOpenCommand = value;
				UiUtilsSettings.get().autoduperHybridOpen = true;
				UiUtilsSettings.get().autoduperReopenCommand = true;
				UiUtilsSettings.get().autoduperReopenDoubleCommand = true;
				UiUtilsSettings.get().autoduperReopenInteract = true;
				UiUtilsSettings.save();
				yield PREFIX
					+ "Enabled hybrid command+interact autoduper opens.";
			}
			default -> PREFIX
				+ "Usage: autoduper <open|start|stop|status|slot|command|attempt|hybrid [openCommand]>";
		};
	}
	
	private static String packetHud(String args) {
		String mode = args == null ? "" : args.trim().toLowerCase(Locale.ROOT);
		if(mode.isBlank() || mode.equals("toggle") || mode.equals("cycle"))
			UiUtilsSettings.get().packetHudPosition =
				UiUtilsSettings.get().packetHudPosition.next();
		else if(mode.equals("on"))
			UiUtilsSettings.get().packetHudPosition =
				UiUtilsSettings.PacketHudPosition.TOP_LEFT;
		else if(mode.equals("off"))
			UiUtilsSettings.get().packetHudPosition =
				UiUtilsSettings.PacketHudPosition.OFF;
		else if(mode.equals("topleft"))
			UiUtilsSettings.get().packetHudPosition =
				UiUtilsSettings.PacketHudPosition.TOP_LEFT;
		else if(mode.equals("topright"))
			UiUtilsSettings.get().packetHudPosition =
				UiUtilsSettings.PacketHudPosition.TOP_RIGHT;
		else if(mode.equals("bottomleft"))
			UiUtilsSettings.get().packetHudPosition =
				UiUtilsSettings.PacketHudPosition.BOTTOM_LEFT;
		else if(mode.equals("bottomright"))
			UiUtilsSettings.get().packetHudPosition =
				UiUtilsSettings.PacketHudPosition.BOTTOM_RIGHT;
		else
			return PREFIX + "Usage: packethud <cycle|toggle|on|off|topleft|topright|bottomleft|bottomright>";
		UiUtilsSettings.get().packetHudEnabled =
			UiUtilsSettings.get().packetHudPosition.isEnabled();
		UiUtilsSettings.save();
		return PREFIX + "Packet HUD: "
			+ UiUtilsSettings.get().packetHudPosition.label();
	}
	
	private static String delay(String args) {
		String mode = args == null ? "" : args.trim().toLowerCase(Locale.ROOT);
		if(mode.isBlank() || mode.equals("toggle"))
			UiUtilsState.delayUiPackets = !UiUtilsState.delayUiPackets;
		else if(mode.equals("on"))
			UiUtilsState.delayUiPackets = true;
		else if(mode.equals("off"))
			UiUtilsState.delayUiPackets = false;
		else
			return PREFIX + "Usage: delay <on|off|toggle>";
		return PREFIX + "Delay packets: "
			+ (UiUtilsState.delayUiPackets ? "ON" : "OFF");
	}

	private static String closeDelay(String args) {
		String value = args == null ? "" : args.trim();
		if(value.isBlank())
			return PREFIX + "Close delay ticks: "
				+ UiUtilsSettings.get().uiCloseDelayTicks;
		if(!UiUtils.isInteger(value))
			return PREFIX + "Usage: closedelay <ticks>";
		UiUtilsSettings.get().uiCloseDelayTicks =
			Math.max(0, Integer.parseInt(value));
		UiUtilsSettings.save();
		return PREFIX + "Close delay ticks set to "
			+ UiUtilsSettings.get().uiCloseDelayTicks + ".";
	}

	private static String commandDelay(String args) {
		String value = args == null ? "" : args.trim();
		if(value.isBlank())
			return PREFIX + "Command delay ticks: "
				+ UiUtilsSettings.get().uiCommandDelayTicks;
		if(!UiUtils.isInteger(value))
			return PREFIX + "Usage: commanddelay <ticks>";
		UiUtilsSettings.get().uiCommandDelayTicks =
			Math.max(0, Integer.parseInt(value));
		UiUtilsSettings.save();
		return PREFIX + "Command delay ticks set to "
			+ UiUtilsSettings.get().uiCommandDelayTicks + ".";
	}
	
	private static String sendPackets(String args) {
		String mode = args == null ? "" : args.trim().toLowerCase(Locale.ROOT);
		if(mode.isBlank() || mode.equals("toggle"))
			UiUtilsState.sendUiPackets = !UiUtilsState.sendUiPackets;
		else if(mode.equals("on"))
			UiUtilsState.sendUiPackets = true;
		else if(mode.equals("off"))
			UiUtilsState.sendUiPackets = false;
		else
			return PREFIX + "Usage: sendpackets <on|off|toggle>";
		return PREFIX + "Send packets: "
			+ (UiUtilsState.sendUiPackets ? "ON" : "OFF");
	}
	
	private static String disconnectMethod(String args) {
		String value = args == null ? "" : args.trim();
		if(value.isBlank() || value.equalsIgnoreCase("current"))
			return PREFIX + "Disconnect method: "
				+ UiUtilsDisconnect.getConfiguredMethod().name();
		if(value.equalsIgnoreCase("list")) {
			StringJoiner joiner = new StringJoiner(", ");
			for(UiUtilsDisconnect.Method method : UiUtilsDisconnect.Method.values())
				joiner.add(method.name());
			return PREFIX + "Disconnect methods: " + joiner;
		}
		try {
			UiUtilsDisconnect.setConfiguredMethod(
				UiUtilsDisconnect.Method.valueOf(value.toUpperCase(Locale.ROOT)));
			return PREFIX + "Disconnect method set to "
				+ UiUtilsDisconnect.getConfiguredMethod().name();
		}catch(Exception ignored) {
			return PREFIX + "Usage: disconnectmethod <list|current|METHOD>";
		}
	}
	
	private static String timeout(String args) {
		String value = args == null ? "" : args.trim();
		if(value.isBlank())
			return PREFIX + "Timeout seconds: "
				+ UiUtilsDisconnect.getConfiguredTimeoutSeconds();
		if(!UiUtils.isInteger(value))
			return PREFIX + "Usage: timeout <seconds>";
		int seconds = Math.max(1, Integer.parseInt(value));
		UiUtilsDisconnect.setConfiguredTimeoutSeconds(seconds);
		return PREFIX + "Timeout seconds set to "
			+ UiUtilsDisconnect.getConfiguredTimeoutSeconds();
	}
	
	private static String lagMethod(String args) {
		String value = args == null ? "" : args.trim();
		if(value.isBlank() || value.equalsIgnoreCase("current"))
			return PREFIX + "Timeout lag method: "
				+ UiUtilsDisconnect.getConfiguredLagMethod().name();
		if(value.equalsIgnoreCase("list")) {
			StringJoiner joiner = new StringJoiner(", ");
			for(UiUtilsDisconnect.LagMethod method : UiUtilsDisconnect.LagMethod.values())
				joiner.add(method.name());
			return PREFIX + "Lag methods: " + joiner;
		}
		try {
			UiUtilsDisconnect.setConfiguredLagMethod(
				UiUtilsDisconnect.LagMethod.valueOf(
					value.toUpperCase(Locale.ROOT)));
			return PREFIX + "Timeout lag method set to "
				+ UiUtilsDisconnect.getConfiguredLagMethod().name();
		}catch(Exception ignored) {
			return PREFIX + "Usage: lagmethod <list|current|METHOD>";
		}
	}

	private static String formatDelay(int ticks) {
		return ticks > 0 ? " after " + ticks + " tick(s)" : "";
	}
}
