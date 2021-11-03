/****************************************************************************
*
*  TokenChange.java
*
****************************************************************************/

package com.evolving.nglm.evolution;

import java.util.Date;
import java.util.Map;

import org.apache.kafka.connect.data.Field;
import org.apache.kafka.connect.data.Schema;
import org.apache.kafka.connect.data.SchemaAndValue;
import org.apache.kafka.connect.data.SchemaBuilder;
import org.apache.kafka.connect.data.Struct;
import org.apache.kafka.connect.data.Timestamp;

import com.evolving.nglm.core.ConnectSerde;
import com.evolving.nglm.core.SchemaUtilities;
import com.evolving.nglm.core.SubscriberStreamOutput;
import com.evolving.nglm.evolution.ContactPolicyCommunicationChannels.ContactType;
import com.evolving.nglm.evolution.DeliveryRequest.Module;


public class NotificationEvent extends SubscriberStreamOutput implements EvolutionEngineEvent
{
  
  /*****************************************
  *me
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
    schemaBuilder.name("notification_event");
    schemaBuilder.version(SchemaUtilities.packSchemaVersion(subscriberStreamOutputSchema().version(), 3));
    for (Field field : subscriberStreamOutputSchema().fields()) schemaBuilder.field(field.name(), field.schema());
    schemaBuilder.field("subscriberID", Schema.OPTIONAL_STRING_SCHEMA);
    schemaBuilder.field("eventDateTime", Timestamp.builder().schema());
    schemaBuilder.field("eventID", Schema.OPTIONAL_STRING_SCHEMA);
    schemaBuilder.field("templateID", Schema.OPTIONAL_STRING_SCHEMA);
    schemaBuilder.field("tags", SchemaBuilder.map(Schema.STRING_SCHEMA, Schema.STRING_SCHEMA).optional());
    schemaBuilder.field("channelID", Schema.OPTIONAL_STRING_SCHEMA);
    schemaBuilder.field("contactType", SchemaBuilder.string().optional().defaultValue("unknown").schema());
    schemaBuilder.field("source", Schema.OPTIONAL_STRING_SCHEMA);
    schemaBuilder.field("featureID", Schema.STRING_SCHEMA);
    schemaBuilder.field("moduleID", Schema.STRING_SCHEMA);
    schemaBuilder.field("origin", SchemaBuilder.string().optional().defaultValue("CC").schema());
    
    schema = schemaBuilder.build();
  };

  //
  // serde
  //

  private static ConnectSerde<NotificationEvent> serde = new ConnectSerde<NotificationEvent>(schema, false, NotificationEvent.class, NotificationEvent::pack, NotificationEvent::unpack);

  //
  // accessor
  //

  public static Schema schema() { return schema; }
  public static ConnectSerde<NotificationEvent> serde() { return serde; }

  /****************************************
  *
  * data
  *
  ****************************************/

  private String subscriberID;
  private Date eventDateTime;
  private String eventID;
  private String templateID;
  private Map<String, String> tags;
  private String channelID;
  private ContactType contactType;
  private String origin;
  private String source;
  private String featureID;
  private Module moduleID;
    
  /****************************************
  *
  * accessors - basic
  *
  ****************************************/

  //
  // accessor
  //

  @Override public String getSubscriberID() { return subscriberID; }
  public Date geteventDateTime() { return eventDateTime; }
  @Override public String getEventID() { return eventID; }
 
  public String getTemplateID()
  {
    return templateID;
  }

  public Map<String, String> getTags()
  {
    return tags;
  }

  
  public String getChannelID()
  {
    return channelID;
  }
  public ContactType getContactType() { return contactType; }
  public String getOrigin() { return origin; }
  public String getSource() { return source; }
  public String getFeatureID() { return featureID; }
  public Module getModuleID() { return moduleID; }
  
  

  //
  //  setters
  //

  public void setSubscriberID(String subscriberID) { this.subscriberID = subscriberID; }
  public void seteventDateTime(Date eventDateTime) { this.eventDateTime = eventDateTime; }
  public void setEventID(String eventID) { this.eventID = eventID; }
  public void setTemplateID(String templateID) { this.templateID = templateID; }
  public void setTags(Map<String, String> tags) { this.tags = tags; }
  public void setChannelID(String channelID) { this.channelID = channelID; }
  public void setContactType(ContactType contactType) { this.contactType = contactType; }
  public void setSource(String source) { this.source = source; }
  public void setFeatureID(String featureID) { this.featureID = featureID; }
  public void setModuleID(Module moduleID) { this.moduleID = moduleID; }
   
