package com.evolving.nglm.evolution;

import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import org.apache.kafka.connect.data.Schema;
import org.apache.kafka.connect.data.SchemaAndValue;
import org.apache.kafka.connect.data.Struct;
import org.apache.kafka.connect.sink.SinkRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.evolving.nglm.core.Deployment;
import com.evolving.nglm.core.DeploymentCommon;
import com.evolving.nglm.core.RLMDateUtils;
import com.evolving.nglm.core.ReferenceDataReader;
import com.evolving.nglm.core.SimpleESSinkConnector;
import com.evolving.nglm.core.StreamESSinkTask;
import com.evolving.nglm.evolution.MailNotificationManager.MailNotificationManagerRequest;
import com.evolving.nglm.evolution.NotificationManager.NotificationManagerRequest;
import com.evolving.nglm.evolution.PushNotificationManager.PushNotificationManagerRequest;
import com.evolving.nglm.evolution.SMSNotificationManager.SMSNotificationManagerRequest;

public class NotificationSinkConnector extends SimpleESSinkConnector
{
  private final Logger log = LoggerFactory.getLogger(NotificationSinkConnector.class);

  /****************************************
  *
  *  taskClass
  *
  ****************************************/
  
  @Override public Class<NotificationSinkConnectorTask> taskClass()
  {
    return NotificationSinkConnectorTask.class;
  }

  /****************************************
  *
  *  taskClass
  *
  ****************************************/
  
  public static class NotificationSinkConnectorTask extends StreamESSinkTask<MessageDelivery>
  {
    private static DynamicCriterionFieldService dynamicCriterionFieldService;
    private static SegmentationDimensionService segmentationDimensionService;
    private static ReferenceDataReader<String,SubscriberGroupEpoch> subscriberGroupEpochReader;

    /*****************************************
    *
    *  start
    *
    *****************************************/

    @Override public void start(Map<String, String> taskConfig)
    {
      //
      //  super
      //

      super.start(taskConfig);
      
      subscriberGroupEpochReader = ReferenceDataReader.<String,SubscriberGroupEpoch>startReader("odrsinkconnector-subscriberGroupEpoch", Deployment.getBrokerServers(), Deployment.getSubscriberGroupEpochTopic(), SubscriberGroupEpoch::unpack);
      
      dynamicCriterionFieldService = new DynamicCriterionFieldService(Deployment.getBrokerServers(), "odrsinkconnector-dynamiccriterionfieldservice-" + getTaskNumber(), Deployment.getDynamicCriterionFieldTopic(), false);
      CriterionContext.initialize(dynamicCriterionFieldService);
      dynamicCriterionFieldService.start();      
      
      segmentationDimensionService = new SegmentationDimensionService(Deployment.getBrokerServers(), "odrsinkconnector-segmentationDimensionservice-" + Integer.toHexString((new Random()).nextInt(1000000000)), Deployment.getSegmentationDimensionTopic(), false);
      segmentationDimensionService.start();

    }

    /*****************************************
    *
    *  stop
    *
    *****************************************/

    @Override public void stop()
    {
      
      segmentationDimensionService.stop();

      //
      //  super
      //

      super.stop();
    }

    /*****************************************
    *
    *  unpackRecord
    *
    *****************************************/

    // closely duplicated in com.evolving.nglm.evolution.BDRSinkConnector.BDRSinkConnectorTask#unpackRecord
    @Override public MessageDelivery unpackRecord(SinkRecord sinkRecord) 
    {
      Object notificationValue = sinkRecord.value();
      Schema notificationValueSchema = sinkRecord.valueSchema();

      Struct valueStruct = (Struct) notificationValue;
      String type = valueStruct.getString("deliveryType");

      //  safety guard - return null
      if(type == null || type.equals("") || Deployment.getDeliveryManagers().get(type)==null ) {
        return null;
      }

      return (MessageDelivery) Deployment.getDeliveryManagers().get(type).getRequestSerde().unpack(new SchemaAndValue(notificationValueSchema, notificationValue));

    }

