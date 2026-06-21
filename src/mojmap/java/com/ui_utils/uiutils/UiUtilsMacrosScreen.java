package com.ui_utils.uiutils;

import com.mojang.blaze3d.platform.InputConstants;
import com.ui_utils.uiutils.macro.UiUtilsMacro;
import com.ui_utils.uiutils.macro.UiUtilsMacroAction;
import com.ui_utils.uiutils.macro.UiUtilsMacroActionType;
import com.ui_utils.uiutils.macro.UiUtilsMacroExecutor;
import com.ui_utils.uiutils.macro.UiUtilsMacroManager;
import com.ui_utils.uiutils.macroeditor.ActionFieldRegistry;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

public final class UiUtilsMacrosScreen extends Screen {
    private final Screen parent;
    private final String initialMacroName;

    private UiUtilsMacro editing;
    private String originalName;
    private boolean loaded;
    private EditBox nameField;
    private boolean waitingForBindKey = false;
    private int selectedStep = -1;
    private int stepOffset = 0;
    private String status = "";

    private final Deque<UiUtilsMacro> undoStack = new ArrayDeque<>();
    private final Deque<UiUtilsMacro> redoStack = new ArrayDeque<>();

    private final List<StepRowWidget> stepRows = new ArrayList<>();
    private final List<UiUtilsColoredButton> rowControls = new ArrayList<>();
    private boolean draggingScrollbar;
    private int scrollbarGrabOffset;
    private ScrollbarMetrics lastScrollbar = ScrollbarMetrics.none();

    public UiUtilsMacrosScreen(Screen parent) {
        this(parent, null);
    }

    public UiUtilsMacrosScreen(Screen parent, String initialMacroName) {
        super(Component.literal("Create Macro"));
        this.parent = parent;
        this.initialMacroName = initialMacroName;
    }

    @Override
    protected void init() {
        clearWidgets();
        if (!loaded) {
            loadMacro();
            loaded = true;
        }

        int left = this.width / 2 - 250;
        int top = Math.max(8, this.height / 2 - 188);
        int row = 18;
        int gap = 3;
        int width = 500;
        int half = (width - gap) / 2;

        nameField = new EditBox(this.font, left, top + row + gap, width, row, Component.literal("Macro Name"));
        nameField.setHint(Component.literal("Macro Name"));
        nameField.setValue(editing.name == null ? "" : editing.name);
        addRenderableWidget(nameField);

        int y = top + (row + gap) * 2;
        addRenderableWidget(UiUtils.styledButton(bindLabel(), b -> {
            waitingForBindKey = true;
            status = "Press a key (ESC clears)";
        }, left, y, 84, row));

        addRenderableWidget(UiUtils.styledButton("Run", b -> {
            pushUndo();
            editing.loop = !editing.loop;
            rebuild();
        }, left + 88, y, 64, row));

        addRenderableWidget(UiUtils.styledButton("Once", b -> runOnce(), left + 156, y, 56, row));
        addRenderableWidget(UiUtils.styledButton("Run", b -> runEditing(), left + 216, y, 56, row));
        addRenderableWidget(UiUtils.styledButton("Stop", b -> UiUtilsMacroExecutor.stop(), left + 276, y, 56, row));

        y += row + gap + 2;
        addRenderableWidget(UiUtils.styledButton("Add Action", b ->
            openPicker(UiUtilsMacroTypePickerScreen.Mode.ACTION),
            left, y, half, row));
        addRenderableWidget(UiUtils.styledButton("Add Conditional", b ->
            openPicker(UiUtilsMacroTypePickerScreen.Mode.CONDITION),
            left + half + gap, y, half, row));

        y += row + gap;
        int listY = y;
        stepRows.clear();
        rowControls.clear();
        for (int i = 0; i < 8; i++) {
            int ry = listY + i * (row + gap);
            StepRowWidget rw = addRenderableWidget(new StepRowWidget(left, ry, 374, row, i));
            stepRows.add(rw);
            int bx = left + 378;
            int idx = i;
            rowControls.add(addRenderableWidget(UiUtils.styledButton("^", b -> moveStep(stepOffset + idx, -1), bx, ry, 22, row))); bx += 24;
            rowControls.add(addRenderableWidget(UiUtils.styledButton("v", b -> moveStep(stepOffset + idx, +1), bx, ry, 22, row))); bx += 24;
            rowControls.add(addRenderableWidget(UiUtils.styledButton("D", b -> duplicateStep(stepOffset + idx), bx, ry, 22, row))); bx += 24;
            rowControls.add(addRenderableWidget(UiUtils.styledButton("E", b -> editStep(stepOffset + idx), bx, ry, 22, row))); bx += 24;
            rowControls.add(addRenderableWidget(UiUtils.styledButton("X", b -> deleteStep(stepOffset + idx), bx, ry, 22, row)));
        }

        int by = listY + (row + gap) * 8 + 7;
        addRenderableWidget(UiUtils.styledButton("Save", b -> saveMacro(), left, by, 110, row));
        addRenderableWidget(UiUtils.styledButton("Cancel", b -> McCompat.setScreen(minecraft, parent), left + 114, by, 110, row));
        addRenderableWidget(UiUtils.styledButton("Undo", b -> undo(), left + 228, by, 86, row));
        addRenderableWidget(UiUtils.styledButton("Redo", b -> redo(), left + 318, by, 86, row));
        addRenderableWidget(UiUtils.styledButton("Done", b -> McCompat.setScreen(minecraft, parent), left + 408, by, 92, row));

        refreshRows();
    }

