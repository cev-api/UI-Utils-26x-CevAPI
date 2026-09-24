package com.ui_utils.uiutils;

import com.ui_utils.uiutils.macro.UiUtilsMacroAction;
import com.ui_utils.uiutils.macroeditor.ActionFieldRegistry;
import com.ui_utils.uiutils.macroeditor.FieldDef;
import com.ui_utils.uiutils.ui.UiButton;
import com.ui_utils.uiutils.ui.UiContent;
import com.ui_utils.uiutils.ui.UiInput;
import com.ui_utils.uiutils.ui.UiModernScreen;
import com.ui_utils.uiutils.ui.UiToggle;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.chat.Component;

public final class UiUtilsStepEditScreen extends UiModernScreen {
    private final Screen parent;
    private final UiUtilsMacroAction action;
    private final Runnable onSave;

    private final Map<String, EditBox> fields = new HashMap<>();
    private final Map<String, StringListEditor> stringListEditors = new HashMap<>();
    private final List<FieldDef> defs = new ArrayList<>();
    private final List<FieldDef> visibleDefs = new ArrayList<>();

    public UiUtilsStepEditScreen(Screen parent, UiUtilsMacroAction action, Runnable onSave) {
        super(Component.literal("Edit Step: " + action.getType().name()));
        this.parent = parent;
        this.action = action;
        this.onSave = onSave;
    }

    @Override
    protected int naturalWidth() {
        return 420;
    }

    @Override
    protected void buildContent(UiContent c) {
        applyTransientInputs();
        defs.clear();
        defs.addAll(ActionFieldRegistry.get(action.getType()).fields());
        fields.clear();
        stringListEditors.clear();
        visibleDefs.clear();
        for (FieldDef def : defs) {
            if (isVisible(def)) visibleDefs.add(def);
        }

        for (FieldDef def : visibleDefs) {
            switch (def.type()) {
                case TOGGLE -> {
                    c.row(UiContent.text(def.label()), UiContent.of(new UiToggle("",
                        () -> action.getData().getBooleanOr(def.key(), false),
                        value -> {
                            action.getData().putBoolean(def.key(), value);
                            Minecraft.getInstance().execute(this::rebuildWidgets);
                        }).detail(action.getData().getBooleanOr(def.key(), false)
                            ? "ON" : "OFF"), 2F));
                }
                case ENUM -> {
                    List<String> opts = def.enumOptions();
                    UiButton button = UiButton.of(
                        action.getData().getStringOr(def.key(), opts.isEmpty() ? "" : opts.get(0)),
                        null);
                    button.action(() -> {
                        if (opts.isEmpty()) return;
                        String cur = action.getData().getStringOr(def.key(), opts.get(0));
                        int idx = opts.indexOf(cur);
                        if (idx < 0) idx = 0;
                        String next = opts.get((idx + 1) % opts.size());
                        action.getData().putString(def.key(), next);
                        button.setMessage(Component.literal(next));
                    });
                    c.row(UiContent.text(def.label()), UiContent.of(button, 2F));
                }
                case STRING_LIST -> {
                    List<String> currentItems = readStringList(def.key());
                    UiInput box = new UiInput(this.font, 1,
                        String.join(", ", currentItems), Component.literal("value1, value2"));
                    box.setMaxLength(1024);
                    UiButton add = UiButton.of(def.addLabel(), null);
                    UiButton clear = UiButton.of("Clear", null);
                    StringListEditor editor = new StringListEditor(box, add, clear,
                        currentItems);
                    add.action(() -> {
                        for (String v : parseStringItems(box.getValue())) {
                            if (!editor.items.contains(v)) editor.items.add(v);
                        }
                        box.setValue(String.join(", ", editor.items));
                    });
                    clear.action(() -> {
                        editor.items.clear();
                        box.setValue("");
                    });
                    stringListEditors.put(def.key(), editor);
                    c.row(UiContent.text(def.label()), UiContent.of(box, 4F),
                        UiContent.fixed(add, 40), UiContent.fixed(clear, 38));
                }
                case NUMBER, DECIMAL, TEXT, BLOCK_POS, SLOT -> {
                    UiInput box = new UiInput(this.font, 1, readFieldValue(def),
                        Component.literal(def.label()));
                    box.setMaxLength(1024);
                    fields.put(def.key(), box);
                    c.row(UiContent.text(def.label()), UiContent.of(box, 2F));
                }
            }
        }

        c.footerButton("Cancel", UiButton.Kind.SECONDARY,
            () -> McCompat.setScreen(minecraft, parent));
        c.footerButton("Save", UiButton.Kind.PRIMARY, () -> {
            apply();
            onSave.run();
        });
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

    private static List<String> parseStringItems(String raw) {
        List<String> out = new ArrayList<>();
        if (raw == null || raw.isBlank()) return out;
        for (String part : raw.split("[,\\n]")) {
            String value = part.trim();
            if (!value.isBlank()) out.add(value);
        }
        return out;
    }

    @Override
    public void onClose() {
        McCompat.setScreen(minecraft, parent);
    }

    private static final class StringListEditor {
        private final EditBox input;
        private final UiButton addButton;
        private final UiButton clearButton;
        private final List<String> items;

        private StringListEditor(EditBox input, UiButton addButton, UiButton clearButton, List<String> items) {
            this.input = input;
            this.addButton = addButton;
            this.clearButton = clearButton;
            this.items = items;
        }
    }
}
