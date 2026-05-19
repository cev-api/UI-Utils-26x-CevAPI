package com.ui_utils.uiutils.macro;

public enum UiUtilsMacroActionType {
    DELAY,
    PACKET,
    WAIT_PACKET,
    WAIT_HEALTH,
    WAIT_ITEM,
    WAIT_BLOCK,
    WAIT_GUI,
    CLICK,
    ROTATE,
    USE_ITEM,
    INVENTORY,
    SEND_PACKET,
    CRAFT,
    SELECT_SLOT,
    XCARRY,
    DROP,
    ITEM,
    TICK_SYNC,
    REVISION_SYNC,
    SERVER_TICK_SYNC,
    CLOSE_GUI,
    SWAP_SLOTS,
    WAIT_COOLDOWN,
    GO_TO,
    WAIT_POS,
    PAYLOAD,
    DISCONNECT,
    TOGGLE_MODULE,
    STOP_MACRO,
    SNEAK,
    JUMP,
    SPRINT,
    MOVE,
    LOOK_AT_BLOCK,
    REPEAT,
    WAIT_CHAT,
    WAIT_ENTITY,
    WAIT_SLOT_CHANGE,
    OPEN_CONTAINER,
    DESYNC,
    RESTORE_GUI,
    SAVE_GUI,
    SEND_TOGGLE,
    DELAY_PACKETS,
    INVENTORY_AUDIT,
    STORE_ITEM,
    WAIT_SOUND,
    MINE,
    PAY,
    SEND_CHAT,
    NBT_BOOK,
    WAIT_LAN_STEP;

    public static UiUtilsMacroActionType byName(String name) {
        if (name == null || name.isBlank()) return DELAY;
        try {
            return valueOf(name.trim().toUpperCase());
        } catch (IllegalArgumentException ignored) {
            return DELAY;
        }
    }
}
