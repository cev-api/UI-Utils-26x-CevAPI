package com.ui_utils.uiutils;

import com.ui_utils.uiutils.macro.UiUtilsMacro;
import com.ui_utils.uiutils.macro.UiUtilsMacroKeybinds;
import com.ui_utils.uiutils.macro.UiUtilsMacroExecutor;
import com.ui_utils.uiutils.macro.UiUtilsMacroManager;
import java.util.ArrayList;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import net.minecraft.SharedConstants;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;

/** Headless regression checks; no game window or server is needed. */
public final class UiRegressionChecks {
    public static void main(String[] args) throws Exception {
        SharedConstants.tryDetectVersion();
        dropdownSelectionAfterRepeatedLayout();
        hiddenControlsAndScaleChanges();
        versionSpecificKeys();
        chatHistoryAndScaling();
        macroBindingRoundTrip();
        macroKeybindEdges();
        screenSetterDoesNotClearLevel();
        runtimeScreenApi();
        oldWorkerCannotStopNewMacro();
        System.out.println("UI regression checks passed for " + SharedConstants.getCurrentVersion().id());
    }

    private static UiUtilsDropdown dropdown() {
        return new UiUtilsDropdown(null, 0, 0, 216, 15, "Action",
            List.of("PICKUP", "QUICK_MOVE", "SWAP", "CLONE", "THROW", "QUICK_CRAFT", "PICKUP_ALL"));
    }

    private static void dropdownSelectionAfterRepeatedLayout() {
        for (float scale : new float[] {0.4F, 0.875F, 1F, 1.5F}) {
            UiUtilsDropdown widget = dropdown();
            widget.setX(320);
            widget.setY(80);
            widget.setWidth(Math.round(216 * scale));
            widget.setHeight(Math.round(15 * scale));
            int headerHeight = widget.getHeight();
            check(widget.mouseClicked(click(322, 81), false), "Header must open dropdown");
            // The panel reapplies its layout before every frame while the list is open.
            widget.setHeight(headerHeight);
            for (int index = 0; index < 7; index++) {
                widget.setExpanded(true);
                double y = widget.listTop() + (index + 0.5) * widget.itemHeight();
                check(widget.isMouseOver(322, y), "Option must remain inside hit test");
                check(widget.mouseClicked(click(322, y), false), "Option click must be consumed");
                check(widget.selectedIndex() == index, "Wrong option selected");
                check(!widget.isExpanded(), "Selection must close list");
                check(widget.getHeight() == headerHeight, "Expansion must not mutate layout height");
            }
            widget.visible = false;
            widget.setExpanded(true);
            check(!widget.isMouseOver(322, widget.listTop() + 1), "Hidden list must not take input");
        }
    }

    private static MouseButtonEvent click(double x, double y) {
        return new MouseButtonEvent(x, y, new MouseButtonInfo(McCompat.LEFT_BUTTON, 0));
    }

    private static void hiddenControlsAndScaleChanges() throws Exception {
        Field scaleField = UiUtilsPanels.class.getDeclaredField("panelScale");
        scaleField.setAccessible(true);
        Field cacheField = UiUtilsPanels.class.getDeclaredField("designSizes");
        cacheField.setAccessible(true);
        Method layout = UiUtilsPanels.class.getDeclaredMethod("scalePanelWidgets", List.class, int.class, int.class);
        layout.setAccessible(true);
        ((Map<?, ?>) cacheField.get(null)).clear();
        UiUtilsDropdown widget = dropdown();
        widget.visible = false;
        layout.invoke(null, List.of(widget), 700, 8);
        check(((Map<?, ?>) cacheField.get(null)).isEmpty(), "Hidden controls must not cache unlaid positions");
        widget.visible = true;
        for (float scale : new float[] {1F, 0.4F, 1.5F, 0.875F, 1F}) {
            scaleField.setFloat(null, scale);
            for (int origin : new int[] {700, 50, 300}) {
                for (int modeY : new int[] {110, 160, 90}) {
                    widget.setX(origin + 8);
                    widget.setY(8 + modeY);
                    layout.invoke(null, List.of(widget), origin, 8);
                    check(widget.getX() == origin + Math.round(8 * scale), "Panel drag or scale changed relative x");
                    check(widget.getY() == 8 + Math.round(modeY * scale), "Mode switch reused stale y");
                    check(widget.getWidth() == Math.round(216 * scale), "Scale compounded on width");
                }
            }
        }
    }

    private static void versionSpecificKeys() {
        boolean sdl = McCompat.LEFT_BUTTON == 1;
        check(McCompat.isConfirmationKey(new KeyEvent(sdl ? 40 : 257, 0, 0)), "Enter must submit");
        check(McCompat.isClearKey(new KeyEvent(sdl ? 42 : 259, 0, 0)), "Backspace must clear");
        if (!sdl) {
            check(!McCompat.isClearKey(new KeyEvent(76, 0, 0)), "GLFW L must not clear a binding");
            check(!McCompat.isClearKey(new KeyEvent(42, 0, 0)), "GLFW punctuation must not clear");
            check(!McCompat.isConfirmationKey(new KeyEvent(40, 0, 0)), "GLFW punctuation must not submit");
        }
    }

