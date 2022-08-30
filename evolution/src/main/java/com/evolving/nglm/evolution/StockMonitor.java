/*****************************************************************************
*
*  StockMonitor.java
*
*****************************************************************************/

package com.evolving.nglm.evolution;

import com.evolving.nglm.core.Deployment;
import com.evolving.nglm.core.NGLMRuntime;
import com.evolving.nglm.core.RLMDateUtils;
import com.evolving.nglm.core.SystemTime;
import com.evolving.nglm.evolution.GUIService.GUIManagedObjectListener;
import com.evolving.nglm.evolution.tenancy.Tenant;
import com.evolving.nglm.evolution.zookeeper.Configuration;
import com.evolving.nglm.evolution.zookeeper.ZookeeperEvolution;
import com.evolving.nglm.evolution.zookeeper.ZookeeperJSONObject;
import org.apache.zookeeper.CreateMode;
import org.apache.zookeeper.KeeperException;
import org.apache.zookeeper.WatchedEvent;
import org.apache.zookeeper.Watcher;
import org.apache.zookeeper.ZooKeeper;
import org.apache.zookeeper.ZooDefs.Ids;
import org.apache.zookeeper.data.Stat;

import com.rii.utilities.JSONUtilities;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ConcurrentHashMap;

public class StockMonitor implements Runnable
{
  /*****************************************
  *
  *  configuration
  *
  *****************************************/

  private static final Logger log = LoggerFactory.getLogger(StockMonitor.class);

  /*****************************************
  *
  *  data
  *
  *****************************************/

  //
  //  key
  //

  private String stockMonitorKey;
  private boolean stockMonitorKeysChanged = false;
  //
  //  stockableItems
  //

  private Set<GUIService> stockItemServices;
  private Map<String,StockableItem> stockableItems = new HashMap<>();
  private Map<String,StockableItem> updatedStockableItems = new HashMap<>();
  
  //
  //  local stock
  //

  private Map<String,LocalAllocation> localAllocations = new HashMap<String,LocalAllocation>();
  private Map<String,LocalUncommitted> localUncommitted = new HashMap<String,LocalUncommitted>();

  //
  //  worker
  //

  private volatile boolean running = true;
  private Thread worker = null;

  //
  //  zookeeper
  //
  
  private ZooKeeper zookeeper = null;
  private boolean sessionKeyExists = false;
  private String stockRoot = Deployment.getZookeeperRoot() + "/stock";

  /*****************************************
  *
  *  constructor
  *
  *****************************************/

  public StockMonitor(String stockMonitorKey, GUIService... stockItemServices)
  {
    log.info("[PRJT] StockMonitor()...");
    //
    //  simple
    //

    this.stockMonitorKey = stockMonitorKey;
    this.worker = new Thread(this, "StockMonitor");
    this.stockItemServices = new HashSet<GUIService>(Arrays.<GUIService>asList(stockItemServices));

    //
    //  register listeners
    //

    for (GUIService guiService : this.stockItemServices)
      {
        GUIManagedObjectListener listener = new GUIManagedObjectListener()
        {
          @Override public void guiManagedObjectActivated(GUIManagedObject guiManagedObject) {
            //a bit hacky, but services might not have only StockableItem (ie VoucherService got VoucherPersonal which are not, but VoucherShared are)
            if (guiManagedObject instanceof StockableItem) monitorStockableItem((StockableItem) guiManagedObject);
          }
          @Override public void guiManagedObjectDeactivated(String guiManagedObjectID, int tenantID) { }
        };
        guiService.registerListener(listener);
      }

    //
    //  initial stockableItems
    //

    Date now = SystemTime.getCurrentTime();
    for(Tenant tenant : Deployment.getTenants())
      {
        int tenantID = tenant.getTenantID();
        for (GUIService guiService : this.stockItemServices)
          {
            for (GUIManagedObject guiManagedObject : guiService.getActiveGUIManagedObjects(now, tenantID))
              {
                //a bit hacky, but services might not have only StockableItem (ie VoucherService got VoucherPersonal which are not, but VoucherShared are)
                if (guiManagedObject instanceof StockableItem) monitorStockableItem((StockableItem) guiManagedObject);
              }
          }
      }
  }

  /*****************************************
  *
  *  start
  *
  *****************************************/

  public void start()
  {
    worker.start();
  }

  /*****************************************
  *
  *  close
  *
  *****************************************/

