package com.evolving.nglm.evolution.reports.journeycustomerstatistics;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipOutputStream;

import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.evolving.nglm.core.AlternateID;
import com.evolving.nglm.core.Deployment;
import com.evolving.nglm.core.DeploymentCommon;
import com.evolving.nglm.core.SystemTime;
import com.evolving.nglm.evolution.GUIManagedObject;
import com.evolving.nglm.evolution.Journey;
import com.evolving.nglm.evolution.Journey.SubscriberJourneyStatus;
import com.evolving.nglm.evolution.JourneyMetricDeclaration;
import com.evolving.nglm.evolution.JourneyService;
import com.evolving.nglm.evolution.reports.ReportCsvFactory;
import com.evolving.nglm.evolution.reports.ReportMonoPhase;
import com.evolving.nglm.evolution.reports.ReportUtils;
import com.evolving.nglm.evolution.reports.ReportsCommonCode;

public class JourneyCustomerStatisticsReportMultithread implements ReportCsvFactory
{
  private static final Logger log = LoggerFactory.getLogger(JourneyCustomerStatisticsReportMultithread.class);
  private static final String CSV_SEPARATOR = ReportUtils.getSeparator();
  private JourneyService journeyService;
  private final String subscriberID = "subscriberID";
  private final static String customerID = "customerID";
  
  private static final String journeyID = "journeyID";
  private static final String journeyName = "journeyName";
  private static final String journeyType = "journeyType";
  private static final String customerState = "customerState";
  private static final String customerStatus = "customerStatus";
  private static final String sample = "sample";
  private static final String dateTime = "dateTime";
  private static final String startDate = "startDate";
  private static final String endDate = "endDate";
  
  
  
  static List<String> headerFieldsOrder = new ArrayList<String>();
  static
  {
    headerFieldsOrder.add(customerID);
    for (AlternateID alternateID : Deployment.getAlternateIDs().values())
    {
      headerFieldsOrder.add(alternateID.getName());
    }
    headerFieldsOrder.add(journeyID);
    headerFieldsOrder.add(journeyName);
    headerFieldsOrder.add(journeyType);
    headerFieldsOrder.add(customerState);
    headerFieldsOrder.add(customerStatus);
    headerFieldsOrder.add(sample);
    headerFieldsOrder.add(dateTime);
    headerFieldsOrder.add(startDate);
    headerFieldsOrder.add(endDate);
    for (JourneyMetricDeclaration journeyMetricDeclaration : Deployment.getJourneyMetricConfiguration().getMetrics().values())
    {
      headerFieldsOrder.add(journeyMetricDeclaration.getESFieldPrior());
      headerFieldsOrder.add(journeyMetricDeclaration.getESFieldDuring());
      headerFieldsOrder.add(journeyMetricDeclaration.getESFieldPost());
    }
  }
  
  public void dumpLineToCsv(Map<String, Object> lineMap, ZipOutputStream writer, boolean addHeaders)
  {
    try
      {
        if (addHeaders)
          {
            addHeaders(writer, lineMap, 1);
          }
        String line = ReportUtils.formatResult(lineMap);
        if (log.isTraceEnabled()) log.trace("Writing to csv file : " + line);
        writer.write(line.getBytes());
      } 
    catch (IOException e)
      {
        e.printStackTrace();
      }
  }
  
