package com.ui_utils.uiutils;

import com.mojang.blaze3d.platform.InputConstants;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;

public final class UiUtilsKeybindsScreen extends Screen {
	private final Screen parent;
	private String waitingForAction;
	private int offset;

	public UiUtilsKeybindsScreen(Screen parent) {
		super(Component.literal("UI-Utils Keybinds"));
		this.parent = parent;
	}

	@Override
	protected void init() {
		List<UiUtils.KeybindAction> actions = UiUtils.keybindActions();
		int width = 420;
		int left = this.width / 2 - width / 2;
		int row = 20;
		int gap = 4;
		int y = Math.max(12, this.height / 2 - 132);
		int visibleRows = 10;
		int maxOffset = Math.max(0, actions.size() - visibleRows);
		if(offset > maxOffset)
			offset = maxOffset;
		int start = offset;
		int end = Math.min(actions.size(), start + visibleRows);

		for(int i = start; i < end; i++) {
			UiUtils.KeybindAction action = actions.get(i);
			addRenderableWidget(UiUtils.styledButton(labelFor(action), b -> {
				waitingForAction = action.id();
				rebuildWidgets();
			}, left, y, width, row));
			y += row + gap;
		}

		y += 8;

		addRenderableWidget(UiUtils.styledButton("Back",
			b -> this.minecraft.setScreen(parent), left, y, width, row));
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		List<UiUtils.KeybindAction> actions = UiUtils.keybindActions();
		int width = 420;
		int left = this.width / 2 - width / 2;
		int row = 20;
		int gap = 4;
		int top = Math.max(12, this.height / 2 - 132);
		int bottom = top + 10 * (row + gap) - gap;
		if(mouseX < left || mouseX > left + width || mouseY < top || mouseY > bottom)
			return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
		int maxOffset = Math.max(0, actions.size() - 10);
		if(scrollY < 0)
			offset = Math.min(maxOffset, offset + 1);
		else if(scrollY > 0)
			offset = Math.max(0, offset - 1);
		rebuildWidgets();
		return true;
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX,
		int mouseY, float partialTicks) {
		super.extractRenderState(graphics, mouseX, mouseY, partialTicks);
		if(waitingForAction != null)
			graphics.centeredText(this.font,
				Component.literal("Press a key, Backspace clears, Esc cancels"),
				this.width / 2, Math.max(6, this.height / 2 - 154),
				0xFFFFFFFF);
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
		if(keyEvent.key() == org.lwjgl.glfw.GLFW.GLFW_KEY_BACKSPACE
			|| keyEvent.key() == org.lwjgl.glfw.GLFW.GLFW_KEY_DELETE) {
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
		this.minecraft.setScreen(parent);
	}

	private String labelFor(UiUtils.KeybindAction action) {
		if(action.id().equals(waitingForAction))
			return action.label() + ": press key...";
		String key = UiUtils.getKeybind(action.id(), action.defaultKey());
		return action.label() + ": " + formatKey(key);
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
