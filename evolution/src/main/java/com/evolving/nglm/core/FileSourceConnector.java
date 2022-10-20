/****************************************************************************
*
*  FileSourceConnector.java
*
****************************************************************************/

package com.evolving.nglm.core;

import com.evolving.nglm.evolution.EvolutionUtilities;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.config.ConfigDef;
import org.apache.kafka.common.config.ConfigDef.Importance;
import org.apache.kafka.common.config.ConfigDef.Type;
import org.apache.kafka.connect.connector.Task;
import org.apache.kafka.connect.errors.ConnectException;
import org.apache.kafka.connect.source.SourceConnector;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.Files;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.SortedSet;
import java.util.TimeZone;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import java.util.stream.Collectors;

public abstract class FileSourceConnector extends SourceConnector
{
  /*****************************************
  *
  *  config
  *
  *****************************************/

  //
  //  logger
  //

  private static final Logger log = LoggerFactory.getLogger(FileSourceConnector.class);
  
  //
  //  configuration
  //

  private String connectorName = null;
  private File directory = null;
  private String filenamePattern = null;
  private Integer pollMaxRecords = null;
  private Integer pollingInterval = null;
  private Integer verifySizeInterval;
  private String fileCharset = null;
  private String topic = null;
  private String bootstrapServers = null;
  private String internalTopic = null;
  private File archiveDirectory = null;
  private boolean parseSubfolders = true; 
  private boolean pollOnce;
  
  //
  //  state
  //

  private boolean stopRequested = false;

  Thread pollDirectoryLoopThread = null;
  KafkaConsumer<byte[],byte[]> initConsumer = null;

  //
  //  version
  //

  final static String FileSourceVersion = "0.1";

  /****************************************
  *
  *  version
  *
  ****************************************/

  @Override public String version()
  {
    return FileSourceVersion;
  }

  /****************************************
  *
  *  start
  *
  ****************************************/

