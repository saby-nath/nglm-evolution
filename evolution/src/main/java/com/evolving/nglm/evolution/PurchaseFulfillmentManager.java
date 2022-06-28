/*****************************************************************************
*
*  PurchaseFulfillmentManager.java
*
*****************************************************************************/

package com.evolving.nglm.evolution;

import java.time.Duration;
import java.util.*;
import java.util.stream.Collectors;

import com.evolving.nglm.core.*;
import com.evolving.nglm.evolution.commoditydelivery.CommodityDeliveryException;
import com.evolving.nglm.evolution.commoditydelivery.CommodityDeliveryManagerRemovalUtils;
import com.evolving.nglm.evolution.uniquekey.ZookeeperUniqueKeyServer;
import org.apache.kafka.clients.consumer.*;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.Serializer;
import org.apache.kafka.connect.data.Field;
import org.apache.kafka.connect.data.Schema;
import org.apache.kafka.connect.data.SchemaAndValue;
import org.apache.kafka.connect.data.SchemaBuilder;
import org.apache.kafka.connect.data.Struct;
import org.elasticsearch.ElasticsearchException;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.evolving.nglm.evolution.CommodityDeliveryManager.CommodityDeliveryOperation;
import com.evolving.nglm.evolution.DeliveryRequest.Module;
import com.evolving.nglm.evolution.EvolutionEngine.EvolutionEventContext;
import com.evolving.nglm.evolution.EvolutionUtilities.TimeUnit;
import com.evolving.nglm.evolution.Expression.ConstantExpression;
import com.evolving.nglm.evolution.Expression.ExpressionContext;
import com.evolving.nglm.evolution.Expression.ExpressionReader;
import com.evolving.nglm.evolution.GUIManagedObject.GUIManagedObjectType;
import com.evolving.nglm.evolution.GUIManager.GUIManagerException;
import com.evolving.nglm.evolution.SubscriberProfileService.EngineSubscriberProfileService;
import com.evolving.nglm.evolution.SubscriberProfileService.SubscriberProfileServiceException;
import com.evolving.nglm.evolution.elasticsearch.ElasticsearchClientAPI;
import com.evolving.nglm.evolution.statistics.CounterStat;
import com.evolving.nglm.evolution.statistics.StatBuilder;
import com.evolving.nglm.evolution.statistics.StatsBuilders;

public class PurchaseFulfillmentManager extends DeliveryManager implements Runnable
{

  public static final String PURCHASEFULFILLMENT_DELIVERY_TYPE = "purchaseFulfillment";

  /*****************************************
  *
  *  enum
  *
  *****************************************/

  public enum PurchaseFulfillmentStatus
  {
    PURCHASED(0),
    PURCHASED_AND_CANCELLED(1),
    MISSING_PARAMETERS(4),
    BAD_FIELD_VALUE(5),
    PENDING(708),
    CUSTOMER_NOT_FOUND(20),
    SYSTEM_ERROR(21),
    THIRD_PARTY_ERROR(24),
    BONUS_NOT_FOUND(100),
    OFFER_NOT_FOUND(400),
    PRODUCT_NOT_FOUND(401),
    INVALID_PRODUCT(402),
    OFFER_NOT_APPLICABLE(403),
    INSUFFICIENT_STOCK(404),
    INSUFFICIENT_BALANCE(405),
    BAD_OFFER_STATUS(406),
    PRICE_NOT_APPLICABLE(407),
    NO_VOUCHER_CODE_AVAILABLE(408),
    CHANNEL_DEACTIVATED(409),
    CUSTOMER_OFFER_LIMIT_REACHED(410),
    BAD_OFFER_DATES(411),
    UNKNOWN(-1);
    private Integer externalRepresentation;
    private PurchaseFulfillmentStatus(Integer externalRepresentation) { this.externalRepresentation = externalRepresentation; }
    public Integer getReturnCode() { return externalRepresentation; }
    public static PurchaseFulfillmentStatus fromReturnCode(Integer externalRepresentation) { for (PurchaseFulfillmentStatus enumeratedValue : PurchaseFulfillmentStatus.values()) { if (enumeratedValue.getReturnCode().equals(externalRepresentation)) return enumeratedValue; } return UNKNOWN; }
  }

  /*****************************************
  *
  *  conversion method
  *
  *****************************************/

  public DeliveryStatus getPurchaseFulfillmentStatus (PurchaseFulfillmentStatus status)
  {

    switch(status)
      {
        case PENDING:
          return DeliveryStatus.Pending;
        case PURCHASED:
        case PURCHASED_AND_CANCELLED:
          return DeliveryStatus.Delivered;
        case MISSING_PARAMETERS:
        case BAD_FIELD_VALUE:
        case SYSTEM_ERROR:
        case THIRD_PARTY_ERROR:
        case BONUS_NOT_FOUND:
        case OFFER_NOT_FOUND:
        case PRODUCT_NOT_FOUND:
        case INVALID_PRODUCT:
        case OFFER_NOT_APPLICABLE:
        case INSUFFICIENT_STOCK:
        case INSUFFICIENT_BALANCE:
        case BAD_OFFER_STATUS:
        case PRICE_NOT_APPLICABLE:
        case NO_VOUCHER_CODE_AVAILABLE:
        case CHANNEL_DEACTIVATED:
        case CUSTOMER_OFFER_LIMIT_REACHED:
        case BAD_OFFER_DATES:
        default:
          return DeliveryStatus.Failed;
      }
  }

  /*****************************************
  *
  *  configuration
  *
  *****************************************/

  //
  //  logger
  //

  private static final Logger log = LoggerFactory.getLogger(PurchaseFulfillmentManager.class);

  //
  //  variables
  //

  private static final int threadNumber = 50;   //TODO : make this configurable (would even be better if it is used)

  /*****************************************
  *
  *  data
  *
  *****************************************/

  private ArrayList<Thread> threads = new ArrayList<Thread>();

  private ElasticsearchClientAPI elasticsearch;
  private SubscriberProfileService subscriberProfileService;
  private DynamicCriterionFieldService dynamicCriterionFieldService;
  private OfferService offerService;
  private ProductService productService;
  private VoucherService voucherService;
  private VoucherTypeService voucherTypeService;
  private SalesChannelService salesChannelService;
  private StockMonitor stockService;
  private DeliverableService deliverableService;
  private PaymentMeanService paymentMeanService;
  private ResellerService resellerService;
  private ReferenceDataReader<String,SubscriberGroupEpoch> subscriberGroupEpochReader;
  private StatBuilder<CounterStat> statsCounter;
  private ZookeeperUniqueKeyServer zookeeperUniqueKeyServer;
  private String application_ID;
  private KafkaProducer<byte[], byte[]> kafkaProducer;
  /*****************************************
  *
  *  constructor
  *
  *****************************************/

  public PurchaseFulfillmentManager(String deliveryManagerKey, ElasticsearchClientAPI elasticsearch)
  {
    //
    //  superclass
    //
    
    super("deliverymanager-purchasefulfillment", deliveryManagerKey, Deployment.getBrokerServers(), PurchaseFulfillmentRequest.serde(), Deployment.getDeliveryManagers().get(PURCHASEFULFILLMENT_DELIVERY_TYPE),threadNumber);

    //
    // variables
    //
    
    application_ID = "application-deliverymanager-purchasefulfillment";

    //
    //  unique key server
    //
    
    zookeeperUniqueKeyServer = ZookeeperUniqueKeyServer.get(CommodityDeliveryManager.COMMODITY_DELIVERY_TYPE);
    
    //
    //  plugin instanciation
    //

    this.elasticsearch = elasticsearch;

    subscriberProfileService = new EngineSubscriberProfileService(Deployment.getSubscriberProfileEndpoints(), threadNumber);
    subscriberProfileService.start();
    
    dynamicCriterionFieldService = new DynamicCriterionFieldService(Deployment.getBrokerServers(), "PurchaseMgr-dynamiccriterionfieldservice-"+deliveryManagerKey, Deployment.getDynamicCriterionFieldTopic(), false);
    dynamicCriterionFieldService.start();
    CriterionContext.initialize(dynamicCriterionFieldService);
    
    offerService = new OfferService(Deployment.getBrokerServers(), "PurchaseMgr-offerservice-"+deliveryManagerKey, Deployment.getOfferTopic(), false);
    offerService.start();

    productService = new ProductService(Deployment.getBrokerServers(), "PurchaseMgr-productservice-"+deliveryManagerKey, Deployment.getProductTopic(), false);
    productService.start();

    voucherService = new VoucherService(Deployment.getBrokerServers(), "PurchaseMgr-voucherservice-"+deliveryManagerKey, Deployment.getVoucherTopic(), elasticsearch);
    voucherService.start();

    voucherTypeService = new VoucherTypeService(Deployment.getBrokerServers(), "PurchaseMgr-voucherservice-"+deliveryManagerKey, Deployment.getVoucherTypeTopic(), false);
    voucherTypeService.start();

    salesChannelService = new SalesChannelService(Deployment.getBrokerServers(), "PurchaseMgr-salesChannelservice-"+deliveryManagerKey, Deployment.getSalesChannelTopic(), false);
    salesChannelService.start();

    stockService = new StockMonitor("PurchaseMgr-stockService-"+deliveryManagerKey, offerService, productService, voucherService);
    stockService.start();

    deliverableService = new DeliverableService(Deployment.getBrokerServers(), "PurchaseMgr-deliverableservice-"+deliveryManagerKey, Deployment.getDeliverableTopic(), false);
    deliverableService.start();

    paymentMeanService = new PaymentMeanService(Deployment.getBrokerServers(), "PurchaseMgr-paymentmeanservice-"+deliveryManagerKey, Deployment.getPaymentMeanTopic(), false);
    paymentMeanService.start();

    resellerService = new ResellerService(Deployment.getBrokerServers(), "PurchaseMgr-resellereservice-"+deliveryManagerKey, Deployment.getResellerTopic(), false);
    resellerService.start();

    subscriberGroupEpochReader = ReferenceDataReader.<String,SubscriberGroupEpoch>startReader("PurchaseMgr-subscribergroupepoch", Deployment.getBrokerServers(), Deployment.getSubscriberGroupEpochTopic(), SubscriberGroupEpoch::unpack);

    //
    //  kafka producer
    //
    
    Properties producerProperties = new Properties();
    producerProperties.put("bootstrap.servers", Deployment.getBrokerServers());
    producerProperties.put("acks", "all");
    producerProperties.put("key.serializer", "org.apache.kafka.common.serialization.ByteArraySerializer");
    producerProperties.put("value.serializer", "org.apache.kafka.common.serialization.ByteArraySerializer");
    kafkaProducer = new KafkaProducer<byte[], byte[]>(producerProperties);
    
    //
    // define as commodityDelivery response consumer
    //
    
    addCommodityDeliveryResponseConsumer();
    
    //
    // statistics
    //

    statsCounter = StatsBuilders.getEvolutionCounterStatisticsBuilder("purchasefulfillment","purchasefulfillmentmanager-"+deliveryManagerKey);
    
    //
    //  threads
    //
    
    for(int i = 0; i < threadNumber; i++)
      {
        Thread t = new Thread(this, "PurchaseFulfillmentManagerThread_"+i);
        threads.add(t);
        t.start();
      }
    
    //
    //  startDelivery
    //
    
    startDelivery();

  }

	public void addCommodityDeliveryResponseConsumer(){

		for(DeliveryManagerDeclaration deliveryManager : Deployment.getDeliveryManagers().values()){
			CommodityDeliveryManager.CommodityType commodityType = CommodityDeliveryManager.CommodityType.fromExternalRepresentation(deliveryManager.getRequestClassName());

			if(commodityType != null){

				switch (commodityType) {
					case JOURNEY:
					case IN:
					case POINT:
					case REWARD:
					case EMPTY:

						log.info("CommodityDeliveryManager.getCommodityAndPaymentMeanFromDM() : get information from deliveryManager "+deliveryManager);

						//
						// update list of (kafka) response consumers
						//

						for(String responseTopic:deliveryManager.getResponseTopicsList()){

							String prefix = commodityType.toString()+"_"+deliveryManager.getProviderID()+"_"+responseTopic;
							Thread consumerThread = new Thread(new Runnable(){
								private volatile boolean stopping=false;
								@Override
								public void run()
								{
									Properties consumerProperties = new Properties();
									consumerProperties.put("bootstrap.servers", Deployment.getBrokerServers());
									consumerProperties.put("group.id", prefix+"_"+"requestReader");
									consumerProperties.put("auto.offset.reset", "earliest");
									consumerProperties.put("enable.auto.commit", "false");
									consumerProperties.put("key.deserializer", "org.apache.kafka.common.serialization.ByteArrayDeserializer");
									consumerProperties.put("value.deserializer", "org.apache.kafka.common.serialization.ByteArrayDeserializer");
									consumerProperties.put(ConsumerConfig.MAX_POLL_INTERVAL_MS_CONFIG, Deployment.getMaxPollIntervalMs());
									KafkaConsumer consumer = new KafkaConsumer<byte[], byte[]>(consumerProperties);
									consumer.subscribe(Arrays.asList(responseTopic));
									log.info("CommodityDeliveryManager.getCommodityAndPaymentMeanFromDM() : added kafka consumer for provider "+deliveryManager.getProviderName()+" "+responseTopic);

									NGLMRuntime.addShutdownHook(notUsed->stopping=true);
									while(!stopping){

										// poll

										long lastPollTime=System.currentTimeMillis();// to log poll processing time if exception happens later
										ConsumerRecords<byte[], byte[]> fileRecords = consumer.poll(Duration.ofMillis(5000));

										//  process records

										try{
											for (ConsumerRecord<byte[], byte[]> fileRecord : fileRecords) {
												//  parse
												DeliveryRequest response = deliveryManager.getRequestSerde().deserializer().deserialize(responseTopic, fileRecord.value());
												handleCommodityDeliveryResponse(response);
											}
											if(fileRecords.count()>0) consumer.commitSync();

										}catch (CommitFailedException ex){
											long lastPoll_ms=System.currentTimeMillis()-lastPollTime;
											log.warn("CommodityDeliveryManager : CommitFailedException catched, last poll was "+lastPoll_ms+"ms ago");
										}

									}

									// thread leaving the main loop !
									consumer.close();
									log.warn("CommodityDeliveryManager : STOPPING reading response from "+commodityType+" "+responseTopic);
								}
							}, "consumer_"+prefix);
							consumerThread.start();

						}

						log.info("CommodityDeliveryManager.getCommodityAndPaymentMeanFromDM() : get information from deliveryManager "+deliveryManager+" DONE");

						break;

					default:
						log.info("CommodityDeliveryManager.getCommodityAndPaymentMeanFromDM() : skip deliveryManager "+deliveryManager);
						break;
				}
			}

			// -------------------------------
			// skip all other managers
			// -------------------------------

			else{
				log.info("CommodityDeliveryManager.getCommodityAndPaymentMeanFromDM() : skip deliveryManager "+deliveryManager);
			}
		}

	}

  /*****************************************
  *
  *  class PurchaseFulfillmentRequest
  *
  *****************************************/

  public static class PurchaseFulfillmentRequest extends DeliveryRequest implements OfferDelivery
  {
    
    public static final String SCHEMA_NAME = "service_purchasefulfillment_request";
    
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
      schemaBuilder.name(SCHEMA_NAME);
      schemaBuilder.version(SchemaUtilities.packSchemaVersion(commonSchema().version(), 10));
      for (Field field : commonSchema().fields()) schemaBuilder.field(field.name(), field.schema());
      schemaBuilder.field("offerID", Schema.STRING_SCHEMA);
      schemaBuilder.field("offerDisplay", Schema.OPTIONAL_STRING_SCHEMA);
      schemaBuilder.field("quantity", Schema.INT32_SCHEMA);
      schemaBuilder.field("salesChannelID", Schema.STRING_SCHEMA);
      schemaBuilder.field("return_code", Schema.INT32_SCHEMA);
      schemaBuilder.field("offerContent", Schema.STRING_SCHEMA);
      schemaBuilder.field("meanOfPayment", Schema.STRING_SCHEMA);
      schemaBuilder.field("offerPrice", Schema.INT64_SCHEMA);
      schemaBuilder.field("origin", Schema.OPTIONAL_STRING_SCHEMA);
      schemaBuilder.field("resellerID", Schema.OPTIONAL_STRING_SCHEMA);
      schemaBuilder.field("resellerDisplay", Schema.OPTIONAL_STRING_SCHEMA);
      schemaBuilder.field("supplierDisplay", Schema.OPTIONAL_STRING_SCHEMA);
      schemaBuilder.field("voucherDeliveries", SchemaBuilder.array(VoucherDelivery.schema()).optional());
      schemaBuilder.field("cancelPurchase", Schema.BOOLEAN_SCHEMA);
      schema = schemaBuilder.build();
    }

    //
    //  serde
    //
        
    private static ConnectSerde<PurchaseFulfillmentRequest> serde = new ConnectSerde<PurchaseFulfillmentRequest>(schema, false, PurchaseFulfillmentRequest.class, PurchaseFulfillmentRequest::pack, PurchaseFulfillmentRequest::unpack);

    //
    //  accessor
    //

    public static Schema schema() { return schema; }
    public static ConnectSerde<PurchaseFulfillmentRequest> serde() { return serde; }
    public Schema subscriberStreamEventSchema() { return schema(); }
        
    /*****************************************
    *
    *  data
    *
    *****************************************/

    private String offerID;
    private String offerDisplay;
    private int quantity;
    private String salesChannelID;
    private PurchaseFulfillmentStatus status;
    private int returnCode;
    private String returnCodeDetails;
    private String offerContent;
    private String meanOfPayment;
    private long offerPrice;
    private String origin;
    private String resellerID;
    private String resellerDisplay;
    private String supplierDisplay;
    private List<VoucherDelivery> voucherDeliveries;
    private boolean cancelPurchase;
    
    //
    //  accessors
    //

    public String getOfferID() { return offerID; }
    public String getOfferDisplay() { return offerDisplay; }
    public int getQuantity() { return quantity; }
    public String getSalesChannelID() { return salesChannelID; }
    public PurchaseFulfillmentStatus getStatus() { return status; }
    public int getReturnCode() { return returnCode; }
    public String getOfferContent() { return offerContent; }
    public String getMeanOfPayment() { return meanOfPayment; }
    public long getOfferPrice() { return offerPrice; }
    public String getOrigin() { return origin; }
    public String getResellerID() { return resellerID; }
    public List<VoucherDelivery> getVoucherDeliveries() { return voucherDeliveries; }
    public String getResellerDisplay() { return resellerDisplay; }
    public String getSupplierDisplay() { return supplierDisplay; }
    public boolean getCancelPurchase() {return cancelPurchase; }
    //
    //  setters
    //

    public void setStatus(PurchaseFulfillmentStatus status) { this.status = status; }
    public void setReturnCode(Integer returnCode) { this.returnCode = returnCode; }
    public void setReturnCodeDetails(String returnCodeDetails) { this.returnCodeDetails = returnCodeDetails; }
    public void setOfferDisplay(String offerDisplay) { this.offerDisplay = offerDisplay; }
    public void setOfferContent(String offerContent) { this.offerContent = offerContent; }
    public void setMeanOfPayment(String meanOfPayment) { this.meanOfPayment = meanOfPayment; }
    public void setOfferPrice(Long offerPrice) { this.offerPrice = offerPrice; }
    public void addVoucherDelivery(VoucherDelivery voucherDelivery) {if(getVoucherDeliveries()==null){ this.voucherDeliveries = new ArrayList<>();} this.voucherDeliveries.add(voucherDelivery); }
    public void setResellerDisplay(String resellerDisplay) { this.resellerDisplay = resellerDisplay; }
    public void setSupplierDisplay(String supplierDisplay) { this.supplierDisplay = supplierDisplay; }
    public void setCancelPurchase(boolean cancelPurchase) { this.cancelPurchase = cancelPurchase;}
    
    //
    //  offer delivery accessors
    //

    public int getOfferDeliveryReturnCode() { return getReturnCode(); }
    public String getOfferDeliveryReturnCodeDetails() { return null; }
    public String getOfferDeliveryOrigin() { return getOrigin(); }
    public String getOfferDeliveryOfferDisplay() { return getOfferDisplay(); }
    public String getOfferDeliveryOfferID() { return getOfferID(); }
    public int getOfferDeliveryOfferQty() { return getQuantity(); }
    public String getOfferDeliverySalesChannelId() { return getSalesChannelID(); }
    public long getOfferDeliveryOfferPrice() { return getOfferPrice(); }
    public String getOfferDeliveryMeanOfPayment() { return getMeanOfPayment(); }
    public String getOfferDeliveryVoucherCode() { return getVoucherDeliveries()==null?"":getVoucherDeliveries().get(0).getVoucherCode(); }
    public String getOfferDeliveryVoucherExpiryDate() { return getVoucherDeliveries()==null?"":getVoucherDeliveries().get(0).getVoucherExpiryDate()==null?"":getVoucherDeliveries().get(0).getVoucherExpiryDate().toString(); }
    public String getOfferDeliveryVoucherPartnerId() { return ""; }//TODO
    public String getOfferDeliveryOfferContent() { return getOfferContent(); }
    public String getOfferDeliveryResellerID() { return getResellerID(); }
    public String getResellerName_OfferDelivery() { return getResellerDisplay(); }
    public String getSupplierName_OfferDelivery() { return getSupplierDisplay(); }
    
    /*****************************************
    *
    *  constructor
    *
    *****************************************/

