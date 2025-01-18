package io.openems.edge.ess.fronius.charger;

import static io.openems.common.utils.JsonUtils.getAsFloat;
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
import io.openems.edge.meter.api.ElectricityMeter;
import io.openems.edge.pvinverter.api.ManagedSymmetricPvInverter;
import io.openems.edge.timedata.api.Timedata;
import io.openems.edge.timedata.api.TimedataProvider;
import io.openems.edge.timedata.api.utils.CalculateEnergyFromPower;

@Designate(ocd = Config.class, factory = true)
@Component(
    name = "Fronius.ESS.Charger",
    immediate = true,
    configurationPolicy = ConfigurationPolicy.REQUIRE,
    property = {
        "type=PRODUCTION"
    }
)
@EventTopics({
    EdgeEventConstants.TOPIC_CYCLE_AFTER_PROCESS_IMAGE,
})
public class FroniusEssChargerImpl extends AbstractOpenemsComponent implements FroniusEssCharger, ElectricityMeter,
        OpenemsComponent, EventHandler, TimedataProvider, ManagedSymmetricPvInverter {

    private final Logger log = LoggerFactory.getLogger(FroniusEssChargerImpl.class);
    private final CalculateEnergyFromPower calculateActualEnergy = new CalculateEnergyFromPower(this,
            ElectricityMeter.ChannelId.ACTIVE_PRODUCTION_ENERGY);

    @Reference
    protected ConfigurationAdmin cm;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    private BridgeHttpFactory httpBridgeFactory;
    private BridgeHttp httpBridge;

    @Reference(policy = ReferencePolicy.DYNAMIC, policyOption = ReferencePolicyOption.GREEDY, cardinality = ReferenceCardinality.OPTIONAL)
    private volatile Timedata timedata;

    private String baseUrl;
    private Config config;

    public FroniusEssChargerImpl() {
        super(
            OpenemsComponent.ChannelId.values(),
            ElectricityMeter.ChannelId.values(),
            FroniusEssCharger.ChannelId.values(),
            ManagedSymmetricPvInverter.ChannelId.values()
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

        // Fronius API URL für Echtzeitdaten
        this.httpBridge.subscribeJsonEveryCycle(this.baseUrl + "/solar_api/v1/GetPowerFlowRealtimeData.fcgi",
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
        case EdgeEventConstants.TOPIC_CYCLE_AFTER_PROCESS_IMAGE:
            this.calculateEnergy();
            break;
        }
    }

    private void fetchAndUpdateEssRealtimeStatus(HttpResponse<JsonElement> result, HttpError error) {
        Integer pvPower = null;

        if (error != null) {
            this.logDebug(this.log, error.getMessage());

        } else {
            try {
            	var response = result.data().getAsJsonObject();
                var data = response.getAsJsonObject("Body").getAsJsonObject("Data");
                
                // Zugriff auf die Inverter-Daten und die Site-Daten
                var site = data.getAsJsonObject("Site");
                
                // Fronius API: PV-Leistung auslesen
                pvPower = round(getAsFloat(site, "P_PV"));

                this.log.info("PV Power from API: " + pvPower + " W");

            } catch (OpenemsNamedException e) {
                this.log.debug("Exception while processing ESS realtime status: " + e.getMessage());
            }
        }

        // Setzt die aktive Leistung (PV-Produktion)
        this._setActivePower(pvPower);
    }

    /**
     * Berechnet die Energie aus der aktiven Leistung.
     */
    private void calculateEnergy() {
        var actualPower = this.getActivePower().get();
        if (actualPower == null) {
            this.calculateActualEnergy.update(null);
        } else if (actualPower > 0) {
            this.calculateActualEnergy.update(actualPower);
        } else {
            this.calculateActualEnergy.update(0);
        }
    }

    @Override
    public String debugLog() {
        return "PV Production Power: " + this.getActivePower().asString();
    }

    @Override
    public Timedata getTimedata() {
        return this.timedata;
    }

    @Override
    public MeterType getMeterType() {
        return MeterType.PRODUCTION;
    }
}
