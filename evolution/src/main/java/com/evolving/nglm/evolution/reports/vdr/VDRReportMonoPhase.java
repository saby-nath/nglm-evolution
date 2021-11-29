package com.evolving.nglm.evolution.reports.vdr;

import com.evolving.nglm.core.AlternateID;
import com.evolving.nglm.core.Deployment;
import com.evolving.nglm.core.RLMDateUtils;
import com.evolving.nglm.evolution.DeliveryRequest;
import com.evolving.nglm.evolution.JourneyService;
import com.evolving.nglm.evolution.LoyaltyProgramService;
import com.evolving.nglm.evolution.OfferService;
import com.evolving.nglm.evolution.ProductService;
import com.evolving.nglm.evolution.SalesChannelService;
import com.evolving.nglm.evolution.Supplier;
import com.evolving.nglm.evolution.SupplierService;
import com.evolving.nglm.evolution.VoucherService;
import com.evolving.nglm.evolution.VoucherType;
import com.evolving.nglm.evolution.Voucher;
import com.evolving.nglm.evolution.VoucherTypeService;
import com.evolving.nglm.evolution.DeliveryRequest.Module;
import com.evolving.nglm.evolution.reports.ReportCsvFactory;
import com.evolving.nglm.evolution.reports.ReportMonoPhase;
import com.evolving.nglm.evolution.reports.ReportMonoPhase.PERIOD;
import com.evolving.nglm.evolution.reports.ReportUtils;
import com.evolving.nglm.evolution.reports.ReportsCommonCode;
import com.evolving.nglm.evolution.reports.bdr.BDRReportMonoPhase;

import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;
import java.util.Map.Entry;
import java.util.zip.ZipOutputStream;

public class VDRReportMonoPhase implements ReportCsvFactory
{
  private static final Logger log = LoggerFactory.getLogger(VDRReportMonoPhase.class);
  private static final DateFormat DATE_FORMAT;

  private static final String CSV_SEPARATOR = ReportUtils.getSeparator();
  private JourneyService journeyService;
  private OfferService offerService;
  private SalesChannelService salesChannelService;
  private LoyaltyProgramService loyaltyProgramService;
  private ProductService productService;
  private SupplierService supplierService;
  private VoucherTypeService voucherTypeService;
  private VoucherService voucherService;
  private int tenantID = 0;

  private final static String moduleId = "moduleID";
  private final static String featureId = "featureID";
  private final static String moduleName = "moduleName";
  private final static String featureName = "featureName";
  private final static String subscriberID = "subscriberID";
  private final static String customerID = "customerID";
  private static final String eventDatetime = "eventDatetime";
  private static final String eventID = "eventID";
  private static final String origin = "origin";
  private static final String voucherCode = "voucherCode";
  private static final String supplier = "supplier";
  private static final String expiryDate = "expiryDate";
  private static final String operation = "operation";
  private static final String voucherType = "voucherType";
  private static final String returnCode = "returnCode";
  private static final String returnCodeDetails = "returnCodeDetails";
  private static final String deliveryRequestID = "deliveryRequestID";

  static List<String> headerFieldsOrder = new LinkedList<String>();
  static
    {
      DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd");    // TODO EVPRO-99
      DATE_FORMAT.setTimeZone(TimeZone.getTimeZone(Deployment.getDefault().getTimeZone())); // TODO EVPRO-99 use systemTimeZone instead of baseTimeZone, is it correct or should it be per tenant ???
      
      headerFieldsOrder.add(customerID);
      for (AlternateID alternateID : Deployment.getAlternateIDs().values())
        {
          headerFieldsOrder.add(alternateID.getName());
        }
      headerFieldsOrder.add(eventID);
      headerFieldsOrder.add(eventDatetime);
      headerFieldsOrder.add(voucherCode);      
      headerFieldsOrder.add(supplier);
      headerFieldsOrder.add(expiryDate);
      headerFieldsOrder.add(operation);
      headerFieldsOrder.add(voucherType);
      headerFieldsOrder.add(returnCode);
      headerFieldsOrder.add(returnCodeDetails);
      headerFieldsOrder.add(moduleName);
      headerFieldsOrder.add(featureName);
      headerFieldsOrder.add(origin);
      headerFieldsOrder.add(deliveryRequestID);
    }

