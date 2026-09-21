package com.ui_utils.uiutils;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.KeyEvent;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.network.HashedPatchMap;
import net.minecraft.network.HashedStack;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ServerboundContainerButtonClickPacket;
import net.minecraft.network.protocol.game.ServerboundContainerClickPacket;
import net.minecraft.util.Mth;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.ItemStack;
import it.unimi.dsi.fastutil.ints.Int2ObjectArrayMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;

/**
 * The Fabricate Packet and GUI Tools panels.
 * <p>
 * Both are drawn as floating panels on top of whatever screen is open and are
 * not tied to container screens: the widgets are attached to the current screen
 * in {@link #attach}, so they work on the inventory, chat, settings and every
 * other screen as well as on chests.
 */
public final class UiUtilsPanels {
	private static final int MODE_CLICK_SLOT = 0;
	private static final int MODE_BUTTON_CLICK = 1;
	private static final int MODE_TIMED_SPAM = 2;

	private static final int OVERLAY_WIDTH = 260;
	private static final int MODE_BUTTON_WIDTH = 82;
	private static final int MODE_BUTTON_GAP = 6;
	private static final int FIELD_WIDTH = 118;
	private static final int FIELD_GAP = 8;
	private static final int ROW_SPACING = 32;
	private static final int LABEL_OFFSET = 10;
	private static final int SEND_BUTTON_WIDTH = 120;
	private static final int DRAG_BAR_HEIGHT = 10;
	private static final int INFO_LINE_HEIGHT = 10;
	private static final int INFO_LINES = 3;

	private static final int OVERLAY_TITLE_TO_MODES = 24;
	private static final int OVERLAY_INFO_TOP = 58;
	private static final int OVERLAY_INFO_TO_CONTENT = 22;
	private static final int OVERLAY_ACTION_TO_FIELDS = 40;

	private static final int TOOLS_WIDTH = 300;
	private static final int TOOLS_ROW_SPACING = 22;
	private static final int TOOLS_ROW_HEIGHT = 18;
	private static final int TOOLS_ROW_GAP = 6;
	private static final int TOOLS_INFO_LINES = 3;
	private static final int FABRICATOR_TEXT_WIDTH = OVERLAY_WIDTH - 12;
	private static final int TOOLS_TEXT_WIDTH = TOOLS_WIDTH - 12;
	/** How long a panel status line stays on screen before clearing. */
	private static final long STATUS_LINGER_MS = 4000L;

	// Fabricator state
	private static boolean fabricatorInitialized;
	private static int fabricateMode = MODE_CLICK_SLOT;
	private static UiUtilsColoredButton modeClickSlotButton;
	private static UiUtilsColoredButton modeButtonClickButton;
	private static UiUtilsColoredButton modeTimedSpamButton;
	private static UiUtilsDropdown actionDropdown;
	private static EditBox clickSyncIdField;
	private static EditBox clickRevisionField;
	private static EditBox clickSlotField;
	private static EditBox clickButtonField;
	private static UiUtilsColoredButton clickDelayToggle;
	private static EditBox clickTimesField;
	private static UiUtilsColoredButton clickSendButton;
	private static EditBox buttonSyncIdField;
	private static EditBox buttonIdField;
	private static UiUtilsColoredButton buttonDelayToggle;
	private static EditBox buttonTimesField;
	private static UiUtilsColoredButton buttonSendButton;
	private static EditBox timedSlotField;
	private static EditBox timedButtonField;
	private static EditBox timedCountField;
	private static EditBox timedIntervalField;
	private static UiUtilsColoredButton timedStartButton;
	private static boolean clickDelayEnabled;
	private static boolean buttonDelayEnabled;
	private static String fabricateStatus = "";
	private static int fabricateStatusColor = 0xFFAAAAAA;
	private static long fabricateStatusAt;
	private static final List<UiUtilsColoredButton> fabricatorButtons = new ArrayList<>();
	private static int overlayX;
	private static int overlayY;
	private static int overlayBottomY;
	private static int overlayActionRowY;
	private static int overlayStatusRowY;
	private static boolean overlayDragging;
	private static int overlayDragX;
	private static int overlayDragY;

	// GUI Tools state
	private static boolean toolsInitialized;
	private static final List<AbstractWidget> toolsWidgets = new ArrayList<>();
	private static final List<List<AbstractWidget>> toolsRows = new ArrayList<>();
	private static int toolsX;
	private static int toolsY;
	private static int toolsBottomY;
	private static boolean toolsDragging;
	private static int toolsDragX;
	private static int toolsDragY;
	private static String toolsStatus = "";
	private static int toolsStatusColor = 0xFFAAAAAA;
	private static long toolsStatusAt;

	// Shared input state
	private static Screen attachedScreen;
	private static double mouseX;
	private static double mouseY;
	private static boolean loggedAttach;
	// Widgets created during attach, handed to the screen mixin to register.
	private static final List<AbstractWidget> pendingAdd = new ArrayList<>();
	// Widgets currently registered on a screen, so a rebuild can drop them first.
	private static final List<AbstractWidget> registered = new ArrayList<>();

	private UiUtilsPanels() {
	}

	/** True while either panel is showing. */
	public static boolean anyOpen() {
		return UiUtilsState.fabricateOverlayOpen || UiUtilsState.guiToolsOverlayOpen;
	}

	/**
	 * Only container and inventory screens host the panels. Chat, the pause and
	 * options menus, the level-loading screens used during a portal transition
	 * and other mods' screens are all excluded so the panels cannot float over
	 * them.
	 */
	public static boolean isAllowedScreen(Screen screen) {
		if (screen == null)
			return false;
		// Container screens, which includes the player and creative inventories.
		return screen instanceof AbstractContainerScreen;
	}

	public static void toggleFabricator(Screen screen) {
		if (!UiUtilsState.isUiEnabled())
			return;
		UiUtilsState.fabricateOverlayOpen = !UiUtilsState.fabricateOverlayOpen;
		if (UiUtilsState.fabricateOverlayOpen && !isAllowedScreen(screen))
			UiUtils.chatIfEnabled(
				"Fabricate Packet opened; open a container or your inventory to use it");
		else
			UiUtils.chatIfEnabled("Fabricate Packet: "
				+ (UiUtilsState.fabricateOverlayOpen ? "opened" : "closed"));
	}

