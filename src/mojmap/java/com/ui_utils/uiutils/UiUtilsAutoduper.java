package com.ui_utils.uiutils;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.world.InteractionHand;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ServerboundContainerClosePacket;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

public final class UiUtilsAutoduper {
	private static final Strategy[] STRATEGIES = buildStrategies();
	private static boolean running;
	private static int attempt;
	private static Strategy[] runPlan = STRATEGIES;
	private static int ticksUntilNext;
	private static ItemStack targetStack = ItemStack.EMPTY;
	private static int activeTargetSlot = -1;
	private static int baselineCount;
	private static int lastObservedCount;
	private static Strategy activeStrategy = STRATEGIES[0];
	private static int activeStrategyNumber = 1;
	private static Phase phase = Phase.IDLE;
	private static String status = "Idle";
	private static Screen savedScreen;
	private static AbstractContainerMenu savedMenu;
	private static int validationGroundCountBefore;
	private static int validationInventoryCountBefore;
	private static int validationTicks;
	private static int setupTicks;
	private static int abortHoldTicks;
	private static int pendingClosePacketContainerId = -1;
	private static boolean pluginInventorySeedMode;
	private static boolean resumeAfterReconnect;
	private static int resumeAttempt;
	private static boolean resumingRun;
	private static boolean reopenRecoveryTried;
	private static boolean setupReopenDesyncTried;
	private static ReplayStartMode[] replayStartModes = {ReplayStartMode.DEFAULT};
	private static int replayStartIndex;
	private static ReplayStartMode replayStartMode = ReplayStartMode.DEFAULT;

	private UiUtilsAutoduper() {}

	public static void start() {
		Minecraft mc = Minecraft.getInstance();
		if(mc.player == null) {
			status = "Not connected";
			return;
		}
		int slot = UiUtilsSettings.get().autoduperTargetSlot;
		if(slot < 0) {
			status = "Target slot must be >= 0";
			UiUtils.chatIfEnabled("Autoduper: " + status);
			return;
		}
		targetStack = ItemStack.EMPTY;
		activeTargetSlot = slot;
		pluginInventorySeedMode = false;
		replayStartModes = new ReplayStartMode[] {ReplayStartMode.DEFAULT};
		replayStartIndex = 0;
		replayStartMode = ReplayStartMode.DEFAULT;
		setupReopenDesyncTried = false;
		resumeAfterReconnect = false;
		resumeAttempt = 0;
		resumingRun = false;
		baselineCount = 0;
		lastObservedCount = 0;
		runPlan = buildRunPlan();
		if(runPlan.length == 0) {
			status = "No Autoduper strategy categories are enabled";
			UiUtils.chatIfEnabled("Autoduper: " + status);
			return;
		}
		attempt = 0;
		abortHoldTicks = 0;
		pendingClosePacketContainerId = -1;
		ticksUntilNext = 1;
		phase = Phase.ACQUIRE_TARGET;
		activeStrategy = runPlan[0];
		activeStrategyNumber = activeStrategy.number;
		savedScreen = mc.screen;
		savedMenu = mc.player.containerMenu;
		running = true;
		status = "Waiting for target slot " + slot + " to become available";
		UiUtils.chatIfEnabled("Autoduper: " + status);
	}

	public static void stop(String reason) {
		running = false;
		resumeAfterReconnect = false;
		resumeAttempt = 0;
		resumingRun = false;
		setupReopenDesyncTried = false;
		replayStartModes = new ReplayStartMode[] {ReplayStartMode.DEFAULT};
		replayStartIndex = 0;
		replayStartMode = ReplayStartMode.DEFAULT;
		phase = Phase.IDLE;
		UiUtilsState.delayUiPackets = false;
		abortHoldTicks = 0;
		pendingClosePacketContainerId = -1;
		status = reason == null || reason.isBlank() ? "Stopped" : reason;
		UiUtils.chatIfEnabled("Autoduper: " + status);
	}

	public static void abort() {
		stop("Aborted by user");
	}

	public static boolean isRunning() {
		return running;
	}

	public static String getStatus() {
		return status;
	}

	public static String getStrategyLabel() {
		return activeStrategy.label;
	}

	public static int getAttempt() {
		return attempt;
	}

	public static int getStrategyCount() {
		return STRATEGIES.length;
	}

	public static int getRunPlanCount() {
		return runPlan.length;
	}

	public static int getEnabledStrategyCount() {
		return buildRunPlan().length;
	}

	public static void onClientTick(Minecraft mc) {
		if(!running) {
			tickReconnectResume(mc);
			return;
		}
		if(mc == null || mc.player == null || mc.getConnection() == null) {
			pauseForReconnect("Paused: waiting for reconnect");
			return;
		}
		tickAbortKey(mc);
		if(ticksUntilNext-- > 0)
			return;
		ticksUntilNext = Math.max(1, UiUtilsSettings.get().autoduperStepDelayTicks);
		try {
			tick(mc);
		}catch(Throwable t) {
			UiUtils.LOGGER.warn("Autoduper failed during {}", phase, t);
			stop("Error: " + t.getClass().getSimpleName());
		}
	}

	private static void tickReconnectResume(Minecraft mc) {
		if(!resumeAfterReconnect)
			return;
		if(mc == null || mc.player == null || mc.getConnection() == null)
			return;
		runPlan = buildRunPlan();
		if(runPlan.length == 0) {
			stop("No Autoduper strategy categories are enabled after reconnect");
			return;
		}
		attempt = Math.min(Math.max(0, resumeAttempt), getAttemptLimit());
		if(attempt >= getAttemptLimit()) {
			stop("No confirmed dupe after reconnect; attempts exhausted");
			return;
		}
		if(targetStack.isEmpty())
			activeTargetSlot = UiUtilsSettings.get().autoduperTargetSlot;
		baselineCount = 0;
		lastObservedCount = 0;
		setupTicks = 0;
		ticksUntilNext = 1;
		phase = Phase.ACQUIRE_TARGET;
		running = true;
		resumeAfterReconnect = false;
		resumingRun = true;
		status = "Reconnected; resuming Autoduper at attempt "
			+ (attempt + 1) + "/" + getAttemptLimit();
		UiUtils.chatIfEnabled("Autoduper: " + status);
	}

	private static void pauseForReconnect(String reason) {
		resumeAfterReconnect = true;
		resumeAttempt = attempt;
		running = false;
		phase = Phase.IDLE;
		UiUtilsState.delayUiPackets = false;
		status = reason == null || reason.isBlank()
			? "Paused: waiting for reconnect" : reason;
		UiUtils.chatIfEnabled("Autoduper: " + status);
	}

