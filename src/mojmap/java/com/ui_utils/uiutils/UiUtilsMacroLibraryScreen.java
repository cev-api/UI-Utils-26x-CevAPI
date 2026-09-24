package com.ui_utils.uiutils;

import com.ui_utils.uiutils.macro.UiUtilsMacro;
import com.ui_utils.uiutils.macro.UiUtilsMacroExecutor;
import com.ui_utils.uiutils.macro.UiUtilsMacroIo;
import com.ui_utils.uiutils.macro.UiUtilsMacroManager;
import com.ui_utils.uiutils.ui.UiButton;
import com.ui_utils.uiutils.ui.UiContent;
import com.ui_utils.uiutils.ui.UiInput;
import com.ui_utils.uiutils.ui.UiListRow;
import com.ui_utils.uiutils.ui.UiModernScreen;
import com.ui_utils.uiutils.ui.UiTheme;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import org.lwjgl.util.tinyfd.TinyFileDialogs;

public final class UiUtilsMacroLibraryScreen extends UiModernScreen {
    private final Screen parent;
    private UiInput searchField;
    private UiInput importField;
    private UiInput exportField;
    private final List<UiListRow> rows = new ArrayList<>();
    private final List<UiUtilsMacro> filtered = new ArrayList<>();
    private int selected = -1;
    private int offset = 0;
    private String status = "";

    public UiUtilsMacroLibraryScreen(Screen parent) {
        super(Component.literal("Macro Library"));
        this.parent = parent;
    }

    @Override
    protected int naturalWidth() {
        return 420;
    }

    @Override
    protected boolean expandWidth() {
        return true;
    }

    @Override
    protected int maxWidth() {
        return 560;
    }

    @Override
    protected void buildContent(UiContent c) {
        ensureDefaultDirectories();
        rows.clear();

        searchField = c.input("", value -> {
            offset = 0;
            refreshRows();
        });
        searchField.setHint(Component.literal("Search macros..."));
        c.row(UiContent.of(UiButton.of("Create New",
                () -> McCompat.setScreen(minecraft, new UiUtilsMacrosScreen(this)))),
            UiContent.of(UiButton.of("Edit Selected", this::openSelected)),
            UiContent.of(UiButton.of("Delete Selected", this::deleteSelected)));
        c.row(UiContent.of(UiButton.of("Run Selected", this::runSelected)),
            UiContent.of(UiButton.of("Stop", UiUtilsMacroExecutor::stop)));

        // Only as many list slots as the viewport has room for: the import and
        // export controls below must stay inside the panel, not clip behind the
        // footer where they cannot be reached.
        // One row per macro, always. The panel scrolls as a whole, so the import and
        // export controls below can never be stranded behind the footer. The list is
        // filtered first, because the row count depends on it.
        rebuildFiltered();
        int slots = Math.max(3, Math.min(40, filtered.size() + 1));
        for (int slot = 0; slot < slots; slot++) {
            final int rowSlot = slot;
            UiListRow row = new UiListRow("", () -> selectRow(rowSlot, false));
            row.doubleRun(() -> selectRow(rowSlot, true));
            rows.add(row);
            c.row(UiContent.of(row));
        }

        c.section("Import / Export");
        importField = new UiInput(this.font, 1, defaultImportDirectory().toString(),
            Component.literal("Import path (NBT)"));
        importField.setMaxLength(1024);
        c.row(UiContent.of(importField, 6F),
            UiContent.fixed(UiButton.of("...", () -> openFilePicker(importField, true)), 26),
            UiContent.of(UiButton.of("Import", this::importMacro), 1.5F));

        exportField = new UiInput(this.font, 1, defaultExportDirectory().toString(),
            Component.literal("Export folder"));
        exportField.setMaxLength(1024);
        c.row(UiContent.of(exportField, 6F),
            UiContent.fixed(UiButton.of("...", () -> openFilePicker(exportField, false)), 26),
            UiContent.of(UiButton.of("Export", this::exportMacro), 1.5F));

        refreshRows();
    }

    @Override
    public void tick() {
        super.tick();
        refreshRows();
        String running = UiUtilsMacroExecutor.isRunning()
            ? UiUtilsMacroExecutor.currentName() : "none";
        setStatus(status.isEmpty() ? "Running: " + running : status);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX,
        double scrollY) {
        // Only take the wheel for the inner list when it has more entries than
        // slots; otherwise the panel needs it to reach the rest of the form.
        if (!rows.isEmpty() && scrollY != 0 && filtered.size() > rows.size()) {
            int designY = designY(mouseY);
            UiListRow first = rows.get(0);
            UiListRow last = rows.get(rows.size() - 1);
            if (designY >= first.getY() && designY <= last.getY() + last.getHeight()) {
                int maxOffset = Math.max(0, filtered.size() - rows.size());
                offset = Mth.clamp(offset + (scrollY < 0 ? 1 : -1), 0, maxOffset);
                refreshRows();
                return true;
            }
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    private void selectRow(int slot, boolean open) {
        int index = offset + slot;
        if (index < 0 || index >= filtered.size())
            return;
        selected = index;
        refreshRows();
        if (open)
            openSelected();
    }

    private void importMacro() {
        status = UiUtilsMacroIo.importMacro(importField.getValue().trim(), "");
        Minecraft.getInstance().execute(this::rebuildWidgets);
    }

    private void exportMacro() {
        UiUtilsMacro macro = selectedMacro();
        status = macro == null ? "No macro selected."
            : UiUtilsMacroIo.exportMacro(macro.name, exportField.getValue().trim());
    }

    private void deleteSelected() {
        UiUtilsMacro macro = selectedMacro();
        if (macro == null) {
            status = "No macro selected.";
            return;
        }
        UiUtilsMacroManager.get().remove(macro.name);
        selected = -1;
        refreshRows();
        status = "Deleted " + macro.name;
    }

    private void runSelected() {
        UiUtilsMacro macro = selectedMacro();
        if (macro != null) UiUtilsMacroManager.get().execute(macro.name);
    }

    private void openSelected() {
        UiUtilsMacro macro = selectedMacro();
        if (macro != null) McCompat.setScreen(minecraft, new UiUtilsMacrosScreen(this, macro.name));
    }

    private UiUtilsMacro selectedMacro() {
        return (selected >= 0 && selected < filtered.size()) ? filtered.get(selected) : null;
    }

    /** Rebuilds the filtered list from the search box. Safe to call any time. */
    private void rebuildFiltered() {
        String query = searchField == null ? ""
            : searchField.getValue().trim().toLowerCase(Locale.ROOT);
        filtered.clear();
        for (UiUtilsMacro macro : UiUtilsMacroManager.get().getAll()) {
            if (query.isBlank() || macro.name.toLowerCase(Locale.ROOT).contains(query))
                filtered.add(macro);
        }
    }

    private void refreshRows() {
        rebuildFiltered();
        int maxOffset = Math.max(0, filtered.size() - rows.size());
        if (offset > maxOffset) offset = maxOffset;
        if (selected >= filtered.size()) selected = filtered.isEmpty() ? -1 : 0;
        for (int i = 0; i < rows.size(); i++) {
            UiListRow row = rows.get(i);
            int index = offset + i;
            boolean filled = index >= 0 && index < filtered.size();
            row.active = filled;
            row.label(filled ? filtered.get(index).name : "");
            row.detail(filled ? filtered.get(index).actions.size() + " steps" : "");
            row.selected(filled && index == selected);
        }
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
}
