package com.ui_utils.uiutils;

import com.ui_utils.uiutils.ui.UiButton;
import com.ui_utils.uiutils.ui.UiContent;
import com.ui_utils.uiutils.ui.UiInput;
import com.ui_utils.uiutils.ui.UiSlider;
import com.ui_utils.uiutils.ui.UiModernScreen;
import com.ui_utils.uiutils.ui.UiTheme;
import com.ui_utils.uiutils.ui.UiToggle;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;

public final class UiUtilsSettingsScreen extends UiModernScreen {
	private final Screen parent;
	private boolean settingsDirty;

	private UiInput selectedColorHexField;

	private UiButton overlayModeButton;
	private UiButton packetHudButton;
	private UiButton disconnectMethodButton;
	private UiButton timeoutSecondsButton;
	private UiButton timeoutLagMethodButton;
	private UiButton colorTargetButton;
	private UiButton uiScaleButton;
	private HsvPickerWidget colorPickerWidget;
	private ColorTarget selectedTarget = ColorTarget.BUTTON_COLOR;

	public UiUtilsSettingsScreen(Screen parent) {
		super(Component.literal("UI-Utils Settings"));
		this.parent = parent;
	}

	@Override
	protected int naturalWidth() {
		return 380;
	}

	@Override
	protected boolean expandHeight() {
		return true;
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
	protected void buildContent(UiContent c) {
		// Two balanced columns, one control per cell: a cell is wide enough for the
		// full label, so nothing is truncated at normal scale.
		c.columns(2, true);

		c.section("Interface");
		uiScaleButton = c.button("", () -> {
			cycleUiScale();
			Minecraft.getInstance().execute(this::rebuildWidgets);
		});
		refreshUiScaleLabel();
		overlayModeButton = c.button("", () -> {
			cycleOverlayMode();
			refreshOverlayModeLabel();
		});
		refreshOverlayModeLabel();
		packetHudButton = c.button("", () -> {
			cyclePacketHudPosition();
			refreshPacketHudLabel();
		});
		refreshPacketHudLabel();

		c.toggle("Log to chat",
			() -> UiUtilsSettings.get().logToChat,
			v -> UiUtilsSettings.get().logToChat = v);
		c.toggle("AntiCheat detector",
			() -> UiUtilsSettings.get().antiCheatDetectorEnabled,
			v -> UiUtilsSettings.get().antiCheatDetectorEnabled = v);
		c.toggle("Bypass resource pack",
			() -> UiUtilsSettings.get().bypassResourcePack,
			v -> UiUtilsSettings.get().bypassResourcePack = v);
		c.toggle("Force deny resource pack",
			() -> UiUtilsSettings.get().resourcePackForceDeny,
			v -> UiUtilsSettings.get().resourcePackForceDeny = v);
		c.toggle("Show resource pack buttons",
			() -> UiUtilsSettings.get().showResourcePackButtons,
			v -> UiUtilsSettings.get().showResourcePackButtons = v);
		c.toggle("Steal / Store / Dump buttons",
			() -> UiUtilsSettings.get().showStealDumpButtons,
			v -> UiUtilsSettings.get().showStealDumpButtons = v);
		disconnectMethodButton = UiButton.of("", () -> {
			cycleDisconnectMethod();
			refreshDisconnectMethodLabel();
		});
		refreshDisconnectMethodLabel();
		// Keep the related navigation beside the disconnect setting.
		UiButton keybindsButton = UiButton.of("Keybinds",
			() -> McCompat.setScreen(this.minecraft, new UiUtilsKeybindsScreen(this)));
		c.spanRow(UiContent.of(disconnectMethodButton),
			UiContent.of(keybindsButton));
		c.space(6);

		c.spanRow(UiContent.of(new UiSlider("Close Delay", 0, 80,
			UiUtilsSettings.get().uiCloseDelayTicks, v -> {
				UiUtilsSettings.get().uiCloseDelayTicks = v;
				settingsDirty = true;
			})));
		c.spanRow(UiContent.of(new UiSlider("Command Delay", 0, 80,
			UiUtilsSettings.get().uiCommandDelayTicks, v -> {
				UiUtilsSettings.get().uiCommandDelayTicks = v;
				settingsDirty = true;
			})));

		c.section("Disconnect");
		timeoutSecondsButton = c.button("", () -> {
			cycleTimeoutSeconds();
			refreshTimeoutSecondsLabel();
		});
		refreshTimeoutSecondsLabel();
		timeoutLagMethodButton = c.button("", () -> {
			cycleTimeoutLagMethod();
			refreshTimeoutLagMethodLabel();
		});
		refreshTimeoutLagMethodLabel();

		c.section("Colours");
		colorTargetButton = UiButton.of("", () -> {
			cycleColorTarget();
			refreshColorTargetLabel();
		});
		refreshColorTargetLabel();
		c.centeredRow(UiContent.fixed(colorTargetButton, 250));
		colorPickerWidget = new HsvPickerWidget(0, 0, 1, 1, rgb -> {
			setSelectedTargetColor(rgb);
			updateSelectedColorHexField();
		});
		colorPickerWidget.setColor(getSelectedTargetColor());
		c.spanRow(UiContent.block(colorPickerWidget, 76, 120));
		selectedColorHexField = new UiInput(this.font, 1, colorHexText(),
			Component.literal("#RRGGBB"));
		selectedColorHexField.setMaxLength(7);
		c.centeredRow(UiContent.fixed(selectedColorHexField, 250));
		c.centeredRow(UiContent.fixed(UiButton.of("Apply selected color",
			this::applySelectedColor), 220));

		c.spanRow(UiContent.of(new UiSlider("Slot overlay alpha", 0, 255,
			UiUtilsSettings.get().slotOverlayAlpha, v -> {
				UiUtilsSettings.get().slotOverlayAlpha = v;
				settingsDirty = true;
			})));
		c.spanRow(UiContent.of(new UiSlider("Slot overlay offset X", -20, 20,
			UiUtilsSettings.get().slotOverlayOffsetX, v -> {
				UiUtilsSettings.get().slotOverlayOffsetX = v;
				settingsDirty = true;
			})));
		c.spanRow(UiContent.of(new UiSlider("Slot overlay offset Y", -20, 20,
			UiUtilsSettings.get().slotOverlayOffsetY, v -> {
				UiUtilsSettings.get().slotOverlayOffsetY = v;
				settingsDirty = true;
			})));
		c.spanRow(UiContent.of(new UiSlider("Overlay background alpha", 0, 255,
			UiUtilsSettings.get().fabricateOverlayBgAlpha, v -> {
				UiUtilsSettings.get().fabricateOverlayBgAlpha = v;
				settingsDirty = true;
			})));

		// This is a single full-width setting at the end of a two-column page.
		// Reset the flow before its category so the old column baseline cannot
		// leave a large blank gap above the key field.
		c.columns(1);
		c.compactSection("DubeDB");
		UiInput dupeDbKeyField = c.inputSlot(
			UiUtilsSettings.get().dupeDbApiKey, value -> {
				UiUtilsSettings.get().dupeDbApiKey = value;
				UiUtilsSettings.save();
			});
		dupeDbKeyField.setMaxLength(512);
		dupeDbKeyField.setHint(Component.literal("DupeDB API key"));
		dupeDbKeyField.addFormatter((value, cursor) ->
			FormattedCharSequence.forward("•".repeat(value.length()),
				Style.EMPTY.withColor(UiTheme.TEXT)));
		c.spanRow(UiContent.of(dupeDbKeyField));
		c.space(3);
		c.note("Refreshes the vulnerable plugin list once at launch when a key is set.");

		c.footerButton("Done", UiButton.Kind.PRIMARY, this::onClose);
	}

	private UiToggle toggle(String label, BooleanSupplier getter,
		Consumer<Boolean> setter) {
		return new UiToggle(label, getter, v -> {
			setter.accept(v);
			UiUtilsSettings.save();
		});
	}

	private String colorHexText() {
		return String.format("#%06X", getSelectedTargetColor() & 0xFFFFFF);
	}

	private void applySelectedColor() {
		String raw = selectedColorHexField.getValue().trim();
		if (raw.startsWith("#"))
			raw = raw.substring(1);
		if (!raw.matches("[0-9a-fA-F]{6}"))
			return;
		int rgb = Integer.parseInt(raw, 16) & 0xFFFFFF;
		setSelectedTargetColor(rgb);
		if (colorPickerWidget != null)
			colorPickerWidget.setColor(rgb);
		updateSelectedColorHexField();
	}

	private void cycleUiScale() {
		int[] options = {0, 75, 100, 125, 150};
		int current = UiUtilsSettings.get().uiScalePercent;
		int next = options[0];
		for (int i = 0; i < options.length; i++)
			if (options[i] == current)
				next = options[(i + 1) % options.length];
		UiUtilsSettings.get().uiScalePercent = next;
		UiUtilsSettings.save();
	}

	private String uiScaleText() {
		int percent = UiUtilsSettings.get().uiScalePercent;
		return percent <= 0 ? "Auto" : percent + "%";
	}

	private void refreshUiScaleLabel() {
		if (uiScaleButton != null)
			uiScaleButton.setMessage(Component.literal("UI scale: " + uiScaleText()));
	}

	@Override
	public boolean keyPressed(KeyEvent keyEvent) {
		return super.keyPressed(keyEvent);
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

	private void cyclePacketHudPosition() {
		UiUtilsSettings.get().packetHudPosition =
			UiUtilsSettings.get().packetHudPosition.next();
		UiUtilsSettings.get().packetHudEnabled =
			UiUtilsSettings.get().packetHudPosition.isEnabled();
		UiUtilsSettings.save();
		refreshPacketHudLabel();
	}

	private void refreshPacketHudLabel() {
		if(packetHudButton == null)
			return;
		packetHudButton.setMessage(Component.literal(
			"Packet HUD: " + UiUtilsSettings.get().packetHudPosition.label()));
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
			"Disconnect: " + UiUtilsDisconnect.getConfiguredMethod().name()));
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
		settingsDirty = true;
	}

