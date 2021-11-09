package com.evolving.nglm.evolution;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.TimeZone;

import org.apache.kafka.connect.data.Field;
import org.apache.kafka.connect.data.Schema;
import org.apache.kafka.connect.data.SchemaAndValue;
import org.apache.kafka.connect.data.SchemaBuilder;
import org.apache.kafka.connect.data.Struct;

import com.evolving.nglm.core.ConnectSerde;
import com.evolving.nglm.core.Deployment;
import com.evolving.nglm.core.RLMDateUtils;
import com.evolving.nglm.core.SchemaUtilities;
import com.evolving.nglm.core.SubscriberStreamOutput;
import com.evolving.nglm.evolution.ActionManager.Action;
import com.evolving.nglm.evolution.ActionManager.ActionType;
import com.evolving.nglm.evolution.Badge.BadgeAction;
import com.evolving.nglm.evolution.DeliveryRequest.Module;

public class BadgeChange extends SubscriberStreamOutput implements EvolutionEngineEvent, Action
{
  
  private static Schema schema = null;
  static
    {
      SchemaBuilder schemaBuilder = SchemaBuilder.struct();
      schemaBuilder.name("badge_change");
      schemaBuilder.version(SchemaUtilities.packSchemaVersion(subscriberStreamOutputSchema().version(), 1));
      for (Field field : subscriberStreamOutputSchema().fields())
        {
          schemaBuilder.field(field.name(), field.schema());
        }
      schemaBuilder.field("subscriberID", Schema.STRING_SCHEMA);
      schemaBuilder.field("deliveryRequestID", Schema.STRING_SCHEMA);
      schemaBuilder.field("action", Schema.STRING_SCHEMA);
      schemaBuilder.field("badgeID", Schema.STRING_SCHEMA);
      schemaBuilder.field("moduleID", Schema.STRING_SCHEMA);
      schemaBuilder.field("featureID", Schema.STRING_SCHEMA);
      schemaBuilder.field("origin", Schema.OPTIONAL_STRING_SCHEMA);
      schemaBuilder.field("returnStatus", Schema.OPTIONAL_STRING_SCHEMA);
      schemaBuilder.field("tenantID", Schema.INT16_SCHEMA);
      schemaBuilder.field("infos", ParameterMap.schema());
      schemaBuilder.field("responseEvent", Schema.BOOLEAN_SCHEMA);
      schema = schemaBuilder.build();
    }
  
  private static ConnectSerde<BadgeChange> serde = new ConnectSerde<BadgeChange>(schema, false, BadgeChange.class, BadgeChange::pack, BadgeChange::unpack);
  public static Schema schema() { return schema; }
  public static ConnectSerde<BadgeChange> serde() { return serde; }
  
  private String subscriberID;
  private String deliveryRequestID;
  private BadgeAction action;
  private String badgeID;
  private String moduleID;
  private String featureID;
  private String origin;
  private RESTAPIGenericReturnCodes returnStatus;
  private int tenantID;
  private ParameterMap infos;
  private boolean responseEvent;
  
  @Override public String getSubscriberID()
  {
    return subscriberID;
  }
  
  public String getDeliveryRequestID()
  {
    return deliveryRequestID;
  }

  @Override public String getEventName()
  {
    if (responseEvent)
      {
        return "Badge Change Result";
      }
    else
      {
        return "Badge Change Request";
      }
  }
  
  public BadgeAction getAction()
  {
    return action;
  }
  public String getBadgeID()
  {
    return badgeID;
  }
  public String getModuleID()
  {
    return moduleID;
  }
  public String getFeatureID()
  {
    return featureID;
  }
  public String getOrigin()
  {
    return origin;
  }
  public RESTAPIGenericReturnCodes getReturnStatus()
  {
    return returnStatus;
  }
  public void setReturnStatus(RESTAPIGenericReturnCodes returnStatus)
  {
    this.returnStatus = returnStatus;
  }
  public int getTenantID()
  {
    return tenantID;
  }
  public ParameterMap getInfos() { return infos; }
  public boolean IsResponseEvent() { return responseEvent; }
  public void changeToBadgeChangeResponse() { this.responseEvent = true; }
  
  @Override
  public Schema subscriberStreamEventSchema()
  {
    return schema;
  }
  @Override
  public Object subscriberStreamEventPack(Object value)
  {
    return pack(value);
  }
  
