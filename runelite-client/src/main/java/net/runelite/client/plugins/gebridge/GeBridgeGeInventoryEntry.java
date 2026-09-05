package net.runelite.client.plugins.gebridge;

import lombok.Value;

@Value
class GeBridgeGeInventoryEntry
{
	int inventorySlot;
	int rawItemId;
	int canonicalItemId;
	int quantity;
	GeBridgeBounds bounds;
}
