package com.evolving.nglm.evolution.datacubes.generator;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.elasticsearch.ElasticsearchException;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.aggregations.AggregationBuilder;
import org.elasticsearch.search.aggregations.AggregationBuilders;
import org.elasticsearch.search.aggregations.bucket.composite.ParsedComposite.ParsedBucket;
import org.elasticsearch.search.aggregations.bucket.range.ParsedDateRange;
import org.elasticsearch.search.aggregations.bucket.filter.ParsedFilter;
import org.elasticsearch.search.aggregations.metrics.ParsedSum;

import com.evolving.nglm.core.RLMDateUtils;
import com.evolving.nglm.evolution.Deployment;
import com.evolving.nglm.evolution.Journey;
import com.evolving.nglm.evolution.JourneyMetricDeclaration;
import com.evolving.nglm.evolution.JourneyService;
import com.evolving.nglm.evolution.JourneyStatisticESSinkConnector;
import com.evolving.nglm.evolution.SegmentationDimensionService;
import com.evolving.nglm.evolution.datacubes.DatacubeWriter;
import com.evolving.nglm.evolution.datacubes.SimpleDatacubeGenerator;
import com.evolving.nglm.evolution.datacubes.mapping.JourneysMap;
import com.evolving.nglm.evolution.datacubes.mapping.SegmentationDimensionsMap;
import com.evolving.nglm.evolution.elasticsearch.ElasticsearchClientAPI;

public class JourneyTrafficDatacubeGenerator extends SimpleDatacubeGenerator
{
  public static final String DATACUBE_ES_INDEX_PREFIX = "datacube_journeytraffic-";
  private static final String DATA_ES_INDEX_PREFIX = "journeystatistic-";
  private static final String FILTER_STRATUM_PREFIX = "subscriberStratum.";
  private static final String METRIC_CONVERSION_COUNT = "metricConversionCount";
  private static final String METRIC_CONVERTED_TODAY = "metricConvertedToday";

  /*****************************************
  *
  * Properties
  *
  *****************************************/
  private List<String> filterFields;
  private List<String> basicFilterFields;
  private SegmentationDimensionsMap segmentationDimensionList;
  private JourneysMap journeysMap;
  
  private String journeyID;
  private Date publishDate;
  private Date metricTargetDayStart;
  private Date metricTargetDayAfterStart;

  /*****************************************
  *
  * Constructors
  *
  *****************************************/
  public JourneyTrafficDatacubeGenerator(String datacubeName, ElasticsearchClientAPI elasticsearch, DatacubeWriter datacubeWriter, SegmentationDimensionService segmentationDimensionService, JourneyService journeyService)  
  {
    super(datacubeName, elasticsearch, datacubeWriter);

    this.segmentationDimensionList = new SegmentationDimensionsMap(segmentationDimensionService);
    this.journeysMap = new JourneysMap(journeyService);
    
    //
    // Filter fields
    //
    this.basicFilterFields = new ArrayList<String>();
    this.basicFilterFields.add("status");
    this.basicFilterFields.add("nodeID");
    this.basicFilterFields.add("sample");
    this.filterFields = new ArrayList<String>();
  }

  /*****************************************
  *
  * Elasticsearch indices settings
  *
  *****************************************/
  @Override protected String getDataESIndex() 
  { 
    return DATA_ES_INDEX_PREFIX + JourneyStatisticESSinkConnector.journeyIDFormatterForESIndex(this.journeyID); 
  }
  
  @Override protected String getDatacubeESIndex() 
  { 
    return DATACUBE_ES_INDEX_PREFIX + JourneyStatisticESSinkConnector.journeyIDFormatterForESIndex(this.journeyID); 
  }

  /*****************************************
  *
  * Filters settings
  *
  *****************************************/
  @Override protected List<String> getFilterFields() { return filterFields; }
  
