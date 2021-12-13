/****************************************************************************
 *
 *  voucherCustomerReportMonoPhase.java 
 *
 ****************************************************************************/

package com.evolving.nglm.evolution.reports.voucherCustomer;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.zip.ZipOutputStream;

import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.evolving.nglm.core.AlternateID;
import com.evolving.nglm.core.Deployment;
import com.evolving.nglm.core.SystemTime;
import com.evolving.nglm.evolution.GUIManagedObject;
import com.evolving.nglm.evolution.VoucherService;
import com.evolving.nglm.evolution.Voucher;
import com.evolving.nglm.evolution.Supplier;
import com.evolving.nglm.evolution.SupplierService;
import com.evolving.nglm.evolution.VoucherType;
import com.evolving.nglm.evolution.VoucherTypeService;
import com.evolving.nglm.evolution.reports.ReportCsvFactory;
import com.evolving.nglm.evolution.reports.ReportMonoPhase;
import com.evolving.nglm.evolution.reports.ReportUtils;
import com.evolving.nglm.evolution.reports.ReportsCommonCode;
import com.evolving.nglm.evolution.reports.tokenOffer.TokenOfferReportMonoPhase;

public class VoucherCustomerReportMonoPhase implements ReportCsvFactory
{
  private static final Logger log = LoggerFactory.getLogger(VoucherCustomerReportMonoPhase.class);
  final private static String CSV_SEPARATOR = ReportUtils.getSeparator();

  private final static String subscriberID = "subscriberID";
  private final static String customerID = "customerID";

  private static final String voucherCode = "voucherCode";
  private static final String supplier = "supplier";
  private static final String deliveryDate = "deliveryDate";
  private static final String expiryDate = "expiryDate";
  private static final String voucherStatus = "voucherStatus";
  private static final String voucherType = "voucherType";
  
  static List<String> headerFieldsOrder = new ArrayList<String>();
  static
  {
    headerFieldsOrder.add(customerID);
    for (AlternateID alternateID : Deployment.getAlternateIDs().values())
    {
      headerFieldsOrder.add(alternateID.getName());
    }
    headerFieldsOrder.add(voucherCode);
    headerFieldsOrder.add(supplier);
    headerFieldsOrder.add(deliveryDate);
    headerFieldsOrder.add(expiryDate);
    headerFieldsOrder.add(voucherStatus);
    headerFieldsOrder.add(voucherType);
  }
  
  private VoucherService voucherService = null;
  private SupplierService supplierService = null;
  private VoucherTypeService voucherTypeService = null;
  private int tenantID = 0;


