package net.runelite.client.plugins.geautotrader;

import java.awt.Canvas;
import java.awt.event.KeyEvent;
import java.lang.reflect.Field;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
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
		assertTrue(plugin.isManualRestartAllowed());
		assertEquals(GeReasonCode.STOPPED_F8, plugin.getLastReason());
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

	@Test
	public void testExecutionFailureCannotBeRestartedWithConfigToggle()
	{
		GeAutoTraderPlugin plugin = new GeAutoTraderPlugin();
		plugin.stopForExecutionFailure();
		assertTrue(plugin.isStopped());
		assertFalse(plugin.isManualRestartAllowed());

		plugin.onConfigChanged(enabledChange("false"));
		plugin.onConfigChanged(enabledChange("true"));
		assertFalse(plugin.isRestartRequested());
		assertTrue(plugin.isStopped());
	}

	@Test
	public void testExecutionFailurePreservesTrueReason() throws Exception
	{
		GeAutoTraderPlugin plugin = new GeAutoTraderPlugin();
		setLastReason(plugin, GeReasonCode.EXECUTION_TARGET_UNAVAILABLE);
		plugin.stopForExecutionFailure();

		assertTrue(plugin.isStopped());
		assertFalse(plugin.isManualRestartAllowed());
		assertEquals(GeReasonCode.EXECUTION_TARGET_UNAVAILABLE, plugin.getLastReason());
	}

	@Test
	public void testTransientTargetUnavailableIsReportedAsRunning() throws Exception
	{
		GeAutoTraderPlugin plugin = new GeAutoTraderPlugin();
		Field configField = GeAutoTraderPlugin.class.getDeclaredField("config");
		configField.setAccessible(true);
		configField.set(plugin, enabledConfig());
		setLastReason(plugin, GeReasonCode.EXECUTION_TARGET_UNAVAILABLE);

		assertEquals("RUNNING", plugin.getStatusText());
	}

	@Test
	public void testNoOpportunityIsReportedAsRunningScanState() throws Exception
	{
		GeReasonCode noOpportunity = null;
		for (GeReasonCode reason : GeReasonCode.values())
		{
			if ("NO_OPPORTUNITY".equals(reason.name()))
			{
				noOpportunity = reason;
				break;
			}
		}
		assertNotNull("NO_OPPORTUNITY reason must exist", noOpportunity);

		GeAutoTraderPlugin plugin = new GeAutoTraderPlugin();
		Field configField = GeAutoTraderPlugin.class.getDeclaredField("config");
		configField.setAccessible(true);
		configField.set(plugin, enabledConfig());
		setLastReason(plugin, noOpportunity);

		assertEquals("RUNNING", plugin.getStatusText());
	}

	private static void setLastReason(GeAutoTraderPlugin plugin, GeReasonCode reason) throws Exception
	{
		Field reasonField = GeAutoTraderPlugin.class.getDeclaredField("lastReason");
		reasonField.setAccessible(true);
		reasonField.set(plugin, reason);
	}

	private static GeAutoTraderConfig enabledConfig()
	{
		return new GeAutoTraderConfig()
		{
			@Override public boolean enabled() { return true; }
		};
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
