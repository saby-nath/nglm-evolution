/*****************************************
*
*  PointFulfillmentRequest.java
*
*****************************************/

package com.evolving.nglm.evolution;

import java.util.Date;
import java.util.HashMap;

import org.apache.kafka.connect.data.Field;
import org.apache.kafka.connect.data.Schema;
import org.apache.kafka.connect.data.SchemaAndValue;
import org.apache.kafka.connect.data.SchemaBuilder;
import org.apache.kafka.connect.data.Struct;
import org.apache.kafka.connect.data.Timestamp;
import org.json.simple.JSONObject;

import com.evolving.nglm.core.ConnectSerde;
import com.evolving.nglm.core.JSONUtilities;
import com.evolving.nglm.core.SchemaUtilities;
import com.evolving.nglm.evolution.CommodityDeliveryManager.CommodityDeliveryOperation;
import com.evolving.nglm.evolution.CommodityDeliveryManager.CommodityDeliveryRequest;
import com.evolving.nglm.evolution.EvolutionEngine.EvolutionEventContext;
import com.evolving.nglm.evolution.EvolutionUtilities.TimeUnit;

public class PointFulfillmentRequest extends BonusDelivery
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
    schemaBuilder.name("service_pointfulfillment_request");
    schemaBuilder.version(SchemaUtilities.packSchemaVersion(commonSchema().version(), 9));
    for (Field field : commonSchema().fields()) schemaBuilder.field(field.name(), field.schema());
    schemaBuilder.field("pointID", Schema.STRING_SCHEMA);
    schemaBuilder.field("pointName", Schema.OPTIONAL_STRING_SCHEMA);
    schemaBuilder.field("operation", Schema.STRING_SCHEMA);
    schemaBuilder.field("amount", Schema.OPTIONAL_INT32_SCHEMA);
    schemaBuilder.field("validityPeriodType", Schema.OPTIONAL_STRING_SCHEMA);
    schemaBuilder.field("validityPeriodQuantity", Schema.OPTIONAL_INT32_SCHEMA);
    schemaBuilder.field("deliverableExpirationDate", Timestamp.builder().optional().schema());
    schemaBuilder.field("expirationDateFrom", Timestamp.builder().optional().schema());
    schemaBuilder.field("expirationDateTo", Timestamp.builder().optional().schema());
    schema = schemaBuilder.build();
  };

  //
  //  serde
  //
      
  private static ConnectSerde<PointFulfillmentRequest> serde = new ConnectSerde<PointFulfillmentRequest>(schema, false, PointFulfillmentRequest.class, PointFulfillmentRequest::pack, PointFulfillmentRequest::unpack);

  //
  //  accessor
  //

  public static Schema schema() { return schema; }
  public static ConnectSerde<PointFulfillmentRequest> serde() { return serde; }
  public Schema subscriberStreamEventSchema() { return schema(); }
      
  /*****************************************
  *
  *  data
  *
  *****************************************/

  private String pointID;
  private String pointName;
  private CommodityDeliveryOperation operation;
  private int amount;
  private TimeUnit validityPeriodType;
  private Integer validityPeriodQuantity;
  private Date deliverableExpirationDate;
  private Date expirationDateFrom;
  private Date expirationDateTo;

  //
  //  accessors
  //

  public String getPointID() { return pointID; }
  public String getPointName() { return pointName; }
  public CommodityDeliveryOperation getOperation() { return operation; }
  public int getAmount() { return amount; }
  public TimeUnit getValidityPeriodType() { return validityPeriodType; }
  public Integer getValidityPeriodQuantity() { return validityPeriodQuantity; }
  public Date getDeliverableExpirationDate() { return deliverableExpirationDate; }
  public Date getExpirationDateFrom() { return expirationDateFrom; }
  public Date getExpirationDateTo() { return expirationDateTo; }

  //
  //  setters
  //  

  public void setValidityPeriodType(TimeUnit validityPeriodType) { this.validityPeriodType = validityPeriodType; }
  public void setValidityPeriodQuantity(Integer validityPeriodQuantity) { this.validityPeriodQuantity = validityPeriodQuantity; } 
  public void setDeliverableExpirationDate(Date deliverableExpirationDate) { this.deliverableExpirationDate = deliverableExpirationDate; } 

  //
  //  structure
  //

  @Override public ActivityType getActivityType() { return ActivityType.BDR; }
  @Override public Date getBonusDeliveryDeliverableExpirationDate() { return this.deliverableExpirationDate; }

  /*****************************************
  *
  *  constructor
  *
  *****************************************/

  public PointFulfillmentRequest(EvolutionEventContext context, String deliveryType, String deliveryRequestSource, String pointID, CommodityDeliveryOperation operation, int amount, TimeUnit validityPeriodType, Integer validityPeriodQuantity, int tenantID)
  {
    super(context, deliveryType, deliveryRequestSource, tenantID);
    this.pointID = pointID;
    this.operation = operation;
    this.amount = amount;
    this.validityPeriodType = validityPeriodType;
    this.validityPeriodQuantity = validityPeriodQuantity;
    this.deliverableExpirationDate = null;
  }

  /*****************************************
  *
  *  constructor -- external
  *
  *****************************************/

  public PointFulfillmentRequest(CommodityDeliveryRequest originatingRequest, JSONObject jsonRoot, DeliveryManagerDeclaration deliveryManager, int tenantID)
  {
    super(originatingRequest,jsonRoot, tenantID);
    this.pointID = JSONUtilities.decodeString(jsonRoot, "pointID", true);
    this.operation = CommodityDeliveryOperation.fromExternalRepresentation(JSONUtilities.decodeString(jsonRoot, "operation", true));
    this.amount = JSONUtilities.decodeInteger(jsonRoot, "amount", true);
    this.validityPeriodType = TimeUnit.fromExternalRepresentation(JSONUtilities.decodeString(jsonRoot, "validityPeriodType", false));
    this.validityPeriodQuantity = JSONUtilities.decodeInteger(jsonRoot, "validityPeriodQuantity", false);
    this.deliverableExpirationDate = originatingRequest.getDeliverableExpirationDate();
    this.expirationDateFrom = JSONUtilities.decodeDate(jsonRoot, "expirationDateFrom");
    this.expirationDateTo = JSONUtilities.decodeDate(jsonRoot, "expirationDateTo");
  }

  /*****************************************
  *
  *  constructor -- unpack
  *
  *****************************************/

  private PointFulfillmentRequest(SchemaAndValue schemaAndValue, String pointID, CommodityDeliveryOperation operation, int amount, TimeUnit validityPeriodType, Integer validityPeriodQuantity, Date deliverableExpirationDate, Date expirationDateFrom, Date expirationDateTo)
  {
    super(schemaAndValue);
    this.pointID = pointID;
    this.operation = operation;
    this.amount = amount;
    this.validityPeriodType = validityPeriodType;
    this.validityPeriodQuantity = validityPeriodQuantity;
    this.deliverableExpirationDate = deliverableExpirationDate;
    this.expirationDateFrom = expirationDateFrom;
    this.expirationDateTo = expirationDateTo;
  }

  /*****************************************
  *
  *  constructor -- copy
  *
  *****************************************/

  private PointFulfillmentRequest(PointFulfillmentRequest pointFulfillmentRequest)
  {
    super(pointFulfillmentRequest);
    this.pointID = pointFulfillmentRequest.getPointID();
    this.operation = pointFulfillmentRequest.getOperation();
    this.amount = pointFulfillmentRequest.getAmount();
    this.validityPeriodType = pointFulfillmentRequest.getValidityPeriodType();
    this.validityPeriodQuantity = pointFulfillmentRequest.getValidityPeriodQuantity();
    this.deliverableExpirationDate = pointFulfillmentRequest.getDeliverableExpirationDate();
    this.expirationDateFrom = pointFulfillmentRequest.getExpirationDateFrom();
    this.expirationDateTo = pointFulfillmentRequest.getExpirationDateTo();
  }

  /*****************************************
  *
  *  copy
  *
  *****************************************/

  public PointFulfillmentRequest copy()
  {
    return new PointFulfillmentRequest(this);
  }

  /*****************************************
  *
  *  pack
  *
  *****************************************/

  public static Object pack(Object value)
  {
    PointFulfillmentRequest pointFulfillmentRequest = (PointFulfillmentRequest) value;
    Struct struct = new Struct(schema);
    packCommon(struct, pointFulfillmentRequest);
    struct.put("pointID", pointFulfillmentRequest.getPointID());
    struct.put("pointName", pointFulfillmentRequest.getPointName());
    struct.put("operation", pointFulfillmentRequest.getOperation().getExternalRepresentation());
    struct.put("amount", pointFulfillmentRequest.getAmount());
    struct.put("validityPeriodType", pointFulfillmentRequest.getValidityPeriodType().getExternalRepresentation());
    struct.put("validityPeriodQuantity", pointFulfillmentRequest.getValidityPeriodQuantity());
    struct.put("deliverableExpirationDate", pointFulfillmentRequest.getDeliverableExpirationDate());
    struct.put("expirationDateFrom", pointFulfillmentRequest.getExpirationDateFrom());
    struct.put("expirationDateTo", pointFulfillmentRequest.getExpirationDateTo());
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

  public static PointFulfillmentRequest unpack(SchemaAndValue schemaAndValue)
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
    String pointID = valueStruct.getString("pointID");
    String pointName = (schemaVersion >= 2) ? valueStruct.getString("pointName") : "";
    CommodityDeliveryOperation operation = CommodityDeliveryOperation.fromExternalRepresentation(valueStruct.getString("operation"));
    int amount = valueStruct.getInt32("amount");
    TimeUnit validityPeriodType = TimeUnit.fromExternalRepresentation(valueStruct.getString("validityPeriodType"));
    Integer validityPeriodQuantity = valueStruct.getInt32("validityPeriodQuantity");
    Date deliverableExpirationDate = (Date) valueStruct.get("deliverableExpirationDate");
    Date expirationDateFrom = (schemaVersion >= 9) ? (Date) valueStruct.get("expirationDateFrom") : null;
    Date expirationDateTo = (schemaVersion >= 9) ? (Date) valueStruct.get("expirationDateTo") : null;

    //
    //  return
    //

    return new PointFulfillmentRequest(schemaAndValue, pointID, operation, amount, validityPeriodType, validityPeriodQuantity, deliverableExpirationDate, expirationDateFrom, expirationDateTo);
  }

  /****************************************
  *
  *  presentation utilities
  *
  ****************************************/
  
  @Override public void addFieldsForGUIPresentation(HashMap<String, Object> guiPresentationMap, SubscriberMessageTemplateService subscriberMessageTemplateService, SalesChannelService salesChannelService, JourneyService journeyService, OfferService offerService, LoyaltyProgramService loyaltyProgramService, ProductService productService, VoucherService voucherService, DeliverableService deliverableService, PaymentMeanService paymentMeanService, ResellerService resellerService, int tenantID)
  {
    guiPresentationMap.put(CUSTOMERID, getSubscriberID());
    guiPresentationMap.put(DELIVERABLEID, getPointID());
    guiPresentationMap.put(DELIVERABLEQTY, getAmount());
    guiPresentationMap.put(OPERATION, getOperation().toString());
    guiPresentationMap.put(VALIDITYPERIODTYPE, getValidityPeriodType().getExternalRepresentation());
    guiPresentationMap.put(VALIDITYPERIODQUANTITY, getValidityPeriodQuantity());
    guiPresentationMap.put(DELIVERABLEEXPIRATIONDATE, getDateString(getDeliverableExpirationDate()));
    guiPresentationMap.put(MODULEID, getModuleID());
    guiPresentationMap.put(MODULENAME, getModule().toString());
    guiPresentationMap.put(FEATUREID, getFeatureID());
    guiPresentationMap.put(FEATURENAME, getFeatureName(getModule(), getFeatureID(), journeyService, offerService, loyaltyProgramService));
    guiPresentationMap.put(FEATUREDISPLAY, getFeatureDisplay(getModule(), getFeatureID(), journeyService, offerService, loyaltyProgramService));
    guiPresentationMap.put(ORIGIN, "");
  }
  
  @Override public void addFieldsForThirdPartyPresentation(HashMap<String, Object> thirdPartyPresentationMap, SubscriberMessageTemplateService subscriberMessageTemplateService, SalesChannelService salesChannelService, JourneyService journeyService, OfferService offerService, LoyaltyProgramService loyaltyProgramService, ProductService productService, VoucherService voucherService, DeliverableService deliverableService, PaymentMeanService paymentMeanService, ResellerService resellerService, int tenantID)
  {
    thirdPartyPresentationMap.put(DELIVERABLEID, getPointID());
    thirdPartyPresentationMap.put(DELIVERABLEQTY, getAmount());
    thirdPartyPresentationMap.put(OPERATION, getOperation().toString());
    thirdPartyPresentationMap.put(VALIDITYPERIODTYPE, getValidityPeriodType().getExternalRepresentation());
    thirdPartyPresentationMap.put(VALIDITYPERIODQUANTITY, getValidityPeriodQuantity());
    thirdPartyPresentationMap.put(DELIVERABLEEXPIRATIONDATE, getDateString(getDeliverableExpirationDate()));
    thirdPartyPresentationMap.put(MODULEID, getModuleID());
    thirdPartyPresentationMap.put(MODULENAME, getModule().toString());
    thirdPartyPresentationMap.put(FEATUREID, getFeatureID());
    thirdPartyPresentationMap.put(FEATURENAME, getFeatureName(getModule(), getFeatureID(), journeyService, offerService, loyaltyProgramService));
    thirdPartyPresentationMap.put(FEATUREDISPLAY, getFeatureDisplay(getModule(), getFeatureID(), journeyService, offerService, loyaltyProgramService));
    thirdPartyPresentationMap.put(ORIGIN, "");
  }

  /*****************************************
  *  
  *  toString
  *
  *****************************************/

  public String toString()
  {
    StringBuilder b = new StringBuilder();
    b.append("PointFulfillmentRequest:{");
    b.append(super.toStringFields());
    b.append("," + getSubscriberID());
    b.append("," + pointID);
    b.append("," + pointName);
    b.append("," + operation);
    b.append("," + amount);
    b.append("," + validityPeriodType);
    b.append("," + validityPeriodQuantity);
    b.append("," + deliverableExpirationDate);
    b.append("}");
    return b.toString();
  }
  @Override
  public void resetDeliveryRequestAfterReSchedule()
  {
    // 
    // PointFulfillmentRequest never rescheduled, let return unchanged
    //  
    
  }
}
