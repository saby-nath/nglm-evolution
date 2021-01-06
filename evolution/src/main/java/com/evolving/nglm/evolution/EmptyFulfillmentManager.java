/*****************************************************************************
*
*  EmptyFulfillmentManager.java
*
*****************************************************************************/

package com.evolving.nglm.evolution;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;

import com.evolving.nglm.evolution.statistics.CounterStat;
import com.evolving.nglm.evolution.statistics.StatBuilder;
import com.evolving.nglm.evolution.statistics.StatsBuilders;
import org.apache.kafka.connect.data.Field;
import org.apache.kafka.connect.data.Schema;
import org.apache.kafka.connect.data.SchemaAndValue;
import org.apache.kafka.connect.data.SchemaBuilder;
import org.apache.kafka.connect.data.Struct;
import org.json.simple.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.evolving.nglm.core.ConnectSerde;
import com.evolving.nglm.core.JSONUtilities;
import com.evolving.nglm.core.SchemaUtilities;
import com.evolving.nglm.core.SystemTime;
import com.evolving.nglm.evolution.EvolutionEngine.EvolutionEventContext;
import com.evolving.nglm.evolution.GUIManager.GUIManagerException;
  
public class EmptyFulfillmentManager extends DeliveryManager implements Runnable
{
  /*****************************************
  *
  *  enum
  *
  *****************************************/

  //
  //  EmptyFulfillmentOperation
  //

  public enum EmptyFulfillmentOperation
  {
    Credit("credit"),
    Debit("debit"),
    Unknown("(unknown)");
    private String externalRepresentation;
    private EmptyFulfillmentOperation(String externalRepresentation) { this.externalRepresentation = externalRepresentation; }
    public String getExternalRepresentation() { return externalRepresentation; }
    public static EmptyFulfillmentOperation fromExternalRepresentation(String externalRepresentation) { for (EmptyFulfillmentOperation enumeratedValue : EmptyFulfillmentOperation.values()) { if (enumeratedValue.getExternalRepresentation().equals(externalRepresentation)) return enumeratedValue; } return Unknown; }
  }

  //
  //  EmptyFulfillmentStatus
  //

  public enum EmptyFulfillmentStatus
  {
    PENDING(10),
    SUCCESS(0),
    SYSTEM_ERROR(21),
    TIMEOUT(22),
    THROTTLING(23),
    CUSTOMER_NOT_FOUND(20),
    BONUS_NOT_FOUND(100),
    UNKNOWN(999);
    private Integer externalRepresentation;
    private EmptyFulfillmentStatus(Integer externalRepresentation) { this.externalRepresentation = externalRepresentation; }
    public Integer getExternalRepresentation() { return externalRepresentation; }
    public static EmptyFulfillmentStatus fromReturnCode(Integer externalRepresentation) { for (EmptyFulfillmentStatus enumeratedValue : EmptyFulfillmentStatus.values()) { if (enumeratedValue.getExternalRepresentation().equals(externalRepresentation)) return enumeratedValue; } return UNKNOWN; }
  }

  /*****************************************
  *
  *  conversion method
  *
  *****************************************/

  public DeliveryStatus getEmptyFulfillmentStatus (EmptyFulfillmentStatus status)
  {
    switch(status)
      {
        case PENDING:
          return DeliveryStatus.Pending;
        case SUCCESS:
          return DeliveryStatus.Delivered;
        case SYSTEM_ERROR:
        case CUSTOMER_NOT_FOUND:
        case BONUS_NOT_FOUND:
          return DeliveryStatus.Failed;
        case TIMEOUT:
        case THROTTLING:
          return DeliveryStatus.FailedRetry;
        default:
          return DeliveryStatus.Failed;
      }
  }

  /*****************************************
  *
  *  configuration
  *
  *****************************************/

  //
  //  logger
  //

  private static final Logger log = LoggerFactory.getLogger(EmptyFulfillmentManager.class);

  //
  //  number of threads
  //
  
  private int threadNumber = 5;   //TODO : make this configurable
  
  /*****************************************
  *
  *  data
  *
  *****************************************/

  private ArrayList<Thread> threads = new ArrayList<Thread>();
  private StatBuilder<CounterStat> statsCounter = null;
  
