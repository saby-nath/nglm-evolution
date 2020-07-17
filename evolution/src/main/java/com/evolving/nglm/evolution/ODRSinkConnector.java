package com.evolving.nglm.evolution;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

import org.apache.kafka.connect.data.Schema;
import org.apache.kafka.connect.data.SchemaAndValue;
import org.apache.kafka.connect.sink.SinkRecord;

import com.evolving.nglm.core.SimpleESSinkConnector;
import com.evolving.nglm.core.StreamESSinkTask;
import com.evolving.nglm.core.SystemTime;
import com.evolving.nglm.evolution.PurchaseFulfillmentManager.PurchaseFulfillmentRequest;
import com.evolving.nglm.evolution.PurchaseFulfillmentManager.PurchaseFulfillmentStatus;

public class ODRSinkConnector extends SimpleESSinkConnector
{
  
  private static OfferService offerService;
  private static ProductService productService;
  private static VoucherService voucherService;
  private static PaymentMeanService paymentMeanService;
  
  /****************************************
  *
  *  taskClass
  *
  ****************************************/
  
  @Override public Class<ODRSinkConnectorTask> taskClass()
  {
    return ODRSinkConnectorTask.class;
  }

  /****************************************
  *
  *  taskClass
  *
  ****************************************/
  
  public static class ODRSinkConnectorTask extends StreamESSinkTask<PurchaseFulfillmentRequest>
  {
    private static String elasticSearchDateFormat = Deployment.getElasticSearchDateFormat();
    private DateFormat dateFormat = new SimpleDateFormat(elasticSearchDateFormat);

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
    
      //
      //  services
      //
   
      offerService = new OfferService(Deployment.getBrokerServers(), "ordsinkconnector-offerservice-" + Integer.toHexString((new Random()).nextInt(1000000000)), Deployment.getOfferTopic(), false);
      offerService.start();
      
      productService = new ProductService(Deployment.getBrokerServers(), "ordsinkconnector-productservice-" + Integer.toHexString((new Random()).nextInt(1000000000)), Deployment.getProductTopic(), false);
      productService.start();

      voucherService = new VoucherService(Deployment.getBrokerServers(), "ordsinkconnector-voucherservice-" + Integer.toHexString((new Random()).nextInt(1000000000)), Deployment.getVoucherTopic());
      voucherService.start();

      paymentMeanService = new PaymentMeanService(Deployment.getBrokerServers(), "ordsinkconnector-paymentmeanservice-" + Integer.toHexString((new Random()).nextInt(1000000000)), Deployment.getPaymentMeanTopic(), false);
      paymentMeanService.start();
    }

    /*****************************************
    *
    *  stop
    *
    *****************************************/