	public static void toggleTools(Screen screen) {
		if (!UiUtilsState.isUiEnabled())
			return;
		UiUtilsState.guiToolsOverlayOpen = !UiUtilsState.guiToolsOverlayOpen;
		if (UiUtilsState.guiToolsOverlayOpen && !isAllowedScreen(screen))
			UiUtils.chatIfEnabled(
				"GUI Tools opened; open a container or your inventory to use it");
		else
			UiUtils.chatIfEnabled(
				"GUI Tools: " + (UiUtilsState.guiToolsOverlayOpen ? "opened" : "closed"));
	}

	/**
	 * Builds the panel widgets for the given screen and registers them through
	 * Fabric's widget list, which is backed by the screen's own renderable,
	 * narratable and child lists.
	 */
	public static void attach(Screen screen) {
		if (!isAllowedScreen(screen))
			return;
		attachedScreen = screen;
		pendingAdd.clear();
		// Drop any previous set first: a stale set would keep rendering at its old
		// coordinates while the panel box moves, which looks like frozen buttons.
		dropRegistered(screen);
		initFabricator(screen);
		initTools(screen);
		registerPending(screen);
	}

	private static void dropRegistered(Screen screen) {
		if (registered.isEmpty())
			return;
		try {
			Screens.getWidgets(screen).removeAll(registered);
		} catch (Throwable ignored) {
		}
		registered.clear();
	}

	/** True when the current panel widgets are live children of this screen. */
	private static boolean panelsRegisteredOn(Screen screen) {
		if (registered.isEmpty())
			return false;
		try {
			return screen.children().contains(registered.get(0));
		} catch (Throwable ignored) {
			return true;
		}
	}

	private static void registerPending(Screen screen) {
		if (pendingAdd.isEmpty())
			return;
		List<AbstractWidget> pending = new ArrayList<>(pendingAdd);
		pendingAdd.clear();
		Screens.getWidgets(screen).addAll(pending);
		registered.addAll(pending);
		if (!loggedAttach) {
			loggedAttach = true;
			UiUtils.LOGGER.info(
				"UI-Utils panels registered on {} ({} fabricator widgets, {} tools widgets)",
				screen.getClass().getSimpleName(), fabricatorButtons.size(),
				toolsWidgets.size());
		}
	}

	/** Called on a fresh screen so a resize rebuilds the widgets. */
	public static void onScreenInit() {
		fabricatorInitialized = false;
		toolsInitialized = false;
		overlayDragging = false;
		toolsDragging = false;
		// Screen#init clears its widget lists, so the old set is gone with it.
		registered.clear();
		pendingAdd.clear();
	}

	public static void update(Screen screen) {
		attachedScreen = screen;
		expireStatuses();
		updateFabricatorVisibility();
		updateToolsVisibility();
	}

	/** Panel status lines fade out on their own so they do not linger forever. */
	private static void expireStatuses() {
		long now = System.currentTimeMillis();
		if (!fabricateStatus.isEmpty()
			&& now - fabricateStatusAt > STATUS_LINGER_MS)
			fabricateStatus = "";
		if (!toolsStatus.isEmpty() && now - toolsStatusAt > STATUS_LINGER_MS)
			toolsStatus = "";
	}

	public static void renderBackground(Screen screen, GuiGraphicsExtractor graphics) {
		if (!isAllowedScreen(screen))
			return;
		attachedScreen = screen;
		// Self-heal if the host event never reached this screen. Guarded on the
		// registered set so this can never add a second set of widgets.
		if (!panelsRegisteredOn(screen))
			attach(screen);
		update(screen);
		renderFabricatorBackground(graphics);
		renderToolsBackground(graphics);
	}

	public static void renderForeground(Screen screen, GuiGraphicsExtractor graphics,
		int mouseX, int mouseY) {
		UiUtilsPanels.mouseX = mouseX;
		UiUtilsPanels.mouseY = mouseY;
		// Only draw what was actually registered, so a failed attach cannot leave
		// half a panel floating with no widgets.
		if (!isAllowedScreen(screen))
			return;
		if (fabricatorInitialized && UiUtilsState.fabricateOverlayOpen) {
			drawFabricatorForeground(graphics);
			if (actionDropdown != null)
				actionDropdown.renderExpandedList(graphics, mouseX, mouseY);
		}
		if (toolsInitialized && UiUtilsState.guiToolsOverlayOpen)
			drawToolsForeground(graphics);
	}

	/**
	 * Called before widget dispatch for a left press. Only the drag bars are
	 * consumed, because they hold no widgets; everything else is left to the
	 * normal dispatch so the buttons and text fields keep working.
	 */
	public static boolean onMousePress(double mx, double my, int button) {
		if (!anyOpen() || button != McCompat.LEFT_BUTTON)
			return false;
		mouseX = mx;
		mouseY = my;
		if (UiUtilsState.fabricateOverlayOpen
			&& isOverDragBar(mx, my, overlayX, overlayY, OVERLAY_WIDTH)) {
			overlayDragging = true;
			overlayDragX = (int)Math.round(mx - overlayX);
			overlayDragY = (int)Math.round(my - overlayY);
			return true;
		}
		if (UiUtilsState.guiToolsOverlayOpen
			&& isOverDragBar(mx, my, toolsX, toolsY, TOOLS_WIDTH)) {
			toolsDragging = true;
			toolsDragX = (int)Math.round(mx - toolsX);
			toolsDragY = (int)Math.round(my - toolsY);
			return true;
		}
		// A press anywhere else closes an open action list.
		if (actionDropdown != null && actionDropdown.isExpanded()
			&& !actionDropdown.isMouseOver(mx, my))
			actionDropdown.setExpanded(false);
		return false;
	}

	/** Ends a panel drag. A release is never consumed, so this returns nothing. */
	public static void onMouseRelease() {
		overlayDragging = false;
		toolsDragging = false;
	}

	/**
	 * Applies the drag on every mouse move so the widgets follow the panel even
	 * if the render hook is delayed. Never consumes, so widgets keep their drags.
	 */
	public static void onMouseDrag(double mx, double my) {
		if (!overlayDragging && !toolsDragging)
			return;
		mouseX = mx;
		mouseY = my;
		layoutFabricator();
		layoutTools();
	}

	/** Esc closes an open dropdown; otherwise leaves the key alone. */
	public static boolean onKey(KeyEvent event) {
		if (actionDropdown == null || !actionDropdown.isExpanded())
			return false;
		if (event.isEscape() || McCompat.isConfirmationKey(event)) {
			actionDropdown.setExpanded(false);
			return true;
		}
		return false;
	}