    public PurchaseFulfillmentRequest(EvolutionEventContext context, String deliveryRequestSource, String offerID, int quantity, String salesChannelID, String origin, String resellerID, int tenantID)
    {
      super(context, "purchaseFulfillment", deliveryRequestSource, tenantID);
      this.offerID = offerID;
      this.quantity = quantity;
      this.salesChannelID = salesChannelID;
      this.status = PurchaseFulfillmentStatus.PENDING;
      this.returnCode = PurchaseFulfillmentStatus.PENDING.getReturnCode();
      this.origin = origin;       
      this.resellerID = resellerID;
      updatePurchaseFulfillmentRequest(context.getOfferService(), context.getPaymentMeanService(), context.getResellerService(), context.getProductService(), context.getSupplierService(), context.getVoucherService(), context.eventDate(), tenantID);
    }

    /*****************************************
    *
    *  updatePurchaseFulfillmentRequest
    *
    *****************************************/

    private void updatePurchaseFulfillmentRequest(OfferService offerService, PaymentMeanService paymentMeanService, ResellerService resellerService, ProductService productService, SupplierService supplierService, VoucherService voucherService, Date now, int tenantID)
    {
      
      // resellerDisplay
      if (resellerID != null)
        {
          Reseller reseller = resellerService.getActiveReseller(resellerID, now);
          this.resellerDisplay = (reseller == null || reseller.getGUIManagedObjectDisplay() == null) ? "" : reseller.getGUIManagedObjectDisplay();
        }

      //
      // offerDisplay
      //
      
      Offer offer = offerService.getActiveOffer(offerID, now);
      String offerDisplay = (offer == null || offer.getDisplay() == null) ? "" : offer.getDisplay();
      this.offerDisplay = offerDisplay;

      //
      // offerContent
      //
      
      String offerContent = "";
      boolean firstTime = true;
      if (offer != null)
        {
          if (offer.getOfferProducts()!=null)
            {
              for (OfferProduct offerProduct : offer.getOfferProducts())
                {
                  if (firstTime)
                  {
                    firstTime = false;
                  }
                  else
                  {
                    offerContent += ", ";
                  }
                  if (log.isDebugEnabled()) log.debug("adding (productID_" + offerProduct.getJSONRepresentation().get("productID") + ") to offer content (" + offerContent + ")");
                  offerContent += offerProduct.getQuantity() + " productID_" + offerProduct.getJSONRepresentation().get("productID");
                }
            }
          if (offer.getOfferVouchers()!=null)
            {
              for (OfferVoucher offerVoucher : offer.getOfferVouchers())
                {
                  if (firstTime)
                  {
                    firstTime = false;
                  }
                  else
                  {
                    offerContent += ", ";
                  }
                  if (log.isDebugEnabled()) log.debug("adding (voucherID_" + offerVoucher.getJSONRepresentationForPurchaseTransaction().get("voucherID") + ") to offer content (" + offerContent + ")");
                  offerContent += offerVoucher.getQuantity() + " " + offerVoucher.getJSONRepresentationForPurchaseTransaction().get("voucherID");
                }
            }
        }
        this.offerContent = offerContent;

        // supplierDisplay
        // look for it in products, and if none, look for vouchers
        
        boolean found = false;
        
        if (offer != null)
          {
            Set<OfferProduct> offerProducts = offer.getOfferProducts();
            if (offerProducts != null)
              {
                for (OfferProduct offerProduct : offerProducts)
                  {
                    String productID = offerProduct.getProductID();
                    Product product = productService.getActiveProduct(productID, now);
                    if (product != null)
                      {
                        String supplierID = product.getSupplierID();
                        if (supplierID != null)
                          {
                            Supplier supplier = supplierService.getActiveSupplier(supplierID, now);
                            if (supplier != null)
                              {
                                supplierDisplay = supplier.getGUIManagedObjectDisplay();
                                found = true;
                                break; // only consider first valid one
                              }
                          }
                      }
                  }
              }
            if (!found)
              {
                Set<OfferVoucher> offerVouchers = offer.getOfferVouchers();
                if (offerVouchers != null)
                  {
                    for (OfferVoucher offerVoucher : offerVouchers)
                      {
                        String voucherID = offerVoucher.getVoucherID();
                        Voucher voucher = voucherService.getActiveVoucher(voucherID, now);
                        if (voucher != null)
                          {
                            String supplierID = voucher.getSupplierID();
                            if (supplierID != null)
                              {
                                Supplier supplier = supplierService.getActiveSupplier(supplierID, now);
                                if (supplier != null)
                                  {
                                    supplierDisplay = supplier.getGUIManagedObjectDisplay();
                                    found = true;
                                    break; // only consider first valid one
                                  }
                              }
                          }
                      }
                  }
              }
          }
        if (!found)
          {
            log.info("Unable to find a supplier from offer " + offer);
            supplierDisplay = "";
          }
        
        
        //
        // meanOfPayment
        // offerPrice
        //
        
        String meanOfPayment = "";
        long offerPrice = 0;
        if (offer != null)
          {
            if (offer != null)
              {
                for (OfferSalesChannelsAndPrice oscap : offer.getOfferSalesChannelsAndPrices())
                  {
                    if (oscap.getSalesChannelIDs().contains(salesChannelID))
                      {
                        OfferPrice price = oscap.getPrice();
                        if (price != null) 
                          {
                            String meanOfPaymentID = price.getPaymentMeanID();
                            PaymentMean paymentMean = paymentMeanService.getActivePaymentMean(meanOfPaymentID, now);
                            meanOfPayment = (paymentMean == null) ? "" : paymentMean.getDisplay(); 
                            offerPrice =  price.getAmount();
                          }
                        break;
                      }
                  }
              }
          }
        this.offerPrice = offerPrice;
        this.meanOfPayment = meanOfPayment;   
    }
    
    /*****************************************
    *
    *  constructor -- external
    *
    *****************************************/

    public PurchaseFulfillmentRequest(SubscriberProfile subscriberProfile, ReferenceDataReader<String,SubscriberGroupEpoch> subscriberGroupEpochReader, JSONObject jsonRoot, DeliveryManagerDeclaration deliveryManager, OfferService offerService, PaymentMeanService paymentMeanService, ResellerService resellerService, ProductService productService, SupplierService supplierService, VoucherService voucherService, Date now, int tenantID)
    {
      super(subscriberProfile,subscriberGroupEpochReader,jsonRoot, tenantID);
      this.offerID = JSONUtilities.decodeString(jsonRoot, "offerID", true);
      this.quantity = JSONUtilities.decodeInteger(jsonRoot, "quantity", true);
      this.salesChannelID = JSONUtilities.decodeString(jsonRoot, "salesChannelID", true);
      this.status = PurchaseFulfillmentStatus.PENDING;
      this.returnCode = PurchaseFulfillmentStatus.PENDING.getReturnCode();
      this.returnCodeDetails = "";
      this.origin = JSONUtilities.decodeString(jsonRoot, "origin", false);
      this.resellerID = JSONUtilities.decodeString(jsonRoot, "resellerID", false);
      this.resellerDisplay = JSONUtilities.decodeString(jsonRoot, "resellerDisplay", false);
      this.supplierDisplay = JSONUtilities.decodeString(jsonRoot, "supplierDisplay", false);
      this.cancelPurchase = JSONUtilities.decodeBoolean(jsonRoot, "cancelPurchase", Boolean.FALSE);
      log.info("RAJ K this.cancelPurchase {}, jsonRoot {}", this.cancelPurchase, jsonRoot);
      updatePurchaseFulfillmentRequest(offerService, paymentMeanService, resellerService, productService, supplierService, voucherService, now, tenantID);
      log.info("RAJ K this.cancelPurchase {}", this.cancelPurchase);
    }

    /*****************************************
    *
    *  constructor -- unpack
     * @param offerPrice 
     * @param meanOfPayment 
     * @param offerContent 
    *
    *****************************************/

    private PurchaseFulfillmentRequest(SchemaAndValue schemaAndValue, String offerID, String offerDisplay, int quantity, String salesChannelID, PurchaseFulfillmentStatus status, String offerContent, String meanOfPayment, long offerPrice, String origin, String resellerID, String resellerDisplay, String supplierDisplay, List<VoucherDelivery> voucherDeliveries, boolean cancelPurchase)
    {
      super(schemaAndValue);
      this.offerID = offerID;
      this.offerDisplay = offerDisplay;
      this.quantity = quantity;
      this.salesChannelID = salesChannelID;
      this.status = status;
      this.returnCode = status.getReturnCode();
      this.offerContent = offerContent;
      this.meanOfPayment = meanOfPayment;
      this.offerPrice = offerPrice;
      this.origin = origin;
      this.resellerID = resellerID;
      this.voucherDeliveries = voucherDeliveries;
      this.resellerDisplay = resellerDisplay;
      this.supplierDisplay = supplierDisplay;
      this.cancelPurchase = cancelPurchase;
    }

    /*****************************************
    *
    *  constructor -- copy
    *
    *****************************************/

    private PurchaseFulfillmentRequest(PurchaseFulfillmentRequest purchaseFulfillmentRequest)
    {
      super(purchaseFulfillmentRequest);
      this.offerID = purchaseFulfillmentRequest.getOfferID();
      this.offerDisplay = purchaseFulfillmentRequest.getOfferDisplay();
      this.quantity = purchaseFulfillmentRequest.getQuantity();
      this.salesChannelID = purchaseFulfillmentRequest.getSalesChannelID();
      this.returnCode = purchaseFulfillmentRequest.getReturnCode();
      this.status = purchaseFulfillmentRequest.getStatus();
      this.offerContent = purchaseFulfillmentRequest.getOfferContent();
      this.meanOfPayment = purchaseFulfillmentRequest.getMeanOfPayment();
      this.offerPrice = purchaseFulfillmentRequest.getOfferPrice();
      this.origin = purchaseFulfillmentRequest.getOrigin();
      this.resellerID = purchaseFulfillmentRequest.getResellerID();
      this.resellerDisplay = purchaseFulfillmentRequest.getResellerDisplay();
      this.supplierDisplay = purchaseFulfillmentRequest.getSupplierDisplay();
      this.voucherDeliveries = purchaseFulfillmentRequest.getVoucherDeliveries();
      this.cancelPurchase = purchaseFulfillmentRequest.getCancelPurchase();
    }

    /*****************************************
    *
    *  copy
    *
    *****************************************/

    public PurchaseFulfillmentRequest copy()
    {
      return new PurchaseFulfillmentRequest(this);
    }
    
    /*****************************************
    *
    *  PurchaseFulfillmentRequest - esFields - minimal
    *
    *****************************************/
    
    public PurchaseFulfillmentRequest(Map<String, Object> esFields, SupplierService supplierService, OfferService offerService, ProductService productService, VoucherService voucherService, ResellerService resellerService)
    {
      //
      //  super
      //
      
      super(esFields);
      try {
        setCreationDate(RLMDateUtils.parseDateFromElasticsearch((String) esFields.get("creationDate")));
        setDeliveryDate(RLMDateUtils.parseDateFromElasticsearch((String) esFields.get("eventDatetime")));
      }
      catch(java.text.ParseException e) {
        throw new ServerRuntimeException(e);
      }
      
      //
      //  this
      //
      
      this.offerID = (String) esFields.get("offerID");
      this.salesChannelID = (String) esFields.get("salesChannelID");
      this.meanOfPayment = (String) esFields.get("meanOfPayment");
      this.offerPrice = (Integer) esFields.get("offerPrice");
      this.origin = (String) esFields.get("origin");
      this.resellerID = (String) esFields.get("resellerID");
      this.quantity = (Integer) esFields.get("offerQty");
      this.returnCode = (Integer) esFields.get("returnCode");
      
      //
      // derived
      //
      
      GUIManagedObject offer = offerService.getStoredOffer(offerID);
      Supplier supplier = getOfferSupplier(offer, supplierService, productService, voucherService);
      this.supplierDisplay = "";
      if (supplier != null) this.supplierDisplay = supplier.getGUIManagedObjectDisplay();
      GUIManagedObject reseller = resellerService.getStoredReseller(resellerID);
      this.resellerDisplay = "";
      if (reseller != null) this.resellerDisplay = reseller.getGUIManagedObjectDisplay();
      if (esFields.get("vouchers") != null)
        {
          List<Map <String, Object>> voucherESList = (List<Map<String, Object>>) esFields.get("vouchers");
          if (voucherESList != null && !voucherESList.isEmpty())
            {
              List<VoucherDelivery> voucherDeliveries = new ArrayList<VoucherDelivery>();
              for (Map<String, Object> voucher : voucherESList)
                {
            	  String voucherCode = (String) voucher.get("voucherCode");
                  String voucherID = (String) voucher.get("voucherID");
                  String voucherFileID = (String) voucher.get("voucherFileID");
                  Date voucherExpiryDate = null;
				try {
					voucherExpiryDate = RLMDateUtils.parseDateFromElasticsearch((String)voucher.get("voucherExpiryDate"));
				} catch (java.text.ParseException e) {
					throw new ServerRuntimeException(e);
				}
                  VoucherDelivery voucherDelivery = new VoucherDelivery(voucherID, voucherFileID, voucherCode, null, voucherExpiryDate); //minimal
                  voucherDeliveries.add(voucherDelivery);
                }
              this.voucherDeliveries = voucherDeliveries;
            }
        }
      
    }

    private Supplier getOfferSupplier(GUIManagedObject offerUnchecked, SupplierService supplierService, ProductService productService, VoucherService voucherService)
    {
      Supplier result = null;
      Date now = SystemTime.getCurrentTime();
      if (offerUnchecked != null && offerUnchecked instanceof Offer)
        {
          Offer offer = (Offer) offerUnchecked;
          boolean found = false;
          Set<OfferProduct> offerProducts = offer.getOfferProducts();
          if (offerProducts != null && !offerProducts.isEmpty())
            {
              for (OfferProduct offerProduct : offerProducts)
                {
                  String productID = offerProduct.getProductID();
                  Product product = productService.getActiveProduct(productID, now);
                  if (product != null)
                    {
                      String supplierID = product.getSupplierID();
                      if (supplierID != null)
                        {
                          Supplier supplier = supplierService.getActiveSupplier(supplierID, now);
                          if (supplier != null)
                            {
                              result = supplier;
                              found = true;
                              break; // only consider first valid one
                            }
                        }
                    }
                }
            }
         if (!found)
            {
              Set<OfferVoucher> offerVouchers = offer.getOfferVouchers();
              if (offerVouchers != null && !offerVouchers.isEmpty())
                {
                  for (OfferVoucher offerVoucher : offerVouchers)
                    {
                      String voucherID = offerVoucher.getVoucherID();
                      Voucher voucher = voucherService.getActiveVoucher(voucherID, now);
                      if (voucher != null)
                        {
                          String supplierID = voucher.getSupplierID();
                          if (supplierID != null)
                            {
                              Supplier supplier = supplierService.getActiveSupplier(supplierID, now);
                              if (supplier != null)
                                {
                                  result = supplier;
                                  found = true;
                                  break; // only consider first valid one
                                }
                            }
                        }
                    }
                }
              
            }
        }
      return result;
    }
    /*****************************************
    *
    *  pack
    *
    *****************************************/

    public static Object pack(Object value)
    {
      PurchaseFulfillmentRequest purchaseFulfillmentRequest = (PurchaseFulfillmentRequest) value;
      Struct struct = new Struct(schema);
      packCommon(struct, purchaseFulfillmentRequest);
      struct.put("offerID", purchaseFulfillmentRequest.getOfferID());
      struct.put("offerDisplay", purchaseFulfillmentRequest.getOfferDisplay());
      struct.put("quantity", purchaseFulfillmentRequest.getQuantity());
      struct.put("salesChannelID", purchaseFulfillmentRequest.getSalesChannelID());
      struct.put("return_code", purchaseFulfillmentRequest.getReturnCode());
      struct.put("offerContent", purchaseFulfillmentRequest.getOfferContent());
      struct.put("meanOfPayment", purchaseFulfillmentRequest.getMeanOfPayment());
      struct.put("offerPrice", purchaseFulfillmentRequest.getOfferPrice());
      struct.put("origin", purchaseFulfillmentRequest.getOrigin());
      struct.put("resellerID", purchaseFulfillmentRequest.getResellerID());
      struct.put("resellerDisplay", purchaseFulfillmentRequest.getResellerDisplay());
      struct.put("supplierDisplay", purchaseFulfillmentRequest.getSupplierDisplay());
      struct.put("cancelPurchase", purchaseFulfillmentRequest.getCancelPurchase());
      if(purchaseFulfillmentRequest.getVoucherDeliveries()!=null) struct.put("voucherDeliveries", packVoucherDeliveries(purchaseFulfillmentRequest.getVoucherDeliveries()));
      return struct;
    }

