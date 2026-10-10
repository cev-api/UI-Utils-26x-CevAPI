package com.ui_utils.uiutils.macro;

import com.ui_utils.uiutils.McCompat;
import com.ui_utils.uiutils.UiUtils;
import com.ui_utils.uiutils.UiUtilsCommandSystem;
import com.ui_utils.uiutils.UiUtilsDisconnect;
import com.ui_utils.uiutils.UiUtilsState;
import com.ui_utils.uiutils.UiUtilsTasks;
import java.util.Locale;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

public final class UiUtilsMacroExecutor {
    private static volatile boolean running;
    private static volatile String currentName;
    private static volatile Thread worker;
    private static volatile net.minecraft.client.multiplayer.ClientPacketListener session;
    private static volatile String lastError = "";
    public static String lastError() { return lastError; }
    // Accessed on the client thread. An old run must not release a new run's key.
    private static final Map<KeyMapping, Thread> heldKeys = new HashMap<>();
    private static final Set<KeyMapping> persistentKeys = new HashSet<>();

    private UiUtilsMacroExecutor() {}

    public static boolean isRunning() { return running; }
    public static boolean isRunning(String macroName) { return running && currentName != null && currentName.equalsIgnoreCase(macroName); }
    public static String currentName() { return currentName; }

    public static synchronized void start(UiUtilsMacro macro) {
        if (macro == null) return;
        stop();
        Minecraft mc = Minecraft.getInstance();
        session = mc == null ? null : mc.getConnection();
        lastError = "";
        macro = macro.deepCopy();
        UiUtilsMacro snapshot = macro;
        running = true;
        currentName = macro.name;
        worker = new Thread(() -> runMacro(snapshot), "ui-utils-macro-exec");
        worker.setDaemon(true);
        worker.start();
    }

    public static synchronized void stop() { stopRun(true); }

    private static void stopRun(boolean cancelPending) {
        Thread previous = worker;
        if (cancelPending) UiUtilsTasks.cancelMacroTasks(previous);
        worker = null;
        running = false;
        currentName = null;
        if (previous != null && previous != Thread.currentThread()) previous.interrupt();
        Minecraft mc = Minecraft.getInstance();
        if (mc != null) mc.execute(() -> {
            heldKeys.entrySet().removeIf(entry -> {
                if (entry.getValue() != previous) return false;
                if (persistentKeys.contains(entry.getKey())) {
                    if (cancelPending) {
                        entry.getKey().setDown(false);
                        persistentKeys.remove(entry.getKey());
                    }
                    return true;
                }
                entry.getKey().setDown(false); return true;
            });
            if (cancelPending) {
                persistentKeys.removeIf(key -> {
                    if (previous != null || heldKeys.containsKey(key)) return false;
                    key.setDown(false);
                    return true;
                });
            }
            if (mc.player != null && !mc.options.keySprint.isDown()) mc.player.setSprinting(false);
        });
    }

    private static boolean isCurrentRun() {
        return running && worker == Thread.currentThread() && !Thread.currentThread().isInterrupted();
    }

    private static synchronized void finishRun(Thread finished) {
        if (worker == finished) stopRun(false);
    }

    private static void runMacro(UiUtilsMacro macro) {
        int loops = macro.loop ? (macro.loopCount < 0 ? Integer.MAX_VALUE : Math.max(1, macro.loopCount)) : 1;
        try {
            for (int loop = 0; isCurrentRun() && loop < loops; loop++) runSteps(macro, 0, macro.actions.size(), 0);
        } catch (java.util.concurrent.CancellationException ignored) {
        } catch (Throwable failure) {
            synchronized (UiUtilsMacroExecutor.class) {
                if (worker == Thread.currentThread()) UiUtilsTasks.cancelMacroTasks(Thread.currentThread());
            }
            lastError = failure.getMessage() == null ? failure.getClass().getSimpleName() : failure.getMessage();
            UiUtils.LOGGER.warn("Macro {} stopped: {}", macro.name, lastError);
            UiUtils.LOGGER.debug("Macro failure details", failure);
            Minecraft mc = Minecraft.getInstance();
            if (mc != null) UiUtils.reportError("Macro " + macro.name + " stopped: " + lastError);
        } finally { finishRun(Thread.currentThread()); }
    }

