package com.ui_utils.uiutils;

import com.ui_utils.uiutils.macro.UiUtilsMacroActionType;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

public final class UiUtilsMacroTypePickerScreen extends Screen {
    public enum Mode { ACTION, CONDITION }

    private final UiUtilsMacrosScreen parent;
    private final Mode mode;
    private int rowOffset = 0;
    private int maxOffset = 0;
    private int viewportTop;
    private int viewportBottom;
    private int viewportRows;
    private ScrollbarMetrics lastScrollbar = ScrollbarMetrics.none();
    private boolean draggingScrollbar;
    private int scrollbarGrabOffset;

    public UiUtilsMacroTypePickerScreen(UiUtilsMacrosScreen parent, Mode mode) {
        super(Component.literal(mode == Mode.ACTION ? "Add Action" : "Add Condition"));
        this.parent = parent;
        this.mode = mode;
    }

	@Override
	protected void init() {
		clearWidgets();
		int panelWidth = Math.min(455, Math.max(250, this.width - 32));
		int left = (this.width - panelWidth) / 2;
		int top = Math.max(8, (this.height - Math.min(this.height - 16, 360)) / 2);
		int row = 16;
		int gap = 3;
		boolean stacked = panelWidth < 360;
		int half = stacked ? panelWidth : (panelWidth - gap) / 2;
		int contentTop = top + row + 8;

		List<RowEntry> rows = flattenRows(mode == Mode.ACTION ? actionSections() : conditionSections());
		viewportRows = Math.min(18, Math.max(7, (this.height - contentTop - 52) / (row + gap)));
		maxOffset = Math.max(0, rows.size() - viewportRows);
		if (rowOffset > maxOffset) rowOffset = maxOffset;

        viewportTop = contentTop;
        int drawY = contentTop;
        for (int i = rowOffset; i < rows.size() && i < rowOffset + viewportRows; i++) {
            RowEntry entry = rows.get(i);
			if (entry.header != null) {
				addRenderableWidget(new UiUtilsSectionLabel(left, drawY, panelWidth, entry.header));
			} else {
				UiUtilsMacroActionType a = entry.left;
				addRenderableWidget(UiUtils.styledButton(label(a), b -> {
					parent.addStepFromPicker(a);
				}, left, drawY, half, row));
				if (entry.right != null) {
					UiUtilsMacroActionType bType = entry.right;
					addRenderableWidget(UiUtils.styledButton(label(bType), b -> {
						parent.addStepFromPicker(bType);
					}, stacked ? left : left + half + gap,
						stacked ? drawY + row + gap : drawY, half, row));
				}
			}
			drawY += row + gap;
			if (stacked && entry.header == null && entry.right != null)
				drawY += row + gap;
		}
		viewportBottom = drawY - gap;
		lastScrollbar = computeScrollbar(left + panelWidth + 8, viewportTop, viewportBottom,
			rows.size(), viewportRows, rowOffset);

		int footerY = drawY + 6;
		addRenderableWidget(UiUtils.styledButton("Cancel", b -> McCompat.setScreen(minecraft, parent), left, footerY, panelWidth, row));
	}

    private static String label(UiUtilsMacroActionType t) {
        String s = t.name().replace('_', ' ').toLowerCase(Locale.ROOT);
        String[] p = s.split(" ");
        StringBuilder out = new StringBuilder();
        for (String part : p) {
            if (part.isEmpty()) continue;
            if (!out.isEmpty()) out.append(' ');
            out.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        }
        return out.toString();
    }

    private static List<RowEntry> flattenRows(List<PickerSection> sections) {
        List<RowEntry> rows = new ArrayList<>();
        for (PickerSection section : sections) {
            rows.add(RowEntry.header(section.title));
            List<UiUtilsMacroActionType> copy = new ArrayList<>(section.types);
            int i = 0;
            while (i < copy.size()) {
                UiUtilsMacroActionType left = copy.get(i++);
                UiUtilsMacroActionType right = i < copy.size() ? copy.get(i++) : null;
                rows.add(RowEntry.pair(left, right));
            }
        }
        return rows;
    }

