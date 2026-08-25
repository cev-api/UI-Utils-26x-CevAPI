package com.ui_utils.uiutils;

import com.mojang.blaze3d.platform.InputConstants;
import java.util.Locale;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

public final class UiUtilsAutoduperScreen extends Screen {
	private final Screen parent;
	private EditBox openCommandField;
	private EditBox prepareCommandField;
	private EditBox targetSlotField;
	private EditBox singleAttemptField;
	private UiUtilsColoredButton dropValidationButton;
	private UiUtilsColoredButton verboseModeButton;
	private UiUtilsColoredButton abortHoldButton;
	private UiUtilsColoredButton abortKeyButton;
	private UiUtilsColoredButton startStopButton;
	private boolean categoryPage;
	private boolean waitingForAbortKey;

	public UiUtilsAutoduperScreen(Screen parent) {
		super(Component.literal("Autoduper"));
		this.parent = parent;
	}

	@Override
	protected void init() {
		int width = Math.min(420, Math.max(240, this.width - 32));
		int left = (this.width - width) / 2;
		int row = 20;
		int gap = 4;
		boolean stacked = width < 360;
		int half = stacked ? width : (width - gap) / 2;
		int y = categoryPage ? Math.max(8,
			(this.height - getCategoryContentHeight(row, gap, stacked)) / 2)
			: Math.max(12, this.height / 2 - 116);

		if(categoryPage) {
			addCategoryToggles(left, y, width, half, row, gap, stacked);
			return;
		}

		openCommandField = new EditBox(this.font, left, y, width, row,
			Component.literal("Plugin GUI Open Command"));
		openCommandField.setMaxLength(128);
		openCommandField.setHint(Component.literal(
			"Plugin GUI Open Command (Example: /pv 1, /ec, /ah, /shop)"));
		openCommandField.setValue(UiUtilsSettings.get().autoduperOpenCommand);
		addRenderableWidget(openCommandField);
		y += row + gap;

		prepareCommandField = new EditBox(this.font, left, y, width, row,
			Component.literal("Prepare Command"));
		prepareCommandField.setMaxLength(128);
		prepareCommandField.setHint(Component.literal(
			"Optional Prepare Command"));
		prepareCommandField
			.setValue(UiUtilsSettings.get().autoduperPrepareCommand);
		addRenderableWidget(prepareCommandField);
		y += row + gap;

		y += this.font.lineHeight + 2;

		targetSlotField = new EditBox(this.font, left, y, 110, row,
			Component.literal("Target Slot (e.g. 54)"));
		targetSlotField.setMaxLength(4);
		targetSlotField.setHint(Component.literal("54"));
		targetSlotField
			.setValue(String.valueOf(UiUtilsSettings.get().autoduperTargetSlot));
		addRenderableWidget(targetSlotField);

		if(stacked) {
			y += row + gap;
			addRenderableWidget(new IntSlider(left, y, width, row, "Max Attempts",
				1, 500, UiUtilsSettings.get().autoduperMaxAttempts,
				v -> UiUtilsSettings.get().autoduperMaxAttempts = v));
			y += row + gap;
			singleAttemptField = new EditBox(this.font, left, y, width, row,
				Component.literal("Replay Attempt #"));
		} else {
			addRenderableWidget(new IntSlider(left + 120, y, 150, row,
				"Max Attempts", 1, 500, UiUtilsSettings.get().autoduperMaxAttempts,
				v -> UiUtilsSettings.get().autoduperMaxAttempts = v));
			singleAttemptField = new EditBox(this.font, left + 280, y, 140, row,
				Component.literal("Replay Attempt #"));
		}
		singleAttemptField.setMaxLength(4);
		singleAttemptField.setHint(Component.literal("0 = All"));
		singleAttemptField
			.setValue(String.valueOf(UiUtilsSettings.get().autoduperSingleAttempt));
		addRenderableWidget(singleAttemptField);
		y += row + gap;

		addRenderableWidget(new IntSlider(left, y, width, row, "Step Delay Ticks",
			1, 80, UiUtilsSettings.get().autoduperStepDelayTicks,
			v -> UiUtilsSettings.get().autoduperStepDelayTicks = v));
		y += row + gap;

		dropValidationButton = addRenderableWidget(UiUtils.styledButton("",
			b -> {
				UiUtilsSettings.get().autoduperDropValidation =
					!UiUtilsSettings.get().autoduperDropValidation;
				UiUtilsSettings.save();
				refreshDropValidationLabel();
			}, left, y, half, row));
		refreshDropValidationLabel();
		verboseModeButton = addRenderableWidget(UiUtils.styledButton("",
			b -> {
				UiUtilsSettings.get().autoduperVerboseMode =
					!UiUtilsSettings.get().autoduperVerboseMode;
				UiUtilsSettings.save();
				refreshVerboseModeLabel();
			}, left + half + gap, y, half, row));
		refreshVerboseModeLabel();
		y += row + gap;

		abortHoldButton = addRenderableWidget(UiUtils.styledButton("",
			b -> {
				UiUtilsSettings.get().autoduperAbortHoldEnabled =
					!UiUtilsSettings.get().autoduperAbortHoldEnabled;
				UiUtilsSettings.save();
				refreshAbortHoldLabel();
			}, left, y, half, row));
		refreshAbortHoldLabel();
		abortKeyButton = addRenderableWidget(UiUtils.styledButton("",
			b -> {
				waitingForAbortKey = true;
				refreshAbortKeyLabel();
			}, left + half + gap, y, half, row));
		refreshAbortKeyLabel();
		y += row + gap;

		addRenderableWidget(UiUtils.styledButton("Categories",
			b -> {
				applyFields();
				categoryPage = true;
				rebuildWidgets();
			}, left, y, width, row));
		y += row + gap;

		startStopButton = addRenderableWidget(UiUtils.styledButton("",
			b -> {
				applyFields();
				if(UiUtilsAutoduper.isRunning())
					UiUtilsAutoduper.stop("Stopped By User");
				else
					UiUtilsAutoduper.start();
				refreshStartStopLabel();
			}, left, y, width, row));
		refreshStartStopLabel();
		y += row + gap;

		if(stacked) {
			addRenderableWidget(UiUtils.styledButton("Apply", b -> applyFields(),
				left, y, width, row));
			y += row + gap;
			addRenderableWidget(UiUtils.styledButton("Done", b -> {
				applyFields();
				McCompat.setScreen(this.minecraft, parent);
			}, left, y, width, row));
		} else {
			addRenderableWidget(UiUtils.styledButton("Apply", b -> applyFields(),
				left, y, 110, row));
			addRenderableWidget(UiUtils.styledButton("Done", b -> {
				applyFields();
				McCompat.setScreen(this.minecraft, parent);
			}, left + width - 110, y, 110, row));
		}
	}