  /*****************************************
  *
  *  constructor
  *
  *****************************************/

  public EmptyFulfillmentManager(String deliveryManagerKey, String pluginName)
  {
    //
    //  superclass
    //
    
    super("deliverymanager-emptyfulfillment", deliveryManagerKey, Deployment.getBrokerServers(), EmptyFulfillmentRequest.serde(), Deployment.getDeliveryManagers().get(pluginName), 1);
    
    //
    // statistics
    //

    statsCounter = StatsBuilders.getEvolutionCounterStatisticsBuilder("emptyfulfillmentdelivery","emptyfulfillmentmanager-"+deliveryManagerKey);
    
    //
    //  threads
    //
    
    for(int i = 0; i < threadNumber; i++)
      {
        threads.add(new Thread(this, "EmptyFulfillmentManagerThread_"+i));
      }
    
    //
    //  startDelivery
    //
    
    startDelivery();

  }

  /*****************************************
  *
  *  class EmptyFulfillmentRequest
  *
  *****************************************/

  public static class EmptyFulfillmentRequest extends DeliveryRequest implements BonusDelivery
  {
    /*****************************************
    *
    *  schema
    *
    *****************************************/

    //
    //  schema
    //

    private static Schema schema = null;
    static
    {
      SchemaBuilder schemaBuilder = SchemaBuilder.struct();
      schemaBuilder.name("service_emptyfulfillment_request");
      schemaBuilder.version(SchemaUtilities.packSchemaVersion(commonSchema().version(),8));
      for (Field field : commonSchema().fields()) schemaBuilder.field(field.name(), field.schema());
      schemaBuilder.field("providerID", Schema.STRING_SCHEMA);
      schemaBuilder.field("commodityID", Schema.STRING_SCHEMA);
      schemaBuilder.field("commodityName", Schema.OPTIONAL_STRING_SCHEMA);
      schemaBuilder.field("operation", Schema.STRING_SCHEMA);
      schemaBuilder.field("amount", Schema.OPTIONAL_INT32_SCHEMA);
      schemaBuilder.field("return_code", Schema.INT32_SCHEMA);
      schema = schemaBuilder.build();
    };

    //
    //  serde
    //
        
    private static ConnectSerde<EmptyFulfillmentRequest> serde = new ConnectSerde<EmptyFulfillmentRequest>(schema, false, EmptyFulfillmentRequest.class, EmptyFulfillmentRequest::pack, EmptyFulfillmentRequest::unpack);

    //
    //  accessor
    //

    public static Schema schema() { return schema; }
    public static ConnectSerde<EmptyFulfillmentRequest> serde() { return serde; }
    public Schema subscriberStreamEventSchema() { return schema(); }
        
    /*****************************************
    *
    *  data
    *
    *****************************************/

//  private String deliveryRequestID;
//  private String deliveryRequestSource;
//  private String deliveryType;
//  private String eventID;
//  private String moduleID;
//  private String featureID;
//  private String subscriberID;
//  private boolean control;

    public String providerID;
    private String commodityID;
    private String commodityName;
    private EmptyFulfillmentOperation operation;
    private int amount;
    private int returnCode;
    private EmptyFulfillmentStatus status;
    private String returnCodeDetails;

    //
    //  accessors
    //

    public String getProviderID() { return providerID; }
    public String getCommodityID() { return commodityID; }
    public String getCommodityName() { return commodityName; }
    public EmptyFulfillmentOperation getOperation() { return operation; }
    public int getAmount() { return amount; }
    public Integer getReturnCode() { return returnCode; }
    public EmptyFulfillmentStatus getStatus() { return status; }
    public String getReturnCodeDetails() { return returnCodeDetails; }

    //
    //  setters
    //  

    public void setReturnCode(Integer returnCode) { this.returnCode = returnCode; }
    public void setStatus(EmptyFulfillmentStatus status) { this.status = status; }
    public void setReturnCodeDetails(String returnCodeDetails) { this.returnCodeDetails = returnCodeDetails; }

    //
    //  bonus delivery accessors
    //

