package com.evolving.nglm.evolution.reports.journeycustomerstates;

import com.evolving.nglm.core.SystemTime;
import com.evolving.nglm.evolution.Deployment;
import com.evolving.nglm.evolution.Journey;
import com.evolving.nglm.evolution.JourneyService;
import com.evolving.nglm.evolution.reports.ReportEsReader;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.LinkedHashMap;

/**
 * This implements phase 1 of the Journey report. All it does is specifying
 * <ol>
 * <li>Which field in Elastic Search is used as the key in the Kafka topic that
 * is produced ({@link JourneyCustomerStatesReportObjects#KEY_STR}), and
 * <li>Which Elastic Search indexes have to be read (passed as an array to the
 * {@link ReportEsReader} constructor.
 * </ol>
 * <p>
 * All data is written to a single topic.
 */
public class JourneyCustomerStatesReportESReader
{

  //
  // logger
  //

  private static final Logger log = LoggerFactory.getLogger(JourneyCustomerStatesReportESReader.class);

  public static void main(String[] args)
  {
    log.info("received " + args.length + " args");
    for (String arg : args)
      {
        log.info("JourneyCustomerStatesReportESReader: arg " + arg);
      }

    if (args.length < 5)
      {
        log.warn("Usage : JourneyCustomerStatesReportESReader <Output Topic> <KafkaNodeList> <ZKhostList> <ESNode> <ES journey index>");
        return;
      }
    String topicName = args[0];
    String kafkaNodeList = args[1];
    String kzHostList = args[2];
    String esNode = args[3];
    String esIndexJourney = args[4];

    JourneyService journeyService = new JourneyService(kafkaNodeList, "JourneyCustomerStatesReportESReader-journeyservice-" + topicName, Deployment.getJourneyTopic(), false);
    journeyService.start();

    Collection<Journey> activeJourneys = journeyService.getActiveJourneys(SystemTime.getCurrentTime());
    StringBuilder activeJourneyEsIndex = new StringBuilder();
    boolean firstEntry = true;
    for (Journey journey : activeJourneys)
      {
        if (!firstEntry) activeJourneyEsIndex.append(",");
        String indexName = esIndexJourney + journey.getJourneyID();
        activeJourneyEsIndex.append(indexName);
        firstEntry = false;
      }

    log.info("Reading data from ES in (" + activeJourneyEsIndex.toString() + ") and writing to " + topicName + " topic.");
    LinkedHashMap<String, QueryBuilder> esIndexWithQuery = new LinkedHashMap<String, QueryBuilder>();
    esIndexWithQuery.put(activeJourneyEsIndex.toString(), QueryBuilders.matchAllQuery());

    ReportEsReader reportEsReader = new ReportEsReader(JourneyCustomerStatesReportObjects.KEY_STR, topicName, kafkaNodeList, kzHostList, esNode, esIndexWithQuery, false);
    reportEsReader.start();
    journeyService.stop();
    log.info("Finished JourneyCustomerStatesReportESReader");
  }

}
