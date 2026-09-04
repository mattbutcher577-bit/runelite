package net.runelite.client.plugins.geautotrader;

import java.awt.Canvas;
import java.awt.event.KeyEvent;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import org.junit.Test;
import net.runelite.client.events.ConfigChanged;

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

	@Test
	public void testF8RestartRequiresExplicitOffThenOn()
	{
		GeAutoTraderPlugin plugin = new GeAutoTraderPlugin();
		KeyEvent event = new KeyEvent(
			new Canvas(), KeyEvent.KEY_PRESSED, System.currentTimeMillis(), 0, KeyEvent.VK_F8, KeyEvent.CHAR_UNDEFINED);
		plugin.keyPressed(event);

		plugin.onConfigChanged(enabledChange("true"));
		assertFalse(plugin.isRestartRequested());
		assertTrue(plugin.isStopped());

		plugin.onConfigChanged(enabledChange("false"));
		assertFalse(plugin.isRestartRequested());
		assertTrue(plugin.isStopped());

		plugin.onConfigChanged(enabledChange("true"));
		assertTrue(plugin.isRestartRequested());
		assertTrue(plugin.isStopped());
	}

	private static ConfigChanged enabledChange(String value)
	{
		ConfigChanged event = new ConfigChanged();
		event.setGroup(GeAutoTraderPlugin.CONFIG_GROUP);
		event.setKey("enabled");
		event.setNewValue(value);
		return event;
	}
}
