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
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;

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

    if (args.length < 6)
      {
        log.warn("Usage : JourneyCustomerStatesReportESReader <Output Topic> <KafkaNodeList> <ZKhostList> <ESNode> <ES customer index> <ES journey index>");
        return;
      }
    
    //
    //  read arguments
    //
    
    String topicName = args[0];
    String kafkaNode = args[1];
    String kzHostList = args[2];
    String esNode = args[3];
    String esIndexJourney = args[4];
    String esIndexCustomer = args[5];
    
    //
    //  journeyService
    //
    
    Date now = SystemTime.getCurrentTime();
    JourneyService journeyService = new JourneyService(kafkaNode, "JourneyCustomerStatesReportESReader-journeyservice-" + now.getTime(), Deployment.getJourneyTopic(), false);
    journeyService.start();
    
    //
    //  actvieJourneys
    //
    
    Collection<Journey> actvieJourneys = journeyService.getActiveJourneys(now);
    
    //
    // elasticserach
    //
    
    LinkedHashMap<String, QueryBuilder> esIndexWithQuery = new LinkedHashMap<String, QueryBuilder>();
    for (Journey journey : actvieJourneys)
      {
        log.info("RAJ creating esIndexJourney {} ", esIndexJourney + journey.getJourneyID());
        esIndexWithQuery.put(esIndexJourney + journey.getJourneyID(), QueryBuilders.matchAllQuery());
      }
    esIndexWithQuery.put(esIndexCustomer, QueryBuilders.matchAllQuery());
    log.info("Reading data from ES and writing to topic.");
    ReportEsReader reportEsReader = new ReportEsReader(JourneyCustomerStatesReportObjects.KEY_STR, topicName, kafkaNode, kzHostList, esNode, esIndexWithQuery);
    reportEsReader.start();
    log.info("Finished JourneyCustomerStatesReportESReader");
  }

}