  @Override public void start(Map<String, String> properties)
  {
    /*****************************************
    *
    *  configuration -- connectorName
    *
    *****************************************/

    connectorName = properties.get("name");

    /*****************************************
    *
    *  log
    *
    *****************************************/

    log.info("{} -- Connector.start() START", connectorName);

    /*****************************************
    *
    *  configuration -- directory
    *
    *****************************************/

    //
    //  get the directory name
    //

    String directoryName = properties.get("directory");
    if (directoryName == null || directoryName.trim().length() == 0) throw new ConnectException("FileSourceConnector configuration must specify 'directory'");

    //
    //  ensure absolute path
    //

    directory = new File(directoryName);
    if (! directory.isAbsolute()) throw new ConnectException("FileSourceConnector configuration must specify absolute path for 'directory'");

    //
    //  create (if necessary)
    //

    if (! directory.exists())
      {
        boolean created = directory.mkdirs();
        if (!created)
          {
            throw new ConnectException("FileSourceConnector cannot create input directory: " + directory.getAbsolutePath());
          }
      }
    
    //
    //  ensure directory
    //

    if (! directory.isDirectory())
      {
        throw new ConnectException("FileSourceConnector cannot create input directory: " + directory.getAbsolutePath());
      }

    /*****************************************
    *
    *  configuration -- filenamePattern
    *
    *****************************************/

    //
    //  get the filename pattern
    //

    filenamePattern = properties.get("filenamePattern");
    if (filenamePattern == null || filenamePattern.trim().length() == 0) throw new ConnectException("FileSourceConnector configuration must specify 'filenamePattern'");

    //
    //  validate
    //
    
    try
      {
        Pattern pattern = Pattern.compile(filenamePattern);
      }
    catch (PatternSyntaxException e)
      {
        throw new ConnectException("FileSourceConnector bad file pattern syntax: " + filenamePattern, e);
      }

    /*****************************************
    *
    *  configuration -- pollMaxRecords
    *
    *****************************************/

    //
    //  parse integer
    //

    String pollMaxRecordsString = properties.get("pollMaxRecords");
    try
      {
        pollMaxRecords = (pollMaxRecordsString != null || pollMaxRecordsString.trim().length() > 0) ? Integer.parseInt(pollMaxRecordsString) : 1000;
      }
    catch (NumberFormatException e)
      {
        throw new ConnectException("FileSourceConnector configuration must include 'pollMaxRecords' setting",e);
      }

    //
    //  validate
    //

    if (pollMaxRecords <= 0)
      {
        throw new ConnectException("FileSourceConnector configuration must include positive 'pollMaxRecords' setting");
      }

    /*****************************************
    *
    *  configuration -- pollingInterval
    *
    *****************************************/

    //
    //  get the parameter
    //

    String pollingIntervalString = properties.get("pollingInterval");

    //
    //  parse integer
    //

    try
      {
        pollingInterval = (pollingIntervalString != null && pollingIntervalString.trim().length() > 0) ? Integer.parseInt(pollingIntervalString) : 10;
      }
    catch (NumberFormatException e)
      {
        throw new ConnectException("FileSourceConnector configuration must include 'pollingInterval' setting",e);
      }

    //
    //  validate
    //

    if (pollingInterval <= 0)
      {
        throw new ConnectException("FileSourceConnector configuration must include positive 'pollingInterval' setting");
      }

    /*****************************************
    *
    *  configuration -- pollOnce
    *
    *****************************************/

    //
    //  get the parameter
    //

    String pollOnceString = properties.get("pollOnce");

    //
    //  set the parameter
    //

    pollOnce = (pollOnceString != null) && pollOnceString.equalsIgnoreCase("true");

    /*****************************************
    *
    *  configuration -- parseSubfolders
    *
    *****************************************/

    //
    //  get the parameter
    //

    String parseSubfoldersString = properties.get("parseSubfolders");

    //
    //  set the parameter
    //

    parseSubfolders = (parseSubfoldersString != null) ? parseSubfoldersString.equalsIgnoreCase("true") : true ; // defaults to true
    log.info("parseSubfolders = " + parseSubfolders);
    
    /*****************************************
    *
    *  configuration -- verifySizeInterval
    *
    *****************************************/

    //
    //  get the parameter
    //

    String verifySizeIntervalString = properties.get("verifySizeInterval");
    
    //
    // parse integer
    //

    try
      {
        verifySizeInterval = (verifySizeIntervalString != null && verifySizeIntervalString.trim().length() > 0) ? Integer.parseInt(verifySizeIntervalString) : 0;
      }
    catch (NumberFormatException e)
      {
        throw new ConnectException("FileSourceConnector configuration must include 'verifySizeInterval' setting",e);
      }

    /*****************************************
    *
    *  configuration -- fileCharset
    *
    *****************************************/
    
    fileCharset = (properties.get("fileCharset") != null) ? properties.get("fileCharset") : "ISO-8859-1";

    /*****************************************
    *
    *  configuration -- topic
    *
    *****************************************/
    
    //
    //  get the topic
    //

    topic = properties.get("topic");

    //
    //  validate
    //

    if (topic == null || topic.trim().length() == 0)
      {
        throw new ConnectException("FileSourceConnector configuration must include'topic' setting");
      }
    
    //
    //  parse topic if it contains recordTopic:topic entries
    //

    if (topic.contains(":"))
      {
        //
        //  extract each record/topic pair
        //

        String[] recordTopicTokens;
        try
          {
            recordTopicTokens = topic.split("[,]");
          }
        catch (PatternSyntaxException e)
          {
            throw new ConnectException("improperly formatted configuration topic: " + topic, e);
          }

        //
        //  split single record and topic, updating recordTopics
        //

        for (String recordTopicToken : recordTopicTokens)
          {
            String[] recordTopicPair;
            try
              {
                recordTopicPair = recordTopicToken.split("[:]", 2);
              }
            catch (PatternSyntaxException e)
              {
                throw new ConnectException("improperly formatted configuration topic: " + topic, e);
              }
            if (recordTopicPair.length != 2)
              {
                throw new ConnectException("improperly formatted configuration topic: " + topic);
              }
          }
      }

    /*****************************************
    *
    *  configuration -- bootstrapServers
    *
    *****************************************/

    //
    //  get bootstrapServers
    //
    
    bootstrapServers = properties.get("bootstrapServers");

    //
    //  validate
    //
    
    if (bootstrapServers == null || bootstrapServers.trim().length() == 0)
      {
        throw new ConnectException("FileSourceConnector configuration must specify 'bootstrapServers'");
      }
    
    /*****************************************
    *
    *  configuration -- internalTopic
    *
    *****************************************/
    
    //
    //  get the topic
    //

    internalTopic = properties.get("internalTopic");

    //
    //  validate
    //

    if (internalTopic == null || internalTopic.trim().length() == 0)
      {
        throw new ConnectException("FileSourceConnector configuration must include'internalTopic' setting");
      }
    
    /*****************************************
    *
    *  configuration -- archiveDirectory
    *
    *****************************************/

    String archiveDirectoryName = properties.get("archiveDirectory");
    if (archiveDirectoryName != null && archiveDirectoryName.trim().length() > 0)
      {
        //
        //  ensure absolute path
        //

        archiveDirectory = new File(archiveDirectoryName);
        if (! archiveDirectory.isAbsolute()) throw new ConnectException("FileSourceConnector configuration must specify absolute path for 'archiveDirectory'");

        //
        //  create (if necessary)
        //

        if (! archiveDirectory.exists())
          {
            boolean created = archiveDirectory.mkdirs();
            if (!created)
              {
                throw new ConnectException("FileSourceConnector cannot create input archiveDirectory: " + archiveDirectory.getAbsolutePath());
              }
          }

        //
        //  ensure archiveDirectory
        //

        if (! archiveDirectory.isDirectory())
          {
            throw new ConnectException("FileSourceConnector cannot create input archiveDirectory: " + archiveDirectory.getAbsolutePath());
          }
      }

    /*****************************************
    *
    *  pollDirectory
    *
    *****************************************/

    pollDirectoryLoopThread = new Thread(this::runPollDirectoryLoop, "PollDirectoryLoop-"+connectorName);
    pollDirectoryLoopThread.start();
    
    /*****************************************
    *
    *  log
    *
    *****************************************/
    
    log.info("{} -- Connector.start() END", connectorName);
  }

