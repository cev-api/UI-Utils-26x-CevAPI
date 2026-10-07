package com.ui_utils.uiutils.macro;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Predicate;

/** Rising-edge tracking shared by all saved macro bindings. */
public final class UiUtilsMacroKeybinds {
    private final Map<String, Boolean> keyDown = new HashMap<>();

    public void update(List<UiUtilsMacro> macros, Predicate<UiUtilsMacro> isDown,
        boolean allowExecution, Consumer<String> execute) {
        keyDown.keySet().retainAll(macros.stream().map(m -> m.name).toList());
        boolean started = false;
        for (UiUtilsMacro macro : macros) {
            boolean down = isDown.test(macro);
            boolean wasDown = keyDown.getOrDefault(macro.name, false);
            keyDown.put(macro.name, down);
            if (allowExecution && !started && down && !wasDown) {
                execute.accept(macro.name);
                started = true;
            }
        }
    }
}
