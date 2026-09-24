package com.ui_utils.uiutils.ui;

import com.ui_utils.uiutils.UiUtilsSettings;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.joml.Matrix3x2fStack;

/**
 * Compact dark palette and flat drawing primitives shared by every modern
 * UI-Utils screen. The look follows the Wurst7 "modern" ClickGUI: flat fills, one
 * pixel borders, a single accent colour and small rows, so a whole screen fits
 * inside a normally GUI-scaled window.
 */
public final class UiTheme {
	/** Row metrics. One row is one button or one setting. */
	public static final int ROW_HEIGHT = 20;
	public static final int ROW_HEIGHT_COMPACT = 15;
	public static final int GAP = 3;
	public static final int PAD = 7;
	public static final int HEADER_HEIGHT = 20;
	public static final int FOOTER_HEIGHT = 28;
	public static final int SCROLLBAR_WIDTH = 3;
	/** Kept for call sites that predate the flat style. */
	public static final int RADIUS = 0;

	/** The world stays visible behind the panel, so the scrim is light. */
	public static final int SCRIM = 0x60000000;
	public static final int WINDOW = 0xE6121216;
	public static final int SURFACE = 0xE6121216;
	public static final int SURFACE_HEADER = 0xF218181D;
	public static final int SURFACE_FOOTER = 0xF2141418;
	public static final int SURFACE_ROW = 0x1AFFFFFF;
	public static final int SURFACE_ROW_HOVER = 0x2EFFFFFF;
	public static final int SURFACE_ROW_ACTIVE = 0x554A90E2;
	public static final int SURFACE_SUNK = 0xB00C0C0F;
	/**
	 * Outline colour. Kept bright enough to read against both the panel and a
	 * control's own fill: a very low alpha made outlines look absent where a
	 * widget's fill is close to the panel background.
	 */
	public static final int BORDER = 0x4DFFFFFF;
	public static final int BORDER_SOFT = 0x1FFFFFFF;
	public static final int SHADOW = 0x80000000;
	public static final int ACCENT = 0xFF4A90E2;
	public static final int TEXT = 0xFFE8E8EC;
	public static final int TEXT_DIM = 0xFF9BA1A8;
	public static final int TEXT_MUTED = 0xFF6E747C;
	public static final int OK = 0xFF5BC46B;
	public static final int WARN = 0xFFE0A33B;
	public static final int DANGER = 0xFFD9534F;

	private UiTheme() {
	}

	/** Accent colour, taken from the user's configured button colour. */
	public static int accent() {
		return 0xFF000000 | (UiUtilsSettings.get().uiButtonColor & 0xFFFFFF);
	}

	/** Text colour used on top of accent-filled surfaces. */
	public static int accentText() {
		return 0xFF000000 | (UiUtilsSettings.get().uiButtonTextColor & 0xFFFFFF);
	}

	/**
	 * Manual UI scale as a factor, or 0 when the screen should pick the largest
	 * scale that still fits the window.
	 */
	public static float forcedScale() {
		int percent = UiUtilsSettings.get().uiScalePercent;
		return percent <= 0 ? 0F : percent / 100F;
	}

	/**
	 * The one UI scale used by everything drawn over a screen: the button panel,
	 * the overlay panels and every UI-Utils window. Deriving them all from the
	 * window size is what keeps them looking like parts of the same interface.
	 * A manual setting wins, but callers still clamp the result so it fits.
	 */
	public static float screenScale(int guiWidth, int guiHeight) {
		float forced = forcedScale();
		if (forced > 0F)
			return clampScale(forced);
		// Auto is the stable 100% baseline. Let each screen fit or scroll its own
		// content instead of making large displays inflate some UI layers more than others.
		return 1F;
	}

	/** Smallest scale the UI will use before text becomes unreadable. */
	public static final float MIN_SCALE = 0.4F;
	public static final float MAX_SCALE = 1.5F;

	public static float clampScale(float value) {
		return Math.max(MIN_SCALE, Math.min(MAX_SCALE, value));
	}

	/**
	 * Snaps a scale down to a step and never above {@code limit}, so a fitted
	 * scale can never grow into an overflow again.
	 */
	public static float snapScaleDown(float value, float limit) {
		float step = 0.25F;
		float snapped = (float)(Math.floor(value / step) * step);
		float cap = (float)(Math.floor(limit / step) * step);
		if (snapped > cap)
			snapped = cap;
		return clampScale(snapped);
	}

	/** Accent at a given strength, for tints that must not overpower the text. */
	public static int accentTint(int alpha) {
		return withAlpha(accent(), alpha);
	}

	// ------------------------------------------------------------------
	// Flat drawing primitives
	// ------------------------------------------------------------------

	/**
	 * Fills a rectangle. Panels are flat, so the corner radius is accepted only
	 * to let older call sites keep compiling.
	 */
	public static void fillRound(GuiGraphicsExtractor graphics, int x, int y,
		int w, int h, int radius, int color) {
		if (w <= 0 || h <= 0)
			return;
		graphics.fill(x, y, x + w, y + h, color);
	}

	public static void strokeRound(GuiGraphicsExtractor graphics, int x, int y,
		int w, int h, int radius, int color) {
		if (w <= 0 || h <= 0)
			return;
		graphics.outline(x, y, w, h, color);
	}

	public static void hLine(GuiGraphicsExtractor graphics, int x, int y, int width,
		int color) {
		graphics.fill(x, y, x + width, y + 1, color);
	}

	public static void vLine(GuiGraphicsExtractor graphics, int x, int y,
		int height, int color) {
		graphics.fill(x, y, x + 1, y + height, color);
	}