  public static Object pack(Object value)
  {
    BadgeChange badgeChange = (BadgeChange) value;
    Struct struct = new Struct(schema);
    packSubscriberStreamOutput(struct, badgeChange);
    struct.put("subscriberID", badgeChange.getSubscriberID());
    struct.put("deliveryRequestID", badgeChange.getDeliveryRequestID());
    struct.put("action", badgeChange.getAction().getExternalRepresentation());
    struct.put("badgeID", badgeChange.getBadgeID());
    struct.put("moduleID", badgeChange.getModuleID());
    struct.put("featureID", badgeChange.getFeatureID());
    struct.put("origin", badgeChange.getOrigin());
    struct.put("returnStatus", badgeChange.getReturnStatus().getGenericResponseMessage());
    struct.put("tenantID", (short) badgeChange.getTenantID());
    struct.put("infos", ParameterMap.pack(badgeChange.getInfos()));
    struct.put("responseEvent", badgeChange.IsResponseEvent());
    return struct;
  }
  
  public static BadgeChange unpack(SchemaAndValue schemaAndValue)
  {
    Schema schema = schemaAndValue.schema();
    Object value = schemaAndValue.value();
    Integer schemaVersion = (schema != null) ? SchemaUtilities.unpackSchemaVersion1(schema.version()) : null;
    Struct valueStruct = (Struct) value;
    String subscriberID = valueStruct.getString("subscriberID");
    String deliveryRequestID = valueStruct.getString("deliveryRequestID");
    BadgeAction action = BadgeAction.fromExternalRepresentation(valueStruct.getString("action"));
    String badgeID = valueStruct.getString("badgeID");
    String moduleID = valueStruct.getString("moduleID");
    String featureID = valueStruct.getString("featureID");
    String origin = valueStruct.getString("origin");
    RESTAPIGenericReturnCodes returnStatus = RESTAPIGenericReturnCodes.fromGenericResponseMessage(valueStruct.getString("returnStatus"));
    int tenantID = valueStruct.getInt16("tenantID");
    ParameterMap infos = ParameterMap.unpack(new SchemaAndValue(schema.field("infos").schema(), valueStruct.get("infos")));
    boolean responseEvent = valueStruct.getBoolean("responseEvent");
    
    return new BadgeChange(schemaAndValue, subscriberID, deliveryRequestID, action, badgeID, moduleID, featureID, origin, returnStatus, tenantID, infos, responseEvent);
  }
  
  public BadgeChange(SchemaAndValue schemaAndValue, String subscriberID, String deliveryRequestID, BadgeAction action, String badgeID, String moduleID, String featureID, String origin, RESTAPIGenericReturnCodes returnStatus, int tenantID, ParameterMap infos, boolean responseEvent)
  {
    super(schemaAndValue);
    this.subscriberID = subscriberID;
    this.deliveryRequestID = deliveryRequestID;
    this.action = action;
    this.badgeID = badgeID;
    this.moduleID = moduleID;
    this.featureID = featureID;
    this.origin = origin;
    this.returnStatus = returnStatus;
    this.tenantID = tenantID;
    this.infos = infos;
    this.responseEvent =responseEvent;
  }
  
  public BadgeChange(BadgeChange badgeChange)
  {
    super(badgeChange);
    this.subscriberID = badgeChange.getSubscriberID();
    this.deliveryRequestID = badgeChange.getDeliveryRequestID();
    this.action = badgeChange.getAction();
    this.badgeID = badgeChange.getBadgeID();
    this.moduleID = badgeChange.getModuleID();
    this.featureID = badgeChange.getFeatureID();
    this.origin = badgeChange.getOrigin();
    this.returnStatus = badgeChange.getReturnStatus();
    this.tenantID = badgeChange.getTenantID();
    this.infos = badgeChange.getInfos();
    this.responseEvent = badgeChange.IsResponseEvent();
  }
  
  
  public BadgeChange(String subscriberID, String deliveryRequestID, String eventID, BadgeAction action, String badgeID, String moduleID, String featureID, String origin, RESTAPIGenericReturnCodes returnStatus, int tenantID, ParameterMap infos)
  {
    super();
    this.subscriberID = subscriberID;
    this.deliveryRequestID = deliveryRequestID;
    this.setEventID(eventID);
    this.action = action;
    this.badgeID = badgeID;
    this.moduleID = moduleID;
    this.featureID = featureID;
    this.origin = origin;
    this.returnStatus = returnStatus;
    this.tenantID = tenantID;
    this.infos = infos;
  }
  
  //
  //  ES
  //
  
