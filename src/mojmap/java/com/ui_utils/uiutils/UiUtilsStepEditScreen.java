package com.ui_utils.uiutils;

import com.ui_utils.uiutils.macro.UiUtilsMacroAction;
import com.ui_utils.uiutils.macro.UiUtilsMacroLabels;
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
        super(Component.literal("Edit step: " + UiUtilsMacroLabels.actionName(action.getType())));
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
        apply();
        defs.clear();
        defs.addAll(ActionFieldRegistry.get(action.getType()).fields());
        if ((action.getType().name().startsWith("WAIT_") || action.getType().name().endsWith("_SYNC"))
            && defs.stream().noneMatch(def -> def.key().equals("timeoutMs")))
            defs.addAll(com.ui_utils.uiutils.macroeditor.ActionFieldSchema.builder().number("timeoutMs", "Timeout (ms; 0 = 30000)").range(0, 300000).build().fields());
        fields.clear();
        stringListEditors.clear();
        visibleDefs.clear();
        // Materialize displayed defaults before evaluating fields that depend on them.
        for (FieldDef def : defs) {
            if (action.getData().contains(def.key())) continue;
            if (def.type() == com.ui_utils.uiutils.macroeditor.FieldType.ENUM && !def.enumOptions().isEmpty())
                action.getData().putString(def.key(), def.enumOptions().get(0));
            if (def.type() == com.ui_utils.uiutils.macroeditor.FieldType.TOGGLE
                && List.of("waitForArrival", "openInventory", "enabled", "tap", "sneak", "sprint", "centerOnPlayer").contains(def.key()))
                action.getData().putBoolean(def.key(), true);
        }
        for (FieldDef def : defs) {
            if (isVisible(def)) visibleDefs.add(def);
        }

        for (FieldDef def : visibleDefs) {
            switch (def.type()) {
                case TOGGLE -> {
                    c.row(UiContent.text(UiUtilsMacroLabels.sentence(def.label())), UiContent.of(new UiToggle("",
                        () -> action.getData().getBooleanOr(def.key(), false),
                        value -> {
                            action.getData().putBoolean(def.key(), value);
                            Minecraft.getInstance().execute(this::rebuildWidgets);
                        }).detail(action.getData().getBooleanOr(def.key(), false)
                            ? "On" : "Off"), 2F));
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
                        button.setMessage(Component.literal(UiUtilsMacroLabels.sentence(next)));
                        Minecraft.getInstance().execute(this::rebuildWidgets);
                    });
                    button.setMessage(Component.literal(UiUtilsMacroLabels.sentence(button.getMessage().getString())));
                    c.row(UiContent.text(UiUtilsMacroLabels.sentence(def.label())), UiContent.of(button, 2F));
                }
                case STRING_LIST -> {
                    List<String> currentItems = readStringList(def.key());
                    UiInput box = new UiInput(this.font, 1,
                        String.join(", ", currentItems), Component.literal("value1, value2"));
                    box.setMaxLength(1024);
                    // Initial width is a layout placeholder; show the list's beginning
                    // instead of keeping the caret scrolled to its final character.
                    box.moveCursorToStart(false);
                    UiButton add = UiButton.of(UiUtilsMacroLabels.sentence(def.addLabel()), null);
                    UiButton clear = UiButton.of("Clear", null);
                    StringListEditor editor = new StringListEditor(box);
                    add.action(() -> {
                        box.setValue(String.join(", ", parseStringItems(box.getValue()).stream().distinct().toList()));
                        box.moveCursorToStart(false);
                    });
                    clear.action(() -> {
                        box.setValue("");
                    });
                    stringListEditors.put(def.key(), editor);
                    c.row(UiContent.text(UiUtilsMacroLabels.sentence(def.label())), UiContent.of(box, 4F),
                        UiContent.fixed(add, 40), UiContent.fixed(clear, 38));
                    if (def.key().equals("packetNames")) {
                        c.row(UiContent.of(UiButton.of("Browse / search packets", () -> {
                            apply();
                            McCompat.setScreen(minecraft, new UiUtilsMacroPacketPickerScreen(this,
                                action.getData().getStringOr("direction", "C2S"), parseStringItems(box.getValue()),
                                names -> {
                                    box.setValue(String.join(", ", names));
                                    box.moveCursorToStart(false);
                                }));
                        })));
                        c.note("Rules expire after the chosen duration. 0 lasts until Stop or the next macro. Allow removes the selected macro rules.");
                    }
                    if (def.key().equals("targetItems")) c.note("Example: gunpowder or minecraft:gunpowder. Separate items with commas. #slot selects an open-menu slot.");
                }
                case NUMBER, DECIMAL, TEXT, BLOCK_POS, SLOT -> {
                    UiInput box = new UiInput(this.font, 1, readFieldValue(def),
                        Component.literal(UiUtilsMacroLabels.sentence(def.label())));
                    box.setMaxLength(1024);
                    fields.put(def.key(), box);
                    c.row(UiContent.text(UiUtilsMacroLabels.sentence(def.label())), UiContent.of(box, 2F));
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
            com.ui_utils.uiutils.macro.UiUtilsMacroActions.writeSelectors(action.getData(), entry.getKey(), entry.getValue().input.getValue());
        }
    }

    private List<String> readStringList(String key) {
        return com.ui_utils.uiutils.macro.UiUtilsMacroActions.selectors(action.getData(), key);
    }

    private static int parseInt(String s, int fallback) {
        try { return Integer.parseInt(s.trim()); } catch (Exception ignored) { return fallback; }
    }

    private static double parseDouble(String s, double fallback) {
        try { return Double.parseDouble(s.trim()); } catch (Exception ignored) { return fallback; }
    }

    private static List<String> parseStringItems(String raw) {
        return com.ui_utils.uiutils.macro.UiUtilsMacroActions.parseSelectors(raw);
    }

    @Override
    public void onClose() {
        McCompat.setScreen(minecraft, parent);
    }

    private record StringListEditor(EditBox input) {}
}
