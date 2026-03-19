package com.ui_utils.uiutils;

import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

public final class UiUtilsSettingsScreen extends Screen {
	private final Screen parent;

	private EditBox restoreKeyField;
	private EditBox packetToolsKeyField;
	private EditBox delayToggleKeyField;
	private EditBox selectedColorHexField;

	private UiUtilsColoredButton overlayModeButton;
	private UiUtilsColoredButton disconnectMethodButton;
	private UiUtilsColoredButton timeoutSecondsButton;
	private UiUtilsColoredButton timeoutLagMethodButton;
	private UiUtilsColoredButton colorTargetButton;
	private HsvPickerWidget colorPickerWidget;
	private ColorTarget selectedTarget = ColorTarget.BUTTON_COLOR;

	public UiUtilsSettingsScreen(Screen parent) {
		super(Component.literal("UI-Utils Settings"));
		this.parent = parent;
	}

	private enum OverlayMode {
		OFF,
		HOVER,
		ALWAYS
	}

	private enum ColorTarget {
		BUTTON_COLOR("Button background"),
		BUTTON_TEXT("Button text"),
		OVERLAY_NUMBERS("Overlay numbers"),
		PACKET_HUD("Packet HUD text");

		private final String label;

		ColorTarget(String label) {
			this.label = label;
		}
	}

