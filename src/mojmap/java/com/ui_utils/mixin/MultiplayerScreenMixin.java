package com.ui_utils.mixin;

import com.ui_utils.uiutils.UiUtils;
import com.ui_utils.uiutils.UiUtilsSettings;
import com.ui_utils.uiutils.UiUtilsState;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(JoinMultiplayerScreen.class)
public abstract class MultiplayerScreenMixin extends Screen {
	@Unique
	private Button uiUtils$bypassResourcePackButton;

	@Unique
	private Button uiUtils$forceDenyResourcePackButton;

	private MultiplayerScreenMixin(Component title) {
		super(title);
	}

	@Inject(method = "init()V", at = @At("TAIL"))
	private void uiutils$addResourcePackButtons(CallbackInfo ci) {
		if (!UiUtilsState.isUiEnabled() || !UiUtilsSettings.get().showResourcePackButtons) {
			uiUtils$bypassResourcePackButton = null;
			uiUtils$forceDenyResourcePackButton = null;
			return;
		}

		uiUtils$bypassResourcePackButton = addRenderableWidget(Button.builder(
			Component.literal("Bypass Resource Pack: "
				+ (UiUtilsSettings.get().bypassResourcePack ? "ON" : "OFF")),
			b -> {
				UiUtilsSettings.get().bypassResourcePack = !UiUtilsSettings.get().bypassResourcePack;
				UiUtilsSettings.save();
				b.setMessage(Component.literal("Bypass Resource Pack: "
					+ (UiUtilsSettings.get().bypassResourcePack ? "ON" : "OFF")));
			}).bounds(0, 0, 200, 20).build());

		uiUtils$forceDenyResourcePackButton = addRenderableWidget(Button.builder(
			Component.literal("Force Deny: "
				+ (UiUtilsSettings.get().resourcePackForceDeny ? "ON" : "OFF")),
			b -> {
				UiUtilsSettings.get().resourcePackForceDeny = !UiUtilsSettings.get().resourcePackForceDeny;
				UiUtilsSettings.save();
				b.setMessage(Component.literal("Force Deny: "
					+ (UiUtilsSettings.get().resourcePackForceDeny ? "ON" : "OFF")));
			}).bounds(0, 0, 200, 20).build());

		uiutils$layoutResourcePackButtons();
	}

	@Inject(method = "repositionElements()V", at = @At("TAIL"))
	private void uiutils$layoutResourcePackButtonsAfterReposition(CallbackInfo ci) {
		uiutils$layoutResourcePackButtons();
	}

	@Unique
	private void uiutils$layoutResourcePackButtons() {
		if (uiUtils$bypassResourcePackButton == null || uiUtils$forceDenyResourcePackButton == null) {
			return;
		}

		int w = 200;
		int h = 20;
		int x = 8;
		int y2 = this.height - h - 8;
		int y1 = y2 - h - 4;
		uiUtils$bypassResourcePackButton.setX(x);
		uiUtils$bypassResourcePackButton.setY(y1);
		uiUtils$bypassResourcePackButton.setWidth(w);
		uiUtils$bypassResourcePackButton.setHeight(h);
		uiUtils$forceDenyResourcePackButton.setX(x);
		uiUtils$forceDenyResourcePackButton.setY(y2);
		uiUtils$forceDenyResourcePackButton.setWidth(w);
		uiUtils$forceDenyResourcePackButton.setHeight(h);
	}
}
