/*****************************************************************************
*
*  SMSNotificationManager.java
*
*****************************************************************************/

package com.evolving.nglm.evolution;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Random;

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
import com.evolving.nglm.core.SchemaUtilities;
import com.evolving.nglm.evolution.ContactPolicyCommunicationChannels.ContactType;
import com.evolving.nglm.evolution.DeliveryManager;
import com.evolving.nglm.evolution.DeliveryManagerDeclaration;
import com.evolving.nglm.evolution.DeliveryRequest;
import com.evolving.nglm.evolution.EvolutionEngine.EvolutionEventContext;
import com.evolving.nglm.evolution.SMSMessage;
import com.evolving.nglm.evolution.SubscriberEvaluationRequest;
import com.evolving.nglm.core.JSONUtilities;
import com.evolving.nglm.core.SystemTime;

public class SMSNotificationManager extends DeliveryManager implements Runnable
{
  /*****************************************
  *
  *  enum - status
  *
  *****************************************/

  public enum SMSMessageStatus
  {
    PENDING(10),
    SENT(1),
    NO_CUSTOMER_LANGUAGE(701),
    NO_CUSTOMER_CHANNEL(702),
    DELIVERED(0),
    EXPIRED(707),
    ERROR(706),
    UNDELIVERABLE(703),
    INVALID(704),
    QUEUE_FULL(705),
    RESCHEDULE(709),
    THROTTLING(23),
    UNKNOWN(999);
    private Integer returnCode;
    private SMSMessageStatus(Integer externalRepresentation) { this.returnCode = externalRepresentation; }
    public Integer getReturnCode() { return returnCode; }
    public static SMSMessageStatus fromReturnCode(Integer externalRepresentation) { for (SMSMessageStatus enumeratedValue : SMSMessageStatus.values()) { if (enumeratedValue.getReturnCode().equals(externalRepresentation)) return enumeratedValue; } return UNKNOWN; }
    public static SMSMessageStatus fromExternalRepresentation(String value) { for (SMSMessageStatus enumeratedValue : SMSMessageStatus.values()) { if (enumeratedValue.toString().equalsIgnoreCase(value)) return enumeratedValue; } return UNKNOWN; }
  }

  /*****************************************
  *
  *  conversion method
  *
  *****************************************/

  public DeliveryStatus getMessageStatus(SMSMessageStatus status)
  {
    switch(status)
      {
        case PENDING:
          return DeliveryStatus.Pending;
        case SENT:
        case DELIVERED:
          return DeliveryStatus.Delivered;
        case RESCHEDULE:
          return DeliveryStatus.Reschedule;
        case NO_CUSTOMER_LANGUAGE:
        case NO_CUSTOMER_CHANNEL:
        case ERROR:
        case UNDELIVERABLE:
        case INVALID:
        case QUEUE_FULL:
        default:
          return DeliveryStatus.Failed;
      }
  }

  /*****************************************
  *
  *  data
  *
  *****************************************/

  private int threadNumber = 5;   //TODO : make this configurable
  private SMSNotificationInterface smsNotification;
  private ArrayList<Thread> threads = new ArrayList<Thread>();
  private NotificationStatistics stats = null;
  private static String applicationID = "deliverymanager-notificationmanagersms";
  public String pluginName;
  private SubscriberMessageTemplateService subscriberMessageTemplateService;

  //
  //  logger
  //

  private static final Logger log = LoggerFactory.getLogger(SMSNotificationManager.class);
  
  /*****************************************
  *
  *  accessors
  *
  *****************************************/

  public SubscriberMessageTemplateService getSubscriberMessageTemplateService() { return subscriberMessageTemplateService; }

  /*****************************************
  *
  *  constructor
  *
  *****************************************/
  
