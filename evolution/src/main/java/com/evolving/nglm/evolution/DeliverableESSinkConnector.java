/****************************************************************************
*
*  OfferESSinkConnector.java
*
****************************************************************************/

package com.evolving.nglm.evolution;

import java.util.HashMap;
import java.util.Map;

import org.apache.kafka.connect.connector.Task;
import org.apache.kafka.connect.data.Schema;
import org.apache.kafka.connect.data.SchemaAndValue;
import org.apache.kafka.connect.sink.SinkRecord;

import com.evolving.nglm.core.ChangeLogESSinkTask;
import com.evolving.nglm.core.Deployment;
import com.evolving.nglm.core.SimpleESSinkConnector;
import com.evolving.nglm.evolution.GUIManager.GUIManagerException;

@Deprecated
public class DeliverableESSinkConnector extends SimpleESSinkConnector
{
  /****************************************
  *
  *  taskClass
  *
  ****************************************/
  
  @Override public Class<? extends Task> taskClass()
  {
    DynamicCriterionFieldService dynamicCriterionFieldService = new DynamicCriterionFieldService(Deployment.getBrokerServers(), "loyaltyprogramessinkconnector-dynamiccriterionfieldservice-001", Deployment.getDynamicCriterionFieldTopic(), false);
    dynamicCriterionFieldService.start();
    CriterionContext.initialize(dynamicCriterionFieldService);
    return DeliverableESSinkTask.class;
  }

  /****************************************
  *
  *  taskClass
  *
  ****************************************/
  @Deprecated
  public static class DeliverableESSinkTask extends ChangeLogESSinkTask<Deliverable>
  {
    @Override public Deliverable unpackRecord(SinkRecord sinkRecord) 
    {
      Object guiManagedObjectValue = sinkRecord.value();
      Schema guiManagedObjectValueSchema = sinkRecord.valueSchema();
      GUIManagedObject guiManagedObject = GUIManagedObject.commonSerde().unpack(new SchemaAndValue(guiManagedObjectValueSchema, guiManagedObjectValue));
      if(guiManagedObject instanceof Deliverable) {
        return (Deliverable) guiManagedObject;
      } else {
        return null;
      }
    }
    
    @Override
    public String getDocumentID(Deliverable deliverable) {
      return "_" + deliverable.getDeliverableID().hashCode();    
    }
  
    @Override public Map<String,Object> getDocumentMap(Deliverable deliverable)
    {
      Map<String,Object> documentMap = new HashMap<String,Object>();
      documentMap.put("deliverableID", deliverable.getDeliverableID());
      documentMap.put("deliverableName", deliverable.getDeliverableDisplay());
      documentMap.put("deliverableActive", deliverable.getActive());
      documentMap.put("deliverableProviderID", deliverable.getFulfillmentProviderID());
      
      return documentMap;
    }
  }
}
