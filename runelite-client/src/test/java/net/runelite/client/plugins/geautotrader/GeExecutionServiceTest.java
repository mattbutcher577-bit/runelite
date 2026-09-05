package net.runelite.client.plugins.geautotrader;

import net.runelite.api.Client;
import net.runelite.api.MenuAction;
import static org.junit.Assert.assertEquals;
import org.junit.Test;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

public class GeExecutionServiceTest
{
	@Test
	public void testExecutesExactMenuActionOnce()
	{
		Client client = mock(Client.class);
		GeExecutionService service = new GeExecutionService(client, () -> false);
		GeWidgetActionSpec spec = new GeWidgetActionSpec(
			3, 200, MenuAction.CC_OP, 1, -1, "Buy", "");

		assertEquals(GeReasonCode.OK, service.execute(spec));
		verify(client).menuAction(3, 200, MenuAction.CC_OP, 1, -1, "Buy", "");
	}

	@Test
	public void testF8StopPreventsExecution()
	{
		Client client = mock(Client.class);
		GeExecutionService service = new GeExecutionService(client, () -> true);
		GeWidgetActionSpec spec = new GeWidgetActionSpec(
			3, 200, MenuAction.CC_OP, 1, -1, "Buy", "");

		assertEquals(GeReasonCode.STOPPED_F8, service.execute(spec));
		verify(client, never()).menuAction(3, 200, MenuAction.CC_OP, 1, -1, "Buy", "");
	}
}
