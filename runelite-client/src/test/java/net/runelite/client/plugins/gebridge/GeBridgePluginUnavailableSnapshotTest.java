package net.runelite.client.plugins.gebridge;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Collections;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import static org.junit.Assert.fail;
import org.junit.Test;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class GeBridgePluginUnavailableSnapshotTest
{
	@Test
	public void testUnavailableSnapshotDoesNotReadTopLevelInterfaceBeforeClientIsReady() throws Exception
	{
		Client client = mock(Client.class);
		when(client.getWorldType()).thenReturn(Collections.emptySet());
		when(client.getTopLevelInterfaceId()).thenThrow(new NullPointerException("client not initialized"));

		GeBridgePlugin plugin = new GeBridgePlugin();
		Field clientField = GeBridgePlugin.class.getDeclaredField("client");
		clientField.setAccessible(true);
		clientField.set(plugin, client);

		Method publish = GeBridgePlugin.class.getDeclaredMethod("publishUnavailableSnapshot", GameState.class);
		publish.setAccessible(true);
		try
		{
			publish.invoke(plugin, GameState.UNKNOWN);
		}
		catch (InvocationTargetException ex)
		{
			fail("unavailable snapshot accessed uninitialised client state: " + ex.getCause());
		}

		verify(client, never()).getTopLevelInterfaceId();
	}
}