    private static List<Object> packVoucherDeliveries(List<VoucherDelivery> voucherDeliveries){
      List<Object> result = new ArrayList<>();
      for(VoucherDelivery voucherDelivery:voucherDeliveries){
        result.add(VoucherDelivery.pack(voucherDelivery));
      }
      return result;
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

    public static PurchaseFulfillmentRequest unpack(SchemaAndValue schemaAndValue)
    {
      //
      //  data
      //

      Schema schema = schemaAndValue.schema();
      Object value = schemaAndValue.value();
      Integer schemaVersion = (schema != null) ? SchemaUtilities.unpackSchemaVersion2(schema.version()) : null;

      //  unpack
      //

      Struct valueStruct = (Struct) value;
      String offerID = valueStruct.getString("offerID");
      String offerDisplay = (schemaVersion >= 2) ? valueStruct.getString("offerDisplay") : "";
      int quantity = valueStruct.getInt32("quantity");
      String salesChannelID = valueStruct.getString("salesChannelID");
      Integer returnCode = valueStruct.getInt32("return_code");
      PurchaseFulfillmentStatus status = PurchaseFulfillmentStatus.fromReturnCode(returnCode);
      String offerContent = (schemaVersion >= 2) ? valueStruct.getString("offerContent") : "";
      String meanOfPayment = (schemaVersion >= 2) ? valueStruct.getString("meanOfPayment") : "";
      long offerPrice = (schemaVersion >= 2) ? valueStruct.getInt64("offerPrice") : 0;
      String origin = (schemaVersion >= 3) ? valueStruct.getString("origin") : "";
      String resellerID = (schemaVersion >= 4) ? valueStruct.getString("resellerID") : "";
      String resellerDisplay = (schemaVersion >= 9) ? valueStruct.getString("resellerDisplay") : "";
      String supplierDisplay = (schemaVersion >= 9) ? valueStruct.getString("supplierDisplay") : "";
      List<VoucherDelivery> voucherDeliveries = (schemaVersion >= 5) ? unpackVoucherDeliveries(schema.field("voucherDeliveries").schema(), valueStruct.get("voucherDeliveries")) : null;
      boolean cancelPurchase = (schemaVersion >= 10) ? valueStruct.getBoolean("cancelPurchase") : false;


      //
      //  return
      //

      return new PurchaseFulfillmentRequest(schemaAndValue, offerID, offerDisplay, quantity, salesChannelID, status, offerContent, meanOfPayment, offerPrice, origin, resellerID, resellerDisplay, supplierDisplay, voucherDeliveries, cancelPurchase);
    }

    private static List<VoucherDelivery> unpackVoucherDeliveries(Schema schema, Object value){
      if(value==null) return null;
      Schema voucherDeliverySchema = schema.valueSchema();
      List<VoucherDelivery> result = new ArrayList<>();
      List<Object> valueArray = (List<Object>) value;
      for(Object voucherDelivery:valueArray){
        result.add(VoucherDelivery.unpack(new SchemaAndValue(voucherDeliverySchema,voucherDelivery)));
      }
      return result;
    }

    /*****************************************
    *  
    *  toString
    *
    *****************************************/

    public String toString()
    {
      StringBuilder b = new StringBuilder();
      b.append("PurchaseFulfillmentRequest:{");
      b.append(super.toStringFields());
      b.append("," + getSubscriberID());
      b.append("," + offerID);
      b.append("," + offerDisplay);
      b.append("," + quantity);
      b.append("," + salesChannelID);
      b.append("," + returnCode);
      b.append("," + returnCodeDetails);
      b.append("," + offerContent);
      b.append("," + meanOfPayment);
      b.append("," + offerPrice);
      b.append("," + origin);
      b.append("," + resellerID);
      b.append("," + resellerDisplay);
      b.append("," + supplierDisplay);
      b.append(",{");
      if(voucherDeliveries!=null) b.append(Arrays.toString(voucherDeliveries.toArray()));
      b.append("}");
      b.append("}");
      return b.toString();
    }
    
    @Override public ActivityType  getActivityType() { return ActivityType.ODR; }
    
    /****************************************
    *
    *  presentation utilities
    *
    ****************************************/
    
    @Override public void addFieldsForGUIPresentation(HashMap<String, Object> guiPresentationMap, SubscriberMessageTemplateService subscriberMessageTemplateService, SalesChannelService salesChannelService, JourneyService journeyService, OfferService offerService, LoyaltyProgramService loyaltyProgramService, ProductService productService, VoucherService voucherService, DeliverableService deliverableService, PaymentMeanService paymentMeanService, ResellerService resellerService, int tenantID)
    {
      //
      //  salesChannel
      //

      SalesChannel salesChannel = salesChannelService.getActiveSalesChannel(getSalesChannelID(), SystemTime.getCurrentTime());
      
      //
      //  offer
      //

      guiPresentationMap.put(SALESCHANNELID, getSalesChannelID());
      guiPresentationMap.put(SALESCHANNEL, (salesChannel != null) ? salesChannel.getGUIManagedObjectDisplay() : null);
      guiPresentationMap.put(MODULEID, getModuleID());
      guiPresentationMap.put(MODULENAME, getModule().toString());
      guiPresentationMap.put(FEATUREID, getFeatureID());
      guiPresentationMap.put(FEATURENAME, getFeatureName(getModule(), getFeatureID(), journeyService, offerService, loyaltyProgramService));
      guiPresentationMap.put(FEATUREDISPLAY, getFeatureDisplay(getModule(), getFeatureID(), journeyService, offerService, loyaltyProgramService));
      guiPresentationMap.put(ORIGIN, getOrigin());
      guiPresentationMap.put(RESELLERDISPLAY, getResellerDisplay());
      guiPresentationMap.put(SUPPLIERDISPLAY, getSupplierDisplay());
      guiPresentationMap.put(RETURNCODE, getReturnCode());
      guiPresentationMap.put(RETURNCODEDETAILS, PurchaseFulfillmentStatus.fromReturnCode(getReturnCode()).toString());
      guiPresentationMap.put(VOUCHERCODE, getOfferDeliveryVoucherCode());
      guiPresentationMap.put(VOUCHEREXPIRYDATE, getOfferDeliveryVoucherExpiryDate());
      guiPresentationMap.put(CUSTOMERID, getSubscriberID());
      guiPresentationMap.put(OFFERID, getOfferID());
      guiPresentationMap.put(OFFERQTY, getQuantity());

      GUIManagedObject offerGMO = offerService.getStoredOffer(getOfferID(), true);

      //
      //  presentation
      //
      
      if (offerGMO != null)
        {
          guiPresentationMap.put(OFFERNAME, offerGMO.getJSONRepresentation().get("name"));
          guiPresentationMap.put(OFFERDISPLAY, offerGMO.getJSONRepresentation().get("display"));

          guiPresentationMap.put(OFFERSTOCK, offerGMO.getJSONRepresentation().get("presentationStock")); // in case we don't find the offer
          
          if (offerGMO instanceof Offer)
            {
              Offer offer = (Offer) offerGMO;
              guiPresentationMap.put(OFFERSTOCK, offer.getStock());
              if(offer.getOfferSalesChannelsAndPrices() != null){
                for(OfferSalesChannelsAndPrice channel : offer.getOfferSalesChannelsAndPrices()){
                  if(channel.getSalesChannelIDs() != null) {
                    for(String salesChannelID : channel.getSalesChannelIDs()) {
                      if(salesChannelID.equals(getSalesChannelID())) {
                        if(channel.getPrice() != null) {
                          PaymentMean paymentMean = (PaymentMean) paymentMeanService.getStoredPaymentMean(channel.getPrice().getPaymentMeanID());
                          if(paymentMean != null) {
                            guiPresentationMap.put(OFFERPRICE, channel.getPrice().getAmount());
                            guiPresentationMap.put(MEANOFPAYMENT, paymentMean.getDisplay());
                            guiPresentationMap.put(PAYMENTPROVIDERID, paymentMean.getFulfillmentProviderID());
                          }
                        }
                      }
                    }
                  }
                }
              }

              StringBuilder sb = new StringBuilder();
              if(offer.getOfferProducts() != null) {
                for(OfferProduct offerProduct : offer.getOfferProducts()) {
                  Product product = (Product) productService.getStoredProduct(offerProduct.getProductID());
                  sb.append(offerProduct.getQuantity()+" ").append(product!=null?product.getDisplay():"product"+offerProduct.getProductID()).append(",");
                }
              }
              if(offer.getOfferVouchers() != null) {
                for(OfferVoucher offerVoucher : offer.getOfferVouchers()) {
                  Voucher voucher = (Voucher) voucherService.getStoredVoucher(offerVoucher.getVoucherID());
                  sb.append(offerVoucher.getQuantity()+" ").append(voucher!=null?voucher.getVoucherDisplay():"voucher"+offerVoucher.getVoucherID()).append(",");
                  String voucherFormat = "";
                  if(voucher instanceof VoucherShared){
                	  voucherFormat = ((VoucherShared)voucher).getCodeFormatId();
                    } else if (voucher instanceof VoucherPersonal){
                    	for(VoucherFile voucherFile:((VoucherPersonal)voucher).getVoucherFiles()){
                    		if(voucherFile.getFileId().equals(getVoucherDeliveries()==null?"":getVoucherDeliveries().get(0).getFileID())) {
                    			voucherFormat = voucherFile.getCodeFormatId();
                    		}
                    	}
                    }
                  guiPresentationMap.put(VOUCHERFORMAT, voucherFormat);
                  guiPresentationMap.put(VOUCHERSUPPLIERID, voucher.getSupplierID());
                  
                }
              }
              String offerContent = null;
              if(sb.length() >0){
                offerContent = sb.toString().substring(0, sb.toString().length()-1);
              }
              guiPresentationMap.put(OFFERCONTENT, offerContent);
            }
        }
    }
    
    @Override public void addFieldsForThirdPartyPresentation(HashMap<String, Object> thirdPartyPresentationMap, SubscriberMessageTemplateService subscriberMessageTemplateService, SalesChannelService salesChannelService, JourneyService journeyService, OfferService offerService, LoyaltyProgramService loyaltyProgramService, ProductService productService, VoucherService voucherService, DeliverableService deliverableService, PaymentMeanService paymentMeanService, ResellerService resellerService, int tenantID)
    {
      //
      //  salesChannel
      //

      SalesChannel salesChannel = salesChannelService.getActiveSalesChannel(getSalesChannelID(), SystemTime.getCurrentTime());
      
      //
      //  offer
      //

      Offer offer = null;
      GUIManagedObject offerObject = offerService.getStoredOffer(getOfferID(), true);
      if (offerObject != null && offerObject instanceof Offer)
        {
          offer = (Offer) offerObject;
        }

      //
      //  presentation
      //
      if(offer != null)
        {
          thirdPartyPresentationMap.put(OFFERID, getOfferID());
          thirdPartyPresentationMap.put(OFFERNAME, offer.getJSONRepresentation().get("name"));
          thirdPartyPresentationMap.put(OFFERDISPLAY, offer.getJSONRepresentation().get("display"));
          thirdPartyPresentationMap.put(OFFERQTY, getQuantity());
          thirdPartyPresentationMap.put(OFFERSTOCK, offer.getStock());
          if(offer.getOfferSalesChannelsAndPrices() != null){
            for(OfferSalesChannelsAndPrice channel : offer.getOfferSalesChannelsAndPrices()){
              if(channel.getSalesChannelIDs() != null) {
                for(String salesChannelID : channel.getSalesChannelIDs()) {
                  if(salesChannelID.equals(getSalesChannelID())) {
                    if(channel.getPrice() != null) {
                      PaymentMean paymentMean = (PaymentMean) paymentMeanService.getStoredPaymentMean(channel.getPrice().getPaymentMeanID());
                      if(paymentMean != null) {
                        thirdPartyPresentationMap.put(OFFERPRICE, channel.getPrice().getAmount());
                        thirdPartyPresentationMap.put(MEANOFPAYMENT, paymentMean.getDisplay());
                        thirdPartyPresentationMap.put(PAYMENTPROVIDERID, paymentMean.getFulfillmentProviderID());
                      }
                    }
                  }
                }
              }
            }
          }

          StringBuilder sb = new StringBuilder();
          if(offer.getOfferProducts() != null) {
            for(OfferProduct offerProduct : offer.getOfferProducts()) {
              Product product = (Product) productService.getStoredProduct(offerProduct.getProductID());
              sb.append(product!=null?product.getDisplay():"product"+offerProduct.getProductID()).append(";").append(offerProduct.getQuantity()).append(",");
            }
          }
          if(offer.getOfferVouchers() != null) {
            for(OfferVoucher offerVoucher : offer.getOfferVouchers()) {
              Voucher voucher = (Voucher) voucherService.getStoredVoucher(offerVoucher.getVoucherID());
              sb.append(voucher!=null?voucher.getVoucherDisplay():"voucher"+offerVoucher.getVoucherID()).append(";").append(offerVoucher.getQuantity()).append(",");
              String voucherFormat = "";
              if(voucher instanceof VoucherShared){
                  voucherFormat = ((VoucherShared)voucher).getCodeFormatId();
                } else if (voucher instanceof VoucherPersonal){
                	for(VoucherFile voucherFile:((VoucherPersonal)voucher).getVoucherFiles()){
                		if(voucherFile.getFileId().equals(getVoucherDeliveries()==null?"":getVoucherDeliveries().get(0).getFileID())) {
                			voucherFormat = voucherFile.getCodeFormatId();
                		}
                	}
                }
              thirdPartyPresentationMap.put(VOUCHERFORMAT, voucherFormat);
              thirdPartyPresentationMap.put(VOUCHERSUPPLIERID, voucher.getSupplierID());
            }
          }
          String offerContent = sb.length()>0?sb.toString().substring(0, sb.toString().length()-1):"";
          thirdPartyPresentationMap.put(OFFERCONTENT, offerContent);

          thirdPartyPresentationMap.put(SALESCHANNELID, getSalesChannelID());
          thirdPartyPresentationMap.put(SALESCHANNEL, (salesChannel != null) ? salesChannel.getGUIManagedObjectDisplay() : null);
          thirdPartyPresentationMap.put(MODULEID, getModuleID());
          thirdPartyPresentationMap.put(MODULENAME, getModule().toString());
          thirdPartyPresentationMap.put(FEATUREID, getFeatureID());
          thirdPartyPresentationMap.put(FEATURENAME, getFeatureName(getModule(), getFeatureID(), journeyService, offerService, loyaltyProgramService));
          thirdPartyPresentationMap.put(FEATUREDISPLAY, getFeatureDisplay(getModule(), getFeatureID(), journeyService, offerService, loyaltyProgramService));
          thirdPartyPresentationMap.put(ORIGIN, getOrigin());
          thirdPartyPresentationMap.put(RESELLERDISPLAY, getResellerDisplay());
          thirdPartyPresentationMap.put(SUPPLIERDISPLAY, getSupplierDisplay());
          thirdPartyPresentationMap.put(RETURNCODE, getReturnCode());
          thirdPartyPresentationMap.put(RETURNCODEDESCRIPTION, RESTAPIGenericReturnCodes.fromGenericResponseCode(getReturnCode()).getGenericResponseMessage());
          thirdPartyPresentationMap.put(RETURNCODEDETAILS, getOfferDeliveryReturnCodeDetails());
          thirdPartyPresentationMap.put(VOUCHERCODE, getOfferDeliveryVoucherCode());
          thirdPartyPresentationMap.put(VOUCHEREXPIRYDATE, getOfferDeliveryVoucherExpiryDate());
        }
    }
    @Override
    public void resetDeliveryRequestAfterReSchedule()
    {
      // 
      // PurchaseFulfillmentRequest never rescheduled, let return unchanged
      //  
      
    }
  }

  /*****************************************
  *
  *  run
  *
  *****************************************/

  @Override
  public void run()
  {
    mainLoop://labeled loop to "continue" from nested ones
    while (true)
      {
        /*****************************************
        *
        *  nextRequest
        *
        *****************************************/
        
        DeliveryRequest deliveryRequest = nextRequest();
        if(log.isDebugEnabled()) log.debug("run() : NEW REQUEST "+deliveryRequest.getDeliveryRequestID());
        PurchaseFulfillmentRequest purchaseRequest = ((PurchaseFulfillmentRequest)deliveryRequest);
        
        /*****************************************
        *
        *  respond with correlator
        *
        *****************************************/
        
        String correlator = deliveryRequest.getDeliveryRequestID();
        deliveryRequest.setCorrelator(correlator);
        updateRequest(deliveryRequest);
        if(log.isDebugEnabled()) log.debug("run() : "+deliveryRequest.getDeliveryRequestID()+" correlator set ");
        
        /*****************************************
        *
        *  get offer, customer, ...
        *
        *****************************************/
        
        Date processingDate = SystemTime.getCurrentTime();
        Date eventDate = deliveryRequest.getEventDate();

        String offerID = purchaseRequest.getOfferID();
        int quantity = purchaseRequest.getQuantity();
        String subscriberID = purchaseRequest.getSubscriberID();
        String salesChannelID = purchaseRequest.getSalesChannelID();
        
        if (purchaseRequest.getCancelPurchase())
          {
            log.info("RAJ K got a CancelpurchaseRequest {} with deliveryRequestID {}", purchaseRequest, purchaseRequest.getDeliveryRequestID());
            PurchaseRequestStatus purchaseStatus = new PurchaseRequestStatus(correlator, purchaseRequest.getEventID(), purchaseRequest.getModuleID(), purchaseRequest.getFeatureID(), offerID, subscriberID, quantity, salesChannelID);
            proceedRollback(purchaseRequest, purchaseStatus, PurchaseFulfillmentStatus.PURCHASED_AND_CANCELLED, "got a purchase cancel request for deliverrequestID {}" + purchaseRequest.getDeliveryRequestID());
          }
        else
          {
            PurchaseRequestStatus purchaseStatus = new PurchaseRequestStatus(correlator, purchaseRequest.getEventID(), purchaseRequest.getModuleID(), purchaseRequest.getFeatureID(), offerID, subscriberID, quantity, salesChannelID);
            log.info("RAJ K got a purchaseRequest {} with deliveryRequestID {}", purchaseRequest, purchaseRequest.getDeliveryRequestID());
            
            //
            // Get quantity
            //

            if (quantity < 1)
              {
                log.info("run() : (offer " + offerID + ", subscriberID " + subscriberID + ") : bad field value for quantity");
                submitCorrelatorUpdate(purchaseStatus, PurchaseFulfillmentStatus.BAD_FIELD_VALUE, "bad field value for quantity");
                continue mainLoop;
              }
            
            //
            // Get customer
            //

            if (subscriberID == null)
              {
                log.info("run() : (offer " + offerID + ", subscriberID " + subscriberID + ") : bad field value for subscriberID");
                submitCorrelatorUpdate(purchaseStatus, PurchaseFulfillmentStatus.MISSING_PARAMETERS, "missing mandatory field (subscriberID)");
                continue mainLoop;
              }
            
            //
            //  subscriberProfile
            //
            
            SubscriberProfile subscriberProfile = null;
            try
              {
                subscriberProfile = subscriberProfileService.getSubscriberProfile(subscriberID);
                if (subscriberProfile == null)
                  {
                    log.info("run() : (offer " + offerID + ", subscriberID " + subscriberID + ") : subscriber " + subscriberID + " not found");
                    submitCorrelatorUpdate(purchaseStatus, PurchaseFulfillmentStatus.CUSTOMER_NOT_FOUND, "customer " + subscriberID + " not found");
                    continue mainLoop;
                  } 
                else
                  {
                    if (log.isDebugEnabled()) log.debug("run() : (offer " + offerID + ", subscriberID " + subscriberID + ") : subscriber " + subscriberID + " found (" + subscriberProfile + ")");
                  }
              } 
            catch (SubscriberProfileServiceException e)
              {
                log.info("run() : (offer " + offerID + ", subscriberID " + subscriberID + ") : subscriberService not available");
                submitCorrelatorUpdate(purchaseStatus, PurchaseFulfillmentStatus.SYSTEM_ERROR, "subscriberService not available");
                continue mainLoop;
              }

            //
            // Get offer
            //
            
            if (offerID == null)
              {
                log.info("run() : (offer " + offerID + ", subscriberID " + subscriberID + ") : bad field value for offerID");
                submitCorrelatorUpdate(purchaseStatus, PurchaseFulfillmentStatus.MISSING_PARAMETERS, "missing mandatory field (offerID)");
                continue mainLoop;
              }
            
            //
            //  valid offer
            //
            
            Offer offer = offerService.getActiveOffer(offerID, eventDate);
            if (offer == null)
              {
                log.info("run() : (offer " + offerID + ", subscriberID " + subscriberID + ") : offer " + offerID + " not found");
                submitCorrelatorUpdate(purchaseStatus, PurchaseFulfillmentStatus.OFFER_NOT_FOUND, "offer " + offerID + " not found or not active (date = " + eventDate + ")");
                continue mainLoop;
              } 
            else
              {
                if (log.isDebugEnabled()) log.debug("run() : (offer " + offerID + ", subscriberID " + subscriberID + ") : offer " + offerID + " found (" + offer + ")");
              }

            //
            // Get sales channel
            //

            SalesChannel salesChannel = salesChannelService.getActiveSalesChannel(salesChannelID, eventDate);
            if (salesChannel == null)
              {
                log.info("run() : (offer " + offerID + ", subscriberID " + subscriberID + ") : salesChannel " + salesChannelID + " not found");
                submitCorrelatorUpdate(purchaseStatus, PurchaseFulfillmentStatus.CHANNEL_DEACTIVATED, "salesChannel " + salesChannelID + " not activated");
                continue mainLoop;
              } 
            else
              {
                if (log.isDebugEnabled())
                  log.debug("run() : (offer " + offerID + ", subscriberID " + subscriberID + ") : salesChannel " + salesChannelID + " found (" + salesChannel + ")");
              }

            //
            // Get offer price
            //
            
            OfferPrice offerPrice = null;
            Boolean priceFound = false;
            for (OfferSalesChannelsAndPrice offerSalesChannelsAndPrice : offer.getOfferSalesChannelsAndPrices())
              {
                if (offerSalesChannelsAndPrice.getSalesChannelIDs() != null && offerSalesChannelsAndPrice.getSalesChannelIDs().contains(salesChannel.getSalesChannelID()))
                  {
                    offerPrice = offerSalesChannelsAndPrice.getPrice();
                    priceFound = true;
                    if (log.isDebugEnabled())
                      {
                        String offerPriceStr = (offerPrice == null) ? "free" : offerPrice.getAmount() + " " + offerPrice.getPaymentMeanID();
                        log.debug("run() : (offer, subscriberProfile) : offer price for sales channel " + salesChannel.getSalesChannelID() + " found (" + offerPriceStr + ")");
                      }
                    break;
                  }
              }
            if (!priceFound)
              { // need this boolean since price can be null (if offer is free)
                log.info("run() : (offer " + offerID + ", subscriberID " + subscriberID + ") : offer price for sales channel " + salesChannelID + " not found");
                submitCorrelatorUpdate(purchaseStatus, PurchaseFulfillmentStatus.PRICE_NOT_APPLICABLE, "offer price for sales channel " + salesChannelID + " not found");
                continue mainLoop;
              }
            purchaseStatus.addPaymentToBeDebited(offerPrice);

            /*****************************************
            *
            *  Check offer, subscriber, ...
            *
            *****************************************/
            
            //
            // check offer is active (should be since we used 'getActiveOffer' ...)
            //

            if (!offerService.isActiveOffer(offer, eventDate))
              {
                log.info("run() : (offer, subscriberProfile) : offer " + offer.getOfferID() + " not active (date = " + eventDate + ")");
                submitCorrelatorUpdate(purchaseStatus, PurchaseFulfillmentStatus.BAD_OFFER_STATUS, "offer " + offer.getOfferID() + " not active (date = " + eventDate + ")");
                continue mainLoop;
              }
            purchaseStatus.addOfferStockToBeDebited(offer.getOfferID());

            //
            // check offer content
            //

            if(offer.getOfferProducts()!=null){
              for(OfferProduct offerProduct : offer.getOfferProducts()){
                Product product = productService.getActiveProduct(offerProduct.getProductID(), eventDate);
                if(product == null){
                  log.info("run() : (offer, subscriberProfile) : product with ID " + offerProduct.getProductID() + " not found or not active (date = "+eventDate+")");
                  submitCorrelatorUpdate(purchaseStatus, PurchaseFulfillmentStatus.PRODUCT_NOT_FOUND, "product with ID " + offerProduct.getProductID() + " not found or not active (date = "+eventDate+")");
                  continue mainLoop;
                }else{
                  purchaseStatus.addProductStockToBeDebited(offerProduct);
                  purchaseStatus.addProductToBeCredited(offerProduct);
                }
              }
            }

            if(offer.getOfferVouchers()!=null){
              for(OfferVoucher offerVoucher : offer.getOfferVouchers()){
                Voucher voucher = voucherService.getActiveVoucher(offerVoucher.getVoucherID(), eventDate);
                if(voucher==null){
                  log.info("run() : (offer, subscriberProfile) : voucher with ID " + offerVoucher.getVoucherID() + "not found or not active (date = "+eventDate+")");
                  submitCorrelatorUpdate(purchaseStatus, PurchaseFulfillmentStatus.PRODUCT_NOT_FOUND, "voucher with ID " + offerVoucher.getVoucherID() + " not found or not active (date = "+eventDate+")");
                  continue mainLoop;
                }else{
                  // more than 1 will ever be allowed ? trying to code like yes, but sure not really tested! (biggest problem I see, how do we "send" all codes)
                  int voucherQuantity = offerVoucher.getQuantity() * purchaseStatus.getQuantity();
                  offerVoucher.setQuantity(voucherQuantity);
                  if(voucher instanceof VoucherShared){
                    purchaseStatus.addVoucherSharedToBeAllocated(offerVoucher);
                  }else if (voucher instanceof VoucherPersonal){
                    purchaseStatus.addVoucherPersonalToBeAllocated(offerVoucher);
                  }else{
                    log.info("run() : (offer, subscriberProfile) : voucher with ID " + offerVoucher.getVoucherID() + " voucher type not recognized (date = "+eventDate+")");
                    submitCorrelatorUpdate(purchaseStatus, PurchaseFulfillmentStatus.SYSTEM_ERROR, "voucher with ID " + offerVoucher.getVoucherID() + " voucher type not recognized (date = "+eventDate+")");
                    continue mainLoop;
                  }
                }
              }
            }

          //
          // check offer criteria (for the specific subscriber)
          //

          SubscriberEvaluationRequest evaluationRequest = new SubscriberEvaluationRequest(subscriberProfile, subscriberGroupEpochReader, eventDate, offer.getTenantID());
          if (!offer.evaluateProfileCriteria(evaluationRequest))
            {
              log.info("run() : (offer, subscriberProfile) : criteria of offer " + offer.getOfferID() + " not valid for subscriber " + subscriberProfile.getSubscriberID() + " (date = " + eventDate + ")");
              submitCorrelatorUpdate(purchaseStatus, PurchaseFulfillmentStatus.OFFER_NOT_APPLICABLE, "criteria of offer " + offer.getOfferID() + " not valid for subscriber " + subscriberProfile.getSubscriberID() + " (date = " + eventDate + ")");
              continue mainLoop;
            }
            

            //
            // check offer purchase limit for this subscriber
            //
            
            Date earliestDateToKeep = EvolutionEngine.computeEarliestDateToKeep(processingDate, offer, deliveryRequest.getTenantID());
            List<Date> purchaseHistory = new ArrayList<Date>();
            
          //TODO: before EVPRO-1066 all the purchase were kept like Map<String,List<Date>, now it is Map<String, List<Pair<String, Date>>> <saleschnl, Date>
            // so it is important to migrate data, but once all customer run over this version, this should be removed
            // ------ START DATA MIGRATION COULD BE REMOVED
            Map<String, List<Date>> offerPurchaseHistory = subscriberProfile.getOfferPurchaseHistory();
            if (offerPurchaseHistory.get(offerID) != null)
              {
                purchaseHistory = offerPurchaseHistory.get(offerID).stream().filter(date -> date.after(earliestDateToKeep)).collect(Collectors.toList());
              }
            
            // ------ END DATA MIGRATION COULD BE REMOVED
            
            //
            // new version
            //
            
            if (subscriberProfile.getOfferPurchaseSalesChannelHistory().get(offerID) != null)
              {
                purchaseHistory.addAll(subscriberProfile.getOfferPurchaseSalesChannelHistory().get(offerID).stream().filter(datepair -> datepair.getSecondElement().after(earliestDateToKeep)).map(datepair -> datepair.getSecondElement()).collect(Collectors.toList()));
              }
            
            int totalPurchased = (purchaseHistory != null) ? purchaseHistory.size() : 0;

            if (offerPurchaseHistory.get("TBR_" + purchaseRequest.getDeliveryRequestID()) == null && subscriberProfile.getOfferPurchaseSalesChannelHistory().get("TBR_" + purchaseRequest.getDeliveryRequestID()) == null)
              { // EvolEngine has not processed this one yet
                if (purchaseHistory != null)
                  {
                    // only keep recent purchase dates (discard dates that are too old)
                    totalPurchased = purchaseRequest.getQuantity();
                    for (Date purchaseDate : purchaseHistory)
                      {
                        if (purchaseDate.after(earliestDateToKeep))
                          {
                            totalPurchased++;
                          }
                      }
                  }
              }
            if (EvolutionEngine.isPurchaseLimitReached(offer, totalPurchased)) {
              log.info("run() : maximumAcceptances : " + offer.getMaximumAcceptances() + " of offer "+offer.getOfferID()+" exceeded for subscriber "+subscriberProfile.getSubscriberID()+" as totalPurchased = " + totalPurchased+" (date = "+processingDate+")");
              submitCorrelatorUpdate(purchaseStatus, PurchaseFulfillmentStatus.CUSTOMER_OFFER_LIMIT_REACHED, "maximumAcceptances : " + offer.getMaximumAcceptances() + " of offer "+offer.getOfferID()+" exceeded for subscriber "+subscriberProfile.getSubscriberID()+" (date = "+processingDate+")");
              continue mainLoop;
            }
            
            /*****************************************
            *
            *  Proceed with the purchase
            *
            *****************************************/

            log.info(Thread.currentThread().getId()+" - PurchaseFulfillmentManager ("+deliveryRequest.getDeliveryRequestID()+") : proceedPurchase(...)");
            proceedPurchase(purchaseRequest,purchaseStatus, deliveryRequest.getTenantID(), subscriberID);
          }
      }
  }
  
