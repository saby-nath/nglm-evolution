package com.evolving.nglm.evolution.datacubes.generator;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;

import org.elasticsearch.ElasticsearchException;
import org.elasticsearch.search.aggregations.AggregationBuilder;
import org.elasticsearch.search.aggregations.bucket.composite.CompositeValuesSourceBuilder;
import org.elasticsearch.search.aggregations.bucket.composite.DateHistogramValuesSourceBuilder;
import org.elasticsearch.search.aggregations.bucket.composite.ParsedComposite.ParsedBucket;
import org.elasticsearch.search.aggregations.bucket.histogram.DateHistogramInterval;

import com.evolving.nglm.core.RLMDateUtils;
import com.evolving.nglm.core.SystemTime;
import com.evolving.nglm.evolution.JourneyService;
import com.evolving.nglm.evolution.LoyaltyProgramService;
import com.evolving.nglm.evolution.OfferObjectiveService;
import com.evolving.nglm.evolution.OfferService;
import com.evolving.nglm.evolution.SupplierService;
import com.evolving.nglm.evolution.VoucherService;
import com.evolving.nglm.evolution.datacubes.DatacubeUtils;
import com.evolving.nglm.evolution.datacubes.DatacubeWriter;
import com.evolving.nglm.evolution.datacubes.SimpleDatacubeGenerator;
import com.evolving.nglm.evolution.datacubes.mapping.DeliverablesMap;
import com.evolving.nglm.evolution.datacubes.mapping.JourneysMap;
import com.evolving.nglm.evolution.datacubes.mapping.LoyaltyProgramsMap;
import com.evolving.nglm.evolution.datacubes.mapping.ModulesMap;
import com.evolving.nglm.evolution.datacubes.mapping.OfferObjectivesMap;
import com.evolving.nglm.evolution.datacubes.mapping.OffersMap;
import com.evolving.nglm.evolution.datacubes.mapping.SuppliersMap;
import com.evolving.nglm.evolution.datacubes.mapping.VouchersMap;
import com.evolving.nglm.evolution.elasticsearch.ElasticsearchClientAPI;

public class VDRDatacubeGenerator extends SimpleDatacubeGenerator
{
  private static final String DATACUBE_ES_INDEX = "datacube_vdr";
  private static final String DATA_ES_INDEX_PREFIX = "detailedrecords_vouchers-";
  
  /*****************************************
  *
  * Properties
  *
  *****************************************/
  private List<String> filterFields;
  private OffersMap offersMap;
  private ModulesMap modulesMap;
  private OfferObjectivesMap offerObjectivesMap;
  private LoyaltyProgramsMap loyaltyProgramsMap;
  private DeliverablesMap deliverablesMap;
  private JourneysMap journeysMap;
  private VouchersMap vouchersMap;
  private SuppliersMap suppliersMap;

  private boolean hourlyMode;
  private String targetDay;
  private String targetTimestamp;

  /*****************************************
  *
  * Constructors
  *
  *****************************************/
  public VDRDatacubeGenerator(String datacubeName, ElasticsearchClientAPI elasticsearch, DatacubeWriter datacubeWriter, OfferService offerService, OfferObjectiveService offerObjectiveService, LoyaltyProgramService loyaltyProgramService, JourneyService journeyService, VoucherService voucherService, SupplierService supplierService, int tenantID, String timeZone)  
  {
    super(datacubeName, elasticsearch, datacubeWriter, tenantID, timeZone);

    this.offersMap = new OffersMap(offerService);
    this.modulesMap = new ModulesMap();
    this.offerObjectivesMap = new OfferObjectivesMap(offerObjectiveService);
    this.loyaltyProgramsMap = new LoyaltyProgramsMap(loyaltyProgramService);
    this.deliverablesMap = new DeliverablesMap();
    this.journeysMap = new JourneysMap(journeyService);
    this.vouchersMap = new VouchersMap(voucherService);
    this.suppliersMap = new SuppliersMap(supplierService);
    
    //
    // Filter fields
    //
    this.filterFields = new ArrayList<String>();
    this.filterFields.add("voucherID");
    this.filterFields.add("moduleID");
    this.filterFields.add("featureID");
    this.filterFields.add("returnCode");
    this.filterFields.add("origin");
    this.filterFields.add("action");
    
   }