  public void close()
  {
    //
    //  signal shutdown
    //
    
    synchronized (this)
      {
        running = false;
        this.notifyAll();
      }

    //
    //  wait for flush
    //
    
    Date now = SystemTime.getCurrentTime();
    Date flushTimeout = RLMDateUtils.addSeconds(now, 15);
    while (now.before(flushTimeout))
      {
        try
          {
            long waitTime = flushTimeout.getTime() - now.getTime();
            worker.join(waitTime);
            break;
          }
        catch (InterruptedException e)
          {
          }
        now = SystemTime.getCurrentTime();
      }
  }

  /*****************************************
  *
  *  monitorStockableItem
  *
  *****************************************/

  private synchronized void monitorStockableItem(StockableItem stockableItem)
  {
    log.info("[PRJT] StockMonitor.monitorStockableItem()...");
    if (stockableItem.getStock() != null)
      {
        //
        //  update allocations/used
        //

        if (! stockableItems.keySet().contains(stockableItem.getStockableItemID()))
          {
            localAllocations.put(stockableItem.getStockableItemID(), new LocalAllocation());
            localUncommitted.put(stockableItem.getStockableItemID(), new LocalUncommitted());
          }

        //
        //  update stockableItems
        //

        stockableItems.remove(stockableItem.getStockableItemID());
        stockableItems.put(stockableItem.getStockableItemID(),stockableItem);
        updatedStockableItems.remove(stockableItem.getStockableItemID());
        updatedStockableItems.put(stockableItem.getStockableItemID(),stockableItem);

        //
        //  notify
        //

        this.notifyAll();
      }
  }

  /*****************************************
  *
  *  consume
  *
  *****************************************/

  public synchronized boolean consume(StockableItem stockableItem, int quantity)
  {
    log.info("[PRJT] StockMonitor.consume()...");
    boolean stockAvailable = reserve(stockableItem, quantity);
    if (stockAvailable) confirmReservation(stockableItem, quantity);
    return stockAvailable;
  }

  /*****************************************
  *
  *  reserve
  *
  *****************************************/

  public synchronized boolean reserve(StockableItem stockableItem, int quantity)
  {
    log.info("[PRJT] StockMonitor.reserve()...");
    //
    //  unlimited stock
    //

    if (stockableItem.getStock() == null)
      {
        return true;
      }

    //
    //  running?
    //

    if (!running)
      {
        return false;
      }
    
    //
    //  stock
    //
    
    LocalAllocation allocation = localAllocations.get(stockableItem.getStockableItemID());
    LocalUncommitted uncommitted = localUncommitted.get(stockableItem.getStockableItemID());
    if (allocation == null || uncommitted == null)
      {
        return false;
      }

    //
    //  test
    //

    boolean stockAvailable = allocation.getAllocated() >= quantity;
    
    //
    //  apply
    //

    if (stockAvailable)
      {
        allocation.reserve(quantity);
        uncommitted.reserve(quantity);
      }
    
    //
    //  return
    //

    return stockAvailable;
  }

  /*****************************************
  *
  *  confirmReservation
  *
  *****************************************/

  public synchronized void confirmReservation(StockableItem stockableItem, int quantity)
  {
    log.info("[PRJT] StockMonitor.confirmReservation()...");
    LocalUncommitted uncommitted = localUncommitted.get(stockableItem.getStockableItemID());
    if (stockableItem.getStock() != null && uncommitted != null)
      {
        uncommitted.confirmReservation(quantity);
      }
  }

  /*****************************************
  *
  *  voidReservation
  *
  *****************************************/

  public synchronized void voidReservation(StockableItem stockableItem, int quantity)
  {
    log.info("[PRJT] StockMonitor.voidReservation()...");
    //
    //  stock
    //
    
    LocalAllocation allocation = localAllocations.get(stockableItem.getStockableItemID());
    LocalUncommitted uncommitted = localUncommitted.get(stockableItem.getStockableItemID());
    if (stockableItem.getStock() != null && allocation != null && uncommitted != null)
      {
        allocation.voidReservation(quantity);
        uncommitted.voidReservation(quantity);
      }
  }
  
  /*****************************************
  *
  *  voidConsumption
  *
  *****************************************/

  public synchronized void voidConsumption(StockableItem stockableItem, int quantity)
  {
    log.info("[PRJT] StockMonitor.voidConsumption()...");
    //
    //  stock
    //
    
    LocalAllocation allocation = localAllocations.get(stockableItem.getStockableItemID());
    LocalUncommitted uncommitted = localUncommitted.get(stockableItem.getStockableItemID());
    if (stockableItem.getStock() != null && allocation != null && uncommitted != null)
      {
        allocation.voidReservation(quantity);
        uncommitted.voidConsumption(quantity);
      }
  }

