package com.ui_utils.uiutils;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.input.KeyEvent;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.SharedConstants;

public final class McCompat {
	// 26.3 replaced GLFW input codes with SDL scancodes in KeyEvent#key(). Keep
	// matching the older GLFW key codes too for compatibility with earlier 26.x.
	private static final int SDL_SCANCODE_RETURN = 40;
	private static final int SDL_SCANCODE_KP_ENTER = 88;
	private static final int SDL_SCANCODE_BACKSPACE = 42;
	private static final int SDL_SCANCODE_DELETE = 76;
	private static final int GLFW_KEY_ENTER = 257;
	private static final int GLFW_KEY_KP_ENTER = 335;
	private static final int GLFW_KEY_BACKSPACE = 259;
	private static final int GLFW_KEY_DELETE = 261;

	/** Mouse button ids changed from GLFW's zero-based values to SDL's values in 26.3. */
	public static final int LEFT_BUTTON = leftMouseButton();

	private McCompat() {
	}

	public static Screen getScreen(Minecraft mc) {
		if (mc == null)
			return null;

		Object gui = mc.gui;
		Screen screen = findScreen(gui);
		return screen != null ? screen : findScreen(mc);
	}

	public static void setScreen(Minecraft mc, Screen screen) {
		if (mc == null)
			return;

		if (!invokeScreenSetter(mc.gui, screen))
			invokeScreenSetter(mc, screen);
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
		for (Method method : InputConstants.class.getMethods()) {
			if (!Modifier.isStatic(method.getModifiers())
				|| method.getReturnType() != boolean.class)
				continue;
			Class<?>[] parameters = method.getParameterTypes();
			try {
				if (parameters.length == 1 && parameters[0] == int.class)
					return (boolean)method.invoke(null, key.getValue());
				if (parameters.length == 2 && parameters[1] == int.class
					&& parameters[0].isInstance(mc.getWindow()))
					return (boolean)method.invoke(null, mc.getWindow(), key.getValue());
			} catch (ReflectiveOperationException ignored) {
			}
		}
		return false;
	}

	/** Returns the key type used for keyboard bindings in the running release. */
	public static InputConstants.Key getKeyboardKey(int value) {
		// 26.1/26.2 declare KEYSYM first; 26.3 declares KEYBOARD first.
		// Using the first keyboard type avoids linking to either version-specific enum field.
		InputConstants.Type[] types = InputConstants.Type.values();
		if (types.length == 0)
			throw new IllegalStateException("Minecraft has no input key types");
		return types[0].getOrCreate(value);
	}

	/** True for Enter and keypad Enter, used to submit chat and text fields. */
	public static boolean isConfirmationKey(KeyEvent event) {
		if (event == null)
			return false;
		return matches(event.key(), SDL_SCANCODE_RETURN, SDL_SCANCODE_KP_ENTER,
			GLFW_KEY_ENTER, GLFW_KEY_KP_ENTER);
	}

	/** True for Backspace and Delete, used to clear a keybind. */
	public static boolean isClearKey(KeyEvent event) {
		if (event == null)
			return false;
		return matches(event.key(), SDL_SCANCODE_BACKSPACE, SDL_SCANCODE_DELETE,
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
		ChatComponent chat = findChatComponent(gui);
		if (chat != null)
			return chat;
		if (gui == null)
			return null;
		for (Field field : allFields(gui.getClass())) {
			Object nested = readField(field, gui);
			chat = findChatComponent(nested);
			if (chat != null)
				return chat;
		}
		return null;
	}

	private static Screen findScreen(Object owner) {
		if (owner == null)
			return null;
		for (Method method : owner.getClass().getMethods()) {
			if (method.getParameterCount() != 0
				|| !Screen.class.isAssignableFrom(method.getReturnType()))
				continue;
			try {
				Object value = method.invoke(owner);
				if (value instanceof Screen screen)
					return screen;
			} catch (ReflectiveOperationException ignored) {
			}
		}
		for (Field field : allFields(owner.getClass())) {
			if (!Screen.class.isAssignableFrom(field.getType()))
				continue;
			Object value = readField(field, owner);
			if (value instanceof Screen screen)
				return screen;
		}
		return null;
	}

	private static boolean invokeScreenSetter(Object owner, Screen screen) {
		if (owner == null)
			return false;
		for (Method method : owner.getClass().getMethods()) {
			if (method.getParameterCount() != 1
				|| method.getParameterTypes()[0] != Screen.class
				|| method.getReturnType() != void.class)
				continue;
			try {
				method.invoke(owner, screen);
				return true;
			} catch (ReflectiveOperationException ignored) {
			}
		}
		return false;
	}

	private static ChatComponent findChatComponent(Object owner) {
		if (owner == null)
			return null;
		for (Method method : owner.getClass().getMethods()) {
			if (method.getParameterCount() != 0
				|| !ChatComponent.class.isAssignableFrom(method.getReturnType()))
				continue;
			try {
				Object value = method.invoke(owner);
				if (value instanceof ChatComponent chat)
					return chat;
			} catch (ReflectiveOperationException ignored) {
			}
		}
		for (Field field : allFields(owner.getClass())) {
			if (!ChatComponent.class.isAssignableFrom(field.getType()))
				continue;
			Object value = readField(field, owner);
			if (value instanceof ChatComponent chat)
				return chat;
		}
		return null;
	}

	private static List<Field> allFields(Class<?> type) {
		List<Field> fields = new java.util.ArrayList<>();
		for (Class<?> current = type; current != null; current = current.getSuperclass())
			java.util.Collections.addAll(fields, current.getDeclaredFields());
		return fields;
	}

	private static Object readField(Field field, Object owner) {
		try {
			if (!field.canAccess(owner) && !field.trySetAccessible())
				return null;
			return field.get(owner);
		} catch (ReflectiveOperationException | RuntimeException ignored) {
			return null;
		}
	}

	private static int leftMouseButton() {
		return usesSdlInput() ? 1 : 0;
	}

	private static boolean usesSdlInput() {
		String version = SharedConstants.getCurrentVersion().id();
		String[] parts = version.split("\\.");
		try {
			int major = Integer.parseInt(parts[0]);
			int minor = parts.length > 1
				? Integer.parseInt(parts[1].replaceAll("[^0-9].*$", "")) : 0;
			return major > 26 || major == 26 && minor >= 3;
		} catch (NumberFormatException ignored) {
			return version.startsWith("26.3");
		}
	}
}
