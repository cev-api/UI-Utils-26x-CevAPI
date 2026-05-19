package com.ui_utils.uiutils.macro;

import com.ui_utils.uiutils.UiUtils;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;

public final class UiUtilsMacroIo {
    private UiUtilsMacroIo() {}

    public static String exportMacro(String macroName, String filePath) {
        UiUtilsMacro macro = UiUtilsMacroManager.get().getByName(macroName);
        if (macro == null) return "Macro not found: " + macroName;
        try {
            Path path = resolveExportPath(filePath, macro.name);
            if (path.getParent() != null) Files.createDirectories(path.getParent());
            CompoundTag root = new CompoundTag();
            root.put("macro", macro.toTag());
            NbtIo.write(root, path);
            return "Exported macro '" + macro.name + "' to " + path;
        } catch (Exception e) {
            UiUtils.LOGGER.warn("Macro export failed", e);
            return "Macro export failed: " + e.getMessage();
        }
    }

    public static String importMacro(String filePath, String preferredName) {
        try {
            Path path = Path.of(filePath);
            if (Files.isDirectory(path)) return "Choose a macro .nbt file inside " + path;
            if (!Files.exists(path)) return "File not found: " + path;
            CompoundTag root = NbtIo.read(path);
            if (root == null) return "Invalid macro file.";
            CompoundTag macroTag;
            if (root.contains("macro") && root.get("macro") instanceof CompoundTag tag) {
                macroTag = tag;
            } else {
                macroTag = root;
            }
            UiUtilsMacro imported = UiUtilsMacro.fromTag(macroTag);
            if (preferredName != null && !preferredName.isBlank()) imported.name = preferredName.trim();
            imported.name = UiUtilsMacroManager.get().createUniqueName(imported.name);
            UiUtilsMacroManager.get().add(imported, true);
            return "Imported macro as '" + imported.name + "'.";
        } catch (Exception e) {
            UiUtils.LOGGER.warn("Macro import failed", e);
            return "Macro import failed: " + e.getMessage();
        }
    }

    private static Path resolveExportPath(String filePath, String macroName) {
        Path path = Path.of(filePath);
        String raw = filePath == null ? "" : filePath.trim();
        boolean looksLikeFile = raw.toLowerCase(Locale.ROOT).endsWith(".nbt");
        if (Files.isDirectory(path) || !looksLikeFile) {
            return path.resolve(safeFileName(macroName) + ".nbt");
        }
        return path;
    }

    private static String safeFileName(String macroName) {
        String safe = macroName == null || macroName.isBlank() ? "macro" : macroName.trim();
        return safe.replaceAll("[\\\\/:*?\"<>|]+", "_");
    }
}
