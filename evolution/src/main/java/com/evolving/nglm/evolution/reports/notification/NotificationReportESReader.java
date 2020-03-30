package com.evolving.nglm.evolution.reports.notification;

import com.evolving.nglm.core.RLMDateUtils;
import com.evolving.nglm.core.SystemTime;
import com.evolving.nglm.evolution.Deployment;
import com.evolving.nglm.evolution.reports.ReportEsReader;
import com.evolving.nglm.evolution.reports.ReportEsReader.PERIOD;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.TimeZone;

public class NotificationReportESReader
{

  private static final Logger log = LoggerFactory.getLogger(NotificationReportESReader.class);
  private static String elasticSearchDateFormat = Deployment.getElasticSearchDateFormat();
  private static DateFormat dateFormat = new SimpleDateFormat(elasticSearchDateFormat);
  private static final DateFormat DATE_FORMAT;
  static
  {
    DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd");
    DATE_FORMAT.setTimeZone(TimeZone.getTimeZone(Deployment.getBaseTimeZone()));
  }

  public static void main(String[] args)
  {
    log.info("received " + args.length + " args");
    for (String arg : args)
      {
        log.info("NotificationReportESReader: arg " + arg);
      }

    if (args.length < 6)
      {
        log.warn("Usage : NotificationReportESReader <Output Topic> <KafkaNodeList> <ZKhostList> <ESNode> <ES customer index> <ES journey index>");
        return;
      }
    String topicName = args[0];
    String kafkaNodeList = args[1];
    String kzHostList = args[2];
    String esNode = args[3];
    String esIndexNotif = args[4];
    String esIndexCustomer = args[5];

    Integer reportPeriodQuantity = 0;
    String reportPeriodUnit = null;
    if (args.length > 6 && args[6] != null && args[7] != null)
      {
        reportPeriodQuantity = Integer.parseInt(args[6]);
        reportPeriodUnit = args[7];
      }
    
    Date fromDate = getFromDate(reportPeriodUnit, reportPeriodQuantity);
    Date toDate = SystemTime.getCurrentTime();
    
    List<String> esIndexDates = getEsIndexDates(fromDate, toDate);
    StringBuilder esIndexNotifList = new StringBuilder();
    boolean firstEntry = true;
    for (String esIndexDate : esIndexDates)
      {
        if (!firstEntry) esIndexNotifList.append(",");
        String indexName = esIndexNotif + esIndexDate;
        esIndexNotifList.append(indexName);
        firstEntry = false;
      }

    log.info("Reading data from ES in (" + esIndexNotifList.toString() + ")  index and writing to " + topicName + " topic.");

    LinkedHashMap<String, QueryBuilder> esIndexWithQuery = new LinkedHashMap<String, QueryBuilder>();
    esIndexWithQuery.put(esIndexNotifList.toString(), QueryBuilders.matchAllQuery());
    esIndexWithQuery.put(esIndexCustomer, QueryBuilders.matchAllQuery());

    ReportEsReader reportEsReader = new ReportEsReader("subscriberID", topicName, kafkaNodeList, kzHostList, esNode, esIndexWithQuery);

    reportEsReader.start();
    log.info("Finished NotificationReportESReader");
  }
  
  private static List<String> getEsIndexDates(final Date fromDate, Date toDate)
  {
    Date tempfromDate = fromDate;
    List<String> esIndexOdrList = new ArrayList<String>();
    while(tempfromDate.getTime() <= toDate.getTime())
      {
        esIndexOdrList.add(DATE_FORMAT.format(tempfromDate));
        tempfromDate = RLMDateUtils.addDays(tempfromDate, 1, Deployment.getBaseTimeZone());
      }
    return esIndexOdrList;
  }
  
  private static Date getFromDate(String reportPeriodUnit, Integer reportPeriodQuantity)
  {
    reportPeriodQuantity = reportPeriodQuantity == null || reportPeriodQuantity == 0 ? new Integer(1) : reportPeriodQuantity;
    if (reportPeriodUnit == null) reportPeriodUnit  = PERIOD.DAYS.getExternalRepresentation();
    
    //
    //
    //
    
    Date now = SystemTime.getCurrentTime();
    Date fromDate = null;
    switch (reportPeriodUnit.toUpperCase())
      {
        case "DAYS":
          fromDate = RLMDateUtils.addDays(now, -reportPeriodQuantity, com.evolving.nglm.core.Deployment.getBaseTimeZone());
          break;
          
        case "WEEKS":
          fromDate = RLMDateUtils.addWeeks(now, -reportPeriodQuantity, com.evolving.nglm.core.Deployment.getBaseTimeZone());
          break;
          
        case "MONTHS":
          fromDate = RLMDateUtils.addMonths(now, -reportPeriodQuantity, com.evolving.nglm.core.Deployment.getBaseTimeZone());
          break;
          
        default:
          break;
      }
    return fromDate;
  }
}