	@Override
	protected void init() {
		int panelWidth = 420;
		int left = this.width / 2 - panelWidth / 2;
		int y = this.height / 2 - 200;
		int rowH = 20;
		int gap = 4;

		overlayModeButton = addRenderableWidget(UiUtils.styledButton("",
			b -> cycleOverlayMode(), left, y, 205, rowH));
		refreshOverlayModeLabel();

		addRenderableWidget(makeToggleButton(left + 215, y, 205, rowH,
			"Packet HUD", () -> UiUtilsSettings.get().packetHudEnabled,
			v -> UiUtilsSettings.get().packetHudEnabled = v));
		y += rowH + gap;

		addRenderableWidget(makeToggleButton(left, y, panelWidth, rowH,
			"Log to chat", () -> UiUtilsSettings.get().logToChat,
			v -> UiUtilsSettings.get().logToChat = v));
		y += rowH + gap;

		addRenderableWidget(makeToggleButton(left, y, 205, rowH, "Bypass RP",
			() -> UiUtilsSettings.get().bypassResourcePack,
			v -> UiUtilsSettings.get().bypassResourcePack = v));
		addRenderableWidget(
			makeToggleButton(left + 215, y, 205, rowH, "Force Deny RP",
				() -> UiUtilsSettings.get().resourcePackForceDeny,
				v -> UiUtilsSettings.get().resourcePackForceDeny = v));
		y += rowH + gap;
		
		disconnectMethodButton = addRenderableWidget(UiUtils.styledButton("",
			b -> cycleDisconnectMethod(), left, y, panelWidth, rowH));
		refreshDisconnectMethodLabel();
		y += rowH + gap;
		
		timeoutSecondsButton = addRenderableWidget(UiUtils.styledButton("",
			b -> cycleTimeoutSeconds(), left, y, 205, rowH));
		refreshTimeoutSecondsLabel();
		timeoutLagMethodButton = addRenderableWidget(UiUtils.styledButton("",
			b -> cycleTimeoutLagMethod(), left + 215, y, 205, rowH));
		refreshTimeoutLagMethodLabel();
		y += rowH + gap;

		colorTargetButton = addRenderableWidget(UiUtils.styledButton("",
			b -> cycleColorTarget(), left, y, panelWidth, rowH));
		refreshColorTargetLabel();
		y += rowH + gap;

		colorPickerWidget =
			addRenderableWidget(new HsvPickerWidget(left, y, panelWidth, 100,
				rgb -> {
					setSelectedTargetColor(rgb);
					updateSelectedColorHexField();
				}));
		colorPickerWidget.setColor(getSelectedTargetColor());
		y += 100 + gap;

		selectedColorHexField = new EditBox(this.font, left, y, 205, rowH,
			Component.literal("#RRGGBB"));
		selectedColorHexField.setMaxLength(7);
		selectedColorHexField.setHint(Component.literal("#RRGGBB"));
		updateSelectedColorHexField();
		addRenderableWidget(selectedColorHexField);

		addRenderableWidget(UiUtils.styledButton("Apply selected color", b -> {
			String raw = selectedColorHexField.getValue().trim();
			if(raw.startsWith("#"))
				raw = raw.substring(1);
			if(!raw.matches("[0-9a-fA-F]{6}"))
				return;
			int rgb = Integer.parseInt(raw, 16) & 0xFFFFFF;
			setSelectedTargetColor(rgb);
			colorPickerWidget.setColor(rgb);
			updateSelectedColorHexField();
		}, left + 215, y, 205, rowH));
		y += rowH + gap;

		addRenderableWidget(new IntSlider(left, y, panelWidth, rowH,
			"Slot overlay alpha", 0, 255, UiUtilsSettings.get().slotOverlayAlpha,
			v -> UiUtilsSettings.get().slotOverlayAlpha = v));
		y += rowH + gap;

		addRenderableWidget(new IntSlider(left, y, panelWidth, rowH,
			"Slot overlay offset X", -20, 20,
			UiUtilsSettings.get().slotOverlayOffsetX,
			v -> UiUtilsSettings.get().slotOverlayOffsetX = v));
		y += rowH + gap;

		addRenderableWidget(new IntSlider(left, y, panelWidth, rowH,
			"Slot overlay offset Y", -20, 20,
			UiUtilsSettings.get().slotOverlayOffsetY,
			v -> UiUtilsSettings.get().slotOverlayOffsetY = v));
		y += rowH + gap;

		addRenderableWidget(new IntSlider(left, y, panelWidth, rowH,
			"Fabricate overlay background alpha", 0, 255,
			UiUtilsSettings.get().fabricateOverlayBgAlpha,
			v -> UiUtilsSettings.get().fabricateOverlayBgAlpha = v));
		y += rowH + gap;

		restoreKeyField = new EditBox(this.font, left, y, 205, rowH,
			Component.literal("Load GUI key"));
		restoreKeyField.setMaxLength(64);
		restoreKeyField.setValue(UiUtilsSettings.get().restoreKey);
		restoreKeyField.setHint(Component.literal("key.keyboard.v"));
		addRenderableWidget(restoreKeyField);
		addRenderableWidget(UiUtils.styledButton("Apply restore key", b -> {
			String key = restoreKeyField.getValue().trim();
			if(!key.isEmpty()) {
				UiUtilsSettings.get().restoreKey = key;
				UiUtilsSettings.save();
			}
		}, left + 215, y, 205, rowH));
		y += rowH + gap;

		packetToolsKeyField = new EditBox(this.font, left, y, 205, rowH,
			Component.literal("Packet tool key"));
		packetToolsKeyField.setMaxLength(64);
		packetToolsKeyField.setValue(UiUtilsSettings.get().packetToolsKey);
		packetToolsKeyField.setHint(Component.literal("key.keyboard.p"));
		addRenderableWidget(packetToolsKeyField);
		addRenderableWidget(UiUtils.styledButton("Apply packet key", b -> {
			String key = packetToolsKeyField.getValue().trim();
			if(!key.isEmpty()) {
				UiUtilsSettings.get().packetToolsKey = key;
				UiUtilsSettings.save();
			}
		}, left + 215, y, 205, rowH));
		y += rowH + gap;

		delayToggleKeyField = new EditBox(this.font, left, y, 205, rowH,
			Component.literal("Delay toggle key"));
		delayToggleKeyField.setMaxLength(64);
		delayToggleKeyField.setValue(UiUtilsSettings.get().delayToggleKey);
		delayToggleKeyField.setHint(Component.literal("key.keyboard.o"));
		addRenderableWidget(delayToggleKeyField);
		addRenderableWidget(UiUtils.styledButton("Apply delay key", b -> {
			String key = delayToggleKeyField.getValue().trim();
			if(!key.isEmpty()) {
				UiUtilsSettings.get().delayToggleKey = key;
				UiUtilsSettings.save();
			}
		}, left + 215, y, 205, rowH));
		y += rowH + gap;

		addRenderableWidget(UiUtils.styledButton("Done",
			b -> this.minecraft.setScreen(parent), left + 130, y, 160, rowH));
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX,
		int mouseY, float partialTicks) {
		super.extractRenderState(graphics, mouseX, mouseY, partialTicks);
	}

	private UiUtilsColoredButton makeToggleButton(int x, int y, int width,
		int height, String label, BooleanSupplier getter,
		Consumer<Boolean> setter) {
		UiUtilsColoredButton button = UiUtils.styledButton("", b -> {
			boolean next = !getter.getAsBoolean();
			setter.accept(next);
			UiUtilsSettings.save();
			b.setMessage(Component.literal(
				label + ": " + (next ? "ON" : "OFF")));
		}, x, y, width, height);
		button.setMessage(Component
			.literal(label + ": " + (getter.getAsBoolean() ? "ON" : "OFF")));
		return button;
	}