  @Override
  public void dumpLineToCsv(Map<String, Object> lineMap, ZipOutputStream writer, boolean addHeaders)
  {
    try
      {
        if (addHeaders)
          {
            addHeaders(writer, headerFieldsOrder, 1);
          }
        String line = ReportUtils.formatResult(headerFieldsOrder, lineMap);
        if (log.isTraceEnabled()) log.trace("Writing to csv file : " + line);
        writer.write(line.getBytes());
      }
    catch (IOException e)
      {
        e.printStackTrace();
      }
  }

  /****************************************
  *
  * dumpElementToCsv
  *
  ****************************************/
 public boolean dumpElementToCsvMono(Map<String,Object> map, ZipOutputStream writer, boolean addHeaders) throws IOException
 {
   Map<String, List<Map<String, Object>>> mapLocal = getSplittedReportElementsForFileMono(map);  
   if(mapLocal.size() != 1) {
     log.debug("We have multiple dates in the same index " + mapLocal.size());
   } else {
     if(mapLocal.values().size() != 1) {
       log.debug("We have multiple values for this date " + mapLocal.values().size());
     }
     else {
       Set<Entry<String, List<Map<String, Object>>>> setLocal = mapLocal.entrySet();
       if(setLocal.size() != 1) {
         log.debug("We have multiple dates in this report " + setLocal.size());
       } else {
         for (Entry<String, List<Map<String, Object>>> entry : setLocal) {
           List<Map<String, Object>> list = entry.getValue();

           if(list.size() != 1) {
             log.debug("We have multiple reports in this folder " + list.size());
           } else {
             Map<String, Object> reportMap = list.get(0);
             dumpLineToCsv(reportMap, writer, addHeaders);
             return false;
           }
         }
       }
     }
   }
   return true;
 }

 
  public Map<String, List<Map<String, Object>>> getSplittedReportElementsForFileMono(Map<String, Object> map)
  {
    Map<String, List<Map<String, Object>>> result = new LinkedHashMap<String, List<Map<String, Object>>>();
    Map<String, Object> VDRFields = map;
    LinkedHashMap<String, Object> vdrRecs = new LinkedHashMap<>();
    if (VDRFields != null && !VDRFields.isEmpty())
      {
        if (VDRFields.get(subscriberID) != null)
          {
            Object subscriberIDField = VDRFields.get(subscriberID);
            vdrRecs.put(customerID, subscriberIDField);
          }
        for (AlternateID alternateID : Deployment.getAlternateIDs().values())
          {
            if (VDRFields.get(alternateID.getID()) != null)
              {
                Object alternateId = VDRFields.get(alternateID.getID());
                vdrRecs.put(alternateID.getName(), alternateId);
              }
            else
            {
              vdrRecs.put(alternateID.getName(), "");
            }
          }
        if (VDRFields.containsKey(eventID) && VDRFields.get(eventID) != null)
          {
            vdrRecs.put(eventID, VDRFields.get(eventID));
          }
        else
          {
            vdrRecs.put(eventID, "");
          }

        if (VDRFields.containsKey(eventDatetime) && VDRFields.get(eventDatetime) != null)
          {
            Object eventDatetimeObj = VDRFields.get(eventDatetime);
            if (eventDatetimeObj instanceof String)
              {

                // TEMP fix for BLK : reformat date with correct template.

                vdrRecs.put(eventDatetime, ReportsCommonCode.parseDate((String) eventDatetimeObj));

                // END TEMP fix for BLK

              }
            else
              {
                log.info(eventDatetime + " is of wrong type : " + eventDatetimeObj.getClass().getName());
              }
          }

        else
          {
            vdrRecs.put(eventDatetime, "");
          }

        // Compute featureName and ModuleName from ID
        if (VDRFields.containsKey(moduleId) && VDRFields.get(moduleId) != null)
          {
            Module module = Module.fromExternalRepresentation(String.valueOf(VDRFields.get(moduleId)));
            vdrRecs.put(moduleName, module.toString());
          }
        else
          {
            vdrRecs.put(moduleName, "");
          }
        if (VDRFields.containsKey(moduleId) && VDRFields.get(moduleId) != null && VDRFields.containsKey(featureId)
            && VDRFields.get(featureId) != null)
          {
            Module module = Module.fromExternalRepresentation(String.valueOf(VDRFields.get(moduleId)));
            String feature = DeliveryRequest.getFeatureDisplay(module,
                String.valueOf(VDRFields.get(featureId).toString()), journeyService, offerService,
                loyaltyProgramService);
            vdrRecs.put(featureName, feature);
          }
        else
          {
            vdrRecs.put(featureName, "");
          }

        if (VDRFields.containsKey(origin) && VDRFields.get(origin) != null)
          {
            vdrRecs.put(origin, VDRFields.get(origin));
          }
        else
          {
            vdrRecs.put(origin, "");
          }
        // get salesChannel display

        if (VDRFields.containsKey(voucherCode) && VDRFields.get(voucherCode) != null)
          {
            vdrRecs.put(voucherCode, VDRFields.get(voucherCode));
          }
        else
          {
            vdrRecs.put(voucherCode, "");
          }
        if (VDRFields.containsKey(expiryDate) && VDRFields.get(expiryDate) != null)
          {
            Object expiryDateObj = VDRFields.get(expiryDate);
            if (expiryDateObj != null && expiryDateObj instanceof String)
              {
                vdrRecs.put(expiryDate, ReportsCommonCode.parseDate((String) expiryDateObj));

              }
            else
              {
                log.info(expiryDate + " is of wrong type : " + expiryDateObj.getClass().getName());
              }
          }
        else
          {
            vdrRecs.put(expiryDate, "");
          }

        if (VDRFields.containsKey("action") && VDRFields.get("action") != null)
          {
            vdrRecs.put(operation, VDRFields.get("action"));
          }
        else
          {
            vdrRecs.put(operation, "");
          }
        String voucherID = null;
        if (VDRFields.containsKey("voucherID") && VDRFields.get("voucherID") != null)
          {
            voucherID = VDRFields.get("voucherID").toString();
          }
        Voucher voucher = null;
        String voucherTypeID = null;
        if (voucherID != null)
          {
            voucher = (Voucher) voucherService.getStoredVoucher(voucherID);
          }
        String supplierID = null;
        if (voucher != null && voucher instanceof Voucher)
          {
            supplierID = voucher.getSupplierID();
            voucherTypeID = voucher.getVoucherTypeId();
          }

        if (supplierID != null && !(supplierID.isEmpty()))
          {
            Supplier currentSupplier = (Supplier) (supplierService.getStoredSupplier(supplierID));
            if (currentSupplier != null && currentSupplier instanceof Supplier)
              {
                vdrRecs.put(supplier, currentSupplier.getGUIManagedObjectDisplay());
              }
            else
              {
                vdrRecs.put(supplier, "");
              }
          }
        else
          {
            vdrRecs.put(supplier, "");
          }
        if (voucherTypeID != null && !(voucherTypeID.isEmpty()))
          {
            VoucherType currentVoucherType = (VoucherType) (voucherTypeService.getStoredVoucherType(voucherTypeID));
            if (currentVoucherType != null && currentVoucherType instanceof VoucherType)
              {
                vdrRecs.put(voucherType, currentVoucherType.getGUIManagedObjectDisplay());
              }
            else
              {
                vdrRecs.put(voucherType, "");
              }
          }
        else
          {
            vdrRecs.put(voucherType, "");
          }

        if (VDRFields.containsKey(returnCode) && VDRFields.get(returnCode) != null)
          {
            Object code = VDRFields.get(returnCode);
            vdrRecs.put(returnCode, code);

          }
        else
          {
            vdrRecs.put(returnCode, "");
          }
        if (VDRFields.containsKey(returnCodeDetails) && VDRFields.get(returnCodeDetails) != null)
          {

            vdrRecs.put(returnCodeDetails, VDRFields.get(returnCodeDetails));
          }
        else
          {
            vdrRecs.put(returnCodeDetails, "");
          }
        if (VDRFields.containsKey(deliveryRequestID) && VDRFields.get(deliveryRequestID) != null)
          {
            vdrRecs.put(deliveryRequestID, VDRFields.get(deliveryRequestID));
          }
        else
          {
            vdrRecs.put(deliveryRequestID, "");
          }

        //
        // result
        //

        String rawEventDateTime = vdrRecs.get(eventDatetime) == null ? null : vdrRecs.get(eventDatetime).toString();
        if (rawEventDateTime == null)
          log.warn("bad EventDateTime -- report will be generated in 'null' file name -- for record {} ", VDRFields);
        String evntDate = getEventDate(rawEventDateTime);
        if (result.containsKey(evntDate))
          {
            result.get(evntDate).add(vdrRecs);
          }
        else
          {
            List<Map<String, Object>> elements = new ArrayList<Map<String, Object>>();
            elements.add(vdrRecs);
            result.put(evntDate, elements);
          }
      }
    return result;
  }