    @Override public void stop()
    {
      //
      //  services
      //

      offerService.stop();
      productService.stop();
      voucherService.stop();
      paymentMeanService.stop();
      
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
    
    @Override public PurchaseFulfillmentRequest unpackRecord(SinkRecord sinkRecord) 
    {
      Object purchaseManagertValue = sinkRecord.value();
      Schema purchaseManagerValueSchema = sinkRecord.valueSchema();
      return PurchaseFulfillmentRequest.unpack(new SchemaAndValue(purchaseManagerValueSchema, purchaseManagertValue)); 
    }
    
    
    /*****************************************
    *
    *  getDocumentMap
    *
    *****************************************/
    
    @Override
    public Map<String, Object> getDocumentMap(PurchaseFulfillmentRequest purchaseManager)
    {
      Date now = SystemTime.getCurrentTime();
      Offer offer = offerService.getActiveOffer(purchaseManager.getOfferID(), now);
      
      Map<String,Object> documentMap = new HashMap<String,Object>();
      documentMap.put("subscriberID", purchaseManager.getSubscriberID());
      SinkConnectorUtils.putAlternateIDs(purchaseManager.getAlternateIDs(), documentMap);
      documentMap.put("deliveryRequestID", purchaseManager.getDeliveryRequestID());
      documentMap.put("originatingDeliveryRequestID", purchaseManager.getOriginatingDeliveryRequestID());
      documentMap.put("eventDatetime", purchaseManager.getEventDate()!=null?dateFormat.format(purchaseManager.getEventDate()):"");
      documentMap.put("eventID", purchaseManager.getEventID());
      documentMap.put("offerID", purchaseManager.getOfferID());
      documentMap.put("offerQty", purchaseManager.getQuantity());
      documentMap.put("salesChannelID", purchaseManager.getSalesChannelID());
      String voucherCodes = "";
        if (offer != null)
          {
            if (offer.getOfferSalesChannelsAndPrices() != null)
              {
                for (OfferSalesChannelsAndPrice channel : offer.getOfferSalesChannelsAndPrices())
                  {
                    if (channel.getSalesChannelIDs() != null)
                      {
                        for (String salesChannelID : channel.getSalesChannelIDs())
                          {
                            if (salesChannelID.equals(purchaseManager.getSalesChannelID()))
                              {
                                OfferPrice price = channel.getPrice();
                                if (price != null)
                                  {
                                    PaymentMean paymentMean = (PaymentMean) paymentMeanService.getStoredPaymentMean(price.getPaymentMeanID());
                                    if (paymentMean != null)
                                      {
                                        documentMap.put("offerPrice", price.getAmount());
                                        documentMap.put("meanOfPayment", paymentMean.getDisplay());
                                      }
                                  }
                              }
                          }
                      }
                  }
              }
        documentMap.put("offerStock", offer.getStock());
        StringBuilder sb = new StringBuilder();
        if(offer.getOfferProducts() != null) {
          for(OfferProduct offerProduct : offer.getOfferProducts()) {
            Product product = (Product) productService.getStoredProduct(offerProduct.getProductID());
            sb.append(offerProduct.getQuantity()+" ").append(product!=null?product.getDisplay():"product"+offerProduct.getProductID()).append(",");
          }
        }
        if(purchaseManager.getVoucherDeliveries()!=null){
          StringBuilder voucherCodeSb=new StringBuilder("");//ready for several vouchers in 1 purchase, though might only just allow one for simplicity
          for(VoucherDelivery voucherDelivery:purchaseManager.getVoucherDeliveries()){
            Voucher voucher = (Voucher) voucherService.getStoredVoucher(voucherDelivery.getVoucherID());
            sb.append("1 ").append(voucher!=null?voucher.getVoucherDisplay():"voucher"+voucherDelivery.getVoucherID()).append(",");
            if(voucherDelivery.getVoucherCode()!=null&&!voucherDelivery.getVoucherCode().isEmpty()){
              voucherCodeSb.append(voucherDelivery.getVoucherCode()).append(",");
            }
          }
          voucherCodes = voucherCodeSb.length()>0?voucherCodeSb.toString().substring(0,voucherCodeSb.toString().length()-1):"";
        }
        String offerContent = sb.length()>0?sb.toString().substring(0, sb.toString().length()-1):"";
        documentMap.put("offerContent", offerContent);
      }

      // populate with default values (for reports)
      if (documentMap.get("offerPrice") == null) documentMap.put("offerPrice", 0L);
      if (documentMap.get("meanOfPayment") == null) documentMap.put("meanOfPayment", "");
      if (documentMap.get("offerStock") == null) documentMap.put("offerStock", -1);
      if (documentMap.get("offerContent") == null) documentMap.put("offerContent", "");
      
      documentMap.put("moduleID", purchaseManager.getModuleID());
      documentMap.put("featureID", purchaseManager.getFeatureID());
      documentMap.put("origin", purchaseManager.getOrigin());
      documentMap.put("resellerID", purchaseManager.getResellerID());
      Object code = purchaseManager.getReturnCode();
      documentMap.put("returnCode", code);
      documentMap.put("returnCodeDetails", purchaseManager.getOfferDeliveryReturnCodeDetails());
      documentMap.put("voucherCode", voucherCodes);
      documentMap.put("voucherPartnerID", "");
      
      return documentMap;
    }
  }
}