    /*****************************************
    *
    *  getDocumentIndexName
    *
    *****************************************/
    
    @Override
    protected String getDocumentIndexName(MessageDelivery notification)
    {
      Date timestamp;
      int tenantID;
      if (notification instanceof MailNotificationManagerRequest) {
        tenantID = ((MailNotificationManagerRequest) notification).getTenantID();
        timestamp = ((MailNotificationManagerRequest) notification).getCreationDate();
      }
      else if (notification instanceof SMSNotificationManagerRequest) {
        tenantID = ((SMSNotificationManagerRequest) notification).getTenantID();
        timestamp = ((SMSNotificationManagerRequest) notification).getCreationDate();
      }
      else if (notification instanceof NotificationManagerRequest) {
        tenantID = ((NotificationManagerRequest) notification).getTenantID();
        timestamp = ((NotificationManagerRequest) notification).getCreationDate();
      }
      else {
        tenantID = ((PushNotificationManagerRequest) notification).getTenantID();
        timestamp = ((PushNotificationManagerRequest) notification).getCreationDate();
      }

      String timeZone = DeploymentCommon.getDeployment(tenantID).getTimeZone();
      return this.getDefaultIndexName() + RLMDateUtils.formatDateISOWeek(timestamp, timeZone);
    }
    
    /*****************************************
    *
    *  getDocumentMap
    *
    *****************************************/
    
