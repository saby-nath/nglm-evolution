package com.evolving.nglm.evolution;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.ExecutionException;

import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.elasticsearch.ElasticsearchStatusException;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.evolving.nglm.core.ConnectSerde;
import com.evolving.nglm.core.Deployment;
import com.evolving.nglm.core.JSONUtilities;
import com.evolving.nglm.core.LongKey;
import com.evolving.nglm.core.ServerRuntimeException;
import com.evolving.nglm.core.StringKey;
import com.evolving.nglm.core.SystemTime;
import com.evolving.nglm.evolution.CommodityDeliveryManager.CommodityDeliveryRequest;
import com.evolving.nglm.evolution.EvaluationCriterion.CriterionException;
import com.evolving.nglm.evolution.GUIManager.GUIManagerException;
import com.evolving.nglm.evolution.SubscriberPredictions.SubscriberPredictionsRequest;
import com.evolving.nglm.evolution.elasticsearch.ElasticsearchClientAPI;

/**
 * Singleton class. Should be instantiated once.
 * 
 * Organize predictions by: 
 * - reading scheduling settings from PredictionOrder (GUIManagedObject) 
 * - scheduling jobs accordingly that extract a sub-list of subscribers and push them in subscriberpredictionsrequest topic
 */
public class PredictionOrganizer
{
  private static final Logger log = LoggerFactory.getLogger(PredictionOrganizer.class);
  
  /*****************************************
  *
  * SubscriberPredictionsRequest producer
  *
  *****************************************/
  private static int BATCH_SIZE = 1000;
  