    private static void runSteps(UiUtilsMacro macro, int from, int to, int depth) {
        if (depth > 16) throw new IllegalArgumentException("Repeat nesting exceeds 16 levels");
        for (int index = from; index < to && isCurrentRun(); index++) {
            UiUtilsMacroAction action = macro.actions.get(index);
            if (!action.isEnabled()) continue;
            if (macro.shareSteps && isCurrentRun()) {
                int step = index + 1;
                Minecraft mc = Minecraft.getInstance();
                String message = macro.name + " — Step " + step + "/"
                    + macro.actions.size() + ": "
                    + UiUtilsMacroLabels.stepDescription(action);
                runOnMain(mc, () -> {
                    if (mc.player != null)
                        UiUtils.postSystemMessage(net.minecraft.network.chat.Component.literal(
                            "[UI-Utils] " + message));
                });
            }
            if (action.getType() == UiUtilsMacroActionType.REPEAT) {
                int count = action.getData().getIntOr("stepCount", 1);
                int repeats = action.getData().getIntOr("repeatCount", 1);
                if (count < 1 || count > index || repeats < 1 || repeats > 10000)
                    throw new IllegalArgumentException("Invalid Repeat at step " + (index + 1));
                for (int repeat = 0; repeat < repeats && isCurrentRun(); repeat++) runSteps(macro, index - count, index, depth + 1);
            } else executeAction(action);
        }
    }

    private static void executeAction(UiUtilsMacroAction action) {
        Minecraft mc = Minecraft.getInstance();
        try {
            String legacyCommand = action.getData().getStringOr("uiutilsCommand", "");
            if (!legacyCommand.isBlank()) {
                runOnMain(mc, () -> UiUtilsCommandSystem.execute(legacyCommand)); return;
            }
            switch (action.getType()) {
                case DELAY -> sleepMillis(action.getData().getBooleanOr("useTicks", false)
                    ? Math.max(0, action.getData().getIntOr("delayTicks", 1)) * 50L
                    : Math.max(0, action.getData().getIntOr("delayMs", 50)));
                case SEND_CHAT -> {
                    String msg = action.getData().getStringOr("message", "");
                    // A leading slash means the macro wants a command, matching the chat field keybind.
                    if (action.getData().getBooleanOr("waitForGui", false))
                        awaitCondition(mc, action, () -> guiTitleMatches(mc, action.getData().getStringOr("guiName", "")));
                    if (!msg.isBlank()) runOnMain(mc, () -> {
                        if (msg.startsWith("/")) UiUtils.sendCommandWithConfiguredDelay(mc, msg.substring(1));
                        else UiUtils.sendChatWithConfiguredDelay(mc, msg);
                    });
                }
                case SEND_COMMAND -> {
                    String command = action.getData().getStringOr("command", "");
                    if (!command.isBlank()) runOnMain(mc, () -> UiUtils.sendCommandWithConfiguredDelay(mc, command));
                }
                case CLOSE_GUI -> runOnMain(mc, () -> closeMatchingGui(mc, action));
                case DESYNC -> runOnMain(mc, () -> UiUtils.sendClosePacketWithConfiguredDelay(mc));
                case RESTORE_GUI -> {
                    if (UiUtilsState.storedScreen == null || UiUtilsState.storedMenu == null)
                        throw new IllegalStateException("No saved GUI is available to restore");
                    runOnMain(mc, () -> UiUtils.executeKeybindAction("restore_gui", mc));
                    if (action.getData().getBooleanOr("waitForGui", false))
                        awaitCondition(mc, action, () -> UiUtilsState.storedScreen != null && McCompat.getScreen(mc) == UiUtilsState.storedScreen);
                }
                case SAVE_GUI -> runOnMain(mc, () -> {
                    boolean saved = UiUtils.saveCurrentGuiToSlot(mc, "default");
                    if (!saved) throw new IllegalStateException("Could not save the current GUI");
                    if (saved && action.getData().getBooleanOr("closeAfter", false))
                        UiUtils.closeScreenWithConfiguredDelay(mc, action.getData().getBooleanOr("sendPacket", false));
                });
                case SELECT_SLOT -> runOnMain(mc, () -> {
                    if (mc.player == null) return;
                    int slot = resolveHotbarSlot(mc, action.getData().getStringOr("itemName", ""), action.getData().getIntOr("slot", 0));
                    mc.player.getInventory().setSelectedSlot(slot);
                });
                case ROTATE -> rotate(mc, action);
                case JUMP -> pressKeyForTicks(mc, mc.options.keyJump,
                    action.getData().getBooleanOr("tap", true) ? 1 : Math.max(1, action.getData().getIntOr("durationTicks", 1)));
                case SNEAK -> setHeldKey(mc, mc.options.keyShift, action.getData().getBooleanOr("sneak", true), action.getData().getBooleanOr("persistent", false));
                case SPRINT -> {
                    boolean sprint = action.getData().getBooleanOr("sprint", true);
                    setHeldKey(mc, mc.options.keySprint, sprint, action.getData().getBooleanOr("persistent", false));
                    runOnMain(mc, () -> { if (mc.player != null) mc.player.setSprinting(sprint); });
                }
                case MOVE -> {
                    int ticks = Math.max(1, action.getData().getIntOr("durationTicks", 20));
                    String dir = action.getData().getStringOr("direction", "FORWARD").toUpperCase(Locale.ROOT);
                    KeyMapping key = switch (dir) {
                        case "BACKWARD" -> mc.options.keyDown;
                        case "LEFT" -> mc.options.keyLeft;
                        case "RIGHT" -> mc.options.keyRight;
                        default -> mc.options.keyUp;
                    };
                    if (action.getData().getBooleanOr("nonBlocking", false)) {
                        Thread owner = Thread.currentThread();
                        setHeldKey(mc, key, true, true);
                        UiUtilsTasks.scheduleOwned(owner, () -> {
                            if (heldKeys.remove(key, owner) || persistentKeys.remove(key)) key.setDown(false);
                        }, ticks * 50L);
                    } else pressKeyForTicks(mc, key, ticks);
                }
                case USE_ITEM -> runUseItem(mc, action);
                case DROP -> runDrop(mc, action);
                case SWAP_SLOTS -> runSwapSlots(mc, action);
                case ITEM -> runItemClick(mc, action);
                case STORE_ITEM -> runStoreItem(mc, action);
                case WAIT_HEALTH -> waitForHealth(mc, action);
                case WAIT_POS -> waitForPos(mc, action);
                case WAIT_GUI -> waitForGui(mc, action);
                case WAIT_CHAT -> waitForChat(action);
                case WAIT_PACKET -> waitForPacket(action);
                case SEND_TOGGLE -> runOnMain(mc, () -> {
                    String mode = action.getData().getStringOr("mode", "");
                    boolean state = "DISABLE".equalsIgnoreCase(mode) ? false :
                        ("ENABLE".equalsIgnoreCase(mode) ? true : !UiUtilsState.sendUiPackets);
                    UiUtilsState.sendUiPackets = state;
                });
                case DELAY_PACKETS -> runOnMain(mc, () -> {
                    String mode = action.getData().getStringOr("mode", "");
                    boolean state = "DISABLE".equalsIgnoreCase(mode) ? false :
                        ("ENABLE".equalsIgnoreCase(mode) ? true : !UiUtilsState.delayUiPackets);
                    UiUtilsState.delayUiPackets = state;
                    if (!state && action.getData().getBooleanOr("flushOnDisable", false)) {
                        UiUtils.sendQueuedPackets(mc, 1); UiUtils.clearQueuedPackets();
                    }
                });
                case DISCONNECT -> {
                    sleepMillis(Math.max(0, action.getData().getIntOr("delayMs", 0)));
                    String raw = action.getData().getStringOr("mode", "DISCONNECT");
                    UiUtilsDisconnect.Method method;
                    try {
                        method = raw.equalsIgnoreCase("DISCONNECT")
                            ? UiUtilsDisconnect.getConfiguredMethod()
                            : UiUtilsDisconnect.Method.valueOf(raw.toUpperCase(Locale.ROOT));
                    } catch (IllegalArgumentException invalid) {
                        throw new IllegalArgumentException("Unsupported saved disconnect mode '" + raw + "'. Edit this step and choose a listed method.");
                    }
                    int packetCount = Math.max(1, action.getData().getIntOr("packetCount", UiUtilsDisconnect.getConfiguredLagPacketCount()));
                    runOnMain(mc, () -> UiUtilsDisconnect.execute(mc, method, packetCount));
                }
                case STOP_MACRO -> stop();
                case REPEAT -> throw new IllegalStateException("Repeat must run through the step sequencer");
                default -> UiUtilsMacroActions.execute(mc, action);

            }
        } catch (Throwable t) {
            if (t instanceof java.util.concurrent.CancellationException cancel) throw cancel;
            throw new IllegalStateException(action.getType() + ": " + (t.getMessage() == null ? t.getClass().getSimpleName() : t.getMessage()), t);
        }
    }

