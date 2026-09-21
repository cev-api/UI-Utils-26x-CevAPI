/*
 * Copyright (c) 2014-2026 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package com.ui_utils.mixin.ui_utils;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.inventory.AbstractContainerMenu;
import com.ui_utils.uiutils.UiUtils;
import com.ui_utils.uiutils.UiUtilsAutoduper;
import com.ui_utils.uiutils.UiUtilsContainerTransfer;
import com.ui_utils.uiutils.UiUtilsSettings;
import com.ui_utils.uiutils.UiUtilsState;
import com.ui_utils.uiutils.UiUtilsTimedClickSlot;

@Mixin(AbstractContainerScreen.class)
public abstract class UiUtilsAbstractContainerScreenMixin<T extends AbstractContainerMenu>
	extends Screen
{
	@Unique
	private EditBox uiUtilsChatField;
	
	@Shadow
	protected int leftPos;
	
	@Shadow
	protected int topPos;
	
	@Shadow
	protected int imageWidth;
	
	// Steal / Store / Dump buttons above the open container. Vanilla styled so
	// they match the current resource pack.
	@Unique
	private Button containerStealButton;
	
	@Unique
	private Button containerStoreButton;
	
	@Unique
	private Button containerDumpButton;
	
	@Unique
	private static final int CONTAINER_BUTTON_WIDTH = 44;
	
	@Unique
	private static final int CONTAINER_BUTTON_HEIGHT = 12;
	
	@Unique
	private static final int CONTAINER_BUTTON_GAP = 3;
	
	private UiUtilsAbstractContainerScreenMixin(Component title)
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
		// Keep the stack near top-left so it does not bury in-game chat.
		// "UI-Utils by CevAPI" ends up just below common Wurst headers.
		int preferredTop = 24;
		int startY = Mth.clamp(preferredTop, 5, Math.max(5,
			this.height - chatHeight - 5));
		int baseX = 8;
		int maxRight = Math.max(baseX + 160,
			Math.min(this.leftPos - 8, this.width - 8));
		UiUtils.UiWidgetLayout layout = UiUtils.addUiWidgets(mc, baseX, startY,
			spacing, this.height - startY - chatHeight - 8, maxRight,
			this::addRenderableWidget);
		uiUtilsChatField = UiUtils.createChatField(mc, this.font,
			layout.chatX(), layout.chatY(), layout.chatWidth(),
			layout.chatHeight());
		addRenderableWidget(uiUtilsChatField);
		
		initContainerButtons();
	}
	
	/** True when this screen is a container GUI rather than an inventory. */
	@Unique
	private boolean isContainerGui()
	{
		if((Object)this instanceof InventoryScreen)
			return false;
		return !((Object)this instanceof CreativeModeInventoryScreen);
	}
	
	/** Small vanilla-styled button so it matches the active resource pack. */
	@Unique
	private Button containerButton(String label, Button.OnPress onPress)
	{
		Button button = Button.builder(Component.literal(label), onPress)
			.bounds(0, 0, CONTAINER_BUTTON_WIDTH, CONTAINER_BUTTON_HEIGHT).build();
		addRenderableWidget(button);
		return button;
	}
	
	/** Steal / Store / Dump buttons shown above the container GUI. */
	@Unique
	private void initContainerButtons()
	{
		// Screen#clearWidgets() drops the old widgets on a resize while the fields
		// stay set, so recreate them whenever they are no longer real children.
		if(containerStealButton != null && !children().contains(containerStealButton))
		{
			containerStealButton = null;
			containerStoreButton = null;
			containerDumpButton = null;
		}
		if(containerStealButton != null)
			return;
		containerStealButton = containerButton("Steal",
			b -> UiUtilsContainerTransfer.steal(Minecraft.getInstance()));
		containerStoreButton = containerButton("Store",
			b -> UiUtilsContainerTransfer.store(Minecraft.getInstance()));
		containerDumpButton = containerButton("Dump",
			b -> UiUtilsContainerTransfer.dump(Minecraft.getInstance()));
	}
	
	@Unique
	private boolean containerButtonsAllowed()
	{
		if(!UiUtilsState.isUiEnabled()
			|| !UiUtilsSettings.get().showStealDumpButtons || !isContainerGui())
			return false;
		Minecraft mc = Minecraft.getInstance();
		return mc.player != null && mc.player.containerMenu != null
			&& mc.player.containerMenu != mc.player.inventoryMenu;
	}
	
	@Unique
	private void updateContainerButtons()
	{
		if(containerStealButton == null)
			return;
		boolean show = containerButtonsAllowed();
		AbstractWidget[] buttons = {containerStealButton, containerStoreButton,
			containerDumpButton};
		for(AbstractWidget button : buttons)
		{
			button.visible = show;
			button.active = show;
		}
		if(!show)
		{
			for(AbstractWidget button : buttons)
			{
				button.setX(-2000);
				button.setY(-2000);
			}
			return;
		}
		int total = buttons.length * CONTAINER_BUTTON_WIDTH
			+ (buttons.length - 1) * CONTAINER_BUTTON_GAP;
		int x = this.leftPos + (this.imageWidth - total) / 2;
		int y = this.topPos - CONTAINER_BUTTON_HEIGHT - 4;
		for(AbstractWidget button : buttons)
		{
			button.setX(x);
			button.setY(y);
			x += CONTAINER_BUTTON_WIDTH + CONTAINER_BUTTON_GAP;
		}
	}
	
	@Inject(at = @At("HEAD"),
		method = "extractRenderState(Lnet/minecraft/client/gui/GuiGraphicsExtractor;IIF)V")
	private void uiutils$updateContainerButtons(GuiGraphicsExtractor graphics,
		int mouseX, int mouseY, float partialTicks, CallbackInfo ci)
	{
		if(!UiUtilsState.isUiEnabled())
			return;
		updateContainerButtons();
	}
	
	@Inject(at = @At("HEAD"),
		method = "keyPressed(Lnet/minecraft/client/input/KeyEvent;)Z",
		cancellable = true)
	private void onKeyPressed(KeyEvent keyEvent,
		CallbackInfoReturnable<Boolean> cir)
	{
		if(!UiUtilsState.isUiEnabled())
			return;
		
		if(uiUtilsChatField == null || !uiUtilsChatField.isFocused())
			return;
		
		if(uiUtilsChatField.keyPressed(keyEvent))
		{
			cir.setReturnValue(true);
			return;
		}
		
		if(keyEvent.isEscape())
		{
			uiUtilsChatField.setFocused(false);
			cir.setReturnValue(true);
			return;
		}
		
		cir.setReturnValue(true);
	}
	
	@Inject(at = @At("HEAD"),
		method = "mouseClicked(Lnet/minecraft/client/input/MouseButtonEvent;Z)Z",
		cancellable = true)
	private void uiutils$abortAutoduperClick(
		net.minecraft.client.input.MouseButtonEvent context,
		boolean doubleClick, CallbackInfoReturnable<Boolean> cir)
	{
		if(UiUtilsAutoduper.handleAbortOverlayClick(context.x(), context.y(),
			context.button()))
		{
			cir.setReturnValue(true);
			cir.cancel();
		}
	}
	
	@Inject(at = @At("HEAD"), method = "removed()V", cancellable = true)
	private void onRemoved(CallbackInfo ci)
	{
		if(UiUtilsState.skipNextContainerRemoval)
		{
			UiUtilsState.skipNextContainerRemoval = false;
			ci.cancel();
			return;
		}
		
		UiUtilsTimedClickSlot.stop();
		containerStealButton = null;
		containerStoreButton = null;
		containerDumpButton = null;
	}
}