  @Override
  protected boolean runPreGenerationPhase() throws ElasticsearchException, IOException, ClassCastException
  {
    if(!isESIndexAvailable(getDataESIndex())) {
      log.info("Elasticsearch index [" + getDataESIndex() + "] does not exist.");
      return false;
    }
    
    this.segmentationDimensionList.update();
    this.journeysMap.update();
    
    //
    // Should we keep pushing datacube for this journey ?
    //
    Journey journey = this.journeysMap.get(this.journeyID);
    if(journey == null) {
      log.error("Error, unable to retrieve info for JourneyID=" + this.journeyID);
      return false;
    }
    // Number of day needed after EndDate for JourneyMetrics to be pushed. - Add 24 hours to be sure (due to truncation, see populateMetricsPost)
    int maximumPostPeriod = Deployment.getJourneyMetricConfiguration().getPostPeriodDays() + 1;
    Date stopDate = RLMDateUtils.addDays(journey.getEffectiveEndDate(), maximumPostPeriod, Deployment.getSystemTimeZone()); // TODO EVPRO-99 use systemTimeZone instead of baseTimeZone, is it correct or should it be per tenant ???
    if(publishDate.after(stopDate)) {
      log.info("JourneyID=" + this.journeyID + " has ended more than " + maximumPostPeriod + " days ago. No data will be published anymore.");
      return false;
    }
    
    //
    // Segments
    //
    this.filterFields = new ArrayList<String>(this.basicFilterFields);
    for(String dimensionID: segmentationDimensionList.keySet()) {
      this.filterFields.add(FILTER_STRATUM_PREFIX + dimensionID);
    }
    
    return true;
  }
  
  @Override 
  protected void embellishFilters(Map<String, Object> filters) 
  {
    //
    // Journey (already in index name, added for Kibana/Grafana)
    //
    filters.put("journey", journeysMap.getDisplay(journeyID, "journey"));
    
    //
    // nodeID
    //
    String nodeID = (String) filters.remove("nodeID");
    filters.put("node", journeysMap.getNodeDisplay(journeyID, nodeID, "node"));
    
    //
    // Special dimension with all, for Grafana 
    //
    filters.put(FILTER_STRATUM_PREFIX + "Global", " ");
    
    //
    // subscriberStratum dimensions
    //
    for(String dimensionID: segmentationDimensionList.keySet())
      {
        String fieldName = FILTER_STRATUM_PREFIX + dimensionID;
        String segmentID = (String) filters.remove(fieldName);
        
        String newFieldName = FILTER_STRATUM_PREFIX + segmentationDimensionList.getDimensionDisplay(dimensionID, fieldName);
        filters.put(newFieldName, segmentationDimensionList.getSegmentDisplay(dimensionID, segmentID, fieldName));
      }
  }
  
  /*****************************************
  *
  * Metrics settings
  *
  *****************************************/
  @Override
  protected List<AggregationBuilder> getMetricAggregations()
  {
    // Those aggregations need to be recomputed with the update of journeyTrafficList
    List<AggregationBuilder> metricAggregations = new ArrayList<AggregationBuilder>();
    
    metricAggregations.add(AggregationBuilders.sum(METRIC_CONVERSION_COUNT).field("conversionCount"));
    metricAggregations.add(AggregationBuilders.dateRange(METRIC_CONVERTED_TODAY).field("lastConversionDate")
        .addRange(RLMDateUtils.printTimestamp(metricTargetDayStart), RLMDateUtils.printTimestamp(metricTargetDayAfterStart)));

    Map<String, JourneyMetricDeclaration> journeyMetricsMap = Deployment.getJourneyMetricConfiguration().getMetrics();
    if(! journeyMetricsMap.entrySet().isEmpty()) {
      JourneyMetricDeclaration valJournMetric = journeyMetricsMap.entrySet().iterator().next().getValue();
      
      metricAggregations.add(AggregationBuilders.filter("subsWithPriorMetrics", QueryBuilders.existsQuery(valJournMetric.getESFieldPrior())));
      metricAggregations.add(AggregationBuilders.filter("subsWithDuringMetrics", QueryBuilders.existsQuery(valJournMetric.getESFieldDuring())));
      metricAggregations.add(AggregationBuilders.filter("subsWithPostMetrics", QueryBuilders.existsQuery(valJournMetric.getESFieldPost())));
      
      for(Entry<String, JourneyMetricDeclaration> entry : journeyMetricsMap.entrySet())   {
        metricAggregations.add(AggregationBuilders.sum(entry.getValue().getID() + "_" + entry.getValue().getESFieldPrior()).field(entry.getValue().getESFieldPrior()));
        metricAggregations.add(AggregationBuilders.sum(entry.getValue().getID() + "_" + entry.getValue().getESFieldDuring()).field(entry.getValue().getESFieldDuring()));
        metricAggregations.add(AggregationBuilders.sum(entry.getValue().getID() + "_" + entry.getValue().getESFieldPost()).field(entry.getValue().getESFieldPost()));
      }
    }

    return metricAggregations;
  }
  