  /*****************************************
  *
  *  CorrelatorUpdate
  *
  *****************************************/

  private void submitCorrelatorUpdate(PurchaseRequestStatus purchaseStatus, PurchaseFulfillmentStatus status, String statusMessage){
    purchaseStatus.setPurchaseFulfillmentStatus(status);
    purchaseStatus.setDeliveryStatus(getPurchaseFulfillmentStatus(status));
    purchaseStatus.setDeliveryStatusCode(status.getReturnCode());
    purchaseStatus.setDeliveryStatusMessage(statusMessage);
    submitCorrelatorUpdate(purchaseStatus);
  }
  
  private void submitCorrelatorUpdate(PurchaseRequestStatus purchaseStatus){
    if(log.isDebugEnabled()) log.debug("submitCorrelatorUpdate() : "+purchaseStatus.getCorrelator()+", "+purchaseStatus.getJSONRepresentation());
    submitCorrelatorUpdate(purchaseStatus.getCorrelator(), purchaseStatus.getJSONRepresentation());
  }

  @Override protected void processCorrelatorUpdate(DeliveryRequest deliveryRequest, JSONObject correlatorUpdate)
  {
    if(log.isDebugEnabled()) log.debug("processCorrelatorUpdate() : "+deliveryRequest.getDeliveryRequestID()+", "+correlatorUpdate+") : called ...");

    PurchaseRequestStatus purchaseStatus = new PurchaseRequestStatus(correlatorUpdate);
    PurchaseFulfillmentRequest purchaseFulfillmentRequest = (PurchaseFulfillmentRequest) deliveryRequest;
    if(purchaseStatus.getVoucherSharedAllocated()!=null && !purchaseStatus.getVoucherSharedAllocated().isEmpty()){
      for(OfferVoucher offerVoucher:purchaseStatus.getVoucherSharedAllocated()){
        for(int i=0;i<offerVoucher.getQuantity();i++){
          VoucherDelivery voucherDelivery = new VoucherDelivery(offerVoucher.getVoucherID(),offerVoucher.getFileID(),offerVoucher.getVoucherCode(), VoucherDelivery.VoucherStatus.Delivered, offerVoucher.getVoucherExpiryDate());
          if(log.isDebugEnabled()) log.debug("processCorrelatorUpdate() : "+deliveryRequest.getDeliveryRequestID()+", "+correlatorUpdate+") adding voucherDelivery "+voucherDelivery);
          purchaseFulfillmentRequest.addVoucherDelivery(voucherDelivery);
        }
      }
    }
    if(purchaseStatus.getVoucherPersonalAllocated()!=null && !purchaseStatus.getVoucherPersonalAllocated().isEmpty()){
      for(OfferVoucher offerVoucher:purchaseStatus.getVoucherPersonalAllocated()){
        VoucherDelivery voucherDelivery = new VoucherDelivery(offerVoucher.getVoucherID(),offerVoucher.getFileID(),offerVoucher.getVoucherCode(), VoucherDelivery.VoucherStatus.Delivered, offerVoucher.getVoucherExpiryDate());
        if(log.isDebugEnabled()) log.debug("processCorrelatorUpdate() : "+deliveryRequest.getDeliveryRequestID()+", "+correlatorUpdate+") adding voucherDelivery "+voucherDelivery);
        purchaseFulfillmentRequest.addVoucherDelivery(voucherDelivery);
      }
    }
    purchaseFulfillmentRequest.setReturnCode(purchaseStatus.getDeliveryStatusCode());
    purchaseFulfillmentRequest.setStatus(purchaseStatus.getPurchaseFulfillmentStatus());
    purchaseFulfillmentRequest.setDeliveryStatus(purchaseStatus.getDeliveryStatus());
    purchaseFulfillmentRequest.setDeliveryDate(SystemTime.getCurrentTime());
    completeRequest(deliveryRequest);

    statsCounter.withLabel(StatsBuilders.LABEL.status.name(),purchaseFulfillmentRequest.getDeliveryStatus().getExternalRepresentation())
                .withLabel(StatsBuilders.LABEL.module.name(), purchaseFulfillmentRequest.getModule().name())
                .withLabel(StatsBuilders.LABEL.tenant.name(), String.valueOf(purchaseFulfillmentRequest.getTenantID()))
                .getStats().increment();

    if(log.isDebugEnabled()) log.debug("processCorrelatorUpdate() : "+deliveryRequest.getDeliveryRequestID()+", "+correlatorUpdate+") : DONE");

  }

  /*****************************************
  *
  *  shutdown
  *
  *****************************************/

  @Override protected void shutdown()
  {
    log.info("PurchaseFulfillmentManager: shutdown called");
    if (stockService != null) stockService.close();
    if (kafkaProducer != null) kafkaProducer.close();
    log.info("PurchaseFulfillmentManager: shutdown DONE");
  }
  
  /*****************************************
  *
  *  main
  *
  *****************************************/

  public static void main(String[] args)
  {
    new LoggerInitialization().initLogger();
    log.info("PurchaseFulfillmentManager: recieved " + args.length + " args :");
    for(int index = 0; index < args.length; index++){
      log.info("       args["+index+"] " + args[index]);
    }
    
    //
    //  configuration
    //

    String deliveryManagerKey = args[0];

    //
    //  instance  
    //
    
    log.info("PurchaseFulfillmentManager: Configuration " + Deployment.getDeliveryManagers());

    ElasticsearchClientAPI elasticsearch;
    try
    {
      elasticsearch = new ElasticsearchClientAPI("PurchaseFulfillmentManager");
    }
    catch (ElasticsearchException e)
    {
      throw new ServerRuntimeException("could not initialize elasticsearch client", e);
    }


    PurchaseFulfillmentManager manager = new PurchaseFulfillmentManager(deliveryManagerKey,elasticsearch);

    //
    //  run
    //

    manager.run();
  }
  
  /*****************************************
  *
  *  proceed with purchase
  *
  *****************************************/

  private void proceedPurchase(DeliveryRequest originatingDeliveryRequest, PurchaseRequestStatus purchaseStatus, int tenantID, String subscriberID){

    //Change to return PurchaseManagerStatus? 
    
    //
    // reserve all products (manage stock)
    //

    if(purchaseStatus.getProductStockToBeDebited() != null && !purchaseStatus.getProductStockToBeDebited().isEmpty()){
      boolean debitOK = debitProductStock(originatingDeliveryRequest, purchaseStatus, tenantID);
      if(!debitOK){
        proceedRollback(originatingDeliveryRequest,purchaseStatus, PurchaseFulfillmentStatus.INSUFFICIENT_STOCK, "proceedPurchase : could not debit stock of product "+purchaseStatus.getProductStockDebitFailed().getProductID());
        return;
      }
    }

    //
    // reserve all shared vouchers (manage stock)
    //

    if(purchaseStatus.getVoucherSharedToBeAllocated() != null && !purchaseStatus.getVoucherSharedToBeAllocated().isEmpty()){
      boolean allocatedOK = allocateVoucherShared(originatingDeliveryRequest, purchaseStatus);
      if(!allocatedOK){
        proceedRollback(originatingDeliveryRequest,purchaseStatus, PurchaseFulfillmentStatus.INSUFFICIENT_STOCK, "proceedPurchase : could not debit stock of voucher "+purchaseStatus.getVoucherAllocateFailed().getVoucherID());
        return;
      }
    }

    //
    // reserve all personal vouchers
    //

    if(purchaseStatus.getVoucherPersonalToBeAllocated() != null && !purchaseStatus.getVoucherPersonalToBeAllocated().isEmpty()){
      boolean allocatedOK = allocateVoucherPersonal(originatingDeliveryRequest, purchaseStatus, tenantID);
      if(!allocatedOK){
        proceedRollback(originatingDeliveryRequest,purchaseStatus, PurchaseFulfillmentStatus.INSUFFICIENT_STOCK, "proceedPurchase : could not debit stock of voucher "+purchaseStatus.getVoucherAllocateFailed().getVoucherID());
        return;
      }
    }

    //
    // reserve offer (manage stock)
    //
    
    if(purchaseStatus.getOfferStockToBeDebited() != null && !purchaseStatus.getOfferStockToBeDebited().isEmpty()){
      boolean debitOK = debitOfferStock(originatingDeliveryRequest, purchaseStatus);
      if(!debitOK){
        proceedRollback(originatingDeliveryRequest,purchaseStatus, PurchaseFulfillmentStatus.INSUFFICIENT_STOCK, "proceedPurchase : could not debit stock of offer "+purchaseStatus.getOfferStockDebitFailed());
        return;
      }
    }

    //
    // make payments
    //
    
    if(purchaseStatus.getPaymentToBeDebited() != null && !purchaseStatus.getPaymentToBeDebited().isEmpty()){
      OfferPrice offerPrice = purchaseStatus.getPaymentToBeDebited().remove(0);
      if(offerPrice == null || offerPrice.getAmount()<=0){// => offer is free
        purchaseStatus.addPaymentDebited(offerPrice);
      }else{
        purchaseStatus.setPaymentBeingDebited(offerPrice);
        requestCommodityDelivery(originatingDeliveryRequest,purchaseStatus);
        return;
      }
    }

    //
    // credit products
    //

    if(purchaseStatus.getProductToBeCredited() != null && !purchaseStatus.getProductToBeCredited().isEmpty()){
      OfferProduct productToBeCredited = purchaseStatus.getProductToBeCredited().remove(0);
      purchaseStatus.setProductBeingCredited(productToBeCredited);
      requestCommodityDelivery(originatingDeliveryRequest,purchaseStatus);
      return;
    }

    //
    // confirm products, shared voucher and offers reservations
    //

    Voucher purchasedVoucher = null;
    Product purchasedProduct = null;
    Offer purchasedOffer = null;
    if(purchaseStatus.getProductStockDebited() != null && !purchaseStatus.getProductStockDebited().isEmpty()){
      for(OfferProduct offerProduct : purchaseStatus.getProductStockDebited()){
        Product product = productService.getActiveProduct(offerProduct.getProductID(), originatingDeliveryRequest.getEventDate());
        purchasedProduct = product;
        if(product == null){
          log.info("proceedPurchase() : (offer "+purchaseStatus.getOfferID()+", subscriberID "+purchaseStatus.getSubscriberID()+") : could not confirm reservation of product "+offerProduct.getProductID());
        }else{
          int quantity = offerProduct.getQuantity() * purchaseStatus.getQuantity();
          stockService.confirmReservation(product, quantity);
        }
      }
    }
    if(purchaseStatus.getVoucherSharedAllocated() != null && !purchaseStatus.getVoucherSharedAllocated().isEmpty()){
      for(OfferVoucher offerVoucher : purchaseStatus.getVoucherSharedAllocated()){
        VoucherShared voucher = null;
        try{
          voucher = (VoucherShared) voucherService.getActiveVoucher(offerVoucher.getVoucherID(), originatingDeliveryRequest.getEventDate());
          purchasedVoucher = voucher;
        }catch(ClassCastException ex){
          log.info("proceedPurchase() : (offer "+purchaseStatus.getOfferID()+", subscriberID "+purchaseStatus.getSubscriberID()+") : could not confirm reservation of bad voucher type "+offerVoucher.getVoucherID());
        }
        if(voucher == null){
          log.info("proceedPurchase() : (offer "+purchaseStatus.getOfferID()+", subscriberID "+purchaseStatus.getSubscriberID()+") : could not confirm reservation of voucher "+offerVoucher.getVoucherID());
        }else{
          offerVoucher.setVoucherCode(voucher.getSharedCode());
          stockService.confirmReservation(voucher, offerVoucher.getQuantity());
        }
      }
    }
    if(purchaseStatus.getOfferStockDebited() != null && !purchaseStatus.getOfferStockDebited().isEmpty()){
      for(String offerID : purchaseStatus.getOfferStockDebited()){
        Offer offer = offerService.getActiveOffer(offerID, originatingDeliveryRequest.getEventDate());
        purchasedOffer = offer;
        if(offer == null){
          log.info("proceedPurchase() : (offer "+purchaseStatus.getOfferID()+", subscriberID "+purchaseStatus.getSubscriberID()+") : could not confirm reservation of offer "+offerID);
        }else{
          int quantity = purchaseStatus.getQuantity();
          stockService.confirmReservation(offer, quantity);
        }
      }
    }
    
    
    
    //TODO : still to be done :
    //    - subscriber stats and/or limits (?) 

    //
    // everything is OK => update and return response (succeed)
    //
   if (purchasedProduct != null && subscriberID != null && purchasedProduct.getWorkflowID() != null && purchasedOffer.getOfferID() != null)
      {
        generateWorkflowEvent(originatingDeliveryRequest, subscriberID, purchasedProduct.getWorkflowID(), purchasedOffer.getOfferID());
      }
    if (purchasedVoucher != null && subscriberID != null && purchasedVoucher.getWorkflowID() != null && purchasedOffer.getOfferID() != null)
      {
        generateWorkflowEvent(originatingDeliveryRequest, subscriberID, purchasedVoucher.getWorkflowID(), purchasedOffer.getOfferID());
      }
    
    submitCorrelatorUpdate(purchaseStatus, PurchaseFulfillmentStatus.PURCHASED, "Success");
  }
  
  /*****************************************
  *
  *  generateWorkflowEvent
  *
  *****************************************/

  private void generateWorkflowEvent(SubscriberStreamOutput originatingEvent, String subscriberID, String workflowID, String feature)
  {
    String topic = Deployment.getWorkflowEventTopic();
    Serializer<StringKey> keySerializer = StringKey.serde().serializer();
    Serializer<WorkflowEvent> valueSerializer = WorkflowEvent.serde().serializer();
    WorkflowEvent workflowEvent = new WorkflowEvent(originatingEvent, subscriberID, workflowID, Module.Offer_Catalog.getExternalRepresentation() , feature);
    kafkaProducer.send(new ProducerRecord<byte[],byte[]>(
        topic,
        keySerializer.serialize(topic, new StringKey(subscriberID)),
        valueSerializer.serialize(topic, workflowEvent)
        ));
  }

  /*****************************************
  *
  *  steps of the purchase
  *
  *****************************************/

  private boolean debitProductStock(DeliveryRequest originatingRequest, PurchaseRequestStatus purchaseStatus, int tenantID){
    if(log.isDebugEnabled()) log.debug(Thread.currentThread().getId()+" - PurchaseFulfillmentManager.debitProductStock (offerID "+purchaseStatus.getOfferID()+", subscriberID "+purchaseStatus.getSubscriberID()+") called ...");
    boolean allGood = true;
    if(purchaseStatus.getProductStockToBeDebited() != null && !purchaseStatus.getProductStockToBeDebited().isEmpty()){
      while(!purchaseStatus.getProductStockToBeDebited().isEmpty() && allGood){
        OfferProduct offerProduct = purchaseStatus.getProductStockToBeDebited().remove(0);
        purchaseStatus.setProductStockBeingDebited(offerProduct);
        Product product = productService.getActiveProduct(offerProduct.getProductID(), originatingRequest.getEventDate());
        if(product == null){
          purchaseStatus.setProductStockDebitFailed(purchaseStatus.getProductStockBeingDebited());
          purchaseStatus.setProductStockBeingDebited(null);
          allGood = false;
        }else{
          if(log.isDebugEnabled()) log.debug("debitProductStock() : (offerID "+purchaseStatus.getOfferID()+", productID "+product.getProductID()+", subscriberID "+purchaseStatus.getSubscriberID()+") called ...");
          int quantity = offerProduct.getQuantity() * purchaseStatus.getQuantity();
          boolean approved = stockService.reserve(product, quantity);
          if(approved){
            purchaseStatus.addProductStockDebited(purchaseStatus.getProductStockBeingDebited());
            purchaseStatus.setProductStockBeingDebited(null);
            if(log.isDebugEnabled()) log.debug("debitProductStock() : product with ID " + product.getProductID() + " reserved " + quantity);
          }else{
            purchaseStatus.setProductStockDebitFailed(purchaseStatus.getProductStockBeingDebited());
            purchaseStatus.setProductStockBeingDebited(null);
            allGood = false;
            log.info("debitProductStock() :  product with ID " + product.getProductID() + " reservation of " + quantity + " FAILED");
          }
        }
      }
    }
    if(log.isDebugEnabled()) log.debug("debitProductStock() : (offerID "+purchaseStatus.getOfferID()+", subscriberID "+purchaseStatus.getSubscriberID()+") DONE");
    return allGood;
  }

  private boolean allocateVoucherShared(DeliveryRequest originatingRequest, PurchaseRequestStatus purchaseStatus){
    if(log.isDebugEnabled()) log.debug("allocateVoucherShared() : (offerID "+purchaseStatus.getOfferID()+", subscriberID "+purchaseStatus.getSubscriberID()+") called ...");
    if(purchaseStatus.getVoucherSharedToBeAllocated() != null && !purchaseStatus.getVoucherSharedToBeAllocated().isEmpty()){
      while(!purchaseStatus.getVoucherSharedToBeAllocated().isEmpty()){
        OfferVoucher offerVoucher = purchaseStatus.getVoucherSharedToBeAllocated().remove(0);
        VoucherShared voucherShared = null;
        try{
          voucherShared = (VoucherShared) voucherService.getActiveVoucher(offerVoucher.getVoucherID(), originatingRequest.getEventDate());
        }catch(ClassCastException ex){
          log.info("allocateVoucherShared() : voucher with ID " + offerVoucher.getVoucherID() + " bad voucher type " + ex.getMessage());
          purchaseStatus.setVoucherAllocateFailed(offerVoucher);
          return false;
        }
        if(voucherShared == null){
          log.info("allocateVoucherShared() : voucher with ID " + offerVoucher.getVoucherID() + " voucher does not exist");
          purchaseStatus.setVoucherAllocateFailed(offerVoucher);
          return false;
        }else{
          if(log.isDebugEnabled()) log.debug("allocateVoucherShared() : (offerID "+purchaseStatus.getOfferID()+", voucherID "+voucherShared.getVoucherID()+", subscriberID "+purchaseStatus.getSubscriberID()+") called ...");
          boolean approved = stockService.reserve(voucherShared, offerVoucher.getQuantity());
          if(approved){
            VoucherType voucherType = voucherTypeService.getActiveVoucherType(voucherShared.getVoucherTypeId(),originatingRequest.getEventDate());
            if(voucherType == null){
              log.info("allocateVoucherShared() : voucher type for voucher ID " + offerVoucher.getVoucherID() + " does not exist");
              purchaseStatus.setVoucherAllocateFailed(offerVoucher);
              return false;
            }
            purchaseStatus.addVoucherSharedAllocated(offerVoucher);
            if(log.isDebugEnabled()) log.debug("allocateVoucherShared() : voucher with ID " + voucherShared.getVoucherID() + " reserved " + offerVoucher.getQuantity());
          }else{
            log.info("allocateVoucherShared() : voucher with ID " + voucherShared.getVoucherID() + " reservation of "+offerVoucher.getQuantity()+" FAILED");
            purchaseStatus.setVoucherAllocateFailed(offerVoucher);
            return false;
          }
        }
      }
    }
    if(log.isDebugEnabled()) log.debug("allocateVoucherShared() : (offerID "+purchaseStatus.getOfferID()+", subscriberID "+purchaseStatus.getSubscriberID()+") DONE");
    return true;
  }

