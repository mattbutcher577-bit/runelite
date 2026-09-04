package net.runelite.client.plugins.geautotrader;

import java.awt.Canvas;
import java.awt.event.KeyEvent;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

public class GeAutoTraderPluginTest
{
	@Test
	public void testF8SetsHardStoppedFlag()
	{
		GeAutoTraderPlugin plugin = new GeAutoTraderPlugin();
		KeyEvent event = new KeyEvent(
			new Canvas(), KeyEvent.KEY_PRESSED, System.currentTimeMillis(), 0, KeyEvent.VK_F8, KeyEvent.CHAR_UNDEFINED);
		plugin.keyPressed(event);
		assertTrue(plugin.isStopped());
		assertTrue(event.isConsumed());
	}
}
