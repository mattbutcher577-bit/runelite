package net.runelite.client.plugins.gebridge;

import java.util.Collections;
import java.util.List;
import lombok.Value;

@Value
class GeBridgeSearchState
{
	boolean open;
	String query;
	List<GeBridgeSearchResult> results;

	static GeBridgeSearchState closed()
	{
		return new GeBridgeSearchState(false, "", Collections.emptyList());
	}
}