  private boolean allocateVoucherPersonal(DeliveryRequest originatingRequest, PurchaseRequestStatus purchaseStatus, int tenantID){
    if(log.isDebugEnabled()) log.debug("allocateVoucherPersonal() : (offerID "+purchaseStatus.getOfferID()+", subscriberID "+purchaseStatus.getSubscriberID()+") called ...");
    if(purchaseStatus.getVoucherPersonalToBeAllocated() != null && !purchaseStatus.getVoucherPersonalToBeAllocated().isEmpty()){
      while(!purchaseStatus.getVoucherPersonalToBeAllocated().isEmpty()){
        OfferVoucher offerVoucher = purchaseStatus.getVoucherPersonalToBeAllocated().remove(0);
        VoucherPersonal voucherPersonal = null;
        try{
          voucherPersonal = (VoucherPersonal) voucherService.getActiveVoucher(offerVoucher.getVoucherID(), originatingRequest.getEventDate());
        }catch(ClassCastException ex){
          log.info("allocateVoucherPersonal() : voucher with ID " + offerVoucher.getVoucherID() + " bad voucher type " + ex.getMessage());
          purchaseStatus.setVoucherAllocateFailed(offerVoucher);
          return false;
        }
        if(voucherPersonal == null){
          log.info("allocateVoucherPersonal() : voucher with ID " + offerVoucher.getVoucherID() + " voucher does not exist");
          purchaseStatus.setVoucherAllocateFailed(offerVoucher);
          return false;
        }else{
          for(int i=0;i<offerVoucher.getQuantity();i++){
            if(log.isDebugEnabled()) log.debug("allocateVoucherPersonal() : (offerID "+purchaseStatus.getOfferID()+", voucherID "+voucherPersonal.getVoucherID()+", subscriberID "+purchaseStatus.getSubscriberID()+") called ...");
            VoucherPersonalES esVoucher = voucherService.getVoucherPersonalESService().allocatePendingVoucher(voucherPersonal.getSupplierID(),voucherPersonal.getVoucherID(),purchaseStatus.getSubscriberID(), tenantID);
            if(esVoucher!=null && esVoucher.getSubscriberId()!=null && esVoucher.getVoucherCode()!=null && esVoucher.getFileId()!=null){
              OfferVoucher allocatedVoucher = new OfferVoucher(offerVoucher);
              allocatedVoucher.setQuantity(1);
              allocatedVoucher.setVoucherCode(esVoucher.getVoucherCode());
              allocatedVoucher.setVoucherExpiryDate(esVoucher.getExpiryDate());
              allocatedVoucher.setFileID(esVoucher.getFileId());
              purchaseStatus.addVoucherPersonalAllocated(allocatedVoucher);
              if(log.isDebugEnabled()) log.debug("allocateVoucherPersonal() : voucher with ID " + voucherPersonal.getVoucherID() + " reserved " + allocatedVoucher.getQuantity()+ ", "+allocatedVoucher.getVoucherCode()+", "+allocatedVoucher.getVoucherExpiryDate());
            }else{
              log.info("allocateVoucherPersonal() : voucher with ID " + voucherPersonal.getVoucherID() + " reservation of "+offerVoucher.getQuantity()+" FAILED");
              purchaseStatus.setVoucherAllocateFailed(offerVoucher);
              return false;
            }
          }
        }
      }
    }
    if(log.isDebugEnabled()) log.debug("allocateVoucherPersonal() : (offerID "+purchaseStatus.getOfferID()+", subscriberID "+purchaseStatus.getSubscriberID()+") DONE");
    return true;
  }

  private boolean debitOfferStock(DeliveryRequest originatingRequest, PurchaseRequestStatus purchaseStatus){
    if(log.isDebugEnabled()) log.debug("debitOfferStock() : (offer "+purchaseStatus.getOfferID()+", subscriberID "+purchaseStatus.getSubscriberID()+") called ...");
    boolean allGood = true;
    if(purchaseStatus.getOfferStockToBeDebited() != null && !purchaseStatus.getOfferStockToBeDebited().isEmpty()){
      while(!purchaseStatus.getOfferStockToBeDebited().isEmpty() && allGood){
        String offerID = purchaseStatus.getOfferStockToBeDebited().remove(0);
        purchaseStatus.setOfferStockBeingDebited(offerID);
        Offer offer = offerService.getActiveOffer(offerID, originatingRequest.getEventDate());
        if(offer == null){
          purchaseStatus.setOfferStockDebitFailed(purchaseStatus.getOfferStockBeingDebited());
          purchaseStatus.setOfferStockBeingDebited(null);
          allGood = false;
        }else{
          int quantity = purchaseStatus.getQuantity();
          boolean approved = stockService.reserve(offer, quantity);
          if(approved){
            purchaseStatus.addOfferStockDebited(purchaseStatus.getOfferStockBeingDebited());
            purchaseStatus.setOfferStockBeingDebited(null);
          }else{
            purchaseStatus.setOfferStockDebitFailed(purchaseStatus.getOfferStockBeingDebited());
            purchaseStatus.setOfferStockBeingDebited(null);
            allGood = false;
          }
        }
      }
    }
    if(log.isDebugEnabled()) log.debug("debitOfferStock() : (offer "+purchaseStatus.getOfferID()+", subscriberID "+purchaseStatus.getSubscriberID()+") DONE "+allGood);
    return allGood;
  }

  /*****************************************
  *
  *  proceed with rollback
  *
  *****************************************/

  private void proceedRollback(DeliveryRequest originatingDeliveryRequest, PurchaseRequestStatus purchaseStatus, PurchaseFulfillmentStatus deliveryStatus, String statusMessage)
  {
    log.info("RAJ K proceedRollback");

    //
    // update purchaseStatus
    //

    purchaseStatus.setRollbackInProgress(true);
    if (deliveryStatus != null)
      {
        purchaseStatus.setDeliveryStatus(getPurchaseFulfillmentStatus(deliveryStatus));
        purchaseStatus.setDeliveryStatusCode(deliveryStatus.getReturnCode());
      }
    if (statusMessage != null)
      {
        purchaseStatus.setDeliveryStatusMessage(statusMessage);
      }

    //
    // cancel all product stocks
    //

    if (purchaseStatus.getProductStockDebited() != null && !purchaseStatus.getProductStockDebited().isEmpty())
      {
        log.info("RAJ K proceedRollback - cancel all product stocks");
        while (purchaseStatus.getProductStockDebited() != null && !purchaseStatus.getProductStockDebited().isEmpty())
          {
            OfferProduct offerProduct = purchaseStatus.getProductStockDebited().remove(0);
            Product product = productService.getActiveProduct(offerProduct.getProductID(), originatingDeliveryRequest.getEventDate());
            if (product == null)
              {
                log.info("proceedRollback() : (offer " + purchaseStatus.getOfferID() + ", subscriberID " + purchaseStatus.getSubscriberID() + ") : could not cancel reservation of product " + offerProduct.getProductID());
                purchaseStatus.addProductStockRollbackFailed(offerProduct);
              } 
            else
              {
                int quantity = offerProduct.getQuantity() * purchaseStatus.getQuantity();
                stockService.voidReservation(product, quantity);
                purchaseStatus.addProductStockRollbacked(offerProduct);
                if (log.isDebugEnabled()) log.debug("proceedRollback() : reservation product " + product.getProductID() + " canceled " + quantity);
              }
          }
      }

    //
    // cancel all shared voucher stocks
    //

    if (purchaseStatus.getVoucherSharedAllocated() != null && !purchaseStatus.getVoucherSharedAllocated().isEmpty())
      {
        log.info("RAJ K proceedRollback - cancel all shared voucher stocks");
        while (purchaseStatus.getVoucherSharedAllocated() != null && !purchaseStatus.getVoucherSharedAllocated().isEmpty())
          {
            OfferVoucher offerVoucher = purchaseStatus.getVoucherSharedAllocated().remove(0);
            VoucherShared voucherShared = null;
            try
              {
                voucherShared = (VoucherShared) voucherService.getActiveVoucher(offerVoucher.getVoucherID(), originatingDeliveryRequest.getEventDate());
              } 
            catch (ClassCastException ex)
              {
                log.info("proceedRollback() : (offer " + purchaseStatus.getOfferID() + ", subscriberID " + purchaseStatus.getSubscriberID() + ") : could not cancel reservation of bad type shared voucher " + offerVoucher.getVoucherID());
                purchaseStatus.addVoucherSharedRollBackFailed(offerVoucher);
              }
            if (voucherShared == null)
              {
                log.info("proceedRollback() : (offer " + purchaseStatus.getOfferID() + ", subscriberID " + purchaseStatus.getSubscriberID() + ") : could not cancel reservation of shared voucher " + offerVoucher.getVoucherID());
                purchaseStatus.addVoucherSharedRollBackFailed(offerVoucher);
              } 
            else
              {
                stockService.voidReservation(voucherShared, offerVoucher.getQuantity());
                purchaseStatus.addVoucherSharedRollBacked(offerVoucher);
                if (log.isDebugEnabled()) log.debug("proceedRollback() : reservation shared voucher " + voucherShared.getVoucherID() + " canceled " + offerVoucher.getQuantity());
              }
          }
      }

    //
    // cancel all personal vouchers allocated
    //

    if (purchaseStatus.getVoucherPersonalAllocated() != null && !purchaseStatus.getVoucherPersonalAllocated().isEmpty())
      {
        log.info("RAJ K proceedRollback - cancel all personal vouchers allocated");
        while (purchaseStatus.getVoucherPersonalAllocated() != null && !purchaseStatus.getVoucherPersonalAllocated().isEmpty())
          {
            OfferVoucher offerVoucher = purchaseStatus.getVoucherPersonalAllocated().remove(0);
            VoucherPersonal voucherPersonal = null;
            try
              {
                voucherPersonal = (VoucherPersonal) voucherService.getActiveVoucher(offerVoucher.getVoucherID(), originatingDeliveryRequest.getEventDate());
              } catch (ClassCastException ex)
              {
                log.info("proceedRollback() : (offer " + purchaseStatus.getOfferID() + ", subscriberID " + purchaseStatus.getSubscriberID() + ") : could not cancel reservation of bad type Personal voucher " + offerVoucher.getVoucherID());
                purchaseStatus.addVoucherPersonalRollBackFailed(offerVoucher);
              }
            if (voucherPersonal == null)
              {
                log.info("proceedRollback() : (offer " + purchaseStatus.getOfferID() + ", subscriberID " + purchaseStatus.getSubscriberID() + ") : could not cancel reservation of Personal voucher " + offerVoucher.getVoucherID());
                purchaseStatus.addVoucherPersonalRollBackFailed(offerVoucher);
              } 
            else
              {
                if (voucherService.getVoucherPersonalESService().voidReservation(voucherPersonal.getSupplierID(), offerVoucher.getVoucherCode()))
                  {
                    purchaseStatus.addVoucherPersonalRollBacked(offerVoucher);
                    if (log.isDebugEnabled()) log.debug("proceedRollback : reservation Personal voucher " + voucherPersonal.getVoucherID() + " canceled " + offerVoucher.getVoucherCode());
                  } 
                else
                  {
                    log.info("proceedRollback() : (offer " + purchaseStatus.getOfferID() + ", subscriberID " + purchaseStatus.getSubscriberID() + ") : could not cancel reservation in ES for Personal voucher " + offerVoucher.getVoucherID());
                    purchaseStatus.addVoucherPersonalRollBackFailed(offerVoucher);
                  }
              }
          }
      }

    //
    // cancel all offer stocks
    //

    if (purchaseStatus.getOfferStockDebited() != null && !purchaseStatus.getOfferStockDebited().isEmpty())
      {
        log.info("RAJ K proceedRollback - cancel all offer stocks");
        while (purchaseStatus.getOfferStockDebited() != null && !purchaseStatus.getOfferStockDebited().isEmpty())
          {
            String offerID = purchaseStatus.getOfferStockDebited().remove(0);
            Offer offer = offerService.getActiveOffer(offerID, originatingDeliveryRequest.getEventDate());
            if (offer == null)
              {
                purchaseStatus.addOfferStockRollbackFailed(offerID);
                log.info("proceedRollback() : (offer " + purchaseStatus.getOfferID() + ", subscriberID " + purchaseStatus.getSubscriberID() + ") : could not cancel reservation of offer " + offerID);
              } 
            else
              {
                int quantity = purchaseStatus.getQuantity();
                stockService.voidReservation(offer, quantity);
                purchaseStatus.addOfferStockRollbacked(offerID);
                if (log.isDebugEnabled()) log.debug("proceedRollback() : reservation offer " + offer.getOfferID() + " canceled");
              }
          }
      }

    //
    // cancel all payments
    //

    if (purchaseStatus.getPaymentDebited() != null && !purchaseStatus.getPaymentDebited().isEmpty())
      {
        log.info("RAJ K cancel all payments - cancel all offer stocks");
        OfferPrice offerPrice = purchaseStatus.getPaymentDebited().remove(0);
        if (offerPrice == null || offerPrice.getAmount() <= 0)
          {// => offer is free
            purchaseStatus.addPaymentRollbacked(offerPrice);
          } 
        else
          {
            purchaseStatus.setPaymentBeingRollbacked(offerPrice);
            requestCommodityDelivery(originatingDeliveryRequest, purchaseStatus);
            return;
          }
        proceedRollback(originatingDeliveryRequest, purchaseStatus, null, null);
        return;
      }

    //
    // cancel all product deliveries
    //

    if (purchaseStatus.getProductCredited() != null && !purchaseStatus.getProductCredited().isEmpty())
      {
        log.info("RAJ K cancel all payments - cancel all product deliveries");
        OfferProduct offerProduct = purchaseStatus.getProductCredited().remove(0);
        if (offerProduct != null)
          {
            purchaseStatus.setProductBeingRollbacked(offerProduct);
            requestCommodityDelivery(originatingDeliveryRequest, purchaseStatus);
            return;
          } 
        else
          {
            proceedRollback(originatingDeliveryRequest, purchaseStatus, null, null);
            return;
          }
      }

    //
    // rollback completed => update and return response (failed)
    //

    submitCorrelatorUpdate(purchaseStatus);
    log.info("RAJ K proceedRollback done");
  }

  /*****************************************
  *
  *  requestCommodityDelivery (paymentMean or product)
  *
  *****************************************/
  
