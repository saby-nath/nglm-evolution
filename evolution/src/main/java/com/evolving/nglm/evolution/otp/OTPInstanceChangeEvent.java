/*****************************************************************************
*
*  OTPInstanceChangeEvent.java
*
*****************************************************************************/

package com.evolving.nglm.evolution.otp;

import java.util.Date;

import org.apache.kafka.connect.data.*;

import com.evolving.nglm.core.ConnectSerde;
import com.evolving.nglm.core.SchemaUtilities;
import com.evolving.nglm.core.SubscriberStreamOutput;
import com.evolving.nglm.evolution.EvolutionEngineEvent;
import com.evolving.nglm.evolution.RESTAPIGenericReturnCodes;
//import com.evolving.nglm.evolution.ActionManager.Action;
import com.evolving.nglm.evolution.DeliveryRequest.Module;

public class OTPInstanceChangeEvent extends SubscriberStreamOutput implements EvolutionEngineEvent
{
  /*****************************************
   *
   * schema
   *
   *****************************************/

  //
  // schema
  //

  private static Schema schema = null;
  static
    {
      SchemaBuilder schemaBuilder = SchemaBuilder.struct();
      schemaBuilder.name("otpInstanceChangeEvent");
      schemaBuilder.version(SchemaUtilities.packSchemaVersion(subscriberStreamOutputSchema().version(), 10));
      for (Field field : subscriberStreamOutputSchema().fields())
        schemaBuilder.field(field.name(), field.schema());
      // IN + OUT
      schemaBuilder.field("subscriberID", Schema.STRING_SCHEMA);
      schemaBuilder.field("eventDate", Timestamp.builder().schema());
      schemaBuilder.field("eventID", Schema.STRING_SCHEMA);
      schemaBuilder.field("action", Schema.STRING_SCHEMA);
      schemaBuilder.field("otpTypeName", Schema.STRING_SCHEMA);
      // IN
      schemaBuilder.field("otpCheckValue", Schema.OPTIONAL_STRING_SCHEMA);
      schemaBuilder.field("forceBurn", Schema.OPTIONAL_BOOLEAN_SCHEMA);
      // OUT
      schemaBuilder.field("returnStatus", Schema.OPTIONAL_STRING_SCHEMA);
      schemaBuilder.field("remainingAttempts", Schema.OPTIONAL_INT32_SCHEMA);
      schemaBuilder.field("validityDuration", Schema.OPTIONAL_INT32_SCHEMA);
      schemaBuilder.field("currentTypeErrors", Schema.OPTIONAL_INT32_SCHEMA);
      schemaBuilder.field("globalErrorCounts", Schema.OPTIONAL_INT32_SCHEMA);
      schemaBuilder.field("featureID", Schema.STRING_SCHEMA);
      schemaBuilder.field("moduleID", Schema.STRING_SCHEMA);

      // global
      schemaBuilder.field("tenantID", Schema.INT16_SCHEMA);

      schema = schemaBuilder.build();
    };

  //
  // serde
  //

  private static ConnectSerde<OTPInstanceChangeEvent> serde = new ConnectSerde<OTPInstanceChangeEvent>(schema, false, OTPInstanceChangeEvent.class, OTPInstanceChangeEvent::pack, OTPInstanceChangeEvent::unpack);

  //
  // constants
  //

  // action
  public enum OTPChangeAction
  {
    Check("Check"), Generate("Generate"), Burn("Burn"), Ban("Ban"), Allow("Allow"), Unknown("(Unknown)");

    private String externalRepresentation;

    private OTPChangeAction(String externalRepresentation)
      {
        this.externalRepresentation = externalRepresentation;
      }

    public String getExternalRepresentation()
    {
      return externalRepresentation;
    }

    public static OTPChangeAction fromExternalRepresentation(String externalRepresentation)
    {
      for (OTPChangeAction enumeratedValue : OTPChangeAction.values())
        {
          if (enumeratedValue.getExternalRepresentation().equalsIgnoreCase(externalRepresentation)) return enumeratedValue;
        }
      return Unknown;
    }
  }

  //
  // accessor
  //

  public static Schema schema()
  {
    return schema;
  }

  public static ConnectSerde<OTPInstanceChangeEvent> serde()
  {
    return serde;
  }

  public Schema subscriberStreamEventSchema()
  {
    return schema();
  }

  /****************************************
   *
   * data
   *
   ****************************************/

  private Date eventDate;
  private String subscriberID;
  private String eventID;
  private OTPChangeAction action;
  private String otpTypeName;
  private String otpCheckValue;
  private Integer remainingAttempts;
  private Integer validityDuration;
  private Integer currentTypeErrors;
  private Integer globalErrorCounts;
  private Boolean forceBurn;
  private RESTAPIGenericReturnCodes returnStatus;
  private int tenantID;
  private String featureID;
  private Module moduleID;

  /****************************************
   *
   * accessors
   *
   ****************************************/

  public String getEventName()
  {
    return "otpInstanceChangeEvent";
  }

  @Override
  public String getSubscriberID()
  {
    return subscriberID;
  }

