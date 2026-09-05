package net.runelite.client.plugins.geautotrader;

import org.junit.runner.RunWith;
import org.junit.runners.Suite;

/**
 * Consolidated V6 verification entry point.
 *
 * The focused tests stay separate for diagnosis, while this suite gives one
 * stable target that exercises the complete buy/collect/sell lifecycle plus
 * restart recovery, slot targeting, prompt handling and fail-closed safety.
 */
@RunWith(Suite.class)
@Suite.SuiteClasses({
	GeAutoTraderPluginTest.class,
	GeExecutionServiceTest.class,
	GeLimitLedgerTest.class,
	GeMarketServiceDiagnosticsTest.class,
	GeOpportunitySelectorTest.class,
	GePromptInputServiceTest.class,
	GeSafetyPolicyTest.class,
	GeStateReaderTest.class,
	GeWidgetActionResolverTest.class,
	GeSearchResultTraversalTest.class,
	GeSlotRootDispatchTest.class,
	GeActionDispatcherTest.class,
	GeOfferStatusComponentDispatchTest.class,
	GeProofDrivenExecutionTest.class,
	GeTradeLedgerTest.class,
	GeTradeLedgerPersistenceTest.class,
	GeTradeSchedulerTest.class,
	GeTradeStateMachineBuyTest.class,
	GeTradeStateMachineCollectTest.class,
	GeTradeStateMachineSellTest.class,
	GeTradeStateMachineLifecycleTest.class,
	GeTradeStateMachineProofTimeoutTest.class,
	GeTradeStateMachineReasonResetTest.class,
	GeTradeStateMachineRestartRecoveryTest.class,
	GeTradeStateMachineSetupSerializationTest.class
})
public class GeAutoTraderV6EndToEndTest
{
}