    @Override
    public Map<String, Object> getDocumentMap(MessageDelivery notification)
    {
      Map<String,Object> documentMap = new HashMap<String,Object>();
      
      if (notification instanceof MailNotificationManagerRequest) {
        MailNotificationManagerRequest mailNotification = (MailNotificationManagerRequest) notification;
        if(mailNotification.getOriginatingSubscriberID() != null && mailNotification.getOriginatingSubscriberID().startsWith(DeliveryManager.TARGETED))
          {
            // case where this is a delegated request and its response is for the original subscriberID, so this response must be ignored.
            return null;
          }
        documentMap.put("subscriberID", mailNotification.getSubscriberID());
        SinkConnectorUtils.putAlternateIDs(mailNotification.getAlternateIDs(), documentMap);
        documentMap.put("tenantID", mailNotification.getTenantID());
        documentMap.put("deliveryRequestID", mailNotification.getDeliveryRequestID());
        documentMap.put("originatingDeliveryRequestID", emptyStringIfNull(mailNotification.getOriginatingDeliveryRequestID()));
        documentMap.put("eventID", mailNotification.getEventID());
        documentMap.put("creationDate", mailNotification.getCreationDate()!=null?RLMDateUtils.formatDateForElasticsearchDefault(mailNotification.getCreationDate()):"");
        documentMap.put("deliveryDate", mailNotification.getDeliveryDate()!=null?RLMDateUtils.formatDateForElasticsearchDefault(mailNotification.getDeliveryDate()):"");
        documentMap.put("moduleID", mailNotification.getModuleID());
        documentMap.put("featureID", mailNotification.getFeatureID());
        documentMap.put("source", mailNotification.getFromAddress());
        documentMap.put("returnCode", mailNotification.getReturnCode());
        documentMap.put("returnCodeDetails", mailNotification.getMessageDeliveryReturnCodeDetails());
        documentMap.put("templateID", mailNotification.getTemplateID());
        documentMap.put("language", mailNotification.getLanguage());
        Map<String,List<String>> tags = new HashMap<>();
        tags.put("subjectTags", mailNotification.getSubjectTags());
        tags.put("textBodyTags", mailNotification.getTextBodyTags());
        tags.put("htmlBodyTags", mailNotification.getHtmlBodyTags());
        documentMap.put("tags", tags);
        String deliveryType = mailNotification.getDeliveryType();
        String channelID = Deployment.getDeliveryTypeCommunicationChannelIDMap().get(deliveryType);
        documentMap.put("channelID", channelID);
        documentMap.put("contactType", mailNotification.getContactType());
        documentMap.put("destination", mailNotification.getDestination());
        documentMap.put("origin", mailNotification.getMessageDeliveryOrigin());
        documentMap.put("stratum", mailNotification.getStatisticsSegmentsMap(subscriberGroupEpochReader, segmentationDimensionService));
      }
      else if (notification instanceof SMSNotificationManagerRequest) {
        SMSNotificationManagerRequest smsNotification = (SMSNotificationManagerRequest) notification;
        if(smsNotification.getOriginatingSubscriberID() != null && smsNotification.getOriginatingSubscriberID().startsWith(DeliveryManager.TARGETED))
          {
            // case where this is a delegated request and its response is for the original subscriberID, so this response must be ignored.
            return null;
          }
        documentMap.put("subscriberID", smsNotification.getSubscriberID());
        SinkConnectorUtils.putAlternateIDs(smsNotification.getAlternateIDs(), documentMap);
        documentMap.put("tenantID", smsNotification.getTenantID());
        documentMap.put("deliveryRequestID", smsNotification.getDeliveryRequestID());
        documentMap.put("originatingDeliveryRequestID", emptyStringIfNull(smsNotification.getOriginatingDeliveryRequestID()));
        documentMap.put("eventID", smsNotification.getEventID());
        documentMap.put("creationDate", smsNotification.getCreationDate()!=null?RLMDateUtils.formatDateForElasticsearchDefault(smsNotification.getCreationDate()):"");
        documentMap.put("deliveryDate", smsNotification.getDeliveryDate()!=null?RLMDateUtils.formatDateForElasticsearchDefault(smsNotification.getDeliveryDate()):"");
        documentMap.put("moduleID", smsNotification.getModuleID());
        documentMap.put("featureID", smsNotification.getFeatureID());
        documentMap.put("source", smsNotification.getSource());
        documentMap.put("returnCode", smsNotification.getReturnCode());
        documentMap.put("returnCodeDetails", smsNotification.getMessageDeliveryReturnCodeDetails());
        documentMap.put("templateID", smsNotification.getTemplateID());
        documentMap.put("language", smsNotification.getLanguage());
        documentMap.put("tags", smsNotification.getMessageTags());
        Map<String,List<String>> tags = new HashMap<>();
        tags.put("tags", smsNotification.getMessageTags());
        documentMap.put("tags", tags);
        String deliveryType = smsNotification.getDeliveryType();
        String channelID = Deployment.getDeliveryTypeCommunicationChannelIDMap().get(deliveryType);
        documentMap.put("channelID", channelID);
        documentMap.put("contactType", smsNotification.getContactType());
        documentMap.put("destination", smsNotification.getDestination());
        documentMap.put("origin", smsNotification.getMessageDeliveryOrigin());
        documentMap.put("stratum", smsNotification.getStatisticsSegmentsMap(subscriberGroupEpochReader, segmentationDimensionService));

      }
      else if (notification instanceof NotificationManagerRequest) {
        NotificationManagerRequest notifNotification = (NotificationManagerRequest) notification;
        if(notifNotification.getOriginatingSubscriberID() != null && notifNotification.getOriginatingSubscriberID().startsWith(DeliveryManager.TARGETED))
          {
            // case where this is a delegated request and its response is for the original subscriberID, so this response must be ignored.
            return null;
          }
        documentMap.put("subscriberID", notifNotification.getSubscriberID());
        SinkConnectorUtils.putAlternateIDs(notifNotification.getAlternateIDs(), documentMap);
        documentMap.put("tenantID", notifNotification.getTenantID());
        documentMap.put("deliveryRequestID", notifNotification.getDeliveryRequestID());
        documentMap.put("originatingDeliveryRequestID", emptyStringIfNull(notifNotification.getOriginatingDeliveryRequestID()));
        documentMap.put("eventID", notifNotification.getEventID());
        documentMap.put("creationDate", notifNotification.getCreationDate()!=null?RLMDateUtils.formatDateForElasticsearchDefault(notifNotification.getCreationDate()):"");
        documentMap.put("deliveryDate", notifNotification.getDeliveryDate()!=null?RLMDateUtils.formatDateForElasticsearchDefault(notifNotification.getDeliveryDate()):"");
        documentMap.put("moduleID", notifNotification.getModuleID());
        documentMap.put("featureID", notifNotification.getFeatureID());
        documentMap.put("source", notifNotification.getSourceAddressParam());
        documentMap.put("returnCode", notifNotification.getReturnCode());
        documentMap.put("returnCodeDetails", notifNotification.getMessageDeliveryReturnCodeDetails());
        documentMap.put("templateID", notifNotification.getTemplateID());
        documentMap.put("language", notifNotification.getLanguage());
        documentMap.put("tags", notifNotification.getTags());        
        String channelID = notifNotification.getChannelID();
        documentMap.put("channelID", channelID);
        documentMap.put("contactType", notifNotification.getContactType());
        documentMap.put("destination", notifNotification.getDestination());
        documentMap.put("noOfParts", notifNotification.extractLastSentCount());
        documentMap.put("origin", notifNotification.getMessageDeliveryOrigin());
        documentMap.put("stratum", notifNotification.getStatisticsSegmentsMap(subscriberGroupEpochReader, segmentationDimensionService));

      }
      else {
        PushNotificationManagerRequest pushNotification = (PushNotificationManagerRequest) notification;
        if(pushNotification.getOriginatingSubscriberID() != null && pushNotification.getOriginatingSubscriberID().startsWith(DeliveryManager.TARGETED))
          {
            // case where this is a delegated request and its response is for the original subscriberID, so this response must be ignored.
            return null;
          }
        documentMap.put("subscriberID", pushNotification.getSubscriberID());
        SinkConnectorUtils.putAlternateIDs(pushNotification.getAlternateIDs(), documentMap);
        documentMap.put("tenantID", pushNotification.getTenantID());
        documentMap.put("deliveryRequestID", pushNotification.getDeliveryRequestID());
        documentMap.put("originatingDeliveryRequestID", emptyStringIfNull(pushNotification.getOriginatingDeliveryRequestID()));
        documentMap.put("eventID", pushNotification.getEventID());
        documentMap.put("creationDate", pushNotification.getCreationDate()!=null?RLMDateUtils.formatDateForElasticsearchDefault(pushNotification.getCreationDate()):"");
        documentMap.put("deliveryDate", pushNotification.getDeliveryDate()!=null?RLMDateUtils.formatDateForElasticsearchDefault(pushNotification.getDeliveryDate()):"");
        documentMap.put("moduleID", pushNotification.getModuleID());
        documentMap.put("featureID", pushNotification.getFeatureID());
        documentMap.put("source", ""); // TODO SCH : what is the source of push notifications ?
        documentMap.put("returnCode", pushNotification.getReturnCode());
        documentMap.put("returnCodeDetails", pushNotification.getMessageDeliveryReturnCodeDetails());
        documentMap.put("templateID", pushNotification.getTemplateID());
        documentMap.put("language", pushNotification.getLanguage());
        documentMap.put("tags", pushNotification.getTags()); // TODO
        String deliveryType = pushNotification.getDeliveryType();
        String channelID = Deployment.getDeliveryTypeCommunicationChannelIDMap().get(deliveryType);
        documentMap.put("channelID", channelID);
        documentMap.put("contactType", pushNotification.getContactType());
        documentMap.put("destination", pushNotification.getDestination());
        documentMap.put("origin", pushNotification.getMessageDeliveryOrigin());
        documentMap.put("stratum", pushNotification.getStatisticsSegmentsMap(subscriberGroupEpochReader, segmentationDimensionService));
      }
      return documentMap;
    }

    private Object emptyStringIfNull(String originatingDeliveryRequestID)
    {
      return originatingDeliveryRequestID==null?"":originatingDeliveryRequestID;
    }
  }
}
