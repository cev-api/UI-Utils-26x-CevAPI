package com.ui_utils.uiutils;

import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenKeyboardEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import com.ui_utils.mixin.ui_utils.UiUtilsScreenAccessor;
import com.ui_utils.uiutils.ui.UiModernScreen;
import com.ui_utils.uiutils.ui.UiScalable;

/**
 * Hosts the Fabricate Packet and GUI Tools panels on container and inventory
 * screens.
 * <p>
 * Fabric screen events provide the final render pass and input hooks. Embedded
 * UI-Utils controls render above their host; floating panels are restricted to
 * container and inventory screens.
 */
public final class UiUtilsPanelHost {
	private static final Map<Screen, List<AbstractWidget>> foregroundWidgets = new WeakHashMap<>();

	/** Moves a native UI-Utils control out of the host's normal render pass. */
	public static void registerForegroundWidget(Screen screen, AbstractWidget widget) {
		foregroundWidgets.computeIfAbsent(screen, s -> new ArrayList<>()).add(widget);
		((UiUtilsScreenAccessor)screen).uiutils$getRenderables().remove(widget);
	}

	public static void renderMainWidgets(Screen screen, GuiGraphicsExtractor graphics,
		int mouseX, int mouseY) {
		if (!UiUtilsState.isUiEnabled())
			return;
		List<AbstractWidget> widgets = foregroundWidgets.get(screen);
		if (widgets == null || widgets.isEmpty())
			return;
		graphics.nextStratum();
		for (AbstractWidget widget : widgets)
			if (widget.visible)
				widget.extractRenderState(graphics, mouseX, mouseY, 0F);
	}

	private UiUtilsPanelHost() {
	}

	public static void register() {
		// A screen can initialise more than once per open: Fabric fires AFTER_INIT
		// from both Screen#init and Screen#resize, with the widget lists only
		// cleared once. Rebuild on the start of an initialisation and then attach
		// only once, or the follow-up event would add a second set of widgets that
		// stops following the panel once it is dragged.
		ScreenEvents.BEFORE_INIT
			.register((client, screen, scaledWidth, scaledHeight) -> {
				foregroundWidgets.remove(screen);
				UiUtilsPanels.onScreenInit();
			});
		ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) -> {
			if (!UiUtilsPanels.claimAttach())
				return;
			try {
				// Full UI-Utils screens own their layout; embedded controls need a late pass.
				if (!(screen instanceof UiModernScreen)) {
					List<AbstractWidget> widgets = foregroundWidgets.computeIfAbsent(screen, s -> new ArrayList<>());
					var renderables = ((UiUtilsScreenAccessor)screen).uiutils$getRenderables();
					for (var renderable : renderables)
						if (renderable instanceof AbstractWidget widget && widget instanceof UiScalable)
							widgets.add(widget);
					renderables.removeAll(widgets);
					foregroundWidgets.put(screen, widgets);
				}
				ScreenRenderCompat.registerForeground(screen);
				if (UiUtilsPanels.isAllowedScreen(screen))
					attachTo(screen);
			} catch (Throwable t) {
				UiUtils.LOGGER.error("Could not attach the UI-Utils panels to {}",
					screen.getClass().getSimpleName(), t);
			}
		});
	}

	private static void attachTo(Screen screen) {
		UiUtilsPanels.attach(screen);

		ScreenRenderCompat.registerBackground(screen);
		ScreenEvents.afterTick(screen)
			.register(s -> UiUtilsPanels.onClientTick(UiUtilsPanels.minecraft()));

		// Consume panel presses before inventory handling; route drags to the focused widget.
		ScreenMouseEvents.allowMouseClick(screen).register((s, click) -> !UiUtilsPanels
			.onMousePress(click));
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
