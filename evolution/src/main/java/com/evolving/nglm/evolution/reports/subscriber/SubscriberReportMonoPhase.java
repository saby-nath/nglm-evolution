/****************************************************************************
 *
 *  SubscriberReportMonoPhase.java 
 *
 ****************************************************************************/

package com.evolving.nglm.evolution.reports.subscriber;

import java.io.IOException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;
import java.util.zip.ZipOutputStream;

import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.evolving.nglm.core.AlternateID;
import com.evolving.nglm.core.Deployment;
import com.evolving.nglm.evolution.CriterionField;
import com.evolving.nglm.evolution.GUIManagedObject;
import com.evolving.nglm.evolution.Segment;
import com.evolving.nglm.evolution.SegmentationDimension;
import com.evolving.nglm.evolution.SegmentationDimensionService;
import com.evolving.nglm.evolution.reports.ReportCsvFactory;
import com.evolving.nglm.evolution.reports.ReportMonoPhase;
import com.evolving.nglm.evolution.reports.ReportUtils;
import com.evolving.nglm.evolution.reports.ReportsCommonCode;
import com.evolving.nglm.evolution.tenancy.Tenant;

public class SubscriberReportMonoPhase implements ReportCsvFactory {

  private static final Logger log = LoggerFactory.getLogger(SubscriberReportMonoPhase.class);
  final private static String CSV_SEPARATOR = ReportUtils.getSeparator();
  private static SegmentationDimensionService segmentationDimensionService = null;
  private final static String subscriberID = "subscriberID";
  private final static String segments = "segments";
  private final static String evolutionSubscriberStatusChangeDate = "evolutionSubscriberStatusChangeDate";
  private static Map<Integer, Map<String, String[]>> segmentsNamesPerTenant = new HashMap<>(); // TenantID -> [segmentID -> [dimensionName, segmentName]]
  private final static int INDEX_DIMENSION_NAME = 0;
  private final static int INDEX_SEGMENT_NAME = 1;
  private Map<Integer, Map<String, String>> allDimensionsMapPerTenant = new HashMap<>();
  private List<String> allProfileFields = new ArrayList<>();
  public static final ThreadLocal<SimpleDateFormat> parseSDF1_ = ThreadLocal.withInitial(   // TODO EVPRO-99
      () -> {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSX");
        sdf.setTimeZone(TimeZone.getTimeZone(Deployment.getDefault().getTimeZone())); // TODO EVPRO-99 use systemTimeZone instead of baseTimeZone, is it correct or should it be per tenant ???
        return sdf;
      });
  
  public static final ThreadLocal<SimpleDateFormat> parseSDF2_ = ThreadLocal.withInitial(   // TODO EVPRO-99
      () -> {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSSXX");
        sdf.setTimeZone(TimeZone.getTimeZone(Deployment.getDefault().getTimeZone())); // TODO EVPRO-99 use systemTimeZone instead of baseTimeZone, is it correct or should it be per tenant ???
        return sdf;
      });
  
  
  
  private Map<Integer, Map<String, String>> dimNameDisplayMappingPerTenant = new HashMap<>();
  private int tenantID = 0;

  private static final String customerID = "customerID";
  private static final String activationDate = "activationDate";
  private static final String relationships = "relationships";
  
  static List<String> headerFieldsOrder = new ArrayList<String>();
  static
  {
    headerFieldsOrder.add(customerID);
    for (AlternateID alternateID : Deployment.getAlternateIDs().values())
    {
      headerFieldsOrder.add(alternateID.getName());
    }
    headerFieldsOrder.add(activationDate);
    headerFieldsOrder.add(relationships);
    headerFieldsOrder.add(evolutionSubscriberStatusChangeDate);
  }
  