  private static String requestTopic = Deployment.getSubscriberPredictionsRequestTopic(); // Topic(SubscriberID, List<SubscriberID>)  
  private static ConnectSerde<StringKey> keySerde = StringKey.serde();
  private static ConnectSerde<SubscriberPredictionsRequest> valueSerde = SubscriberPredictionsRequest.serde();
  private static KafkaProducer<byte[], byte[]> kafkaProducer;
  static {
    Properties producerProperties = new Properties();
    producerProperties.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, Deployment.getBrokerServers());
    producerProperties.put(ProducerConfig.ACKS_CONFIG, "all");
    producerProperties.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, "org.apache.kafka.common.serialization.ByteArraySerializer");
    producerProperties.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, "org.apache.kafka.common.serialization.ByteArraySerializer");
    kafkaProducer = new KafkaProducer<byte[], byte[]>(producerProperties);
  }
  
  public static void send(String predictionID, Set<String> subscriberIDs) {
    try {
      kafkaProducer.send(new ProducerRecord<byte[], byte[]>(requestTopic, 
          keySerde.serializer().serialize(requestTopic, new StringKey(predictionID)),
          valueSerde.serializer().serialize(requestTopic, new SubscriberPredictionsRequest(subscriberIDs)))).get();
    } 
    catch (InterruptedException|ExecutionException e) {
      log.error("Error while trying to push a new prediction request.",e);
    }
  }
  
  /*****************************************
  *
  * Job scheduler
  *
  *****************************************/
  private static JobScheduler predictionJobScheduler;
  private static ElasticsearchClientAPI elasticsearchRestClient;
  private static PredictionOrderService predictionOrderService;

  // Remember : those orders are references from the GUIService, but in fact GUIManagedObject are never modified
  // they are always re-created from ser/de. Theoretically we should deep copy them to be able to detect any modification.
  // But here we can only keep the same reference as the one from GUIService because when a modification occur, 
  // the complete object is replaced.
  // We can directly check if references are equals to detect any modification
  private static Map<String, PredictionOrder> predictionOrders; // all orders               Map(PredictionOrderID, PredictionOrder)
  private static Map<String, ScheduledJob> predictionOrderJobs; // all corresponding jobs   Map(PredictionOrderID, ScheduledJob)
  
  // StartDate: date when this "version" of the job ran for the first time - for "every duration" management.
  // StartDate will be initialize when the job run for the first time, it will be null before.
  private static Map<String, Date> predictionOrderStartDate;
  
  private static void removeScheduledJob(String predictionOrderID) {
    ScheduledJob oldJob = predictionOrderJobs.remove(predictionOrderID);
    predictionOrderStartDate.remove(predictionOrderID);
    log.info("Prediction order (ID="+predictionOrderID+") has been removed.");
    if(oldJob != null) {
      log.info("Removing job ("+oldJob.jobName+") from scheduling.");
      predictionJobScheduler.deschedule(oldJob);
    }
  }
  
  private static void updateScheduledJob(String predictionOrderID, PredictionOrder predictionOrder) {
    ScheduledJob oldJob = predictionOrderJobs.remove(predictionOrderID);
    predictionOrderStartDate.remove(predictionOrderID);
    log.info("Prediction order (ID="+predictionOrderID+") has been modified.");
    if(oldJob != null) {
      log.info("Removing job ("+oldJob.jobName+") from scheduling.");
      predictionJobScheduler.deschedule(oldJob);
    }
    
    int tenantID = predictionOrder.getTenantID();
    String cronScheduler;
    try {
      cronScheduler = predictionOrder.retrieveCronFrequency();
    } catch (GUIManagerException e) {
      StringWriter stackTraceWriter = new StringWriter();
      e.printStackTrace(new PrintWriter(stackTraceWriter, true));
      log.error("Error while scheduling a new job: "+stackTraceWriter.toString()+"");
      return;
    }
    
    ScheduledJobConfiguration config = new ScheduledJobConfiguration("PredictionRequest-"+predictionOrderID, 
        ScheduledJobConfiguration.Type.PredictionRequest, 
        true, // enabled 
        false, // schedule at restart
        cronScheduler,
        tenantID,
        Deployment.getDeployment(tenantID).getTimeZone());
    
    String orderID = predictionOrderID;
    ScheduledJob newJob = new ScheduledJob(config)
    {
      @Override protected void run() { predictionJobRun(orderID); }
    };
    
    // replace old job by new one
    predictionOrderJobs.put(predictionOrderID, newJob);
    predictionOrders.put(predictionOrderID, predictionOrder);
    log.info("Scheduling new job ("+newJob.jobName+").");
    predictionJobScheduler.schedule(newJob);
  }
  
  public static void start(ElasticsearchClientAPI es, PredictionOrderService orderService) {
    predictionJobScheduler = new JobScheduler("Prediction jobs");
    elasticsearchRestClient = es;
    predictionOrderService = orderService;
    predictionOrders = new HashMap<String, PredictionOrder>();
    predictionOrderJobs = new HashMap<String, ScheduledJob>();
    predictionOrderStartDate = new HashMap<String, Date>();

    //
    // Wake-up job: look if there is any new PredictionOrder - this mechanism could be replace by a listener in GUIService (if we had a modifiedListener and newListener)
    //
    ScheduledJobConfiguration config = new ScheduledJobConfiguration("PredictionWakeUp", 
        ScheduledJobConfiguration.Type.PredictionWakeUp, 
        true, // enabled 
        true, // schedule at restart
        "* * * * *", // Every minute // TODO CHANGE AFTER DEV ?
        0,
        Deployment.getDefault().getTimeZone());
    
    predictionJobScheduler.schedule(new ScheduledJob(config)
      {
        @Override
        protected void run()
        {
          Collection<PredictionOrder> orders = predictionOrderService.getActivePredictionOrders(SystemTime.getCurrentTime(), 0); // Tenat 0: retrieve all
          Map<String, PredictionOrder> predictionOrdersCopy = new HashMap<String, PredictionOrder>(predictionOrders); // to keep track removed ones.
          
          //
          // Check if any changes since last wake up
          //
          
          // Check for new / modified
          for(PredictionOrder order: orders) {
            PredictionOrder current = predictionOrdersCopy.remove(order.getGUIManagedObjectID());
            if(order != current) { // This is special, and due to the way GUIManagedObjects work (see comment above for predictionOrders variable)
              updateScheduledJob(order.getGUIManagedObjectID(), order);
            }
          }

          // Check for removed (remaining)
          for(String orderID: predictionOrdersCopy.keySet()) {
            removeScheduledJob(orderID);
          }
        }
      });
    
    predictionJobScheduler.runScheduler();
  }
  
  public static void close() {
    predictionJobScheduler.stop();
    kafkaProducer.close();
  }

  /*****************************************
  *
  * Prediction Job
  *
  *****************************************/
  //
  // predictionJobRun
  //
  public static void predictionJobRun(String orderID) {
    PredictionOrder order = predictionOrders.get(orderID);
    if(order != null) {
      try {
        if(skipRun(orderID, order)) {
          return;
        }
        
        List<String> subscribers = getSubscribers(order.getTargetCriteria(), order.getTenantID(), elasticsearchRestClient);
        Set<String> batch = new HashSet<String>();
        
        for(String subscriberID: subscribers) {
          batch.add(subscriberID);
          if(batch.size() >= BATCH_SIZE) {
            //
            // push
            //
            send(order.getGUIManagedObjectID(), batch);
            batch = new HashSet<String>();
          }
        }
        
        //
        // push remaining
        //
        if(batch.size() > 0) {
          send(orderID, batch);
        }
      } 
      catch (ElasticsearchStatusException | IOException | GUIManagerException e) {
        log.error("Unable to retrieve list of subscribers matching target criteria {}", e.getMessage());
      }
    }
    else {
      log.error("Something wrong happened, lost order reference.");
    }
  }
  
  /**
   * skipRun
   * Jobs are scheduled every day/week/month by CRON settings but, the "real" scheduling can be "every 3 weeks". 
   * This cannot be managed by CRON directly (do not fit cron standard), therefore, before each run we need to 
   * check if we skip it or not
   */
  public static boolean skipRun(String orderID, PredictionOrder predictionOrder) {
    Date now = SystemTime.getCurrentTime();
    Date start = predictionOrderStartDate.get(orderID);
    if(start == null) {
      //
      // First run
      //
      predictionOrderStartDate.put(orderID, now);
      return false;
    }
    
    return !predictionOrder.isValidRun(start, now);
  }
  
  // 
  // Elasticsearch search request
  //
  /**
   * Retrieve the list of all subscribers matching a list of EvaluationCriterion
   */
  public static List<String> getSubscribers(List<EvaluationCriterion> criteriaList, int tenantID, ElasticsearchClientAPI elasticsearch) throws IOException, ElasticsearchStatusException, GUIManagerException {
    BoolQueryBuilder query = EvaluationCriterion.esCountMatchCriteriaGetQuery(criteriaList);
    query.filter().add(QueryBuilders.termQuery("tenantID", tenantID)); // filter to keep only tenant related subscribers.
    
    SearchSourceBuilder searchSourceRequest = new SearchSourceBuilder().query(query);
    
    log.info(searchSourceRequest.toString());  // TODO debug

    List<SearchHit> hits = elasticsearch.getESHits(new SearchRequest("subscriberprofile").source(searchSourceRequest));
    
    List<String> result = new LinkedList<String>();
    for (SearchHit hit : hits) {
      Map<String, Object> esFields = hit.getSourceAsMap();
      if((String) esFields.get("subscriberID") != null) {
        result.add((String) esFields.get("subscriberID"));
      }
    }
    
    log.info(result.toString()); // TODO debug
    
    return result;
  }
}