    public int getBonusDeliveryReturnCode() { return getReturnCode() == null ? 0 : getReturnCode(); }
    public String getBonusDeliveryReturnCodeDetails() { return getReturnCodeDetails(); }
    public String getBonusDeliveryOrigin() { return ""; }
    public String getBonusDeliveryProviderId() { return getProviderID(); }
    public String getBonusDeliveryDeliverableId() { return getCommodityID(); }
    public String getBonusDeliveryDeliverableName() { return getCommodityName(); }
    public int getBonusDeliveryDeliverableQty() { return getAmount(); }
    public String getBonusDeliveryOperation() { return getOperation().getExternalRepresentation(); }

    /*****************************************
    *
    *  constructor
    *
    *****************************************/

    public EmptyFulfillmentRequest(EvolutionEventContext context, String deliveryType, String deliveryRequestSource, String providerID, String commodityID, EmptyFulfillmentOperation operation, int amount, int tenantID)
    {
      super(context, deliveryType, deliveryRequestSource, tenantID);
      this.providerID = providerID;
      this.commodityID = commodityID;
      this.operation = operation;
      this.amount = amount;
      this.status = EmptyFulfillmentStatus.PENDING;
      this.returnCode = EmptyFulfillmentStatus.PENDING.getExternalRepresentation();
      this.returnCodeDetails = "";
    }

    /*****************************************
    *
    *  constructor -- unpack
    *
    *****************************************/

    private EmptyFulfillmentRequest(SchemaAndValue schemaAndValue, String providerID, String commodityID, String commodityName, EmptyFulfillmentOperation operation, int amount, EmptyFulfillmentStatus status)
    {
      super(schemaAndValue);
      this.providerID = providerID;
      this.commodityID = commodityID;
      this.commodityName = commodityName;
      this.operation = operation;
      this.amount = amount;
      this.status = status;
      this.returnCode = status.getExternalRepresentation();
    }

    /*****************************************
    *
    *  constructor -- copy
    *
    *****************************************/

    private EmptyFulfillmentRequest(EmptyFulfillmentRequest emptyFulfillmentRequest)
    {
      super(emptyFulfillmentRequest);
      this.providerID = emptyFulfillmentRequest.getProviderID();
      this.commodityID = emptyFulfillmentRequest.getCommodityID();
      this.operation = emptyFulfillmentRequest.getOperation();
      this.amount = emptyFulfillmentRequest.getAmount();
      this.status = emptyFulfillmentRequest.getStatus();
      this.returnCode = emptyFulfillmentRequest.getReturnCode();
      this.returnCodeDetails = emptyFulfillmentRequest.getReturnCodeDetails();
    }

    /*****************************************
    *
    *  copy
    *
    *****************************************/

    public EmptyFulfillmentRequest copy()
    {
      return new EmptyFulfillmentRequest(this);
    }

    /*****************************************
    *
    *  pack
    *
    *****************************************/

    public static Object pack(Object value)
    {
      EmptyFulfillmentRequest emptyFulfillmentRequest = (EmptyFulfillmentRequest) value;
      Struct struct = new Struct(schema);
      packCommon(struct, emptyFulfillmentRequest);
      struct.put("providerID", emptyFulfillmentRequest.getProviderID());
      struct.put("commodityID", emptyFulfillmentRequest.getCommodityID());
      struct.put("commodityName", emptyFulfillmentRequest.getCommodityName());
      struct.put("operation", emptyFulfillmentRequest.getOperation().getExternalRepresentation());
      struct.put("amount", emptyFulfillmentRequest.getAmount());
      struct.put("return_code", emptyFulfillmentRequest.getReturnCode());
      return struct;
    }

    //
    //  subscriberStreamEventPack
    //

    public Object subscriberStreamEventPack(Object value) { return pack(value); }

    /*****************************************
    *
    *  unpack
    *
    *****************************************/