  /****************************************
  *
  *  dumpElementToCsv
  *
  ****************************************/
  public boolean dumpElementToCsvMono(Map<String,Object> map, ZipOutputStream writer, boolean addHeaders) throws IOException
  {
    final int tenantID;
    LinkedHashMap<String, Object> result = new LinkedHashMap<>();
    Map<String, Object> elasticFields = map;
    if (elasticFields != null)
    {
      if (elasticFields.get("tenantID") != null)
        tenantID = (Integer) elasticFields.get("tenantID");
      else
        tenantID = 0;
      if (elasticFields.get(subscriberID) != null)
      {
        result.put(customerID, elasticFields.get(subscriberID));
        for (AlternateID alternateID : Deployment.getAlternateIDs().values())
        {
          Object alternateId = elasticFields.get(alternateID.getESField());
          result.put(alternateID.getName(), alternateId);
        }
      } 
      if (elasticFields.containsKey("activationDate") && elasticFields.get("activationDate") != null)
      {
        Object activationDateObj = elasticFields.get("activationDate");
        if (activationDateObj instanceof String)
        {
          String activationDateStr = (String) activationDateObj;
          // TEMP fix for BLK : reformat date with correct template.
          // current format comes from ES and is : 2020-04-20T09:51:38.953Z
          try
          {
            Date date = parseSDF1_.get().parse(activationDateStr);
            // replace with new value
            result.put(activationDate, ReportsCommonCode.getDateString(date)); 
          }
          catch (ParseException e1)
          {
            // Could also be 2019-11-27 15:39:30.276+0100
            try
            {
              Date date =  parseSDF2_.get().parse(activationDateStr);
              // replace with new value
              result.put(activationDate, ReportsCommonCode.getDateString(date));
            }
            catch (ParseException e2)
            {
              log.info("Unable to parse " + activationDateStr);
            }
          }
        }
        else
        {
          log.info("activationDate is of wrong type : " + activationDateObj.getClass().getName());
        }
      }
      else
      {
        result.put(activationDate, "");
      }
      for (String field : allProfileFields)
      {
        if (!field.equals(segments))
        {
          result.put(field, elasticFields.get(field));
        }
        else if (field.equals(evolutionSubscriberStatusChangeDate))
        {

          // TEMP fix for BLK : reformat date with correct template.

          result.put(evolutionSubscriberStatusChangeDate, ReportsCommonCode.parseDate((String) elasticFields.get(evolutionSubscriberStatusChangeDate)));

          // END TEMP fix for BLK
        }
      }
      if (elasticFields.containsKey("relationships"))
      {
        if (elasticFields.get("relationships") != null)
        {
          Object relationshipObject = elasticFields.get("relationships");
          result.put(relationships, relationshipObject);
        }
        else
        {
          result.put(relationships, "");
        }
      }
      else
      {
        result.put(relationships, "");
      }
      result.putAll(allDimensionsMapPerTenant.get(tenantID)); // all dimensions have empty segments
      for (String field : allProfileFields)
      {
        if (elasticFields.containsKey(segments))
        {
          String s = "" + elasticFields.get(segments);
          String removeBrackets = s.substring(1, s.length() - 1); // "[ seg1, seg2, ...]"
          String segmentIDs[] = removeBrackets.split(",");
          Arrays.stream(segmentIDs).forEach(
              segmentID -> {
                String[] couple = segmentsNamesPerTenant.get(tenantID).get(segmentID.trim());
                if (couple != null)
                {
                  String dimName = couple[INDEX_DIMENSION_NAME];
                  String dimDisplay = dimNameDisplayMappingPerTenant.get(tenantID).get(dimName);
                  if (dimDisplay == null || dimDisplay.isEmpty()) dimDisplay = dimName;
                  result.put(dimDisplay, couple[INDEX_SEGMENT_NAME]);
                }
                else
                {
                  log.trace("Unknown segment ID : " + segmentID);
                }
              });
        }
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
    return addHeaders;
  }
  
  /****************************************
  *
  *  addHeaders
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
  *  initSegmentationData
  *
  ****************************************/
  
  private void initSegmentationData(int tenantID)
  {
    // segmentID -> [dimensionName, segmentName]
    for (GUIManagedObject dimension : segmentationDimensionService.getStoredSegmentationDimensions(tenantID))
      {
        if (dimension instanceof SegmentationDimension)
          {
            SegmentationDimension segmentation = (SegmentationDimension) dimension;
            //allDimensions.add(segmentation.getSegmentationDimensionName());
            if(allDimensionsMapPerTenant.get(tenantID) == null) { allDimensionsMapPerTenant.put(tenantID, new HashMap<>()); }
            allDimensionsMapPerTenant.get(tenantID).put(segmentation.getGUIManagedObjectDisplay(), "");
            
            if(dimNameDisplayMappingPerTenant.get(tenantID) == null) { dimNameDisplayMappingPerTenant.put(tenantID, new HashMap<>()); }
            dimNameDisplayMappingPerTenant.get(tenantID).put(segmentation.getSegmentationDimensionName(), dimension.getGUIManagedObjectDisplay());
            if (segmentation.getSegments() != null)
              {
                for (Segment segment : segmentation.getSegments())
                  {
                    String[] segmentInfo = new String[2];
                    segmentInfo[INDEX_DIMENSION_NAME] = segmentation.getSegmentationDimensionName();
                    segmentInfo[INDEX_SEGMENT_NAME] = segment.getName();
                    if(segmentsNamesPerTenant.get(tenantID) == null) { segmentsNamesPerTenant.put(tenantID, new HashMap<>()); }
                    segmentsNamesPerTenant.get(tenantID).put(segment.getID(), segmentInfo);
                  }
              }
          }
      }
  }

	public static void main(String[] args, final Date reportGenerationDate)
	{
    SubscriberReportMonoPhase subscriberReportMonoPhase = new SubscriberReportMonoPhase();
    subscriberReportMonoPhase.start(args, reportGenerationDate);
  }
  
  private void start(String[] args, final Date reportGenerationDate)
  {
	  log.info("** received " + args.length + " args");
	  for(String arg : args){
	    log.info("SubscriberReportMonoPhase: arg " + arg);
	  }

	  if (args.length < 4) {
	    log.warn(
	        "Usage : SubscriberReportMonoPhase <KafkaNodeList> <ESNode> <ES customer index> <csvfile>");
	    return;
	  }
	  String kafkaNodeList   = args[0];
	  String esNode          = args[1];
	  String esIndexCustomer = args[2];
    String csvfile         = args[3];
    if (args.length > 4) tenantID = Integer.parseInt(args[4]);

	  log.info("Reading data from ES in "+esIndexCustomer+"  index and writing to "+csvfile+" file.");	

    LinkedHashMap<String, QueryBuilder> esIndexWithQuery = new LinkedHashMap<String, QueryBuilder>();
    esIndexWithQuery.put(esIndexCustomer, QueryBuilders.boolQuery().filter(QueryBuilders.termQuery("tenantID", tenantID)));

    ReportMonoPhase reportMonoPhase = new ReportMonoPhase(
        esNode,
        esIndexWithQuery,
        this,
        csvfile
        );
    synchronized (log) // why not, this is a static object that always exists
    {
      if (segmentationDimensionService == null) // do it only once, because we can't stop it fully
        {
          segmentationDimensionService = new SegmentationDimensionService(kafkaNodeList, "report-segmentationDimensionservice-subscriberReportMonoPhase", Deployment.getSegmentationDimensionTopic(), false);
          segmentationDimensionService.start(); // never stop it
        }
    }
      
    try {
      //
      // build map of segmentID -> [dimensionName, segmentName] once for all
      //

      synchronized (allDimensionsMapPerTenant)
      {
        for(Tenant tenant : Deployment.getTenants())
          {
            int tenantID = tenant.getTenantID();
            if(allDimensionsMapPerTenant.get(tenantID) == null) {  allDimensionsMapPerTenant.put(tenantID, new HashMap<>()); }
            allDimensionsMapPerTenant.get(tenantID).clear();
            
            if(segmentsNamesPerTenant.get(tenantID) == null) {  segmentsNamesPerTenant.put(tenantID, new HashMap<>()); }
            segmentsNamesPerTenant.get(tenantID).clear();

            if(dimNameDisplayMappingPerTenant.get(tenantID) == null) {  dimNameDisplayMappingPerTenant.put(tenantID, new HashMap<>()); }
            dimNameDisplayMappingPerTenant.get(tenantID).clear();
            
            initSegmentationData(tenantID);
          }

      }

      synchronized (allProfileFields)
      {
        allProfileFields.clear();
        initProfileFields();
      }

      if (allProfileFields.isEmpty())
        {
          log.warn("Cannot find any profile field in configuration, no report produced");
          return;
        }

      if (!reportMonoPhase.startOneToOne())
        {
          log.warn("An error occured, the report might be corrupted");
          throw new RuntimeException("An error occurred, report must be restarted");
        }
    } finally {
      log.info("Finished SubscriberReportESReader");
    }
  }
	
  private void initProfileFields()
  {
    for (CriterionField field : Deployment.getBaseProfileCriterionFields().values())
      {
        if (field.getESField() != null)
          {
            allProfileFields.add(field.getESField());
          }
      }
  }
}
