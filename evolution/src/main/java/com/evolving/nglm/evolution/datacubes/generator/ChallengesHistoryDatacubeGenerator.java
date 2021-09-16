package com.evolving.nglm.evolution.datacubes.generator;

import java.io.IOException;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.apache.lucene.search.join.ScoreMode;
import org.elasticsearch.ElasticsearchException;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.rest.RestStatus;
import org.elasticsearch.search.aggregations.Aggregation;
import org.elasticsearch.search.aggregations.AggregationBuilder;
import org.elasticsearch.search.aggregations.AggregationBuilders;
import org.elasticsearch.search.aggregations.Aggregations;
import org.elasticsearch.search.aggregations.bucket.composite.CompositeValuesSourceBuilder;
import org.elasticsearch.search.aggregations.bucket.composite.ParsedComposite;
import org.elasticsearch.search.aggregations.bucket.composite.TermsValuesSourceBuilder;
import org.elasticsearch.search.aggregations.bucket.nested.ParsedNested;
import org.elasticsearch.search.aggregations.bucket.nested.ParsedReverseNested;
import org.elasticsearch.search.aggregations.bucket.range.DateRangeAggregationBuilder;
import org.elasticsearch.search.aggregations.bucket.range.ParsedDateRange;
import org.elasticsearch.search.aggregations.bucket.terms.ParsedTerms;
import org.elasticsearch.search.aggregations.bucket.terms.Terms;
import org.elasticsearch.search.aggregations.bucket.terms.TermsAggregationBuilder;
import org.elasticsearch.search.aggregations.metrics.ParsedSum;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.elasticsearch.search.sort.FieldSortBuilder;
import org.elasticsearch.search.sort.SortOrder;

import com.evolving.nglm.core.Deployment;
import com.evolving.nglm.core.Pair;
import com.evolving.nglm.core.RLMDateUtils;
import com.evolving.nglm.core.SystemTime;
import com.evolving.nglm.evolution.GUIManagedObject;
import com.evolving.nglm.evolution.LoyaltyProgram;
import com.evolving.nglm.evolution.LoyaltyProgramService;
import com.evolving.nglm.evolution.SegmentationDimension;
import com.evolving.nglm.evolution.SegmentationDimensionService;
import com.evolving.nglm.evolution.datacubes.DatacubeGenerator;
import com.evolving.nglm.evolution.datacubes.DatacubeManager;
import com.evolving.nglm.evolution.datacubes.DatacubeWriter;
import com.evolving.nglm.evolution.datacubes.SubscriberProfileDatacubeMetric;
import com.evolving.nglm.evolution.datacubes.mapping.LoyaltyProgramChallengesMap;
import com.evolving.nglm.evolution.datacubes.mapping.SegmentationDimensionsMap;
import com.evolving.nglm.evolution.elasticsearch.ElasticsearchClientAPI;

public class ChallengesHistoryDatacubeGenerator extends DatacubeGenerator
{
  private static final String DATACUBE_ES_INDEX_SUFFIX = "_datacube_challengeshistory";
  public static final String DATACUBE_ES_INDEX(int tenantID) { return "t" + tenantID + DATACUBE_ES_INDEX_SUFFIX; }
  private static final String DATA_ES_INDEX = "subscriberprofile";
  private static final String DATA_METRIC_PREFIX = "metric_";
  private static final String DATA_FILTER_STRATUM_PREFIX = "stratum."; // from subscriberprofile index
  private static final String DATACUBE_FILTER_STRATUM_PREFIX = "stratum."; // pushed in datacube index - same as SubscriberProfileDatacube

  /*****************************************
  *
  * Properties
  *
  *****************************************/
  private LoyaltyProgramChallengesMap loyaltyProgramChallengesMap;
  private SegmentationDimensionsMap segmentationDimensionList;
  private Map<String, SubscriberProfileDatacubeMetric> customMetrics;

  private String metricTargetDay;
  private Date metricTargetDayStart;
  private Date metricTargetDayAfterStart;
  private Date metricTargetTwoDaysAfterStart;

  /*****************************************
  *
  * Constructors
  *
  *****************************************/
  public ChallengesHistoryDatacubeGenerator(String datacubeName, ElasticsearchClientAPI elasticsearch, DatacubeWriter datacubeWriter, LoyaltyProgramService loyaltyProgramService, int tenantID, String timeZone, SegmentationDimensionService segmentationDimensionService)

