package net.runelite.client.plugins.gebridge;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.Value;

@Value
class GeBridgeSnapshot
{
	int protocol;
	long generatedAtEpochMs;
	String gameState;
	List<GeBridgeSlot> slots;
	List<GeBridgeInventoryItem> inventory;
	int inventoryGp;

	Map<Integer, Integer> inventoryAsMap()
	{
		Map<Integer, Integer> result = new LinkedHashMap<>();
		for (GeBridgeInventoryItem item : inventory)
		{
			result.put(item.getItemId(), item.getQuantity());
		}
		return result;
	}
}
