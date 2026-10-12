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
public abstract class ClientPacketListenerMixin implements com.ui_utils.uiutils.UiUtilsServerCommands {
    @org.spongepowered.asm.mixin.Shadow @org.spongepowered.asm.mixin.Final
    private static net.minecraft.network.protocol.game.ClientboundCommandsPacket.NodeBuilder<net.minecraft.client.multiplayer.ClientSuggestionProvider> COMMAND_NODE_BUILDER;
    @org.spongepowered.asm.mixin.Unique
    private com.mojang.brigadier.tree.RootCommandNode<net.minecraft.client.multiplayer.ClientSuggestionProvider> uiutils$serverRoot;

    @Inject(method = "handleCommands", at = @At(value = "INVOKE",
        target = "Lcom/mojang/brigadier/CommandDispatcher;<init>(Lcom/mojang/brigadier/tree/RootCommandNode;)V"))
    private void uiutils$captureServerCommands(net.minecraft.network.protocol.game.ClientboundCommandsPacket packet, CallbackInfo ci) {
        var connection = (ClientPacketListener)(Object)this;
        // Decode independently: Fabric mutates the regular dispatcher at RETURN.
        uiutils$serverRoot = packet.getRoot(net.minecraft.commands.CommandBuildContext.simple(
            connection.registryAccess(), connection.enabledFeatures()), COMMAND_NODE_BUILDER);
    }

    @Override
    public com.mojang.brigadier.tree.RootCommandNode<net.minecraft.client.multiplayer.ClientSuggestionProvider> uiutils$getServerCommandRoot() {
        return uiutils$serverRoot;
    }

	@Inject(method = "handleCommandSuggestions(Lnet/minecraft/network/protocol/game/ClientboundCommandSuggestionsPacket;)V", at = @At("TAIL"))
	private void uiutils$onSuggestions(ClientboundCommandSuggestionsPacket packet, CallbackInfo ci) {
		if (net.minecraft.client.Minecraft.getInstance().getConnection() != (Object)this) return;
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
    @Inject(method = "handleContainerContent", at = @At("TAIL"))
    private void uiutils$contents(net.minecraft.network.protocol.game.ClientboundContainerSetContentPacket packet, CallbackInfo ci) {
        var mc = net.minecraft.client.Minecraft.getInstance();
        if (mc.getConnection() != (Object)this) return;
        if (mc.player == null) return;
        var menu = packet.containerId() == 0 ? mc.player.inventoryMenu : mc.player.containerMenu;
        if (menu.containerId == packet.containerId())
            com.ui_utils.uiutils.UiUtilsInventoryEvidence.fullContents(menu, packet.items(), packet.carriedItem());
    }
    @Inject(method = "handleSetTime", at = @At("TAIL"))
    private void uiutils$serverTick(net.minecraft.network.protocol.game.ClientboundSetTimePacket packet, CallbackInfo ci) {
        com.ui_utils.uiutils.macro.UiUtilsMacroRuntimeState.serverTime(packet.gameTime());
    }
}