  @Override
  protected Map<String, Long> extractMetrics(ParsedBucket compositeBucket) throws ClassCastException
  {
    HashMap<String, Long> metrics = new HashMap<String, Long>();
    
    if (compositeBucket.getAggregations() == null) {
      log.error("Unable to extract metrics, aggregation is missing.");
      return metrics;
    }

    // ConversionCount
    ParsedSum conversionCountAggregation = compositeBucket.getAggregations().get(METRIC_CONVERSION_COUNT);
    if (conversionCountAggregation == null) {
      log.error("Unable to extract conversionCount in journeystatistics, aggregation is missing.");
      return metrics;
    } else {
      metrics.put("conversions", new Long((long) conversionCountAggregation.getValue()));
    }
    
    // ConvertedToday
    ParsedDateRange convertedTodayAggregation = compositeBucket.getAggregations().get(METRIC_CONVERTED_TODAY);
    if(convertedTodayAggregation == null || convertedTodayAggregation.getBuckets() == null) {
      log.error("Date Range buckets are missing in search response, unable to retrieve convertedToday.");
      return metrics;
    }
    
    for(org.elasticsearch.search.aggregations.bucket.range.Range.Bucket bucket: convertedTodayAggregation.getBuckets()) {
      // WARNING: we should only enter this loop once because there is only one bucket defined !
      metrics.put("converted.today", new Long(bucket.getDocCount()));
    }

    Map<String, JourneyMetricDeclaration> journeyMetricsMap = Deployment.getJourneyMetricConfiguration().getMetrics();
    if(! journeyMetricsMap.entrySet().isEmpty()) {
      ParsedFilter countAggregation = compositeBucket.getAggregations().get("subsWithPriorMetrics");
      if (countAggregation != null ) {
        metrics.put("custom.subsWithPriorMetrics", countAggregation.getDocCount());
      }
  
      countAggregation = compositeBucket.getAggregations().get("subsWithDuringMetrics");
      if (countAggregation != null ) {
        metrics.put("custom.subsWithDuringMetrics", countAggregation.getDocCount());
      }
  
      countAggregation = compositeBucket.getAggregations().get("subsWithPostMetrics");
      if (countAggregation != null ) {
        metrics.put("custom.subsWithPostMetrics", countAggregation.getDocCount());
      }
  
      for(Entry<String, JourneyMetricDeclaration> entry : journeyMetricsMap.entrySet()) {
        ParsedSum priorAggregation = compositeBucket.getAggregations().get(entry.getValue().getID() + "_" + entry.getValue().getESFieldPrior());
        ParsedSum duringAggregation = compositeBucket.getAggregations().get(entry.getValue().getID() + "_" + entry.getValue().getESFieldDuring());
        ParsedSum postAggregation = compositeBucket.getAggregations().get(entry.getValue().getID() + "_" + entry.getValue().getESFieldPost());
  
        if (priorAggregation != null ) {
          metrics.put("custom." + entry.getValue().getESFieldPrior(), (long) priorAggregation.getValue());
        }
  
        if (duringAggregation != null) {
          metrics.put("custom." + entry.getValue().getESFieldDuring(), (long) duringAggregation.getValue());
        }
  
        if(postAggregation != null) {
          metrics.put("custom." + entry.getValue().getESFieldPost(), (long) postAggregation.getValue());
        }
      }
    }
        
    return metrics;
  }

  /*****************************************
  *
  * Run
  *
  *****************************************/
  public void definitive(String journeyID, long journeyStartDateTime, Date publishDate)
  {
    this.journeyID = journeyID;
    this.publishDate = publishDate;

    Date tomorrow = RLMDateUtils.addDays(publishDate, 1, Deployment.getSystemTimeZone()); // TODO EVPRO-99 use systemTimeZone instead of baseTimeZone, is it correct or should it be per tenant ???
    // Dates: YYYY-MM-dd 00:00:00.000
    Date beginningOfToday = RLMDateUtils.truncate(publishDate, Calendar.DATE, Deployment.getSystemTimeZone()); // TODO EVPRO-99 use systemTimeZone instead of baseTimeZone, is it correct or should it be per tenant ???
    Date beginningOfTomorrow = RLMDateUtils.truncate(tomorrow, Calendar.DATE, Deployment.getSystemTimeZone()); // TODO EVPRO-99 use systemTimeZone instead of baseTimeZone, is it correct or should it be per tenant ???
    this.metricTargetDayStart = beginningOfToday;
    this.metricTargetDayAfterStart = beginningOfTomorrow;
    
    String timestamp = RLMDateUtils.printTimestamp(publishDate);
    long targetPeriod = publishDate.getTime() - journeyStartDateTime;
    this.run(timestamp, targetPeriod);
  }
}
