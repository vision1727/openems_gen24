package io.openems.edge.ess.fronius.ess;

import org.junit.Test;

import io.openems.edge.bridge.http.dummy.DummyBridgeHttpFactory;
import io.openems.edge.common.test.AbstractComponentTest.TestCase;
import io.openems.edge.common.test.ComponentTest;
import io.openems.edge.ess.power.api.Phase;

public class FroniusEssImplTest {

	@Test
	public void test() throws Exception {
		new ComponentTest(new FroniusEssImpl()) //
				.addReference("httpBridgeFactory", DummyBridgeHttpFactory.ofDummyBridge()) //
				.activate(MyConfig.create() //
						.setId("charger0") //
						.setIp("127.0.0.1") //
						.setPhase(Phase.L1) //
						.setCapacity(3600) //
						.build()) //
				.next(new TestCase()) //
				.deactivate();
	}

}