	/**
	 * Moves a panel while its drag bar is held. The press and release edges come
	 * from the screen mouse events; this only tracks the cursor position.
	 */
	public static void onClientTick(Minecraft mc) {
		if (!anyOpen() || mc == null) {
			overlayDragging = false;
			toolsDragging = false;
			return;
		}
		if (!overlayDragging && !toolsDragging)
			return;
		if (mc.mouseHandler != null) {
			mouseX = mc.mouseHandler.getScaledXPos(mc.getWindow());
			mouseY = mc.mouseHandler.getScaledYPos(mc.getWindow());
		}
	}

	public static Minecraft minecraft() {
		return Minecraft.getInstance();
	}

	private static boolean isOverDragBar(double mx, double my, int px, int py,
		int panelWidth) {
		return mx >= px && mx <= px + panelWidth && my >= py
			&& my <= py + DRAG_BAR_HEIGHT;
	}

	// ### Fabricator panel ###

	private static void initFabricator(Screen screen) {
		Font font = screen.getFont();
		if (fabricatorInitialized && allStillAttached(screen, fabricatorButtons))
			return;
		fabricatorInitialized = false;
		fabricatorButtons.clear();

		modeClickSlotButton = add(fabricatorButtons, UiUtils.styledButton(
			"Click Slot", b -> switchMode(MODE_CLICK_SLOT), 0, 0, MODE_BUTTON_WIDTH, 20));
		modeButtonClickButton = add(fabricatorButtons,
			UiUtils.styledButton("Button Click", b -> switchMode(MODE_BUTTON_CLICK), 0, 0,
				MODE_BUTTON_WIDTH, 20));
		modeTimedSpamButton = add(fabricatorButtons, UiUtils.styledButton(
			"Timed Spam", b -> switchMode(MODE_TIMED_SPAM), 0, 0, MODE_BUTTON_WIDTH, 20));

		List<String> actions = new ArrayList<>();
		for (ContainerInput input : ContainerInput.values())
			actions.add(input.name());
		actionDropdown = new UiUtilsDropdown(font, 0, 0, FIELD_WIDTH * 2 + FIELD_GAP, 20,
			"Action", actions);
		pendingAdd.add(actionDropdown);

		clickSyncIdField = field(font, "Sync Id");
		clickRevisionField = field(font, "Revision");
		clickSlotField = field(font, "Slot", "0");
		clickButtonField = field(font, "Button", "0");
		clickDelayEnabled = false;
		clickDelayToggle = add(fabricatorButtons, UiUtils.styledButton("Delay: OFF", b -> {
			clickDelayEnabled = !clickDelayEnabled;
			b.setMessage(
				Component.literal("Delay: " + (clickDelayEnabled ? "ON" : "OFF")));
		}, 0, 0, FIELD_WIDTH, 20));
		clickTimesField = field(font, "Times to send", "1");
		clickSendButton = add(fabricatorButtons, UiUtils.styledButton("Send",
			b -> sendClickSlot(), 0, 0, SEND_BUTTON_WIDTH, 20));

		buttonSyncIdField = field(font, "Sync Id");
		buttonIdField = field(font, "Button Id", "0");
		buttonDelayEnabled = false;
		buttonDelayToggle = add(fabricatorButtons, UiUtils.styledButton("Delay: OFF", b -> {
			buttonDelayEnabled = !buttonDelayEnabled;
			b.setMessage(
				Component.literal("Delay: " + (buttonDelayEnabled ? "ON" : "OFF")));
		}, 0, 0, FIELD_WIDTH, 20));
		buttonTimesField = field(font, "Times to send", "1");
		buttonSendButton = add(fabricatorButtons, UiUtils.styledButton("Send",
			b -> sendButtonClick(), 0, 0, SEND_BUTTON_WIDTH, 20));

		timedSlotField = field(font, "Slot", "0");
		timedButtonField = field(font, "Button", "0");
		timedCountField = field(font, "Clicks", "40");
		timedIntervalField = field(font, "Interval (ms)", "50");
		timedStartButton = add(fabricatorButtons, UiUtils.styledButton("Start Spam",
			b -> toggleTimedSpam(), 0, 0, SEND_BUTTON_WIDTH, 20));

		fabricatorInitialized = true;
		switchMode(fabricateMode);
		updateFabricatorVisibility();
	}

	private static UiUtilsColoredButton add(List<UiUtilsColoredButton> tracked,
		UiUtilsColoredButton widget) {
		pendingAdd.add(widget);
		tracked.add(widget);
		return widget;
	}

	private static EditBox field(Font font, String label) {
		return field(font, label, "");
	}

	private static EditBox field(Font font, String label, String value) {
		EditBox box = new EditBox(font, 0, 0, FIELD_WIDTH, 20, Component.literal(label));
		if (!value.isEmpty())
			box.setValue(value);
		pendingAdd.add(box);
		return box;
	}

	private static boolean allStillAttached(Screen screen, List<UiUtilsColoredButton> widgets) {
		if (widgets.isEmpty())
			return false;
		try {
			return screen.children().contains(widgets.get(0));
		} catch (Throwable ignored) {
			return true;
		}
	}

	private static void switchMode(int mode) {
		fabricateMode = mode;
		if (modeClickSlotButton != null)
			modeClickSlotButton.setMessage(Component
				.literal("Click Slot" + (mode == MODE_CLICK_SLOT ? " \u2713" : "")));
		if (modeButtonClickButton != null)
			modeButtonClickButton.setMessage(Component
				.literal("Button Click" + (mode == MODE_BUTTON_CLICK ? " \u2713" : "")));
		if (modeTimedSpamButton != null)
			modeTimedSpamButton.setMessage(Component
				.literal("Timed Spam" + (mode == MODE_TIMED_SPAM ? " \u2713" : "")));

		boolean showClick = mode == MODE_CLICK_SLOT;
		boolean showButton = mode == MODE_BUTTON_CLICK;
		boolean showTimed = mode == MODE_TIMED_SPAM;

		show(actionDropdown, showClick || showTimed);
		show(clickSyncIdField, showClick);
		show(clickRevisionField, showClick);
		show(clickSlotField, showClick);
		show(clickButtonField, showClick);
		show(clickDelayToggle, showClick);
		show(clickTimesField, showClick);
		show(clickSendButton, showClick);

		show(buttonSyncIdField, showButton);
		show(buttonIdField, showButton);
		show(buttonDelayToggle, showButton);
		show(buttonTimesField, showButton);
		show(buttonSendButton, showButton);

		show(timedSlotField, showTimed);
		show(timedButtonField, showTimed);
		show(timedCountField, showTimed);
		show(timedIntervalField, showTimed);
		show(timedStartButton, showTimed);

		if (actionDropdown != null && !showClick && !showTimed)
			actionDropdown.setExpanded(false);
	}

