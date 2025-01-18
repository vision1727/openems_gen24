package io.openems.edge.ess.fronius.gridmeter;

import static io.openems.common.utils.JsonUtils.getAsFloat;
import static io.openems.common.utils.JsonUtils.getAsJsonObject;
import static java.lang.Math.round;

import org.osgi.service.cm.ConfigurationAdmin;
import org.osgi.service.component.ComponentContext;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.ConfigurationPolicy;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferenceCardinality;
import org.osgi.service.component.annotations.ReferencePolicy;
import org.osgi.service.component.annotations.ReferencePolicyOption;
import org.osgi.service.event.Event;
import org.osgi.service.event.EventHandler;
import org.osgi.service.event.propertytypes.EventTopics;
import org.osgi.service.metatype.annotations.Designate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.JsonElement;

import io.openems.common.exceptions.OpenemsError.OpenemsNamedException;
import io.openems.common.types.MeterType;
import io.openems.edge.bridge.http.api.BridgeHttp;
import io.openems.edge.bridge.http.api.BridgeHttpFactory;
import io.openems.edge.bridge.http.api.HttpError;
import io.openems.edge.bridge.http.api.HttpResponse;
import io.openems.edge.common.component.AbstractOpenemsComponent;
import io.openems.edge.common.component.OpenemsComponent;
import io.openems.edge.common.event.EdgeEventConstants;
import io.openems.edge.ess.api.SymmetricEss;
import io.openems.edge.meter.api.ElectricityMeter;
import io.openems.edge.timedata.api.Timedata;
import io.openems.edge.timedata.api.TimedataProvider;
import io.openems.edge.timedata.api.utils.CalculateEnergyFromPower;


@Designate(ocd = Config.class, factory = true)
@Component(//
        name = "Fronius.ESS.Grid-Meter", //
        immediate = true, //
        configurationPolicy = ConfigurationPolicy.REQUIRE)

