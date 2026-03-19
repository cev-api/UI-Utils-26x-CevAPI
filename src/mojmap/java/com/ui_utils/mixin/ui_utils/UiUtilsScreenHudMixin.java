package com.ui_utils.mixin.ui_utils;

import com.ui_utils.uiutils.PacketHud;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Screen.class)
public class UiUtilsScreenHudMixin {
    @Inject(method = "extractRenderState(Lnet/minecraft/client/gui/GuiGraphicsExtractor;IIF)V", at = @At("TAIL"))
    private void uiutils$renderHudEverywhere(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTicks, CallbackInfo ci) {
        PacketHud.render(graphics);
    }
}
