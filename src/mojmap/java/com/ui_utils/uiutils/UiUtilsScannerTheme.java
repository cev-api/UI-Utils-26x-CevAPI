package com.ui_utils.uiutils;

/** Standalone UI-Utils uses its configured palette; Wurst supplies ClickGUI colors. */
public final class UiUtilsScannerTheme {
 private UiUtilsScannerTheme() {}
 public static int color(String role, int fallback) { return fallback; }
}
