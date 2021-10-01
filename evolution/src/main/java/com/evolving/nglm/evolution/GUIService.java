package com.evolving.nglm.evolution;

import com.evolving.nglm.core.*;
import com.evolving.nglm.evolution.GUIManagedObject.ElasticSearchMapping;
import com.evolving.nglm.evolution.elasticsearch.ElasticsearchClientAPI;

import org.apache.kafka.common.PartitionInfo;
import org.apache.kafka.common.errors.RecordTooLargeException;
import org.apache.kafka.common.errors.TimeoutException;
import org.apache.kafka.common.utils.Bytes;
import org.json.simple.JSONObject;

import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRebalanceListener;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.errors.SerializationException;
import org.apache.kafka.common.errors.WakeupException;
import org.elasticsearch.action.delete.DeleteRequest;
import org.elasticsearch.action.update.UpdateRequest;
import org.elasticsearch.client.RequestOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class GUIService {
  /*****************************************
   *
   * configuration
   *
   *****************************************/

  //
  // logger
  //

  private static final Logger log = LoggerFactory.getLogger(GUIService.class);

  //
  // elasticsearch ElasticsearchClientAPI
  //
  private ElasticsearchClientAPI elasticsearch;

  //
  // statistics
  //

  private GUIServiceStatistics serviceStatistics = null;

  /*****************************************
   *
   * data
   *
   *****************************************/

  private volatile boolean stopRequested = false;
  private ConcurrentHashMap<String, Date> forFullDeletionObjects = new ConcurrentHashMap<>();// to trigger object full
                                                                                             // delete on topic (Date is
                                                                                             // "deleted" date)
  private HashMap<Integer, ConcurrentHashMap<String, GUIManagedObject>> storedPerTenantGUIManagedObjects = new HashMap<>();
  private HashMap<Integer, ConcurrentHashMap<String, GUIManagedObject>> availablePerTenantGUIManagedObjects = new HashMap<>();
  private HashMap<Integer, ConcurrentHashMap<String, GUIManagedObject>> activePerTenantGUIManagedObjects = new HashMap<>();
  // store objects that should have been "active", but are not because an update
  // "suspend" them, either from direct normal GUI call "suspend" or an invalid
  // update, but they were active at some point to end up there
  private HashMap<Integer, ConcurrentHashMap<String, GUIManagedObject>> interruptedPerTenantGUIManagedObjects = new HashMap();
  private Date lastUpdate = SystemTime.getCurrentTime();
  private TreeSet<ScheduleEntry> schedule = new TreeSet<ScheduleEntry>();
  private String guiManagedObjectTopic;
  private String guiAuditTopic = Deployment.getGUIAuditTopic();

  private KafkaProducer<byte[], byte[]> kafkaProducer;

  // singleton instance lazy init for all services
  private static class KafkaProducerInstanceHolder {
    private static KafkaProducer<byte[], byte[]> kafkaProducer;
    static {
      Properties producerProperties = new Properties();
      producerProperties.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, Deployment.getBrokerServers());
      producerProperties.put(ProducerConfig.ACKS_CONFIG, "all");
      producerProperties.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG,
          "org.apache.kafka.common.serialization.ByteArraySerializer");
      producerProperties.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG,
          "org.apache.kafka.common.serialization.ByteArraySerializer");
      kafkaProducer = new KafkaProducer<byte[], byte[]>(producerProperties);
    }

    private static KafkaProducer<byte[], byte[]> getKafkaProducer() {
      return kafkaProducer;
    }
  }

  private Properties guiManagedObjectsConsumerProperties;
  private KafkaConsumer<byte[], byte[]> guiManagedObjectsConsumer;
  private Map<TopicPartition, Long> consumedOffsets;
  private boolean masterService;
  Thread schedulerThread = null;
  Thread listenerThread = null;
  Thread guiManagedObjectReaderThread = null;
  private List<GUIManagedObjectListener> guiManagedObjectListeners = new ArrayList<GUIManagedObjectListener>();
  private boolean notifyOnSignificantChange;
  private BlockingQueue<GUIManagedObject> listenerQueue = new LinkedBlockingQueue<GUIManagedObject>();
  private int lastGeneratedObjectID = 0;
  private String putAPIString;
  private String removeAPIString;

  //
  // services usable only by the GUIManager (with a special start)
  //

  private JourneyService journeyService;
  private TargetService targetService;
  private JourneyObjectiveService journeyObjectiveService;
  private ContactPolicyService contactPolicyService;

  //
  // serdes
  //

  private ConnectSerde<StringKey> stringKeySerde = StringKey.serde();
  private ConnectSerde<GUIManagedObject> guiManagedObjectSerde = GUIManagedObject.commonSerde();
  private ConnectSerde<GUIManagedObject> incompleteObjectSerde = GUIManagedObject.incompleteObjectSerde();
  private ConnectSerde<GUIObjectAudit> guiObjectAuditSerde = GUIObjectAudit.serde();

  /*****************************************
   *
   * accessors
   *
   *****************************************/

  public synchronized Date getLastUpdate() {
    return lastUpdate;
  }

  /*****************************************
   *
   * constructor
   *
   *****************************************/

  // to remove once cleaned up
  @Deprecated // groupID not needed
  protected GUIService(String bootstrapServers, String serviceName, String groupID, String guiManagedObjectTopic,
      boolean masterService, GUIManagedObjectListener guiManagedObjectListener, String putAPIString,
      String removeAPIString, boolean notifyOnSignificantChange) {
    this(bootstrapServers, serviceName, guiManagedObjectTopic, masterService, guiManagedObjectListener, putAPIString,
        removeAPIString, notifyOnSignificantChange);
  }

  protected GUIService(String bootstrapServers, String serviceName, String guiManagedObjectTopic, boolean masterService,
      GUIManagedObjectListener guiManagedObjectListener, String putAPIString, String removeAPIString,
      boolean notifyOnSignificantChange) {
    //
    // configuration
    //

    this.guiManagedObjectTopic = guiManagedObjectTopic;
    this.masterService = masterService;
    this.putAPIString = putAPIString;
    this.removeAPIString = removeAPIString;
    this.notifyOnSignificantChange = notifyOnSignificantChange;

    //
    // listener
    //

    if (guiManagedObjectListener != null) {
      guiManagedObjectListeners.add(guiManagedObjectListener);
    }

    //
    // statistics
    //

    if (masterService) {
      try {
        this.serviceStatistics = new GUIServiceStatistics(serviceName);
        kafkaProducer = KafkaProducerInstanceHolder.getKafkaProducer();
      } catch (ServerException e) {
        throw new ServerRuntimeException("Could not initialize statistics");
      }
    }

    //
    // set up consumer
    //

    Properties consumerProperties = new Properties();
    consumerProperties.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
    consumerProperties.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
    consumerProperties.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "false");
    consumerProperties.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG,
        "org.apache.kafka.common.serialization.ByteArrayDeserializer");
    consumerProperties.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG,
        "org.apache.kafka.common.serialization.ByteArrayDeserializer");
    consumerProperties.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG,
        Deployment.getGuiConfigurationInitialConsumerMaxPollRecords());// speed up initial read
    consumerProperties.put(ConsumerConfig.FETCH_MAX_BYTES_CONFIG,
        Deployment.getGuiConfigurationInitialConsumerMaxFetchBytes());// speed up initial read
    consumerProperties.put(ConsumerConfig.MAX_PARTITION_FETCH_BYTES_CONFIG,
        Deployment.getGuiConfigurationInitialConsumerMaxFetchBytes());// speed up initial read
    guiManagedObjectsConsumerProperties = consumerProperties;
    guiManagedObjectsConsumer = new KafkaConsumer<>(guiManagedObjectsConsumerProperties);

    //
    // read initial guiManagedObjects
    //

    readGUIManagedObjects(true);

    //
    // close consumer (if master)
    //

    if (masterService) {
      guiManagedObjectsConsumer.close();
    } else {
      // once initial read over can lower a lot memory, we expect one record at a time
      // update
      guiManagedObjectsConsumerProperties.put(ConsumerConfig.FETCH_MAX_BYTES_CONFIG, 1024);
      guiManagedObjectsConsumerProperties.put(ConsumerConfig.MAX_PARTITION_FETCH_BYTES_CONFIG, 1024);
      reconnectConsumer();
    }

    //
    // initialize listenerQueue
    //

    listenerQueue.clear();

    // if master service, start background thread for topic deletion and ES update
    if (masterService)
      {
        Timer cleaner = new Timer("GUIService-deleted-object-cleaner", true);
        cleaner.scheduleAtFixedRate(new TimerTask()
        {
          @Override
          public void run()
          {
            
            //
            //  ES update
            //
            
            for (GUIManagedObject guiManagedObject : getStoredGUIManagedObjects(0))
              {
                updateElasticSearch(guiManagedObject, true);
              }
            
            //
            //  topic deletion
            //
            
            for (Map.Entry<String, Date> entry : forFullDeletionObjects.entrySet())
              {
                Date toRemoveFromTopicDate = RLMDateUtils.addDays(entry.getValue(), Deployment.getGuiConfigurationRetentionDays(), Deployment.getDefault().getTimeZone());
                if (SystemTime.getCurrentTime().after(toRemoveFromTopicDate))
                  {
                    log.info("removing from topic {}, deleted on {}", entry.getKey(), entry.getValue());
                    forFullDeletionObjects.remove(entry.getKey());
                    try
                      {
                        kafkaProducer.send(new ProducerRecord<byte[], byte[]>(guiManagedObjectTopic, stringKeySerde.serializer().serialize(guiManagedObjectTopic, new StringKey(entry.getKey())), null)).get();
                      } 
                    catch (InterruptedException | ExecutionException e)
                      {
                        log.error("error deleting to kafka " + entry.getKey(), e);
                      }
                  }
              }
          }
        }, DeploymentCommon.getGuiConfigurationCleanerThreadPeriodMs(), Deployment.getGuiConfigurationCleanerThreadPeriodMs());

      }

  }

  /*****************************************
   *
   * start
   *
   *****************************************/

  public void start(ElasticsearchClientAPI elasticSearch, JourneyService journeyService, JourneyObjectiveService journeyObjectiveService, TargetService targetService, ContactPolicyService contactPolicyService)
  {
    this.elasticsearch = elasticSearch;
    this.journeyService = journeyService;
    this.journeyObjectiveService = journeyObjectiveService;
    this.targetService = targetService;
    this.contactPolicyService = contactPolicyService;
    start();
  }

  public void start()
  {
    //
    // scheduler
    //

    Runnable scheduler = new Runnable()
    {
      @Override
      public void run()
      {
        runScheduler();
      }
    };
    schedulerThread = new Thread(scheduler, "GUIManagedObjectScheduler " + this.getClass().getSimpleName());
    schedulerThread.start();

    //
    // listener
    //

    Runnable listener = new Runnable()
    {
      @Override
      public void run()
      {
        runListener();
      }
    };
    listenerThread = new Thread(listener, "GUIManagedObjectListener " + this.getClass().getSimpleName());
    listenerThread.start();

    //
    // read guiManagedObject updates
    //

    if (!masterService)
      {
        Runnable guiManagedObjectReader = new Runnable()
        {
          @Override
          public void run()
          {
            readGUIManagedObjects(false);
          }
        };
        guiManagedObjectReaderThread = new Thread(guiManagedObjectReader, "GUIManagedObjectReader " + this.getClass().getSimpleName());
        guiManagedObjectReaderThread.start();
      }

  }

  /*****************************************
   *
   * stop
   *
   *****************************************/

  public void stop() {
    /*****************************************
     *
     * stopRequested
     *
     *****************************************/

    synchronized (this) {
      //
      // mark stopRequested
      //

      stopRequested = true;

      //
      // wake sleeping polls/threads (if necessary)
      //

      if (guiManagedObjectsConsumer != null)
        guiManagedObjectsConsumer.wakeup();
      if (schedulerThread != null)
        schedulerThread.interrupt();
      if (listenerThread != null)
        listenerThread.interrupt();
    }

    /*****************************************
     *
     * wait for threads to finish
     *
     *****************************************/

    try {
      if (schedulerThread != null)
        schedulerThread.join();
      if (listenerThread != null)
        listenerThread.join();
      if (guiManagedObjectReaderThread != null)
        guiManagedObjectReaderThread.join();
    } catch (InterruptedException e) {
      // nothing
    }

    /*****************************************
     *
     * close resources
     *
     *****************************************/

    if (guiManagedObjectsConsumer != null)
      guiManagedObjectsConsumer.close();
    if (kafkaProducer != null)
      kafkaProducer.close();

    NGLMRuntime.unregisterSystemTimeDependency(this); // remove this, otherwise references to the service exists, even
                                                      // after we stop it

  }

  /*****************************************
   *
   * registerListener
   *
   *****************************************/

  public void registerListener(GUIManagedObjectListener guiManagedObjectListener)
  {
    synchronized (this)
      {
        guiManagedObjectListeners.add(guiManagedObjectListener);
      }
  }

  /*****************************************
   *
   * generateGUIManagedObjectID
   *
   *****************************************/

  protected String generateGUIManagedObjectID()
  {
    synchronized (this)
      {
        lastGeneratedObjectID += 1;
        return Long.toString(lastGeneratedObjectID);
      }
  }

  /*****************************************
   *
   * getLastGeneratedObjectID
   *
   *****************************************/

  int getLastGeneratedObjectID()
  {
    synchronized (this)
      {
        return lastGeneratedObjectID;
      }
  }

  /*****************************************
   *
   * getStoredGUIManagedObject
   *
   *****************************************/

  protected GUIManagedObject getStoredGUIManagedObject(String guiManagedObjectID, boolean includeArchived)
  {
    if (guiManagedObjectID == null) return null;
    Map<String, GUIManagedObject> storedGUIManagedObjects = createAndGetTenantSpecificMap(storedPerTenantGUIManagedObjects, 0);
    GUIManagedObject result = storedGUIManagedObjects.get(guiManagedObjectID);
    result = (result != null && (includeArchived || !result.getDeleted())) ? result : null;
    return result;
  }

  //
  // (w/o includeArchived)
  //

  protected GUIManagedObject getStoredGUIManagedObject(String guiManagedObjectID)
  {
    return getStoredGUIManagedObject(guiManagedObjectID, false);
  }

  /*****************************************
   *
   * getStoredGUIManagedObjects
   *
   ****************************************/

  protected Collection<GUIManagedObject> getStoredGUIManagedObjects(boolean includeArchived, int tenantID)
  {
    List<GUIManagedObject> result = new ArrayList<GUIManagedObject>();
    Map<String, GUIManagedObject> storedGUIManagedObjects = createAndGetTenantSpecificMap(storedPerTenantGUIManagedObjects, tenantID);
    for (GUIManagedObject guiManagedObject : storedGUIManagedObjects.values())
      {
        if (includeArchived || !guiManagedObject.getDeleted())
          {
            result.add(guiManagedObject);
          }
      }
    return result;
  }

  //
  // (w/o includeArchived)
  //

  protected Collection<GUIManagedObject> getStoredGUIManagedObjects(int tenantID)
  {
    return getStoredGUIManagedObjects(false, tenantID);
  }

  /*****************************************
   *
   * isActiveThroughInterval
   *
   *****************************************/

  protected boolean isActiveThroughInterval(GUIManagedObject guiManagedObject, Date startDate, Date endDate)
  {
    boolean active = (guiManagedObject != null) && guiManagedObject.getAccepted() && guiManagedObject.getActive() && !guiManagedObject.getDeleted();
    boolean activeThroughInterval = active && (guiManagedObject.getEffectiveStartDate().compareTo(startDate) <= 0) && (guiManagedObject.getEffectiveEndDate().compareTo(endDate) >= 0);
    return activeThroughInterval;
  }

  /*****************************************
   *
   * isActiveGUIManagedObject
   *
   *****************************************/

  protected boolean isActiveGUIManagedObject(GUIManagedObject guiManagedObject, Date date)
  {
    if (guiManagedObject == null)
      return false;
    if (!guiManagedObject.getAccepted())
      return false;
    Map<String, GUIManagedObject> activeGUIManagedObjects = createAndGetTenantSpecificMap(activePerTenantGUIManagedObjects, guiManagedObject.getTenantID());
    if (activeGUIManagedObjects == null)
      return false;
    if (activeGUIManagedObjects.get(guiManagedObject.getGUIManagedObjectID()) == null)
      return false;
    if (guiManagedObject.getEffectiveStartDate().after(date))
      return false;
    if (guiManagedObject.getEffectiveEndDate().before(date))
      return false;
    return true;
  }

  protected boolean isInterruptedGUIManagedObject(GUIManagedObject guiManagedObject, Date date)
  {
    if (guiManagedObject == null)
      return false;
    Map<String, GUIManagedObject> interruptedGUIManagedObjects = createAndGetTenantSpecificMap(interruptedPerTenantGUIManagedObjects, guiManagedObject.getTenantID());
    if (interruptedGUIManagedObjects == null)
      return false;
    if (interruptedGUIManagedObjects.get(guiManagedObject.getGUIManagedObjectID()) == null)
      return false;
    if (guiManagedObject.getEffectiveStartDate() != null && guiManagedObject.getEffectiveStartDate().after(date))
      return false;
    if (guiManagedObject.getEffectiveEndDate() != null && guiManagedObject.getEffectiveEndDate().before(date))
      return false;
    return true;
  }

  /*****************************************
   *
   * getActiveGUIManagedObject
   *
   *****************************************/

  protected GUIManagedObject getActiveGUIManagedObject(String guiManagedObjectID, Date date)
  {
    if (guiManagedObjectID == null)
      return null;
    Map<String, GUIManagedObject> activeGUIManagedObjects = createAndGetTenantSpecificMap(activePerTenantGUIManagedObjects, 0);
    if (activeGUIManagedObjects == null)
      activeGUIManagedObjects = new ConcurrentHashMap<>();
    GUIManagedObject guiManagedObject = activeGUIManagedObjects.get(guiManagedObjectID);
    if (isActiveGUIManagedObject(guiManagedObject, date))
      return guiManagedObject;
    else
      return null;
  }

  protected GUIManagedObject getInterruptedGUIManagedObject(String guiManagedObjectID, Date date)
  {
    if (guiManagedObjectID == null)
      return null;
    Map<String, GUIManagedObject> interruptedGUIManagedObjects = createAndGetTenantSpecificMap(interruptedPerTenantGUIManagedObjects, 0);
    if (interruptedGUIManagedObjects == null)
      interruptedGUIManagedObjects = new ConcurrentHashMap<>();
    GUIManagedObject guiManagedObject = interruptedGUIManagedObjects.get(guiManagedObjectID);
    if (isInterruptedGUIManagedObject(guiManagedObject, date))
      return guiManagedObject;
    else
      return null;
  }

  /*****************************************
   *
   * getActiveGUIManagedObjects
   *
   ****************************************/

  protected Collection<? extends GUIManagedObject> getActiveGUIManagedObjects(Date date, int tenantID)
  {
    Collection<GUIManagedObject> result = new HashSet<GUIManagedObject>();
    Map<String, GUIManagedObject> activeGUIManagedObjects = createAndGetTenantSpecificMap(activePerTenantGUIManagedObjects, tenantID);
    if (activeGUIManagedObjects == null)
      activeGUIManagedObjects = new ConcurrentHashMap<>();
    for (GUIManagedObject guiManagedObject : activeGUIManagedObjects.values())
      {
        if (guiManagedObject.getEffectiveStartDate().compareTo(date) <= 0 && date.compareTo(guiManagedObject.getEffectiveEndDate()) < 0)
          {
            result.add(guiManagedObject);
          }
      }
    return result;
  }

  /*****************************************
   *
   * per tenant Map utilities
   *
   *****************************************/

  private Map<String, GUIManagedObject> createAndGetTenantSpecificMap(HashMap<Integer, ConcurrentHashMap<String, GUIManagedObject>> currentTenantMap, int tenantID)
  {
    Map<String, GUIManagedObject> result = currentTenantMap.get(tenantID);
    if (result == null)
      {
        synchronized (currentTenantMap)
          {
            result = currentTenantMap.get(tenantID);
            if (result == null)
              {
                result = new ConcurrentHashMap<>();
                currentTenantMap.put(tenantID, (ConcurrentHashMap<String, GUIManagedObject>) result);
              }
          }
      }
    return result;
  }

  private void putSpecificAndAllTenants(HashMap<Integer, ConcurrentHashMap<String, GUIManagedObject>> currentTenantMap, GUIManagedObject guiManagedObject)
  {
    Map<String, GUIManagedObject> tenantMap = createAndGetTenantSpecificMap(currentTenantMap, guiManagedObject.getTenantID());
    tenantMap.put(guiManagedObject.getGUIManagedObjectID(), guiManagedObject);
    // add also in the map related to tenant 0 i.e All tenants..
    Map<String, GUIManagedObject> allTenantMap = createAndGetTenantSpecificMap(currentTenantMap, 0);
    allTenantMap.put(guiManagedObject.getGUIManagedObjectID(), guiManagedObject);
  }

  private void removeSpecificAndAllTenants(HashMap<Integer, ConcurrentHashMap<String, GUIManagedObject>> currentTenantMap, String guiManagedObjectID, int tenantID)
  {
    Map<String, GUIManagedObject> tenantMap = createAndGetTenantSpecificMap(currentTenantMap, tenantID);
    tenantMap.remove(guiManagedObjectID);
    // add also in the map related to tenant 0 i.e All tenants..
    Map<String, GUIManagedObject> allTenantMap = createAndGetTenantSpecificMap(currentTenantMap, 0);
    allTenantMap.remove(guiManagedObjectID);
  }

  /*****************************************
   *
   * putGUIManagedObject
   *
   *****************************************/

  public void putGUIManagedObject(GUIManagedObject guiManagedObject, Date date, boolean newObject, String userID)
  {

    log.info("put {} {}", guiManagedObject.getClass().getSimpleName(), guiManagedObject.getGUIManagedObjectID());

    //
    // created/updated date
    //

    if (!masterService)
      throw new RuntimeException("can not update conf outside the master service");

    GUIManagedObject existingStoredGUIManagedObject = createAndGetTenantSpecificMap(storedPerTenantGUIManagedObjects, guiManagedObject.getTenantID()).get(guiManagedObject.getGUIManagedObjectID());
    guiManagedObject.setCreatedDate((existingStoredGUIManagedObject != null && existingStoredGUIManagedObject.getCreatedDate() != null) ? existingStoredGUIManagedObject.getCreatedDate() : date);
    guiManagedObject.setUpdatedDate(date);

    //
    // mark (not) deleted
    //

    guiManagedObject.markDeleted(false);

    //
    // submit to kafka
    //
    try
      {
        kafkaProducer.send(new ProducerRecord<byte[], byte[]>(guiManagedObjectTopic, stringKeySerde.serializer().serialize(guiManagedObjectTopic, new StringKey(guiManagedObject.getGUIManagedObjectID())), guiManagedObjectSerde.optionalSerializer().serialize(guiManagedObjectTopic, guiManagedObject))).get();
      } catch (InterruptedException | ExecutionException e)
      {
        log.error("putGUIManagedObject error saving to kafka " + guiManagedObject.getClass().getSimpleName() + " " + guiManagedObject.getGUIManagedObjectID(), e);
        if (e.getCause() instanceof RecordTooLargeException)
          {
            throw new RuntimeException("too big to be saved", e);
          }
        throw new RuntimeException(e);
      }

    //
    // audit
    //

    if (userID != null)
      {
        kafkaProducer.send(new ProducerRecord<byte[], byte[]>(guiAuditTopic, guiObjectAuditSerde.serializer().serialize(guiAuditTopic, new GUIObjectAudit(userID, putAPIString, newObject, guiManagedObject.getGUIManagedObjectID(), guiManagedObject, date))));
      }

    //
    // process
    //

    processGUIManagedObject(guiManagedObject.getGUIManagedObjectID(), guiManagedObject, date, guiManagedObject.getTenantID());
    updateElasticSearch(guiManagedObject, false);
  }

  /*****************************************
   *
   * removeGUIManagedObject
   *
   *****************************************/

  protected void removeGUIManagedObject(String guiManagedObjectID, Date date, String userID, int tenantID)
  {

    log.info("{} remove {}", this.getClass().getSimpleName(), guiManagedObjectID);
    if (guiManagedObjectID == null) throw new RuntimeException("null guiManagedObjectID" + guiManagedObjectID + " " + tenantID);
    if (!masterService) throw new RuntimeException("can not update conf outside the master service");
    
    //
    // created/updated date
    //

    GUIManagedObject existingStoredGUIManagedObject = createAndGetTenantSpecificMap(storedPerTenantGUIManagedObjects, tenantID).get(guiManagedObjectID);
    existingStoredGUIManagedObject.setCreatedDate((existingStoredGUIManagedObject != null && existingStoredGUIManagedObject.getCreatedDate() != null) ? existingStoredGUIManagedObject.getCreatedDate() : date);
    existingStoredGUIManagedObject.setUpdatedDate(date);

    //
    // mark deleted
    //

    if (existingStoredGUIManagedObject != null)
      existingStoredGUIManagedObject.markDeleted(true);

    //
    // submit to kafka
    //

    kafkaProducer.send(new ProducerRecord<byte[], byte[]>(guiManagedObjectTopic, stringKeySerde.serializer().serialize(guiManagedObjectTopic, new StringKey(guiManagedObjectID)), guiManagedObjectSerde.optionalSerializer().serialize(guiManagedObjectTopic, existingStoredGUIManagedObject)));

    //
    // audit
    //

    if (userID != null)
      {
        kafkaProducer.send(new ProducerRecord<byte[], byte[]>(guiAuditTopic, guiObjectAuditSerde.serializer().serialize(guiAuditTopic, new GUIObjectAudit(userID, removeAPIString, false, guiManagedObjectID, null, date))));
      }

    //
    // process
    //

    processGUIManagedObject(guiManagedObjectID, existingStoredGUIManagedObject, date, tenantID);
    updateElasticSearch(existingStoredGUIManagedObject, false);
  }

  /****************************************
   *
   * processGUIManagedObject
   *
   ****************************************/

  protected void processGUIManagedObject(String guiManagedObjectID, GUIManagedObject guiManagedObject, Date date,
      int tenantID) {
    if (guiManagedObjectID == null)
      throw new RuntimeException("null guiManagedObjectID");
    synchronized (this) {

      // handle deleted case first and return, avoiding easy NPE after
      if (guiManagedObject == null || guiManagedObject.getDeleted()) {
        // if still in memory, check if needs to be removed
        GUIManagedObject storedGuiManagedObject = createAndGetTenantSpecificMap(storedPerTenantGUIManagedObjects,
            tenantID).get(guiManagedObjectID);
        if (storedGuiManagedObject != null) {

          // don't check with stored one (previous version), if this is an update
          if (guiManagedObject != null)
            storedGuiManagedObject = guiManagedObject;

          if (serviceStatistics != null)
            serviceStatistics.updateRemoveCount(guiManagedObjectID);

          removeSpecificAndAllTenants(availablePerTenantGUIManagedObjects, guiManagedObjectID,
              storedGuiManagedObject.getTenantID());
          removeSpecificAndAllTenants(activePerTenantGUIManagedObjects, guiManagedObjectID,
              storedGuiManagedObject.getTenantID());
          removeSpecificAndAllTenants(interruptedPerTenantGUIManagedObjects, guiManagedObjectID,
              storedGuiManagedObject.getTenantID());

          Date toRemoveFromMemoryDate = RLMDateUtils.addDays(storedGuiManagedObject.getUpdatedDate(),
              Deployment.getGuiConfigurationSoftRetentionDays(), Deployment.getDefault().getTimeZone());
          if (SystemTime.getCurrentTime().after(toRemoveFromMemoryDate)) {
            log.info("removing from memory {} {}, deleted on {}", storedGuiManagedObject.getClass().getSimpleName(),
                storedGuiManagedObject.getGUIManagedObjectID(), storedGuiManagedObject.getUpdatedDate());
            removeSpecificAndAllTenants(storedPerTenantGUIManagedObjects, guiManagedObjectID,
                storedGuiManagedObject.getTenantID());
            // if master service and not yet "topic deleted", need to keep track of it, to
            // initiate a topic delete later
            if (masterService && guiManagedObject != null) {
              forFullDeletionObjects.put(storedGuiManagedObject.getGUIManagedObjectID(),
                  storedGuiManagedObject.getUpdatedDate());
            }
          } else {
            putSpecificAndAllTenants(storedPerTenantGUIManagedObjects, storedGuiManagedObject);
            ScheduleEntry scheduleEntry = new ScheduleEntry(new Date(toRemoveFromMemoryDate.getTime() + 1),
                storedGuiManagedObject.getGUIManagedObjectID(), storedGuiManagedObject.getTenantID());
            schedule.add(scheduleEntry);
            this.notifyAll();
          }
        }
        return;
      }

      //
      // accepted?
      //

      boolean accepted = guiManagedObject.getAccepted();

      //
      // created/updated dates
      //

      GUIManagedObject existingStoredGUIManagedObject = createAndGetTenantSpecificMap(storedPerTenantGUIManagedObjects,
          0).get(guiManagedObjectID);
      guiManagedObject.setCreatedDate(
          (existingStoredGUIManagedObject != null && existingStoredGUIManagedObject.getCreatedDate() != null)
              ? existingStoredGUIManagedObject.getCreatedDate()
              : date);
      guiManagedObject.setUpdatedDate(date);

      //
      // classify
      //

      boolean inActivePeriod = !guiManagedObject.getDeleted()
          && (guiManagedObject.getEffectiveStartDate() == null
              || guiManagedObject.getEffectiveStartDate().compareTo(date) <= 0)
          && (guiManagedObject.getEffectiveEndDate() == null
              || date.compareTo(guiManagedObject.getEffectiveEndDate()) < 0);
      boolean active = accepted && !guiManagedObject.getDeleted() && guiManagedObject.getActive()
          && (guiManagedObject.getEffectiveStartDate().compareTo(date) <= 0)
          && (date.compareTo(guiManagedObject.getEffectiveEndDate()) < 0);
      boolean future = accepted && !guiManagedObject.getDeleted() && guiManagedObject.getActive()
          && (guiManagedObject.getEffectiveStartDate().compareTo(date) > 0);

      //
      // store
      //

      putSpecificAndAllTenants(storedPerTenantGUIManagedObjects, guiManagedObject);
      if (serviceStatistics != null)
        serviceStatistics.updatePutCount(guiManagedObject.getGUIManagedObjectID());

      //
      // existingActiveGUIManagedObject
      //

      GUIManagedObject existingActiveGUIManagedObject = createAndGetTenantSpecificMap(activePerTenantGUIManagedObjects,
          tenantID).get(guiManagedObjectID);

      //
      // clear
      //

      if (!inActivePeriod || active || future) {
        removeSpecificAndAllTenants(interruptedPerTenantGUIManagedObjects, guiManagedObjectID, tenantID);
      }

      if (!active) {
        removeSpecificAndAllTenants(availablePerTenantGUIManagedObjects, guiManagedObjectID, tenantID);
        removeSpecificAndAllTenants(activePerTenantGUIManagedObjects, guiManagedObjectID, tenantID);
        if (existingActiveGUIManagedObject != null) {
          if (inActivePeriod)
            putSpecificAndAllTenants(interruptedPerTenantGUIManagedObjects, existingActiveGUIManagedObject);
          notifyListener(existingActiveGUIManagedObject);
        }
      }

      //
      // add to availableGUIManagedObjects
      //

      if (active || future) {
        putSpecificAndAllTenants(availablePerTenantGUIManagedObjects, (GUIManagedObject) guiManagedObject);
        if (guiManagedObject.getEffectiveEndDate().compareTo(NGLMRuntime.END_OF_TIME) < 0) {
          ScheduleEntry scheduleEntry = new ScheduleEntry(guiManagedObject.getEffectiveEndDate(),
              guiManagedObject.getGUIManagedObjectID(), tenantID);
          schedule.add(scheduleEntry);
          this.notifyAll();
        }
      }

      //
      // add to activeGUIManagedObjects
      //

      if (active) {
        putSpecificAndAllTenants(activePerTenantGUIManagedObjects, (GUIManagedObject) guiManagedObject);
        if (existingActiveGUIManagedObject == null
            || existingActiveGUIManagedObject.getEpoch() != guiManagedObject.getEpoch() || !notifyOnSignificantChange)
          notifyListener(guiManagedObject);
      }

      //
      // scheduler
      //

      if (future) {
        ScheduleEntry scheduleEntry = new ScheduleEntry(guiManagedObject.getEffectiveStartDate(),
            guiManagedObject.getGUIManagedObjectID(), tenantID);
        schedule.add(scheduleEntry);
        this.notifyAll();
      }

      //
      // record guiManagedObjectID for autogenerate (if necessary)
      //

      Pattern p = Pattern.compile("[0-9]+$");
      Matcher m = p.matcher(guiManagedObjectID);
      Integer objectID = m.find() ? Integer.parseInt(m.group(0)) : null;
      lastGeneratedObjectID = (objectID != null && objectID.intValue() > lastGeneratedObjectID) ? objectID.intValue()
          : lastGeneratedObjectID;

      //
      // statistics
      //

      if (serviceStatistics != null) {
        serviceStatistics
            .setActiveCount(createAndGetTenantSpecificMap(activePerTenantGUIManagedObjects, tenantID).size());
        serviceStatistics
            .setObjectCount(createAndGetTenantSpecificMap(availablePerTenantGUIManagedObjects, tenantID).size());
      }

      //
      // lastUpdate
      //

      lastUpdate = date.after(lastUpdate) ? date : lastUpdate;
    }
  }

  /****************************************
   *
   * readGUIManagedObjects
   *
   ****************************************/

  private void readGUIManagedObjects(boolean readInitialTopicRecords) {

    assignAllTopicPartitions();

    //
    // on the initial read, skip the poll if there are no records
    //

    if (readInitialTopicRecords) {
      boolean foundRecord = false;
      Map<TopicPartition, Long> endOffsets = getEndOffsets();
      for (TopicPartition partition : endOffsets.keySet()) {
        if (endOffsets.get(partition) > 0) {
          foundRecord = true;
          break;
        }
      }
      if (!foundRecord) {
        log.info("No records found.  Skipping initial read for {}", guiManagedObjectTopic);
        return;
      }
    }

    Date readStartDate = SystemTime.getCurrentTime();
    while (!stopRequested) {

      ConsumerRecords<byte[], byte[]> guiManagedObjectRecords = ConsumerRecords.<byte[], byte[]>empty();
      try {
        guiManagedObjectRecords = guiManagedObjectsConsumer.poll(5000);
      } catch (WakeupException e) {
        if (!stopRequested)
          log.info("wakeup while reading topic " + guiManagedObjectTopic);
      }

      if (stopRequested)
        continue;

      Date now = SystemTime.getCurrentTime();
      Map<Bytes, byte[]> toLoad = new HashMap<>();// key is Bytes and not byte[] directly, primitive byte array would
                                                  // not behave as expected regarding Map contract (hashcode and equals)
      int sizeConsumed = 0;// for debug logging
      for (ConsumerRecord<byte[], byte[]> record : guiManagedObjectRecords) {
        toLoad.put(new Bytes(record.key()), record.value());
        sizeConsumed += record.serializedKeySize() + record.serializedValueSize();
        consumedOffsets.put(new TopicPartition(record.topic(), record.partition()), record.offset());
      }

      if (log.isDebugEnabled())
        log.debug("will process " + toLoad.size() + " records, after reading " + sizeConsumed + " bytes for "
            + guiManagedObjectRecords.count() + " total records");

      Iterator<Map.Entry<Bytes, byte[]>> groupedRecordIterator = toLoad.entrySet().iterator();
      while (groupedRecordIterator.hasNext()) {

        Map.Entry<Bytes, byte[]> record = groupedRecordIterator.next();
        groupedRecordIterator.remove();

        String guiManagedObjectID = stringKeySerde.deserializer()
            .deserialize(guiManagedObjectTopic, record.getKey().get()).getKey();
        GUIManagedObject guiManagedObject;
        try {
          guiManagedObject = guiManagedObjectSerde.optionalDeserializer().deserialize(guiManagedObjectTopic,
              record.getValue());
        } catch (SerializationException e) {
          log.info("error reading guiManagedObject on " + guiManagedObjectTopic + " : {}", e.getMessage());
          guiManagedObject = incompleteObjectSerde.optionalDeserializer().deserialize(guiManagedObjectTopic,
              record.getValue());
        }

        if (guiManagedObject != null)
          log.info("read {} {}", guiManagedObject.getClass().getSimpleName(), guiManagedObject.getGUIManagedObjectID());

        int tenantId = guiManagedObject != null ? guiManagedObject.getTenantID() : 0;// deleted case (null value)
        processGUIManagedObject(guiManagedObjectID, guiManagedObject, readInitialTopicRecords ? readStartDate : now,
            tenantId);

      }

      if (readInitialTopicRecords) {
        //
        // consumed all available?
        //

        Map<TopicPartition, Long> availableOffsets = getEndOffsets();
        boolean consumedAllAvailable = true;
        for (TopicPartition partition : availableOffsets.keySet()) {
          Long availableOffsetForPartition = availableOffsets.get(partition);
          Long consumedOffsetForPartition = consumedOffsets.get(partition);
          if (consumedOffsetForPartition == null) {
            consumedOffsetForPartition = guiManagedObjectsConsumer.position(partition) - 1L;
            consumedOffsets.put(partition, consumedOffsetForPartition);
          }
          if (consumedOffsetForPartition < availableOffsetForPartition - 1) {
            consumedAllAvailable = false;
            break;
          }
        }
        if (consumedAllAvailable)
          return;
      }

    }

  }

  private void assignAllTopicPartitions() {
    boolean freshAsignment = consumedOffsets == null;
    if (guiManagedObjectsConsumer != null) {
      Set<TopicPartition> partitions = new HashSet<>();
      List<PartitionInfo> partitionInfos = null;
      while (partitionInfos == null) {
        try {
          partitionInfos = guiManagedObjectsConsumer.partitionsFor(guiManagedObjectTopic, Duration.ofSeconds(5));
        } catch (TimeoutException e) {
          // a kafka broker might just be down, consumer.partitionsFor() can ends up
          // timeout trying on this one
          reconnectConsumer();
          log.warn("timeout while getting topic partitions", e.getMessage());
        } catch (WakeupException e) {
        }
      }
      if (freshAsignment)
        consumedOffsets = new HashMap<>();
      for (PartitionInfo partitionInfo : partitionInfos) {
        TopicPartition topicPartition = new TopicPartition(guiManagedObjectTopic, partitionInfo.partition());
        partitions.add(topicPartition);
      }
      guiManagedObjectsConsumer.assign(partitions);
      for (Map.Entry<TopicPartition, Long> position : consumedOffsets.entrySet()) {
        if (freshAsignment)
          consumedOffsets.put(position.getKey(), guiManagedObjectsConsumer.position(position.getKey()) - 1L);
        guiManagedObjectsConsumer.seek(position.getKey(), position.getValue());
      }
    } else {
      log.error("NULL kafka consumer while assigning topic partitions " + guiManagedObjectTopic);
    }
  }

  private Map<TopicPartition, Long> getEndOffsets() {
    if (guiManagedObjectsConsumer == null) {
      log.error("NULL kafka consumer reading end offets");
      return Collections.emptyMap();
    }
    while (true) {
      try {
        return guiManagedObjectsConsumer.endOffsets(guiManagedObjectsConsumer.assignment());
      } catch (TimeoutException e) {
        // a kafka broker might just went down (kafkaConsumer.assign(), not
        // kafkaConsumer.consume(), so need to catch it)
        reconnectConsumer();
        log.warn("timeout while getting end offsets", e.getMessage());
      } catch (WakeupException e) {
      }
    }
  }

  private void reconnectConsumer() {
    if (guiManagedObjectsConsumer != null)
      guiManagedObjectsConsumer.close();
    guiManagedObjectsConsumer = new KafkaConsumer<byte[], byte[]>(guiManagedObjectsConsumerProperties);
    assignAllTopicPartitions();
  }

  /****************************************
   *
   * runScheduler
   *
   ****************************************/

  private void runScheduler() {
    NGLMRuntime.registerSystemTimeDependency(this);
    while (!stopRequested) {
      synchronized (this) {
        //
        // wait for next evaluation date
        //

        Date now = SystemTime.getCurrentTime();
        Date nextEvaluationDate = (schedule.size() > 0) ? schedule.first().getEvaluationDate()
            : NGLMRuntime.END_OF_TIME;
        long waitTime = nextEvaluationDate.getTime() - now.getTime();
        while (!stopRequested && waitTime > 0) {
          try {
            this.wait(waitTime);
          } catch (InterruptedException e) {
            // ignore
          }
          now = SystemTime.getCurrentTime();
          nextEvaluationDate = (schedule.size() > 0) ? schedule.first().getEvaluationDate() : NGLMRuntime.END_OF_TIME;
          waitTime = nextEvaluationDate.getTime() - now.getTime();
        }

        //
        // processing?
        //

        if (stopRequested)
          continue;

        //
        // process
        //

        ScheduleEntry entry = schedule.pollFirst();
        GUIManagedObject guiManagedObject = createAndGetTenantSpecificMap(availablePerTenantGUIManagedObjects, 0)
            .get(entry.getGUIManagedObjectID());
        if (guiManagedObject != null) {

          //
          // existingActiveGUIManagedObject
          //
          GUIManagedObject existingActiveGUIManagedObject = createAndGetTenantSpecificMap(
              activePerTenantGUIManagedObjects, guiManagedObject.getTenantID())
                  .get(guiManagedObject.getGUIManagedObjectID());

          //
          // active window
          //

          if (guiManagedObject.getEffectiveStartDate().compareTo(now) <= 0
              && now.compareTo(guiManagedObject.getEffectiveEndDate()) < 0) {
            putSpecificAndAllTenants(activePerTenantGUIManagedObjects, guiManagedObject);
            removeSpecificAndAllTenants(interruptedPerTenantGUIManagedObjects, guiManagedObject.getGUIManagedObjectID(),
                guiManagedObject.getTenantID());
            notifyListener(guiManagedObject);
          }

          //
          // after active window
          //

          if (now.compareTo(guiManagedObject.getEffectiveEndDate()) >= 0) {
            removeSpecificAndAllTenants(availablePerTenantGUIManagedObjects, guiManagedObject.getGUIManagedObjectID(),
                guiManagedObject.getTenantID());
            removeSpecificAndAllTenants(activePerTenantGUIManagedObjects, guiManagedObject.getGUIManagedObjectID(),
                guiManagedObject.getTenantID());
            removeSpecificAndAllTenants(interruptedPerTenantGUIManagedObjects, guiManagedObject.getGUIManagedObjectID(),
                guiManagedObject.getTenantID());
            if (existingActiveGUIManagedObject != null)
              notifyListener(guiManagedObject);
          }

          //
          // lastUpdate
          //

          lastUpdate = now.after(lastUpdate) ? now : lastUpdate;
        }
      }
    }
  }

  /*****************************************
   *
   * generateResponseJSON
   *
   *****************************************/

  public JSONObject generateResponseJSON(GUIManagedObject guiManagedObject, boolean fullDetails, Date date)
  {
    JSONObject responseJSON = new JSONObject();
    if (guiManagedObject != null)
      {
        responseJSON.putAll(fullDetails ? getJSONRepresentation(guiManagedObject) : getSummaryJSONRepresentation(guiManagedObject));
        responseJSON.put("accepted", guiManagedObject.getAccepted());
        responseJSON.put("active", guiManagedObject.getActive());
        responseJSON.put("valid", guiManagedObject.getAccepted());
        responseJSON.put("processing", isActiveGUIManagedObject(guiManagedObject, date));
        responseJSON.put("readOnly", guiManagedObject.getReadOnly());

      }
    return responseJSON;
  }

  /*****************************************
   *
   * getJSONRepresentation
   *
   *****************************************/

  protected JSONObject getJSONRepresentation(GUIManagedObject guiManagedObject)
  {
    JSONObject result = new JSONObject();
    result.putAll(guiManagedObject.getJSONRepresentation());
    return result;
  }

  /*****************************************
   *
   * getSummaryJSONRepresentation
   *
   *****************************************/

  protected JSONObject getSummaryJSONRepresentation(GUIManagedObject guiManagedObject)
  {
    JSONObject result = new JSONObject();
    result.put("id", guiManagedObject.getJSONRepresentation().get("id"));
    result.put("name", guiManagedObject.getJSONRepresentation().get("name"));
    result.put("description", guiManagedObject.getJSONRepresentation().get("description"));
    result.put("display", guiManagedObject.getJSONRepresentation().get("display"));
    result.put("icon", guiManagedObject.getJSONRepresentation().get("icon"));
    result.put("effectiveStartDate", guiManagedObject.getJSONRepresentation().get("effectiveStartDate"));
    result.put("effectiveEndDate", guiManagedObject.getJSONRepresentation().get("effectiveEndDate"));
    result.put("userID", guiManagedObject.getJSONRepresentation().get("userID"));
    result.put("userName", guiManagedObject.getJSONRepresentation().get("userName"));
    result.put("groupID", guiManagedObject.getJSONRepresentation().get("groupID"));
    result.put("createdDate", guiManagedObject.getJSONRepresentation().get("createdDate"));
    result.put("updatedDate", guiManagedObject.getJSONRepresentation().get("updatedDate"));
    result.put("info", guiManagedObject.getJSONRepresentation().get("info"));
    result.put("deleted", guiManagedObject.getJSONRepresentation().get("deleted") != null ? guiManagedObject.getJSONRepresentation().get("deleted") : false);
    return result;
  }

  /****************************************************************************
   *
   * ScheduleEntry
   *
   ****************************************************************************/

  private static class ScheduleEntry implements Comparable<ScheduleEntry>
  {
    //
    // data
    //

    private Date evaluationDate;
    private String guiManagedObjectID;
    private int tenantID;

    //
    // accessors
    //

    Date getEvaluationDate()
    {
      return evaluationDate;
    }

    String getGUIManagedObjectID()
    {
      return guiManagedObjectID;
    }

    int getTenantID()
    {
      return tenantID;
    }

    //
    // constructor
    //

    ScheduleEntry(Date evaluationDate, String guiManagedObjectID, int tenantID)
    {
      this.evaluationDate = evaluationDate;
      this.guiManagedObjectID = guiManagedObjectID;
      this.tenantID = tenantID;
    }

    //
    // compareTo
    //

    public int compareTo(ScheduleEntry other)
    {
      if (this.evaluationDate.before(other.evaluationDate))
        return -1;
      else if (this.evaluationDate.after(other.evaluationDate))
        return 1;
      else
        return this.guiManagedObjectID.compareTo(other.guiManagedObjectID);
    }
  }

  /*****************************************
   *
   * class GuiManagedObjectsConsumerRebalanceListener
   *
   *****************************************/

  private class GuiManagedObjectsConsumerRebalanceListener implements ConsumerRebalanceListener {
    //
    // data
    //

    private String serviceName;
    private String groupID;

    //
    // constructor
    //

    public GuiManagedObjectsConsumerRebalanceListener(String serviceName, String groupId) {
      this.serviceName = serviceName;
      this.groupID = groupId;
    }

    @Override
    public void onPartitionsRevoked(Collection<TopicPartition> lastAssignedPartitions) {
    }

    @Override
    public void onPartitionsAssigned(Collection<TopicPartition> partitionsToBeAssigned) {
      if (partitionsToBeAssigned.size() == 0) {
        log.error("{} has multiple instance with same key {}", serviceName, groupID);
      }
    }
  }

  /*****************************************
   *
   * interface GUIManagedObjectListener
   *
   *****************************************/

  protected interface GUIManagedObjectListener {
    public void guiManagedObjectActivated(GUIManagedObject guiManagedObject);

    public void guiManagedObjectDeactivated(String objectID, int tenantID);
  }

  /*****************************************
   *
   * notifyListener
   *
   *****************************************/

  private void notifyListener(GUIManagedObject guiManagedObject) {
    updateElasticSearch(guiManagedObject, false);
    listenerQueue.add(guiManagedObject);
  }

  /*****************************************
   *
   * runListener
   *
   *****************************************/

  private void runListener() {
    while (!stopRequested) {
      try {
        //
        // get next
        //

        GUIManagedObject guiManagedObject = listenerQueue.take();

        //
        // listeners
        //

        List<GUIManagedObjectListener> guiManagedObjectListeners = new ArrayList<GUIManagedObjectListener>();
        synchronized (this) {
          guiManagedObjectListeners.addAll(this.guiManagedObjectListeners);
        }

        //
        // notify
        //

        Date now = SystemTime.getCurrentTime();
        for (GUIManagedObjectListener guiManagedObjectListener : guiManagedObjectListeners) {
          if (isActiveGUIManagedObject(guiManagedObject, now))
            guiManagedObjectListener.guiManagedObjectActivated(guiManagedObject);
          else
            guiManagedObjectListener.guiManagedObjectDeactivated(guiManagedObject.getGUIManagedObjectID(),
                guiManagedObject.getTenantID());
        }
      } catch (InterruptedException e) {
        //
        // ignore
        //
      } catch (Throwable e) {
        StringWriter stackTraceWriter = new StringWriter();
        e.printStackTrace(new PrintWriter(stackTraceWriter, true));
        log.warn("Exception processing listener: {}", stackTraceWriter.toString());
      }
    }
  }

  public static void setCommonConsumerProperties(Properties consumerProperties) {
    consumerProperties.put(ConsumerConfig.MAX_POLL_INTERVAL_MS_CONFIG, Deployment.getMaxPollIntervalMs());

  }

  /*****************************************
  *
  * updateElasticSearch
  *
  *****************************************/
  
  public void updateElasticSearch(GUIManagedObject guiManagedObject, final boolean autoUpdate)
  {
    if (guiManagedObject instanceof ElasticSearchMapping && elasticsearch != null /* to ensure it has been started with the good parameters*/ )
      {
        if (guiManagedObject.getDeleted())
          {
            DeleteRequest deleteRequest = new DeleteRequest(((ElasticSearchMapping) guiManagedObject).getESIndexName(), ((ElasticSearchMapping) guiManagedObject).getESDocumentID());
            deleteRequest.id(((ElasticSearchMapping) guiManagedObject).getESDocumentID());
            try
              {
                elasticsearch.delete(deleteRequest, RequestOptions.DEFAULT);
              } 
            catch (IOException e)
              {
                e.printStackTrace();
              }
          } 
        else
          {
            UpdateRequest request = new UpdateRequest(((ElasticSearchMapping) guiManagedObject).getESIndexName(), ((ElasticSearchMapping) guiManagedObject).getESDocumentID());
            request.doc(((ElasticSearchMapping) guiManagedObject).getESDocumentMap(autoUpdate, elasticsearch, journeyService, targetService, journeyObjectiveService, contactPolicyService));
            request.docAsUpsert(true);
            request.retryOnConflict(4);
            try
              {
                elasticsearch.update(request, RequestOptions.DEFAULT);
              } 
            catch (IOException e)
              {
                e.printStackTrace();
              }
          }
      }
  }
}
