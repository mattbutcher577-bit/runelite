package net.runelite.client.plugins.gebridge;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.runelite.api.GameState;
import net.runelite.api.GrandExchangeOffer;
import net.runelite.api.GrandExchangeOfferState;
import net.runelite.api.Item;

final class GeBridgeSnapshotBuilder
{
	static final int PROTOCOL = 5;
	static final int COINS_ID = 995;
	static final int INVENTORY_CAPACITY = 28;

	private GeBridgeSnapshotBuilder()
	{
	}

	static GeBridgeSnapshot build(
		GameState gameState,
		GrandExchangeOffer[] offers,
		Item[] inventory,
		long generatedAtEpochMs,
		long tick,
		GeBridgeClientState clientState,
		GeBridgePlayerState playerState,
		GeBridgeInterfaceState interfaceState,
		GeBridgeGeState geState,
		GeBridgeSafetyState safetyState,
		GeBridgeInputState inputState,
		GeBridgeSearchState searchState)
	{
		List<GeBridgeSlot> slots = new ArrayList<>();
		if (offers != null)
		{
			for (int i = 0; i < offers.length; i++)
			{
				slots.add(toSlot(i, offers[i]));
			}
		}

		Map<Integer, Integer> aggregated = new LinkedHashMap<>();
		int occupiedSlots = 0;
		if (inventory != null)
		{
			for (Item item : inventory)
			{
				if (item == null || item.getId() < 0 || item.getQuantity() <= 0)
				{
					continue;
				}
				occupiedSlots++;
				aggregated.merge(item.getId(), item.getQuantity(), Integer::sum);
			}
		}

		List<GeBridgeInventoryItem> inventoryItems = new ArrayList<>();
		for (Map.Entry<Integer, Integer> entry : aggregated.entrySet())
		{
			inventoryItems.add(new GeBridgeInventoryItem(entry.getKey(), entry.getValue()));
		}

		int boundedOccupiedSlots = Math.min(INVENTORY_CAPACITY, occupiedSlots);
		GeBridgeInventoryState inventoryState = new GeBridgeInventoryState(
			INVENTORY_CAPACITY,
			boundedOccupiedSlots,
			Math.max(0, INVENTORY_CAPACITY - boundedOccupiedSlots)
		);

		return new GeBridgeSnapshot(
			PROTOCOL,
			generatedAtEpochMs,
			tick,
			gameState == null ? GameState.UNKNOWN.name() : gameState.name(),
			slots,
			inventoryItems,
			aggregated.getOrDefault(COINS_ID, 0),
			clientState,
			playerState,
			interfaceState,
			geState,
			inventoryState,
			safetyState,
			inputState,
			searchState == null ? GeBridgeSearchState.closed() : searchState
		);
	}

	private static GeBridgeSlot toSlot(int slot, GrandExchangeOffer offer)
	{
		if (offer == null)
		{
			return emptySlot(slot);
		}

		GrandExchangeOfferState state = offer.getState();
		if (state == null)
		{
			state = GrandExchangeOfferState.EMPTY;
		}

		return new GeBridgeSlot(
			slot,
			offer.getItemId(),
			state.name(),
			GeBridgeStateMapper.visualFor(state),
			offer.getPrice(),
			offer.getTotalQuantity(),
			offer.getQuantitySold(),
			offer.getSpent(),
			GeBridgeStateMapper.collectReady(state)
		);
	}

	private static GeBridgeSlot emptySlot(int slot)
	{
		return new GeBridgeSlot(slot, -1, GrandExchangeOfferState.EMPTY.name(), "EMPTY", 0, 0, 0, 0, false);
	}
}
