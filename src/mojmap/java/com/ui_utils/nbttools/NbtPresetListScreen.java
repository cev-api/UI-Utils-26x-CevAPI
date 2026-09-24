/*
 * Copyright (c) 2014-2026 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package com.ui_utils.nbttools;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;

import com.ui_utils.uiutils.McCompat;
import com.ui_utils.uiutils.ui.UiButton;
import com.ui_utils.uiutils.ui.UiContent;
import com.ui_utils.uiutils.ui.UiListRow;
import com.ui_utils.uiutils.ui.UiModernScreen;

public final class NbtPresetListScreen extends UiModernScreen
{
	private final Screen returnScreen;
	private final Consumer<String> loadCallback;
	private final List<UiListRow> rows = new ArrayList<>();
	private String selected;
	private UiButton loadButton;
	private UiButton deleteButton;

	public NbtPresetListScreen(Screen returnScreen,
		Consumer<String> loadCallback)
	{
		super(Component.literal("NBT Presets"));
		this.returnScreen = returnScreen;
		this.loadCallback = loadCallback;
	}

	@Override
	protected int naturalWidth()
	{
		return 320;
	}

	@Override
	protected void buildContent(UiContent content)
	{
		rows.clear();
		List<String> names = UiUtilsNbtEditor.presetNames().stream().toList();
		if(names.isEmpty())
			content.note("No presets saved yet. Save one from the NBT editor options.");
		for(String name : names)
		{
			UiListRow row = new UiListRow(name, () -> select(name));
			row.doubleRun(this::load);
			row.detail("ui-utils-nbt-presets/" + name + ".snbt");
			rows.add(row);
			content.row(UiContent.of(row));
		}

		loadButton = content.footerButton("Load", UiButton.Kind.PRIMARY,
			this::load);
		deleteButton = content.footerButton("Delete", UiButton.Kind.DANGER,
			this::delete);
		content.footerButton("Done", UiButton.Kind.SECONDARY, this::done);
		refreshSelection();
	}

	private void select(String name)
	{
		selected = name;
		refreshSelection();
	}

	private void refreshSelection()
	{
		for(UiListRow row : rows)
			row.selected(row.label().equals(selected));
		boolean hasSelection = selected != null;
		if(loadButton != null)
			loadButton.active = hasSelection;
		if(deleteButton != null)
			deleteButton.active = hasSelection;
	}

	private void load()
	{
		if(selected == null)
			return;
		String value = UiUtilsNbtEditor.loadPreset(selected);
		if(value != null && loadCallback != null)
			loadCallback.accept(value);
		done();
	}

	private void delete()
	{
		if(selected == null)
			return;
		UiUtilsNbtEditor.deletePreset(selected);
		selected = null;
		McCompat.setScreen(minecraft,
			new NbtPresetListScreen(returnScreen, loadCallback));
	}

	private void done()
	{
		McCompat.setScreen(minecraft, returnScreen);
	}

	@Override
	public boolean keyPressed(KeyEvent event)
	{
		if(event.isEscape())
		{
			done();
			return true;
		}
		if(event.isConfirmation())
		{
			load();
			return true;
		}
		return super.keyPressed(event);
	}

	@Override
	public boolean isPauseScreen()
	{
		return false;
	}

	@Override
	public boolean shouldCloseOnEsc()
	{
		return false;
	}
}
