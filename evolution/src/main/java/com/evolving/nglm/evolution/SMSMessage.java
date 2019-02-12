/*****************************************************************************
*
*  SMSMessage.java
*
*****************************************************************************/

package com.evolving.nglm.evolution;

import com.evolving.nglm.core.ConnectSerde;
import com.evolving.nglm.core.JSONUtilities;
import com.evolving.nglm.core.JSONUtilities.JSONUtilitiesException;
import com.evolving.nglm.core.SchemaUtilities;
import com.evolving.nglm.evolution.EvaluationCriterion.CriterionDataType;
import com.evolving.nglm.evolution.GUIManager.GUIManagerException;

import org.apache.kafka.connect.data.Schema;
import org.apache.kafka.connect.data.SchemaAndValue;
import org.apache.kafka.connect.data.SchemaBuilder;
import org.apache.kafka.connect.data.Struct;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

import java.text.Format;
import java.text.MessageFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SMSMessage
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
    schemaBuilder.name("sms_message");
    schemaBuilder.version(SchemaUtilities.packSchemaVersion(1));
    schemaBuilder.field("messageText", DialogMessage.schema());
    schema = schemaBuilder.build();
  };

  //
  //  serde
  //

  private static ConnectSerde<SMSMessage> serde = new ConnectSerde<SMSMessage>(schema, false, SMSMessage.class, SMSMessage::pack, SMSMessage::unpack);

  //
  //  accessor
  //

  public static Schema schema() { return schema; }
  public static ConnectSerde<SMSMessage> serde() { return serde; }

  /*****************************************
  *
  *  data
  *
  *****************************************/

  private DialogMessage messageText = null;

  /*****************************************
  *
  *  accessors
  *
  *****************************************/

  public DialogMessage getMessageText() { return messageText; }
  
  /*****************************************
  *
  *  constructor -- standard
  *
  *****************************************/

  public SMSMessage(JSONArray messagesJSON, CriterionContext criterionContext) throws GUIManagerException
  {
    this.messageText = new DialogMessage(messagesJSON, "messageText", criterionContext);
  }

  /*****************************************
  *
  *  constructor -- unpack
  *
  *****************************************/

  private SMSMessage(DialogMessage messageText)
  {
    this.messageText = messageText;
  }

  /*****************************************
  *
  *  constructor -- copy
  *
  *****************************************/

  public SMSMessage(SMSMessage smsMessage)
  {
    this.messageText = new DialogMessage(smsMessage.getMessageText());
  }

  /*****************************************
  *
  *  pack
  *
  *****************************************/

  public static Object pack(Object value)
  {
    SMSMessage smsMessage = (SMSMessage) value;
    Struct struct = new Struct(schema);
    struct.put("messageText", DialogMessage.pack(smsMessage.getMessageText()));
    return struct;
  }

  /*****************************************
  *
  *  unpack
  *
  *****************************************/

  public static SMSMessage unpack(SchemaAndValue schemaAndValue)
  {
    //
    //  data
    //

    Schema schema = schemaAndValue.schema();
    Object value = schemaAndValue.value();
    Integer schemaVersion = (schema != null) ? SchemaUtilities.unpackSchemaVersion0(schema.version()) : null;

    //
    //  unpack
    //

    Struct valueStruct = (Struct) value;
    DialogMessage messageText = DialogMessage.unpack(new SchemaAndValue(schema.field("messageText").schema(), valueStruct.get("messageText")));

    //
    //  return
    //

    return new SMSMessage(messageText);
  }

  /*****************************************
  *
  *  resolve
  *
  *****************************************/

  public String resolve(SubscriberEvaluationRequest subscriberEvaluationRequest) { return messageText.resolve(subscriberEvaluationRequest); }
}
