package com.ui_utils.mixin;

import java.util.List;
import java.util.Set;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

public final class UiUtilsMixinPlugin implements IMixinConfigPlugin {
	private static final String GUI_CLASS = "net.minecraft.client.gui.Gui";
	private static final String HUD_CLASS = "net.minecraft.client.gui.Hud";
	private static final String HUD_METHOD = "extractRenderState";
	private static final String LEGACY_HUD_DESC = "(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/client/DeltaTracker;)V";

	@Override
	public void onLoad(String mixinPackage) {
	}

	@Override
	public String getRefMapperConfig() {
		return null;
	}

	@Override
	public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
		if (mixinClassName.endsWith(".ui_utils.UiUtilsIngameHudMixin"))
			return targetClassName.equals(GUI_CLASS) && hasMethod(targetClassName, HUD_METHOD, LEGACY_HUD_DESC);
		if (mixinClassName.endsWith(".ui_utils.UiUtilsHudMixin"))
			return targetClassName.equals(HUD_CLASS) && hasMethod(targetClassName, HUD_METHOD, LEGACY_HUD_DESC);
		return true;
	}

	@Override
	public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {
	}

	@Override
	public List<String> getMixins() {
		return null;
	}

	@Override
	public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
	}

	@Override
	public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
	}

	private static boolean hasMethod(String className, String methodName, String descriptor) {
		try {
			ClassNode node = new ClassNode();
			var reader = new org.objectweb.asm.ClassReader(className);
			reader.accept(node, ClassReaderFlags.SKIP_DEBUG_AND_FRAMES);
			for (MethodNode method : node.methods) {
				if ((method.access & Opcodes.ACC_SYNTHETIC) != 0)
					continue;
				if (method.name.equals(methodName) && method.desc.equals(descriptor))
					return true;
			}
		} catch (Throwable ignored) {
		}
		return false;
	}

	private static final class ClassReaderFlags {
		private static final int SKIP_DEBUG_AND_FRAMES = org.objectweb.asm.ClassReader.SKIP_DEBUG
			| org.objectweb.asm.ClassReader.SKIP_FRAMES
			| org.objectweb.asm.ClassReader.SKIP_CODE;

		private ClassReaderFlags() {
		}
	}
}
