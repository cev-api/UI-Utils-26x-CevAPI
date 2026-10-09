/*
 * Copyright (c) 2014-2026 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package com.ui_utils.mixin.ui_utils;

import java.util.ArrayList;
import java.util.List;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.util.Mth;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.BookViewScreen;
import net.minecraft.network.chat.Component;

import com.ui_utils.uiutils.UiUtils;
import com.ui_utils.uiutils.UiUtilsState;
import com.ui_utils.uiutils.UiUtilsSettings;
import com.ui_utils.uiutils.UiUtilsMainPanelDrag;
import com.ui_utils.uiutils.McCompat;

@Mixin(BookViewScreen.class)
public abstract class UiUtilsBookViewScreenMixin extends Screen
{
	@Unique
	private EditBox uiUtilsChatField;
	@Unique
	private final List<AbstractWidget> uiUtilsMainWidgets = new ArrayList<>();
	
	private UiUtilsBookViewScreenMixin(Component title)
	{
		super(title);
	}
	
	@Inject(at = @At("TAIL"), method = "init()V")
	private void onInit(CallbackInfo ci)
	{
		if(!UiUtilsState.isUiEnabled())
			return;
		
		Minecraft mc = Minecraft.getInstance();
		int spacing = 4;
		int chatHeight = 20;
		UiUtilsSettings.Data settings = UiUtilsSettings.get();
		int startY = Mth.clamp(settings.mainUiY >= 0 ? settings.mainUiY : 24,
			10, Math.max(10, this.height - chatHeight - 5));
		int baseX = Mth.clamp(settings.mainUiX >= 0 ? settings.mainUiX : 8,
			4, Math.max(4, this.width - 140));
		uiUtilsMainWidgets.clear();
		UiUtils.UiWidgetLayout layout = UiUtils.addUiWidgets(mc, baseX, startY,
			spacing, this.height - startY - chatHeight - 8, this.width - 8,
			widget -> {
				uiUtilsMainWidgets.add(widget);
				addRenderableWidget(widget);
			});
		uiUtilsChatField = UiUtils.createChatField(mc, this.font,
			layout.chatX(), layout.chatY(), layout.chatWidth(),
			layout.chatHeight(), layout.uiScale());
		addRenderableWidget(uiUtilsChatField);
		uiUtilsMainWidgets.add(uiUtilsChatField);
		UiUtilsMainPanelDrag.attach(this, uiUtilsMainWidgets, layout, baseX, startY);
	}
	
	// The book's text widget can cover the panel, so dispatch foreground controls first.
	@Inject(at = @At("HEAD"), method = "mouseClicked(Lnet/minecraft/client/input/MouseButtonEvent;Z)Z", cancellable = true)
	private void uiutils$mouseClicked(MouseButtonEvent event, boolean doubleClick, CallbackInfoReturnable<Boolean> cir)
	{
		if(UiUtilsState.isUiEnabled()) {
			if(event.button() == McCompat.LEFT_BUTTON)
				for(AbstractWidget widget : uiUtilsMainWidgets)
					if(widget.active && widget.visible && widget.isMouseOver(event.x(), event.y())
						&& widget.mouseClicked(event, doubleClick)) {
						setFocused(widget);
						setDragging(true);
						cir.setReturnValue(true); return;
					}
			if(UiUtilsMainPanelDrag.mouseClicked(this, event)) { cir.setReturnValue(true); return; }
		}

	}

	@Override
	public boolean mouseDragged(MouseButtonEvent event, double dx, double dy)
	{
		if(UiUtilsState.isUiEnabled() && UiUtilsMainPanelDrag.mouseDragged(this, event, dx, dy))
			return true;
		return super.mouseDragged(event, dx, dy);
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent event)
	{
		if(UiUtilsMainPanelDrag.mouseReleased(this)) return true;
		return super.mouseReleased(event);
	}

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (UiUtilsState.isUiEnabled() && uiUtilsChatField != null && uiUtilsChatField.isFocused()) {
            if (event.isEscape()) setFocused(null); else uiUtilsChatField.keyPressed(event);
            return true;
        }
        return super.keyPressed(event);
    }
}