  {
    super(datacubeName, elasticsearch, datacubeWriter, tenantID, timeZone);
    this.loyaltyProgramChallengesMap = new LoyaltyProgramChallengesMap(loyaltyProgramService);
    this.segmentationDimensionList = new SegmentationDimensionsMap(segmentationDimensionService);
    //TODO: this.subscriberStatusDisplayMapping = new SubscriberStatusMap();
  }
  
  public ChallengesHistoryDatacubeGenerator(String datacubeName, int tenantID, DatacubeManager datacubeManager) {
    this(datacubeName,
        datacubeManager.getElasticsearchClientAPI(),
        datacubeManager.getDatacubeWriter(),
        datacubeManager.getLoyaltyProgramService(),
        tenantID,
        Deployment.getDeployment(tenantID).getTimeZone(),
        datacubeManager.getSegmentationDimensionService());
  }

  /*****************************************
  *
  * Elasticsearch indices settings
  *
  *****************************************/
  @Override protected String getDataESIndex() { return DATA_ES_INDEX; }
  @Override protected String getDatacubeESIndex() { return DATACUBE_ES_INDEX(this.tenantID); }

  /*****************************************
  *
  * Datacube generation phases
  *
  *****************************************/
  @Override
  protected boolean runPreGenerationPhase() throws ElasticsearchException, IOException, ClassCastException
  {
    loyaltyProgramChallengesMap.update();
    this.segmentationDimensionList.update();
    this.customMetrics = Deployment.getSubscriberProfileDatacubeMetrics();
    //TODO: subscriberStatusDisplayMapping.updateFromElasticsearch(elasticsearch);
    return true;
  }

