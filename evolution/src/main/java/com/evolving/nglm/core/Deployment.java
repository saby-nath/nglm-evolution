/****************************************************************************
*
*  Deployment.java
*
****************************************************************************/

package com.evolving.nglm.core;

import com.evolving.nglm.evolution.BillingMode;
import com.evolving.nglm.evolution.CommunicationChannelTimeWindow;
import com.evolving.nglm.evolution.CriterionContext;
import com.evolving.nglm.evolution.EvaluationCriterion;
import com.evolving.nglm.evolution.GUIManagedObject;
import com.evolving.nglm.evolution.PropensityRule;
import com.evolving.nglm.evolution.ScheduledJobConfiguration;
import com.evolving.nglm.evolution.SupportedCurrency;
import com.evolving.nglm.evolution.SupportedLanguage;
import com.evolving.nglm.evolution.SupportedTimeUnit;
import com.evolving.nglm.evolution.GUIManager.GUIManagerException;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.PrintWriter;
import java.io.StringWriter;

import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** 
 * This class should not contain any static variable. 
 * All settings put here can be override by each tenant.
 *
 * Deployment.getDefault().getX() allow to retrieve the default value of one setting (from tenant 0).
 * Deployment.getDeployment(4).getX() allow to retrieve the specific value of one setting for tenant 4 (retrieve the default one otherwise) 
 */
public class Deployment extends DeploymentCommon
{
  /*****************************************
  *
  * Static data
  *
  *****************************************/
  protected static final Logger log = LoggerFactory.getLogger(Deployment.class);
  
  /*****************************************
  *
  * Properties
  *
  *****************************************/
  //
  // Local environment
  //
  private String timeZone;
  private ZoneId zoneId;
  private String language;
  private String country;
  private Map<String,SupportedLanguage> supportedLanguages;
  private String baseLanguageID;
  
  //
  // Elasticsearch
  //
  private int elasticsearchRetentionDaysJourneys;
  private int elasticsearchRetentionDaysCampaigns;
  private int elasticsearchRetentionDaysBulkCampaigns;
  private int elasticsearchRetentionDaysExpiredVouchers; 
  private Map<String,ScheduledJobConfiguration> datacubeJobsScheduling;
  private Map<String,ScheduledJobConfiguration> elasticsearchJobsScheduling;
  
  //
  // Others
  //
  private CommunicationChannelTimeWindow defaultNotificationTimeWindowsMap;
  private Map<String,BillingMode> billingModes;
  private Map<String,SupportedCurrency> supportedCurrencies;
  private Map<String,SupportedTimeUnit> supportedTimeUnits;
  private List<EvaluationCriterion> journeyUniversalEligibilityCriteria;
  private PropensityRule propensityRule;
  
  
  /*****************************************
  *
  * Getters
  *
  *****************************************/  
  //
  // Local environment
  //
  public String getTimeZone() { return timeZone; }
  public ZoneId getZoneId() { return zoneId; }
  public Map<String,SupportedLanguage> getSupportedLanguages() { return supportedLanguages; }
  public String getSupportedLanguageID(String language){ return Deployment.getSupportedLanguageID(language, getSupportedLanguages()); }
  public String getLanguage() { return language; }
  public String getLanguageID() { return baseLanguageID; }
  public String getCountry() { return country; }
  
  //
  // Elasticsearch
  //
  public int getElasticsearchRetentionDaysJourneys() { return elasticsearchRetentionDaysJourneys; }
  public int getElasticsearchRetentionDaysCampaigns() { return elasticsearchRetentionDaysCampaigns; }
  public int getElasticsearchRetentionDaysBulkCampaigns() { return elasticsearchRetentionDaysBulkCampaigns; }
  public int getElasticsearchRetentionDaysExpiredVouchers() { return elasticsearchRetentionDaysExpiredVouchers; }  
  public Map<String,ScheduledJobConfiguration> getDatacubeJobsScheduling() { return datacubeJobsScheduling; }
  public Map<String,ScheduledJobConfiguration> getElasticsearchJobsScheduling() { return elasticsearchJobsScheduling; }
  
  //
  // Others
  //
  public CommunicationChannelTimeWindow getDefaultNotificationDailyWindows() { return defaultNotificationTimeWindowsMap; }
  public Map<String,BillingMode> getBillingModes() { return billingModes; }
  public Map<String,SupportedCurrency> getSupportedCurrencies() { return supportedCurrencies; }
  public Map<String,SupportedTimeUnit> getSupportedTimeUnits() { return supportedTimeUnits; }
  public List<EvaluationCriterion> getJourneyUniversalEligibilityCriteria() { return journeyUniversalEligibilityCriteria; } 
  public PropensityRule getPropensityRule() { return propensityRule; }
  
  /*****************************************
  *
  * Constructor (needs to be empty for newInstance calls)
  *
  *****************************************/  
  public Deployment(){}
  
