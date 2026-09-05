package net.runelite.client.plugins.geautotrader;

import java.time.Duration;
import java.time.Instant;

final class GePendingUiOperation
{
	private static final Duration TARGET_UNAVAILABLE_RETRY_WINDOW = Duration.ofSeconds(5);

	private final GePlannedActionType type;
	private final Instant firstAttemptAt;
	private Instant lastAttemptAt;
	private int attemptCount;
	private GeReasonCode lastResult = GeReasonCode.OK;

	GePendingUiOperation(GePlannedActionType type, Instant now)
	{
		this.type = type == null ? GePlannedActionType.NONE : type;
		this.firstAttemptAt = now;
	}

	GePlannedActionType getType()
	{
		return type;
	}

	boolean matches(GePlannedActionType candidate)
	{
		return type == candidate;
	}

	void markAttempt(Instant now)
	{
		lastAttemptAt = now;
		attemptCount++;
	}

	void recordResult(GeReasonCode result)
	{
		lastResult = result == null ? GeReasonCode.EXECUTION_REJECTED : result;
	}

	boolean isTargetUnavailableExpired(Instant now)
	{
		return lastResult == GeReasonCode.EXECUTION_TARGET_UNAVAILABLE
			&& firstAttemptAt != null
			&& now != null
			&& now.isAfter(firstAttemptAt.plus(TARGET_UNAVAILABLE_RETRY_WINDOW));
	}

	Instant getLastAttemptAt()
	{
		return lastAttemptAt;
	}

	int getAttemptCount()
	{
		return attemptCount;
	}

	GeReasonCode getLastResult()
	{
		return lastResult;
	}
}
