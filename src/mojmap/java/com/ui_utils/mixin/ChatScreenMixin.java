package com.ui_utils.mixin;

import com.ui_utils.uiutils.UiUtils;
import com.ui_utils.uiutils.UiUtilsCommandSystem;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ChatScreen.class)
public abstract class ChatScreenMixin extends Screen {
	private ChatScreenMixin(Component title) {
		super(title);
	}

	@Inject(at = @At("HEAD"), method = "handleChatInput(Ljava/lang/String;Z)V", cancellable = true)
	private void uiutils$onSendMessage(String message, boolean addToHistory, CallbackInfo ci) {
		message = normalizeChatMessage(message);
		if (message.isEmpty())
			return;

		if (message.equalsIgnoreCase("^toggleuiutils")) {
			UiUtils.toggleUiUtils(minecraft);
			if (addToHistory)
				minecraft.gui.getChat().addRecentChat(message);
			minecraft.setScreen(null);
			ci.cancel();
			return;
		}

		if (UiUtilsCommandSystem.isUiUtilsCommand(message)) {
			String command = UiUtilsCommandSystem.extractCommandBody(message);
			String result = UiUtilsCommandSystem.execute(command);
			boolean opensScreen = command.equalsIgnoreCase("settings") || command.toLowerCase().startsWith("settings ");
			if (addToHistory)
				minecraft.gui.getChat().addRecentChat(message);
			if (minecraft.player != null && !result.isEmpty()) {
				for (String line : result.split("\\n"))
					minecraft.player.sendSystemMessage(Component.literal(line));
			}
			if (!opensScreen)
				minecraft.setScreen(null);
			ci.cancel();
		}
	}

	@Shadow
	public abstract String normalizeChatMessage(String chatText);
}