    private static List<PickerSection> actionSections() {
        return List.of(
            new PickerSection("Flow", List.of(
                UiUtilsMacroActionType.SEND_CHAT, UiUtilsMacroActionType.DELAY, UiUtilsMacroActionType.REPEAT,
                UiUtilsMacroActionType.STOP_MACRO, UiUtilsMacroActionType.SEND_TOGGLE, UiUtilsMacroActionType.DELAY_PACKETS,
                UiUtilsMacroActionType.SAVE_GUI, UiUtilsMacroActionType.RESTORE_GUI
            )),
            new PickerSection("Movement", List.of(
                UiUtilsMacroActionType.ROTATE, UiUtilsMacroActionType.LOOK_AT_BLOCK, UiUtilsMacroActionType.SNEAK,
                UiUtilsMacroActionType.JUMP, UiUtilsMacroActionType.SPRINT, UiUtilsMacroActionType.MOVE
            )),
            new PickerSection("Inventory", List.of(
                UiUtilsMacroActionType.ITEM, UiUtilsMacroActionType.USE_ITEM, UiUtilsMacroActionType.INVENTORY,
                UiUtilsMacroActionType.SELECT_SLOT, UiUtilsMacroActionType.XCARRY, UiUtilsMacroActionType.DROP,
                UiUtilsMacroActionType.SWAP_SLOTS, UiUtilsMacroActionType.OPEN_CONTAINER, UiUtilsMacroActionType.STORE_ITEM,
                UiUtilsMacroActionType.INVENTORY_AUDIT, UiUtilsMacroActionType.CRAFT, UiUtilsMacroActionType.CLICK
            )),
            new PickerSection("Network", List.of(
                UiUtilsMacroActionType.PACKET, UiUtilsMacroActionType.PAYLOAD, UiUtilsMacroActionType.CLOSE_GUI,
                UiUtilsMacroActionType.DESYNC, UiUtilsMacroActionType.NBT_BOOK, UiUtilsMacroActionType.DISCONNECT
            )),
            new PickerSection("Automation", List.of(
                UiUtilsMacroActionType.PAY, UiUtilsMacroActionType.MINE, UiUtilsMacroActionType.TOGGLE_MODULE,
                UiUtilsMacroActionType.SEND_PACKET
            ))
        );
    }

    private static List<PickerSection> conditionSections() {
        return List.of(
            new PickerSection("Player", List.of(
                UiUtilsMacroActionType.WAIT_HEALTH, UiUtilsMacroActionType.WAIT_COOLDOWN,
                UiUtilsMacroActionType.WAIT_ITEM, UiUtilsMacroActionType.WAIT_SLOT_CHANGE
            )),
            new PickerSection("World", List.of(
                UiUtilsMacroActionType.WAIT_POS, UiUtilsMacroActionType.WAIT_BLOCK,
                UiUtilsMacroActionType.WAIT_ENTITY, UiUtilsMacroActionType.WAIT_SOUND
            )),
            new PickerSection("Events", List.of(
                UiUtilsMacroActionType.WAIT_GUI, UiUtilsMacroActionType.WAIT_CHAT, UiUtilsMacroActionType.WAIT_LAN_STEP
            )),
            new PickerSection("Sync", List.of(
                UiUtilsMacroActionType.WAIT_PACKET, UiUtilsMacroActionType.TICK_SYNC,
                UiUtilsMacroActionType.REVISION_SYNC, UiUtilsMacroActionType.SERVER_TICK_SYNC
            ))
        );
    }

    private record PickerSection(String title, List<UiUtilsMacroActionType> types) {}
    private record RowEntry(String header, UiUtilsMacroActionType left, UiUtilsMacroActionType right) {
        private static RowEntry header(String title) { return new RowEntry(title, null, null); }
        private static RowEntry pair(UiUtilsMacroActionType left, UiUtilsMacroActionType right) { return new RowEntry(null, left, right); }
    }

    private static final class UiUtilsSectionLabel extends AbstractWidget {
		private UiUtilsSectionLabel(int x, int y, int width, String title) {
			super(x, y, width, 20, Component.literal(title));
			this.active = false;
		}

        @Override
        protected void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTicks) {
            graphics.text(Minecraft.getInstance().font, getMessage(), getX(), getY() + 6, 0xFFFFD27A, false);
        }

