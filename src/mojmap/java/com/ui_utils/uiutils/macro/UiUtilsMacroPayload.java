package com.ui_utils.uiutils.macro;

import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Opaque payload data: UTF-8 by default, or exact bytes after a hex: prefix. */
public record UiUtilsMacroPayload(CustomPacketPayload.Type<UiUtilsMacroPayload> type, byte[] bytes) implements CustomPacketPayload {
    private static final Set<Identifier> REGISTERED = new HashSet<>();
    public static void send(Minecraft mc, String channel, String value) {
        Identifier id = Identifier.parse(channel);
        byte[] bytes = value.startsWith("hex:") ? java.util.HexFormat.of().parseHex(value.substring(4).replace(" ", "")) : value.getBytes(StandardCharsets.UTF_8);
        if (bytes.length > 32767) throw new IllegalArgumentException("Payload exceeds 32767 bytes");
        Type<UiUtilsMacroPayload> type = new Type<>(id);
        if (REGISTERED.add(id)) {
            try {
                PayloadTypeRegistry.serverboundPlay().register(type, new StreamCodec<RegistryFriendlyByteBuf, UiUtilsMacroPayload>() {
                    public UiUtilsMacroPayload decode(RegistryFriendlyByteBuf buffer) {
                        byte[] data = new byte[buffer.readableBytes()]; buffer.readBytes(data); return new UiUtilsMacroPayload(type, data);
                    }
                    public void encode(RegistryFriendlyByteBuf buffer, UiUtilsMacroPayload payload) { buffer.writeBytes(payload.bytes()); }
                });
            } catch (RuntimeException failure) { REGISTERED.remove(id); throw new IllegalArgumentException("Channel already has a typed codec or cannot be registered: " + id, failure); }
        }
        mc.getConnection().send(new ServerboundCustomPayloadPacket(new UiUtilsMacroPayload(type, bytes)));
    }
}