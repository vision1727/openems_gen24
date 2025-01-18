package io.openems.edge.ess.fronius.ess;

import io.openems.common.test.AbstractComponentConfig;
import io.openems.common.utils.ConfigUtils;
import io.openems.edge.ess.fronius.ess.Config;
import io.openems.edge.ess.power.api.Phase;

@SuppressWarnings("all")
public class MyConfig extends AbstractComponentConfig implements Config {

	protected static class Builder {
		private String id;
		private String ip;
		private Phase phase;
		private int capacity;
		private String modbusId;
		private int modbusUnitId;
		private boolean readOnlyMode;

		private Builder() {
		}

		public Builder setId(String id) {
			this.id = id;
			return this;
		}
		public Builder setModbusId(String modbusId) {
			this.modbusId = modbusId;
			return this;
		}
		public Builder setIp(String ip) {
			this.ip = ip;
			return this;
		}

		public Builder setReadOnlyMode(boolean readOnlyMode) {
			this.readOnlyMode = readOnlyMode;
			return this;
		}
		public Builder setPhase(Phase phase) {
			this.phase = phase;
			return this;
		}

		public Builder setCapacity(int capacity) {
			this.capacity = capacity;
			return this;
		}

		public MyConfig build() {
			return new MyConfig(this);
		}
	}

	/**
	 * Create a Config builder.
	 *
	 * @return a {@link Builder}
	 */
	public static Builder create() {
		return new Builder();
	}

	private final Builder builder;

	private MyConfig(Builder builder) {
		super(Config.class, builder.id);
		this.builder = builder;
	}

	@Override
	public String ip() {
		return this.builder.ip;
	}
	@Override
	public String modbus_id() {
		return this.builder.modbusId;
	}

	@Override
	public String Modbus_target() {
		return ConfigUtils.generateReferenceTargetFilter(this.id(), this.modbus_id());
	}

	@Override
	public int modbusUnitId() {
		return this.builder.modbusUnitId;
	}

	@Override
	public Phase phase() {
		return this.builder.phase;
	}

	@Override
	public int capacity() {
		return this.builder.capacity;
	}
	
	@Override
	public boolean readOnlyMode() {
		return this.builder.readOnlyMode;
	}
}