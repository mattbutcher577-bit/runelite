package net.runelite.client.plugins.gebridge;

final class GeBridgeSafetyPolicy
{
	private GeBridgeSafetyPolicy()
	{
	}

	static GeBridgeSafetyState evaluate(boolean bridgeReady, GeBridgeInterfaceState interfaces)
	{
		if (interfaces == null)
		{
			return new GeBridgeSafetyState(bridgeReady, true, false, false);
		}

		boolean geOwnedInput = interfaces.isGrandExchangeOfferSetupOpen()
			&& interfaces.isChatboxInputOpen();
		boolean modalBlocker = interfaces.isBankOpen()
			|| interfaces.isWorldMapOpen()
			|| interfaces.isDialogOpen()
			|| (interfaces.isChatboxInputOpen() && !geOwnedInput)
			|| interfaces.isDraggingWidget();
		boolean safeForMouseActions = bridgeReady && !modalBlocker;
		boolean safeForGeMouseActions = safeForMouseActions && interfaces.isGrandExchangeOpen();
		return new GeBridgeSafetyState(
			bridgeReady,
			modalBlocker,
			safeForMouseActions,
			safeForGeMouseActions);
	}
}
