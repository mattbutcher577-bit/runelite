package net.runelite.client.plugins.geautotrader;

import java.awt.Dimension;
import java.awt.Graphics2D;
import javax.inject.Inject;
import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.LineComponent;

public final class GeAutoTraderOverlay extends OverlayPanel
{
	private final GeAutoTraderPlugin plugin;

	@Inject
	private GeAutoTraderOverlay(GeAutoTraderPlugin plugin)
	{
		super(plugin);
		this.plugin = plugin;
		setPosition(OverlayPosition.TOP_LEFT);
		setPriority(PRIORITY_MED);
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		GeObservedState state = plugin.getLastState();
		panelComponent.getChildren().add(LineComponent.builder()
			.left("GE Auto-Trader V6")
			.right(plugin.getStatusText())
			.build());
		panelComponent.getChildren().add(LineComponent.builder()
			.left("F8")
			.right("STOP")
			.build());
		panelComponent.getChildren().add(LineComponent.builder()
			.left("World")
			.right(state == null ? "-" : Integer.toString(state.getWorld()))
			.build());
		panelComponent.getChildren().add(LineComponent.builder()
			.left("GP")
			.right(state == null ? "-" : Long.toString(state.getGp()))
			.build());
		panelComponent.getChildren().add(LineComponent.builder()
			.left("Reserved")
			.right(Long.toString(plugin.getReservedGp()))
			.build());
		for (int slot = 1; slot <= 3; slot++)
		{
			GeCandidate candidate = plugin.getCandidate(slot);
			String right = plugin.getPhase(slot).name();
			if (candidate != null)
			{
				right += " " + candidate.getName();
			}
			panelComponent.getChildren().add(LineComponent.builder()
				.left("S" + slot)
				.right(right)
				.build());
		}
		panelComponent.getChildren().add(LineComponent.builder()
			.left("Reason")
			.right(plugin.getLastReason().name())
			.build());
		return super.render(graphics);
	}
}