	@Override
	public boolean keyPressed(KeyEvent keyEvent) {
		if(waitingForAbortKey) {
			if(keyEvent.isEscape()) {
				waitingForAbortKey = false;
				refreshAbortKeyLabel();
				return true;
			}
			InputConstants.Key key = InputConstants.getKey(keyEvent);
			UiUtilsSettings.get().autoduperAbortKey = key.getName();
			UiUtilsSettings.save();
			waitingForAbortKey = false;
			refreshAbortKeyLabel();
			return true;
		}
		return super.keyPressed(keyEvent);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX,
		int mouseY, float partialTicks) {
		super.extractRenderState(graphics, mouseX, mouseY, partialTicks);
		if(!categoryPage && targetSlotField != null && singleAttemptField != null) {
			int labelY = targetSlotField.getY() - this.font.lineHeight - 1;
			graphics.text(this.font, "Target Slot",
				targetSlotField.getX(), labelY, 0xFFE0E0E0, false);
			graphics.text(this.font, "Replay Attempt (0 = Run All)",
				singleAttemptField.getX(), labelY, 0xFFE0E0E0, false);
		}
	}

	private void applyFields() {
		if(openCommandField == null || prepareCommandField == null
			|| targetSlotField == null || singleAttemptField == null)
			return;
		UiUtilsSettings.get().autoduperOpenCommand =
			openCommandField.getValue().trim();
		UiUtilsSettings.get().autoduperPrepareCommand =
			prepareCommandField.getValue().trim();
		if(UiUtils.isInteger(targetSlotField.getValue()))
			UiUtilsSettings.get().autoduperTargetSlot =
				Math.max(0, Integer.parseInt(targetSlotField.getValue()));
		if(UiUtils.isInteger(singleAttemptField.getValue()))
			UiUtilsSettings.get().autoduperSingleAttempt =
				Math.max(0, Integer.parseInt(singleAttemptField.getValue()));
		UiUtilsSettings.save();
	}

