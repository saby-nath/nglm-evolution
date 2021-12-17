/*****************************************************************************
 *
 *  ContactPolicyConfigurationReportDriver.java
 *
 *****************************************************************************/

package com.evolving.nglm.evolution.reports.contactpolicyconfig;

import com.evolving.nglm.core.AlternateID;
import com.evolving.nglm.core.Deployment;
import com.evolving.nglm.core.JSONUtilities;
import com.evolving.nglm.core.NGLMRuntime;
import com.evolving.nglm.core.SystemTime;
import com.evolving.nglm.evolution.*;
import com.evolving.nglm.evolution.reports.FilterObject;
import com.evolving.nglm.evolution.reports.ReportDriver;
import com.evolving.nglm.evolution.reports.ReportUtils;
import com.evolving.nglm.evolution.reports.bdr.BDRReportMonoPhase;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class ContactPolicyConfigurationReportDriver extends ReportDriver 
{
  private static final Logger log = LoggerFactory.getLogger(ContactPolicyConfigurationReportDriver.class);
  private ContactPolicyService contactPolicyService;
  private SegmentContactPolicyService segmentContactPolicyService;
  private JourneyObjectiveService journeyObjectiveService;
  private static SegmentationDimensionService segmentationDimensionService = null;
  private boolean addHeaders=true;
  
  private static final String policyId = "policyId";
  private static final String policyName = "policyName";
  private static final String description = "description";
  private static final String active = "active";
  private static final String segments = "segments";
  private static final String journeyObjectives = "journeyObjectives";
  private static final String communicationChannelName = "communicationChannelName";
  private static final String limits = "limits";
  
  static List<String> headerFieldsOrder = new ArrayList<String>();
  static
  {
    headerFieldsOrder.add(policyId);
    headerFieldsOrder.add(policyName);
    headerFieldsOrder.add(description);
    headerFieldsOrder.add(active);
    headerFieldsOrder.add(segments);
    headerFieldsOrder.add(journeyObjectives);
    headerFieldsOrder.add(communicationChannelName);
    headerFieldsOrder.add(limits);
  }

  /****************************************
   * 
   * produceReport
   * 
   ****************************************/
  
  @Override public void produceReport(Report report, final Date reportGenerationDate, String zookeeper, String kafka, String elasticSearch, String csvFilename, String[] params, int tenantID)
  {
    log.info("Entered in to the contact policy produceReport");

    Random r = new Random();
    int apiProcessKey = r.nextInt(999);

    contactPolicyService = new ContactPolicyService(kafka, "contactPolicyConfigReportDriver-contactpolicyservice-" + apiProcessKey, Deployment.getContactPolicyTopic(), false);
    contactPolicyService.start();

    segmentContactPolicyService = new SegmentContactPolicyService(kafka, "contactPolicyConfigReportDriver-segmentcontactpolicyservice-" + apiProcessKey, Deployment.getSegmentContactPolicyTopic(), false);
    segmentContactPolicyService.start();

    journeyObjectiveService = new JourneyObjectiveService(kafka, "contactPolicyConfigReportDriver-journeyobjectiveservice-" + apiProcessKey, Deployment.getJourneyObjectiveTopic(), false);
    journeyObjectiveService.start();
    
    synchronized (log) // why not, this is a static object that always exists
    {
      if (segmentationDimensionService == null) // do it only once, because we can't stop it fully
        {
          segmentationDimensionService = new SegmentationDimensionService(kafka, "contactPolicyConfigReportDriver-segmentationDimensionservice-" + apiProcessKey, Deployment.getSegmentationDimensionTopic(), false);
          segmentationDimensionService.start(); // never stop it
        }
    }

    File file = new File(csvFilename+".zip");
    FileOutputStream fos = null;
    ZipOutputStream writer = null;
    try
    {

      fos = new FileOutputStream(file);
      writer = new ZipOutputStream(fos);
      ZipEntry entry = new ZipEntry(new File(csvFilename).getName()); // do not include tree structure in zipentry, just csv filename
      writer.putNextEntry(entry);
      if(contactPolicyService.getStoredContactPolicies(tenantID).size()==0)
        {
          if (headerFieldsOrder != null && !headerFieldsOrder.isEmpty())
            {
              String csvSeparator = ReportUtils.getSeparator();
              int offset = 1;
              String headers = "";
              for (String field : headerFieldsOrder)
                {
                  headers += field + csvSeparator;
                }
              headers = headers.substring(0, headers.length() - offset);
              writer.write(headers.getBytes());
              if (offset == 1)
                {
                  writer.write("\n".getBytes());
                }
            }
          log.info("No Contact Policies ");
        }
      else
        {
          for (GUIManagedObject guiManagedObject : contactPolicyService.getStoredContactPolicies(tenantID))
            {
              if(guiManagedObject instanceof ContactPolicy)
                {
                  ContactPolicy contactPolicy = (ContactPolicy) guiManagedObject;
                  JSONObject contactPolicyJSON = contactPolicyService.generateResponseJSON(guiManagedObject, true, SystemTime.getCurrentTime());
                  List<String> segmentIDs = contactPolicyService.getAllSegmentIDsUsingContactPolicy(contactPolicy.getContactPolicyID(), segmentContactPolicyService, tenantID);
                  List<String> segmentNames = new ArrayList<>();
                  for(String segmentID :segmentIDs) {
                    Segment segment = segmentationDimensionService.getSegment(segmentID, tenantID);
                    segmentNames.add(segment.getName());
                  }
                  List<String> journeyObjectives = contactPolicyService.getAllJourneyObjectiveNamesUsingContactPolicy(contactPolicy.getContactPolicyID(), journeyObjectiveService, tenantID);
                  contactPolicyJSON.put("segmentNames", JSONUtilities.encodeArray(segmentNames));
                  contactPolicyJSON.put("journeyObjectiveNames", JSONUtilities.encodeArray(journeyObjectives));
                  Map<String, Object> formattedFields = formatSimpleFields(contactPolicyJSON);
                  if(contactPolicy.getContactPolicyCommunicationChannels() != null) {
                    for(ContactPolicyCommunicationChannels channel : contactPolicy.getContactPolicyCommunicationChannels()) {
                      formattedFields.put(communicationChannelName, channel.getCommunicationChannelName());
                      
                      StringBuilder sbLimits = new StringBuilder();
                      String limits = null;
                      if(channel.getMessageLimits() != null && !(channel.getMessageLimits().isEmpty())) {
                        for(MessageLimits limit : channel.getMessageLimits()) {
                          sbLimits.append(limit.getMaxMessages()).append(" per ").append(limit.getDuration()).append(" ").append(limit.getTimeUnit()).append(",");
                        }
                        limits = sbLimits.toString().substring(0, sbLimits.toString().length()-1);
                      }  
                      formattedFields.put("limits", limits);
                      
                      try
                      {
                        dumpElementToCsv(formattedFields, writer);
                      } 
                      catch (Exception e)
                      {
                        log.info(e.getLocalizedMessage());
                      }
                    }
                  }
                }
            }
        } 
    }
    catch (IOException e)
    {
      e.printStackTrace();
    }
    finally 
    {
      contactPolicyService.stop();
      segmentContactPolicyService.stop();
      journeyObjectiveService.stop();

      try
      {
        if (writer != null) 
          {
            writer.flush();
            writer.closeEntry();
            writer.close();
          }
      }
      catch (IOException e)
      {
        log.info(e.getLocalizedMessage());
      }
      finally {
        try
        {
          if (fos != null) fos.close();
        }
        catch (IOException e)
        {
          log.info(e.getLocalizedMessage());
        }           
      }
    }
  }


  private void dumpElementToCsv(Map<String, Object> allFields, ZipOutputStream writer) throws Exception
  {
    String csvSeparator = ReportUtils.getSeparator();
    
    if (addHeaders)
      {
        String headers = "";
        for (String fields : allFields.keySet())
          {
            headers += fields + csvSeparator;
          }
        headers = headers.substring(0, headers.length() - 1);
        writer.write(headers.getBytes());
        writer.write("\n".getBytes());
        addHeaders = false;
      }
    
    String line = ReportUtils.formatResult(allFields);
    writer.write(line.getBytes());
  }
  
  private Map<String, Object> formatSimpleFields(JSONObject jsonObject)
  {
    Map<String, Object> result = new LinkedHashMap<>();
    result.put(policyId, jsonObject.get("id").toString());
    result.put(policyName, jsonObject.get("display").toString());
    if (jsonObject.get(description) != null)
      {
        result.put(description, jsonObject.get(description).toString());
      }
    else
      {
        result.put(description, "");
      }

    result.put(active, jsonObject.get(active).toString());
  
    
    StringBuilder sbSegments = new StringBuilder();
    String segments = null;
    if(jsonObject.get("segmentNames") != null) {
      JSONArray jsonArray = (JSONArray) jsonObject.get("segmentNames");
      if(!jsonArray.isEmpty()) {
        for(int i = 0; i<jsonArray.size(); i++) {
          sbSegments.append(jsonArray.get(i)).append(",");
        }
        segments = sbSegments.toString().substring(0, sbSegments.toString().length()-1);
      }
    }  
    
    StringBuilder sbObjectives = new StringBuilder();
    String journeyObjectives = null;
    if(jsonObject.get("journeyObjectiveNames") != null) {
      JSONArray jsonArray = (JSONArray) jsonObject.get("journeyObjectiveNames");
      if(!jsonArray.isEmpty()) {
        for(int i = 0; i<jsonArray.size(); i++) {
          sbObjectives.append(jsonArray.get(i)).append(",");
        }
        journeyObjectives = sbObjectives.toString().substring(0, sbObjectives.toString().length()-1);
      }
    }  
    
    result.put("segments", segments);
    result.put("journeyObjectives", journeyObjectives);
    
    return result;
  }
  

  @Override
  public List<FilterObject> reportFilters() {
    return null;
  }

  @Override
  public List<String> reportHeader() {
    List<String> result = ContactPolicyConfigurationReportDriver.headerFieldsOrder;
    return result;
  }
}
