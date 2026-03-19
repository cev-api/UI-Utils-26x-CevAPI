package com.ui_utils.mixin.ui_utils;

import com.ui_utils.packettools.AdvancedPacketTool;
import com.ui_utils.uiutils.PacketHud;
import io.netty.channel.ChannelHandlerContext;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Connection.class)
public class UiUtilsConnectionReceiveMixin {
	@Inject(at = @At("HEAD"),
		method = "channelRead0(Lio/netty/channel/ChannelHandlerContext;Lnet/minecraft/network/protocol/Packet;)V",
		cancellable = true)
	private void uiutils$onIncoming(ChannelHandlerContext context,
		Packet<?> packet, CallbackInfo ci) {
		if (!AdvancedPacketTool.onIncoming(packet))
			ci.cancel();

		// Count for HUD
		PacketHud.incIncoming();
	}
}
