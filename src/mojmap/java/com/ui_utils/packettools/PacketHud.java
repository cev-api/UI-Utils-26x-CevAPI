package com.ui_utils.packettools;

import net.minecraft.client.Minecraft;

public final class PacketHud {
    private static long lastSecond = System.currentTimeMillis();
    private static int curIn;
    private static int curOut;
    private static int ppsIn;
    private static int ppsOut;
    private static long totalIn;
    private static long totalOut;

    private PacketHud() {}

    static void recordIncoming() {
        curIn++;
        totalIn++;
        tick();
    }

    static void recordOutgoing() {
        curOut++;
        totalOut++;
        tick();
    }

    private static void tick() {
        long now = System.currentTimeMillis();
        if (now - lastSecond >= 1000L) {
            ppsIn = curIn;
            ppsOut = curOut;
            curIn = curOut = 0;
            lastSecond = now;
        }
    }

    public static int getPpsIn() { return ppsIn; }
    public static int getPpsOut() { return ppsOut; }
    public static long getTotalIn() { return totalIn; }
    public static long getTotalOut() { return totalOut; }
}