    private static void runUseItem(Minecraft mc, UiUtilsMacroAction action) {
        String itemName = action.getData().getStringOr("itemName", "").trim();
        int slot = runOnMainResult(mc, () -> {
            if (mc.player == null) return -1;
            if (!itemName.isBlank()) return findHotbarSlot(mc, itemName);
            return Math.max(0, Math.min(8, action.getData().getIntOr("slot", 0)));
        });
        if (!itemName.isBlank() && slot < 0) throw new IllegalStateException("No hotbar item matches '" + itemName + "'");
        if (slot >= 0) runOnMain(mc, () -> {
            if (mc.player != null) mc.player.getInventory().setSelectedSlot(Math.max(0, Math.min(8, slot)));
        });
        int uses = Math.max(1, action.getData().getIntOr("useCount", 1));
        String mode = action.getData().getStringOr("useMode", "AUTOMATIC");
        int holdTicks = Math.max(1, action.getData().getIntOr("holdTicks", 20));
        for (int i = 0; i < uses && isCurrentRun(); i++) {
            runOnMain(mc, () -> {
                if (mc.player == null || mc.getConnection() == null) return;
                mc.gameMode.useItem(mc.player, InteractionHand.MAIN_HAND);
            });
            if ("CUSTOM_HOLD".equalsIgnoreCase(mode)) {
                sleepMillis(holdTicks * 50L);
                runOnMain(mc, () -> {
                    if (mc.getConnection() != null) {
                        mc.gameMode.releaseUsingItem(mc.player);
                    }
                });
            }
        }
    }

