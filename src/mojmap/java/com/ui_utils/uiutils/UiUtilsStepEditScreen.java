package com.ui_utils.uiutils;

import com.ui_utils.uiutils.macro.UiUtilsMacroAction;
import com.ui_utils.uiutils.macroeditor.ActionFieldRegistry;
import com.ui_utils.uiutils.macroeditor.FieldDef;
import com.ui_utils.uiutils.macroeditor.FieldType;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.chat.Component;

public final class UiUtilsStepEditScreen extends Screen {
    private final Screen parent;
    private final UiUtilsMacroAction action;
    private final Runnable onSave;

    private final Map<String, EditBox> fields = new HashMap<>();
    private final Map<String, StringListEditor> stringListEditors = new HashMap<>();
    private final List<FieldDef> defs = new ArrayList<>();
    private final List<FieldDef> visibleDefs = new ArrayList<>();
    private int fieldOffset = 0;
    private int maxOffset = 0;
    private boolean draggingScrollbar;
    private int scrollbarGrabOffset;
    private ScrollbarMetrics lastScrollbar = ScrollbarMetrics.none();

    public UiUtilsStepEditScreen(Screen parent, UiUtilsMacroAction action, Runnable onSave) {
        super(Component.literal("Edit Step"));
        this.parent = parent;
        this.action = action;
        this.onSave = onSave;
    }

    @Override
    protected void init() {
        defs.clear();
        defs.addAll(ActionFieldRegistry.get(action.getType()).fields());
        rebuildForm(false);
    }

    private void rebuildForm(boolean preserveInputs) {
        if (preserveInputs) applyTransientInputs();
        clearWidgets();
        fields.clear();
        stringListEditors.clear();
        visibleDefs.clear();

        for (FieldDef def : defs) {
            if (isVisible(def)) visibleDefs.add(def);
        }

        int width = formWidth();
        int left = this.width / 2 - width / 2;
        int top = Math.max(46, this.height / 2 - 150);
        int row = 20;
        int gap = 4;
        int labelWidth = Math.min(138, width / 3);
        int controlX = left + labelWidth + 8;
        int controlWidth = width - labelWidth - 8;
        int viewport = visibleRowCapacity(top, row, gap);

        maxOffset = Math.max(0, visibleDefs.size() - viewport);
        if (fieldOffset > maxOffset) fieldOffset = maxOffset;

        int rendered = 0;
        for (int i = fieldOffset; i < visibleDefs.size() && rendered < viewport; i++) {
            FieldDef def = visibleDefs.get(i);
            int y = top + rendered * (row + gap);
            switch (def.type()) {
                case TOGGLE -> {
                    boolean v = action.getData().getBooleanOr(def.key(), false);
                    addRenderableWidget(UiUtils.styledButton(v ? "ON" : "OFF", btn -> {
                        boolean nv = !action.getData().getBooleanOr(def.key(), false);
                        action.getData().putBoolean(def.key(), nv);
                        rebuildForm(true);
                    }, controlX, y, controlWidth, row));
                }
                case ENUM -> {
                    List<String> opts = def.enumOptions();
                    String current = action.getData().getStringOr(def.key(), opts.isEmpty() ? "" : opts.get(0));
                    addRenderableWidget(UiUtils.styledButton(current, btn -> {
                        if (opts.isEmpty()) return;
                        String cur = action.getData().getStringOr(def.key(), opts.get(0));
                        int idx = opts.indexOf(cur);
                        if (idx < 0) idx = 0;
                        action.getData().putString(def.key(), opts.get((idx + 1) % opts.size()));
                        rebuildForm(true);
                    }, controlX, y, controlWidth, row));
                }
                case STRING_LIST -> {
                    int itemWidth = controlWidth - 86;
                    EditBox box = new EditBox(this.font, controlX, y, itemWidth, row, Component.literal(def.label()));
                    box.setMaxLength(1024);
                    box.setHint(Component.literal("value1, value2"));
                    List<String> currentItems = readStringList(def.key());
                    box.setValue(String.join(", ", currentItems));
                    addRenderableWidget(box);
                    UiUtilsColoredButton addBtn = addRenderableWidget(UiUtils.styledButton(def.addLabel(), btn -> {
                        StringListEditor editor = stringListEditors.get(def.key());
                        for (String v : parseStringItems(box.getValue())) {
                            if (!editor.items.contains(v)) editor.items.add(v);
                        }
                        box.setValue(String.join(", ", editor.items));
                    }, controlX + itemWidth + 4, y, 40, row));
                    UiUtilsColoredButton clearBtn = addRenderableWidget(UiUtils.styledButton("Clear", btn -> {
                        StringListEditor editor = stringListEditors.get(def.key());
                        editor.items.clear();
                        box.setValue("");
                    }, controlX + itemWidth + 48, y, 38, row));
                    stringListEditors.put(def.key(), new StringListEditor(box, addBtn, clearBtn, currentItems));
                }
                case NUMBER, DECIMAL, TEXT, BLOCK_POS, SLOT -> {
                    EditBox box = new EditBox(this.font, controlX, y, controlWidth, row, Component.literal(def.label()));
                    box.setMaxLength(1024);
                    box.setHint(Component.literal(def.label()));
                    box.setValue(readFieldValue(def));
                    addRenderableWidget(box);
                    fields.put(def.key(), box);
                }
            }
            rendered++;
        }

        int contentRows = Math.max(1, Math.min(viewport, visibleDefs.size()));
        int by = top + contentRows * (row + gap) + 14;
        addRenderableWidget(UiUtils.styledButton("Cancel", b -> McCompat.setScreen(minecraft, parent), left, by, (width - gap) / 2, row));
        addRenderableWidget(UiUtils.styledButton("Save", b -> {
            apply();
            onSave.run();
        }, left + (width + gap) / 2, by, (width - gap) / 2, row));
    }

