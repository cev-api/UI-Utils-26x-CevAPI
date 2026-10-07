package com.ui_utils.uiutils;

import com.ui_utils.uiutils.ui.UiButton;
import com.ui_utils.uiutils.ui.UiContent;
import com.ui_utils.uiutils.ui.UiInput;
import com.ui_utils.uiutils.ui.UiModernScreen;
import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** File selection without optional LWJGL classes or platform native libraries. */
public final class UiUtilsFilePickerScreen extends UiModernScreen {
    private static final int PAGE_SIZE = 12;
    private final Screen parent;
    private final boolean selectFile;
    private final Consumer<Path> onSelected;
    private Path directory;
    private List<Path> entries = List.of();
    private UiInput pathField;
    private int page;
    private String error = "";

    public UiUtilsFilePickerScreen(Screen parent, Path start, boolean selectFile,
        Consumer<Path> onSelected) {
        super(Component.literal(selectFile ? "Import Macro File" : "Choose Export Folder"));
        this.parent = parent;
        this.selectFile = selectFile;
        this.onSelected = onSelected;
        Path path = start.toAbsolutePath().normalize();
        while (!Files.isDirectory(path) && path.getParent() != null)
            path = path.getParent();
        loadDirectory(path);
    }

    @Override
    protected int naturalWidth() {
        return 420;
    }

    @Override
    protected void buildContent(UiContent c) {
        pathField = c.input(directory.toString(), value -> {});
        pathField.setMaxLength(4096);
        pathField.setValue(directory.toString());
        c.row(UiContent.of(UiButton.of("Up", () -> {
            if (directory.getParent() != null) navigate(directory.getParent());
        })), UiContent.of(UiButton.of("Go", this::goToPath)),
            UiContent.of(UiButton.of("Refresh", () -> navigate(directory))));
        for (Path root : FileSystems.getDefault().getRootDirectories())
            c.row(UiContent.of(UiButton.of(root.toString(), () -> navigate(root))));
        if (!error.isEmpty()) c.note(error);
        int pages = Math.max(1, (entries.size() + PAGE_SIZE - 1) / PAGE_SIZE);
        page = Math.min(page, pages - 1);
        c.note("Page " + (page + 1) + " / " + pages);
        for (int i = page * PAGE_SIZE; i < Math.min(entries.size(), (page + 1) * PAGE_SIZE); i++) {
            Path entry = entries.get(i);
            boolean folder = Files.isDirectory(entry);
            c.row(UiContent.of(UiButton.of((folder ? "[Folder] " : "")
                + entry.getFileName(), () -> {
                    if (folder) navigate(entry);
                    else choose(entry);
                })));
        }
        c.row(UiContent.of(UiButton.of("Previous", () -> changePage(-1))),
            UiContent.of(UiButton.of("Next", () -> changePage(1))));
        if (!selectFile)
            c.footerButton("Choose Folder", UiButton.Kind.PRIMARY, () -> choose(directory));
        c.footerButton("Cancel", UiButton.Kind.SECONDARY, this::onClose);
    }

    private void loadDirectory(Path path) {
        directory = path.toAbsolutePath().normalize();
        error = "";
        try (var stream = Files.list(directory)) {
            entries = stream.filter(p -> Files.isDirectory(p) || selectFile && Files.isRegularFile(p))
                .sorted(Comparator.<Path, Boolean>comparing(p -> !Files.isDirectory(p))
                    .thenComparing(p -> p.getFileName().toString(), String.CASE_INSENSITIVE_ORDER))
                .toList();
        } catch (IOException | SecurityException e) {
            entries = List.of();
            error = "Cannot read folder: " + e.getMessage();
        }
    }

    private void navigate(Path path) {
        page = 0;
        loadDirectory(path);
        rebuildWidgets();
    }

    private void changePage(int delta) {
        page = Math.max(0, Math.min(Math.max(0, (entries.size() - 1) / PAGE_SIZE), page + delta));
        rebuildWidgets();
    }

    private void goToPath() {
        try {
            Path path = directory.resolve(pathField.getValue().trim()).normalize();
            if (selectFile && Files.isRegularFile(path)) choose(path);
            else navigate(path);
        } catch (IllegalArgumentException e) {
            setStatus("Invalid path");
        }
    }

    private void choose(Path path) {
        if (selectFile ? !Files.isRegularFile(path) : !Files.isDirectory(path)) {
            setStatus("Path no longer exists");
            return;
        }
        // Reinitialise the parent first; it recreates the import/export fields.
        McCompat.setScreen(minecraft, parent);
        onSelected.accept(path.toAbsolutePath());
    }

    @Override
    public void onClose() {
        McCompat.setScreen(minecraft, parent);
    }
}
