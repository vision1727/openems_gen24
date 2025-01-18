package io.openems.edge.ess.fronius.ess;

import static io.openems.common.utils.JsonUtils.getAsFloat;
import static java.lang.Math.round;


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

import io.openems.edge.bridge.modbus.api.task.FC3ReadRegistersTask;

import org.osgi.service.cm.ConfigurationAdmin;


import io.openems.common.exceptions.OpenemsError.OpenemsNamedException;
import io.openems.common.exceptions.OpenemsException;
import io.openems.edge.bridge.modbus.api.AbstractOpenemsModbusComponent;
import io.openems.edge.bridge.modbus.api.BridgeModbus;
import io.openems.edge.bridge.modbus.api.ModbusComponent;
import io.openems.edge.bridge.modbus.api.ModbusProtocol;

import io.openems.edge.bridge.modbus.api.task.FC16WriteRegistersTask;
import io.openems.edge.common.channel.EnumWriteChannel;
import io.openems.edge.common.channel.IntegerWriteChannel;
import io.openems.edge.common.component.OpenemsComponent;
import io.openems.edge.common.sum.GridMode;
import io.openems.edge.common.taskmanager.Priority;
import io.openems.edge.ess.api.AsymmetricEss;
import io.openems.edge.ess.api.ManagedAsymmetricEss;
import io.openems.edge.ess.api.ManagedSinglePhaseEss;
import io.openems.edge.ess.api.ManagedSymmetricEss;
import io.openems.edge.ess.api.SinglePhase;
import io.openems.edge.ess.api.SinglePhaseEss;
import io.openems.edge.ess.api.SymmetricEss;
import io.openems.edge.ess.power.api.Phase;
import io.openems.edge.ess.power.api.Power;
import com.google.gson.JsonElement;
import io.openems.edge.bridge.http.api.BridgeHttp;
import io.openems.edge.bridge.http.api.BridgeHttpFactory;
import io.openems.edge.bridge.http.api.HttpError;
import io.openems.edge.bridge.http.api.HttpResponse;
import io.openems.edge.bridge.modbus.api.element.SignedWordElement;
import io.openems.edge.bridge.modbus.api.element.UnsignedWordElement;
import io.openems.edge.common.event.EdgeEventConstants;
import io.openems.edge.ess.dccharger.api.EssDcCharger;
import io.openems.edge.timedata.api.Timedata;
import io.openems.edge.timedata.api.TimedataProvider;
import io.openems.edge.timedata.api.utils.CalculateEnergyFromPower;
import io.openems.edge.ess.fronius.ess.FroniusEssImpl;
import io.openems.edge.Fronius.enums.SetControlMode;


