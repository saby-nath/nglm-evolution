/*****************************************************************************
*
*  SubscriberGroupEpochService.java
*
*****************************************************************************/

package com.evolving.nglm.evolution;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.zookeeper.CreateMode;
import org.apache.zookeeper.KeeperException;
import org.apache.zookeeper.WatchedEvent;
import org.apache.zookeeper.Watcher;
import org.apache.zookeeper.ZooKeeper;
import org.apache.zookeeper.ZooDefs.Ids;
import org.apache.zookeeper.data.Stat;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.evolving.nglm.core.ConnectSerde;
import com.evolving.nglm.core.ServerRuntimeException;
import com.evolving.nglm.core.StringKey;
import com.evolving.nglm.evolution.SubscriberGroupLoader.LoadType;

public class SubscriberGroupEpochService
{
  
  /*****************************************
  *
  *  configuration
  *
  *****************************************/

  //
  //  logger
  //

  private static final Logger log = LoggerFactory.getLogger(SubscriberGroupEpochService.class);
  
  //
  //  
  //

  private static final String SubscriberGroupEpochNodes = "/subscriberGroups/epochs/";
  private static final String SubscriberGroupLockNodes = "/subscriberGroups/locks/";

  //
  //  serdes
  //
  
  private static ConnectSerde<StringKey> stringKeySerde = StringKey.serde();
  private static ConnectSerde<SubscriberGroupEpoch> subscriberGroupEpochSerde = SubscriberGroupEpoch.serde();
  
  /****************************************
  *
  *  openZooKeeperAndLockGroup
  *
  ****************************************/

  public static ZooKeeper openZooKeeperAndLockGroup(String groupName)
  {
    //
    //  open zookeeper
    //
    
    ZooKeeper zookeeper = null;
    while (zookeeper == null)
      {
        try
          {
            zookeeper = new ZooKeeper(System.getProperty("zookeeper.connect"), 3000, new Watcher() { @Override public void process(WatchedEvent event) {} }, false);
          }
        catch (IOException e)
          {
            // ignore
          }
      }

    //
    //  ensure connected
    //

    ensureZooKeeper(zookeeper);
    
    //
    //  lock group
    //

    try
      {
        zookeeper.create(Deployment.getZookeeperRoot() + SubscriberGroupLockNodes + groupName, new byte[0], Ids.OPEN_ACL_UNSAFE, CreateMode.EPHEMERAL);
      }
    catch (KeeperException.NodeExistsException e)
      {
        log.error("subscriber group {} currently being updated", groupName);
        System.exit(-1);
      }
    catch (KeeperException e)
      {
        log.error("openZooKeeperAndLockGroup() - lock() - KeeperException code {}", e.code());
        throw new ServerRuntimeException("zookeeper", e);
      }
    catch (InterruptedException e)
      {
        log.error("openZooKeeperAndLockGrou() - lock() - Interrupted exception");
        throw new ServerRuntimeException("zookeeper", e);
      }

    //
    //  return
    //

    return zookeeper;
  }

  /****************************************
  *
  *  closeZooKeeperAndReleaseGroup
  *
  ****************************************/

  public static void closeZooKeeperAndReleaseGroup(ZooKeeper zookeeper, String groupName)
  {
    //
    //  ensure connected
    //

    ensureZooKeeper(zookeeper);

    //
    //  release group
    //

    // ephemeral node - will vanish on close

    //
    //  close
    //

    try
      {
        zookeeper.close();
      }
    catch (InterruptedException e)
      {
        // ignore
      }
  }
  
  /****************************************
  *
  *  ensureZooKeeper
  *
  ****************************************/

  private static void ensureZooKeeper(ZooKeeper zookeeper)
  {
    //
    //  ensure connected
    //

    while (zookeeper.getState().isAlive() && ! zookeeper.getState().isConnected())
      {
        try { Thread.currentThread().sleep(200); } catch (InterruptedException ie) { }
      }

    //
    //  verify connected
    //

    if (! zookeeper.getState().isConnected())
      {
        throw new ServerRuntimeException("zookeeper unavailable");
      }
  }
  
  /****************************************
  *
  *  retrieveSubscriberGroupEpoch
  *
  ****************************************/

