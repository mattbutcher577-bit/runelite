package net.runelite.client.plugins.gebridge;

import lombok.Value;

@Value
class GeBridgeMouseEvent
{
	long eventSeq;
	String type;
	int canvasX;
	int canvasY;
	int button;
	int wheelRotation;
	long whenMillis;
	boolean consumed;
}