  @Override
  public Date getEventDate()
  {
    return eventDate;
  }

  @Override
  public String getEventID()
  {
    return eventID;
  }

  public OTPChangeAction getAction()
  {
    return action;
  }

  public String getOTPTypeName()
  {
    return otpTypeName;
  }

  public String getOTPCheckValue()
  {
    return otpCheckValue;
  }

  public Boolean getForceBurn()
  {
    return forceBurn;
  }

  public Integer getRemainingAttempts()
  {
    return remainingAttempts;
  }

  public Integer getValidityDuration()
  {
    return validityDuration;
  }

  public Integer getCurrentTypeErrors()
  {
    return currentTypeErrors;
  }

  public Integer getGlobalErrorCounts()
  {
    return globalErrorCounts;
  }

  public RESTAPIGenericReturnCodes getReturnStatus()
  {
    return returnStatus;
  }

  public String getFeatureID()
  {
    return featureID;
  }

  public Module getModuleID()
  {
    return moduleID;
  }

  public int getTenantID()
  {
    return tenantID;
  }

  /*****************************************
   *
   * constructor
   *
   *****************************************/

  public OTPInstanceChangeEvent(Date eventDate, String subscriberID, String eventID, OTPChangeAction action, String otpTypeName, String otpCheckValue, Boolean forceBurn, Integer remainingAttempts, Integer validityDuration, Integer currentTypeErrors, Integer globalErrorCounts, RESTAPIGenericReturnCodes returnStatus, String featureID, Module moduleID, int tenantID)
    {
      // super();
      this.eventDate = eventDate;
      this.subscriberID = subscriberID;
      this.eventID = eventID;
      this.action = action;
      this.otpTypeName = otpTypeName;
      this.otpCheckValue = otpCheckValue;
      this.forceBurn = forceBurn;
      this.remainingAttempts = remainingAttempts;
      this.validityDuration = validityDuration;
      this.currentTypeErrors = currentTypeErrors;
      this.globalErrorCounts = globalErrorCounts;
      this.returnStatus = returnStatus;
      this.featureID = featureID;
      this.moduleID = moduleID;
      this.tenantID = tenantID;
    }

  public OTPInstanceChangeEvent(OTPInstanceChangeEvent eventToClone)
    {
      // super();
      this.eventDate = eventToClone.getEventDate();
      this.subscriberID = eventToClone.getSubscriberID();
      this.eventID = eventToClone.getEventID();
      this.action = eventToClone.getAction();
      this.otpTypeName = eventToClone.getOTPTypeName();
      this.otpCheckValue = eventToClone.getOTPCheckValue();
      this.forceBurn = eventToClone.getForceBurn();
      this.remainingAttempts = eventToClone.getRemainingAttempts();
      this.validityDuration = eventToClone.getValidityDuration();
      this.currentTypeErrors = eventToClone.getCurrentTypeErrors();
      this.globalErrorCounts = eventToClone.getGlobalErrorCounts();
      this.returnStatus = eventToClone.getReturnStatus();
      this.featureID = eventToClone.getFeatureID();
      this.moduleID = eventToClone.getModuleID();
      this.tenantID = eventToClone.getTenantID();
    }

  /*****************************************
   *
   * constructor unpack
   *
   *****************************************/

  public OTPInstanceChangeEvent(SchemaAndValue schemaAndValue, Date eventDate, String subscriberID, String eventID, OTPChangeAction action, String otpTypeName, String otpCheckValue, Boolean forceBurn, Integer remainingAttempts, Integer validityDuration, Integer currentTypeErrors, Integer globalErrorCounts, RESTAPIGenericReturnCodes returnStatus, String featureID, Module moduleID, int tenantID)
    {
      super(schemaAndValue);
      this.eventDate = eventDate;
      this.subscriberID = subscriberID;
      this.eventID = eventID;
      this.action = action;
      this.otpTypeName = otpTypeName;
      this.otpCheckValue = otpCheckValue;
      this.forceBurn = forceBurn;
      this.remainingAttempts = remainingAttempts;
      this.validityDuration = validityDuration;
      this.currentTypeErrors = currentTypeErrors;
      this.globalErrorCounts = globalErrorCounts;
      this.returnStatus = returnStatus;
      this.featureID = featureID;
      this.moduleID = moduleID;
      this.tenantID = tenantID;
    }

  @Override
  public String toString()
  {
    return "OTPInstanceChangeEvent [eventDate=" + eventDate + ", subscriberID=" + subscriberID + ", eventID=" + eventID + ", action=" + action + ", otpTypeName=" + otpTypeName + ", otpCheckValue=" + otpCheckValue + ", remainingAttempts=" + remainingAttempts + ", validityDuration=" + validityDuration + ", currentTypeErrors=" + currentTypeErrors + ", globalErrorCounts=" + globalErrorCounts + ", returnStatus=" + returnStatus + ", tenantID=" + tenantID + ", featureID=" + featureID + ", moduleID=" + moduleID + "]";
  }

  /*****************************************
   *
   * pack
   *
   *****************************************/