  private void requestCommodityDelivery(DeliveryRequest originatingRequest, PurchaseRequestStatus purchaseStatus)
  {
    if (log.isDebugEnabled()) log.debug("requestCommodityDelivery() : (offer " + purchaseStatus.getOfferID() + ", subscriberID " + purchaseStatus.getSubscriberID() + ") called ...");

    //
    // debit price
    //

    OfferPrice offerPrice = purchaseStatus.getPaymentBeingDebited();
    if (offerPrice != null)
      {
        if (log.isDebugEnabled()) log.debug("requestCommodityDelivery() : (offer " + purchaseStatus.getOfferID() + ", subscriberID " + purchaseStatus.getSubscriberID() + ") debiting offer price ...");
        purchaseStatus.incrementNewRequestCounter();
        String deliveryRequestID = zookeeperUniqueKeyServer.getStringKey();
        try
          {
            CommodityDeliveryManagerRemovalUtils.sendCommodityDeliveryRequest(paymentMeanService, deliverableService, originatingRequest, purchaseStatus.getJSONRepresentation(), application_ID, deliveryRequestID, purchaseStatus.getCorrelator(), false, purchaseStatus.getEventID(), purchaseStatus.getModuleID(), purchaseStatus.getFeatureID(), purchaseStatus.getSubscriberID(), offerPrice.getProviderID(), offerPrice.getPaymentMeanID(), CommodityDeliveryOperation.Debit, offerPrice.getAmount() * purchaseStatus.getQuantity(), null, 0, "");
            if (log.isDebugEnabled()) log.debug("requestCommodityDelivery() : PurchaseFulfillmentManager.requestCommodityDelivery (deliveryReqID " + deliveryRequestID + ", originatingDeliveryRequestID " + purchaseStatus.getCorrelator() + ", offer " + purchaseStatus.getOfferID() + ", subscriberID " + purchaseStatus.getSubscriberID() + ") debiting offer price DONE");
          } 
        catch (CommodityDeliveryException e)
          {
            log.info("requestCommodityDelivery() : (offer " + purchaseStatus.getOfferID() + ", subscriberID " + purchaseStatus.getSubscriberID() + ") debiting paymentmean (" + offerPrice.getPaymentMeanID() + ") FAILED => rollback");
            purchaseStatus.setPaymentDebitFailed(offerPrice);
            purchaseStatus.setPaymentBeingDebited(null);
            proceedRollback(originatingRequest, purchaseStatus, PurchaseFulfillmentStatus.PRICE_NOT_APPLICABLE, "could not debit paymentmean " + offerPrice.getPaymentMeanID() + " " + e.getError().getGenericResponseMessage());
          }
      }

    //
    // deliver product
    //

    OfferProduct offerProduct = purchaseStatus.getProductBeingCredited();
    if (offerProduct != null)
      {
        Product product = productService.getActiveProduct(offerProduct.getProductID(), originatingRequest.getEventDate());
        if (product != null)
          {
            Deliverable deliverable = deliverableService.getActiveDeliverable(product.getDeliverableID(), originatingRequest.getEventDate());
            int deliverableQuantity = product.getDeliverableQuantity();
            TimeUnit deliverableValidityType = null;
            int deliverableValidityPeriod = 0;
            if (product.getDeliverableValidity() != null && product.getDeliverableValidity().getValidityPeriod() != null)
              {
                deliverableValidityPeriod = product.getDeliverableValidity().getValidityPeriod();
              }
            if (product.getDeliverableValidity() != null && product.getDeliverableValidity().getValidityType() != null)
              {
                deliverableValidityType = product.getDeliverableValidity().getValidityType();
              }

            if (deliverable != null)
              {
                if (log.isDebugEnabled()) log.debug("requestCommodityDelivery() : (offer " + purchaseStatus.getOfferID() + ", subscriberID " + purchaseStatus.getSubscriberID() + ") delivering product (" + offerProduct.getProductID() + ") ...");
                purchaseStatus.incrementNewRequestCounter();
                String deliveryRequestID = zookeeperUniqueKeyServer.getStringKey();
                try
                  {
                    CommodityDeliveryManagerRemovalUtils.sendCommodityDeliveryRequest(paymentMeanService, deliverableService, originatingRequest, purchaseStatus.getJSONRepresentation(), application_ID, deliveryRequestID, purchaseStatus.getCorrelator(), false, purchaseStatus.getEventID(), purchaseStatus.getModuleID(), purchaseStatus.getFeatureID(), purchaseStatus.getSubscriberID(), deliverable.getFulfillmentProviderID(), deliverable.getDeliverableID(), CommodityDeliveryOperation.Credit, deliverableQuantity * offerProduct.getQuantity() * purchaseStatus.getQuantity(), deliverableValidityType, deliverableValidityPeriod, "");
                    if (log.isDebugEnabled()) log.debug("requestCommodityDelivery() : (deliveryReqID " + deliveryRequestID + ", originatingDeliveryRequestID " + purchaseStatus.getCorrelator() + ", offer " + purchaseStatus.getOfferID() + ", subscriberID " + purchaseStatus.getSubscriberID() + ") delivering product (" + offerProduct.getProductID() + ") DONE");
                  } 
                catch (CommodityDeliveryException e)
                  {
                    log.info("requestCommodityDelivery() : (offer " + purchaseStatus.getOfferID() + ", subscriberID " + purchaseStatus.getSubscriberID() + ") delivering deliverable (" + offerProduct.getProductID() + ") FAILED => rollback");
                    purchaseStatus.setProductCreditFailed(offerProduct);
                    purchaseStatus.setProductBeingCredited(null);
                    proceedRollback(originatingRequest, purchaseStatus, PurchaseFulfillmentStatus.INVALID_PRODUCT, "could not credit deliverable " + product.getDeliverableID() + " " + e.getError().getGenericResponseMessage());
                  }
              } else
              {
                log.info("requestCommodityDelivery() : (offer " + purchaseStatus.getOfferID() + ", subscriberID " + purchaseStatus.getSubscriberID() + ") delivering deliverable (" + offerProduct.getProductID() + ") FAILED => rollback");
                purchaseStatus.setProductCreditFailed(offerProduct);
                purchaseStatus.setProductBeingCredited(null);
                proceedRollback(originatingRequest, purchaseStatus, PurchaseFulfillmentStatus.INVALID_PRODUCT, "could not credit deliverable " + product.getDeliverableID());
              }
          } else
          {
            log.info("requestCommodityDelivery() : (offer " + purchaseStatus.getOfferID() + ", subscriberID " + purchaseStatus.getSubscriberID() + ") delivering product (" + offerProduct.getProductID() + ") FAILED => rollback");
            purchaseStatus.setProductCreditFailed(offerProduct);
            purchaseStatus.setProductBeingCredited(null);
            proceedRollback(originatingRequest, purchaseStatus, PurchaseFulfillmentStatus.PRODUCT_NOT_FOUND, "could not credit product " + offerProduct.getProductID());
          }
      }

    //
    // rollback debited price
    //

    OfferPrice offerPriceRollback = purchaseStatus.getPaymentBeingRollbacked();
    if (offerPriceRollback != null)
      {
        if (log.isDebugEnabled()) log.debug("requestCommodityDelivery() : (offer " + purchaseStatus.getOfferID() + ", subscriberID " + purchaseStatus.getSubscriberID() + ") rollbacking offer price ...");
        purchaseStatus.incrementNewRequestCounter();
        String deliveryRequestID = zookeeperUniqueKeyServer.getStringKey();
        try
          {
            CommodityDeliveryManagerRemovalUtils.sendCommodityDeliveryRequest(paymentMeanService, deliverableService, originatingRequest, purchaseStatus.getJSONRepresentation(), application_ID, deliveryRequestID, purchaseStatus.getCorrelator(), false, purchaseStatus.getEventID(), purchaseStatus.getModuleID(), purchaseStatus.getFeatureID(), purchaseStatus.getSubscriberID(), offerPriceRollback.getProviderID(), offerPriceRollback.getPaymentMeanID(), CommodityDeliveryOperation.Credit, offerPriceRollback.getAmount() * purchaseStatus.getQuantity(), null, 0, "");
            if (log.isDebugEnabled()) log.debug("requestCommodityDelivery() : (deliveryReqID " + deliveryRequestID + ", originatingDeliveryRequestID " + purchaseStatus.getCorrelator() + ", offer " + purchaseStatus.getOfferID() + ", subscriberID " + purchaseStatus.getSubscriberID() + ") rollbacking offer price DONE");
          } 
        catch (CommodityDeliveryException e)
          {
            log.info("requestCommodityDelivery() : (deliveryReqID " + deliveryRequestID + ", originatingDeliveryRequestID " + purchaseStatus.getCorrelator() + ", offer " + purchaseStatus.getOfferID() + ", subscriberID " + purchaseStatus.getSubscriberID() + ") rollbacking offer price FAILED " + e.getError().getGenericResponseMessage());
            purchaseStatus.addPaymentRollbackFailed(offerPrice);
            purchaseStatus.setPaymentBeingRollbacked(null);
            proceedRollback(originatingRequest, purchaseStatus, null, null);
          }
      }

    //
    // rollback product delivery
    //

    OfferProduct offerProductRollback = purchaseStatus.getProductBeingRollbacked();
    if (offerProductRollback != null)
      {
        Product product = productService.getActiveProduct(offerProductRollback.getProductID(), originatingRequest.getEventDate());
        if (product != null)
          {
            Deliverable deliverable = deliverableService.getActiveDeliverable(product.getDeliverableID(), originatingRequest.getEventDate());
            int deliverableQuantity = product.getDeliverableQuantity();
            TimeUnit deliverableValidityType = null;
            int deliverableValidityPeriod = 0;
            if (product.getDeliverableValidity() != null && product.getDeliverableValidity().getValidityPeriod() != null)
              {
                deliverableValidityPeriod = product.getDeliverableValidity().getValidityPeriod();
              }
            if (product.getDeliverableValidity() != null && product.getDeliverableValidity().getValidityType() != null)
              {
                deliverableValidityType = product.getDeliverableValidity().getValidityType();
              }
            if (deliverable != null)
              {
                if (log.isDebugEnabled()) log.debug("requestCommodityDelivery() : (offer " + purchaseStatus.getOfferID() + ", subscriberID " + purchaseStatus.getSubscriberID() + ") rollbacking product delivery (" + offerProductRollback.getProductID() + ") ...");
                purchaseStatus.incrementNewRequestCounter();
                String deliveryRequestID = zookeeperUniqueKeyServer.getStringKey();
                try
                  {
                    CommodityDeliveryManagerRemovalUtils.sendCommodityDeliveryRequest(paymentMeanService, deliverableService, originatingRequest, purchaseStatus.getJSONRepresentation(), application_ID, deliveryRequestID, purchaseStatus.getCorrelator(), false, purchaseStatus.getEventID(), purchaseStatus.getModuleID(), purchaseStatus.getFeatureID(), purchaseStatus.getSubscriberID(), deliverable.getFulfillmentProviderID(), deliverable.getDeliverableID(), CommodityDeliveryOperation.Debit, deliverableQuantity * offerProduct.getQuantity() * purchaseStatus.getQuantity(), deliverableValidityType, deliverableValidityPeriod, "");
                    if (log.isDebugEnabled()) log.debug("requestCommodityDelivery() : (deliveryReqID " + deliveryRequestID + ", originatingDeliveryRequestID " + purchaseStatus.getCorrelator() + ", offer " + purchaseStatus.getOfferID() + ", subscriberID " + purchaseStatus.getSubscriberID() + ") rollbacking product delivery (" + offerProductRollback.getProductID() + ") DONE");
                  } 
                catch (CommodityDeliveryException e)
                  {
                    log.info("requestCommodityDelivery() : (offer " + purchaseStatus.getOfferID() + ", subscriberID " + purchaseStatus.getSubscriberID() + ") rollbacking deliverable delivery failed (product id " + offerProductRollback.getProductID() + ")");
                    purchaseStatus.addProductRollbackFailed(offerProductRollback);
                    purchaseStatus.setProductBeingRollbacked(null);
                    proceedRollback(originatingRequest, purchaseStatus, null, null);
                  }
              } 
            else
              {
                log.info("requestCommodityDelivery() : (offer " + purchaseStatus.getOfferID() + ", subscriberID " + purchaseStatus.getSubscriberID() + ") rollbacking deliverable delivery failed (product id " + offerProductRollback.getProductID() + ")");
                purchaseStatus.addProductRollbackFailed(offerProductRollback);
                purchaseStatus.setProductBeingRollbacked(null);
                proceedRollback(originatingRequest, purchaseStatus, null, null);
              }
          } 
        else
          {
            log.info("requestCommodityDelivery() : (offer " + purchaseStatus.getOfferID() + ", subscriberID " + purchaseStatus.getSubscriberID() + ") rollbacking product delivery failed (product id " + offerProductRollback.getProductID() + ")");
            purchaseStatus.addProductRollbackFailed(offerProductRollback);
            purchaseStatus.setProductBeingRollbacked(null);
            proceedRollback(originatingRequest, purchaseStatus, null, null);
          }
      }
    if (log.isDebugEnabled()) log.debug("requestCommodityDelivery() : (offer " + purchaseStatus.getOfferID() + ", subscriberID " + purchaseStatus.getSubscriberID() + ") DONE");
  }
    
  /*****************************************
  *
  *  CommodityDeliveryResponseHandler.handleCommodityDeliveryResponse(...)
  *
  *****************************************/

  public void handleCommodityDeliveryResponse(DeliveryRequest response)
  {

    if(log.isDebugEnabled()) log.debug("handleCommodityDeliveryResponse() : called with " + response);

    if(response.getDiplomaticBriefcase() == null || response.getDiplomaticBriefcase().get(CommodityDeliveryManager.APPLICATION_ID)==null || !response.getDiplomaticBriefcase().get(CommodityDeliveryManager.APPLICATION_ID).equals(application_ID)){
      // not a bonus for purchase
      if(log.isDebugEnabled()) log.debug("handleCommodityDeliveryResponse() : not message for purchase, ignoring");
      return;
    }

    // ------------------------------------
    // Getting initial request status
    // ------------------------------------

    if(log.isDebugEnabled()) log.debug("handleCommodityDeliveryResponse() : getting purchase status ");
    if(response.getDiplomaticBriefcase() == null || response.getDiplomaticBriefcase().get(CommodityDeliveryManager.APPLICATION_BRIEFCASE) == null || response.getDiplomaticBriefcase().get(CommodityDeliveryManager.APPLICATION_BRIEFCASE).isEmpty()){
      log.info("handleCommodityDeliveryResponse() : can not get purchase status => ignore this response");
      return;
    }
    JSONParser parser = new JSONParser();
    PurchaseRequestStatus purchaseStatus = null;
    try
      {
        JSONObject requestStatusJSON = (JSONObject) parser.parse(response.getDiplomaticBriefcase().get(CommodityDeliveryManager.APPLICATION_BRIEFCASE));
        purchaseStatus = new PurchaseRequestStatus(requestStatusJSON);
      } catch (ParseException e)
      {
        log.warn("handleCommodityDeliveryResponse() : ERROR while getting purchase status from '"+response.getDiplomaticBriefcase().get(CommodityDeliveryManager.APPLICATION_BRIEFCASE)+"' => IGNORED");
        return;
      }
    if(log.isDebugEnabled()) log.debug("handleCommodityDeliveryResponse() : getting purchase status DONE : "+purchaseStatus);
    
    // ------------------------------------
    // Handling response
    // ------------------------------------

    DeliveryStatus responseDeliveryStatus = response.getDeliveryStatus();
    boolean isRollbackInProgress = purchaseStatus.getRollbackInProgress();
    if(isRollbackInProgress){

      //
      // processing rollback
      //

      //  ---  check payment  ---
      if(purchaseStatus.getPaymentBeingRollbacked() != null){
        OfferPrice offerPrice = purchaseStatus.getPaymentBeingRollbacked();
        if(responseDeliveryStatus.equals(DeliveryStatus.Delivered)){
          if(log.isDebugEnabled()) log.debug("handleCommodityDeliveryResponse() : ("+purchaseStatus.getOfferID()+", "+purchaseStatus.getSubscriberID()+") : price rollbacked");
          purchaseStatus.addPaymentRollbacked(offerPrice);
          purchaseStatus.setPaymentBeingRollbacked(null);
        }else{
          //responseDeliveryStatus is one of those : Pending, FailedRetry, Delivered, Indeterminate, Failed, FailedTimeout, Unknown
          log.info("handleCommodityDeliveryResponse() : ("+purchaseStatus.getOfferID()+", "+purchaseStatus.getSubscriberID()+") : price rollback failed");
          purchaseStatus.addPaymentRollbackFailed(offerPrice);
          purchaseStatus.setPaymentBeingRollbacked(null);
        }
      }

      //  ---  check products  ---
      if(purchaseStatus.getProductBeingRollbacked() != null){
        OfferProduct offerProduct = purchaseStatus.getProductBeingRollbacked();
        if(responseDeliveryStatus.equals(DeliveryStatus.Delivered)){
          if(log.isDebugEnabled()) log.debug("handleCommodityDeliveryResponse() : e("+purchaseStatus.getOfferID()+", "+purchaseStatus.getSubscriberID()+") : product delivery rollbacked (product id "+offerProduct.getProductID()+")");
          purchaseStatus.addProductRollbacked(offerProduct);
          purchaseStatus.setProductBeingRollbacked(null);
        }else{
          //responseDeliveryStatus is one of those : Pending, FailedRetry, Delivered, Indeterminate, Failed, FailedTimeout, Unknown
          log.info("handleCommodityDeliveryResponse() : ("+purchaseStatus.getOfferID()+", "+purchaseStatus.getSubscriberID()+") : product delivery rollback failed (product id "+offerProduct.getProductID()+")");
          purchaseStatus.addProductRollbackFailed(offerProduct);
          purchaseStatus.setProductBeingRollbacked(null);
        }
      }

      // continue rollback process
      if(log.isDebugEnabled()) log.debug("handleCommodityDeliveryResponse() : ("+purchaseStatus.getOfferID()+", "+purchaseStatus.getSubscriberID()+") : continue rollback process ...");
      proceedRollback(response,purchaseStatus, null, null);

    }else{
      
      //
      // processing purchase
      //
      
      //  ---  check payment  ---
      if(purchaseStatus.getPaymentBeingDebited() != null){
        OfferPrice offerPrice = purchaseStatus.getPaymentBeingDebited();
        if(responseDeliveryStatus.equals(DeliveryStatus.Delivered)){
          if(log.isDebugEnabled()) log.debug("handleCommodityDeliveryResponse() : ("+purchaseStatus.getOfferID()+", "+purchaseStatus.getSubscriberID()+") : price debited");
          purchaseStatus.addPaymentDebited(offerPrice);
          purchaseStatus.setPaymentBeingDebited(null);
        }else{
          //responseDeliveryStatus is one of those : Pending, FailedRetry, Delivered, Indeterminate, Failed, FailedTimeout, Unknown
          log.info("handleCommodityDeliveryResponse() : ("+purchaseStatus.getOfferID()+", "+purchaseStatus.getSubscriberID()+") : price debit failed => initiate rollback ...");
          purchaseStatus.setPaymentDebitFailed(offerPrice);
          purchaseStatus.setPaymentBeingDebited(null);
          proceedRollback(response,purchaseStatus, PurchaseFulfillmentStatus.INSUFFICIENT_BALANCE, "handleCommodityDeliveryResponse : could not make payment of price "+offerPrice);
          return;
        }
      }
      
      //  ---  check products  ---
      if(purchaseStatus.getProductBeingCredited() != null){
        OfferProduct product = purchaseStatus.getProductBeingCredited();
        if(responseDeliveryStatus.equals(DeliveryStatus.Delivered)){
          if(log.isDebugEnabled()) log.debug("handleCommodityDeliveryResponse() : ("+purchaseStatus.getOfferID()+", "+purchaseStatus.getSubscriberID()+") : product credited (product id "+product.getProductID()+")");
          purchaseStatus.addProductCredited(product);
          purchaseStatus.setProductBeingCredited(null);
        }else{
          //responseDeliveryStatus is one of those : Pending, FailedRetry, Delivered, Indeterminate, Failed, FailedTimeout, Unknown
          log.info("handleCommodityDeliveryResponse() : ("+purchaseStatus.getOfferID()+", "+purchaseStatus.getSubscriberID()+") : product credit failed (product id "+product.getProductID()+") => initiate rollback ...");
          purchaseStatus.setProductCreditFailed(product);
          purchaseStatus.setProductBeingCredited(null);
          proceedRollback(response,purchaseStatus, PurchaseFulfillmentStatus.THIRD_PARTY_ERROR, "handleCommodityDeliveryResponse : could not credit product "+product.getProductID());
          return;
        }
      }

      // continue purchase process
      if(log.isDebugEnabled()) log.debug("handleCommodityDeliveryResponse() : ("+purchaseStatus.getOfferID()+", "+purchaseStatus.getSubscriberID()+") : continue purchase process ...");
      proceedPurchase(response,purchaseStatus, response.getTenantID(), response.getSubscriberID());
     
    }

    if(log.isDebugEnabled()) log.debug("handleCommodityDeliveryResponse() :  DONE");

  }
  
  /*****************************************
  *
  *  class PurchaseRequestStatus
  *
  *****************************************/

  private static class PurchaseRequestStatus
  {

    /*****************************************
    *
    *  data
    *
    *****************************************/
    
    private int newRequestCounter = 0;
    
    private String correlator = null;
    private String  eventID = null;
    private String  moduleID = null;
    private String  featureID = null;
    private String offerID = null;
    private String subscriberID = null;
    private int quantity = -1;
    private String salesChannelID = null;
    
    private boolean rollbackInProgress = false;
    
    private PurchaseFulfillmentStatus purchaseFulfillmentStatus = PurchaseFulfillmentStatus.PENDING;
    private DeliveryStatus deliveryStatus = DeliveryStatus.Unknown;
    private int deliveryStatusCode = -1;
    private String deliveryStatusMessage = null;
    
    private List<OfferProduct> productStockToBeDebited = null;
    private OfferProduct productStockBeingDebited = null;
    private List<OfferProduct> productStockDebited = null;
    private OfferProduct productStockDebitFailed = null;
    private OfferProduct productStockBeingRollbacked = null;
    private List<OfferProduct> productStockRollbacked = null;
    private List<OfferProduct> productStockRollbackFailed = null;
    
    private List<String> offerStockToBeDebited = null;
    private String offerStockBeingDebited = null;
    private List<String> offerStockDebited = null;
    private String offerStockDebitFailed = null;
    private String offerStockBeingRollbacked = null;
    private List<String> offerStockRollbacked = null;
    private List<String> offerStockRollbackFailed = null;
    
    private List<OfferPrice> paymentToBeDebited = null;
    private OfferPrice paymentBeingDebited = null;
    private List<OfferPrice> paymentDebited = null;
    private OfferPrice paymentDebitFailed = null;
    private OfferPrice paymentBeingRollbacked = null;
    private List<OfferPrice> paymentRollbacked = null;
    private List<OfferPrice> paymentRollbackFailed = null;
    
    private List<OfferProduct> productToBeCredited = null;
    private OfferProduct productBeingCredited = null;
    private List<OfferProduct> productCredited = null;
    private OfferProduct productCreditFailed = null;
    private OfferProduct productBeingRollbacked = null;
    private List<OfferProduct> productRollbacked = null;
    private List<OfferProduct> productRollbackFailed = null;

    private List<OfferVoucher> voucherSharedToBeAllocated = null;
    private List<OfferVoucher> voucherSharedAllocated = null;
    private List<OfferVoucher> voucherSharedRollBacked = null;
    private List<OfferVoucher> voucherSharedRollBackFailed = null;
    private List<OfferVoucher> voucherPersonalToBeAllocated = null;
    private List<OfferVoucher> voucherPersonalAllocated = null;
    private List<OfferVoucher> voucherPersonalRollBacked = null;
    private List<OfferVoucher> voucherPersonalRollBackFailed = null;
    private OfferVoucher voucherAllocateFailed = null;

    private List<String> providerToBeNotifyed = null;
    private List<String> providerNotifyed = null;
    
//    private Boolean sendNotificationDone = false;
//    private Boolean recordPaymentDone = false;                  // generate BDR
//    private Boolean recordPurchaseDone = false;                 // generate ODR and PODR
//    private Boolean incrementPurchaseCountersDone = false;      //check subscriber limits... and record stats
//    private Boolean incrementPurchaseStatsDone = false;         //check subscriber limits... and record stats
    
    /*****************************************
    *
    *  getters
    *
    *****************************************/

    public int getNewRequestCounter(){return newRequestCounter;}
    
    public String getCorrelator(){return correlator;}
    public String getEventID(){return eventID;}
    public String getModuleID(){return moduleID;}
    public String getFeatureID(){return featureID;}
    public String getOfferID(){return offerID;}
    public String getSubscriberID(){return subscriberID;}
    public int getQuantity(){return quantity;}
    public String getSalesChannelID(){return salesChannelID;}

    public boolean getRollbackInProgress(){return rollbackInProgress;}
    
    public PurchaseFulfillmentStatus getPurchaseFulfillmentStatus(){return purchaseFulfillmentStatus;}
    public DeliveryStatus getDeliveryStatus(){return deliveryStatus;}
    public int getDeliveryStatusCode(){return deliveryStatusCode;}
    public String getDeliveryStatusMessage(){return deliveryStatusMessage;}

    public List<OfferProduct> getProductStockToBeDebited(){return productStockToBeDebited;}
    public OfferProduct getProductStockBeingDebited(){return productStockBeingDebited;}
    public List<OfferProduct> getProductStockDebited(){return productStockDebited;}
    public OfferProduct getProductStockDebitFailed(){return productStockDebitFailed;}
    public OfferProduct getProductStockBeingRollbacked(){return productStockBeingRollbacked;}
    public List<OfferProduct> getProductStockRollbacked(){return productStockRollbacked;}
    public List<OfferProduct> getProductStockRollbackFailed(){return productStockRollbackFailed;}

    public List<String> getOfferStockToBeDebited(){return offerStockToBeDebited;}
    public String getOfferStockBeingDebited(){return offerStockBeingDebited;}
    public List<String> getOfferStockDebited(){return offerStockDebited;}
    public String getOfferStockDebitFailed(){return offerStockDebitFailed;}
    public String getOfferStockBeingRollbacked(){return offerStockBeingRollbacked;}
    public List<String> getOfferStockRollbacked(){return offerStockRollbacked;}
    public List<String> getOfferStockRollbackFailed(){return offerStockRollbackFailed;}
    
    public List<OfferPrice> getPaymentToBeDebited(){return paymentToBeDebited;}
    public OfferPrice getPaymentBeingDebited(){return paymentBeingDebited;}
    public List<OfferPrice> getPaymentDebited(){return paymentDebited;}
    public OfferPrice getPaymentDebitFailed(){return paymentDebitFailed;}
    public OfferPrice getPaymentBeingRollbacked(){return paymentBeingRollbacked;}
    public List<OfferPrice> getPaymentRollbacked(){return paymentRollbacked;}
    public List<OfferPrice> getPaymentRollbackFailed(){return paymentRollbackFailed;}
    
    public List<OfferProduct> getProductToBeCredited(){return productToBeCredited;}
    public OfferProduct getProductBeingCredited(){return productBeingCredited;}
    public List<OfferProduct> getProductCredited(){return productCredited;}
    public OfferProduct getProductCreditFailed(){return productCreditFailed;}
    public OfferProduct getProductBeingRollbacked(){return productBeingRollbacked;}
    public List<OfferProduct> getProductRollbacked(){return productRollbacked;}
    public List<OfferProduct> getProductRollbackFailed(){return productRollbackFailed;}

    public List<OfferVoucher> getVoucherSharedToBeAllocated() {return voucherSharedToBeAllocated;}
    public List<OfferVoucher> getVoucherSharedAllocated() {return voucherSharedAllocated;}
    public List<OfferVoucher> getVoucherSharedRollBacked() {return voucherSharedRollBacked;}
    public List<OfferVoucher> getVoucherSharedRollBackFailed() {return voucherSharedRollBackFailed;}
    public List<OfferVoucher> getVoucherPersonalToBeAllocated() {return voucherPersonalToBeAllocated;}
    public List<OfferVoucher> getVoucherPersonalAllocated() {return voucherPersonalAllocated;}
    public List<OfferVoucher> getVoucherPersonalRollBacked() {return voucherPersonalRollBacked;}
    public List<OfferVoucher> getVoucherPersonalRollBackFailed() {return voucherPersonalRollBackFailed;}
    public OfferVoucher getVoucherAllocateFailed() {return voucherAllocateFailed;}

