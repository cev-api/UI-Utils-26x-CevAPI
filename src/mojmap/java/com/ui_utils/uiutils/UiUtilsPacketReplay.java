package com.ui_utils.uiutils;

import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;

/** Explicit release bypasses every delay rule once, including nested send hooks. */
public final class UiUtilsPacketReplay {
    private static final ThreadLocal<Boolean> REPLAYING = ThreadLocal.withInitial(() -> false);
    private UiUtilsPacketReplay() {}
    public static boolean isReplaying() { return REPLAYING.get(); }
    @SuppressWarnings({"rawtypes", "unchecked"})
    public static boolean handle(Connection connection, net.minecraft.network.PacketListener listener, Packet<?> packet) {
        if (!connection.isConnected() || listener == null || connection.getPacketListener() != listener || !listener.shouldHandleMessage(packet)) return false;
        ((Packet)packet).handle(listener); return true;
    }
    public static void send(Connection connection, Packet<?> packet) {
        boolean previous = REPLAYING.get();
        REPLAYING.set(true);
        try { connection.send(packet); }
        finally { if (previous) REPLAYING.set(true); else REPLAYING.remove(); }
    }
}
