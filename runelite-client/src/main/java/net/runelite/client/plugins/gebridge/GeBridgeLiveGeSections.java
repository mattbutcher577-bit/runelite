package net.runelite.client.plugins.gebridge;

import lombok.Value;
import net.runelite.api.GrandExchangeOffer;
import net.runelite.api.widgets.Widget;
import net.runelite.client.game.ItemManager;
import net.runelite.client.util.Text;

@Value
class GeBridgeLiveGeSections
{
	GeBridgeGeInputState geInput;
	GeBridgeGeActionState geActions;
	GeBridgeGeInventoryState geInventory;

	static GeBridgeLiveGeSections read(
		boolean offerSetupOpen,
		int messageLayerMode,
		Widget prompt,
		Widget inputField,
		Widget window,
		Widget setup,
		Widget inventory,
		ItemManager itemManager,
		long tick)
	{
		return read(
			offerSetupOpen,
			messageLayerMode,
			prompt,
			inputField,
			false,
			-1,
			window,
			setup,
			inventory,
			itemManager,
			null,
			null,
			null,
			null,
			null,
			tick);
	}

	static GeBridgeLiveGeSections read(
		boolean offerSetupOpen,
		int messageLayerMode,
		Widget prompt,
		Widget inputField,
		Widget window,
		Widget setup,
		Widget inventory,
		ItemManager itemManager,
		GrandExchangeOffer[] offers,
		long tick)
	{
		return read(
			offerSetupOpen,
			messageLayerMode,
			prompt,
			inputField,
			false,
			-1,
			window,
			setup,
			inventory,
			itemManager,
			offers,
			null,
			null,
			null,
			null,
			tick);
	}

	static GeBridgeLiveGeSections read(
		boolean offerSetupOpen,
		int messageLayerMode,
		Widget prompt,
		Widget inputField,
		boolean buySetup,
		int setupItemId,
		Widget window,
		Widget setup,
		Widget inventory,
		ItemManager itemManager,
		GrandExchangeOffer[] offers,
		long tick)
	{
		return read(
			offerSetupOpen,
			messageLayerMode,
			prompt,
			inputField,
			buySetup,
			setupItemId,
			window,
			setup,
			inventory,
			itemManager,
			offers,
			null,
			null,
			null,
			null,
			tick);
	}

	static GeBridgeLiveGeSections read(
		boolean offerSetupOpen,
		int messageLayerMode,
		Widget prompt,
		Widget inputField,
		boolean buySetup,
		int setupItemId,
		Widget window,
		Widget setup,
		Widget inventory,
		ItemManager itemManager,
		GrandExchangeOffer[] offers,
		Widget[] slotRoots,
		long tick)
	{
		return read(
			offerSetupOpen,
			messageLayerMode,
			prompt,
			inputField,
			buySetup,
			setupItemId,
			window,
			setup,
			inventory,
			itemManager,
			offers,
			slotRoots,
			null,
			null,
			null,
			tick);
	}

	static GeBridgeLiveGeSections read(
		boolean offerSetupOpen,
		int messageLayerMode,
		Widget prompt,
		Widget inputField,
		boolean buySetup,
		int setupItemId,
		Widget window,
		Widget setup,
		Widget inventory,
		ItemManager itemManager,
		GrandExchangeOffer[] offers,
		Widget[] slotRoots,
		Widget collectRoot,
		Widget modifyRoot,
		long tick)
	{
		return read(
			offerSetupOpen,
			messageLayerMode,
			prompt,
			inputField,
			buySetup,
			setupItemId,
			window,
			setup,
			inventory,
			itemManager,
			offers,
			slotRoots,
			null,
			collectRoot,
			modifyRoot,
			tick);
	}

	static GeBridgeLiveGeSections read(
		boolean offerSetupOpen,
		int messageLayerMode,
		Widget prompt,
		Widget inputField,
		boolean buySetup,
		int setupItemId,
		Widget window,
		Widget setup,
		Widget inventory,
		ItemManager itemManager,
		GrandExchangeOffer[] offers,
		Widget[] slotRoots,
		Widget detailsRoot,
		Widget collectRoot,
		Widget modifyRoot,
		long tick)
	{
		String promptText = prompt == null ? null : Text.removeTags(prompt.getText());
		if (promptText != null)
		{
			promptText = promptText.trim();
		}

		boolean fullInputVisible = inputField != null && !inputField.isHidden();
		String mode = GeBridgeGeInputClassifier.classify(
			offerSetupOpen,
			messageLayerMode,
			promptText,
			fullInputVisible,
			buySetup,
			setupItemId);
		GeBridgeGeInputState geInput = new GeBridgeGeInputState(
			mode,
			tick,
			boundsOf(prompt),
			boundsOf(inputField));
		GeBridgeGeActionState geActions = GeBridgeGeActionReader.read(
			window, setup, offers, slotRoots, detailsRoot, collectRoot, modifyRoot, tick);
		GeBridgeGeInventoryState geInventory = GeBridgeGeInventoryReader.read(inventory, itemManager, tick);
		return new GeBridgeLiveGeSections(geInput, geActions, geInventory);
	}

	private static GeBridgeBounds boundsOf(Widget widget)
	{
		return widget == null || widget.isHidden()
			? GeBridgeBounds.invalid()
			: GeBridgeBounds.from(widget.getBounds());
	}
}