	private static void show(AbstractWidget widget, boolean visible) {
		if (widget == null)
			return;
		widget.visible = visible;
		widget.active = visible;
	}

	private static void updateFabricatorVisibility() {
		if (!fabricatorInitialized)
			return;
		boolean visible = UiUtilsState.isUiEnabled()
			&& UiUtilsState.fabricateOverlayOpen && isAllowedScreen(attachedScreen);
		show(modeClickSlotButton, visible);
		show(modeButtonClickButton, visible);
		show(modeTimedSpamButton, visible);
		if (!visible) {
			hideFabricator();
			return;
		}
		switchMode(fabricateMode);
		updateSyncFields();
		layoutFabricator();
	}

	/**
	 * Keeps the captured sync id and revision in step with the open container.
	 * A field the user is currently typing in is left alone.
	 */
	private static void updateSyncFields() {
		Minecraft mc = Minecraft.getInstance();
		AbstractContainerMenu menu = mc.player == null ? null
			: mc.player.containerMenu;
		if (menu == null)
			return;
		String syncId = String.valueOf(menu.containerId);
		String revision = String.valueOf(menu.getStateId());
		if (clickSyncIdField != null && !clickSyncIdField.isFocused())
			clickSyncIdField.setValue(syncId);
		if (clickRevisionField != null && !clickRevisionField.isFocused())
			clickRevisionField.setValue(revision);
		if (buttonSyncIdField != null && !buttonSyncIdField.isFocused())
			buttonSyncIdField.setValue(syncId);
	}

	private static void hideFabricator() {
		if (actionDropdown != null) {
			actionDropdown.visible = false;
			actionDropdown.active = false;
			actionDropdown.setExpanded(false);
		}
		AbstractWidget[] boxes = {clickSyncIdField, clickRevisionField, clickSlotField,
			clickButtonField, clickTimesField, buttonSyncIdField, buttonIdField,
			buttonTimesField, timedSlotField, timedButtonField, timedCountField,
			timedIntervalField};
		for (AbstractWidget widget : boxes) {
			if (widget == null)
				continue;
			widget.visible = false;
			widget.active = false;
			widget.setX(-2000);
			widget.setY(-2000);
		}
		for (UiUtilsColoredButton widget : fabricatorButtons) {
			show(widget, false);
			widget.setX(-2000);
			widget.setY(-2000);
		}
	}

	private static void layoutFabricator() {
		if (!fabricatorInitialized || attachedScreen == null)
			return;
		int panelWidth = attachedScreen.width;
		int overlayWidth = panelWidth == 0 ? OVERLAY_WIDTH : panelWidth;
		int x = UiUtilsState.fabricateOverlayX >= 0 ? UiUtilsState.fabricateOverlayX
			: overlayWidth - OVERLAY_WIDTH - 8;
		x = Mth.clamp(x, 4, Math.max(4, overlayWidth - OVERLAY_WIDTH - 4));
		int y = UiUtilsState.fabricateOverlayY >= 0 ? UiUtilsState.fabricateOverlayY : 8;
		y = Mth.clamp(y, 4, Math.max(4, attachedScreen.height - 40));

		if (overlayDragging) {
			x = Mth.clamp((int)mouseX - overlayDragX, 4,
				Math.max(4, overlayWidth - OVERLAY_WIDTH - 4));
			y = Mth.clamp((int)mouseY - overlayDragY, 4,
				Math.max(4, attachedScreen.height - 40));
			UiUtilsState.fabricateOverlayX = x;
			UiUtilsState.fabricateOverlayY = y;
		}

		int modeGroup = MODE_BUTTON_WIDTH * 3 + MODE_BUTTON_GAP * 2;
		int modeStart = x + (OVERLAY_WIDTH - modeGroup) / 2;
		int modeY = y + OVERLAY_TITLE_TO_MODES;
		place(modeClickSlotButton, modeStart, modeY);
		place(modeButtonClickButton, modeStart + MODE_BUTTON_WIDTH + MODE_BUTTON_GAP,
			modeY);
		place(modeTimedSpamButton,
			modeStart + (MODE_BUTTON_WIDTH + MODE_BUTTON_GAP) * 2, modeY);

		int rowY = y + OVERLAY_INFO_TOP + INFO_LINES * INFO_LINE_HEIGHT
			+ OVERLAY_INFO_TO_CONTENT;
		overlayActionRowY = rowY;
		int left = x + (OVERLAY_WIDTH - (FIELD_WIDTH * 2 + FIELD_GAP)) / 2;
		int right = left + FIELD_WIDTH + FIELD_GAP;
		place(actionDropdown, left, rowY);

		int fieldsTop = fabricateMode == MODE_BUTTON_CLICK ? rowY
			: rowY + OVERLAY_ACTION_TO_FIELDS;

		int clickY = fieldsTop;
		placePair(clickSyncIdField, clickRevisionField, left, right, clickY);
		clickY += ROW_SPACING;
		placePair(clickSlotField, clickButtonField, left, right, clickY);
		clickY += ROW_SPACING;
		placePlacePairDelayed(clickDelayToggle, clickTimesField, left, right, clickY);
		clickY += ROW_SPACING;
		place(clickSendButton, x + (OVERLAY_WIDTH - SEND_BUTTON_WIDTH) / 2, clickY);

		int buttonY = fieldsTop;
		placePair(buttonSyncIdField, buttonIdField, left, right, buttonY);
		buttonY += ROW_SPACING;
		placePlacePairDelayed(buttonDelayToggle, buttonTimesField, left, right, buttonY);
		buttonY += ROW_SPACING;
		place(buttonSendButton, x + (OVERLAY_WIDTH - SEND_BUTTON_WIDTH) / 2, buttonY);

		int timedY = fieldsTop;
		placePair(timedSlotField, timedButtonField, left, right, timedY);
		timedY += ROW_SPACING;
		placePair(timedCountField, timedIntervalField, left, right, timedY);
		timedY += ROW_SPACING;
		place(timedStartButton, x + (OVERLAY_WIDTH - SEND_BUTTON_WIDTH) / 2, timedY);

		overlayStatusRowY = switch (fabricateMode) {
			case MODE_BUTTON_CLICK -> buttonY + 28;
			case MODE_TIMED_SPAM -> timedY + 28;
			default -> clickY + 28;
		};

		overlayX = x;
		overlayY = y;
		overlayBottomY = overlayStatusRowY + 12;
	}

