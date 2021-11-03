package com.evolving.nglm.evolution;

import org.apache.kafka.connect.data.*;

import com.evolving.nglm.core.ConnectSerde;
import com.evolving.nglm.core.SchemaUtilities;
import com.evolving.nglm.core.SubscriberStreamOutput;
import com.evolving.nglm.evolution.ActionManager.ActionType;

public class WorkflowEvent extends SubscriberStreamOutput implements EvolutionEngineEvent
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
    schemaBuilder.name("workflow_change");
    schemaBuilder.version(SchemaUtilities.packSchemaVersion(subscriberStreamOutputSchema().version(),2));
    for (Field field : subscriberStreamOutputSchema().fields()) schemaBuilder.field(field.name(), field.schema());
    schemaBuilder.field("subscriberID", Schema.STRING_SCHEMA);
    schemaBuilder.field("workflowID", Schema.STRING_SCHEMA);
    schemaBuilder.field("moduleID", Schema.OPTIONAL_STRING_SCHEMA);
    schemaBuilder.field("featureID", Schema.OPTIONAL_STRING_SCHEMA);
    schema = schemaBuilder.build();
  };

  //
  // serde
  //

  private static ConnectSerde<WorkflowEvent> serde = new ConnectSerde<WorkflowEvent>(schema, false, WorkflowEvent.class, WorkflowEvent::pack, WorkflowEvent::unpack);

  //
  // accessor
  //

  public static Schema schema() { return schema; }
  public static ConnectSerde<WorkflowEvent> serde() { return serde; }

  /****************************************
  *
  * data
  *
  ****************************************/

  private String subscriberID;
  private String workflowID;
  private String moduleID;
  private String featureID;
  
  /****************************************
  *
  * accessors - basic
  *
  ****************************************/

  //
  // accessor
  //

  @Override public String getSubscriberID() { return subscriberID; }
  public String getWorkflowID() { return workflowID; }
  public String getModuleID() { return moduleID; }
  public String getFeatureID() { return featureID; }
  public ActionType getActionType() { return ActionType.TokenChange; }

  //
  //  setters
  //
/*
  public void setSubscriberID(String subscriberID) { this.subscriberID = subscriberID; }
  public void seteventDateTime(Date eventDateTime) { this.eventDateTime = eventDateTime; }
  public void setEventID(String eventID) { this.eventID = eventID; }
  public void setTokenCode(String tokenCode) { this.tokenCode = tokenCode; }
  public void setAction(String action) { this.action = action; }
  public void setReturnStatus(String returnStatus) { this.returnStatus = returnStatus; }
  public void setOrigin(String origin) { this.origin = origin; }
  public void setModuleID(String moduleID) { this.moduleID = moduleID; }
  public void setFeatureID(String featureID) { this.featureID = featureID; }
  */


  /*****************************************
  *
  * constructor (simple)
  *
  *****************************************/

  public WorkflowEvent(SubscriberStreamOutput originatingRequest, String subscriberID, String workflowID, String moduleID, String featureID)
  {
    super(originatingRequest);
    this.subscriberID = subscriberID;
    this.workflowID = workflowID;
    this.moduleID = moduleID;
    this.featureID = featureID;
  }

  /*****************************************
   *
   * constructor unpack
   *
   *****************************************/
  public WorkflowEvent(SchemaAndValue schemaAndValue, String subscriberID, String workflowID, String moduleID, String featureID)
  {
    super(schemaAndValue);
    this.subscriberID = subscriberID;
    this.workflowID = workflowID;
    this.moduleID = moduleID;
    this.featureID = featureID;
  }

  /*****************************************
  *
  * pack
  *
  *****************************************/

  public static Object pack(Object value)
  {
    WorkflowEvent tokenChange = (WorkflowEvent) value;
    Struct struct = new Struct(schema);
    packSubscriberStreamOutput(struct,tokenChange);
    struct.put("subscriberID",tokenChange.getSubscriberID());
    struct.put("workflowID", tokenChange.getWorkflowID());
    struct.put("moduleID", tokenChange.getModuleID());
    struct.put("featureID", tokenChange.getFeatureID());
    return struct;
  }

  /*****************************************
  *
  * unpack
  *
  *****************************************/

  public static WorkflowEvent unpack(SchemaAndValue schemaAndValue)
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
    String workflowID = valueStruct.getString("workflowID");
    String moduleID =  valueStruct.getString("moduleID") ;
    String featureID = valueStruct.getString("featureID");

    //
    // validate
    //

    //
    // return
    //

    return new WorkflowEvent(schemaAndValue, subscriberID, workflowID, moduleID, featureID);
  }

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
  
  @Override
  public String getEventName()
  {
    return "workflow change";
  }
}
