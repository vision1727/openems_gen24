package io.openems.edge.ess.fronius.gridmeter;

import org.osgi.service.event.EventHandler;

import io.openems.common.channel.Level;
import io.openems.common.types.OpenemsType;
import io.openems.edge.common.channel.Doc;
import io.openems.edge.common.channel.StateChannel;
import io.openems.edge.common.channel.value.Value;
import io.openems.edge.common.component.OpenemsComponent;
import io.openems.edge.meter.api.ElectricityMeter;
import io.openems.edge.pvinverter.api.ManagedSymmetricPvInverter;

public interface FroniusEssGridMeter
        extends ElectricityMeter, OpenemsComponent, EventHandler, ManagedSymmetricPvInverter {

	
    public static enum ChannelId implements io.openems.edge.common.channel.ChannelId {
        /**
         * Warning when the Fronius Grid Meter is not reachable.
         *
         * <ul>
         * <li>Type: State
         * </ul>
         */
        GRID_COMMUNICATION_FAILED(Doc.of(Level.FAULT) //
                .text("Fronius Grid Meter not reachable!")),
        /**
         * Measures the power imported from or exported to the grid.
         * 
         * <ul>
         * <li>Type: Double</li>
         * </ul>
         */
        GRID_PW(Doc.of(OpenemsType.DOUBLE) //
                .text("Grid Power"));

 
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
    public default void _setGridPw(double value) {
        this.channel(ChannelId.GRID_PW).setNextValue(value);
    }

    /**
     * Gets the Channel for {@link ChannelId#GRID_COMMUNICATION_FAILED}.
     *
     * @return the Channel
     */
    public default StateChannel getGridCommunicationFailedChannel() {
        return this.channel(ChannelId.GRID_COMMUNICATION_FAILED);
    }

    /**
     * Gets the Grid Communication Failed State. See
     * {@link ChannelId#GRID_COMMUNICATION_FAILED}.
     *
     * @return the Channel {@link Value}
     */
    public default Value<Boolean> getGridCommunicationFailed() {
        return this.getGridCommunicationFailedChannel().value();
    }

    /**
     * Internal method to set the 'nextValue' on
     * {@link ChannelId#GRID_COMMUNICATION_FAILED} Channel.
     *
     * @param value the next value
     */
    public default void _setGridCommunicationFailed(boolean value) {
        this.getGridCommunicationFailedChannel().setNextValue(value);
    }
    
    
}
