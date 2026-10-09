package com.ui_utils.uiutils.macro;

import com.ui_utils.uiutils.McCompat;
import com.ui_utils.uiutils.UiUtils;
import com.ui_utils.uiutils.UiUtilsCommandSystem;
import com.ui_utils.uiutils.UiUtilsState;
import com.ui_utils.uiutils.UiUtilsTasks;
import java.util.Locale;
import java.util.HashMap;
import java.util.Map;
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
        if (cancelPending) UiUtilsTasks.cancelMacroTasks();
        Thread previous = worker;
        worker = null;
        running = false;
        currentName = null;
        if (previous != null && previous != Thread.currentThread()) previous.interrupt();
        Minecraft mc = Minecraft.getInstance();
        if (mc != null) mc.execute(() -> {
            heldKeys.entrySet().removeIf(entry -> {
                if (entry.getValue() != previous) return false;
                entry.getKey().setDown(false); return true;
            });
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
                if (worker == Thread.currentThread()) UiUtilsTasks.cancelMacroTasks();
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
            if (action.getType() == UiUtilsMacroActionType.REPEAT) {
                int count = action.getData().getIntOr("stepCount", 1);
                int repeats = action.getData().getIntOr("repeatCount", 1);
                if (count < 1 || count > index || repeats < 1 || repeats > 10000)
                    throw new IllegalArgumentException("Invalid Repeat at step " + (index + 1));
                for (int repeat = 0; repeat < repeats && isCurrentRun(); repeat++) runSteps(macro, index - count, index, depth + 1);
            } else executeAction(action);
            if (macro.shareSteps && isCurrentRun()) {
                int step = index + 1;
                Minecraft mc = Minecraft.getInstance();
                runOnMain(mc, () -> {
                    if (mc.player != null && mc.getConnection() != null)
                        mc.getConnection().sendChat(UiUtilsMacroRuntimeState.stepMessage(mc.player.getGameProfile().name(), macro.name, step));
                });
            }
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
                    if (!msg.isBlank()) runOnMain(mc, () -> {
                        if (msg.startsWith("/")) UiUtils.sendCommandWithConfiguredDelay(mc, msg.substring(1));
                        else UiUtils.sendChatWithConfiguredDelay(mc, msg);
                    });
                }
                case SEND_COMMAND -> {
                    String command = action.getData().getStringOr("command", "");
                    if (!command.isBlank()) runOnMain(mc, () -> UiUtils.sendCommandWithConfiguredDelay(mc, command));
                }
                case CLOSE_GUI -> runOnMain(mc, () -> UiUtils.closeScreenWithConfiguredDelay(mc));
                case DESYNC -> runOnMain(mc, () -> UiUtils.sendClosePacketWithConfiguredDelay(mc));
                case RESTORE_GUI -> runOnMain(mc, () -> UiUtils.executeKeybindAction("restore_gui", mc));
                case SAVE_GUI -> runOnMain(mc, () -> UiUtils.executeKeybindAction("save_gui", mc));
                case SELECT_SLOT -> runOnMain(mc, () -> {
                    if (mc.player == null) return;
                    int slot = Math.max(0, Math.min(8, action.getData().getIntOr("slot", 0)));
                    mc.player.getInventory().setSelectedSlot(slot);
                });
                case ROTATE -> runOnMain(mc, () -> {
                    if (mc.player == null) return;
                    float yaw = action.getData().getFloatOr("yaw", mc.player.getYRot()), pitch = action.getData().getFloatOr("pitch", mc.player.getXRot());
                    if (!Float.isFinite(yaw) || !Float.isFinite(pitch)) throw new IllegalArgumentException("Rotation must be finite");
                    mc.player.setYRot(net.minecraft.util.Mth.wrapDegrees(yaw));
                    mc.player.setXRot(net.minecraft.util.Mth.clamp(pitch, -90, 90));
                });
                case JUMP -> pressKeyForTicks(mc, mc.options.keyJump,
                    action.getData().getBooleanOr("tap", true) ? 1 : Math.max(1, action.getData().getIntOr("durationTicks", 1)));
                case SNEAK -> setHeldKey(mc, mc.options.keyShift, action.getData().getBooleanOr("sneak", true));
                case SPRINT -> {
                    boolean sprint = action.getData().getBooleanOr("sprint", true);
                    setHeldKey(mc, mc.options.keySprint, sprint);
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
                    pressKeyForTicks(mc, key, ticks);
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
                case DISCONNECT -> runOnMain(mc, () -> UiUtils.executeKeybindAction("disconnect", mc));
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
        int slot = parsePreferredSlot(action.getData(), "itemName", "slot", -1);
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
            int slot = parsePreferredSlot(action.getData(), "itemName", "slot", -1);
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
            int slot = action.getData().getBooleanOr("useSlot", false)
                ? action.getData().getIntOr("targetSlot", -1) : -1;
            if (slot < 0) slot = parseLegacyListFirstSlot(action.getData(), "itemNames");
            if (slot < 0) return;
            int handlerSlot = resolveHandlerSlot(menu, slot);
            if (handlerSlot < 0) return;
            int actionIndex = action.getData().getIntOr("actionIndex", 0);
            int button = action.getData().getIntOr("button", 0);
            int times = Math.max(1, action.getData().getIntOr("times", 1));
            ContainerInput input = toContainerInput(actionIndex);
            if (actionIndex == 1 || actionIndex == 6 || actionIndex == 7) button = 0;
            if (actionIndex == 3) button = 2;
            if (actionIndex == 8) button = 1;
            for (int i = 0; i < times; i++) mc.gameMode.handleContainerInput(menu.containerId, handlerSlot, button, input, mc.player);
        });
    }

    private static void runStoreItem(Minecraft mc, UiUtilsMacroAction action) {
        runOnMain(mc, () -> {
            if (mc.player == null || mc.gameMode == null) return;
            AbstractContainerMenu menu = mc.player.containerMenu;
            if (menu == null || menu == mc.player.inventoryMenu) return;
            boolean store = action.getData().getStringOr("mode", "STORE").equalsIgnoreCase("STORE");
            boolean all = action.getData().getBooleanOr("allItems", false);
            String targetName = parseLegacyListFirstName(action.getData(), "targetItems");
            for (int i = 0; i < menu.slots.size(); i++) {
                Slot slot = menu.slots.get(i);
                if (slot == null) continue;
                boolean playerInvSide = isPlayerInventorySlot(menu, i);
                if ((store && !playerInvSide) || (!store && playerInvSide)) continue;
                ItemStack stack = slot.getItem();
                if (stack.isEmpty()) continue;
                if (!all && !targetName.isBlank() && !matchesName(stack, targetName)) continue;
                mc.gameMode.handleContainerInput(menu.containerId, i, 0, ContainerInput.QUICK_MOVE, mc.player);
            }
        });
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
        Pattern regex = action.getData().getBooleanOr("useRegex", false) ? Pattern.compile(pattern, Pattern.CASE_INSENSITIVE) : null;
        long start = UiUtilsMacroRuntimeState.chatCount();
        awaitCondition(Minecraft.getInstance(), action, () -> UiUtilsMacroRuntimeState.chats().stream().anyMatch(event -> event.sequence() > start
            && (!action.getData().getBooleanOr("serverMessageOnly", false) || event.server())
            && (regex != null ? regex.matcher(event.text()).find() : event.text().toLowerCase(Locale.ROOT).contains(pattern.toLowerCase(Locale.ROOT)))));
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

    private static int parsePreferredSlot(net.minecraft.nbt.CompoundTag tag, String legacyTargetKey, String directSlotKey, int fallback) {
        int slot = tag.getIntOr(directSlotKey, fallback);
        if (slot >= 0) return slot;
        if (tag.contains(legacyTargetKey) && tag.get(legacyTargetKey) instanceof net.minecraft.nbt.CompoundTag target) {
            if (target.contains("slot")) return target.getIntOr("slot", fallback);
        }
        return fallback;
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

    private static String parseLegacyListFirstName(net.minecraft.nbt.CompoundTag tag, String listKey) {
        if (!tag.contains(listKey) || !(tag.get(listKey) instanceof net.minecraft.nbt.ListTag list) || list.isEmpty()) return "";
        var first = list.get(0);
        if (first instanceof net.minecraft.nbt.CompoundTag t) {
            if (t.contains("id")) return t.getStringOr("id", "");
            if (t.contains("name")) return t.getStringOr("name", "");
        }
        String s = first.asString().orElse("");
        int pipe = s.indexOf('|');
        return pipe >= 0 && pipe + 1 < s.length() ? s.substring(pipe + 1).trim() : s.trim();
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
        mc.execute(() -> { if (heldKeys.remove(key, owner)) key.setDown(false); });
    }
    static void cleanupOnMain(Minecraft mc, Runnable cleanup) {
        Thread owner = Thread.currentThread();
        var origin = session;
        mc.execute(() -> { if (mc.getConnection() == origin && (worker == owner || worker == null)) cleanup.run(); });
    }
    static void setHeldKey(Minecraft mc, KeyMapping key, boolean down) {
        Thread owner = Thread.currentThread();
        runOnMain(mc, () -> {
            if (down) heldKeys.put(key, owner); else heldKeys.remove(key, owner);
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