  /*****************************************
  *
  * Elasticsearch indices settings
  *
  *****************************************/
  @Override protected String getDatacubeESIndex() { return DATACUBE_ES_INDEX; }
  @Override protected String getDataESIndex() { return (DATA_ES_INDEX_PREFIX+targetDay); }

  /*****************************************
  *
  * Filters settings
  *
  *****************************************/
  @Override protected List<String> getFilterFields() { return filterFields; }
  
  @Override
  protected CompositeValuesSourceBuilder<?> getSpecialSourceFilter() {
    if(this.hourlyMode) {
      return new DateHistogramValuesSourceBuilder("timestamp").field("eventDatetime").calendarInterval(DateHistogramInterval.HOUR);
    } else {
      return null;
    }
  }
  
  @Override
  protected boolean runPreGenerationPhase() throws ElasticsearchException, IOException, ClassCastException
  {
    if(!isESIndexAvailable(getDataESIndex())) {
      log.info("Elasticsearch index [" + getDataESIndex() + "] does not exist.");
      return false;
    }
    
    offersMap.update();
    modulesMap.updateFromElasticsearch(elasticsearch);
    offerObjectivesMap.update();
    loyaltyProgramsMap.update();
    deliverablesMap.updateFromElasticsearch(elasticsearch);
    journeysMap.update();
    vouchersMap.update();
    suppliersMap.update();
    
    return true;
  }
  
  @Override
  protected void embellishFilters(Map<String, Object> filters)
  {
    String voucherID = (String) filters.remove("voucherID");
    filters.put("voucher", vouchersMap.getDisplay(voucherID, "voucher"));
    String supplierID = vouchersMap.getSupplierID(voucherID, "voucher");
    filters.put("supplier", suppliersMap.getDisplay(supplierID, "supplier"));
    
    String moduleID = (String) filters.remove("moduleID");
    filters.put("module", modulesMap.getDisplay(moduleID, "module"));

    DatacubeUtils.embelishFeature(filters, moduleID, modulesMap, loyaltyProgramsMap, deliverablesMap, offersMap, journeysMap);

       
    DatacubeUtils.embelishReturnCode(filters);
    
    // Specials for timestamp (will not be display in filter but extracted later in DatacubeGenerator)
    if(this.hourlyMode) {
      Long time = (Long) filters.remove("timestamp");
      // The timestamp retrieve from the Date Histogram is the START of the interval. 
      // But here we want to timestamp at the END (+ 1hour -1ms)
      Date date = new Date(time + 3600*1000 - 1);
      filters.put("timestamp", this.printTimestamp(date));
    }
  }

