package net.runelite.client.plugins.gebridge;

import lombok.Value;

@Value
class GeBridgeSlot
{
	int slot;
	int itemId;
	String state;
	String visual;
	int price;
	int totalQuantity;
	int quantityTraded;
	int spent;
	boolean collectReady;
}