    private static void runDrop(Minecraft mc, UiUtilsMacroAction action) {
        runOnMain(mc, () -> {
            if (mc.player == null || mc.gameMode == null) return;
            AbstractContainerMenu menu = mc.player.containerMenu;
            if (menu == null) return;
            String itemName = action.getData().getStringOr("itemName", "").trim();
            int slot = itemName.isBlank() ? action.getData().getIntOr("slot", -1) : findMenuSlot(menu, itemName);
            if (!itemName.isBlank() && slot < 0) throw new IllegalStateException("No open-container item matches '" + itemName + "'");
            if (slot < 0) slot = parseLegacyListFirstSlot(action.getData(), "itemNames");
            int clicks = Math.max(1, action.getData().getIntOr("count", action.getData().getIntOr("dropCount", 1)));
            if (slot < 0) return;
            int handlerSlot = resolveHandlerSlot(menu, slot);
            if (handlerSlot < 0) return;
            boolean fullStack = action.getData().getStringOr("mode", "TIMES").equalsIgnoreCase("ALL");
            if (fullStack) {
                mc.gameMode.handleContainerInput(menu.containerId, handlerSlot, 1, ContainerInput.THROW, mc.player);
            } else {
                for (int i = 0; i < clicks; i++) mc.gameMode.handleContainerInput(menu.containerId, handlerSlot, 0, ContainerInput.THROW, mc.player);
            }
        });
    }

    private static void runSwapSlots(Minecraft mc, UiUtilsMacroAction action) {
        runOnMain(mc, () -> {
            if (mc.player == null || mc.gameMode == null) return;
            AbstractContainerMenu menu = mc.player.containerMenu;
            if (menu == null) return;
            int from = action.getData().getIntOr("fromSlot", -1);
            int to = action.getData().getIntOr("toSlot", -1);
            if (from < 0 || to < 0) return;
            int fromHandler = resolveHandlerSlot(menu, from);
            int toHandler = resolveHandlerSlot(menu, to);
            if (fromHandler < 0 || toHandler < 0) return;
            mc.gameMode.handleContainerInput(menu.containerId, fromHandler, 0, ContainerInput.PICKUP, mc.player);
            mc.gameMode.handleContainerInput(menu.containerId, toHandler, 0, ContainerInput.PICKUP, mc.player);
            mc.gameMode.handleContainerInput(menu.containerId, fromHandler, 0, ContainerInput.PICKUP, mc.player);
        });
    }

    private static void runItemClick(Minecraft mc, UiUtilsMacroAction action) {
        runOnMain(mc, () -> {
            if (mc.player == null || mc.gameMode == null) return;
            AbstractContainerMenu menu = mc.player.containerMenu;
            if (menu == null) return;
            Set<Integer> targetSlots = new java.util.LinkedHashSet<>();
            if (action.getData().getBooleanOr("useSlot", false)) {
                int slot = action.getData().getIntOr("targetSlot", -1);
                if (slot >= 0) targetSlots.add(slot);
            } else {
                for (String selector : UiUtilsMacroActions.selectors(action.getData(), "itemNames")) {
                    if (selector.startsWith("#")) {
                        String[] parts = selector.substring(1).split("\\|", 2);
                        try {
                            int slot = Integer.parseInt(parts[0]);
                            if (slot >= 0 && slot < menu.slots.size()
                                && (parts.length == 1 || matchesName(menu.slots.get(slot).getItem(), parts[1]))) targetSlots.add(slot);
                        } catch (NumberFormatException ignored) {}
                    } else if (!selector.isBlank()) {
                        for (int i = 0; i < menu.slots.size(); i++)
                            if (matchesName(menu.slots.get(i).getItem(), selector)) targetSlots.add(i);
                    }
                }
            }
            if (targetSlots.isEmpty()) throw new IllegalStateException("No slots match the configured item selection");
            int actionIndex = action.getData().getIntOr("actionIndex", 0);
            int button = action.getData().getIntOr("button", 0);
            int times = Math.max(1, action.getData().getIntOr("times", 1));
            ContainerInput input = toContainerInput(actionIndex);
            if (actionIndex == 1 || actionIndex == 6 || actionIndex == 7) button = 0;
            if (actionIndex == 3) button = 2;
            if (actionIndex == 8) button = 1;
            for (int slot : targetSlots) {
                int handlerSlot = resolveHandlerSlot(menu, slot);
                if (handlerSlot < 0) continue;
                for (int i = 0; i < times; i++) mc.gameMode.handleContainerInput(menu.containerId, handlerSlot, button, input, mc.player);
            }
        });
    }

