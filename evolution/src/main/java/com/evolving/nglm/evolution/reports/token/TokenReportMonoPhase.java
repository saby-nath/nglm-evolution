/****************************************************************************
 *
 *  TokenReportCsvWriter.java 
 *
 ****************************************************************************/

package com.evolving.nglm.evolution.reports.token;

import com.evolving.nglm.evolution.reports.ReportsCommonCode;
import com.evolving.nglm.core.AlternateID;
import com.evolving.nglm.core.SystemTime;
import com.evolving.nglm.evolution.*;
import com.evolving.nglm.evolution.DeliveryRequest.Module;
import com.evolving.nglm.evolution.SubscriberProfileService.EngineSubscriberProfileService;
import com.evolving.nglm.evolution.reports.ReportCsvFactory;
import com.evolving.nglm.evolution.reports.ReportCsvWriter;
import com.evolving.nglm.evolution.reports.ReportMonoPhase;
import com.evolving.nglm.evolution.reports.ReportUtils;
import com.evolving.nglm.evolution.reports.ReportUtils.ReportElement;
import com.evolving.nglm.evolution.reports.subscriber.SubscriberReportMonoPhase;

import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.json.simple.JSONArray;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.*;
import java.util.zip.ZipOutputStream;

public class TokenReportMonoPhase implements ReportCsvFactory
{
  private static final Logger log = LoggerFactory.getLogger(TokenReportMonoPhase.class);
  final private static String CSV_SEPARATOR = ReportUtils.getSeparator();

  private final static String subscriberID = "subscriberID";
  private final static String customerID = "customerID";

  private static OfferService offerService = null;
  private static TokenTypeService tokenTypeservice = null;
  private static PresentationStrategyService presentationStrategyService = null;
  private static ScoringStrategyService scoringStrategyService = null;
  private static JourneyService journeyService = null;
  private static LoyaltyProgramService loyaltyProgramService = null;