  public static Object pack(Object value)
  {
    OTPInstanceChangeEvent otpInstanceChangeEvent = (OTPInstanceChangeEvent) value;
    Struct struct = new Struct(schema);
    packSubscriberStreamOutput(struct, otpInstanceChangeEvent);

    struct.put("eventDate", otpInstanceChangeEvent.getEventDate());
    struct.put("subscriberID", otpInstanceChangeEvent.getSubscriberID());
    struct.put("eventID", otpInstanceChangeEvent.getEventID());
    struct.put("action", otpInstanceChangeEvent.getAction().getExternalRepresentation());
    struct.put("otpTypeName", otpInstanceChangeEvent.getOTPTypeName());
    struct.put("otpCheckValue", otpInstanceChangeEvent.getOTPCheckValue());
    struct.put("forceBurn", otpInstanceChangeEvent.getForceBurn());
    struct.put("remainingAttempts", otpInstanceChangeEvent.getRemainingAttempts());
    struct.put("validityDuration", otpInstanceChangeEvent.getValidityDuration());
    struct.put("currentTypeErrors", otpInstanceChangeEvent.getCurrentTypeErrors());
    struct.put("globalErrorCounts", otpInstanceChangeEvent.getGlobalErrorCounts());
    struct.put("returnStatus", otpInstanceChangeEvent.getReturnStatus().getGenericResponseMessage());
    struct.put("tenantID", (short) otpInstanceChangeEvent.getTenantID());
    struct.put("featureID", otpInstanceChangeEvent.getFeatureID());
    struct.put("moduleID", otpInstanceChangeEvent.getModuleID().getExternalRepresentation());
    return struct;
  }

  //
  // subscriberStreamEventPack
  //

  public Object subscriberStreamEventPack(Object value)
  {
    return pack(value);
  }

  /*****************************************
   *
   * unpack
   *
   *****************************************/

  public static OTPInstanceChangeEvent unpack(SchemaAndValue schemaAndValue)
  {
    //
    // data
    //

    Schema schema = schemaAndValue.schema();
    Object value = schemaAndValue.value();
    Integer schemaVersion = (schema != null) ? SchemaUtilities.unpackSchemaVersion1(schema.version()) : null;

    //
    // unpack
    //

    Struct valueStruct = (Struct) value;
    String subscriberID = valueStruct.getString("subscriberID");
    Date eventDate = (Date) valueStruct.get("eventDate");
    String eventID = valueStruct.getString("eventID");
    OTPChangeAction action = OTPChangeAction.fromExternalRepresentation(valueStruct.getString("action"));
    String otpTypeName = valueStruct.getString("otpTypeName");
    String otpCheckValue = valueStruct.getString("otpCheckValue");
    Boolean forceBurn = valueStruct.getBoolean("forceBurn");
    Integer remainingAttempts = valueStruct.getInt32("remainingAttempts");
    Integer validityDuration = valueStruct.getInt32("validityDuration");
    Integer currentTypeErrors = valueStruct.getInt32("currentTypeErrors");
    Integer globalErrorCounts = valueStruct.getInt32("globalErrorCounts");
    RESTAPIGenericReturnCodes returnStatus = RESTAPIGenericReturnCodes.fromGenericResponseMessage(valueStruct.getString("returnStatus"));
    String featureID = schema.field("featureID") != null ? valueStruct.getString("featureID") : "";
    String moduleString = schema.field("moduleID") != null ? valueStruct.getString("moduleID") : Module.Unknown.getExternalRepresentation();
    Module moduleID = Module.fromExternalRepresentation(moduleString);
    int tenantID = (schemaVersion >= 9) ? valueStruct.getInt16("tenantID") : 1; // for old system, default to tenant 1

    //
    // return
    //

    return new OTPInstanceChangeEvent(schemaAndValue, eventDate, subscriberID, eventID, action, otpTypeName, otpCheckValue, forceBurn, remainingAttempts, validityDuration, currentTypeErrors, globalErrorCounts, returnStatus, featureID, moduleID, tenantID);
  }

  public void setReturnStatus(RESTAPIGenericReturnCodes returnStatus)
  {
    this.returnStatus = returnStatus;
  }

  public void setEventDate(Date eventDate)
  {
    this.eventDate = eventDate;
  }

  public void setEventID(String eventID)
  {
    this.eventID = eventID;
  }

  public void setAction(OTPChangeAction action)
  {
    this.action = action;
  }

  public void setOTPCheckValue(String otpCheckValue)
  {
    this.otpCheckValue = otpCheckValue;
  }

  public void setForceBurn(Boolean forceBurn)
  {
    this.forceBurn = forceBurn;
  }

  public void setRemainingAttempts(Integer remainingAttempts)
  {
    this.remainingAttempts = remainingAttempts;
  }

  public void setValidityDuration(Integer validityDuration)
  {
    this.validityDuration = validityDuration;
  }

  public void setCurrentTypeErrors(Integer currentTypeErrors)
  {
    this.currentTypeErrors = currentTypeErrors;
  }

  public void setGlobalErrorCounts(Integer globalErrorCounts)
  {
    this.globalErrorCounts = globalErrorCounts;
  }

}
