package com.ui_utils.mixin;

import com.ui_utils.uiutils.UiUtils;
import com.ui_utils.uiutils.UiUtilsSettings;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientCommonPacketListenerImpl;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.common.ClientboundResourcePackPushPacket;
import net.minecraft.network.protocol.common.ServerboundResourcePackPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientCommonPacketListenerImpl.class)
public abstract class ClientCommonNetworkHandlerMixin
{
	@Shadow
	protected Minecraft minecraft;

	@Shadow
	protected Connection connection;

	@Inject(at = @At("HEAD"),
		method = "handleResourcePackPush(Lnet/minecraft/network/protocol/common/ClientboundResourcePackPushPacket;)V",
		cancellable = true)
	private void uiutils$onResourcePack(ClientboundResourcePackPushPacket packet,
		CallbackInfo ci)
	{
		var settings = UiUtilsSettings.get();
		if(!settings.bypassResourcePack)
			return;
		if(!(packet.required() || settings.resourcePackForceDeny))
			return;

		if(connection == null)
			return;

		if(settings.resourcePackForceDeny)
		{
			connection.send(new ServerboundResourcePackPacket(packet.id(),
				ServerboundResourcePackPacket.Action.DECLINED));
			UiUtils.chatIfEnabled("Declined server resource pack request.");
		}else
		{
			connection.send(new ServerboundResourcePackPacket(packet.id(),
				ServerboundResourcePackPacket.Action.ACCEPTED));
			connection.send(new ServerboundResourcePackPacket(packet.id(),
				ServerboundResourcePackPacket.Action.SUCCESSFULLY_LOADED));
			UiUtils.chatIfEnabled("Bypassed server resource pack request.");
		}

		UiUtils.LOGGER.info("[UI Utils] Resource pack request intercepted. required={}, forceDeny={}, url={} ",
			packet.required(), settings.resourcePackForceDeny, packet.url());
		ci.cancel();
	}
}
