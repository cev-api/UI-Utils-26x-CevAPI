package com.ui_utils.mixin.ui_utils;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import io.netty.channel.ChannelHandlerContext;
import io.netty.buffer.ByteBuf;
import java.util.List;
import net.minecraft.network.PacketDecoder;
import net.minecraft.network.protocol.Packet;
import com.ui_utils.packettools.AdvancedPacketTool;
@Mixin(PacketDecoder.class)
public abstract class PacketTimingDecoderMixin {
 @Unique private int uiutils$outputStart;
 @Inject(method="decode",at=@At("HEAD"))
 private void start(ChannelHandlerContext context,ByteBuf bytes,List<Object> out,CallbackInfo ci){uiutils$outputStart=out.size();}
 @Inject(method="decode",at=@At("TAIL"))
 private void end(ChannelHandlerContext context,ByteBuf bytes,List<Object> out,CallbackInfo ci){for(int i=uiutils$outputStart;i<out.size();i++)if(out.get(i) instanceof Packet<?> packet)AdvancedPacketTool.timing(packet,"S2C","DECODED");}
}