  public Map<String, List<Map<String, Object>>> getDataMultithread(Journey journey, Map<String,Object> map)
  {
    Map<String, List<Map<String, Object>>> result = new LinkedHashMap<String, List<Map<String, Object>>>();
    Map<String, Object> journeyStats = map;
    Map<String, Object> journeyMetric = map;
    if (journeyStats != null && !journeyStats.isEmpty())
      {
        Map<String, Object> journeyInfo = new LinkedHashMap<String, Object>();
        if (journeyStats.get(subscriberID) != null)
          {
            Object subscriberIDField = journeyStats.get(subscriberID);
            journeyInfo.put(customerID, subscriberIDField);
          }
        for (AlternateID alternateID : DeploymentCommon.getAlternateIDs().values())
        {
          if (journeyStats.get(alternateID.getID()) != null)
          {
            Object alternateId = journeyStats.get(alternateID.getID());
            journeyInfo.put(alternateID.getID(), alternateId);
          }
          else
          {
            journeyInfo.put(alternateID.getName(), "");
          }
        }
        journeyInfo.put(journeyID, journey.getJourneyID());
        journeyInfo.put(journeyName, journey.getGUIManagedObjectDisplay());
        journeyInfo.put(journeyType, journey.getTargetingType());
        journeyInfo.put(customerState, journey.getJourneyNodes().get(journeyStats.get("nodeID")).getNodeName());

        // No need to do all that anymore, "status" is already correct in ES
        /*
                boolean statusNotified = (boolean) journeyStats.get("statusNotified");
                boolean journeyComplete = (boolean) journeyStats.get("journeyComplete");
                boolean statusConverted = (boolean) journeyStats.get("statusConverted");
                Boolean statusTargetGroup = journeyStats.get("statusTargetGroup") == null ? null : (boolean) journeyStats.get("statusTargetGroup");
                Boolean statusControlGroup = journeyStats.get("statusControlGroup") == null ? null : (boolean) journeyStats.get("statusControlGroup");
                Boolean statusUniversalControlGroup = journeyStats.get("statusUniversalControlGroup") == null ? null : (boolean) journeyStats.get("statusUniversalControlGroup");
                journeyInfo.put("customerStatus", getSubscriberJourneyStatus(journeyComplete, statusConverted, statusNotified, statusTargetGroup, statusControlGroup, statusUniversalControlGroup).getDisplay());
         */
        journeyInfo.put(customerStatus, journeyStats.get("status"));

        if (journeyStats.get(sample) != null)
          {
            journeyInfo.put(sample, journeyStats.get("sample"));
          }
        else
          {
            journeyInfo.put(sample, "");
          }
        //Required Changes in accordance to EVPRO-530          
        //                String specialExit=journeyStats.get("status") == null ? null : (String) journeyStats.get("status");
        //                if(specialExit!=null && !specialExit.equalsIgnoreCase("null") && !specialExit.isEmpty() &&  (SubscriberJourneyStatus.fromExternalRepresentation(specialExit).in(SubscriberJourneyStatus.NotEligible,SubscriberJourneyStatus.UniversalControlGroup,SubscriberJourneyStatus.Excluded,SubscriberJourneyStatus.ObjectiveLimitReached))
        //                     )
        //                journeyInfo.put("customerStatus", SubscriberJourneyStatus.fromExternalRepresentation(specialExit).getDisplay());
        //                else 
        Date currentDate = SystemTime.getCurrentTime();
        journeyInfo.put(dateTime, ReportsCommonCode.getDateString(currentDate));
        journeyInfo.put(startDate, ReportsCommonCode.getDateString(journey.getEffectiveStartDate()));
        journeyInfo.put(endDate, ReportsCommonCode.getDateString(journey.getEffectiveEndDate()));

        for (JourneyMetricDeclaration journeyMetricDeclaration : DeploymentCommon.getJourneyMetricConfiguration().getMetrics().values())
          {
            journeyInfo.put(journeyMetricDeclaration.getESFieldPrior(), journeyMetric.get(journeyMetricDeclaration.getESFieldPrior()));
            journeyInfo.put(journeyMetricDeclaration.getESFieldDuring(), journeyMetric.get(journeyMetricDeclaration.getESFieldDuring()));
            journeyInfo.put(journeyMetricDeclaration.getESFieldPost(), journeyMetric.get(journeyMetricDeclaration.getESFieldPost()));
          }


        /*
         * if (addHeaders) { headerFieldsOrder.clear(); addHeaders(writer,
         * subscriberFields, 0); addHeaders(writer, journeyInfo, 1); } String line =
         * ReportUtils.formatResult(headerFieldsOrder, journeyInfo, subscriberFields);
         * log.trace("Writing to csv file : " + line); writer.write(line.getBytes());
         * writer.write("\n".getBytes());
         */

        //
        // result
        //

        String journeyID = journeyInfo.get("journeyID").toString();
        if (result.containsKey(journeyID))
          {
            result.get(journeyID).add(journeyInfo);
          } 
        else
          {
            List<Map<String, Object>> elements = new ArrayList<Map<String, Object>>();
            elements.add(journeyInfo);
            result.put(journeyID, elements);
          }

      }
    return result;
  }

