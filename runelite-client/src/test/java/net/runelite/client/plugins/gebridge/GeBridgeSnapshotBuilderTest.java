package net.runelite.client.plugins.gebridge;

import com.google.gson.Gson;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import net.runelite.api.GameState;
import net.runelite.api.GrandExchangeOffer;
import net.runelite.api.GrandExchangeOfferState;
import net.runelite.api.Item;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import org.junit.Test;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class GeBridgeSnapshotBuilderTest
{
	@Test
	public void testStateCompatibilityMapping()
	{
		assertEquals("EMPTY", GeBridgeStateMapper.visualFor(GrandExchangeOfferState.EMPTY));
		assertEquals("ORANGE", GeBridgeStateMapper.visualFor(GrandExchangeOfferState.BUYING));
		assertEquals("ORANGE", GeBridgeStateMapper.visualFor(GrandExchangeOfferState.SELLING));
		assertEquals("GREEN", GeBridgeStateMapper.visualFor(GrandExchangeOfferState.BOUGHT));
		assertEquals("GREEN", GeBridgeStateMapper.visualFor(GrandExchangeOfferState.SOLD));
		assertEquals("RED", GeBridgeStateMapper.visualFor(GrandExchangeOfferState.CANCELLED_BUY));
		assertEquals("RED", GeBridgeStateMapper.visualFor(GrandExchangeOfferState.CANCELLED_SELL));
	}

	@Test
	public void testSnapshotContainsProtocolV5ExactStateAndSearchResultsWithoutQueryLeak()
	{
		GrandExchangeOffer offer = mock(GrandExchangeOffer.class);
		when(offer.getItemId()).thenReturn(314);
		when(offer.getState()).thenReturn(GrandExchangeOfferState.BUYING);
		when(offer.getPrice()).thenReturn(12);
		when(offer.getTotalQuantity()).thenReturn(1000);
		when(offer.getQuantitySold()).thenReturn(420);
		when(offer.getSpent()).thenReturn(5040);

		Item[] inventory = {
			new Item(995, 50000),
			new Item(314, 100),
			new Item(995, 3000),
			new Item(-1, 0)
		};

		GeBridgeSearchResult result = new GeBridgeSearchResult(
			0,
			314,
			"Feather",
			new GeBridgeBounds(40, 410, 32, 32, true),
			new GeBridgeBounds(80, 410, 150, 24, true));
		GeBridgeSearchState searchState = new GeBridgeSearchState(
			true,
			"Feather",
			Collections.singletonList(result));

		GeBridgeSnapshot snapshot = GeBridgeSnapshotBuilder.build(
			GameState.LOGGED_IN,
			new GrandExchangeOffer[]{offer},
			inventory,
			123456789L,
			42L,
			new GeBridgeClientState(
				true, 301, Collections.emptyList(), false,
				773, 535, 104, 232, true,
				765, 503, 4, 4, 548, 50),
			new GeBridgePlayerState(true, 3164, 3487, 0),
			new GeBridgeInterfaceState(true, true, false, false, false, true, false),
			new GeBridgeGeState(
				true, true, -1,
				new GeBridgeBounds(20, 20, 500, 360, true),
				new GeBridgeBounds(40, 80, 440, 280, true),
				new GeBridgeBounds(550, 200, 180, 250, true)),
			new GeBridgeSafetyState(true, true, false, false),
			new GeBridgeInputState(
				123456700L, 123456600L, 123456700L, 123456650L,
				123456680L, 123456500L, 123456400L,
				400, 250, true, 0, 1, -1, "SHIFT", 89L),
			searchState
		);

		assertEquals(5, snapshot.getProtocol());
		assertEquals(53000, snapshot.getInventoryGp());
		assertEquals(42L, snapshot.getTick());
		assertEquals("LOGGED_IN", snapshot.getGameState());
		assertEquals(104, snapshot.getClient().getCanvasScreenX());
		assertEquals(232, snapshot.getClient().getCanvasScreenY());
		assertTrue(snapshot.getClient().isCanvasScreenPositionValid());
		assertTrue(snapshot.getSearch().isOpen());
		assertEquals(1, snapshot.getSearch().getResults().size());
		assertEquals(314, snapshot.getSearch().getResults().get(0).getItemId());
		assertEquals("Feather", snapshot.getSearch().getResults().get(0).getName());
		assertTrue(snapshot.getSearch().getResults().get(0).getNameBounds().isValid());

		String json = new Gson().toJson(snapshot);
		assertFalse("protocol 5 must not publish raw GE search text", json.contains("\"query\""));
		assertFalse("protocol 5 must not publish the typed search value", json.contains("\"Feather\"") && json.contains("\"query\""));

		List<GeBridgeSlot> slots = snapshot.getSlots();
		assertEquals(1, slots.size());
		assertEquals("BUYING", slots.get(0).getState());
		assertEquals("ORANGE", slots.get(0).getVisual());
		assertFalse(slots.get(0).isCollectReady());

		Map<Integer, Integer> inventoryMap = snapshot.inventoryAsMap();
		assertEquals(Integer.valueOf(53000), inventoryMap.get(995));
		assertEquals(Integer.valueOf(100), inventoryMap.get(314));
		assertEquals(3, snapshot.getInventoryState().getOccupiedSlots());
		assertEquals(25, snapshot.getInventoryState().getFreeSlots());
	}

	@Test
	public void testInventorySlotCountCapsAtCapacity()
	{
		Item[] inventory = new Item[30];
		Arrays.fill(inventory, new Item(995, 1));
		GeBridgeSnapshot snapshot = buildSimpleSnapshot(new GrandExchangeOffer[0], inventory);
		assertEquals(28, snapshot.getInventoryState().getOccupiedSlots());
		assertEquals(0, snapshot.getInventoryState().getFreeSlots());
	}

	@Test
	public void testNullOfferBecomesEmptySlot()
	{
		GeBridgeSnapshot snapshot = buildSimpleSnapshot(new GrandExchangeOffer[]{null}, new Item[0]);
		GeBridgeSlot slot = snapshot.getSlots().get(0);
		assertEquals(-1, slot.getItemId());
		assertEquals("EMPTY", slot.getState());
		assertEquals("EMPTY", slot.getVisual());
		assertFalse(slot.isCollectReady());
	}

	private static GeBridgeSnapshot buildSimpleSnapshot(GrandExchangeOffer[] offers, Item[] inventory)
	{
		return GeBridgeSnapshotBuilder.build(
			GameState.LOGGED_IN,
			offers,
			inventory,
			1L,
			1L,
			new GeBridgeClientState(
				true, 301, Collections.emptyList(), false,
				773, 535, 104, 232, true,
				765, 503, 4, 4, 548, 50),
			new GeBridgePlayerState(true, 3164, 3487, 0),
			new GeBridgeInterfaceState(true, false, false, false, false, false, false),
			new GeBridgeGeState(
				true, false, -1,
				new GeBridgeBounds(20, 20, 500, 360, true),
				GeBridgeBounds.invalid(),
				new GeBridgeBounds(550, 200, 180, 250, true)),
			new GeBridgeSafetyState(true, false, true, true),
			new GeBridgeInputState(0L, 0L, 0L, 0L, 0L, 0L, 0L, -1, -1, false, 0, 0, 0, "", -1L),
			GeBridgeSearchState.closed()
		);
	}
}