  /****************************************
  *
  *  abstract
  *
  ****************************************/

  @Override public abstract Class<? extends Task> taskClass();
  public Map<String,String> additionalTaskConfig() { return Collections.<String,String>emptyMap(); }

  
  public String getFilenamePattern() { return filenamePattern; }
  public void setFilenamePattern(String filenamePattern) { this.filenamePattern = filenamePattern; }
  
  /****************************************
  *
  *  taskConfigs
  *
  ****************************************/

  @Override public List<Map<String, String>> taskConfigs(int maxTasks)
  {
    /*****************************************
    *
    *  log
    *
    *****************************************/

    log.info("taskConfigs() START");
    log.info("taskConfigs() {} maxTasks", maxTasks);

    /*****************************************
    *
    *  create N task configs
    *
    *****************************************/

    List<Map<String, String>> result = new ArrayList<Map<String,String>>();
    for (int i = 0; i < maxTasks; i++)
      {
        Map<String, String> taskConfig = new HashMap<>();
        taskConfig.put("connectorName", connectorName);
        taskConfig.put("directory", directory.getAbsolutePath());
        taskConfig.put("pollMaxRecords", Integer.toString(pollMaxRecords));
        taskConfig.put("fileCharset", fileCharset);
        taskConfig.put("topic", topic);
        taskConfig.put("bootstrapServers", bootstrapServers);
        taskConfig.put("internalTopic", internalTopic);
        taskConfig.put("archiveDirectory", (archiveDirectory != null) ? archiveDirectory.getAbsolutePath() : "");
        taskConfig.put("taskNumber",Integer.toString(i));
        taskConfig.putAll(additionalTaskConfig());
        result.add(taskConfig);
      }
    
    /*****************************************
    *
    *  return
    *
    *****************************************/

    //
    //  log
    //

    log.info("taskConfigs() END");

    //
    //  return
    //

    return result;
  }