  public static void main(String[] args, Date reportGenerationDate, int tenantID)
  {
    JourneyCustomerStatisticsReportMultithread journeyCustomerStatisticsReportMonoPhase = new JourneyCustomerStatisticsReportMultithread();
    journeyCustomerStatisticsReportMonoPhase.start(args, reportGenerationDate, tenantID);
  }
  
  private void start(String[] args, final Date reportGenerationDate, int tenantID)
  {
    log.info("received " + args.length + " args");
    for (String arg : args)
      {
        log.info("JourneyCustomerStatisticsReportMultithread: arg " + arg);
      }

    if (args.length < 3)
      {
        log.warn("Usage : JourneyCustomerStatisticsReportMultithread <ESNode> <ES journey index> <csvfile>");
        return;
      }
    String esNode = args[0];
    String esIndexJourney = args[1];
    String csvfile = args[2];
    if (args.length > 5) tenantID = Integer.parseInt(args[5]);

    journeyService = new JourneyService(DeploymentCommon.getBrokerServers(), "JourneyCustomerStatisticsReportMultithread-journeyservice-JourneyCustomerStatisticsReportMultithread", DeploymentCommon.getJourneyTopic(), false);
    journeyService.start();

    try {
      Collection<GUIManagedObject> allJourneys = journeyService.getStoredJourneys(tenantID);
      List<Journey> activeJourneys = new ArrayList<>();
      Date yesterdayAtMidnight = ReportUtils.delayAtZeroHour(reportGenerationDate, 0); // EVPRO-1488
      Date nDayAgoAtZeroHour = ReportUtils.delayAtMidnight(reportGenerationDate, Deployment.getReportManagerJourneysReportActiveNHoursAgo());
      for (GUIManagedObject gmo : allJourneys) {
        if (gmo.getEffectiveStartDate().before(yesterdayAtMidnight) && gmo.getEffectiveEndDate().after(nDayAgoAtZeroHour)) {
          activeJourneys.add((Journey) gmo);
        }
      }
      StringBuilder activeJourneyEsIndex = new StringBuilder();
      boolean firstEntry = true;
      for (Journey journey : activeJourneys)
        {
          if (!firstEntry) activeJourneyEsIndex.append(",");
          String indexName = esIndexJourney + journey.getJourneyID();
          activeJourneyEsIndex.append(indexName);
          firstEntry = false;
        }

      log.info("Reading data from ES in (" + activeJourneyEsIndex.toString() + ") and " + esIndexJourney + " index on " + esNode + " producing " + csvfile + " with '" + CSV_SEPARATOR + "' separator");

      LinkedHashMap<String, QueryBuilder> esIndexWithQuery = new LinkedHashMap<String, QueryBuilder>();
      esIndexWithQuery.put(activeJourneyEsIndex.toString(), QueryBuilders.matchAllQuery());

      ReportMonoPhase reportMonoPhase = new ReportMonoPhase(
          esNode,
          esIndexWithQuery,
          this,
          csvfile
          );

      if (!reportMonoPhase.startOneToOneMultiThread(journeyService, activeJourneys))
        {
          log.warn("An error occured, the report might be corrupted");
          throw new RuntimeException("An error occurred, report must be restarted");
        }
    } finally {

      journeyService.stop();
      log.info("Finished JourneyCustomerStatisticsReport Multithread");
    }
  }

  private void addHeaders(ZipOutputStream writer, Map<String, Object> values, int offset) throws IOException
  {
    if (values != null && !values.isEmpty())
      {
        String[] allFields = values.keySet().toArray(new String[0]);
        // Arrays.sort(allFields);
        String headers = "";
        for (String fields : allFields)
          {
            headerFieldsOrder.add(fields);
            headers += fields + CSV_SEPARATOR;
          }
        headers = headers.substring(0, headers.length() - offset);
        writer.write(headers.getBytes());
        if (offset == 1)
          {
            writer.write("\n".getBytes());
          }
      }
  }

  public SubscriberJourneyStatus getSubscriberJourneyStatus(boolean journeyComplete, boolean statusConverted, boolean statusNotified, Boolean statusTargetGroup, Boolean statusControlGroup, Boolean statusUniversalControlGroup)
  {
    return Journey.getSubscriberJourneyStatus(statusConverted, statusNotified, statusTargetGroup, statusControlGroup, statusUniversalControlGroup);
  }
}
