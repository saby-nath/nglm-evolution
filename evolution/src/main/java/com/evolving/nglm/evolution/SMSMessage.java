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
    schemaBuilder.field("messageTextByLanguage", SchemaBuilder.map(Schema.STRING_SCHEMA, Schema.STRING_SCHEMA));
    schemaBuilder.field("tags", SchemaBuilder.array(CriterionField.schema()));
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

  private Map<String,String> messageTextByLanguage = new HashMap<String,String>();
  private List<CriterionField> tags = new ArrayList<CriterionField>();

  /*****************************************
  *
  *  accessors
  *
  *****************************************/

  public Map<String,String> getMessageTextByLanguage() { return messageTextByLanguage; }
  public  List<CriterionField> getTags() { return tags; }
  
  /*****************************************
  *
  *  constructor -- standard
  *
  *****************************************/

  public SMSMessage(JSONArray messagesJSON, CriterionContext criterionContext) throws GUIManagerException
  {
    Map<CriterionField,String> tagReplacements = new HashMap<CriterionField,String>();
    for (int i=0; i<messagesJSON.size(); i++)
      {
        /*****************************************
        *
        *  messageJSON
        *
        *****************************************/

        JSONObject messageJSON = (JSONObject) messagesJSON.get(i);

        /*****************************************
        *
        *  language
        *
        *****************************************/

        SupportedLanguage supportedLanaguage = Deployment.getSupportedLanguages().get(JSONUtilities.decodeString(messageJSON, "languageID", true));
        String language = (supportedLanaguage != null) ? supportedLanaguage.getName() : null;
        if (language == null) throw new GUIManagerException("unsupported language", JSONUtilities.decodeString(messageJSON, "languageID", true));

        /*****************************************
        *
        *  unprocessedMessageText
        *
        *****************************************/

        String unprocessedMessageText = JSONUtilities.decodeString(messageJSON, "messageText", true);

        /*****************************************
        *
        *  find tags
        *
        *****************************************/

        Pattern p = Pattern.compile("\\{(.*?)\\}");
        Matcher m = p.matcher(unprocessedMessageText);
        Map<String,String> rawTagReplacements = new HashMap<String,String>();
        while (m.find())
          {
            /*****************************************
            *
            *  resolve reference
            *
            *****************************************/

            //
            //  criterionField
            //

            String rawTag = m.group();
            String criterionFieldName = m.group(1).trim();
            CriterionField criterionField = criterionContext.getCriterionFields().get(criterionFieldName);
            if (criterionField == null) throw new GUIManagerException("unsupported tag", criterionFieldName);

            //
            //  valid data type
            //

            switch (criterionField.getFieldDataType())
              {
                case IntegerCriterion:
                case DoubleCriterion:
                case StringCriterion:
                case BooleanCriterion:
                case DateCriterion:
                  break;

                default:
                  throw new GUIManagerException("unsupported tag type", criterionFieldName);  
              }

            /*****************************************
            *
            *  generate MessageFormat replacement
            *
            *****************************************/

            if (tagReplacements.get(criterionField) == null)
              {
                StringBuilder replacement = new StringBuilder();
                replacement.append("{");
                replacement.append(tags.size());
                replacement.append(criterionField.resolveTagFormat());
                replacement.append("}");
                tagReplacements.put(criterionField, replacement.toString());
                tags.add(criterionField);
              }
            rawTagReplacements.put(rawTag, tagReplacements.get(criterionField));
          }

        /*****************************************
        *
        *  replace tags
        *
        *****************************************/

        String messageText = unprocessedMessageText;
        for (String rawTag : rawTagReplacements.keySet())
          {
            messageText = messageText.replace(rawTag, rawTagReplacements.get(rawTag));
          }
        
        /*****************************************
        *
        *  messageTextByLanguage
        *
        *****************************************/
        
        messageTextByLanguage.put(language, messageText);
      }
  }

  /*****************************************
  *
  *  constructor -- unpack
  *
  *****************************************/

  private SMSMessage(Map<String,String> messageTextByLanguage, List<CriterionField> tags)
  {
    this.messageTextByLanguage = messageTextByLanguage;
    this.tags = tags;
  }

  /*****************************************
  *
  *  constructor -- copy
  *
  *****************************************/

  public SMSMessage(SMSMessage smsMessage)
  {
    this.messageTextByLanguage = new HashMap<String,String>(smsMessage.getMessageTextByLanguage());
    this.tags = new ArrayList<CriterionField>(smsMessage.getTags());
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
    struct.put("messageTextByLanguage", smsMessage.getMessageTextByLanguage());
    struct.put("tags", packTags(smsMessage.getTags()));
    return struct;
  }

  /*****************************************
  *
  *  packTags
  *
  *****************************************/

  private static List<Object> packTags(List<CriterionField> tags)
  {
    List<Object> result = new ArrayList<Object>();
    for (CriterionField criterionField : tags)
      {
        result.add(CriterionField.pack(criterionField));
      }
    return result;
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
    Map<String,String> messageTextByLanguage = (Map<String,String>) valueStruct.get("messageTextByLanguage");
    List<CriterionField> tags = unpackTags(schema.field("tags").schema(), (List<Object>) valueStruct.get("tags"));

    //
    //  return
    //

    return new SMSMessage(messageTextByLanguage, tags);
  }

  /*****************************************
  *
  *  unpackTags
  *
  *****************************************/

  private static List<CriterionField> unpackTags(Schema schema, List<Object> value)
  {
    //
    //  get schema for EvaluationCriterion
    //

    Schema criterionFieldSchema = schema.valueSchema();
    
    //
    //  unpack
    //

    List<CriterionField> result = new ArrayList<CriterionField>();
    List<Object> valueArray = (List<Object>) value;
    for (Object criterionField : valueArray)
      {
        result.add(CriterionField.unpack(new SchemaAndValue(criterionFieldSchema, criterionField)));
      }

    //
    //  return
    //

    return result;
  }

  /*****************************************
  *
  *  resolve
  *
  *****************************************/

  public String resolve(SubscriberEvaluationRequest subscriberEvaluationRequest)
  {
    /*****************************************
    *
    *  resolve language and messageText
    *
    *****************************************/
    
    //
    //  subscriber language
    //

    CriterionField subscriberLanguage = CriterionContext.Profile.getCriterionFields().get("subscriber.language");
    String language = (String) subscriberLanguage.retrieve(subscriberEvaluationRequest);

    //
    //  message text
    //

    String messageText = (language != null) ? messageTextByLanguage.get(language) : null;

    //
    //  use base language (if necessary)
    //

    if (messageText == null)
      {
        language = Deployment.getBaseLanguage();
        messageText = messageTextByLanguage.get(language);
      }
    
    /*****************************************
    *
    *  message formatter
    *
    *****************************************/
    
    Locale messageLocale = new Locale(language, Deployment.getBaseCountry());
    MessageFormat formatter = new MessageFormat(messageText, messageLocale);
    for (Format format : formatter.getFormats())
      {
        if (format instanceof SimpleDateFormat)
          {
            ((SimpleDateFormat) format).setTimeZone(TimeZone.getTimeZone(Deployment.getBaseTimeZone()));
          }
      }
    
    /*****************************************
    *
    *  tags
    *
    *****************************************/

    Object[] messageTags = new Object[this.tags.size()];
    for (int i=0; i<this.tags.size(); i++)
      {
        CriterionField tag = this.tags.get(i);
        Object value = tag.retrieve(subscriberEvaluationRequest);
        messageTags[i] = value;
      }

    /*****************************************
    *
    *  format
    *
    *****************************************/

    return formatter.format(messageTags);
  }
}
