package net.runelite.client.plugins.gebridge;

import java.awt.Canvas;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

public class GeBridgeInputTrackerTest
{
	private final Canvas canvas = new Canvas();

	@Test
	public void testMouseMovementClickAndButtonsAreObservedWithoutConsumption()
	{
		GeBridgeInputTracker tracker = new GeBridgeInputTracker();

		MouseEvent move = new MouseEvent(canvas, MouseEvent.MOUSE_MOVED, 1000L, 0, 120, 220, 0, false, MouseEvent.NOBUTTON);
		assertSame(move, tracker.mouseMoved(move));
		assertFalse(move.isConsumed());

		MouseEvent press = new MouseEvent(canvas, MouseEvent.MOUSE_PRESSED, 1100L, 0, 121, 221, 1, false, MouseEvent.BUTTON1);
		assertSame(press, tracker.mousePressed(press));
		assertFalse(press.isConsumed());

		MouseEvent click = new MouseEvent(canvas, MouseEvent.MOUSE_CLICKED, 1200L, 0, 122, 222, 1, false, MouseEvent.BUTTON1);
		assertSame(click, tracker.mouseClicked(click));
		assertFalse(click.isConsumed());

		GeBridgeInputState pressed = tracker.snapshot(1250L);
		assertEquals(122, pressed.getMouseX());
		assertEquals(222, pressed.getMouseY());
		assertEquals(1000L, pressed.getLastMouseMoveEpochMs());
		assertEquals(1100L, pressed.getLastMousePressEpochMs());
		assertEquals(1200L, pressed.getLastMouseClickEpochMs());
		assertEquals(1, pressed.getLastMouseButton());
		assertEquals(1, pressed.getMouseButtonsDownMask());
		assertEquals(50L, pressed.getInputIdleMs());

		MouseEvent release = new MouseEvent(canvas, MouseEvent.MOUSE_RELEASED, 1300L, 0, 123, 223, 1, false, MouseEvent.BUTTON1);
		assertSame(release, tracker.mouseReleased(release));
		assertFalse(release.isConsumed());
		assertEquals(0, tracker.snapshot(1350L).getMouseButtonsDownMask());
	}

	@Test
	public void testMouseEnterExitAndWheelAreObserved()
	{
		GeBridgeInputTracker tracker = new GeBridgeInputTracker();
		MouseEvent enter = new MouseEvent(canvas, MouseEvent.MOUSE_ENTERED, 2000L, 0, 5, 6, 0, false, MouseEvent.NOBUTTON);
		tracker.mouseEntered(enter);
		assertTrue(tracker.snapshot(2000L).isMouseInsideCanvas());

		MouseWheelEvent wheel = new MouseWheelEvent(
			canvas, MouseEvent.MOUSE_WHEEL, 2100L, 0, 7, 8, 0, false,
			MouseWheelEvent.WHEEL_UNIT_SCROLL, 3, -2);
		assertSame(wheel, tracker.mouseWheelMoved(wheel));
		GeBridgeInputState afterWheel = tracker.snapshot(2150L);
		assertEquals(2100L, afterWheel.getLastMouseWheelEpochMs());
		assertEquals(-2, afterWheel.getLastWheelRotation());

		MouseEvent exit = new MouseEvent(canvas, MouseEvent.MOUSE_EXITED, 2200L, 0, 9, 10, 0, false, MouseEvent.NOBUTTON);
		tracker.mouseExited(exit);
		assertFalse(tracker.snapshot(2200L).isMouseInsideCanvas());
	}

	@Test
	public void testKeyboardStoresOnlyWhitelistedControlKeyNames()
	{
		GeBridgeInputTracker tracker = new GeBridgeInputTracker();

		KeyEvent shift = new KeyEvent(canvas, KeyEvent.KEY_PRESSED, 3000L, 0, KeyEvent.VK_SHIFT, KeyEvent.CHAR_UNDEFINED);
		tracker.keyPressed(shift);
		assertFalse(shift.isConsumed());
		GeBridgeInputState afterShift = tracker.snapshot(3050L);
		assertEquals(3000L, afterShift.getLastKeyboardEpochMs());
		assertEquals("SHIFT", afterShift.getLastControlKey());

		KeyEvent typedSecret = new KeyEvent(canvas, KeyEvent.KEY_TYPED, 3100L, 0, KeyEvent.VK_UNDEFINED, 'x');
		tracker.keyTyped(typedSecret);
		assertFalse(typedSecret.isConsumed());
		GeBridgeInputState afterTyped = tracker.snapshot(3150L);
		assertEquals(3100L, afterTyped.getLastKeyboardEpochMs());
		assertEquals("SHIFT", afterTyped.getLastControlKey());

		KeyEvent letter = new KeyEvent(canvas, KeyEvent.KEY_PRESSED, 3200L, 0, KeyEvent.VK_A, 'a');
		tracker.keyPressed(letter);
		GeBridgeInputState afterLetter = tracker.snapshot(3250L);
		assertEquals(3200L, afterLetter.getLastKeyboardEpochMs());
		assertEquals("SHIFT", afterLetter.getLastControlKey());
	}

	@Test
	public void testF8IsWhitelistedWithoutCapturingCharacters()
	{
		GeBridgeInputTracker tracker = new GeBridgeInputTracker();
		KeyEvent f8 = new KeyEvent(canvas, KeyEvent.KEY_PRESSED, 4000L, 0, KeyEvent.VK_F8, KeyEvent.CHAR_UNDEFINED);
		tracker.keyPressed(f8);
		assertEquals("F8", tracker.snapshot(4010L).getLastControlKey());
	}

	@Test
	public void testObservedInputRequestsSnapshotRefresh()
	{
		AtomicInteger refreshes = new AtomicInteger();
		GeBridgeInputTracker tracker = new GeBridgeInputTracker(refreshes::incrementAndGet);

		tracker.mouseMoved(new MouseEvent(
			canvas, MouseEvent.MOUSE_MOVED, 5000L, 0, 10, 20, 0, false, MouseEvent.NOBUTTON));
		tracker.keyPressed(new KeyEvent(
			canvas, KeyEvent.KEY_PRESSED, 5100L, 0, KeyEvent.VK_SHIFT, KeyEvent.CHAR_UNDEFINED));

		assertEquals(2, refreshes.get());
	}
}
