package com.ui_utils.mixin;

import net.minecraft.core.MappedRegistry;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(MappedRegistry.class)
public interface MappedRegistryFrozenAccessor {
	@Accessor("frozen")
	boolean uiutils$isFrozen();

	@Accessor("frozen")
	void uiutils$setFrozen(boolean frozen);
}