    private static void runStoreItem(Minecraft mc, UiUtilsMacroAction action) {
        boolean persistent = action.getData().getBooleanOr("persistent", false);
        List<String> targets = UiUtilsMacroActions.selectors(action.getData(), "targetItems");
        boolean all = action.getData().getBooleanOr("allItems", false);
        if (!all && targets.isEmpty()) throw new IllegalArgumentException("Choose target items or enable All Items");
        boolean store = action.getData().getStringOr("mode", "STORE").equalsIgnoreCase("STORE");
        do {
            int moved = runOnMainResult(mc, () -> {
                if (mc.player == null || mc.gameMode == null) return 0;
                AbstractContainerMenu menu = mc.player.containerMenu;
                if (menu == null || menu == mc.player.inventoryMenu) return 0;
                int count = 0;
                for (int i = 0; i < menu.slots.size(); i++) {
                    Slot slot = menu.slots.get(i);
                    if (slot == null || (store == isPlayerInventorySlot(menu, i))) continue;
                    ItemStack stack = slot.getItem();
                    if (stack.isEmpty()) continue;
                    if (!all && !targets.isEmpty()) {
                        boolean matches = false;
                        for (String target : targets) if (storeTargetMatches(menu, i, stack, target)) { matches = true; break; }
                        if (!matches) continue;
                    }
                    mc.gameMode.handleContainerInput(menu.containerId, i, 0, ContainerInput.QUICK_MOVE, mc.player);
                    count++;
                }
                return count;
            });
            if (!persistent || !isCurrentRun()) break;
            // Let the server apply the quick-move results before scanning again.
            sleepMillis(moved == 0 ? 100 : 50);
        } while (isCurrentRun());
        if (action.getData().getBooleanOr("closeAfter", false)) runOnMain(mc, () ->
            UiUtils.closeScreenWithConfiguredDelay(mc, action.getData().getBooleanOr("closeSendPkt", false)));
    }

    private static void waitForHealth(Minecraft mc, UiUtilsMacroAction action) {
        float threshold = action.getData().getFloatOr("healthThreshold", 20f);
        boolean below = "Drops Below".equalsIgnoreCase(action.getData().getStringOr("comparison", "Drops Below"));
        awaitCondition(mc, action, () -> mc.player != null && (below ? mc.player.getHealth() < threshold : mc.player.getHealth() > threshold));
    }
    private static void waitForPos(Minecraft mc, UiUtilsMacroAction action) {
        var target = UiUtilsMacroActions.position(action.getData());
        double radius = Math.max(0.1, action.getData().getDoubleOr("leeway", 1));
        awaitCondition(mc, action, () -> mc.player != null && mc.player.position().distanceToSqr(target) <= radius * radius
            && (!action.getData().getBooleanOr("checkRotation", false)
                || (Math.abs(net.minecraft.util.Mth.wrapDegrees(mc.player.getYRot() - action.getData().getFloatOr("yaw", 0))) <= action.getData().getFloatOr("rotLeeway", 5)
                    && Math.abs(mc.player.getXRot() - action.getData().getFloatOr("pitch", 0)) <= action.getData().getFloatOr("rotLeeway", 5))));
    }
    private static void waitForGui(Minecraft mc, UiUtilsMacroAction action) {
        String expected = action.getData().getStringOr("guiTitle", "").toLowerCase(Locale.ROOT);
        boolean close = action.getData().getStringOr("waitMode", "OPEN").equalsIgnoreCase("CLOSE");
        awaitCondition(mc, action, () -> {
            var screen = McCompat.getScreen(mc);
            boolean match = screen != null && screen.getTitle().getString().toLowerCase(Locale.ROOT).contains(expected);
            return close ? !match : match;
        });
    }
    private static void waitForChat(UiUtilsMacroAction action) {
        String pattern = action.getData().getStringOr("pattern", "");
        boolean useRegex = action.getData().getBooleanOr("useRegex", false);
        Pattern regex = useRegex ? Pattern.compile(pattern, Pattern.CASE_INSENSITIVE) : null;
        int fuzzyPercent = Math.max(40, Math.min(100, action.getData().getIntOr("fuzzyPercent", 80)));
        long start = UiUtilsMacroRuntimeState.chatCount();
        Minecraft mc = Minecraft.getInstance();
        awaitCondition(mc, action, () -> (!action.getData().getBooleanOr("waitForGui", false)
            || guiTitleMatches(mc, action.getData().getStringOr("waitGuiName", "")))
            && UiUtilsMacroRuntimeState.chats().stream().anyMatch(event -> event.sequence() > start
                && (!action.getData().getBooleanOr("serverMessageOnly", false) || event.server())
                && (useRegex ? regex.matcher(event.text()).find() : fuzzyContains(event.text(), pattern, fuzzyPercent))));
    }
    private static void waitForPacket(UiUtilsMacroAction action) {
        String name = action.getData().getStringOr("packetName", "");
        String needle = UiUtilsMacroRuntimeState.normalize(name);
        long in = UiUtilsMacroRuntimeState.incomingCount(), out = UiUtilsMacroRuntimeState.outgoingCount();
        awaitCondition(Minecraft.getInstance(), action, () ->
            (!name.toUpperCase(Locale.ROOT).startsWith("C2S:") && UiUtilsMacroRuntimeState.packetSince(true, needle, in))
            || (!name.toUpperCase(Locale.ROOT).startsWith("S2C:") && UiUtilsMacroRuntimeState.packetSince(false, needle, out)));
    }

