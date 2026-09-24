package com.ui_utils.uiutils;

import com.mojang.blaze3d.platform.InputConstants;
import com.ui_utils.uiutils.ui.UiButton;
import com.ui_utils.uiutils.ui.UiContent;
import com.ui_utils.uiutils.ui.UiInput;
import com.ui_utils.uiutils.ui.UiModernScreen;
import com.ui_utils.uiutils.ui.UiSlider;
import com.ui_utils.uiutils.ui.UiToggle;
import java.util.Locale;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;

public final class UiUtilsAutoduperScreen extends UiModernScreen {
	private final Screen parent;
	private UiInput openCommandField;
	private UiInput prepareCommandField;
	private UiInput targetSlotField;
	private UiInput singleAttemptField;
	private UiButton abortKeyButton;
	private UiButton startStopButton;
	private boolean categoryPage;
	private boolean waitingForAbortKey;
	private int flashTicks;

	public UiUtilsAutoduperScreen(Screen parent) {
		super(Component.literal("Autoduper"));
		this.parent = parent;
	}

	@Override
	protected int naturalWidth() {
		return categoryPage ? 440 : 400;
	}

	@Override
	protected void buildContent(UiContent c) {
		if(categoryPage) {
			// Two full-width columns per row keep the toggle labels readable.
			c.columns(1);
			buildCategoryPage(c);
			return;
		}
		c.columns(2, true);

		c.section("Open");
		openCommandField = c.input(UiUtilsSettings.get().autoduperOpenCommand, null);
		openCommandField.setMaxLength(128);
		openCommandField.setHint(Component.literal(
			"Example: /pv 1, /ec, /ah, /shop"));
		prepareCommandField = c.input(
			UiUtilsSettings.get().autoduperPrepareCommand, null);
		prepareCommandField.setMaxLength(128);
		prepareCommandField.setHint(Component.literal("Optional prepare command"));

		c.section("Timing");
		targetSlotField = new UiInput(this.font, 96,
			String.valueOf(UiUtilsSettings.get().autoduperTargetSlot),
			Component.literal("54"));
		targetSlotField.setMaxLength(4);
		c.row(UiContent.text("Target Slot"), UiContent.of(targetSlotField, 2F));

		singleAttemptField = new UiInput(this.font, 96,
			String.valueOf(UiUtilsSettings.get().autoduperSingleAttempt),
			Component.literal("0 = All"));
		singleAttemptField.setMaxLength(4);
		c.row(UiContent.text("Replay Attempt (0 = all)"),
			UiContent.of(singleAttemptField, 2F));

		c.slider("Max Attempts", 1, 500,
			UiUtilsSettings.get().autoduperMaxAttempts, v -> {
				UiUtilsSettings.get().autoduperMaxAttempts = v;
				UiUtilsSettings.save();
			});
		c.slider("Step Delay Ticks", 1, 80,
			UiUtilsSettings.get().autoduperStepDelayTicks, v -> {
				UiUtilsSettings.get().autoduperStepDelayTicks = v;
				UiUtilsSettings.save();
			});

		c.section("Behaviour");
		c.spanRow(UiContent.of(toggle("Drop Validation",
				() -> UiUtilsSettings.get().autoduperDropValidation,
				v -> UiUtilsSettings.get().autoduperDropValidation = v)),
			UiContent.of(toggle("Verbose Mode",
				() -> UiUtilsSettings.get().autoduperVerboseMode,
				v -> UiUtilsSettings.get().autoduperVerboseMode = v)));
		abortKeyButton = UiButton.of("", () -> {
			waitingForAbortKey = true;
			refreshAbortKeyLabel();
			setStatus("Press a key - Esc cancels");
		});
		refreshAbortKeyLabel();
		c.spanRow(UiContent.of(toggle("Hold Key Abort",
				() -> UiUtilsSettings.get().autoduperAbortHoldEnabled,
				v -> UiUtilsSettings.get().autoduperAbortHoldEnabled = v)),
			UiContent.of(abortKeyButton));

		UiButton categoriesButton = UiButton.of("Categories", () -> {
			applyFields();
			categoryPage = true;
			scheduleRebuild();
		}).style(UiButton.Kind.SECONDARY);
		startStopButton = UiButton.of("", () -> {
			applyFields();
			if(UiUtilsAutoduper.isRunning())
				UiUtilsAutoduper.stop("Stopped By User");
			else
				UiUtilsAutoduper.start();
			refreshStartStopLabel();
		});
		refreshStartStopLabel();
		c.spanRow(UiContent.of(categoriesButton), UiContent.of(startStopButton));

		c.footerButton("Apply", UiButton.Kind.SECONDARY, this::applyFields);
		c.footerButton("Done", UiButton.Kind.PRIMARY, this::onClose);
	}

