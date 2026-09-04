package net.runelite.client.plugins.geautotrader;

import java.util.function.BooleanSupplier;
import net.runelite.api.Client;

public final class GeExecutionService
{
	private final Client client;
	private final BooleanSupplier stopped;

	public GeExecutionService(Client client, BooleanSupplier stopped)
	{
		this.client = client;
		this.stopped = stopped;
	}

	public GeReasonCode execute(GeWidgetActionSpec spec)
	{
		if (stopped.getAsBoolean())
		{
			return GeReasonCode.STOPPED_F8;
		}
		if (spec == null)
		{
			return GeReasonCode.EXECUTION_TARGET_UNAVAILABLE;
		}
		try
		{
			client.menuAction(
				spec.getParam0(),
				spec.getParam1(),
				spec.getAction(),
				spec.getIdentifier(),
				spec.getItemId(),
				spec.getOption(),
				spec.getTarget());
			return GeReasonCode.OK;
		}
		catch (RuntimeException ex)
		{
			return GeReasonCode.EXECUTION_REJECTED;
		}
	}
}
