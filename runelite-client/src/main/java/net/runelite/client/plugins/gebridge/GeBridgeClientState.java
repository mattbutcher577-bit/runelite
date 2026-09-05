package net.runelite.client.plugins.gebridge;

import java.util.Collections;
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
	int realWidth;
	int realHeight;
	int stretchedWidth;
	int stretchedHeight;
	boolean stretchedEnabled;
	int viewportWidth;
	int viewportHeight;
	int viewportXOffset;
	int viewportYOffset;
	int topLevelInterfaceId;
	int fps;
	int clientTick;
	int lastLoginTick;
	boolean loginSettled;

	GeBridgeClientState(
		boolean loggedIn,
		int world,
		List<String> worldTypes,
		boolean membersWorld,
		int canvasWidth,
		int canvasHeight,
		int canvasScreenX,
		int canvasScreenY,
		boolean canvasScreenPositionValid,
		int realWidth,
		int realHeight,
		int stretchedWidth,
		int stretchedHeight,
		boolean stretchedEnabled,
		int viewportWidth,
		int viewportHeight,
		int viewportXOffset,
		int viewportYOffset,
		int topLevelInterfaceId,
		int fps,
		int clientTick,
		int lastLoginTick,
		boolean loginSettled)
	{
		this.loggedIn = loggedIn;
		this.world = world;
		this.worldTypes = worldTypes;
		this.membersWorld = membersWorld;
		this.canvasWidth = canvasWidth;
		this.canvasHeight = canvasHeight;
		this.canvasScreenX = canvasScreenX;
		this.canvasScreenY = canvasScreenY;
		this.canvasScreenPositionValid = canvasScreenPositionValid;
		this.realWidth = realWidth;
		this.realHeight = realHeight;
		this.stretchedWidth = stretchedWidth;
		this.stretchedHeight = stretchedHeight;
		this.stretchedEnabled = stretchedEnabled;
		this.viewportWidth = viewportWidth;
		this.viewportHeight = viewportHeight;
		this.viewportXOffset = viewportXOffset;
		this.viewportYOffset = viewportYOffset;
		this.topLevelInterfaceId = topLevelInterfaceId;
		this.fps = fps;
		this.clientTick = clientTick;
		this.lastLoginTick = lastLoginTick;
		this.loginSettled = loginSettled;
	}

	GeBridgeClientState(
		boolean loggedIn,
		int world,
		List<String> worldTypes,
		boolean membersWorld,
		int canvasWidth,
		int canvasHeight,
		int canvasScreenX,
		int canvasScreenY,
		boolean canvasScreenPositionValid,
		int realWidth,
		int realHeight,
		int stretchedWidth,
		int stretchedHeight,
		boolean stretchedEnabled,
		int viewportWidth,
		int viewportHeight,
		int viewportXOffset,
		int viewportYOffset,
		int topLevelInterfaceId,
		int fps)
	{
		this(
			loggedIn,
			world,
			worldTypes,
			membersWorld,
			canvasWidth,
			canvasHeight,
			canvasScreenX,
			canvasScreenY,
			canvasScreenPositionValid,
			realWidth,
			realHeight,
			stretchedWidth,
			stretchedHeight,
			stretchedEnabled,
			viewportWidth,
			viewportHeight,
			viewportXOffset,
			viewportYOffset,
			topLevelInterfaceId,
			fps,
			-1,
			-1,
			false);
	}

	GeBridgeClientState(
		boolean loggedIn,
		int world,
		List<String> worldTypes,
		boolean membersWorld,
		int canvasWidth,
		int canvasHeight,
		int canvasScreenX,
		int canvasScreenY,
		boolean canvasScreenPositionValid,
		int viewportWidth,
		int viewportHeight,
		int viewportXOffset,
		int viewportYOffset,
		int topLevelInterfaceId,
		int fps)
	{
		this(
			loggedIn,
			world,
			worldTypes,
			membersWorld,
			canvasWidth,
			canvasHeight,
			canvasScreenX,
			canvasScreenY,
			canvasScreenPositionValid,
			canvasWidth,
			canvasHeight,
			canvasWidth,
			canvasHeight,
			false,
			viewportWidth,
			viewportHeight,
			viewportXOffset,
			viewportYOffset,
			topLevelInterfaceId,
			fps,
			-1,
			-1,
			false);
	}

	static GeBridgeClientState unavailable(int lastLoginTick)
	{
		return new GeBridgeClientState(
			false,
			-1,
			Collections.emptyList(),
			false,
			-1,
			-1,
			-1,
			-1,
			false,
			-1,
			-1,
			-1,
			-1,
			false,
			-1,
			-1,
			-1,
			-1,
			-1,
			-1,
			-1,
			lastLoginTick,
			false);
	}
}
