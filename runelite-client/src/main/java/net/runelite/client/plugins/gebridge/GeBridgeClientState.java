package net.runelite.client.plugins.gebridge;

import java.util.List;
import lombok.Value;

@Value
class GeBridgeClientState
{
	boolean loggedIn;
	int world;
	List<String> worldTypes;
	boolean membersWorld;
	int canvasWidth;
	int canvasHeight;
	int canvasScreenX;
	int canvasScreenY;
	boolean canvasScreenPositionValid;
	int viewportWidth;
	int viewportHeight;
	int viewportXOffset;
	int viewportYOffset;
	int topLevelInterfaceId;
	int fps;
}
