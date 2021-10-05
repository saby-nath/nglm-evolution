package com.evolving.nglm.evolution;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import com.evolving.nglm.evolution.statistics.CounterStat;
import com.evolving.nglm.evolution.statistics.StatBuilder;
import com.evolving.nglm.evolution.statistics.StatsBuilders;
import org.apache.kafka.connect.data.Field;
import org.apache.kafka.connect.data.Schema;
import org.apache.kafka.connect.data.SchemaAndValue;
import org.apache.kafka.connect.data.SchemaBuilder;
import org.apache.kafka.connect.data.Struct;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.evolving.nglm.core.ConnectSerde;
import com.evolving.nglm.core.Deployment;
import com.evolving.nglm.core.JSONUtilities;
import com.evolving.nglm.core.NGLMRuntime;
import com.evolving.nglm.core.RLMDateUtils;
import com.evolving.nglm.core.SchemaUtilities;
import com.evolving.nglm.core.ServerRuntimeException;
import com.evolving.nglm.core.SystemTime;
import com.evolving.nglm.evolution.ContactPolicyCommunicationChannels.ContactType;
import com.evolving.nglm.evolution.DeliveryRequest.Module;
import com.evolving.nglm.evolution.EvaluationCriterion.CriterionDataType;
import com.evolving.nglm.evolution.EvaluationCriterion.CriterionOperator;
import com.evolving.nglm.evolution.EvolutionEngine.EvolutionEventContext;
import com.evolving.nglm.evolution.EvolutionUtilities.TimeUnit;
import com.evolving.nglm.evolution.GUIManagedObject.GUIManagedObjectType;
import com.evolving.nglm.evolution.GUIManager.GUIManagerException;
import com.evolving.nglm.evolution.NodeType.OutputType;
import com.evolving.nglm.evolution.notification.NotificationTemplateParameters;
import com.evolving.nglm.evolution.toolbox.ActionBuilder;
import com.evolving.nglm.evolution.toolbox.ArgumentBuilder;
import com.evolving.nglm.evolution.toolbox.AvailableValueDynamicBuilder;
import com.evolving.nglm.evolution.toolbox.AvailableValueStaticStringBuilder;
import com.evolving.nglm.evolution.toolbox.OutputConnectorBuilder;
import com.evolving.nglm.evolution.toolbox.ParameterBuilder;
import com.evolving.nglm.evolution.toolbox.ToolBoxBuilder;
import com.evolving.nglm.evolution.toolbox.TransitionCriteriaBuilder;

public class NotificationManager extends DeliveryManagerForNotifications implements Runnable
{

  private static final Logger log = LoggerFactory.getLogger(NotificationManager.class);

  private static StatBuilder<CounterStat> statsCounter;
  private static final String applicationID = "deliverymanager-notificationmanager";

  private NotificationInterface pluginInstance;

  public NotificationManager(String deliveryManagerKey, CommunicationChannel cc, int threadNumber)
    {

      super(
          applicationID+"-"+cc.getName(),
          (cc.getDeliveryManagerDeclaration()!=null && cc.getDeliveryManagerDeclaration().getRoutingTopic()!=null)?cc.getDeliveryManagerDeclaration().getRoutingTopic().getName():applicationID,
          deliveryManagerKey, Deployment.getBrokerServers(), cc.getDeliveryManagerDeclaration().getRequestSerde(), cc.getDeliveryManagerDeclaration(), threadNumber);

      // this channel's plugin must be initialized
      try {
        pluginInstance = (NotificationInterface) (Class.forName(cc.getNotificationPluginClass()).newInstance());
        pluginInstance.init(this, cc.getNotificationPluginConfiguration());
      } catch (InstantiationException | IllegalAccessException | IllegalArgumentException e) {
        log.error("NotificationManager: could not create new instance of class " + cc.getNotificationPluginClass(), e);
        throw new RuntimeException("NotificationManager: could not create new instance of class " + cc.getNotificationPluginClass(), e);
      } catch (ClassNotFoundException e) {
        log.error("NotificationManager: could not find class " + cc.getNotificationPluginClass(), e);
        throw new RuntimeException("NotificationManager: could not find class " + cc.getNotificationPluginClass(), e);
      }

      for(int i=0;i<threadNumber;i++) new Thread(this,cc.getName()+"-"+i).start();

      statsCounter = StatsBuilders.getEvolutionCounterStatisticsBuilder("notificationdelivery","notificationmanager-"+deliveryManagerKey);

    }

  private static Map<String,CommunicationChannel> channels = null;
  private static Object channelsSync = new Object();
  private static Map<String,CommunicationChannel> GetCommunicationChannels()
  {
    if(channels == null)
    {
      synchronized(channelsSync)
      {
        if(channels == null)
        {
          channels = Deployment.getCommunicationChannels();
          for (CommunicationChannel staticCommunicationChannel : channels.values())
            {
              CommunicationChannel dynamicCommunicationChannel = getCommunicationChannelService().getActiveCommunicationChannel(staticCommunicationChannel.getID(), SystemTime.getCurrentTime());
              if(dynamicCommunicationChannel != null)
                {
                  DeliveryManagerDeclaration deliveryManagerDeclaration = staticCommunicationChannel.getDeliveryManagerDeclaration();
                  try
                  {
                    deliveryManagerDeclaration = new DeliveryManagerDeclaration(dynamicCommunicationChannel.getJSONRepresentation());
                  }
                  catch (NoSuchMethodException|IllegalAccessException e) 
                  {
                    log.error("Error setting the deliveryManagerDeclaration for dynamic communication channel id: {}", dynamicCommunicationChannel.getID());
                  }
      
                  //set DeliveryManagerDeclaration - because it is missing from the comm channel schema, to update it to latest dynamic changes from GUI
                  dynamicCommunicationChannel.setDeliveryManagerDeclaration(deliveryManagerDeclaration);
      
                  // replace the static communicationChannel
                  channels.replace(staticCommunicationChannel.getID(), dynamicCommunicationChannel);
                }
            }
        }
      }
    }

    return channels;
  }

  /*****************************************
   *
   * class NotificationManagerRequest
   *
   *****************************************/

