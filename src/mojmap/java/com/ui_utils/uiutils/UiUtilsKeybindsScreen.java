package com.ui_utils.uiutils;

import com.mojang.blaze3d.platform.InputConstants;
import com.ui_utils.uiutils.ui.UiButton;
import com.ui_utils.uiutils.ui.UiContent;
import com.ui_utils.uiutils.ui.UiListRow;
import com.ui_utils.uiutils.ui.UiModernScreen;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;

/**
 * Keybind list. Rows are ordinary list widgets, so the shared canvas owns
 * scrolling, clipping and hit testing for them.
 */
public final class UiUtilsKeybindsScreen extends UiModernScreen {
	private final Screen parent;
	private String waitingForAction;

	public UiUtilsKeybindsScreen(Screen parent) {
		super(Component.literal("UI-Utils Keybinds"));
		this.parent = parent;
	}

	@Override
	protected int naturalWidth() {
		return 300;
	}

	@Override
	protected void buildContent(UiContent c) {
		c.note("Click a row and press a key. Backspace clears it, Esc cancels.");
		for(UiUtils.KeybindAction action : UiUtils.keybindActions()) {
			boolean waiting = action.id().equals(waitingForAction);
			UiListRow row = new UiListRow(action.label(), () -> {
				waitingForAction = action.id();
				Minecraft.getInstance().execute(this::rebuildWidgets);
			}).detail(waiting ? "press a key..."
				: formatKey(UiUtils.getKeybind(action.id(), action.defaultKey())));
			row.selected(waiting);
			c.row(UiContent.of(row));
		}
		setStatus(waitingForAction == null ? "" : "Waiting for a key");
		c.footerButton("Back", UiButton.Kind.SECONDARY, this::onClose);
	}

	@Override
	public boolean keyPressed(KeyEvent keyEvent) {
		if(waitingForAction == null)
			return super.keyPressed(keyEvent);
		if(keyEvent.isEscape()) {
			waitingForAction = null;
			rebuildWidgets();
			return true;
		}
		if(McCompat.isClearKey(keyEvent)) {
			UiUtils.setKeybind(waitingForAction, "");
		}else {
			InputConstants.Key key = InputConstants.getKey(keyEvent);
			UiUtils.setKeybind(waitingForAction, key.getName());
		}
		waitingForAction = null;
		rebuildWidgets();
		return true;
	}

	@Override
	public void onClose() {
		McCompat.setScreen(this.minecraft, parent);
	}

	private String formatKey(String key) {
		if(key == null || key.isBlank())
			return "UNBOUND";
		int dot = key.lastIndexOf('.');
		String part = dot >= 0 && dot + 1 < key.length()
			? key.substring(dot + 1) : key;
		return part.toUpperCase(Locale.ROOT);
	}
}
