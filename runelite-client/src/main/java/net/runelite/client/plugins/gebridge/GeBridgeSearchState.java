package net.runelite.client.plugins.gebridge;

import java.util.Collections;
import java.util.List;
import lombok.Value;

@Value
class GeBridgeSearchState
{
	boolean open;
	long updatedTick;
	List<GeBridgeSearchResult> results;

	GeBridgeSearchState(boolean open, String ignoredLegacyQuery, List<GeBridgeSearchResult> results)
	{
		this(open, -1L, results);
	}

	static GeBridgeSearchState closed()
	{
		return new GeBridgeSearchState(false, -1L, Collections.emptyList());
	}
}