    private void rebuild() {
        syncNameField();
        init();
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
        return editing.keyCode < 0 ? "Bind Key" : "Key: " + editing.keyCode;
    }

    private String loopLabel() {
        return editing.loop ? "Run" : "Run";
    }

    private int maxStepOffset() {
        return Math.max(0, editing.actions.size() - stepRows.size());
    }

    private void refreshRows() {
        if (stepOffset > maxStepOffset()) stepOffset = maxStepOffset();
        for (int i = 0; i < stepRows.size(); i++) {
            int actual = stepOffset + i;
            boolean hasStep = actual >= 0 && actual < editing.actions.size();
            stepRows.get(i).setMessage(Component.literal(rowText(i)));
            for (int c = 0; c < 5; c++) {
                int controlIndex = i * 5 + c;
                if (controlIndex < rowControls.size()) {
                    rowControls.get(controlIndex).visible = hasStep;
                    rowControls.get(controlIndex).active = hasStep;
                }
            }
        }
    }

    private String rowText(int row) {
        int idx = stepOffset + row;
        if (idx < 0 || idx >= editing.actions.size()) return "";
        UiUtilsMacroAction a = editing.actions.get(idx);
        String title = a.getType().name().replace('_', ' ');
        String marker = idx == selectedStep ? "> " : "";
        return marker + (idx + 1) + "  " + title;
    }

    private void pushUndo() {
        undoStack.push(editing.deepCopy());
        redoStack.clear();
    }

    private void undo() {
        if (undoStack.isEmpty()) return;
        redoStack.push(editing.deepCopy());
        editing = undoStack.pop();
        if (nameField != null) nameField.setValue(editing.name == null ? "" : editing.name);
        refreshRows();
    }

