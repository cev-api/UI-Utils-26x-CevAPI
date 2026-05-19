package com.ui_utils.uiutils.macro;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

public final class UiUtilsMacro {
    public String name = "New Macro";
    public String description = "";
    public boolean loop = false;
    public int loopCount = -1;
    public int keyCode = -1;
    public final List<UiUtilsMacroAction> actions = new ArrayList<>();

    public UiUtilsMacro deepCopy() {
        return fromTag(toTag());
    }

    public CompoundTag toTag() {
        CompoundTag tag = new CompoundTag();
        tag.putString("name", name == null ? "New Macro" : name);
        tag.putString("description", description == null ? "" : description);
        tag.putBoolean("loop", loop);
        tag.putInt("loopCount", loopCount);
        tag.putInt("keyCode", keyCode);
        ListTag list = new ListTag();
        for (UiUtilsMacroAction action : actions) {
            list.add(action.toTag());
        }
        tag.put("actions", list);
        return tag;
    }

    public static UiUtilsMacro fromTag(CompoundTag tag) {
        UiUtilsMacro macro = new UiUtilsMacro();
        macro.name = tag.getStringOr("name", "New Macro");
        macro.description = tag.getStringOr("description", "");
        macro.loop = tag.getBooleanOr("loop", false);
        macro.loopCount = tag.getIntOr("loopCount", -1);
        macro.keyCode = tag.getIntOr("keyCode", -1);
        if (tag.contains("actions")) {
            ListTag list = (ListTag) tag.get("actions");
            for (Tag element : list) {
                if (element instanceof CompoundTag actionTag) {
                    macro.actions.add(UiUtilsMacroAction.fromTag(actionTag));
                }
            }
        }
        return macro;
    }
}