  public SMSNotificationManager(String deliveryManagerKey, String pluginName, String pluginConfiguration)
  {
    //
    //  superclass
    //
    
    super(applicationID, deliveryManagerKey, Deployment.getBrokerServers(), SMSNotificationManagerRequest.serde(), Deployment.getDeliveryManagers().get(pluginName));
    
    //
    //  plugin class
    //

    String smsPluginClassName = JSONUtilities.decodeString(Deployment.getDeliveryManagers().get(pluginName).getJSONRepresentation(), "notificationPluginClass", true);
    JSONObject smsPluginConfiguration = JSONUtilities.decodeJSONObject(Deployment.getDeliveryManagers().get(pluginName).getJSONRepresentation(), "notificationPluginConfiguration", true);
    log.info("SMSNotificationManager: plugin instanciation : smsPluginClassName = "+smsPluginClassName);
    log.info("SMSNotificationManager: plugin instanciation : smsPluginConfiguration = "+smsPluginConfiguration);

    //
    //  service
    //

    subscriberMessageTemplateService = new SubscriberMessageTemplateService(Deployment.getBrokerServers(), "smsnotificationmanager-subscribermessagetemplateservice-" + deliveryManagerKey, Deployment.getSubscriberMessageTemplateTopic(), false);
    subscriberMessageTemplateService.start();
        
    //
    //  manager
    //
    
    this.pluginName = pluginName;
    try
      {
        smsNotification = (SMSNotificationInterface) (Class.forName(smsPluginClassName).newInstance());
        smsNotification.init(this, smsPluginConfiguration, pluginConfiguration, pluginName);
      }
    catch (InstantiationException | IllegalAccessException | IllegalArgumentException e)
      {
        log.error("SMSNotificationManager: could not create new instance of class " + smsPluginClassName, e);
        throw new RuntimeException("SMSNotificationManager: could not create new instance of class " + smsPluginClassName, e);
      }
    catch (ClassNotFoundException e)
      {
        log.error("SMSNotificationManager: could not find class " + smsPluginClassName, e);
        throw new RuntimeException("SMSNotificationManager: could not find class " + smsPluginClassName, e);
      }

    //
    // statistics
    //
    
    try
      {
        stats = new NotificationStatistics(applicationID, pluginName);
      }
    catch(Exception e)
      {
        log.error("SMSNotificationManager: could not load statistics ", e);
        throw new RuntimeException("SMSNotificationManager: could not load statistics  ", e);
      }
      
    //
    //  threads
    //
    
    for(int i = 0; i < threadNumber; i++)
      {
        threads.add(new Thread(this, "SMSNotificationManagerThread_"+i));
      }
    
    //
    //  startDelivery
    //
    
    startDelivery();
  }
  
  /*****************************************
  *
  *  class NotificationManagerRequest
  *
  *****************************************/
  