  /****************************************
  *
  *  stop
  *
  ****************************************/

  @Override public void stop()
  {
    /****************************************
    *
    *  log
    *
    ****************************************/
    
    log.info("{} -- Connector.stop() called", connectorName);
    
    /*****************************************
    *
    *  mark stopRequested
    *
    *****************************************/

    stopRequested = true;

    /*****************************************
    *
    *  wake sleeping poll (if necessary)
    *
    *****************************************/

    if(initConsumer!=null){
      log.info("{} -- Connector.stop() in init phase, wakeup consumer", connectorName);
      // wake up consumer if needed, no synchro, might turned null
      try{
        initConsumer.wakeup();
        log.info("{} -- Connector.stop() consumer wakeup done", connectorName);
      }catch (NullPointerException e){ }
	}

    synchronized (this)
      {
        this.notifyAll();
      }

    // wait end of polling job
    if(pollDirectoryLoopThread!=null){
      try {
        log.info("{} -- Connector.stop() waiting end thread {}", connectorName, pollDirectoryLoopThread.getName());
        pollDirectoryLoopThread.join();
      } catch (InterruptedException e) {}
    }

    log.info("{} -- Connector.stop() done", connectorName);

  }

  /****************************************
  *
  *  config
  *
  ****************************************/

  @Override public ConfigDef config()
  {
    ConfigDef result = new ConfigDef();
    result.define("directory", Type.STRING, Importance.HIGH, "source directory");
    result.define("filenamePattern", Type.STRING, Importance.HIGH, "filename pattern");
    result.define("topic", Type.STRING, null, Importance.HIGH, "topic to publish data to");
    result.define("bootstrapServers", Type.STRING, null, Importance.HIGH, "kafka brokers");
    result.define("internalTopic", Type.STRING, null, Importance.HIGH, "topic to partition available files");
    return result;
  }
  
  /*****************************************
  *
  *  runPollDirectoryLoop
  *
  *****************************************/