	private static void tick(Minecraft mc) {
		AbstractContainerMenu menu = mc.player.containerMenu;
		if(menu == null) {
			stop("Stopped: no container menu");
			return;
		}
		if(phase == Phase.ACQUIRE_TARGET) {
			acquireTarget(mc, menu);
			return;
		}
		if(isSetupPhase(phase)) {
			tickSetupCycle(mc, menu);
			return;
		}

		if(phase == Phase.OBSERVE) {
			refreshActiveTargetSlot(menu);
			int observed = countMatching(menu, targetStack);
			lastObservedCount = observed;
			if(observed > baselineCount) {
				if(UiUtilsSettings.get().autoduperDropValidation) {
					status = "Potential dupe found; validating by drop";
					phase = Phase.VALIDATE_DROP;
				}else {
					stop("Success on dupe attempt #" + activeStrategyNumber
						+ ": count " + baselineCount + " -> " + observed);
					return;
				}
			}
		}

		switch(phase) {
			case PREPARE -> prepare(mc);
			case RUN_STRATEGY -> runStrategy(mc);
			case WAIT_REOPEN -> waitForReopen(mc);
			case OBSERVE -> observe(mc);
			case VALIDATE_DROP -> validateByDrop(mc);
			case VALIDATE_OBSERVE -> validateDropResult(mc);
			case IDLE, ACQUIRE_TARGET, SETUP_STORE, SETUP_STORE_OBSERVE,
				SETUP_RETRIEVE_FROM_CONTAINER,
				SETUP_RETRIEVE_CONTAINER_OBSERVE,
				SETUP_CLOSE_NORMAL, SETUP_REOPEN_FOR_RETRIEVE,
				SETUP_RETRIEVE_OBSERVE, SETUP_CLOSE_WITHOUT_PACKET,
				SETUP_DESYNC_RECOVER_CLOSE, SETUP_FINAL_REOPEN,
				SETUP_FINAL_REOPEN_OBSERVE -> {}
		}
	}

	private static void acquireTarget(Minecraft mc, AbstractContainerMenu menu) {
		if(!isPluginContainerScreenOpen(mc)) {
			applyReopen(mc, preferredOpenMode());
			status = "Waiting for plugin/container GUI to open before acquiring target";
			return;
		}
		int slot = activeTargetSlot;
		if(slot < 0 || slot >= menu.slots.size()) {
			applyReopen(mc, preferredOpenMode());
			status = "Waiting for slot " + slot + " in open container (" + menu.slots.size()
				+ " slots visible)";
			return;
		}
		if(!targetStack.isEmpty()
			&& !matches(menu.slots.get(slot).getItem(), targetStack)) {
			int foundSlot = firstPlayerInventorySlot(menu, targetStack);
			if(foundSlot < 0)
				foundSlot = firstContainerSlot(menu, targetStack);
			if(foundSlot < 0) {
				stop("Resume failed: original target item was not found");
				return;
			}
			slot = foundSlot;
			activeTargetSlot = foundSlot;
		}
		ItemStack stack = targetStack.isEmpty()
			? menu.slots.get(slot).getItem().copy() : targetStack.copy();
		if(stack.isEmpty()) {
			stop("Target slot is empty");
			return;
		}
		targetStack = stack;
		baselineCount = countMatching(menu, targetStack);
		lastObservedCount = baselineCount;
		setupTicks = 0;
		setupReopenDesyncTried = false;
		pluginInventorySeedMode = isPlayerInventorySlot(menu, slot);
		replayStartModes = buildReplayStartModes(menu);
		if(replayStartIndex >= replayStartModes.length)
			replayStartIndex = 0;
		replayStartMode = replayStartModes[replayStartIndex];
		if(pluginInventorySeedMode) {
			runPlan = buildRunPlan();
			if(runPlan.length == 0) {
				stop("No plugin-inventory seed Autoduper strategies are enabled");
				return;
			}
		}
		if(!resumingRun && pluginInventorySeedMode && replayStartIndex == 0)
			attempt = 0;
		phase = pluginInventorySeedMode ? Phase.SETUP_STORE : Phase.PREPARE;
		status = "Locked target slot " + slot + " with "
			+ targetStack.getHoverName().getString() + " x" + baselineCount
			+ (pluginInventorySeedMode
				? "; plugin GUI scan using " + replayStartMode.label : "");
		UiUtils.chatIfEnabled("Autoduper: " + status);
		if(!pluginInventorySeedMode && replayStartMode.startsFromContainer())
			closeForReplayStartMode(mc);
	}

	private static void tickSetupCycle(Minecraft mc, AbstractContainerMenu menu) {
		setupTicks++;
		switch(phase) {
			case SETUP_STORE -> {
				// Player-inventory starts are still plugin GUI scans: seed the
				// target through the configured/opened plugin inventory first.
				clickTracked(mc, activeTargetSlot, 0, ContainerInput.QUICK_MOVE);
				phase = Phase.SETUP_STORE_OBSERVE;
				setupTicks = 0;
				status = "Setup: seeded target from player slot into plugin GUI";
			}
			case SETUP_STORE_OBSERVE -> {
				int containerSlot = firstContainerSlot(menu, targetStack);
				if(containerSlot >= 0) {
					activeTargetSlot = containerSlot;
					if(replayStartMode.startsFromContainer()) {
						closeForReplayStartMode(mc);
						return;
					}
					phase = Phase.SETUP_CLOSE_NORMAL;
					setupTicks = 0;
					status = "Setup: target stored in plugin GUI slot "
						+ activeTargetSlot;
					return;
				}
				if(setupTicks > 20)
					stop("Setup failed: target did not move into plugin GUI");
			}
			case SETUP_RETRIEVE_FROM_CONTAINER -> {
				clickTracked(mc, activeTargetSlot, 0, ContainerInput.QUICK_MOVE);
				phase = Phase.SETUP_RETRIEVE_CONTAINER_OBSERVE;
				setupTicks = 0;
				status = "Setup: moved target from plugin GUI toward player inventory";
			}
			case SETUP_RETRIEVE_CONTAINER_OBSERVE -> {
				int playerSlot = firstPlayerInventorySlot(menu, targetStack);
				if(playerSlot >= 0) {
					activeTargetSlot = playerSlot;
					sendClosePacket(mc);
					mc.setScreen(null);
					phase = Phase.SETUP_FINAL_REOPEN;
					setupTicks = 0;
					status = "Setup: target now in player slot "
						+ activeTargetSlot + "; closed normally";
					return;
				}
				if(setupTicks > 20)
					stop("Setup failed: target did not move out to player inventory");
			}
			case SETUP_CLOSE_NORMAL -> {
				sendClosePacket(mc);
				mc.setScreen(null);
				phase = Phase.SETUP_REOPEN_FOR_RETRIEVE;
				setupTicks = 0;
				status = "Setup: closed plugin GUI normally";
			}
			case SETUP_REOPEN_FOR_RETRIEVE -> {
				applyReopen(mc, preferredOpenMode());
				phase = Phase.SETUP_RETRIEVE_OBSERVE;
				setupTicks = 0;
				status = "Setup: reopened plugin GUI to retrieve target";
			}
			case SETUP_RETRIEVE_OBSERVE -> {
				int containerSlot = firstContainerSlot(menu, targetStack);
				if(containerSlot >= 0) {
					clickTracked(mc, containerSlot, 0, ContainerInput.QUICK_MOVE);
					phase = Phase.SETUP_CLOSE_WITHOUT_PACKET;
					setupTicks = 0;
					status = "Setup: moved seed target back toward player inventory";
					return;
				}
				if(setupTicks > 40)
					stop("Setup failed: target was not found in reopened plugin GUI");
			}
			case SETUP_CLOSE_WITHOUT_PACKET -> {
				int playerSlot = firstPlayerInventorySlot(menu, targetStack);
				if(playerSlot < 0) {
					if(setupTicks > 20)
						stop("Setup failed: target did not return to player inventory");
					return;
				}
				activeTargetSlot = playerSlot;
				rememberClosePacketContainerId(mc);
				mc.setScreen(null);
				phase = Phase.SETUP_DESYNC_RECOVER_CLOSE;
				setupTicks = 0;
				status = "Setup: closed without packet with target in slot "
					+ activeTargetSlot;
			}
			case SETUP_DESYNC_RECOVER_CLOSE -> {
				if(setupTicks < 2)
					return;
				sendClosePacket(mc);
				setupReopenDesyncTried = true;
				phase = Phase.SETUP_FINAL_REOPEN;
				setupTicks = 0;
				status = "Setup: sent close packet after packetless close";
			}
			case SETUP_FINAL_REOPEN -> {
				applyReopen(mc, preferredOpenMode());
				phase = Phase.SETUP_FINAL_REOPEN_OBSERVE;
				setupTicks = 0;
				status = "Setup: waiting for final plugin GUI reopen";
			}
			case SETUP_FINAL_REOPEN_OBSERVE -> {
				if(!isPluginContainerScreenOpen(mc)) {
					if(setupTicks > 8 && !setupReopenDesyncTried) {
						sendClosePacket(mc);
						setupReopenDesyncTried = true;
						phase = Phase.SETUP_FINAL_REOPEN;
						setupTicks = 0;
						status = "Setup: reopen blocked; sent desync close packet";
						return;
					}
					if(setupTicks > 20 && tryAdvanceReplayStartMode())
						return;
					if(setupTicks > 20)
						stop("Setup failed: plugin GUI did not reopen");
					return;
				}
				refreshActiveTargetSlot(menu);
				baselineCount = countMatching(menu, targetStack);
				lastObservedCount = baselineCount;
				phase = Phase.PREPARE;
				resumingRun = false;
				setupTicks = 0;
				status = "Setup complete; scanning plugin GUI with seed slot "
					+ activeTargetSlot;
				UiUtils.chatIfEnabled("Autoduper: " + status);
			}
			default -> {}
		}
	}

