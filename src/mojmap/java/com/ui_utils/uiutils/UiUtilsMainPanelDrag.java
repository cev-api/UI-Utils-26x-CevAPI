package com.ui_utils.uiutils;

import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;

/** Drag handling for the main UI-Utils panel. */
public final class UiUtilsMainPanelDrag {
	private static final Map<Screen, Controller> CONTROLLERS = new WeakHashMap<>();

	private UiUtilsMainPanelDrag() {}

	public static void attach(Screen screen, List<AbstractWidget> widgets,
		UiUtils.UiWidgetLayout layout, int baseX, int baseY) {
		Controller controller = CONTROLLERS.get(screen);
		if(controller == null) {
			controller = new Controller(screen);
			CONTROLLERS.put(screen, controller);
		}
		controller.update(widgets, layout, baseX, baseY);
	}

	public static boolean mouseClicked(Screen screen, MouseButtonEvent event) {
		Controller controller = CONTROLLERS.get(screen);
		return controller != null && controller.mouseClicked(event);
	}

	public static boolean mouseDragged(Screen screen, MouseButtonEvent event,
		double dx, double dy) {
		Controller controller = CONTROLLERS.get(screen);
		return controller != null && controller.mouseDragged(event, dx, dy);
	}

	public static boolean mouseReleased(Screen screen) {
		Controller controller = CONTROLLERS.get(screen);
		return controller != null && controller.mouseReleased();
	}

	private static final class Controller {
		private final Screen screen;
		private List<AbstractWidget> widgets = List.of();
		private int x, y, width, height, headerHeight, baseX, baseY;
		private double lastMouseX, lastMouseY;
		private boolean dragging;

		private Controller(Screen screen) { this.screen = screen; }

		private void update(List<AbstractWidget> widgets,
			UiUtils.UiWidgetLayout layout, int baseX, int baseY) {
			this.widgets = widgets;
			x = layout.panelX(); y = layout.panelY();
			width = layout.panelWidth(); height = layout.panelHeight();
			headerHeight = layout.panelHeaderHeight();
			this.baseX = baseX; this.baseY = baseY;
			dragging = false;
		}

		private boolean mouseClicked(MouseButtonEvent event) {
			if(!isLeftButton(event) || !inDraggableHeader(event.x(), event.y())
				|| isOverPin(event.x(), event.y()))
				return false;
			beginDrag(event);
			return true;
		}

		private boolean isLeftButton(MouseButtonEvent event) {
			// 26.x exposes both GLFW's zero-based button and the event's one-based
			// button mask. Accept either representation of the primary mouse button.
			return event.buttonInfo().button() == McCompat.LEFT_BUTTON
				|| event.button() == 1;
		}

		private boolean inDraggableHeader(double mouseX, double mouseY) {
			return mouseX >= x && mouseX < x + width && mouseY >= y
				&& mouseY < y + Math.max(headerHeight, 22);
		}

		private void beginDrag(MouseButtonEvent event) {
			// A saved pin must not strand the menu off-screen: dragging its header
			// unlocks it as part of the same gesture. The three-line control remains
			// available to lock it again once positioned.
			if(UiUtilsSettings.get().mainUiPinned) {
				UiUtilsSettings.get().mainUiPinned = false;
				UiUtilsSettings.save();
			}
			dragging = true;
			lastMouseX = event.x();
			lastMouseY = event.y();
		}

		private boolean isOverPin(double mouseX, double mouseY) {
			for(AbstractWidget widget : widgets)
				if(widget instanceof com.ui_utils.uiutils.ui.UiPinToggle
					&& mouseX >= widget.getX() && mouseX < widget.getX() + widget.getWidth()
					&& mouseY >= widget.getY() && mouseY < widget.getY() + widget.getHeight())
					return true;
			return false;
		}

		private boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
			if(!dragging) {
				// If the container screen swallowed the initial click, its drag
				// callback still carries the original pointer and lets us recover.
				if(!isLeftButton(event) || !inDraggableHeader(event.x(), event.y())
					|| isOverPin(event.x(), event.y()))
					return false;
				beginDrag(event);
			}
			// Derive movement from the current pointer position. On some client
			// versions the supplied deltas are transformed for a scaled GUI, while
			// the widget coordinates remain in GUI pixels.
			double pointerX = event.x();
			double pointerY = event.y();
			int moveX = (int)Math.round(pointerX - lastMouseX);
			int moveY = (int)Math.round(pointerY - lastMouseY);
			if(moveX == 0 && moveY == 0 && (dx != 0 || dy != 0)) {
				moveX = (int)Math.round(dx);
				moveY = (int)Math.round(dy);
			}
			lastMouseX = pointerX;
			lastMouseY = pointerY;
			int minX = 4 - x, maxX = screen.width - 4 - (x + width);
			int minY = 4 - y, maxY = screen.height - 4 - (y + height);
			moveX = Math.max(minX, Math.min(Math.max(minX, maxX), moveX));
			moveY = Math.max(minY, Math.min(Math.max(minY, maxY), moveY));
			for(AbstractWidget widget : widgets) {
				widget.setX(widget.getX() + moveX);
				widget.setY(widget.getY() + moveY);
			}
			x += moveX; y += moveY; baseX += moveX; baseY += moveY;
			UiUtilsSettings.get().mainUiX = baseX;
			UiUtilsSettings.get().mainUiY = baseY;
			return true;
		}

		private boolean mouseReleased() {
			if(!dragging) return false;
			dragging = false;
			UiUtilsSettings.get().mainUiX = baseX;
			UiUtilsSettings.get().mainUiY = baseY;
			UiUtilsSettings.save();
			return true;
		}
	}
}
