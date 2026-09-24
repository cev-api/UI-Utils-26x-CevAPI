/*
 * Copyright (c) 2014-2026 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package com.ui_utils.nbttools;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.CommonColors;

import com.ui_utils.uiutils.McCompat;
import com.ui_utils.uiutils.ui.UiButton;
import com.ui_utils.uiutils.ui.UiTheme;

/**
 * Native, large-text SNBT editor.
 * <p>
 * Deliberately laid out as a plain editor surface rather than a UI-Utils card:
 * the title sits above the editor, the editor itself takes every pixel left over,
 * and a thin validation strip plus two compact rows of grey Minecraft buttons sit
 * underneath. Everything is derived from the screen size, so it stays correct at
 * any GUI scale.
 */
public final class NbtEditorScreen extends Screen
{
	private final Screen previous;
	private static final Pattern ERROR_POSITION =
		Pattern.compile("(?:position|at)\\s+(\\d+)", Pattern.CASE_INSENSITIVE);
	private static final Pattern RESOURCE_ID =
		Pattern.compile("minecraft:[a-z0-9_./-]+");

	/** Compact control metrics, in GUI pixels. */
	private static final int MARGIN = 16;
	private static final int BUTTON_HEIGHT = 20;
	private static final int BUTTON_GAP = 6;
	private static final int TITLE_TOP = 12;
	private static final int ROW_GAP = 8;

	private NbtSyntaxEditor editor;
	private UiButton applyButton;
	private UiButton targetButton;
	private UiButton worldEditButton;
	private EditBox presetName;
	private int statusTop;
	private int statusHeight;
	private int statusLeft;
	private int statusWidth;
	private String validationStatus = "Validating...";
	private String operationStatus = "";
	private String lastValidatedText = "\u0000";
	private String pendingValidationText = "";
	private int validationCooldown;
	private int validationColor = 0xFFFF8A8A;
	private boolean applyReadOnly;

	public NbtEditorScreen(Screen previous)
	{
		super(Component.literal("NBT Editor"));
		this.previous = previous;
	}

	@Override
	public void init()
	{
		int editorWidth = Math.min(1050, Math.max(320, width - MARGIN * 2));
		int left = (width - editorWidth) / 2;
		int editorTop = TITLE_TOP + font.lineHeight + ROW_GAP;

		// Laid out from the bottom up: the controls are fixed height and the editor
		// takes whatever is left, so the editor dominates the screen.
		int presetRowY = height - MARGIN - BUTTON_HEIGHT;
		int mainRowY = presetRowY - BUTTON_GAP - BUTTON_HEIGHT;
		// Keep validation and action feedback together in one two-line box below
		// the editor, so neither status can overlap the controls.
		statusHeight = font.lineHeight * 2 + 10;
		statusTop = mainRowY - ROW_GAP - statusHeight;
		int editorHeight = Math.max(60, statusTop - ROW_GAP - editorTop);

		editor = new NbtSyntaxEditor(font, left, editorTop, editorWidth,
			editorHeight);
		editor.setValue(UiUtilsNbtEditor.getEditorText());
		addRenderableWidget(editor);
		setFocused(editor);

		statusLeft = left;
		statusWidth = editorWidth;

		// Main row: target selector, then the editor actions.
		int targetWidth = Math.min(120, Math.max(80, editorWidth / 6));
		int actionCount = 4;
		int available = editorWidth - targetWidth - BUTTON_GAP * actionCount;
		int actionWidth = Math.max(70, available / actionCount);
		int rowWidth = targetWidth + (actionWidth + BUTTON_GAP) * actionCount;
		int rowLeft = (width - rowWidth) / 2;

		targetButton = button("", rowLeft, mainRowY, targetWidth, this::cycleTarget);
		addRenderableWidget(targetButton);
		refreshTargetLabel();

		int x = rowLeft + targetWidth + BUTTON_GAP;
		addRenderableWidget(button("Read target", x, mainRowY, actionWidth, () -> {
			setText(UiUtilsNbtEditor.readTarget());
			operationStatus = UiUtilsNbtEditor.getLastEditorMessage();
		}));
		x += actionWidth + BUTTON_GAP;
		addRenderableWidget(button("New item", x, mainRowY, actionWidth, () -> {
			setText(UiUtilsNbtEditor.newItem());
			operationStatus = UiUtilsNbtEditor.getLastEditorMessage();
		}));
		x += actionWidth + BUTTON_GAP;
		applyButton = button("Apply / Give", x, mainRowY, actionWidth, this::apply);
		addRenderableWidget(applyButton);
		x += actionWidth + BUTTON_GAP;
		addRenderableWidget(button("Cancel", x, mainRowY, actionWidth,
			this::close));

		// Preset row: the name field with the preset actions. The OP world-editing
		// switch lives here too: it used to be hidden behind an options screen, and
		// it is the only setting this editor needs.
		int optionsWidth = Math.min(128, Math.max(96, actionWidth));
		int buttonCount = 3;
		int presetsWidth = Math.max(120, editorWidth - (optionsWidth
			+ BUTTON_GAP) * buttonCount - BUTTON_GAP * buttonCount);
		int presetLeft = (width - (presetsWidth + (optionsWidth + BUTTON_GAP)
			* buttonCount)) / 2;
		presetName = new EditBox(font, presetLeft, presetRowY, presetsWidth,
			BUTTON_HEIGHT, Component.literal("Preset name"));
		presetName.setMaxLength(64);
		presetName.setHint(Component.literal("Preset name"));
		addRenderableWidget(presetName);

		int bx = presetLeft + presetsWidth + BUTTON_GAP;
		addRenderableWidget(button("Save preset", bx, presetRowY, optionsWidth,
			this::savePreset));
		bx += optionsWidth + BUTTON_GAP;
		addRenderableWidget(button("Manage presets", bx, presetRowY, optionsWidth,
			() -> McCompat.setScreen(minecraft,
				new NbtPresetListScreen(this, this::loadPresetText))));
		bx += optionsWidth + BUTTON_GAP;
		worldEditButton = button(worldEditLabel(), bx, presetRowY, optionsWidth,
			() -> {
				// The button label carries the state, so no status text is raised.
				UiUtilsNbtEditor.toggleWorldEdits();
				refreshWorldEditLabel();
			});
		addRenderableWidget(worldEditButton);

		requestValidation();
	}