	private static void prepare(Minecraft mc) {
		String prepare = cleanCommand(UiUtilsSettings.get().autoduperPrepareCommand);
		if(!prepare.isBlank())
			sendCommand(mc, prepare);
		phase = Phase.RUN_STRATEGY;
		status = "Prepared; trying " + activeStrategy.label;
	}

	private static void runStrategy(Minecraft mc) {
		if(attempt >= getAttemptLimit()) {
			stop("No confirmed dupe after " + attempt + " attempts");
			return;
		}
		activeStrategy = runPlan[attempt % runPlan.length];
		activeStrategyNumber = activeStrategy.number;
		attempt++;
		status = "Run " + attempt + "/" + getAttemptLimit()
			+ " | Strategy #" + activeStrategyNumber + " | slot "
			+ activeTargetSlot + ": " + activeStrategy.label;
		UiUtils.chatIfEnabled("Autoduper: " + status);
		activeStrategy.run(mc);
		phase = Phase.WAIT_REOPEN;
		setupTicks = 0;
		reopenRecoveryTried = false;
	}

	private static void waitForReopen(Minecraft mc) {
		setupTicks++;
		if(isPluginContainerScreenOpen(mc)) {
			phase = Phase.OBSERVE;
			return;
		}
		if(setupTicks >= 1 && !reopenRecoveryTried
			&& activeStrategy.needsClosePacketRecoveryForReopen()) {
			sendClosePacket(mc);
			applyReopen(mc, activeStrategy.reopenMode);
			reopenRecoveryTried = true;
			status = "Reopen blocked; sent close packet recovery";
			return;
		}
		if(setupTicks >= 2 && !reopenRecoveryTried
			&& hasOpenCommandConfigured()
			&& activeStrategy.shouldRecoverReopenWithCommand()) {
			sendOpenCommand(mc);
			reopenRecoveryTried = true;
			status = "Reopen failed; trying command recovery";
			return;
		}
		if(setupTicks > 6) {
			if(tryAdvanceReplayStartMode())
				return;
			status = "Reopen failed; skipping strategy #"
				+ activeStrategyNumber;
			phase = Phase.RUN_STRATEGY;
		}
	}

	private static void observe(Minecraft mc) {
		AbstractContainerMenu menu = mc.player.containerMenu;
		if(!isPluginContainerScreenOpen(mc)) {
			phase = Phase.WAIT_REOPEN;
			setupTicks = 0;
			return;
		}
		if(menu != null)
			refreshActiveTargetSlot(menu);
		int observed = menu == null ? 0 : countMatching(menu, targetStack);
		lastObservedCount = observed;
		if(observed > baselineCount) {
			phase = UiUtilsSettings.get().autoduperDropValidation
				? Phase.VALIDATE_DROP : Phase.IDLE;
			if(phase == Phase.IDLE)
				stop("Success on dupe attempt #" + activeStrategyNumber
					+ ": count " + baselineCount + " -> " + observed);
			return;
		}
		if(tryAdvanceReplayStartMode())
			return;
		phase = Phase.RUN_STRATEGY;
	}

	private static void validateByDrop(Minecraft mc) {
		AbstractContainerMenu menu = mc.player.containerMenu;
		if(menu == null) {
			stop("Validation failed: no menu");
			return;
		}
		List<Integer> matchingSlots = matchingSlots(menu, targetStack);
		if(matchingSlots.size() < 2) {
			if(tryAdvanceReplayStartMode())
				return;
			phase = Phase.RUN_STRATEGY;
			status = "Validation rejected ghost count";
			return;
		}
		int dropped = 0;
		int target = activeTargetSlot;
		validationGroundCountBefore = countNearbyGroundItems(mc, targetStack);
		validationInventoryCountBefore = countMatching(menu, targetStack);
		for(int slot : matchingSlots) {
			if(slot == target && matchingSlots.size() > dropped + 1)
				continue;
			click(mc, slot, 1, ContainerInput.THROW);
			dropped++;
			if(dropped >= 2)
				break;
		}
		validationTicks = 0;
		phase = Phase.VALIDATE_OBSERVE;
		status = "Dropped " + dropped + " matching slot(s); waiting for server";
	}

