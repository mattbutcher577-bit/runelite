package net.runelite.client.plugins.geautotrader;

import java.util.EnumSet;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.GrandExchangeOffer;
import net.runelite.api.GrandExchangeOfferState;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.WorldType;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.gameval.VarClientID;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.api.vars.InputType;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetInfo;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import org.junit.Test;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class GeStateReaderTest
{
	@Test
	public void testReadsF2pGpInventoryAndOfferState()
	{
		Client client = baseClient();
		ItemContainer inventory = mock(ItemContainer.class);
		Item coins = item(995, 2_035_687);
		Item platebody = item(1127, 4);
		when(inventory.getItems()).thenReturn(new Item[]{coins, platebody});
		when(client.getItemContainer(InventoryID.INV)).thenReturn(inventory);

		GrandExchangeOffer offer = mock(GrandExchangeOffer.class);
		when(offer.getState()).thenReturn(GrandExchangeOfferState.BUYING);
		when(offer.getItemId()).thenReturn(1127);
		when(offer.getTotalQuantity()).thenReturn(125);
		when(offer.getQuantitySold()).thenReturn(40);
		when(offer.getPrice()).thenReturn(9001);
		when(client.getGrandExchangeOffers()).thenReturn(new GrandExchangeOffer[]{offer});

		GeObservedState state = new GeStateReader(client).read(true);
		assertTrue(state.isLoggedIn());
		assertFalse(state.isMembersWorld());
		assertEquals(301, state.getWorld());
		assertEquals(2_035_687L, state.getGp());
		assertEquals(4, state.getInventoryQuantity(1127));
		assertEquals(1, state.getSlots().size());
		assertEquals("BUYING", state.getSlots().get(0).getState());
		assertEquals(40, state.getSlots().get(0).getFilledQuantity());
	}

	@Test
	public void testReadsExactBuySetupAndQuantityPrompt()
	{
		Client client = baseClient();
		visible(client, WidgetInfo.GRAND_EXCHANGE_OFFER_CONTAINER);
		when(client.getVarpValue(VarPlayerID.TRADINGPOST_SEARCH)).thenReturn(1127);
		when(client.getVarbitValue(VarbitID.GE_NEWOFFER_QUANTITY)).thenReturn(125);
		when(client.getVarbitValue(VarbitID.GE_NEWOFFER_PRICE)).thenReturn(9001);
		when(client.getVarbitValue(VarbitID.GE_NEWOFFER_TYPE)).thenReturn(0);
		when(client.getVarcIntValue(VarClientID.MESLAYERMODE)).thenReturn(1);
		Widget title = mock(Widget.class);
		when(title.getText()).thenReturn("How many do you wish to buy?");
		when(client.getWidget(WidgetInfo.CHATBOX_TITLE)).thenReturn(title);

		GeObservedState state = new GeStateReader(client).read(true);
		assertEquals(1127, state.getSetupItemId());
		assertEquals(125, state.getSetupQuantity());
		assertEquals(9001, state.getSetupPrice());
		assertEquals(GeTradeSide.BUY, state.getSetupSide());
		assertEquals(GePromptMode.QUANTITY, state.getPromptMode());
	}

	@Test
	public void testSearchInputModeWinsWhenRuneLiteReportsSearch()
	{
		Client client = baseClient();
		visible(client, WidgetInfo.GRAND_EXCHANGE_OFFER_CONTAINER);
		when(client.getVarcIntValue(VarClientID.MESLAYERMODE)).thenReturn(InputType.SEARCH.getType());
		assertEquals(GePromptMode.ITEM_SEARCH, new GeStateReader(client).read(true).getPromptMode());
	}

	@Test
	public void testBuySetupWithoutLiveInputEvidenceStaysNoneWhenMesLayerIsNone()
	{
		Client client = baseClient();
		visible(client, WidgetInfo.GRAND_EXCHANGE_OFFER_CONTAINER);
		when(client.getVarpValue(VarPlayerID.TRADINGPOST_SEARCH)).thenReturn(-1);
		when(client.getVarbitValue(VarbitID.GE_NEWOFFER_TYPE)).thenReturn(0);
		when(client.getVarcIntValue(VarClientID.MESLAYERMODE)).thenReturn(InputType.NONE.getType());

		GeObservedState state = new GeStateReader(client).read(true);
		assertEquals(-1, state.getSetupItemId());
		assertEquals(GeTradeSide.BUY, state.getSetupSide());
		assertEquals(GePromptMode.NONE, state.getPromptMode());
	}

	@Test
	public void testVisibleFullInputMakesUnknownBuySetupItemSearch()
	{
		Client client = baseClient();
		visible(client, WidgetInfo.GRAND_EXCHANGE_OFFER_CONTAINER);
		visible(client, WidgetInfo.CHATBOX_FULL_INPUT);
		when(client.getVarpValue(VarPlayerID.TRADINGPOST_SEARCH)).thenReturn(-1);
		when(client.getVarbitValue(VarbitID.GE_NEWOFFER_TYPE)).thenReturn(0);
		when(client.getVarcIntValue(VarClientID.MESLAYERMODE)).thenReturn(99);

		GeObservedState state = new GeStateReader(client).read(true);
		assertEquals(-1, state.getSetupItemId());
		assertEquals(GeTradeSide.BUY, state.getSetupSide());
		assertEquals(GePromptMode.ITEM_SEARCH, state.getPromptMode());
	}

	@Test
	public void testUnknownBuySetupWithoutVisibleFullInputStaysUnknown()
	{
		Client client = baseClient();
		visible(client, WidgetInfo.GRAND_EXCHANGE_OFFER_CONTAINER);
		when(client.getVarpValue(VarPlayerID.TRADINGPOST_SEARCH)).thenReturn(-1);
		when(client.getVarbitValue(VarbitID.GE_NEWOFFER_TYPE)).thenReturn(0);
		when(client.getVarcIntValue(VarClientID.MESLAYERMODE)).thenReturn(99);

		assertEquals(GePromptMode.UNKNOWN, new GeStateReader(client).read(true).getPromptMode());
	}

	@Test
	public void testSelectedBuyItemWithVisibleFullInputIsNotForcedToSearch()
	{
		Client client = baseClient();
		visible(client, WidgetInfo.GRAND_EXCHANGE_OFFER_CONTAINER);
		visible(client, WidgetInfo.CHATBOX_FULL_INPUT);
		when(client.getVarpValue(VarPlayerID.TRADINGPOST_SEARCH)).thenReturn(1127);
		when(client.getVarbitValue(VarbitID.GE_NEWOFFER_TYPE)).thenReturn(0);
		when(client.getVarcIntValue(VarClientID.MESLAYERMODE)).thenReturn(99);

		assertEquals(GePromptMode.UNKNOWN, new GeStateReader(client).read(true).getPromptMode());
	}

	@Test
	public void testReadsExactSearchResultItemIdsWhileSearchRemainsOpen()
	{
		Client client = baseClient();
		visible(client, WidgetInfo.GRAND_EXCHANGE_OFFER_CONTAINER);
		when(client.getVarcIntValue(VarClientID.MESLAYERMODE)).thenReturn(InputType.SEARCH.getType());
		Widget container = mock(Widget.class);
		when(container.isHidden()).thenReturn(false);
		Widget icon = mock(Widget.class);
		when(icon.getItemId()).thenReturn(1127);
		Widget name = mock(Widget.class);
		when(name.isHidden()).thenReturn(false);
		when(name.getText()).thenReturn("Adamant platebody");
		Widget spacer = mock(Widget.class);
		when(container.getDynamicChildren()).thenReturn(new Widget[]{icon, name, spacer});
		when(client.getWidget(InterfaceID.Chatbox.MES_LAYER_SCROLLCONTENTS)).thenReturn(container);

		GeObservedState state = new GeStateReader(client).read(true);
		assertEquals(GePromptMode.ITEM_SEARCH, state.getPromptMode());
		assertTrue(state.hasSearchResult(1127));
	}

	@Test
	public void testMembersWorldFlagIsExact()
	{
		Client client = baseClient();
		when(client.getWorldType()).thenReturn(EnumSet.of(WorldType.MEMBERS));
		assertTrue(new GeStateReader(client).read(true).isMembersWorld());
	}

	private static Client baseClient()
	{
		Client client = mock(Client.class);
		when(client.getGameState()).thenReturn(GameState.LOGGED_IN);
		when(client.getWorldType()).thenReturn(EnumSet.noneOf(WorldType.class));
		when(client.getWorld()).thenReturn(301);
		visible(client, WidgetInfo.GRAND_EXCHANGE_WINDOW_CONTAINER);
		when(client.getVarcIntValue(VarClientID.MESLAYERMODE)).thenReturn(InputType.NONE.getType());
		return client;
	}

	private static void visible(Client client, WidgetInfo info)
	{
		Widget widget = mock(Widget.class);
		when(widget.isHidden()).thenReturn(false);
		when(client.getWidget(info)).thenReturn(widget);
	}

	private static Item item(int id, int quantity)
	{
		Item item = mock(Item.class);
		when(item.getId()).thenReturn(id);
		when(item.getQuantity()).thenReturn(quantity);
		return item;
	}
}