    public List<String> getProviderToBeNotifyed(){return providerToBeNotifyed;}
    public List<String> getProviderNotifyed(){return providerNotifyed;}
 
    /*****************************************
    *
    *  setters
    *
    *****************************************/
    
    private void incrementNewRequestCounter(){this.newRequestCounter++;}

    public void setRollbackInProgress(boolean rollbackInProgress){this.rollbackInProgress = rollbackInProgress;}

    public void setPurchaseFulfillmentStatus(PurchaseFulfillmentStatus purchaseFulfillmentStatus){this.purchaseFulfillmentStatus = purchaseFulfillmentStatus;}
    public void setDeliveryStatus(DeliveryStatus deliveryStatus){this.deliveryStatus = deliveryStatus;}
    public void setDeliveryStatusCode(int deliveryStatusCode){this.deliveryStatusCode = deliveryStatusCode;}
    public void setDeliveryStatusMessage(String deliveryStatusMessage){this.deliveryStatusMessage = deliveryStatusMessage;}

    public void addProductStockToBeDebited(OfferProduct product){if(productStockToBeDebited == null){productStockToBeDebited = new ArrayList<OfferProduct>();} productStockToBeDebited.add(product);}
    public void setProductStockBeingDebited(OfferProduct product){this.productStockBeingDebited = product;}
    public void addProductStockDebited(OfferProduct product){if(productStockDebited == null){productStockDebited = new ArrayList<OfferProduct>();} productStockDebited.add(product);}
    public void setProductStockDebitFailed(OfferProduct product){this.productStockDebitFailed = product;}
    public void setProductStockBeingRollbacked(OfferProduct product){this.productStockBeingRollbacked = product;}
    public void addProductStockRollbacked(OfferProduct product){if(productStockRollbacked == null){productStockRollbacked = new ArrayList<OfferProduct>();} productStockRollbacked.add(product);}
    public void addProductStockRollbackFailed(OfferProduct product){if(productStockRollbackFailed == null){productStockRollbackFailed = new ArrayList<OfferProduct>();} productStockRollbackFailed.add(product);}
    
    public void addOfferStockToBeDebited(String offerID){if(offerStockToBeDebited == null){offerStockToBeDebited = new ArrayList<String>();} offerStockToBeDebited.add(offerID);}
    public void setOfferStockBeingDebited(String offerID){this.offerStockBeingDebited = offerID;}
    public void addOfferStockDebited(String offerID){if(offerStockDebited == null){offerStockDebited = new ArrayList<String>();} offerStockDebited.add(offerID);}
    public void setOfferStockDebitFailed(String offerID){this.offerStockDebitFailed = offerID;}
    public void setOfferStockBeingRollbacked(String offerID){this.offerStockBeingRollbacked = offerID;}
    public void addOfferStockRollbacked(String offerID){if(offerStockRollbacked == null){offerStockRollbacked = new ArrayList<String>();} offerStockRollbacked.add(offerID);}
    public void addOfferStockRollbackFailed(String offerID){if(offerStockRollbackFailed == null){offerStockRollbackFailed = new ArrayList<String>();} offerStockRollbackFailed.add(offerID);}

    public void addPaymentToBeDebited(OfferPrice offerPrice){if(paymentToBeDebited == null){paymentToBeDebited = new ArrayList<OfferPrice>();} paymentToBeDebited.add(offerPrice);}
    public void setPaymentBeingDebited(OfferPrice offerPrice){this.paymentBeingDebited = offerPrice;}
    public void addPaymentDebited(OfferPrice offerPrice){if(paymentDebited == null){paymentDebited = new ArrayList<OfferPrice>();} paymentDebited.add(offerPrice);}
    public void setPaymentDebitFailed(OfferPrice offerPrice){this.paymentDebitFailed = offerPrice;}
    public void setPaymentBeingRollbacked(OfferPrice offerPrice){this.paymentBeingRollbacked = offerPrice;}
    public void addPaymentRollbacked(OfferPrice offerPrice){if(paymentRollbacked == null){paymentRollbacked = new ArrayList<OfferPrice>();} paymentRollbacked.add(offerPrice);}
    public void addPaymentRollbackFailed(OfferPrice offerPrice){if(paymentRollbackFailed == null){paymentRollbackFailed = new ArrayList<OfferPrice>();} paymentRollbackFailed.add(offerPrice);}

    public void addProductToBeCredited(OfferProduct product){if(productToBeCredited == null){productToBeCredited = new ArrayList<OfferProduct>();} productToBeCredited.add(product);}
    public void setProductBeingCredited(OfferProduct product){this.productBeingCredited = product;}
    public void addProductCredited(OfferProduct product){if(productCredited == null){productCredited = new ArrayList<OfferProduct>();} productCredited.add(product);}
    public void setProductCreditFailed(OfferProduct product){this.productCreditFailed = product;}
    public void setProductBeingRollbacked(OfferProduct product){this.productBeingRollbacked = product;}
    public void addProductRollbacked(OfferProduct product){if(productRollbacked == null){productRollbacked = new ArrayList<OfferProduct>();} productRollbacked.add(product);}
    public void addProductRollbackFailed(OfferProduct product){if(productRollbackFailed == null){productRollbackFailed = new ArrayList<OfferProduct>();} productRollbackFailed.add(product);}

    public void addVoucherSharedToBeAllocated(OfferVoucher voucher){if(voucherSharedToBeAllocated == null){voucherSharedToBeAllocated = new ArrayList<OfferVoucher>();} voucherSharedToBeAllocated.add(voucher);}
    public void addVoucherSharedAllocated(OfferVoucher voucher){if(voucherSharedAllocated == null){voucherSharedAllocated = new ArrayList<OfferVoucher>();} voucherSharedAllocated.add(voucher);}
    public void addVoucherSharedRollBacked(OfferVoucher voucher){if(voucherSharedRollBacked == null){voucherSharedRollBacked = new ArrayList<OfferVoucher>();} voucherSharedRollBacked.add(voucher);}
    public void addVoucherSharedRollBackFailed(OfferVoucher voucher){if(voucherSharedRollBackFailed == null){voucherSharedRollBackFailed = new ArrayList<OfferVoucher>();} voucherSharedRollBackFailed.add(voucher);}
    public void addVoucherPersonalToBeAllocated(OfferVoucher voucher){if(voucherPersonalToBeAllocated == null){voucherPersonalToBeAllocated = new ArrayList<OfferVoucher>();} voucherPersonalToBeAllocated.add(voucher);}
    public void addVoucherPersonalAllocated(OfferVoucher voucher){if(voucherPersonalAllocated == null){voucherPersonalAllocated = new ArrayList<OfferVoucher>();} voucherPersonalAllocated.add(voucher);}
    public void addVoucherPersonalRollBacked(OfferVoucher voucher){if(voucherPersonalRollBacked == null){voucherPersonalRollBacked = new ArrayList<OfferVoucher>();} voucherPersonalRollBacked.add(voucher);}
    public void addVoucherPersonalRollBackFailed(OfferVoucher voucher){if(voucherPersonalRollBackFailed == null){voucherPersonalRollBackFailed = new ArrayList<OfferVoucher>();} voucherPersonalRollBackFailed.add(voucher);}
    public void setVoucherAllocateFailed(OfferVoucher voucher){this.voucherAllocateFailed = voucher;}

    /*****************************************
    *
    *  Constructors
    *
    *****************************************/

    public PurchaseRequestStatus(String correlator, String eventID, String moduleID, String featureID, String offerID, String subscriberID, int quantity, String salesChannelID){
      this.correlator = correlator;
      this.eventID = eventID;
      this.moduleID = moduleID;
      this.featureID = featureID;
      this.offerID = offerID;
      this.subscriberID = subscriberID;
      this.quantity = quantity;
      this.salesChannelID = salesChannelID;
    }

    /*****************************************
    *
    *  constructor -- JSON
    *
    *****************************************/

    public PurchaseRequestStatus(JSONObject jsonRoot)
    {

      if(log.isDebugEnabled()) log.debug("PurchaseRequestStatus() : "+jsonRoot.toJSONString());

      this.newRequestCounter = JSONUtilities.decodeInteger(jsonRoot, "newRequestCounter", true);
      
      this.correlator = JSONUtilities.decodeString(jsonRoot, "correlator", true);
      this.eventID = JSONUtilities.decodeString(jsonRoot, "eventID");
      this.moduleID = JSONUtilities.decodeString(jsonRoot, "moduleID", true);
      this.featureID = JSONUtilities.decodeString(jsonRoot, "featureID", true);
      this.offerID = JSONUtilities.decodeString(jsonRoot, "offerID", true);
      this.subscriberID = JSONUtilities.decodeString(jsonRoot, "subscriberID", true);
      this.quantity = JSONUtilities.decodeInteger(jsonRoot, "quantity", true);
      this.salesChannelID = JSONUtilities.decodeString(jsonRoot, "salesChannelID", true);
      
      this.rollbackInProgress = JSONUtilities.decodeBoolean(jsonRoot, "rollbackInProgress", false);
      
      this.purchaseFulfillmentStatus = PurchaseFulfillmentStatus.fromReturnCode(JSONUtilities.decodeInteger(jsonRoot, "purchaseFulfillmentStatus", false));
      this.deliveryStatus = DeliveryStatus.fromExternalRepresentation(JSONUtilities.decodeString(jsonRoot, "deliveryStatus", false));
      this.deliveryStatusCode = JSONUtilities.decodeInteger(jsonRoot, "deliveryStatusCode", false);
      this.deliveryStatusMessage = JSONUtilities.decodeString(jsonRoot, "deliveryStatusMessage", false);
      
      //
      // product stock
      //
      
      if(JSONUtilities.decodeJSONObject(jsonRoot, "productStockBeingDebited", false) != null){
        this.productStockBeingDebited = new OfferProduct(JSONUtilities.decodeJSONObject(jsonRoot, "productStockBeingDebited", false));
      }
      if(JSONUtilities.decodeJSONObject(jsonRoot, "productStockDebitFailed", false) != null){
        this.productStockDebitFailed = new OfferProduct(JSONUtilities.decodeJSONObject(jsonRoot, "productStockDebitFailed", false));
      }
      if(JSONUtilities.decodeJSONObject(jsonRoot, "productStockBeingRollbacked", false) != null){
        this.productStockBeingRollbacked = new OfferProduct(JSONUtilities.decodeJSONObject(jsonRoot, "productStockBeingRollbacked", false));
      }
      JSONArray productStockToBeDebitedJSON = JSONUtilities.decodeJSONArray(jsonRoot, "productStockToBeDebited", false);
      if (productStockToBeDebitedJSON != null){
        List<OfferProduct> productStockToBeDebitedList = new ArrayList<OfferProduct>();
        for (int i=0; i<productStockToBeDebitedJSON.size(); i++){
          productStockToBeDebitedList.add(new OfferProduct((JSONObject) productStockToBeDebitedJSON.get(i)));
        }
        this.productStockToBeDebited = productStockToBeDebitedList;
      }
      JSONArray productStockDebitedJSON = JSONUtilities.decodeJSONArray(jsonRoot, "productStockDebited", false);
      if (productStockDebitedJSON != null){
        List<OfferProduct> productStockDebitedList = new ArrayList<OfferProduct>();
        for (int i=0; i<productStockDebitedJSON.size(); i++){
          productStockDebitedList.add(new OfferProduct((JSONObject) productStockDebitedJSON.get(i)));
        }
        this.productStockDebited = productStockDebitedList;
      }
      JSONArray productStockRollbackedJSON = JSONUtilities.decodeJSONArray(jsonRoot, "productStockRollbacked", false);
      if (productStockRollbackedJSON != null){
        List<OfferProduct> productStockRollbackedList = new ArrayList<OfferProduct>();
        for (int i=0; i<productStockRollbackedJSON.size(); i++){
          productStockRollbackedList.add(new OfferProduct((JSONObject) productStockRollbackedJSON.get(i)));
        }
        this.productStockRollbacked = productStockRollbackedList;
      }
      JSONArray productStockRollbackFailedJSON = JSONUtilities.decodeJSONArray(jsonRoot, "productStockRollbackFailed", false);
      if (productStockRollbackFailedJSON != null){
        List<OfferProduct> productStockRollbackFailedList = new ArrayList<OfferProduct>();
        for (int i=0; i<productStockRollbackFailedJSON.size(); i++){
          productStockRollbackFailedList.add(new OfferProduct((JSONObject) productStockRollbackFailedJSON.get(i)));
        }
        this.productStockRollbackFailed = productStockRollbackFailedList;
      }

      //
      // offer stock
      //
      
      JSONArray offerStockToBeDebitedJSON = JSONUtilities.decodeJSONArray(jsonRoot, "offerStockToBeDebited", false);
      if (offerStockToBeDebitedJSON != null){
        List<String> offerStockToBeDebitedList = new ArrayList<String>();
        for (int i=0; i<offerStockToBeDebitedJSON.size(); i++){
          offerStockToBeDebitedList.add((String) offerStockToBeDebitedJSON.get(i));
        }
        this.offerStockToBeDebited = offerStockToBeDebitedList;
      }
      this.offerStockBeingDebited = JSONUtilities.decodeString(jsonRoot, "offerStockBeingDebited", false);
      JSONArray offerStockDebitedJSON = JSONUtilities.decodeJSONArray(jsonRoot, "offerStockDebited", false);
      if (offerStockDebitedJSON != null){
        List<String> offerStockDebitedList = new ArrayList<String>();
        for (int i=0; i<offerStockDebitedJSON.size(); i++){
          offerStockDebitedList.add((String) offerStockDebitedJSON.get(i));
        }
        this.offerStockDebited = offerStockDebitedList;
      }
      this.offerStockDebitFailed = JSONUtilities.decodeString(jsonRoot, "offerStockDebitFailed", false);
      this.offerStockBeingRollbacked = JSONUtilities.decodeString(jsonRoot, "offerStockBeingRollbacked", false);
      JSONArray offerStockRollbackedJSON = JSONUtilities.decodeJSONArray(jsonRoot, "offerStockRollbacked", false);
      if (offerStockRollbackedJSON != null){
        List<String> offerStockRollbackedList = new ArrayList<String>();
        for (int i=0; i<offerStockRollbackedJSON.size(); i++){
          offerStockRollbackedList.add((String) offerStockRollbackedJSON.get(i));
        }
        this.offerStockRollbacked = offerStockRollbackedList;
      }
      JSONArray offerStockRollbackFailedJSON = JSONUtilities.decodeJSONArray(jsonRoot, "offerStockRollbackFailed", false);
      if (offerStockRollbackFailedJSON != null){
        List<String> offerStockRollbackFailedList = new ArrayList<String>();
        for (int i=0; i<offerStockRollbackFailedJSON.size(); i++){
          offerStockRollbackFailedList.add((String) offerStockRollbackFailedJSON.get(i));
        }
        this.offerStockRollbackFailed = offerStockRollbackFailedList;
      }
      
      //
      // paymentMeans
      //
      
      JSONArray paymentToBeDebitedJSON = JSONUtilities.decodeJSONArray(jsonRoot, "paymentToBeDebited", false);
      if (paymentToBeDebitedJSON != null){
        List<OfferPrice> paymentToBeDebitedList = new ArrayList<OfferPrice>();
        for (int i=0; i<paymentToBeDebitedJSON.size(); i++){
            paymentToBeDebitedList.add(new OfferPrice((JSONObject) paymentToBeDebitedJSON.get(i)));
        }
        this.paymentToBeDebited = paymentToBeDebitedList;
      }
      if(JSONUtilities.decodeJSONObject(jsonRoot, "paymentBeingDebited", false) != null){
        this.paymentBeingDebited = new OfferPrice(JSONUtilities.decodeJSONObject(jsonRoot, "paymentBeingDebited", false));
      }
      JSONArray paymentDebitedJSON = JSONUtilities.decodeJSONArray(jsonRoot, "paymentDebited", false);
      if (paymentDebitedJSON != null){
        List<OfferPrice> paymentDebitedList = new ArrayList<OfferPrice>();
        for (int i=0; i<paymentDebitedJSON.size(); i++){
          paymentDebitedList.add(new OfferPrice((JSONObject) paymentDebitedJSON.get(i)));
        }
        this.paymentDebited = paymentDebitedList;
      }
      if(JSONUtilities.decodeJSONObject(jsonRoot, "paymentDebitFailed", false) != null){
        this.paymentDebitFailed = new OfferPrice(JSONUtilities.decodeJSONObject(jsonRoot, "paymentDebitFailed", false));
      }
      if(JSONUtilities.decodeJSONObject(jsonRoot, "paymentBeingRollbacked", false) != null){
        this.paymentBeingRollbacked = new OfferPrice(JSONUtilities.decodeJSONObject(jsonRoot, "paymentBeingRollbacked", false));
      }
      JSONArray paymentRollbackedJSON = JSONUtilities.decodeJSONArray(jsonRoot, "paymentRollbacked", false);
      if (paymentRollbackedJSON != null){
        List<OfferPrice> paymentRollbackedList = new ArrayList<OfferPrice>();
        for (int i=0; i<paymentRollbackedJSON.size(); i++){
          paymentRollbackedList.add(new OfferPrice((JSONObject) paymentRollbackedJSON.get(i)));
        }
        this.paymentRollbacked = paymentRollbackedList;
      }
      JSONArray paymentRollbackFailedJSON = JSONUtilities.decodeJSONArray(jsonRoot, "paymentRollbackFailed", false);
      if (paymentRollbackFailedJSON != null){
        List<OfferPrice> paymentRollbackFailedList = new ArrayList<OfferPrice>();
        for (int i=0; i<paymentRollbackFailedJSON.size(); i++){
          paymentRollbackFailedList.add(new OfferPrice((JSONObject) paymentRollbackFailedJSON.get(i)));
        }
        this.paymentRollbackFailed = paymentRollbackFailedList;
      }
      
      //
      // product delivery
      //
      
      if(JSONUtilities.decodeJSONObject(jsonRoot, "productBeingCredited", false) != null){
        this.productBeingCredited = new OfferProduct(JSONUtilities.decodeJSONObject(jsonRoot, "productBeingCredited", false));
      }
      if(JSONUtilities.decodeJSONObject(jsonRoot, "productCreditFailed", false) != null){
        this.productCreditFailed = new OfferProduct(JSONUtilities.decodeJSONObject(jsonRoot, "productCreditFailed", false));
      }
      if(JSONUtilities.decodeJSONObject(jsonRoot, "productBeingRollbacked", false) != null){
        this.productBeingRollbacked = new OfferProduct(JSONUtilities.decodeJSONObject(jsonRoot, "productBeingRollbacked", false));
      }
      JSONArray productToBeCreditedJSON = JSONUtilities.decodeJSONArray(jsonRoot, "productToBeCredited", false);
      if (productToBeCreditedJSON != null){
        List<OfferProduct> productToBeCreditedList = new ArrayList<OfferProduct>();
        for (int i=0; i<productToBeCreditedJSON.size(); i++){
          productToBeCreditedList.add(new OfferProduct((JSONObject) productToBeCreditedJSON.get(i)));
        }
        this.productToBeCredited = productToBeCreditedList;
      }
      JSONArray productCreditedJSON = JSONUtilities.decodeJSONArray(jsonRoot, "productCredited", false);
      if (productCreditedJSON != null){
        List<OfferProduct> productCreditedList = new ArrayList<OfferProduct>();
        for (int i=0; i<productCreditedJSON.size(); i++){
          productCreditedList.add(new OfferProduct((JSONObject) productCreditedJSON.get(i)));
        }
        this.productCredited = productCreditedList;
      }
      JSONArray productRollbackedJSON = JSONUtilities.decodeJSONArray(jsonRoot, "productRollbacked", false);
      if (productRollbackedJSON != null){
        List<OfferProduct> productRollbackedList = new ArrayList<OfferProduct>();
        for (int i=0; i<productRollbackedJSON.size(); i++){
          productRollbackedList.add(new OfferProduct((JSONObject) productRollbackedJSON.get(i)));
        }
        this.productRollbacked = productRollbackedList;
      }
      JSONArray productRollbackFailedJSON = JSONUtilities.decodeJSONArray(jsonRoot, "productRollbackFailed", false);
      if (productRollbackFailedJSON != null){
        List<OfferProduct> productRollbackFailedList = new ArrayList<OfferProduct>();
        for (int i=0; i<productRollbackFailedJSON.size(); i++){
          productRollbackFailedList.add(new OfferProduct((JSONObject) productRollbackFailedJSON.get(i)));
        }
        this.productRollbackFailed = productRollbackFailedList;
      }

      //
      // voucher
      //

      JSONArray voucherSharedToBeAllocatedJSON = JSONUtilities.decodeJSONArray(jsonRoot, "voucherSharedToBeAllocated", false);
      if (voucherSharedToBeAllocatedJSON != null){
        List<OfferVoucher> voucherSharedToBeAllocatedList = new ArrayList<OfferVoucher>();
        for (int i=0; i<voucherSharedToBeAllocatedJSON.size(); i++){
          voucherSharedToBeAllocatedList.add(new OfferVoucher((JSONObject) voucherSharedToBeAllocatedJSON.get(i)));
        }
        this.voucherSharedToBeAllocated = voucherSharedToBeAllocatedList;
      }
      JSONArray voucherSharedAllocatedJSON = JSONUtilities.decodeJSONArray(jsonRoot, "voucherSharedAllocated", false);
      if (voucherSharedAllocatedJSON != null){
        List<OfferVoucher> voucherSharedAllocatedList = new ArrayList<OfferVoucher>();
        for (int i=0; i<voucherSharedAllocatedJSON.size(); i++){
          voucherSharedAllocatedList.add(new OfferVoucher((JSONObject) voucherSharedAllocatedJSON.get(i)));
        }
        this.voucherSharedAllocated = voucherSharedAllocatedList;
      }
      JSONArray voucherSharedRollBackedJSON = JSONUtilities.decodeJSONArray(jsonRoot, "voucherSharedRollBacked", false);
      if (voucherSharedRollBackedJSON != null){
        List<OfferVoucher> voucherSharedRollBackedList = new ArrayList<OfferVoucher>();
        for (int i=0; i<voucherSharedRollBackedJSON.size(); i++){
          voucherSharedRollBackedList.add(new OfferVoucher((JSONObject) voucherSharedRollBackedJSON.get(i)));
        }
        this.voucherSharedRollBacked = voucherSharedRollBackedList;
      }
      JSONArray voucherSharedRollBackFailedJSON = JSONUtilities.decodeJSONArray(jsonRoot, "voucherSharedRollBackFailed", false);
      if (voucherSharedRollBackFailedJSON != null){
        List<OfferVoucher> voucherSharedRollBackFailedList = new ArrayList<OfferVoucher>();
        for (int i=0; i<voucherSharedRollBackFailedJSON.size(); i++){
          voucherSharedRollBackFailedList.add(new OfferVoucher((JSONObject) voucherSharedRollBackFailedJSON.get(i)));
        }
        this.voucherSharedRollBackFailed = voucherSharedRollBackFailedList;
      }
      JSONArray voucherPersonalToBeAllocatedJSON = JSONUtilities.decodeJSONArray(jsonRoot, "voucherPersonalToBeAllocated", false);
      if (voucherPersonalToBeAllocatedJSON != null){
        List<OfferVoucher> voucherPersonalToBeAllocatedList = new ArrayList<OfferVoucher>();
        for (int i=0; i<voucherPersonalToBeAllocatedJSON.size(); i++){
          voucherPersonalToBeAllocatedList.add(new OfferVoucher((JSONObject) voucherPersonalToBeAllocatedJSON.get(i)));//FAILLING ???
        }
        this.voucherPersonalToBeAllocated = voucherPersonalToBeAllocatedList;
      }
      JSONArray voucherPersonalAllocatedJSON = JSONUtilities.decodeJSONArray(jsonRoot, "voucherPersonalAllocated", false);
      if (voucherPersonalAllocatedJSON != null){
        List<OfferVoucher> voucherPersonalAllocatedList = new ArrayList<OfferVoucher>();
        for (int i=0; i<voucherPersonalAllocatedJSON.size(); i++){
          voucherPersonalAllocatedList.add(new OfferVoucher((JSONObject) voucherPersonalAllocatedJSON.get(i)));
        }
        this.voucherPersonalAllocated = voucherPersonalAllocatedList;
      }
      JSONArray voucherPersonalRollBackedJSON = JSONUtilities.decodeJSONArray(jsonRoot, "voucherPersonalRollBacked", false);
      if (voucherPersonalRollBackedJSON != null){
        List<OfferVoucher> voucherPersonalRollBackedList = new ArrayList<OfferVoucher>();
        for (int i=0; i<voucherPersonalRollBackedJSON.size(); i++){
          voucherPersonalRollBackedList.add(new OfferVoucher((JSONObject) voucherPersonalRollBackedJSON.get(i)));
        }
        this.voucherPersonalRollBacked = voucherPersonalRollBackedList;
      }
      JSONArray voucherPersonalRollBackFailedJSON = JSONUtilities.decodeJSONArray(jsonRoot, "voucherPersonalRollBackFailed", false);
      if (voucherPersonalRollBackFailedJSON != null){
        List<OfferVoucher> voucherPersonalRollBackFailedList = new ArrayList<OfferVoucher>();
        for (int i=0; i<voucherPersonalRollBackFailedJSON.size(); i++){
          voucherPersonalRollBackFailedList.add(new OfferVoucher((JSONObject) voucherPersonalRollBackFailedJSON.get(i)));
        }
        this.voucherPersonalRollBackFailed = voucherPersonalRollBackFailedList;
      }
      if(JSONUtilities.decodeJSONObject(jsonRoot, "voucherAllocateFailed", false) != null){
        this.voucherAllocateFailed = new OfferVoucher(JSONUtilities.decodeJSONObject(jsonRoot, "voucherAllocateFailed", false));
      }

    }
    
