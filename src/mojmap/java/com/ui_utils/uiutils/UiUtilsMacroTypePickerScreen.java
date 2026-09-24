package com.ui_utils.uiutils;

import com.ui_utils.uiutils.macro.UiUtilsMacroActionType;
import com.ui_utils.uiutils.ui.UiButton;
import com.ui_utils.uiutils.ui.UiContent;
import com.ui_utils.uiutils.ui.UiModernScreen;
import java.util.List;
import java.util.Locale;
import net.minecraft.network.chat.Component;

public final class UiUtilsMacroTypePickerScreen extends UiModernScreen {
    public enum Mode { ACTION, CONDITION }

    private final UiUtilsMacrosScreen parent;
    private final Mode mode;

    public UiUtilsMacroTypePickerScreen(UiUtilsMacrosScreen parent, Mode mode) {
        super(Component.literal(mode == Mode.ACTION ? "Add Action" : "Add Condition"));
        this.parent = parent;
        this.mode = mode;
    }

    @Override
    protected int naturalWidth() {
        return 420;
    }

    @Override
    protected void buildContent(UiContent c) {
        for (PickerSection section : mode == Mode.ACTION ? actionSections() : conditionSections()) {
            c.section(section.title());
            List<UiUtilsMacroActionType> types = section.types();
            for (int i = 0; i < types.size(); i += 2) {
                if (i + 1 < types.size())
                    c.row(UiContent.of(pick(types.get(i))),
                        UiContent.of(pick(types.get(i + 1))));
                else
                    c.row(UiContent.of(pick(types.get(i))));
            }
        }
        c.footerButton("Cancel", UiButton.Kind.SECONDARY,
            () -> McCompat.setScreen(this.minecraft, parent));
    }

    private UiButton pick(UiUtilsMacroActionType type) {
        return UiButton.of(label(type), () -> parent.addStepFromPicker(type));
    }

    private static String label(UiUtilsMacroActionType t) {
        String s = t.name().replace('_', ' ').toLowerCase(Locale.ROOT);
        String[] p = s.split(" ");
        StringBuilder out = new StringBuilder();
        for (String part : p) {
            if (part.isEmpty()) continue;
            if (!out.isEmpty()) out.append(' ');
            out.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        }
        return out.toString();
    }

    private static List<PickerSection> actionSections() {
        return List.of(
            new PickerSection("Flow", List.of(
                UiUtilsMacroActionType.SEND_CHAT, UiUtilsMacroActionType.SEND_COMMAND, UiUtilsMacroActionType.DELAY,
                UiUtilsMacroActionType.REPEAT, UiUtilsMacroActionType.STOP_MACRO, UiUtilsMacroActionType.SEND_TOGGLE,
                UiUtilsMacroActionType.DELAY_PACKETS, UiUtilsMacroActionType.SAVE_GUI, UiUtilsMacroActionType.RESTORE_GUI
            )),
            new PickerSection("Movement", List.of(
                UiUtilsMacroActionType.ROTATE, UiUtilsMacroActionType.LOOK_AT_BLOCK, UiUtilsMacroActionType.SNEAK,
                UiUtilsMacroActionType.JUMP, UiUtilsMacroActionType.SPRINT, UiUtilsMacroActionType.MOVE
            )),
            new PickerSection("Inventory", List.of(
                UiUtilsMacroActionType.ITEM, UiUtilsMacroActionType.USE_ITEM, UiUtilsMacroActionType.INVENTORY,
                UiUtilsMacroActionType.SELECT_SLOT, UiUtilsMacroActionType.XCARRY, UiUtilsMacroActionType.DROP,
                UiUtilsMacroActionType.SWAP_SLOTS, UiUtilsMacroActionType.OPEN_CONTAINER, UiUtilsMacroActionType.STORE_ITEM,
                UiUtilsMacroActionType.INVENTORY_AUDIT, UiUtilsMacroActionType.CRAFT, UiUtilsMacroActionType.CLICK
            )),
            new PickerSection("Network", List.of(
                UiUtilsMacroActionType.PACKET, UiUtilsMacroActionType.PAYLOAD, UiUtilsMacroActionType.CLOSE_GUI,
                UiUtilsMacroActionType.DESYNC, UiUtilsMacroActionType.NBT_BOOK, UiUtilsMacroActionType.DISCONNECT
            )),
            new PickerSection("Automation", List.of(
                UiUtilsMacroActionType.PAY, UiUtilsMacroActionType.MINE, UiUtilsMacroActionType.TOGGLE_MODULE,
                UiUtilsMacroActionType.SEND_PACKET
            ))
        );
    }

    private static List<PickerSection> conditionSections() {
        return List.of(
            new PickerSection("Player", List.of(
                UiUtilsMacroActionType.WAIT_HEALTH, UiUtilsMacroActionType.WAIT_COOLDOWN,
                UiUtilsMacroActionType.WAIT_ITEM, UiUtilsMacroActionType.WAIT_SLOT_CHANGE
            )),
            new PickerSection("World", List.of(
                UiUtilsMacroActionType.WAIT_POS, UiUtilsMacroActionType.WAIT_BLOCK,
                UiUtilsMacroActionType.WAIT_ENTITY, UiUtilsMacroActionType.WAIT_SOUND
            )),
            new PickerSection("Events", List.of(
                UiUtilsMacroActionType.WAIT_GUI, UiUtilsMacroActionType.WAIT_CHAT, UiUtilsMacroActionType.WAIT_LAN_STEP
            )),
            new PickerSection("Sync", List.of(
                UiUtilsMacroActionType.WAIT_PACKET, UiUtilsMacroActionType.TICK_SYNC,
                UiUtilsMacroActionType.REVISION_SYNC, UiUtilsMacroActionType.SERVER_TICK_SYNC
            ))
        );
    }

    private record PickerSection(String title, List<UiUtilsMacroActionType> types) {}
}