  public static class SMSNotificationManagerRequest extends DeliveryRequest
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
      schemaBuilder.name("service_smsnotification_request");
      schemaBuilder.version(SchemaUtilities.packSchemaVersion(commonSchema().version(),2));
      for (Field field : commonSchema().fields()) schemaBuilder.field(field.name(), field.schema());
      schemaBuilder.field("destination", Schema.STRING_SCHEMA);
      schemaBuilder.field("source", Schema.STRING_SCHEMA);
      schemaBuilder.field("language", Schema.STRING_SCHEMA);
      schemaBuilder.field("templateID", Schema.STRING_SCHEMA);
      schemaBuilder.field("messageTags", SchemaBuilder.array(Schema.STRING_SCHEMA));
      schemaBuilder.field("confirmationExpected", Schema.BOOLEAN_SCHEMA);
      schemaBuilder.field("restricted", SchemaBuilder.bool().defaultValue(false).schema());
      schemaBuilder.field("flashSMS", Schema.BOOLEAN_SCHEMA);
      schemaBuilder.field("returnCode", Schema.INT32_SCHEMA);
      schemaBuilder.field("returnCodeDetails", Schema.OPTIONAL_STRING_SCHEMA);
      schema = schemaBuilder.build();
    };

    //
    //  serde
    //

    private static ConnectSerde<SMSNotificationManagerRequest> serde = new ConnectSerde<SMSNotificationManagerRequest>(schema, false, SMSNotificationManagerRequest.class, SMSNotificationManagerRequest::pack, SMSNotificationManagerRequest::unpack);

    //
    //  accessor
    //

    public static Schema schema() { return schema; }
    public static ConnectSerde<SMSNotificationManagerRequest> serde() { return serde; }
    public Schema subscriberStreamEventSchema() { return schema(); }

    /*****************************************
    *
    *  data
    *
    *****************************************/

    private String destination;
    private String source;
    private String language;
    private String templateID;
    private List<String> messageTags;
    private boolean confirmationExpected;
    private boolean restricted;
    private boolean flashSMS;
    private SMSMessageStatus status;
    private int returnCode;
    private String returnCodeDetails;

    //
    //  accessors
    //

    public String getDestination() { return destination; }
    public String getSource() { return source; }
    public String getLanguage() { return language; }
    public String getTemplateID() { return templateID; }
    public List<String> getMessageTags() { return messageTags; }
    public boolean getConfirmationExpected() { return confirmationExpected; }
    public boolean getRestricted() { return restricted; }
    public boolean getFlashSMS() { return flashSMS; }
    public SMSMessageStatus getMessageStatus() { return status; }
    public int getReturnCode() { return returnCode; }
    public String getReturnCodeDetails() { return returnCodeDetails; }

    //
    //  abstract
    //

    @Override public Integer getActivityType() { return ActivityType.Messages.getExternalRepresentation(); }

    //
    //  setters
    //

    public void setConfirmationExpected(boolean confirmationExpected) { this.confirmationExpected = confirmationExpected; }
    public void setRestricted(boolean restricted) { this.restricted = restricted; }
    public void setFlashSMS(boolean flashSMS) { this.flashSMS = flashSMS; }
    public void setMessageStatus(SMSMessageStatus status) { this.status = status; }
    public void setReturnCode(Integer returnCode) { this.returnCode = returnCode; }
    public void setReturnCodeDetails(String returnCodeDetails) { this.returnCodeDetails = returnCodeDetails; }
    
    /*****************************************
    *
    *  getText
    *
    *****************************************/

    public String getText(SubscriberMessageTemplateService subscriberMessageTemplateService)
    {
      SMSTemplate smsTemplate = (SMSTemplate) subscriberMessageTemplateService.getActiveSubscriberMessageTemplate(templateID, SystemTime.getCurrentTime());
      DialogMessage dialogMessage = (smsTemplate != null) ? smsTemplate.getMessageText() : null;
      String text = (dialogMessage != null) ? dialogMessage.resolve(language, messageTags) : null;
      return text;
    }

    /*****************************************
    *
    *  constructor
    *
    *****************************************/

    public SMSNotificationManagerRequest(EvolutionEventContext context, String deliveryType, String deliveryRequestSource, String destination, String source, String language, String templateID, List<String> messageTags)
    {
      super(context, deliveryType, deliveryRequestSource);
      this.destination = destination;
      this.source = source;
      this.language = language;
      this.templateID = templateID;
      this.messageTags = messageTags;
      this.status = SMSMessageStatus.PENDING;
      this.returnCode = status.getReturnCode();
      this.returnCodeDetails = null;
    }
    
    /*****************************************
    *
    *  constructor -- external
    *
    *****************************************/
    
    public SMSNotificationManagerRequest(JSONObject jsonRoot, DeliveryManagerDeclaration deliveryManager)
    {
      super(jsonRoot);
      this.destination = JSONUtilities.decodeString(jsonRoot, "destination", true);
      this.source = JSONUtilities.decodeString(jsonRoot, "source", true);
      this.language = JSONUtilities.decodeString(jsonRoot, "language", true);
      this.templateID = JSONUtilities.decodeString(jsonRoot, "templateID", true);
      this.messageTags = decodeMessageTags(JSONUtilities.decodeJSONArray(jsonRoot, "messageTags", new JSONArray()));
      this.status = SMSMessageStatus.PENDING;
      this.returnCode = SMSMessageStatus.PENDING.getReturnCode();
      this.returnCodeDetails = null;
    }

    /*****************************************
    *
    *  decodeMessageTags
    *
    *****************************************/

    private List<String> decodeMessageTags(JSONArray jsonArray)
    {
      List<String> messageTags = new ArrayList<String>();
      for (int i=0; i<jsonArray.size(); i++)
        {
          messageTags.add((String) jsonArray.get(i));
        }
      return messageTags;
    }

    /*****************************************
    *
    *  constructor -- unpack
    *
    *****************************************/

    private SMSNotificationManagerRequest(SchemaAndValue schemaAndValue, String destination, String source, String language, String templateID, List<String> messageTags, boolean confirmationExpected, boolean restricted, boolean flashSMS, SMSMessageStatus status, String returnCodeDetails)
    {
      super(schemaAndValue);
      this.destination = destination;
      this.source = source;
      this.language = language;
      this.templateID = templateID;
      this.messageTags = messageTags;
      this.confirmationExpected = confirmationExpected;
      this.restricted = restricted;
      this.flashSMS = flashSMS;
      this.status = status;
      this.returnCode = status.getReturnCode();
      this.returnCodeDetails = returnCodeDetails;
    }
    
    /*****************************************
    *
    *  constructor -- copy
    *
    *****************************************/

    private SMSNotificationManagerRequest(SMSNotificationManagerRequest smsNotificationManagerRequest)
    {
      super(smsNotificationManagerRequest);
      this.destination = smsNotificationManagerRequest.getDestination();
      this.source = smsNotificationManagerRequest.getSource();
      this.language = smsNotificationManagerRequest.getLanguage();
      this.templateID = smsNotificationManagerRequest.getTemplateID();
      this.messageTags = smsNotificationManagerRequest.getMessageTags();
      this.confirmationExpected = smsNotificationManagerRequest.getConfirmationExpected();
      this.restricted = smsNotificationManagerRequest.getRestricted();
      this.flashSMS = smsNotificationManagerRequest.getFlashSMS();
      this.status = smsNotificationManagerRequest.getMessageStatus();
      this.returnCode = smsNotificationManagerRequest.getReturnCode();
      this.returnCodeDetails = smsNotificationManagerRequest.getReturnCodeDetails();
    }

    /*****************************************
    *
    *  copy
    *
    *****************************************/

    public SMSNotificationManagerRequest copy()
    {
      return new SMSNotificationManagerRequest(this);
    }

    /*****************************************
    *
    *  pack
    *
    *****************************************/

    public static Object pack(Object value)
    {
      SMSNotificationManagerRequest notificationRequest = (SMSNotificationManagerRequest) value;
      Struct struct = new Struct(schema);
      packCommon(struct, notificationRequest);
      struct.put("destination", notificationRequest.getDestination());
      struct.put("source", notificationRequest.getSource());
      struct.put("language", notificationRequest.getLanguage());
      struct.put("templateID", notificationRequest.getTemplateID());
      struct.put("messageTags", notificationRequest.getMessageTags());
      struct.put("confirmationExpected", notificationRequest.getConfirmationExpected());
      struct.put("restricted", notificationRequest.getRestricted());
      struct.put("flashSMS", notificationRequest.getFlashSMS());
      struct.put("returnCode", notificationRequest.getReturnCode());
      struct.put("returnCodeDetails", notificationRequest.getReturnCodeDetails());
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

    public static SMSNotificationManagerRequest unpack(SchemaAndValue schemaAndValue)
    {
      //
      //  data
      //

      Schema schema = schemaAndValue.schema();
      Object value = schemaAndValue.value();
      Integer schemaVersion = (schema != null) ? SchemaUtilities.unpackSchemaVersion1(schema.version()) : null;

      //
      //  unpack
      //

      Struct valueStruct = (Struct) value;
      String destination = valueStruct.getString("destination");
      String source = valueStruct.getString("source");
      String language = valueStruct.getString("language");
      String templateID = valueStruct.getString("templateID");
      List<String> messageTags = (List<String>) valueStruct.get("messageTags");
      boolean confirmationExpected = valueStruct.getBoolean("confirmationExpected");
      boolean restricted = (schemaVersion >= 2) ? valueStruct.getBoolean("restricted") : false;
      boolean flashSMS = valueStruct.getBoolean("flashSMS");
      Integer returnCode = valueStruct.getInt32("returnCode");
      String returnCodeDetails = valueStruct.getString("returnCodeDetails");
      SMSMessageStatus status = SMSMessageStatus.fromReturnCode(returnCode);
      
      //
      //  return
      //

      return new SMSNotificationManagerRequest(schemaAndValue, destination, source, language, templateID, messageTags, confirmationExpected, restricted, flashSMS, status, returnCodeDetails);
    }
    
    /****************************************
    *
    *  presentation utilities
    *
    ****************************************/
    
    //
    //  addFieldsForGUIPresentation
    //

    @Override public void addFieldsForGUIPresentation(HashMap<String, Object> guiPresentationMap, SubscriberMessageTemplateService subscriberMessageTemplateService, SalesChannelService salesChannelService, JourneyService journeyService, OfferService offerService, ProductService productService, DeliverableService deliverableService, PaymentMeanService paymentMeanService)
    {
      
      Module module = Module.fromExternalRepresentation(getModuleID());
      guiPresentationMap.put(CUSTOMERID, getSubscriberID());
      guiPresentationMap.put(EVENTID, null);
      guiPresentationMap.put(MODULEID, getModuleID());
      guiPresentationMap.put(MODULENAME, module.toString());
      guiPresentationMap.put(FEATUREID, getFeatureID());
      guiPresentationMap.put(FEATURENAME, getFeatureName(module, getFeatureID(), journeyService, offerService));
      guiPresentationMap.put(SOURCE, getSource());
      guiPresentationMap.put(RETURNCODE, getReturnCode());
      guiPresentationMap.put(RETURNCODEDETAILS, SMSMessageStatus.fromReturnCode(getReturnCode()).toString());
      guiPresentationMap.put(NOTIFICATION_TEXT_BODY, getText(subscriberMessageTemplateService));
      guiPresentationMap.put(NOTIFICATION_CHANNEL, "SMS");
      guiPresentationMap.put(NOTIFICATION_RECIPIENT, getDestination());
    }
    
    //
    //  addFieldsForThirdPartyPresentation
    //

    @Override public void addFieldsForThirdPartyPresentation(HashMap<String, Object> thirdPartyPresentationMap, SubscriberMessageTemplateService subscriberMessageTemplateService, SalesChannelService salesChannelService, JourneyService journeyService, OfferService offerService, ProductService productService, DeliverableService deliverableService, PaymentMeanService paymentMeanService)
    {
      Module module = Module.fromExternalRepresentation(getModuleID());
      thirdPartyPresentationMap.put(CUSTOMERID, getSubscriberID());
      thirdPartyPresentationMap.put(EVENTID, null);
      thirdPartyPresentationMap.put(MODULEID, getModuleID());
      thirdPartyPresentationMap.put(MODULENAME, module.toString());
      thirdPartyPresentationMap.put(FEATUREID, getFeatureID());
      thirdPartyPresentationMap.put(FEATURENAME, getFeatureName(module, getFeatureID(), journeyService, offerService));
      thirdPartyPresentationMap.put(SOURCE, getSource());
      thirdPartyPresentationMap.put(RETURNCODE, getReturnCode());
      thirdPartyPresentationMap.put(RETURNCODEDETAILS, SMSMessageStatus.fromReturnCode(getReturnCode()).toString());
      thirdPartyPresentationMap.put(NOTIFICATION_TEXT_BODY, getText(subscriberMessageTemplateService));
      thirdPartyPresentationMap.put(NOTIFICATION_CHANNEL, "SMS");
      thirdPartyPresentationMap.put(NOTIFICATION_RECIPIENT, getDestination());
    }
    
    @Override
    public Date getEffectiveDeliveryTime(Date now)
    {
      Date deliveryDate = null;
      
      CommunicationChannelService communicationChannelService = new CommunicationChannelService(Deployment.getBrokerServers(), "smsnotificationmanager-communicationchannelservice-" + Integer.toHexString((new Random()).nextInt(1000000000)), Deployment.getCommunicationChannelTopic(), false);
      CommunicationChannel channel = (CommunicationChannel) communicationChannelService.getActiveCommunicationChannel("sms", now);
      
      if(channel != null)
        {
          deliveryDate = communicationChannelService.getEffectiveDeliveryTime(channel.getGUIManagedObjectID(), now);
        }
      
      if(deliveryDate != null && !deliveryDate.equals(now))
        {
          return deliveryDate;
        }
      
      CommunicationChannelBlackoutService blackoutService = new CommunicationChannelBlackoutService(Deployment.getBrokerServers(), "smsnotificationmanager-communicationchannelblackoutservice-" + Integer.toHexString((new Random()).nextInt(1000000000)), Deployment.getCommunicationChannelBlackoutTopic(), false);
      CommunicationChannelBlackoutPeriod blackoutPeriod = blackoutService.getActiveCommunicationChannelBlackout("blackoutPeriod", now);
      if(blackoutPeriod != null)
        {
          deliveryDate = blackoutService.getEffectiveDeliveryTime(blackoutPeriod.getGUIManagedObjectID(), now);
        }
      
      return deliveryDate;
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
    private String moduleID;

    /*****************************************
    *
    *  constructor
    *
    *****************************************/

    public ActionManager(JSONObject configuration)
    {
      super(configuration);
      this.moduleID = JSONUtilities.decodeString(configuration, "moduleID", true);
      this.deliveryType = JSONUtilities.decodeString(configuration, "deliveryType", true);
    }

    /*****************************************
    *
    *  execute
    *
    *****************************************/

    @Override public DeliveryRequest executeOnEntry(EvolutionEventContext evolutionEventContext, SubscriberEvaluationRequest subscriberEvaluationRequest)
    {
      /*****************************************
      *
      *  parameters
      *
      *****************************************/

      SMSMessage smsMessage = (SMSMessage) CriterionFieldRetriever.getJourneyNodeParameter(subscriberEvaluationRequest,"node.parameter.message");
      ContactType contactType = ContactType.fromExternalRepresentation((String) CriterionFieldRetriever.getJourneyNodeParameter(subscriberEvaluationRequest,"node.parameter.contacttype"));
      String source = (CriterionFieldRetriever.getJourneyNodeParameter(subscriberEvaluationRequest,"node.parameter.source") != null) ? (String) CriterionFieldRetriever.getJourneyNodeParameter(subscriberEvaluationRequest,"node.parameter.source") : "TBD";
      boolean confirmationExpected = (Boolean) CriterionFieldRetriever.getJourneyNodeParameter(subscriberEvaluationRequest,"node.parameter.confirmationexpected");
      boolean restricted = contactType.getRestricted();
      boolean flashSMS = (Boolean) CriterionFieldRetriever.getJourneyNodeParameter(subscriberEvaluationRequest,"node.parameter.flashsms");

      /*****************************************
      *
      *  request arguments
      *
      *****************************************/

      String deliveryRequestSource = subscriberEvaluationRequest.getJourneyState().getJourneyID();
      String msisdn = ((SubscriberProfile) subscriberEvaluationRequest.getSubscriberProfile()).getMSISDN();
      String language = subscriberEvaluationRequest.getLanguage();
      SMSTemplate baseTemplate = (SMSTemplate) smsMessage.resolveTemplate(evolutionEventContext);
      SMSTemplate template = (baseTemplate != null) ? (SMSTemplate) baseTemplate.getReadOnlyCopy(evolutionEventContext) : null;
      DialogMessage messageText = (template != null) ? template.getMessageText() : null;
      List<String> messageTags = (messageText != null) ? messageText.resolveMessageTags(subscriberEvaluationRequest, language) : new ArrayList<String>();

      /*****************************************
      *
      *  request
      *
      *****************************************/

      SMSNotificationManagerRequest request = null;
      if (template != null && msisdn != null)
        {
          request = new SMSNotificationManagerRequest(evolutionEventContext, deliveryType, deliveryRequestSource, msisdn, source, language, template.getSMSTemplateID(), messageTags);
          request.setModuleID(moduleID);
          request.setFeatureID(deliveryRequestSource);
          request.setConfirmationExpected(confirmationExpected);
          request.setRestricted(restricted);
          request.setFlashSMS(flashSMS);
          request.setDeliveryPriority(contactType.getDeliveryPriority());
        }
      else if (template != null)
        {
          log.info("SMSNotificationManager unknown MSISDN for subscriberID {}" + subscriberEvaluationRequest.getSubscriberProfile().getSubscriberID());
        }
      else
        {
          log.info("SMSNotificationManager unknown template {}" + smsMessage.getSubscriberMessageTemplateID());
        }

      /*****************************************
      *
      *  return
      *
      *****************************************/

      return request;
    }
  }

  /*****************************************
  *
  *  run
  *
  *****************************************/

  public void run()
  {
    while (isProcessing())
      {
        /*****************************************
        *
        *  nextRequest
        *
        *****************************************/
        
        DeliveryRequest deliveryRequest = nextRequest();
        Date now = SystemTime.getCurrentTime();
        
        log.debug("SMSNotificationManagerRequest run deliveryRequest;" + deliveryRequest);

        SMSNotificationManagerRequest smsRequest = (SMSNotificationManagerRequest)deliveryRequest;
        if(smsRequest.getRestricted()) 
          {
            Date effectiveDeliveryTime = smsRequest.getEffectiveDeliveryTime(now);
            if(effectiveDeliveryTime.equals(now))
              {
                smsNotification.send(smsRequest);
              }
            else
              {
                smsRequest.setRescheduledDate(effectiveDeliveryTime);
                smsRequest.setDeliveryStatus(DeliveryStatus.Reschedule);
                smsRequest.setReturnCode(SMSMessageStatus.RESCHEDULE.getReturnCode());
                smsRequest.setMessageStatus(SMSMessageStatus.RESCHEDULE);
                completeDeliveryRequest(smsRequest);
              }
          }
        else
          {
            smsNotification.send(smsRequest);
          }
      }
  }

  /*****************************************
  *
  *  updateDeliveryRequest
  *
  *****************************************/

  public void updateDeliveryRequest(DeliveryRequest deliveryRequest)
  {
    log.debug("SMSNotificationManager.updateDeliveryRequest(deliveryRequest="+deliveryRequest+")");
    updateRequest(deliveryRequest);
  }
  
  /*****************************************
  *
  *  completeDeliveryRequest
  *
  *****************************************/

  public void completeDeliveryRequest(DeliveryRequest deliveryRequest)
  {
    log.debug("SMSNotificationManager.updateDeliveryRequest(deliveryRequest="+deliveryRequest+")");
    completeRequest(deliveryRequest);
    stats.updateMessageCount(pluginName, 1, deliveryRequest.getDeliveryStatus());
  }

  /*****************************************
  *
  *  submitCorrelatorUpdateDeliveryRequest
  *
  *****************************************/

  public void submitCorrelatorUpdateDeliveryRequest(String correlator, JSONObject correlatorUpdate)
  {
    log.debug("SMSNotificationManager.submitCorrelatorUpdateDeliveryRequest(correlator="+correlator+", correlatorUpdate="+correlatorUpdate.toJSONString()+")");
    submitCorrelatorUpdate(correlator, correlatorUpdate);
  }
  
  /*****************************************
  *
  *  processCorrelatorUpdate
  *
  *****************************************/

  @Override protected void processCorrelatorUpdate(DeliveryRequest deliveryRequest, JSONObject correlatorUpdate)
  {
    int result = JSONUtilities.decodeInteger(correlatorUpdate, "result", true);
    SMSNotificationManagerRequest smsRequest = (SMSNotificationManagerRequest) deliveryRequest;
    if (smsRequest != null)
      {
        log.debug("SMSNotificationManager.processCorrelatorUpdate(deliveryRequest="+deliveryRequest.toString()+", correlatorUpdate="+correlatorUpdate.toJSONString()+")");
        smsRequest.setMessageStatus(SMSMessageStatus.fromReturnCode(result));
        smsRequest.setDeliveryStatus(getMessageStatus(smsRequest.getMessageStatus()));
        smsRequest.setDeliveryDate(SystemTime.getCurrentTime());
        completeRequest(smsRequest);
      }
  }

  /*****************************************
  *
  *  shutdown
  *
  *****************************************/

  @Override protected void shutdown()
  {
    log.info("SMSNotificationManager:  shutdown");
  }
  
  /*****************************************
  *
  *  main
  *
  *****************************************/

  public static void main(String[] args)
  {
    log.info("SMSNotificationManager: recieved " + args.length + " args");
    for(String arg : args)
      {
        log.info("SMSNotificationManager: arg " + arg);
      }
    
    //
    //  configuration
    //

    String deliveryManagerKey = args[0];
    String pluginName = args[1];
    String pluginConfiguration = args[2];

    //
    //  instance  
    //
    
    log.info("SMSNotificationManager: configuration " + Deployment.getDeliveryManagers());

    SMSNotificationManager manager = new SMSNotificationManager(deliveryManagerKey, pluginName, pluginConfiguration);

    //
    //  run
    //

    manager.run();
  }
}
