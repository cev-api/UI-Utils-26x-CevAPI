package com.ui_utils.uiutils;

import com.mojang.brigadier.suggestion.Suggestion;
import com.mojang.brigadier.suggestion.Suggestions;
import com.ui_utils.uiutils.ui.UiInput;
import com.ui_utils.uiutils.ui.UiTheme;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/** Panel completion popup. Each field owns its editing/cycling state. */
public class UiUtilsCommandInput extends UiInput {
    private final Font completionFont;
    private String original = "", applied = "", dismissed = "";
    private int originalCursor, appliedCursor, selected, top, popupY, popupRows, rowHeight;
    private List<Suggestion> choices = List.of();
    private boolean cycling, tabCycles;
    private float completionScale = 1F;

    public UiUtilsCommandInput(Font font, int width, Component hint) {
        super(font, width, "", hint);
        completionFont = font;
    }

    @Override public UiInput uiScale(float scale) {
        completionScale = scale > 0 ? scale : 1F;
        return super.uiScale(scale);
    }

    private void refresh() {
        String text = getValue();
        int cursor = getCursorPosition();
        if (!isFocused()) { choices = List.of(); cycling = false; originalCursor = -1; return; }
        if (cycling && text.equals(applied) && cursor == appliedCursor) return;
        if (text.equals(original) && cursor == originalCursor) return;
        original = text; originalCursor = cursor; selected = 0; top = 0; cycling = false;
        dismissed = "";
        Suggestions result = UiUtilsCommandCompletion.suggest(text, cursor, true);
        choices = result == null ? List.of() : result.getList();
    }

    /** Called before history and submission by the panel's chat field. */
    protected boolean completionKey(KeyEvent event) {
        refresh();
        if (choices.isEmpty() || getValue().equals(dismissed)) return false;
        if (event.isEscape()) { dismissed = getValue(); cycling = false; return true; }
        if (event.isCycleFocus()) {
            if (cycling && tabCycles) selected = Math.floorMod(selected + (event.hasShiftDown() ? -1 : 1), choices.size());
            else if (event.hasShiftDown()) selected = choices.size() - 1;
            applyChoice(); tabCycles = true; return true;
        }
        int direction = McCompat.chatHistoryDirection(event);
        if (direction != 0) { selected = Math.floorMod(selected + direction, choices.size()); tabCycles = false; return true; }
        return false;
    }

    private void applyChoice() {
        Suggestion choice = choices.get(selected);
        applied = choice.apply(original);
        appliedCursor = choice.getRange().getStart() + choice.getText().length();
        setValue(applied); setCursorPosition(appliedCursor);
        cycling = true;
    }

    private void positionPopup() {
        rowHeight = Math.max(9, Math.round(12 * completionScale));
        int screenHeight = McCompat.getScreen(Minecraft.getInstance()) == null ? getY() + getHeight()
            : McCompat.getScreen(Minecraft.getInstance()).height;
        int above = getY() - 2, below = screenHeight - getY() - getHeight() - 2;
        boolean upwards = above >= below;
        popupRows = Math.min(6, Math.min(choices.size(), Math.max(0, (upwards ? above : below) / rowHeight)));
        popupY = upwards ? getY() - 2 - popupRows * rowHeight : getY() + getHeight() + 2;
        top = Math.max(0, Math.min(selected, Math.max(top, selected - popupRows + 1)));
    }

    @Override public void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float ticks) {
        super.extractWidgetRenderState(graphics, mouseX, mouseY, ticks);
        refresh();
        if (choices.isEmpty() || getValue().equals(dismissed)) return;
        positionPopup();
        graphics.fill(getX(), popupY, getX() + getWidth(), popupY + popupRows * rowHeight, 0xF0202020);
        for (int row = 0; row < popupRows; row++) {
            int index = top + row;
            int y = popupY + row * rowHeight;
            if (index == selected) graphics.fill(getX(), y, getX() + getWidth(), y + rowHeight, 0xFF405060);
            String label = UiTheme.ellipsize(completionFont, choices.get(index).getText(), Math.max(4, Math.round((getWidth() - 8) / completionScale)));
            UiTheme.textScaled(graphics, completionFont, label, getX() + 4, y + 2, completionScale, 0xFFFFFFFF);
        }
    }

    private boolean overPopup(double x, double y) {
        refresh(); positionPopup();
        return isFocused() && !choices.isEmpty() && !getValue().equals(dismissed)
            && x >= getX() && x < getX() + getWidth() && y >= popupY && y < popupY + popupRows * rowHeight;
    }
    @Override public boolean isMouseOver(double x, double y) { return super.isMouseOver(x, y) || overPopup(x, y); }
    @Override public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() == McCompat.LEFT_BUTTON && overPopup(event.x(), event.y())) {
            selected = top + (int)((event.y() - popupY) / rowHeight); applyChoice(); return true;
        }
        return super.mouseClicked(event, doubleClick);
    }
}
