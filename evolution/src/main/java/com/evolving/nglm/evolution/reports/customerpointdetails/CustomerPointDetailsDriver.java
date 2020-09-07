package com.evolving.nglm.evolution.reports.customerpointdetails;

import com.evolving.nglm.evolution.Report;
import com.evolving.nglm.evolution.reports.FilterObject;
import com.evolving.nglm.evolution.reports.ReportDriver;
import com.evolving.nglm.evolution.reports.ReportUtils;
import com.evolving.nglm.evolution.reports.journeycustomerstates.JourneyCustomerStatesReportMonoPhase;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.TimeUnit;

public class CustomerPointDetailsDriver extends ReportDriver
{
  private static final Logger log = LoggerFactory.getLogger(CustomerPointDetailsDriver.class);
  public static final String SUBSCRIBER_ES_INDEX = "subscriberprofile";

  @Override
  public void produceReport(Report report, String zookeeper, String kafka, String elasticSearch, String csvFilename, String[] params)
  {
    log.debug("Processing CustomerPointDetails Report with "+report.getName());
    
    String defaultReportPeriodUnit = report.getDefaultReportPeriodUnit();
    int defaultReportPeriodQuantity = report.getDefaultReportPeriodQuantity();
    log.debug("data for report : " +SUBSCRIBER_ES_INDEX);

    log.debug("PHASE 1 : read ElasticSearch");
    log.trace(kafka+","+zookeeper+","+elasticSearch+","+SUBSCRIBER_ES_INDEX);
    
    
    CustomerPointDetailsMonoPhase.main(new String[]{
        elasticSearch, SUBSCRIBER_ES_INDEX, csvFilename, String.valueOf(defaultReportPeriodQuantity), defaultReportPeriodUnit
      });     
    
    log.debug("Finished with CustomerPointDetails Report");
  }

@Override
public List<FilterObject> reportFilters() {
	// TODO Auto-generated method stub
	return null;
}

}

