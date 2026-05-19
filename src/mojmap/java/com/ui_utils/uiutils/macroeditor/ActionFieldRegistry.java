package com.ui_utils.uiutils.macroeditor;

import com.ui_utils.uiutils.macro.UiUtilsMacroActionType;
import java.util.EnumMap;
import java.util.Map;

public final class ActionFieldRegistry {
    private static final ActionFieldSchema EMPTY = ActionFieldSchema.builder().build();
    private static final Map<UiUtilsMacroActionType, ActionFieldSchema> SCHEMAS = new EnumMap<>(UiUtilsMacroActionType.class);

    public static ActionFieldSchema get(UiUtilsMacroActionType type) {
        return SCHEMAS.getOrDefault(type, EMPTY);
    }

    private ActionFieldRegistry() {}

    static {
        SCHEMAS.put(UiUtilsMacroActionType.DELAY, ActionFieldSchema.builder().toggle("useTicks", "Use Ticks").number("delayMs", "Delay (ms)").range(0, 300000).hideWhen("useTicks").number("delayTicks", "Delay (ticks)").range(0, 20000).showWhen("useTicks").build());
        SCHEMAS.put(UiUtilsMacroActionType.WAIT_HEALTH, ActionFieldSchema.builder().decimal("healthThreshold", "Target Health").decRange(0.0, 20.0).enumField("comparison", "Condition", "Drops Below", "Rises Above").build());
        SCHEMAS.put(UiUtilsMacroActionType.WAIT_BLOCK, ActionFieldSchema.builder().enumField("checkMode", "Check Mode", "AT_POSITION", "IN_REACH", "LOOKING_AT").enumField("waitBehavior", "Wait For", "PLACED", "DESTROYED").toggle("anyBlock", "Any Block").stringList("blockIds", "Block IDs").addLabel("Add").captureBlock().hideWhen("anyBlock").blockPos("pos", "Position").showWhenEnum("checkMode", "AT_POSITION").toggle("mustBeInReach", "Must Be In Reach").showWhenEnum("checkMode", "AT_POSITION").decimal("searchRadius", "Search Radius").decRange(0.0, 32.0).showWhenEnum("checkMode", "IN_REACH").build());
        SCHEMAS.put(UiUtilsMacroActionType.WAIT_GUI, ActionFieldSchema.builder().enumField("waitMode", "Wait Mode", "OPEN", "CLOSE").text("guiTitle", "GUI Title").build());
        SCHEMAS.put(UiUtilsMacroActionType.ROTATE, ActionFieldSchema.builder().decimal("yaw", "Yaw").decRange(-180.0, 180.0).decimal("pitch", "Pitch").decRange(-90.0, 90.0).toggle("smooth", "Smooth").number("smoothness", "Smoothness").range(1, 10).showWhen("smooth").toggle("waitForCompletion", "Wait for Completion").build());
        SCHEMAS.put(UiUtilsMacroActionType.USE_ITEM, ActionFieldSchema.builder().text("itemName", "Item Name").enumField("useMode", "Use Mode", "AUTOMATIC", "CUSTOM_HOLD").number("holdTicks", "Hold Ticks").range(1, 1000).showWhenEnum("useMode", "CUSTOM_HOLD").number("useCount", "Use Count").range(1, 1000).showWhenEnum("useMode", "AUTOMATIC").build());
        SCHEMAS.put(UiUtilsMacroActionType.SELECT_SLOT, ActionFieldSchema.builder().slot("slot", "Slot").text("itemName", "Item Name").build());
        SCHEMAS.put(UiUtilsMacroActionType.CLOSE_GUI, ActionFieldSchema.builder().text("guiName", "GUI Name").toggle("useItemFilter", "Filter by Item").text("itemName", "Item Name").showWhen("useItemFilter").slot("targetSlot", "Target Slot").showWhen("useItemFilter").toggle("sendPacket", "Close without pkt").build());
        SCHEMAS.put(UiUtilsMacroActionType.SWAP_SLOTS, ActionFieldSchema.builder().toggle("fromUseItemName", "From: Use Item Name").text("fromItemName", "From: Item Name").showWhen("fromUseItemName").slot("fromSlot", "From: Slot").hideWhen("fromUseItemName").toggle("toUseItemName", "To: Use Item Name").text("toItemName", "To: Item Name").showWhen("toUseItemName").slot("toSlot", "To: Slot").hideWhen("toUseItemName").build());
        SCHEMAS.put(UiUtilsMacroActionType.WAIT_COOLDOWN, ActionFieldSchema.builder().text("itemName", "Item Name").toggle("checkMainHand", "Check Main Hand").build());
        SCHEMAS.put(UiUtilsMacroActionType.GO_TO, ActionFieldSchema.builder().blockPos("pos", "Target Position").xyzDouble(true).toggle("waitForArrival", "Wait for Arrival").build());
        SCHEMAS.put(UiUtilsMacroActionType.WAIT_POS, ActionFieldSchema.builder().blockPos("pos", "Position").xyzDouble(true).decimal("leeway", "Leeway").decRange(0.0, 100.0).toggle("checkRotation", "Check Rotation").decimal("yaw", "Yaw").decRange(-180.0, 180.0).showWhen("checkRotation").decimal("pitch", "Pitch").decRange(-90.0, 90.0).showWhen("checkRotation").decimal("rotLeeway", "Rotation Leeway").decRange(0.0, 180.0).showWhen("checkRotation").build());
        SCHEMAS.put(UiUtilsMacroActionType.DISCONNECT, ActionFieldSchema.builder().enumField("mode", "Mode", "DISCONNECT", "KICK", "KICK_DUPE", "AUTO_DISCONNECT").number("delayMs", "Delay (ms)").range(0, 10000).showWhenEnum("mode", "DISCONNECT").number("packetCount", "Packet Count").range(1, 1000).hideWhenEnum("mode", "DISCONNECT").hideWhenEnum("mode", "AUTO_DISCONNECT").toggle("useNextAction", "Use Next Action").showWhenEnum("mode", "KICK_DUPE").enumField("trigger", "Trigger", "TELEPORT", "POSITION", "WORLD_CHANGE", "GUI_CLOSE", "INVENTORY_CLEAR").showWhenEnum("mode", "AUTO_DISCONNECT").number("timeoutSec", "Timeout (sec)").range(1, 300).showWhenEnum("mode", "AUTO_DISCONNECT").build());
        SCHEMAS.put(UiUtilsMacroActionType.STOP_MACRO, EMPTY);
        SCHEMAS.put(UiUtilsMacroActionType.SNEAK, ActionFieldSchema.builder().toggle("sneak", "Sneak").toggle("persistent", "Persistent").build());
        SCHEMAS.put(UiUtilsMacroActionType.JUMP, ActionFieldSchema.builder().toggle("tap", "Tap (single tick)").number("durationTicks", "Duration (ticks)").range(1, 200).hideWhen("tap").build());
        SCHEMAS.put(UiUtilsMacroActionType.SPRINT, ActionFieldSchema.builder().toggle("sprint", "Sprint").toggle("persistent", "Persistent").build());
        SCHEMAS.put(UiUtilsMacroActionType.MOVE, ActionFieldSchema.builder().enumField("direction", "Direction", "FORWARD", "BACKWARD", "LEFT", "RIGHT").number("durationTicks", "Duration (ticks)").range(1, 10000).toggle("nonBlocking", "Non-blocking").build());
        SCHEMAS.put(UiUtilsMacroActionType.REPEAT, ActionFieldSchema.builder().number("stepCount", "Steps to Repeat").range(1, 1000).number("repeatCount", "Repeat Count").range(1, 10000).build());
        SCHEMAS.put(UiUtilsMacroActionType.WAIT_CHAT, ActionFieldSchema.builder().text("pattern", "Pattern").toggle("useRegex", "Use Regex").number("fuzzyPercent", "Match Strength").range(40, 100).hideWhen("useRegex").toggle("serverMessageOnly", "Server Message Only").number("timeoutMs", "Timeout (ms)").range(0, 300000).toggle("waitForGui", "Wait for GUI").text("waitGuiName", "GUI Name").showWhen("waitForGui").build());
        SCHEMAS.put(UiUtilsMacroActionType.WAIT_ENTITY, ActionFieldSchema.builder().enumField("checkMode", "Check Mode", "RADIUS", "LOOKING_AT", "WITHIN_REACH").stringList("entityIds", "Entity IDs").addLabel("Add").toggle("centerOnPlayer", "Center on Player").showWhenEnum("checkMode", "RADIUS").blockPos("pos", "Position").xyzDouble(true).decimal("radius", "Radius").decRange(0.0, 100.0).showWhenEnum("checkMode", "RADIUS").toggle("mustBeLookingAt", "Must Be Looking At").showWhenEnum("checkMode", "RADIUS").build());
        SCHEMAS.put(UiUtilsMacroActionType.OPEN_CONTAINER, ActionFieldSchema.builder().enumField("targetMode", "Target", "BLOCK", "ENTITY", "LAST_TARGET").blockPos("pos", "Container Position").showWhenEnum("targetMode", "BLOCK").stringList("entityTargets", "Container Entity").addLabel("Add").showWhenEnum("targetMode", "ENTITY").toggle("waitForGui", "Wait for GUI").build());
        SCHEMAS.put(UiUtilsMacroActionType.DESYNC, EMPTY);
        SCHEMAS.put(UiUtilsMacroActionType.RESTORE_GUI, ActionFieldSchema.builder().toggle("waitForGui", "Wait for GUI").build());
        SCHEMAS.put(UiUtilsMacroActionType.SAVE_GUI, ActionFieldSchema.builder().toggle("closeAfter", "Close After Saving").toggle("sendPacket", "Close without pkt").showWhen("closeAfter").build());
        SCHEMAS.put(UiUtilsMacroActionType.SEND_TOGGLE, ActionFieldSchema.builder().enumField("mode", "Mode", "ENABLE", "DISABLE").build());
        SCHEMAS.put(UiUtilsMacroActionType.DELAY_PACKETS, ActionFieldSchema.builder().enumField("mode", "Mode", "ENABLE", "DISABLE").toggle("flushOnDisable", "Flush on Disable").showWhenEnum("mode", "DISABLE").stringList("c2sPackets", "C2S Packets").addLabel("Add").showWhenEnum("mode", "ENABLE").stringList("s2cPackets", "S2C Packets").addLabel("Add").showWhenEnum("mode", "ENABLE").build());
        SCHEMAS.put(UiUtilsMacroActionType.STORE_ITEM, ActionFieldSchema.builder().enumField("mode", "Mode", "LOOT", "STORE").toggle("allItems", "All Items").stringList("targetItems", "Target Items").addLabel("Add").hideWhen("allItems").toggle("persistent", "Loop Forever").toggle("closeAfter", "Close After").hideWhen("persistent").toggle("closeSendPkt", "Close without pkt").showWhen("closeAfter").build());
        SCHEMAS.put(UiUtilsMacroActionType.WAIT_SOUND, ActionFieldSchema.builder().stringList("soundIds", "Sound IDs").addLabel("Add").toggle("waitForGui", "Wait for GUI").text("waitGuiName", "GUI Name").showWhen("waitForGui").toggle("checkDistance", "Check Distance").decimal("maxDistance", "Max Distance").decRange(0.0, 256.0).showWhen("checkDistance").build());
        SCHEMAS.put(UiUtilsMacroActionType.PAY, ActionFieldSchema.builder().text("commandTemplate", "Command Template").text("amountInput", "Amount").toggle("delayEnabled", "Use Delay").number("delayMs", "Delay (ms)").range(0, 60000).showWhen("delayEnabled").stringList("players", "Players").addLabel("Add").build());
        SCHEMAS.put(UiUtilsMacroActionType.SEND_CHAT, ActionFieldSchema.builder().text("message", "Message").toggle("waitForGui", "Wait for GUI").text("guiName", "GUI Name").showWhen("waitForGui").build());
        SCHEMAS.put(UiUtilsMacroActionType.NBT_BOOK, ActionFieldSchema.builder().number("pages", "Pages").range(1, 100).text("title", "Title").toggle("onlyAscii", "Only ASCII").text("customText", "Custom Component").number("delayTicks", "Delay (ticks)").range(0, 200).number("bookCount", "Book Count").range(1, 64).build());

        // Inventory-family and core action editors (verbatim behavior baseline)
        SCHEMAS.put(UiUtilsMacroActionType.ITEM, ActionFieldSchema.builder()
            .toggle("useSlot", "Use Slot")
            .slot("targetSlot", "Target Slot").showWhen("useSlot")
            .stringList("itemNames", "Item Names").addLabel("Add").hideWhen("useSlot")
            .number("actionIndex", "Action Index").range(0, 8)
            .number("button", "Button").range(0, 9)
            .number("times", "Times").range(1, 1000)
            .build());
        SCHEMAS.put(UiUtilsMacroActionType.DROP, ActionFieldSchema.builder()
            .enumField("mode", "Mode", "TIMES", "ALL")
            .number("count", "Count").range(1, 1000).showWhenEnum("mode", "TIMES")
            .slot("slot", "Slot")
            .text("itemName", "Item Name")
            .build());
        SCHEMAS.put(UiUtilsMacroActionType.USE_ITEM, ActionFieldSchema.builder()
            .slot("slot", "Slot")
            .text("itemName", "Item Name")
            .enumField("useMode", "Use Mode", "AUTOMATIC", "CUSTOM_HOLD")
            .number("useCount", "Use Count").range(1, 1000).showWhenEnum("useMode", "AUTOMATIC")
            .number("holdTicks", "Hold Ticks").range(1, 1000).showWhenEnum("useMode", "CUSTOM_HOLD")
            .build());
        SCHEMAS.put(UiUtilsMacroActionType.SWAP_SLOTS, ActionFieldSchema.builder()
            .slot("fromSlot", "From Slot")
            .slot("toSlot", "To Slot")
            .build());
        SCHEMAS.put(UiUtilsMacroActionType.SELECT_SLOT, ActionFieldSchema.builder()
            .slot("slot", "Slot")
            .text("itemName", "Item Name")
            .build());
        SCHEMAS.put(UiUtilsMacroActionType.OPEN_CONTAINER, ActionFieldSchema.builder()
            .enumField("targetMode", "Target", "BLOCK", "ENTITY", "LAST_TARGET")
            .blockPos("pos", "Container Position").showWhenEnum("targetMode", "BLOCK")
            .stringList("entityTargets", "Container Entity").addLabel("Add").showWhenEnum("targetMode", "ENTITY")
            .toggle("waitForGui", "Wait for GUI")
            .build());
        SCHEMAS.put(UiUtilsMacroActionType.INVENTORY, ActionFieldSchema.builder()
            .toggle("openInventory", "Open Inventory GUI")
            .build());
        SCHEMAS.put(UiUtilsMacroActionType.XCARRY, ActionFieldSchema.builder()
            .toggle("enabled", "Enable XCarry")
            .build());
        SCHEMAS.put(UiUtilsMacroActionType.CRAFT, ActionFieldSchema.builder()
            .text("recipeId", "Recipe ID")
            .number("times", "Times").range(1, 128)
            .build());
        SCHEMAS.put(UiUtilsMacroActionType.CLICK, ActionFieldSchema.builder()
            .enumField("button", "Button", "LEFT", "RIGHT", "MIDDLE")
            .number("times", "Times").range(1, 1000)
            .build());
        SCHEMAS.put(UiUtilsMacroActionType.PACKET, ActionFieldSchema.builder()
            .text("packetName", "Packet Name")
            .toggle("waitForGui", "Wait for GUI")
            .text("waitGuiName", "GUI Name").showWhen("waitForGui")
            .build());
        SCHEMAS.put(UiUtilsMacroActionType.PAYLOAD, ActionFieldSchema.builder()
            .text("channel", "Channel")
            .text("payload", "Payload")
            .build());
        SCHEMAS.put(UiUtilsMacroActionType.TOGGLE_MODULE, ActionFieldSchema.builder()
            .text("moduleName", "Module Name")
            .enumField("mode", "Mode", "TOGGLE", "ENABLE", "DISABLE")
            .build());
        SCHEMAS.put(UiUtilsMacroActionType.SEND_PACKET, ActionFieldSchema.builder()
            .text("packetName", "Packet Name")
            .number("times", "Times").range(1, 1000)
            .build());
        SCHEMAS.put(UiUtilsMacroActionType.WAIT_PACKET, ActionFieldSchema.builder()
            .text("packetName", "Packet Name")
            .build());
        SCHEMAS.put(UiUtilsMacroActionType.WAIT_SLOT_CHANGE, ActionFieldSchema.builder()
            .stringList("itemNames", "Items / Slots").addLabel("Add")
            .build());
        SCHEMAS.put(UiUtilsMacroActionType.WAIT_ITEM, ActionFieldSchema.builder()
            .stringList("itemNames", "Items").addLabel("Add")
            .build());
        SCHEMAS.put(UiUtilsMacroActionType.WAIT_LAN_STEP, ActionFieldSchema.builder()
            .text("peerName", "Peer Name")
            .text("macroName", "Macro Name")
            .number("step", "Step").range(1, 10000)
            .build());
        SCHEMAS.put(UiUtilsMacroActionType.TICK_SYNC, ActionFieldSchema.builder()
            .number("ticks", "Ticks").range(1, 2000)
            .build());
        SCHEMAS.put(UiUtilsMacroActionType.REVISION_SYNC, ActionFieldSchema.builder()
            .number("revision", "Revision").range(0, 1000000)
            .toggle("waitForMatch", "Wait For Match")
            .build());
        SCHEMAS.put(UiUtilsMacroActionType.SERVER_TICK_SYNC, ActionFieldSchema.builder()
            .number("ticks", "Server Ticks").range(1, 2000)
            .build());
        SCHEMAS.put(UiUtilsMacroActionType.INVENTORY_AUDIT, ActionFieldSchema.builder()
            .toggle("strict", "Strict Validation")
            .stringList("rules", "Rules").addLabel("Add Rule")
            .build());
        SCHEMAS.put(UiUtilsMacroActionType.MINE, ActionFieldSchema.builder()
            .stringList("blockIds", "Block IDs").addLabel("Add")
            .decimal("radius", "Radius").decRange(1.0, 128.0)
            .build());
    }
}
