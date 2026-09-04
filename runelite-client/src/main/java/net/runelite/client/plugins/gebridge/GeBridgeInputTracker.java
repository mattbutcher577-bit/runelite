package net.runelite.client.plugins.gebridge;

import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import net.runelite.client.input.KeyListener;
import net.runelite.client.input.MouseListener;
import net.runelite.client.input.MouseWheelListener;

final class GeBridgeInputTracker implements MouseListener, MouseWheelListener, KeyListener
{
	private final AtomicLong lastInputEpochMs = new AtomicLong();
	private final AtomicLong lastMouseMoveEpochMs = new AtomicLong();
	private final AtomicLong lastMouseClickEpochMs = new AtomicLong();
	private final AtomicLong lastMousePressEpochMs = new AtomicLong();
	private final AtomicLong lastMouseReleaseEpochMs = new AtomicLong();
	private final AtomicLong lastMouseWheelEpochMs = new AtomicLong();
	private final AtomicLong lastKeyboardEpochMs = new AtomicLong();
	private final AtomicInteger mouseX = new AtomicInteger(-1);
	private final AtomicInteger mouseY = new AtomicInteger(-1);
	private final AtomicBoolean mouseInsideCanvas = new AtomicBoolean(false);
	private final AtomicInteger mouseButtonsDownMask = new AtomicInteger();
	private final AtomicInteger lastMouseButton = new AtomicInteger();
	private final AtomicInteger lastWheelRotation = new AtomicInteger();
	private final AtomicReference<String> lastControlKey = new AtomicReference<>("");

	GeBridgeInputState snapshot(long nowEpochMs)
	{
		long lastInput = lastInputEpochMs.get();
		long idle = lastInput <= 0L ? -1L : Math.max(0L, nowEpochMs - lastInput);
		return new GeBridgeInputState(
			lastInput,
			lastMouseMoveEpochMs.get(),
			lastMouseClickEpochMs.get(),
			lastMousePressEpochMs.get(),
			lastMouseReleaseEpochMs.get(),
			lastMouseWheelEpochMs.get(),
			lastKeyboardEpochMs.get(),
			mouseX.get(),
			mouseY.get(),
			mouseInsideCanvas.get(),
			mouseButtonsDownMask.get(),
			lastMouseButton.get(),
			lastWheelRotation.get(),
			lastControlKey.get(),
			idle
		);
	}

	@Override
	public MouseEvent mouseClicked(MouseEvent event)
	{
		updateMousePosition(event);
		lastMouseButton.set(event.getButton());
		lastMouseClickEpochMs.set(event.getWhen());
		touch(event.getWhen());
		return event;
	}

	@Override
	public MouseEvent mousePressed(MouseEvent event)
	{
		updateMousePosition(event);
		int button = event.getButton();
		lastMouseButton.set(button);
		lastMousePressEpochMs.set(event.getWhen());
		int mask = buttonMask(button);
		if (mask != 0)
		{
			mouseButtonsDownMask.getAndUpdate(value -> value | mask);
		}
		touch(event.getWhen());
		return event;
	}

	@Override
	public MouseEvent mouseReleased(MouseEvent event)
	{
		updateMousePosition(event);
		int button = event.getButton();
		lastMouseButton.set(button);
		lastMouseReleaseEpochMs.set(event.getWhen());
		int mask = buttonMask(button);
		if (mask != 0)
		{
			mouseButtonsDownMask.getAndUpdate(value -> value & ~mask);
		}
		touch(event.getWhen());
		return event;
	}

	@Override
	public MouseEvent mouseEntered(MouseEvent event)
	{
		mouseInsideCanvas.set(true);
		updateMousePosition(event);
		return event;
	}

	@Override
	public MouseEvent mouseExited(MouseEvent event)
	{
		mouseInsideCanvas.set(false);
		updateMousePosition(event);
		return event;
	}

	@Override
	public MouseEvent mouseDragged(MouseEvent event)
	{
		updateMousePosition(event);
		lastMouseMoveEpochMs.set(event.getWhen());
		touch(event.getWhen());
		return event;
	}

	@Override
	public MouseEvent mouseMoved(MouseEvent event)
	{
		updateMousePosition(event);
		lastMouseMoveEpochMs.set(event.getWhen());
		touch(event.getWhen());
		return event;
	}

	@Override
	public MouseWheelEvent mouseWheelMoved(MouseWheelEvent event)
	{
		mouseX.set(event.getX());
		mouseY.set(event.getY());
		lastWheelRotation.set(event.getWheelRotation());
		lastMouseWheelEpochMs.set(event.getWhen());
		touch(event.getWhen());
		return event;
	}

	@Override
	public void keyTyped(KeyEvent event)
	{
		lastKeyboardEpochMs.set(event.getWhen());
		touch(event.getWhen());
	}

	@Override
	public void keyPressed(KeyEvent event)
	{
		observeKeyboard(event);
	}

	@Override
	public void keyReleased(KeyEvent event)
	{
		observeKeyboard(event);
	}

	private void observeKeyboard(KeyEvent event)
	{
		lastKeyboardEpochMs.set(event.getWhen());
		String safeName = safeControlKeyName(event.getKeyCode());
		if (safeName != null)
		{
			lastControlKey.set(safeName);
		}
		touch(event.getWhen());
	}

	private void updateMousePosition(MouseEvent event)
	{
		mouseX.set(event.getX());
		mouseY.set(event.getY());
	}

	private void touch(long when)
	{
		lastInputEpochMs.accumulateAndGet(when, Math::max);
	}

	private static int buttonMask(int button)
	{
		return button > 0 && button <= 31 ? 1 << (button - 1) : 0;
	}

	private static String safeControlKeyName(int keyCode)
	{
		switch (keyCode)
		{
			case KeyEvent.VK_SHIFT:
				return "SHIFT";
			case KeyEvent.VK_CONTROL:
				return "CTRL";
			case KeyEvent.VK_ALT:
				return "ALT";
			case KeyEvent.VK_ESCAPE:
				return "ESCAPE";
			case KeyEvent.VK_ENTER:
				return "ENTER";
			case KeyEvent.VK_F8:
				return "F8";
			case KeyEvent.VK_TAB:
				return "TAB";
			case KeyEvent.VK_BACK_SPACE:
				return "BACKSPACE";
			case KeyEvent.VK_DELETE:
				return "DELETE";
			case KeyEvent.VK_LEFT:
				return "LEFT";
			case KeyEvent.VK_RIGHT:
				return "RIGHT";
			case KeyEvent.VK_UP:
				return "UP";
			case KeyEvent.VK_DOWN:
				return "DOWN";
			default:
				return null;
		}
	}
}
