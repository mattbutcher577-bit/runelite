package net.runelite.client.plugins.gebridge;

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
	public void testCollectReadyMapping()
	{
		assertFalse(GeBridgeStateMapper.collectReady(GrandExchangeOfferState.EMPTY));
		assertFalse(GeBridgeStateMapper.collectReady(GrandExchangeOfferState.BUYING));
		assertFalse(GeBridgeStateMapper.collectReady(GrandExchangeOfferState.SELLING));
		assertTrue(GeBridgeStateMapper.collectReady(GrandExchangeOfferState.BOUGHT));
		assertTrue(GeBridgeStateMapper.collectReady(GrandExchangeOfferState.SOLD));
		assertTrue(GeBridgeStateMapper.collectReady(GrandExchangeOfferState.CANCELLED_BUY));
		assertTrue(GeBridgeStateMapper.collectReady(GrandExchangeOfferState.CANCELLED_SELL));
	}

	@Test
	public void testSnapshotContainsExactOfferAndAggregatedInventory()
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

		GeBridgeSnapshot snapshot = GeBridgeSnapshotBuilder.build(
			GameState.LOGGED_IN,
			new GrandExchangeOffer[]{offer},
			inventory,
			123456789L
		);

		assertEquals(1, snapshot.getProtocol());
		assertEquals(123456789L, snapshot.getGeneratedAtEpochMs());
		assertEquals("LOGGED_IN", snapshot.getGameState());
		assertEquals(53000, snapshot.getInventoryGp());

		List<GeBridgeSlot> slots = snapshot.getSlots();
		assertEquals(1, slots.size());
		GeBridgeSlot slot = slots.get(0);
		assertEquals(0, slot.getSlot());
		assertEquals(314, slot.getItemId());
		assertEquals("BUYING", slot.getState());
		assertEquals("ORANGE", slot.getVisual());
		assertEquals(12, slot.getPrice());
		assertEquals(1000, slot.getTotalQuantity());
		assertEquals(420, slot.getQuantityTraded());
		assertEquals(5040, slot.getSpent());
		assertFalse(slot.isCollectReady());

		Map<Integer, Integer> inventoryMap = snapshot.inventoryAsMap();
		assertEquals(Integer.valueOf(53000), inventoryMap.get(995));
		assertEquals(Integer.valueOf(100), inventoryMap.get(314));
	}

	@Test
	public void testNullOfferBecomesEmptySlot()
	{
		GeBridgeSnapshot snapshot = GeBridgeSnapshotBuilder.build(
			GameState.LOGGED_IN,
			new GrandExchangeOffer[]{null},
			new Item[0],
			1L
		);

		GeBridgeSlot slot = snapshot.getSlots().get(0);
		assertEquals(-1, slot.getItemId());
		assertEquals("EMPTY", slot.getState());
		assertEquals("EMPTY", slot.getVisual());
		assertFalse(slot.isCollectReady());
	}
}
