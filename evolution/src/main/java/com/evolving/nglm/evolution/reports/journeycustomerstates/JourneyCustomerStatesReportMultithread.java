package com.evolving.nglm.evolution.reports.journeycustomerstates;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
import com.evolving.nglm.evolution.JourneyNode;
import com.evolving.nglm.evolution.JourneyService;
import com.evolving.nglm.evolution.reports.ReportCsvFactory;
import com.evolving.nglm.evolution.reports.ReportMonoPhase;
import com.evolving.nglm.evolution.reports.ReportUtils;
import com.evolving.nglm.evolution.reports.ReportsCommonCode;


public class JourneyCustomerStatesReportMultithread implements ReportCsvFactory
{

  //
  // logger
  //

  private static final Logger log = LoggerFactory.getLogger(JourneyCustomerStatesReportMultithread.class);
  final private static String CSV_SEPARATOR = ReportUtils.getSeparator();
  private JourneyService journeyService;
  private final String subscriberID = "subscriberID";
  private final static String customerID = "customerID";
  
  
  private static final String journeyID = "journeyID";
  private static final String journeyName = "journeyName";
  private static final String journeyType = "journeyType";
  private static final String customerStatus = "customerStatus";
  private static final String customerStates = "customerStates";
  private static final String customerStatuses = "customerStatuses";
  private static final String dateTime = "dateTime";
  private static final String startDate = "startDate";
  private static final String endDate = "endDate";
  private static final String rewards = "rewards";
  private static final String journeyExitDate = "journeyExitDate";
  
  
  
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
    headerFieldsOrder.add(customerStatus);
    headerFieldsOrder.add(customerStates);
    headerFieldsOrder.add(customerStatuses);
    headerFieldsOrder.add(dateTime);
    headerFieldsOrder.add(startDate);
    headerFieldsOrder.add(endDate);
    headerFieldsOrder.add(rewards);
    headerFieldsOrder.add(journeyExitDate);
  }

  public void dumpLineToCsv(Map<String, Object> lineMap, ZipOutputStream writer, boolean addHeaders)
  {
    try
      {
        if (addHeaders)
          {
            addHeaders(writer, lineMap.keySet(), 1);
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

  public Map<String, List<Map<String, Object>>> getDataMultithread(Journey journey, Map<String, Object> map)
  {
    Map<String, List<Map<String, Object>>> result = new LinkedHashMap<String, List<Map<String, Object>>>();
    Map<String, Object> journeyStats = map;
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
                journeyInfo.put(alternateID.getName(), alternateId);
              }
            else
              {
                journeyInfo.put(alternateID.getName(), "");
              }
          }
        journeyInfo.put(journeyID, journey.getJourneyID());
        journeyInfo.put(journeyName, journey.getGUIManagedObjectDisplay());
        journeyInfo.put(journeyType, journey.getTargetingType());

        if (journeyStats.get("sample") != null)
          {
            journeyInfo.put("sample", journeyStats.get("sample"));
          }
        boolean statusNotified = false;
        boolean journeyComplete =false;
        boolean statusConverted = false;
        if (journeyStats.get("statusNotified") != null)
          {
            statusNotified = (boolean) journeyStats.get("statusNotified");
          }
        if (journeyStats.get("journeyComplete") != null)
          {
            journeyComplete = (boolean) journeyStats.get("journeyComplete");
          }
        if (journeyStats.get("statusConverted") != null)
          {
            statusConverted = (boolean) journeyStats.get("statusConverted");
          }
        Boolean statusTargetGroup  = journeyStats.get("statusTargetGroup")  == null ? null : (boolean) journeyStats.get("statusTargetGroup");
        Boolean statusControlGroup = journeyStats.get("statusControlGroup") == null ? null : (boolean) journeyStats.get("statusControlGroup");
        Boolean statusUniversalControlGroup = journeyStats.get("statusUniversalControlGroup") == null ? null : (boolean) journeyStats.get("statusUniversalControlGroup");
        //          String specialExit=journeyStats.get("specialExitStatus") == null ? null : (String) journeyStats.get("specialExitStatus");
        // Required Changes in accordance to EVPRO-530
        //            if(specialExit!=null && !specialExit.equalsIgnoreCase("null") && !specialExit.isEmpty())
        //            journeyInfo.put("customerStatus", SubscriberJourneyStatus.fromExternalRepresentation(specialExit).getDisplay());
        //            else   
        journeyInfo.put(customerStatus, getSubscriberJourneyStatus(journeyComplete, statusConverted, statusNotified, statusTargetGroup, statusControlGroup, statusUniversalControlGroup).getDisplay());

        List<String> nodeHistory = (List<String>) journeyStats.get("nodeHistory");
        StringBuilder sbStatus = new StringBuilder();
        if (nodeHistory != null && !nodeHistory.isEmpty())
          {
            for (String status : nodeHistory)
              {
                if (status != null)
                  {
                    String[] split = status.split(";");
                    String fromNodeName = decodeNodeName(journey, split, 0);
                    String toNodeName   = decodeNodeName(journey, split, 1);
                    Date   date         = decodeDate(split, 2);
                    sbStatus.append("(").append(fromNodeName).append("->").append(toNodeName).append(",").append(ReportsCommonCode.getDateString(date)).append("),");
                  }
              }
          }

        String states = null;
        if (sbStatus.length() > 0)
          {
            states = sbStatus.toString().substring(0, sbStatus.toString().length() - 1);
          }

        StringBuilder sbStatuses = new StringBuilder();
        List<String> statusHistory = (List<String>) journeyStats.get("statusHistory");
        if (statusHistory != null && !statusHistory.isEmpty())
          {
            for (String status : statusHistory)
              {
                String statusNameToBeDisplayed = "";
                String[] split = status.split(";");
                String statusName = null;
                if (split[0] != null && !split[0].equals("null"))
                  {
                    statusName = split[0];
                  }
                Date date = decodeDate(split, 1);
                sbStatuses.append("(").append(SubscriberJourneyStatus.fromExternalRepresentation(statusName).getDisplay()).append(",").append(ReportsCommonCode.getDateString(date)).append("),");
              }
            // EVPRO-1441 If last status is notEligible or similar, patch the customerStatus in the report
            String lastStatus = statusHistory.get(statusHistory.size()-1);
            String[] split = lastStatus.split(";");
            if (split[0] != null && !split[0].equals("null"))
              {
                SubscriberJourneyStatus status = SubscriberJourneyStatus.fromExternalRepresentation(split[0]);
                if (SubscriberJourneyStatus.NotEligible.equals(status)) {
                  journeyInfo.put(customerStatus, SubscriberJourneyStatus.NotEligible);
                } else if (SubscriberJourneyStatus.Excluded.equals(status)) {
                  journeyInfo.put(customerStatus, SubscriberJourneyStatus.Excluded);
                } else if (SubscriberJourneyStatus.ObjectiveLimitReached.equals(status)) {
                  journeyInfo.put(customerStatus, SubscriberJourneyStatus.ObjectiveLimitReached);
                } else if (SubscriberJourneyStatus.UniversalControlGroup.equals(status)) {
                  journeyInfo.put(customerStatus, SubscriberJourneyStatus.UniversalControlGroup);
                }
              }
          }

        String statuses = null;
        if (sbStatuses.length() > 0)
          {
            statuses = sbStatuses.toString().substring(0, sbStatuses.toString().length() - 1);
          }

        journeyInfo.put(customerStates,   states);
        journeyInfo.put(customerStatuses, statuses);
        journeyInfo.put(dateTime,         ReportsCommonCode.getDateString(SystemTime.getCurrentTime()));
        journeyInfo.put(startDate,        ReportsCommonCode.getDateString(journey.getEffectiveStartDate()));
        journeyInfo.put(endDate,          ReportsCommonCode.getDateString(journey.getEffectiveEndDate()));

        List<String> rewardHistory = (List<String>) journeyStats.get("rewardHistory");
        List<Map<String, Object>> outputJSON = new ArrayList<>();

        if (rewardHistory != null && !rewardHistory.isEmpty())
          {
            for (String status : rewardHistory)
              {
                Map<String, Object> historyJSON = new LinkedHashMap<>(); // to preserve order when displaying
                String[] split = status.split(";");
                String rewardID = null;
                String amount   = null;
                Date   date     = null;
                if (split != null && split.length >= 3)
                  {
                    rewardID = split[0];
                    amount   = split[1];
                    date     = decodeDate(split, 2);
                  }
                historyJSON.put("reward", rewardID);
                historyJSON.put("quantity", amount);
                historyJSON.put("date", ReportsCommonCode.getDateString(date));
                outputJSON.add(historyJSON);
              }
          }
        journeyInfo.put(rewards, ReportUtils.formatJSON(outputJSON));
            
        if (journeyStats.containsKey(journeyExitDate) && journeyStats.get(journeyExitDate) != null)
          {
            Object journeyExitDateObj = journeyStats.get(journeyExitDate);
            if (journeyExitDateObj instanceof String)
              {
                journeyInfo.put(journeyExitDate, ReportsCommonCode.parseDate((String) journeyExitDateObj));
              }
            else
              {
                log.info(journeyExitDateObj + " is of wrong type : "+journeyExitDateObj.getClass().getName());
              }
          }
        else
          {
            journeyInfo.put(journeyExitDate, null);
          }
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

  private String decodeNodeName(Journey journey, String[] split, int index)
  {
    String nodeName = null; // default value if error
    if (split[index] != null && !split[index].equals("null"))
      {
        JourneyNode journeyNode = journey.getJourneyNode(split[index]);
        if (journeyNode == null)
          {
            log.info("unknown journey node with name " + split[index] + " in journey " + journey.getGUIManagedObjectDisplay());
          }
        else
          {
            nodeName = journeyNode.getNodeName();
          }
      }
    return nodeName;
  }
  
  private Date decodeDate(String[] split, int index)
  {
    Date date = null;
    if (split[index] != null && !split[index].equals("null"))
      {
        try
          {
            date = new Date(Long.valueOf(split[index]));
          }
        catch (Exception e)
          {
            log.info("unable to convert to date : " + split[index]);
          }
      }
    return date;
  }

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

  public SubscriberJourneyStatus getSubscriberJourneyStatus(boolean journeyComplete, boolean statusConverted, boolean statusNotified, Boolean statusTargetGroup, Boolean statusControlGroup, Boolean statusUniversalControlGroup)
  {
    return Journey.getSubscriberJourneyStatus(statusConverted, statusNotified, statusTargetGroup, statusControlGroup, statusUniversalControlGroup);
  }
  
  public static void main(String[] args, final Date reportGenerationDate, int tenantID)
  {
    JourneyCustomerStatesReportMultithread journeyCustomerStatesReportMonoPhase = new JourneyCustomerStatesReportMultithread();
    journeyCustomerStatesReportMonoPhase.start(args, reportGenerationDate, tenantID);
  }
  
  private void start(String[] args, final Date reportGenerationDate, int tenantID)
  {
    log.info("received " + args.length + " args");
    for (String arg : args)
      {
        log.info("JourneyCustomerStatesReportMultithread: arg " + arg);
      }

    if (args.length < 4)
      {
        log.warn("Usage : JourneyCustomerStatesReportMultithread <ESNode> <ES customer index> <csvfile> <defaultReportPeriodQuantity> <defaultReportPeriodUnit>");
        return;
      }
    String esNode          = args[0];
    String esIndexJourney  = args[1];
    String csvfile         = args[2];
    if (args.length > 5) tenantID = Integer.parseInt(args[5]);

    journeyService = new JourneyService(DeploymentCommon.getBrokerServers(), "journeycustomerstatesreportMultithread-journeyservice-JourneyCustomerStatesReportMultithread", DeploymentCommon.getJourneyTopic(), false);
    journeyService.start();
    
    try {
      Collection<GUIManagedObject> allJourneys = journeyService.getStoredJourneys(tenantID);
      List<Journey> activeJourneys = new ArrayList<>();
      Date yesterdayAtZeroHour = ReportUtils.delayAtZeroHour(reportGenerationDate, Deployment.getReportManagerJourneysReportActiveNHoursAgo());
      Date yesterdayAtMidnight = ReportUtils.delayAtMidnight(reportGenerationDate, Deployment.getReportManagerJourneysReportActiveNHoursAgo());
      for (GUIManagedObject gmo : allJourneys) {
        if (gmo instanceof Journey &&
            gmo.getEffectiveStartDate().before(yesterdayAtMidnight) && gmo.getEffectiveEndDate().after(yesterdayAtZeroHour)) {
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

      log.info("Reading data from ES in (" + activeJourneyEsIndex.toString() + ") and writing to " + csvfile);
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
      log.info("Finished JourneyCustomerStatesReport Multithread");
    }  
  }

}
