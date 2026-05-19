package com.ui_utils.uiutils.macro;

import java.util.Locale;
import java.util.concurrent.atomic.AtomicLong;

public final class UiUtilsMacroRuntimeState {
    private static final AtomicLong incomingCount = new AtomicLong();
    private static final AtomicLong outgoingCount = new AtomicLong();

    private static volatile String lastIncomingPacket = "";
    private static volatile String lastOutgoingPacket = "";
    private static volatile String lastChatMessage = "";

    private UiUtilsMacroRuntimeState() {}

    public static void onIncomingPacket(String simpleName) {
        incomingCount.incrementAndGet();
        lastIncomingPacket = normalize(simpleName);
    }

    public static void onOutgoingPacket(String simpleName) {
        outgoingCount.incrementAndGet();
        lastOutgoingPacket = normalize(simpleName);
    }

    public static void onChatMessage(String message) {
        lastChatMessage = message == null ? "" : message;
    }

    public static long incomingCount() { return incomingCount.get(); }
    public static long outgoingCount() { return outgoingCount.get(); }
    public static String lastIncomingPacket() { return lastIncomingPacket; }
    public static String lastOutgoingPacket() { return lastOutgoingPacket; }
    public static String lastChatMessage() { return lastChatMessage; }

    private static String normalize(String v) {
        if (v == null) return "";
        String s = v.trim().toLowerCase(Locale.ROOT);
        if (s.endsWith("packet")) s = s.substring(0, s.length() - 6);
        return s;
    }
}