	private void addCategoryToggles(int left, int y, int width, int half,
		int row, int gap, boolean stacked) {
		y = addSectionLabel(left, y, "Movement");
		addRenderableWidget(makeToggle(left, y, half, row, "Move None",
			() -> UiUtilsSettings.get().autoduperMoveNone,
			v -> UiUtilsSettings.get().autoduperMoveNone = v));
		if(stacked) {
			y += row + gap;
			addRenderableWidget(makeToggle(left, y, half, row, "Move Pickup",
				() -> UiUtilsSettings.get().autoduperMovePickup,
				v -> UiUtilsSettings.get().autoduperMovePickup = v));
		} else {
			addRenderableWidget(makeToggle(left + half + gap, y, half, row,
				"Move Pickup", () -> UiUtilsSettings.get().autoduperMovePickup,
				v -> UiUtilsSettings.get().autoduperMovePickup = v));
		}
		y += row + gap;

		addRenderableWidget(makeToggle(left, y, half, row, "Move Quick",
			() -> UiUtilsSettings.get().autoduperMoveQuickMove,
			v -> UiUtilsSettings.get().autoduperMoveQuickMove = v));
		if(stacked) {
			y += row + gap;
			addRenderableWidget(makeToggle(left, y, half, row, "Move Offhand",
				() -> UiUtilsSettings.get().autoduperMoveOffhandSwap,
				v -> UiUtilsSettings.get().autoduperMoveOffhandSwap = v));
		} else {
			addRenderableWidget(makeToggle(left + half + gap, y, half, row,
				"Move Offhand", () -> UiUtilsSettings.get().autoduperMoveOffhandSwap,
				v -> UiUtilsSettings.get().autoduperMoveOffhandSwap = v));
		}
		y += row + gap;

		addRenderableWidget(makeToggle(left, y, half, row, "Move Delayed",
			() -> UiUtilsSettings.get().autoduperMoveDelayed,
			v -> UiUtilsSettings.get().autoduperMoveDelayed = v));
		if(stacked) {
			y += row + gap;
			addRenderableWidget(makeToggle(left, y, half, row,
				"Packet Delay Variants",
				() -> UiUtilsSettings.get().autoduperPacketDelayVariants,
				v -> UiUtilsSettings.get().autoduperPacketDelayVariants = v));
		} else {
			addRenderableWidget(makeToggle(left + half + gap, y, half, row,
				"Packet Delay Variants",
				() -> UiUtilsSettings.get().autoduperPacketDelayVariants,
				v -> UiUtilsSettings.get().autoduperPacketDelayVariants = v));
		}
		y += row + gap;

		y += 3;
		y = addSectionLabel(left, y, "Close");
		addRenderableWidget(makeToggle(left, y, half, row, "Close Keep Open",
			() -> UiUtilsSettings.get().autoduperCloseKeepOpen,
			v -> UiUtilsSettings.get().autoduperCloseKeepOpen = v));
		if(stacked) {
			y += row + gap;
			addRenderableWidget(makeToggle(left, y, half, row, "Close Soft",
				() -> UiUtilsSettings.get().autoduperCloseSoftClose,
				v -> UiUtilsSettings.get().autoduperCloseSoftClose = v));
		} else {
			addRenderableWidget(makeToggle(left + half + gap, y, half, row,
				"Close Soft", () -> UiUtilsSettings.get().autoduperCloseSoftClose,
				v -> UiUtilsSettings.get().autoduperCloseSoftClose = v));
		}
		y += row + gap;

		addRenderableWidget(makeToggle(left, y, half, row, "Close Pkt Stale",
			() -> UiUtilsSettings.get().autoduperClosePacketKeepScreen,
			v -> UiUtilsSettings.get().autoduperClosePacketKeepScreen = v));
		if(stacked) {
			y += row + gap;
			addRenderableWidget(makeToggle(left, y, half, row, "Close Pkt Leave",
				() -> UiUtilsSettings.get().autoduperClosePacketLeave,
				v -> UiUtilsSettings.get().autoduperClosePacketLeave = v));
		} else {
			addRenderableWidget(makeToggle(left + half + gap, y, half, row,
				"Close Pkt Leave",
				() -> UiUtilsSettings.get().autoduperClosePacketLeave,
				v -> UiUtilsSettings.get().autoduperClosePacketLeave = v));
		}
		y += row + gap;

		y += 3;
		y = addSectionLabel(left, y, "Reopen");
		addRenderableWidget(makeToggle(left, y, half, row, "Reopen None",
			() -> UiUtilsSettings.get().autoduperReopenNone,
			v -> UiUtilsSettings.get().autoduperReopenNone = v));
		if(stacked) {
			y += row + gap;
			addRenderableWidget(makeToggle(left, y, half, row, "Reopen Command",
				() -> UiUtilsSettings.get().autoduperReopenCommand,
				v -> UiUtilsSettings.get().autoduperReopenCommand = v));
		} else {
			addRenderableWidget(makeToggle(left + half + gap, y, half, row,
				"Reopen Command",
				() -> UiUtilsSettings.get().autoduperReopenCommand,
				v -> UiUtilsSettings.get().autoduperReopenCommand = v));
		}
		y += row + gap;

		addRenderableWidget(makeToggle(left, y, half, row, "Reopen Double",
			() -> UiUtilsSettings.get().autoduperReopenDoubleCommand,
			v -> UiUtilsSettings.get().autoduperReopenDoubleCommand = v));
		if(stacked) {
			y += row + gap;
			addRenderableWidget(makeToggle(left, y, half, row, "Reopen Interact",
				() -> UiUtilsSettings.get().autoduperReopenInteract,
				v -> UiUtilsSettings.get().autoduperReopenInteract = v));
		} else {
			addRenderableWidget(makeToggle(left + half + gap, y, half, row,
				"Reopen Interact",
				() -> UiUtilsSettings.get().autoduperReopenInteract,
				v -> UiUtilsSettings.get().autoduperReopenInteract = v));
		}
		y += row + gap;

		addRenderableWidget(makeToggle(left, y, width, row,
			"Reopen Stale",
			() -> UiUtilsSettings.get().autoduperReopenStaleRestore,
			v -> UiUtilsSettings.get().autoduperReopenStaleRestore = v));
		y += row + gap;

		addRenderableWidget(makeToggle(left, y, width, row, "Reopen Prepare Cmd",
			() -> UiUtilsSettings.get().autoduperReopenPrepareCommand,
			v -> UiUtilsSettings.get().autoduperReopenPrepareCommand = v));
		y += row + gap;

		addRenderableWidget(makeToggle(left, y, width, row,
			"Hybrid Command+Interact Open",
			() -> UiUtilsSettings.get().autoduperHybridOpen,
			v -> UiUtilsSettings.get().autoduperHybridOpen = v));
		y += row + gap;

		y += 3;
		y = addSectionLabel(left, y, "Finish");
		addRenderableWidget(makeToggle(left, y, half, row, "Finish Leave+Send",
			() -> UiUtilsSettings.get().autoduperFinishLeaveSend,
			v -> UiUtilsSettings.get().autoduperFinishLeaveSend = v));
		addRenderableWidget(makeToggle(left + half + gap, y, half, row,
			"Finish Disconnect+Send",
			() -> UiUtilsSettings.get().autoduperFinishDisconnectSend,
			v -> UiUtilsSettings.get().autoduperFinishDisconnectSend = v));
		y += row + gap;

		y += 8;
		addRenderableWidget(UiUtils.styledButton("Back",
			b -> {
				categoryPage = false;
				rebuildWidgets();
			}, left, y, width, row));
	}

