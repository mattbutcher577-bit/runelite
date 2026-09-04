package net.runelite.client.plugins.gebridge;

import lombok.Value;

@Value
class GeBridgePlayerState
{
	boolean present;
	int worldX;
	int worldY;
	int plane;

	static GeBridgePlayerState unavailable()
	{
		return new GeBridgePlayerState(false, -1, -1, -1);
	}
}