  /*****************************************
  *
  * loaders
  *
  *****************************************/
  // This method needs to be overriden in nglm-project
  protected void loadProjectTenantSettings(DeploymentJSONReader jsonReader, int tenantID) throws Exception {
    throw new ServerRuntimeException("loadProjectTenantSettings methods needs to be overriden in your project Deployment class.");
  }
  
  protected void loadProductTenantSettings(DeploymentJSONReader jsonReader, int tenantID) throws Exception
  {
    //
    // Local information
    //
    timeZone = jsonReader.decodeString("timeZone");
    zoneId = ZoneId.of(timeZone);
    language = jsonReader.decodeString("language");
    country = jsonReader.decodeString("country");
    supportedLanguages = jsonReader.decodeMapFromArray(SupportedLanguage.class, "supportedLanguages");
    baseLanguageID = getSupportedLanguageID(getLanguage(), supportedLanguages);
    billingModes = jsonReader.decodeMapFromArray(BillingMode.class, "billingModes");
    supportedCurrencies = jsonReader.decodeMapFromArray(SupportedCurrency.class, "supportedCurrencies");
    supportedTimeUnits = jsonReader.decodeMapFromArray(SupportedTimeUnit.class, "supportedTimeUnits");
    
    //
    // Elasticsearch settings
    //
    elasticsearchRetentionDaysJourneys = jsonReader.decodeInteger("ESRetentionDaysJourneys");
    elasticsearchRetentionDaysCampaigns = jsonReader.decodeInteger("ESRetentionDaysCampaigns");
    elasticsearchRetentionDaysBulkCampaigns = jsonReader.decodeInteger("ESRetentionDaysBulkCampaigns");
    elasticsearchRetentionDaysExpiredVouchers = jsonReader.decodeInteger("ESRetentionDaysExpiredVouchers");
    
    // Datacubes jobs
    if(tenantID == 0) {
      datacubeJobsScheduling = null; // because datacube jobs make no sense for "tenant 0".
    } else {
      datacubeJobsScheduling = new LinkedHashMap<String,ScheduledJobConfiguration>();
      DeploymentJSONReader datacubeJobsSchedulingJSON = jsonReader.get("datacubeJobsScheduling");
      for (Object key : datacubeJobsSchedulingJSON.keySet()) {
        // Change jobID (add tenantID) otherwise they will override themselves in DatacubeManager.
        String newKey = "T" + tenantID + "_" + ((String) key);
        datacubeJobsScheduling.put(newKey, new ScheduledJobConfiguration(newKey, datacubeJobsSchedulingJSON.get(key), tenantID, timeZone));
      }
    }
    
    // Elasticsearch jobs
    if(tenantID == 0) {
      elasticsearchJobsScheduling = null; // because elasticsearch jobs make no sense for "tenant 0".
    } else {
      elasticsearchJobsScheduling = new LinkedHashMap<String,ScheduledJobConfiguration>();
      DeploymentJSONReader elasticsearchJobsSchedulingJSON = jsonReader.get("elasticsearchJobsScheduling");
      for (Object key : elasticsearchJobsSchedulingJSON.keySet()) {
        String newKey = "T" + tenantID + "_" + ((String) key); // TODO EVPRO-99 by tenant ????
        elasticsearchJobsScheduling.put(newKey, new ScheduledJobConfiguration(newKey, elasticsearchJobsSchedulingJSON.get(key), tenantID, timeZone));
      }
    }
    
    //
    // Others 
    //
  
    //  notificationDailyWindows
    JSONObject defaultTimeWindowJSON = jsonReader.decodeJSONObject("notificationDailyWindows");
    defaultTimeWindowJSON.put("id", "default");
    defaultTimeWindowJSON.put("name", "default");
    defaultTimeWindowJSON.put("display", "default");
    defaultTimeWindowJSON.put("active", true);
    defaultTimeWindowJSON.put("communicationChannelID", "default");
    defaultNotificationTimeWindowsMap = new CommunicationChannelTimeWindow(defaultTimeWindowJSON, System.currentTimeMillis() * 1000, null, tenantID);
    
    propensityRule = new PropensityRule(jsonReader.decodeJSONObject("propensityRule"));
    
    //  journeyUniversalEligibilityCriteria
    journeyUniversalEligibilityCriteria = new ArrayList<>();
    JSONArray evaluationCriterionValues = jsonReader.decodeJSONArray("journeyUniversalEligibilityCriteria");
    for (int i=0; i<evaluationCriterionValues.size(); i++)
      {
        JSONObject evaluationCriterionJSON = (JSONObject) evaluationCriterionValues.get(i);
        EvaluationCriterion evaluationCriterion = new EvaluationCriterion(evaluationCriterionJSON, CriterionContext.Profile(tenantID), tenantID);
        getJourneyUniversalEligibilityCriteria().add(evaluationCriterion);                  
      }
  }
}
