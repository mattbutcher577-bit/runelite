package net.runelite.client.plugins.geautotrader;

import java.awt.Component;
import java.awt.event.KeyEvent;
import java.util.function.BooleanSupplier;

public final class GePromptInputService
{
	interface KeyDispatcher
	{
		void typed(char c);

		void enter();
	}

	private final KeyDispatcher dispatcher;
	private final BooleanSupplier stopped;

	public GePromptInputService(Component canvas, BooleanSupplier stopped)
	{
		this(new AwtKeyDispatcher(canvas), stopped);
	}

	GePromptInputService(KeyDispatcher dispatcher, BooleanSupplier stopped)
	{
		this.dispatcher = dispatcher;
		this.stopped = stopped;
	}

	public GeReasonCode typeItemSearch(String text, GeObservedState state)
	{
		if (state == null || state.getPromptMode() != GePromptMode.ITEM_SEARCH)
		{
			return GeReasonCode.EXECUTION_REJECTED;
		}
		if (text == null || text.trim().isEmpty())
		{
			return GeReasonCode.EXECUTION_REJECTED;
		}
		return type(text);
	}

	public GeReasonCode typeQuantity(int quantity, GeObservedState state)
	{
		if (state == null || state.getPromptMode() != GePromptMode.QUANTITY || quantity <= 0)
		{
			return GeReasonCode.EXECUTION_REJECTED;
		}
		return type(Integer.toString(quantity));
	}

	public GeReasonCode typePrice(int price, GeObservedState state)
	{
		if (state == null || state.getPromptMode() != GePromptMode.PRICE || price <= 0)
		{
			return GeReasonCode.EXECUTION_REJECTED;
		}
		return type(Integer.toString(price));
	}

	private GeReasonCode type(String text)
	{
		if (stopped.getAsBoolean())
		{
			return GeReasonCode.STOPPED_F8;
		}
		for (int i = 0; i < text.length(); i++)
		{
			char c = text.charAt(i);
			if (Character.isISOControl(c))
			{
				return GeReasonCode.EXECUTION_REJECTED;
			}
			dispatcher.typed(c);
		}
		if (stopped.getAsBoolean())
		{
			return GeReasonCode.STOPPED_F8;
		}
		dispatcher.enter();
		return GeReasonCode.OK;
	}

	private static final class AwtKeyDispatcher implements KeyDispatcher
	{
		private final Component component;

		private AwtKeyDispatcher(Component component)
		{
			if (component == null)
			{
				throw new IllegalArgumentException("RuneLite canvas required");
			}
			this.component = component;
		}

		@Override
		public void typed(char c)
		{
			long when = System.currentTimeMillis();
			component.dispatchEvent(new KeyEvent(
				component, KeyEvent.KEY_TYPED, when, 0, KeyEvent.VK_UNDEFINED, c));
		}

		@Override
		public void enter()
		{
			long when = System.currentTimeMillis();
			component.dispatchEvent(new KeyEvent(
				component, KeyEvent.KEY_PRESSED, when, 0, KeyEvent.VK_ENTER, '\n'));
			component.dispatchEvent(new KeyEvent(
				component, KeyEvent.KEY_RELEASED, when, 0, KeyEvent.VK_ENTER, '\n'));
		}
	}
}