	private void updateSelectedColorHexField() {
		if(selectedColorHexField != null)
			selectedColorHexField.setValue(
				String.format("#%06X", getSelectedTargetColor() & 0xFFFFFF));
	}

	@Override
	public void onClose() {
		flushPendingSettingsSave();
		McCompat.setScreen(this.minecraft, parent);
	}

	private void flushPendingSettingsSave() {
		if(!settingsDirty)
			return;
		UiUtilsSettings.save();
		settingsDirty = false;
	}

	private static final class HsvPickerWidget extends AbstractWidget {
		private static final int HUE_BAR_WIDTH = 14;
		private static final int PICKER_GAP = 4;
		private static final int TARGET_CELLS = 2800;

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
			int squareStep = renderStep(squareWidth, getHeight());
			int hueStep = Math.max(1, squareStep - 1);

			for(int px = 0; px < squareWidth; px += squareStep) {
				int s = Math.round(px * 100F / Math.max(1, squareWidth - 1));
				int x2 = x + Math.min(px + squareStep, squareWidth);
				for(int py = 0; py < getHeight(); py += squareStep) {
					int v = Math.round((getHeight() - 1 - py) * 100F
						/ Math.max(1, getHeight() - 1));
					int rgb = hsvToRgb(hue, s, v);
					graphics.fill(x + px, y + py, x2,
						y + Math.min(py + squareStep, getHeight()),
						0xFF000000 | rgb);
				}
			}

			int hueX = x + squareWidth + PICKER_GAP;
			for(int py = 0; py < getHeight(); py += hueStep) {
				int h = Math.round(py * 359F / Math.max(1, getHeight() - 1));
				int rgb = hsvToRgb(h, 100, 100);
				graphics.fill(hueX, y + py, hueX + HUE_BAR_WIDTH,
					y + Math.min(py + hueStep, getHeight()),
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
			if(!active || !visible || context.button() != McCompat.LEFT_BUTTON)
				return false;
			return updateFromMouse(context.x(), context.y());
		}

		@Override
		public boolean mouseDragged(MouseButtonEvent context, double dragX,
			double dragY) {
			if(!active || !visible || context.button() != McCompat.LEFT_BUTTON)
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

		private static int renderStep(int width, int height) {
			int area = Math.max(1, width * Math.max(1, height));
			int step = (int)Math.ceil(Math.sqrt(area / (double)TARGET_CELLS));
			return Mth.clamp(step, 2, 8);
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