  private void runPollDirectoryLoop()
  {

    Properties producerProperties = new Properties();
    producerProperties.put("bootstrap.servers", bootstrapServers);
    producerProperties.put("acks", "all");
    producerProperties.put("key.serializer", "org.apache.kafka.common.serialization.ByteArraySerializer");
    producerProperties.put("value.serializer", "org.apache.kafka.common.serialization.ByteArraySerializer");
    producerProperties.put(ProducerConfig.REQUEST_TIMEOUT_MS_CONFIG,120000);
    producerProperties.put(ProducerConfig.DELIVERY_TIMEOUT_MS_CONFIG,Integer.MAX_VALUE);
    KafkaProducer<byte[], byte[]> producer = new KafkaProducer<byte[], byte[]>(producerProperties);

    /*****************************************
    *
    *  serde
    *
    *****************************************/

    ConnectSerde<StringKey> stringKeySerde = StringKey.serde();
    ConnectSerde<StringValue> stringValueSerde = StringValue.serde();

    /*****************************************
    *
    *  initialize previousFiles before loop
    *
    *****************************************/

    Set<File> previousFiles = new HashSet<File>();
    try
      {
        //
        //  populate initialFiles with all files in directory
        //
        
        FileFilter filter = new FileFilter()
        {
          Pattern p = Pattern.compile(filenamePattern);

          public boolean accept(File file)
          {
            Matcher m = p.matcher(file.getName());
            return file.isFile() && m.matches();
          }
        };
        File[] filesInDirectory = directory.listFiles(filter);
        List<File> fullList = new ArrayList<>();
        if (filesInDirectory != null) Arrays.stream(filesInDirectory).forEach( f -> fullList.add(f));
        
        // then add all files in sub-directories

        if (parseSubfolders) {
          for (File file : directory.listFiles()) {
            if (file.isDirectory()) {
              File[] subFiles = file.listFiles(filter);
              if (subFiles.length != 0) {
                if (log.isDebugEnabled()) {
                  log.debug("  Adding {} files :",subFiles.length);
                  for (File f : subFiles) log.debug("   {}", f.getAbsolutePath());
                }
                Arrays.stream(subFiles).forEach( f -> fullList.add(f));
              }
            }
          }
        }
        
        Set<String> initialFiles = new HashSet<String>();
        for (File file : fullList)
          {
            initialFiles.add(file.getName());
          }
        log.info("{} -- Connector.pollDirectory() start init, {} files found in folder {}", connectorName, initialFiles.size(), directory.getName());

        //
        //  populate previousFiles with any file that is both (1) already in the topic and (2) exists in the directory
        //
        
        Properties consumerProperties = new Properties();
        consumerProperties.put("bootstrap.servers", bootstrapServers);
        consumerProperties.put("group.id", "fileconnector-singleton-" + connectorName);
        consumerProperties.put("auto.offset.reset", "earliest");
        consumerProperties.put("enable.auto.commit", "false");
        consumerProperties.put("key.deserializer", "org.apache.kafka.common.serialization.ByteArrayDeserializer");
        consumerProperties.put("value.deserializer", "org.apache.kafka.common.serialization.ByteArrayDeserializer");
        consumerProperties.put(ConsumerConfig.REQUEST_TIMEOUT_MS_CONFIG, Integer.MAX_VALUE);
        consumerProperties.put(ConsumerConfig.DEFAULT_API_TIMEOUT_MS_CONFIG, Integer.MAX_VALUE);
        initConsumer = new KafkaConsumer<>(consumerProperties);
        Set<TopicPartition> toAssign = initConsumer.partitionsFor(internalTopic)
													.stream()
													.map(partitionInfo -> new TopicPartition(partitionInfo.topic(),partitionInfo.partition()))
													.collect(Collectors.toSet());
        initConsumer.assign(toAssign);

        //
        //  populate based on Kafka
        //

        log.info("{} -- Connector.pollDirectory() init reading previous files from topic {}", connectorName, internalTopic);
        int nbInTopic = 0;
        while(!EvolutionUtilities.isEntireTopicPartitionAssignmentRead(initConsumer) && !stopRequested)
          {
            //
            // poll
            //

            ConsumerRecords<byte[], byte[]> fileRecords = initConsumer.poll(Duration.ofSeconds(5));

            //
            //  process
            //
        
            for (ConsumerRecord<byte[], byte[]> fileRecord : fileRecords)
              {
                nbInTopic++;
                //
                //  parse
                //

                StringValue filename = stringValueSerde.deserializer().deserialize(fileRecord.topic(), fileRecord.value());
                if (initialFiles.contains(filename.getValue()))
                  {
                    previousFiles.add(new File(directory,filename.getValue()));
                  }

              }

          }
        log.info("{} -- Connector.pollDirectory() init over, {} files in topic, {} files in folder, {} files in folder already in topic", connectorName, nbInTopic, initialFiles.size(), previousFiles.size());
      }
    finally
      {
        initConsumer.close();
        initConsumer=null;
      }
    
    /*****************************************
    *
    *  run
    *
    *****************************************/
    
    NGLMRuntime.registerSystemTimeDependency(this);
    Map<File,DatedFileAttributes> unverifiedFiles = new HashMap<File,DatedFileAttributes>();
    Date now = SystemTime.getCurrentTime();
    Date nextPollingDate = now;
    Date nextVerifyDate = NGLMRuntime.END_OF_TIME;
    boolean polledAtLeastOnce = false;
    while (! (stopRequested || (pollOnce && polledAtLeastOnce)))
      {
        /*****************************************
        *
        *  now
        *
        *****************************************/

        now = SystemTime.getCurrentTime();

        /*****************************************
        *
        *  wait until work is required
        *
        *****************************************/

        synchronized (this)
          {
            //
            //  wait for next wakeup time
            // 

            Date nextWakeupDate = nextPollingDate.before(nextVerifyDate) ? nextPollingDate : nextVerifyDate;
            while (! stopRequested && now.before(nextWakeupDate))
              {
                try
                  {
                    this.wait(nextWakeupDate.getTime() - now.getTime());
                  }
                catch (InterruptedException e)
                  {
                    // ignore
                  }
                now = SystemTime.getCurrentTime();
              }

            //
            //  stopRequested
            //

            if (stopRequested)
              {
                continue;
              }
          }

        /*****************************************
        *
        *  pollDirectory if necessary
        *
        *****************************************/

        if (now.compareTo(nextPollingDate) >= 0)
          {
            /****************************************
            *
            *  find new files
            *
            ****************************************/

            //
            //  make file filter
            //
            
            FileFilter filter = new FileFilter()
            {
              Pattern p = Pattern.compile(filenamePattern);

              public boolean accept(File file)
              {
                Matcher m = p.matcher(file.getName());
                return file.isFile() && m.matches();
              }
            };

            //
            //  query for files
            //

            long startDate = System.currentTimeMillis();
            File[] filesInDirectory = directory.listFiles(filter);
            if (filesInDirectory == null){
              // thanks to docker, a glusterfs lost partition on host, and recovered later, is never recovered inside the container without restart, so this shutdown
              log.error("{} -- Connector.pollDirectory() FAILED, triggering shutdown on purpose", connectorName);
              stopRequested=true;
              NGLMRuntime.failureShutdown();
            }
            List<File> fullList = new ArrayList<>();
            if (filesInDirectory != null) Arrays.stream(filesInDirectory).forEach( f -> fullList.add(f));

            // then add all files in sub-directories

            if (parseSubfolders) {
              for (File file : directory.listFiles()) {
                if (file.isDirectory()) {
                  File[] subFiles = file.listFiles(filter);
                  if (subFiles.length != 0) {
                    if (log.isDebugEnabled()) {
                      log.debug("  Adding {} files :",subFiles.length);
                      for (File f : subFiles) log.debug("   {}", f.getAbsolutePath());
                    }
                    Arrays.stream(subFiles).forEach( f -> fullList.add(f));
                  }
                }
              }
            }

            Set<File> files = new HashSet<File>(fullList);

            //
            //  log
            //

            log.debug("{} -- pollDirectory found {} in {} msec", connectorName, files.size(), Long.toString(System.currentTimeMillis() - startDate));

            //
            //  new files
            //
            
            Set<File> newFiles = new HashSet<File>(files);
            newFiles.removeAll(previousFiles);
            previousFiles = files;
            
            /****************************************
            *
            *  new files
            *
            ****************************************/
            
            for (File file : newFiles)
              {
                //
                //  invariant
                //
                
                if (unverifiedFiles.containsKey(file))
                  {
                    log.error("{} -- Found file {} that already exists in unverifiedFiles - continuing. ", connectorName, file);
                    continue;
                  }
                
                //
                //  initialize
                //
                
                try
                  {
                    unverifiedFiles.put(file, new DatedFileAttributes(now, Files.<BasicFileAttributes>readAttributes(file.toPath(), BasicFileAttributes.class)));
                  }
                catch (IOException e)
                  {
                    log.error("{} -- Connector.readAttributes() FAILED {}", connectorName, e.getMessage());
                    continue;
                  }
              }

            //
            // set polled at least once bit
            //
            
            polledAtLeastOnce = true;
          }

        /*****************************************
        *
        *  verify and move to filesToProcess if appropriate
        *
        *****************************************/

        //
        //  files to verify
        //

        Set<File> filesToVerify = new HashSet<File>();
        for (File file : unverifiedFiles.keySet())
          {
            DatedFileAttributes attributes = unverifiedFiles.get(file);
            if (now.compareTo(attributes.getVerifyDate()) >= 0)
              {
                filesToVerify.add(file);
              }
          }

        //
        //  verify 
        //

        SortedSet<File> filesToProcess = new TreeSet<File>();
        for (File file : filesToVerify)
          {
            //
            //  updatedAttributes
            //

            DatedFileAttributes attributes = unverifiedFiles.get(file);
            DatedFileAttributes updatedAttributes = null;
            if (now.compareTo(attributes.getDate()) > 0)
              {
                try
                  {
                    updatedAttributes = new DatedFileAttributes(now, Files.<BasicFileAttributes>readAttributes(file.toPath(), BasicFileAttributes.class));
                  }
                catch (IOException e)
                  {
                    log.error("{} -- Connector.readAttributes() FAILED {}", connectorName, e.getMessage());
                    unverifiedFiles.remove(file);
                    continue;
                  }
              }
            else
              {
                updatedAttributes = attributes;
              }

            //
            //  stable?
            //

            if (attributes.isStable(updatedAttributes))
              {
                log.debug("{} -- move to filesToProcess", file.toString());
                filesToProcess.add(file);
                unverifiedFiles.remove(file);
              }
            else
              {
                log.debug("{} -- unverified ({} : {})", file.toString(), attributes.getAttributes().size(), updatedAttributes.getAttributes().size());
                unverifiedFiles.put(file, updatedAttributes);
              }
          }
        
        /*****************************************
        *
        *  stopRequested
        *
        *****************************************/

        if (stopRequested)
          {
            continue;
          }
        
        /*****************************************
        *
        *  submit
        *
        *****************************************/

        for (File file : filesToProcess)
          {
            producer.send(new ProducerRecord<byte[], byte[]>(internalTopic, stringKeySerde.serializer().serialize(internalTopic, new StringKey(file.getAbsolutePath())), stringValueSerde.serializer().serialize(internalTopic, new StringValue(file.getAbsolutePath()))));
          }
        if(!filesToProcess.isEmpty())
          {
            producer.flush();
            log.info("{} -- Connector.pollDirectory() {} files pushed for processing", connectorName, filesToProcess.size());
          }

        /*****************************************
        *
        *  nextPollingDate
        *
        *****************************************/

        while (nextPollingDate.compareTo(now) <= 0)
          {
            nextPollingDate = RLMDateUtils.addSeconds(nextPollingDate, pollingInterval);
          }

        /*****************************************
        *
        *  nextVerifyDate
        *
        *****************************************/

        nextVerifyDate = NGLMRuntime.END_OF_TIME;
        for (DatedFileAttributes attributes : unverifiedFiles.values())
          {
            nextVerifyDate = attributes.getVerifyDate().before(nextVerifyDate) ? attributes.getVerifyDate() : nextVerifyDate;
          }
      }

    /****************************************
    *
    *  shutdown
    *
    ****************************************/

    producer.close();
  }
  
