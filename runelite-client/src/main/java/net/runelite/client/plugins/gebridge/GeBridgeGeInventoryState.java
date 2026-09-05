package net.runelite.client.plugins.gebridge;

import java.util.Collections;
import java.util.List;
import lombok.Value;

@Value
class GeBridgeGeInventoryState
{
	boolean open;
	long updatedTick;
	List<GeBridgeGeInventoryEntry> entries;

	static GeBridgeGeInventoryState closed(long tick)
	{
		return new GeBridgeGeInventoryState(false, tick, Collections.emptyList());
	}
}