  /*****************************************
  *
  * Metrics settings
  *
  *****************************************/
  @Override protected List<AggregationBuilder> getMetricAggregations() { return Collections.emptyList(); }
  @Override protected Map<String, Long> extractMetrics(ParsedBucket compositeBucket) throws ClassCastException { return Collections.emptyMap(); }
  
  
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
    if(this.hourlyMode) {
      // @rl: It also works in hourly mode because there is the real timestamp in filters !
      return this.extractDocumentIDFromFilter(filters, this.targetTimestamp, "HOURLY");
    } else {
      return this.extractDocumentIDFromFilter(filters, this.targetTimestamp, "DAILY");
    }
  }
  
  /*****************************************
  *
  * Run
  *
  *****************************************/
  /**
   * The definitive datacube is generated on yesterday, for a period of 1 day (~24 hours except for some special days)
   * Rows will be timestamped at yesterday_23:59:59.999+ZZZZ
   * Timestamp is the last millisecond of the period (therefore included).
   * This way it shows that *count* is computed for this day (yesterday) but at the very end of the day.
   */
  public void dailyDefinitive()
  {
    Date now = SystemTime.getCurrentTime();
    Date yesterday = RLMDateUtils.addDays(now, -1, this.getTimeZone());  
    Date beginningOfYesterday = RLMDateUtils.truncate(yesterday, Calendar.DATE, this.getTimeZone());
    Date beginningOfToday = RLMDateUtils.truncate(now, Calendar.DATE, this.getTimeZone());  // 00:00:00.000 
    Date endOfYesterday = RLMDateUtils.addMilliseconds(beginningOfToday, -1);   // 23:59:59.999
    
    //
    // Run configurations
    //
    this.hourlyMode = false;
    this.targetDay = this.printDay(yesterday);
    this.targetTimestamp = this.printTimestamp(endOfYesterday);

    //
    // Timestamp & period
    //
    String timestamp = this.printTimestamp(endOfYesterday);
    long targetPeriod = beginningOfToday.getTime() - beginningOfYesterday.getTime();    // most of the time 86400000ms (24 hours)
    
    this.run(timestamp, targetPeriod);
  }
  
  /**
   * A preview is a datacube generation on the today's day. 
   * Timestamp is the last millisecond of the period (therefore included).
   * Because the day is still not ended, it won't be the definitive value of *count*.
   */
  public void dailyPreview()
  {
    Date now = SystemTime.getCurrentTime();
    Date tomorrow = RLMDateUtils.addDays(now, 1, this.getTimeZone()); 
    Date beginningOfToday = RLMDateUtils.truncate(now, Calendar.DATE, this.getTimeZone());  
    Date beginningOfTomorrow = RLMDateUtils.truncate(tomorrow, Calendar.DATE, this.getTimeZone()); // 00:00:00.000 
    Date endOfToday = RLMDateUtils.addMilliseconds(beginningOfTomorrow, -1);                                       
    
    //
    // Run configurations
    //
    this.hourlyMode = false;
    this.targetDay = this.printDay(now);
    this.targetTimestamp = this.printTimestamp(endOfToday);

    //
    // Timestamp & period
    //
    String timestamp = this.printTimestamp(now);
    long targetPeriod = now.getTime() - beginningOfToday.getTime() + 1; // +1 !
    
    this.run(timestamp, targetPeriod);
  }

  /**
   * The definitive datacube is generated on yesterday, for 24 periods of 1 hour (except for some special days)
   * Rows will be timestamped at yesterday_XX:59:59.999+ZZZZ
   * Timestamp is the last millisecond of the period (therefore included).
   * This way it shows that *count* is computed for this hour but at the very end of it.
   */
  public void hourlyDefinitive()
  {
    Date now = SystemTime.getCurrentTime();
    Date yesterday = RLMDateUtils.addDays(now, -1, this.getTimeZone()); 
    Date beginningOfToday = RLMDateUtils.truncate(now, Calendar.DATE, this.getTimeZone());        // 00:00:00.000 
    Date endOfYesterday = RLMDateUtils.addMilliseconds(beginningOfToday, -1);                               // 23:59:59.999
    
    //
    // Run configurations
    //
    this.hourlyMode = true;
    this.targetDay = this.printDay(yesterday);
    this.targetTimestamp = this.printTimestamp(endOfYesterday);

    //
    // Timestamp & period
    //
    String timestamp = null; // Should not be used, will be override.
    long targetPeriod = 3600*1000; // 1 hour
    
    this.run(timestamp, targetPeriod);
  }
  
  /**
   * A preview is a datacube generation on the today's day. 
   * Timestamp is the last millisecond of the period (therefore included).
   * Because the day is still not ended, it won't be the definitive value of *count*.
   * Even for hour periods that had already ended, because ODR can still be added with timestamp of previous hours.
   */
  public void hourlyPreview()
  {
    Date now = SystemTime.getCurrentTime();
    Date tomorrow = RLMDateUtils.addDays(now, 1, this.getTimeZone()); 
    Date beginningOfTomorrow = RLMDateUtils.truncate(tomorrow, Calendar.DATE, this.getTimeZone());        // 00:00:00.000 
    Date endOfToday = RLMDateUtils.addMilliseconds(beginningOfTomorrow, -1);                                        // 23:59:59.999
    
    //
    // Run configurations
    //
    this.hourlyMode = true;
    this.targetDay = this.printDay(now);
    this.targetTimestamp = this.printTimestamp(endOfToday);

    //
    // Timestamp & period
    //
    String timestamp = null; // Should not be used, will be override.
    long targetPeriod = 3600*1000; // 1 hour
    
    this.run(timestamp, targetPeriod);
  }
}
