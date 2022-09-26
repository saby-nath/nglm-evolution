  /*****************************************
  *
  *  StockRecurrenceAndNotificationJob
  *
  *****************************************/

package com.evolving.nglm.evolution;

import java.io.IOException;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.util.EntityUtils;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import com.evolving.nglm.core.JSONUtilities;
import com.evolving.nglm.core.SystemTime;
import com.evolving.nglm.evolution.GUIManager.GUIManagerException;

public class StockRecurrenceAndNotificationJob  extends ScheduledJob 
{
  /*****************************************
  *
  *  data
  *
  *****************************************/
  
  private OfferService offerService;
  private ProductService productService;
  private VoucherService voucherService;
  private CallingChannelService callingChannelService;
  private CatalogCharacteristicService catalogCharacteristicService;
  private SalesChannelService salesChannelService;
  private SupplierService supplierService;
  private String fwkServer;
  int httpTimeout = 10000;
  RequestConfig requestConfig = RequestConfig.custom().setConnectTimeout(httpTimeout).setSocketTimeout(httpTimeout).setConnectionRequestTimeout(httpTimeout).build();
  
  /*****************************************
  *
  *  constructor
  *
  *****************************************/
  
  public StockRecurrenceAndNotificationJob(String jobName, String periodicGenerationCronEntry, String baseTimeZone, boolean scheduleAtStart, OfferService offerService, ProductService productService, VoucherService voucherService, CallingChannelService callingChannelService, CatalogCharacteristicService catalogCharacteristicService, SalesChannelService salesChannelService, SupplierService supplierService, String fwkServer)
  {
    super(jobName, periodicGenerationCronEntry, baseTimeZone, scheduleAtStart); 
    this.offerService = offerService;
    this.productService = productService;
    this.voucherService = voucherService;
    this.callingChannelService = callingChannelService;
    this.catalogCharacteristicService = catalogCharacteristicService;
    this.salesChannelService = salesChannelService;
    this.supplierService = supplierService;
    this.fwkServer = fwkServer;
  }

  /*****************************************
  *
  *  run
  *
  *****************************************/
  
  @Override protected void run()
  {
    Date now = SystemTime.getCurrentTime();
    Collection<Offer> activeOffers = offerService.getActiveOffers(now, 0);
    for (Offer offer : activeOffers)
      {
        boolean stockThersoldBreached = offer.getApproximateRemainingStock() != null && (offer.getApproximateRemainingStock() <= offer.getStockAlertThreshold());
        if (stockThersoldBreached)
          {
            //
            //  send notification
            //
            
            if (offer.getStockAlert())
              {
                log.info("RAJ K ready to send alert notification for offer {}", offer.getGUIManagedObjectDisplay());
                // send stock notification RAJ K (EVPRO-1601)
              }
            
            //
            // auto increment stock (EVPRO-1600)
            //
            
            if (offer.getStockRecurrence())
              {
                JSONObject offerJson = offer.getJSONRepresentation();
                offerJson.replace("presentationStock", offer.getStock() + offer.getStockRecurrenceBatch());
                try
                  {
                    Offer newOffer = new Offer(offerJson, GUIManager.epochServer.getKey(), offer, catalogCharacteristicService, offer.getTenantID());
                    offerService.putOffer(newOffer, callingChannelService, salesChannelService, productService, voucherService, (offer == null), "StockRecurrenceAndNotificationJob");
                  } 
                catch (GUIManagerException e)
                  {
                    e.printStackTrace();
                  }
              } 
            else
              {
                log.debug("stock recurrence scheduling not required for offer[{}]-- remaingin stock[{}], thresold limit[{}]", offer.getOfferID(), offer.getApproximateRemainingStock(), offer.getStockAlertThreshold());
              }
          }
      }
    
    Collection<Product> activeProducts = productService.getActiveProducts(now, 0);
    for (Product product : activeProducts)
      {
        boolean stockThersoldBreached = product.getApproximateRemainingStock() != null && (product.getApproximateRemainingStock() <= product.getStockAlertThreshold());
        if (stockThersoldBreached)
          {
            //
            //  send notification
            //
            
            if (product.getStockAlert())
              {
                log.info("RAJ K ready to send alert notification for product {}", product.getGUIManagedObjectDisplay());
                // send stock notification RAJ K (EVPRO-1601)
              }
          }
      }
    
    Collection<Voucher> activeVouchers = voucherService.getActiveVouchers(now, 0);
    for (Voucher voucher : activeVouchers)
      {
        Voucher voucherwithStock = (Voucher) voucherService.getStoredVoucherWithCurrentStocks(voucher.getGUIManagedObjectID(), false);
        Integer remainingStock = JSONUtilities.decodeInteger(voucherwithStock.getJSONRepresentation(), "remainingStock", false);
        if (remainingStock != null)
          {
            boolean stockThersoldBreached = remainingStock <= voucher.getStockAlertThreshold();
            if (stockThersoldBreached)
              {
                //
                //  send notification
                //
                
                if (voucher.getStockAlert())
                  {
                    log.info("RAJ K ready to send alert notification for voucherShared {}", voucher.getGUIManagedObjectDisplay());
                    // send stock notification RAJ K (EVPRO-1601)
                  }
              }
          }
        
      }
  }
  