    private boolean isVisible(FieldDef def) {
        if (!def.hasShowWhen()) return true;
        String key = def.showWhenKey();
        boolean base;
        if (def.showWhenValue() != null && !def.showWhenValue().isBlank()) {
            base = def.showWhenValue().equalsIgnoreCase(action.getData().getStringOr(key, ""));
        } else {
            base = action.getData().getBooleanOr(key, false);
        }
        return def.showWhenInverted() ? !base : base;
    }

    private String readFieldValue(FieldDef def) {
        return switch (def.type()) {
            case NUMBER, SLOT -> String.valueOf(action.getData().getIntOr(def.key(), def.min() == Integer.MIN_VALUE ? 0 : def.min()));
            case DECIMAL -> String.valueOf(action.getData().getDoubleOr(def.key(), 0.0));
            case STRING_LIST -> String.join(", ", readStringList(def.key()));
            case BLOCK_POS -> {
                if (action.getData().contains("x") || action.getData().contains("y") || action.getData().contains("z")) {
                    yield action.getData().getDoubleOr("x", 0.0) + "," + action.getData().getDoubleOr("y", 0.0) + "," + action.getData().getDoubleOr("z", 0.0);
                }
                yield action.getData().getStringOr(def.key(), "");
            }
            default -> action.getData().getStringOr(def.key(), "");
        };
    }

    private void applyTransientInputs() {
        for (FieldDef def : visibleDefs) {
            EditBox box = fields.get(def.key());
            if (box == null) continue;
            String v = box.getValue().trim();
            switch (def.type()) {
                case NUMBER, SLOT -> action.getData().putInt(def.key(), parseInt(v, action.getData().getIntOr(def.key(), 0)));
                case DECIMAL -> action.getData().putDouble(def.key(), parseDouble(v, action.getData().getDoubleOr(def.key(), 0.0)));
                case BLOCK_POS -> {
                    String[] s = v.split(",");
                    if (s.length == 3) {
                        action.getData().putDouble("x", parseDouble(s[0], 0.0));
                        action.getData().putDouble("y", parseDouble(s[1], 0.0));
                        action.getData().putDouble("z", parseDouble(s[2], 0.0));
                    } else {
                        action.getData().putString(def.key(), v);
                    }
                }
                default -> action.getData().putString(def.key(), v);
            }
        }
    }

    private void apply() {
        applyTransientInputs();
        for (var entry : stringListEditors.entrySet()) {
            ListTag list = new ListTag();
            List<String> values = new ArrayList<>(entry.getValue().items);
            for (String typed : parseStringItems(entry.getValue().input.getValue())) {
                if (!values.contains(typed)) values.add(typed);
            }
            for (String s : values) {
                if (!s.isBlank()) list.add(StringTag.valueOf(s.trim()));
            }
            action.getData().put(entry.getKey(), list);
        }
    }

    private List<String> readStringList(String key) {
        List<String> out = new ArrayList<>();
        if (action.getData().contains(key) && action.getData().get(key) instanceof ListTag list) {
            for (int i = 0; i < list.size(); i++) {
                String v = list.get(i).asString().orElse("");
                if (!v.isBlank()) out.add(v);
            }
        }
        if (out.isEmpty()) {
            String fallback = action.getData().getStringOr(key, "");
            if (!fallback.isBlank()) {
                for (String part : fallback.split(",")) {
                    String p = part.trim();
                    if (!p.isBlank()) out.add(p);
                }
            }
        }
        return out;
    }

    private static int parseInt(String s, int fallback) {
        try { return Integer.parseInt(s.trim()); } catch (Exception ignored) { return fallback; }
    }