	private OverlayMode getOverlayMode() {
		if(!UiUtilsSettings.get().slotOverlayEnabled)
			return OverlayMode.OFF;
		return UiUtilsSettings.get().slotOverlayHoverOnly ? OverlayMode.HOVER
			: OverlayMode.ALWAYS;
	}

	private void cycleOverlayMode() {
		OverlayMode mode = switch(getOverlayMode()) {
			case OFF -> OverlayMode.HOVER;
			case HOVER -> OverlayMode.ALWAYS;
			case ALWAYS -> OverlayMode.OFF;
		};
		if(mode == OverlayMode.OFF) {
			UiUtilsSettings.get().slotOverlayEnabled = false;
		} else {
			UiUtilsSettings.get().slotOverlayEnabled = true;
			UiUtilsSettings.get().slotOverlayHoverOnly = mode == OverlayMode.HOVER;
		}
		UiUtilsSettings.save();
		refreshOverlayModeLabel();
	}

	private void refreshOverlayModeLabel() {
		if(overlayModeButton == null)
			return;
		OverlayMode mode = getOverlayMode();
		overlayModeButton
			.setMessage(Component.literal("Slot overlay: " + mode.name()));
	}

	private void cycleColorTarget() {
		ColorTarget[] targets = ColorTarget.values();
		int next = (selectedTarget.ordinal() + 1) % targets.length;
		selectedTarget = targets[next];
		refreshColorTargetLabel();
		int rgb = getSelectedTargetColor();
		colorPickerWidget.setColor(rgb);
		updateSelectedColorHexField();
	}
	
	private void cycleDisconnectMethod() {
		UiUtilsDisconnect.Method[] methods = UiUtilsDisconnect.Method.values();
		UiUtilsDisconnect.Method current = UiUtilsDisconnect.getConfiguredMethod();
		int next = (current.ordinal() + 1) % methods.length;
		UiUtilsDisconnect.setConfiguredMethod(methods[next]);
		refreshDisconnectMethodLabel();
	}
	
	private void cycleTimeoutSeconds() {
		int seconds = UiUtilsDisconnect.getConfiguredTimeoutSeconds();
		seconds += 5;
		if(seconds > 120)
			seconds = 5;
		UiUtilsDisconnect.setConfiguredTimeoutSeconds(seconds);
		refreshTimeoutSecondsLabel();
	}
	
	private void cycleTimeoutLagMethod() {
		UiUtilsDisconnect.LagMethod[] methods =
			UiUtilsDisconnect.LagMethod.values();
		UiUtilsDisconnect.LagMethod current =
			UiUtilsDisconnect.getConfiguredLagMethod();
		int next = (current.ordinal() + 1) % methods.length;
		UiUtilsDisconnect.setConfiguredLagMethod(methods[next]);
		refreshTimeoutLagMethodLabel();
	}
	
	private void refreshDisconnectMethodLabel() {
		if(disconnectMethodButton == null)
			return;
		disconnectMethodButton.setMessage(Component.literal(
			"Disconnect method: " + UiUtilsDisconnect.getConfiguredMethod().name()));
	}
	
	private void refreshTimeoutSecondsLabel() {
		if(timeoutSecondsButton == null)
			return;
		timeoutSecondsButton.setMessage(Component.literal(
			"Timeout seconds: " + UiUtilsDisconnect.getConfiguredTimeoutSeconds()));
	}
	
	private void refreshTimeoutLagMethodLabel() {
		if(timeoutLagMethodButton == null)
			return;
		timeoutLagMethodButton.setMessage(Component.literal(
			"Timeout lag: " + UiUtilsDisconnect.getConfiguredLagMethod().name()));
	}

	private void refreshColorTargetLabel() {
		if(colorTargetButton == null)
			return;
		colorTargetButton.setMessage(
			Component.literal("Editing color: " + selectedTarget.label));
	}

	private int getSelectedTargetColor() {
		return switch(selectedTarget) {
			case BUTTON_COLOR -> UiUtilsSettings.get().uiButtonColor;
			case BUTTON_TEXT -> UiUtilsSettings.get().uiButtonTextColor;
			case OVERLAY_NUMBERS -> UiUtilsSettings.get().slotOverlayColor;
			case PACKET_HUD -> UiUtilsSettings.get().packetHudColor;
		};
	}