  public static class NotificationManagerRequest extends DeliveryRequest implements MessageDelivery, INotificationRequest
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
        schemaBuilder.name("service_notification_request");
        schemaBuilder.version(SchemaUtilities.packSchemaVersion(commonSchema().version(), 10));
        for (Field field : commonSchema().fields())
          schemaBuilder.field(field.name(), field.schema());
        schemaBuilder.field("destination", Schema.STRING_SCHEMA);
        schemaBuilder.field("language", Schema.STRING_SCHEMA);
        schemaBuilder.field("templateID", Schema.STRING_SCHEMA);
        schemaBuilder.field("tags", SchemaBuilder.map(Schema.STRING_SCHEMA, SchemaBuilder.array(Schema.STRING_SCHEMA)).name("notification_tags").schema());
        schemaBuilder.field("restricted", Schema.BOOLEAN_SCHEMA);
        schemaBuilder.field("returnCode", Schema.INT32_SCHEMA);
        schemaBuilder.field("returnCodeDetails", Schema.OPTIONAL_STRING_SCHEMA);
        schemaBuilder.field("channelID", Schema.STRING_SCHEMA);
        schemaBuilder.field("notificationParameters", ParameterMap.serde().optionalSchema());
        schemaBuilder.field("contactType", SchemaBuilder.string().defaultValue("unknown").schema());
        schemaBuilder.field("origin", Schema.OPTIONAL_STRING_SCHEMA);
        schema = schemaBuilder.build();
      };

    //
    // serde
    //

    private static ConnectSerde<NotificationManagerRequest> serde = new ConnectSerde<NotificationManagerRequest>(schema, false, NotificationManagerRequest.class, NotificationManagerRequest::pack, NotificationManagerRequest::unpack);

    //
    // accessor
    //

    public static Schema schema()
    {
      return schema;
    }

    public static ConnectSerde<NotificationManagerRequest> serde()
    {
      return serde;
    }

    public Schema subscriberStreamEventSchema()
    {
      return schema();
    }

    /*****************************************
     *
     * data
     *
     *****************************************/

    private String destination;
    private String language;
    private String templateID;
    private Map<String, List<String>> tags;
    private boolean restricted;
    private MessageStatus status;
    private int returnCode;
    private String returnCodeDetails;
    private String channelID;
    private ParameterMap notificationParameters;
    private String contactType;
    private String origin;

    //
    // accessors
    //

    public String getDestination()
    {
      return destination;
    }

    public String getLanguage()
    {
      return language;
    }

    public String getTemplateID()
    {
      return templateID;
    }

    public Map<String, List<String>> getTags()
    {
      return tags;
    }

    public boolean getRestricted()
    {
      return restricted;
    }

    public MessageStatus getMessageStatus()
    {
      return status;
    }

    public int getReturnCode()
    {
      return returnCode;
    }

    public String getReturnCodeDetails()
    {
      return returnCodeDetails;
    }

    public String getChannelID()
    {
      return channelID;
    }
    
    public ParameterMap getNotificationParameters()
    {
      return notificationParameters;
    }
    public String getContactType() { return contactType; }
    public String getOrigin() { return origin; }


    // this resolved the source address
    // populating a param "node.parameter.sourceaddress" with a SourceaAddress "display" field from received param "node.parameter.fromaddress" which contains the "id"
    public void resolveFromAddressToSourceAddress(SourceAddressService sourceAddressService){
      String sourceAddressId = getFromAddressParam();
      if(sourceAddressId==null) return;
      GUIManagedObject sourceAddressObject = sourceAddressService.getStoredSourceAddress(sourceAddressId);
      if(sourceAddressObject==null) return;
      String sourceAddress = sourceAddressObject.getGUIManagedObjectDisplay();
      if(sourceAddress==null) return;
      if(log.isDebugEnabled()) log.debug("NotificationManagerRequest.resolveFromAddressToSourceAddress : resolved "+sourceAddressId+" to "+sourceAddress);
      setSourceAddressParam(sourceAddress);
    }
    public String getFromAddressParam(){
      if(getNotificationParameters()==null) return null;
      return (String)getNotificationParameters().get("node.parameter.fromaddress");
    }
    public String getSourceAddressParam(){
      if(getNotificationParameters()==null) return null;
      return (String)getNotificationParameters().get("node.parameter.sourceaddress");
    }
    public void setSourceAddressParam(String sourceAddress){
      if(log.isDebugEnabled()) log.debug("NotificationManagerRequest.setSourceAddressParam("+sourceAddress+") called");
      ParameterMap parameterMap = getNotificationParameters();
      if(parameterMap==null) parameterMap=new ParameterMap();
      parameterMap.put("node.parameter.sourceaddress",sourceAddress);
      this.notificationParameters = parameterMap;
    }

    /*****************************************
    *
    *  getResolvedParameters
    *
    *****************************************/

    public Map<String, String> getResolvedParameters(SubscriberMessageTemplateService subscriberMessageTemplateService)
    {
      Map<String, String> result = new HashMap<String, String>();
      DialogTemplate template = (DialogTemplate) subscriberMessageTemplateService.getActiveSubscriberMessageTemplate(templateID, SystemTime.getCurrentTime());
      if(template.getDialogMessages() != null)
        {
          for(Map.Entry<String, DialogMessage> dialogMessageEntry : template.getDialogMessages().entrySet())
            {
              DialogMessage dialogMessage = dialogMessageEntry.getValue();
              String parameterName = dialogMessageEntry.getKey();
              String resolved = dialogMessage.resolve(language, tags.get(parameterName), template.getTenantID());
              result.put(parameterName, resolved);
            }
        }
      return result;
    }

    //
    // abstract
    //

    @Override
    public ActivityType getActivityType()
    {
      return ActivityType.Messages;
    }

    //
    // setters
    //


    public void setRestricted(boolean restricted)
    {
      this.restricted = restricted;
    }

    public void setMessageStatus(MessageStatus status)
    {
      this.status = status;
    }

    public void setReturnCode(Integer returnCode)
    {
      this.returnCode = returnCode;
    }

    public void setReturnCodeDetails(String returnCodeDetails)
    {
      this.returnCodeDetails = returnCodeDetails;
    }

    public void setChannelID(String channelID)
    {
      this.channelID = channelID;
    }

    //
    // message delivery accessors
    //

    public int getMessageDeliveryReturnCode()
    {
      return getReturnCode();
    }

    public String getMessageDeliveryReturnCodeDetails()
    {
      return getReturnCodeDetails();
    }

    public String getMessageDeliveryOrigin()
    {
      return getOrigin();
    }

    public String getMessageDeliveryMessageId()
    {
      return getEventID();
    }

//    /*****************************************
//     *
//     * getMessage
//     *
//     *****************************************/
//
//    public String getMessage(String messageField, SubscriberMessageTemplateService subscriberMessageTemplateService)
//    {
//      DialogTemplate dialogTemplate = (DialogTemplate) subscriberMessageTemplateService.getActiveSubscriberMessageTemplate(templateID, SystemTime.getCurrentTime());
//      DialogMessage dialogMessage = (dialogTemplate != null) ? dialogTemplate.getDialogMessage(messageField) : null;
//      String text = (dialogMessage != null) ? dialogMessage.resolve(language, tags.get(messageField)) : null;
//      return text;
//    }

    /*****************************************
     *
     * constructor
     *
     *****************************************/

    public NotificationManagerRequest(EvolutionEventContext context, String deliveryType, String deliveryRequestSource, String destination, String language, String templateID, Map<String, List<String>> tags, String channelID, ParameterMap notificationParameters, String contactType, String origin, int tenantID)
      {
        super(context, deliveryType, deliveryRequestSource, tenantID);
        this.destination = destination;
        this.language = language;
        this.templateID = templateID;
        this.tags = tags;
        this.status = MessageStatus.PENDING;
        this.returnCode = status.getReturnCode();
        this.returnCodeDetails = null;
        this.channelID = channelID;
        this.notificationParameters = notificationParameters;
        this.contactType = contactType;
        this.origin = origin;
      }