  /****************************************
  *
  *  readString
  *
  ****************************************/

  //
  //  readString
  //

  public static String readString(String token, String record, String defaultValue)
  {
    return (token != null && token.trim().length() > 0) ? token.trim() : defaultValue;
  }

  //
  //  readString (without defaultValue)
  //

  public static String readString(String token, String record)
  {
    return readString(token, record, null);
  }
  
  /****************************************
  *
  *  readInteger
  *
  ****************************************/

  //
  //  readInteger
  //
      
  public static Integer readInteger(String token, String record, Integer defaultValue) throws IllegalArgumentException
  {
    Integer result = defaultValue;
    if (token != null && token.trim().length() > 0)
      {
        try
          {
            result = Integer.parseInt(token.trim());
          }
        catch (NumberFormatException e)
          {
            log.info("processRecord unparsable integer {} in {}", token, record);
            throw new IllegalArgumentException(e);
          }
      }
    return result;
  }

  //
  //  readInteger  (without defaultValue)
  //
  
  public static Integer readInteger(String token, String record) throws IllegalArgumentException
  {
    return readInteger(token, record, null);
  }
  
  /****************************************
  *
  *  readLong
  *
  ****************************************/

  //
  //  readLong
  //
      
  public static Long readLong(String token, String record, Long defaultValue) throws IllegalArgumentException
  {
    Long result = defaultValue;
    if (token != null && token.trim().length() > 0)
      {
        try
          {
            result = Long.parseLong(token.trim());
          }
        catch (NumberFormatException e)
          {
            log.info("processRecord unparsable long {} in {}", token, record);
            throw new IllegalArgumentException(e);
          }
      }
    return result;
  }
  