	private void setSelectedTargetColor(int rgb) {
		int color = rgb & 0xFFFFFF;
		switch(selectedTarget) {
			case BUTTON_COLOR -> UiUtilsSettings.get().uiButtonColor = color;
			case BUTTON_TEXT -> UiUtilsSettings.get().uiButtonTextColor = color;
			case OVERLAY_NUMBERS -> UiUtilsSettings.get().slotOverlayColor = color;
			case PACKET_HUD -> UiUtilsSettings.get().packetHudColor = color;
		}
		UiUtilsSettings.save();
	}

	private void updateSelectedColorHexField() {
		if(selectedColorHexField != null)
			selectedColorHexField.setValue(
				String.format("#%06X", getSelectedTargetColor() & 0xFFFFFF));
	}

	@Override
	public void onClose() {
		this.minecraft.setScreen(parent);
	}

	private static final class IntSlider extends AbstractSliderButton {
		private final String label;
		private final int min;
		private final int max;
		private final java.util.function.IntConsumer onChange;

		private IntSlider(int x, int y, int w, int h, String label, int min,
			int max, int initial, java.util.function.IntConsumer onChange) {
			super(x, y, w, h, Component.empty(), normalize(initial, min, max));
			this.label = label;
			this.min = min;
			this.max = max;
			this.onChange = onChange;
			updateMessage();
		}

		@Override
		protected void updateMessage() {
			setMessage(Component.literal(label + ": " + toInt()));
		}

		@Override
		protected void applyValue() {
			onChange.accept(toInt());
			UiUtilsSettings.save();
		}

		private int toInt() {
			return Mth.clamp((int)Math.round(min + (max - min) * this.value),
				min, max);
		}

		private static double normalize(int value, int min, int max) {
			if(max <= min)
				return 0.0D;
			return Mth.clamp((value - min) / (double)(max - min), 0.0D, 1.0D);
		}
	}

	private static final class HsvPickerWidget extends AbstractWidget {
		private static final int HUE_BAR_WIDTH = 14;
		private static final int PICKER_GAP = 4;

		private final Consumer<Integer> onColorChanged;
		private int hue = 210;
		private int sat = 68;
		private int val = 89;

		private HsvPickerWidget(int x, int y, int width, int height,
			Consumer<Integer> onColorChanged) {
			super(x, y, width, height, Component.literal("HSV Picker"));
			this.onColorChanged = onColorChanged;
		}

		@Override
		protected void extractWidgetRenderState(GuiGraphicsExtractor graphics,
			int mouseX, int mouseY, float partialTicks) {
			int squareWidth = getWidth() - HUE_BAR_WIDTH - PICKER_GAP;
			int x = getX();
			int y = getY();

			for(int px = 0; px < squareWidth; px += 2) {
				int s = Math.round(px * 100F / Math.max(1, squareWidth - 1));
				for(int py = 0; py < getHeight(); py += 2) {
					int v = Math.round((getHeight() - 1 - py) * 100F
						/ Math.max(1, getHeight() - 1));
					int rgb = hsvToRgb(hue, s, v);
					graphics.fill(x + px, y + py, x + Math.min(px + 2, squareWidth),
						y + Math.min(py + 2, getHeight()), 0xFF000000 | rgb);
				}
			}

			int hueX = x + squareWidth + PICKER_GAP;
			for(int py = 0; py < getHeight(); py++) {
				int h = Math.round(py * 359F / Math.max(1, getHeight() - 1));
				int rgb = hsvToRgb(h, 100, 100);
				graphics.fill(hueX, y + py, hueX + HUE_BAR_WIDTH, y + py + 1,
					0xFF000000 | rgb);
			}

			graphics.outline(x, y, squareWidth, getHeight(), 0xFF202020);
			graphics.outline(hueX, y, HUE_BAR_WIDTH, getHeight(), 0xFF202020);

			int markerX = x
				+ Math.round(sat * (squareWidth - 1) / 100F);
			int markerY = y
				+ Math.round((100 - val) * (getHeight() - 1) / 100F);
			graphics.outline(markerX - 2, markerY - 2, 5, 5, 0xFFFFFFFF);
			graphics.outline(markerX - 3, markerY - 3, 7, 7, 0xFF000000);

			int hueMarkerY = y
				+ Math.round(hue * (getHeight() - 1) / 359F);
			graphics.outline(hueX - 1, hueMarkerY - 1, HUE_BAR_WIDTH + 2, 3,
				0xFFFFFFFF);
		}

