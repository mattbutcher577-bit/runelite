package net.runelite.client.plugins.geautotrader;

import java.awt.Canvas;
import net.runelite.api.Client;
import net.runelite.api.MenuAction;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import static org.junit.Assert.assertEquals;
import org.junit.Test;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class GeOfferStatusComponentDispatchTest
{
	@Test
	public void testCollectUsesExactDetailsCollectComponentEvenWhenNotUnderWindowRoot()
	{
		Client client = mock(Client.class);
		Widget root = visibleRoot();
		Widget collect = actionWidget("Collect", 6, 62001, "Steel dagger");
		when(root.getChildren()).thenReturn(new Widget[]{collect});
		when(client.getWidget(InterfaceID.GeOffers.DETAILS_COLLECT)).thenReturn(root);

		assertEquals(
			GeReasonCode.OK,
			dispatcher(client).dispatch(action(GePlannedActionType.COLLECT), null));
		verify(client).menuAction(6, 62001, MenuAction.CC_OP, 1, -1, "Collect", "Steel dagger");
	}

	@Test
	public void testCollectAcceptsLiveDynamicCollectItemsAction()
	{
		Client client = mock(Client.class);
		Widget root = visibleRoot();
		Widget collect = actionWidget("Collect-items", 6, 62005, "Steel dagger");
		when(root.getDynamicChildren()).thenReturn(new Widget[]{collect});
		when(client.getWidget(InterfaceID.GeOffers.DETAILS_COLLECT)).thenReturn(root);

		assertEquals(
			GeReasonCode.OK,
			dispatcher(client).dispatch(action(GePlannedActionType.COLLECT), null));
		verify(client).menuAction(6, 62005, MenuAction.CC_OP, 1, -1, "Collect-items", "Steel dagger");
	}

	@Test
	public void testAbortUsesExactDetailsModifyComponentEvenWhenNotUnderWindowRoot()
	{
		Client client = mock(Client.class);
		Widget root = visibleRoot();
		Widget abort = actionWidget("Abort offer", 7, 62002, "Steel dagger");
		when(root.getChildren()).thenReturn(new Widget[]{abort});
		when(client.getWidget(InterfaceID.GeOffers.DETAILS_MODIFY)).thenReturn(root);

		assertEquals(
			GeReasonCode.OK,
			dispatcher(client).dispatch(action(GePlannedActionType.ABORT_BUY), null));
		verify(client).menuAction(7, 62002, MenuAction.CC_OP, 1, -1, "Abort offer", "Steel dagger");
	}

	@Test
	public void testCollectFallsBackToDetailsContainerWhenSpecificComponentHasNoAction()
	{
		Client client = mock(Client.class);
		Widget specific = visibleRoot();
		Widget details = visibleRoot();
		Widget collect = actionWidget("Collect", 8, 62003, "Steel dagger");
		when(details.getChildren()).thenReturn(new Widget[]{collect});
		when(client.getWidget(InterfaceID.GeOffers.DETAILS_COLLECT)).thenReturn(specific);
		when(client.getWidget(InterfaceID.GeOffers.DETAILS)).thenReturn(details);

		assertEquals(
			GeReasonCode.OK,
			dispatcher(client).dispatch(action(GePlannedActionType.COLLECT), null));
		verify(client).menuAction(8, 62003, MenuAction.CC_OP, 1, -1, "Collect", "Steel dagger");
	}

	@Test
	public void testAbortFallsBackToDetailsContainerWhenSpecificComponentHasNoAction()
	{
		Client client = mock(Client.class);
		Widget specific = visibleRoot();
		Widget details = visibleRoot();
		Widget abort = actionWidget("Abort offer", 9, 62004, "Steel dagger");
		when(details.getChildren()).thenReturn(new Widget[]{abort});
		when(client.getWidget(InterfaceID.GeOffers.DETAILS_MODIFY)).thenReturn(specific);
		when(client.getWidget(InterfaceID.GeOffers.DETAILS)).thenReturn(details);

		assertEquals(
			GeReasonCode.OK,
			dispatcher(client).dispatch(action(GePlannedActionType.ABORT_BUY), null));
		verify(client).menuAction(9, 62004, MenuAction.CC_OP, 1, -1, "Abort offer", "Steel dagger");
	}

	private static GeActionDispatcher dispatcher(Client client)
	{
		return new GeActionDispatcher(
			client,
			new GeExecutionService(client, () -> false),
			new GePromptInputService(new Canvas(), () -> false));
	}

	private static GePlannedAction action(GePlannedActionType type)
	{
		return GePlannedAction.of(type, 1, 1207, "Steel dagger", 125, 5, "v6-test-1");
	}

	private static Widget visibleRoot()
	{
		Widget widget = mock(Widget.class);
		when(widget.isHidden()).thenReturn(false);
		return widget;
	}

	private static Widget actionWidget(String action, int index, int id, String name)
	{
		Widget widget = mock(Widget.class);
		when(widget.isHidden()).thenReturn(false);
		when(widget.getActions()).thenReturn(new String[]{action});
		when(widget.getIndex()).thenReturn(index);
		when(widget.getId()).thenReturn(id);
		when(widget.getItemId()).thenReturn(-1);
		when(widget.getName()).thenReturn(name);
		return widget;
	}
}
