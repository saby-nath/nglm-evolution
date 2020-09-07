package com.evolving.nglm.evolution.reports.loyaltyprogramcustomerstate;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.evolving.nglm.evolution.Report;
import com.evolving.nglm.evolution.reports.ReportDriver;
import java.util.Date;

public class LoyaltyProgramCustomerStatesDriver extends ReportDriver
{
  private static final Logger log = LoggerFactory.getLogger(LoyaltyProgramCustomerStatesDriver.class);

  @Override public void produceReport(Report report, final Date reportGenerationDate, String zookeeper, String kafka, String elasticSearch, String csvFilename, String[] params)
  {
    log.debug("Processing LoyaltyProgramCustomerStates Report with "+report.getName());
    String defaultReportPeriodUnit = report.getDefaultReportPeriodUnit();
    int defaultReportPeriodQuantity = report.getDefaultReportPeriodQuantity();
    final String SUBSCRIBER_ES_INDEX = getSubscriberProfileIndex(reportGenerationDate);
    log.debug("PHASE 1 : read ElasticSearch");
    LoyaltyProgramCustomerStatesMonoPhase.main(new String[]{elasticSearch, SUBSCRIBER_ES_INDEX, csvFilename, String.valueOf(defaultReportPeriodQuantity), defaultReportPeriodUnit}, reportGenerationDate);         
    log.debug("Finished with LoyaltyProgramCustomerStates Report");
  }

}
