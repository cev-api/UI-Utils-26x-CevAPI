package com.ui_utils.uiutils.macroeditor;

import java.util.Collections;
import java.util.List;

public record FieldDef(
    String key,
    String label,
    FieldType type,
    int min,
    int max,
    double decMin,
    double decMax,
    List<String> enumOptions,
    String showWhenKey,
    boolean showWhenInverted,
    String showWhenValue,
    String addLabel,
    String[] xyzKeys,
    boolean xyzDouble,
    CaptureMode captureMode,
    String[] mutuallyExclusiveWith
) {
    public FieldDef {
        enumOptions = enumOptions == null ? List.of() : Collections.unmodifiableList(enumOptions);
        xyzKeys = xyzKeys == null ? new String[] {"x", "y", "z"} : xyzKeys.clone();
        captureMode = captureMode == null ? CaptureMode.NONE : captureMode;
        mutuallyExclusiveWith = mutuallyExclusiveWith == null ? new String[0] : mutuallyExclusiveWith.clone();
        addLabel = addLabel == null ? "Add" : addLabel;
    }

    public boolean hasShowWhen() {
        return showWhenKey != null && !showWhenKey.isEmpty();
    }
}