  public TokenReportMonoPhase()
  {

  }

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
            if (subscriberFields.get("tokens") != null)
              {
                List<Map<String, Object>> tokensArray = (List<Map<String, Object>>) subscriberFields.get("tokens");
                if (!tokensArray.isEmpty())
                  {
                    commonFields.put(customerID, subscriberID);

                    for (AlternateID alternateID : Deployment.getAlternateIDs().values())
                      {
                        if (subscriberFields.get(alternateID.getESField()) != null)
                          {
                            Object alternateId = subscriberFields.get(alternateID.getESField());
                            commonFields.put(alternateID.getName(), alternateId);
                          }
                      }

                    for (int i = 0; i < tokensArray.size(); i++)
                      {
                        result.clear();
                        result.putAll(commonFields);
                        Map<String, Object> token = (Map<String, Object>) tokensArray.get(i);

                        result.put("tokenCode", token.get("tokenCode"));
                        result.put("tokenType", token.get("tokenType"));
                        result.put("creationDate", dateOrEmptyString(token.get("creationDate")));
                        result.put("expirationDate", dateOrEmptyString(token.get("expirationDate")));
                        result.put("redeemedDate", dateOrEmptyString(token.get("redeemedDate")));
                        result.put("lastAllocationDate", dateOrEmptyString(token.get("lastAllocationDate")));
                        result.put("tokenStatus", token.get("tokenStatus"));
                        result.put("qtyAllocations", token.get("qtyAllocations"));
                        result.put("qtyAllocatedOffers", token.get("qtyAllocatedOffers"));

                        GUIManagedObject presentationStrategy = presentationStrategyService.getStoredPresentationStrategy((String) token.get("presentationStrategyID"));
                        if (presentationStrategy != null)
                          {
                            result.put("presentationStrategy", presentationStrategy.getGUIManagedObjectDisplay());
                          }
                        else
                          {
                            result.put("presentationStrategy", "");
                          }

                        List<String> scoringStrategyArray = (List<String>) subscriberFields.get("scoringStrategyIDs");
                        if (scoringStrategyArray != null)
                          {
                            List<String> scoringStrategy = new ArrayList<>();
                            for (int j = 0; j < scoringStrategyArray.size(); j++)
                              {
                                String ssID = (String) scoringStrategyArray.get(j);
                                GUIManagedObject ss = scoringStrategyService.getStoredScoringStrategy(ssID);
                                if (ss != null)
                                  {
                                    scoringStrategy.add(ss.getGUIManagedObjectDisplay());
                                  }
                              }
                            if (scoringStrategy.size() != 0)
                              {
                                result.put("scoringStrategy", scoringStrategy);
                              }
                            else
                              {
                                result.put("scoringStrategy", "");
                              }
                          }
                        else
                          {
                            result.put("scoringStrategy", "");
                          }

                        GUIManagedObject acceptedOffer = offerService.getStoredOffer((String) token.get("acceptedOfferID"));
                        if (acceptedOffer != null)
                          {
                            result.put("acceptedOffer", acceptedOffer.getGUIManagedObjectDisplay());
                          }
                        else
                          {
                            result.put("acceptedOffer", "");
                          }

                        String moduleID = (String) token.get("moduleID");
                        if (moduleID != null)
                          {
                            Module module = Module.fromExternalRepresentation(moduleID);
                            result.put("module", module.toString());
                            String featureID = (String) token.get("featureID");
                            if (featureID != null)
                              {
                                String featureDisplay = DeliveryRequest.getFeatureDisplay(module, featureID, journeyService, offerService, loyaltyProgramService);
                                result.put("featureName", featureDisplay);
                              }
                            else
                              {
                                result.put("featureName", "");
                              }
                          }
                        else
                          {
                            result.put("module", "");
                            result.put("featureName", "");
                          }

                        if (addHeaders)
                          {
                            addHeaders(writer, result.keySet(), 1);
                            addHeaders = false;
                          }
                        String line = ReportUtils.formatResult(result);
                        log.trace("Writing to csv file : " + line);
                        writer.write(line.getBytes());
                        writer.write("\n".getBytes());
                      }
                  }
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

  /****************************************
   *
   * main
   *
   ****************************************/

  public static void main(String[] args)
  {
    log.info("received " + args.length + " args");
    for (String arg : args)
      {
        log.info("TokenReportESReader: arg " + arg);
      }

    if (args.length < 4) {
      log.warn(
          "Usage : TokenReportMonoPhase <KafkaNodeList> <ESNode> <ES customer index> <csvfile>");
      return;
    }
    String kafkaNodeList   = args[0];
    String esNode          = args[1];
    String esIndexCustomer = args[2];
    String csvfile         = args[3];

    log.info("Reading data from ES in "+esIndexCustomer+"  index and writing to "+csvfile+" file.");  
    ReportCsvFactory reportFactory = new TokenReportMonoPhase();

    LinkedHashMap<String, QueryBuilder> esIndexWithQuery = new LinkedHashMap<String, QueryBuilder>();
    esIndexWithQuery.put(esIndexCustomer, QueryBuilders.matchAllQuery());
      
    ReportMonoPhase reportMonoPhase = new ReportMonoPhase(
              esNode,
              esIndexWithQuery,
              reportFactory,
              csvfile
          );

    offerService = new OfferService(Deployment.getBrokerServers(), "report-offerService-tokenReportMonoPhase", Deployment.getOfferTopic(), false);
    offerService.start();

    scoringStrategyService = new ScoringStrategyService(Deployment.getBrokerServers(), "report-scoringstrategyservice-tokenReportMonoPhase", Deployment.getScoringStrategyTopic(), false);
    scoringStrategyService.start();

    presentationStrategyService = new PresentationStrategyService(Deployment.getBrokerServers(), "report-presentationstrategyservice-tokenReportMonoPhase", Deployment.getPresentationStrategyTopic(), false);
    presentationStrategyService.start();

    tokenTypeservice = new TokenTypeService(Deployment.getBrokerServers(), "report-tokentypeservice-tokenReportMonoPhase", Deployment.getTokenTypeTopic(), false);
    tokenTypeservice.start();
    
    journeyService = new JourneyService(Deployment.getBrokerServers(), "report-journeyservice-tokenReportMonoPhase",Deployment.getJourneyTopic(), false);
    journeyService.start();

    loyaltyProgramService = new LoyaltyProgramService(Deployment.getBrokerServers(), "report-loyaltyprogramservice-tokenReportMonoPhase", Deployment.getLoyaltyProgramTopic(), false);
    loyaltyProgramService.start();
    
    if (!reportMonoPhase.startOneToOne())
      {
        log.warn("An error occured, the report might be corrupted");
        return;
      }
    
  }

}