	/** One pixel border inside the given rectangle. */
	public static void border(GuiGraphicsExtractor graphics, int x, int y, int w,
		int h, int color) {
		graphics.fill(x, y, x + w, y + 1, color);
		graphics.fill(x, y + h - 1, x + w, y + h, color);
		graphics.fill(x, y + 1, x + 1, y + h - 1, color);
		graphics.fill(x + w - 1, y + 1, x + w, y + h - 1, color);
	}

	/** Switch shaped like the Wurst7 modern toggle: track plus a small knob. */
	public static void switchPill(GuiGraphicsExtractor graphics, int x, int y,
		int width, int height, boolean on, int onColor, int offColor,
		int knobColor) {
		graphics.fill(x, y, x + width, y + height, on ? onColor : offColor);
		int knobWidth = Math.max(4, width / 4);
		int knobInset = Math.max(1, height / 4);
		int knobHeight = Math.max(3, height - knobInset * 2);
		int knobX = on ? x + width - knobWidth - knobInset : x + knobInset;
		graphics.fill(knobX, y + knobInset, knobX + knobWidth,
			y + knobInset + knobHeight, knobColor);
	}

	/** The "off" track colour, a dimmed version of the panel background. */
	public static int offTrack() {
		return 0xFF2A2A30;
	}

	// ------------------------------------------------------------------
	// Text helpers
	// ------------------------------------------------------------------

	public static void text(GuiGraphicsExtractor graphics, Font font, String value,
		int x, int y, int color) {
		graphics.text(font, value, x, y, color, false);
	}

	public static void textCentered(GuiGraphicsExtractor graphics, Font font,
		String value, int centerX, int y, int color) {
		graphics.centeredText(font, value, centerX, y, color);
	}

	public static void textRight(GuiGraphicsExtractor graphics, Font font,
		String value, int rightX, int y, int color) {
		graphics.text(font, value, rightX - font.width(value), y, color, false);
	}

	/**
	 * Draws text at a screen position with the given design-unit scale, scaling it
	 * about that position.
	 * <p>
	 * Callers must use this rather than pushing a scale themselves and drawing at
	 * (0, 0): a scale of exactly 1 pushes nothing, which would leave the text at the
	 * screen origin instead of at its own position.
	 */
	public static void textScaled(GuiGraphicsExtractor graphics, Font font,
		String value, int x, int y, float scale, int color) {
		if (Math.abs(scale - 1F) < 0.001F) {
			graphics.text(font, value, x, y, color, false);
			return;
		}
		Matrix3x2fStack pose = graphics.pose();
		pose.pushMatrix();
		pose.translate(x, y);
		pose.scale(scale, scale);
		graphics.text(font, value, 0, 0, color, false);
		pose.popMatrix();
	}

	/** Baseline y that vertically centres one line of text inside a row. */
	public static int textY(Font font, int rowY, int rowHeight) {
		return rowY + (rowHeight - font.lineHeight + 1) / 2;
	}

	public static String ellipsize(Font font, String value, int maxWidth) {
		if (value == null)
			return "";
		if (maxWidth <= 0)
			return "";
		if (font.width(value) <= maxWidth)
			return value;
		String suffix = font.width("...") <= maxWidth ? "..." : "";
		return font.plainSubstrByWidth(value, Math.max(1, maxWidth - font.width(suffix)))
			+ suffix;
	}

	// ------------------------------------------------------------------
	// Colour helpers
	// ------------------------------------------------------------------

	public static int mix(int from, int to, float t) {
		float f = Math.max(0F, Math.min(1F, t));
		int a = channel((from >>> 24) & 0xFF, (to >>> 24) & 0xFF, f);
		int r = channel((from >> 16) & 0xFF, (to >> 16) & 0xFF, f);
		int g = channel((from >> 8) & 0xFF, (to >> 8) & 0xFF, f);
		int b = channel(from & 0xFF, to & 0xFF, f);
		return (a << 24) | (r << 16) | (g << 8) | b;
	}

	public static int scaleRgb(int argb, float factor) {
		int r = clamp((int)(((argb >> 16) & 0xFF) * factor));
		int g = clamp((int)(((argb >> 8) & 0xFF) * factor));
		int b = clamp((int)((argb & 0xFF) * factor));
		return (argb & 0xFF000000) | (r << 16) | (g << 8) | b;
	}

	public static int withAlpha(int argb, int alpha) {
		return (clamp(alpha) << 24) | (argb & 0xFFFFFF);
	}

	/** Colour for a state label such as ON/OFF, ALLOW/DROP or True/False. */
	public static int stateColor(boolean enabled) {
		return enabled ? OK : TEXT_MUTED;
	}

	/**
	 * Pushes a scale transform around a widget that draws itself in design units
	 * while it is positioned in raw GUI pixels, which is how the overlay panels
	 * place their buttons. Returns whether the caller must pop the pose.
	 */
	public static boolean pushWidgetScale(GuiGraphicsExtractor graphics, int x,
		int y, float scale) {
		if (scale <= 0F || Math.abs(scale - 1F) < 0.001F)
			return false;
		Matrix3x2fStack pose = graphics.pose();
		pose.pushMatrix();
		pose.translate(x, y);
		pose.scale(scale, scale);
		return true;
	}

	public static void popWidgetScale(GuiGraphicsExtractor graphics,
		boolean pushed) {
		if (pushed)
			graphics.pose().popMatrix();
	}

	/** Local-space width for a widget drawing through {@link #pushWidgetScale}. */
	public static int localSize(int size, float scale, boolean pushed) {
		return pushed ? Math.max(1, Math.round(size / scale)) : size;
	}

	private static int channel(int from, int to, float t) {
		return clamp(Math.round(from + (to - from) * t));
	}

	private static int clamp(int value) {
		return Math.max(0, Math.min(255, value));
	}
}