  private String getEventDate(String rawEventDateTime)
  {
    String result = "null";
    if (rawEventDateTime == null || rawEventDateTime.trim().isEmpty())
      return result;
    String eventDateTimeFormat = "yyyy-MM-dd";
    result = rawEventDateTime.substring(0, eventDateTimeFormat.length());
    return result;
  }

  private void addHeaders(ZipOutputStream writer, List<String> headers, int offset) throws IOException
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

  public static void main(String[] args, final Date reportGenerationDate)
  {
    VDRReportMonoPhase vdrReportMonoPhase = new VDRReportMonoPhase();
    vdrReportMonoPhase.start(args, reportGenerationDate);
  }
  
  private void start(String[] args, final Date reportGenerationDate)
  {
    if (log.isInfoEnabled())
      log.info("received " + args.length + " args");
    for (String arg : args)
      {
        if (log.isInfoEnabled())
          log.info("VDRReportMonoPhase: arg " + arg);
      }

    if (args.length < 3)
      {
        if (log.isWarnEnabled())
          log.warn(
              "Usage : VDRReportMonoPhase <ESNode> <ES journey index> <csvfile> <defaultReportPeriodQuantity> <defaultReportPeriodUnit>");
        return;
      }
    String esNode = args[0];
    String esIndexVDR = args[1];
    String csvfile = args[2];

    Integer reportPeriodQuantity = 0;
    String reportPeriodUnit = null;
    if (args.length > 4 && args[3] != null && args[4] != null)
      {
        reportPeriodQuantity = Integer.parseInt(args[3]);
        reportPeriodUnit = args[4];
      }
    if (args.length > 5) tenantID = Integer.parseInt(args[5]);

    Date fromDate = getFromDate(reportGenerationDate, reportPeriodUnit, reportPeriodQuantity);
    Date toDate = reportGenerationDate;

    Set<String> esIndexWeeks = getEsIndexWeeks(fromDate, toDate);
    StringBuilder esIndexVDRList = new StringBuilder();
    boolean firstEntry = true;
    for (String esIndexWk : esIndexWeeks)
      {
        if (!firstEntry) esIndexVDRList.append(",");
        String indexName = esIndexVDR + esIndexWk;
        esIndexVDRList.append(indexName);
        firstEntry = false;
      }
    ReportCsvFactory reportFactory = new VDRReportMonoPhase();
    if (log.isInfoEnabled()) log.info("Reading data from ES in (" + esIndexVDRList.toString() + ")  index and writing to " + csvfile);
    LinkedHashMap<String, QueryBuilder> esIndexWithQuery = new LinkedHashMap<String, QueryBuilder>();
    esIndexWithQuery.put(esIndexVDRList.toString(), 
        QueryBuilders.boolQuery()
        .filter(QueryBuilders.termQuery("tenantID", tenantID))
        .filter(QueryBuilders.rangeQuery("eventDatetime").gte(RLMDateUtils.formatDateForElasticsearchDefault(fromDate)).lte(RLMDateUtils.formatDateForElasticsearchDefault(toDate))));

    String journeyTopic = Deployment.getJourneyTopic();
    String offerTopic = Deployment.getOfferTopic();
    String salesChannelTopic = Deployment.getSalesChannelTopic();
    String loyaltyProgramTopic = Deployment.getLoyaltyProgramTopic();
    String productTopic = Deployment.getProductTopic();
    String supplierTopic = Deployment.getSupplierTopic();
    String voucherTypeTopic = Deployment.getVoucherTypeTopic();

    salesChannelService = new SalesChannelService(Deployment.getBrokerServers(),
        "vdrreportcsvwriter-saleschannelservice-VDRReportMonoPhase", salesChannelTopic, false);
    
    offerService = new OfferService(Deployment.getBrokerServers(), "vdrreportcsvwriter-offerservice-VDRReportMonoPhase",
        offerTopic, false);
    
    journeyService = new JourneyService(Deployment.getBrokerServers(),
        "vdrreportcsvwriter-journeyservice-VDRReportMonoPhase", journeyTopic, false);
    
    loyaltyProgramService = new LoyaltyProgramService(Deployment.getBrokerServers(),
        "vdrreportcsvwriter-loyaltyprogramservice-VDRReportMonoPhase", loyaltyProgramTopic, false);
    
    
    productService = new ProductService(Deployment.getBrokerServers(),
        "vdrreportcsvwriter-productService-VDRReportMonoPhase", productTopic, false);
    
    supplierService = new SupplierService(Deployment.getBrokerServers(),
        "vdrreportcsvwriter-productService-VDRReportMonoPhase", supplierTopic, false);
   
    voucherTypeService = new VoucherTypeService(Deployment.getBrokerServers(),
        "vdrreportcsvwriter-productService-VDRReportMonoPhase", voucherTypeTopic, false);
    

    voucherService = new VoucherService(Deployment.getBrokerServers(),
        "report-voucherService-voucherCustomerReportMonoPhase", Deployment.getVoucherTopic());
    

    salesChannelService.start();
    offerService.start();
    journeyService.start();
    loyaltyProgramService.start();
    productService.start();
    supplierService.start();
    voucherTypeService.start();
    voucherService.start();
    
    try {
      ReportMonoPhase reportMonoPhase = new ReportMonoPhase(
          esNode,
          esIndexWithQuery,
          this,
          csvfile
          );

      if (!reportMonoPhase.startOneToOne(false))
        {
          if (log.isWarnEnabled()) log.warn("An error occured, the report might be corrupted");
          throw new RuntimeException("An error occurred, report must be restarted");
        }
    } finally {

      salesChannelService.stop();
      offerService.stop();
      journeyService.stop();
      loyaltyProgramService.stop();
      productService.stop();
      supplierService.stop();
      voucherTypeService.stop();
      voucherService.stop();

      if (log.isInfoEnabled()) log.info("Finished VDRReport");
    }
  }

