package net.runelite.client.plugins.gebridge;

import java.util.Collections;
import java.util.List;
import lombok.Value;

@Value
class GeBridgeMouseState
{
	long updatedTick;
	long updatedSeq;
	long eventSeq;
	int canvasX;
	int canvasY;
	boolean insideCanvas;
	int currentButton;
	boolean dragging;
	int mouseIdleTicks;
	long lastPressMillis;
	long lastMoveMillis;
	long lastReleaseMillis;
	long lastWheelMillis;
	int lastWheelRotation;
	String lastEventType;
	int lastEventButton;
	int lastEventCanvasX;
	int lastEventCanvasY;
	boolean canvasFocused;
	boolean clientWindowFocused;
	List<GeBridgeMouseEvent> recentEvents;

	static GeBridgeMouseState unavailable()
	{
		return new GeBridgeMouseState(
			-1L, -1L, 0L,
			-1, -1, false, 0, false, -1, 0L,
			0L, 0L, 0L, 0,
			"NONE", 0, -1, -1,
			false, false, Collections.emptyList());
	}
}