  @Override
  protected SearchRequest getElasticsearchRequest()
  {
    //
    // Target index
    //
    String ESIndex = getDataESIndex();
    
    //
    // Filter query
    //
    // Hack: When a newly created subscriber in Elasticsearch comes first by ExtendedSubscriberProfile sink connector,
    // it has not yet any of the "product" main (& mandatory) fields.
    // Those comes when the SubscriberProfile sink connector push them.
    // For a while, it is possible a document in subscriberprofile index miss many product fields required by datacube generation.
    // Therefore, we filter out those subscribers with missing data by looking for lastUpdateDate
    BoolQueryBuilder query = QueryBuilders.boolQuery();
    query.filter().add(QueryBuilders
        .rangeQuery("lastUpdateDate")
        .gte(this.printTimestamp(metricTargetDayStart))
        .lt(this.printTimestamp(metricTargetTwoDaysAfterStart)));
    query.filter().add(QueryBuilders.termQuery("tenantID", this.tenantID)); // filter to keep only tenant related items !
    query.filter().add(QueryBuilders.nestedQuery("loyaltyPrograms", QueryBuilders.boolQuery().filter(QueryBuilders.termQuery("loyaltyPrograms.loyaltyProgramType", LoyaltyProgram.LoyaltyProgramType.CHALLENGE.getExternalRepresentation())), ScoreMode.Total));

    //
    // Aggregations
    //
    List<CompositeValuesSourceBuilder<?>> sources = new ArrayList<>();
    sources.add(new TermsValuesSourceBuilder("loyaltyProgramID").field("loyaltyPrograms.programID"));
    sources.add(new TermsValuesSourceBuilder("level").field("loyaltyPrograms.levelName").missingBucket(false)); // Missing means opt-out. Do not count them here
    sources.add(new TermsValuesSourceBuilder("occurrenceNumber").field("loyaltyPrograms.occurrenceNumber").missingBucket(true)); // Missing means opt-out. We need to catch them !

    //
    // Sub Aggregation STATUS(filter) with metrics
    //
    TermsAggregationBuilder statusAgg = AggregationBuilders.terms("STATUS").field("evolutionSubscriberStatus").missing("undefined"); // default bucket for status=null
       
    //
    // Sub Aggregation DATE_BUCKETS (internal, won't be exported as a filter)
    //
    DateRangeAggregationBuilder dateBuckets = AggregationBuilders.dateRange("DATE_BUCKETS").field("lastUpdateDate")
        .addRange(this.printTimestamp(metricTargetDayStart), this.printTimestamp(metricTargetDayAfterStart))
        .addRange(this.printTimestamp(metricTargetDayAfterStart), this.printTimestamp(metricTargetTwoDaysAfterStart));
    statusAgg.subAggregation(dateBuckets);

    //
    // Sub Aggregations dimensions (only statistic dimensions)
    //
    TermsAggregationBuilder rootStratumBuilder = null; // first aggregation
    TermsAggregationBuilder termStratumBuilder = null; // last aggregation
   
    for (String dimensionID : segmentationDimensionList.keySet())  {
      GUIManagedObject segmentationObject = segmentationDimensionList.get(dimensionID);
      if (segmentationObject != null && segmentationObject instanceof SegmentationDimension && ((SegmentationDimension) segmentationObject).getStatistics()) {
        if (termStratumBuilder != null) {
          TermsAggregationBuilder temp = AggregationBuilders.terms(DATA_FILTER_STRATUM_PREFIX + dimensionID)
              .field(DATA_FILTER_STRATUM_PREFIX + dimensionID).missing("undefined");
          termStratumBuilder = termStratumBuilder.subAggregation(temp);
          termStratumBuilder = temp;
        }
        else {
          termStratumBuilder = AggregationBuilders.terms(DATA_FILTER_STRATUM_PREFIX + dimensionID)
              .field(DATA_FILTER_STRATUM_PREFIX + dimensionID).missing("undefined");
          rootStratumBuilder = termStratumBuilder;
        }
      }
    }
    dateBuckets.subAggregation(rootStratumBuilder);
    
    //
    // Metrics - Scores
    //
    for(String programID: loyaltyProgramChallengesMap.keySet()) {
      termStratumBuilder.subAggregation(AggregationBuilders.sum("TODAY." + programID).field("scoreFluctuations." + programID + ".today"));
      termStratumBuilder.subAggregation(AggregationBuilders.sum("YESTERDAY." + programID).field("scoreFluctuations." + programID + ".yesterday"));
    }
    
    //
    // Metrics - Subscriber Metrics
    //
    for(String metricID: customMetrics.keySet()) {
      SubscriberProfileDatacubeMetric metric = customMetrics.get(metricID);
      
      termStratumBuilder.subAggregation(AggregationBuilders.sum("TODAY." + DATA_METRIC_PREFIX + metricID).field(metric.getTodayESField()));
      termStratumBuilder.subAggregation(AggregationBuilders.sum("YESTERDAY." + DATA_METRIC_PREFIX + metricID).field(metric.getYesterdayESField()));
    }  
    
    //
    // Final
    //
    AggregationBuilder aggregation = AggregationBuilders.nested("DATACUBE", "loyaltyPrograms").subAggregation(
        AggregationBuilders.composite("LOYALTY-COMPOSITE", sources).size(ElasticsearchClientAPI.MAX_BUCKETS).subAggregation(
            AggregationBuilders.reverseNested("REVERSE").subAggregation(statusAgg) // *metrics is STATUS with metrics
        )
    );
    
    //
    // Datacube request
    //
    SearchSourceBuilder datacubeRequest = new SearchSourceBuilder()
        .sort(FieldSortBuilder.DOC_FIELD_NAME, SortOrder.ASC)
        .query(query)
        .aggregation(aggregation)
        .size(0);
    
    return new SearchRequest(ESIndex).source(datacubeRequest);
  }
  
  @Override
  protected void embellishFilters(Map<String, Object> filters)
  {
    String loyaltyProgramID = (String) filters.remove("loyaltyProgramID");
    filters.put("loyaltyProgram", loyaltyProgramChallengesMap.getDisplay(loyaltyProgramID, "loyaltyProgram"));
    
    for (String dimensionID : segmentationDimensionList.keySet()) {
      GUIManagedObject segmentationObject = segmentationDimensionList.get(dimensionID);
      if (segmentationObject != null && SegmentationDimension.class.isAssignableFrom(segmentationObject.getClass()) && ((SegmentationDimension) segmentationObject).getStatistics()) {
        String segmentID = (String) filters.remove(DATA_FILTER_STRATUM_PREFIX + dimensionID);
        String dimensionDisplay = segmentationDimensionList.getDimensionDisplay(dimensionID, DATA_FILTER_STRATUM_PREFIX + dimensionID);
        String fieldName = DATACUBE_FILTER_STRATUM_PREFIX + dimensionDisplay;
        filters.put(fieldName, segmentationDimensionList.getSegmentDisplay(dimensionID, segmentID, fieldName));
      }
    }
  }
  
  // A = A+B
  private void datacubeRowAddition(Map<String, Object> a, Map<String, Object> b){
    // Add counts
    a.put("count", ((Long) a.get("count")) + ((Long) b.get("count")));
  
    // Add metrics
    for(String key : a.keySet()) {
      if(key.startsWith("metric.")) {
        a.put(key, ((Long) a.get(key)) + ((Long) b.get(key)));
      }
    }
  }
  
