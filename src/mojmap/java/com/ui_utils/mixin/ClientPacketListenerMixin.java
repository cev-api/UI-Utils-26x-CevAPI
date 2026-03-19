package com.ui_utils.mixin;

import com.ui_utils.uiutils.UiUtilsCommandScanner;
import com.ui_utils.uiutils.UiUtilsPluginScanner;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundCommandSuggestionsPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPacketListener.class)
public abstract class ClientPacketListenerMixin {
	@Inject(method = "handleCommandSuggestions(Lnet/minecraft/network/protocol/game/ClientboundCommandSuggestionsPacket;)V", at = @At("TAIL"))
	private void uiutils$onSuggestions(ClientboundCommandSuggestionsPacket packet, CallbackInfo ci) {
		UiUtilsPluginScanner.onSuggestionsPacket(packet);
		UiUtilsCommandScanner.onSuggestionsPacket(packet);
	}
}