//    /*****************************************
//    *
//    *  constructor -- external
//    *
//    *****************************************/
//    
//    public NotificationManagerRequest(JSONObject jsonRoot, DeliveryManagerDeclaration deliveryManager)
//    {
//      super(jsonRoot);
//      this.destination = JSONUtilities.decodeString(jsonRoot, "destination", true);
//      this.language = JSONUtilities.decodeString(jsonRoot, "language", true);
//      this.templateID = JSONUtilities.decodeString(jsonRoot, "templateID", true);
//      this.tags = decodeTags(JSONUtilities.decodeJSONArray(jsonRoot, "tags", new JSONArray()));
//      this.status = MessageStatus.PENDING;
//      this.returnCode = MessageStatus.PENDING.getReturnCode();
//      this.returnCodeDetails = null;
//      this.channelID = JSONUtilities.decodeString(jsonRoot, "channelID", true);
//    }
//
//    /*****************************************
//     *
//     * decodeMessageTags
//     *
//     *****************************************/
//
//    private Map<String, List<String>> decodeTags(JSONArray jsonArray) // TODO SCH : A TESTER !!! !!! !!! !!! !!! !!! !!! !!! !!!
//    {
//      Map<String, List<String>> tags = new HashMap<String, List<String>>();
//      if (jsonArray != null)
//        {
//          for (int i = 0; i < jsonArray.size(); i++)
//            {
//              JSONObject messageTagJSON = (JSONObject) jsonArray.get(i);
//              String messageField = JSONUtilities.decodeString(messageTagJSON, "messageField", true);
//              List<String> messageTags = (List<String>) JSONUtilities.decodeJSONObject(messageTagJSON, "messageTags");
//              tags.put(messageField, messageTags);
//            }
//        }
//      return tags;
//
//    }

    /*****************************************
    *
    *  constructor : minimum for reports
    *
    *****************************************/

    public NotificationManagerRequest(String templateID, String language, Map<String, List<String>> tags, int tenantID)
    {
      this.language = language;
      this.templateID = templateID;
      this.tags = tags;
      this.tenantID = tenantID;
    }

    /*****************************************
     *
     * constructor -- unpack
     *
     *****************************************/

    private NotificationManagerRequest(SchemaAndValue schemaAndValue, String destination, String language, String templateID, Map<String, List<String>> tags, boolean restricted, MessageStatus status, String returnCodeDetails, String channelID, ParameterMap notificationParameters, String contactType, String origin)
      {
        super(schemaAndValue);
        this.destination = destination;
        this.language = language;
        this.templateID = templateID;
        this.tags = tags;
        this.restricted = restricted;
        this.status = status;
        this.returnCode = status.getReturnCode();
        this.returnCodeDetails = returnCodeDetails;
        this.channelID = channelID;
        this.notificationParameters = notificationParameters;
        this.contactType = contactType;
        this.origin = origin;
      }

    /*****************************************
     *
     * constructor -- copy
     *
     *****************************************/

    private NotificationManagerRequest(NotificationManagerRequest notificationManagerRequest)
      {
        super(notificationManagerRequest);
        this.destination = notificationManagerRequest.getDestination();
        this.language = notificationManagerRequest.getLanguage();
        this.templateID = notificationManagerRequest.getTemplateID();
        this.tags = notificationManagerRequest.getTags();
        this.restricted = notificationManagerRequest.getRestricted();
        this.status = notificationManagerRequest.getMessageStatus();
        this.returnCode = notificationManagerRequest.getReturnCode();
        this.returnCodeDetails = notificationManagerRequest.getReturnCodeDetails();
        this.channelID = notificationManagerRequest.getChannelID();
        this.notificationParameters = notificationManagerRequest.getNotificationParameters();
        this.contactType = notificationManagerRequest.getContactType();
        this.origin = notificationManagerRequest.getOrigin();
      }

    /*****************************************
    *
    *  constructor : es - minimum
    *
    *****************************************/
    
    public NotificationManagerRequest(Map<String, Object> esFields)
    {
      super(esFields);
      try
        {
          setCreationDate(RLMDateUtils.parseDateFromElasticsearch((String) esFields.get("creationDate")));
          setDeliveryDate(RLMDateUtils.parseDateFromElasticsearch((String) esFields.get("deliveryDate")));
        } 
      catch (java.text.ParseException e)
        {
          throw new ServerRuntimeException(e);
        }
      
      this.destination = (String) esFields.get("destination");
      setSourceAddressParam((String) esFields.get("source"));
      setSubscriberID((String) esFields.get("subscriberID"));
      this.language = (String) esFields.get("language");
      this.templateID = (String) esFields.get("templateID");
      if (esFields.get("tags") != null)
        {
          Map<String,List<String>> tags = (Map<String, List<String>>) esFields.get("tags");
          this.tags = tags;
        }
      this.returnCode = (Integer) esFields.get("returnCode");
      this.returnCodeDetails = (String) esFields.get("returnCodeDetails");
      this.channelID = (String) esFields.get("channelID");
      this.origin = (String) esFields.get("origin");
    //NOT in ES this.notificationParameters = esFields.get("");
    }

    /*****************************************
     *
     * copy
     *
     *****************************************/

    public NotificationManagerRequest copy()
    {
      return new NotificationManagerRequest(this);
    }

    /*****************************************
     *
     * pack
     *
     *****************************************/

    public static Object pack(Object value)
    {
      NotificationManagerRequest notificationRequest = (NotificationManagerRequest) value;
      Struct struct = new Struct(schema);
      packCommon(struct, notificationRequest);
      struct.put("destination", notificationRequest.getDestination());
      struct.put("language", notificationRequest.getLanguage());
      struct.put("templateID", notificationRequest.getTemplateID());
      struct.put("tags", notificationRequest.getTags());
      struct.put("restricted", notificationRequest.getRestricted());
      struct.put("returnCode", notificationRequest.getReturnCode());
      struct.put("returnCodeDetails", notificationRequest.getReturnCodeDetails());
      struct.put("channelID", notificationRequest.getChannelID());
      struct.put("notificationParameters", ParameterMap.serde().packOptional(notificationRequest.getNotificationParameters()));
      struct.put("contactType", notificationRequest.getContactType());
      struct.put("origin", notificationRequest.getOrigin());
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

    public static NotificationManagerRequest unpack(SchemaAndValue schemaAndValue)
    {
      //
      // data
      //

      Schema schema = schemaAndValue.schema();
      Object value = schemaAndValue.value();
      Integer schemaVersion = (schema != null) ? SchemaUtilities.unpackSchemaVersion2(schema.version()) : null;

      //
      // unpack
      //

      Struct valueStruct = (Struct) value;
      String destination = valueStruct.getString("destination");
      String language = valueStruct.getString("language");
      String templateID = valueStruct.getString("templateID");
      Map<String, List<String>> tags = (Map<String, List<String>>) valueStruct.get("tags");
      boolean restricted = valueStruct.getBoolean("restricted");
      Integer returnCode = valueStruct.getInt32("returnCode");
      String returnCodeDetails = valueStruct.getString("returnCodeDetails");
      String channelID = valueStruct.getString("channelID");
      ParameterMap notificationParameters = null;
      if(schemaVersion < 3)
        {
          notificationParameters = ParameterMap.unpack(new SchemaAndValue(schema.field("notificationParameters").schema(), valueStruct.get("notificationParameters")));
        }
      else 
        {
          // >=3
          notificationParameters = ParameterMap.serde().unpackOptional(new SchemaAndValue(schema.field("notificationParameters").schema(), valueStruct.get("notificationParameters")));
      }
      MessageStatus status = MessageStatus.fromReturnCode(returnCode);
      String contactType = schemaVersion >= 9 ? valueStruct.getString("contactType") : "unknown";
      String origin = schemaVersion >= 10 ? valueStruct.getString("origin") : "unknown";

      //
      // return
      //

      return new NotificationManagerRequest(schemaAndValue, destination, language, templateID, tags, restricted, status, returnCodeDetails, channelID, notificationParameters, contactType, origin);
    }

//    /*****************************************
//    *
//    *  unpackTags
//    *
//    *****************************************/
//
//    private static Map<String, List<String>> unpackTags(Schema schema, Object value)
//    {
//      //
//      //  get schema for JourneyNode
//      //
//
//      Schema journeyNodeSchema = schema.valueSchema();
//      
//      //
//      //  unpack
//      //
//
//      Map<String, List<String>> tagsStruct = (Map<String, List<String>>) value;
//      Map<String,List<String>> result = new LinkedHashMap<String,List<String>>();
//      for (String parameterName : tagsStruct.keySet())
//        {
//          List<String> values = tagsStruct.get(parameterName);
//          result.put(parameterName, values);
//        }
//
//      //
//      //  return
//      //
//
//      return result;
//    }

    /****************************************
     *
     * presentation utilities
     *
     ****************************************/

    //
    // addFieldsForGUIPresentation
    //

    @Override
    public void addFieldsForGUIPresentation(HashMap<String, Object> guiPresentationMap, SubscriberMessageTemplateService subscriberMessageTemplateService, SalesChannelService salesChannelService, JourneyService journeyService, OfferService offerService, LoyaltyProgramService loyaltyProgramService, ProductService productService, VoucherService voucherService, DeliverableService deliverableService, PaymentMeanService paymentMeanService, ResellerService resellerService, int tenantID)
    {
      guiPresentationMap.put(CUSTOMERID, getSubscriberID());
      //guiPresentationMap.put(EVENTID, null); //why null?
      guiPresentationMap.put(MODULEID, getModuleID());
      guiPresentationMap.put(MODULENAME, getModule().toString());
      guiPresentationMap.put(FEATUREID, getFeatureID());
      guiPresentationMap.put(FEATURENAME, getFeatureName(getModule(), getFeatureID(), journeyService, offerService, loyaltyProgramService));
      guiPresentationMap.put(FEATUREDISPLAY, getFeatureDisplay(getModule(), getFeatureID(), journeyService, offerService, loyaltyProgramService));
      guiPresentationMap.put(SOURCE, getSourceAddressParam());
      guiPresentationMap.put(RETURNCODE, getReturnCode());
      guiPresentationMap.put(RETURNCODEDETAILS, MessageStatus.fromReturnCode(getReturnCode()).toString());
      //todo check NOTIFICATION_CHANNEL is ID or display: getChannelID() or...
      guiPresentationMap.put(NOTIFICATION_CHANNEL, GetCommunicationChannels().get(getChannelID()).getDisplay());
      guiPresentationMap.put(NOTIFICATION_RECIPIENT, getDestination());
      guiPresentationMap.put("messageContent", gatherChannelParameters(subscriberMessageTemplateService));
      guiPresentationMap.put("contactType", getContactType());
      guiPresentationMap.put("origin", getOrigin());
      
    }

    //
    // addFieldsForThirdPartyPresentation
    //

    @Override
    public void addFieldsForThirdPartyPresentation(HashMap<String, Object> thirdPartyPresentationMap, SubscriberMessageTemplateService subscriberMessageTemplateService, SalesChannelService salesChannelService, JourneyService journeyService, OfferService offerService, LoyaltyProgramService loyaltyProgramService, ProductService productService, VoucherService voucherService, DeliverableService deliverableService, PaymentMeanService paymentMeanService, ResellerService resellerService, int tenantID)
    {
      thirdPartyPresentationMap.put(CUSTOMERID, getSubscriberID());
      //thirdPartyPresentationMap.put(EVENTID, null); //why null?
      thirdPartyPresentationMap.put(MODULEID, getModuleID());
      thirdPartyPresentationMap.put(MODULENAME, getModule().toString());
      thirdPartyPresentationMap.put(FEATUREID, getFeatureID());
      thirdPartyPresentationMap.put(FEATURENAME, getFeatureName(getModule(), getFeatureID(), journeyService, offerService, loyaltyProgramService));
      thirdPartyPresentationMap.put(FEATUREDISPLAY, getFeatureDisplay(getModule(), getFeatureID(), journeyService, offerService, loyaltyProgramService));
      thirdPartyPresentationMap.put(SOURCE, getSourceAddressParam());
      thirdPartyPresentationMap.put(RETURNCODE, getReturnCode());
      thirdPartyPresentationMap.put(RETURNCODEDESCRIPTION, RESTAPIGenericReturnCodes.fromGenericResponseCode(getReturnCode()).getGenericResponseMessage());
      thirdPartyPresentationMap.put(RETURNCODEDETAILS, getReturnCodeDetails());
      //todo check NOTIFICATION_CHANNEL is ID or display: getChannelID() or...
      thirdPartyPresentationMap.put(NOTIFICATION_CHANNEL, GetCommunicationChannels().get(getChannelID()).getDisplay());
      thirdPartyPresentationMap.put(NOTIFICATION_RECIPIENT, getDestination());
      thirdPartyPresentationMap.put("messageContent", gatherChannelParameters(subscriberMessageTemplateService));
      thirdPartyPresentationMap.put("contactType", getContactType());
      thirdPartyPresentationMap.put("origin", getOrigin());
    }

    public Map<String, Object> gatherChannelParameters(SubscriberMessageTemplateService subscriberMessageTemplateService)
    {
      Map<String, Object> messageContent = new HashMap<>();
      Map<String, String> resolvedParameters = getResolvedParameters(subscriberMessageTemplateService);
      Map<String, CriterionField> comChannelParams = GetCommunicationChannels().get(getChannelID()).getParameters();
      for (Entry<String, String> entry : resolvedParameters.entrySet())
        {
          String paramName = entry.getKey();
          CriterionField param = comChannelParams.get(paramName);
          if (param == null)
            {
              log.debug("unexpected : null param in configuration of " + GetCommunicationChannels().get(getChannelID()).getDisplay() + " : " + paramName);
            }
          else
            {
              String paramDisplay = param.getDisplay();
              String paramValue = entry.getValue();
              messageContent.put(paramDisplay, paramValue);
            }
        }
      return messageContent;
    }

    public void resetDeliveryRequestAfterReSchedule()
    {
      this.setReturnCode(MessageStatus.PENDING.getReturnCode());
      this.setMessageStatus(MessageStatus.PENDING);

    }

    @Override
    public String toString()
    {
      return "NotificationManagerRequest [destination=" + destination + ", language=" + language + ", templateID=" + templateID + ", tags=" + tags + ", restricted=" + restricted + ", status=" + status + ", returnCode=" + returnCode + ", returnCodeDetails=" + returnCodeDetails + ", channelID=" + channelID + "] " + super.toString();
    }

    public static final String lastSentCountBriefcaseKey = "lastSentCount";
    public int extractLastSentCount() 
    {
      if(getDiplomaticBriefcase().containsKey(lastSentCountBriefcaseKey))
      {
        return Integer.parseInt(getDiplomaticBriefcase().get(lastSentCountBriefcaseKey));
      }
      return 1;
    }

  }

  /*****************************************
   *
   * class ActionManager
   *
   *****************************************/

  public static class ActionManager extends com.evolving.nglm.evolution.ActionManager
  {
    /*****************************************
     *
     * data
     *
     *****************************************/

    private String moduleID;
    private String channelID;

    /*****************************************
     *
     * constructor
     *
     *****************************************/

    public ActionManager(JSONObject configuration) throws GUIManagerException
      {
        super(configuration);
        this.moduleID = JSONUtilities.decodeString(configuration, "moduleID", true);
        this.channelID = JSONUtilities.decodeString(configuration, "channelID", true);
      }

    /*****************************************
     *
     * execute
     *
     *****************************************/

    @Override
    public List<Action> executeOnEntry(EvolutionEventContext evolutionEventContext, SubscriberEvaluationRequest subscriberEvaluationRequest)
    {

      /*****************************************
       *
       * now
       *
       *****************************************/

      Date now = SystemTime.getCurrentTime();

      /*****************************************
       *
       * template parameters
       *
       *****************************************/

      NotificationTemplateParameters templateParameters = (NotificationTemplateParameters) CriterionFieldRetriever.getJourneyNodeParameter(subscriberEvaluationRequest, "node.parameter.dialog_template");
      // templateParameters contains only field specific to a template, by example sms.body, but no isFlashSMS
      
      
      /*****************************************
       *
       * get DialogTemplate
       *
       *****************************************/
      
      String origin = subscriberEvaluationRequest.getJourneyNode().getNodeName() != null ? subscriberEvaluationRequest.getJourneyNode().getNodeName() : "unknown";
      String journeyID = subscriberEvaluationRequest.getJourneyState().getJourneyID();
      Journey journey = evolutionEventContext.getJourneyService().getActiveJourney(journeyID, evolutionEventContext.now());
      String newModuleID = moduleID;
      
      if (journey != null && journey.getGUIManagedObjectType() == GUIManagedObjectType.Workflow && journey.getJSONRepresentation().get("areaAvailability") != null )
        {
          JSONArray areaAvailability = (JSONArray) journey.getJSONRepresentation().get("areaAvailability");
          if (areaAvailability != null && !(areaAvailability.isEmpty())) {
          for (int i = 0; i < areaAvailability.size(); i++)
            {
              if (!(areaAvailability.get(i).equals("realtime")) && !(areaAvailability.get(i).equals("journeymanager")))
                {
                  newModuleID = Module.Loyalty_Program.getExternalRepresentation();
                  if (subscriberEvaluationRequest.getJourneyState() != null && subscriberEvaluationRequest.getJourneyState().getsourceOrigin() != null) origin = subscriberEvaluationRequest.getJourneyState().getsourceOrigin();
                  break;
                }
            }
          }
        }
      if (journey != null && journey.getGUIManagedObjectType() == GUIManagedObjectType.Workflow && journey.getJSONRepresentation().get("areaAvailability") != null )
        {
          JSONArray areaAvailability = (JSONArray) journey.getJSONRepresentation().get("areaAvailability");
          if (areaAvailability != null && !(areaAvailability.isEmpty())) {
          for (int i = 0; i < areaAvailability.size(); i++)
            {
              if (areaAvailability.get(i).equals("realtime"))
                {
                  newModuleID = Module.Offer_Catalog.getExternalRepresentation();
                  break;
                }
            }
          }
        }
      
      String deliveryRequestSource = extractWorkflowFeatureID(evolutionEventContext, subscriberEvaluationRequest, journeyID);
      String language = subscriberEvaluationRequest.getLanguage();
      SubscriberMessageTemplateService subscriberMessageTemplateService = evolutionEventContext.getSubscriberMessageTemplateService();
      DialogTemplate baseTemplate = (DialogTemplate) subscriberMessageTemplateService.getActiveSubscriberMessageTemplate(templateParameters.getSubscriberMessageTemplateID(), now);
      DialogTemplate template = (baseTemplate != null) ? ((DialogTemplate) baseTemplate.getReadOnlyCopy(evolutionEventContext)) : null;

      String destAddress = null;

      //
      // messages
      //

      Map<String, List<String>> tags = null;
      if (template != null)
        {
          //
          // get communicationChannel
          //

          CommunicationChannel communicationChannel = GetCommunicationChannels().get(template.getCommunicationChannelID());

          //
          // get dest address
          //

          CriterionField criterionField = Deployment.getProfileCriterionFields().get(communicationChannel.getProfileAddressField());
          destAddress = (String) criterionField.retrieveNormalized(subscriberEvaluationRequest);

          //
          // get dialogMessageTags
          //

//          log.info(" ===================================");
//          log.info("destAddress = "+destAddress);

          tags = new HashMap<String, List<String>>();
          for (String messageField : template.getDialogMessageFields().keySet())
            {
              DialogMessage dialogMessage = template.getDialogMessage(messageField);
              List<String> dialogMessageTags = (dialogMessage != null) ? dialogMessage.resolveMessageTags(subscriberEvaluationRequest, language) : new ArrayList<String>();
              tags.put(messageField, dialogMessageTags);

//            log.info("  ------------------------");
//            log.info("template.getDialogMessageFields contains :");
//            for(String m : template.getDialogMessageFields()){log.info("     - "+m);}
//            log.info("template.getDialogMessageFields contains :");
//            for(DialogMessage dm : template.getDialogMessages()){
//              log.info("    => dialogMessage :");
//              for(String k : dm.getMessageTextByLanguage().keySet()){
//                log.info("     - "+k+" : "+dm.getMessageTextByLanguage().get(k));
//              }
//            }
//            log.info("handling messageField = "+messageField);
//            log.info("found dialogMessage = "+dialogMessage+" (SHOULD NOT BE NULL !!!)");
//            log.info("dialogMessageTags = "+dialogMessageTags+" ("+dialogMessageTags.size()+" elements)");

            }
//          log.info(" ===================================");

          //
          // Parameters specific to the channel toolbox but NOT related to template
          //          
          ParameterMap notificationParameters = new ParameterMap();
          for(CriterionField field : communicationChannel.getToolboxParameters().values()) {
            Object value = CriterionFieldRetriever.getJourneyNodeParameter(subscriberEvaluationRequest,field.getID());
            notificationParameters.put(field.getID(), value);            
          }
          
          // add also the mandatory parameters for all channels
          Object value = CriterionFieldRetriever.getJourneyNodeParameter(subscriberEvaluationRequest,"node.parameter.contacttype");
          ContactType contactType = ContactType.fromExternalRepresentation((String) value);
          notificationParameters.put("node.parameter.contacttype", value);
          value = CriterionFieldRetriever.getJourneyNodeParameter(subscriberEvaluationRequest,"node.parameter.fromaddress");
          notificationParameters.put("node.parameter.fromaddress", value);
          
          /*****************************************
          *
          * request
          *
          *****************************************/

         NotificationManagerRequest request = null;
         if (destAddress != null)
           {
             request = new NotificationManagerRequest(evolutionEventContext, communicationChannel.getDeliveryType(), deliveryRequestSource, destAddress, language, template.getDialogTemplateID(), tags, channelID, notificationParameters, contactType.getExternalRepresentation(), origin, subscriberEvaluationRequest.getTenantID());
             request.setModuleID(newModuleID);
             request.setFeatureID(deliveryRequestSource);
             request.forceDeliveryPriority(contactType.getDeliveryPriority());
             request.setRestricted(contactType.getRestricted());
           }
         else
           {
             log.info("NotificationManager unknown destination address for subscriberID " + subscriberEvaluationRequest.getSubscriberProfile().getSubscriberID());
           }

         /*****************************************
          *
          * return
          *
          *****************************************/

         return Collections.<Action>singletonList(request);
        }
      
      else
        {
          log.info("NotificationManager unknown dialog template ");
          throw new RuntimeException("NotificationManager unknown dialog template for Journey " 
              + subscriberEvaluationRequest.getJourneyState().getJourneyID() 
              + " node " + subscriberEvaluationRequest.getJourneyNode().getNodeID() + "/" + subscriberEvaluationRequest.getJourneyNode().getNodeName());
        }
      }
    
    @Override public Map<String, String> getGUIDependencies(List<GUIService> guiServiceList, JourneyNode journeyNode, int tenantID)
    {
      Map<String, String> result = new HashMap<String, String>();
      NotificationTemplateParameters templateParameters = (NotificationTemplateParameters) CriterionFieldRetriever.getJourneyNodeParameter(new SubscriberEvaluationRequest(null, null, null, null, journeyNode, null, null, SystemTime.getCurrentTime(), tenantID), "node.parameter.dialog_template");
      String dialogueTemplateID = (String) templateParameters.getSubscriberMessageTemplateID();
      String fromAddressID = (String) journeyNode.getNodeParameters().get("node.parameter.fromaddress");
      if (fromAddressID != null) result.put("sourceaddress", fromAddressID);
      if (dialogueTemplateID != null) {
    	  result.put("dialogtemplate", dialogueTemplateID);
    	  SimpleParameterMap paramMap = templateParameters.getParameterTags();
    	  String customCriteria = "";
    	  if (paramMap != null) {
    	    for (String paramKey : paramMap.keySet()) {
    	      Object param = paramMap.get(paramKey);
    	      customCriteria += ",toto";
    	    }
    	    result.put("customcriteria", customCriteria.substring(1)); // remove leading ','
    	  }
      }
      return result;
    }
  }

  /*****************************************
   *
   * run
   *
   *****************************************/

  public void run()
  {
    int lastSentCount = 1; //(only for Smpp) how many sms parts were sent. For checking throttling
    while (true)
      {
        /*****************************************
         *
         * nextRequest
         *
         *****************************************/

        DeliveryRequest deliveryRequest = nextRequest(lastSentCount);
        Date now = SystemTime.getCurrentTime();

        if(log.isDebugEnabled()) log.debug("NotificationManagerRequest run deliveryRequest" + deliveryRequest);

        NotificationManagerRequest dialogRequest = (NotificationManagerRequest) deliveryRequest;
        //prometheus status pending
        incrementStats(dialogRequest);
        dialogRequest.resolveFromAddressToSourceAddress(getSourceAddressService());
        DialogTemplate dialogTemplate = (DialogTemplate) getSubscriberMessageTemplateService().getActiveSubscriberMessageTemplate(dialogRequest.getTemplateID(), now);
        
        if (dialogTemplate != null) 
          {
            
            if(dialogRequest.getRestricted()) 
              {

                Date effectiveDeliveryTime = now;
                CommunicationChannel channel = GetCommunicationChannels().get(dialogRequest.getChannelID());
                if(channel != null) 
                  {
                    effectiveDeliveryTime = channel.getEffectiveDeliveryTime(getBlackoutService(), getTimeWindowService(), now, dialogRequest.getTenantID());
                  }

                if(effectiveDeliveryTime.equals(now) || effectiveDeliveryTime.before(now))
                  {
                    if(log.isDebugEnabled()) log.debug("NotificationManagerRequest SEND Immediately restricted " + dialogRequest);
                    pluginInstance.send(dialogRequest);
                    lastSentCount = dialogRequest.extractLastSentCount();
                  }
                else
                  {
                    if(log.isDebugEnabled()) log.debug("NotificationManagerRequest RESCHEDULE to " + effectiveDeliveryTime + " restricted " + dialogRequest);
                    dialogRequest.setRescheduledDate(effectiveDeliveryTime);
                    dialogRequest.setDeliveryStatus(DeliveryStatus.Reschedule);
                    dialogRequest.setReturnCode(MessageStatus.RESCHEDULE.getReturnCode());
                    dialogRequest.setMessageStatus(MessageStatus.RESCHEDULE);
                    completeDeliveryRequest((INotificationRequest) dialogRequest);
                  }      
              }
            else
              {
                if(log.isDebugEnabled()) log.debug("NotificationManagerRequest SEND Immediately NON restricted " + dialogRequest);
                pluginInstance.send(dialogRequest);
                lastSentCount = dialogRequest.extractLastSentCount();
              }
            }
          else
            {
              log.info("NotificationManagerRequest run deliveryRequest : ERROR : template with id '"+dialogRequest.getTemplateID()+"' not found");
              if(log.isDebugEnabled())
                {
                  log.debug("subscriberMessageTemplateService contains :");
                  for(GUIManagedObject obj : getSubscriberMessageTemplateService().getActiveSubscriberMessageTemplates(now, deliveryRequest.getTenantID()))
                    {
                      log.debug("   - "+obj.getGUIManagedObjectName()+" (id "+obj.getGUIManagedObjectID()+") : "+obj.getClass().getName());
                    }
                }
              dialogRequest.setDeliveryStatus(DeliveryStatus.Failed);
              dialogRequest.setReturnCode(MessageStatus.UNKNOWN.getReturnCode());
              dialogRequest.setMessageStatus(MessageStatus.UNKNOWN);
              dialogRequest.setReturnCodeDetails("NoTemplate" + dialogRequest.getTemplateID());
              completeDeliveryRequest((INotificationRequest)dialogRequest);
            }
      }

  }

  public void completeRequest(DeliveryRequest deliveryRequest)
  {
    if(log.isDebugEnabled()) log.debug("NotificationManager.completeDeliveryRequest(deliveryRequest=" + deliveryRequest + ")");
    if(((NotificationManagerRequest) deliveryRequest).getReturnCode() == MessageStatus.DELIVERED.getReturnCode()) 
    {
      ((NotificationManagerRequest) deliveryRequest).setReturnCodeDetails(deliveryRequest.getDeliveryStatus().name());
    }
    super.completeRequest(deliveryRequest);
  }

  public void updateDeliveryRequest(INotificationRequest deliveryRequest)
  {
    if(log.isDebugEnabled()) log.debug("NotificationManager.updateDeliveryRequest(deliveryRequest=" + deliveryRequest + ")");
    updateRequest((DeliveryRequest)deliveryRequest);
  }

  public void completeDeliveryRequest(INotificationRequest deliveryRequest)
  {
    if(log.isDebugEnabled()) log.debug("NotificationManager.completeDeliveryRequest(deliveryRequest=" + deliveryRequest + ")");
    completeRequest((DeliveryRequest)deliveryRequest);
    incrementStats((NotificationManagerRequest) deliveryRequest);
  }

  private void incrementStats(NotificationManagerRequest notificationManagerRequest)
  {
    statsCounter.withLabel(StatsBuilders.LABEL.status.name(),notificationManagerRequest.getDeliveryStatus().getExternalRepresentation())
            .withLabel(StatsBuilders.LABEL.channel.name(), GetCommunicationChannels().get(notificationManagerRequest.getChannelID()).getDisplay())
            .withLabel(StatsBuilders.LABEL.module.name(), notificationManagerRequest.getModule().name())
            .withLabel(StatsBuilders.LABEL.priority.name(), notificationManagerRequest.getDeliveryPriority().getExternalRepresentation())
            .withLabel(StatsBuilders.LABEL.tenant.name(), String.valueOf(notificationManagerRequest.getTenantID()))
            .getStats().increment();
  }

  /*****************************************
   *
   * submitCorrelatorUpdateDeliveryRequest
   *
   *****************************************/

  public void submitCorrelatorUpdateDeliveryRequest(String correlator, JSONObject correlatorUpdate)
  {
    if(log.isDebugEnabled()) log.debug("NotificationManager.submitCorrelatorUpdateDeliveryRequest(correlator=" + correlator + ", correlatorUpdate=" + correlatorUpdate.toJSONString() + ")");
    submitCorrelatorUpdate(correlator, correlatorUpdate);
  }

  /*****************************************
   *
   * processCorrelatorUpdate
   *
   *****************************************/

  @Override
  protected void processCorrelatorUpdate(DeliveryRequest deliveryRequest, JSONObject correlatorUpdate)
  {
    int result = JSONUtilities.decodeInteger(correlatorUpdate, "result", true);
    INotificationRequest dialogRequest = (INotificationRequest) deliveryRequest;
    if (dialogRequest != null)
      {
        dialogRequest.setMessageStatus(MessageStatus.fromReturnCode(result));
        dialogRequest.setDeliveryStatus(getDeliveryStatus(dialogRequest.getMessageStatus()));
        dialogRequest.setDeliveryDate(SystemTime.getCurrentTime());
        completeDeliveryRequest(dialogRequest);
      }
  }

  /*****************************************
   *
   * shutdown
   *
   *****************************************/

  @Override
  protected void shutdown()
  {
    log.info("NotificationManager:  shutdown");
  }

  /*****************************************
   *
   * main
   *
   *****************************************/

  public static void main(String[] args)
  {
    //trigger static initializations
    NGLMRuntime.initialize(true);
    new LoggerInitialization().initLogger();

    log.info("NotificationManager: recieved " + args.length + " args");
    for (String arg : args)
      {
        log.info("NotificationManager: arg " + arg);
      }

    String deliveryManagerKey = args[0];
    // Point separated by example: sms.sms_flash.email.pushapp
    // and maybe each followed by ',threadNumber' : sms,10.sms_flash,10.email.pushapp
    String listOfChannels = args[1];

    int defaultThreadNumber = 10;//TODO make it configurable

    if (listOfChannels != null){
      // specified plugin only
      for (String channelArg : listOfChannels.split("\\.")){
        String[] split2 = channelArg.split(",");
        String channel = split2.length==2 ? split2[0] : channelArg;
        int threadNumber = split2.length==2 ? Integer.parseInt(split2[1]) : defaultThreadNumber;
        for(CommunicationChannel cc:GetCommunicationChannels().values()){
          if(cc.getName().equals(channel)){
            int nbInstances = JSONUtilities.decodeInteger(cc.getDeliveryManagerDeclaration().getJSONRepresentation(), "nbInstancePerProcess", 1);
            log.warn("Number of instances for channel " + channel + " is " + nbInstances);
            for(int i=0; i<nbInstances; i++)
              {
                log.info("NotificationManager: starting plugin for "+channel+" with "+threadNumber+" threads");
                new NotificationManager(deliveryManagerKey,cc,threadNumber).startDelivery();
              }
          }
        }
      }
    } else{
      // otherwise all ones
      for(CommunicationChannel cc:GetCommunicationChannels().values()){
        int nbInstances = JSONUtilities.decodeInteger(cc.getDeliveryManagerDeclaration().getJSONRepresentation(), "nbInstancePerProcess", 1);
        log.warn("Number of instances for channel " + cc.getName() + " is " + nbInstances);
        for(int i=0; i<nbInstances; i++)
          {
            log.info("NotificationManager: starting plugin for "+cc.getName()+" with "+defaultThreadNumber+" threads");
            new NotificationManager(deliveryManagerKey,cc,defaultThreadNumber).startDelivery();
          }
      }
    }

  }

  public static ArrayList<String> getNotificationNodeTypes()
  {

//    {
//      "id"                     : "143",
//      "name"                   : "appPush",
//      "display"                : "App Push",
//      "icon"                   : "jmr_components/styles/images/objects/app-push.png",
//      "height"                 : 70,
//      "width"                  : 70,
//      "outputType"             : "static",
//      "outputConnectors"       : 
//        [ 
//          { "name" : "delivered", "display" : "Delivered/Sent","transitionCriteria" : [ { "criterionField" : "node.action.deliverystatus", "criterionOperator" : "is in set", "argument" : { "expression" : "[ 'delivered', 'acknowledged' ]" } } ] },
//          { "name" : "failed",    "display" : "Failed",        "transitionCriteria" : [ { "criterionField" : "node.action.deliverystatus", "criterionOperator" : "is in set", "argument" : { "expression" : "[ 'failed', 'indeterminate', 'failedTimeout' ]" } } ] },
//          { "name" : "timeout",   "display" : "Timeout",       "transitionCriteria" : [ { "criterionField" : "evaluation.date", "criterionOperator" : ">=", "argument" : { "timeUnit" : "instant", "expression" : "dateAdd(node.entryDate, 1, 'minute')" } } ] },
//          { "name" : "unknown",   "display" : "UnknownAppID",  "transitionCriteria" : [ { "criterionField" : "subscriber.appID", "criterionOperator" : "is null" } ] }
//        ],
//      "parameters" :
//        [
//          { 
//            "id" : "node.parameter.dialog_template",
//            "display" : "Message Template",
//            "dataType" : "string",
//            "multiple" : false,
//            "mandatory" : true,
//            "availableValues" : [ "#dialog_template_3#" ],
//            "defaultValue" : null
//          },
//          { 
//            "id" : "node.parameter.contacttype",
//            "display" : "Contact Type",
//            "dataType" : "string",
//            "multiple" : false,
//            "mandatory" : true,
//            "availableValues" : 
//              [ 
//                { "id" : "callToAction",  "display" : "Call To Action" },
//                { "id" : "response", "display" : "Response" },
//                { "id" : "reminder", "display" : "Reminder" },
//                { "id" : "announcement", "display" : "Announcement" },
//                { "id" : "actionNotification", "display" : "Action Notification" }
//              ],
//            "defaultValue" : null
//          },
//
//          { 
//            "id" : "node.parameter.fromaddress",
//            "display" : "From Address",
//            "dataType" : "string",
//            "multiple" : false,
//            "mandatory" : true,
//            "availableValues" : [ "#dialog_source_address_3#" ],
//            "defaultValue" : null
//          }
//        ],
//      "action" : 
//        {
//          "actionManagerClass" : "com.evolving.nglm.evolution.NotificationManager$ActionManager",
//          "channelID" : "3",
//          "moduleID" : "1"
//        }
//     },

    ArrayList<String> result = new ArrayList<>();
    //TODO here use Deployment.getCommunicationChannels(), not GetCommunicationChannels(), because of static trigger calls in DeploymentCommon
    for (CommunicationChannel current : Deployment.getCommunicationChannels().values())
      {
        if(!current.isGeneric()) {
          continue;
        }
        
        ToolBoxBuilder tb = new ToolBoxBuilder(current.getToolboxID(), current.getName(), current.getDisplay(), current.getIcon(), current.getToolboxHeight(), current.getToolboxWidth(), OutputType.Static);
     
        tb.addFlatStringField("communicationChannelID", current.getID());
        tb.addOutputConnector(new OutputConnectorBuilder("delivered", "Delivered/Sent").addTransitionCriteria(new TransitionCriteriaBuilder("node.action.deliverystatus", CriterionOperator.IsInSetOperator, new ArgumentBuilder("[ 'delivered', 'acknowledged' ]"))));
        tb.addOutputConnector(new OutputConnectorBuilder("failed", "Failed").addTransitionCriteria(new TransitionCriteriaBuilder("node.action.deliverystatus", CriterionOperator.IsInSetOperator, new ArgumentBuilder("[ 'failed', 'indeterminate', 'failedTimeout' ]"))));
        tb.addOutputConnector(new OutputConnectorBuilder("timeout", "Timeout").addTransitionCriteria(new TransitionCriteriaBuilder("evaluation.date", CriterionOperator.GreaterThanOrEqualOperator, new ArgumentBuilder("dateAdd(node.entryDate, " + current.getToolboxTimeout() + ", '" + current.getToolboxTimeoutUnit()+"')").setTimeUnit(TimeUnit.Instant))));
        tb.addOutputConnector(new OutputConnectorBuilder("unknown", "Unknown " + current.getProfileAddressField()).addTransitionCriteria(new TransitionCriteriaBuilder(current.getProfileAddressField(), CriterionOperator.IsNullOperator, null)));
        tb.addOutputConnector(new OutputConnectorBuilder("unknown_relationship", "UnknownRelationship").addTransitionCriteria(new TransitionCriteriaBuilder("unknown.relationship", CriterionOperator.EqualOperator, new ArgumentBuilder("true"))));

        // add manually all parameters common to any notification : contact type, from
        // address
        // node.parameter.contacttype
        ParameterBuilder parameterBuilder = new ParameterBuilder("node.parameter.contacttype", "Contact Type", CriterionDataType.StringCriterion, false, true, null);
        // contact type
        for (ContactType currentContactType : ContactType.values())
          {
            if(currentContactType==ContactType.Unknown) continue;//not for GUI use
            parameterBuilder.addAvailableValue(new AvailableValueStaticStringBuilder(currentContactType.getExternalRepresentation(), currentContactType.getDisplay()));
          }
        tb.addParameter(parameterBuilder);

        // node.parameter.fromaddress
        tb.addParameter(new ParameterBuilder("node.parameter.fromaddress", "From Address", CriterionDataType.StringCriterion, false, true, null).addAvailableValue(new AvailableValueDynamicBuilder("#dialog_source_address_" + current.getID() + "#")));

        // if the configuration of the communication channel allows the use the
        // templates that are created from template GUI, let add the following
        // parameter:
        if (current.allowGuiTemplate())
          {
            ParameterBuilder templateParameter = new ParameterBuilder("node.parameter.dialog_template", "Message Template", CriterionDataType.Dialog, false, false, null).addAvailableValue(new AvailableValueDynamicBuilder("#dialog_template_" + current.getID() + "#"));
            templateParameter.addFlatStringField("communicationChannelID", current.getID());
            tb.addParameter(templateParameter);
          }
        if (current.getJSONRepresentation().get("toolboxParameters") != null)
          {
            JSONArray paramsJSON = JSONUtilities.decodeJSONArray(current.getJSONRepresentation(), "toolboxParameters");
            for (int i = 0; i < paramsJSON.size(); i++)
              {
                JSONObject cp = (JSONObject) paramsJSON.get(i);
                String dataType = JSONUtilities.decodeString(cp, "dataType");
                if(dataType != null && dataType.startsWith("template_")) {
                  // this parameter must not be put into the toolbox as the GUI will retrieve it directly from the channel definition
                  log.warn("Channel " + current.getID() + " must not have a toolbox field of type " + dataType + " fieldID " + JSONUtilities.decodeString(cp, "id"));
                  continue;
                }
                parameterBuilder = new ParameterBuilder(JSONUtilities.decodeString(cp, "id"), JSONUtilities.decodeString(cp, "display"), CriterionDataType.fromExternalRepresentation(JSONUtilities.decodeString(cp, "dataType")), JSONUtilities.decodeBoolean(cp, "multiple"), JSONUtilities.decodeBoolean(cp, "mandatory"), cp.get("defaultValue"));
                tb.addParameter(parameterBuilder);
                // TODO EVPRO-146 Available Values
              }
          } 
        
        // add parameter relation to relationship
        tb.addParameter(new ParameterBuilder("node.parameter.relationship", "Hierarchy Relationship", CriterionDataType.StringCriterion, false, true, "customer").addAvailableValue(new AvailableValueDynamicBuilder("#supportedRelationshipsAndPartners#")));

        // Action:
        tb.setAction(new ActionBuilder("com.evolving.nglm.evolution.NotificationManager$ActionManager").addManagerClassConfigurationField("channelID", current.getID()).addManagerClassConfigurationField("moduleID", "1"));
        result.add(tb.build(0));
      }

    return result;
  }
}
