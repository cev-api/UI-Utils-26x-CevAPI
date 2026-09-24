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
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.inventory.AbstractContainerMenu;
import com.ui_utils.uiutils.UiUtils;
import com.ui_utils.uiutils.UiUtilsContainerTransfer;
import com.ui_utils.uiutils.UiUtilsMainPanelDrag;
import com.ui_utils.uiutils.UiUtilsSettings;
import com.ui_utils.uiutils.UiUtilsState;
import com.ui_utils.uiutils.UiUtilsTimedClickSlot;

@Mixin(AbstractContainerScreen.class)
public abstract class UiUtilsAbstractContainerScreenMixin<T extends AbstractContainerMenu>
	extends Screen
{
	@Unique
	private EditBox uiUtilsChatField;
	@Unique
	private final List<AbstractWidget> uiUtilsMainWidgets = new ArrayList<>();
	
	@Shadow
	protected int leftPos;
	
	@Shadow
	protected int topPos;
	
	@Shadow
	protected int imageWidth;

	@Shadow
	protected int imageHeight;
	
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
		// "UI-utils by CevAPI" ends up just below common Wurst headers.
		int preferredTop = 10;
		UiUtilsSettings.Data settings = UiUtilsSettings.get();
		int baseX = settings.mainUiX >= 0 ? settings.mainUiX : 8;
		baseX = Mth.clamp(baseX, 4, Math.max(4, this.width - 140));
		int requestedTop = settings.mainUiY >= 0 ? settings.mainUiY : preferredTop;
		int startY = Mth.clamp(requestedTop, 10, Math.max(10,
			this.height - chatHeight - 5));
		int maxRight = this.width - 8;
		uiUtilsMainWidgets.clear();
		UiUtils.UiWidgetLayout layout = UiUtils.addUiWidgets(mc, baseX, startY,
			spacing, this.height - startY - chatHeight - 8, maxRight, widget -> {
				uiUtilsMainWidgets.add(widget);
				addRenderableWidget(widget);
			});
		uiUtilsChatField = UiUtils.createChatField(mc, this.font,
			layout.chatX(), layout.chatY(), layout.chatWidth(),
			layout.chatHeight(), 1F);
		addRenderableWidget(uiUtilsChatField);
		uiUtilsMainWidgets.add(uiUtilsChatField);
		UiUtilsMainPanelDrag.attach(this, uiUtilsMainWidgets, layout, baseX,
			startY);
		
		initContainerButtons();
		updateContainerButtons();
	}
	
	/** True when this screen is a container GUI rather than an inventory. */
	@Unique
	private boolean isContainerGui()
	{
		return !((Object)this instanceof CreativeModeInventoryScreen);
	}
	
	/** Small vanilla-styled button so it matches the active resource pack. */
	@Unique
	private Button containerButton(String label, Button.OnPress onPress)
	{
		Button button = Button.builder(Component.literal(label), onPress)
			.bounds(0, 0, CONTAINER_BUTTON_WIDTH, CONTAINER_BUTTON_HEIGHT).build();
		button.visible = false;
		button.active = false;
		button.setX(-2000);
		button.setY(-2000);
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
		if(!UiUtilsSettings.get().showStealDumpButtons || !isContainerGui())
			return false;
		Minecraft mc = Minecraft.getInstance();
		return mc.player != null && mc.player.containerMenu != null
			&& (mc.player.containerMenu != mc.player.inventoryMenu
				|| isPlayerInventory());
	}

	@Unique
	private boolean isPlayerInventory()
	{
		Minecraft mc = Minecraft.getInstance();
		return (Object)this instanceof InventoryScreen || mc.player != null
			&& mc.player.containerMenu == mc.player.inventoryMenu;
	}
	
	@Unique
	private void updateContainerButtons()
	{
		if(containerStealButton == null)
			return;
		boolean show = containerButtonsAllowed();
		AbstractWidget[] buttons = {containerStealButton, containerStoreButton,
			containerDumpButton};
		boolean playerInventory = isPlayerInventory();
		containerStealButton.visible = show && !playerInventory;
		containerStealButton.active = containerStealButton.visible;
		containerStoreButton.visible = show && !playerInventory;
		containerStoreButton.active = containerStoreButton.visible;
		containerDumpButton.visible = show;
		containerDumpButton.active = show;
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
		int y = this.topPos - CONTAINER_BUTTON_HEIGHT - 4;
		if(playerInventory)
		{
			containerStealButton.setX(-2000);
			containerStoreButton.setX(-2000);
			containerStealButton.setY(-2000);
			containerStoreButton.setY(-2000);
			int guiWidth = this.imageWidth > 0 ? this.imageWidth : 176;
			int guiHeight = Math.max(this.imageHeight, 166);
			int guiLeft = this.leftPos > 0 ? this.leftPos
				: (this.width - guiWidth) / 2;
			int guiTop = this.topPos > 4 ? this.topPos
				: (this.height - guiHeight) / 2;
			// InventoryScreen's GUI is centered. Keep the single Dump control
			// directly above it, never anchored to the screen's top corner.
			containerDumpButton.setX(guiLeft
				+ (guiWidth - CONTAINER_BUTTON_WIDTH) / 2);
			containerDumpButton.setY(Math.max(4, guiTop
				- CONTAINER_BUTTON_HEIGHT - 4));
			return;
		}
		int x = this.leftPos + (this.imageWidth - total) / 2;
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
		updateContainerButtons();
	}

	@Inject(method = "mouseClicked(Lnet/minecraft/client/input/MouseButtonEvent;Z)Z",
		at = @At("HEAD"), cancellable = true)
	private void uiutils$beginMainPanelDrag(MouseButtonEvent event,
		boolean doubleClick, CallbackInfoReturnable<Boolean> cir)
	{
		if(UiUtilsState.isUiEnabled()
			&& UiUtilsMainPanelDrag.mouseClicked((Screen)(Object)this, event))
			cir.setReturnValue(true);
	}

	@Inject(method = "mouseDragged(Lnet/minecraft/client/input/MouseButtonEvent;DD)Z",
		at = @At("HEAD"), cancellable = true)
	private void uiutils$dragMainPanel(MouseButtonEvent event, double deltaX,
		double deltaY, CallbackInfoReturnable<Boolean> cir)
	{
		if(UiUtilsMainPanelDrag.mouseDragged((Screen)(Object)this, event,
			deltaX, deltaY))
			cir.setReturnValue(true);
	}

	@Inject(method = "mouseReleased(Lnet/minecraft/client/input/MouseButtonEvent;)Z",
		at = @At("HEAD"), cancellable = true)
	private void uiutils$finishMainPanelDrag(MouseButtonEvent event,
		CallbackInfoReturnable<Boolean> cir)
	{
		if(UiUtilsMainPanelDrag.mouseReleased((Screen)(Object)this))
			cir.setReturnValue(true);
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