  /*****************************************
  *
  *  run
  *   -- periodic, every stockRefreshPeriod sec
  *   -- updatedStockableItems.size() > 0
  *   -- stockMonitorKeys.watcher fires
  *   -- running = false
  *
  *****************************************/

  @Override public void run()
  {
    log.info("[PRJT] StockMonitor.run()...");
    /*****************************************
    *
    *  data
    *
    *****************************************/

    List<StockableItem> stockableItemsToProcess = new ArrayList<>();

    /*****************************************
    *
    *  main loop
    *
    *****************************************/


    NGLMRuntime.registerSystemTimeDependency(this);
    Date nextProcessingTime = RLMDateUtils.addSeconds(SystemTime.getCurrentTime(), Deployment.getStockRefreshPeriod());
    while (running)
      {
        //
        //  stockableToProcess
        //

        stockableItemsToProcess.clear();
        synchronized (this)
          {
            //
            //  updatedStockableItems
            //

            stockableItemsToProcess.addAll(updatedStockableItems.values());
            updatedStockableItems.clear();

            //
            //  periodic wakeup
            //

            Date now = SystemTime.getCurrentTime();
            boolean periodicCheck = false;
            if (now.compareTo(nextProcessingTime) >= 0)
              {
                stockableItemsToProcess.addAll(stockableItems.values());
                periodicCheck = true;
                while (now.compareTo(nextProcessingTime) >= 0)
                  {
                    nextProcessingTime = RLMDateUtils.addSeconds(nextProcessingTime, Deployment.getStockRefreshPeriod());
                  }
              }

            //
            //  stockMonitorKeysChanged
            //

            if (!periodicCheck && stockMonitorKeysChanged)
              {
                stockableItemsToProcess.addAll(stockableItems.values());
                stockMonitorKeysChanged = false;
              }
          }

        //
        //  process stockableItems
        //

        for (StockableItem stockableItem : stockableItemsToProcess)
          {
            processStock(stockableItem);
          }

        //
        //  wait for next processing request
        //

        synchronized (this)
          {
            Date now = SystemTime.getCurrentTime();
            if (now.before(nextProcessingTime))
              {
                try
                  {
                    this.wait(nextProcessingTime.getTime() - now.getTime());
                  }
                catch (InterruptedException e)
                  {
                    // ignore
                  }
              }
            else
              {
                log.warn("StockMonitor.processStock() - took {} ms while the configured refresh period is {} sec", Deployment.getStockRefreshPeriod()*1000+now.getTime()-nextProcessingTime.getTime(), Deployment.getStockRefreshPeriod());
              }
          }
      }

    /*****************************************
    *
    *  shutdown
    *
    *****************************************/

    //
    //  stockableItemsToProcess
    //

    stockableItemsToProcess.clear();
    synchronized (this)
      {
        stockableItemsToProcess.addAll(stockableItems.values());
      }

    //
    //  final processing (to flush)
    //

    for (StockableItem stockableItem : stockableItemsToProcess)
      {
        processStock(stockableItem);
      }

    //
    //  zookeeper
    //

    try { if(zookeeper!=null )zookeeper.close(); } catch (InterruptedException e) { }
  }

  /*****************************************
  *
  *  processStock
  *
  *****************************************/

