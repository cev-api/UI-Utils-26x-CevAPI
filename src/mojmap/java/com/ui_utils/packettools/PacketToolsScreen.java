/*
 * Copyright (c) 2014-2026 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package com.ui_utils.packettools;

import com.mojang.blaze3d.platform.InputConstants;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.InputWithModifiers;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;
import com.ui_utils.packettools.AdvancedPacketTool.PacketDirection;
import com.ui_utils.packettools.AdvancedPacketTool.PacketMode;
import com.ui_utils.uiutils.McCompat;
import com.ui_utils.uiutils.ui.UiButton;
import com.ui_utils.uiutils.ui.UiTheme;
import java.util.function.Consumer;

public final class PacketToolsScreen extends Screen
{
	private final Screen parent;
	private final AdvancedPacketTool packetTools;

	private PacketMode editMode = PacketMode.LOG;

	private PacketActionButton loggingButton;
	private PacketActionButton denyButton;
	private PacketActionButton delayButton;
	private PacketActionButton outputButton;
	private PacketActionButton modeButton;
	private PacketActionButton outsideGameButton;
	private PacketActionButton delayMinusButton;
	private PacketActionButton delayPlusButton;

	// Set in init() so the label stays between - and + regardless of how many
	// rows the header grows to.
	private int delayLabelX;
	private int delayLabelY;

	private boolean holdingDelayMinus;
	private boolean holdingDelayPlus;
	private int holdTicks;

	private DualPacketListWidget s2cSelector;
	private DualPacketListWidget c2sSelector;

	public PacketToolsScreen(Screen parent, AdvancedPacketTool packetTools)
	{
		super(Component.literal("Advanced Packet Tool"));
		this.parent = parent;
		this.packetTools = packetTools;
	}

	@Override
	protected void init()
	{
		super.init();

		int panelWidth = Math.max(80, Math.min(544, width - 46));
		int panelX = (width - panelWidth) / 2;
		int y = 32;
		int gap = 6;

		int third = (panelWidth - gap * 2) / 3;

		loggingButton = addRenderableWidget(packetButton(enabledLabel("Logging",
			packetTools.isLoggingEnabled()), panelX, y, third, 20, b -> {
				boolean value = !packetTools.isLoggingEnabled();
				packetTools.setLoggingEnabled(value);
				b.setMessage(enabledLabel("Logging", value));
			}));

		denyButton = addRenderableWidget(packetButton(enabledLabel("Deny",
			packetTools.isDenyEnabled()), panelX + third + gap, y, third, 20, b -> {
				boolean value = !packetTools.isDenyEnabled();
				packetTools.setDenyEnabled(value);
				b.setMessage(enabledLabel("Deny", value));
			}));

		delayButton = addRenderableWidget(packetButton(enabledLabel("Delay",
			packetTools.isDelayEnabled()), panelX + (third + gap) * 2, y, third, 20,
				b -> {
					boolean value = !packetTools.isDelayEnabled();
					packetTools.setDelayEnabled(value);
					b.setMessage(enabledLabel("Delay", value));
				}));

		y += 26;

		outputButton = addRenderableWidget(packetButton(Component.literal(
			"Output: " + (packetTools.isFileOutput() ? "File" : "Chat")),
			panelX, y, third, 20, b -> {
				boolean file = !packetTools.isFileOutput();
				packetTools.setFileOutput(file);
				b.setMessage(
					Component.literal("Output: " + (file ? "File" : "Chat")));
			}));

		modeButton = addRenderableWidget(packetButton(
			Component.literal("Editing: " + editMode.getLabel()),
			panelX + third + gap, y, third, 20, b -> {
					PacketMode previous = editMode;
					editMode = editMode.next();
					b.setMessage(
						Component.literal("Editing: " + editMode.getLabel()));
					reloadSelectorsFromMode(previous);
			}));

		outsideGameButton = addRenderableWidget(packetButton(
			enabledLabel("Outside-game",
				packetTools.isVerboseOutsideGame()),
			panelX + (third + gap) * 2, y, third, 20, b -> {
				boolean value = !packetTools.isVerboseOutsideGame();
				packetTools.setVerboseOutsideGame(value);
				b.setMessage(enabledLabel("Outside-game", value));
			}));

		y += 26;

		addRenderableWidget(packetButton(
			enabledLabel("Verbose", packetTools.isVerboseEnabled()),
			panelX, y, third, 20, b -> {
				boolean value = !packetTools.isVerboseEnabled();
				packetTools.setVerboseEnabled(value);
				b.setMessage(enabledLabel("Verbose", value));
			}));

		addRenderableWidget(packetButton(
			enabledLabel("Human log", packetTools.isVerboseHumanReadable()),
			panelX + third + gap, y, third, 20, b -> {
				boolean value = !packetTools.isVerboseHumanReadable();
				packetTools.setVerboseHumanReadable(value);
				b.setMessage(enabledLabel("Human log", value));
			}));

		// Verbose controls get their own row; without this they were drawn on top
		// of the S2C/C2S selection controls below.
		y += 26;

		int controlWidth = 70;
		addRenderableWidget(packetButton(Component.literal("S2C All"), panelX, y,
			controlWidth, 20, b -> s2cSelector.selectAll()));
		addRenderableWidget(packetButton(Component.literal("S2C None"),
			panelX + controlWidth + gap, y, controlWidth + 12, 20,
			b -> s2cSelector.clearAll()));
		addRenderableWidget(packetButton(Component.literal("C2S All"),
			panelX + panelWidth - (controlWidth * 2 + gap + 12), y, controlWidth,
			20, b -> c2sSelector.selectAll()));
		addRenderableWidget(packetButton(Component.literal("C2S None"),
			panelX + panelWidth - (controlWidth + 12), y, controlWidth + 12, 20,
			b -> c2sSelector.clearAll()));

		int delayCenter = panelX + panelWidth / 2;
		delayMinusButton = addRenderableWidget(packetButton(Component.literal("-"),
			delayCenter - 66, y, 20, 20, b -> changeDelay(-1)));
		delayPlusButton = addRenderableWidget(packetButton(Component.literal("+"),
			delayCenter + 46, y, 20, 20, b -> changeDelay(1)));
		delayLabelX = delayCenter;
		delayLabelY = y + 6;

		y += 27;
		// Clamp so the lists stay usable on short windows now that the header
		// stack is taller.
		int selectorHeight = Math.max(40, (height - y - 56) / 2 - 4);

		s2cSelector = new DualPacketListWidget(panelX, y, panelWidth,
			selectorHeight, "S2C Packets (Server -> Client)",
			packetTools.getAvailablePackets(PacketDirection.S2C),
			packetTools.getSelection(editMode, PacketDirection.S2C), set -> {});
		addRenderableWidget(s2cSelector.getSearchBox());

		y += selectorHeight + 10;

		c2sSelector = new DualPacketListWidget(panelX, y, panelWidth,
			selectorHeight, "C2S Packets (Client -> Server)",
			packetTools.getAvailablePackets(PacketDirection.C2S),
			packetTools.getSelection(editMode, PacketDirection.C2S), set -> {});
		addRenderableWidget(c2sSelector.getSearchBox());

		int bottomY = height - 28;
		addRenderableWidget(packetButton(Component.literal("Save"),
			width / 2 - 105, bottomY, 100, 20, b -> saveAndClose()));
		addRenderableWidget(packetButton(Component.literal("Cancel"),
			width / 2 + 5, bottomY, 100, 20, b -> onClose()));
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor context, int mouseX,
		int mouseY, float partialTick)
	{
		context.fillGradient(0, 0, width, height, 0xA0101010, 0xB0101010);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor context, int mouseX,
		int mouseY, float partialTicks)
	{
		extractBackground(context, mouseX, mouseY, partialTicks);

		int panelWidth = Math.max(80, Math.min(544, width - 46));
		int panelX = (width - panelWidth) / 2;
		int frameX = panelX - 8;
		int frameY = 1;
		int frameWidth = panelWidth + 16;
		int frameHeight = height - 5;
		context.fill(frameX, frameY, frameX + frameWidth,
			frameY + frameHeight, UiTheme.WINDOW);
		context.fill(frameX + 1, frameY + 1, frameX + frameWidth - 1, 22,
			UiTheme.SURFACE_HEADER);
		context.fill(frameX + 1, 22, frameX + frameWidth - 1, 23,
			UiTheme.accent());
		UiTheme.border(context, frameX, frameY, frameWidth, frameHeight,
			UiTheme.BORDER);

		s2cSelector.extractRenderState(context, mouseX, mouseY, partialTicks);
		c2sSelector.extractRenderState(context, mouseX, mouseY, partialTicks);

		super.extractRenderState(context, mouseX, mouseY, partialTicks);

		context.centeredText(font, title, width / 2, 7, 0xFFFFFFFF);

		String delayLabel = "Delay ticks: " + packetTools.getDelayTicks();
		context.centeredText(font, delayLabel, delayLabelX, delayLabelY,
			0xFFFFFFFF);
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick)
	{
		boolean superHandled = super.mouseClicked(event, doubleClick);
		boolean selectorHandled = s2cSelector.mouseClicked(event, doubleClick)
			|| c2sSelector.mouseClicked(event, doubleClick);

		boolean leftClick = event.button() == InputConstants.MOUSE_BUTTON_LEFT;
		holdingDelayMinus = leftClick && delayMinusButton != null
			&& delayMinusButton.isHoveredOrFocused();
		holdingDelayPlus = leftClick && delayPlusButton != null
			&& delayPlusButton.isHoveredOrFocused();
		if(holdingDelayMinus || holdingDelayPlus)
			holdTicks = 0;

		return superHandled || selectorHandled;
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent event)
	{
		s2cSelector.mouseReleased(event);
		c2sSelector.mouseReleased(event);

		holdingDelayMinus = false;
		holdingDelayPlus = false;
		holdTicks = 0;
		return super.mouseReleased(event);
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent event, double deltaX,
		double deltaY)
	{
		boolean dragged = s2cSelector.mouseDragged(event, deltaX, deltaY)
			|| c2sSelector.mouseDragged(event, deltaX, deltaY);
		return dragged || super.mouseDragged(event, deltaX, deltaY);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY,
		double horizontalAmount, double verticalAmount)
	{
		if(s2cSelector.mouseScrolled(mouseX, mouseY, verticalAmount))
			return true;
		if(c2sSelector.mouseScrolled(mouseX, mouseY, verticalAmount))
			return true;
		return super.mouseScrolled(mouseX, mouseY, horizontalAmount,
			verticalAmount);
	}

	@Override
	public boolean keyPressed(KeyEvent event)
	{
		if(super.keyPressed(event))
			return true;
		return s2cSelector.keyPressed(event) || c2sSelector.keyPressed(event);
	}

	@Override
	public boolean charTyped(CharacterEvent event)
	{
		if(super.charTyped(event))
			return true;
		return s2cSelector.charTyped(event) || c2sSelector.charTyped(event);
	}

	@Override
	public void tick()
	{
		super.tick();

		if(!(holdingDelayMinus || holdingDelayPlus))
		{
			holdTicks = 0;
			return;
		}

		holdTicks++;
		if(holdTicks < 6)
			return;

		int step = holdTicks >= 50 ? 50 : holdTicks >= 30 ? 20 : 10;
		changeDelay(holdingDelayPlus ? step : -step);
	}

	private void saveAndClose()
	{
		packetTools.updateSelection(editMode, PacketDirection.S2C,
			s2cSelector.getSelection());
		packetTools.updateSelection(editMode, PacketDirection.C2S,
			c2sSelector.getSelection());
		packetTools.saveSelectionConfig();
		onClose();
	}

	private void reloadSelectorsFromMode(PacketMode previousMode)
	{
		packetTools.updateSelection(previousMode, PacketDirection.S2C,
			s2cSelector.getSelection());
		packetTools.updateSelection(previousMode, PacketDirection.C2S,
			c2sSelector.getSelection());

		s2cSelector
			.setPackets(packetTools.getAvailablePackets(PacketDirection.S2C));
		c2sSelector
			.setPackets(packetTools.getAvailablePackets(PacketDirection.C2S));
		s2cSelector.setSelection(
			packetTools.getSelection(editMode, PacketDirection.S2C));
		c2sSelector.setSelection(
			packetTools.getSelection(editMode, PacketDirection.C2S));
	}

	private void changeDelay(int delta)
	{
		int value = packetTools.getDelayTicks();
		value = Math.max(0, Math.min(9999, value + delta));
		packetTools.setDelayTicks(value);
	}

	@Override
	public void onClose()
	{
		if(minecraft != null)
			McCompat.setScreen(minecraft, parent);
	}

	@Override
	public boolean isPauseScreen()
	{
		return false;
	}

	private static PacketActionButton packetButton(Component label, int x, int y,
		int width, int height, Consumer<PacketActionButton> action) {
		return new PacketActionButton(x, y, width, height, label, action);
	}

	private static final class PacketActionButton extends UiButton {
		private final Consumer<PacketActionButton> action;

		private PacketActionButton(int x, int y, int width, int height,
			Component label, Consumer<PacketActionButton> action) {
			super(x, y, width, height, label, null);
			this.action = action;
			style(Kind.PRIMARY);
		}

		@Override
		public void onPress(InputWithModifiers input) {
			action.accept(this);
		}
	}

	private static Component enabledLabel(String name, boolean enabled)
	{
		Component state = Component.literal(enabled ? "ON" : "OFF")
			.withStyle(enabled ? ChatFormatting.GREEN : ChatFormatting.RED);
		return Component.literal(name + ": ").append(state);
	}
}
