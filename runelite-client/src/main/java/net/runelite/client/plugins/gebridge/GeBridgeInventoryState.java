package net.runelite.client.plugins.gebridge;

import lombok.Value;

@Value
class GeBridgeInventoryState
{
	int capacity;
	int occupiedSlots;
	int freeSlots;
}
