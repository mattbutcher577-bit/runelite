package net.runelite.client.plugins.gebridge;

import lombok.Value;

@Value
class GeBridgeGeInputState
{
	String mode;
	long updatedTick;
	GeBridgeBounds promptBounds;
	GeBridgeBounds inputFieldBounds;

	static GeBridgeGeInputState none(long tick)
	{
		return new GeBridgeGeInputState("NONE", tick, GeBridgeBounds.invalid(), GeBridgeBounds.invalid());
	}

	static GeBridgeGeInputState unknown(long tick, GeBridgeBounds promptBounds, GeBridgeBounds inputFieldBounds)
	{
		return new GeBridgeGeInputState("UNKNOWN", tick, promptBounds, inputFieldBounds);
	}
}