    public static EmptyFulfillmentRequest unpack(SchemaAndValue schemaAndValue)
    {
      //
      //  data
      //

      Schema schema = schemaAndValue.schema();
      Object value = schemaAndValue.value();
      Integer schemaVersion = (schema != null) ? SchemaUtilities.unpackSchemaVersion2(schema.version()) : null;

      //  unpack
      //

      Struct valueStruct = (Struct) value;
      String providerID = valueStruct.getString("providerID");
      String commodityID = valueStruct.getString("commodityID");
      String commodityName = (schemaVersion >= 2) ? valueStruct.getString("commodityName") : "";    
      EmptyFulfillmentOperation operation = EmptyFulfillmentOperation.fromExternalRepresentation(valueStruct.getString("operation"));
      int amount = valueStruct.getInt32("amount");
      Integer returnCode = valueStruct.getInt32("return_code");
      EmptyFulfillmentStatus status = EmptyFulfillmentStatus.fromReturnCode(returnCode);

      //
      //  return
      //

      return new EmptyFulfillmentRequest(schemaAndValue, providerID, commodityID, commodityName, operation, amount, status);
    }

    /*****************************************
    *  
    *  toString
    *
    *****************************************/

    public String toString()
    {
      StringBuilder b = new StringBuilder();
      b.append("EmptyFulfillmentRequest:{");
      b.append(super.toStringFields());
      b.append("," + getSubscriberID());
      b.append("," + providerID);
      b.append("," + commodityID);
      b.append("," + commodityName);
      b.append("," + operation);
      b.append("," + amount);
      b.append("," + returnCode);
      b.append("," + status.toString());
      b.append("}");
      return b.toString();
    }
    
    @Override public ActivityType getActivityType() { return ActivityType.BDR; }
    
    /****************************************
    *
    *  presentation utilities
    *
    ****************************************/
    
    @Override public void addFieldsForGUIPresentation(HashMap<String, Object> guiPresentationMap, SubscriberMessageTemplateService subscriberMessageTemplateService, SalesChannelService salesChannelService, JourneyService journeyService, OfferService offerService, LoyaltyProgramService loyaltyProgramService, ProductService productService, VoucherService voucherService, DeliverableService deliverableService, PaymentMeanService paymentMeanService, ResellerService resellerService)
    {
      guiPresentationMap.put(CUSTOMERID, getSubscriberID());
      guiPresentationMap.put(PROVIDERID, getProviderID());
      guiPresentationMap.put(PROVIDERNAME, Deployment.getFulfillmentProviders().get(getProviderID()).getProviderName());
      guiPresentationMap.put(DELIVERABLEID, getCommodityID());
      guiPresentationMap.put(DELIVERABLENAME, deliverableService.getActiveDeliverable(getCommodityID(), SystemTime.getCurrentTime()).getDeliverableName());
      guiPresentationMap.put(DELIVERABLEQTY, getAmount());
      guiPresentationMap.put(OPERATION, getOperation().getExternalRepresentation());
      guiPresentationMap.put(MODULEID, getModuleID());
      guiPresentationMap.put(MODULENAME, getModule().toString());
      guiPresentationMap.put(FEATUREID, getFeatureID());
      guiPresentationMap.put(FEATURENAME, getFeatureName(getModule(), getFeatureID(), journeyService, offerService, loyaltyProgramService));
      guiPresentationMap.put(FEATUREDISPLAY, getFeatureDisplay(getModule(), getFeatureID(), journeyService, offerService, loyaltyProgramService));
      guiPresentationMap.put(ORIGIN, "");
      guiPresentationMap.put(RETURNCODE, getReturnCode());
      guiPresentationMap.put(RETURNCODEDETAILS, getReturnCodeDetails());
    }
    
