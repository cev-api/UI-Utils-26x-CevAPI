package com.ui_utils.uiutils;

import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenKeyboardEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;
import net.minecraft.client.gui.screens.Screen;

/**
 * Hosts the Fabricate Packet and GUI Tools panels on container and inventory
 * screens.
 * <p>
 * Uses Fabric's screen API rather than our own Screen mixin: the widget list it
 * exposes is backed by the screen's real renderable/narratable/child lists, and
 * the per-screen events give us render and input hooks without touching Screen's
 * class hierarchy. Screens that should not host the panels (chat, pause menu,
 * level loading during a portal transition, other mods' screens) are skipped
 * entirely, so nothing is drawn over them.
 */
public final class UiUtilsPanelHost {
	private UiUtilsPanelHost() {
	}

	public static void register() {
		ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) -> {
			// Fresh screen or resize: rebuild the panels, but only on screens that
			// are allowed to host them.
			UiUtilsPanels.onScreenInit();
			if (!UiUtilsPanels.isAllowedScreen(screen))
				return;
			try {
				attachTo(screen);
			} catch (Throwable t) {
				UiUtils.LOGGER.error("Could not attach the UI-Utils panels to {}",
					screen.getClass().getSimpleName(), t);
			}
		});
	}

	private static void attachTo(Screen screen) {
		UiUtilsPanels.attach(screen);

		ScreenEvents.afterBackground(screen).register((s, graphics, mouseX, mouseY,
			partialTicks) -> UiUtilsPanels.renderBackground(s, graphics));
		ScreenEvents.afterForeground(screen).register((s, graphics, mouseX, mouseY,
			partialTicks) -> UiUtilsPanels.renderForeground(s, graphics, mouseX, mouseY));
		ScreenEvents.afterTick(screen)
			.register(s -> UiUtilsPanels.onClientTick(UiUtilsPanels.minecraft()));

		// Fabric's allow* events follow "true = allow, false = cancel". Only a click
		// that lands on a panel drag bar is consumed; releases, drags and keys are
		// always allowed through so vanilla slot handling keeps working.
		ScreenMouseEvents.allowMouseClick(screen).register((s, click) -> !UiUtilsPanels
			.onMousePress(click.x(), click.y(), click.button()));
		ScreenMouseEvents.allowMouseRelease(screen).register((s, release) -> {
			UiUtilsPanels.onMouseRelease();
			return true;
		});
		ScreenMouseEvents.allowMouseDrag(screen).register((s, drag, draggedX, draggedY) -> {
			UiUtilsPanels.onMouseDrag(drag.x(), drag.y());
			return true;
		});
		ScreenKeyboardEvents.allowKeyPress(screen)
			.register((s, event) -> !UiUtilsPanels.onKey(event));
	}
}