  /****************************************
   *
   * dumpElementToCsv
   *
   ****************************************/

 
  public boolean dumpElementToCsvMono(Map<String,Object> map, ZipOutputStream writer, boolean addHeaders) throws IOException
  {
    LinkedHashMap<String, Object> result = new LinkedHashMap<>();
    LinkedHashMap<String, Object> commonFields = new LinkedHashMap<>();
    Map<String, Object> subscriberFields = map;

    if (subscriberFields != null)
      {
        String subscriberID = Objects.toString(subscriberFields.get("subscriberID"));
        Date now = SystemTime.getCurrentTime();
        if (subscriberID != null)
          {
            if (subscriberFields.get("vouchers") != null)
              {
                Map<String, Object> voucherMapped = (Map<String, Object>)subscriberFields.get("vouchers");
                List<Map<String, Object>> vouchersArray = (List<Map<String, Object>>) voucherMapped.get("vouchers");
                if (!vouchersArray.isEmpty())
                  {
                    commonFields.put(customerID, subscriberID);

                    for (AlternateID alternateID : Deployment.getAlternateIDs().values())
                      {
                        if (subscriberFields.get(alternateID.getESField()) != null)
                          {
                            Object alternateId = subscriberFields.get(alternateID.getESField());
                            commonFields.put(alternateID.getName(), alternateId);
                          }
                        else
                          {
                            commonFields.put(alternateID.getName(), "");
                          }
                      }

                    for (int i = 0; i < vouchersArray.size(); i++)
                      {
                        result.clear();
                        result.putAll(commonFields);
                        Map<String, Object> voucher = (Map<String, Object>) vouchersArray.get(i);
                        if (voucher.containsKey(voucherCode) && voucher.get(voucherCode) != null)
                          {
                            result.put(voucherCode, voucher.get(voucherCode));
                          }
                        else
                          {
                            result.put(voucherCode, "");
                          }
                        String supplierID = null;
                        Voucher currentVoucher = null;
                        if (voucher.containsKey("voucherID") && voucher.get("voucherID") != null)
                          {
                            GUIManagedObject voucherObject = voucherService
                                .getStoredVoucher((String) voucher.get("voucherID"));
                            if (voucherObject != null && voucherObject instanceof Voucher)
                              {
                                currentVoucher = (Voucher) voucherObject;
                                if (currentVoucher != null)
                                  {
                                    supplierID = currentVoucher.getSupplierID();
                                  }
                              }
                          }
                        if (supplierID != null)
                          {
                            GUIManagedObject supplierObject = supplierService.getStoredSupplier(supplierID);

                            if (supplierObject != null && supplierObject instanceof Supplier)
                              {
                                result.put(supplier, ((Supplier) supplierObject).getGUIManagedObjectDisplay());
                              }
                            else
                              {
                                result.put(supplier, "");
                              }
                          }
                        else
                          {
                            result.put(supplier, "");
                          }
                        if (voucher.containsKey("voucherDeliveryDate") && voucher.get("voucherDeliveryDate") != null)
                          {
                            Object voucherDeliveryDateObj = voucher.get("voucherDeliveryDate");
                            if (voucherDeliveryDateObj instanceof String)
                              {
                                // TEMP fix for BLK : reformat date with correct template.
                                result.put(deliveryDate, ReportsCommonCode.parseDate((String) voucherDeliveryDateObj));
                                // END TEMP fix for BLK
                              }
                            else
                              {
                                log.info("voucherDeliveryDate" + " is of wrong type : " + voucherDeliveryDateObj.getClass().getName());
                                result.put(deliveryDate, "");
                              }
                          }
                        else
                          {
                            result.put(deliveryDate, "");
                          }
                        if (voucher.containsKey("voucherExpiryDate") && voucher.get("voucherExpiryDate") != null)
                          {
                            Object voucherExpiryDateObj = voucher.get("voucherExpiryDate");
                            if (voucherExpiryDateObj instanceof String)
                              {
                                // TEMP fix for BLK : reformat date with correct template.
                                result.put(expiryDate, ReportsCommonCode.parseDate((String) voucherExpiryDateObj));
                                // END TEMP fix for BLK
                              }
                            else
                              {
                                log.info("voucherExpiryDate" + " is of wrong type : " + voucherExpiryDateObj.getClass().getName());
                                result.put(expiryDate, "");
                              }
                          }
                        else
                          {
                            result.put(expiryDate, "");
                          }
                        if (voucher.containsKey(voucherStatus) && voucher.get(voucherStatus) != null)
                          {
                            result.put(voucherStatus, voucher.get(voucherStatus));
                          }
                        else
                          {
                            result.put(voucherStatus, "");
                          }
                        if (currentVoucher != null && currentVoucher instanceof Voucher)
                          {
                            GUIManagedObject voucherTypeObject = voucherTypeService
                                .getStoredVoucherType(currentVoucher.getVoucherTypeId());
                            if (voucherTypeObject != null && voucherTypeObject instanceof VoucherType)
                              {
                                result.put(voucherType,
                                    ((VoucherType) voucherTypeObject).getGUIManagedObjectDisplay());
                              }
                            else
                              {
                                result.put(voucherType, "");
                              }
                          }
                        else
                          {
                            result.put(voucherType, "");
                          }

       

                        if (addHeaders)
                          {
                            addHeaders(writer, result.keySet(), 1);
                            addHeaders = false;
                          }
                        String line = ReportUtils.formatResult(result);
                        if (log.isTraceEnabled()) log.trace("Writing to csv file : " + line);
                        writer.write(line.getBytes());
                      }
                  }
              }
            else {
              this.dumpHeaderToCsv(writer, addHeaders);
              addHeaders = false;
            }
          }
      }
    return addHeaders;
  }

