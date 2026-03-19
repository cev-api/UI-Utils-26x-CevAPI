/*
 * Copyright (c) 2014-2026 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package com.ui_utils.mixin.ui_utils;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractSignEditScreen;
import net.minecraft.network.chat.Component;
import com.ui_utils.uiutils.UiUtilsDisconnect;
import com.ui_utils.uiutils.UiUtils;
import com.ui_utils.uiutils.UiUtilsState;

@Mixin(AbstractSignEditScreen.class)
public abstract class UiUtilsAbstractSignEditScreenMixin extends Screen
{
	private UiUtilsAbstractSignEditScreenMixin(Component title)
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
		int buttonHeight = 20;
		int totalHeight = buttonHeight * 2 + spacing;
		int startY = Math.max(5, (this.height - totalHeight) / 2);
		int baseX = 8;
		addRenderableWidget(UiUtils.styledButton("Close without packet", b -> {
				UiUtilsState.shouldEditSign = false;
				mc.setScreen(null);
			}, baseX, startY, 115, 20));
		
		addRenderableWidget(UiUtils.styledButton("Disconnect", b -> {
				UiUtilsDisconnect.disconnectWithConfiguredMethod(mc);
			}, baseX, startY + buttonHeight + spacing, 115, 20));
	}
}

