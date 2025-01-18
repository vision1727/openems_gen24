package io.openems.edge.ess.fronius.ess;

import org.osgi.service.event.EventHandler;

import io.openems.common.channel.AccessMode;
import io.openems.common.channel.Level;
import io.openems.common.channel.Unit;
import io.openems.common.types.OpenemsType;
import io.openems.edge.common.channel.Doc;
import io.openems.edge.common.channel.StateChannel;
import io.openems.edge.common.channel.value.Value;
import io.openems.edge.common.component.OpenemsComponent;
import io.openems.edge.ess.api.SymmetricEss;
import io.openems.common.exceptions.OpenemsError.OpenemsNamedException;
import io.openems.edge.bridge.modbus.api.ModbusComponent;
import io.openems.edge.ess.api.AsymmetricEss;
import io.openems.edge.ess.api.ManagedAsymmetricEss;
import io.openems.edge.ess.api.ManagedSinglePhaseEss;
import io.openems.edge.ess.api.ManagedSymmetricEss;
import io.openems.edge.ess.api.SinglePhaseEss;
import io.openems.edge.Fronius.enums.SetControlMode;

public interface FroniusEss extends SymmetricEss, EventHandler, ManagedSinglePhaseEss, SinglePhaseEss, ManagedAsymmetricEss, AsymmetricEss,
ManagedSymmetricEss, ModbusComponent, OpenemsComponent {

    public static enum ChannelId implements io.openems.edge.common.channel.ChannelId {
        /**
         * Warning when Fronius system is not reachable.
         *
         * <ul>
         * <li>Type: State</li>
         * </ul>
         */
        SLAVE_COMMUNICATION_FAILED(Doc.of(Level.FAULT) //
                .text("Fronius ESS not reachable!")),

        /**
         * Describes the collection time. This represents the timestamp at which data
         * was collected or recorded.
         * 
         * <ul>
         * <li>Type: String</li>
         * </ul>
         */
        COLEC_TM(Doc.of(OpenemsType.STRING) //
                .text("Collection Time")),

        /**
         * Measures the power imported from or exported to the grid.
         * 
         * <ul>
         * <li>Type: Double</li>
         * </ul>
         */
        GRID_PW(Doc.of(OpenemsType.DOUBLE) //
                .text("Grid Power")),

        
        
     // EnumWriteChannsl
     		SET_CONTROL_MODE(Doc.of(SetControlMode.values()).accessMode(AccessMode.READ_WRITE)), //
        
        DCW3(Doc.of(OpenemsType.INTEGER) //
                .text("DCW3")),
        DCW4(Doc.of(OpenemsType.INTEGER) //
                .text("DCW4")),
        DCW_SF(Doc.of(OpenemsType.INTEGER) //
                .text("DCW_SF")),

        /**
         * Indicates the absolute discharge power.
         * 
         * <ul>
         * <li>Type: Double</li>
         * </ul>
         */
        ABS_DSC_POWER(Doc.of(OpenemsType.DOUBLE) //
                .text("Absolute Discharge Power")),
        
        SET_ACTIVE_POWER(Doc.of(OpenemsType.INTEGER) //
				.accessMode(AccessMode.WRITE_ONLY) //
				.unit(Unit.WATT)), //
        
        SET_ACTIVE_POWER2(Doc.of(OpenemsType.INTEGER) //
				.accessMode(AccessMode.WRITE_ONLY) //
				.unit(Unit.WATT)), //


        /**
         * Measures the total power consumption of the system or facility.
         * 
         * <ul>
         * <li>Type: Double</li>
         * </ul>
         */
        CONS_PW(Doc.of(OpenemsType.DOUBLE) //
                .text("Consumption Power")),

        /**
         * Reports the current state of charge of the battery. This percentage reflects
         * the remaining energy capacity of the battery.
         * 
         * <ul>
         * <li>Type: Integer</li>
         * </ul>
         */
        BT_SOC(Doc.of(OpenemsType.INTEGER) //
                .text("Battery State of Charge")),

        /**
         * Accumulates the total energy consumed actively by the system or facility.
         * This parameter is typically measured in kilowatt-hours.
         * 
         * <ul>
         * <li>Type: Integer</li>
         * </ul>
         */
        ACTIVE_CONSUMPTION_ENERGY(Doc.of(OpenemsType.INTEGER) //
                .text("Active Consumption Energy")),

        /**
         * Accumulates the total energy produced actively by the system. This value is
         * crucial for calculating the efficiency and output of energy production
         * systems.
         * 
         * <ul>
         * <li>Type: Integer</li>
         * </ul>
         */
        ACTIVE_PRODUCTION_ENERGY(Doc.of(OpenemsType.INTEGER) //
                .text("Active Production Energy"));

        private final Doc doc;

        private ChannelId(Doc doc) {
            this.doc = doc;
        }

        @Override
        public Doc doc() {
            return this.doc;
        }
    }

    /**
     * Internal method to set the 'nextValue' on {@link ChannelId#GRID_PW} Channel.
     *
     * @param value the next value
     */
  

   
    

    /**
     * Internal method to set the 'nextValue' on {@link ChannelId#CONS_PW} Channel.
     *
     * @param value the next value
     */
    public default void _setConsPw(double value) {
        this.channel(ChannelId.CONS_PW).setNextValue(value);
    }

    /**
     * Internal method to set the 'nextValue' on {@link ChannelId#BT_SOC} Channel.
     *
     * @param value the next value
     */
    public default void _setBtSoc(int value) {
        this.channel(ChannelId.BT_SOC).setNextValue(value);
    }

    /**
     * Gets the Channel for {@link ChannelId#SLAVE_COMMUNICATION_FAILED}.
     *
     * @return the Channel
     */
    public default StateChannel getSlaveCommunicationFailedChannel() {
        return this.channel(ChannelId.SLAVE_COMMUNICATION_FAILED);
    }

    /**
     * Gets the Slave Communication Failed State. See
     * {@link ChannelId#SLAVE_COMMUNICATION_FAILED}.
     *
     * @return the Channel {@link Value}
     */
    public default Value<Boolean> getSlaveCommunicationFailed() {
        return this.getSlaveCommunicationFailedChannel().value();
    }

    /**
     * Internal method to set the 'nextValue' on
     * {@link ChannelId#SLAVE_COMMUNICATION_FAILED} Channel.
     *
     * @param value the next value
     */
    public default void _setSlaveCommunicationFailed(boolean value) {
        this.getSlaveCommunicationFailedChannel().setNextValue(value);
    }

	public String getModbusBridgeId();

/**
 * Specify implementation to apply the calculated Power.
 *
 * @param activePowerL1   the active power set-point for L1
 * @param reactivePowerL1 the reactive power set-point for L1
 * @param activePowerL2   the active power set-point for L2
 * @param reactivePowerL2 the reactive power set-point for L2
 * @param activePowerL3   the active power set-point for L3
 * @param reactivePowerL3 the reactive power set-point for L3
 */
public void applyPower(int activePowerL1, int reactivePowerL1, int activePowerL2, int reactivePowerL2,
		int activePowerL3, int reactivePowerL3) throws OpenemsNamedException;
}