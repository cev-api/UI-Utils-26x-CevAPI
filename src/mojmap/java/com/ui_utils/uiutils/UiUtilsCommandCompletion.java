package com.ui_utils.uiutils;

import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import com.ui_utils.uiutils.macro.UiUtilsMacroManager;

/** Local suggestions; never send UI-Utils input to the server for completion. */
public final class UiUtilsCommandCompletion {
    private UiUtilsCommandCompletion() {}

    /** Null means ordinary chat/server command input, which vanilla should handle. */
    public static Suggestions suggest(String text, int cursor, boolean bareCommands) {
        cursor = Math.max(0, Math.min(cursor, text.length()));
        String before = text.substring(0, cursor);
        int first = 0;
        while (first < before.length() && Character.isWhitespace(before.charAt(first))) first++;
        int rootEnd = first;
        while (rootEnd < before.length() && !Character.isWhitespace(before.charAt(rootEnd))) rootEnd++;
        String root = before.substring(first, rootEnd).toLowerCase(Locale.ROOT);
        boolean rooted = root.equals(UiUtilsCommandSystem.ROOT_COMMAND) || root.equals(UiUtilsCommandSystem.ALT_ROOT_COMMAND);
        if (!rooted) {
            if (rootEnd == before.length() && !root.isEmpty()
                && (UiUtilsCommandSystem.ROOT_COMMAND.startsWith(root) || UiUtilsCommandSystem.ALT_ROOT_COMMAND.startsWith(root)))
                return build(before, first, List.of(UiUtilsCommandSystem.ROOT_COMMAND, UiUtilsCommandSystem.ALT_ROOT_COMMAND));
            if (!bareCommands || root.isEmpty() || !Arrays.stream(UiUtilsCommandSystem.SUBCOMMANDS).anyMatch(c -> c.startsWith(root))) return null;
        } else if (rootEnd == before.length()) {
            // Append the separator so the next Tab offers commands, not the root again.
            return build(before, first, List.of(root + " "));
        }
        int bodyStart = rooted ? rootEnd : first;
        while (bodyStart < before.length() && Character.isWhitespace(before.charAt(bodyStart))) bodyStart++;
        String body = before.substring(bodyStart);
        int tokenStart = before.length();
        while (tokenStart > bodyStart && !Character.isWhitespace(before.charAt(tokenStart - 1))) tokenStart--;
        String[] words = body.split("\\s+", -1);
        List<String> options = words.length == 1 ? List.of(UiUtilsCommandSystem.SUBCOMMANDS) : arguments(words);
        return build(before, tokenStart, options);
    }

    private static Suggestions build(String input, int start, List<String> options) {
        SuggestionsBuilder builder = new SuggestionsBuilder(input, start);
        String prefix = input.substring(start).toLowerCase(Locale.ROOT);
        for (String option : options) if (option.toLowerCase(Locale.ROOT).startsWith(prefix)) builder.suggest(option);
        return builder.build();
    }

    private static List<String> arguments(String[] words) {
        String command = words[0].toLowerCase(Locale.ROOT);
        if (words.length == 2) return switch (command) {
            case "queue" -> List.of("list", "clear", "sendone", "poplast", "spam");
            case "delay", "sendpackets", "sendui" -> List.of("on", "off", "toggle");
            case "screen" -> List.of("save", "load", "list", "info");
            case "macro", "macros" -> List.of("list", "run", "start", "stop", "delete", "remove", "import", "export", "status");
            case "gui", "gtools" -> List.of("status", "info", "save", "saveclose", "load", "restore", "clear", "copy", "json", "steal", "store", "dump", "tools", "screen");
            case "guilog" -> List.of("on", "off", "toggle", "file", "clear", "copy", "open", "show");
            case "guipackets", "gpkt" -> List.of("list", "cycle", "reset", "delay", "screen");
            case "packethud", "phud", "hud" -> List.of("cycle", "toggle", "on", "off", "topleft", "topright", "bottomleft", "bottomright");
            case "autoduper", "duper" -> List.of("open", "screen", "start", "stop", "status", "slot", "command", "cmd", "attempt", "hybrid");
            case "disconnectmethod", "dcmethod" -> java.util.stream.Stream.concat(java.util.stream.Stream.of("list", "current"), Arrays.stream(UiUtilsDisconnect.Method.values()).map(Enum::name)).toList();
            case "lagmethod" -> java.util.stream.Stream.concat(java.util.stream.Stream.of("list", "current"), Arrays.stream(UiUtilsDisconnect.LagMethod.values()).map(Enum::name)).toList();
            default -> List.of();
        };
        if (words.length == 3) {
            String action = words[1].toLowerCase(Locale.ROOT);
            if ((command.equals("macro") || command.equals("macros")) && List.of("run", "start", "delete", "remove", "export").contains(action))
                return UiUtilsMacroManager.get().getAll().stream().map(m -> m.name).filter(name -> !name.contains(" ")).toList();
            if (command.equals("screen") && List.of("load", "info").contains(action)) return List.copyOf(UiUtilsState.savedScreens.keySet());
            if ((command.equals("guipackets") || command.equals("gpkt")) && action.equals("cycle")) return UiUtilsGuiPacketControl.entries().stream().map(UiUtilsGuiPacketControl.Entry::id).toList();
        }
        return List.of();
    }
}
