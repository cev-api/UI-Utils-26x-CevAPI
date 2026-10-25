package com.ui_utils.mixin.ui_utils;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.network.PacketListener;
import net.minecraft.network.protocol.Packet;
import net.minecraft.client.Minecraft;
import com.ui_utils.packettools.AdvancedPacketTool;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
@Mixin(targets = "net.minecraft.network.PacketProcessor$ListenerAndPacket")
public abstract class ForensicPacketHandlerMixin {
 @WrapOperation(method = "handle", at = @At(value = "INVOKE", target = "Lnet/minecraft/network/protocol/Packet;handle(Lnet/minecraft/network/PacketListener;)V"))
 private void wurst$handler(Packet packet, PacketListener listener, Operation<Void> original) {
  if(!Minecraft.getInstance().isSameThread()) { original.call(packet, listener); return; }
  
  AdvancedPacketTool.timing(packet,"S2C","HANDLER_START");
  try { original.call(packet, listener); }
  finally { AdvancedPacketTool.timing(packet,"S2C","HANDLER_END"); }
 }
}