  public static SubscriberGroupEpoch retrieveSubscriberGroupEpoch(ZooKeeper zookeeper, String dimensionID, LoadType loadType, String display)
  {
    /*****************************************
    *
    *  zookeeper
    *
    *****************************************/

    //
    //  ensure connected
    //

    ensureZooKeeper(zookeeper);

    //
    //  ensure subscriberGroupEpoch node exists
    //

    boolean subscriberGroupEpochNodeExists = false;
    String node = Deployment.getZookeeperRoot() + SubscriberGroupEpochNodes + dimensionID;
    if (! subscriberGroupEpochNodeExists)
      {
        //
        //  read existing node
        //

        try
          {
            subscriberGroupEpochNodeExists = (zookeeper.exists(node, false) != null);
            if (!subscriberGroupEpochNodeExists) log.info("subscriberGroupEpoch node with ID {} does not exist", dimensionID);
          }
        catch (KeeperException e)
          {
            log.error("retrieveSubscriberGroupEpoch() - exists() - KeeperException code {}", e.code());
            throw new ServerRuntimeException("zookeeper", e);
          }
        catch (InterruptedException e)
          {
            log.error("retrieveSubscriberGroupEpoch() - exists() - Interrupted exception");
            throw new ServerRuntimeException("zookeeper", e);
          }

        //
        //  ensure exists (if not new)
        //

        if (! subscriberGroupEpochNodeExists && loadType != LoadType.New)
          {
            throw new ServerRuntimeException("no such group");
          }
        
        //
        //  create subscriberGroupEpoch node (if necessary)
        //

        if (! subscriberGroupEpochNodeExists)
          {
            log.info("retrieveSubscriberGroupEpoch() - creating node {}", dimensionID);
            try
              {
                SubscriberGroupEpoch newSubscriberGroupEpoch = new SubscriberGroupEpoch(dimensionID, display);
                JSONObject jsonNewSubscriberGroupEpoch = newSubscriberGroupEpoch.getJSONRepresentation();
                String stringNewSubscriberGroupEpoch = jsonNewSubscriberGroupEpoch.toString();
                byte[] rawNewSubscriberGroupEpoch = stringNewSubscriberGroupEpoch.getBytes(StandardCharsets.UTF_8);
                zookeeper.create(node, rawNewSubscriberGroupEpoch, Ids.OPEN_ACL_UNSAFE, CreateMode.PERSISTENT);
                subscriberGroupEpochNodeExists = true;
              }
            catch (KeeperException.NodeExistsException e)
              {
                subscriberGroupEpochNodeExists = true;
              }
            catch (KeeperException e)
              {
                log.error("retrieveSubscriberGroupEpoch() - create() - KeeperException code {}", e.code());
                throw new ServerRuntimeException("zookeeper", e);
              }
            catch (InterruptedException e)
              {
                log.error("retrieveSubscriberGroupEpoch() - exists() - Interrupted exception");
                throw new ServerRuntimeException("zookeeper", e);
              }
          }
      }
    
    //
    //  read subscriberGroupEpoch
    //

    SubscriberGroupEpoch subscriberGroupEpoch = null;
    try
      {
        Stat stat = new Stat();
        byte[] rawSubscriberGroupEpoch = zookeeper.getData(node, null, stat);
        String stringSubscriberGroupEpoch = new String(rawSubscriberGroupEpoch, StandardCharsets.UTF_8);
        JSONObject jsonSubscriberGroupEpoch = (JSONObject) (new JSONParser()).parse(stringSubscriberGroupEpoch);
        subscriberGroupEpoch = new SubscriberGroupEpoch(jsonSubscriberGroupEpoch, stat.getVersion());
      }
    catch (KeeperException e)
      {
        log.error("retrieveSubscriberGroupEpoch() - getData() - KeeperException code {}", e.code());
        throw new ServerRuntimeException("zookeeper", e);
      }
    catch (InterruptedException|ParseException e)
      {
        log.error("retrieveSubscriberGroupEpoch() - getData() - Exception");
        throw new ServerRuntimeException("zookeeper", e);
      }

    //
    //  return
    //

    return subscriberGroupEpoch;
  }

  /****************************************
  *
  *  updateSubscriberGroupEpoch
  *
  ****************************************/

  public static void updateSubscriberGroupEpoch(ZooKeeper zookeeper, SubscriberGroupEpoch existingSubscriberGroupEpoch, int epoch, boolean active, KafkaProducer<byte[], byte[]> kafkaProducer, String subscriberGroupEpochTopic)
  {
    /*****************************************
    *
    *  zookeeper
    *
    *****************************************/

    //
    //  ensure connected
    //

    ensureZooKeeper(zookeeper);

    //
    //  update subscriberGroupEpoch node
    //

    SubscriberGroupEpoch subscriberGroupEpoch = new SubscriberGroupEpoch(existingSubscriberGroupEpoch.getDimensionID(), epoch, existingSubscriberGroupEpoch.getDisplay(), active);
    String node = Deployment.getZookeeperRoot() + SubscriberGroupEpochNodes + subscriberGroupEpoch.getDimensionID();
    try
      {
        JSONObject jsonSubscriberGroupEpoch = subscriberGroupEpoch.getJSONRepresentation();
        String stringSubscriberGroupEpoch = jsonSubscriberGroupEpoch.toString();
        byte[] rawSubscriberGroupEpoch = stringSubscriberGroupEpoch.getBytes(StandardCharsets.UTF_8);
        zookeeper.setData(node, rawSubscriberGroupEpoch, existingSubscriberGroupEpoch.getZookeeperVersion());
      }
    catch (KeeperException.BadVersionException e)
      {
        log.error("concurrent write aborted for subscriberGroupEpoch {}", subscriberGroupEpoch.getDimensionID());
        throw new ServerRuntimeException("zookeeper", e);
      }
    catch (KeeperException e)
      {
        log.error("setData() - KeeperException code {}", e.code());
        throw new ServerRuntimeException("zookeeper", e);
      }
    catch (InterruptedException e)
      {
        log.error("setData() - InterruptedException");
        throw new ServerRuntimeException("zookeeper", e);
      }
    
    /*****************************************
    *
    *  kafka
    *
    *****************************************/

    kafkaProducer.send(new ProducerRecord<byte[], byte[]>(subscriberGroupEpochTopic, stringKeySerde.serializer().serialize(subscriberGroupEpochTopic, new StringKey(subscriberGroupEpoch.getDimensionID())), subscriberGroupEpochSerde.serializer().serialize(subscriberGroupEpochTopic, subscriberGroupEpoch)));
    
    /*****************************************
    *
    *  log
    *
    *****************************************/

    log.info("updateSubscriberGroupEpoch() - updated group {}", subscriberGroupEpoch.getDimensionID());
  }

}
