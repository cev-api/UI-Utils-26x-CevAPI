package com.ui_utils.uiutils.macro;

import com.ui_utils.uiutils.McCompat;
import com.ui_utils.uiutils.UiUtils;
import com.ui_utils.uiutils.UiUtilsCommandSystem;
import com.ui_utils.uiutils.UiUtilsState;
import java.util.Locale;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
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

    private UiUtilsMacroExecutor() {}

    public static boolean isRunning() { return running; }
    public static boolean isRunning(String macroName) { return running && currentName != null && currentName.equalsIgnoreCase(macroName); }
    public static String currentName() { return currentName; }

    public static synchronized void start(UiUtilsMacro macro) {
        if (macro == null) return;
        stop();
        running = true;
        currentName = macro.name;
        Thread worker = new Thread(() -> runMacro(macro), "ui-utils-macro-exec");
        worker.setDaemon(true);
        worker.start();
    }

    public static synchronized void stop() {
        running = false;
        currentName = null;
    }

    private static void runMacro(UiUtilsMacro macro) {
        int loops = macro.loop ? (macro.loopCount < 0 ? Integer.MAX_VALUE : Math.max(1, macro.loopCount)) : 1;
        for (int loopIndex = 0; running && loopIndex < loops; loopIndex++) {
            for (UiUtilsMacroAction action : macro.actions) {
                if (!running) break;
                if (!action.isEnabled()) continue;
                executeAction(action);
            }
        }
        stop();
    }

    private static void executeAction(UiUtilsMacroAction action) {
        Minecraft mc = Minecraft.getInstance();
        try {
            switch (action.getType()) {
                case DELAY -> sleepMillis(action.getData().getBooleanOr("useTicks", false)
                    ? Math.max(0, action.getData().getIntOr("delayTicks", 1)) * 50L
                    : Math.max(0, action.getData().getIntOr("delayMs", 50)));
                case SEND_CHAT -> {
                    String msg = action.getData().getStringOr("message", "");
                    if (!msg.isBlank()) runOnMain(mc, () -> UiUtils.sendChatWithConfiguredDelay(mc, msg));
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
                    mc.player.setYRot(action.getData().getFloatOr("yaw", mc.player.getYRot()));
                    mc.player.setXRot(action.getData().getFloatOr("pitch", mc.player.getXRot()));
                });
                case JUMP -> pressKeyForTicks(mc, mc.options.keyJump,
                    action.getData().getBooleanOr("tap", true) ? 1 : Math.max(1, action.getData().getIntOr("durationTicks", 1)));
                case SNEAK -> runOnMain(mc, () -> mc.options.keyShift.setDown(action.getData().getBooleanOr("sneak", true)));
                case SPRINT -> runOnMain(mc, () -> {
                    boolean sprint = action.getData().getBooleanOr("sprint", true);
                    mc.options.keySprint.setDown(sprint);
                    if (mc.player != null) mc.player.setSprinting(sprint);
                });
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
                case SEND_TOGGLE -> {
                    String mode = action.getData().getStringOr("mode", "");
                    boolean state = "DISABLE".equalsIgnoreCase(mode) ? false :
                        ("ENABLE".equalsIgnoreCase(mode) ? true : !UiUtilsState.sendUiPackets);
                    UiUtilsState.sendUiPackets = state;
                }
                case DELAY_PACKETS -> {
                    String mode = action.getData().getStringOr("mode", "");
                    boolean state = "DISABLE".equalsIgnoreCase(mode) ? false :
                        ("ENABLE".equalsIgnoreCase(mode) ? true : !UiUtilsState.delayUiPackets);
                    UiUtilsState.delayUiPackets = state;
                }
                case DISCONNECT -> runOnMain(mc, () -> UiUtils.executeKeybindAction("disconnect", mc));
                case STOP_MACRO -> stop();
                default -> {
                    String command = action.getData().getStringOr("uiutilsCommand", "");
                    if (!command.isBlank()) UiUtilsCommandSystem.execute(command);
                    else UiUtils.LOGGER.info("Macro action {} currently has no direct executor mapping", action.getType());
                }
            }
        } catch (Throwable t) {
            UiUtils.LOGGER.warn("Macro action failed: {}", action.getType(), t);
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
        for (int i = 0; i < uses && running; i++) {
            runOnMain(mc, () -> {
                if (mc.player == null || mc.getConnection() == null) return;
                mc.getConnection().send(new ServerboundUseItemPacket(InteractionHand.MAIN_HAND, 0, mc.player.getYRot(), mc.player.getXRot()));
            });
            if ("CUSTOM_HOLD".equalsIgnoreCase(mode)) {
                sleepMillis(holdTicks * 50L);
                runOnMain(mc, () -> {
                    if (mc.getConnection() != null) {
                        mc.getConnection().send(new ServerboundPlayerActionPacket(ServerboundPlayerActionPacket.Action.RELEASE_USE_ITEM, BlockPos.ZERO, Direction.DOWN));
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
        float threshold = action.getData().getFloatOr("healthThreshold", 20.0f);
        boolean below = action.getData().contains("comparison")
            ? "Drops Below".equalsIgnoreCase(action.getData().getStringOr("comparison", "Drops Below"))
            : action.getData().getBooleanOr("below", true);
        long deadline = System.currentTimeMillis() + 30000L;
        while (running && System.currentTimeMillis() < deadline) {
            if (mc.player != null) {
                float hp = mc.player.getHealth();
                if (below ? hp < threshold : hp > threshold) return;
            }
            sleepMillis(50L);
        }
    }

    private static void waitForPos(Minecraft mc, UiUtilsMacroAction action) {
        double x = action.getData().getDoubleOr("x", 0.0);
        double y = action.getData().getDoubleOr("y", 0.0);
        double z = action.getData().getDoubleOr("z", 0.0);
        double leeway = Math.max(0.1, action.getData().getDoubleOr("leeway", 1.0));
        boolean checkRot = action.getData().getBooleanOr("checkRotation", false);
        float yaw = action.getData().getFloatOr("yaw", 0f);
        float pitch = action.getData().getFloatOr("pitch", 0f);
        float rotLeeway = Math.max(0.5f, action.getData().getFloatOr("rotLeeway", 5f));
        long deadline = System.currentTimeMillis() + 30000L;
        while (running && System.currentTimeMillis() < deadline) {
            if (mc.player != null) {
                double dx = mc.player.getX() - x;
                double dy = mc.player.getY() - y;
                double dz = mc.player.getZ() - z;
                boolean posOk = dx * dx + dy * dy + dz * dz <= leeway * leeway;
                boolean rotOk = !checkRot || (Math.abs(mc.player.getYRot() - yaw) <= rotLeeway
                    && Math.abs(mc.player.getXRot() - pitch) <= rotLeeway);
                if (posOk && rotOk) return;
            }
            sleepMillis(50L);
        }
    }

    private static void waitForGui(Minecraft mc, UiUtilsMacroAction action) {
        String mode = action.getData().getStringOr("waitMode", "OPEN");
        String expected = action.getData().getStringOr("guiTitle", "").toLowerCase(Locale.ROOT);
        long deadline = System.currentTimeMillis() + 30000L;
        while (running && System.currentTimeMillis() < deadline) {
            var screen = McCompat.getScreen(mc);
            boolean open = screen != null;
            String title = open && screen.getTitle() != null ? screen.getTitle().getString().toLowerCase(Locale.ROOT) : "";
            if ("CLOSE".equalsIgnoreCase(mode)) {
                if (!open || !title.contains(expected)) return;
            } else {
                if (open && (expected.isBlank() || title.contains(expected))) return;
            }
            sleepMillis(50L);
        }
    }

    private static void waitForChat(UiUtilsMacroAction action) {
        String pattern = action.getData().getStringOr("pattern", "").trim();
        boolean regex = action.getData().getBooleanOr("useRegex", false);
        int timeout = Math.max(0, action.getData().getIntOr("timeoutMs", 0));
        long deadline = System.currentTimeMillis() + (timeout <= 0 ? 30000L : timeout);
        String start = UiUtilsMacroRuntimeState.lastChatMessage();
        while (running && System.currentTimeMillis() < deadline) {
            String current = UiUtilsMacroRuntimeState.lastChatMessage();
            if (!current.equals(start)) {
                if (pattern.isBlank()) return;
                if (regex && Pattern.compile(pattern, Pattern.CASE_INSENSITIVE).matcher(current).find()) return;
                if (!regex && current.toLowerCase(Locale.ROOT).contains(pattern.toLowerCase(Locale.ROOT))) return;
            }
            sleepMillis(50L);
        }
    }

    private static void waitForPacket(UiUtilsMacroAction action) {
        String raw = action.getData().getStringOr("packetName", "").trim();
        long startIn = UiUtilsMacroRuntimeState.incomingCount();
        long startOut = UiUtilsMacroRuntimeState.outgoingCount();
        long deadline = System.currentTimeMillis() + 30000L;
        String needle = normalizePacketName(raw.contains(":") ? raw.substring(raw.indexOf(':') + 1) : raw);
        String dir = raw.contains(":") ? raw.substring(0, raw.indexOf(':')).toUpperCase(Locale.ROOT) : "";
        while (running && System.currentTimeMillis() < deadline) {
            boolean inAdvanced = UiUtilsMacroRuntimeState.incomingCount() > startIn;
            boolean outAdvanced = UiUtilsMacroRuntimeState.outgoingCount() > startOut;
            if (needle.isBlank()) {
                if (inAdvanced || outAdvanced) return;
            } else {
                if (!"C2S".equals(dir) && inAdvanced && normalizePacketName(UiUtilsMacroRuntimeState.lastIncomingPacket()).contains(needle)) return;
                if (!"S2C".equals(dir) && outAdvanced && normalizePacketName(UiUtilsMacroRuntimeState.lastOutgoingPacket()).contains(needle)) return;
            }
            sleepMillis(25L);
        }
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
        runOnMain(mc, () -> key.setDown(true));
        sleepMillis(Math.max(1, ticks) * 50L);
        runOnMain(mc, () -> key.setDown(false));
    }

    private static void runOnMain(Minecraft mc, Runnable runnable) {
        if (mc.isSameThread()) {
            runnable.run();
            return;
        }
        CountDownLatch latch = new CountDownLatch(1);
        mc.execute(() -> {
            try {
                runnable.run();
            } finally {
                latch.countDown();
            }
        });
        try {
            latch.await(2, TimeUnit.SECONDS);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
    }

    private static void sleepMillis(long millis) {
        try {
            Thread.sleep(Math.max(0L, millis));
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
    }
}
