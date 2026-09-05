package net.runelite.client.plugins.geautotrader;

import java.util.Collections;
import static org.junit.Assert.assertEquals;
import org.junit.Test;

public class GePromptInputServiceTest
{
	@Test
	public void testWrongPromptModeEmitsNoInput()
	{
		Capture capture = new Capture();
		GePromptInputService service = new GePromptInputService(capture, () -> false);
		assertEquals(GeReasonCode.EXECUTION_REJECTED,
			service.typeQuantity(10, state(GePromptMode.PRICE)));
		assertEquals("", capture.text.toString());
	}

	@Test
	public void testUnknownItemSearchPromptEmitsNoInput()
	{
		Capture capture = new Capture();
		GePromptInputService service = new GePromptInputService(capture, () -> false);
		assertEquals(GeReasonCode.EXECUTION_REJECTED,
			service.typeItemSearch("Tomato", state(GePromptMode.UNKNOWN)));
		assertEquals("", capture.text.toString());
	}

	@Test
	public void testQuantityTypesDigitsAndOneEnter()
	{
		Capture capture = new Capture();
		GePromptInputService service = new GePromptInputService(capture, () -> false);
		assertEquals(GeReasonCode.OK,
			service.typeQuantity(125, state(GePromptMode.QUANTITY)));
		assertEquals("125\n", capture.text.toString());
	}

	@Test
	public void testPriceTypesDigitsAndOneEnter()
	{
		Capture capture = new Capture();
		GePromptInputService service = new GePromptInputService(capture, () -> false);
		assertEquals(GeReasonCode.OK,
			service.typePrice(9000, state(GePromptMode.PRICE)));
		assertEquals("9000\n", capture.text.toString());
	}

	@Test
	public void testItemSearchTypesTextAndOneEnter()
	{
		Capture capture = new Capture();
		GePromptInputService service = new GePromptInputService(capture, () -> false);
		assertEquals(GeReasonCode.OK,
			service.typeItemSearch("Adamant platebody", state(GePromptMode.ITEM_SEARCH)));
		assertEquals("Adamant platebody\n", capture.text.toString());
	}

	@Test
	public void testF8PreventsInput()
	{
		Capture capture = new Capture();
		GePromptInputService service = new GePromptInputService(capture, () -> true);
		assertEquals(GeReasonCode.STOPPED_F8,
			service.typePrice(9000, state(GePromptMode.PRICE)));
		assertEquals("", capture.text.toString());
	}

	private static GeObservedState state(GePromptMode mode)
	{
		return new GeObservedState(
			true,
			false,
			true,
			true,
			false,
			301,
			2_000_000L,
			Collections.emptyList(),
			Collections.emptyMap(),
			-1,
			0,
			0,
			GeTradeSide.UNKNOWN,
			mode);
	}

	private static final class Capture implements GePromptInputService.KeyDispatcher
	{
		private final StringBuilder text = new StringBuilder();

		@Override
		public void typed(char c)
		{
			text.append(c);
		}

		@Override
		public void enter()
		{
			text.append('\n');
		}
	}
}
