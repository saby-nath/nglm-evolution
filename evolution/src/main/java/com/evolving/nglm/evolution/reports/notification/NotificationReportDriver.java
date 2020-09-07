package com.evolving.nglm.evolution.reports.notification;

import java.util.Date;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.evolving.nglm.evolution.Report;
import com.evolving.nglm.evolution.reports.ReportDriver;
import com.evolving.nglm.evolution.reports.ReportUtils;

public class NotificationReportDriver extends ReportDriver
{
  private static final Logger log = LoggerFactory.getLogger(NotificationReportDriver.class);

  @Override public void produceReport(Report report, final Date reportGenerationDate, String zookeeper, String kafka, String elasticSearch, String csvFilename, String[] params)
  {
    log.debug("Processing " + report.getName());
    String esIndexNotif = "detailedrecords_messages-";
    String defaultReportPeriodUnit = report.getDefaultReportPeriodUnit();
    int defaultReportPeriodQuantity = report.getDefaultReportPeriodQuantity();
    NotificationReportMonoPhase.main(new String[] {elasticSearch, esIndexNotif, csvFilename, String.valueOf(defaultReportPeriodQuantity), defaultReportPeriodUnit }, reportGenerationDate);
    log.debug("Finished with BDR Report");
  }
}
