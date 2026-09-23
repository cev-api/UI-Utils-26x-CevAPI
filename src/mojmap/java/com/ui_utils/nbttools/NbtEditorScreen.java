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
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.CommonColors;

import com.ui_utils.uiutils.McCompat;

/** Native, large-text SNBT editor with preset integration. */
public final class NbtEditorScreen extends Screen
{
	private final Screen previous;
	private static final Pattern ERROR_POSITION =
		Pattern.compile("(?:position|at)\\s+(\\d+)", Pattern.CASE_INSENSITIVE);
	private static final Pattern RESOURCE_ID =
		Pattern.compile("minecraft:[a-z0-9_./-]+");
	private NbtSyntaxEditor editor;
	private Button applyButton;
	private int validationY;
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
		int editorWidth = Math.min(1050, Math.max(420, width - 260));
		int x = (width - editorWidth) / 2;
		int y = 38;
		int buttonY = height - 61;
		// Keep the status strip immediately above the controls. This makes the
		// raw editor consume every usable pixel instead of leaving a dead gap.
		int editorHeight = Math.max(130, buttonY - y - 34);
		editor = new NbtSyntaxEditor(font, x, y, editorWidth, editorHeight);
		editor.setValue(UiUtilsNbtEditor.getEditorText());
		addRenderableWidget(editor);
		setFocused(editor);

		int editorBottom = y + editorHeight;
		validationY = editorBottom + 10;
		int buttonWidth = 118;
		int gap = 6;
		int total = buttonWidth * 5 + gap * 4;
		int start = (width - total) / 2;
		addRenderableWidget(
			button("Read target", start, buttonY, b -> {
				setText(UiUtilsNbtEditor.readTarget());
				operationStatus = UiUtilsNbtEditor.getLastEditorMessage();
			}));
		addRenderableWidget(
			button("New item", start + (buttonWidth + gap), buttonY, b -> {
				setText(UiUtilsNbtEditor.newItem());
				operationStatus = UiUtilsNbtEditor.getLastEditorMessage();
			}));
		applyButton = button("Apply / Give", start + (buttonWidth + gap) * 2,
			buttonY, b -> apply());
		addRenderableWidget(applyButton);		addRenderableWidget(button("Options", start + (buttonWidth + gap) * 3,
			buttonY, b -> openOptions()));
		addRenderableWidget(button("Cancel", start + (buttonWidth + gap) * 4,
			buttonY, b -> close()));
		requestValidation();
	}

	private void setText(String text)
	{
		editor.setValue(text == null ? "" : text);
		requestValidation();
	}

	private void openOptions()
	{
		McCompat.setScreen(minecraft, new NbtEditorOptionsScreen(this));
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

	private Button button(String label, int x, int y, Button.OnPress action)
	{
		return Button.builder(Component.literal(label), action)
			.bounds(x, y, 118, 20).build();
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
		context.centeredText(font, "NBT Editor", width / 2, 14,
			CommonColors.WHITE);
		context.centeredText(font,
			"Large SNBT editor. Ctrl+V pastes, mouse wheel scrolls, Ctrl+Enter applies.",
			width / 2, 26, 0xAAAAAA);
		super.extractRenderState(context, mouseX, mouseY, partialTicks);
		int statusWidth = Math.min(1050, Math.max(420, width - 260));
		int statusLeft = (width - statusWidth) / 2;
		context.fill(statusLeft, validationY - 3, statusLeft + statusWidth,
			validationY + 21, 0xDD121820);
		context.fill(statusLeft, validationY - 3, statusLeft + statusWidth,
			validationY - 2, 0xFF4B5563);
		context.text(font, "Target: " + UiUtilsNbtEditor.getReadFrom().shortLabel(),
			statusLeft + 4, validationY, 0xFFB8D8FF);
		context.centeredText(font, validationStatus, width / 2, validationY,
			validationColor);
		if(!operationStatus.isEmpty())
			context.centeredText(font, shortStatus(operationStatus), width / 2,
				validationY + 11, 0xFFE2E8F0);
	}

	@Override
	public boolean isPauseScreen()
	{
		return false;
	}
}
