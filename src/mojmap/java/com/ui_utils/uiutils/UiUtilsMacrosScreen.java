package com.ui_utils.uiutils;

import com.mojang.blaze3d.platform.InputConstants;
import com.ui_utils.uiutils.macro.UiUtilsMacro;
import com.ui_utils.uiutils.macro.UiUtilsMacroAction;
import com.ui_utils.uiutils.macro.UiUtilsMacroActionType;
import com.ui_utils.uiutils.macro.UiUtilsMacroExecutor;
import com.ui_utils.uiutils.macro.UiUtilsMacroManager;
import com.ui_utils.uiutils.macroeditor.ActionFieldRegistry;
import com.ui_utils.uiutils.ui.UiButton;
import com.ui_utils.uiutils.ui.UiContent;
import com.ui_utils.uiutils.ui.UiInput;
import com.ui_utils.uiutils.ui.UiListRow;
import com.ui_utils.uiutils.ui.UiModernScreen;
import com.ui_utils.uiutils.ui.UiTheme;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

public final class UiUtilsMacrosScreen extends UiModernScreen {
    private final Screen parent;
    private final String initialMacroName;

    private UiUtilsMacro editing;
    private String originalName;
    private boolean loaded;
    private UiInput nameField;
    private boolean waitingForBindKey = false;
    private int selectedStep = -1;
    private int stepOffset = 0;
    private String status = "";

    private final Deque<UiUtilsMacro> undoStack = new ArrayDeque<>();
    private final Deque<UiUtilsMacro> redoStack = new ArrayDeque<>();

    private final List<UiListRow> stepRows = new ArrayList<>();
    private final List<UiButton> rowControls = new ArrayList<>();

    public UiUtilsMacrosScreen(Screen parent) {
        this(parent, null);
    }

    public UiUtilsMacrosScreen(Screen parent, String initialMacroName) {
        super(Component.literal("Create Macro"));
        this.parent = parent;
        this.initialMacroName = initialMacroName;
    }

    @Override
    protected int naturalWidth() {
        return 420;
    }

    @Override
    protected boolean expandWidth() {
        return false;
    }

    @Override
    protected void buildContent(UiContent c) {
        if (!loaded) {
            loadMacro();
            loaded = true;
        }

        nameField = c.input(editing.name == null ? "" : editing.name, value -> {});
        nameField.setHint(Component.literal("Macro Name"));

        stepRows.clear();
        rowControls.clear();
        c.row(UiContent.of(bindButton()), UiContent.of(loopButton()),
            UiContent.of(UiButton.of("Once", this::runOnce)),
            UiContent.of(UiButton.of("Run", this::runEditing)),
            UiContent.of(UiButton.of("Stop", UiUtilsMacroExecutor::stop)));
        c.row(UiContent.of(UiButton.of("Add Action",
                () -> openPicker(UiUtilsMacroTypePickerScreen.Mode.ACTION))),
            UiContent.of(UiButton.of("Add Conditional",
                () -> openPicker(UiUtilsMacroTypePickerScreen.Mode.CONDITION))));

        // One row per step, always. The panel itself scrolls, so nothing is hidden
        // behind the footer. A spare empty row keeps the edit controls reachable
        // when the macro is empty, and the cap bounds the height for a huge macro.
        int slots = Math.max(3, Math.min(40, editing.actions.size() + 1));
        for (int slot = 0; slot < slots; slot++) {
            final int rowSlot = slot;
            UiListRow row = new UiListRow("", () -> selectStep(rowSlot, false));
            row.doubleRun(() -> selectStep(rowSlot, true));
            stepRows.add(row);
            c.row(UiContent.of(row, 6F),
                UiContent.fixed(control("^", rowSlot, c0 -> moveStep(stepOffset + c0, -1)), 22),
                UiContent.fixed(control("v", rowSlot, c0 -> moveStep(stepOffset + c0, 1)), 22),
                UiContent.fixed(control("D", rowSlot, c0 -> duplicateStep(stepOffset + c0)), 22),
                UiContent.fixed(control("E", rowSlot, c0 -> editStep(stepOffset + c0)), 22),
                UiContent.fixed(control("X", rowSlot, c0 -> deleteStep(stepOffset + c0)), 22));
        }

        // Breathing room so the footer reads as its own band under the step list.
        c.space(6);
        c.footerButton("Save", UiButton.Kind.SECONDARY, this::saveMacro);
        c.footerButton("Cancel", UiButton.Kind.SECONDARY,
            () -> McCompat.setScreen(minecraft, parent));
        c.footerButton("Undo", UiButton.Kind.SECONDARY, this::undo);
        c.footerButton("Redo", UiButton.Kind.SECONDARY, this::redo);
        c.footerButton("Done", UiButton.Kind.PRIMARY, this::saveAndClose);

        refreshRows();
    }

