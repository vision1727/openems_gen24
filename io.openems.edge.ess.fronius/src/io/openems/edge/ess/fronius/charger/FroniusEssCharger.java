package io.openems.edge.ess.fronius.charger;

import org.osgi.service.event.EventHandler;

import io.openems.common.channel.Level;
import io.openems.common.types.OpenemsType;
import io.openems.edge.common.channel.Doc;
import io.openems.edge.common.channel.StateChannel;
import io.openems.edge.common.channel.value.Value;
import io.openems.edge.common.component.OpenemsComponent;
import io.openems.edge.meter.api.ElectricityMeter;
import io.openems.edge.pvinverter.api.ManagedSymmetricPvInverter;

public interface FroniusEssCharger
        extends ElectricityMeter, OpenemsComponent, EventHandler, ManagedSymmetricPvInverter {

    public static enum ChannelId implements io.openems.edge.common.channel.ChannelId {
        /**
         * Warning when one or more Inverters are not reachable.
         *
         * <ul>
         * <li>Type: State
         * </ul>
         */
        COMMUNICATION_FAILED(Doc.of(Level.FAULT) //
                .text("Fronius ESS not reachable or wrong output!")),

        /**
         * Measures the power produced by photovoltaic (PV) panels.
         * 
         * <ul>
         * <li>Type: Double</li>
         * </ul>
         */
        PV_PW(Doc.of(OpenemsType.DOUBLE) //
                .text("PV Power"));

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
     * Internal method to set the 'nextValue' on {@link ChannelId#PV_PW} Channel.
     *
     * @param value the next value
     */
    public default void _setPvPw(double value) {
        this.channel(ChannelId.PV_PW).setNextValue(value);
    }

    /**
     * Gets the Channel for {@link ChannelId#COMMUNICATION_FAILED}.
     *
     * @return the Channel
     */
    public default StateChannel getCommunicationFailedChannel() {
        return this.channel(ChannelId.COMMUNICATION_FAILED);
    }

    /**
     * Gets the Communication Failed State. See {@link ChannelId#COMMUNICATION_FAILED}.
     *
     * @return the Channel {@link Value}
     */
    public default Value<Boolean> getCommunicationFailed() {
        return this.getCommunicationFailedChannel().value();
    }

    /**
     * Internal method to set the 'nextValue' on
     * {@link ChannelId#COMMUNICATION_FAILED} Channel.
     *
     * @param value the next value
     */
    public default void _setCommunicationFailed(boolean value) {
        this.getCommunicationFailedChannel().setNextValue(value);
    }

}
