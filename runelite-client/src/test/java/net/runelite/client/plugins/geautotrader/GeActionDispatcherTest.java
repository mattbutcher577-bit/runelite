package net.runelite.client.plugins.geautotrader;

import java.awt.Canvas;
import net.runelite.api.Client;
import net.runelite.api.MenuAction;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import static org.junit.Assert.assertEquals;
import org.junit.Test;
import static org.mockito.ArgumentMatchers.anyString;
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

		GeExecutionService execution = new GeExecutionService(client, () -> false);
		GePromptInputService input = new GePromptInputService(new Canvas(), () -> false);
		GeActionDispatcher dispatcher = new GeActionDispatcher(client, execution, input);

		GePlannedAction action = GePlannedAction.of(
			GePlannedActionType.SELECT_ITEM,
			1,
			1127,
			"Adamant platebody",
			125,
			9001,
			"v6-buy-1");

		assertEquals(GeReasonCode.OK, dispatcher.dispatch(action, null));
		verify(client).menuAction(
			4,
			12345,
			MenuAction.CC_OP,
			1,
			-1,
			"Select",
			"Adamant platebody");
	}
}
