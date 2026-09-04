package net.runelite.client.plugins.gebridge;

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
	public void testSnapshotContainsProtocolV3ExactOfferClientAndInputState()
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

		GeBridgeClientState clientState = new GeBridgeClientState(
			true,
			301,
			Collections.singletonList("PVP"),
			false,
			773,
			535,
			765,
			503,
			4,
			4,
			548,
			50
		);
		GeBridgePlayerState playerState = new GeBridgePlayerState(true, 3164, 3487, 0);
		GeBridgeInterfaceState interfaceState = new GeBridgeInterfaceState(
			true, false, false, false, false, false, false);
		GeBridgeGeState geState = new GeBridgeGeState(
			true,
			false,
			-1,
			new GeBridgeBounds(20, 20, 500, 360, true),
			GeBridgeBounds.invalid(),
			new GeBridgeBounds(550, 200, 180, 250, true)
		);
		GeBridgeSafetyState safetyState = new GeBridgeSafetyState(true, false, true, true);
		GeBridgeInputState inputState = new GeBridgeInputState(
			123456700L,
			123456600L,
			123456700L,
			123456650L,
			123456680L,
			123456500L,
			123456400L,
			400,
			250,
			true,
			0,
			1,
			-1,
			"SHIFT",
			89L
		);

		GeBridgeSnapshot snapshot = GeBridgeSnapshotBuilder.build(
			GameState.LOGGED_IN,
			new GrandExchangeOffer[]{offer},
			inventory,
			123456789L,
			42L,
			clientState,
			playerState,
			interfaceState,
			geState,
			safetyState,
			inputState
		);

		assertEquals(3, snapshot.getProtocol());
		assertEquals(123456789L, snapshot.getGeneratedAtEpochMs());
		assertEquals(42L, snapshot.getTick());
		assertEquals("LOGGED_IN", snapshot.getGameState());
		assertEquals(53000, snapshot.getInventoryGp());
		assertEquals(773, snapshot.getClient().getCanvasWidth());
		assertEquals(535, snapshot.getClient().getCanvasHeight());
		assertEquals(3164, snapshot.getPlayer().getWorldX());
		assertTrue(snapshot.getInterfaces().isGrandExchangeOpen());
		assertTrue(snapshot.getGe().getWindowBounds().isValid());
		assertTrue(snapshot.getSafety().isSafeForGeMouseActions());
		assertEquals(400, snapshot.getInput().getMouseX());
		assertEquals(250, snapshot.getInput().getMouseY());
		assertEquals("SHIFT", snapshot.getInput().getLastControlKey());

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
				true, 301, Collections.emptyList(), false, 773, 535, 765, 503, 4, 4, 548, 50),
			new GeBridgePlayerState(true, 3164, 3487, 0),
			new GeBridgeInterfaceState(true, false, false, false, false, false, false),
			new GeBridgeGeState(
				true, false, -1, new GeBridgeBounds(20, 20, 500, 360, true),
				GeBridgeBounds.invalid(), new GeBridgeBounds(550, 200, 180, 250, true)),
			new GeBridgeSafetyState(true, false, true, true),
			new GeBridgeInputState(0L, 0L, 0L, 0L, 0L, 0L, 0L, -1, -1, false, 0, 0, 0, "", -1L)
		);
	}
}
