package com.ui_utils.uiutils;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.tree.RootCommandNode;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.ClientSuggestionProvider;

/** A separately decoded server packet tree, never the dispatcher merged with local commands. */
public interface UiUtilsServerCommands {
    RootCommandNode<ClientSuggestionProvider> uiutils$getServerCommandRoot();

    static CommandDispatcher<ClientSuggestionProvider> dispatcher(ClientPacketListener connection) {
        if (!(connection instanceof UiUtilsServerCommands commands)) return null;
        var root = commands.uiutils$getServerCommandRoot();
        return root == null ? null : new CommandDispatcher<>(root);
    }
}
