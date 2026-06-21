package com.ui_utils.uiutils;

import com.mojang.blaze3d.platform.InputConstants;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;

public final class UiUtilsKeybindsScreen extends Screen {
	private static final int VISIBLE_ROWS = 12;
	private static final int SCROLLBAR_WIDTH = 4;
	private final Screen parent;
	private String waitingForAction;
	private int offset;
	private boolean draggingScrollbar;
	private int scrollbarGrabOffset;
	private ScrollbarMetrics lastScrollbar = ScrollbarMetrics.none();

	public UiUtilsKeybindsScreen(Screen parent) {
		super(Component.literal("UI-Utils Keybinds"));
		this.parent = parent;
	}

	@Override
	protected void init() {
		List<UiUtils.KeybindAction> actions = UiUtils.keybindActions();
		int width = 420;
		int left = this.width / 2 - width / 2;
		int listWidth = width - SCROLLBAR_WIDTH - 4;
		int scrollbarX = left + listWidth + 4;
		int row = 20;
		int gap = 4;
		int y = Math.max(12, this.height / 2 - 132);
		int top = y;
		int visibleRows = VISIBLE_ROWS;
		int maxOffset = Math.max(0, actions.size() - visibleRows);
		if(offset > maxOffset)
			offset = maxOffset;
		int start = offset;
		int end = Math.min(actions.size(), start + visibleRows);

		for(int i = start; i < end; i++) {
			UiUtils.KeybindAction action = actions.get(i);
			addRenderableWidget(UiUtils.styledButton(labelFor(action), b -> {
				waitingForAction = action.id();
				rebuildWidgets();
			}, left, y, listWidth, row));
			y += row + gap;
		}

		y += 8;

		addRenderableWidget(UiUtils.styledButton("Back",
			b -> McCompat.setScreen(this.minecraft, parent), left, y, listWidth, row));
		lastScrollbar = computeScrollbar(scrollbarX, top,
			top + visibleRows * (row + gap) - gap, actions.size(), visibleRows, offset);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		List<UiUtils.KeybindAction> actions = UiUtils.keybindActions();
		int width = 420;
		int left = this.width / 2 - width / 2;
		int row = 20;
		int gap = 4;
		int top = Math.max(12, this.height / 2 - 132);
		int bottom = top + VISIBLE_ROWS * (row + gap) - gap;
		if(mouseX < left || mouseX > left + width || mouseY < top || mouseY > bottom)
			return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
		int maxOffset = Math.max(0, actions.size() - VISIBLE_ROWS);
		if(scrollY < 0)
			offset = Math.min(maxOffset, offset + 1);
		else if(scrollY > 0)
			offset = Math.max(0, offset - 1);
		rebuildWidgets();
		return true;
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent context, boolean doubleClick) {
		if(context.button() == 0 && lastScrollbar.hasScroll && lastScrollbar.contains(context.x(), context.y())) {
			if(context.y() >= lastScrollbar.thumbY && context.y() <= lastScrollbar.thumbY + lastScrollbar.thumbH) {
				draggingScrollbar = true;
				scrollbarGrabOffset = (int)Math.max(0, Math.round(context.y()) - lastScrollbar.thumbY);
			}else {
				jumpScrollToMouse((int)Math.round(context.y()), 0);
				rebuildWidgets();
			}
			return true;
		}
		return super.mouseClicked(context, doubleClick);
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent context, double dragX, double dragY) {
		if(draggingScrollbar && context.button() == 0 && lastScrollbar.hasScroll) {
			jumpScrollToMouse((int)Math.round(context.y()), scrollbarGrabOffset);
			rebuildWidgets();
			return true;
		}
		return super.mouseDragged(context, dragX, dragY);
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent context) {
		if(context.button() == 0 && draggingScrollbar) {
			draggingScrollbar = false;
			return true;
		}
		return super.mouseReleased(context);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX,
		int mouseY, float partialTicks) {
		super.extractRenderState(graphics, mouseX, mouseY, partialTicks);
		renderScrollbar(graphics, lastScrollbar);
		if(waitingForAction != null)
			graphics.centeredText(this.font,
				Component.literal("Press a key, Backspace clears, Esc cancels"),
				this.width / 2, Math.max(6, this.height / 2 - 154),
				0xFFFFFFFF);
	}

