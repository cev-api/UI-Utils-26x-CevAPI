package com.ui_utils.uiutils;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
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

import com.ui_utils.nbttools.UiUtilsNbtEditor;
import com.ui_utils.uiutils.ui.UiButton;
import com.ui_utils.uiutils.ui.UiInput;
import com.ui_utils.uiutils.ui.UiScalable;
import com.ui_utils.uiutils.ui.UiTheme;
import com.ui_utils.uiutils.ui.UiToggle;

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

	private static final int OVERLAY_WIDTH = 240;
	private static final int PANEL_HEADER_HEIGHT = 17;
	private static final int MODE_BUTTON_WIDTH = 72;
	private static final int MODE_BUTTON_GAP = 3;
	private static final int FIELD_WIDTH = 108;
	private static final int FIELD_GAP = 8;
	private static final int FIELD_HEIGHT = 15;
	/** Height of the small caption drawn above a field. */
	private static final int LABEL_HEIGHT = 8;
	private static final int GAP = 3;
	/** Extra gap between the last form row and the send button. */
	private static final int SEND_GAP = 8;
	/** Extra gap after the action dropdown, which is wider than the fields. */
	private static final int ACTION_GAP = 6;
	/** One labelled form row: caption plus field plus the gap to the next row. */
	private static final int ROW_PITCH = LABEL_HEIGHT + FIELD_HEIGHT + GAP;
	private static final int SEND_BUTTON_WIDTH = 104;
	private static final int INFO_LINE_HEIGHT = 9;
	private static final int INFO_LINES = 3;

	private static final int OVERLAY_INFO_TOP = PANEL_HEADER_HEIGHT + 5;
	private static final int OVERLAY_MODES_TOP = OVERLAY_INFO_TOP
		+ INFO_LINES * INFO_LINE_HEIGHT + 6;
	private static final int OVERLAY_FORM_TOP = OVERLAY_MODES_TOP + FIELD_HEIGHT + 8;

	private static final int TOOLS_WIDTH = 280;
	private static final int TOOLS_ROW_SPACING = 24;
	private static final int TOOLS_ROW_HEIGHT = 19;
	private static final int TOOLS_ROW_GAP = 6;
	private static final int TOOLS_INFO_LINES = 3;
	private static final int TOOLS_INFO_TOP = PANEL_HEADER_HEIGHT + 5;
	private static final int TOOLS_FORM_TOP = TOOLS_INFO_TOP
		+ TOOLS_INFO_LINES * INFO_LINE_HEIGHT + 7;
	/** How long a panel status line stays on screen before clearing. */
	private static final long STATUS_LINGER_MS = 4000L;

	/** One scale for both panels, so every panel label renders at the same size. */
	private static float panelScale = 1F;
	private static final Map<AbstractWidget, int[]> designSizes = new IdentityHashMap<>();

	/** Panel design units to screen pixels. */
	private static int ps(int units)
	{
		return Math.round(units * panelScale);
	}

	/**
	 * Picks the panel scale from the screen size, using the same value as the button
	 * panel and every UI-Utils window. Both overlay panels share it, and it is
	 * clamped against the larger panel, so they always match and always fit.
	 */
	private static void updatePanelScale()
	{
		if(attachedScreen == null) {
			panelScale = 1F;
			return;
		}
		float preferred = UiTheme.screenScale(attachedScreen.width,
			attachedScreen.height);
		int designWidth = Math.max(OVERLAY_WIDTH, TOOLS_WIDTH);
		int designHeight = Math.max(fabricatorDesignHeight(), toolsDesignHeight());
		float fit = Math.min(
			(attachedScreen.width - 8F) / Math.max(1, designWidth),
			(attachedScreen.height - 8F) / Math.max(1, designHeight));
		panelScale = UiTheme.snapScaleDown(Math.min(preferred, fit), fit);
	}

	/**
	 * Converts a laid-out panel widget from design units to screen pixels.
	 * <p>
	 * The design box is captured on the first pass and every later pass writes
	 * absolute pixel values from it. Scaling the widget's own current position
	 * instead would compound on each layout, which collapses the whole form toward
	 * the panel origin.
	 */
	private static void scalePanelWidgets(List<? extends AbstractWidget> widgets,
		int originX, int originY)
	{
		for(AbstractWidget widget : widgets) {
			if(widget == null || widget.getX() <= -1000)
				continue;
			int[] design = designSizes.computeIfAbsent(widget, w -> new int[]{
				w.getX() - originX, w.getY() - originY, w.getWidth(), w.getHeight()});
			widget.setX(originX + ps(design[0]));
			widget.setY(originY + ps(design[1]));
			widget.setWidth(Math.max(8, ps(design[2])));
			widget.setHeight(Math.max(8, ps(design[3])));
			if(widget instanceof UiScalable scalable)
				scalable.applyUiScale(panelScale);
		}
	}

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
	private static UiToggle clickDelayToggle;
	private static EditBox clickTimesField;
	private static UiUtilsColoredButton clickSendButton;
	private static EditBox buttonSyncIdField;
	private static EditBox buttonIdField;
	private static UiToggle buttonDelayToggle;
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
	private static final List<AbstractWidget> fabricatorButtons = new ArrayList<>();
	private static int overlayX;
	private static int overlayY;
	private static int overlayBottomY;
	private static int overlayActionRowY;
	private static int overlayStatusRowY;
	/** Caption above a field, in panel design units, rebuilt on every layout. */
	private static final List<String> overlayLabelTexts = new ArrayList<>();
	private static final List<Integer> overlayLabelDesignY = new ArrayList<>();
	private static final List<AbstractWidget> overlayLabelFields = new ArrayList<>();
	private static int overlayStatusDesignY;
	/** Panel origin of the layout currently being built, for relative captions. */
	private static int overlayLayoutY;
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
	// Every widget instance ever handed to a screen. Rebuilding while an older set
	// is still live would leave that set rendering behind the panel, which only
	// becomes obvious after a drag when the stale copy stops following the panel.
	private static final List<AbstractWidget> ownedWidgets = new ArrayList<>();
	// A screen initialises more than once per open (Screen#init and Screen#resize
	// both fire AFTER_INIT), so the panels are attached once per initialisation.
	private static boolean attachClaimed;

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
		if (ownedWidgets.isEmpty()) {
			registered.clear();
			return;
		}
		// Remove every widget we ever gave this screen. Passing entries that are no
		// longer present is harmless, and asking for the full set means a rebuild can
		// never leave an older one behind.
		List<AbstractWidget> owned = new ArrayList<>(ownedWidgets);
		boolean purged = false;
		try {
			Screens.getWidgets(screen).removeAll(owned);
			purged = true;
		} catch (Throwable ignored) {
		}
		// Keep the list if the removal failed so a later attach can retry; otherwise
		// forget it, which also keeps it from growing across screen changes.
		if (purged)
			ownedWidgets.clear();
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
		ownedWidgets.addAll(pending);
		if (!loggedAttach) {
			loggedAttach = true;
			UiUtils.LOGGER.info(
				"UI-Utils panels registered on {} ({} fabricator widgets, {} tools widgets)",
				screen.getClass().getSimpleName(), fabricatorButtons.size(),
				toolsWidgets.size());
		}
	}

	/**
	 * Called when a screen begins initialising (before its widget lists are
	 * cleared) so the next attach rebuilds the panels.
	 */
	public static void onScreenInit() {
		fabricatorInitialized = false;
		toolsInitialized = false;
		overlayDragging = false;
		toolsDragging = false;
		// Screen#init clears its widget lists, so the old set is gone with it.
		registered.clear();
		pendingAdd.clear();
		designSizes.clear();
		attachClaimed = false;
	}

	/**
	 * Claims the attach for the current initialisation. Returns true only for the
	 * first caller, so the follow-up AFTER_INIT of a resize cannot add a second set
	 * of panel widgets on top of the one that is already live.
	 */
	public static boolean claimAttach() {
		if (attachClaimed)
			return false;
		attachClaimed = true;
		return true;
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
		if (!isAllowedScreen(screen))
			return;
		// Only the open dropdown list is drawn here: it has to sit above the other
		// controls. Everything else is panel chrome and is drawn in the background
		// pass, above the panel body but below the widgets.
		if (fabricatorInitialized && UiUtilsState.fabricateOverlayOpen
			&& actionDropdown != null)
			actionDropdown.renderExpandedList(graphics, mouseX, mouseY);
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
			&& isOverPinControl(mx, my, overlayX, overlayY, ps(OVERLAY_WIDTH))) {
			UiUtilsSettings.get().fabricatePanelPinned =
				!UiUtilsSettings.get().fabricatePanelPinned;
			UiUtilsSettings.save();
			return true;
		}
		if (UiUtilsState.fabricateOverlayOpen
			&& isOverDragBar(mx, my, overlayX, overlayY, ps(OVERLAY_WIDTH))) {
			if (UiUtilsSettings.get().fabricatePanelPinned)
				return true;
			overlayDragging = true;
			overlayDragX = (int)Math.round(mx - overlayX);
			overlayDragY = (int)Math.round(my - overlayY);
			return true;
		}
		if (UiUtilsState.guiToolsOverlayOpen
			&& isOverPinControl(mx, my, toolsX, toolsY, ps(TOOLS_WIDTH))) {
			UiUtilsSettings.get().guiToolsPanelPinned =
				!UiUtilsSettings.get().guiToolsPanelPinned;
			UiUtilsSettings.save();
			return true;
		}
		if (UiUtilsState.guiToolsOverlayOpen
			&& isOverDragBar(mx, my, toolsX, toolsY, ps(TOOLS_WIDTH))) {
			if (UiUtilsSettings.get().guiToolsPanelPinned)
				return true;
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
		if (overlayDragging) {
			UiUtilsSettings.get().fabricatePanelX = UiUtilsState.fabricateOverlayX;
			UiUtilsSettings.get().fabricatePanelY = UiUtilsState.fabricateOverlayY;
			UiUtilsSettings.save();
		}
		if (toolsDragging) {
			UiUtilsSettings.get().guiToolsPanelX = UiUtilsState.guiToolsOverlayX;
			UiUtilsSettings.get().guiToolsPanelY = UiUtilsState.guiToolsOverlayY;
			UiUtilsSettings.save();
		}
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
		// The whole title bar drags, so clicking the panel title works too.
		return mx >= px && mx <= px + panelWidth && my >= py
			&& my <= py + ps(PANEL_HEADER_HEIGHT);
	}

	private static boolean isOverPinControl(double mx, double my, int px, int py,
		int panelWidth) {
		return mx >= px + panelWidth - ps(42) && mx <= px + panelWidth
			&& my >= py && my <= py + ps(PANEL_HEADER_HEIGHT);
	}

	// ### Fabricator panel ###

	private static void initFabricator(Screen screen) {
		Font font = screen.getFont();
		if (fabricatorInitialized && allStillAttached(screen, fabricatorButtons))
			return;
		fabricatorInitialized = false;
		fabricatorButtons.clear();

		modeClickSlotButton = add(fabricatorButtons, UiUtils.styledButton(
			"Slot Click", b -> switchMode(MODE_CLICK_SLOT), 0, 0, MODE_BUTTON_WIDTH, FIELD_HEIGHT));
		modeButtonClickButton = add(fabricatorButtons,
			UiUtils.styledButton("Btn Click", b -> switchMode(MODE_BUTTON_CLICK), 0, 0,
				MODE_BUTTON_WIDTH, FIELD_HEIGHT));
		modeTimedSpamButton = add(fabricatorButtons, UiUtils.styledButton(
			"Timed", b -> switchMode(MODE_TIMED_SPAM), 0, 0, MODE_BUTTON_WIDTH, FIELD_HEIGHT));

		List<String> actions = new ArrayList<>();
		for (ContainerInput input : ContainerInput.values())
			actions.add(input.name());
		actionDropdown = new UiUtilsDropdown(font, 0, 0, FIELD_WIDTH * 2 + FIELD_GAP, FIELD_HEIGHT,
			"Action", actions);
		pendingAdd.add(actionDropdown);

		clickSyncIdField = field(font, "Sync Id");
		clickRevisionField = field(font, "Revision");
		clickSlotField = field(font, "Slot", "0");
		clickButtonField = field(font, "Button", "0");
		clickDelayEnabled = false;
		clickDelayToggle = add(fabricatorButtons, new UiToggle("Delay",
			() -> clickDelayEnabled, value -> clickDelayEnabled = value));
		setSize(clickDelayToggle, FIELD_WIDTH, FIELD_HEIGHT);
		clickTimesField = field(font, "Times to send", "1");
		clickSendButton = add(fabricatorButtons, UiUtils.styledButton("Send",
			b -> sendClickSlot(), 0, 0, SEND_BUTTON_WIDTH, FIELD_HEIGHT));

		buttonSyncIdField = field(font, "Sync Id");
		buttonIdField = field(font, "Button Id", "0");
		buttonDelayEnabled = false;
		buttonDelayToggle = add(fabricatorButtons, new UiToggle("Delay",
			() -> buttonDelayEnabled, value -> buttonDelayEnabled = value));
		setSize(buttonDelayToggle, FIELD_WIDTH, FIELD_HEIGHT);
		buttonTimesField = field(font, "Times to send", "1");
		buttonSendButton = add(fabricatorButtons, UiUtils.styledButton("Send",
			b -> sendButtonClick(), 0, 0, SEND_BUTTON_WIDTH, FIELD_HEIGHT));

		timedSlotField = field(font, "Slot", "0");
		timedButtonField = field(font, "Button", "0");
		timedCountField = field(font, "Clicks", "40");
		timedIntervalField = field(font, "Interval (ms)", "50");
		timedStartButton = add(fabricatorButtons, UiUtils.styledButton("Start Spam",
			b -> toggleTimedSpam(), 0, 0, SEND_BUTTON_WIDTH, FIELD_HEIGHT));

		fabricatorInitialized = true;
		switchMode(fabricateMode);
		updateFabricatorVisibility();
	}

	private static <T extends AbstractWidget> T add(List<AbstractWidget> tracked,
		T widget) {
		pendingAdd.add(widget);
		tracked.add(widget);
		return widget;
	}

	private static EditBox field(Font font, String label) {
		return field(font, label, "");
	}

	private static EditBox field(Font font, String label, String value) {
		UiInput box = new UiInput(font, FIELD_WIDTH, value,
			Component.literal(label));
		setSize(box, FIELD_WIDTH, FIELD_HEIGHT);
		pendingAdd.add(box);
		return box;
	}

	/** Fixes a panel widget's design size, which the scale pass reads back. */
	private static void setSize(AbstractWidget widget, int width, int height) {
		widget.setWidth(width);
		widget.setHeight(height);
	}

	private static boolean allStillAttached(Screen screen, List<AbstractWidget> widgets) {
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
			.literal("Slot Click" + (mode == MODE_CLICK_SLOT ? " \u2713" : "")));
		if (modeButtonClickButton != null)
			modeButtonClickButton.setMessage(Component
			.literal("Btn Click" + (mode == MODE_BUTTON_CLICK ? " \u2713" : "")));
		if (modeTimedSpamButton != null)
			modeTimedSpamButton.setMessage(Component
			.literal("Timed" + (mode == MODE_TIMED_SPAM ? " \u2713" : "")));

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
		for (AbstractWidget widget : fabricatorButtons) {
			show(widget, false);
			widget.setX(-2000);
			widget.setY(-2000);
		}
	}

	private static void layoutFabricator() {
		if (!fabricatorInitialized || attachedScreen == null)
			return;
		updatePanelScale();
		int panelWidth = attachedScreen.width;
		int overlayWidth = panelWidth == 0 ? OVERLAY_WIDTH : panelWidth;
		int designHeight = fabricatorDesignHeight();
		int maxY = Math.max(4, attachedScreen.height - ps(designHeight) - 4);
		if (UiUtilsState.fabricateOverlayX < 0
			&& UiUtilsSettings.get().fabricatePanelX >= 0) {
			UiUtilsState.fabricateOverlayX = UiUtilsSettings.get().fabricatePanelX;
			UiUtilsState.fabricateOverlayY = UiUtilsSettings.get().fabricatePanelY;
		}
		int x = UiUtilsState.fabricateOverlayX >= 0 ? UiUtilsState.fabricateOverlayX
			: overlayWidth - ps(OVERLAY_WIDTH) - 8;
		x = clampOverlayX(x, overlayWidth, ps(OVERLAY_WIDTH));
		int y = UiUtilsState.fabricateOverlayY >= 0 ? UiUtilsState.fabricateOverlayY : 8;
		y = Mth.clamp(y, 4, maxY);

		if (overlayDragging) {
			x = clampOverlayX((int)mouseX - overlayDragX, overlayWidth,
				ps(OVERLAY_WIDTH));
			y = Mth.clamp((int)mouseY - overlayDragY, 4, maxY);
			UiUtilsState.fabricateOverlayX = x;
			UiUtilsState.fabricateOverlayY = y;
		}

		int modeGroup = MODE_BUTTON_WIDTH * 3 + MODE_BUTTON_GAP * 2;
		int modeStart = x + (OVERLAY_WIDTH - modeGroup) / 2;
		int modeY = y + OVERLAY_MODES_TOP;
		place(modeClickSlotButton, modeStart, modeY);
		place(modeButtonClickButton, modeStart + MODE_BUTTON_WIDTH + MODE_BUTTON_GAP,
			modeY);
		place(modeTimedSpamButton,
			modeStart + (MODE_BUTTON_WIDTH + MODE_BUTTON_GAP) * 2, modeY);

		int left = x + (OVERLAY_WIDTH - (FIELD_WIDTH * 2 + FIELD_GAP)) / 2;
		int right = left + FIELD_WIDTH + FIELD_GAP;
		int sendX = x + (OVERLAY_WIDTH - SEND_BUTTON_WIDTH) / 2;
		// Every row is a caption above a field, laid out from a cursor so the form
		// cannot overlap itself or the info block above it.
		overlayLabelTexts.clear();
		overlayLabelDesignY.clear();
		overlayLabelFields.clear();
		overlayLayoutY = y;
		int cy = y + OVERLAY_FORM_TOP;
		if (fabricateMode == MODE_CLICK_SLOT) {
			addLabel("Action", cy, actionDropdown);
			place(actionDropdown, left, cy + LABEL_HEIGHT);
			cy += ROW_PITCH + ACTION_GAP;
			addLabel("Sync Id", cy, clickSyncIdField);
			addLabel("Revision", cy, clickRevisionField);
			placePair(clickSyncIdField, clickRevisionField, left, right,
				cy + LABEL_HEIGHT);
			cy += ROW_PITCH;
			addLabel("Slot", cy, clickSlotField);
			addLabel("Button", cy, clickButtonField);
			placePair(clickSlotField, clickButtonField, left, right,
				cy + LABEL_HEIGHT);
			cy += ROW_PITCH;
			addLabel("Times", cy, clickTimesField);
			placePair(clickDelayToggle, clickTimesField, left, right,
				cy + LABEL_HEIGHT);
			cy += ROW_PITCH + SEND_GAP;
			place(clickSendButton, sendX, cy);
			cy += FIELD_HEIGHT + GAP;
		} else if (fabricateMode == MODE_BUTTON_CLICK) {
			addLabel("Sync Id", cy, buttonSyncIdField);
			addLabel("Button Id", cy, buttonIdField);
			placePair(buttonSyncIdField, buttonIdField, left, right,
				cy + LABEL_HEIGHT);
			cy += ROW_PITCH;
			addLabel("Times", cy, buttonTimesField);
			placePair(buttonDelayToggle, buttonTimesField, left, right,
				cy + LABEL_HEIGHT);
			cy += ROW_PITCH + SEND_GAP;
			place(buttonSendButton, sendX, cy);
			cy += FIELD_HEIGHT + GAP;
		} else {
			addLabel("Action", cy, actionDropdown);
			place(actionDropdown, left, cy + LABEL_HEIGHT);
			cy += ROW_PITCH + ACTION_GAP;
			addLabel("Slot", cy, timedSlotField);
			addLabel("Button", cy, timedButtonField);
			placePair(timedSlotField, timedButtonField, left, right,
				cy + LABEL_HEIGHT);
			cy += ROW_PITCH;
			addLabel("Clicks", cy, timedCountField);
			addLabel("Interval (ms)", cy, timedIntervalField);
			placePair(timedCountField, timedIntervalField, left, right,
				cy + LABEL_HEIGHT);
			cy += ROW_PITCH + SEND_GAP;
			place(timedStartButton, sendX, cy);
			cy += FIELD_HEIGHT + GAP;
		}

		overlayStatusDesignY = cy - y + 2;

		overlayX = x;
		overlayY = y;
		overlayBottomY = y + ps(overlayStatusDesignY + INFO_LINE_HEIGHT + 4);
		UiUtilsState.fabricateOverlayX = x;
		UiUtilsState.fabricateOverlayY = y;
		scalePanelWidgets(fabricatorButtons, x, y);
		scalePanelWidgets(fabricatorFields(), x, y);
	}

	/** Design height of the fabricator panel for a given mode. */
	private static int fabricatorDesignHeight(int mode) {
		// One row per caption line: Action, then the field rows, then the send button.
		int labelRows = switch (mode) {
			case MODE_BUTTON_CLICK -> 2;
			case MODE_TIMED_SPAM -> 3;
			default -> 4;
		};
		return OVERLAY_FORM_TOP + labelRows * ROW_PITCH + FIELD_HEIGHT
			+ SEND_GAP + FIELD_HEIGHT + GAP + INFO_LINE_HEIGHT + 4;
	}

	/** Largest design height across modes, so the panel never resizes on switch. */
	private static int fabricatorDesignHeight() {
		return Math.max(fabricatorDesignHeight(MODE_CLICK_SLOT),
			Math.max(fabricatorDesignHeight(MODE_BUTTON_CLICK),
				fabricatorDesignHeight(MODE_TIMED_SPAM)));
	}

	/** Design height of the GUI Tools panel, used to keep it on screen. */
	private static int toolsDesignHeight() {
		return TOOLS_FORM_TOP + toolsRows.size() * TOOLS_ROW_SPACING + 6;
	}

	/** Every fabricator field, in the order the form lays them out. */
	private static List<AbstractWidget> fabricatorFields() {
		List<AbstractWidget> fields = new ArrayList<>();
		for (AbstractWidget widget : new AbstractWidget[] { clickSyncIdField,
			clickRevisionField, clickSlotField, clickButtonField, clickTimesField,
			buttonSyncIdField, buttonIdField, buttonTimesField, timedSlotField,
			timedButtonField, timedCountField, timedIntervalField })
			if (widget != null)
				fields.add(widget);
		if (actionDropdown != null)
			fields.add(actionDropdown);
		return fields;
	}

	private static void place(AbstractWidget widget, int x, int y) {
		if (widget == null)
			return;
		widget.setX(x);
		widget.setY(y);
	}

	/** Records a caption that the foreground pass draws above its field. */
	private static void addLabel(String text, int designY, AbstractWidget field) {
		if (field == null)
			return;
		overlayLabelTexts.add(text);
		// Stored relative to the panel, so the draw pass can scale it directly.
		overlayLabelDesignY.add(designY - overlayLayoutY);
		overlayLabelFields.add(field);
	}

	private static void placePair(AbstractWidget a, AbstractWidget b, int left, int right,
		int y) {
		place(a, left, y);
		place(b, right, y);
	}

	private static void renderFabricatorBackground(GuiGraphicsExtractor graphics) {
		if (!fabricatorInitialized || !UiUtilsState.fabricateOverlayOpen)
			return;
		// The body, the header and every label are chrome, so they are drawn here,
		// in the background pass: after the screen background but before the
		// widgets. That keeps them underneath the controls instead of being covered
		// by them, whatever order the screen events happen to fire in.
		drawPanelChrome(graphics, overlayX, overlayY, ps(OVERLAY_WIDTH),
			overlayBottomY, UiUtilsSettings.get().fabricateOverlayBgAlpha);
		drawFabricatorForeground(graphics);
	}

	/** Flat panel: body, top bar and a one pixel border. */
	private static void drawPanelChrome(GuiGraphicsExtractor graphics, int panelX,
		int panelY, int panelWidth, int panelBottom, int alpha) {
		// Panels sit over the game, so they stay readable even when the configured
		// background alpha is low.
		int bodyAlpha = Math.max(0xD8, Math.min(0xFF, alpha + 0x80));
		int headerAlpha = Math.min(0xFF, bodyAlpha + 0x10);
		int body = UiTheme.withAlpha(UiTheme.SURFACE, bodyAlpha);
		int header = UiTheme.withAlpha(UiTheme.SURFACE_HEADER, headerAlpha);
		int height = Math.max(1, panelBottom - panelY);
		graphics.fill(panelX, panelY, panelX + panelWidth, panelY + height, body);
		graphics.fill(panelX, panelY, panelX + panelWidth,
			panelY + ps(PANEL_HEADER_HEIGHT), header);
		graphics.fill(panelX, panelY + ps(PANEL_HEADER_HEIGHT) - 1,
			panelX + panelWidth, panelY + ps(PANEL_HEADER_HEIGHT),
			UiTheme.withAlpha(UiTheme.accent(), Math.min(255, headerAlpha)));
		UiTheme.border(graphics, panelX, panelY, panelWidth, height,
			UiTheme.BORDER);
	}

	private static void drawFabricatorForeground(GuiGraphicsExtractor graphics) {
		Font font = font();
		Minecraft mc = Minecraft.getInstance();
		int titleY = overlayY + (ps(PANEL_HEADER_HEIGHT) - Math.round(font.lineHeight * panelScale)) / 2 + 1;
		drawPanelText(graphics, "Fabricate Packet", overlayX + ps(6), titleY,
			0xFFEAEAEA);
		drawPinControl(graphics, overlayX, overlayY, ps(OVERLAY_WIDTH),
			UiUtilsSettings.get().fabricatePanelPinned);

		AbstractContainerMenu menu = mc.player == null ? null : mc.player.containerMenu;
		int infoColor = UiTheme.TEXT_DIM;
		drawPanelText(graphics, "syncId=" + (menu == null ? "-" : menu.containerId),
			overlayX + ps(6), overlayY + ps(OVERLAY_INFO_TOP), infoColor);
		drawPanelText(graphics,
			"revision=" + (menu == null ? "-" : menu.getStateId()),
			overlayX + ps(6), overlayY + ps(OVERLAY_INFO_TOP + INFO_LINE_HEIGHT),
			infoColor);
		UiUtilsGuiCache.Status saved = UiUtilsGuiCache.status(mc);
		int savedColor = !saved.present() ? UiTheme.TEXT_MUTED
			: saved.active() ? UiTheme.OK : UiTheme.WARN;
		drawPanelText(graphics, saved.present()
			? "Saved: " + saved.name() + " [" + saved.syncId() + "/"
				+ saved.revision() + "] " + (saved.active() ? "LIVE" : "STALE")
			: "Saved: none",
			overlayX + ps(6),
			overlayY + ps(OVERLAY_INFO_TOP + INFO_LINE_HEIGHT * 2), savedColor);

		drawLabels(graphics);

		String status = fabricateStatus;
		int color = fabricateStatusColor;
		if (fabricateMode == MODE_TIMED_SPAM && UiUtilsTimedClickSlot.isRunning()) {
			status = UiUtilsTimedClickSlot.status();
			color = 0xFF8BE88B;
		}
		if (status != null && !status.isBlank())
			drawPanelText(graphics, fitPanelText(status, OVERLAY_WIDTH - 12), overlayX + ps(6),
				overlayY + ps(overlayStatusDesignY), color);
	}

	/** Draws every caption recorded by the layout, above its own field. */
	private static void drawLabels(GuiGraphicsExtractor graphics) {
		for(int i = 0; i < overlayLabelTexts.size(); i++) {
			AbstractWidget field = i < overlayLabelFields.size()
				? overlayLabelFields.get(i) : null;
			if(field == null)
				continue;
			drawPanelText(graphics, overlayLabelTexts.get(i), field.getX(),
				overlayY + ps(overlayLabelDesignY.get(i)), 0xFFAAAAAA);
		}
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
		}, 0, 0, 10, 10), UiUtils.styledButton("Open NBT", b -> {
			UiUtilsNbtEditor.openEditor(Minecraft.getInstance());
			setToolsStatus("Opened the NBT editor", 0xFF8BE88B);
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
		updatePanelScale();
		int screenWidth = attachedScreen.width;
		int screenHeight = attachedScreen.height;
		int maxY = Math.max(4, screenHeight - ps(toolsDesignHeight()) - 4);
		if (UiUtilsState.guiToolsOverlayX < 0
			&& UiUtilsSettings.get().guiToolsPanelX >= 0) {
			UiUtilsState.guiToolsOverlayX = UiUtilsSettings.get().guiToolsPanelX;
			UiUtilsState.guiToolsOverlayY = UiUtilsSettings.get().guiToolsPanelY;
		}
		int x = UiUtilsState.guiToolsOverlayX >= 0 ? UiUtilsState.guiToolsOverlayX
			: screenWidth - ps(TOOLS_WIDTH) - 8;
		x = clampOverlayX(x, screenWidth, ps(TOOLS_WIDTH));
		int y = UiUtilsState.guiToolsOverlayY >= 0 ? UiUtilsState.guiToolsOverlayY
			: UiUtilsState.fabricateOverlayOpen && overlayBottomY > 0
				? overlayBottomY + 4 : 8;
		y = Mth.clamp(y, 4, maxY);
		if (toolsDragging) {
			x = clampOverlayX((int)mouseX - toolsDragX, screenWidth,
				ps(TOOLS_WIDTH));
			y = Mth.clamp((int)mouseY - toolsDragY, 4, maxY);
			UiUtilsState.guiToolsOverlayX = x;
			UiUtilsState.guiToolsOverlayY = y;
		}
		// Keep the panels from sharing a hit area. Resolve after applying drag
		// coordinates too, so releasing GUI Tools over Fabricate Packet cannot leave
		// their widgets stacked or steal clicks from one another.
		if (UiUtilsState.fabricateOverlayOpen && overlayBottomY > overlayY
			&& rectanglesOverlap(x, y, ps(TOOLS_WIDTH), ps(toolsDesignHeight()),
				overlayX, overlayY, ps(OVERLAY_WIDTH), overlayBottomY - overlayY)) {
			int toolsHeight = ps(toolsDesignHeight());
			int belowY = overlayBottomY + 4;
			int aboveY = overlayY - toolsHeight - 4;
			int rightX = overlayX + ps(OVERLAY_WIDTH) + 4;
			int leftX = overlayX - ps(TOOLS_WIDTH) - 4;
			if (belowY + toolsHeight <= screenHeight - 4)
				y = belowY;
			else if (aboveY >= 4)
				y = aboveY;
			else if (rightX + ps(TOOLS_WIDTH) <= screenWidth - 4)
				x = rightX;
			else if (leftX >= 4)
				x = leftX;
			else
				y = Mth.clamp(overlayY - toolsHeight - 4, 4, maxY);
			UiUtilsState.guiToolsOverlayX = x;
			UiUtilsState.guiToolsOverlayY = y;
		}
		x = clampOverlayX(x, screenWidth, ps(TOOLS_WIDTH));
		UiUtilsState.guiToolsOverlayX = x;

		int contentX = x + ps(6);
		int contentWidth = TOOLS_WIDTH - 12;
		int contentTop = y + TOOLS_FORM_TOP;
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
		toolsBottomY = y + ps(contentTop + toolsRows.size() * TOOLS_ROW_SPACING
			+ INFO_LINE_HEIGHT + 10 - y);
		UiUtilsState.guiToolsOverlayX = x;
		UiUtilsState.guiToolsOverlayY = y;
		scalePanelWidgets(toolsWidgets, x, y);
	}

	private static boolean rectanglesOverlap(int ax, int ay, int aw, int ah,
		int bx, int by, int bw, int bh) {
		return ax < bx + bw && ax + aw > bx && ay < by + bh && ay + ah > by;
	}

	/** Keep floating panels on screen while allowing placement across its full width. */
	private static int clampOverlayX(int x, int screenWidth, int panelWidth) {
		int maxX = Math.max(4, screenWidth - panelWidth - 4);
		return Mth.clamp(x, 4, maxX);
	}

	private static void renderToolsBackground(GuiGraphicsExtractor graphics) {
		if (!toolsInitialized || !UiUtilsState.guiToolsOverlayOpen)
			return;
		drawPanelChrome(graphics, toolsX, toolsY, ps(TOOLS_WIDTH), toolsBottomY,
			UiUtilsSettings.get().fabricateOverlayBgAlpha);
		drawToolsForeground(graphics);
	}

	private static void drawToolsForeground(GuiGraphicsExtractor graphics) {
		Font font = font();
		int titleY = toolsY + (ps(PANEL_HEADER_HEIGHT) - Math.round(font.lineHeight * panelScale)) / 2 + 1;
		drawPanelText(graphics, "GUI Tools", toolsX + ps(6), titleY, 0xFFEAEAEA);
		drawPinControl(graphics, toolsX, toolsY, ps(TOOLS_WIDTH),
			UiUtilsSettings.get().guiToolsPanelPinned);

		Minecraft mc = Minecraft.getInstance();
		AbstractContainerMenu menu = mc.player == null ? null : mc.player.containerMenu;
		int infoColor = UiTheme.TEXT_DIM;
		drawPanelText(graphics, "syncId=" + (menu == null ? "-" : menu.containerId),
			toolsX + ps(6), toolsY + ps(TOOLS_INFO_TOP), infoColor);
		drawPanelText(graphics,
			"revision=" + (menu == null ? "-" : menu.getStateId()),
			toolsX + ps(6), toolsY + ps(TOOLS_INFO_TOP + INFO_LINE_HEIGHT),
			infoColor);
		UiUtilsGuiCache.Status saved = UiUtilsGuiCache.status(mc);
		int savedColor = !saved.present() ? UiTheme.TEXT_MUTED
			: saved.active() ? UiTheme.OK : UiTheme.WARN;
		drawPanelText(graphics, "Saved: " + saved.label(), toolsX + ps(6),
			toolsY + ps(TOOLS_INFO_TOP + INFO_LINE_HEIGHT * 2), savedColor);
		if (UiUtilsContainerTransfer.isBusy())
			drawPanelText(graphics, UiUtilsContainerTransfer.status(),
				toolsX + ps(6),
				toolsY + ps(TOOLS_INFO_TOP + INFO_LINE_HEIGHT * 3), UiTheme.OK);
		if (toolsStatus != null && !toolsStatus.isBlank())
			drawPanelText(graphics, fitPanelText(toolsStatus, TOOLS_WIDTH - 12), toolsX + ps(6),
				toolsBottomY - ps(INFO_LINE_HEIGHT + 5), toolsStatusColor);
	}

	// ### Shared helpers ###

	private static Font font() {
		return Minecraft.getInstance().font;
	}

	/** Clipped panel line: scales down instead of spilling past the panel. */
	/** Draws one line of panel text at a raw pixel position, scaled with it. */
	private static void drawPanelText(GuiGraphicsExtractor graphics, String text,
		int x, int y, int color) {
		UiTheme.textScaled(graphics, font(), text, x, y, panelScale, color);
	}

	/** Keep transient status text inside the panel while preserving its useful ending. */
	private static String fitPanelText(String text, int maxWidth) {
		Font font = font();
		if (font.width(text) <= maxWidth)
			return text;
		String suffix = "…";
		int end = text.length();
		while (end > 0 && font.width(text.substring(0, end) + suffix) > maxWidth)
			end--;
		return text.substring(0, end) + suffix;
	}

	/** Clickable title-bar control that locks or unlocks a panel's position. */
	private static void drawPinControl(GuiGraphicsExtractor graphics, int panelX,
		int panelY, int panelWidth, boolean pinned) {
		int color = pinned ? UiTheme.DANGER : UiTheme.TEXT_DIM;
		int x = panelX + panelWidth - ps(15);
		int y = panelY + ps(5);
		for(int i = 0; i < 3; i++)
			graphics.fill(x, y + ps(i * 3), x + ps(9), y + ps(i * 3 + 1), color);
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