    private static void chatHistoryAndScaling() {
        boolean sdl = McCompat.LEFT_BUTTON == 1;
        check(McCompat.chatHistoryDirection(new KeyEvent(sdl ? 82 : 265, 0, 0)) == -1, "Up must recall older chat");
        check(McCompat.chatHistoryDirection(new KeyEvent(sdl ? 81 : 264, 0, 0)) == 1, "Down must recall newer chat");
        check(McCompat.chatHistoryDirection(new KeyEvent(sdl ? 265 : 82, 0, 0)) == 0, "Other input backend must not recall history");
        UiUtilsChatHistory cursor = new UiUtilsChatHistory();
        List<String> sent = List.of("first", "/second");
        check(cursor.move(sent, "draft", -1).equals("/second"), "Up must start at newest sent message");
        check(cursor.move(sent, "/second", -1).equals("first"), "Up must reach oldest message");
        check(cursor.move(sent, "first", -1).equals("first"), "Oldest history boundary must clamp");
        check(cursor.move(sent, "first", 1).equals("/second"), "Down must return toward newest");
        check(cursor.move(sent, "/second", 1).equals("draft"), "Down must restore unsent draft");
        check(cursor.move(sent, "draft", 1).equals("draft"), "Newest history boundary must retain draft");
        cursor.move(sent, "draft", -1);
        cursor.reset();
        check(cursor.move(sent, "", 1).isEmpty(), "Sending must clear history cursor and draft");
        check(cursor.move(List.of(), "unsent", -1).equals("unsent"), "Empty history must retain draft");
        var field = new com.ui_utils.uiutils.ui.UiInput(null, 120, "", net.minecraft.network.chat.Component.empty());
        for (float scale : new float[] {0.4F, 0.875F, 1F, 1.5F}) {
            field.setWidth(Math.round(120 * scale));
            field.applyUiScale(scale);
            check(field.getInnerWidth() == 112, "Scaled field must measure text in the same units as rendering");
        }
    }

    private static void macroBindingRoundTrip() {
        UiUtilsMacro macro = new UiUtilsMacro();
        macro.keyCode = 65;
        macro.keyName = "key.keyboard.a";
        UiUtilsMacro copy = macro.deepCopy();
        check(copy.keyName.equals(macro.keyName), "Portable binding was lost on save/copy");
        check(copy.keyCode == 65, "Legacy binding was lost on save/copy");
        check(UiUtilsMacroManager.bindingKey(copy).getName().equals("key.keyboard.a"),
            "Portable key name does not resolve in the runtime input system");
    }

    private static void macroKeybindEdges() {
        UiUtilsMacro macro = new UiUtilsMacro();
        macro.name = "Test";
        List<UiUtilsMacro> macros = List.of(macro);
        List<String> runs = new ArrayList<>();
        UiUtilsMacroKeybinds keys = new UiUtilsMacroKeybinds();
        keys.update(macros, m -> true, true, runs::add);
        keys.update(macros, m -> true, true, runs::add);
        check(runs.size() == 1, "Holding a key must not restart the macro every tick");
        keys.update(macros, m -> false, true, runs::add);
        keys.update(macros, m -> true, false, runs::add);
        keys.update(macros, m -> true, true, runs::add);
        check(runs.size() == 1, "Typing/rebinding must not queue a macro run");
        keys.update(macros, m -> false, true, runs::add);
        keys.update(macros, m -> true, true, runs::add);
        check(runs.size() == 2, "Releasing and pressing again must run the macro");
        UiUtilsMacro duplicate = new UiUtilsMacro();
        duplicate.name = "Duplicate key";
        keys.update(List.of(macro, duplicate), m -> false, true, runs::add);
        keys.update(List.of(macro, duplicate), m -> true, true, runs::add);
        check(runs.size() == 3, "Duplicate bindings must start only one macro");
    }

    public static final class ScreenOwner {
        boolean set;
        boolean cleared;
        public void clearClientLevel(net.minecraft.client.gui.screens.Screen screen) { cleared = true; }
        public void setScreen(net.minecraft.client.gui.screens.Screen screen) { set = true; }
    }

    private static void screenSetterDoesNotClearLevel() throws Exception {
        Method setter = McCompat.class.getDeclaredMethod("invokeScreenSetter", Object.class,
            net.minecraft.client.gui.screens.Screen.class);
        setter.setAccessible(true);
        ScreenOwner owner = new ScreenOwner();
        check((boolean) setter.invoke(null, owner, null), "Named setter must be found");
        check(owner.set && !owner.cleared, "Screen navigation must never clear the world");
        check(!(boolean) setter.invoke(null, new Object(), null), "Missing setter must fall through");
    }

    private static void runtimeScreenApi() {
        boolean found = false;
        for (Class<?> owner : List.of(net.minecraft.client.Minecraft.class, net.minecraft.client.gui.Gui.class)) {
            try {
                owner.getMethod("setScreen", net.minecraft.client.gui.screens.Screen.class);
                found = true;
            } catch (NoSuchMethodException ignored) {}
        }
        check(found, "Runtime has no named screen setter");
        check(java.util.Arrays.stream(com.mojang.blaze3d.platform.InputConstants.class.getMethods())
            .anyMatch(m -> m.getName().equals("isKeyDown") && m.getReturnType() == boolean.class),
            "Runtime has no compatible key polling API");
    }

    private static void oldWorkerCannotStopNewMacro() throws Exception {
        Field worker = UiUtilsMacroExecutor.class.getDeclaredField("worker");
        Field running = UiUtilsMacroExecutor.class.getDeclaredField("running");
        worker.setAccessible(true);
        running.setAccessible(true);
        Thread oldRun = new Thread();
        Thread newRun = new Thread();
        worker.set(null, newRun);
        running.setBoolean(null, true);
        Method finish = UiUtilsMacroExecutor.class.getDeclaredMethod("finishRun", Thread.class);
        finish.setAccessible(true);
        finish.invoke(null, oldRun);
        check(UiUtilsMacroExecutor.isRunning(), "An old worker stopped the replacement macro");
        finish.invoke(null, newRun);
        check(!UiUtilsMacroExecutor.isRunning(), "Current worker failed to finish");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