	/** Themed button, matching the rest of the UI-Utils screens. */
	private UiButton button(String label, int x, int y, int width,
		Runnable action)
	{
		UiButton button = new UiButton(x, y, width, BUTTON_HEIGHT,
			Component.literal(label), action);
		button.style(UiButton.Kind.PRIMARY);
		return button;
	}

	private void setText(String text)
	{
		editor.setValue(text == null ? "" : text);
		requestValidation();
	}

	private static String worldEditLabel()
	{
		return "OP World Edit: "
			+ (UiUtilsNbtEditor.isWorldEditsEnabled() ? "ON" : "OFF");
	}

	private void refreshWorldEditLabel()
	{
		if(worldEditButton != null)
			worldEditButton.setMessage(Component.literal(worldEditLabel()));
	}

	private void cycleTarget()
	{
		UiUtilsNbtEditor.ReadFrom[] targets = UiUtilsNbtEditor.ReadFrom.values();
		int next = (UiUtilsNbtEditor.getReadFrom().ordinal() + 1) % targets.length;
		UiUtilsNbtEditor.setReadFrom(targets[next]);
		UiUtilsNbtEditor.readTarget();
		operationStatus = UiUtilsNbtEditor.getLastEditorMessage();
		refreshTargetLabel();
	}

	private void refreshTargetLabel()
	{
		if(targetButton != null)
			targetButton.setMessage(Component
				.literal("Target: " + UiUtilsNbtEditor.getReadFrom().shortLabel()));
	}

	private void savePreset()
	{
		String name = presetName.getValue() == null ? "" : presetName.getValue().trim();
		if(name.isBlank())
		{
			operationStatus = "Enter a preset name first.";
			return;
		}
		UiUtilsNbtEditor.savePreset(name, UiUtilsNbtEditor.getEditorText());
		operationStatus = UiUtilsNbtEditor.getLastEditorMessage();
		presetName.setValue("");
	}

	private void requestValidation()
	{
		pendingValidationText = editor == null ? "" : editor.getValue();
		validationCooldown = 8;
	}

	@Override
	public void tick()
	{
		super.tick();
		if(editor == null)
			return;
		String text = editor.getValue();
		if(!text.equals(pendingValidationText))
		{
			operationStatus = "";
			pendingValidationText = text;
			validationCooldown = 8;
		}
		if(validationCooldown > 0)
		{
			validationCooldown--;
			return;
		}
		if(text.equals(lastValidatedText))
			return;
		lastValidatedText = text;

		// An open container is a snapshot of the synced menu, so there is
		// nothing the editor could send back to it.
		if(UiUtilsNbtEditor.isReadOnlyTarget())
		{
			validationStatus = "Open container (read-only)";
			validationColor = 0xFFB8D8FF;
			setApplyReadOnly(true);
			return;
		}
		setApplyReadOnly(false);

		String error = UiUtilsNbtEditor.validate(text);
		validationStatus =
			error == null ? "Ready to apply" : errorWithLocation(text, error);
		validationColor = error == null ? 0xFF86EFAC : 0xFFFF8A8A;
		if(applyButton != null)
			applyButton.active = error == null;
	}

