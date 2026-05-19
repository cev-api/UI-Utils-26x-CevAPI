package com.ui_utils.uiutils.macro;

import net.minecraft.nbt.CompoundTag;

public final class UiUtilsMacroAction {
    private boolean enabled = true;
    private UiUtilsMacroActionType type = UiUtilsMacroActionType.DELAY;
    private CompoundTag data = new CompoundTag();

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public UiUtilsMacroActionType getType() { return type; }
    public void setType(UiUtilsMacroActionType type) { this.type = type == null ? UiUtilsMacroActionType.DELAY : type; }
    public CompoundTag getData() { return data; }
    public void setData(CompoundTag data) { this.data = data == null ? new CompoundTag() : data.copy(); }

    public CompoundTag toTag() {
        CompoundTag tag = data.copy();
        tag.putString("type", type.name());
        tag.putBoolean("enabled", enabled);
        return tag;
    }

    public static UiUtilsMacroAction fromTag(CompoundTag tag) {
        UiUtilsMacroAction action = new UiUtilsMacroAction();
        action.setType(UiUtilsMacroActionType.byName(tag.getStringOr("type", "DELAY")));
        action.setEnabled(tag.getBooleanOr("enabled", true));
        action.setData(tag);
        return action;
    }
}