  /**
   * Extract metrics from a list of aggregations
   * @return metrics
   */
  private Map<String, Long> metricExtraction(Aggregations metricAggregations, String metricPrefix, String programID) {
    HashMap<String, Long> metrics = new HashMap<String,Long>();
    
    //
    // Extract score
    // 
    if (programID != null) { // Otherwise no score for this loyalty program
      ParsedSum score = metricAggregations.get(metricPrefix + programID);
      if (score == null) {
        log.error("Unable to extract score metric for LP: " + programID + ", aggregation is missing.");
      } else {
        metrics.put("score", new Long((int) score.getValue()));
      }
    }
    
    //
    // Subscriber Metrics
    //
    for(String metricID: customMetrics.keySet()) {
      SubscriberProfileDatacubeMetric subscriberProfileCustomMetric = customMetrics.get(metricID);
      
      ParsedSum customMetric = metricAggregations.get(metricPrefix + DATA_METRIC_PREFIX + metricID);
      if (customMetric == null) {
        log.error("Unable to extract custom." + metricID + ", aggregation is missing.");
      } else {
        String customFieldName = "custom." + subscriberProfileCustomMetric.getDisplay();
        metrics.put(customFieldName, new Long((int) customMetric.getValue()));
      }
    }
    
    return metrics;
  }
  
  /**
   * Auxiliary recursive function for row extraction
   * This function will explore the combination tree built from buckets.
   * Each leaf of the tree represent a final combination. 
   * The doc count of the combination (and also its metrics) will be retrieve in its leaf.
   * 
   * This recursive function will return every combination created from this 
   * node as it was the root of the tree.
   * 
   * The return is hacky, it contains metrics: Map(field, value) with a special pair ("count",c)
   * This pair need to be removed from the metrics afterward.
   * 
   * @return List[ Combination(Dimension, Segment) -> Metrics+Count ]
   */
  private List<Pair<Map<String, String>, Map<String, Long>>> extractSegmentationStratum(ParsedTerms parsedTerms, String metricPrefix, String programID)
  {
    if (parsedTerms == null || parsedTerms.getBuckets() == null) {
      log.error("stratum buckets are missing in search response.");
      return Collections.emptyList();
    }
    
    List<Pair<Map<String, String>, Map<String, Long>>> result = new LinkedList<Pair<Map<String, String>, Map<String, Long>>>();
    
    String dimensionID = parsedTerms.getName();
    for (Terms.Bucket stratumBucket : parsedTerms.getBuckets()) { // Explore each segment for this dimension.
      String segmentID = stratumBucket.getKeyAsString();
      
      //
      // Leaf detection
      //
      boolean leaf = false;
      Map<String, Aggregation> stratumBucketAggregation = stratumBucket.getAggregations().getAsMap();
      if (stratumBucketAggregation == null || stratumBucketAggregation.isEmpty())  {
        leaf = true;
      } 
      else {
        for(String subAggregationName :  stratumBucketAggregation.keySet()) {
          if(subAggregationName.startsWith("TODAY.") || subAggregationName.startsWith("YESTERDAY.")) {
            leaf = true;
          }
          break; // Only look at the first element
        } 
      }
      
      if (leaf)  {
        //
        // Leaf - extract count
        //
        Map<String, String> combination = new HashMap<String,String>();
        Map<String, Long> metrics = metricExtraction(stratumBucket.getAggregations(), metricPrefix, programID);
        long count = stratumBucket.getDocCount();
        metrics.put("count", count);                                // Add special metric count
        combination.put(dimensionID, segmentID);                    // Add new dimension
        result.add(new Pair<Map<String, String>, Map<String, Long>>(combination, metrics)); // Add this combination to the result
      }
      else {
        //
        // Node - recursive call
        // 
        for(Aggregation subAggregation :  stratumBucket.getAggregations()) {
          List<Pair<Map<String, String>, Map<String, Long>>> childResults = extractSegmentationStratum((ParsedTerms) subAggregation, metricPrefix, programID);
  
          for (Pair<Map<String, String>, Map<String, Long>> stratum : childResults) {
            stratum.getFirstElement().put(dimensionID, segmentID);  // Add new dimension
            result.add(stratum);                                    // Add this combination to the result
          }
        }
      }
    }
    
    return result;
  }


