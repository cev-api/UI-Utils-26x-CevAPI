package com.ui_utils.mixin.ui_utils;
import net.minecraft.network.PacketProcessor;
import net.minecraft.network.PacketListener;
import net.minecraft.network.protocol.Packet;
import com.ui_utils.packettools.AdvancedPacketTool;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(PacketProcessor.class)
public abstract class ForensicPacketScheduleMixin {
 @Inject(method="scheduleIfPossible", at=@At("HEAD"))
 private void wurst$schedule(PacketListener listener, Packet<?> packet, CallbackInfo ci) {
  if(!(listener instanceof net.minecraft.network.ClientboundPacketListener))return;
  AdvancedPacketTool.timing(packet,"S2C","CLIENT_SCHEDULE_REQUESTED");
 }
}