    private static double parseDouble(String s, double fallback) {
        try { return Double.parseDouble(s.trim()); } catch (Exception ignored) { return fallback; }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTicks) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTicks);
        int formWidth = formWidth();
        int left = this.width / 2 - formWidth / 2;
        int top = Math.max(46, this.height / 2 - 150) - this.font.lineHeight - 10;
        graphics.text(this.font, "Edit: " + action.getType().name(), left, top, 0xFFE6EEF7, false);
        int formLeft = this.width / 2 - formWidth / 2;
        int formTop = Math.max(46, this.height / 2 - 150);
        int row = 20;
        int gap = 4;
        int viewport = visibleRowCapacity(formTop, row, gap);
        int rendered = 0;
        for (int i = fieldOffset; i < visibleDefs.size() && rendered < viewport; i++) {
            FieldDef def = visibleDefs.get(i);
            int y = formTop + rendered * (row + gap) + (row - this.font.lineHeight) / 2;
            graphics.text(this.font, def.label(), formLeft, y, 0xFFE6EEF7, false);
            rendered++;
        }
        int listBottom = formTop + viewport * (row + gap) - gap;
        lastScrollbar = computeScrollbar(formLeft + formWidth + 6, formTop, listBottom, visibleDefs.size(), viewport, fieldOffset);
        renderScrollbar(graphics, lastScrollbar);
    }

    @Override
    public void onClose() {
        McCompat.setScreen(minecraft, parent);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        int formWidth = formWidth();
        int formLeft = this.width / 2 - formWidth / 2;
        int formTop = Math.max(46, this.height / 2 - 150);
        int viewport = visibleRowCapacity(formTop, 20, 4);
        int listBottom = formTop + viewport * (20 + 4) - 4;
        if (mouseX < formLeft || mouseX > formLeft + formWidth || mouseY < formTop || mouseY > listBottom) return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
        if (scrollY < 0) fieldOffset = Math.min(maxOffset, fieldOffset + 1);
        else if (scrollY > 0) fieldOffset = Math.max(0, fieldOffset - 1);
        rebuildForm(true);
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
                rebuildForm(true);
            }
            return true;
        }
        return super.mouseClicked(context, doubleClick);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent context, double dragX, double dragY) {
        if (draggingScrollbar && context.button() == 0 && lastScrollbar.hasScroll) {
            jumpScrollToMouse((int)Math.round(context.y()), scrollbarGrabOffset);
            rebuildForm(true);
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

    private void jumpScrollToMouse(int mouseY, int grabOffset) {
        if (!lastScrollbar.hasScroll) return;
        int maxScroll = Math.max(1, lastScrollbar.totalRows - lastScrollbar.visibleRows);
        int travel = Math.max(1, lastScrollbar.trackBottom - lastScrollbar.trackTop - lastScrollbar.thumbH);
        int thumbTop = Math.max(lastScrollbar.trackTop, Math.min(lastScrollbar.trackBottom - lastScrollbar.thumbH, mouseY - grabOffset));
        double ratio = (thumbTop - lastScrollbar.trackTop) / (double)travel;
        fieldOffset = Math.max(0, Math.min(maxScroll, (int)Math.round(ratio * maxScroll)));
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

    private int visibleRowCapacity(int top, int row, int gap) {
        return Math.max(4, Math.min(10, (this.height - top - 88) / (row + gap)));
    }

    private int formWidth() {
        int preferred = 420;
        for (FieldDef def : visibleDefs) {
            if (def.type() == FieldType.STRING_LIST || def.type() == FieldType.BLOCK_POS) {
                preferred = 560;
                break;
            }
            if (def.type() == FieldType.TEXT) preferred = Math.max(preferred, 500);
        }
        return Math.min(preferred, this.width - 56);
    }

    private static List<String> parseStringItems(String raw) {
        List<String> out = new ArrayList<>();
        if (raw == null || raw.isBlank()) return out;
        for (String part : raw.split("[,\\n]")) {
            String value = part.trim();
            if (!value.isBlank()) out.add(value);
        }
        return out;
    }

    private static final class StringListEditor {
        private final EditBox input;
        private final UiUtilsColoredButton addButton;
        private final UiUtilsColoredButton clearButton;
        private final List<String> items;

        private StringListEditor(EditBox input, UiUtilsColoredButton addButton, UiUtilsColoredButton clearButton, List<String> items) {
            this.input = input;
            this.addButton = addButton;
            this.clearButton = clearButton;
            this.items = items;
        }
    }

    private record ScrollbarMetrics(int x, int trackTop, int trackBottom, int thumbY, int thumbH, boolean hasScroll, int totalRows, int visibleRows) {
        private static ScrollbarMetrics none() {
            return new ScrollbarMetrics(0, 0, 0, 0, 0, false, 0, 0);
        }

        private boolean contains(double mx, double my) {
            return mx >= x && mx <= x + 3 && my >= trackTop && my <= trackBottom;
        }
    }
}
