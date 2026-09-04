package net.runelite.client.plugins.gebridge;

import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import net.runelite.client.input.MouseListener;
import net.runelite.client.input.MouseWheelListener;

final class GeBridgeMouseTracker implements MouseListener, MouseWheelListener
{
	private static final int RECENT_EVENT_LIMIT = 32;

	private final Runnable onInput;
	private final AtomicLong eventSeq = new AtomicLong();
	private final AtomicLong lastMoveMillis = new AtomicLong();
	private final AtomicLong lastReleaseMillis = new AtomicLong();
	private final AtomicLong lastWheelMillis = new AtomicLong();
	private final AtomicInteger lastWheelRotation = new AtomicInteger();
	private final AtomicReference<String> lastEventType = new AtomicReference<>("NONE");
	private final AtomicInteger lastEventButton = new AtomicInteger();
	private final AtomicInteger lastEventCanvasX = new AtomicInteger(-1);
	private final AtomicInteger lastEventCanvasY = new AtomicInteger(-1);
	private final Deque<GeBridgeMouseEvent> recentEvents = new ArrayDeque<>();

	GeBridgeMouseTracker()
	{
		this(() -> { });
	}

	GeBridgeMouseTracker(Runnable onInput)
	{
		this.onInput = onInput == null ? () -> { } : onInput;
	}

	GeBridgeMouseState snapshot(
		long updatedTick,
		long updatedSeq,
		int canvasX,
		int canvasY,
		boolean insideCanvas,
		int currentButton,
		boolean dragging,
		int mouseIdleTicks,
		long lastPressMillis,
		boolean canvasFocused,
		boolean clientWindowFocused)
	{
		return new GeBridgeMouseState(
			updatedTick,
			updatedSeq,
			eventSeq.get(),
			canvasX,
			canvasY,
			insideCanvas,
			currentButton,
			dragging,
			mouseIdleTicks,
			lastPressMillis,
			lastMoveMillis.get(),
			lastReleaseMillis.get(),
			lastWheelMillis.get(),
			lastWheelRotation.get(),
			lastEventType.get(),
			lastEventButton.get(),
			lastEventCanvasX.get(),
			lastEventCanvasY.get(),
			canvasFocused,
			clientWindowFocused,
			copyRecentEvents());
	}

	@Override
	public MouseEvent mouseClicked(MouseEvent event)
	{
		record("CLICK", event, 0);
		return event;
	}

	@Override
	public MouseEvent mousePressed(MouseEvent event)
	{
		record("PRESS", event, 0);
		return event;
	}

	@Override
	public MouseEvent mouseReleased(MouseEvent event)
	{
		lastReleaseMillis.set(event.getWhen());
		record("RELEASE", event, 0);
		return event;
	}

	@Override
	public MouseEvent mouseEntered(MouseEvent event)
	{
		record("ENTER", event, 0);
		return event;
	}

	@Override
	public MouseEvent mouseExited(MouseEvent event)
	{
		record("EXIT", event, 0);
		return event;
	}

	@Override
	public MouseEvent mouseDragged(MouseEvent event)
	{
		lastMoveMillis.set(event.getWhen());
		record("DRAG", event, 0);
		return event;
	}

	@Override
	public MouseEvent mouseMoved(MouseEvent event)
	{
		lastMoveMillis.set(event.getWhen());
		record("MOVE", event, 0);
		return event;
	}

	@Override
	public MouseWheelEvent mouseWheelMoved(MouseWheelEvent event)
	{
		lastWheelMillis.set(event.getWhen());
		lastWheelRotation.set(event.getWheelRotation());
		record("WHEEL", event, event.getWheelRotation());
		return event;
	}

	private void record(String type, MouseEvent event, int wheelRotation)
	{
		long seq = eventSeq.incrementAndGet();
		lastEventType.set(type);
		lastEventButton.set(event.getButton());
		lastEventCanvasX.set(event.getX());
		lastEventCanvasY.set(event.getY());
		GeBridgeMouseEvent observed = new GeBridgeMouseEvent(
			seq,
			type,
			event.getX(),
			event.getY(),
			event.getButton(),
			wheelRotation,
			event.getWhen(),
			event.isConsumed());
		synchronized (recentEvents)
		{
			while (recentEvents.size() >= RECENT_EVENT_LIMIT)
			{
				recentEvents.removeFirst();
			}
			recentEvents.addLast(observed);
		}
		onInput.run();
	}

	private List<GeBridgeMouseEvent> copyRecentEvents()
	{
		synchronized (recentEvents)
		{
			return new ArrayList<>(recentEvents);
		}
	}
}
