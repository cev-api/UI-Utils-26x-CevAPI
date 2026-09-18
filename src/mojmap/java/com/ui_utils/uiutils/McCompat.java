package com.ui_utils.uiutils;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.components.ChatComponent;
import com.mojang.blaze3d.platform.InputConstants;

public final class McCompat {
	private McCompat() {
	}

	public static Screen getScreen(Minecraft mc) {
		if (mc == null)
			return null;

		Object gui = mc.gui;
		if (gui != null) {
			// In current Minecraft versions the active screen lives on Gui, not
			// Minecraft. Use the mapped accessor so this also works after remapping.
			try {
				Method method = gui.getClass().getMethod("screen");
				Object result = method.invoke(gui);
				if (result instanceof Screen screen)
					return screen;
			} catch (ReflectiveOperationException ignored) {
			}
		}

		try {
			Field field = mc.getClass().getField("screen");
			Object result = field.get(mc);
			if (result instanceof Screen screen)
				return screen;
		} catch (ReflectiveOperationException ignored) {
		}

		return null;
	}

	public static void setScreen(Minecraft mc, Screen screen) {
		if (mc == null)
			return;

		try {
			Method method = mc.getClass().getMethod("setScreen", Screen.class);
			method.invoke(mc, screen);
			return;
		} catch (ReflectiveOperationException ignored) {
		}

		try {
			Method method = mc.getClass().getMethod("setScreenAndShow", Screen.class);
			method.invoke(mc, screen);
			return;
		} catch (ReflectiveOperationException ignored) {
		}

		Object gui = mc.gui;
		if (gui == null)
			return;

		try {
			Method method = gui.getClass().getMethod("setScreen", Screen.class);
			method.invoke(gui, screen);
		} catch (ReflectiveOperationException ignored) {
		}
	}

	public static void addRecentChat(Minecraft mc, String message) {
		if (mc == null || message == null)
			return;

		ChatComponent chat = getChatComponent(mc);
		if (chat != null)
			chat.addRecentChat(message);
	}

	/** Handles the InputConstants signature change between 26.1/26.2 and 26.3. */
	public static boolean isKeyDown(Minecraft mc, InputConstants.Key key) {
		if (mc == null || key == null)
			return false;
		try {
			Method current = InputConstants.class.getMethod("isKeyDown", int.class);
			return (boolean) current.invoke(null, key.getValue());
		} catch (ReflectiveOperationException ignored) {
		}
		try {
			for (Method method : InputConstants.class.getMethods()) {
				if (method.getName().equals("isKeyDown") && method.getParameterCount() == 2)
					return (boolean) method.invoke(null, mc.getWindow(), key.getValue());
			}
		} catch (ReflectiveOperationException ignored) {
		}
		return false;
	}

	private static ChatComponent getChatComponent(Minecraft mc) {
		Object gui = mc.gui;
		if (gui == null)
			return null;

		try {
			Method method = gui.getClass().getMethod("getChat");
			Object result = method.invoke(gui);
			if (result instanceof ChatComponent chat)
				return chat;
		} catch (ReflectiveOperationException ignored) {
		}

		try {
			Field hudField = gui.getClass().getField("hud");
			Object hud = hudField.get(gui);
			if (hud == null)
				return null;
			Method method = hud.getClass().getMethod("getChat");
			Object result = method.invoke(hud);
			if (result instanceof ChatComponent chat)
				return chat;
		} catch (ReflectiveOperationException ignored) {
		}

		return null;
	}
}