  public BadgeChange(Map<String, Object> esFields)
  {
    super();
    this.subscriberID = (String) esFields.get("subscriberID");
    this.deliveryRequestID = (String) esFields.get("deliveryRequestID");;
    try
      {
        this.setEventDate(RLMDateUtils.parseDateFromElasticsearch((String) esFields.get("eventDatetime")));
      } 
    catch (ParseException e)
      {
      }
    this.setEventID((String) esFields.get("eventID"));
    this.action = BadgeAction.fromExternalRepresentation((String) esFields.get("action"));
    this.badgeID = (String) esFields.get("badgeID");
    this.moduleID = (String) esFields.get("moduleID");
    this.featureID = (String) esFields.get("featureID");
    this.origin = (String) esFields.get("origin");
    this.returnStatus = RESTAPIGenericReturnCodes.fromGenericResponseCode((Integer) esFields.get("returnCode"));
    this.tenantID = (int) esFields.get("tenantID");
    this.infos = new ParameterMap();
  }
  
  //
  //  getGUIPresentationMap
  //
  
  public Map<String, Object> getGUIPresentationMap(BadgeService badgeService, JourneyService journeyService, OfferService offerService, LoyaltyProgramService loyaltyProgramService)
  {
    Map<String, Object> result = new HashMap<String, Object>();
    result.put("eventID", getEventID());
    result.put("eventDate", getDateString(getEventDate()));
    result.put("action", action.getExternalRepresentation());
    result.put("badgeID", badgeID);
    result.put("moduleID", moduleID);
    result.put("moduleName", getModule().toString());
    result.put("featureID", featureID);
    result.put("featureName", DeliveryRequest.getFeatureName(getModule(), getFeatureID(), journeyService, offerService, loyaltyProgramService));
    result.put("tenantID", tenantID);
    result.put("returnCode", returnStatus.getGenericResponseCode());
    result.put("returnCodeDetails", returnStatus.getGenericResponseMessage());
    GUIManagedObject badgeUnchecked = badgeService.getStoredBadge(badgeID);
    if (badgeUnchecked != null && badgeUnchecked.getAccepted())
      {
        Badge badge = (Badge) badgeUnchecked;
        result.put("badgeName", badge.getGUIManagedObjectName());
        result.put("badgeDisplay", badge.getGUIManagedObjectDisplay());
        result.put("badgeType", badge.getBadgeType().getExternalRepresentation());
      }
    return result;
  }
  
  //
  //  getThirdPartyPresentationMap
  //
  
  public Map<String, Object> getThirdPartyPresentationMap(BadgeService badgeService, JourneyService journeyService, OfferService offerService, LoyaltyProgramService loyaltyProgramService)
  {
    Map<String, Object> result = new HashMap<String, Object>();
    result.put("eventID", getEventID());
    result.put("eventDate", getDateString(getEventDate()));
    result.put("action", action.getExternalRepresentation());
    result.put("badgeID", badgeID);
    result.put("moduleID", moduleID);
    result.put("moduleName", getModule().toString());
    result.put("featureID", featureID);
    result.put("featureName", DeliveryRequest.getFeatureName(getModule(), getFeatureID(), journeyService, offerService, loyaltyProgramService));
    result.put("tenantID", tenantID);
    result.put("returnCode", returnStatus.getGenericResponseCode());
    result.put("returnCodeDetails", returnStatus.getGenericResponseMessage());
    GUIManagedObject badgeUnchecked = badgeService.getStoredBadge(badgeID);
    if (badgeUnchecked != null && badgeUnchecked.getAccepted())
      {
        Badge badge = (Badge) badgeUnchecked;
        result.put("badgeName", badge.getGUIManagedObjectName());
        result.put("badgeDisplay", badge.getGUIManagedObjectDisplay());
        result.put("badgeType", badge.getBadgeType().getExternalRepresentation());
      }
    return result;
  }
  
  public Module getModule(){return Module.fromExternalRepresentation(getModuleID());}
  
  /*****************************************
  *
  *  getDateString
  *
  *****************************************/
  @Deprecated public String getDateString(Date date)

  {
    String result = null;
    if (null == date) return result;
    try
      {
        SimpleDateFormat dateFormat = new SimpleDateFormat(Deployment.getAPIresponseDateFormat());   // TODO EVPRO-99
        dateFormat.setTimeZone(TimeZone.getTimeZone(Deployment.getDeployment(tenantID).getTimeZone()));
        result = dateFormat.format(date);
      }
    catch (Exception e)
      {
      }
    return result;
  }
  
  @Override public ActionType getActionType()
  {
    return ActionType.BadgeChange;
  }
  @Override
  public String toString()
  {
    return "BadgeChange [subscriberID=" + subscriberID + ", deliveryRequestID=" + deliveryRequestID + ", action=" + action + ", badgeID=" + badgeID + ", moduleID=" + moduleID + ", featureID=" + featureID + ", origin=" + origin + ", returnStatus=" + returnStatus + ", tenantID=" + tenantID + ", infos=" + infos + ", responseEvent=" + responseEvent + ", getEventDate()=" + getEventDate() + ", getEventID()=" + getEventID() + "]";
  }
  
  
}
