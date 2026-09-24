package com.ui_utils.uiutils.ui;

/**
 * Implemented by UI-utils widgets that draw themselves in design units.
 * <p>
 * The modern screens scale the whole canvas at once, but the overlay panels place
 * widgets in raw GUI pixels, so they hand each widget the scale to draw itself
 * with. Keeping one scale for every widget is what stops labels, fields and
 * buttons from ending up at different sizes.
 */
public interface UiScalable {
	void applyUiScale(float scale);
}
