package net.runelite.client.plugins.gebridge;

import lombok.Value;

@Value
class GeBridgeInputState
{
	long lastInputEpochMs;
	long lastMouseMoveEpochMs;
	long lastMouseClickEpochMs;
	long lastMousePressEpochMs;
	long lastMouseReleaseEpochMs;
	long lastMouseWheelEpochMs;
	long lastKeyboardEpochMs;
	int mouseX;
	int mouseY;
	boolean mouseInsideCanvas;
	int mouseButtonsDownMask;
	int lastMouseButton;
	int lastWheelRotation;
	String lastControlKey;
	long inputIdleMs;
}