    /*****************************************
    *  
    *  to JSONObject
    *
    *****************************************/
    
//    private List<String> providerToBeNotifyed = null;
//    private List<String> providerNotifyed = null;
    
    public JSONObject getJSONRepresentation(){
      Map<String, Object> data = new HashMap<String, Object>();
      
      data.put("newRequestCounter", this.getNewRequestCounter());

      data.put("correlator", this.getCorrelator());
      data.put("eventID", this.getEventID());
      data.put("moduleID", this.getModuleID());
      data.put("featureID", this.getFeatureID());
      data.put("offerID", this.getOfferID());
      data.put("subscriberID", this.getSubscriberID());
      data.put("quantity", this.getQuantity());
      data.put("salesChannelID", this.getSalesChannelID());
      
      data.put("rollbackInProgress", this.getRollbackInProgress());
      
      data.put("purchaseFulfillmentStatus", this.getPurchaseFulfillmentStatus().getReturnCode());
      data.put("deliveryStatus", this.getDeliveryStatus().getExternalRepresentation());
      data.put("deliveryStatusCode", this.getDeliveryStatusCode());
      data.put("deliveryStatusMessage", this.getDeliveryStatusMessage());
      
      //
      // product stock
      //
      
      if(this.getProductStockBeingDebited() != null){
        data.put("productStockBeingDebited", this.getProductStockBeingDebited().getJSONRepresentation());
      }
      if(this.getProductStockDebitFailed() != null){
        data.put("productStockDebitFailed", this.getProductStockDebitFailed().getJSONRepresentation());
      }
      if(this.getProductStockBeingRollbacked() != null){
        data.put("productStockBeingRollbacked", this.getProductStockBeingRollbacked().getJSONRepresentation());
      }
      if(this.getProductStockToBeDebited() != null){
        List<JSONObject> productStockToBeDebitedList = new ArrayList<JSONObject>();
        for(OfferProduct product : this.getProductStockToBeDebited()){
          productStockToBeDebitedList.add(product.getJSONRepresentation());
        }
        data.put("productStockToBeDebited", productStockToBeDebitedList);
      }
      if(this.getProductStockDebited() != null){
        List<JSONObject> productStockDebitedList = new ArrayList<JSONObject>();
        for(OfferProduct product : this.getProductStockDebited()){
          productStockDebitedList.add(product.getJSONRepresentation());
        }
        data.put("productStockDebited", productStockDebitedList);
      }
      if(this.getProductStockRollbacked() != null){
        List<JSONObject> productStockRollbackedList = new ArrayList<JSONObject>();
        for(OfferProduct product : this.getProductStockRollbacked()){
          productStockRollbackedList.add(product.getJSONRepresentation());
        }
        data.put("productStockRollbacked", productStockRollbackedList);
      }
      if(this.getProductStockRollbackFailed() != null){
        List<JSONObject> productStockRollbackFailedList = new ArrayList<JSONObject>();
        for(OfferProduct product : this.getProductStockRollbackFailed()){
          productStockRollbackFailedList.add(product.getJSONRepresentation());
        }
        data.put("productStockRollbackFailed", productStockRollbackFailedList);
      }

      //
      // offer stock
      //
      
      data.put("offerStockToBeDebited", this.getOfferStockToBeDebited());
      data.put("offerStockBeingDebited", this.getOfferStockBeingDebited());
      data.put("offerStockDebited", this.getOfferStockDebited());
      data.put("offerStockDebitFailed", this.getOfferStockDebitFailed());
      data.put("offerStockBeingRollbacked", this.getOfferStockBeingRollbacked());
      data.put("offerStockRollbacked", this.getOfferStockRollbacked());
      data.put("offerStockRollbackFailed", this.getOfferStockRollbackFailed());

      //
      // paymentMeans
      //

      if(this.getPaymentBeingDebited() != null){
        data.put("paymentBeingDebited", this.getPaymentBeingDebited().getJSONRepresentation());
      }
      if(this.getPaymentDebitFailed() != null){
        data.put("paymentDebitFailed", this.getPaymentDebitFailed().getJSONRepresentation());
      }
      if(this.getPaymentBeingRollbacked() != null){
        data.put("paymentBeingRollbacked", this.getPaymentBeingRollbacked().getJSONRepresentation());
      }
      if(this.getPaymentToBeDebited() != null){
        List<JSONObject> paymentToBeDebitedList = new ArrayList<JSONObject>();
        for(OfferPrice price : this.getPaymentToBeDebited()){
          if (price != null) paymentToBeDebitedList.add(price.getJSONRepresentation());
        }
        data.put("paymentToBeDebited", paymentToBeDebitedList);
      }
      if(this.getPaymentDebited() != null){
        List<JSONObject> paymentDebitedList = new ArrayList<JSONObject>();
        for(OfferPrice price : this.getPaymentDebited()){
          if (price != null) paymentDebitedList.add(price.getJSONRepresentation());
        }
        data.put("paymentDebited", paymentDebitedList);
      }
      if(this.getPaymentRollbacked() != null){
        List<JSONObject> paymentRollbackedList = new ArrayList<JSONObject>();
        for(OfferPrice price : this.getPaymentRollbacked()){
          if (price != null) paymentRollbackedList.add(price.getJSONRepresentation());
        }
        data.put("paymentRollbacked", paymentRollbackedList);
      }
      if(this.getPaymentRollbackFailed() != null){
        List<JSONObject> paymentRollbackFailedList = new ArrayList<JSONObject>();
        for(OfferPrice price : this.getPaymentRollbackFailed()){
          if (price != null) paymentRollbackFailedList.add(price.getJSONRepresentation());
        }
        data.put("paymentRollbackFailed", paymentRollbackFailedList);
      }

      //
      // product delivery
      //

      if(this.getProductBeingCredited() != null){
        data.put("productBeingCredited", this.getProductBeingCredited().getJSONRepresentation());
      }
      if(this.getProductCreditFailed() != null){
        data.put("productCreditFailed", this.getProductCreditFailed().getJSONRepresentation());
      }
      if(this.getProductBeingRollbacked() != null){
        data.put("productBeingRollbacked", this.getProductBeingRollbacked().getJSONRepresentation());
      }
      if(this.getProductToBeCredited() != null){
        List<JSONObject> productToBeCreditedList = new ArrayList<JSONObject>();
        for(OfferProduct product : this.getProductToBeCredited()){
          productToBeCreditedList.add(product.getJSONRepresentation());
        }
        data.put("productToBeCredited", productToBeCreditedList);
      }
      if(this.getProductCredited() != null){
        List<JSONObject> productCreditedList = new ArrayList<JSONObject>();
        for(OfferProduct product : this.getProductCredited()){
          productCreditedList.add(product.getJSONRepresentation());
        }
        data.put("productCredited", productCreditedList);
      }
      if(this.getProductRollbacked() != null){
        List<JSONObject> productRollbackedList = new ArrayList<JSONObject>();
        for(OfferProduct product : this.getProductRollbacked()){
          productRollbackedList.add(product.getJSONRepresentation());
        }
        data.put("productRollbacked", productRollbackedList);
      }
      if(this.getProductRollbackFailed() != null){
        List<JSONObject> productRollbackFailedList = new ArrayList<JSONObject>();
        for(OfferProduct product : this.getProductRollbackFailed()){
          productRollbackFailedList.add(product.getJSONRepresentation());
        }
        data.put("productRollbackFailed", productRollbackFailedList);
      }

      //
      // voucher
      //

      if(this.getVoucherSharedToBeAllocated() != null){
        List<JSONObject> voucherSharedToBeAllocatedList = new ArrayList<JSONObject>();
        for(OfferVoucher voucher : this.getVoucherSharedToBeAllocated()){
          voucherSharedToBeAllocatedList.add(voucher.getJSONRepresentationForPurchaseTransaction());
        }
        data.put("voucherSharedToBeAllocated", voucherSharedToBeAllocatedList);
      }
      if(this.getVoucherSharedAllocated() != null){
        List<JSONObject> voucherSharedAllocatedList = new ArrayList<JSONObject>();
        for(OfferVoucher voucher : this.getVoucherSharedAllocated()){
          voucherSharedAllocatedList.add(voucher.getJSONRepresentationForPurchaseTransaction());
        }
        data.put("voucherSharedAllocated", voucherSharedAllocatedList);
      }
      if(this.getVoucherSharedRollBacked() != null){
        List<JSONObject> voucherSharedRollBackedList = new ArrayList<JSONObject>();
        for(OfferVoucher voucher : this.getVoucherSharedRollBacked()){
          voucherSharedRollBackedList.add(voucher.getJSONRepresentationForPurchaseTransaction());
        }
        data.put("voucherSharedRollBacked", voucherSharedRollBackedList);
      }
      if(this.getVoucherSharedRollBackFailed() != null){
        List<JSONObject> voucherSharedRollBackFailedList = new ArrayList<JSONObject>();
        for(OfferVoucher voucher : this.getVoucherSharedRollBackFailed()){
          voucherSharedRollBackFailedList.add(voucher.getJSONRepresentationForPurchaseTransaction());
        }
        data.put("voucherSharedRollBackFailed", voucherSharedRollBackFailedList);
      }
      if(this.getVoucherPersonalToBeAllocated() != null){
        List<JSONObject> voucherPersonalToBeAllocatedList = new ArrayList<JSONObject>();
        for(OfferVoucher voucher : this.getVoucherPersonalToBeAllocated()){
          voucherPersonalToBeAllocatedList.add(voucher.getJSONRepresentationForPurchaseTransaction());
        }
        data.put("voucherPersonalToBeAllocated", voucherPersonalToBeAllocatedList);
      }
      if(this.getVoucherPersonalAllocated() != null){
        List<JSONObject> voucherPersonalAllocatedList = new ArrayList<JSONObject>();
        for(OfferVoucher voucher : this.getVoucherPersonalAllocated()){
          voucherPersonalAllocatedList.add(voucher.getJSONRepresentationForPurchaseTransaction());
        }
        data.put("voucherPersonalAllocated", voucherPersonalAllocatedList);
      }
      if(this.getVoucherPersonalRollBacked() != null){
        List<JSONObject> voucherPersonalRollBackedList = new ArrayList<JSONObject>();
        for(OfferVoucher voucher : this.getVoucherPersonalRollBacked()){
          voucherPersonalRollBackedList.add(voucher.getJSONRepresentationForPurchaseTransaction());
        }
        data.put("voucherPersonalRollBacked", voucherPersonalRollBackedList);
      }
      if(this.getVoucherPersonalRollBackFailed() != null){
        List<JSONObject> voucherPersonalRollBackFailedList = new ArrayList<JSONObject>();
        for(OfferVoucher voucher : this.getVoucherPersonalRollBackFailed()){
          voucherPersonalRollBackFailedList.add(voucher.getJSONRepresentationForPurchaseTransaction());
        }
        data.put("voucherPersonalRollBackFailed", voucherPersonalRollBackFailedList);
      }
      if(this.getVoucherAllocateFailed() != null){
        data.put("voucherAllocateFailed", this.getVoucherAllocateFailed().getJSONRepresentationForPurchaseTransaction());
      }

      //
      // return 
      //

      if(log.isDebugEnabled()) log.debug("PurchaseRequestStatus.getJSONRepresentation() : " + JSONUtilities.encodeObject(data).toJSONString());

      return JSONUtilities.encodeObject(data);
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

    private String moduleID;
    private String salesChannelID;//TODO: remove after everyone well migrated above 2.0
    
    /*****************************************
    *
    *  constructor
    *
    *****************************************/

    public ActionManager(JSONObject configuration) throws GUIManagerException
    {
      super(configuration);
      this.moduleID = JSONUtilities.decodeString(configuration, "moduleID", true);
      this.salesChannelID = JSONUtilities.decodeString(configuration, "salesChannel", false);
    }

    /*****************************************
    *
    *  execute
    *
    *****************************************/

    @Override public List<Action> executeOnEntry(EvolutionEventContext evolutionEventContext, SubscriberEvaluationRequest subscriberEvaluationRequest)
    {
      /*****************************************
      *
      *  parameters
      *
      *****************************************/

      String offerID = (String) subscriberEvaluationRequest.getJourneyNode().getNodeParameters().get("node.parameter.offerid");
      String salesChannelDisplay = (String) CriterionFieldRetriever.getJourneyNodeParameter(subscriberEvaluationRequest,"node.parameter.saleschannel");
      String salesChannelID=null;
      if(salesChannelDisplay==null && this.salesChannelID!=null)
        {
          salesChannelID = this.salesChannelID;
          log.error("old purchaseOffer node in journey "+subscriberEvaluationRequest.getJourneyState().getJourneyID()+" node "+subscriberEvaluationRequest.getJourneyState().getJourneyNodeID()+", please update journey to select a salesChannel. Will still use "+salesChannelID+" for now.");
        }
      else
        {
          for (SalesChannel salesChannel : evolutionEventContext.getSalesChannelService().getActiveSalesChannels(evolutionEventContext.eventDate(), subscriberEvaluationRequest.getTenantID()))
            {
              if (salesChannel.getGUIManagedObjectDisplay().equals(salesChannelDisplay))
                {
                  salesChannelID = salesChannel.getSalesChannelID();
                  break;
                }
            }
          if (salesChannelID==null)
            {
              log.info("unknown salesChannelID for display "+salesChannelDisplay);
              return Collections.emptyList();
            }
        }
      int quantity = (Integer) subscriberEvaluationRequest.getJourneyNode().getNodeParameters().get("node.parameter.quantity");
      String origin = null;
      if (subscriberEvaluationRequest.getJourneyNode() != null)
        {
          origin = subscriberEvaluationRequest.getJourneyNode().getNodeName();
        }
      /*****************************************
      *
      *  TEMP DEW HACK
      *
      *****************************************/

      offerID = (offerID != null) ? offerID : "0";
      
      /*****************************************
      *
      *  request arguments
      *
      *****************************************/
      String journeyID = subscriberEvaluationRequest.getJourneyState().getJourneyID();
      Journey journey = evolutionEventContext.getJourneyService().getActiveJourney(journeyID, evolutionEventContext.eventDate());
      String newModuleID = moduleID;
      if (journey != null && journey.getJSONRepresentation().get("areaAvailability") != null )
        {
          JSONArray areaAvailability = (JSONArray) journey.getJSONRepresentation().get("areaAvailability");
          if (areaAvailability != null && !(areaAvailability.isEmpty())) {
          for (int i = 0; i < areaAvailability.size(); i++)
            {
              if (!(areaAvailability.get(i).equals("realtime")) && !(areaAvailability.get(i).equals("journeymanager")))
                {
                  newModuleID = Module.Loyalty_Program.getExternalRepresentation();
                  if (subscriberEvaluationRequest.getJourneyState() != null && subscriberEvaluationRequest.getJourneyState().getsourceOrigin() != null)
                    {
                      origin = subscriberEvaluationRequest.getJourneyState().getsourceOrigin();
                    }
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
 
      /*****************************************
      *
      *  request
      *
      *****************************************/

      PurchaseFulfillmentRequest request = new PurchaseFulfillmentRequest(evolutionEventContext, deliveryRequestSource, offerID, quantity, salesChannelID, origin, "", subscriberEvaluationRequest.getTenantID());
      request.setModuleID(newModuleID);
      request.setFeatureID(deliveryRequestSource);

      /*****************************************
      *s
      *  return
      *
      *****************************************/

      return Collections.<Action>singletonList(request);
    }
    
    @Override public Map<String, String> getGUIDependencies(List<GUIService> guiServiceList, JourneyNode journeyNode, int tenantID)
    {
      Map<String, String> result = new HashMap<String, String>();
      String offerID = (String) journeyNode.getNodeParameters().get("node.parameter.offerid");
      if (offerID != null) result.put("offer", offerID);
      
      Object salesChannelObj = journeyNode.getNodeParameters().get("node.parameter.saleschannel");
      if (salesChannelObj != null && salesChannelObj instanceof ParameterExpression)
        {
          ParameterExpression supplierDisplayExp = (ParameterExpression) salesChannelObj;
          ExpressionReader expressionReader = new ExpressionReader(supplierDisplayExp.getCriterionContext(), supplierDisplayExp.getExpressionString(), supplierDisplayExp.getBaseTimeUnit(), supplierDisplayExp.getTenantID());
          Expression expression = expressionReader.parse(ExpressionContext.Parameter, tenantID);
          if (expression != null && expression instanceof ConstantExpression)
            {
              ConstantExpression consExpression = (ConstantExpression) expression;
              String salesChannelDisplay  = (String) consExpression.evaluateConstant();
              if (salesChannelDisplay != null)
                {
                  SalesChannelService salesChannelService = (SalesChannelService) guiServiceList.stream().filter(srvc -> srvc.getClass() == SalesChannelService.class).findFirst().orElse(null);
                  if (salesChannelService == null)
                    {
                      log.error("salesChannelService not found in guiServiceList - getGUIDependencies will be effected");
                    }
                  else
                    {
                      GUIManagedObject salesChannel = salesChannelService.getStoredSalesChannels(tenantID).stream().filter(guiObj -> salesChannelDisplay.equals(guiObj.getGUIManagedObjectDisplay())).findFirst().orElse(null);
                      if (salesChannel != null && salesChannel.getAccepted()) result.put("saleschannel", salesChannel.getGUIManagedObjectID());
                    }
                }
            }
        }
      return result;
    }
  }
  
  
}

