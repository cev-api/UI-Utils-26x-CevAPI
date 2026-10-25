package com.ui_utils.mixin.ui_utils;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(net.minecraft.client.Minecraft.class)
public abstract class PacketTimingShutdownMixin {
 @Inject(method="close",at=@At("HEAD"))
 private void close(CallbackInfo ci){com.ui_utils.packettools.AdvancedPacketTool.shutdown();}
}
