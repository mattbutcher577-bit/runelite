package net.runelite.client.plugins.gebridge;

import lombok.Value;

@Value
class GeBridgeSafetyState
{
	boolean bridgeReady;
	boolean modalBlocker;
	boolean safeForMouseActions;
	boolean safeForGeMouseActions;
}
