package com.evolving.nglm.evolution.reports.bdr;

import java.util.ArrayList;
import java.util.List;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.evolving.nglm.evolution.Report;
import com.evolving.nglm.evolution.reports.ColumnType;
import com.evolving.nglm.evolution.reports.FilterObject;
import com.evolving.nglm.evolution.reports.ReportDriver;

public class BDRReportDriver extends ReportDriver{
  
private static final Logger log = LoggerFactory.getLogger(BDRReportDriver.class);

  @Override
  public void produceReport(
      Report report,
      String zookeeper,
      String kafka,
      String elasticSearch,
      String csvFilename,
      String[] params)
  {
    log.debug("Processing "+report.getName());
    String esIndexBdr = "detailedrecords_bonuses-";
    String defaultReportPeriodUnit = report.getDefaultReportPeriodUnit();
    int defaultReportPeriodQuantity = report.getDefaultReportPeriodQuantity();
    BDRReportMonoPhase.main(new String[]{
        elasticSearch, esIndexBdr, csvFilename, String.valueOf(defaultReportPeriodQuantity), defaultReportPeriodUnit 
    });         
    log.debug("Finished with BDR Report");
  }

  @Override
  public List<FilterObject> reportFilters() {
	  List<FilterObject> result = new ArrayList<>();
	  
	  result.add(new FilterObject(BDRReportMonoPhase.moduleName,         ColumnType.STRING, new String[] {"Journey_Manager", "Loyalty_Program"}));
	  result.add(new FilterObject(BDRReportMonoPhase.deliverableDisplay, ColumnType.STRING, new String[] {"OnNetMinutes"}));
	  
	  return result;
  }
}
