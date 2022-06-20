/*****************************************************************************
*
*  OfferReportDriver.java
*
*****************************************************************************/

package com.evolving.nglm.evolution.reports.offer;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.evolving.nglm.core.Deployment;
import com.evolving.nglm.core.JSONUtilities;
import com.evolving.nglm.evolution.CatalogCharacteristic;
import com.evolving.nglm.evolution.CatalogCharacteristicInstance;
import com.evolving.nglm.evolution.CatalogCharacteristicService;
import com.evolving.nglm.evolution.GUIManagedObject;
import com.evolving.nglm.evolution.Offer;
import com.evolving.nglm.evolution.OfferObjective;
import com.evolving.nglm.evolution.OfferObjectiveInstance;
import com.evolving.nglm.evolution.OfferObjectiveService;
import com.evolving.nglm.evolution.OfferService;
import com.evolving.nglm.evolution.PaymentMeanService;
import com.evolving.nglm.evolution.Product;
import com.evolving.nglm.evolution.ProductService;
import com.evolving.nglm.evolution.Report;
import com.evolving.nglm.evolution.SalesChannel;
import com.evolving.nglm.evolution.SalesChannelService;
import com.evolving.nglm.evolution.SupportedCurrency;
import com.evolving.nglm.evolution.Voucher;
import com.evolving.nglm.evolution.VoucherService;
import com.evolving.nglm.evolution.reports.FilterObject;
import com.evolving.nglm.evolution.reports.ReportDriver;
import com.evolving.nglm.evolution.reports.ReportDriver.ReportTypeDef;
import com.evolving.nglm.evolution.reports.journeys.JourneysReportDriver;
import com.evolving.nglm.evolution.reports.ReportUtils;
import com.evolving.nglm.evolution.reports.ReportsCommonCode;

@ReportTypeDef(reportType = "detailedrecords")
public class OfferReportDriver extends ReportDriver
{
  private static final Logger log = LoggerFactory.getLogger(OfferReportDriver.class);
  private OfferService offerService;
  private SalesChannelService salesChannelService;
  private OfferObjectiveService offerObjectiveService;
  private ProductService productService;
  private VoucherService voucherService;
  private PaymentMeanService paymentmeanservice;
  private CatalogCharacteristicService catalogCharacteristicService;
  
  private static final String offerID = "offerID";
  private static final String offerName = "offerName";
  private static final String offerContent = "offerContent";
  private static final String offerDescription = "offerDescription";
  private static final String offerCharacteristics = "offerCharacteristics";
  private static final String offerObjectives = "offerObjectives";
  private static final String startDate = "startDate";
  private static final String endDate = "endDate";
  private static final String availableStock = "availableStock";
  private static final String availableStockThreshold = "availableStockThreshold";
  private static final String salesChannelAndPrices = "salesChannelAndPrices";
  private static final String active = "active";
  
  
  
  static List<String> headerFieldsOrder = new ArrayList<String>();
  static
  {
    headerFieldsOrder.add(offerID);
    headerFieldsOrder.add(offerName);
    headerFieldsOrder.add(offerContent);
    headerFieldsOrder.add(offerDescription);
    headerFieldsOrder.add(offerCharacteristics);
    headerFieldsOrder.add(offerObjectives);
    headerFieldsOrder.add(startDate);
    headerFieldsOrder.add(endDate);
    headerFieldsOrder.add(availableStock);
    headerFieldsOrder.add(availableStockThreshold);
    headerFieldsOrder.add(salesChannelAndPrices);
    headerFieldsOrder.add(active);
  }

  /****************************************
   * 
   * produceReport
   * 
   ****************************************/

