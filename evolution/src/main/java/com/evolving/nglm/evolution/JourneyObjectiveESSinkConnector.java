package com.evolving.nglm.evolution;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import org.apache.kafka.connect.data.Schema;
import org.apache.kafka.connect.data.SchemaAndValue;
import org.apache.kafka.connect.data.Struct;
import org.apache.kafka.connect.sink.SinkRecord;
import org.json.simple.JSONObject;

import com.evolving.nglm.core.ChangeLogESSinkTask;
import com.evolving.nglm.core.NGLMRuntime;
import com.evolving.nglm.core.RLMDateUtils;
import com.evolving.nglm.core.SimpleESSinkConnector;
import com.evolving.nglm.core.StreamESSinkTask;
import com.evolving.nglm.core.SystemTime;
import com.evolving.nglm.evolution.EvolutionUtilities.RoundingSelection;
import com.evolving.nglm.evolution.EvolutionUtilities.TimeUnit;
import com.evolving.nglm.evolution.GUIManagedObject.GUIManagedObjectType;
import com.evolving.nglm.evolution.PurchaseFulfillmentManager.PurchaseFulfillmentRequest;
import com.evolving.nglm.evolution.datacubes.DatacubeGenerator;

public class JourneyObjectiveESSinkConnector extends SimpleESSinkConnector
{
  private static DynamicCriterionFieldService dynamicCriterionFieldService;
  private static ContactPolicyService contactPolicyService;
  private static JourneyObjectiveService journeyObjectiveService;
  
  /****************************************
  *
  *  taskClass
  *
  ****************************************/
  
  @Override public Class<JourneyObjectiveESSinkConnectorTask> taskClass()
  {
    return JourneyObjectiveESSinkConnectorTask.class;
  }

  /****************************************
  *
  *  taskClass
  *
  ****************************************/
  
  public static class JourneyObjectiveESSinkConnectorTask extends ChangeLogESSinkTask<JourneyObjective>
  {
    private static String elasticSearchDateFormat = com.evolving.nglm.core.Deployment.getElasticsearchDateFormat();
    private DateFormat dateFormat = new SimpleDateFormat(elasticSearchDateFormat);

    /*****************************************
    *
    *  start
    *
    *****************************************/

    @Override public void start(Map<String, String> taskConfig)
    {
      //
      //  super
      //

      super.start(taskConfig);
    
      //
      //  services
      //
   
      dynamicCriterionFieldService = new DynamicCriterionFieldService(Deployment.getBrokerServers(), "odrsinkconnector-dynamiccriterionfieldservice-" + getTaskNumber(), Deployment.getDynamicCriterionFieldTopic(), false);
      CriterionContext.initialize(dynamicCriterionFieldService);
      dynamicCriterionFieldService.start();      
      
      contactPolicyService = new ContactPolicyService(Deployment.getBrokerServers(), "journeyobjectivesinkconnector-contactpolicyservice-" + Integer.toHexString((new Random()).nextInt(1000000000)), Deployment.getContactPolicyTopic(), false);
      contactPolicyService.start();
      journeyObjectiveService = new JourneyObjectiveService(Deployment.getBrokerServers(), "journeyobjectivesinkconnector-journeyObjectiveServiceservice-" + Integer.toHexString((new Random()).nextInt(1000000000)), Deployment.getJourneyObjectiveTopic(), false);
      journeyObjectiveService.start();
    }

    /*****************************************
    *
    *  stop
    *
    *****************************************/

    @Override public void stop()
    {
      //
      //  services
      //

      contactPolicyService.stop();
      journeyObjectiveService.stop();
      
      //
      //  super
      //

      super.stop();
    }

    /*****************************************
    *
    *  unpackRecord
    *
    *****************************************/
    
    @Override public JourneyObjective unpackRecord(SinkRecord sinkRecord) 
    {
      Object journeyObjectiveValue = sinkRecord.value();
      Schema journeyObjectiveValueSchema = sinkRecord.valueSchema();
      return JourneyObjective.unpack(new SchemaAndValue(JourneyObjective.schema(), ((Struct) journeyObjectiveValue).get("journey_objective"))); 
    }
    
    
    /*****************************************
    *
    *  getDocumentMap
    *
    *****************************************/
    
    @Override
    public Map<String, Object> getDocumentMap(JourneyObjective journeyObjective)
    {
      Date now = SystemTime.getCurrentTime();
      Map<String,Object> documentMap = new HashMap<String,Object>();
           
      // We read all data from JSONRepresentation()
      // because native data in object is sometimes not correct
      
      JSONObject jr = journeyObjective.getJSONRepresentation();
      documentMap.put("id",      jr.get("id"));
      documentMap.put("display", jr.get("display"));
      String contactPolicyID = (String) jr.get("contactPolicyID");
      ContactPolicy contactPolicy = contactPolicyService.getActiveContactPolicy(contactPolicyID, now);
      documentMap.put("contactPolicy", (contactPolicy == null) ? "" : contactPolicy.getGUIManagedObjectDisplay());
      documentMap.put("timestamp",     RLMDateUtils.printTimestamp(SystemTime.getCurrentTime()));
      
      return documentMap;
    }

    @Override
    public String getDocumentID(JourneyObjective journeyObjective)
    {
      return journeyObjective.getJourneyObjectiveID();
    }
  }
}

