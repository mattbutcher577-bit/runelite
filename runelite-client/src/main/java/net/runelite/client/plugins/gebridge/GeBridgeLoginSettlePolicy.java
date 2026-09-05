package net.runelite.client.plugins.gebridge;

final class GeBridgeLoginSettlePolicy
{
	private static final int REQUIRED_TICKS = 2;

	private GeBridgeLoginSettlePolicy()
	{
	}

	static boolean isSettled(int ticksSinceLogin)
	{
		return ticksSinceLogin >= REQUIRED_TICKS;
	}
}
