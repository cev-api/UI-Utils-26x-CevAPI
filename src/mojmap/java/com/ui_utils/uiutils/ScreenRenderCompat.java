package com.ui_utils.uiutils;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Proxy;
import java.lang.reflect.Type;

import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.event.Event;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;

/** Bridges the screen-render event names used by the Fabric API across 26.x. */
final class ScreenRenderCompat {
	private ScreenRenderCompat() {
	}

	static void registerBackground(Screen screen) {
		register(screen, "afterBackground", true);
	}

	static void registerForeground(Screen screen) {
		Method eventFactory = findEventFactory("afterExtract");
		if (eventFactory == null)
			eventFactory = findEventFactory("afterForeground");
		if (eventFactory == null)
			eventFactory = findEventFactory("afterRender");
		if (eventFactory == null) {
			UiUtils.LOGGER.warn("No compatible Fabric screen render event is available");
			return;
		}
		register(screen, eventFactory, false);
	}

	private static void register(Screen screen, String eventName,
		boolean background) {
		Method eventFactory = findEventFactory(eventName);
		if (eventFactory == null) {
			UiUtils.LOGGER.warn("No compatible Fabric screen {} render event is available",
				eventName);
			return;
		}
		register(screen, eventFactory, background);
	}

	private static void register(Screen screen, Method eventFactory,
		boolean background) {
		try {
			Type returnType = eventFactory.getGenericReturnType();
			if (!(returnType instanceof ParameterizedType parameterized))
				throw new IllegalStateException("Screen event listener type is unavailable");
			Type listenerType = parameterized.getActualTypeArguments()[0];
			if (!(listenerType instanceof Class<?> listenerClass)
				|| !listenerClass.isInterface())
				throw new IllegalStateException("Screen event listener is not an interface");

			Object event = eventFactory.invoke(null, screen);
			Object listener = Proxy.newProxyInstance(listenerClass.getClassLoader(),
				new Class<?>[] {listenerClass}, renderHandler(background));
			registerListener(event, listener);
		} catch (ReflectiveOperationException | RuntimeException e) {
			UiUtils.LOGGER.warn("Could not register the UI-Utils screen render event", e);
		}
	}

	@SuppressWarnings({"rawtypes", "unchecked"})
	private static void registerListener(Object event, Object listener) {
		((Event)event).register(listener);
	}

	private static Method findEventFactory(String name) {
		try {
			return ScreenEvents.class.getMethod(name, Screen.class);
		} catch (NoSuchMethodException ignored) {
			return null;
		}
	}

	private static InvocationHandler renderHandler(boolean background) {
		return (proxy, method, args) -> {
			if (method.getDeclaringClass() == Object.class) {
				return switch (method.getName()) {
					case "toString" -> "UI-Utils screen render listener";
					case "hashCode" -> System.identityHashCode(proxy);
					case "equals" -> proxy == args[0];
					default -> null;
				};
			}
			if (args != null && args.length >= 4
				&& args[0] instanceof Screen screen
				&& args[1] instanceof GuiGraphicsExtractor graphics) {
				if (background)
					UiUtilsPanels.renderBackground(screen, graphics);
				else
					UiUtilsPanels.renderForeground(screen, graphics,
						((Number)args[2]).intValue(), ((Number)args[3]).intValue());
			}
			return null;
		};
	}
}