    static void awaitCondition(Minecraft mc, UiUtilsMacroAction action, java.util.function.BooleanSupplier condition) {
        long timeout = action.getData().getIntOr("timeoutMs", 30000);
        if (timeout <= 0) timeout = 30000;
        long deadline = System.nanoTime() + timeout * 1_000_000L;
        while (isCurrentRun()) {
            java.util.concurrent.atomic.AtomicBoolean satisfied = new java.util.concurrent.atomic.AtomicBoolean();
            runOnMain(mc, () -> satisfied.set(condition.getAsBoolean()));
            if (satisfied.get()) return;
            if (System.nanoTime() >= deadline) throw new IllegalStateException("Condition timed out after " + timeout + " ms");
            sleepMillis(50);
        }
        throw new java.util.concurrent.CancellationException();
    }

    private static String normalizePacketName(String raw) {
        String s = raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT);
        return s.endsWith("packet") ? s.substring(0, s.length() - 6) : s;
    }

    private static int parseLegacyListFirstSlot(net.minecraft.nbt.CompoundTag tag, String listKey) {
        if (!tag.contains(listKey) || !(tag.get(listKey) instanceof net.minecraft.nbt.ListTag list) || list.isEmpty()) return -1;
        var first = list.get(0);
        if (first instanceof net.minecraft.nbt.CompoundTag t && t.contains("slot")) return t.getIntOr("slot", -1);
        String s = first.asString().orElse("");
        if (s.startsWith("#")) {
            int pipe = s.indexOf('|');
            String raw = pipe > 1 ? s.substring(1, pipe) : s.substring(1);
            try { return Integer.parseInt(raw.trim()); } catch (Exception ignored) {}
        }
        return -1;
    }

    private static int findHotbarSlot(Minecraft mc, String target) {
        for (int slot = 0; slot < 9; slot++)
            if (matchesName(mc.player.getInventory().getItem(slot), target)) return slot;
        return -1;
    }

    private static int findMenuSlot(AbstractContainerMenu menu, String target) {
        for (int slot = 0; slot < menu.slots.size(); slot++)
            if (matchesName(menu.slots.get(slot).getItem(), target)) return slot;
        return -1;
    }

    private static boolean storeTargetMatches(AbstractContainerMenu menu, int slot, ItemStack stack, String target) {
        if (!target.startsWith("#")) return matchesName(stack, target);
        String[] parts = target.substring(1).split("\\|", 2);
        try {
            if (Integer.parseInt(parts[0]) != slot) return false;
        } catch (NumberFormatException invalid) { return false; }
        return parts.length == 1 || matchesName(stack, parts[1]);
    }

    private static int resolveHotbarSlot(Minecraft mc, String itemName, int fallback) {
        if (itemName != null && !itemName.isBlank()) {
            int found = findHotbarSlot(mc, itemName.trim());
            if (found < 0) throw new IllegalStateException("No hotbar item matches '" + itemName + "'");
            return found;
        }
        return Math.max(0, Math.min(8, fallback));
    }

    private static <T> T runOnMainResult(Minecraft mc, java.util.function.Supplier<T> supplier) {
        java.util.concurrent.atomic.AtomicReference<T> result = new java.util.concurrent.atomic.AtomicReference<>();
        runOnMain(mc, () -> result.set(supplier.get()));
        return result.get();
    }

    private static boolean guiTitleMatches(Minecraft mc, String expected) {
        var screen = McCompat.getScreen(mc);
        return screen != null && screen.getTitle().getString().toLowerCase(Locale.ROOT)
            .contains((expected == null ? "" : expected).toLowerCase(Locale.ROOT));
    }

    private static void closeMatchingGui(Minecraft mc, UiUtilsMacroAction action) {
        if (mc.player == null || !guiTitleMatches(mc, action.getData().getStringOr("guiName", ""))) return;
        if (action.getData().getBooleanOr("useItemFilter", false)) {
            AbstractContainerMenu menu = mc.player.containerMenu;
            String itemName = action.getData().getStringOr("itemName", "").trim();
            int targetSlot = action.getData().getIntOr("targetSlot", -1);
            if (itemName.isBlank() && targetSlot < 0) return;
            if (targetSlot >= 0) {
                if (targetSlot >= menu.slots.size()) return;
                ItemStack stack = menu.slots.get(targetSlot).getItem();
                if (stack.isEmpty() || (!itemName.isBlank() && !matchesName(stack, itemName))) return;
            } else if (findMenuSlot(menu, itemName) < 0) return;
        }
        UiUtils.closeScreenWithConfiguredDelay(mc, action.getData().getBooleanOr("sendPacket", false));
    }

    private static boolean fuzzyContains(String text, String pattern, int matchPercent) {
        String haystack = text.toLowerCase(Locale.ROOT);
        String needle = pattern.toLowerCase(Locale.ROOT).trim();
        if (haystack.contains(needle)) return true;
        if (needle.isEmpty()) return true;
        String[] words = haystack.trim().split("\\s+");
        int targetWords = needle.split("\\s+").length;
        int minimumWords = Math.max(1, targetWords - 1);
        int maximumWords = Math.min(words.length, targetWords + 1);
        for (int count = minimumWords; count <= maximumWords; count++) {
            for (int start = 0; start + count <= words.length; start++) {
                String candidate = String.join(" ", java.util.Arrays.copyOfRange(words, start, start + count));
                int distance = editDistance(needle, candidate);
                int similarity = 100 * (Math.max(needle.length(), candidate.length()) - distance)
                    / Math.max(needle.length(), candidate.length());
                if (similarity >= matchPercent) return true;
            }
        }
        return false;
    }

    private static int editDistance(String left, String right) {
        int[] previous = new int[right.length() + 1];
        for (int j = 0; j <= right.length(); j++) previous[j] = j;
        for (int i = 1; i <= left.length(); i++) {
            int[] current = new int[right.length() + 1];
            current[0] = i;
            for (int j = 1; j <= right.length(); j++)
                current[j] = Math.min(Math.min(current[j - 1] + 1, previous[j] + 1), previous[j - 1] + (left.charAt(i - 1) == right.charAt(j - 1) ? 0 : 1));
            previous = current;
        }
        return previous[right.length()];
    }

    private static void rotate(Minecraft mc, UiUtilsMacroAction action) {
        float[] start = runOnMainResult(mc, () -> mc.player == null ? null : new float[] { mc.player.getYRot(), mc.player.getXRot() });
        if (start == null) return;
        float targetYaw = action.getData().getFloatOr("yaw", start[0]);
        float targetPitch = action.getData().getFloatOr("pitch", start[1]);
        if (!Float.isFinite(targetYaw) || !Float.isFinite(targetPitch)) throw new IllegalArgumentException("Rotation must be finite");
        targetYaw = net.minecraft.util.Mth.wrapDegrees(targetYaw);
        targetPitch = net.minecraft.util.Mth.clamp(targetPitch, -90, 90);
        if (!action.getData().getBooleanOr("smooth", false)) {
            final float yaw = targetYaw, pitch = targetPitch;
            runOnMain(mc, () -> setRotation(mc, yaw, pitch));
            return;
        }
        int smoothness = Math.max(1, Math.min(10, action.getData().getIntOr("smoothness", 5)));
        int frames = smoothness * 4;
        Thread owner = Thread.currentThread();
        if (action.getData().getBooleanOr("waitForCompletion", false)) {
            for (int frame = 1; frame <= frames && isCurrentRun(); frame++) {
                sleepMillis(25);
                float amount = (float)frame / frames;
                float yaw = net.minecraft.util.Mth.wrapDegrees(start[0] + net.minecraft.util.Mth.wrapDegrees(targetYaw - start[0]) * amount);
                float pitch = start[1] + (targetPitch - start[1]) * amount;
                final float nextYaw = yaw, nextPitch = pitch;
                runOnMain(mc, () -> setRotation(mc, nextYaw, nextPitch));
            }
        } else scheduleRotation(mc, owner, start[0], start[1], targetYaw, targetPitch, 1, frames);
    }

    private static void scheduleRotation(Minecraft mc, Thread owner, float startYaw, float startPitch,
        float targetYaw, float targetPitch, int frame, int frames) {
        UiUtilsTasks.scheduleOwned(owner, () -> {
            float amount = (float)frame / frames;
            setRotation(mc, net.minecraft.util.Mth.wrapDegrees(startYaw + net.minecraft.util.Mth.wrapDegrees(targetYaw - startYaw) * amount),
                startPitch + (targetPitch - startPitch) * amount);
            if (frame < frames) scheduleRotation(mc, owner, startYaw, startPitch, targetYaw, targetPitch, frame + 1, frames);
        }, 25);
    }

    private static void setRotation(Minecraft mc, float yaw, float pitch) {
        if (mc.player != null) { mc.player.setYRot(yaw); mc.player.setXRot(pitch); }
    }

    private static int resolveHandlerSlot(AbstractContainerMenu menu, int visibleSlot) {
        if (visibleSlot >= 0 && visibleSlot < menu.slots.size()) return visibleSlot;
        if (visibleSlot >= 0 && visibleSlot <= 8) {
            int candidate = menu.slots.size() - 9 + visibleSlot;
            if (candidate >= 0 && candidate < menu.slots.size()) return candidate;
        }
        return -1;
    }

    private static ContainerInput toContainerInput(int actionIndex) {
        return switch (actionIndex) {
            case 1 -> ContainerInput.QUICK_MOVE;
            case 2 -> ContainerInput.SWAP;
            case 3 -> ContainerInput.CLONE;
            case 4, 7, 8 -> ContainerInput.THROW;
            case 5 -> ContainerInput.QUICK_CRAFT;
            case 6 -> ContainerInput.PICKUP_ALL;
            default -> ContainerInput.PICKUP;
        };
    }

    private static boolean isPlayerInventorySlot(AbstractContainerMenu menu, int index) {
        int start = Math.max(0, menu.slots.size() - 36);
        return index >= start;
    }

    private static boolean matchesName(ItemStack stack, String target) {
        if (target == null || target.isBlank()) return true;
        String needle = target.toLowerCase(Locale.ROOT);
        String id = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString().toLowerCase(Locale.ROOT);
        String hover = stack.getHoverName().getString().toLowerCase(Locale.ROOT);
        return id.contains(needle) || hover.contains(needle);
    }

    private static void pressKeyForTicks(Minecraft mc, KeyMapping key, int ticks) {
        Thread owner = Thread.currentThread();
        runOnMain(mc, () -> {
            heldKeys.put(key, owner);
            key.setDown(true);
        });
        try {
            sleepMillis(Math.max(1, ticks) * 50L);
        } finally {
            // Cleanup must run even after stop interrupts this worker.
            mc.execute(() -> {
                if (heldKeys.remove(key, owner)) key.setDown(false);
            });
        }
    }

    static void releaseHeldKey(Minecraft mc, KeyMapping key) {
        Thread owner = Thread.currentThread();
        releaseHeldKey(mc, key, owner);
    }
    static void releaseHeldKey(Minecraft mc, KeyMapping key, Thread owner) {
        mc.execute(() -> { if (heldKeys.remove(key, owner)) key.setDown(false); });
    }
    static void releasePersistentHeldKey(Minecraft mc, KeyMapping key, Thread owner) {
        mc.execute(() -> {
            boolean owned = heldKeys.remove(key, owner);
            boolean persistent = persistentKeys.remove(key);
            if (owned || persistent) key.setDown(false);
        });
    }
    static void cleanupOnMain(Minecraft mc, Runnable cleanup) {
        Thread owner = Thread.currentThread();
        var origin = session;
        mc.execute(() -> { if (mc.getConnection() == origin && (worker == owner || worker == null)) cleanup.run(); });
    }
    static void setHeldKey(Minecraft mc, KeyMapping key, boolean down) { setHeldKey(mc, key, down, false); }
    static void setHeldKey(Minecraft mc, KeyMapping key, boolean down, boolean persistent) {
        Thread owner = Thread.currentThread();
        runOnMain(mc, () -> {
            if (down) {
                heldKeys.put(key, owner);
                if (persistent) persistentKeys.add(key); else persistentKeys.remove(key);
            } else {
                heldKeys.remove(key);
                persistentKeys.remove(key);
            }
            key.setDown(down);
        });
    }

    static void runOnMain(Minecraft mc, Runnable runnable) {
        if (mc == null) throw new IllegalStateException("Minecraft is unavailable");
        Thread owner = Thread.currentThread();
        if (mc.isSameThread()) { runnable.run(); return; }
        java.util.concurrent.CompletableFuture<Void> completion = new java.util.concurrent.CompletableFuture<>();
        mc.execute(() -> {
            try {
                if (!running || worker != owner || owner.isInterrupted() || mc.getConnection() != session)
                    throw new java.util.concurrent.CancellationException();
                UiUtilsTasks.runOwned(owner, runnable); completion.complete(null);
            } catch (Throwable failure) { completion.completeExceptionally(failure); }
        });
        try { completion.get(); }
        catch (InterruptedException failure) { Thread.currentThread().interrupt(); throw new java.util.concurrent.CancellationException(); }
        catch (java.util.concurrent.ExecutionException failure) {
            if (failure.getCause() instanceof RuntimeException runtime) throw runtime;
            throw new IllegalStateException("Client action failed", failure.getCause());
        }
    }

    static void sleepMillis(long millis) {
        try {
            Thread.sleep(Math.max(0L, millis));
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
    }
}
