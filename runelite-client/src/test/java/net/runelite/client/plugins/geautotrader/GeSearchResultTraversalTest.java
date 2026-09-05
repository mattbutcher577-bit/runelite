package net.runelite.client.plugins.geautotrader;

import java.awt.Canvas;
import java.util.EnumSet;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.MenuAction;
import net.runelite.api.WorldType;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.VarClientID;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.api.vars.InputType;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetInfo;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import org.junit.Test;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class GeSearchResultTraversalTest
{
	@Test
	public void testStateReaderFindsSearchItemOutsideDynamicTriplets()
	{
		Client client = searchClient();
		Widget container = mock(Widget.class);
		Widget row = mock(Widget.class);
		Widget icon = mock(Widget.class);
		when(container.isHidden()).thenReturn(false);
		when(container.getNestedChildren()).thenReturn(new Widget[]{row});
		when(row.isHidden()).thenReturn(false);
		when(row.getStaticChildren()).thenReturn(new Widget[]{icon});
		when(icon.isHidden()).thenReturn(false);
		when(icon.getItemId()).thenReturn(325);
		when(client.getWidget(InterfaceID.Chatbox.MES_LAYER_SCROLLCONTENTS)).thenReturn(container);

		GeObservedState state = new GeStateReader(client).read(true);
		assertEquals(GePromptMode.ITEM_SEARCH, state.getPromptMode());
		assertTrue(state.hasSearchResult(325));
	}

	@Test
	public void testDispatcherSelectsExactNamedResultOutsideDynamicTriplets()
	{
		Client client = mock(Client.class);
		Widget container = mock(Widget.class);
		Widget row = mock(Widget.class);
		Widget icon = mock(Widget.class);
		Widget name = mock(Widget.class);
		when(container.isHidden()).thenReturn(false);
		when(container.getNestedChildren()).thenReturn(new Widget[]{row});
		when(row.isHidden()).thenReturn(false);
		when(row.getStaticChildren()).thenReturn(new Widget[]{icon, name});
		when(icon.isHidden()).thenReturn(false);
		when(icon.getItemId()).thenReturn(325);
		when(name.isHidden()).thenReturn(false);
		when(name.getItemId()).thenReturn(-1);
		when(name.getName()).thenReturn("Sardine");
		when(name.getText()).thenReturn("Sardine");
		when(name.getActions()).thenReturn(new String[]{"Select"});
		when(name.getIndex()).thenReturn(4);
		when(name.getId()).thenReturn(12345);
		when(client.getWidget(InterfaceID.Chatbox.MES_LAYER_SCROLLCONTENTS)).thenReturn(container);

		GeExecutionService execution = new GeExecutionService(client, () -> false);
		GePromptInputService input = new GePromptInputService(new Canvas(), () -> false);
		GeActionDispatcher dispatcher = new GeActionDispatcher(client, execution, input);
		GePlannedAction action = GePlannedAction.of(
			GePlannedActionType.SELECT_ITEM, 1, 325, "Sardine", 100, 17, "v6-buy-1");

		assertEquals(GeReasonCode.OK, dispatcher.dispatch(action, null));
		verify(client).menuAction(4, 12345, MenuAction.CC_OP, 1, -1, "Select", "Sardine");
	}

	private static Client searchClient()
	{
		Client client = mock(Client.class);
		when(client.getGameState()).thenReturn(GameState.LOGGED_IN);
		when(client.getWorldType()).thenReturn(EnumSet.noneOf(WorldType.class));
		when(client.getWorld()).thenReturn(452);
		visible(client, WidgetInfo.GRAND_EXCHANGE_WINDOW_CONTAINER);
		visible(client, WidgetInfo.GRAND_EXCHANGE_OFFER_CONTAINER);
		visible(client, WidgetInfo.CHATBOX_FULL_INPUT);
		when(client.getVarpValue(VarPlayerID.TRADINGPOST_SEARCH)).thenReturn(-1);
		when(client.getVarbitValue(VarbitID.GE_NEWOFFER_TYPE)).thenReturn(0);
		when(client.getVarcIntValue(VarClientID.MESLAYERMODE)).thenReturn(InputType.SEARCH.getType());
		return client;
	}

	private static void visible(Client client, WidgetInfo info)
	{
		Widget widget = mock(Widget.class);
		when(widget.isHidden()).thenReturn(false);
		when(client.getWidget(info)).thenReturn(widget);
	}
}