@EventTopics({ //
        EdgeEventConstants.TOPIC_CYCLE_BEFORE_PROCESS_IMAGE, //
})
public class FroniusEssGridMeterImpl extends AbstractOpenemsComponent
        implements FroniusEssGridMeter, ElectricityMeter, OpenemsComponent, EventHandler, TimedataProvider {

    private final Logger log = LoggerFactory.getLogger(FroniusEssGridMeterImpl.class);
    private final CalculateEnergyFromPower calculateProductionEnergy = new CalculateEnergyFromPower(this,
            ElectricityMeter.ChannelId.ACTIVE_PRODUCTION_ENERGY);
    private final CalculateEnergyFromPower calculateConsumptionEnergy = new CalculateEnergyFromPower(this,
            ElectricityMeter.ChannelId.ACTIVE_CONSUMPTION_ENERGY);

    @Reference
    protected ConfigurationAdmin cm;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    private BridgeHttpFactory httpBridgeFactory;
    private BridgeHttp httpBridge;

    @Reference(policy = ReferencePolicy.DYNAMIC, policyOption = ReferencePolicyOption.GREEDY, cardinality = ReferenceCardinality.OPTIONAL)
    private volatile Timedata timedata;

    private String currentGridStatus = "Unknown";
    private String baseUrl;
    private Config config;

    private float voltageL1, voltageL2, voltageL3;
    private float currentL1, currentL2, currentL3;
    private float pPhase1, pPhase2, pPhase3, pGrid;
    private float rPhase1, rPhase2, rPhase3, rGrid;
    private float frequency;

    public FroniusEssGridMeterImpl() {
        super(//
                OpenemsComponent.ChannelId.values(), //
                ElectricityMeter.ChannelId.values(), //
                FroniusEssGridMeter.ChannelId.values() //
        );
        ElectricityMeter.calculatePhasesFromActivePower(this);
    }

    @Activate
    private void activate(ComponentContext context, Config config) {
        super.activate(context, config.id(), config.alias(), config.enabled());
        this.baseUrl = "http://" + config.ip();
        this.httpBridge = this.httpBridgeFactory.get();
        this.config = config;

        if (!this.isEnabled()) {
            return;
        }

        // Update the URL to use the Fronius GEN24 API endpoint
        this.httpBridge.subscribeJsonEveryCycle(this.baseUrl + "/solar_api/v1/GetMeterRealtimeData.cgi?Scope=System",
                this::fetchAndUpdateEssRealtimeStatus);
    }

    @Override
    @Deactivate
    protected void deactivate() {
        this.httpBridgeFactory.unget(this.httpBridge);
        this.httpBridge = null;
        super.deactivate();
    }

    @Override
    public void handleEvent(Event event) {
        if (!this.isEnabled()) {
            return;
        }

        switch (event.getTopic()) {
            case EdgeEventConstants.TOPIC_CYCLE_BEFORE_PROCESS_IMAGE:
                this.calculateEnergy();
                break;
        }
    }

    private void fetchAndUpdateEssRealtimeStatus(HttpResponse<JsonElement> result, HttpError error) {
        Integer gridPw = null;
        String currentGridStatus = "Unknown";

        if (error != null) {
            this.logDebug(this.log, error.getMessage());
        } else {
            try {
                var response = getAsJsonObject(result.data());
                var bodyData = getAsJsonObject(response, "Body").getAsJsonObject("Data");
                var data = bodyData.getAsJsonObject("0"); // Assuming "0" is the key for current meter data

                // Extracting necessary data from the raw JSON response
                float pGrid = getAsFloat(data, "PowerReal_P_Sum");  // Sum of real power
              //  float pPhase1 = getAsFloat(data, "PowerReal_P_Phase_1");
           //     float pPhase2 = getAsFloat(data, "PowerReal_P_Phase_2");
            //    float pPhase3 = getAsFloat(data, "PowerReal_P_Phase_3");

                // Extracting voltage and current values for each phase
  //              this.voltageL1 = getAsFloat(data, "Voltage_AC_Phase_1");
   //             this.voltageL2 = getAsFloat(data, "Voltage_AC_Phase_2");
   //             this.voltageL3 = getAsFloat(data, "Voltage_AC_Phase_3");
                
                this.currentL1 = getAsFloat(data, "Current_AC_Phase_1");
                int roundedcurrentL1 = Math.round(this.currentL1*1000);
                this.channel(ElectricityMeter.ChannelId.CURRENT_L1).setNextValue(roundedcurrentL1);
                
                this.currentL2 = getAsFloat(data, "Current_AC_Phase_2");
                int roundedcurrentL2 = Math.round(this.currentL2*1000);
                this.channel(ElectricityMeter.ChannelId.CURRENT_L2).setNextValue(roundedcurrentL2);
                
                this.currentL3 = getAsFloat(data, "Current_AC_Phase_3");
                int roundedcurrentL3 = Math.round(this.currentL3*1000);
                this.channel(ElectricityMeter.ChannelId.CURRENT_L3).setNextValue(roundedcurrentL3);
                
          //      this.currentL2 = getAsFloat(data, "Current_AC_Phase_2");
            //    this.currentL3 = getAsFloat(data, "Current_AC_Phase_3");
                
                this.voltageL1 = getAsFloat(data, "Voltage_AC_Phase_1");
                int roundedVoltageL1 = Math.round(this.voltageL1*1000);
                this.channel(ElectricityMeter.ChannelId.VOLTAGE_L1).setNextValue(roundedVoltageL1);
                
                this.voltageL2 = getAsFloat(data, "Voltage_AC_Phase_2");
                int roundedVoltageL2 = Math.round(this.voltageL2*1000);
                this.channel(ElectricityMeter.ChannelId.VOLTAGE_L2).setNextValue(roundedVoltageL2);
                
                this.voltageL3 = getAsFloat(data, "Voltage_AC_Phase_3");
                int roundedVoltageL3 = Math.round(this.voltageL3*1000);
                this.channel(ElectricityMeter.ChannelId.VOLTAGE_L3).setNextValue(roundedVoltageL3);
                
                
                this.pPhase1 = getAsFloat(data, "PowerReal_P_Phase_1");
                int roundedpowerL1 = Math.round(this.pPhase1);
                this.channel(ElectricityMeter.ChannelId.ACTIVE_POWER_L1).setNextValue(roundedpowerL1);
                
                this.pPhase2 = getAsFloat(data, "PowerReal_P_Phase_2");
                int roundedpowerL2 = Math.round(this.pPhase2);
                this.channel(ElectricityMeter.ChannelId.ACTIVE_POWER_L2).setNextValue(roundedpowerL2);
                
                this.pPhase3 = getAsFloat(data, "PowerReal_P_Phase_3");
                int roundedpowerL3 = Math.round(this.pPhase3);
                this.channel(ElectricityMeter.ChannelId.ACTIVE_POWER_L3).setNextValue(roundedpowerL3);
                
                this.pGrid = getAsFloat(data, "PowerReal_P_Sum");
                int roundedpowergrid = Math.round(this.pGrid);
                this.channel(ElectricityMeter.ChannelId.ACTIVE_POWER).setNextValue(roundedpowergrid);
                
                
       
             
                this.rPhase1 = getAsFloat(data, "PowerReactive_Q_Phase_1");
                int roundedpowerrL1 = Math.round(this.rPhase1);
                this.channel(ElectricityMeter.ChannelId.REACTIVE_POWER_L1).setNextValue(roundedpowerrL1);
                
                this.rPhase2 = getAsFloat(data, "PowerReactive_Q_Phase_2");
                int roundedpowerrL2 = Math.round(this.rPhase2);
                this.channel(ElectricityMeter.ChannelId.REACTIVE_POWER_L2).setNextValue(roundedpowerrL2);
                
                this.rPhase3 = getAsFloat(data, "PowerReactive_Q_Phase_3");
                int roundedpowerrL3 = Math.round(this.rPhase3);
                this.channel(ElectricityMeter.ChannelId.REACTIVE_POWER_L3).setNextValue(roundedpowerrL3);
                
                this.rGrid = getAsFloat(data, "PowerReactive_Q_Sum");
                int roundedpowerrgrid = Math.round(this.rGrid);
                this.channel(ElectricityMeter.ChannelId.REACTIVE_POWER).setNextValue(roundedpowerrgrid);
                
                this.frequency = getAsFloat(data, "Frequency_Phase_Average");
                int roundedfrequency = Math.round(this.frequency*1000);
                this.channel(ElectricityMeter.ChannelId.FREQUENCY).setNextValue(roundedfrequency);
             
          
                 if (pGrid > 0) {
                    currentGridStatus = "Buy from Grid";
                    gridPw = (int) pGrid;
                } else if (pGrid < 0) {
                    currentGridStatus = "Sell to Grid";
                    gridPw = (int) -pGrid;
                } else {
                    currentGridStatus = "Unknown";
                    gridPw = 0;
                }

                // Optionally log phase-wise powers, voltages, and currents for debugging
                this.logDebug(this.log, "Phase 1 Power: " + pPhase1 + ", Voltage: " + this.voltageL1 + "V, Current: " + this.currentL1 + "A");
                this.logDebug(this.log, "Phase 2 Power: " + pPhase2 + ", Voltage: " + this.voltageL2 + "V, Current: " + this.currentL2 + "A");
                this.logDebug(this.log, "Phase 3 Power: " + pPhase3 + ", Voltage: " + this.voltageL3 + "V, Current: " + this.currentL3 + "A");

            } catch (OpenemsNamedException e) {
                this.logDebug(this.log, e.getMessage());
            }
        }

        this._setActivePower(gridPw);
        this.currentGridStatus = currentGridStatus;
    }

    private void calculateEnergy() {
        Integer activePower = this.getActivePower().orElse(null);
        if (activePower == null) {
            this.calculateProductionEnergy.update(null);
            this.calculateConsumptionEnergy.update(null);
        } else if (activePower > 0) {
            this.calculateProductionEnergy.update(activePower);
            this.calculateConsumptionEnergy.update(0);
        } else {
            this.calculateProductionEnergy.update(0);
            this.calculateConsumptionEnergy.update(-activePower);
        }
    }

    @Override
    public String debugLog() {
        return "L:" + this.getActivePower().asString() //
                + " |Status: " + this.currentGridStatus 
                + " |L1 Voltage: " + this.voltageL1 + "V, L1 Current: " + this.currentL1 + "A"
                + " |L2 Voltage: " + this.voltageL2 + "V, L2 Current: " + this.currentL2 + "A"
                + " |L3 Voltage: " + this.voltageL3 + "V, L3 Current: " + this.currentL3 + "A";
    }

    @Override
    public Timedata getTimedata() {
        return this.timedata;
    }

    @Override
    public MeterType getMeterType() {
        return this.config.type();
    }
}
