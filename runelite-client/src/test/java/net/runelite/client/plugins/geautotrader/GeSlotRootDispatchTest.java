package net.runelite.client.plugins.geautotrader;

import java.awt.Canvas;
import java.util.Arrays;
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

public class GeSlotRootDispatchTest
{
	@Test
	public void testOpenBuyUsesExactThirdSlotRoot()
	{
		Client client = baseClient();
		Widget wrong = actionWidget("Create Buy offer", 1, 50001, "wrong");
		Widget right = actionWidget("Create Buy offer", 3, 50003, "");
		Widget window = visibleWidget();
		when(window.getChildren()).thenReturn(new Widget[]{wrong});
		when(client.getWidget(WidgetInfo.GRAND_EXCHANGE_WINDOW_CONTAINER)).thenReturn(window);
		Widget slot3 = visibleWidget();
		when(slot3.getChildren()).thenReturn(new Widget[]{right});
		when(client.getWidget(InterfaceID.GeOffers.INDEX_2)).thenReturn(slot3);

		GeReasonCode result = dispatcher(client).dispatch(
			GePlannedAction.of(GePlannedActionType.OPEN_BUY, 3, 950, "Silk", 100, 20, "v6-buy-3"),
			state());

		assertEquals(GeReasonCode.OK, result);
		verify(client).menuAction(3, 50003, MenuAction.CC_OP, 1, -1, "Create Buy offer", "");
	}

	@Test
	public void testOpenSellUsesExactThirdSlotRoot()
	{
		Client client = baseClient();
		Widget right = actionWidget("Create Sell offer", 4, 51003, "");
		Widget slot3 = visibleWidget();
		when(slot3.getChildren()).thenReturn(new Widget[]{right});
		when(client.getWidget(InterfaceID.GeOffers.INDEX_2)).thenReturn(slot3);

		GeReasonCode result = dispatcher(client).dispatch(
			GePlannedAction.of(GePlannedActionType.OPEN_SELL, 3, 950, "Silk", 100, 20, "v6-sell-3"),
			state());

		assertEquals(GeReasonCode.OK, result);
		verify(client).menuAction(4, 51003, MenuAction.CC_OP, 1, -1, "Create Sell offer", "");
	}

	@Test
	public void testOpenOfferUsesExactSecondSlotRoot()
	{
		Client client = baseClient();
		Widget right = actionWidget("View offer", 5, 52002, "Oak longbow");
		Widget slot2 = visibleWidget();
		when(slot2.getChildren()).thenReturn(new Widget[]{right});
		when(client.getWidget(InterfaceID.GeOffers.INDEX_1)).thenReturn(slot2);

		GeReasonCode result = dispatcher(client).dispatch(
			GePlannedAction.of(GePlannedActionType.OPEN_OFFER, 2, 845, "Oak longbow", 1000, 14, "v6-buy-2"),
			state());

		assertEquals(GeReasonCode.OK, result);
		verify(client).menuAction(5, 52002, MenuAction.CC_OP, 1, -1, "View offer", "Oak longbow");
	}

	private static GeObservedState state()
	{
		return new GeObservedState(
			true, false, true, true, false, 2_020_062L,
			Arrays.asList(
				new GeObservedSlot(1, "BOUGHT", 1207, 125, 125, 5),
				new GeObservedSlot(2, "BUYING", 845, 1000, 0, 14),
				new GeObservedSlot(3, "EMPTY", -1, 0, 0, 0)),
			-1, 0, 0, GeTradeSide.UNKNOWN);
	}

	private static Client baseClient()
	{
		Client client = mock(Client.class);
		Widget window = visibleWidget();
		when(client.getWidget(WidgetInfo.GRAND_EXCHANGE_WINDOW_CONTAINER)).thenReturn(window);
		return client;
	}

	private static Widget visibleWidget()
	{
		Widget widget = mock(Widget.class);
		when(widget.isHidden()).thenReturn(false);
		return widget;
	}

	private static Widget actionWidget(String option, int index, int id, String target)
	{
		Widget widget = visibleWidget();
		when(widget.getActions()).thenReturn(new String[]{option});
		when(widget.getIndex()).thenReturn(index);
		when(widget.getId()).thenReturn(id);
		when(widget.getItemId()).thenReturn(-1);
		when(widget.getName()).thenReturn(target);
		return widget;
	}

	private static GeActionDispatcher dispatcher(Client client)
	{
		return new GeActionDispatcher(
			client,
			new GeExecutionService(client, () -> false),
			new GePromptInputService(new Canvas(), () -> false));
	}
}