  @Override
  protected List<Map<String, Object>> extractDatacubeRows(SearchResponse response, String timestamp, long period) throws ClassCastException
  {
    Map<String, Map<String,Object>> result = new HashMap<String, Map<String,Object>>();
    
    if (response.isTimedOut()
        || response.getFailedShards() > 0
        || response.status() != RestStatus.OK) {
      log.error("Elasticsearch search response return with bad status.");
      log.error(response.toString());
      return Collections.emptyList();
    }
    
    if(response.getAggregations() == null) {
      log.error("Main aggregation is missing in search response.");
      return Collections.emptyList();
    }
    
    ParsedNested parsedNested = response.getAggregations().get("DATACUBE");
    if(parsedNested == null || parsedNested.getAggregations() == null) {
      log.error("Nested aggregation is missing in search response.");
      return Collections.emptyList();
    }
    
    ParsedComposite parsedComposite = parsedNested.getAggregations().get("LOYALTY-COMPOSITE");
    if(parsedComposite == null || parsedComposite.getBuckets() == null) {
      log.error("Composite buckets are missing in search response.");
      return Collections.emptyList();
    }
    
    for(ParsedComposite.ParsedBucket bucket: parsedComposite.getBuckets()) {
      //
      // Extract one part of the filter
      //
      Map<String, Object> filters = bucket.getKey();
      for(String key: filters.keySet()) {
        if(filters.get(key) == null) {
          filters.replace(key, UNDEFINED_BUCKET_VALUE);
        }
      }
      
      // Special filter: tenantID 
      filters.put("tenantID", this.tenantID);
      
      String loyaltyProgramID = (String) filters.get("loyaltyProgramID");

      //
      // Extract the second part of the filter
      //
      if(bucket.getAggregations() == null) {
        log.error("Aggregations in bucket is missing in search response.");
        continue;
      }
      
      ParsedReverseNested parsedReverseNested = bucket.getAggregations().get("REVERSE");
      if(parsedReverseNested == null || parsedReverseNested.getAggregations() == null) {
        log.error("Reverse nested aggregation is missing in search response.");
        continue;
      }
      
      ParsedTerms parsedTerms = parsedReverseNested.getAggregations().get("STATUS");
      if(parsedTerms == null || parsedTerms.getBuckets() == null) {
        log.error("Status buckets are missing in search response.");
        continue;
      }

      for(org.elasticsearch.search.aggregations.bucket.terms.Terms.Bucket statusBucket: parsedTerms.getBuckets()) {
        //
        // Split between today & yesterday for metrics extraction
        //
        ParsedDateRange parsedDateBuckets = statusBucket.getAggregations().get("DATE_BUCKETS");
        if(parsedDateBuckets == null || parsedDateBuckets.getBuckets() == null) {
          log.error("Date Range buckets are missing in search response.");
          continue;
        }
        
        for(org.elasticsearch.search.aggregations.bucket.range.Range.Bucket dateBucket: parsedDateBuckets.getBuckets()) {
          //
          // Extract metrics prefix (from date)
          //
          String metricPrefix = "";
          String evolutionSubscriberStatus = (String) statusBucket.getKey();
       
          long from = ((ZonedDateTime) dateBucket.getFrom()).toEpochSecond() * 1000;
          if(from == (long) metricTargetDayStart.getTime()) {
            metricPrefix = "TODAY."; // Look for today metrics
          } else if(from == (long) metricTargetDayAfterStart.getTime()) {
            // Those subscribers have been updated after midnight and before the execution of the datacube.
            metricPrefix = "YESTERDAY."; // Look for yesterday metrics
          } else {
            log.error("Should not happen, did not success to split between today and yesterday metrics.");
            continue;
          }
          
          if (dateBucket.getAggregations() == null) {
            log.error("Unable to extract stratums, aggregations are missing.");
            continue;
          }
          
          for(Aggregation stratumParsedTerms : dateBucket.getAggregations()) {
            List<Pair<Map<String, String>, Map<String, Long>>> childResults = extractSegmentationStratum((ParsedTerms) stratumParsedTerms, metricPrefix, loyaltyProgramID);
            
            for (Pair<Map<String, String>, Map<String, Long>> stratum : childResults) {
              Map<String, Object> filtersCopy = new HashMap<String, Object>(filters);
              filtersCopy.put("evolutionSubscriberStatus", evolutionSubscriberStatus);
              for (String dimensionID : stratum.getFirstElement().keySet()) {
                filtersCopy.put(dimensionID, stratum.getFirstElement().get(dimensionID));
              }
              
              Map<String, Long> metrics = stratum.getSecondElement();
              Long docCount = metrics.remove("count");  // remove special metric
              
              //
              // Build row
              //
              Map<String, Object> row = extractRow(filtersCopy, docCount, timestamp, period, metrics);
              String rowID = (String) row.get("_id");
              
              Map<String, Object> duplicate = result.get(rowID);
              if(duplicate != null) {
                datacubeRowAddition(duplicate, row);
              } else {
                result.put(rowID, row);
              }
            }
          }
        }
      }
    }
    
    List<Map<String,Object>> resultList = new ArrayList<Map<String,Object>>();
    for(String key: result.keySet()) {
      resultList.add(result.get(key));
    }
    return resultList;
  }

  
  /*****************************************
  *
  * DocumentID settings
  *
  *****************************************/
  /**
   * In order to override preview documents, we use the following trick: the timestamp used in the document ID must be 
   * the timestamp of the definitive push (and not the time we publish it).
   * This way, preview documents will override each other till be overriden by the definitive one running the day after.
   * 
   * Be careful, it only works if we ensure to publish the definitive one. 
   * Already existing combination of filters must be published even if there is 0 count inside, in order to 
   * override potential previews.
   */
  @Override
  protected String getDocumentID(Map<String,Object> filters, String timestamp) {
    return this.extractDocumentIDFromFilter(filters, this.metricTargetDay, "default");
  }
  