  @Override public void produceReport(Report report, final Date reportGenerationDate, String zookeeper, String kafka, String elasticSearch, String csvFilename, String[] params, int tenantID)
  {
    log.info("Entered OfferReportDriver.produceReport");

    Random r = new Random();
    int apiProcessKey = r.nextInt(999);

    log.trace("apiProcessKey " + apiProcessKey);

    offerService = new OfferService(kafka, "offerReportDriver-offerService-" + apiProcessKey, Deployment.getOfferTopic(), false);
    offerService.start();

    salesChannelService = new SalesChannelService(kafka, "offerReportDriver-saleschannelService-" + apiProcessKey, Deployment.getSalesChannelTopic(), false);
    salesChannelService.start();

    offerObjectiveService = new OfferObjectiveService(kafka, "offerReportDriver-offerObjectiveService-" + apiProcessKey, Deployment.getOfferObjectiveTopic(), false);
    offerObjectiveService.start();

    productService = new ProductService(kafka, "offerReportDriver-productService-" + apiProcessKey, Deployment.getProductTopic(), false);
    productService.start();
    
    voucherService = new VoucherService(kafka, "offerReportDriver-voucherService-" + apiProcessKey, Deployment.getVoucherTopic(), null);
    voucherService.start();

    paymentmeanservice = new PaymentMeanService(kafka, "offerReportDriver-paymentmeanservice-" + apiProcessKey, Deployment.getPaymentMeanTopic(), false);
    paymentmeanservice.start();

    catalogCharacteristicService = new CatalogCharacteristicService(kafka, "offerReportDriver-catalogcharacteristicservice-" + apiProcessKey, Deployment.getCatalogCharacteristicTopic(), false);
    catalogCharacteristicService.start();
    
    ReportsCommonCode.initializeDateFormats();

    boolean header = true;
    int first = 0;
    File file = new File(csvFilename + ".zip");
    FileOutputStream fos = null;
    ZipOutputStream writer = null;
    
    try
      {

        fos = new FileOutputStream(file);
        writer = new ZipOutputStream(fos);
        // do not include tree structure in zipentry, just csv filename
        ZipEntry entry = new ZipEntry(new File(csvFilename).getName());
        writer.putNextEntry(entry);
        log.info("no. of Offers :" + offerService.getStoredOffers(tenantID).size());
        if (offerService.getStoredOffers(tenantID).size() == 0)
          {
            if (headerFieldsOrder != null && !headerFieldsOrder.isEmpty())
              {
                String csvSeparator = ReportUtils.getSeparator();
                int offset = 1;
                String headers = "";
                for (String field : headerFieldsOrder)
                  {
                    headers += field + csvSeparator;
                  }
                headers = headers.substring(0, headers.length() - offset);
                writer.write(headers.getBytes());
                if (offset == 1)
                  {
                    writer.write("\n".getBytes());
                  }
              }
            log.info("No Offers ");
          }
        else
          {
            Collection<GUIManagedObject> offers = offerService.getStoredOffers(tenantID);
            int nbOffers = offers.size();
            log.debug("offer list size : " + nbOffers);

            for (GUIManagedObject guiManagedObject : offers)
              {
                try
                  {
                    Offer offer = (guiManagedObject instanceof Offer) ? (Offer) guiManagedObject : null;
                    dumpElementToCsv(offer, offerService.generateResponseJSON(guiManagedObject, true, reportGenerationDate), writer, header, (first == nbOffers - 1));
                    if (first == 0)
                      {
                        header = false;
                      }
                    ++first;
                  }
                catch (IOException | InterruptedException e)
                  {
                    log.info("exception " + e.getLocalizedMessage());
                  }
              }
          }
      }
    catch (IOException e)
      {
        log.info("exception " + e.getLocalizedMessage());
      }
    finally {
      offerService.stop();
      salesChannelService.stop();
      offerObjectiveService.stop();
      productService.stop();
      voucherService.stop();
      paymentmeanservice.stop();
      catalogCharacteristicService.stop();

      try
      {
        if (writer != null) writer.close();
      }
      catch (IOException e)
      {
        log.info("exception " + e.getLocalizedMessage());
      }
      if (fos != null)
        {
          try
          {
            fos.close();
          }
          catch (IOException e)
          {
            log.info("Exception " + e);
          }
        }
    }
  }

  /****************************************
   *
   * dumpElementToCsv
   *
   ****************************************/