  private void processStock(StockableItem stockableItem)
  {
    log.info("[PRJT] StockMonitor.processStock()...");
    /****************************************
    *
    *  get/reset local uncommitted usage
    *
    ****************************************/

    LocalUncommitted uncommitted;
    synchronized (this)
      {
        uncommitted = localUncommitted.get(stockableItem.getStockableItemID());
        localUncommitted.put(stockableItem.getStockableItemID(), new LocalUncommitted());
      }
    
    /****************************************
    *
    *  update stock
    *
    ****************************************/

    boolean stockUpdated = false;
    Stock stock = null;
    mainLoop:
    while (! stockUpdated)
      {
        /******************************************
        *
        *  prepare zookeeper connection
        *
        *****************************************/

        do
          {
            ensureConnected();
          }
        while (zookeeper == null || zookeeper.getState() != ZooKeeper.States.CONNECTED || !sessionKeyExists);

        /******************************************
        *
        *  ensure stock exists (if necessary)
        *
        *****************************************/

        boolean stockNodeExists = false;
        while (! stockNodeExists)
          {
            //
            //  read existing session node
            //

            try
              {
                stockNodeExists = (zookeeper.exists(stockRoot + "/stocks/" + stockableItem.getStockableItemID(), false) != null);
                if (!stockNodeExists) log.info("stock node {} does not exist", stockableItem.getStockableItemID());
              }
            catch (KeeperException e)
              {
                log.info("processStock() - exists() - KeeperException code {}", e.code());
                continue mainLoop;
              }
            catch (InterruptedException e)
              {
                continue;
              }

            //
            //  create session node (if necessary)
            //

            if (! stockNodeExists)
              {
                try
                  {
                    Stock newStock = new Stock(stockableItem.getStockableItemID());
                    JSONObject jsonNewStock = newStock.toJSON();
                    String stringNewStock = jsonNewStock.toString();
                    byte[] rawNewStock = stringNewStock.getBytes(StandardCharsets.UTF_8);
                    zookeeper.create(stockRoot + "/stocks/" + stockableItem.getStockableItemID(), rawNewStock, Ids.OPEN_ACL_UNSAFE, CreateMode.PERSISTENT);
                    stockNodeExists = true;
                  }
                catch (KeeperException.NodeExistsException e)
                  {
                    stockNodeExists = true;
                  }
                catch (KeeperException e)
                  {
                    log.info("processStock() - create() - KeeperException code {}", e.code());
                    continue mainLoop;
                  }
                catch (InterruptedException e)
                  {
                    continue;
                  }
              }
          }
        
        /******************************************
        *
        *  retrieve stock
        *
        *****************************************/

        stock = null;
        try
          {
            Stat stat = new Stat();
            byte[] rawStock = zookeeper.getData(stockRoot + "/stocks/" + stockableItem.getStockableItemID(), null, stat);
            String stringStock = new String(rawStock, StandardCharsets.UTF_8);
            JSONObject jsonStock = (JSONObject) (new JSONParser()).parse(stringStock);
            stock = new Stock(jsonStock, stat.getVersion());
          }
        catch (org.json.simple.parser.ParseException e)
          {
            throw new RuntimeException("stock", e);
          }
        catch (KeeperException e)
          {
            log.info("processStock() - getData() - KeeperException code {}", e.code());
            continue mainLoop;
          }
        catch (InterruptedException e)
          {
            continue;
          }

        /******************************************
        *
        *  retrieve stockMonitorKeys
        *
        *****************************************/

        List<String> stockMonitorKeys;
        try
          {
            stockMonitorKeys = zookeeper.getChildren(stockRoot + "/stockmonitors", true, null);
            log.debug("existing stock monitors: {}",  stockMonitorKeys);
          }
        catch (KeeperException e)
          {
            log.info("processStock() - getChildren() - KeeperException code {}", e.code());
            continue mainLoop;
          }
        catch (InterruptedException e)
          {
            continue;
          }

        /******************************************
        *
        *  log - before processing
        *
        *****************************************/

        log.debug("stock for {} before processing:    {}", stockableItem.getStockableItemID(), stock.toJSON());
        log.debug("uncommitted for {} before processing: {}", stockableItem.getStockableItemID(), uncommitted);
        log.debug("stockMonitorKeys before processing: {}", stockMonitorKeys);
        
        /******************************************
        *
        *  ensure entries for stockMonitorKey
        *
        *****************************************/

        if (! stock.getStockAllocated().containsKey(stockMonitorKey)) stock.getStockAllocated().put(stockMonitorKey, 0);
        
        /******************************************
        *
        *  apply uncommitted
        *
        *****************************************/

        //
        //  reserved
        //

        int localReserved = uncommitted.getReserved();
        int stockReserved = stock.getStockReserved();
        stock.setStockReserved(stockReserved + localReserved);

        //
        //  consumed
        //

        int localConsumed = uncommitted.getConsumed();
        int stockConsumed = stock.getStockConsumed();
        stock.setStockConsumed(stockConsumed + localConsumed);
        
        /******************************************
        *
        *  return dead allocations
        *
        *****************************************/

        //
        //  identify stockMonitorKeys in stock
        //

        Set<String> stockMonitorKeysToRemove = new HashSet<String>();
        stockMonitorKeysToRemove.addAll(stock.getStockAllocated().keySet());
        stockMonitorKeysToRemove.removeAll(stockMonitorKeys);

        //
        //  update stock
        //

        for (String key : stockMonitorKeysToRemove)
          {
            stock.getStockAllocated().remove(key);
          }
        
        /******************************************
        *
        *  rebalance our allocations
        *
        *****************************************/

        stock.getStockAllocated().put(stockMonitorKey, adjustAllocation(stockableItem, stock, stockMonitorKey, stockMonitorKeys));

        /******************************************
        *
        *  log - after processing
        *
        *****************************************/

        log.debug("processStock for {}: {}, initial stock: {}", stockableItem.getStockableItemID(), stock.toJSON(), stockableItem.getStock());
        
        /******************************************
        *
        *  update zookeeper node
        *
        *****************************************/

        try
          {
            JSONObject jsonStock = stock.toJSON();
            String stringStock = jsonStock.toString();
            byte[] rawStock = stringStock.getBytes(StandardCharsets.UTF_8);
            zookeeper.setData(stockRoot + "/stocks/" + stockableItem.getStockableItemID(), rawStock, stock.getZookeeperVersion());
          }
        catch (KeeperException.BadVersionException e)
          {
            log.info("concurrent write aborted for stock {} by {}", stockableItem.getStockableItemID(), stockMonitorKey);
            continue mainLoop;
          }
        catch (KeeperException e)
          {
            log.info("setData() - KeeperException code {}", e.code());
            continue mainLoop;
          }
        catch (InterruptedException e)
          {
            log.info("setData() - InterruptedException");
            continue;
          }

        /******************************************
        *
        *  success
        *
        *****************************************/
        
        stockUpdated = true;
      }
    
    /******************************************
    *
    *  commit new allocations
    *
    *****************************************/

    synchronized (this)
      {
        LocalAllocation currentAllocation = localAllocations.get(stockableItem.getStockableItemID());
        LocalUncommitted currentUncommitted = localUncommitted.get(stockableItem.getStockableItemID());
        currentAllocation.setAllocated(stock.getStockAllocated().get(stockMonitorKey) - currentUncommitted.getUsed());
      }
  }