	@Override
	public boolean keyPressed(KeyEvent keyEvent) {
		if(waitingForAction == null)
			return super.keyPressed(keyEvent);
		if(keyEvent.isEscape()) {
			waitingForAction = null;
			rebuildWidgets();
			return true;
		}
		if(keyEvent.key() == org.lwjgl.glfw.GLFW.GLFW_KEY_BACKSPACE
			|| keyEvent.key() == org.lwjgl.glfw.GLFW.GLFW_KEY_DELETE) {
			UiUtils.setKeybind(waitingForAction, "");
		}else {
			InputConstants.Key key = InputConstants.getKey(keyEvent);
			UiUtils.setKeybind(waitingForAction, key.getName());
		}
		waitingForAction = null;
		rebuildWidgets();
		return true;
	}

	@Override
	public void onClose() {
		McCompat.setScreen(this.minecraft, parent);
	}

	private String labelFor(UiUtils.KeybindAction action) {
		if(action.id().equals(waitingForAction))
			return action.label() + ": press key...";
		String key = UiUtils.getKeybind(action.id(), action.defaultKey());
		return action.label() + ": " + formatKey(key);
	}

	private String formatKey(String key) {
		if(key == null || key.isBlank())
			return "UNBOUND";
		int dot = key.lastIndexOf('.');
		String part = dot >= 0 && dot + 1 < key.length()
			? key.substring(dot + 1) : key;
		return part.toUpperCase(Locale.ROOT);
	}

	private void jumpScrollToMouse(int mouseY, int grabOffset) {
		if(!lastScrollbar.hasScroll)
			return;
		int maxScroll = Math.max(1, lastScrollbar.totalRows - lastScrollbar.visibleRows);
		int travel = Math.max(1, lastScrollbar.trackBottom - lastScrollbar.trackTop - lastScrollbar.thumbH);
		int thumbTop = Math.max(lastScrollbar.trackTop,
			Math.min(lastScrollbar.trackBottom - lastScrollbar.thumbH, mouseY - grabOffset));
		double ratio = (thumbTop - lastScrollbar.trackTop) / (double)travel;
		offset = Math.max(0, Math.min(maxScroll, (int)Math.round(ratio * maxScroll)));
	}

	private static ScrollbarMetrics computeScrollbar(int x, int top, int bottom, int totalRows,
		int visibleRows, int scroll) {
		int trackH = Math.max(1, bottom - top);
		if(totalRows <= visibleRows)
			return new ScrollbarMetrics(x, top, bottom, top, trackH, false, totalRows, visibleRows);
		double ratio = visibleRows / (double)Math.max(1, totalRows);
		int thumbH = Math.max(12, (int)Math.round(trackH * ratio));
		int maxScroll = Math.max(1, totalRows - visibleRows);
		int travel = Math.max(1, trackH - thumbH);
		int thumbY = top + (int)Math.round((Math.max(0, Math.min(scroll, maxScroll)) / (double)maxScroll) * travel);
		return new ScrollbarMetrics(x, top, bottom, thumbY, thumbH, true, totalRows, visibleRows);
	}

	private void renderScrollbar(GuiGraphicsExtractor graphics, ScrollbarMetrics m) {
		graphics.fill(m.x, m.trackTop, m.x + SCROLLBAR_WIDTH, m.trackBottom, 0x99303030);
		if(m.hasScroll)
			graphics.fill(m.x, m.thumbY, m.x + SCROLLBAR_WIDTH, m.thumbY + m.thumbH,
				draggingScrollbar ? 0xFFFFFFFF : 0xFFCFCFCF);
	}

	private record ScrollbarMetrics(int x, int trackTop, int trackBottom, int thumbY,
		int thumbH, boolean hasScroll, int totalRows, int visibleRows) {
		private static ScrollbarMetrics none() {
			return new ScrollbarMetrics(0, 0, 0, 0, 0, false, 0, 0);
		}

		private boolean contains(double mx, double my) {
			return mx >= x && mx <= x + SCROLLBAR_WIDTH && my >= trackTop && my <= trackBottom;
		}
	}
}
