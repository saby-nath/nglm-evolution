/*****************************************************************************
*
*  ProductReportDriver.java
*
*****************************************************************************/

package com.evolving.nglm.evolution.reports.product;

import com.evolving.nglm.core.NGLMRuntime;
import com.evolving.nglm.core.SystemTime;
import com.evolving.nglm.evolution.*;
import com.evolving.nglm.evolution.reports.ReportDriver;
import com.evolving.nglm.evolution.reports.ReportUtils;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class ProductReportDriver extends ReportDriver
{
  private static final Logger log = LoggerFactory.getLogger(ProductReportDriver.class);
  private ProductService productService;
  private SupplierService supplierService;
  private DeliverableService deliverableService;
  private ProductTypeService productTypeService;
 
  @Override
  public void produceReport(Report report, String zookeeper, String kafka, String elasticSearch, String csvFilename, String[] params)
  {
    log.info("Entered in to the Product produceReport");
    
    Random r = new Random();
    int apiProcessKey = r.nextInt(999);

    log.info("apiProcessKey"+apiProcessKey);
    
    boolean header=true;
    int first=0;
    productService = new ProductService(kafka, "productReportDriver-productservice-" + apiProcessKey, Deployment.getProductTopic(), false);
    productService.start();
    supplierService = new SupplierService(kafka, "productReportDriver-supplierservice-"+ apiProcessKey, Deployment.getSupplierTopic(), false);
    supplierService.start();
    deliverableService= new DeliverableService(kafka, "productReportDriver-deliverableservice-"+ apiProcessKey, Deployment.getDeliverableTopic(), false);
    deliverableService.start();
    productTypeService= new ProductTypeService(kafka, "productReportDriver-productTypeservice-"+ apiProcessKey, Deployment.getProductTypeTopic(), false);
    productTypeService.start();
    File file = new File(csvFilename+".zip");
    FileOutputStream fos;
    try
      {
        log.info("productService.getStoredProducts().size(): "+productService.getStoredProducts().size());
        if(productService.getStoredProducts().size()==0){
          log.info("No products ");
          NGLMRuntime.addShutdownHook(new ShutdownHook(productService,supplierService,deliverableService,productTypeService));
                            
        }
          else{
            
        fos = new FileOutputStream(file);
        ZipOutputStream writer = new ZipOutputStream(fos);
        ZipEntry entry = new ZipEntry(new File(csvFilename).getName()); // do not include tree structure in zipentry, just csv filename
        writer.putNextEntry(entry);
        
        List<JSONObject> productJsonList = new ArrayList<JSONObject>();
        
        for (GUIManagedObject guiManagedObject : productService.getStoredProducts())
          {
            productJsonList.add(productService.generateResponseJSON(guiManagedObject, true, SystemTime.getCurrentTime()));
          }
       
        
       // log.info("productJsonList.size: "+productJsonList.size());
        for (JSONObject recordJson : productJsonList)
          {
            //log.info("recordJson: {}",recordJson);
            try
              {
                dumpElementToCsv(recordJson, writer,header,(first==productJsonList.size()-1));
                
                if(first==0){
                  header=false;
                }
                ++first;
              } catch (IOException | InterruptedException e)
              {
                e.printStackTrace();
              }

          }
          }   

      } catch (IOException e)
      {
        e.printStackTrace();
      }
       
  }

  private void dumpElementToCsv(JSONObject recordJson, ZipOutputStream writer, Boolean addHeaders, boolean last)
      throws IOException, InterruptedException
  {

    // log.info("inside dumpElementToCsv method");
    String csvSeparator = ReportUtils.getSeparator();

    Map<String, Object> productFields = new LinkedHashMap<>();

    try
      {
        productFields.put("productId", recordJson.get("id"));
        productFields.put("productName", recordJson.get("display"));
        productFields.put("productStartDate", recordJson.get("effectiveStartDate"));
        productFields.put("productEndDate", recordJson.get("effectiveEndDate"));
        productFields.put("supplierName", (supplierService.getStoredSupplier((String) recordJson.get("supplierID"))).getJSONRepresentation().get("display"));
        if (recordJson.get("deliverableID") != null)
          {
            productFields.put("fulfillment",
                (deliverableService.getStoredDeliverable((String) recordJson.get("deliverableID"))).getJSONRepresentation().get("name"));
          }
        else
          {
            productFields.put("fulfillment", "");
          }
        productFields.put("recommendedPrice", recordJson.get("recommendedPrice"));
        productFields.put("active", recordJson.get("active"));
        productFields.put("unitaryCost", recordJson.get("unitaryCost"));
        productFields.put("stock", recordJson.get("stock"));
        
        {
          List<Map<String, Object>> productTypeJSON = new ArrayList<>();
          JSONArray elements = (JSONArray) recordJson.get("productTypes");
          log.info("productType Size: " + elements.size());
          for (int i = 0; i < elements.size(); i++)
            {
              JSONObject element = (JSONObject) elements.get(i);
              if (element != null)
                {
                  JSONArray productChars = (JSONArray) element.get("catalogCharacteristics");
                  String objectid = (String) (element.get("productTypeID"));
                  GUIManagedObject guiManagedObject = (GUIManagedObject) productTypeService.getStoredProductType(objectid);
                  if (guiManagedObject != null)
                    {
                      Map<String, Object> ptJSON = new LinkedHashMap<>(); // to preserve order
                      String PtypeName = (String) guiManagedObject.getJSONRepresentation().get("display");
                      ptJSON.put("productType", PtypeName);
                      List<Map<String, Object>> characteristicsJSON = new ArrayList<>();
                      for (int j = 0; j < productChars.size(); j++)
                        {
                          HashMap<String, Object> characteristicJSON = new HashMap<>();
                          JSONObject productChar = (JSONObject) productChars.get(j);
                          if (productChar != null)
                            {
                              characteristicJSON.put((String) productChar.get("catalogCharacteristicName"), productChar.get("value"));
                              characteristicsJSON.add(characteristicJSON);
                            }
                        }
                      ptJSON.put("characteristics", characteristicsJSON);
                      productTypeJSON.add(ptJSON);
                    }
                }
            }
          productFields.put("productType", ReportUtils.formatJSON(productTypeJSON));
        }
        

        if (productFields != null)
          {
            // Arrays.sort(allFields);
            if (addHeaders)
              {
                String headers = "";
                for (String fields : productFields.keySet())
                  {
                    headers += fields + csvSeparator;
                  }
                headers = headers.substring(0, headers.length() - 1);
                writer.write(headers.getBytes());
                writer.write("\n".getBytes());
                log.info("product headers :", headers);
                addHeaders = false;
              }
            String line = ReportUtils.formatResult(productFields);
            writer.write(line.getBytes());
          }
      }
    catch (Exception ex)
      {
        log.info("Exception while processing a line : " + ex.getLocalizedMessage());
      }

    if (last)
      {
        log.info("Last offer record  inserted into csv");
        writeCompleted(writer);
      }
  }

  private void writeCompleted(ZipOutputStream writer) throws IOException, InterruptedException
  {
    log.info(" writeCompleted ");
    log.info("product Service {}", productService.toString());
    writer.flush();
    writer.closeEntry();
    writer.close();
    log.info("csv Writer closed");
    NGLMRuntime.addShutdownHook(new ShutdownHook(productService,supplierService,deliverableService,productTypeService));
    
  
  }
  private static class ShutdownHook implements NGLMRuntime.NGLMShutdownHook
  {

    private ProductService productService;
    private SupplierService supplierService;
    private DeliverableService deliverableService;
    private ProductTypeService productTypeService;

    public ShutdownHook(ProductService productService,SupplierService supplierService,DeliverableService deliverableService,ProductTypeService productTypeService)
    {
      
      this.productService=productService;
      this.supplierService=supplierService;
      this.deliverableService=deliverableService;
      this.productTypeService=productTypeService;
    }

    @Override
    public void shutdown(boolean normalShutdown)
    {
      
      if(productService!=null){productService.stop();}
      if(supplierService!=null){supplierService.stop();}
      if(deliverableService!=null){deliverableService.stop();}
      if(productTypeService!=null){productTypeService.stop();}
      
    }

   
  }
}
