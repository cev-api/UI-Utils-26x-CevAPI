package com.ui_utils.mixin.ui_utils;

import java.util.List;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Allows overlay widgets to keep input/narration without the screen's early draw. */
@Mixin(Screen.class)
public interface UiUtilsScreenAccessor {
	@Accessor("renderables")
	List<Renderable> uiutils$getRenderables();

	@Accessor("children")
	List<GuiEventListener> uiutils$getChildren();

	@Accessor("narratables")
	List<NarratableEntry> uiutils$getNarratables();
}