  private String dateOrEmptyString(Object time)
  {
    return (time == null) ? "" : ReportsCommonCode.getDateString(new Date((long) time));
  }
  
  /****************************************
   *
   * addHeaders
   *
   ****************************************/

  private void addHeaders(ZipOutputStream writer, Set<String> headers, int offset) throws IOException
  {
    if (headers != null && !headers.isEmpty())
      {
        String header = "";
        for (String field : headers)
          {
            header += field + CSV_SEPARATOR;
          }
        header = header.substring(0, header.length() - offset);
        writer.write(header.getBytes());
        if (offset == 1)
          {
            writer.write("\n".getBytes());
          }
      }
  }
  

  /*************************************
   * 
   * Add headers for empty file   * 
   * 
   *****************************************/
 @Override public void dumpHeaderToCsv(ZipOutputStream writer, boolean addHeaders)
  {
    try
      {
        if (addHeaders)
          {
            Set<String> headers = new HashSet<String>(headerFieldsOrder);
            addHeaders(writer, headers, 1);
          }
      } 
    catch (IOException e)
      {
        e.printStackTrace();
      }
  }
  

  /****************************************
   *
   * main
   *
   ****************************************/
  public static void main(String[] args, final Date reportGenerationDate)
  {
    VoucherCustomerReportMonoPhase voucherCustomerReportMonoPhase = new VoucherCustomerReportMonoPhase();
    voucherCustomerReportMonoPhase.start(args, reportGenerationDate);
  }
  
  private void start(String[] args, final Date reportGenerationDate)
  {
    if(log.isInfoEnabled())
    log.info("received " + args.length + " args");
    for (String arg : args)
      {
        if(log.isInfoEnabled())
        log.info("VoucherCustomerESReader: arg " + arg);
      }

    if (args.length < 3) {
      if(log.isWarnEnabled())
      log.warn(
          "Usage : VoucherCustomerMonoPhase <ESNode> <ES customer index> <csvfile>");
      return;
    }
    String esNode          = args[0];
    String esIndexCustomer = args[1];
    String csvfile         = args[2];
    if (args.length > 3) tenantID = Integer.parseInt(args[3]);

    if(log.isInfoEnabled())
    log.info("Reading data from ES in "+esIndexCustomer+"  index and writing to "+csvfile+" file.");  
    ReportCsvFactory reportFactory = new VoucherCustomerReportMonoPhase();

    LinkedHashMap<String, QueryBuilder> esIndexWithQuery = new LinkedHashMap<String, QueryBuilder>();
    esIndexWithQuery.put(esIndexCustomer, QueryBuilders.boolQuery().filter(QueryBuilders.termQuery("tenantID", tenantID)));
      
    ReportMonoPhase reportMonoPhase = new ReportMonoPhase(
        esNode,
        esIndexWithQuery,
        this,
        csvfile
    );

    voucherService = new VoucherService(Deployment.getBrokerServers(), "report-voucherService-voucherCustomerReportMonoPhase", Deployment.getVoucherTopic());
    
    voucherTypeService = new VoucherTypeService(Deployment.getBrokerServers(), "report-voucherTypeService-voucherCustomerReportMonoPhase", Deployment.getVoucherTypeTopic(), false);
    
    supplierService = new SupplierService(Deployment.getBrokerServers(), "report-supplierService-voucherCustomerReportMonoPhase", Deployment.getSupplierTopic(), false);
   

    voucherService.start();
    voucherTypeService.start();
    supplierService.start();
    
    try {
      if (!reportMonoPhase.startOneToOne())
        {
          if(log.isWarnEnabled())
            log.warn("An error occured, the report might be corrupted");
          throw new RuntimeException("An error occurred, report must be restarted");
        }
    } finally {
      voucherService.stop();
      voucherTypeService.stop();
      supplierService.stop();
    }    
  }

}
