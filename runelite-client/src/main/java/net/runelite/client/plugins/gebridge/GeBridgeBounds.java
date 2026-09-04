package net.runelite.client.plugins.gebridge;

import java.awt.Rectangle;
import lombok.Value;

@Value
class GeBridgeBounds
{
	int x;
	int y;
	int width;
	int height;
	boolean valid;

	static GeBridgeBounds from(Rectangle rectangle)
	{
		if (rectangle == null || rectangle.width <= 0 || rectangle.height <= 0)
		{
			return invalid();
		}

		return new GeBridgeBounds(rectangle.x, rectangle.y, rectangle.width, rectangle.height, true);
	}

	static GeBridgeBounds invalid()
	{
		return new GeBridgeBounds(-1, -1, 0, 0, false);
	}
}
