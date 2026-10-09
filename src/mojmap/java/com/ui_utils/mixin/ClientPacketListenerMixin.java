package com.ui_utils.mixin;

import com.ui_utils.uiutils.UiUtilsCommandScanner;
import com.ui_utils.uiutils.UiUtilsLegacyPluginScanner;
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
		UiUtilsLegacyPluginScanner.onSuggestionsPacket(packet);
		UiUtilsCommandScanner.onSuggestionsPacket(packet);
	}
    @Inject(method = "handleOpenScreen", at = @At("TAIL"))
    private void uiutils$opened(net.minecraft.network.protocol.game.ClientboundOpenScreenPacket packet, CallbackInfo ci) {
        var mc = net.minecraft.client.Minecraft.getInstance();
        if (mc.player != null) com.ui_utils.uiutils.UiUtilsGuiCache.opened(mc.player.containerMenu);
    }
    @Inject(method = "handleContainerClose", at = @At("TAIL"))
    private void uiutils$closed(net.minecraft.network.protocol.game.ClientboundContainerClosePacket packet, CallbackInfo ci) {
        com.ui_utils.uiutils.UiUtilsGuiCache.closedId(packet.getContainerId());
    }
    @Inject(method = "handleSetTime", at = @At("TAIL"))
    private void uiutils$serverTick(net.minecraft.network.protocol.game.ClientboundSetTimePacket packet, CallbackInfo ci) {
        com.ui_utils.uiutils.macro.UiUtilsMacroRuntimeState.serverTime(packet.gameTime());
    }
}