  /*****************************************
  *
  *  adjustAllocation
  *
  *****************************************/

  private int adjustAllocation(StockableItem stockableItem, Stock stock, String stockMonitorKey, List<String> stockMonitorKeys)
  {
    log.info("[PRJT] StockMonitor.adjustAllocation()...");
    //
    //  determine ideal allocation
    //

    int remaining = stockableItem.getStock() - stock.getStockUsed();
    int unallocated = Math.max(remaining - (stock.getTotalAllocated() - stock.getStockAllocated().get(stockMonitorKey)), 0);
    int target = (int) Math.ceil((double) remaining / (double) stockMonitorKeys.size());
    int ideal = Math.min(target, unallocated);

    //
    //  determine ask and alter local budget (as necessary)
    //

    int ask = ideal;
    synchronized (this)
      {
        int currentAllocation = localAllocations.get(stockableItem.getStockableItemID()).getAllocated();
        int currentUncommitted = localUncommitted.get(stockableItem.getStockableItemID()).getUsed();
        if (ideal < currentAllocation + currentUncommitted)
          {
            localAllocations.get(stockableItem.getStockableItemID()).setAllocated(Math.max(ideal - currentUncommitted, 0));
            ask = localAllocations.get(stockableItem.getStockableItemID()).getAllocated() + currentUncommitted;
          }
      }

    //
    //  return
    //

    return ask;
  }

  /*****************************************
  *
  *  ensureConnected
  *
  *****************************************/

  private void ensureConnected()
  {
    /*****************************************
    *
    *  zookeeper client status
    *
    *****************************************/

    if (zookeeper != null && ! zookeeper.getState().isAlive())
      {
        log.info("closing zookeeper client due to status {}", zookeeper.getState());
        try { zookeeper.close(); } catch (InterruptedException e) { }
        zookeeper = null;
      }

    /*****************************************
    *
    *  (re-) create zookeeper client (if necessary)
    *
    *****************************************/

    while (zookeeper == null)
      {
        try
          {
            zookeeper = new ZooKeeper(Deployment.getZookeeperConnect(), 3000, new Watcher() { @Override public void process(WatchedEvent event) { processWatchedEvent(event); } }, false);
          }
        catch (IOException e)
          {
            log.info("could not create zookeeper client using {}", Deployment.getZookeeperConnect());
          }
      }

    /*****************************************
    *
    *  ensure connected
    *
    *****************************************/

    if (zookeeper != null && zookeeper.getState() != ZooKeeper.States.CONNECTED)
      {
        synchronized (this)
          {
            while (zookeeper.getState().isAlive() && zookeeper.getState() != ZooKeeper.States.CONNECTED)
              {
                try
                  {
                    this.wait();
                  }
                catch (InterruptedException e)
                  {
                    // nothing
                  }
              }

            //
            //  ensure connected
            //

            switch (zookeeper.getState())
              {
                case CONNECTED:
                  break;
                default:
                  log.info("closing zookeeper client due to status {}", zookeeper.getState());
                  try { zookeeper.close(); } catch (InterruptedException e) { }
                  zookeeper = null;
                  break;
              }
          }
      }

    /******************************************
    *
    *  ensure zookeeper connection
    *
    *****************************************/

    if (zookeeper == null) return;
    if (zookeeper.getState() != ZooKeeper.States.CONNECTED) return;
    
    /******************************************
    *
    *  create proposal manager key node
    *
    *****************************************/

    //
    //  read existing session node
    //

    sessionKeyExists = false;
    try
      {
        sessionKeyExists = (zookeeper.exists(stockRoot + "/stockmonitors/" + stockMonitorKey, false) != null);
        if (!sessionKeyExists) log.info("session key node {} does not exist", stockMonitorKey);
      }
    catch (KeeperException e)
      {
        log.info("ensureConnected() - exists() - KeeperException code {}", e.code());
        return;
      }
    catch (InterruptedException e)
      {
        return;
      }

    //
    //  create session node (if necessary)
    //

    if (! sessionKeyExists)
      {
        try
          {
            zookeeper.create(stockRoot + "/stockmonitors/" + stockMonitorKey, new byte[0], Ids.OPEN_ACL_UNSAFE, CreateMode.EPHEMERAL);
            sessionKeyExists = true;
          }
        catch (KeeperException e)
          {
            log.info("ensureConnected() - create() - KeeperException code {}", e.code());
            return;
          }
        catch (InterruptedException e)
          {
            return;
          }
      }
  }

