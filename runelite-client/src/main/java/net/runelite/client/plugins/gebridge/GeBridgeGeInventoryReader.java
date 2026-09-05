package net.runelite.client.plugins.gebridge;

import java.util.ArrayList;
import java.util.List;
import net.runelite.api.widgets.Widget;
import net.runelite.client.game.ItemManager;

final class GeBridgeGeInventoryReader
{
	private GeBridgeGeInventoryReader()
	{
	}

	static GeBridgeGeInventoryState read(Widget container, ItemManager itemManager, long tick)
	{
		if (container == null || container.isHidden())
		{
			return GeBridgeGeInventoryState.closed(tick);
		}

		Widget[] children = container.getChildren();
		if (children == null || children.length == 0)
		{
			return new GeBridgeGeInventoryState(true, tick, new ArrayList<>());
		}

		List<GeBridgeGeInventoryEntry> entries = new ArrayList<>();
		for (int slot = 0; slot < children.length; slot++)
		{
			Widget child = children[slot];
			if (child == null || child.isHidden())
			{
				continue;
			}
			int rawItemId = child.getItemId();
			int quantity = child.getItemQuantity();
			if (rawItemId <= 0 || quantity <= 0)
			{
				continue;
			}
			int canonicalItemId = itemManager == null ? rawItemId : itemManager.canonicalize(rawItemId);
			entries.add(new GeBridgeGeInventoryEntry(
				slot,
				rawItemId,
				canonicalItemId,
				quantity,
				GeBridgeBounds.from(child.getBounds())));
		}
		return new GeBridgeGeInventoryState(true, tick, entries);
	}
}
