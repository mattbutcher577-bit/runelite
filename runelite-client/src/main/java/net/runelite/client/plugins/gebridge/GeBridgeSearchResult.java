package net.runelite.client.plugins.gebridge;

import lombok.Value;

@Value
class GeBridgeSearchResult
{
	int index;
	int itemId;
	String name;
	GeBridgeBounds iconBounds;
	GeBridgeBounds nameBounds;
}
