package net.runelite.client.plugins.gebridge;

import lombok.Value;

@Value
class GeBridgeInterfaceState
{
	boolean grandExchangeOpen;
	boolean grandExchangeOfferSetupOpen;
	boolean bankOpen;
	boolean worldMapOpen;
	boolean dialogOpen;
	boolean chatboxInputOpen;
	boolean draggingWidget;
}