    private UiButton bindButton() {
        return UiButton.of(bindLabel(), () -> {
            waitingForBindKey = true;
            setStatusText("Press a key (ESC clears)");
        });
    }

    private UiButton loopButton() {
        return UiButton.of(loopToggleLabel(), () -> {
            pushUndo();
            editing.loop = !editing.loop;
            scheduleRebuild();
        });
    }

    private UiButton control(String label, int slot, java.util.function.IntConsumer action) {
        UiButton button = UiButton.of(label, null);
        button.action(() -> action.accept(slot));
        rowControls.add(button);
        return button;
    }

    private void selectStep(int slot, boolean open) {
        int index = stepOffset + slot;
        if (index < 0 || index >= editing.actions.size())
            return;
        selectedStep = index;
        refreshRows();
        if (open)
            editStep(index);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX,
        double scrollY) {
        // Only take the wheel for the step list when it has more steps than slots;
        // otherwise the panel needs it to reach the rest of the form.
        if (!stepRows.isEmpty() && scrollY != 0
            && editing.actions.size() > stepRows.size()) {
            int designY = designY(mouseY);
            UiListRow first = stepRows.get(0);
            UiListRow last = stepRows.get(stepRows.size() - 1);
            if (designY >= first.getY() && designY <= last.getY() + last.getHeight()) {
                stepOffset = Mth.clamp(stepOffset + (scrollY < 0 ? 1 : -1), 0,
                    maxStepOffset());
                refreshRows();
                return true;
            }
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    private void scheduleRebuild() {
        Minecraft.getInstance().execute(this::rebuildWidgets);
    }

    private void setStatusText(String value) {
        status = value == null ? "" : value;
        setStatus(status);
    }

    /** "Done" saves first; it used to be identical to Cancel and silently discarded edits. */
    private void saveAndClose() {
        if (saveMacro()) McCompat.setScreen(minecraft, parent);
    }

    private void openPicker(UiUtilsMacroTypePickerScreen.Mode mode) {
        syncNameField();
        McCompat.setScreen(minecraft, new UiUtilsMacroTypePickerScreen(this, mode));
    }

    private void syncNameField() {
        if (nameField != null) {
            String name = nameField.getValue().trim();
            if (!name.isBlank()) editing.name = name;
        }
    }

    private void loadMacro() {
        UiUtilsMacro source = null;
        if (initialMacroName != null && !initialMacroName.isBlank()) {
            source = UiUtilsMacroManager.get().getByName(initialMacroName);
        }
        if (source == null) {
            editing = new UiUtilsMacro();
            editing.name = UiUtilsMacroManager.get().createUniqueName("New Macro");
            originalName = null;
        } else {
            editing = source.deepCopy();
            originalName = source.name;
        }
        selectedStep = -1;
        stepOffset = 0;
        undoStack.clear();
        redoStack.clear();
    }

    private String bindLabel() {
        if (waitingForBindKey) return "Press Key...";
        if (editing.keyCode < 0)
            return "Bind Key";
        String name = InputConstants.Type.KEYBOARD.getOrCreate(editing.keyCode)
            .getDisplayName().getString();
        return "Key: " + name;
    }

    private String loopToggleLabel() {
        return editing.loop ? "Loop: ON" : "Loop: OFF";
    }

    private int maxStepOffset() {
        return Math.max(0, editing.actions.size() - stepRows.size());
    }

    private void refreshRows() {
        if (stepOffset > maxStepOffset()) stepOffset = maxStepOffset();
        for (int i = 0; i < stepRows.size(); i++) {
            int actual = stepOffset + i;
            boolean hasStep = actual >= 0 && actual < editing.actions.size();
            UiListRow row = stepRows.get(i);
            row.active = hasStep;
            row.label(hasStep ? stepText(actual) : "");
            row.selected(hasStep && actual == selectedStep);
            for (int c = 0; c < 5; c++) {
                int controlIndex = i * 5 + c;
                if (controlIndex < rowControls.size()) {
                    UiButton button = rowControls.get(controlIndex);
                    button.visible = hasStep;
                    button.active = hasStep;
                }
            }
        }
        setStatus(status);
    }

    private String stepText(int index) {
        UiUtilsMacroAction action = editing.actions.get(index);
        String title = action.getType().name().replace('_', ' ').toLowerCase(Locale.ROOT);
        return (index + 1) + "  " + title;
    }

    private void pushUndo() {
        undoStack.push(editing.deepCopy());
        redoStack.clear();
    }

    private void undo() {
        if (undoStack.isEmpty()) return;
        redoStack.push(editing.deepCopy());
        editing = undoStack.pop();
        refreshRows();
        scheduleRebuild();
    }

    private void redo() {
        if (redoStack.isEmpty()) return;
        undoStack.push(editing.deepCopy());
        editing = redoStack.pop();
        refreshRows();
        scheduleRebuild();
    }

    private void moveStep(int index, int delta) {
        if (index < 0 || index >= editing.actions.size()) return;
        int to = index + delta;
        if (to < 0 || to >= editing.actions.size()) return;
        pushUndo();
        UiUtilsMacroAction a = editing.actions.remove(index);
        editing.actions.add(to, a);
        selectedStep = to;
        ensureVisible();
        refreshRows();
    }

    private void duplicateStep(int index) {
        if (index < 0 || index >= editing.actions.size()) return;
        pushUndo();
        UiUtilsMacroAction copy = UiUtilsMacroAction.fromTag(editing.actions.get(index).toTag());
        editing.actions.add(index + 1, copy);
        selectedStep = index + 1;
        ensureVisible();
        refreshRows();
    }

    private void deleteStep(int index) {
        if (index < 0 || index >= editing.actions.size()) return;
        pushUndo();
        editing.actions.remove(index);
        if (selectedStep >= editing.actions.size()) selectedStep = editing.actions.size() - 1;
        ensureVisible();
        refreshRows();
    }

    private void editStep(int index) {
        if (index < 0 || index >= editing.actions.size()) return;
        syncNameField();
        selectedStep = index;
        if (ActionFieldRegistry.get(editing.actions.get(index).getType()).fields().isEmpty()) {
            setStatusText("No options for " + editing.actions.get(index).getType().name());
            refreshRows();
            return;
        }
        UiUtilsMacroAction draft = UiUtilsMacroAction.fromTag(editing.actions.get(index).toTag());
        McCompat.setScreen(minecraft, new UiUtilsStepEditScreen(this, draft, () -> {
            pushUndo();
            editing.actions.set(index, draft);
            McCompat.setScreen(minecraft, this);
            refreshRows();
        }));
    }

    private void ensureVisible() {
        if (selectedStep < stepOffset) stepOffset = selectedStep;
        if (selectedStep >= stepOffset + stepRows.size()) stepOffset = selectedStep - stepRows.size() + 1;
        if (stepOffset < 0) stepOffset = 0;
    }

    private boolean saveMacro() {
        syncNameField();
        String n = nameField == null ? editing.name : nameField.getValue().trim();
        if (n == null || n.isBlank()) {
            setStatusText("Name Required");
            return false;
        }
        editing.name = n;
        if (originalName != null) UiUtilsMacroManager.get().remove(originalName);
        UiUtilsMacro saved = UiUtilsMacroManager.get().add(editing.deepCopy(), true);
        // add() renames on a name clash, so adopt the name it actually stored.
        if (saved != null) {
            editing.name = saved.name;
            originalName = saved.name;
        }
        status = "Saved";
        setStatus(status);
        return true;
    }

    private void runEditing() {
        saveMacro();
        UiUtilsMacroManager.get().execute(editing.name);
    }

    private void runOnce() {
        syncNameField();
        UiUtilsMacro temp = editing.deepCopy();
        temp.loop = false;
        UiUtilsMacroExecutor.start(temp);
    }

    public void addStepFromPicker(UiUtilsMacroActionType type) {
        UiUtilsMacroAction a = new UiUtilsMacroAction();
        a.setType(type);
        applyDefaults(a);
        if (ActionFieldRegistry.get(type).fields().isEmpty()) {
            pushUndo();
            editing.actions.add(a);
            selectedStep = editing.actions.size() - 1;
            ensureVisible();
            McCompat.setScreen(minecraft, this);
            refreshRows();
            return;
        }
        McCompat.setScreen(minecraft, new UiUtilsStepEditScreen(this, a, () -> {
            pushUndo();
            editing.actions.add(a);
            selectedStep = editing.actions.size() - 1;
            ensureVisible();
            McCompat.setScreen(minecraft, this);
            refreshRows();
        }));
    }

    public static List<UiUtilsMacroActionType> actionTypesForPicker() {
        return List.of(UiUtilsMacroActionType.values());
    }

    public static List<UiUtilsMacroActionType> conditionTypesForPicker() {
        return List.of(
            UiUtilsMacroActionType.WAIT_HEALTH,
            UiUtilsMacroActionType.WAIT_COOLDOWN,
            UiUtilsMacroActionType.WAIT_ITEM,
            UiUtilsMacroActionType.WAIT_SLOT_CHANGE,
            UiUtilsMacroActionType.WAIT_POS,
            UiUtilsMacroActionType.WAIT_BLOCK,
            UiUtilsMacroActionType.WAIT_ENTITY,
            UiUtilsMacroActionType.WAIT_SOUND,
            UiUtilsMacroActionType.WAIT_GUI,
            UiUtilsMacroActionType.WAIT_CHAT,
            UiUtilsMacroActionType.WAIT_PACKET,
            UiUtilsMacroActionType.TICK_SYNC,
            UiUtilsMacroActionType.REVISION_SYNC,
            UiUtilsMacroActionType.SERVER_TICK_SYNC,
            UiUtilsMacroActionType.WAIT_LAN_STEP
        );
    }

    @Override
    public boolean keyPressed(KeyEvent keyEvent) {
        if (waitingForBindKey) {
            if (keyEvent.isEscape()) {
                editing.keyCode = -1;
            } else {
                editing.keyCode = InputConstants.getKey(keyEvent).getValue();
            }
            waitingForBindKey = false;
            setStatusText("");
            scheduleRebuild();
            return true;
        }
        return super.keyPressed(keyEvent);
    }

    @Override
    public void onClose() {
        McCompat.setScreen(Minecraft.getInstance(), parent);
    }

    private static void applyDefaults(UiUtilsMacroAction a) {
        var d = a.getData();
        switch (a.getType()) {
            case SEND_CHAT -> d.putString("message", "");
            case SEND_COMMAND -> d.putString("command", "");
            case DELAY -> {
                d.putBoolean("useTicks", false);
                d.putInt("delayMs", 250);
                d.putInt("delayTicks", 5);
            }
            case WAIT_HEALTH -> {
                d.putDouble("healthThreshold", 20.0);
                d.putString("comparison", "Drops Below");
            }
            case WAIT_COOLDOWN -> d.putString("itemName", "");
            case WAIT_ITEM, WAIT_SLOT_CHANGE -> d.putString("itemNames", "");
            case WAIT_POS -> {
                d.putDouble("x", 0.0);
                d.putDouble("y", 0.0);
                d.putDouble("z", 0.0);
                d.putDouble("leeway", 1.0);
            }
            case WAIT_BLOCK -> {
                d.putString("checkMode", "AT_POSITION");
                d.putString("waitBehavior", "PLACED");
            }
            case WAIT_ENTITY -> d.putString("checkMode", "RADIUS");
            case WAIT_SOUND -> d.putDouble("maxDistance", 16.0);
            case WAIT_GUI -> d.putString("waitMode", "OPEN");
            case WAIT_CHAT -> {
                d.putString("pattern", "");
                d.putInt("timeoutMs", 0);
            }
            case WAIT_PACKET -> d.putString("packetName", "");
            case TICK_SYNC, SERVER_TICK_SYNC -> d.putInt("ticks", 1);
            case REVISION_SYNC -> d.putInt("revision", 0);
            case ITEM -> {
                d.putBoolean("useSlot", true);
                d.putInt("targetSlot", 0);
                d.putInt("actionIndex", 0);
                d.putInt("button", 0);
                d.putInt("times", 1);
            }
            case USE_ITEM -> {
                d.putInt("slot", 0);
                d.putString("useMode", "AUTOMATIC");
                d.putInt("useCount", 1);
                d.putInt("holdTicks", 20);
            }
            case DROP -> {
                d.putString("mode", "TIMES");
                d.putInt("count", 1);
                d.putInt("slot", 0);
            }
            case SWAP_SLOTS -> {
                d.putInt("fromSlot", 0);
                d.putInt("toSlot", 1);
            }
            case SELECT_SLOT -> d.putInt("slot", 0);
            case STORE_ITEM -> d.putString("mode", "STORE");
            default -> {}
        }
        a.setData(d);
    }
}
