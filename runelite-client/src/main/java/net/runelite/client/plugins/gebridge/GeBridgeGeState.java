package net.runelite.client.plugins.gebridge;

import lombok.Value;

@Value
class GeBridgeGeState
{
	boolean open;
	boolean offerSetupOpen;
	boolean offerDetailsVisible;
	int offerSetupItemId;
	int offerSetupQuantity;
	int offerSetupPrice;
	String offerSetupType;
	GeBridgeBounds windowBounds;
	GeBridgeBounds offerSetupBounds;
	GeBridgeBounds inventoryBounds;

	GeBridgeGeState(
		boolean open,
		boolean offerSetupOpen,
		boolean offerDetailsVisible,
		int offerSetupItemId,
		int offerSetupQuantity,
		int offerSetupPrice,
		String offerSetupType,
		GeBridgeBounds windowBounds,
		GeBridgeBounds offerSetupBounds,
		GeBridgeBounds inventoryBounds)
	{
		this.open = open;
		this.offerSetupOpen = offerSetupOpen;
		this.offerDetailsVisible = offerDetailsVisible;
		this.offerSetupItemId = offerSetupItemId;
		this.offerSetupQuantity = offerSetupQuantity;
		this.offerSetupPrice = offerSetupPrice;
		this.offerSetupType = offerSetupType == null ? "UNKNOWN" : offerSetupType;
		this.windowBounds = windowBounds;
		this.offerSetupBounds = offerSetupBounds;
		this.inventoryBounds = inventoryBounds;
	}

	GeBridgeGeState(
		boolean open,
		boolean offerSetupOpen,
		int offerSetupItemId,
		int offerSetupQuantity,
		int offerSetupPrice,
		String offerSetupType,
		GeBridgeBounds windowBounds,
		GeBridgeBounds offerSetupBounds,
		GeBridgeBounds inventoryBounds)
	{
		this(open, offerSetupOpen, false, offerSetupItemId, offerSetupQuantity, offerSetupPrice,
			offerSetupType, windowBounds, offerSetupBounds, inventoryBounds);
	}

	GeBridgeGeState(
		boolean open,
		boolean offerSetupOpen,
		int offerSetupItemId,
		GeBridgeBounds windowBounds,
		GeBridgeBounds offerSetupBounds,
		GeBridgeBounds inventoryBounds)
	{
		this(
			open,
			offerSetupOpen,
			false,
			offerSetupItemId,
			0,
			0,
			"UNKNOWN",
			windowBounds,
			offerSetupBounds,
			inventoryBounds);
	}
}