	private static void place(AbstractWidget widget, int x, int y) {
		if (widget == null)
			return;
		widget.setX(x);
		widget.setY(y);
	}

	private static void placePair(AbstractWidget a, AbstractWidget b, int left, int right,
		int y) {
		place(a, left, y);
		place(b, right, y);
	}

	private static void placePlacePairDelayed(AbstractWidget a, AbstractWidget b, int left,
		int right, int y) {
		if (a != null)
			a.setWidth(FIELD_WIDTH);
		if (b != null)
			b.setWidth(FIELD_WIDTH);
		place(a, left, y);
		place(b, right, y);
	}

	private static void renderFabricatorBackground(GuiGraphicsExtractor graphics) {
		if (!fabricatorInitialized || !UiUtilsState.fabricateOverlayOpen)
			return;
		int alpha = UiUtilsSettings.get().fabricateOverlayBgAlpha;
		if (alpha <= 0)
			return;
		int x1 = Math.max(0, overlayX - 6);
		int y1 = Math.max(0, overlayY - 6);
		int x2 = Math.min(attachedScreen.width, overlayX + OVERLAY_WIDTH + 6);
		int y2 = Math.min(attachedScreen.height, overlayBottomY + 6);
		graphics.fill(x1, y1, x2, y2, alpha << 24);
	}

	private static void drawFabricatorForeground(GuiGraphicsExtractor graphics) {
		Font font = font();
		Minecraft mc = Minecraft.getInstance();
		graphics.text(font, "Fabricate Packet", overlayX + 6, overlayY + 4, 0xFFEAEAEA,
			false);
		drawGrip(graphics, overlayX, overlayY, OVERLAY_WIDTH);

		AbstractContainerMenu menu = mc.player == null ? null : mc.player.containerMenu;
		drawPanelLine(graphics, "GUI: " + UiUtilsGuiCache.currentGuiName(mc), overlayX + 6,
			overlayY + OVERLAY_INFO_TOP, FABRICATOR_TEXT_WIDTH, 0xFFB8D8FF);
		String state = menu != null && mc.getConnection() != null ? "LIVE" : "CLIENT-ONLY";
		drawPanelLine(graphics, "syncId=" + (menu == null ? "-" : menu.containerId)
			+ "  revision=" + (menu == null ? "-" : menu.getStateId()) + "  " + state,
			overlayX + 6, overlayY + OVERLAY_INFO_TOP + INFO_LINE_HEIGHT,
			FABRICATOR_TEXT_WIDTH, 0xFFB8D8FF);
		UiUtilsGuiCache.Status saved = UiUtilsGuiCache.status(mc);
		drawPanelLine(graphics, saved.present()
			? "Saved: " + saved.name() + " [" + saved.syncId() + "/" + saved.revision()
				+ "] " + (saved.active() ? "LIVE" : "STALE")
			: "Saved: none", overlayX + 6,
			overlayY + OVERLAY_INFO_TOP + INFO_LINE_HEIGHT * 2, FABRICATOR_TEXT_WIDTH,
			0xFFB8D8FF);

		int labelTop = overlayActionRowY;
		if (fabricateMode == MODE_CLICK_SLOT) {
			drawLabel(graphics, "Action", actionDropdown, labelTop);
			drawLabel(graphics, "Sync Id", clickSyncIdField, labelTop
				+ OVERLAY_ACTION_TO_FIELDS);
			drawLabel(graphics, "Revision", clickRevisionField, labelTop
				+ OVERLAY_ACTION_TO_FIELDS);
			drawLabel(graphics, "Slot", clickSlotField,
				labelTop + OVERLAY_ACTION_TO_FIELDS + ROW_SPACING);
			drawLabel(graphics, "Button", clickButtonField,
				labelTop + OVERLAY_ACTION_TO_FIELDS + ROW_SPACING);
			drawLabel(graphics, "Delay", clickDelayToggle,
				labelTop + OVERLAY_ACTION_TO_FIELDS + ROW_SPACING * 2);
			drawLabel(graphics, "Times", clickTimesField,
				labelTop + OVERLAY_ACTION_TO_FIELDS + ROW_SPACING * 2);
		} else if (fabricateMode == MODE_BUTTON_CLICK) {
			drawLabel(graphics, "Sync Id", buttonSyncIdField, labelTop);
			drawLabel(graphics, "Button Id", buttonIdField, labelTop);
			drawLabel(graphics, "Delay", buttonDelayToggle, labelTop + ROW_SPACING);
			drawLabel(graphics, "Times", buttonTimesField, labelTop + ROW_SPACING);
		} else {
			drawLabel(graphics, "Action", actionDropdown, labelTop);
			drawLabel(graphics, "Slot", timedSlotField, labelTop + OVERLAY_ACTION_TO_FIELDS);
			drawLabel(graphics, "Button", timedButtonField,
				labelTop + OVERLAY_ACTION_TO_FIELDS);
			drawLabel(graphics, "Clicks", timedCountField,
				labelTop + OVERLAY_ACTION_TO_FIELDS + ROW_SPACING);
			drawLabel(graphics, "Interval (ms)", timedIntervalField,
				labelTop + OVERLAY_ACTION_TO_FIELDS + ROW_SPACING);
		}

		String status = fabricateStatus;
		int color = fabricateStatusColor;
		if (fabricateMode == MODE_TIMED_SPAM && UiUtilsTimedClickSlot.isRunning()) {
			status = UiUtilsTimedClickSlot.status();
			color = 0xFF8BE88B;
		}
		if (status != null && !status.isBlank())
			drawPanelLine(graphics, status, overlayX + 6, overlayStatusRowY,
				FABRICATOR_TEXT_WIDTH, color);
	}

	private static void drawLabel(GuiGraphicsExtractor graphics, String text,
		AbstractWidget field, int fieldY) {
		if (field == null)
			return;
		graphics.text(font(), text, field.getX(), fieldY - LABEL_OFFSET, 0xFFAAAAAA, false);
	}

	private static ContainerInput selectedAction() {
		ContainerInput[] actions = ContainerInput.values();
		if (actions.length == 0)
			return ContainerInput.PICKUP;
		int index = actionDropdown == null ? 0 : actionDropdown.selectedIndex();
		return actions[Mth.clamp(index, 0, actions.length - 1)];
	}

