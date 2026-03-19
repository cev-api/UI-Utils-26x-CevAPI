package com.ui_utils.uiutils;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Font;

public final class PacketHud {
    private static long lastSecond = System.nanoTime();
    private static int secIn, secOut;
    private static int rateIn, rateOut;
    private static long totalIn, totalOut;

    private PacketHud() {}

    public static void onTick() {
        long now = System.nanoTime();
        if (now - lastSecond >= 1_000_000_000L) {
            rateIn = secIn;
            rateOut = secOut;
            secIn = 0;
            secOut = 0;
            lastSecond = now;
        }
    }

    public static void incIncoming() {
        secIn++;
        totalIn++;
    }

    public static void incOutgoing() {
        secOut++;
        totalOut++;
    }

    public static void render(GuiGraphicsExtractor g) {
        if (!UiUtilsSettings.get().packetHudEnabled)
            return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null)
            return; // only in-game like Wurst's HUDs
        int sw = mc.getWindow().getGuiScaledWidth();
        int xRight = sw - 6;
        int y = 6;
        int queued = com.ui_utils.uiutils.UiUtilsState.delayedUiPackets.size();
        boolean delaying = com.ui_utils.uiutils.UiUtilsState.delayUiPackets;
        String l1 = rateIn + " IN / " + rateOut + " OUT";
        String l2 = queued > 0 ? "    " + queued + " QUEUED" : "";
        int w1 = mc.font.width(l1);
        int w2 = l2.isEmpty() ? 0 : mc.font.width(l2);
        int color = 0xFF000000 | (UiUtilsSettings.get().packetHudColor & 0xFFFFFF);
        g.text(mc.font, l1, xRight - w1, y, color, false);
        if(!l2.isEmpty())
            g.text(mc.font, l2, xRight - w2, y + 10, color, false);
    }

    // Render path that accepts the runtime GuiGraphics instance without importing it
    public static void renderAny(Object graphics) {
        if (!UiUtilsSettings.get().packetHudEnabled)
            return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null)
            return;

        int queued = com.ui_utils.uiutils.UiUtilsState.delayedUiPackets.size();
        String l1 = rateIn + " IN / " + rateOut + " OUT";
        String l2 = queued > 0 ? "    " + queued + " QUEUED" : "";

        int sw = mc.getWindow().getGuiScaledWidth();
        int xRight = sw - 6;
        int y = 6;
        Font font = mc.font;
        int w1 = font.width(l1);
        int w2 = l2.isEmpty() ? 0 : font.width(l2);

        int color = 0xFF000000 | (UiUtilsSettings.get().packetHudColor & 0xFFFFFF);
        try {
            // Try to call drawString(Font,String,int,int,int,boolean) reflectively on GuiGraphics
            Class<?> gg = Class.forName("net.minecraft.client.gui.GuiGraphics");
            if (gg.isInstance(graphics)) {
                java.lang.reflect.Method draw = gg.getMethod("drawString", Font.class, String.class, int.class, int.class, int.class, boolean.class);
                draw.invoke(graphics, font, l1, xRight - w1, y, color, false);
                if(!l2.isEmpty())
                    draw.invoke(graphics, font, l2, xRight - w2, y + 10, color, false);
                return;
            }
        } catch (Throwable ignored) {
            // Fall back silently
        }

        if (graphics instanceof GuiGraphicsExtractor ge) {
            ge.text(font, l1, xRight - w1, y, color, false);
            if(!l2.isEmpty())
                ge.text(font, l2, xRight - w2, y + 10, color, false);
        }
    }
}
