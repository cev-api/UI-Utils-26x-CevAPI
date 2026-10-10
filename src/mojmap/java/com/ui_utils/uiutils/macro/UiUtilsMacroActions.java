package com.ui_utils.uiutils.macro;

import com.ui_utils.uiutils.*;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ServerboundEditBookPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.*;

/** Client actions and conditions for the fields exposed by the macro editor. */
public final class UiUtilsMacroActions {
    private static HitResult lastContainerTarget;
    public static void reset() { lastContainerTarget = null; }
    private UiUtilsMacroActions() {}
    public static boolean handles(UiUtilsMacroActionType type) {
        return switch (type) {
            case WAIT_ITEM, WAIT_SLOT_CHANGE, WAIT_COOLDOWN, WAIT_BLOCK, WAIT_ENTITY, WAIT_SOUND, WAIT_LAN_STEP,
                TICK_SYNC, REVISION_SYNC, SERVER_TICK_SYNC, LOOK_AT_BLOCK, GO_TO, INVENTORY, XCARRY, CRAFT,
                CLICK, OPEN_CONTAINER, PACKET, SEND_PACKET, PAYLOAD, TOGGLE_MODULE, INVENTORY_AUDIT, MINE, PAY, NBT_BOOK -> true;
            default -> false;
        };
    }
    static void execute(Minecraft mc, UiUtilsMacroAction action) {
        CompoundTag data = action.getData();
        switch (action.getType()) {
            case WAIT_ITEM -> {
                List<String> targets = requiredList(data, "itemNames");
                wait(mc, action, () -> targets.stream().allMatch(target -> inventoryContains(mc, target)));
            }
            case WAIT_SLOT_CHANGE -> {
                List<String> targets = selectors(data, "itemNames");
                List<ItemStack> baseline = new ArrayList<>();
                net.minecraft.world.inventory.AbstractContainerMenu[] original = new net.minecraft.world.inventory.AbstractContainerMenu[1];
                main(mc, () -> {
                    requirePlayer(mc);
                    original[0] = mc.player.containerMenu;
                    for (var slot : original[0].slots) baseline.add(slot.getItem().copy());
                });
                wait(mc, action, () -> {
                    requirePlayer(mc);
                    if (mc.player.containerMenu != original[0]) throw new IllegalStateException("Container changed while waiting for slot change");
                    var slots = mc.player.containerMenu.slots;
                    if (slots.size() != baseline.size()) return targets.isEmpty();
                    for (int i = 0; i < slots.size(); i++) {
                        ItemStack before = baseline.get(i), after = slots.get(i).getItem();
                        if (!ItemStack.matches(before, after) && (targets.isEmpty() || slotMatches(targets, i, before, after))) return true;
                    }
                    return false;
                });
            }
            case WAIT_COOLDOWN -> wait(mc, action, () -> {
                requirePlayer(mc);
                String target = data.getStringOr("itemName", "");
                if (data.getBooleanOr("checkMainHand", false)) {
                    ItemStack held = mc.player.getMainHandItem();
                    return !held.isEmpty() && (target.isBlank() || matches(held, target))
                        && !mc.player.getCooldowns().isOnCooldown(held);
                }
                boolean found = false;
                for (var slot : mc.player.inventoryMenu.slots) if (!slot.getItem().isEmpty() && matches(slot.getItem(), target)) {
                    found = true; if (mc.player.getCooldowns().isOnCooldown(slot.getItem())) return false;
                }
                return found;
            });
            case WAIT_BLOCK -> {
                List<String> ids = selectors(data, "blockIds");
                if (!data.getBooleanOr("anyBlock", false) && ids.isEmpty()) throw new IllegalArgumentException("Choose block IDs or enable Any Block");
                wait(mc, action, () -> blockCondition(mc, data, ids));
            }
            case WAIT_ENTITY -> {
                List<String> ids = requiredList(data, "entityIds");
                wait(mc, action, () -> findEntity(mc, data, ids) != null);
            }
            case WAIT_SOUND -> {
                List<String> ids = requiredList(data, "soundIds");
                long start = UiUtilsMacroRuntimeState.soundCount();
                wait(mc, action, () -> guiMatches(mc, data) && UiUtilsMacroRuntimeState.sounds().stream().anyMatch(sound -> sound.sequence() > start
                    && ids.stream().anyMatch(id -> id.equals(sound.id()))
                    && (!data.getBooleanOr("checkDistance", false) || mc.player != null
                        && mc.player.position().distanceToSqr(new Vec3(sound.x(), sound.y(), sound.z())) <= square(data.getDoubleOr("maxDistance", 16)))));
            }
            case WAIT_LAN_STEP -> {
                throw new IllegalStateException("Wait for LAN Step is unavailable because macro progress is kept local and is not sent through server chat. Remove this legacy step to continue.");
            }
            case TICK_SYNC -> {
                long[] start = new long[1];
                main(mc, () -> { requirePlayer(mc); start[0] = mc.player.tickCount; });
                int ticks = bounded(data, "ticks", 1, 1, 2000);
                wait(mc, action, () -> mc.player != null && mc.player.tickCount - start[0] >= ticks);
            }
            case SERVER_TICK_SYNC -> {
                long start = UiUtilsMacroRuntimeState.serverTick();
                if (start < 0) throw new IllegalStateException("No server time update received in this session");
                int ticks = bounded(data, "ticks", 1, 1, 2000);
                wait(mc, action, () -> UiUtilsMacroRuntimeState.serverTick() - start >= ticks);
            }
            case REVISION_SYNC -> {
                int revision = data.getIntOr("revision", 0);
                wait(mc, action, () -> mc.player != null && (data.getBooleanOr("waitForMatch", false)
                    ? mc.player.containerMenu.getStateId() == revision : mc.player.containerMenu.getStateId() >= revision));
            }
            case LOOK_AT_BLOCK -> main(mc, () -> lookAt(mc, position(data).add(0.5, 0.5, 0.5)));
            case GO_TO -> {
                Vec3 target = position(data);
                if (!data.getBooleanOr("waitForArrival", true)) {
                    Thread owner = Thread.currentThread();
                    UiUtilsMacroExecutor.setHeldKey(mc, mc.options.keyUp, true, true);
                    scheduleGoTo(mc, owner, target);
                } else {
                    UiUtilsMacroExecutor.setHeldKey(mc, mc.options.keyUp, true);
                    try { wait(mc, action, () -> { requirePlayer(mc); lookAt(mc, new Vec3(target.x, mc.player.getEyeY(), target.z)); return mc.player.position().distanceToSqr(target) < 1; }); }
                    finally { UiUtilsMacroExecutor.releaseHeldKey(mc, mc.options.keyUp); }
                }
            }
            case INVENTORY -> main(mc, () -> {
                requirePlayer(mc);
                if (data.getBooleanOr("openInventory", true)) McCompat.setScreen(mc, new InventoryScreen(mc.player));
                else mc.player.closeContainer();
            });
            case XCARRY -> main(mc, () -> UiUtilsState.xCarry = data.getBooleanOr("enabled", true));
            case CRAFT -> craft(mc, action);
            case CLICK -> {
                for (int i = 0; i < bounded(data, "times", 1, 1, 1000); i++) {
                    main(mc, () -> {
                        requirePlayer(mc);
                        String button = data.getStringOr("button", "LEFT");
                        if (McCompat.getScreen(mc) instanceof net.minecraft.client.gui.screens.inventory.AbstractContainerScreen<?> screen) {
                            var event = new net.minecraft.client.input.MouseButtonEvent(mc.mouseHandler.xpos() * mc.getWindow().getGuiScaledWidth() / mc.getWindow().getScreenWidth(),
                                mc.mouseHandler.ypos() * mc.getWindow().getGuiScaledHeight() / mc.getWindow().getScreenHeight(),
                                new net.minecraft.client.input.MouseButtonInfo(mouseButton(button), 0));
                            screen.mouseClicked(event, false); screen.mouseReleased(event);
                        } else if (button.equalsIgnoreCase("RIGHT")) useTarget(mc, mc.hitResult);
                        else if (button.equalsIgnoreCase("MIDDLE")) {
                            if (mc.hitResult instanceof BlockHitResult block) mc.gameMode.handlePickItemFromBlock(block.getBlockPos(), false);
                            else if (mc.hitResult instanceof EntityHitResult entity) mc.gameMode.handlePickItemFromEntity(entity.getEntity(), false);
                        } else if (mc.hitResult instanceof EntityHitResult entity) mc.gameMode.attack(mc.player, entity.getEntity());
                        else if (mc.hitResult instanceof BlockHitResult block) mc.gameMode.startDestroyBlock(block.getBlockPos(), block.getDirection());
                    });
                    UiUtilsMacroExecutor.sleepMillis(50);
                }
            }
            case OPEN_CONTAINER -> {
                main(mc, () -> {
                    requirePlayer(mc);
                    String mode = data.getStringOr("targetMode", "BLOCK");
                    if (mode.equals("ENTITY")) {
                        Entity entity = findEntity(mc, data, requiredList(data, "entityTargets"));
                        if (entity == null) throw new IllegalStateException("No matching container entity in reach");
                        lastContainerTarget = new EntityHitResult(entity); useTarget(mc, lastContainerTarget);
                    } else if (mode.equals("LAST_TARGET")) {
                        if (lastContainerTarget == null) throw new IllegalStateException("No previous container target in this session");
                        useTarget(mc, lastContainerTarget);
                    }
                    else {
                        BlockPos pos = BlockPos.containing(position(data));
                        if (mc.player.getEyePosition().distanceToSqr(Vec3.atCenterOf(pos)) > square(mc.player.blockInteractionRange()))
                            throw new IllegalStateException("Container is out of reach");
                        lastContainerTarget = new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false); useTarget(mc, lastContainerTarget);
                    }
                });
                if (data.getBooleanOr("waitForGui", false)) wait(mc, action, () -> mc.player != null && mc.player.containerMenu != mc.player.inventoryMenu);
            }
            case PACKET, SEND_PACKET -> {
                if (data.getBooleanOr("waitForGui", false)) wait(mc, action, () -> guiMatches(mc, data));
                String name = required(data, "packetName");
                Packet<?> packet = UiUtilsMacroRuntimeState.outgoingPacket(name);
                if (packet == null) throw new IllegalStateException("No captured outgoing " + name + " in this session; perform that action first");
                for (int i = 0; i < bounded(data, "times", 1, 1, 1000); i++) main(mc, () -> { requirePlayer(mc); mc.getConnection().send(packet); });
            }
            case PAYLOAD -> main(mc, () -> { requirePlayer(mc); UiUtilsMacroPayload.send(mc, required(data, "channel"), data.getStringOr("payload", "")); });
            case TOGGLE_MODULE -> main(mc, () -> toggleModule(required(data, "moduleName"), data.getStringOr("mode", "TOGGLE")));
            case INVENTORY_AUDIT -> main(mc, () -> {
                requirePlayer(mc);
                for (var slot : mc.player.containerMenu.slots) {
                    ItemStack stack = slot.getItem();
                    if (!stack.isEmpty() && (stack.getCount() < 1 || data.getBooleanOr("strict", false) && stack.getCount() > stack.getMaxStackSize()))
                        throw new IllegalStateException("Invalid stack in slot " + slot.index);
                }
                for (String rule : selectors(data, "rules")) {
                    var match = java.util.regex.Pattern.compile("(\\d+)=([^>=]+)>=(\\d+)").matcher(rule);
                    if (!match.matches()) throw new IllegalArgumentException("Audit rule must be slot=item>=count: " + rule);
                    int slot = Integer.parseInt(match.group(1));
                    if (slot >= mc.player.containerMenu.slots.size()) throw new IllegalStateException("Audit slot missing: " + slot);
                    ItemStack stack = mc.player.containerMenu.slots.get(slot).getItem();
                    if (!matches(stack, match.group(2)) || stack.getCount() < Integer.parseInt(match.group(3))) throw new IllegalStateException("Audit failed: " + rule);
                }
                UiUtils.chatIfEnabled("Inventory audit passed");
            });
            case MINE -> {
                List<String> ids = requiredList(data, "blockIds");
                BlockPos[] target = new BlockPos[1];
                main(mc, () -> { requirePlayer(mc); target[0] = findBlock(mc, ids, Math.min(data.getDoubleOr("radius", 4), mc.player.blockInteractionRange())); });
                if (target[0] == null) throw new IllegalStateException("No matching block in reach");
                try {
                    wait(mc, action, () -> {
                        requirePlayer(mc);
                        if (mc.level.getBlockState(target[0]).isAir()) return true;
                        if (mc.player.getEyePosition().distanceToSqr(Vec3.atCenterOf(target[0])) > square(mc.player.blockInteractionRange())) throw new IllegalStateException("Mining target left reach");
                        lookAt(mc, Vec3.atCenterOf(target[0]));
                        if (!mc.gameMode.isDestroying()) mc.gameMode.startDestroyBlock(target[0], Direction.UP);
                        else mc.gameMode.continueDestroyBlock(target[0], Direction.UP);
                        return false;
                    });
                } finally { UiUtilsMacroExecutor.cleanupOnMain(mc, () -> mc.gameMode.stopDestroyBlock()); }
            }
            case PAY -> {
                String template = required(data, "commandTemplate"), amount = required(data, "amountInput");
                for (String player : requiredList(data, "players")) {
                    String command = template.replace("{player}", player).replace("{amount}", amount);
                    main(mc, () -> UiUtils.sendCommandWithConfiguredDelay(mc, command));
                    if (data.getBooleanOr("delayEnabled", false)) UiUtilsMacroExecutor.sleepMillis(bounded(data, "delayMs", 0, 0, 60000));
                }
            }
            case NBT_BOOK -> {
                int count = bounded(data, "bookCount", 1, 1, 64), pages = bounded(data, "pages", 1, 1, 100);
                String text = data.getStringOr("customText", "");
                if (data.getBooleanOr("onlyAscii", false)) text = text.replaceAll("[^\\x00-\\x7F]", "?");
                if (text.length() > 1024) throw new IllegalArgumentException("Book page exceeds 1024 characters");
                String page = text, title = data.getStringOr("title", "");
                if (title.length() > 32) throw new IllegalArgumentException("Book title exceeds 32 characters");
                for (int i = 0; i < count; i++) {
                    main(mc, () -> {
                        requirePlayer(mc);
                        int selected = mc.player.getInventory().getSelectedSlot();
                        int slot = mc.player.getMainHandItem().is(Items.WRITABLE_BOOK) ? selected : mc.player.getOffhandItem().is(Items.WRITABLE_BOOK) ? 40 : -1;
                        if (slot < 0) throw new IllegalStateException("Hold a writable book before NBT Book");
                        mc.getConnection().send(new ServerboundEditBookPacket(slot, Collections.nCopies(pages, page), title.isBlank() ? Optional.empty() : Optional.of(title)));
                    });
                    UiUtilsMacroExecutor.sleepMillis(bounded(data, "delayTicks", 0, 0, 200) * 50L);
                }
            }
            default -> throw new IllegalArgumentException("No executor for " + action.getType());
        }
    }
    private static void scheduleGoTo(Minecraft mc, Thread owner, Vec3 target) {
        com.ui_utils.uiutils.UiUtilsTasks.scheduleOwned(owner, () -> {
            if (mc.player == null || mc.getConnection() == null || mc.player.position().distanceToSqr(target) < 1) {
                UiUtilsMacroExecutor.releasePersistentHeldKey(mc, mc.options.keyUp, owner);
                return;
            }
            lookAt(mc, new Vec3(target.x, mc.player.getEyeY(), target.z));
            scheduleGoTo(mc, owner, target);
        }, 50);
    }

    static Vec3 position(CompoundTag data) {
        if (data.get("pos") instanceof CompoundTag pos) data = pos;
        double x = data.getDoubleOr("x", 0), y = data.getDoubleOr("y", 0), z = data.getDoubleOr("z", 0);
        if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)) throw new IllegalArgumentException("Position must contain finite coordinates");
        return new Vec3(x, y, z);
    }
    static List<String> selectors(CompoundTag data, String key) {
        List<String> result = new ArrayList<>();
        if (data.get(key) instanceof ListTag list) for (var value : list) {
            if (value instanceof CompoundTag tag) {
                if (tag.contains("slot")) result.add("#" + tag.getIntOr("slot", -1));
                else result.add(tag.getStringOr("id", tag.getStringOr("name", "")));
            } else value.asString().ifPresent(result::add);
        } else {
            String raw = data.getStringOr(key, "");
            if (!raw.isBlank()) result.addAll(Arrays.asList(raw.split("[,;\\n]")));
        }
        return result.stream().map(String::trim).filter(s -> !s.isBlank()).toList();
    }
    private static List<String> requiredList(CompoundTag data, String key) {
        List<String> result = selectors(data, key);
        if (result.isEmpty()) throw new IllegalArgumentException("Configure " + key + " first"); return result;
    }
    private static String required(CompoundTag data, String key) {
        String value = data.getStringOr(key, "").trim();
        if (value.isEmpty()) throw new IllegalArgumentException("Configure " + key + " first"); return value;
    }
    private static int bounded(CompoundTag data, String key, int fallback, int min, int max) {
        int n = data.getIntOr(key, fallback);
        if (n < min || n > max) throw new IllegalArgumentException(key + " must be between " + min + " and " + max); return n;
    }
    private static double square(double n) { return n * n; }
    private static void requirePlayer(Minecraft mc) {
        if (mc.player == null || mc.getConnection() == null || mc.level == null || mc.gameMode == null) throw new IllegalStateException("Not connected");
    }
    private static boolean matches(ItemStack stack, String selector) {
        if (stack.isEmpty()) return false;
        String needle = selector.contains("|") ? selector.substring(selector.indexOf('|') + 1) : selector;
        String id = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
        return needle.isBlank() || id.equalsIgnoreCase(needle) || id.equalsIgnoreCase("minecraft:" + needle) || stack.getHoverName().getString().equalsIgnoreCase(needle);
    }
    private static boolean slotMatches(List<String> targets, int slot, ItemStack before, ItemStack after) {
        return targets.stream().anyMatch(target -> target.startsWith("#")
            ? Integer.parseInt(target.substring(1).split("\\|", 2)[0]) == slot
            : matches(before, target) || matches(after, target));
    }
    private static boolean inventoryContains(Minecraft mc, String target) {
        requirePlayer(mc);
        if (target.startsWith("#")) {
            String number = target.substring(1).split("\\|", 2)[0];
            int slot = Integer.parseInt(number);
            return slot >= 0 && slot < mc.player.containerMenu.slots.size() && !mc.player.containerMenu.slots.get(slot).getItem().isEmpty()
                && (!target.contains("|") || matches(mc.player.containerMenu.slots.get(slot).getItem(), target));
        }
        return mc.player.inventoryMenu.slots.stream().anyMatch(slot -> matches(slot.getItem(), target));
    }
    private static boolean guiMatches(Minecraft mc, CompoundTag data) {
        if (!data.getBooleanOr("waitForGui", false)) return true;
        var screen = McCompat.getScreen(mc);
        return screen != null && screen.getTitle().getString().toLowerCase(Locale.ROOT).contains(data.getStringOr("waitGuiName", data.getStringOr("guiName", "")).toLowerCase(Locale.ROOT));
    }
    private static boolean blockMatches(Minecraft mc, BlockPos pos, List<String> ids) {
        var state = mc.level.getBlockState(pos);
        return !state.isAir() && (ids.isEmpty() || ids.contains(BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString()));
    }
    private static BlockPos findBlock(Minecraft mc, List<String> ids, double radius) {
        BlockPos origin = mc.player.blockPosition();
        int r = Math.min(32, (int)Math.ceil(radius));
        BlockPos best = null; double distance = square(radius);
        for (BlockPos pos : BlockPos.betweenClosed(origin.offset(-r, -r, -r), origin.offset(r, r, r))) {
            double candidate = mc.player.getEyePosition().distanceToSqr(Vec3.atCenterOf(pos));
            if (candidate <= distance && blockMatches(mc, pos, ids)) { best = pos.immutable(); distance = candidate; }
        }
        return best;
    }
    private static boolean blockCondition(Minecraft mc, CompoundTag data, List<String> ids) {
        requirePlayer(mc);
        String mode = data.getStringOr("checkMode", "AT_POSITION");
        boolean present;
        if (mode.equals("IN_REACH")) present = findBlock(mc, ids, Math.min(data.getDoubleOr("searchRadius", 4), mc.player.blockInteractionRange())) != null;
        else {
            BlockPos pos = mode.equals("LOOKING_AT") ? mc.hitResult instanceof BlockHitResult hit && hit.getType() != HitResult.Type.MISS ? hit.getBlockPos() : null : BlockPos.containing(position(data));
            if (pos == null) present = false;
            else {
                if (data.getBooleanOr("mustBeInReach", false) && mc.player.getEyePosition().distanceToSqr(Vec3.atCenterOf(pos)) > square(mc.player.blockInteractionRange())) return false;
                present = blockMatches(mc, pos, ids);
            }
        }
        return data.getStringOr("waitBehavior", "PLACED").equals("DESTROYED") ? !present : present;
    }
    private static Entity findEntity(Minecraft mc, CompoundTag data, List<String> ids) {
        requirePlayer(mc);
        String mode = data.getStringOr("checkMode", "WITHIN_REACH");
        if (mode.equals("LOOKING_AT") || data.getBooleanOr("mustBeLookingAt", false)) {
            return mc.hitResult instanceof EntityHitResult hit && ids.contains(BuiltInRegistries.ENTITY_TYPE.getKey(hit.getEntity().getType()).toString()) ? hit.getEntity() : null;
        }
        Vec3 center = data.getBooleanOr("centerOnPlayer", true) ? mc.player.position() : position(data);
        double radius = mode.equals("WITHIN_REACH") ? mc.player.entityInteractionRange() : data.getDoubleOr("radius", 16);
        return mc.level.getEntities(mc.player, new AABB(center, center).inflate(radius), entity -> ids.contains(BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString())
            && entity.position().distanceToSqr(center) <= square(radius)).stream().min(Comparator.comparingDouble(entity -> entity.distanceToSqr(mc.player))).orElse(null);
    }
    private static void lookAt(Minecraft mc, Vec3 target) {
        requirePlayer(mc);
        Vec3 delta = target.subtract(mc.player.getEyePosition());
        mc.player.setYRot((float)Math.toDegrees(Math.atan2(delta.z, delta.x)) - 90);
        mc.player.setXRot((float)-Math.toDegrees(Math.atan2(delta.y, Math.sqrt(delta.x * delta.x + delta.z * delta.z))));
    }
    private static void useTarget(Minecraft mc, HitResult target) {
        if (target instanceof BlockHitResult block && target.getType() != HitResult.Type.MISS) mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND, block);
        else if (target instanceof EntityHitResult entity) mc.gameMode.interact(mc.player, entity.getEntity(), entity, InteractionHand.MAIN_HAND);
        else mc.gameMode.useItem(mc.player, InteractionHand.MAIN_HAND);
    }
    private static int mouseButton(String button) {
        if (button.equalsIgnoreCase("LEFT")) return McCompat.LEFT_BUTTON;
        boolean sdl = McCompat.LEFT_BUTTON == 1;
        return button.equalsIgnoreCase("RIGHT") ? sdl ? 3 : 1 : sdl ? 2 : 2;
    }
    private static void toggleModule(String name, String mode) {
        boolean toggle = mode.equalsIgnoreCase("TOGGLE"), enabled = !mode.equalsIgnoreCase("DISABLE");
        switch (name.toLowerCase(Locale.ROOT).replace("-", "").replace("_", "").replace(" ", "")) {
            case "uiutils", "ui" -> UiUtilsState.enabled = toggle ? !UiUtilsState.enabled : enabled;
            case "packethud" -> UiUtilsSettings.get().packetHudEnabled = toggle ? !UiUtilsSettings.get().packetHudEnabled : enabled;
            case "sendpackets" -> UiUtilsState.sendUiPackets = toggle ? !UiUtilsState.sendUiPackets : enabled;
            case "delaypackets" -> UiUtilsState.delayUiPackets = toggle ? !UiUtilsState.delayUiPackets : enabled;
            case "autoduper" -> { if (toggle ? !UiUtilsAutoduper.isRunning() : enabled) UiUtilsAutoduper.start(); else UiUtilsAutoduper.stop("Macro"); }
            case "packetlogging" -> com.ui_utils.packettools.AdvancedPacketTool.setLoggingEnabled(toggle ? !com.ui_utils.packettools.AdvancedPacketTool.isLoggingEnabled() : enabled);
            case "packetdelay" -> com.ui_utils.packettools.AdvancedPacketTool.setDelayEnabled(toggle ? !com.ui_utils.packettools.AdvancedPacketTool.isDelayEnabled() : enabled);
            default -> throw new IllegalArgumentException("Unknown UI-Utils module: " + name);
        }
        UiUtilsSettings.save();
    }
    private static void craft(Minecraft mc, UiUtilsMacroAction action) {
        String requested = required(action.getData(), "recipeId");
        net.minecraft.world.item.crafting.display.RecipeDisplayId[] id = new net.minecraft.world.item.crafting.display.RecipeDisplayId[1];
        main(mc, () -> {
            requirePlayer(mc);
            for (var collection : mc.player.getRecipeBook().getCollections()) for (var recipe : collection.getRecipes()) {
                ItemStack result = recipe.display().result().resolveForFirstStack(net.minecraft.world.item.crafting.display.SlotDisplayContext.fromLevel(mc.level));
                if (Integer.toString(recipe.id().index()).equals(requested) || matches(result, requested)) { id[0] = recipe.id(); break; }
            }
            if (id[0] == null) throw new IllegalArgumentException("No known recipe display/output matches " + requested);
        });
        for (int i = 0; i < bounded(action.getData(), "times", 1, 1, 128); i++) {
            int[] revision = new int[1];
            main(mc, () -> { requirePlayer(mc); revision[0] = mc.player.containerMenu.getStateId(); mc.gameMode.handlePlaceRecipe(mc.player.containerMenu.containerId, id[0], false); });
            wait(mc, action, () -> mc.player != null && mc.player.containerMenu.getStateId() != revision[0] && !mc.player.containerMenu.slots.get(0).getItem().isEmpty());
            main(mc, () -> mc.gameMode.handleContainerInput(mc.player.containerMenu.containerId, 0, 0, ContainerInput.QUICK_MOVE, mc.player));
        }
    }
    private static void main(Minecraft mc, Runnable action) { UiUtilsMacroExecutor.runOnMain(mc, action); }
    private static void wait(Minecraft mc, UiUtilsMacroAction action, java.util.function.BooleanSupplier condition) { UiUtilsMacroExecutor.awaitCondition(mc, action, condition); }
}