  /*****************************************
  *
  * constructor (simple)
  *
  *****************************************/

  public NotificationEvent(String subscriberID, Date eventDateTime, String eventID, String templateID, Map<String, String> tags, String channelID, ContactType contactType, String origin, String source, String featureID, Module moduleID)
  {
    this.subscriberID = subscriberID;
    this.eventDateTime = eventDateTime;
    this.eventID = eventID;
    this.templateID = templateID;
    this.tags = tags;
    this.channelID = channelID;
    this.contactType = contactType;
    this.origin = origin;
    this.source = source;
    this.featureID = featureID;
    this.moduleID = moduleID;
  }

  /*****************************************
   *
   * constructor unpack
   *
   *****************************************/
  public NotificationEvent(SchemaAndValue schemaAndValue, String subscriberID, Date eventDateTime, String eventID, String templateID, Map<String, String> tags, String channelID, ContactType contactType, String origin, String source, String featureID, Module moduleID)
  {
    super(schemaAndValue);
    this.subscriberID = subscriberID;
    this.eventDateTime = eventDateTime;
    this.eventID = eventID;
    this.templateID = templateID;
    this.tags = tags;
    this.channelID = channelID;
    this.contactType = contactType;
    this.origin = origin;
    this.source = source;
    this.featureID = featureID;
    this.moduleID = moduleID;
  }
  

  /*****************************************
  *
  * pack
  *
  *****************************************/

  public static Object pack(Object value)
  {
    NotificationEvent notificationEvent = (NotificationEvent) value;
    Struct struct = new Struct(schema);
    packSubscriberStreamOutput(struct,notificationEvent);
    struct.put("subscriberID",notificationEvent.getSubscriberID());
    struct.put("eventDateTime",notificationEvent.geteventDateTime());
    struct.put("eventID", notificationEvent.getEventID());
    struct.put("templateID", notificationEvent.getTemplateID());
    struct.put("tags", notificationEvent.getTags());
    struct.put("channelID", notificationEvent.getChannelID());
    struct.put("contactType", notificationEvent.getContactType() != null ? notificationEvent.getContactType().getExternalRepresentation() : null);
    struct.put("source", notificationEvent.getSource());
    struct.put("featureID", notificationEvent.getFeatureID());
    struct.put("moduleID", notificationEvent.getModuleID().getExternalRepresentation());
    struct.put("origin", notificationEvent.getOrigin());
    return struct;
  }

  /*****************************************
  *
  * unpack
  *
  *****************************************/

  public static NotificationEvent unpack(SchemaAndValue schemaAndValue)
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
    Date eventDateTime = (Date) valueStruct.get("eventDateTime");
    String eventID = valueStruct.getString("eventID");
    String templateID = valueStruct.getString("templateID");
    Map<String, String> tags = (Map<String, String>) valueStruct.get("tags");
    String channelID = valueStruct.getString("channelID");
    String contactType = valueStruct.getString("contactType");
    ContactType contactTypeEnum = contactType != null ? ContactType.fromExternalRepresentation(contactType) : ContactType.Unknown;
    String source = valueStruct.getString("source");
    String featureID = schema.field("featureID") != null ? valueStruct.getString("featureID") : "";
    String moduleString = schema.field("moduleID") != null ? valueStruct.getString("moduleID") : Module.Unknown.getExternalRepresentation();
    Module moduleID = Module.fromExternalRepresentation(moduleString);
    String origin = schemaVersion >= 10 ? valueStruct.getString("origin") : "CC";
    
    //
    // validate
    //

    //
    // return
    //

    return new NotificationEvent(schemaAndValue, subscriberID, eventDateTime, eventID,templateID, tags, channelID, contactTypeEnum, origin, source, featureID, moduleID);
  }
  
  
  @Override
  public Date getEventDate()
  {
    return geteventDateTime();
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
    return "Notification Event";
  }
}