    @Override public void addFieldsForThirdPartyPresentation(HashMap<String, Object> thirdPartyPresentationMap, SubscriberMessageTemplateService subscriberMessageTemplateService, SalesChannelService salesChannelService, JourneyService journeyService, OfferService offerService, LoyaltyProgramService loyaltyProgramService, ProductService productService, VoucherService voucherService, DeliverableService deliverableService, PaymentMeanService paymentMeanService, ResellerService resellerService)
    {
      thirdPartyPresentationMap.put(PROVIDERID, getProviderID());
      thirdPartyPresentationMap.put(PROVIDERNAME, Deployment.getFulfillmentProviders().get(getProviderID()).getProviderName());
      thirdPartyPresentationMap.put(DELIVERABLEID, getCommodityID());
      thirdPartyPresentationMap.put(DELIVERABLENAME, deliverableService.getActiveDeliverable(getCommodityID(), SystemTime.getCurrentTime()).getDeliverableName());
      thirdPartyPresentationMap.put(DELIVERABLEQTY, getAmount());
      thirdPartyPresentationMap.put(OPERATION, getOperation().getExternalRepresentation());
      thirdPartyPresentationMap.put(MODULEID, getModuleID());
      thirdPartyPresentationMap.put(MODULENAME, getModule().toString());
      thirdPartyPresentationMap.put(FEATUREID, getFeatureID());
      thirdPartyPresentationMap.put(FEATURENAME, getFeatureName(getModule(), getFeatureID(), journeyService, offerService, loyaltyProgramService));
      thirdPartyPresentationMap.put(FEATUREDISPLAY, getFeatureDisplay(getModule(), getFeatureID(), journeyService, offerService, loyaltyProgramService));
      thirdPartyPresentationMap.put(ORIGIN, "");
      thirdPartyPresentationMap.put(RETURNCODE, getReturnCode());
      thirdPartyPresentationMap.put(RETURNCODEDESCRIPTION, RESTAPIGenericReturnCodes.fromGenericResponseCode(getReturnCode()).getGenericResponseMessage());
      thirdPartyPresentationMap.put(RETURNCODEDETAILS, getReturnCodeDetails());
    }
    @Override
    public void resetDeliveryRequestAfterReSchedule()
    {
      // 
      // EmptyFulfillmentRequest never rescheduled, let return unchanged
      //        
    }
  }

  /*****************************************
  *
  *  class ActionManager
  *
  *****************************************/

  public static class ActionManager extends com.evolving.nglm.evolution.ActionManager
  {
    /*****************************************
    *
    *  data
    *
    *****************************************/

    private String deliveryType;
    private EmptyFulfillmentOperation operation;
    
    /*****************************************
    *
    *  constructor
    *
    *****************************************/

    public ActionManager(JSONObject configuration) throws GUIManagerException
    {
      super(configuration);
      this.deliveryType = JSONUtilities.decodeString(configuration, "deliveryType", true);
      this.operation = EmptyFulfillmentOperation.fromExternalRepresentation(JSONUtilities.decodeString(configuration, "operation", true));
    }

    /*****************************************
    *
    *  execute
    *
    *****************************************/

    @Override public List<Action> executeOnEntry(EvolutionEventContext evolutionEventContext, SubscriberEvaluationRequest subscriberEvaluationRequest)
    {
      /*****************************************
      *
      *  parameters
      *
      *****************************************/

      String providerID = (String) CriterionFieldRetriever.getJourneyNodeParameter(subscriberEvaluationRequest,"node.parameter.providerid");
      String commodityID = (String) CriterionFieldRetriever.getJourneyNodeParameter(subscriberEvaluationRequest,"node.parameter.commodityid");
      int amount = ((Number) CriterionFieldRetriever.getJourneyNodeParameter(subscriberEvaluationRequest,"node.parameter.amount")).intValue();
      
      /*****************************************
      *
      *  request arguments
      *
      *****************************************/

      String deliveryRequestSource = subscriberEvaluationRequest.getJourneyState().getJourneyID();
      deliveryRequestSource = extractWorkflowFeatureID(evolutionEventContext, subscriberEvaluationRequest, deliveryRequestSource);
      
      EmptyFulfillmentOperation operation = this.operation;

      /*****************************************
      *
      *  request
      *
      *****************************************/

      EmptyFulfillmentRequest request = new EmptyFulfillmentRequest(evolutionEventContext, deliveryType, deliveryRequestSource, providerID, commodityID, operation, amount, subscriberEvaluationRequest.getTenantID());

      /*****************************************
      *
      *  return
      *
      *****************************************/

      return Collections.<Action>singletonList(request);
    }
  }

  /*****************************************
  *
  *  run
  *
  *****************************************/