	private int getCategoryContentHeight(int row, int gap, boolean stacked) {
		int label = 14;
		int movementRows = stacked ? 6 : 3;
		int closeRows = stacked ? 4 : 2;
		int reopenRows = stacked ? 7 : 5;
		int finishRows = stacked ? 2 : 1;
		int backRows = 1;
		return label + movementRows * (row + gap)
			+ 3 + label + closeRows * (row + gap)
			+ 3 + label + reopenRows * (row + gap)
			+ 3 + label + finishRows * (row + gap)
			+ 8 + backRows * row;
	}

	private int addSectionLabel(int left, int y, String label) {
		addRenderableWidget(new UiUtilsTextLabel(left, y, 90, 14,
			Component.literal(label)));
		return y + 14;
	}

	private UiUtilsColoredButton makeToggle(int x, int y, int width, int height,
		String label, BooleanSupplier getter, Consumer<Boolean> setter) {
		UiUtilsColoredButton button = UiUtils.styledButton("", b -> {
			boolean next = !getter.getAsBoolean();
			setter.accept(next);
			UiUtilsSettings.save();
			b.setMessage(Component.literal(
				label + ": " + (next ? "ON" : "OFF")));
		}, x, y, width, height);
		button.setMessage(Component.literal(
			label + ": " + (getter.getAsBoolean() ? "ON" : "OFF")));
		return button;
	}