  /*****************************************
  *
  *  processWatchedEvent
  *
  *****************************************/

  void processWatchedEvent(WatchedEvent event)
  {
    switch (event.getState())
      {
        case SyncConnected:
          if (event.getPath() != null && event.getPath().equals(stockRoot + "/stockmonitors") && event.getType() == Watcher.Event.EventType.NodeChildrenChanged)
            {
              synchronized (this)
                {
                  stockMonitorKeysChanged = true;
                } 
            }
          synchronized (this)
            {
              this.notifyAll();
            } 
          break;

        default:
          synchronized (this)
            {
              this.notifyAll();
            } 
          break;
      }
  }

  /*****************************************
  *
  *  interface 
  *
  *****************************************/

  public interface StockableItem
  {
    public String getStockableItemID();
    public Integer getStock();
    // can return null, means infinite
    default public @Nullable Integer getApproximateRemainingStock(){
		if(this.getStock()==null) return null;//no stock, infinite, don't even call cached value, cause if never called no init of zookeeper cache updater
		return GlobalApproximateStockMonitor.getApproximateRemainingStock(this);
    }
  }

  /*****************************************
  *
  *  class Stock
  *
  *****************************************/

  public static class Stock
  {
    /*****************************************
    *
    *  data
    *
    *****************************************/

    private String stockableItemID;
    private Map<String,Integer> stockAllocated;
    private int stockReserved;
    private int stockConsumed;
    private int zookeeperVersion;

    /*****************************************
    *
    *  constructor (simple)
    *
    *****************************************/

    public Stock(String stockableItemID)
    {
      log.info("[PRJT] StockMonitor.Stock()...");
      this.stockableItemID = stockableItemID;
      this.stockAllocated = new HashMap<String,Integer>();
      this.stockReserved = 0;
      this.stockConsumed = 0;
      this.zookeeperVersion = -1;
    }
    
    /*****************************************
    *
    *  accessors
    *
    *****************************************/

    public String getStockableItemID() { return stockableItemID; }
    public Map<String,Integer> getStockAllocated() { return stockAllocated; }
    public int getStockReserved() { return stockReserved; }
    public int getStockConsumed() { return stockConsumed; }
    public int getStockUsed() { return stockReserved + stockConsumed; }
    public int getZookeeperVersion() { return zookeeperVersion; }

    /*****************************************
    *
    *  setters
    *
    *****************************************/

    public void setStockReserved(int stockReserved) { this.stockReserved = stockReserved; }
    public void setStockConsumed(int stockConsumed) { this.stockConsumed = stockConsumed; }

    /*****************************************
    *
    *  getTotalAllocated
    *
    *****************************************/

    public int getTotalAllocated()
    {
      log.info("[PRJT] StockMonitor.Stock.getTotalAllocated()...");
      int totalAllocated = 0;
      for (Integer allocatedForStockMonitor : stockAllocated.values())
        {
          totalAllocated += allocatedForStockMonitor;
        }
      return totalAllocated;
    }

    /*****************************************
    *
    *  constructor (json)
    *
    *****************************************/

