package net.runelite.client.plugins.geautotrader;

import java.awt.Canvas;
import java.util.Arrays;
import java.util.Collections;
import net.runelite.api.Client;
import net.runelite.api.MenuAction;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetInfo;
import static org.junit.Assert.assertEquals;
import org.junit.Test;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class GeActionDispatcherTest
{
	@Test
	public void testSearchResultUsesIconItemIdAndAdjacentClickableName()
	{
		Client client = mock(Client.class);
		Widget container = mock(Widget.class);
		Widget icon = mock(Widget.class);
		Widget name = mock(Widget.class);
		Widget spacer = mock(Widget.class);

		when(client.getWidget(InterfaceID.Chatbox.MES_LAYER_SCROLLCONTENTS)).thenReturn(container);
		when(container.isHidden()).thenReturn(false);
		when(container.getDynamicChildren()).thenReturn(new Widget[]{icon, name, spacer});

		when(icon.isHidden()).thenReturn(false);
		when(icon.getItemId()).thenReturn(1127);
		when(name.isHidden()).thenReturn(false);
		when(name.getActions()).thenReturn(new String[]{"Select"});
		when(name.getIndex()).thenReturn(4);
		when(name.getId()).thenReturn(12345);
		when(name.getItemId()).thenReturn(-1);
		when(name.getName()).thenReturn("Adamant platebody");

		GeActionDispatcher dispatcher = dispatcher(client);
		GePlannedAction action = action(GePlannedActionType.SELECT_ITEM, 1127, "Adamant platebody");

		assertEquals(GeReasonCode.OK, dispatcher.dispatch(action, null));
		verify(client).menuAction(4, 12345, MenuAction.CC_OP, 1, -1, "Select", "Adamant platebody");
	}

	@Test
	public void testOpenBuyRecognizesCreateBuyOfferAction()
	{
		Client client = mock(Client.class);
		Widget buy = slotWidget(client, "Create Buy offer");
		when(buy.getIndex()).thenReturn(7);
		when(buy.getId()).thenReturn(54321);

		assertEquals(GeReasonCode.OK, dispatcher(client).dispatch(openBuyAction(), emptySlotState()));
		verify(client).menuAction(7, 54321, MenuAction.CC_OP, 1, -1, "Create Buy offer", "");
	}

	@Test
	public void testOpenBuyRecognizesTaggedCreateBuyOfferAction()
	{
		Client client = mock(Client.class);
		String liveAction = "<col=ff9040>Create Buy offer</col>";
		Widget buy = slotWidget(client, liveAction);
		when(buy.getIndex()).thenReturn(7);
		when(buy.getId()).thenReturn(54321);

		assertEquals(GeReasonCode.OK, dispatcher(client).dispatch(openBuyAction(), emptySlotState()));
		verify(client).menuAction(7, 54321, MenuAction.CC_OP, 1, -1, liveAction, "");
	}

	@Test
	public void testOpenBuyIgnoresGenericBuyActionOnOccupiedSlot()
	{
		Client client = mock(Client.class);
		Widget window = mock(Widget.class);
		Widget occupiedBuyAlias = actionWidget("Buy", 1, 50001, -1, "Steel dagger");
		Widget slot2Buy = actionWidget("Create Buy offer", 2, 50002, -1, "");
		Widget slot3Buy = actionWidget("Create Buy offer", 3, 50003, -1, "");
		when(client.getWidget(WidgetInfo.GRAND_EXCHANGE_WINDOW_CONTAINER)).thenReturn(window);
		when(window.isHidden()).thenReturn(false);
		when(window.getChildren()).thenReturn(new Widget[]{occupiedBuyAlias, slot2Buy, slot3Buy});

		GeObservedState state = new GeObservedState(
			true, false, true, true, false, 2_035_687L,
			Arrays.asList(
				new GeObservedSlot(1, "BUYING", 1207, 125, 0, 5),
				new GeObservedSlot(2, "EMPTY", -1, 0, 0, 0),
				new GeObservedSlot(3, "EMPTY", -1, 0, 0, 0)),
			-1, 0, 0, GeTradeSide.UNKNOWN);
		GePlannedAction action = GePlannedAction.of(
			GePlannedActionType.OPEN_BUY, 2, 325, "Sardine", 100, 17, "v6-buy-2");

		assertEquals(GeReasonCode.OK, dispatcher(client).dispatch(action, state));
		verify(client).menuAction(2, 50002, MenuAction.CC_OP, 1, -1, "Create Buy offer", "");
	}

	@Test
	public void testOpenSellRecognizesCreateSellOfferAction()
	{
		Client client = mock(Client.class);
		Widget sell = slotWidget(client, "Create Sell offer");
		when(sell.getIndex()).thenReturn(8);
		when(sell.getId()).thenReturn(54322);

		GePlannedAction action = action(GePlannedActionType.OPEN_SELL, 1982, "Tomato");
		assertEquals(GeReasonCode.OK, dispatcher(client).dispatch(action, emptySlotState()));
		verify(client).menuAction(8, 54322, MenuAction.CC_OP, 1, -1, "Create Sell offer", "");
	}

	@Test
	public void testOpenOfferUsesNonEmptySlotViewAction()
	{
		Client client = mock(Client.class);
		Widget view = slotWidget(client, "View offer");
		when(view.getIndex()).thenReturn(9);
		when(view.getId()).thenReturn(54323);

		GePlannedAction action = action(GePlannedActionType.OPEN_OFFER, 1982, "Tomato");
		assertEquals(GeReasonCode.OK, dispatcher(client).dispatch(action, nonEmptySlotState()));
		verify(client).menuAction(9, 54323, MenuAction.CC_OP, 1, -1, "View offer", "");
	}

	@Test
	public void testSelectSellItemRequiresExactInventoryItemId()
	{
		Client client = mock(Client.class);
		Widget root = mock(Widget.class);
		Widget item = mock(Widget.class);
		when(client.getWidget(WidgetInfo.GRAND_EXCHANGE_INVENTORY_ITEMS_CONTAINER)).thenReturn(root);
		when(root.isHidden()).thenReturn(false);
		when(root.getChildren()).thenReturn(new Widget[]{item});
		when(item.isHidden()).thenReturn(false);
		when(item.getItemId()).thenReturn(1982);
		when(item.getActions()).thenReturn(new String[]{"Offer"});
		when(item.getIndex()).thenReturn(5);
		when(item.getId()).thenReturn(60001);
		when(item.getName()).thenReturn("Tomato");

		GePlannedAction action = action(GePlannedActionType.SELECT_SELL_ITEM, 1982, "Tomato");
		assertEquals(GeReasonCode.OK, dispatcher(client).dispatch(action, null));
		verify(client).menuAction(5, 60001, MenuAction.CC_OP, 1, 1982, "Offer", "Tomato");
	}

	@Test
	public void testSetupLifecycleActionsResolveExactWidgetTargets()
	{
		assertSetupAction(GePlannedActionType.OPEN_QUANTITY, "Set quantity");
		assertSetupAction(GePlannedActionType.OPEN_PRICE, "Set price");
		assertSetupAction(GePlannedActionType.CONFIRM, "Confirm");
		assertSetupAction(GePlannedActionType.ABORT_BUY, "Abort offer");
		assertSetupAction(GePlannedActionType.COLLECT, "Collect items");
	}

	@Test
	public void testDuplicateSetupTargetsFailClosed()
	{
		Client client = mock(Client.class);
		Widget root = mock(Widget.class);
		Widget a = actionWidget("Confirm", 3, 44444, -1, "");
		Widget b = actionWidget("Confirm", 4, 44445, -1, "");
		when(client.getWidget(WidgetInfo.GRAND_EXCHANGE_OFFER_CONTAINER)).thenReturn(root);
		when(root.isHidden()).thenReturn(false);
		when(root.getChildren()).thenReturn(new Widget[]{a, b});

		assertEquals(GeReasonCode.EXECUTION_TARGET_UNAVAILABLE,
			dispatcher(client).dispatch(action(GePlannedActionType.CONFIRM, 1982, "Tomato"), null));
	}

	private static void assertSetupAction(GePlannedActionType type, String alias)
	{
		Client client = mock(Client.class);
		Widget root = mock(Widget.class);
		Widget target = actionWidget(alias, 3, 44444, -1, "");
		when(client.getWidget(WidgetInfo.GRAND_EXCHANGE_OFFER_CONTAINER)).thenReturn(root);
		when(root.isHidden()).thenReturn(false);
		when(root.getChildren()).thenReturn(new Widget[]{target});

		assertEquals(GeReasonCode.OK, dispatcher(client).dispatch(action(type, 1982, "Tomato"), null));
		verify(client).menuAction(3, 44444, MenuAction.CC_OP, 1, -1, alias, "");
	}

	private static Widget slotWidget(Client client, String alias)
	{
		Widget window = mock(Widget.class);
		Widget target = actionWidget(alias, 0, 0, -1, "");
		when(client.getWidget(WidgetInfo.GRAND_EXCHANGE_WINDOW_CONTAINER)).thenReturn(window);
		when(window.isHidden()).thenReturn(false);
		when(window.getChildren()).thenReturn(new Widget[]{target});
		return target;
	}

	private static Widget actionWidget(String alias, int index, int id, int itemId, String name)
	{
		Widget widget = mock(Widget.class);
		when(widget.isHidden()).thenReturn(false);
		when(widget.getActions()).thenReturn(new String[]{alias});
		when(widget.getIndex()).thenReturn(index);
		when(widget.getId()).thenReturn(id);
		when(widget.getItemId()).thenReturn(itemId);
		when(widget.getName()).thenReturn(name);
		return widget;
	}

	private static GeActionDispatcher dispatcher(Client client)
	{
		GeExecutionService execution = new GeExecutionService(client, () -> false);
		GePromptInputService input = new GePromptInputService(new Canvas(), () -> false);
		return new GeActionDispatcher(client, execution, input);
	}

	private static GeObservedState emptySlotState()
	{
		return new GeObservedState(
			true, false, true, true, false, 2_000_000L,
			Collections.singletonList(new GeObservedSlot(1, "EMPTY", -1, 0, 0, 0)),
			-1, 0, 0, GeTradeSide.UNKNOWN);
	}

	private static GeObservedState nonEmptySlotState()
	{
		return new GeObservedState(
			true, false, true, true, false, 2_000_000L,
			Collections.singletonList(new GeObservedSlot(1, "BOUGHT", 1982, 100, 100, 160)),
			-1, 0, 0, GeTradeSide.UNKNOWN);
	}

	private static GePlannedAction openBuyAction()
	{
		return action(GePlannedActionType.OPEN_BUY, 1127, "Adamant platebody");
	}

	private static GePlannedAction action(GePlannedActionType type, int itemId, String name)
	{
		return GePlannedAction.of(type, 1, itemId, name, 100, 160, "v6-test-1");
	}
}
