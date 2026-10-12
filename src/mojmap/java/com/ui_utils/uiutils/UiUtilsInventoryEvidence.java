package com.ui_utils.uiutils;

import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;

/** Inventory evidence comes from full server contents, never locally predicted clicks. */
public final class UiUtilsInventoryEvidence {
    public record Snapshot(long sequence, List<ItemStack> stacks, ItemStack carried) {
        public int count(ItemStack target) {
            int total = ItemStack.isSameItemSameComponents(carried, target) ? carried.getCount() : 0;
            for (ItemStack stack : stacks)
                if (ItemStack.isSameItemSameComponents(stack, target)) total += stack.getCount();
            return total;
        }
    }
    private static final Map<AbstractContainerMenu, Snapshot> CONTENTS = new WeakHashMap<>();
    private static long sequence;
    private UiUtilsInventoryEvidence() {}
    public static void fullContents(AbstractContainerMenu menu, List<ItemStack> stacks, ItemStack carried) {
        CONTENTS.put(menu, new Snapshot(++sequence, stacks.stream().map(ItemStack::copy).toList(), carried.copy()));
    }
    public static Snapshot snapshot(AbstractContainerMenu menu) { return CONTENTS.get(menu); }
    public static void clear() { CONTENTS.clear(); }
}
