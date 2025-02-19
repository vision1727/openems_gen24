package io.openems.edge.ess.fronius.gridmeter;

import org.junit.Test;

import io.openems.common.types.MeterType;
import io.openems.edge.bridge.http.dummy.DummyBridgeHttpFactory;
import io.openems.edge.common.test.AbstractComponentTest.TestCase;
import io.openems.edge.common.test.ComponentTest;

public class FroniusEssGridMeterImplTest {

	@Test
	public void test() throws Exception {
		new ComponentTest(new FroniusEssGridMeterImpl()) //
				.addReference("httpBridgeFactory", DummyBridgeHttpFactory.ofDummyBridge()) //
				.activate(MyConfig.create() //
						.setId("charger0") //
						.setIp("127.0.0.1") //
						.setType(MeterType.GRID) //
						.build()) //
				.next(new TestCase()) //
				.deactivate();
	}

}