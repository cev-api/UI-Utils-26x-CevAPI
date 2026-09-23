/*
 * Copyright (c) 2014-2026 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package com.ui_utils.nbttools;

import java.util.EnumMap;
import java.util.Map;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.CommonColors;

import com.ui_utils.uiutils.McCompat;

/**
 * Secondary NBT editor options. The target toggles and the world-editing switch
 * are also exposed in the main UI-Utils panel; everything else the editor needs
 * lives here so the editor itself stays a plain text surface.
 */
public final class NbtEditorOptionsScreen extends Screen
{
	private static final int BTN = 100;
	private static final int GAP = 6;
	private final NbtEditorScreen editorScreen;
	private final Map<UiUtilsNbtEditor.ReadFrom, Button> targetButtons =
		new EnumMap<>(UiUtilsNbtEditor.ReadFrom.class);
	private Button worldEditButton;
	private EditBox presetName;
	private String status = "";

	public NbtEditorOptionsScreen(NbtEditorScreen editorScreen)
	{
		super(Component.literal("NBT Editor Options"));
		this.editorScreen = editorScreen;
	}

	@Override
	public void init()
	{
		int centerX = width / 2;
		int columnWidth = Math.min(420, width - 40);
		int left = (width - columnWidth) / 2;
		int y = 52;

		int third = (columnWidth - 12) / 3;
		int toggleX = left;
		for(UiUtilsNbtEditor.ReadFrom target : UiUtilsNbtEditor.ReadFrom.values())
		{
			Button button = Button.builder(targetLabel(target),
				b -> selectTarget(target)).bounds(toggleX, y, third, 20).build();
			targetButtons.put(target, button);
			addRenderableWidget(button);
			toggleX += third + 6;
		}
		y += 30;

		worldEditButton = Button.builder(worldEditLabel(), b -> {
			UiUtilsNbtEditor.toggleWorldEdits();
			updateLabels();
		}).bounds(left, y, columnWidth, 20).build();
		addRenderableWidget(worldEditButton);
		y += 30;

		int presetWidth = Math.max(60, columnWidth - 2 * (BTN + GAP));
		int presetButton = Math.max(60,
			(columnWidth - presetWidth - 2 * GAP) / 2);
		presetName = new EditBox(font, left, y, presetWidth, 20,
			Component.literal("Preset name"));
		presetName.setMaxLength(64);
		addRenderableWidget(presetName);
		addRenderableWidget(Button.builder(Component.literal("Save preset"),
			b -> savePreset()).bounds(left + presetWidth + GAP, y, presetButton, 20)
			.build());
		addRenderableWidget(Button.builder(Component.literal("Manage presets"),
			b -> McCompat.setScreen(minecraft, new NbtPresetListScreen(this,
				value -> loadPreset(value))))
			.bounds(left + presetWidth + GAP + presetButton + GAP, y,
				presetButton, 20)
			.build());
		y += 30;

		addRenderableWidget(Button.builder(Component.literal("Format editor text"),
			b -> reformat()).bounds(left, y, (columnWidth - 6) / 2, 20).build());
		addRenderableWidget(Button.builder(Component.literal("Minify editor text"),
			b -> minify()).bounds(left + (columnWidth - 6) / 2 + 6, y,
			columnWidth - (columnWidth - 6) / 2 - 6, 20).build());
		y += 34;

		addRenderableWidget(Button.builder(Component.literal("Back"),
			b -> McCompat.setScreen(minecraft, editorScreen))
			.bounds(centerX - 60, height - 40, 120, 20).build());
		updateLabels();
	}

	private void selectTarget(UiUtilsNbtEditor.ReadFrom target)
	{
		UiUtilsNbtEditor.setReadFrom(target);
		UiUtilsNbtEditor.readTarget();
		updateLabels();
		status = UiUtilsNbtEditor.getLastEditorMessage();
	}

	private void savePreset()
	{
		if(UiUtilsNbtEditor.savePreset(presetName.getValue(),
			UiUtilsNbtEditor.getEditorText()))
		{
			presetName.setValue("");
			status = UiUtilsNbtEditor.getLastEditorMessage();
		}else
			status = UiUtilsNbtEditor.getLastEditorMessage();
	}

	private void loadPreset(String value)
	{
		editorScreen.loadPresetText(value);
		status = UiUtilsNbtEditor.getLastEditorMessage();
	}

	private void reformat()
	{
		String formatted = UiUtilsNbtEditor.formatNbt(
			UiUtilsNbtEditor.getEditorText());
		editorScreen.loadPresetText(formatted, "Reformatted editor text.");
		status = "Reformatted editor text.";
	}

	private void minify()
	{
		String minified = UiUtilsNbtEditor.minifyNbt(
			UiUtilsNbtEditor.getEditorText());
		editorScreen.loadPresetText(minified, "Minified editor text.");
		status = "Minified editor text.";
	}

	private void updateLabels()
	{
		for(Map.Entry<UiUtilsNbtEditor.ReadFrom, Button> entry : targetButtons
			.entrySet())
			entry.getValue().setMessage(targetLabel(entry.getKey()));
		if(worldEditButton != null)
			worldEditButton.setMessage(worldEditLabel());
	}

	private static Component targetLabel(UiUtilsNbtEditor.ReadFrom target)
	{
		boolean active = UiUtilsNbtEditor.getReadFrom() == target;
		return Component.literal((active ? "> " : "") + target);
	}

	private static Component worldEditLabel()
	{
		return Component.literal("World editing (OP): "
			+ (UiUtilsNbtEditor.isWorldEditsEnabled() ? "True" : "False"));
	}

	@Override
	public boolean keyPressed(KeyEvent event)
	{
		if(event.isEscape())
		{
			McCompat.setScreen(minecraft, editorScreen);
			return true;
		}
		return super.keyPressed(event);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX,
		int mouseY, float partialTicks)
	{
		super.extractRenderState(graphics, mouseX, mouseY, partialTicks);
		graphics.centeredText(font, title, width / 2, 14, CommonColors.WHITE);
		graphics.centeredText(font,
			"Target, OP world editing, presets and formatting",
			width / 2, 26, 0xFFB8D8FF);
		graphics.text(font, "Target", width / 2 - Math.min(420, width - 40) / 2,
			40, 0xFF9AA4B2);
		if(status != null && !status.isBlank())
			graphics.centeredText(font, status, width / 2, height - 54,
				0xFFE2E8F0);
	}

	@Override
	public boolean isPauseScreen()
	{
		return false;
	}
}
