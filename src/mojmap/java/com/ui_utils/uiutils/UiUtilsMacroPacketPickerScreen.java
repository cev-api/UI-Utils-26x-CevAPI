package com.ui_utils.uiutils;

import com.ui_utils.packettools.PacketCatalog;
import com.ui_utils.uiutils.ui.UiButton;
import com.ui_utils.uiutils.ui.UiContent;
import com.ui_utils.uiutils.ui.UiInput;
import com.ui_utils.uiutils.ui.UiModernScreen;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Consumer;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Searchable, paged packet selection using the runtime protocol catalog. */
public final class UiUtilsMacroPacketPickerScreen extends UiModernScreen {
    private final Screen parent;
    private final Consumer<List<String>> onSelect;
    private final List<String> catalog;
    private final Set<String> selected = new LinkedHashSet<>();
    private final List<UiButton> rows = new ArrayList<>();
    private List<String> filtered = List.of();
    private UiInput search;
    private int offset;

    public UiUtilsMacroPacketPickerScreen(Screen parent, String direction, List<String> current, Consumer<List<String>> onSelect) {
        super(Component.literal("Choose " + direction + " packets"));
        this.parent = parent;
        this.onSelect = onSelect;
        catalog = List.copyOf(direction.equals("S2C") ? PacketCatalog.getS2CNames() : PacketCatalog.getC2SNames());
        current.stream().filter(catalog::contains).forEach(selected::add);
    }
    @Override protected int naturalWidth() { return 460; }
    @Override protected void buildContent(UiContent c) {
        String query = search == null ? "" : search.getValue();
        rows.clear();
        search = c.input(query, value -> { offset = 0; refresh(); });
        search.setHint(Component.literal("Search packet names..."));
        for (int i = 0; i < 10; i++) {
            final int row = i;
            UiButton button = UiButton.of("", () -> {
                int index = offset + row;
                if (index >= filtered.size()) return;
                String name = filtered.get(index);
                if (!selected.remove(name)) selected.add(name);
                refresh();
            });
            rows.add(button);
            c.row(UiContent.of(button));
        }
        c.row(UiContent.of(UiButton.of("Previous", () -> { offset = Math.max(0, offset - 10); refresh(); })),
            UiContent.of(UiButton.of("Next", () -> { if (offset + 10 < filtered.size()) offset += 10; refresh(); })),
            UiContent.of(UiButton.of("Clear selection", () -> { selected.clear(); refresh(); })));
        c.footerButton("Cancel", UiButton.Kind.SECONDARY, this::onClose);
        c.footerButton("Use selected", UiButton.Kind.PRIMARY, () -> {
            onSelect.accept(List.copyOf(selected));
            onClose();
        });
        refresh();
    }
    private void refresh() {
        String query = search == null ? "" : search.getValue().trim().toLowerCase(Locale.ROOT);
        filtered = catalog.stream().filter(name -> name.toLowerCase(Locale.ROOT).contains(query)).toList();
        for (int i = 0; i < rows.size(); i++) {
            UiButton row = rows.get(i);
            int index = offset + i;
            row.active = index < filtered.size();
            row.setMessage(Component.literal(row.active ? (selected.contains(filtered.get(index)) ? "[x] " : "[ ] ") + filtered.get(index) : ""));
        }
        setStatus(selected.size() + " selected / " + filtered.size() + " results");
    }
    @Override public void onClose() { McCompat.setScreen(minecraft, parent); }
}