    public Stock(JSONObject jsonRoot, int zookeeperVersion)
    {
      log.info("[PRJT] StockMonitor.Stock().json...");
      //
      //  stockableItem
      //

      this.stockableItemID = JSONUtilities.decodeString(jsonRoot, "stockableItemID", true);

      //
      //  stockAllocated
      //

      this.stockAllocated = new HashMap<String,Integer>();
      JSONArray jsonStockAllocatedArray = JSONUtilities.decodeJSONArray(jsonRoot, "stockAllocated", true);
      for (int i=0; i< jsonStockAllocatedArray.size(); i++)
        {
          JSONObject jsonStockAllocated = (JSONObject) jsonStockAllocatedArray.get(i);
          String stockMonitorKey = JSONUtilities.decodeString(jsonStockAllocated, "stockMonitorKey", true);
          Integer stockAllocated = JSONUtilities.decodeInteger(jsonStockAllocated, "stockAllocated", true);
          this.stockAllocated.put(stockMonitorKey, stockAllocated);
        }

      //
      //  stockReserved/stockConsumed
      //

      this.stockReserved = JSONUtilities.decodeInteger(jsonRoot, "stockReserved", true);
      this.stockConsumed = JSONUtilities.decodeInteger(jsonRoot, "stockConsumed", true);

      //
      //  zookeeperVersion
      //

      this.zookeeperVersion = zookeeperVersion;
    }

    /*****************************************
    *
    *  toJSON
    *
    *****************************************/

    public JSONObject toJSON()
    {
      //
      //  stockAllocated
      //

      List<JSONObject> jsonStockAllocatedList = new ArrayList<JSONObject>();
      for (String stockMonitorKey : stockAllocated.keySet())
        {
          HashMap<String,Object> jsonStockAllocated = new HashMap<String,Object>();
          Integer allocated = stockAllocated.get(stockMonitorKey);
          jsonStockAllocated.put("stockMonitorKey", stockMonitorKey);
          jsonStockAllocated.put("stockAllocated", allocated);
          jsonStockAllocatedList.add(JSONUtilities.encodeObject(jsonStockAllocated));
        }

      //
      //  encode
      //

      HashMap<String,Object> rootMap = new HashMap<String,Object>();
      rootMap.put("version", 1);
      rootMap.put("stockableItemID", stockableItemID);
      rootMap.put("stockAllocated", jsonStockAllocatedList);
      rootMap.put("stockReserved", stockReserved);
      rootMap.put("stockConsumed", stockConsumed);
      return JSONUtilities.encodeObject(rootMap);
    }
  }

  // cached global (so approximate) stock value
  // inner private class for lazy init
  private static class GlobalApproximateStockMonitor{
    private static Logger log = LoggerFactory.getLogger(GlobalApproximateStockMonitor.class);
    // cache update frequency
    private static final long CACHE_UPDATE_FREQUENCY_MS = 1000L;
    // cache values
    private final static Map<String,ReaderStock> approximateGlobalStocks = new ConcurrentHashMap<>();
    // cache values updater
    static {
      log.info("GlobalApproximateStockMonitor init class");
      Timer timer = new Timer("global-stocks-items-updater",true);
      // the regular update of our cache values from zookeeper reading
      timer.schedule(new TimerTask() {
        @Override
        public void run() {
          for(Map.Entry<String,ReaderStock> entry:approximateGlobalStocks.entrySet()){
            ReaderStock readerStock = getFromZookeeper(entry.getValue().stockableItem);
            entry.setValue(readerStock);
          }
        }
      },CACHE_UPDATE_FREQUENCY_MS,CACHE_UPDATE_FREQUENCY_MS);
      // from time to time we just completely clean the cache, avoid to keep in it stuff not used anymore
      timer.scheduleAtFixedRate(new TimerTask() {
        @Override
        public void run() {
          approximateGlobalStocks.clear();
        }
      },3_600_000L,3_600_000L);
    }

    private static ReaderStock getFromZookeeper(StockableItem stockableItem){
      if(log.isTraceEnabled()) log.trace("GlobalApproximateStockMonitor.getFromZookeeper called for "+stockableItem.getStockableItemID());
      ReaderStock readerStock = ZookeeperEvolution.getZookeeperEvolutionInstance().read(Configuration.NODE.STOCK_STOCKS.node().newChild(stockableItem.getStockableItemID()),new ReaderStock(stockableItem,new Stock(stockableItem.getStockableItemID())));
      // never seen yet in zookeeper
      if(readerStock==null) readerStock = new ReaderStock(stockableItem,null);
      return readerStock;
    }