	private void setApplyReadOnly(boolean readOnly)
	{
		if(applyReadOnly == readOnly)
			return;
		applyReadOnly = readOnly;
		if(applyButton != null)
		{
			applyButton.active = false;
			applyButton.setMessage(Component.literal(
				readOnly ? "Read-only" : "Apply / Give"));
		}
	}

	private String errorWithLocation(String text, String error)
	{
		String message = error == null ? "Invalid item data." : error;
		Matcher matcher = ERROR_POSITION.matcher(message);
		int position = -1;
		if(matcher.find())
			try
			{
				position = Math.clamp(Integer.parseInt(matcher.group(1)), 0,
					text.length());
			}catch(NumberFormatException ignored)
			{}
		else
		{
			// Registry errors omit offsets, so find the rejected id in the input.
			Matcher id = RESOURCE_ID.matcher(message);
			if(id.find())
				position = text.indexOf(id.group());
		}
		if(position < 0)
			return shortStatus("Invalid: " + message);
		try
		{
			int line = 1;
			int column = 1;
			for(int i = 0; i < position; i++)
				if(text.charAt(i) == '\n')
				{
					line++;
					column = 1;
				}else
					column++;
			return shortStatus("Invalid at line " + line + ", column " + column
				+ ": " + message);
		}catch(RuntimeException ignored)
		{
			return shortStatus("Invalid: " + message);
		}
	}

	private String shortStatus(String message)
	{
		if(message == null)
			return "";
		String singleLine = message.replace('\n', ' ').replace('\r', ' ')
			.replaceAll("\\s+", " ").trim();
		return font.plainSubstrByWidth(singleLine, Math.max(80, width - 40));
	}

	public void loadPresetText(String value)
	{
		loadPresetText(value, null);
	}

	/** Replaces the editor text; a null status falls back to the NBT message. */
	public void loadPresetText(String value, String status)
	{
		UiUtilsNbtEditor.setEditorText(value);
		lastValidatedText = "\u0000";
		operationStatus = status == null ? UiUtilsNbtEditor.getLastEditorMessage()
			: status;
		if(editor != null)
			editor.setValue(value == null ? "" : value);
	}

	private void apply()
	{
		if(!UiUtilsNbtEditor.apply(editor.getValue()))
		{
			operationStatus = UiUtilsNbtEditor.getLastEditorMessage();
			return;
		}

		// World edits are sent as commands, so keep the editor open to show the
		// server's feedback and allow further tweaks.
		if(UiUtilsNbtEditor.isWorldTarget())
		{
			operationStatus = UiUtilsNbtEditor.getLastEditorMessage();
			return;
		}

		close();
	}

	private void close()
	{
		McCompat.setScreen(minecraft, previous);
	}

	@Override
	public boolean keyPressed(KeyEvent event)
	{
		if(event.isEscape())
		{
			close();
			return true;
		}
		if(event.isConfirmation() && event.hasControlDown())
		{
			apply();
			return true;
		}
		return super.keyPressed(event);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor context, int mouseX,
		int mouseY, float partialTicks)
	{
		context.centeredText(font, "NBT Editor", width / 2, TITLE_TOP,
			CommonColors.WHITE);
		super.extractRenderState(context, mouseX, mouseY, partialTicks);

		// A single box below the editor holds validation and the latest operation
		// message, when an action has produced one.
		context.fill(statusLeft, statusTop, statusLeft + statusWidth,
			statusTop + statusHeight, 0xDD121820);
		UiTheme.border(context, statusLeft, statusTop, statusWidth, statusHeight,
			0xFF4B5563);
		int lineOneY = statusTop + 4;
		int lineTwoY = lineOneY + font.lineHeight + 2;
		context.centeredText(font,
			font.plainSubstrByWidth(validationStatus, Math.max(40, statusWidth - 12)),
			statusLeft + statusWidth / 2, lineOneY, validationColor);
		if(!operationStatus.isEmpty()) {
			String message = font.plainSubstrByWidth(shortStatus(operationStatus),
				Math.max(40, statusWidth - 16));
			context.centeredText(font, message, statusLeft + statusWidth / 2,
				lineTwoY, 0xFFE2E8F0);
		}
	}

	@Override
	public boolean isPauseScreen()
	{
		return false;
	}
}
