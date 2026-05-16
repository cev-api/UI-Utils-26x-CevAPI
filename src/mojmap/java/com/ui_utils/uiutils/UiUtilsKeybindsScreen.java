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
	private int page;

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
		int perPage = 10;
		int start = page * perPage;
		int end = Math.min(actions.size(), start + perPage);

		for(int i = start; i < end; i++) {
			UiUtils.KeybindAction action = actions.get(i);
			addRenderableWidget(UiUtils.styledButton(labelFor(action), b -> {
				waitingForAction = action.id();
				rebuildWidgets();
			}, left, y, width, row));
			y += row + gap;
		}

		int half = (width - gap) / 2;
		addRenderableWidget(UiUtils.styledButton("Previous", b -> {
			if(page > 0) {
				page--;
				rebuildWidgets();
			}
		}, left, y, half, row));
		addRenderableWidget(UiUtils.styledButton("Next", b -> {
			if((page + 1) * perPage < actions.size()) {
				page++;
				rebuildWidgets();
			}
		}, left + half + gap, y, half, row));
		y += row + gap + 8;

		addRenderableWidget(UiUtils.styledButton("Back",
			b -> this.minecraft.setScreen(parent), left, y, width, row));
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