    private static Integer getApproximateRemainingStock(StockableItem stockableItem){
      if(log.isTraceEnabled()) log.trace("GlobalApproximateStockMonitor.getApproximateRemainingStock called for "+stockableItem.getStockableItemID());
      ReaderStock readerStock = approximateGlobalStocks.get(stockableItem.getStockableItemID());
      // if not yet cached at all, init
      if(readerStock==null){
        if(log.isDebugEnabled()) log.trace("GlobalApproximateStockMonitor.getApproximateRemainingStock will keep in cache for "+stockableItem.getStockableItemID());
        readerStock = getFromZookeeper(stockableItem);
        approximateGlobalStocks.put(stockableItem.getStockableItemID(),readerStock);
      }
      // if not yet any consumed
      if(readerStock.stock==null) return readerStock.stockableItem.getStock();
      //below 0 (can happen if stock modified) means 0 remaining
      return Math.max(0,stockableItem.getStock() - readerStock.stock.getStockConsumed());
    }

  }

  // just to not change old Stock class for the reader changes
  private static class ReaderStock extends ZookeeperJSONObject<ReaderStock>{
    StockableItem stockableItem;
    Stock stock;
    ReaderStock(StockableItem stockableItem, Stock stock){ this.stockableItem = stockableItem; this.stock=stock; }
    @Override protected ReaderStock fromJson(JSONObject jsonObject) { return new ReaderStock(stockableItem,new Stock(jsonObject,-1)); }
    @Override protected JSONObject toJson() { return this.stock.toJSON(); }//not used
    @Override protected ReaderStock updateAtomic(ReaderStock storedInZookeeperObject) { return storedInZookeeperObject; }//not used
    @Override protected void onUpdateAtomicSucces() { }//not used
  }
  /*****************************************
  *
  *  class LocalAllocation
  *
  *****************************************/

  private static class LocalAllocation
  {
    /*****************************************
    *
    *  data
    *
    *****************************************/

    private int allocated;

    /*****************************************
    *
    *  constructor
    *
    *****************************************/

    public LocalAllocation()
    {
      allocated = 0;
    }

    //
    //  accessors
    //

    public int getAllocated() { return allocated; }

    //
    //  setters
    //

    public void setAllocated(int allocated) { this.allocated = allocated; }

    /*****************************************
    *
    *  reserve
    *
    *****************************************/

    void reserve(int quantity)
    {
      log.info("[PRJT] StockMonitor.LocalAllocation.reserve()...");
      allocated -= quantity;
    }
    
    /*****************************************
    *
    *  voidProposal
    *
    *****************************************/

    void voidReservation(int quantity)
    {
      log.info("[PRJT] StockMonitor.LocalAllocation.voidReservation()...");
      allocated += quantity;
    }

    /*****************************************
    *
    *  toString
    *
    *****************************************/

    public String toString()
    {
      return new String("allocated: " + allocated);
    }
  }

  /*****************************************
  *
  *  class 
  *
  *****************************************/

  private static class LocalUncommitted
  {
    /*****************************************
    *
    *  data
    *
    *****************************************/

    private int reserved;
    private int consumed;

    /*****************************************
    *
    *  constructor
    *
    *****************************************/

    public LocalUncommitted()
    {
      this.reserved = 0;
      this.consumed = 0;
    }

    /*****************************************
    *
    *  accessors
    *
    *****************************************/

    public int getReserved() { return reserved; }
    public int getConsumed() { return consumed; }
    public int getUsed() { return reserved + consumed; }

    /*****************************************
    *
    *  reserve
    *
    *****************************************/

    void reserve(int quantity)
    {
      log.info("[PRJT] StockMonitor.LocalUncommitted.reserve()...");
      reserved += quantity;
    }

    /*****************************************
    *
    *  confirmReservation
    *
    *****************************************/

    void confirmReservation(int quantity)
    {
      log.info("[PRJT] StockMonitor.LocalUncommitted.confirmReservation()...");
      reserved -= quantity;
      consumed += quantity;
    }

    /*****************************************
    *
    *  voidReservation
    *
    *****************************************/

    void voidReservation(int quantity)
    {
      log.info("[PRJT] StockMonitor.LocalUncommitted.voidReservation()...");
      reserved -= quantity;
    }
    
    void voidConsumption(int quantity)
    {
      log.info("[PRJT] StockMonitor.LocalUncommitted.voidConsumption()...");
      consumed -= quantity;
    }

    /*****************************************
    *
    *  toString
    *
    *****************************************/

    public String toString()
    {
      return new String("reserved: " + reserved + ", consumed: " + consumed);
    }
  }

}