		@Override
		public boolean mouseClicked(MouseButtonEvent context,
			boolean doubleClick) {
			if(!active || !visible || context.button() != 0)
				return false;
			return updateFromMouse(context.x(), context.y());
		}

		@Override
		public boolean mouseDragged(MouseButtonEvent context, double dragX,
			double dragY) {
			if(!active || !visible || context.button() != 0)
				return false;
			return updateFromMouse(context.x(), context.y());
		}

		@Override
		protected void updateWidgetNarration(NarrationElementOutput narration) {
			defaultButtonNarrationText(narration);
		}

		private boolean updateFromMouse(double mouseX, double mouseY) {
			int squareWidth = getWidth() - HUE_BAR_WIDTH - PICKER_GAP;
			int x = getX();
			int y = getY();
			int hueX = x + squareWidth + PICKER_GAP;

			if(mouseX >= x && mouseX < x + squareWidth && mouseY >= y
				&& mouseY < y + getHeight()) {
				int localX = Mth.clamp((int)Math.round(mouseX) - x, 0,
					squareWidth - 1);
				int localY = Mth.clamp((int)Math.round(mouseY) - y, 0,
					getHeight() - 1);
				sat = Math.round(localX * 100F / Math.max(1, squareWidth - 1));
				val = Math.round((getHeight() - 1 - localY) * 100F
					/ Math.max(1, getHeight() - 1));
				onColorChanged.accept(hsvToRgb(hue, sat, val));
				return true;
			}

			if(mouseX >= hueX && mouseX < hueX + HUE_BAR_WIDTH && mouseY >= y
				&& mouseY < y + getHeight()) {
				int localY = Mth.clamp((int)Math.round(mouseY) - y, 0,
					getHeight() - 1);
				hue = Math.round(localY * 359F / Math.max(1, getHeight() - 1));
				onColorChanged.accept(hsvToRgb(hue, sat, val));
				return true;
			}

			return false;
		}

		private void setColor(int rgb) {
			int[] hsv = rgbToHsv(rgb);
			hue = hsv[0];
			sat = hsv[1];
			val = hsv[2];
		}

		private static int[] rgbToHsv(int rgb) {
			int r = (rgb >> 16) & 0xFF;
			int g = (rgb >> 8) & 0xFF;
			int b = rgb & 0xFF;
			float rf = r / 255f;
			float gf = g / 255f;
			float bf = b / 255f;
			float max = Math.max(rf, Math.max(gf, bf));
			float min = Math.min(rf, Math.min(gf, bf));
			float delta = max - min;
			float h;
			if(delta == 0)
				h = 0;
			else if(max == rf)
				h = 60f * (((gf - bf) / delta) % 6f);
			else if(max == gf)
				h = 60f * (((bf - rf) / delta) + 2f);
			else
				h = 60f * (((rf - gf) / delta) + 4f);
			if(h < 0)
				h += 360f;
			float s = max == 0 ? 0 : (delta / max);
			float v = max;
			return new int[] {Math.round(h), Math.round(s * 100f),
				Math.round(v * 100f)};
		}

		private static int hsvToRgb(int h, int s, int v) {
			float hf = (h % 360) / 60f;
			float sf = Mth.clamp(s / 100f, 0f, 1f);
			float vf = Mth.clamp(v / 100f, 0f, 1f);
			int i = (int)Math.floor(hf) % 6;
			float f = hf - (int)Math.floor(hf);
			float p = vf * (1 - sf);
			float q = vf * (1 - f * sf);
			float t = vf * (1 - (1 - f) * sf);
			float rf = 0, gf = 0, bf = 0;
			switch(i) {
				case 0 -> {
					rf = vf;
					gf = t;
					bf = p;
				}
				case 1 -> {
					rf = q;
					gf = vf;
					bf = p;
				}
				case 2 -> {
					rf = p;
					gf = vf;
					bf = t;
				}
				case 3 -> {
					rf = p;
					gf = q;
					bf = vf;
				}
				case 4 -> {
					rf = t;
					gf = p;
					bf = vf;
				}
				case 5 -> {
					rf = vf;
					gf = p;
					bf = q;
				}
			}
			int rr = Math.round(rf * 255);
			int gg = Math.round(gf * 255);
			int bb = Math.round(bf * 255);
			return (rr & 0xFF) << 16 | (gg & 0xFF) << 8 | (bb & 0xFF);
		}
	}
}