@Designate(ocd = Config.class, factory = true)
@Component(
        name = "Fronius.ESS",
        immediate = true,
        configurationPolicy = ConfigurationPolicy.REQUIRE
)
@EventTopics({
        EdgeEventConstants.TOPIC_CYCLE_BEFORE_PROCESS_IMAGE,
})
public class FroniusEssImpl extends AbstractOpenemsModbusComponent
        implements  SinglePhaseEss, ManagedAsymmetricEss, AsymmetricEss, ManagedSymmetricEss,
		SymmetricEss, ModbusComponent, ManagedSinglePhaseEss,  OpenemsComponent, EventHandler, TimedataProvider/*, HybridEss  */{
	@Reference
	private Power power;
	
	
	
    private final Logger log = LoggerFactory.getLogger(FroniusEssImpl.class);
    private final CalculateEnergyFromPower calculateAcChargeEnergy = new CalculateEnergyFromPower(this,
            SymmetricEss.ChannelId.ACTIVE_CHARGE_ENERGY);
    private final CalculateEnergyFromPower calculateAcDischargeEnergy = new CalculateEnergyFromPower(this,
            SymmetricEss.ChannelId.ACTIVE_DISCHARGE_ENERGY);
  
	protected static final int MAX_APPARENT_POWER = 10000; 
	protected static final int MAX_Charge_power = -9200;
	protected static final int MAX_Discharge_power = 9200;
	
	

    @Reference(policy = ReferencePolicy.DYNAMIC, policyOption = ReferencePolicyOption.GREEDY, cardinality = ReferenceCardinality.OPTIONAL)
    private volatile Timedata timedata = null;
    @Reference
    private ConfigurationAdmin cm;
    
    @Override
    @Reference(policy = ReferencePolicy.STATIC, policyOption = ReferencePolicyOption.GREEDY, cardinality = ReferenceCardinality.MANDATORY)
	
	protected void setModbus(BridgeModbus modbus) {
		super.setModbus(modbus);
	}
	private SinglePhase singlePhase = null;
	private Config config;
    
    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    private BridgeHttpFactory httpBridgeFactory;
    private BridgeHttp httpBridge;

    private String baseUrl;
    private Integer latestPakku = 0;
    private Integer latestGridPw = 0;
 //   private Integer latestPvPw = 0;
    private Integer latestPcsPw = 0;
    private Integer latestConsPw = 0;
    private Integer latestBatteryStatus = -1;
    private Integer latestGridStatus = -1;
    private Integer setActivePowerChannel;
    private Integer myActivePower = 0;
    private Integer dcw_3 = 0;
    private Integer dcw_4 = 0;
    private Integer dcwsf = 0;
    private Integer myactivepower_ =0;
 //   private Integer SOC = this.channel(SymmetricEss.ChannelId.SOC);

    public FroniusEssImpl() {
        super(
                OpenemsComponent.ChannelId.values(),
                SymmetricEss.ChannelId.values(),
                SinglePhaseEss.ChannelId.values(),
                FroniusEss.ChannelId.values(),
				ManagedSymmetricEss.ChannelId.values(),//
				ManagedSinglePhaseEss.ChannelId.values(), //
				AsymmetricEss.ChannelId.values(), //
				ManagedAsymmetricEss.ChannelId.values(), //
                EssDcCharger.ChannelId.values(),
				ModbusComponent.ChannelId.values() //
           
        );
        
    }

    @Activate
    private void activate(ComponentContext context, Config config) throws OpenemsException {
        super.activate(context, config.id(), config.alias(), config.enabled(), config.modbusUnitId(), this.cm,
    			"Modbus", config.modbus_id()); 
    		
        this.baseUrl = "http://" + config.ip();
        this.httpBridge = this.httpBridgeFactory.get();
       this._setCapacity(config.capacity());
        this._setGridMode(GridMode.ON_GRID); // Keine Backup-FunktionalitÃƒÂ¤t
        this._setAllowedChargePower(MAX_Charge_power);
    	this._setAllowedDischargePower(MAX_Discharge_power);
    		this._setMaxApparentPower(MAX_APPARENT_POWER);

        this.config = config;

        switch (config.phase()) {
		case ALL:
			this.singlePhase = null;
			break;
		case L1:
			this.singlePhase = SinglePhase.L1;
			break;
		case L2:
			this.singlePhase = SinglePhase.L2;
			break;
		case L3:
			this.singlePhase = SinglePhase.L3;
			break;
		}

		if (this.singlePhase != null) {
			SinglePhaseEss.initializeCopyPhaseChannel(this, this.singlePhase);
		}
        
        if (!this.isEnabled()) {
            return;
        }

        
        this.httpBridge.subscribeJsonEveryCycle(this.baseUrl + "/solar_api/v1/GetPowerFlowRealtimeData.fcgi",
                this::fetchAndUpdateFroniusRealtimeData);
    }

    @Override
    @Deactivate
    protected void deactivate() {
        this.httpBridgeFactory.unget(this.httpBridge);
        this.httpBridge = null;
        super.deactivate();
    }
    
    @Override
    public void applyPower(int activePower, int reactivePower) throws OpenemsNamedException {
        log.info("applyPower(int activePower, int reactivePower) called with activePower={}, reactivePower={}", activePower, reactivePower);

        if (this.config.readOnlyMode()) {
            log.warn("applyPower skipped: readOnlyMode is enabled.");
            return;
        }
        
        	
        EnumWriteChannel setControlMode = this.channel(FroniusEss.ChannelId.SET_CONTROL_MODE) ;
        IntegerWriteChannel setActivePowerChannel = this.channel(FroniusEss.ChannelId.SET_ACTIVE_POWER);
      IntegerWriteChannel setActivePowerChannel2 = this.channel(FroniusEss.ChannelId.SET_ACTIVE_POWER2);

      setControlMode.setNextWriteValue(SetControlMode.START);
        setActivePowerChannel.setNextWriteValue(activePower);
      setActivePowerChannel2.setNextWriteValue(activePower * (-1) );
     
            }

    @Override
    public void applyPower(int activePowerL1, int reactivePowerL1, int activePowerL2, int reactivePowerL2,
                           int activePowerL3, int reactivePowerL3) throws OpenemsNamedException {
        log.info("applyPower(int activePowerL1, int reactivePowerL1, int activePowerL2, int reactivePowerL2, int activePowerL3, int reactivePowerL3) called with values: activePowerL1={}, reactivePowerL1={}, activePowerL2={}, reactivePowerL2={}, activePowerL3={}, reactivePowerL3={}",
                 activePowerL1, reactivePowerL1, activePowerL2, reactivePowerL2, activePowerL3, reactivePowerL3);

        if (this.config.phase() == Phase.ALL) {
            log.warn("applyPower skipped: config.phase() is Phase.ALL.");
            return;
        }

        // Call parent method or handle logic
        ManagedSinglePhaseEss.super.applyPower(activePowerL1, reactivePowerL1, activePowerL2, reactivePowerL2,
                                               activePowerL3, reactivePowerL3);

        log.info("applyPower successfully applied power for all phases.");
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

    private void fetchAndUpdateFroniusRealtimeData(HttpResponse<JsonElement> result, HttpError error) {
     //   Integer pvPw = null;
        Integer pcsPw = null;
        Integer gridPw = null;
        Integer consPw = null;
        Integer batteryStatus = null;
        Integer gridStatus = null;
        Integer soc = null;
        Integer pakku = null;

        if (error != null) {
            this.logDebug(this.log, error.getMessage());
        } else {
            try {
                var response = result.data().getAsJsonObject();
                var data = response.getAsJsonObject("Body").getAsJsonObject("Data");
                
                // Zugriff auf die Inverter-Daten und die Site-Daten
                var inverter = data.getAsJsonObject("Inverters").getAsJsonObject("1");
                var site = data.getAsJsonObject("Site");

                // Werte extrahieren
          //      pvPw = round(getAsFloat(site, "P_PV"));
                pcsPw = round(getAsFloat(inverter, "P"));
                gridPw = round(getAsFloat(site, "P_Grid"));
                consPw = round(getAsFloat(site, "P_Load"));
                soc = round(getAsFloat(inverter, "SOC")); // SoC-Wert aus Inverter
                pakku = round(getAsFloat(site, "P_Akku"));
                batteryStatus = inverter.get("Battery_Mode").getAsString().equals("normal") ? 1 : 0;
                gridStatus = site.get("Mode").getAsString().equals("bidirectional") ? 1 : 0;

            } catch (OpenemsNamedException e) {
                this.logDebug(this.log, e.getMessage());
            }
        }

        
        this._setSoc(soc); // Set the State of Charge (SOC)

        // Update the latest values
//        this.latestPvPw = pvPw;
        this.latestPcsPw = pcsPw;
        this.latestGridPw = gridPw;
        this.latestConsPw = consPw;
        this.latestBatteryStatus = batteryStatus;
        this.latestGridStatus = gridStatus;
        this.latestPakku = pakku;
        

        // Log SOC-Wert
        this.log.info("Aktueller SoC: " + soc + "%");
        
        
        
        this._setActivePower(((latestPakku) ));
        this._setActivePowerL1(((latestPakku))/(3));
        this._setActivePowerL2(((latestPakku))/(3));
        this._setActivePowerL3(((latestPakku))/(3));
        this._setReactivePower(0);   
        this._setReactivePowerL1(0);
        this._setReactivePowerL2(0);
        this._setReactivePowerL3(0);}

    
    

    @Override
    public Timedata getTimedata() {
        return this.timedata;
    }
  
    private void calculateEnergy() {
        var activePower = this.getActivePowerChannel().getNextValue().get();
        if (activePower == null) {
            this.calculateAcChargeEnergy.update(null);
            this.calculateAcDischargeEnergy.update(null);

        } else {
            if (activePower > 0) {
                this.calculateAcDischargeEnergy.update(activePower);
            } else {
                this.calculateAcChargeEnergy.update(activePower);
            }
        }
    }

@Override
protected ModbusProtocol defineModbusProtocol() {
	return new ModbusProtocol(this, //
			
			new FC3ReadRegistersTask(40101, Priority.HIGH, //
					
					m(FroniusEss.ChannelId.DCW_SF, new SignedWordElement(40101))),
			new FC3ReadRegistersTask(40314, Priority.HIGH, //
					
					m(FroniusEss.ChannelId.DCW3, new SignedWordElement(40314))),
			
			new FC3ReadRegistersTask(40315, Priority.HIGH, //
					
					m(FroniusEss.ChannelId.DCW4, new SignedWordElement(40315))),
			
			new FC16WriteRegistersTask(40348,
					m(FroniusEss.ChannelId.SET_CONTROL_MODE, new UnsignedWordElement(40348))),
			new FC16WriteRegistersTask(40356, //
					m(FroniusEss.ChannelId.SET_ACTIVE_POWER2, new SignedWordElement(40356))), //
			new FC16WriteRegistersTask(40355, 
			m(FroniusEss.ChannelId.SET_ACTIVE_POWER, new SignedWordElement(40355))),
			new FC16WriteRegistersTask(40356, //
					m(FroniusEss.ChannelId.SET_ACTIVE_POWER2, new SignedWordElement(40356))), //
			new FC16WriteRegistersTask(40355, 
			m(FroniusEss.ChannelId.SET_ACTIVE_POWER, new SignedWordElement(40355))),
			new FC16WriteRegistersTask(40356, //
					m(FroniusEss.ChannelId.SET_ACTIVE_POWER2, new SignedWordElement(40356))), //
			new FC16WriteRegistersTask(40355, 
			m(FroniusEss.ChannelId.SET_ACTIVE_POWER, new SignedWordElement(40355))));
	
}


@Override
public String debugLog() {
	return "SoC:" + this.getSoc().asString() //
			+ "|L:" + this.getActivePower().asString() //
			+ "|Allowed:" + this.getAllowedChargePower().asStringWithoutUnit() + ";" //
			+ this.getAllowedDischargePower().asString() //
			+ "|" + this.getGridModeChannel().value().asOptionString()
			+ 
	 "/" + this.getMaxApparentPower().asString();
	}

@Override
public Power getPower() {
	return this.power;
}

@Override
public int getPowerPrecision() {
	return 1;
}

@Override
public SinglePhase getPhase() {
	return this.singlePhase;
}

}


