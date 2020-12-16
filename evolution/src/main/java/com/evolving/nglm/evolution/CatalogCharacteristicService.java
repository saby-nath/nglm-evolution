/****************************************************************************
*
*  CatalogCharacteristicService.java
*
****************************************************************************/

package com.evolving.nglm.evolution;

import com.evolving.nglm.evolution.GUIManager.GUIManagerException;

import com.evolving.nglm.core.ConnectSerde;
import com.evolving.nglm.core.NGLMRuntime;
import com.evolving.nglm.core.ServerRuntimeException;
import com.evolving.nglm.core.StringKey;
import com.evolving.nglm.core.SystemTime;

import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.common.TopicPartition;

import org.json.simple.JSONObject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

public class CatalogCharacteristicService extends GUIService
{
  /*****************************************
  *
  *  configuration
  *
  *****************************************/

  //
  //  logger
  //

  private static final Logger log = LoggerFactory.getLogger(CatalogCharacteristicService.class);

  /*****************************************
  *
  *  data
  *
  *****************************************/

  private CatalogCharacteristicListener catalogCharacteristicListener = null;

  /*****************************************
  *
  *  constructor
  *
  *****************************************/

  public CatalogCharacteristicService(String bootstrapServers, String groupID, String catalogCharacteristicTopic, boolean masterService, CatalogCharacteristicListener catalogCharacteristicListener, boolean notifyOnSignificantChange)
  {
    super(bootstrapServers, "CatalogCharacteristicService", groupID, catalogCharacteristicTopic, masterService, getSuperListener(catalogCharacteristicListener), "putCatalogCharacteristic", "removeCatalogCharacteristic", notifyOnSignificantChange);
  }

  //
  //  constructor
  //

  public CatalogCharacteristicService(String bootstrapServers, String groupID, String catalogCharacteristicTopic, boolean masterService, CatalogCharacteristicListener catalogCharacteristicListener)
  {
    this(bootstrapServers, groupID, catalogCharacteristicTopic, masterService, catalogCharacteristicListener, true);
  }

  //
  //  constructor
  //

  public CatalogCharacteristicService(String bootstrapServers, String groupID, String catalogCharacteristicTopic, boolean masterService)
  {
    this(bootstrapServers, groupID, catalogCharacteristicTopic, masterService, (CatalogCharacteristicListener) null, true);
  }

  //
  //  getSuperListener
  //

  private static GUIManagedObjectListener getSuperListener(CatalogCharacteristicListener catalogCharacteristicListener)
  {
    GUIManagedObjectListener superListener = null;
    if (catalogCharacteristicListener != null)
      {
        superListener = new GUIManagedObjectListener()
        {
          @Override public void guiManagedObjectActivated(GUIManagedObject guiManagedObject) { catalogCharacteristicListener.catalogCharacteristicActivated((CatalogCharacteristic) guiManagedObject); }
          @Override public void guiManagedObjectDeactivated(String guiManagedObjectID, int tenantID) { catalogCharacteristicListener.catalogCharacteristicDeactivated(guiManagedObjectID); }
        };
      }
    return superListener;
  }

  /*****************************************
  *
  *  getSummaryJSONRepresentation
  *
  *****************************************/

  @Override protected JSONObject getSummaryJSONRepresentation(GUIManagedObject guiManagedObject)
  {
    JSONObject result = super.getSummaryJSONRepresentation(guiManagedObject);
    result.put("type", guiManagedObject.getJSONRepresentation().get("type"));
    result.put("unit", guiManagedObject.getJSONRepresentation().get("unit"));
    return result;
  }
  
  /*****************************************
  *
  *  getCatalogCharacteristics
  *
  *****************************************/

  public String generateCatalogCharacteristicID() { return generateGUIManagedObjectID(); }
  public GUIManagedObject getStoredCatalogCharacteristic(String catalogCharacteristicID, int tenantID) { return getStoredGUIManagedObject(catalogCharacteristicID, tenantID); }
  public GUIManagedObject getStoredCatalogCharacteristic(String catalogCharacteristicID, boolean includeArchived, int tenantID) { return getStoredGUIManagedObject(catalogCharacteristicID, includeArchived, tenantID); }
  public Collection<GUIManagedObject> getStoredCatalogCharacteristics(int tenantID) { return getStoredGUIManagedObjects(tenantID); }
  public Collection<GUIManagedObject> getStoredCatalogCharacteristics(boolean includeArchived, int tenantID) { return getStoredGUIManagedObjects(includeArchived, tenantID); }
  public boolean isActiveCatalogCharacteristic(GUIManagedObject catalogCharacteristicUnchecked, Date date) { return isActiveGUIManagedObject(catalogCharacteristicUnchecked, date); }
  public CatalogCharacteristic getActiveCatalogCharacteristic(String catalogCharacteristicID, Date date, int tenantID) { return (CatalogCharacteristic) getActiveGUIManagedObject(catalogCharacteristicID, date, tenantID); }
  public Collection<CatalogCharacteristic> getActiveCatalogCharacteristics(Date date, int tenantID) { return (Collection<CatalogCharacteristic>) getActiveGUIManagedObjects(date, tenantID); }

  /*****************************************
  *
  *  putCatalogCharacteristic
  *
  *****************************************/

  public void putCatalogCharacteristic(GUIManagedObject catalogCharacteristic, boolean newObject, String userID, int tenantID) { putGUIManagedObject(catalogCharacteristic, SystemTime.getCurrentTime(), newObject, userID, tenantID); }

  /*****************************************
  *
  *  removeCatalogCharacteristic
  *
  *****************************************/

  public void removeCatalogCharacteristic(String catalogCharacteristicID, String userID, int tenantID) { removeGUIManagedObject(catalogCharacteristicID, SystemTime.getCurrentTime(), userID, tenantID); }

  /*****************************************
  *
  *  interface CatalogCharacteristicListener
  *
  *****************************************/

  public interface CatalogCharacteristicListener
  {
    public void catalogCharacteristicActivated(CatalogCharacteristic catalogCharacteristic);
    public void catalogCharacteristicDeactivated(String guiManagedObjectID);
  }

  /*****************************************
  *
  *  example main
  *
  *****************************************/

  public static void main(String[] args)
  {
    //
    //  catalogCharacteristicListener
    //

    CatalogCharacteristicListener catalogCharacteristicListener = new CatalogCharacteristicListener()
    {
      @Override public void catalogCharacteristicActivated(CatalogCharacteristic catalogCharacteristic) { System.out.println("catalogCharacteristic activated: " + catalogCharacteristic.getCatalogCharacteristicID()); }
      @Override public void catalogCharacteristicDeactivated(String guiManagedObjectID) { System.out.println("catalogCharacteristic deactivated: " + guiManagedObjectID); }
    };

    //
    //  catalogCharacteristicService
    //

    CatalogCharacteristicService catalogCharacteristicService = new CatalogCharacteristicService(Deployment.getBrokerServers(), "example-catalogCharacteristicservice-001", Deployment.getCatalogCharacteristicTopic(), false, catalogCharacteristicListener);
    catalogCharacteristicService.start();

    //
    //  sleep forever
    //

    while (true)
      {
        try
          {
            Thread.sleep(Long.MAX_VALUE);
          }
        catch (InterruptedException e)
          {
            //
            //  ignore
            //
          }
      }
  }
}
