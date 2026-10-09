package com.ui_utils.mixin;

import com.mojang.brigadier.ParseResults;
import com.mojang.brigadier.suggestion.Suggestions;
import com.ui_utils.uiutils.UiUtilsCommandCompletion;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.gui.components.CommandSuggestions;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.multiplayer.ClientSuggestionProvider;
import net.minecraft.util.FormattedCharSequence;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Use Minecraft's existing dropdown, navigation, narration and Tab cycling. */
@Mixin(CommandSuggestions.class)
public abstract class UiUtilsCommandSuggestionsMixin {
    @Shadow @Final private EditBox input;
    @Shadow @Final private List<FormattedCharSequence> commandUsage;
    @Shadow private ParseResults<ClientSuggestionProvider> currentParse;
    @Shadow private CompletableFuture<Suggestions> pendingSuggestions;
    @Shadow private CommandSuggestions.SuggestionsList suggestions;
    @Shadow private boolean keepSuggestions;
    @Shadow private boolean allowSuggestions;
    @Shadow private boolean currentParseIsCommand;
    @Shadow private boolean currentParseIsMessage;
    @Shadow public abstract void showSuggestions(boolean narrate);

    @Inject(method = "updateCommandInfo()V", at = @At("HEAD"), cancellable = true)
    private void uiutils$complete(CallbackInfo ci) {
        Suggestions local = UiUtilsCommandCompletion.suggest(input.getValue(), input.getCursorPosition(), false);
        if (local == null) return;
        currentParse = null;
        currentParseIsCommand = false;
        currentParseIsMessage = true;
        commandUsage.clear();
        if (!keepSuggestions) {
            input.setSuggestion(null);
            suggestions = null;
            pendingSuggestions = CompletableFuture.completedFuture(local);
            if (allowSuggestions) showSuggestions(false);
        }
        ci.cancel();
    }
}
