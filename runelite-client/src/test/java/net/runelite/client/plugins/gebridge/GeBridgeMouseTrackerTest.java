package net.runelite.client.plugins.gebridge;

import java.awt.Canvas;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

public class GeBridgeMouseTrackerTest
{
	private final Canvas canvas = new Canvas();

	@Test
	public void testMovePressReleaseAreSequencedAndNeverConsumed()
	{
		GeBridgeMouseTracker tracker = new GeBridgeMouseTracker();

		MouseEvent move = new MouseEvent(canvas, MouseEvent.MOUSE_MOVED, 1000L, 0, 10, 20, 0, false, MouseEvent.NOBUTTON);
		assertSame(move, tracker.mouseMoved(move));
		assertFalse(move.isConsumed());

		MouseEvent press = new MouseEvent(canvas, MouseEvent.MOUSE_PRESSED, 1010L, 0, 11, 21, 1, false, MouseEvent.BUTTON1);
		assertSame(press, tracker.mousePressed(press));
		assertFalse(press.isConsumed());

		MouseEvent release = new MouseEvent(canvas, MouseEvent.MOUSE_RELEASED, 1020L, 0, 12, 22, 1, false, MouseEvent.BUTTON1);
		assertSame(release, tracker.mouseReleased(release));
		assertFalse(release.isConsumed());

		GeBridgeMouseState state = tracker.snapshot(
			55L, 99L, 12, 22, true, 0, false, 3, 1010L, true, true);
		assertEquals(3L, state.getEventSeq());
		assertEquals("RELEASE", state.getLastEventType());
		assertEquals(1, state.getLastEventButton());
		assertEquals(12, state.getLastEventCanvasX());
		assertEquals(22, state.getLastEventCanvasY());
		assertEquals(1000L, state.getLastMoveMillis());
		assertEquals(1020L, state.getLastReleaseMillis());
		assertEquals(55L, state.getUpdatedTick());
		assertEquals(99L, state.getUpdatedSeq());
		assertEquals(3, state.getRecentEvents().size());
		assertTrue(state.isCanvasFocused());
		assertTrue(state.isClientWindowFocused());
	}

	@Test
	public void testWheelIsObservedWithRotation()
	{
		GeBridgeMouseTracker tracker = new GeBridgeMouseTracker();
		MouseWheelEvent wheel = new MouseWheelEvent(
			canvas, MouseEvent.MOUSE_WHEEL, 2000L, 0, 30, 40, 0, false,
			MouseWheelEvent.WHEEL_UNIT_SCROLL, 3, -2);
		assertSame(wheel, tracker.mouseWheelMoved(wheel));
		assertFalse(wheel.isConsumed());

		GeBridgeMouseState state = tracker.snapshot(
			1L, 2L, 30, 40, true, 0, false, 0, 0L, true, true);
		assertEquals("WHEEL", state.getLastEventType());
		assertEquals(-2, state.getLastWheelRotation());
		assertEquals(2000L, state.getLastWheelMillis());
		assertEquals(-2, state.getRecentEvents().get(0).getWheelRotation());
	}

	@Test
	public void testRecentEventRingIsBounded()
	{
		GeBridgeMouseTracker tracker = new GeBridgeMouseTracker();
		for (int i = 0; i < 40; i++)
		{
			tracker.mouseMoved(new MouseEvent(
				canvas, MouseEvent.MOUSE_MOVED, 3000L + i, 0, i, i, 0, false, MouseEvent.NOBUTTON));
		}

		GeBridgeMouseState state = tracker.snapshot(
			1L, 2L, 39, 39, true, 0, false, 0, 0L, true, true);
		assertEquals(32, state.getRecentEvents().size());
		assertEquals(40L, state.getEventSeq());
		assertEquals(9L, state.getRecentEvents().get(0).getEventSeq());
		assertEquals(40L, state.getRecentEvents().get(31).getEventSeq());
	}
}