	private static void setFabricateStatus(String text, int color) {
		fabricateStatus = text == null ? "" : text;
		fabricateStatusColor = color;
		fabricateStatusAt = System.currentTimeMillis();
		UiUtils.chatIfEnabled(text);
	}

	private static boolean requireIntegers(String fields, EditBox... boxes) {
		for (EditBox box : boxes)
			if (box == null || !UiUtils.isInteger(box.getValue())) {
				setFabricateStatus("Invalid " + fields + " value", 0xFFFF9B6B);
				return false;
			}
		return true;
	}

	private static void sendClickSlot() {
		if (!requireIntegers("syncId / revision / slot / button / times", clickSyncIdField,
			clickRevisionField, clickSlotField, clickButtonField, clickTimesField))
			return;

		Minecraft mc = Minecraft.getInstance();
		int syncId = parseInt(clickSyncIdField.getValue());
		short slot = parseShort(clickSlotField.getValue());
		byte button = parseByte(clickButtonField.getValue());
		int timesToSend = parseInt(clickTimesField.getValue());
		if (timesToSend < 1) {
			setFabricateStatus("Times must be at least 1", 0xFFFF9B6B);
			return;
		}
		ContainerInput action = selectedAction();
		if (mc.getConnection() == null || mc.player == null)
			return;
		AbstractContainerMenu menu = mc.player.containerMenu;
		if (menu == null)
			return;
		if (slot < 0 || slot >= menu.slots.size()) {
			setFabricateStatus(
				"Slot " + slot + " is out of range (0-" + (menu.slots.size() - 1) + ")",
				0xFFFF9B6B);
			return;
		}

		HashedPatchMap.HashGenerator hashGenerator =
			mc.getConnection().decoratedHashOpsGenenerator();
		List<ItemStack> beforeStacks = new ArrayList<>(menu.slots.size());
		for (int i = 0; i < menu.slots.size(); i++)
			beforeStacks.add(menu.slots.get(i).getItem().copy());
		ItemStack carriedBeforeStack = menu.getCarried().copy();

		menu.clicked(slot, button, action, mc.player);
		int revision = menu.getStateId();

		Int2ObjectMap<HashedStack> diffSlots = new Int2ObjectArrayMap<>();
		for (int i = 0; i < menu.slots.size(); i++) {
			ItemStack before = beforeStacks.get(i);
			ItemStack after = menu.slots.get(i).getItem();
			boolean changed = before.isEmpty() != after.isEmpty()
				|| (!before.isEmpty() && (before.getItem() != after.getItem()
					|| before.getCount() != after.getCount()));
			if (changed)
				diffSlots.put(i, HashedStack.create(after, hashGenerator));
		}

		ServerboundContainerClickPacket packet = new ServerboundContainerClickPacket(syncId,
			revision, slot, button, action, diffSlots,
			HashedStack.create(menu.getCarried(), hashGenerator));
		UiUtils.LOGGER.info(
			"Fabricate ClickSlot: syncId={}, revision={}, slot={}, button={}, action={}, times={}, diffSlots={}, carriedBefore={}",
			syncId, revision, slot, button, action, timesToSend, diffSlots.size(),
			HashedStack.create(carriedBeforeStack, hashGenerator));

		Runnable toRun = UiUtils.getFabricatePacketRunnable(mc, clickDelayEnabled, packet);
		for (int i = 0; i < timesToSend; i++)
			toRun.run();
		setFabricateStatus("Sent " + timesToSend + "x ClickSlot slot=" + slot + " action="
			+ action + ", diff=" + diffSlots.size(), 0xFF8BE88B);
	}

	private static void sendButtonClick() {
		if (!requireIntegers("syncId / buttonId / times", buttonSyncIdField, buttonIdField,
			buttonTimesField))
			return;
		Minecraft mc = Minecraft.getInstance();
		int syncId = parseInt(buttonSyncIdField.getValue());
		int buttonId = parseInt(buttonIdField.getValue());
		int timesToSend = parseInt(buttonTimesField.getValue());
		if (timesToSend < 1) {
			setFabricateStatus("Times must be at least 1", 0xFFFF9B6B);
			return;
		}
		ServerboundContainerButtonClickPacket packet =
			new ServerboundContainerButtonClickPacket(syncId, buttonId);
		Runnable toRun = UiUtils.getFabricatePacketRunnable(mc, buttonDelayEnabled, packet);
		for (int i = 0; i < timesToSend; i++)
			toRun.run();
		setFabricateStatus("Sent " + timesToSend + "x ButtonClick buttonId=" + buttonId,
			0xFF8BE88B);
	}

