package com.ui_utils.mixin;

import com.ui_utils.uiutils.UiUtilsCommandScanner;
import com.ui_utils.uiutils.macro.UiUtilsMacroRuntimeState;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundPlayerChatPacket;
import net.minecraft.network.protocol.game.ClientboundSystemChatPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPacketListener.class)
public abstract class ClientPacketListenerChatMixin {
    @Inject(method = "handleSystemChat(Lnet/minecraft/network/protocol/game/ClientboundSystemChatPacket;)V", at = @At("TAIL"), require = 0)
    private void uiutils$onSystemChat(ClientboundSystemChatPacket packet, CallbackInfo ci) {
        Component content = packet.content();
        if (content != null) {
            UiUtilsMacroRuntimeState.onChatMessage(content.getString());
            UiUtilsCommandScanner.onChatMessage(content.getString());
        }
    }

    @Inject(method = "handlePlayerChat(Lnet/minecraft/network/protocol/game/ClientboundPlayerChatPacket;)V", at = @At("TAIL"), require = 0)
    private void uiutils$onPlayerChat(ClientboundPlayerChatPacket packet, CallbackInfo ci) {
        String body = packet.body().content();
        if (body != null) {
            UiUtilsMacroRuntimeState.onChatMessage(body);
        }
    }
}