        @Override
        protected void updateWidgetNarration(NarrationElementOutput narration) {}
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTicks) {
		super.extractRenderState(graphics, mouseX, mouseY, partialTicks);
		int panelWidth = Math.min(455, Math.max(250, this.width - 32));
		int left = (this.width - panelWidth) / 2;
		int top = Math.max(8, (this.height - Math.min(this.height - 16, 360)) / 2);
		String head = mode == Mode.ACTION ? "Add Action" : "Add Conditional";
		graphics.text(this.font, head, left, top + 6, 0xFFE6EEF7, false);
		graphics.text(this.font, "Scroll " + (rowOffset + 1) + "/" + (maxOffset + 1),
			left + Math.min(panelWidth - 86, 124), top + 6, 0xFFC6D6E8, false);
		renderScrollbar(graphics, lastScrollbar);
	}

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (mouseY >= viewportTop && mouseY <= viewportBottom) {
            if (scrollY < 0) rowOffset = Math.min(maxOffset, rowOffset + 1);
            else if (scrollY > 0) rowOffset = Math.max(0, rowOffset - 1);
            init();
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent context, boolean doubleClick) {
        if (context.button() == 0 && lastScrollbar.hasScroll && lastScrollbar.contains(context.x(), context.y())) {
            if (context.y() >= lastScrollbar.thumbY && context.y() <= lastScrollbar.thumbY + lastScrollbar.thumbH) {
                draggingScrollbar = true;
                scrollbarGrabOffset = (int)Math.max(0, Math.round(context.y()) - lastScrollbar.thumbY);
            } else {
                jumpScrollToMouse((int)Math.round(context.y()), 0);
                init();
            }
            return true;
        }
        return super.mouseClicked(context, doubleClick);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent context, double dragX, double dragY) {
        if (draggingScrollbar && context.button() == 0 && lastScrollbar.hasScroll) {
            jumpScrollToMouse((int)Math.round(context.y()), scrollbarGrabOffset);
            init();
            return true;
        }
        return super.mouseDragged(context, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent context) {
        if (context.button() == 0 && draggingScrollbar) {
            draggingScrollbar = false;
            return true;
        }
        return super.mouseReleased(context);
    }

    private void jumpScrollToMouse(int mouseY, int grabOffset) {
        if (!lastScrollbar.hasScroll) return;
        int maxScroll = Math.max(1, lastScrollbar.totalRows - lastScrollbar.visibleRows);
        int travel = Math.max(1, lastScrollbar.trackBottom - lastScrollbar.trackTop - lastScrollbar.thumbH);
        int thumbTop = Math.max(lastScrollbar.trackTop,
            Math.min(lastScrollbar.trackBottom - lastScrollbar.thumbH, mouseY - grabOffset));
        double ratio = (thumbTop - lastScrollbar.trackTop) / (double)travel;
        rowOffset = Math.max(0, Math.min(maxScroll, (int)Math.round(ratio * maxScroll)));
    }

    private static ScrollbarMetrics computeScrollbar(int x, int top, int bottom, int totalRows,
        int visibleRows, int scroll) {
        int trackH = Math.max(1, bottom - top);
        if (totalRows <= visibleRows)
            return new ScrollbarMetrics(x, top, bottom, top, trackH, false, totalRows, visibleRows);
        double ratio = visibleRows / (double)Math.max(1, totalRows);
        int thumbH = Math.max(14, (int)Math.round(trackH * ratio));
        int maxScroll = Math.max(1, totalRows - visibleRows);
        int travel = Math.max(1, trackH - thumbH);
        int thumbY = top + (int)Math.round((Math.max(0, Math.min(scroll, maxScroll)) / (double)maxScroll) * travel);
        return new ScrollbarMetrics(x, top, bottom, thumbY, thumbH, true, totalRows, visibleRows);
    }

    private void renderScrollbar(GuiGraphicsExtractor graphics, ScrollbarMetrics m) {
        graphics.fill(m.x, m.trackTop, m.x + 4, m.trackBottom, 0x99303030);
        if (m.hasScroll)
            graphics.fill(m.x, m.thumbY, m.x + 4, m.thumbY + m.thumbH,
                draggingScrollbar ? 0xFFFFFFFF : 0xFFCFCFCF);
    }

    private record ScrollbarMetrics(int x, int trackTop, int trackBottom, int thumbY,
        int thumbH, boolean hasScroll, int totalRows, int visibleRows) {
        private static ScrollbarMetrics none() {
            return new ScrollbarMetrics(0, 0, 0, 0, 0, false, 0, 0);
        }

        private boolean contains(double mx, double my) {
            return mx >= x && mx <= x + 4 && my >= trackTop && my <= trackBottom;
        }
    }
}