  //
  //  readLong (without defaultValue)
  //

  public static Long readLong(String token, String record) throws IllegalArgumentException
  {
    return readLong(token, record, null);
  }

  /****************************************
  *
  *  readBoolean
  *
  ****************************************/

  //
  //  readBoolean
  //
      
  public static Boolean readBoolean(String token, String record, Boolean defaultValue) throws IllegalArgumentException
  {
    Boolean result = defaultValue;
    if (token != null && token.trim().length() > 0)
      {
        try
          {
            result = Boolean.parseBoolean(token.trim());
          }
        catch (NumberFormatException e)
          {
            log.info("processRecord unparsable boolean {} in {}", token, record);
            throw new IllegalArgumentException(e);
          }
      }
    return result;
  }

  //
  //  readBoolean (without defaultValue)
  //
      
  public static Boolean readBoolean(String token, String record) throws IllegalArgumentException
  {
    return readBoolean(token, record, null);
  }

  /****************************************
  *
  *  readDate
  *
  ****************************************/

  //
  //  readDate
  //
      
  public static Date readDate(String token, String record, String format, String timeZone, Date defaultValue) throws IllegalArgumentException
  {
    Date result = defaultValue;
    if (token != null && token.trim().length() > 0)
      {
        try
          {
            DateFormat dateFormat = new SimpleDateFormat(format);
            dateFormat.setTimeZone(TimeZone.getTimeZone(timeZone));
            result = dateFormat.parse(token.trim());
          }
        catch (ParseException e)
          {
            log.info("processRecord unparsable date {} in {}", token, record);
            throw new IllegalArgumentException(e);
          }
      }
    return result;
  }
  
