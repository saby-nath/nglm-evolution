/****************************************************************************
 *
 *  SubscriberReportMonoDriver.java 
 *
 ****************************************************************************/

package com.evolving.nglm.evolution.reports.subscriber;

import java.util.Date;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.evolving.nglm.evolution.Report;
import com.evolving.nglm.evolution.reports.FilterObject;
import com.evolving.nglm.evolution.reports.ReportDriver;
import com.evolving.nglm.evolution.reports.ReportDriver.ReportTypeDef;
import com.evolving.nglm.evolution.reports.journeys.JourneysReportDriver;

@ReportTypeDef(reportType = "subscriberprofile")
public class SubscriberReportDriver extends ReportDriver
{
  private static final Logger log = LoggerFactory.getLogger(SubscriberReportDriver.class);

  @Override public void produceReport(Report report, final Date reportGenerationDate, String zookeeper, String kafka, String elasticSearch, String csvFilename, String[] params, int tenantID)
  {
    log.debug("Processing Subscriber Report with " + report.getName());
    String esIndexSubscriber = getSubscriberProfileIndex(reportGenerationDate);
    SubscriberReportMonoPhase.main(new String[] { kafka, elasticSearch, esIndexSubscriber, csvFilename, tenantID+"" }, reportGenerationDate);
    log.debug("Finished with Subscriber Report");
  }

	@Override
	public List<FilterObject> reportFilters() {
		return null;
	}

	@Override
	public List<String> reportHeader() {
	  List<String> result = SubscriberReportMonoPhase.headerFieldsOrder;
	  return result;
	}
}