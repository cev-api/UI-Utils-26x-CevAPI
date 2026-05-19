package com.ui_utils.uiutils.macro;

import com.ui_utils.uiutils.UiUtils;
import com.ui_utils.uiutils.UiUtilsSettings;
import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;

public final class UiUtilsMacroManager {
    private static UiUtilsMacroManager instance;
    private final List<UiUtilsMacro> macros = new ArrayList<>();
    private final File saveFile = FabricLoader.getInstance().getConfigDir().resolve("ui-utils-macros.nbt").toFile();

    private UiUtilsMacroManager() { load(); }

    public static synchronized UiUtilsMacroManager get() {
        if (instance == null) instance = new UiUtilsMacroManager();
        return instance;
    }

    public synchronized List<UiUtilsMacro> getAll() { return new ArrayList<>(macros); }

    public synchronized UiUtilsMacro getByName(String name) {
        if (name == null) return null;
        for (UiUtilsMacro macro : macros) {
            if (name.equalsIgnoreCase(macro.name)) return macro;
        }
        return null;
    }

    public synchronized String createUniqueName(String preferred) {
        String base = preferred == null || preferred.isBlank() ? "New Macro" : preferred.trim();
        String candidate = base;
        int i = 1;
        while (getByName(candidate) != null) candidate = base + " (" + (i++) + ")";
        return candidate;
    }

    public synchronized UiUtilsMacro add(UiUtilsMacro macro, boolean saveNow) {
        if (macro == null) return null;
        if (macro.name == null || macro.name.isBlank()) macro.name = "New Macro";
        if (getByName(macro.name) != null) macro.name = createUniqueName(macro.name);
        macros.add(macro);
        if (saveNow) save();
        return macro;
    }

    public synchronized boolean remove(String name) {
        UiUtilsMacro existing = getByName(name);
        if (existing == null) return false;
        if (UiUtilsMacroExecutor.isRunning(name)) UiUtilsMacroExecutor.stop();
        macros.remove(existing);
        save();
        return true;
    }

    public synchronized boolean execute(String name) {
        UiUtilsMacro macro = getByName(name);
        if (macro == null) return false;
        UiUtilsMacroExecutor.start(macro.deepCopy());
        UiUtilsSettings.get().lastMacroName = macro.name;
        UiUtilsSettings.save();
        return true;
    }

    public synchronized void save() {
        try {
            CompoundTag root = new CompoundTag();
            ListTag list = new ListTag();
            for (UiUtilsMacro macro : macros) list.add(macro.toTag());
            root.put("macros", list);
            NbtIo.write(root, Path.of(saveFile.getAbsolutePath()));
        } catch (Exception e) {
            UiUtils.LOGGER.warn("Failed to save macros", e);
        }
    }

    public synchronized void load() {
        macros.clear();
        if (!saveFile.exists()) return;
        try {
            CompoundTag root = NbtIo.read(Path.of(saveFile.getAbsolutePath()));
            if (root == null || !root.contains("macros")) return;
            ListTag list = (ListTag) root.get("macros");
            for (Tag element : list) {
                if (element instanceof CompoundTag macroTag) macros.add(UiUtilsMacro.fromTag(macroTag));
            }
        } catch (Exception e) {
            UiUtils.LOGGER.warn("Failed to load macros", e);
        }
    }
}
