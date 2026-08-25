package com.ui_utils.uiutils;

import com.ui_utils.uiutils.macro.UiUtilsMacro;
import com.ui_utils.uiutils.macro.UiUtilsMacroExecutor;
import com.ui_utils.uiutils.macro.UiUtilsMacroIo;
import com.ui_utils.uiutils.macro.UiUtilsMacroManager;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import org.lwjgl.util.tinyfd.TinyFileDialogs;

public final class UiUtilsMacroLibraryScreen extends Screen {
    private final Screen parent;
    private EditBox searchField;
    private EditBox importField;
    private EditBox exportField;
    private final List<MacroRow> rows = new ArrayList<>();
    private final List<UiUtilsMacro> filtered = new ArrayList<>();
    private int selected = -1;
    private int offset = 0;
    private String status = "";
    private boolean draggingScrollbar;
    private int scrollbarGrabOffset;
    private ScrollbarMetrics lastScrollbar = ScrollbarMetrics.none();

    public UiUtilsMacroLibraryScreen(Screen parent) {
        super(Component.literal("Macro Library"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        clearWidgets();
        rows.clear();
        ensureDefaultDirectories();
        int width = panelWidth();
        int left = (this.width - width) / 2;
        int rowH = rowHeight();
        int gap = rowGap();
        int visibleRows = visibleRows(rowH, gap);
        int top = contentTop(rowH, gap, visibleRows);
        boolean stacked = width < 420;
        int half = (width - gap) / 2;

        searchField = new EditBox(this.font, left, top, width, rowH, Component.literal("Search"));
        searchField.setHint(Component.literal("Search macros..."));
        addRenderableWidget(searchField);
        top += rowH + gap;

        addRenderableWidget(UiUtils.styledButton("Create New", b -> {
            McCompat.setScreen(minecraft, new UiUtilsMacrosScreen(this));
        }, left, top, stacked ? half : 110, rowH));
        addRenderableWidget(UiUtils.styledButton("Edit Selected", b -> openSelected(),
            stacked ? left + half + gap : left + 114, top, stacked ? half : 110, rowH));
        top += rowH + gap;
        addRenderableWidget(UiUtils.styledButton("Run Selected", b -> runSelected(),
            left, top, stacked ? half : 110, rowH));
        addRenderableWidget(UiUtils.styledButton("Stop", b -> UiUtilsMacroExecutor.stop(),
            stacked ? left + half + gap : left + 342, top, stacked ? half : 118, rowH));
        top += rowH + gap;

        for (int i = 0; i < visibleRows; i++) {
            MacroRow r = addRenderableWidget(new MacroRow(left, top + i * (rowH + gap), width, rowH, i));
            rows.add(r);
        }

        int ioTop = top + (rowH + gap) * visibleRows + gap;
        int fieldWidth = stacked ? width : Math.max(180, width - 160);
        importField = new EditBox(this.font, left, ioTop, fieldWidth, rowH, Component.literal("Import path"));
        importField.setMaxLength(1024);
        importField.setHint(Component.literal("Import path (NBT)"));
        importField.setValue(defaultImportDirectory().toString());
        addRenderableWidget(importField);
        addRenderableWidget(UiUtils.styledButton("...", b -> openFilePicker(importField, true),
            stacked ? left : left + fieldWidth + 4, ioTop, stacked ? half : 28, rowH));
        addRenderableWidget(UiUtils.styledButton("Import", b -> {
            status = UiUtilsMacroIo.importMacro(importField.getValue().trim(), "");
            refreshRows();
        }, stacked ? left + half + gap : left + fieldWidth + 36, ioTop,
            stacked ? half : width - fieldWidth - 36, rowH));

        exportField = new EditBox(this.font, left, ioTop + rowH + gap, fieldWidth, rowH, Component.literal("Export folder"));
        exportField.setMaxLength(1024);
        exportField.setHint(Component.literal("Export folder"));
        exportField.setValue(defaultExportDirectory().toString());
        addRenderableWidget(exportField);
        addRenderableWidget(UiUtils.styledButton("...", b -> openFilePicker(exportField, false),
            stacked ? left : left + fieldWidth + 4, ioTop + rowH + gap, stacked ? half : 28, rowH));
        addRenderableWidget(UiUtils.styledButton("Export", b -> {
            UiUtilsMacro m = selectedMacro();
            status = m == null ? "No macro selected." : UiUtilsMacroIo.exportMacro(m.name, exportField.getValue().trim());
        }, stacked ? left + half + gap : left + fieldWidth + 36, ioTop + rowH + gap,
            stacked ? half : width - fieldWidth - 36, rowH));

        addRenderableWidget(UiUtils.styledButton("Delete Selected", b -> {
            UiUtilsMacro m = selectedMacro();
            if (m == null) return;
            UiUtilsMacroManager.get().remove(m.name);
            selected = -1;
            refreshRows();
        }, left, ioTop + (rowH + gap) * 2, stacked ? half : 220, rowH));
        addRenderableWidget(UiUtils.styledButton("Done", b -> McCompat.setScreen(minecraft, parent),
            stacked ? left + half + gap : left + 224, ioTop + (rowH + gap) * 2,
            stacked ? half : width - 224, rowH));

        refreshRows();
    }

    private void runSelected() {
        UiUtilsMacro m = selectedMacro();
        if (m != null) UiUtilsMacroManager.get().execute(m.name);
    }

    private void openSelected() {
        UiUtilsMacro m = selectedMacro();
        if (m != null) McCompat.setScreen(minecraft, new UiUtilsMacrosScreen(this, m.name));
    }

    private UiUtilsMacro selectedMacro() {
        return (selected >= 0 && selected < filtered.size()) ? filtered.get(selected) : null;
    }

    private void refreshRows() {
        String q = searchField == null ? "" : searchField.getValue().trim().toLowerCase(Locale.ROOT);
        filtered.clear();
        for (UiUtilsMacro m : UiUtilsMacroManager.get().getAll()) {
            if (q.isBlank() || m.name.toLowerCase(Locale.ROOT).contains(q)) filtered.add(m);
        }
        int maxOffset = Math.max(0, filtered.size() - rows.size());
        if (offset > maxOffset) offset = maxOffset;
        if (selected >= filtered.size()) selected = filtered.isEmpty() ? -1 : 0;
        for (int i = 0; i < rows.size(); i++) {
            rows.get(i).setMessage(Component.literal(rowText(i)));
        }
    }

    private String rowText(int rowIndex) {
        int index = offset + rowIndex;
        if (index < 0 || index >= filtered.size()) return "";
        UiUtilsMacro m = filtered.get(index);
        String marker = index == selected ? "> " : "";
        return marker + m.name + " (" + m.actions.size() + " steps)";
    }

    @Override
    public void tick() {
        super.tick();
        refreshRows();
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTicks) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTicks);
        int width = panelWidth();
        int left = (this.width - width) / 2;
        int rowH = rowHeight();
        int gap = rowGap();
        int visibleRows = visibleRows(rowH, gap);
        int top = contentTop(rowH, gap, visibleRows) - this.font.lineHeight - 3;
        String running = UiUtilsMacroExecutor.isRunning() ? UiUtilsMacroExecutor.currentName() : "none";
        graphics.text(this.font, "Macro Library [" + filtered.size() + "]", left, top, 0xFFE6EEF7, false);
        graphics.text(this.font, "Running: " + running, left + Math.min(175, width / 2), top, 0xFFC6D6E8, false);
        graphics.text(this.font, status, left, top + (rowH + gap) * (visibleRows + 6) + 4, 0xFFFFC66D, false);
        int listTop = contentTop(rowH, gap, visibleRows) + (rowH + gap) * 2;
        int listBottom = listTop + (rowH + gap) * visibleRows - 3;
        lastScrollbar = computeScrollbar(left + width + 5, listTop, listBottom, filtered.size(), rows.size(), offset);
        renderScrollbar(graphics, lastScrollbar);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        int width = panelWidth();
        int left = (this.width - width) / 2;
        int rowH = rowHeight();
        int gap = rowGap();
        int visibleRows = visibleRows(rowH, gap);
        int listTop = contentTop(rowH, gap, visibleRows) + (rowH + gap) * 2;
        int listBottom = listTop + (rowH + gap) * visibleRows - 3;
        if (mouseX < left || mouseX > left + width || mouseY < listTop || mouseY > listBottom) return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
        if (scrollY < 0) offset = Math.min(Math.max(0, filtered.size() - rows.size()), offset + 1);
        else if (scrollY > 0) offset = Math.max(0, offset - 1);
        refreshRows();
        return true;
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent context, boolean doubleClick) {
        if (context.button() == 0 && lastScrollbar.hasScroll && lastScrollbar.contains(context.x(), context.y())) {
            if (context.y() >= lastScrollbar.thumbY && context.y() <= lastScrollbar.thumbY + lastScrollbar.thumbH) {
                draggingScrollbar = true;
                scrollbarGrabOffset = (int)Math.max(0, Math.round(context.y()) - lastScrollbar.thumbY);
            } else {
                jumpScrollToMouse((int)Math.round(context.y()), 0);
                refreshRows();
            }
            return true;
        }
        return super.mouseClicked(context, doubleClick);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent context, double dragX, double dragY) {
        if (draggingScrollbar && context.button() == 0 && lastScrollbar.hasScroll) {
            jumpScrollToMouse((int)Math.round(context.y()), scrollbarGrabOffset);
            refreshRows();
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

    private final class MacroRow extends AbstractWidget {
        private final int row;
        private MacroRow(int x, int y, int w, int h, int row) {
            super(x, y, w, h, Component.empty());
            this.row = row;
        }

        @Override
        public boolean mouseClicked(MouseButtonEvent context, boolean doubleClick) {
            if (!active || !visible || context.button() != 0) return false;
            int idx = offset + row;
            if (idx < 0 || idx >= filtered.size()) return false;
            selected = idx;
            if (doubleClick) openSelected();
            return true;
        }

        @Override
        protected void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTicks) {
            int x = getX();
            int y = getY();
            int w = getWidth();
            int h = getHeight();
            boolean hasContent = !getMessage().getString().isBlank();
            boolean selectedRow = offset + row == selected;
            int baseRgb = UiUtilsSettings.get().uiButtonColor & 0xFFFFFF;
            int fill = hasContent ? (selectedRow ? (0x99000000 | baseRgb) : 0x99000000) : 0x44000000;
            int border = selectedRow ? (0xFF000000 | scaleRgb(baseRgb, 1.35F)) : (hasContent ? 0xAA5D6A72 : 0x66505A60);
            graphics.fill(x, y, x + w, y + h, fill);
            graphics.outline(x, y, w, h, border);
            if (hasContent)
                graphics.fill(x, y, x + 3, y + h, selectedRow ? 0xFFFFFFFF : (0xFF000000 | scaleRgb(baseRgb, 1.15F)));
            int textColor = 0xFF000000 | (UiUtilsSettings.get().uiButtonTextColor & 0xFFFFFF);
            int textY = y + Math.max(1,
                (h - Minecraft.getInstance().font.lineHeight) / 2);
            UiUtils.renderScaledText(graphics, Minecraft.getInstance().font,
                getMessage().getString(), x + 6, textY, w - 12, h - 2,
                textColor, 0.5F);
        }

        @Override
        protected void updateWidgetNarration(NarrationElementOutput narration) {
            defaultButtonNarrationText(narration);
        }
    }

    private static int scaleRgb(int rgb, float factor) {
        int r = (rgb >> 16) & 0xFF;
        int g = (rgb >> 8) & 0xFF;
        int b = rgb & 0xFF;
        r = clamp((int)(r * factor));
        g = clamp((int)(g * factor));
        b = clamp((int)(b * factor));
        return (r << 16) | (g << 8) | b;
    }

    private static int clamp(int value) {
        return Math.max(0, Math.min(255, value));
    }

    private void jumpScrollToMouse(int mouseY, int grabOffset) {
        if (!lastScrollbar.hasScroll) return;
        int maxScroll = Math.max(1, lastScrollbar.totalRows - lastScrollbar.visibleRows);
        int travel = Math.max(1, lastScrollbar.trackBottom - lastScrollbar.trackTop - lastScrollbar.thumbH);
        int thumbTop = Math.max(lastScrollbar.trackTop, Math.min(lastScrollbar.trackBottom - lastScrollbar.thumbH, mouseY - grabOffset));
        double ratio = (thumbTop - lastScrollbar.trackTop) / (double)travel;
        offset = Math.max(0, Math.min(maxScroll, (int)Math.round(ratio * maxScroll)));
    }

    private ScrollbarMetrics computeScrollbar(int x, int top, int bottom, int totalRows, int visibleRows, int scroll) {
        int trackH = Math.max(1, bottom - top);
        if (totalRows <= visibleRows) return new ScrollbarMetrics(x, top, bottom, top, trackH, false, totalRows, visibleRows);
        double ratio = visibleRows / (double)Math.max(1, totalRows);
        int thumbH = Math.max(12, (int)Math.round(trackH * ratio));
        int maxScroll = Math.max(1, totalRows - visibleRows);
        int travel = Math.max(1, trackH - thumbH);
        int thumbY = top + (int)Math.round((Math.max(0, Math.min(scroll, maxScroll)) / (double)maxScroll) * travel);
        return new ScrollbarMetrics(x, top, bottom, thumbY, thumbH, true, totalRows, visibleRows);
    }

    private void renderScrollbar(GuiGraphicsExtractor graphics, ScrollbarMetrics m) {
        if (!m.hasScroll) return;
        graphics.fill(m.x, m.trackTop, m.x + 3, m.trackBottom, 0xFF353535);
        graphics.fill(m.x, m.thumbY, m.x + 3, m.thumbY + m.thumbH, draggingScrollbar ? 0xFFFFFFFF : 0xFFCFCFCF);
    }

    private static Path defaultImportDirectory() {
        return FabricLoader.getInstance().getGameDir().resolve("config").resolve("ui-utils").resolve("macro_import");
    }

    private static Path defaultExportDirectory() {
        return FabricLoader.getInstance().getGameDir().resolve("config").resolve("ui-utils").resolve("macro_export");
    }

    private void ensureDefaultDirectories() {
        try {
            Files.createDirectories(defaultImportDirectory());
            Files.createDirectories(defaultExportDirectory());
        } catch (Exception e) {
            UiUtils.LOGGER.warn("Failed to create macro import/export directories", e);
            status = "Could not create macro import/export folders.";
        }
    }

    private void openFilePicker(EditBox target, boolean importFile) {
        Path start = pickerStartPath(target, importFile);
        status = importFile ? "Opening macro file picker..." : "Opening export folder picker...";
        new Thread(() -> openNativePicker(target, start, importFile), "UI-Utils Macro File Picker").start();
    }

    private Path pickerStartPath(EditBox target, boolean importFile) {
        String value = target.getValue() == null ? "" : target.getValue().trim();
        Path fallback = importFile ? defaultImportDirectory() : defaultExportDirectory();
        if (value.isBlank()) return fallback;
        Path path = Path.of(value);
        if (Files.isRegularFile(path) && path.getParent() != null) return path.getParent();
        return path;
    }

    private void openNativePicker(EditBox target, Path start, boolean importFile) {
        try {
            String picked = importFile
                ? TinyFileDialogs.tinyfd_openFileDialog(
                    "Import UI-Utils Macro",
                    start.toAbsolutePath().toString(),
                    null,
                    null,
                    false)
                : TinyFileDialogs.tinyfd_selectFolderDialog(
                    "Choose UI-Utils macro export folder",
                    start.toAbsolutePath().toString());

            Minecraft.getInstance().execute(() -> {
                if (picked != null && !picked.isBlank()) {
                    target.setValue(picked);
                    status = "Path selected.";
                } else {
                    status = "Picker canceled.";
                }
            });
        } catch (Throwable t) {
            UiUtils.LOGGER.warn("Macro file picker failed", t);
            Minecraft.getInstance().execute(() -> status = "File picker failed: " + t.getClass().getSimpleName());
        }
    }

    private record ScrollbarMetrics(int x, int trackTop, int trackBottom, int thumbY, int thumbH, boolean hasScroll, int totalRows, int visibleRows) {
        private static ScrollbarMetrics none() {
            return new ScrollbarMetrics(0, 0, 0, 0, 0, false, 0, 0);
        }

        private boolean contains(double mx, double my) {
            return mx >= x && mx <= x + 3 && my >= trackTop && my <= trackBottom;
        }
    }

    private int panelWidth() {
        return Math.min(460, Math.max(200, this.width - 20));
    }

    private int rowHeight() {
        return Mth.clamp((this.height - 110) / 19, 12, 17);
    }

    private int rowGap() {
        return rowHeight() <= 15 ? 2 : 3;
    }

    private int visibleRows(int rowH, int gap) {
        return Math.max(4, Math.min(10, (this.height - 170) / (rowH + gap)));
    }

    private int contentTop(int rowH, int gap, int visibleRows) {
        int totalHeight = rowH * (visibleRows + 5) + gap * (visibleRows + 4);
        return Math.max(12, (this.height - totalHeight) / 2);
    }
}
