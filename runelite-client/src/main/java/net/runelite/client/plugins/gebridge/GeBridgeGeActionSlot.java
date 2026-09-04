package net.runelite.client.plugins.gebridge;

import lombok.Value;

@Value
class GeBridgeGeActionSlot
{
	int slot;
	GeBridgeBounds slotBounds;
	GeBridgeBounds buyButton;
	GeBridgeBounds sellButton;
	GeBridgeBounds openButton;
}