    private void redo() {
        if (redoStack.isEmpty()) return;
        undoStack.push(editing.deepCopy());
        editing = redoStack.pop();
        if (nameField != null) nameField.setValue(editing.name == null ? "" : editing.name);
        refreshRows();
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
            status = "No options for " + editing.actions.get(index).getType().name();
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

    private void saveMacro() {
        syncNameField();
        String n = nameField.getValue().trim();
        if (n.isBlank()) {
            status = "Name Required";
            return;
        }
        editing.name = n;
        if (originalName != null) UiUtilsMacroManager.get().remove(originalName);
        UiUtilsMacroManager.get().add(editing.deepCopy(), true);
        originalName = editing.name;
        status = "Saved";
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
            rebuild();
            return true;
        }
        return super.keyPressed(keyEvent);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTicks) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTicks);
        int left = this.width / 2 - 250;
        int screenTop = Math.max(8, this.height / 2 - 188);
        int top = screenTop - this.font.lineHeight - 3;
        graphics.text(this.font, "Create Macro (" + editing.actions.size() + " steps)", left, top, 0xFFE6EEF7, false);
        graphics.text(this.font, status, left, top + (18 + 3) * 15 + 4, 0xFFFFC66D, false);
        int listTop = screenTop + (18 + 3) * 4 + 2;
        int listBottom = listTop + (18 + 3) * 8 - 3;
        lastScrollbar = computeScrollbar(left + 505, listTop, listBottom, editing.actions.size(), stepRows.size(), stepOffset);
        renderScrollbar(graphics, lastScrollbar);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        int left = this.width / 2 - 250;
        int listTop = Math.max(8, this.height / 2 - 188) + (18 + 3) * 4 + 2;
        int listBottom = listTop + (18 + 3) * 8 - 3;
        if (mouseX < left || mouseX > left + 500 || mouseY < listTop || mouseY > listBottom) return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
        if (scrollY < 0) stepOffset = Math.min(maxStepOffset(), stepOffset + 1);
        else if (scrollY > 0) stepOffset = Math.max(0, stepOffset - 1);
        refreshRows();
        return true;
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent context, boolean doubleClick) {
        if (context.button() == 0 && lastScrollbar.hasScroll && lastScrollbar.contains(context.x(), context.y())) {
            if (context.y() >= lastScrollbar.thumbY && context.y() <= lastScrollbar.thumbY + lastScrollbar.thumbH) {
                draggingScrollbar = true;
                scrollbarGrabOffset = (int)Math.max(0, Math.round(context.y()) - lastScrollbar.thumbY);
            } else {
                jumpScrollToMouse((int)Math.round(context.y()), 0);
                refreshRows();
            }
            return true;
        }
        return super.mouseClicked(context, doubleClick);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent context, double dragX, double dragY) {
        if (draggingScrollbar && context.button() == 0 && lastScrollbar.hasScroll) {
            jumpScrollToMouse((int)Math.round(context.y()), scrollbarGrabOffset);
            refreshRows();
            return true;
        }
        return super.mouseDragged(context, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent context) {
        if (context.button() == 0 && draggingScrollbar) {
            draggingScrollbar = false;
            return true;
        }
        return super.mouseReleased(context);
    }

    @Override
    public void onClose() {
        McCompat.setScreen(Minecraft.getInstance(), parent);
    }

    private void jumpScrollToMouse(int mouseY, int grabOffset) {
        if (!lastScrollbar.hasScroll) return;
        int maxScroll = Math.max(1, lastScrollbar.totalRows - lastScrollbar.visibleRows);
        int travel = Math.max(1, lastScrollbar.trackBottom - lastScrollbar.trackTop - lastScrollbar.thumbH);
        int thumbTop = Math.max(lastScrollbar.trackTop, Math.min(lastScrollbar.trackBottom - lastScrollbar.thumbH, mouseY - grabOffset));
        double ratio = (thumbTop - lastScrollbar.trackTop) / (double)travel;
        stepOffset = Math.max(0, Math.min(maxScroll, (int)Math.round(ratio * maxScroll)));
    }

    private ScrollbarMetrics computeScrollbar(int x, int top, int bottom, int totalRows, int visibleRows, int scroll) {
        int trackH = Math.max(1, bottom - top);
        if (totalRows <= visibleRows) return new ScrollbarMetrics(x, top, bottom, top, trackH, false, totalRows, visibleRows);
        double ratio = visibleRows / (double)Math.max(1, totalRows);
        int thumbH = Math.max(12, (int)Math.round(trackH * ratio));
        int maxScroll = Math.max(1, totalRows - visibleRows);
        int travel = Math.max(1, trackH - thumbH);
        int thumbY = top + (int)Math.round((Math.max(0, Math.min(scroll, maxScroll)) / (double)maxScroll) * travel);
        return new ScrollbarMetrics(x, top, bottom, thumbY, thumbH, true, totalRows, visibleRows);
    }

    private void renderScrollbar(GuiGraphicsExtractor graphics, ScrollbarMetrics m) {
        if (!m.hasScroll) return;
        graphics.fill(m.x, m.trackTop, m.x + 3, m.trackBottom, 0xFF353535);
        graphics.fill(m.x, m.thumbY, m.x + 3, m.thumbY + m.thumbH, draggingScrollbar ? 0xFFFFFFFF : 0xFFCFCFCF);
    }

    private final class StepRowWidget extends AbstractWidget {
        private final int row;
        private StepRowWidget(int x, int y, int w, int h, int row) {
            super(x, y, w, h, Component.empty());
            this.row = row;
        }

        @Override
        public boolean mouseClicked(MouseButtonEvent context, boolean doubleClick) {
            if (!active || !visible || context.button() != 0) return false;
            int idx = stepOffset + row;
            if (idx < 0 || idx >= editing.actions.size()) return false;
            selectedStep = idx;
            if (doubleClick) editStep(idx);
            refreshRows();
            return true;
        }

        @Override
        protected void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTicks) {
            int x = getX();
            int y = getY();
            int w = getWidth();
            int h = getHeight();
            boolean hasContent = !getMessage().getString().isBlank();
            boolean selected = stepOffset + row == selectedStep;
            int baseRgb = UiUtilsSettings.get().uiButtonColor & 0xFFFFFF;
            int fill = hasContent ? (selected ? (0x99000000 | baseRgb) : 0x99000000) : 0x44000000;
            int border = selected ? (0xFF000000 | scaleRgb(baseRgb, 1.35F)) : (hasContent ? 0xAA5D6A72 : 0x66505A60);
            graphics.fill(x, y, x + w, y + h, fill);
            graphics.outline(x, y, w, h, border);
            if (hasContent)
                graphics.fill(x, y, x + 3, y + h, selected ? 0xFFFFFFFF : (0xFF000000 | scaleRgb(baseRgb, 1.15F)));
            int textColor = 0xFF000000 | (UiUtilsSettings.get().uiButtonTextColor & 0xFFFFFF);
            int textY = y + (h - Minecraft.getInstance().font.lineHeight) / 2;
            graphics.text(Minecraft.getInstance().font, getMessage().getString(), x + 6, textY, textColor, false);
        }

        @Override
        protected void updateWidgetNarration(NarrationElementOutput narration) {
            defaultButtonNarrationText(narration);
        }
    }

    private static int scaleRgb(int rgb, float factor) {
        int r = (rgb >> 16) & 0xFF;
        int g = (rgb >> 8) & 0xFF;
        int b = rgb & 0xFF;
        r = clamp((int)(r * factor));
        g = clamp((int)(g * factor));
        b = clamp((int)(b * factor));
        return (r << 16) | (g << 8) | b;
    }

    private static int clamp(int value) {
        return Math.max(0, Math.min(255, value));
    }

    private record ScrollbarMetrics(int x, int trackTop, int trackBottom, int thumbY, int thumbH, boolean hasScroll, int totalRows, int visibleRows) {
        private static ScrollbarMetrics none() {
            return new ScrollbarMetrics(0, 0, 0, 0, 0, false, 0, 0);
        }

        private boolean contains(double mx, double my) {
            return mx >= x && mx <= x + 3 && my >= trackTop && my <= trackBottom;
        }
    }

    private static void applyDefaults(UiUtilsMacroAction a) {
        var d = a.getData();
        switch (a.getType()) {
            case SEND_CHAT -> d.putString("message", "");
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
