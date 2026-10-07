package com.ui_utils.uiutils;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.input.KeyEvent;
import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.Window;
import net.minecraft.SharedConstants;

public final class McCompat {
	// 26.3 replaced GLFW input codes with SDL scancodes in KeyEvent#key().
	// Interpret each version's codes separately: some SDL values are GLFW letters.
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
	private static final Method KEY_DOWN_METHOD = findKeyDownMethod();

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

		// Screen transitions must run on the client thread. Match the method by
		// name rather than invoking arbitrary methods with a Screen parameter.
		// Minecraft's executor runs inline when already on the client thread.
		mc.execute(() -> {
			// The named setter moved from Minecraft to Gui in 26.2.
			// Signature-only reflection also matched clearClientLevel(Screen).
			if (!invokeScreenSetter(mc.gui, screen) && !invokeScreenSetter(mc, screen))
				throw new IllegalStateException("No compatible setScreen method");
		});
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
			return KEY_DOWN_METHOD.getParameterCount() == 1
				? (boolean)KEY_DOWN_METHOD.invoke(null, key.getValue())
				: (boolean)KEY_DOWN_METHOD.invoke(null, mc.getWindow(), key.getValue());
		} catch (ReflectiveOperationException ignored) {
		}
		return false;
	}

	private static Method findKeyDownMethod() {
		try {
			try {
				return InputConstants.class.getMethod("isKeyDown", int.class);
			} catch (NoSuchMethodException ignored) {
				return InputConstants.class.getMethod("isKeyDown", Window.class, int.class);
			}
		} catch (NoSuchMethodException e) {
			throw new IllegalStateException("No compatible keyboard polling method", e);
		}
	}

	/** Returns the key type used for keyboard bindings in the running release. */
	public static InputConstants.Key getKeyboardKey(int value) {
		// Use names rather than linking to a version-specific enum field or ordinal.
		for (InputConstants.Type type : InputConstants.Type.values())
			if (type.name().equals("KEYSYM") || type.name().equals("KEYBOARD"))
				return type.getOrCreate(value);
		throw new IllegalStateException("Minecraft has no keyboard key type");
	}

	/** True for Enter and keypad Enter, used to submit chat and text fields. */
	public static boolean isConfirmationKey(KeyEvent event) {
		if (event == null)
			return false;
		return usesSdlInput()
			? matches(event.key(), SDL_SCANCODE_RETURN, SDL_SCANCODE_KP_ENTER)
			: matches(event.key(), GLFW_KEY_ENTER, GLFW_KEY_KP_ENTER);
	}

	/** True for Backspace and Delete, used to clear a keybind. */
	public static boolean isClearKey(KeyEvent event) {
		if (event == null)
			return false;
		return usesSdlInput()
			? matches(event.key(), SDL_SCANCODE_BACKSPACE, SDL_SCANCODE_DELETE)
			: matches(event.key(), GLFW_KEY_BACKSPACE, GLFW_KEY_DELETE);
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
			if (!(method.getName().equals("screen") || method.getName().equals("getScreen"))
				|| method.getParameterCount() != 0
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
			if (!field.getName().equals("screen") || !Screen.class.isAssignableFrom(field.getType()))
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
		try {
			owner.getClass().getMethod("setScreen", Screen.class).invoke(owner, screen);
			return true;
		} catch (NoSuchMethodException e) {
			return false;
		} catch (ReflectiveOperationException e) {
			throw new IllegalStateException("Could not change the active screen", e);
		}
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
