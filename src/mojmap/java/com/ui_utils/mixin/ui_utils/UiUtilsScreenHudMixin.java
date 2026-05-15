package com.ui_utils.mixin.ui_utils;

import com.ui_utils.uiutils.PacketHud;
import com.ui_utils.uiutils.UiUtilsAutoduper;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Screen.class)
public class UiUtilsScreenHudMixin {
    @Inject(method = "extractRenderState(Lnet/minecraft/client/gui/GuiGraphicsExtractor;IIF)V", at = @At("TAIL"))
    private void uiutils$renderHudEverywhere(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTicks, CallbackInfo ci) {
        PacketHud.render(graphics);
        UiUtilsAutoduper.renderAbortOverlay(graphics);
    }

    @Inject(method = "mouseClicked(Lnet/minecraft/client/input/MouseButtonEvent;Z)Z", at = @At("HEAD"), cancellable = true, require = 0)
    private void uiutils$abortAutoduperClick(MouseButtonEvent context, boolean doubleClick, CallbackInfoReturnable<Boolean> cir) {
        if (UiUtilsAutoduper.handleAbortOverlayClick(context.x(), context.y(), context.button())) {
            cir.setReturnValue(true);
            cir.cancel();
        }
    }
}
