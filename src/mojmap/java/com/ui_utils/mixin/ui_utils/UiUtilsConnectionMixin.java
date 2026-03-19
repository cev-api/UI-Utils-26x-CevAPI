/*
 * Copyright (c) 2014-2026 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package com.ui_utils.mixin.ui_utils;

import com.ui_utils.packettools.AdvancedPacketTool;
import com.ui_utils.uiutils.UiUtils;
import com.ui_utils.uiutils.UiUtilsDisconnect;
import com.ui_utils.uiutils.PacketHud;
import com.ui_utils.uiutils.UiUtilsState;
import io.netty.channel.ChannelFutureListener;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ServerboundContainerButtonClickPacket;
import net.minecraft.network.protocol.game.ServerboundContainerClickPacket;
import net.minecraft.network.protocol.game.ServerboundSignUpdatePacket;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Connection.class)
public class UiUtilsConnectionMixin {
	@Inject(at = @At("HEAD"),
		method = "send(Lnet/minecraft/network/protocol/Packet;Lio/netty/channel/ChannelFutureListener;)V",
		cancellable = true)
	private void onSend(Packet<?> packet,
		@Nullable ChannelFutureListener callback, CallbackInfo ci) {
		if (!AdvancedPacketTool.onOutgoing(packet)) {
			ci.cancel();
			return;
		}
		if (UiUtilsDisconnect.shouldCancelOutgoing(packet)) {
			ci.cancel();
			return;
		}
		if (!UiUtilsState.isUiEnabled())
			return;

		boolean isUiPacket = packet instanceof ServerboundContainerClickPacket
			|| packet instanceof ServerboundContainerButtonClickPacket;
		if (isUiPacket) {
			UiUtils.LOGGER.info(
				"UiUtilsConnectionMixin: attempting to send UI packet {} (sendUiPackets={}, delayUiPackets={})",
				packet.getClass().getSimpleName(), UiUtilsState.sendUiPackets,
				UiUtilsState.delayUiPackets);
			UiUtils.chatIfEnabled(
				"Sending UI packet: " + packet.getClass().getSimpleName());
		}

		if (!UiUtilsState.sendUiPackets && isUiPacket) {
			UiUtils.LOGGER.info(
				"UiUtilsConnectionMixin: canceled UI packet send because sendUiPackets=false");
			UiUtils.chatIfEnabled("Canceled UI packet (sendUiPackets=false)");
			ci.cancel();
			return;
		}

		if (UiUtilsState.delayUiPackets) {
			UiUtilsState.delayedUiPackets.add(packet);
			UiUtils.refreshQueueCounterButtons();
			UiUtils.LOGGER.info(
				"UiUtilsConnectionMixin: delayed packet (queued {} packets)",
				UiUtilsState.delayedUiPackets.size());
			UiUtils.chatIfEnabled("Delayed packet (queued "
				+ UiUtilsState.delayedUiPackets.size() + ")");
			ci.cancel();
			return;
		}

		// Update HUD counter for all outgoing packets
		PacketHud.incOutgoing();

		if (!UiUtilsState.shouldEditSign
			&& packet instanceof ServerboundSignUpdatePacket) {
			UiUtilsState.shouldEditSign = true;
			ci.cancel();
		}
	}
}

