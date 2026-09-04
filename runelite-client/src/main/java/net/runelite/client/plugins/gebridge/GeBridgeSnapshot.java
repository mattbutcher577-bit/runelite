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
	long tick;
	String gameState;
	List<GeBridgeSlot> slots;
	List<GeBridgeInventoryItem> inventory;
	int inventoryGp;
	GeBridgeClientState client;
	GeBridgePlayerState player;
	GeBridgeInterfaceState interfaces;
	GeBridgeGeState ge;
	GeBridgeInventoryState inventoryState;
	GeBridgeSafetyState safety;

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