	private static void toggleTimedSpam() {
		if (UiUtilsTimedClickSlot.isRunning()) {
			UiUtilsTimedClickSlot.stop();
			timedStartButton.setMessage(Component.literal("Start Spam"));
			setFabricateStatus("Timed click stopped", 0xFFFFDE7A);
			return;
		}
		if (!requireIntegers("slot / button / clicks / interval", timedSlotField,
			timedButtonField, timedCountField, timedIntervalField))
			return;
		int slot = parseInt(timedSlotField.getValue());
		int button = parseInt(timedButtonField.getValue());
		int count = parseInt(timedCountField.getValue());
		int interval = parseInt(timedIntervalField.getValue());
		if (count < 1 || interval < 0) {
			setFabricateStatus("Clicks must be >= 1 and interval >= 0 ms", 0xFFFF9B6B);
			return;
		}
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null || mc.player.containerMenu == null)
			return;
		int slotCount = mc.player.containerMenu.slots.size();
		if (slot < 0 || slot >= slotCount) {
			setFabricateStatus(
				"Slot " + slot + " is out of range (0-" + (slotCount - 1) + ")",
				0xFFFF9B6B);
			return;
		}
		UiUtilsTimedClickSlot.start(slot, button, selectedAction(), count, interval);
		timedStartButton.setMessage(Component.literal("Stop Spam"));
		setFabricateStatus("Timed click started: slot " + slot + " x" + count + " every "
			+ interval + " ms", 0xFF8BE88B);
	}

	// ### GUI Tools panel ###

	private static void initTools(Screen screen) {
		if (toolsInitialized && !toolsWidgets.isEmpty()
			&& screen.children().contains(toolsWidgets.get(0)))
			return;
		toolsInitialized = false;
		toolsWidgets.clear();
		toolsRows.clear();

		addToolsRow(UiUtils.styledButton("Save GUI", b -> {
			boolean ok = UiUtilsGuiCache.save(Minecraft.getInstance());
			setToolsStatus(ok
				? "Saved: " + UiUtilsGuiCache.status(Minecraft.getInstance()).label()
				: "No GUI to save", ok ? 0xFF8BE88B : 0xFFFF9B6B);
		}, 0, 0, 10, 10), UiUtils.styledButton("Load GUI", b -> {
			boolean ok = UiUtilsGuiCache.load(Minecraft.getInstance());
			setToolsStatus(ok ? "Restored saved GUI" : "No saved GUI",
				ok ? 0xFF8BE88B : 0xFFFF9B6B);
		}, 0, 0, 10, 10));

		addToolsRow(UiUtils.styledButton("Clear GUI Cache", b -> {
			boolean ok = UiUtilsGuiCache.clear();
			setToolsStatus(ok ? "Cleared saved GUI cache" : "Nothing to clear",
				ok ? 0xFF8BE88B : 0xFFAAAAAA);
		}, 0, 0, 10, 10), UiUtils.styledButton("Close w/o Packet", b -> {
			UiUtilsGuiCache.save(Minecraft.getInstance());
			UiUtils.closeScreenWithConfiguredDelay(Minecraft.getInstance());
		}, 0, 0, 10, 10));

		addToolsRow(UiUtils.styledButton("Steal", b -> {
			UiUtilsContainerTransfer.steal(Minecraft.getInstance());
			setToolsStatus("Stealing container contents", 0xFF8BE88B);
		}, 0, 0, 10, 10), UiUtils.styledButton("Store", b -> {
			UiUtilsContainerTransfer.store(Minecraft.getInstance());
			setToolsStatus("Storing inventory contents", 0xFF8BE88B);
		}, 0, 0, 10, 10), UiUtils.styledButton("Dump", b -> {
			UiUtilsContainerTransfer.dump(Minecraft.getInstance());
			setToolsStatus("Dumping container contents", 0xFF8BE88B);
		}, 0, 0, 10, 10));

		addToolsRow(UiUtils.styledButton("Copy GUI JSON", b -> {
			Minecraft mc = Minecraft.getInstance();
			mc.keyboardHandler.setClipboard(UiUtilsGuiCache.buildSnapshotJson(mc));
			setToolsStatus("Copied GUI snapshot JSON", 0xFF8BE88B);
		}, 0, 0, 10, 10), UiUtils.styledButton("Copy Title JSON", b -> {
			Minecraft mc = Minecraft.getInstance();
			String json = UiUtilsGuiCache.buildTitleJson(mc);
			if (json == null) {
				setToolsStatus("No GUI title to copy", 0xFFFF9B6B);
				return;
			}
			mc.keyboardHandler.setClipboard(json);
			setToolsStatus("Copied GUI title JSON", 0xFF8BE88B);
		}, 0, 0, 10, 10));

		addToolsRow(UiUtils.styledButton("Xfer -", b -> {
			UiUtilsContainerTransfer.adjustDelay(-10);
			setToolsStatus("Transfer delay: " + UiUtilsContainerTransfer.delayMs() + " ms",
				0xFFB8D8FF);
		}, 0, 0, 10, 10), UiUtils.styledButton("Transfer delay", b -> {
		}, 0, 0, 10, 10), UiUtils.styledButton("Xfer +", b -> {
			UiUtilsContainerTransfer.adjustDelay(10);
			setToolsStatus("Transfer delay: " + UiUtilsContainerTransfer.delayMs() + " ms",
				0xFFB8D8FF);
		}, 0, 0, 10, 10));

		addToolsRow(UiUtils.styledButton("GUI Packet Control", b -> McCompat
			.setScreen(Minecraft.getInstance(), new UiUtilsGuiPacketControlScreen(screen)),
			0, 0, 10, 10), UiUtils.styledButton("GUI Packet Log", b -> McCompat
				.setScreen(Minecraft.getInstance(), new UiUtilsGuiPacketLogScreen(screen)), 0,
			0, 10, 10));

		addToolsRow(UiUtils.styledButton("GUI Log on/off", b -> {
			UiUtilsGuiPacketLog.setEnabled(!UiUtilsGuiPacketLog.isEnabled());
			setToolsStatus("GUI log: " + (UiUtilsGuiPacketLog.isEnabled() ? "ON" : "OFF"),
				0xFFB8D8FF);
		}, 0, 0, 10, 10), UiUtils.styledButton("Log to file", b -> {
			UiUtilsGuiPacketLog
				.setFileLoggingEnabled(!UiUtilsGuiPacketLog.isFileLoggingEnabled());
			setToolsStatus(
				"GUI log file: " + (UiUtilsGuiPacketLog.isFileLoggingEnabled() ? "ON"
					: "OFF"),
				0xFFB8D8FF);
		}, 0, 0, 10, 10), UiUtils.styledButton("Clear Log", b -> {
			UiUtilsGuiPacketLog.clear();
			setToolsStatus("Cleared GUI packet log", 0xFF8BE88B);
		}, 0, 0, 10, 10));

		addToolsRow(UiUtils.styledButton("Delay -", b -> {
			UiUtilsGuiPacketControl.adjustDelay(-1);
			setToolsStatus("GUI packet delay: " + UiUtilsGuiPacketControl.delayTicks() + "t",
				0xFFB8D8FF);
		}, 0, 0, 10, 10), UiUtils.styledButton("GUI packet delay", b -> {
		}, 0, 0, 10, 10), UiUtils.styledButton("Delay +", b -> {
			UiUtilsGuiPacketControl.adjustDelay(1);
			setToolsStatus("GUI packet delay: " + UiUtilsGuiPacketControl.delayTicks() + "t",
				0xFFB8D8FF);
		}, 0, 0, 10, 10));

		addToolsRow(UiUtils.styledButton("DC & Send Packets", b -> {
			UiUtils.disconnectAndSendPackets(Minecraft.getInstance());
			setToolsStatus("Flushed queued packets + disconnect", 0xFFFFDE7A);
		}, 0, 0, 10, 10), UiUtils.styledButton("De-Sync", b -> {
			UiUtils.sendClosePacketWithConfiguredDelay(Minecraft.getInstance());
		}, 0, 0, 10, 10));

		toolsInitialized = true;
		updateToolsVisibility();
	}

	private static void addToolsRow(AbstractWidget... widgets) {
		List<AbstractWidget> row = new ArrayList<>();
		for (AbstractWidget widget : widgets) {
			if (widget == null)
				continue;
			row.add(widget);
			toolsWidgets.add(widget);
			pendingAdd.add(widget);
		}
		toolsRows.add(row);
	}

	private static void setToolsStatus(String text, int color) {
		toolsStatus = text == null ? "" : text;
		toolsStatusColor = color;
		toolsStatusAt = System.currentTimeMillis();
		UiUtils.chatIfEnabled(text);
	}

	private static void updateToolsVisibility() {
		if (!toolsInitialized)
			return;
		boolean visible = UiUtilsState.isUiEnabled()
			&& UiUtilsState.guiToolsOverlayOpen && isAllowedScreen(attachedScreen);
		for (AbstractWidget widget : toolsWidgets)
			show(widget, visible);
		if (!visible) {
			for (AbstractWidget widget : toolsWidgets) {
				widget.setX(-2000);
				widget.setY(-2000);
			}
			return;
		}
		layoutTools();
	}

	private static void layoutTools() {
		if (!toolsInitialized || attachedScreen == null)
			return;
		int screenWidth = attachedScreen.width;
		int screenHeight = attachedScreen.height;
		int x = UiUtilsState.guiToolsOverlayX >= 0 ? UiUtilsState.guiToolsOverlayX
			: screenWidth - TOOLS_WIDTH - 8;
		x = Mth.clamp(x, 4, Math.max(4, screenWidth - TOOLS_WIDTH - 4));
		int y = UiUtilsState.guiToolsOverlayY >= 0 ? UiUtilsState.guiToolsOverlayY
			: UiUtilsState.fabricateOverlayOpen && overlayBottomY > 0
				? overlayBottomY + 8 : 8;
		y = Mth.clamp(y, 4, Math.max(4, screenHeight - 40));

		if (toolsDragging) {
			x = Mth.clamp((int)mouseX - toolsDragX, 4,
				Math.max(4, screenWidth - TOOLS_WIDTH - 4));
			y = Mth.clamp((int)mouseY - toolsDragY, 4, Math.max(4, screenHeight - 40));
			UiUtilsState.guiToolsOverlayX = x;
			UiUtilsState.guiToolsOverlayY = y;
		}

		int contentX = x + 6;
		int contentWidth = TOOLS_WIDTH - 12;
		int contentTop = y + 4 + 14 + TOOLS_INFO_LINES * INFO_LINE_HEIGHT + 10;
		for (int i = 0; i < toolsRows.size(); i++) {
			List<AbstractWidget> row = toolsRows.get(i);
			if (row.isEmpty())
				continue;
			int available = contentWidth - TOOLS_ROW_GAP * (row.size() - 1);
			int width = Math.max(24, available / row.size());
			int cx = contentX;
			int cy = contentTop + i * TOOLS_ROW_SPACING;
			for (AbstractWidget widget : row) {
				widget.setX(cx);
				widget.setY(cy);
				widget.setWidth(width);
				widget.setHeight(TOOLS_ROW_HEIGHT);
				cx += width + TOOLS_ROW_GAP;
			}
		}
		toolsX = x;
		toolsY = y;
		toolsBottomY = contentTop + toolsRows.size() * TOOLS_ROW_SPACING + 14;
	}

	private static void renderToolsBackground(GuiGraphicsExtractor graphics) {
		if (!toolsInitialized || !UiUtilsState.guiToolsOverlayOpen)
			return;
		int alpha = UiUtilsSettings.get().fabricateOverlayBgAlpha;
		if (alpha <= 0)
			return;
		int x1 = Math.max(0, toolsX - 6);
		int y1 = Math.max(0, toolsY - 6);
		int x2 = Math.min(attachedScreen.width, toolsX + TOOLS_WIDTH + 6);
		int y2 = Math.min(attachedScreen.height, toolsBottomY + 6);
		graphics.fill(x1, y1, x2, y2, alpha << 24);
	}

	private static void drawToolsForeground(GuiGraphicsExtractor graphics) {
		graphics.text(font(), "GUI Tools", toolsX + 6, toolsY + 4, 0xFFEAEAEA, false);
		drawGrip(graphics, toolsX, toolsY, TOOLS_WIDTH);

		Minecraft mc = Minecraft.getInstance();
		drawPanelLine(graphics, "Current: " + UiUtilsGuiCache.currentStatus(mc).label(),
			toolsX + 6, toolsY + 22, TOOLS_TEXT_WIDTH, 0xFFB8D8FF);
		UiUtilsGuiCache.Status saved = UiUtilsGuiCache.status(mc);
		int savedColor = !saved.present() ? 0xFF888888
			: saved.active() ? 0xFF8BE88B : 0xFFFF9B6B;
		drawPanelLine(graphics, "Saved: " + saved.label(), toolsX + 6, toolsY + 32,
			TOOLS_TEXT_WIDTH, savedColor);
		if (UiUtilsContainerTransfer.isBusy())
			drawPanelLine(graphics, UiUtilsContainerTransfer.status(), toolsX + 6,
				toolsY + 42, TOOLS_TEXT_WIDTH, 0xFF8BE88B);
		if (toolsStatus != null && !toolsStatus.isBlank())
			drawPanelLine(graphics, toolsStatus, toolsX + 6, toolsBottomY - 11,
				TOOLS_TEXT_WIDTH, toolsStatusColor);
	}

	// ### Shared helpers ###

	private static Font font() {
		return Minecraft.getInstance().font;
	}

	/** Clipped panel line: scales down instead of spilling past the panel. */
	private static void drawPanelLine(GuiGraphicsExtractor graphics, String text, int x,
		int y, int maxWidth, int color) {
		UiUtils.renderScaledText(graphics, font(), text, x, y, maxWidth, font().lineHeight,
			color, 0.5F);
	}

	/** Three-dash grip in the panel's top-right corner. */
	private static void drawGrip(GuiGraphicsExtractor graphics, int panelX, int panelY,
		int panelWidth) {
		int right = panelX + panelWidth - 6;
		for (int i = 0; i < 3; i++)
			graphics.fill(right - 10, panelY + 4 + i * 3, right, panelY + 5 + i * 3,
				0xFF9A9A9A);
	}

	private static int parseInt(String raw) {
		try {
			return Integer.parseInt(raw);
		} catch (NumberFormatException ignored) {
			return 0;
		}
	}

	private static short parseShort(String raw) {
		try {
			return Short.parseShort(raw);
		} catch (NumberFormatException ignored) {
			return (short)parseInt(raw);
		}
	}

	private static byte parseByte(String raw) {
		try {
			return Byte.parseByte(raw);
		} catch (NumberFormatException ignored) {
			return (byte)parseInt(raw);
		}
	}
}