  //
  //  readDate (without defaultValue)
  //
      
  public static Date readDate(String token, String record, String format, String timeZone) throws IllegalArgumentException
  {
    return readDate(token, record, format, timeZone, null);
  }

  //
  //  readDate (without defaultValue)
  //
      
  public static Date readDate(String token, String record, String format, Date defaultValue, int tenantID) throws IllegalArgumentException
  {
    return readDate(token, record, format, Deployment.getDeployment(tenantID).getTimeZone(), defaultValue);
  }

  //
  //  readDate (without defaultValue)
  //
      
  public static Date readDate(String token, String record, String format, int tenantID) throws IllegalArgumentException
  {
    return readDate(token, record, format, Deployment.getDeployment(tenantID).getTimeZone(), null);
  }

  /****************************************
  *
  *  readDouble
  *
  ****************************************/

  //
  //  readDouble
  //
      
  public static Double readDouble(String token, String record, Double defaultValue) throws IllegalArgumentException
  {
    Double result = defaultValue;
    if (token != null && token.trim().length() > 0)
      {
        try
          {
            result = Double.parseDouble(token.trim());
          }
        catch (NumberFormatException e)
          {
            log.info("processRecord unparsable double {} in {}", token, record);
            throw new IllegalArgumentException(e);
          }
      }
    return result;
  }
  
  //
  //  readDouble (without defaultValue)
  //
      
  public static Double readDouble(String token, String record) throws IllegalArgumentException
  {
    return readDouble(token, record, null);
  }

  /*****************************************
  *
  *  class DatedFileAttributes
  *
  *****************************************/

  private class DatedFileAttributes
  {
    /*****************************************
    *
    *  data
    *
    *****************************************/

    private Date date;
    private Date verifyDate;
    private BasicFileAttributes attributes;

    /*****************************************
    *
    *  constructor
    *
    *****************************************/

    private DatedFileAttributes(Date date, BasicFileAttributes attributes)
    {
      this.date = date;
      this.verifyDate = RLMDateUtils.addSeconds(date, verifySizeInterval);
      this.attributes = attributes;
    }

    /*****************************************
    *
    *  accessors
    *
    *****************************************/

    private Date getDate() { return date; }
    private Date getVerifyDate() { return verifyDate; }
    private BasicFileAttributes getAttributes() { return attributes; }

    /*****************************************
    *
    *  isStable
    *
    *****************************************/

    private boolean isStable(DatedFileAttributes other)
    {
      return attributes.lastModifiedTime().equals(other.getAttributes().lastModifiedTime()) && attributes.size() == other.getAttributes().size();
    }
  }
}