	private static void validateDropResult(Minecraft mc) {
		validationTicks++;
		AbstractContainerMenu menu = mc.player.containerMenu;
		int groundNow = countNearbyGroundItems(mc, targetStack);
		int inventoryNow = menu == null ? 0 : countMatching(menu, targetStack);
		boolean groundAccepted = groundNow > validationGroundCountBefore;
		boolean inventoryAccepted = inventoryNow < validationInventoryCountBefore;
		if(groundAccepted || inventoryAccepted) {
			stop("Validated by server-visible drop; count " + baselineCount
				+ " -> " + lastObservedCount + " on dupe attempt #"
				+ activeStrategyNumber);
			return;
		}
		if(validationTicks > 20) {
			if(tryAdvanceReplayStartMode())
				return;
			status = "Validation rejected ghost item";
			phase = Phase.RUN_STRATEGY;
		}
	}

	public static void renderAbortOverlay(GuiGraphicsExtractor graphics) {
		if(!running)
			return;
		Minecraft mc = Minecraft.getInstance();
		int x = 8;
		int y = 8;
		int w = 150;
		int h = 20;
		graphics.fill(x, y, x + w, y + h, 0xCC7A1010);
		graphics.outline(x, y, w, h, 0xFFFFB0B0);
		String abortText = "ABORT AUTODUPE";
		if(UiUtilsSettings.get().autoduperAbortHoldEnabled
			&& abortHoldTicks > 0) {
			abortText += " "
				+ Math.min(100, abortHoldTicks * 100 / 60) + "%";
		}
		graphics.text(mc.font, abortText, x + 8, y + 6, 0xFFFFFFFF, false);
	}

	public static boolean handleAbortOverlayClick(double mouseX, double mouseY,
		int button) {
		if(!running || button != 0)
			return false;
		if(mouseX >= 8 && mouseX <= 158 && mouseY >= 8 && mouseY <= 28) {
			abort();
			return true;
		}
		return false;
	}

	private static void sendOpenCommand(Minecraft mc) {
		String command = cleanCommand(UiUtilsSettings.get().autoduperOpenCommand);
		if(!command.isBlank())
			sendCommand(mc, command);
	}

