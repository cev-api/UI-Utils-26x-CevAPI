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
        UiUtilsSettings.PacketHudPosition position = UiUtilsSettings.get().packetHudPosition;
        if (!position.isEnabled())
            return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null)
            return; // only in-game like Wurst's HUDs
        int sw = mc.getWindow().getGuiScaledWidth();
        int sh = mc.getWindow().getGuiScaledHeight();
        int queued = com.ui_utils.uiutils.UiUtilsState.delayedUiPackets.size();
        String l1 = rateIn + " IN / " + rateOut + " OUT";
        String l2 = queued > 0 ? "    " + queued + " QUEUED" : "";
        int w1 = mc.font.width(l1);
        int w2 = l2.isEmpty() ? 0 : mc.font.width(l2);
        int color = 0xFF000000 | (UiUtilsSettings.get().packetHudColor & 0xFFFFFF);
        drawHud(g, mc.font, l1, l2, w1, w2, color, sw, sh, position);
    }

    // Render path that accepts the runtime GuiGraphics instance without importing it
    public static void renderAny(Object graphics) {
        UiUtilsSettings.PacketHudPosition position = UiUtilsSettings.get().packetHudPosition;
        if (!position.isEnabled())
            return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null)
            return;

        int queued = com.ui_utils.uiutils.UiUtilsState.delayedUiPackets.size();
        String l1 = rateIn + " IN / " + rateOut + " OUT";
        String l2 = queued > 0 ? "    " + queued + " QUEUED" : "";

        int sw = mc.getWindow().getGuiScaledWidth();
        int sh = mc.getWindow().getGuiScaledHeight();
        Font font = mc.font;
        int w1 = font.width(l1);
        int w2 = l2.isEmpty() ? 0 : font.width(l2);

        int color = 0xFF000000 | (UiUtilsSettings.get().packetHudColor & 0xFFFFFF);
        try {
            // Try to call drawString(Font,String,int,int,int,boolean) reflectively on GuiGraphics
            Class<?> gg = Class.forName("net.minecraft.client.gui.GuiGraphics");
            if (gg.isInstance(graphics)) {
                java.lang.reflect.Method draw = gg.getMethod("drawString", Font.class, String.class, int.class, int.class, int.class, boolean.class);
                drawHud(graphics, draw, font, l1, l2, w1, w2, color, sw, sh, position);
                return;
            }
        } catch (Throwable ignored) {
            // Fall back silently
        }

        if (graphics instanceof GuiGraphicsExtractor ge) {
            drawHud(ge, font, l1, l2, w1, w2, color, sw, sh, position);
        }
    }

    private static void drawHud(GuiGraphicsExtractor g, Font font, String l1,
        String l2, int w1, int w2, int color, int sw, int sh,
        UiUtilsSettings.PacketHudPosition position) {
        int xLeft = 6;
        int xRight = sw - 6;
        boolean right = position == UiUtilsSettings.PacketHudPosition.TOP_RIGHT
            || position == UiUtilsSettings.PacketHudPosition.BOTTOM_RIGHT;
        boolean bottom = position == UiUtilsSettings.PacketHudPosition.BOTTOM_LEFT
            || position == UiUtilsSettings.PacketHudPosition.BOTTOM_RIGHT;
        int x1 = right ? xRight - w1 : xLeft;
        int x2 = right ? xRight - w2 : xLeft;
        int y1 = bottom && !l2.isEmpty() ? sh - 6 - 20 : (bottom ? sh - 6 - 10 : 6);
        int y2 = bottom && !l2.isEmpty() ? sh - 6 - 10 : 16;
        g.text(font, l1, x1, y1, color, false);
        if (!l2.isEmpty())
            g.text(font, l2, x2, y2, color, false);
    }

    private static void drawHud(Object graphics, java.lang.reflect.Method draw,
        Font font, String l1, String l2, int w1, int w2, int color, int sw,
        int sh, UiUtilsSettings.PacketHudPosition position) throws Exception {
        int xLeft = 6;
        int xRight = sw - 6;
        boolean right = position == UiUtilsSettings.PacketHudPosition.TOP_RIGHT
            || position == UiUtilsSettings.PacketHudPosition.BOTTOM_RIGHT;
        boolean bottom = position == UiUtilsSettings.PacketHudPosition.BOTTOM_LEFT
            || position == UiUtilsSettings.PacketHudPosition.BOTTOM_RIGHT;
        int x1 = right ? xRight - w1 : xLeft;
        int x2 = right ? xRight - w2 : xLeft;
        int y1 = bottom && !l2.isEmpty() ? sh - 6 - 20 : (bottom ? sh - 6 - 10 : 6);
        int y2 = bottom && !l2.isEmpty() ? sh - 6 - 10 : 16;
        draw.invoke(graphics, font, l1, x1, y1, color, false);
        if (!l2.isEmpty())
            draw.invoke(graphics, font, l2, x2, y2, color, false);
    }
}