  /*****************************************
  *
  * Run
  *
  *****************************************/
  /**
   * The definitive datacube is at yesterday_23:59:59.999+ZZZZ
   *
   * In this datacube, period is not used ATM, but still set at one day.
   */
  public void definitive()
  {
    Date now = SystemTime.getCurrentTime();
    Date yesterday = RLMDateUtils.addDays(now, -1, this.getTimeZone());
    Date tomorrow = RLMDateUtils.addDays(now, 1, this.getTimeZone());
    
    // Dates: yyyy-MM-dd 00:00:00.000
    Date beginningOfYesterday = RLMDateUtils.truncate(yesterday, Calendar.DATE, this.getTimeZone());
    Date beginningOfToday = RLMDateUtils.truncate(now, Calendar.DATE, this.getTimeZone());
    Date beginningOfTomorrow = RLMDateUtils.truncate(tomorrow, Calendar.DATE, this.getTimeZone());

    this.metricTargetDay = this.printDay(yesterday);
    this.metricTargetDayStart = beginningOfYesterday;
    this.metricTargetDayAfterStart = beginningOfToday;
    this.metricTargetTwoDaysAfterStart = beginningOfTomorrow;

    //
    // Timestamp & period
    //
    Date endOfYesterday = RLMDateUtils.addMilliseconds(beginningOfToday, -1);                               // 23:59:59.999
    String timestamp = this.printTimestamp(endOfYesterday);
    long targetPeriod = beginningOfToday.getTime() - beginningOfYesterday.getTime();    // most of the time 86400000ms (24 hours)
    
    this.run(timestamp, targetPeriod);
  }
  
  /**
   * A preview is a datacube generation on the today's day. 
   *
   * In this datacube, period is not used ATM, but still set at one day.
   */
  public void preview()
  {
    Date now = SystemTime.getCurrentTime();
    Date tomorrow = RLMDateUtils.addDays(now, 1, this.getTimeZone());
    Date afterTomorrow = RLMDateUtils.addDays(now, 2, this.getTimeZone());
    
    // Dates: yyyy-MM-dd 00:00:00.000
    Date beginningOfToday = RLMDateUtils.truncate(now, Calendar.DATE, this.getTimeZone());
    Date beginningOfTomorrow = RLMDateUtils.truncate(tomorrow, Calendar.DATE, this.getTimeZone());
    Date beginningDayAfterTomorrow = RLMDateUtils.truncate(afterTomorrow, Calendar.DATE, this.getTimeZone());
    
    this.metricTargetDay = this.printDay(now);
    this.metricTargetDayStart = beginningOfToday;
    this.metricTargetDayAfterStart = beginningOfTomorrow;
    this.metricTargetTwoDaysAfterStart = beginningDayAfterTomorrow;

    //
    // Timestamp & period
    //
    String timestamp = this.printTimestamp(now);
    long targetPeriod = now.getTime() - beginningOfToday.getTime() + 1; // +1 !
    
    this.run(timestamp, targetPeriod);
  }
}
