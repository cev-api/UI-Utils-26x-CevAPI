package com.ui_utils.mixin;

import java.util.Locale;
import com.ui_utils.uiutils.UiUtilsMultiplayerCompat;
import com.ui_utils.uiutils.UiUtilsSettings;
import com.ui_utils.uiutils.UiUtilsState;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Checkbox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(JoinMultiplayerScreen.class)
public abstract class MultiplayerScreenMixin extends Screen {
	@Unique
	private Checkbox uiUtils$bypassResourcePackBox;

	@Unique
	private Checkbox uiUtils$forceDenyResourcePackBox;

	@Unique
	private Checkbox uiUtils$ignoreRegistrySyncBox;

	private MultiplayerScreenMixin(Component title) {
		super(title);
	}

	@Inject(method = "init()V", at = @At("TAIL"))
	private void uiutils$syncProtectionOptions(CallbackInfo ci) {
		uiutils$syncProtectionControls();
	}

	@Inject(method = "repositionElements()V", at = @At("TAIL"))
	private void uiutils$repositionProtectionOptions(CallbackInfo ci) {
		uiutils$syncProtectionControls();
	}

	@Unique
	private void uiutils$syncProtectionControls() {
		boolean externalResourcePack = false;
		boolean externalRegistrySync = false;
		AbstractWidget backButton = null;
		for (var child : children()) {
			if (!(child instanceof AbstractWidget widget)
				|| uiutils$isOwnWidget(widget))
				continue;

			String rawLabel = widget.getMessage().getString();
			if (rawLabel.equals(CommonComponents.GUI_BACK.getString()))
				backButton = widget;
			if (!widget.visible)
				continue;
			String label = rawLabel.toLowerCase(Locale.ROOT);
			externalResourcePack |= label.contains("bypass resource pack")
				|| label.contains("force deny resource pack");
			externalRegistrySync |= label.contains("ignore registry sync");
		}
		UiUtilsMultiplayerCompat.setExternalControls(externalResourcePack,
			externalRegistrySync);

		boolean show = UiUtilsState.isUiEnabled()
			&& UiUtilsSettings.get().showResourcePackButtons;
		boolean showResourcePack = show && !externalResourcePack;
		boolean showRegistrySync = show && !externalRegistrySync;
		uiutils$ensureBypassResourcePackBox(showResourcePack);
		uiutils$ensureForceDenyResourcePackBox(showResourcePack);
		uiutils$ensureIgnoreRegistrySyncBox(showRegistrySync);

		boolean haveProtectionToggles = showResourcePack || showRegistrySync
			|| externalResourcePack || externalRegistrySync;
		if (haveProtectionToggles && backButton != null) {
			removeWidget(backButton);
		}
		uiutils$layoutProtectionOptions();
	}

	@Unique
	private boolean uiutils$isOwnWidget(AbstractWidget widget) {
		return widget == uiUtils$bypassResourcePackBox
			|| widget == uiUtils$forceDenyResourcePackBox
			|| widget == uiUtils$ignoreRegistrySyncBox;
	}

	@Unique
	private void uiutils$ensureBypassResourcePackBox(boolean visible) {
		if (visible && uiUtils$bypassResourcePackBox == null) {
			uiUtils$bypassResourcePackBox = Checkbox
				.builder(Component.literal("Bypass resource pack"), font)
				.pos(6, 0)
				.selected(UiUtilsSettings.get().bypassResourcePack)
				.onValueChange((box, checked) -> {
					UiUtilsSettings.get().bypassResourcePack = checked;
					UiUtilsSettings.save();
				})
				.maxWidth(220)
				.build();
		}
		uiutils$setWidgetVisible(uiUtils$bypassResourcePackBox, visible);
	}

	@Unique
	private void uiutils$ensureForceDenyResourcePackBox(boolean visible) {
		if (visible && uiUtils$forceDenyResourcePackBox == null) {
			uiUtils$forceDenyResourcePackBox = Checkbox
				.builder(Component.literal("Force deny resource pack"), font)
				.pos(6, 0)
				.selected(UiUtilsSettings.get().resourcePackForceDeny)
				.onValueChange((box, checked) -> {
					UiUtilsSettings.get().resourcePackForceDeny = checked;
					UiUtilsSettings.save();
				})
				.maxWidth(220)
				.build();
		}
		uiutils$setWidgetVisible(uiUtils$forceDenyResourcePackBox, visible);
	}

	@Unique
	private void uiutils$ensureIgnoreRegistrySyncBox(boolean visible) {
		if (visible && uiUtils$ignoreRegistrySyncBox == null) {
			uiUtils$ignoreRegistrySyncBox = Checkbox
				.builder(Component.literal("Ignore registry sync"), font)
				.pos(6, 0)
				.selected(UiUtilsSettings.get().ignoreRegistrySync)
				.onValueChange((box, checked) -> {
					UiUtilsSettings.get().ignoreRegistrySync = checked;
					UiUtilsSettings.save();
				})
				.maxWidth(220)
				.build();
		}
		uiutils$setWidgetVisible(uiUtils$ignoreRegistrySyncBox, visible);
	}

	@Unique
	private void uiutils$setWidgetVisible(Checkbox widget, boolean visible) {
		if (widget == null)
			return;
		if (visible && !children().contains(widget))
			addRenderableWidget(widget);
		widget.visible = visible;
		widget.active = visible;
	}

	@Unique
	private void uiutils$layoutProtectionOptions() {
		int x = 6;
		if (uiUtils$bypassResourcePackBox != null) {
			uiUtils$bypassResourcePackBox.setX(x);
			uiUtils$bypassResourcePackBox.setY(height - 74);
		}
		if (uiUtils$forceDenyResourcePackBox != null) {
			uiUtils$forceDenyResourcePackBox.setX(x);
			uiUtils$forceDenyResourcePackBox.setY(height - 52);
		}
		if (uiUtils$ignoreRegistrySyncBox != null) {
			uiUtils$ignoreRegistrySyncBox.setX(x);
			uiUtils$ignoreRegistrySyncBox.setY(height - 30);
		}
	}
}
