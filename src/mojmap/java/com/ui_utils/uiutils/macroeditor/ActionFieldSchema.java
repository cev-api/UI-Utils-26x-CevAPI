package com.ui_utils.uiutils.macroeditor;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public final class ActionFieldSchema {
    private final List<FieldDef> fields;

    private ActionFieldSchema(List<FieldDef> fields) {
        this.fields = List.copyOf(fields);
    }

    public List<FieldDef> fields() {
        return fields;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private final List<FieldDef> fields = new ArrayList<>();
        private String key;
        private String label;
        private FieldType type;
        private int min = Integer.MIN_VALUE;
        private int max = Integer.MAX_VALUE;
        private double decMin = -Double.MAX_VALUE;
        private double decMax = Double.MAX_VALUE;
        private List<String> enumOpts;
        private String showWhenKey;
        private boolean showWhenInv;
        private String showWhenValue;
        private String addLabel;
        private String[] xyzKeys;
        private boolean xyzDouble;
        private CaptureMode captureMode = CaptureMode.NONE;
        private String[] exclusiveWith;

        private void commit() {
            if (key == null) return;
            fields.add(new FieldDef(key, label, type, min, max, decMin, decMax,
                enumOpts, showWhenKey, showWhenInv, showWhenValue, addLabel, xyzKeys,
                xyzDouble, captureMode, exclusiveWith));
            key = null; label = null; type = null;
            min = Integer.MIN_VALUE; max = Integer.MAX_VALUE;
            decMin = -Double.MAX_VALUE; decMax = Double.MAX_VALUE;
            enumOpts = null; showWhenKey = null; showWhenInv = false; showWhenValue = null;
            addLabel = null; xyzKeys = null; xyzDouble = false; captureMode = CaptureMode.NONE;
            exclusiveWith = null;
        }

        private Builder start(String key, String label, FieldType type) {
            commit();
            this.key = key;
            this.label = label;
            this.type = type;
            return this;
        }

        public Builder toggle(String key, String label) { return start(key, label, FieldType.TOGGLE); }
        public Builder number(String key, String label) { return start(key, label, FieldType.NUMBER); }
        public Builder decimal(String key, String label) { return start(key, label, FieldType.DECIMAL); }
        public Builder text(String key, String label) { return start(key, label, FieldType.TEXT); }
        public Builder stringList(String key, String label) { return start(key, label, FieldType.STRING_LIST); }
        public Builder blockPos(String key, String label) { return start(key, label, FieldType.BLOCK_POS); }
        public Builder slot(String key, String label) { return start(key, label, FieldType.SLOT); }
        public Builder enumField(String key, String label, String... options) { start(key, label, FieldType.ENUM); enumOpts = new ArrayList<>(Arrays.asList(options)); return this; }
        public Builder range(int min, int max) { this.min=min; this.max=max; return this; }
        public Builder decRange(double min, double max) { this.decMin=min; this.decMax=max; return this; }
        public Builder showWhen(String key) { this.showWhenKey=key; this.showWhenInv=false; return this; }
        public Builder hideWhen(String key) { this.showWhenKey=key; this.showWhenInv=true; return this; }
        public Builder showWhenEnum(String key, String value) { this.showWhenKey=key; this.showWhenInv=false; this.showWhenValue=value; return this; }
        public Builder hideWhenEnum(String key, String value) { this.showWhenKey=key; this.showWhenInv=true; this.showWhenValue=value; return this; }
        public Builder addLabel(String label) { this.addLabel = label; return this; }
        public Builder xyzKeys(String... keys) { this.xyzKeys = keys; return this; }
        public Builder xyzDouble(boolean v) { this.xyzDouble = v; return this; }
        public Builder captureBlock() { this.captureMode = CaptureMode.BLOCK_ID; return this; }
        public Builder captureEntity() { this.captureMode = CaptureMode.ENTITY_ID; return this; }
        public Builder captureCatalog() { this.captureMode = CaptureMode.BLOCK_CATALOG; return this; }
        public Builder captureItemSlot() { this.captureMode = CaptureMode.ITEM_SLOT; return this; }
        public Builder exclusiveWith(String... keys) { this.exclusiveWith = keys; return this; }

        public ActionFieldSchema build() { commit(); return new ActionFieldSchema(new ArrayList<>(fields)); }
    }
}
