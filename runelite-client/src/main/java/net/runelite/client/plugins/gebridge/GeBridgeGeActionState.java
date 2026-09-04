package net.runelite.client.plugins.gebridge;

import java.util.Collections;
import java.util.List;
import lombok.Value;

@Value
class GeBridgeGeActionState
{
	long updatedTick;
	GeBridgeBounds window;
	GeBridgeBounds back;
	GeBridgeBounds collect;
	GeBridgeBounds setup;
	GeBridgeBounds setupItem;
	GeBridgeBounds quantityButton;
	GeBridgeBounds priceButton;
	GeBridgeBounds confirm;
	GeBridgeBounds abort;
	List<GeBridgeGeActionSlot> slots;

	static GeBridgeGeActionState unavailable(long tick)
	{
		GeBridgeBounds invalid = GeBridgeBounds.invalid();
		return new GeBridgeGeActionState(
			tick, invalid, invalid, invalid, invalid, invalid,
			invalid, invalid, invalid, invalid, Collections.emptyList());
	}
}