	private void refreshDropValidationLabel() {
		if(dropValidationButton != null)
			dropValidationButton.setMessage(Component.literal(
				"Drop Validation: "
					+ (UiUtilsSettings.get().autoduperDropValidation ? "ON"
						: "OFF")));
	}

	private void refreshVerboseModeLabel() {
		if(verboseModeButton != null)
			verboseModeButton.setMessage(Component.literal(
				"Verbose Mode: "
					+ (UiUtilsSettings.get().autoduperVerboseMode ? "ON"
						: "OFF")));
	}

	private void refreshAbortHoldLabel() {
		if(abortHoldButton != null)
			abortHoldButton.setMessage(Component.literal(
				"Hold Key Abort: "
					+ (UiUtilsSettings.get().autoduperAbortHoldEnabled ? "ON"
						: "OFF")));
	}

	private void refreshAbortKeyLabel() {
		if(abortKeyButton == null)
			return;
		if(waitingForAbortKey) {
			abortKeyButton.setMessage(Component.literal("Press Abort Key..."));
			return;
		}
		abortKeyButton.setMessage(Component.literal(
			"Abort Key: "
				+ formatKeyName(UiUtilsSettings.get().autoduperAbortKey)));
	}

	private String formatKeyName(String keyName) {
		String raw = keyName == null || keyName.isBlank() ? "key.keyboard.space"
			: keyName;
		int dot = raw.lastIndexOf('.');
		String part = dot >= 0 && dot + 1 < raw.length() ? raw.substring(dot + 1)
			: raw;
		return part.toUpperCase(Locale.ROOT);
	}

	private void refreshStartStopLabel() {
		if(startStopButton != null)
			startStopButton.setMessage(Component.literal(
				UiUtilsAutoduper.isRunning() ? "Stop Autoduper"
					: "Start Autoduper"));
	}

	@Override
	public void onClose() {
		applyFields();
		McCompat.setScreen(this.minecraft, parent);
	}

	private static final class UiUtilsTextLabel extends AbstractWidget {
		private UiUtilsTextLabel(int x, int y, int width, int height,
			Component message) {
			super(x, y, width, height, message);
			this.active = false;
		}

		@Override
		public void extractWidgetRenderState(GuiGraphicsExtractor graphics,
			int mouseX, int mouseY, float partialTicks) {
			UiUtils.renderScaledText(graphics, Minecraft.getInstance().font,
				getMessage().getString(), getX(),
				getY() + Math.max(1,
					(getHeight() - Minecraft.getInstance().font.lineHeight) / 2),
				getWidth(), getHeight() - 2, 0xFFE0E0E0, 0.4F);
		}

		@Override
		protected void updateWidgetNarration(NarrationElementOutput narration) {
		}
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

		@Override
		public void extractWidgetRenderState(GuiGraphicsExtractor graphics,
			int mouseX, int mouseY, float partialTicks) {
			Component original = getMessage();
			setMessage(Component.empty());
			super.extractWidgetRenderState(graphics, mouseX, mouseY,
				partialTicks);
			setMessage(original);
			int textY = getY() + Math.max(1,
				(getHeight() - Minecraft.getInstance().font.lineHeight) / 2);
			UiUtils.renderScaledCenteredText(graphics,
				Minecraft.getInstance().font, original,
				getX() + getWidth() / 2, textY, getWidth() - 10,
				getHeight() - 2, 0xFFFFFFFF, 0.35F);
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
}