  @Override
  public void run()
  {
    while (true)
      {
        /*****************************************
        *
        *  nextRequest
        *
        *****************************************/
        
        DeliveryRequest deliveryRequest = nextRequest();

        /*****************************************
        *
        *  call INPlugin
        *
        *****************************************/
        
        EmptyFulfillmentStatus status = null;
        EmptyFulfillmentOperation operation = ((EmptyFulfillmentRequest)deliveryRequest).getOperation();
        switch (operation) {
        case Credit:
          status = credit((EmptyFulfillmentRequest)deliveryRequest);
          break;
        case Debit:
          status = debit((EmptyFulfillmentRequest)deliveryRequest);
          break;
        default:
          break;
        }
        
        /*****************************************
        *
        *  update and return response
        *
        *****************************************/

        ((EmptyFulfillmentRequest)deliveryRequest).setStatus(status);
        ((EmptyFulfillmentRequest)deliveryRequest).setReturnCode(status.getExternalRepresentation());
        deliveryRequest.setDeliveryStatus(getEmptyFulfillmentStatus(status));
        deliveryRequest.setDeliveryDate(SystemTime.getCurrentTime());
        completeRequest(deliveryRequest);

        statsCounter.withLabel(StatsBuilders.LABEL.status.name(),((EmptyFulfillmentRequest) deliveryRequest).getStatus().name())
                .withLabel(StatsBuilders.LABEL.operation.name(),((EmptyFulfillmentRequest) deliveryRequest).getOperation().name())
                .withLabel(StatsBuilders.LABEL.module.name(), deliveryRequest.getModule().name())
                .getStats().increment();

      }
  }

  /*****************************************
  *
  *  credit
  *
  *****************************************/

  private EmptyFulfillmentStatus credit(EmptyFulfillmentRequest emptyFulfillmentRequest)
  {
    log.info("EmptyFulfillmentManager.credit("+emptyFulfillmentRequest+") : called ... return succes ...");
    return EmptyFulfillmentStatus.SUCCESS;
  }
  
  /*****************************************
  *
  *  debit
  *
  *****************************************/

  private EmptyFulfillmentStatus debit(EmptyFulfillmentRequest emptyFulfillmentRequest)
  {
    log.info("EmptyFulfillmentManager.debit("+emptyFulfillmentRequest+") : called ... return succes ...");
    return EmptyFulfillmentStatus.SUCCESS;
  }

  /*****************************************
  *
  *  processCorrelatorUpdate
  *
  *****************************************/

  @Override protected void processCorrelatorUpdate(DeliveryRequest deliveryRequest, JSONObject correlatorUpdate)
  {
    log.info("EmptyFulfillmentManager.processCorrelatorUpdate("+deliveryRequest.getDeliveryRequestID()+", "+correlatorUpdate+") : called ...");

    int result = JSONUtilities.decodeInteger(correlatorUpdate, "result", true);
    EmptyFulfillmentRequest emptyFulfillmentRequest = (EmptyFulfillmentRequest) deliveryRequest;
    if (emptyFulfillmentRequest != null)
      {
        emptyFulfillmentRequest.setStatus(EmptyFulfillmentStatus.fromReturnCode(result));
        emptyFulfillmentRequest.setDeliveryStatus(getEmptyFulfillmentStatus(emptyFulfillmentRequest.getStatus()));
        emptyFulfillmentRequest.setDeliveryDate(SystemTime.getCurrentTime());
        completeRequest(emptyFulfillmentRequest);
      }
  
    log.debug("EmptyFulfillmentManager.processCorrelatorUpdate("+deliveryRequest.getDeliveryRequestID()+", "+correlatorUpdate+") : DONE");
  }

  
  /*****************************************
  *
  *  shutdown
  *
  *****************************************/

  @Override protected void shutdown()
  {
    log.info("EmptyFulfillmentManager:  shutdown");
  }
  
  /*****************************************
  *
  *  main
  *
  *****************************************/

  public static void main(String[] args)
  {
    log.info("EmptyFulfillmentManager: recieved " + args.length + " args");
    for(String arg : args){
      log.info("EmptyFulfillmentManager: arg " + arg);
    }
    
    //
    //  configuration
    //

    String deliveryManagerKey = args[0];
    String pluginName = args[1];

    //
    //  instance  
    //
    
    log.info("Configuration " + Deployment.getDeliveryManagers());

    
    EmptyFulfillmentManager manager = new EmptyFulfillmentManager(deliveryManagerKey, pluginName);

    //
    //  run
    //

    manager.run();
  }
}