	private void buildCategoryPage(UiContent c) {
		c.section("Movement");
		c.row(UiContent.of(toggle("Move None",
				() -> UiUtilsSettings.get().autoduperMoveNone,
				v -> UiUtilsSettings.get().autoduperMoveNone = v)),
			UiContent.of(toggle("Move Pickup",
				() -> UiUtilsSettings.get().autoduperMovePickup,
				v -> UiUtilsSettings.get().autoduperMovePickup = v)));
		c.row(UiContent.of(toggle("Move Quick",
				() -> UiUtilsSettings.get().autoduperMoveQuickMove,
				v -> UiUtilsSettings.get().autoduperMoveQuickMove = v)),
			UiContent.of(toggle("Move Offhand",
				() -> UiUtilsSettings.get().autoduperMoveOffhandSwap,
				v -> UiUtilsSettings.get().autoduperMoveOffhandSwap = v)));
		c.row(UiContent.of(toggle("Move Delayed",
				() -> UiUtilsSettings.get().autoduperMoveDelayed,
				v -> UiUtilsSettings.get().autoduperMoveDelayed = v)),
			UiContent.of(toggle("Packet Delay Variants",
				() -> UiUtilsSettings.get().autoduperPacketDelayVariants,
				v -> UiUtilsSettings.get().autoduperPacketDelayVariants = v)));

		c.section("Close");
		c.row(UiContent.of(toggle("Close Keep Open",
				() -> UiUtilsSettings.get().autoduperCloseKeepOpen,
				v -> UiUtilsSettings.get().autoduperCloseKeepOpen = v)),
			UiContent.of(toggle("Close Soft",
				() -> UiUtilsSettings.get().autoduperCloseSoftClose,
				v -> UiUtilsSettings.get().autoduperCloseSoftClose = v)));
		c.row(UiContent.of(toggle("Close Pkt Stale",
				() -> UiUtilsSettings.get().autoduperClosePacketKeepScreen,
				v -> UiUtilsSettings.get().autoduperClosePacketKeepScreen = v)),
			UiContent.of(toggle("Close Pkt Leave",
				() -> UiUtilsSettings.get().autoduperClosePacketLeave,
				v -> UiUtilsSettings.get().autoduperClosePacketLeave = v)));

		c.section("Reopen");
		c.row(UiContent.of(toggle("Reopen None",
				() -> UiUtilsSettings.get().autoduperReopenNone,
				v -> UiUtilsSettings.get().autoduperReopenNone = v)),
			UiContent.of(toggle("Reopen Command",
				() -> UiUtilsSettings.get().autoduperReopenCommand,
				v -> UiUtilsSettings.get().autoduperReopenCommand = v)));
		c.row(UiContent.of(toggle("Reopen Double",
				() -> UiUtilsSettings.get().autoduperReopenDoubleCommand,
				v -> UiUtilsSettings.get().autoduperReopenDoubleCommand = v)),
			UiContent.of(toggle("Reopen Interact",
				() -> UiUtilsSettings.get().autoduperReopenInteract,
				v -> UiUtilsSettings.get().autoduperReopenInteract = v)));
		c.row(UiContent.of(toggle("Reopen Stale",
				() -> UiUtilsSettings.get().autoduperReopenStaleRestore,
				v -> UiUtilsSettings.get().autoduperReopenStaleRestore = v)),
			UiContent.of(toggle("Reopen Prepare Cmd",
				() -> UiUtilsSettings.get().autoduperReopenPrepareCommand,
				v -> UiUtilsSettings.get().autoduperReopenPrepareCommand = v)));
		c.toggle("Hybrid Command+Interact Open",
			() -> UiUtilsSettings.get().autoduperHybridOpen,
			v -> UiUtilsSettings.get().autoduperHybridOpen = v);

		c.section("Finish");
		c.row(UiContent.of(toggle("Finish Leave+Send",
				() -> UiUtilsSettings.get().autoduperFinishLeaveSend,
				v -> UiUtilsSettings.get().autoduperFinishLeaveSend = v)),
			UiContent.of(toggle("Finish Disconnect+Send",
				() -> UiUtilsSettings.get().autoduperFinishDisconnectSend,
				v -> UiUtilsSettings.get().autoduperFinishDisconnectSend = v)));

		c.footerButton("Back", UiButton.Kind.PRIMARY, () -> {
			categoryPage = false;
			scheduleRebuild();
		});
	}

	private static UiToggle toggle(String label, BooleanSupplier getter,
		Consumer<Boolean> setter) {
		return new UiToggle(label, getter, v -> {
			setter.accept(v);
			UiUtilsSettings.save();
		});
	}

	private void scheduleRebuild() {
		Minecraft.getInstance().execute(this::rebuildWidgets);
	}

	@Override
	public boolean keyPressed(KeyEvent keyEvent) {
		if(waitingForAbortKey) {
			if(keyEvent.isEscape()) {
				waitingForAbortKey = false;
				setStatus("");
				refreshAbortKeyLabel();
				return true;
			}
			InputConstants.Key key = InputConstants.getKey(keyEvent);
			UiUtilsSettings.get().autoduperAbortKey = key.getName();
			UiUtilsSettings.save();
			waitingForAbortKey = false;
			setStatus("");
			refreshAbortKeyLabel();
			return true;
		}
		return super.keyPressed(keyEvent);
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
		if(startStopButton != null) {
			boolean running = UiUtilsAutoduper.isRunning();
			startStopButton.setMessage(Component.literal(
				running ? "Stop Autoduper" : "Start Autoduper"));
			startStopButton.tint(running && (flashTicks / 8) % 2 == 0
				? 0xFFD22E3C : running ? 0xFF7F1821 : 0);
		}
	}

	@Override
	public void tick() {
		super.tick();
		flashTicks++;
		refreshStartStopLabel();
	}

	@Override
	public void onClose() {
		applyFields();
		McCompat.setScreen(this.minecraft, parent);
	}
}