  private void dumpElementToCsv(Offer offer, JSONObject recordJson, ZipOutputStream writer, Boolean addHeaders, boolean last)
      throws IOException, InterruptedException
  {
    // log.info("offer Records : {}",recordJson);

    String csvSeparator = ReportUtils.getSeparator();
    Map<String, Object> offerFields = new LinkedHashMap<>();

    try {
        offerFields.put(offerID, recordJson.get("id"));
        offerFields.put(offerName, recordJson.get("display"));
          {
            List<Map<String, Object>> offerContentJSON = new ArrayList<>();
            JSONArray productsElements = (JSONArray) recordJson.get("products");
            JSONArray vouchersElements = (JSONArray) recordJson.get("vouchers");
            
            //
            //  productsElements
            //
            
            for (Object obj : productsElements)
              {
                JSONObject element = (JSONObject) obj;
                if (element != null)
                  {
                    Map<String, Object> outputJSON = new HashMap<>();
                    String objectid = (String) (element.get("productID"));
                    GUIManagedObject guiManagedObject = (GUIManagedObject) productService.getStoredProduct(objectid);
                    if (guiManagedObject != null && guiManagedObject instanceof Product)
                      {
                        Product product = (Product) guiManagedObject;
                        outputJSON.put(product.getDisplay(), element.get("quantity"));
                      }
                    offerContentJSON.add(outputJSON);
                  }
              }
            
            //
            //  vouchersElements
            //
            
            for (Object obj : vouchersElements)
              {
                JSONObject element = (JSONObject) obj;
                if (element != null)
                  {
                    Map<String, Object> outputJSON = new HashMap<>();
                    String objectid = (String) (element.get("voucherID"));
                    GUIManagedObject guiManagedObject = (GUIManagedObject) voucherService.getStoredVoucher(objectid);
                    if (guiManagedObject != null && guiManagedObject instanceof Voucher)
                      {
                        Voucher voucher = (Voucher) guiManagedObject;
                        outputJSON.put(voucher.getVoucherDisplay(), element.get("quantity"));
                      }
                    offerContentJSON.add(outputJSON);
                  }
              }
            offerFields.put(offerContent, ReportUtils.formatJSON(offerContentJSON));
          }

        offerFields.put(offerDescription, recordJson.get("description"));

          {
            List<Map<String, Object>> outputJSON = new ArrayList<>();
            JSONObject obj = (JSONObject) recordJson.get("offerCharacteristics");
            if (obj != null)
              {
                JSONArray obj1 = (JSONArray) obj.get("languageProperties");
                if (obj1 != null && !obj1.isEmpty())
                  {
                    JSONObject obj2 = (JSONObject) obj1.get(0);
                    if (obj2 != null)
                      {
                        JSONArray obj3 = (JSONArray) obj2.get("properties");
                        if (obj3 != null && !obj3.isEmpty())
                          {
                            Map<String, Object> caracteristicsJSON = new HashMap<>();
                            for (int i = 0; i < obj3.size(); i++)
                              {
                                JSONObject offer2 = (JSONObject) obj3.get(i);
                                if (offer2 != null)
                                  {
                                    Object catalogCharacteristicIDObj = offer2.get("catalogCharacteristicID");
                                    if (catalogCharacteristicIDObj != null && catalogCharacteristicIDObj instanceof String) {
                                      GUIManagedObject catalogCharacteristicObj = catalogCharacteristicService.getStoredCatalogCharacteristic((String) catalogCharacteristicIDObj);
                                      if (catalogCharacteristicObj != null && catalogCharacteristicObj instanceof CatalogCharacteristic) {
                                        String name = ((CatalogCharacteristic) catalogCharacteristicObj).getGUIManagedObjectDisplay();
                                        caracteristicsJSON.put(name, offer2.get("value"));
                                      }
                                    }
                                  }
                              }
                            outputJSON.add(caracteristicsJSON);
                          }
                      }
                  }
              }
            offerFields.put(offerCharacteristics, ReportUtils.formatJSON(outputJSON));
          }

          {
            JSONArray elements = (JSONArray) recordJson.get("offerObjectives");
            List<Map<String, Object>> outputJSON = new ArrayList<>(); // to preserve order when displaying
            if (elements != null)
              {
                for (int i = 0; i < elements.size(); i++)
                  {
                    Map<String, Object> objectivesJSON = new LinkedHashMap<>(); // to preserve order when displaying
                    JSONObject element = (JSONObject) elements.get(i);
                    if (element != null)
                      {
                        String objeciveID = (String) (element.get("offerObjectiveID"));
                        GUIManagedObject guiManagedObject = (GUIManagedObject) offerObjectiveService.getStoredOfferObjective(objeciveID);
                        if (guiManagedObject != null && guiManagedObject instanceof OfferObjective)
                          {
                            OfferObjective offerObjective = (OfferObjective) guiManagedObject;
                            objectivesJSON.put("objectiveName", offerObjective.getOfferObjectiveDisplay());
                            if (offer != null)
                              {
                                List<Map<String, Object>> characteristicsJSON = new ArrayList<>();
                                for (OfferObjectiveInstance objective : offer.getOfferObjectives())
                                  {
                                    for (CatalogCharacteristicInstance characteristicInstance : objective.getCatalogCharacteristics())
                                      {
                                        Object value = characteristicInstance.getValue();
                                        String catalogCharacteristicID = characteristicInstance.getCatalogCharacteristicID();
                                        GUIManagedObject characteristic = catalogCharacteristicService.getStoredCatalogCharacteristic(catalogCharacteristicID);
                                        if (characteristic != null && characteristic instanceof CatalogCharacteristic)
                                          {
                                            Map<String, Object> characteristicJSON = new HashMap<>();
                                            CatalogCharacteristic characteristicObj = (CatalogCharacteristic) characteristic;
                                            String name = characteristicObj.getCatalogCharacteristicName();
                                            characteristicJSON.put(name, value);
                                            characteristicsJSON.add(characteristicJSON);
                                          }
                                      }
                                  }
                                objectivesJSON.put("characteristics", characteristicsJSON);
                              }
                            outputJSON.add(objectivesJSON);
                          }
                      }
                  }
              }
            offerFields.put(offerObjectives, ReportUtils.formatJSON(outputJSON));
          }
      
      offerFields.put(startDate, ReportsCommonCode.parseDate((String) recordJson.get("effectiveStartDate")));
      offerFields.put(endDate, ReportsCommonCode.parseDate((String) recordJson.get("effectiveEndDate")));      
      offerFields.put(availableStock, recordJson.get("presentationStock"));
      offerFields.put(availableStockThreshold, recordJson.get("presentationStockAlertThreshold"));

          {
            List<Map<String, Object>> outputJSON = new ArrayList<>();
            JSONArray elements = (JSONArray) recordJson.get("salesChannelsAndPrices");
            for (Object obj : elements)
              {
                JSONObject element = (JSONObject) obj;
                if (element != null)
                  {
                    JSONArray salesChannelIDs = (JSONArray) element.get("salesChannelIDs");
                    for (Object obj2 : salesChannelIDs)
                      {
                        String salesChannelID = (String) obj2;
                        GUIManagedObject guiManagedObject = (GUIManagedObject) salesChannelService.getStoredSalesChannel(salesChannelID);
                        if (guiManagedObject != null && guiManagedObject instanceof SalesChannel)
                          {
                            Map<String, Object> salesChannelJSON = new LinkedHashMap<>(); // to preserve order when displaying
                            SalesChannel salesChannel = (SalesChannel) guiManagedObject;
                            salesChannelJSON.put("salesChannelName", salesChannel.getGUIManagedObjectDisplay());

                            JSONObject price = (JSONObject) element.get("price");
                            if (price != null)
                              {
                                Long amount = JSONUtilities.decodeLong(price, "amount", Long.valueOf(0L)); // free by default
                                String id = "" + price.get("supportedCurrencyID");
                                String meansOfPayment = "" + price.get("paymentMeanID");
                                if (id != null && meansOfPayment != null)
                                  {
                                    String currency = null;
                                    GUIManagedObject meansOfPaymentObject = paymentmeanservice.getStoredPaymentMean(meansOfPayment);
                                    if (meansOfPaymentObject != null)
                                      {
                                        meansOfPayment = "" + meansOfPaymentObject.getJSONRepresentation().get("display");
                                        for (SupportedCurrency supportedCurrency : Deployment.getDeployment(offer.getTenantID()).getSupportedCurrencies().values())
                                          {
                                            JSONObject supportedCurrencyJSON = supportedCurrency.getJSONRepresentation();
                                            if (id.equals(supportedCurrencyJSON.get("id")))
                                              {
                                                currency = "" + supportedCurrencyJSON.get("display"); // TODO : not used ??
                                                break;
                                              }
                                          }
                                        log.debug("amount: " + amount + ", mean of payment:" + meansOfPayment + ", currency:" + currency);
                                        salesChannelJSON.put("mean of payment", meansOfPayment);
                                        salesChannelJSON.put("amount", amount);
                                        salesChannelJSON.put("currency", currency);
                                      }
                                  }
                              } 
                            else
                              {
                                salesChannelJSON.put("amount", 0);
                              }
                            outputJSON.add(salesChannelJSON);
                          }
                      }
                  }
              }
            offerFields.put(salesChannelAndPrices, ReportUtils.formatJSON(outputJSON));
          }

            // Arrays.sort(allFields);


      offerFields.put(active, recordJson.get("active"));      
     
      if (offerFields != null)
        {
          // Arrays.sort(allFields);
          
          if (addHeaders)
            {              
              String headers = "";              
              for (String fields : offerFields.keySet())
                {
                  
                  headers += fields + csvSeparator;
                }
              headers = headers.substring(0, headers.length() - 1);              
              writer.write(headers.getBytes());
              writer.write("\n".getBytes());
              addHeaders = false;
            }
          String line = ReportUtils.formatResult(offerFields);
          writer.write(line.getBytes());
        }
    }
    catch (Exception ex)
    {
      ex.printStackTrace();
      log.error("Exception while processing a line : {}, for offerID {}", ex.getMessage(), offer.getGUIManagedObjectID());
    }
    

    if (last)
      {
        log.info("Last offer record inserted into csv");
        writeCompleted(writer);
      }
  }

  /****************************************
   *
   * writeCompleted
   *
   ****************************************/

  private void writeCompleted(ZipOutputStream writer) throws IOException, InterruptedException
  {
    log.info("offerService {}", offerService.toString());
    writer.flush();
    writer.closeEntry();
    writer.close();
    log.debug("csv Writer closed");
  }

  @Override
  public List<FilterObject> reportFilters() {
	  return null;
  }

  @Override
  public List<String> reportHeader() {
    List<String> result = OfferReportDriver.headerFieldsOrder;
    return result;
  }
}
