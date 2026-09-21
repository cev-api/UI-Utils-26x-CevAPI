package com.ui_utils.uiutils;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.input.KeyEvent;
import com.mojang.blaze3d.platform.InputConstants;

public final class McCompat {
	// 26.3 replaced the GLFW input codes with SDL ones: KeyEvent#key() holds the SDL
	// scancode and KeyEvent#keycode() the SDL keycode. Accept both families so the
	// same checks keep working across 26.x.
	private static final int SDL_SCANCODE_RETURN = 40;
	private static final int SDL_SCANCODE_KP_ENTER = 88;
	private static final int SDL_SCANCODE_BACKSPACE = 42;
	private static final int SDL_SCANCODE_DELETE = 76;
	private static final int SDLK_RETURN = 13;
	private static final int SDLK_KP_ENTER = 0x40000058;
	private static final int SDLK_BACKSPACE = 8;
	private static final int SDLK_DELETE = 127;
	private static final int GLFW_KEY_ENTER = 257;
	private static final int GLFW_KEY_KP_ENTER = 335;
	private static final int GLFW_KEY_BACKSPACE = 259;
	private static final int GLFW_KEY_DELETE = 261;

	/**
	 * Left mouse button id for the running version. 26.3's SDL backend reports 1
	 * (vanilla's own isValidClickButton compares against 1), whereas the older
	 * GLFW-based versions reported 0.
	 */
	public static final int LEFT_BUTTON = InputConstants.MOUSE_BUTTON_LEFT;

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

	/** True for Enter and keypad Enter, used to submit chat and text fields. */
	public static boolean isConfirmationKey(KeyEvent event) {
		if (event == null)
			return false;
		return matches(event.key(), SDL_SCANCODE_RETURN, SDL_SCANCODE_KP_ENTER,
			GLFW_KEY_ENTER, GLFW_KEY_KP_ENTER)
			|| matches(event.keycode(), SDLK_RETURN, SDLK_KP_ENTER,
				GLFW_KEY_ENTER, GLFW_KEY_KP_ENTER);
	}

	/** True for Backspace and Delete, used to clear a keybind. */
	public static boolean isClearKey(KeyEvent event) {
		if (event == null)
			return false;
		return matches(event.key(), SDL_SCANCODE_BACKSPACE, SDL_SCANCODE_DELETE,
			GLFW_KEY_BACKSPACE, GLFW_KEY_DELETE)
			|| matches(event.keycode(), SDLK_BACKSPACE, SDLK_DELETE,
				GLFW_KEY_BACKSPACE, GLFW_KEY_DELETE);
	}

	private static boolean matches(int value, int... codes) {
		for (int code : codes)
			if (value == code)
				return true;
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
