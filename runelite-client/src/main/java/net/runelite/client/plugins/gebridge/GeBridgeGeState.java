package net.runelite.client.plugins.gebridge;

import lombok.Value;

@Value
class GeBridgeGeState
{
	boolean open;
	boolean offerSetupOpen;
	int offerSetupItemId;
	GeBridgeBounds windowBounds;
	GeBridgeBounds offerSetupBounds;
	GeBridgeBounds inventoryBounds;
}
