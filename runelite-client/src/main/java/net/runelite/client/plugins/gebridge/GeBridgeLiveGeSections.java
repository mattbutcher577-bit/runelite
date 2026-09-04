package net.runelite.client.plugins.gebridge;

import lombok.Value;
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
		String promptText = prompt == null ? null : Text.removeTags(prompt.getText());
		if (promptText != null)
		{
			promptText = promptText.trim();
		}

		String mode = GeBridgeGeInputClassifier.classify(offerSetupOpen, messageLayerMode, promptText);
		GeBridgeGeInputState geInput = new GeBridgeGeInputState(
			mode,
			tick,
			boundsOf(prompt),
			boundsOf(inputField));
		GeBridgeGeActionState geActions = GeBridgeGeActionReader.read(window, setup, tick);
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