  /*****************************************
  *
  *  sendNotification - FWK API Call
  *
  *****************************************/
  
  public void sendNotification(GUIManagedObject guiManagedObject)
  {
    CloseableHttpResponse httpResponse = null;
    try (CloseableHttpClient httpClient = HttpClientBuilder.create().setDefaultRequestConfig(requestConfig).build())
    {
      Map<String, Object> communicationMap = new HashMap<String, Object>();
      if (guiManagedObject instanceof Offer)
        {
          
        }
      else if (guiManagedObject instanceof Product)
        {
          
        }
      else if (guiManagedObject instanceof Voucher)
        {
          
        }
      communicationMap.put("UserId", "");
      communicationMap.put("From", "");
      communicationMap.put("To", "");
      communicationMap.put("Cc", "");
      communicationMap.put("Subject", "");
      communicationMap.put("Body", "");
      communicationMap.put("ObjectShortDescription", "");
      communicationMap.put("IsBodyHtml", true);
      communicationMap.put("AreRecepientsApprovalManagers", true);
      communicationMap.put("AreRecepientsAllUsersWithPermission", true);
      communicationMap.put("PermissionKeyWhichRecipientsMustHave", "");
      communicationMap.put("AreMacrosAvailable", true);
      communicationMap.put("Macros", "");
      communicationMap.put("GenerateTokenForEachRecepeint", true);
      communicationMap.put("AreFirstTwoMacrosFromBodyTokens", true);
      communicationMap.put("SendSeparateEmailForEachRecipient", true);
      communicationMap.put("ApplicationKey", "");
      communicationMap.put("ObjectId", "");
      communicationMap.put("CallBackURL", "");
      String payload = JSONUtilities.encodeObject(communicationMap).toJSONString();
      log.info("RAJ K payload {}", payload);
      
      //
      // create request
      //

      StringEntity stringEntity = new StringEntity(payload, ContentType.create("application/json"));
      HttpPost httpPost = new HttpPost("http://" + fwkServer + "/fwkapi/api/communication/email");
      httpPost.setEntity(stringEntity);

      //
      // submit request
      //

      httpResponse = httpClient.execute(httpPost);

      //
      // process response
      //

      if (httpResponse != null && httpResponse.getStatusLine() != null && httpResponse.getStatusLine().getStatusCode() == 200)
        {
          String jsonResponse = EntityUtils.toString(httpResponse.getEntity(), "UTF-8");
          log.info("FWK communication raw response : {}", jsonResponse);

          //
          // parse JSON response from FWK
          //

          JSONObject jsonRoot = (JSONObject) (new JSONParser()).parse(jsonResponse);
        }
      else if (httpResponse != null && httpResponse.getStatusLine() != null && httpResponse.getStatusLine().getStatusCode() == 401)
        {
          log.error("FWK communication server HTTP reponse code {} ", httpResponse.getStatusLine().getStatusCode());
        }
      else if (httpResponse != null && httpResponse.getStatusLine() != null)
        {
          log.error("FWK communication server HTTP reponse code is invalid {}", httpResponse.getStatusLine().getStatusCode());
        }
      else
        {
          log.error("FWK communication server error httpResponse or httpResponse.getStatusLine() is null {}", httpResponse, httpResponse.getStatusLine());
        }
    }
    catch(ParseException pe) 
    {
      log.error("failed to Parse ParseException {} ", pe.getMessage());
    }
    catch(IOException e) 
    {
      log.error("failed to authenticate in FWK server");
      log.error("IOException: {}", e.getMessage());
    }
    finally
    {
      if (httpResponse != null)
        try
          {
            httpResponse.close();
          } 
      catch (IOException e)
          {
            e.printStackTrace();
          }
    }
  
  }
}