  private static List<String> getEsIndexDates(final Date fromDate, Date toDate)
  {
    Date tempfromDate = fromDate;
    List<String> esIndexVDRList = new ArrayList<String>();
    while (tempfromDate.getTime() <= toDate.getTime())
      {
        esIndexVDRList.add(DATE_FORMAT.format(tempfromDate));
        tempfromDate = RLMDateUtils.addDays(tempfromDate, 1, Deployment.getDefault().getTimeZone()); // TODO EVPRO-99 use systemTimeZone instead of baseTimeZone, is it correct or should it be per tenant ???
      }
    return esIndexVDRList;
  }
  
  /*******************************
   * 
   * will return in yyyy-ww format
   * 
   *******************************/
  
  public static Set<String> getEsIndexWeeks(final Date fromDate, Date toDate)
  {
    Date tempfromDate = fromDate;
    Set<String> esIndexList = new HashSet<String>();
    while(tempfromDate.getTime() <= toDate.getTime())
      {
        esIndexList.add(RLMDateUtils.formatDateISOWeek(tempfromDate, Deployment.getDefault().getTimeZone())); // potential error, missing the true timezone
        tempfromDate = RLMDateUtils.addDays(tempfromDate, 1, Deployment.getDefault().getTimeZone());
      }
    return esIndexList;
  }

  private static Date getFromDate(final Date reportGenerationDate, String reportPeriodUnit, Integer reportPeriodQuantity)
  {
    reportPeriodQuantity = reportPeriodQuantity == null || reportPeriodQuantity == 0 ? new Integer(1) : reportPeriodQuantity;
    if (reportPeriodUnit == null) reportPeriodUnit = PERIOD.DAYS.getExternalRepresentation();

    //
    //
    //

    Date now = reportGenerationDate;
    Date fromDate = null;
    switch (reportPeriodUnit.toUpperCase())
    {
      case "DAYS":
        fromDate = RLMDateUtils.addDays(now, -reportPeriodQuantity, Deployment.getDefault().getTimeZone());
        break;

      case "WEEKS":
        fromDate = RLMDateUtils.addWeeks(now, -reportPeriodQuantity, Deployment.getDefault().getTimeZone());
        break;

      case "MONTHS":
        fromDate = RLMDateUtils.addMonths(now, -reportPeriodQuantity, Deployment.getDefault().getTimeZone());
        break;

      default:
        break;
    }
    if (fromDate != null) fromDate = RLMDateUtils.truncate(fromDate, Calendar.DATE, Deployment.getDefault().getTimeZone());
    return fromDate;
  }
}