	private static void interactInFront(Minecraft mc) {
		if(mc.player == null || mc.gameMode == null || mc.hitResult == null)
			return;
		if(mc.hitResult.getType() != HitResult.Type.BLOCK)
			return;
		mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND,
			(BlockHitResult)mc.hitResult);
	}

	private static void sendClosePacket(Minecraft mc) {
		if(mc.getConnection() == null || mc.player == null)
			return;
		int containerId = pendingClosePacketContainerId >= 0
			? pendingClosePacketContainerId
			: mc.player.containerMenu != null ? mc.player.containerMenu.containerId
				: -1;
		if(containerId < 0)
			return;
		pendingClosePacketContainerId = -1;
		mc.getConnection().send(new ServerboundContainerClosePacket(containerId));
	}

	private static void rememberClosePacketContainerId(Minecraft mc) {
		if(mc != null && mc.player != null && mc.player.containerMenu != null)
			pendingClosePacketContainerId = mc.player.containerMenu.containerId;
	}

	private static void flushDelayed(Minecraft mc) {
		UiUtilsState.delayUiPackets = false;
		UiUtils.sendQueuedPackets(mc, 1);
		UiUtils.clearQueuedPackets();
	}

	private static void closeForReplayStartMode(Minecraft mc) {
		if(replayStartMode == ReplayStartMode.CONTAINER_NORMAL)
			sendClosePacket(mc);
		else
			rememberClosePacketContainerId(mc);
		mc.setScreen(null);
		phase = replayStartMode == ReplayStartMode.CONTAINER_DESYNC
			? Phase.SETUP_DESYNC_RECOVER_CLOSE : Phase.SETUP_FINAL_REOPEN;
		setupTicks = 0;
		status = "Setup: replay seeded target into plugin GUI slot "
			+ activeTargetSlot + " using " + replayStartMode.label;
	}

	private static boolean tryAdvanceReplayStartMode() {
		if(!isSingleReplay())
			return false;
		if(replayStartIndex + 1 >= replayStartModes.length)
			return false;
		replayStartIndex++;
		replayStartMode = replayStartModes[replayStartIndex];
		activeTargetSlot = UiUtilsSettings.get().autoduperTargetSlot;
		baselineCount = 0;
		lastObservedCount = 0;
		setupTicks = 0;
		phase = Phase.ACQUIRE_TARGET;
		status = "Replay start state failed; trying "
			+ replayStartMode.label;
		UiUtils.chatIfEnabled("Autoduper: " + status);
		return true;
	}

	private static void finishStrategy(Minecraft mc, FinishMode finishMode,
		boolean hadDelayedPackets) {
		if(hadDelayedPackets)
			flushDelayed(mc);
		switch(finishMode) {
			case NONE -> {}
			case LEAVE_SEND -> {
				mc.setScreen(null);
				status = "Finished strategy with leave + send";
			}
			case DISCONNECT_SEND -> {
				status = "Finished strategy with disconnect + send";
				resumeAfterReconnect = true;
				resumeAttempt = attempt;
				running = false;
				phase = Phase.IDLE;
				UiUtilsDisconnect.disconnectWithConfiguredMethod(mc);
			}
		}
	}

	private static void restoreSavedGui(Minecraft mc) {
		if(mc.player != null && savedScreen != null && savedMenu != null) {
			mc.setScreen(savedScreen);
			mc.player.containerMenu = savedMenu;
		}
	}

	private static void saveCurrentGui(Minecraft mc) {
		if(mc.player == null)
			return;
		savedScreen = mc.screen;
		savedMenu = mc.player.containerMenu;
	}

	private static void sendCommand(Minecraft mc, String command) {
		if(mc.getConnection() == null || command == null || command.isBlank())
			return;
		String clean = cleanCommand(command);
		if(!clean.isBlank())
			mc.getConnection().sendCommand(clean);
	}

	private static void scheduleAutoduperTask(Minecraft mc, int ticks,
		Runnable runnable) {
		if(ticks <= 0) {
			runnable.run();
			return;
		}
		UiUtils.queueTask(() -> {
			if(running && Minecraft.getInstance().player != null)
				runnable.run();
		}, ticks * 50L);
	}

	private static String cleanCommand(String command) {
		String clean = command == null ? "" : command.trim();
		while(clean.startsWith("/"))
			clean = clean.substring(1).trim();
		return clean;
	}

	private static void click(Minecraft mc, int slot, int button,
		ContainerInput input) {
		if(mc.player == null || mc.gameMode == null)
			return;
		AbstractContainerMenu menu = mc.player.containerMenu;
		if(menu == null || slot < 0 || slot >= menu.slots.size())
			return;
		mc.gameMode.handleContainerInput(menu.containerId, slot, button, input,
			mc.player);
	}

	private static void clickTracked(Minecraft mc, int slot, int button,
		ContainerInput input) {
		if(mc.player == null || mc.gameMode == null)
			return;
		AbstractContainerMenu menu = mc.player.containerMenu;
		if(menu == null || slot < 0 || slot >= menu.slots.size())
			return;
		int[] before = matchingCounts(menu);
		mc.gameMode.handleContainerInput(menu.containerId, slot, button, input,
			mc.player);
		updateActiveTargetSlotAfterClick(menu, before, slot);
	}

	private static int[] matchingCounts(AbstractContainerMenu menu) {
		int[] counts = new int[menu.slots.size()];
		for(int i = 0; i < menu.slots.size(); i++) {
			ItemStack stack = menu.slots.get(i).getItem();
			counts[i] = matches(stack, targetStack) ? stack.getCount() : 0;
		}
		return counts;
	}

	private static void updateActiveTargetSlotAfterClick(AbstractContainerMenu menu,
		int[] before, int clickedSlot) {
		int bestSlot = -1;
		int bestDelta = 0;
		for(int i = 0; i < menu.slots.size() && i < before.length; i++) {
			ItemStack stack = menu.slots.get(i).getItem();
			if(!matches(stack, targetStack))
				continue;
			int delta = stack.getCount() - before[i];
			if(i != clickedSlot && delta > bestDelta) {
				bestDelta = delta;
				bestSlot = i;
			}
		}
		if(bestSlot >= 0) {
			activeTargetSlot = bestSlot;
			return;
		}
		if(clickedSlot >= 0 && clickedSlot < menu.slots.size()
			&& matches(menu.slots.get(clickedSlot).getItem(), targetStack)) {
			activeTargetSlot = clickedSlot;
			return;
		}
		refreshActiveTargetSlot(menu);
	}

	private static boolean isSetupPhase(Phase phase) {
		return phase == Phase.SETUP_STORE
			|| phase == Phase.SETUP_STORE_OBSERVE
			|| phase == Phase.SETUP_RETRIEVE_FROM_CONTAINER
			|| phase == Phase.SETUP_RETRIEVE_CONTAINER_OBSERVE
			|| phase == Phase.SETUP_CLOSE_NORMAL
			|| phase == Phase.SETUP_REOPEN_FOR_RETRIEVE
			|| phase == Phase.SETUP_RETRIEVE_OBSERVE
			|| phase == Phase.SETUP_CLOSE_WITHOUT_PACKET
			|| phase == Phase.SETUP_DESYNC_RECOVER_CLOSE
			|| phase == Phase.SETUP_FINAL_REOPEN
			|| phase == Phase.SETUP_FINAL_REOPEN_OBSERVE;
	}

	private static int playerInventoryStart(AbstractContainerMenu menu) {
		return Math.max(0, menu.slots.size() - 36);
	}

	private static boolean isPlayerInventorySlot(AbstractContainerMenu menu,
		int slot) {
		return slot >= playerInventoryStart(menu) && slot < menu.slots.size();
	}

	private static int firstContainerSlot(AbstractContainerMenu menu,
		ItemStack target) {
		int playerStart = playerInventoryStart(menu);
		for(int i = 0; i < playerStart; i++)
			if(matches(menu.slots.get(i).getItem(), target))
				return i;
		return -1;
	}

	private static int firstPlayerInventorySlot(AbstractContainerMenu menu,
		ItemStack target) {
		for(int i = playerInventoryStart(menu); i < menu.slots.size(); i++)
			if(matches(menu.slots.get(i).getItem(), target))
				return i;
		return -1;
	}

	private static void refreshActiveTargetSlot(AbstractContainerMenu menu) {
		if(activeTargetSlot >= 0 && activeTargetSlot < menu.slots.size()
			&& matches(menu.slots.get(activeTargetSlot).getItem(), targetStack))
			return;
		int playerSlot = firstPlayerInventorySlot(menu, targetStack);
		if(playerSlot >= 0) {
			activeTargetSlot = playerSlot;
			return;
		}
		int containerSlot = firstContainerSlot(menu, targetStack);
		if(containerSlot >= 0)
			activeTargetSlot = containerSlot;
	}

	private static ReopenMode preferredOpenMode() {
		if(!cleanCommand(UiUtilsSettings.get().autoduperOpenCommand).isBlank()
			&& UiUtilsSettings.get().autoduperHybridOpen)
			return ReopenMode.COMMAND_THEN_INTERACT;
		if(!cleanCommand(UiUtilsSettings.get().autoduperOpenCommand).isBlank())
			return ReopenMode.COMMAND;
		return ReopenMode.INTERACT;
	}

	private static boolean isPluginContainerScreenOpen(Minecraft mc) {
		if(mc == null || mc.screen == null)
			return false;
		if(!(mc.screen instanceof AbstractContainerScreen<?>))
			return false;
		return !(mc.screen instanceof InventoryScreen);
	}

	private static boolean hasOpenCommandConfigured() {
		return !cleanCommand(UiUtilsSettings.get().autoduperOpenCommand).isBlank();
	}

	private static boolean hasPrepareCommandConfigured() {
		return !cleanCommand(UiUtilsSettings.get().autoduperPrepareCommand).isBlank();
	}

	private static void tickAbortKey(Minecraft mc) {
		if(!UiUtilsSettings.get().autoduperAbortHoldEnabled
			|| mc.getWindow() == null) {
			abortHoldTicks = 0;
			return;
		}
		InputConstants.Key key = parseKey(UiUtilsSettings.get().autoduperAbortKey,
			"key.keyboard.space");
		if(key != null && InputConstants.isKeyDown(mc.getWindow(), key.getValue())) {
			abortHoldTicks++;
			if(abortHoldTicks >= 60)
				abort();
		}else {
			abortHoldTicks = 0;
		}
	}

	private static InputConstants.Key parseKey(String raw, String fallback) {
		String value = raw == null || raw.isBlank() ? fallback : raw.trim();
		try {
			InputConstants.Key key = InputConstants.getKey(value);
			return key != null ? key : InputConstants.getKey(fallback);
		}catch(Exception ignored) {
			return InputConstants.getKey(fallback);
		}
	}

	private static String readableKey(String raw) {
		String value = raw == null || raw.isBlank() ? "key.keyboard.space" : raw;
		int dot = value.lastIndexOf('.');
		return dot >= 0 && dot + 1 < value.length()
			? value.substring(dot + 1).toUpperCase(Locale.ROOT)
			: value;
	}

	private static void applyMovement(Minecraft mc, Movement movement) {
		int slot = activeTargetSlot;
		switch(movement) {
			case NONE -> {}
			case PICKUP_PULSE -> {
				click(mc, slot, 0, ContainerInput.PICKUP);
				click(mc, slot, 0, ContainerInput.PICKUP);
			}
			case QUICK_MOVE -> clickTracked(mc, slot, 0, ContainerInput.QUICK_MOVE);
			case OFFHAND_SWAP -> clickTracked(mc, slot, 40, ContainerInput.SWAP);
			case QUICK_MOVE_DELAYED -> {
				UiUtilsState.delayUiPackets = true;
				clickTracked(mc, slot, 0, ContainerInput.QUICK_MOVE);
			}
			case PICKUP_DELAYED -> {
				UiUtilsState.delayUiPackets = true;
				click(mc, slot, 0, ContainerInput.PICKUP);
				click(mc, slot, 0, ContainerInput.PICKUP);
			}
		}
		if(mc.player != null && mc.player.containerMenu != null)
			refreshActiveTargetSlot(mc.player.containerMenu);
	}

	private static void applyClose(Minecraft mc, CloseMode closeMode) {
		switch(closeMode) {
			case NONE -> {}
			case SOFT_CLOSE -> {
				rememberClosePacketContainerId(mc);
				mc.setScreen(null);
			}
			case CLOSE_PACKET_KEEP_SCREEN -> sendClosePacket(mc);
			case NORMAL_CLOSE_PACKET -> {
				sendClosePacket(mc);
				mc.setScreen(null);
			}
		}
	}

	private static void applyReopen(Minecraft mc, ReopenMode reopenMode) {
		switch(reopenMode) {
			case NONE -> {}
			case COMMAND -> sendOpenCommand(mc);
			case DOUBLE_COMMAND -> {
				sendOpenCommand(mc);
				sendOpenCommand(mc);
			}
			case INTERACT -> interactInFront(mc);
			case COMMAND_THEN_INTERACT -> {
				sendOpenCommand(mc);
				interactInFront(mc);
			}
			case INTERACT_THEN_COMMAND -> {
				interactInFront(mc);
				sendOpenCommand(mc);
			}
			case DOUBLE_COMMAND_THEN_INTERACT -> {
				sendOpenCommand(mc);
				sendOpenCommand(mc);
				interactInFront(mc);
			}
			case COMMAND_THEN_RESTORE -> {
				sendOpenCommand(mc);
				restoreSavedGui(mc);
			}
			case RESTORE_THEN_COMMAND -> {
				restoreSavedGui(mc);
				sendOpenCommand(mc);
			}
			case PREPARE_THEN_COMMAND -> {
				String prepare =
					cleanCommand(UiUtilsSettings.get().autoduperPrepareCommand);
				if(!prepare.isBlank())
					sendCommand(mc, prepare);
				sendOpenCommand(mc);
			}
		}
	}

	private static Strategy[] buildStrategies() {
		List<Strategy> strategies = new ArrayList<>();
		List<StrategySeed> seeds = new ArrayList<>();
		Movement[] movementOrder = {
			Movement.PICKUP_PULSE,
			Movement.QUICK_MOVE,
			Movement.OFFHAND_SWAP,
			Movement.QUICK_MOVE_DELAYED,
			Movement.PICKUP_DELAYED,
			Movement.NONE
		};
		for(Movement movement : movementOrder)
			for(CloseMode closeMode : CloseMode.values())
				for(ReopenMode reopenMode : ReopenMode.values()) {
					if(movement == Movement.NONE && closeMode == CloseMode.NONE
						&& reopenMode == ReopenMode.NONE)
						continue;
					if(reopenMode == ReopenMode.PREPARE_THEN_COMMAND)
						continue;
					seeds.add(new StrategySeed(movement, closeMode, reopenMode,
						false, false));
					if(movement.isDelayable()) {
						seeds.add(new StrategySeed(movement, closeMode,
							reopenMode, true, false));
						seeds.add(new StrategySeed(movement, closeMode,
							reopenMode, false, true));
					}
				}
		seeds.add(new StrategySeed(Movement.NONE, CloseMode.NONE,
			ReopenMode.PREPARE_THEN_COMMAND, false, false));
		seeds.add(new StrategySeed(Movement.QUICK_MOVE_DELAYED,
			CloseMode.CLOSE_PACKET_KEEP_SCREEN, ReopenMode.DOUBLE_COMMAND, true,
			false));
		seeds.add(new StrategySeed(Movement.OFFHAND_SWAP,
			CloseMode.CLOSE_PACKET_KEEP_SCREEN, ReopenMode.COMMAND_THEN_RESTORE,
			false, true));
		for(StrategySeed seed : seeds)
			addTimingStrategies(strategies, seed.movement, seed.closeMode,
				seed.reopenMode, seed.releaseAfterReopen,
				seed.releaseAfterClose, FinishMode.NONE);
		for(StrategySeed seed : seeds)
			addTimingStrategies(strategies, seed.movement, seed.closeMode,
				seed.reopenMode, seed.releaseAfterReopen,
				seed.releaseAfterClose, FinishMode.LEAVE_SEND);
		for(StrategySeed seed : seeds)
			addTimingStrategies(strategies, seed.movement, seed.closeMode,
				seed.reopenMode, seed.releaseAfterReopen,
				seed.releaseAfterClose,
				FinishMode.DISCONNECT_SEND);
		return strategies.toArray(Strategy[]::new);
	}

	private static void addTimingStrategies(List<Strategy> strategies,
		Movement movement, CloseMode closeMode, ReopenMode reopenMode,
		boolean releaseAfterReopen, boolean releaseAfterClose,
		FinishMode finishMode) {
		boolean canDelayClose = closeMode != CloseMode.NONE;
		boolean canDelayCommand = reopenMode.usesCommand();
		strategies.add(new Strategy(strategies.size() + 1, movement, closeMode,
			reopenMode, releaseAfterReopen, releaseAfterClose, false, false,
			finishMode));
		if(canDelayClose)
			strategies.add(new Strategy(strategies.size() + 1, movement, closeMode,
				reopenMode, releaseAfterReopen, releaseAfterClose, true, false,
				finishMode));
		if(canDelayCommand)
			strategies.add(new Strategy(strategies.size() + 1, movement, closeMode,
				reopenMode, releaseAfterReopen, releaseAfterClose, false, true,
				finishMode));
		if(canDelayClose && canDelayCommand)
			strategies.add(new Strategy(strategies.size() + 1, movement, closeMode,
				reopenMode, releaseAfterReopen, releaseAfterClose, true, true,
				finishMode));
	}

	private static int countMatching(AbstractContainerMenu menu,
		ItemStack target) {
		int count = 0;
		for(int i = 0; i < menu.slots.size(); i++) {
			ItemStack stack = menu.slots.get(i).getItem();
			if(matches(stack, target))
				count += stack.getCount();
		}
		return count;
	}

	private static List<Integer> matchingSlots(AbstractContainerMenu menu,
		ItemStack target) {
		List<Integer> slots = new ArrayList<>();
		for(int i = 0; i < menu.slots.size(); i++)
			if(matches(menu.slots.get(i).getItem(), target))
				slots.add(i);
		return slots;
	}

	private static int countNearbyGroundItems(Minecraft mc, ItemStack target) {
		if(mc.level == null || mc.player == null)
			return 0;
		int count = 0;
		for(Entity entity : mc.level.entitiesForRendering()) {
			if(entity instanceof ItemEntity itemEntity
				&& itemEntity.closerThan(mc.player, 6.0D)
				&& matches(itemEntity.getItem(), target))
				count += itemEntity.getItem().getCount();
		}
		return count;
	}

	private static boolean matches(ItemStack stack, ItemStack target) {
		if(stack.isEmpty() || target.isEmpty())
			return false;
		return ItemStack.isSameItemSameComponents(stack, target);
	}

	public static String summary() {
		return String.format(Locale.ROOT,
			"%s | attempt %d/%d | count %d/%d | %s", running ? "RUNNING"
				: "IDLE", attempt, getAttemptLimit(), lastObservedCount,
			baselineCount, status);
	}

	private static int getAttemptLimit() {
		int base = Math.min(runPlan.length,
			Math.max(1, UiUtilsSettings.get().autoduperMaxAttempts));
		if(isSingleReplay())
			return Math.max(base, replayStartModes.length);
		return base;
	}

	private static Strategy[] buildRunPlan() {
		int single = UiUtilsSettings.get().autoduperSingleAttempt;
		if(single > 0 && single <= STRATEGIES.length)
			return new Strategy[] {STRATEGIES[single - 1]};
		List<Strategy> filtered = new ArrayList<>();
		for(Strategy strategy : STRATEGIES)
			if(strategy.isEnabled()
				&& (UiUtilsSettings.get().autoduperVerboseMode
					|| !strategy.isSmartPruned()))
				filtered.add(strategy);
		return filtered.toArray(Strategy[]::new);
	}

	private static boolean isSingleReplay() {
		int single = UiUtilsSettings.get().autoduperSingleAttempt;
		return single > 0 && single <= STRATEGIES.length && runPlan.length == 1;
	}

	private static ReplayStartMode[] buildReplayStartModes(
		AbstractContainerMenu menu) {
		if(!isSingleReplay())
			return new ReplayStartMode[] {ReplayStartMode.DEFAULT};
		int configuredSlot = UiUtilsSettings.get().autoduperTargetSlot;
		if(!isPlayerInventorySlot(menu, configuredSlot))
			return new ReplayStartMode[] {ReplayStartMode.DEFAULT};
		if(runPlan[0].needsContainerSeedForDirectReplay())
			return new ReplayStartMode[] {
				ReplayStartMode.CONTAINER_DESYNC,
				ReplayStartMode.CONTAINER_NORMAL,
				ReplayStartMode.DEFAULT
			};
		return new ReplayStartMode[] {
			ReplayStartMode.DEFAULT,
			ReplayStartMode.CONTAINER_DESYNC,
			ReplayStartMode.CONTAINER_NORMAL
		};
	}

	private enum Phase {
		IDLE,
		ACQUIRE_TARGET,
		SETUP_STORE,
		SETUP_STORE_OBSERVE,
		SETUP_RETRIEVE_FROM_CONTAINER,
		SETUP_RETRIEVE_CONTAINER_OBSERVE,
		SETUP_CLOSE_NORMAL,
		SETUP_REOPEN_FOR_RETRIEVE,
		SETUP_RETRIEVE_OBSERVE,
		SETUP_CLOSE_WITHOUT_PACKET,
		SETUP_DESYNC_RECOVER_CLOSE,
		SETUP_FINAL_REOPEN,
		SETUP_FINAL_REOPEN_OBSERVE,
		PREPARE,
		RUN_STRATEGY,
		WAIT_REOPEN,
		OBSERVE,
		VALIDATE_DROP,
		VALIDATE_OBSERVE
	}

	private enum ReplayStartMode {
		DEFAULT("default start"),
		CONTAINER_DESYNC("container seed + desync close"),
		CONTAINER_NORMAL("container seed + normal close");

		private final String label;

		ReplayStartMode(String label) {
			this.label = label;
		}

		private boolean startsFromContainer() {
			return this == CONTAINER_DESYNC || this == CONTAINER_NORMAL;
		}
	}

	private enum Movement {
		NONE("no move"),
		PICKUP_PULSE("pickup pulse"),
		QUICK_MOVE("quick move"),
		OFFHAND_SWAP("offhand swap"),
		QUICK_MOVE_DELAYED("delayed quick move"),
		PICKUP_DELAYED("delayed pickup");

		private final String label;

		Movement(String label) {
			this.label = label;
		}

		private boolean isDelayable() {
			return this == PICKUP_PULSE || this == QUICK_MOVE
				|| this == OFFHAND_SWAP;
		}

		private boolean crossesContainerBoundary() {
			return this == QUICK_MOVE || this == QUICK_MOVE_DELAYED;
		}

		private boolean isEnabled() {
			UiUtilsSettings.Data settings = UiUtilsSettings.get();
			return switch(this) {
				case NONE -> settings.autoduperMoveNone;
				case PICKUP_PULSE -> settings.autoduperMovePickup;
				case QUICK_MOVE -> settings.autoduperMoveQuickMove;
				case OFFHAND_SWAP -> settings.autoduperMoveOffhandSwap;
				case QUICK_MOVE_DELAYED, PICKUP_DELAYED -> settings.autoduperMoveDelayed;
			};
		}
	}

	private enum CloseMode {
		NONE("keep open"),
		SOFT_CLOSE("soft close"),
		CLOSE_PACKET_KEEP_SCREEN("close packet keep screen"),
		NORMAL_CLOSE_PACKET("close packet + leave");

		private final String label;

		CloseMode(String label) {
			this.label = label;
		}

		private boolean isEnabled() {
			UiUtilsSettings.Data settings = UiUtilsSettings.get();
			return switch(this) {
				case NONE -> settings.autoduperCloseKeepOpen;
				case SOFT_CLOSE -> settings.autoduperCloseSoftClose;
				case CLOSE_PACKET_KEEP_SCREEN -> settings.autoduperClosePacketKeepScreen;
				case NORMAL_CLOSE_PACKET -> settings.autoduperClosePacketLeave;
			};
		}

		private boolean sendsCloseOrLeaves() {
			return this == CLOSE_PACKET_KEEP_SCREEN
				|| this == NORMAL_CLOSE_PACKET
				|| this == SOFT_CLOSE;
		}
	}

	private enum ReopenMode {
		NONE("no reopen"),
		COMMAND("command reopen"),
		DOUBLE_COMMAND("double command reopen"),
		INTERACT("interact reopen"),
		COMMAND_THEN_INTERACT("command + interact reopen"),
		INTERACT_THEN_COMMAND("interact + command reopen"),
		DOUBLE_COMMAND_THEN_INTERACT("double command + interact reopen"),
		COMMAND_THEN_RESTORE("command then restore stale GUI"),
		RESTORE_THEN_COMMAND("restore stale GUI then command"),
		PREPARE_THEN_COMMAND("prepare command then open command");

		private final String label;

		ReopenMode(String label) {
			this.label = label;
		}

		private boolean isEnabled() {
			UiUtilsSettings.Data settings = UiUtilsSettings.get();
			return switch(this) {
				case NONE -> settings.autoduperReopenNone;
				case COMMAND -> settings.autoduperReopenCommand;
				case COMMAND_THEN_INTERACT,
					INTERACT_THEN_COMMAND -> settings.autoduperReopenCommand
						&& settings.autoduperHybridOpen;
				case DOUBLE_COMMAND,
					DOUBLE_COMMAND_THEN_INTERACT -> settings.autoduperReopenDoubleCommand
						&& (this != DOUBLE_COMMAND_THEN_INTERACT
							|| settings.autoduperHybridOpen);
				case INTERACT -> settings.autoduperReopenInteract;
				case COMMAND_THEN_RESTORE, RESTORE_THEN_COMMAND -> settings.autoduperReopenStaleRestore;
				case PREPARE_THEN_COMMAND -> settings.autoduperReopenPrepareCommand;
			};
		}

		private boolean isRunnableForCurrentConfig() {
			boolean hasOpen = hasOpenCommandConfigured();
			boolean hasPrepare = hasPrepareCommandConfigured();
			return switch(this) {
				case NONE, INTERACT -> true;
				case COMMAND, DOUBLE_COMMAND, COMMAND_THEN_INTERACT,
					INTERACT_THEN_COMMAND, DOUBLE_COMMAND_THEN_INTERACT,
					COMMAND_THEN_RESTORE, RESTORE_THEN_COMMAND -> hasOpen;
				case PREPARE_THEN_COMMAND -> hasOpen && hasPrepare;
			};
		}

		private boolean usesCommand() {
			return switch(this) {
				case COMMAND, DOUBLE_COMMAND, COMMAND_THEN_INTERACT,
					INTERACT_THEN_COMMAND, DOUBLE_COMMAND_THEN_INTERACT,
					COMMAND_THEN_RESTORE, RESTORE_THEN_COMMAND,
					PREPARE_THEN_COMMAND -> true;
				case NONE, INTERACT -> false;
			};
		}

		private boolean isHybridDuplicate() {
			return this == INTERACT_THEN_COMMAND
				|| this == DOUBLE_COMMAND_THEN_INTERACT;
		}
	}

	private enum FinishMode {
		NONE(""),
		LEAVE_SEND(" + leave after send"),
		DISCONNECT_SEND(" + disconnect after send");

		private final String label;

		FinishMode(String label) {
			this.label = label;
		}

		private boolean isEnabled() {
			UiUtilsSettings.Data settings = UiUtilsSettings.get();
			return switch(this) {
				case NONE -> true;
				case LEAVE_SEND -> settings.autoduperFinishLeaveSend;
				case DISCONNECT_SEND -> settings.autoduperFinishDisconnectSend;
			};
		}
	}

	private record StrategySeed(Movement movement, CloseMode closeMode,
		ReopenMode reopenMode, boolean releaseAfterReopen,
		boolean releaseAfterClose) {
	}

	private static final class Strategy {
		private final int number;
		private final Movement movement;
		private final CloseMode closeMode;
		private final ReopenMode reopenMode;
		private final boolean releaseAfterReopen;
		private final boolean releaseAfterClose;
		private final boolean delayedClose;
		private final boolean delayedCommand;
		private final FinishMode finishMode;
		private final String label;

		private Strategy(int number, Movement movement, CloseMode closeMode,
			ReopenMode reopenMode, boolean releaseAfterReopen,
			boolean releaseAfterClose, boolean delayedClose,
			boolean delayedCommand, FinishMode finishMode) {
			this.number = number;
			this.movement = movement;
			this.closeMode = closeMode;
			this.reopenMode = reopenMode;
			this.releaseAfterReopen = releaseAfterReopen;
			this.releaseAfterClose = releaseAfterClose;
			this.delayedClose = delayedClose;
			this.delayedCommand = delayedCommand;
			this.finishMode = finishMode;
			this.label = movement.label + " + " + closeMode.label + " + "
				+ reopenMode.label
				+ (delayedClose ? " + delayed close" : "")
				+ (delayedCommand ? " + delayed command" : "")
				+ (releaseAfterClose ? " + release after close" : "")
				+ (releaseAfterReopen ? " + release queued" : "")
				+ finishMode.label;
		}

		private void run(Minecraft mc) {
			saveCurrentGui(mc);
			boolean wasDelay = UiUtilsState.delayUiPackets;
			if((releaseAfterReopen || releaseAfterClose) && movement.isDelayable())
				UiUtilsState.delayUiPackets = true;
			applyMovement(mc, movement);
			Runnable finish = () -> {
				boolean hadDelayedPackets =
					releaseAfterReopen || releaseAfterClose
						|| movement.name().contains("DELAYED");
				if(!hadDelayedPackets)
					UiUtilsState.delayUiPackets = wasDelay;
				finishStrategy(mc, finishMode, hadDelayedPackets);
			};
			int closeDelay = delayedClose ? UiUtilsSettings.get().uiCloseDelayTicks
				: 0;
			int commandDelay = delayedCommand
				? UiUtilsSettings.get().uiCommandDelayTicks : 0;
			scheduleAutoduperTask(mc, closeDelay, () -> {
				applyClose(mc, closeMode);
				if(releaseAfterClose)
					flushDelayed(mc);
				scheduleAutoduperTask(mc, commandDelay, () -> {
					applyReopen(mc, reopenMode);
					finish.run();
				});
			});
		}

		private boolean isEnabled() {
			if(delayedClose
				&& UiUtilsSettings.get().uiCloseDelayTicks <= 0)
				return false;
			if(delayedCommand
				&& UiUtilsSettings.get().uiCommandDelayTicks <= 0)
				return false;
			if(!finishMode.isEnabled())
				return false;
			if((releaseAfterReopen || releaseAfterClose)
				&& !UiUtilsSettings.get().autoduperPacketDelayVariants)
				return false;
			if(!reopenMode.isRunnableForCurrentConfig())
				return false;
			if(pluginInventorySeedMode && !touchesPluginGuiLifecycle())
				return false;
			return movement.isEnabled() && closeMode.isEnabled()
				&& reopenMode.isEnabled();
		}

		private boolean isSmartPruned() {
			if(releaseAfterClose && closeMode == CloseMode.NONE)
				return true;
			if(releaseAfterReopen && reopenMode == ReopenMode.NONE)
				return true;
			if(releaseAfterClose && releaseAfterReopen)
				return true;
			if(releaseAfterClose && !closeMode.sendsCloseOrLeaves())
				return true;
			if(delayedCommand && !reopenMode.usesCommand())
				return true;
			if(delayedClose && closeMode == CloseMode.NONE)
				return true;
			if(reopenMode.isHybridDuplicate())
				return true;
			if(closeMode == CloseMode.NONE && reopenMode == ReopenMode.NONE)
				return true;
			if(finishMode != FinishMode.NONE && !releaseAfterClose
				&& !releaseAfterReopen)
				return true;
			return false;
		}

		private boolean shouldRecoverReopenWithCommand() {
			return finishMode != FinishMode.DISCONNECT_SEND
				&& reopenMode != ReopenMode.COMMAND
				&& reopenMode != ReopenMode.DOUBLE_COMMAND
				&& reopenMode != ReopenMode.PREPARE_THEN_COMMAND;
		}

		private boolean needsClosePacketRecoveryForReopen() {
			return closeMode == CloseMode.SOFT_CLOSE
				|| closeMode == CloseMode.NONE;
		}

		private boolean needsContainerSeedForDirectReplay() {
			return (releaseAfterReopen || releaseAfterClose)
				&& movement.crossesContainerBoundary();
		}

		private boolean touchesPluginGuiLifecycle() {
			return movement.crossesContainerBoundary()
				&& closeMode != CloseMode.NONE
				&& reopenMode != ReopenMode.NONE;
		}
	}
}
