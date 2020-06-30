package com.evolving.nglm.evolution.reports.journeycustomerstatistics;

import com.evolving.nglm.core.SystemTime;
import com.evolving.nglm.evolution.Deployment;
import com.evolving.nglm.evolution.Journey;
import com.evolving.nglm.evolution.JourneyService;
import com.evolving.nglm.evolution.reports.ReportEsReader;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Collection;
import java.util.Date;
import java.util.LinkedHashMap;

/**
 * This implements phase 1 of the Journey report. All it does is specifying
 * <ol>
 * <li>Which field in Elastic Search is used as the key in the Kafka topic that
 * is produced, and
 * <li>Which Elastic Search indexes have to be read (passed as an array to the
 * {@link ReportEsReader} constructor.
 * </ol>
 * <p>
 * All data is written to a single topic.
 */
public class JourneyCustomerStatisticsReportESReader
{

  //
  // logger
  //

  private static final Logger log = LoggerFactory.getLogger(JourneyCustomerStatisticsReportESReader.class);
  private static String elasticSearchDateFormat = Deployment.getElasticSearchDateFormat();
  private static DateFormat dateFormat = new SimpleDateFormat(elasticSearchDateFormat);

  public static void main(String[] args, JourneyService journeyService, final Date reportGenerationDate)
  {
    log.info("received " + args.length + " args");
    for (String arg : args)
      {
        log.info("JourneyCustomerStatisticsReportESReader: arg " + arg);
      }

    if (args.length < 5)
      {
        log.warn("Usage : JourneyCustomerStatisticsReportESReader <Output Topic> <KafkaNodeList> <ZKhostList> <ESNode> <ES journey index> <ES journey metric index>");
        return;
      }
    String topicName = args[0];
    String kafkaNodeList = args[1];
    String kzHostList = args[2];
    String esNode = args[3];
    String esIndexJourney = args[4];
    String esIndexJourneyMetric = args[5];

    Collection<Journey> activeJourneys = journeyService.getActiveJourneys(reportGenerationDate);
    StringBuilder activeJourneyEsIndex = new StringBuilder();
    boolean firstEntry = true;
    for (Journey journey : activeJourneys)
      {
        if (!firstEntry) activeJourneyEsIndex.append(",");
        String indexName = esIndexJourney + journey.getJourneyID();
        activeJourneyEsIndex.append(indexName);
        firstEntry = false;
      }

    log.info("Reading data from ES in (" + activeJourneyEsIndex.toString() + ") and " + esIndexJourney + " index and writing to " + topicName + " topic.");

    LinkedHashMap<String, QueryBuilder> esIndexWithQuery = new LinkedHashMap<String, QueryBuilder>();
    esIndexWithQuery.put(activeJourneyEsIndex.toString(), QueryBuilders.matchAllQuery());
    esIndexWithQuery.put(esIndexJourneyMetric, QueryBuilders.matchAllQuery());

    ReportEsReader reportEsReader = new ReportEsReader("subscriberID", topicName, kafkaNodeList, kzHostList, esNode, esIndexWithQuery, false);

    reportEsReader.start();
    log.info("Finished JourneyCustomerStatisticsReportESReader");
  }

}
