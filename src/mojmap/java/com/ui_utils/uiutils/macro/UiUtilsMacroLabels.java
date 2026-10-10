package com.ui_utils.uiutils.macro;

import java.util.Locale;

/** Human readable names shared by the macro editor and local progress messages. */
public final class UiUtilsMacroLabels {
    private UiUtilsMacroLabels() {}

    public static String actionName(UiUtilsMacroActionType type) {
        return sentence(type.name());
    }

    public static String sentence(String value) {
        if (value == null || value.isBlank()) return "";
        String[] words = value.replace('_', ' ').trim().toLowerCase(Locale.ROOT).split("\\s+");
        StringBuilder result = new StringBuilder();
        for (String word : words) {
            if (word.isEmpty()) continue;
            if (!result.isEmpty()) result.append(' ');
            String upper = word.toUpperCase(Locale.ROOT);
            if (upper.equals("GUI") || upper.equals("NBT") || upper.equals("LAN")
                || upper.equals("ID") || upper.equals("IDS") || upper.equals("ASCII")
                || upper.equals("C2S") || upper.equals("S2C")) {
                result.append(upper);
            } else if (upper.equals("X-CARRY") || upper.equals("XCARRY")) {
                result.append("XCarry");
            } else {
                if (result.isEmpty()) result.append(Character.toUpperCase(word.charAt(0)));
                else result.append(word.charAt(0));
                result.append(word.substring(1));
            }
        }
        return result.toString();
    }

    public static String stepDescription(UiUtilsMacroAction action) {
        var data = action.getData();
        return switch (action.getType()) {
            case SEND_CHAT -> "Send chat message: “" + data.getStringOr("message", "") + "”";
            case SEND_COMMAND -> "Send command: /" + data.getStringOr("command", "").replaceFirst("^/+", "");
            case DELAY -> data.getBooleanOr("useTicks", false)
                ? "Wait " + data.getIntOr("delayTicks", 1) + " ticks"
                : "Wait " + data.getIntOr("delayMs", 50) + " ms";
            case SELECT_SLOT -> "Select hotbar slot " + (data.getIntOr("slot", 0) + 1);
            case USE_ITEM -> "Use " + (data.getStringOr("itemName", "").isBlank()
                ? "held item" : data.getStringOr("itemName", ""));
            case DROP -> "Drop " + (data.getStringOr("itemName", "").isBlank()
                ? "items" : data.getStringOr("itemName", ""));
            case MOVE -> "Move " + sentence(data.getStringOr("direction", "FORWARD"))
                + " for " + data.getIntOr("durationTicks", 20) + " ticks";
            case STORE_ITEM -> data.getStringOr("mode", "STORE").equalsIgnoreCase("LOOT")
                ? "Loot items" : "Store items";
            case CLOSE_GUI -> "Close GUI";
            case DESYNC -> "Send a close packet";
            case SAVE_GUI -> "Save GUI";
            case RESTORE_GUI -> "Restore GUI";
            case WAIT_ITEM -> "Wait for item";
            case WAIT_GUI -> "Wait for GUI to " + (data.getStringOr("waitMode", "OPEN").equalsIgnoreCase("CLOSE") ? "close" : "open");
            case WAIT_HEALTH -> (data.getStringOr("comparison", "Drops Below").equalsIgnoreCase("Rises Above")
                ? "Wait for health to rise above " : "Wait for health to drop below ")
                + data.getFloatOr("healthThreshold", 20f);
            default -> actionName(action.getType());
        };
    }
}
