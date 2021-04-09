/*****************************************************************************
*
*  DynamicCriterionFieldService.java
*
*****************************************************************************/

package com.evolving.nglm.evolution;

import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import org.apache.kafka.clients.producer.KafkaProducer;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.evolving.nglm.core.ServerRuntimeException;
import com.evolving.nglm.core.SystemTime;
import com.evolving.nglm.evolution.EvaluationCriterion.CriterionDataType;
import com.evolving.nglm.evolution.GUIManager.GUIManagerException;
import com.evolving.nglm.evolution.LoyaltyProgram.LoyaltyProgramType;
import com.evolving.nglm.evolution.LoyaltyProgramPoints.LoyaltyProgramTierChange;
import com.evolving.nglm.evolution.LoyaltyProgramPoints.Tier;
import com.evolving.nglm.evolution.complexobjects.ComplexObjectType;
import com.evolving.nglm.evolution.complexobjects.ComplexObjectTypeSubfield;

public class DynamicCriterionFieldService extends GUIService
{
  /*****************************************
  *
  *  configuration
  *
  *****************************************/

  private static final Logger log = LoggerFactory.getLogger(DynamicCriterionFieldService.class);

  /*****************************************
  *
  *  data
  *
  *****************************************/

  private DynamicCriterionFieldListener dynamicCriterionFieldListener = null;

  /*****************************************
  *
  *  constructor
  *
  *****************************************/

  @Deprecated // groupID not needed
  public DynamicCriterionFieldService(String bootstrapServers, String groupID, String dynamicCriterionFieldTopic, boolean masterService, DynamicCriterionFieldListener dynamicCriterionFieldListener, boolean notifyOnSignificantChange)
  {
    super(bootstrapServers, "DynamicCriterionFieldService", groupID, dynamicCriterionFieldTopic, masterService, getSuperListener(dynamicCriterionFieldListener), "putDynamicCriterionField", "removeDynamicCriterionField", notifyOnSignificantChange);
  }

  //
  //  constructor
  //

  @Deprecated // groupID not needed
  public DynamicCriterionFieldService(String bootstrapServers, String groupID, String dynamicCriterionFieldTopic, boolean masterService, DynamicCriterionFieldListener dynamicCriterionFieldListener)
  {
    this(bootstrapServers, groupID, dynamicCriterionFieldTopic, masterService, dynamicCriterionFieldListener, true);
  }

  //
  //  constructor
  //

  @Deprecated // groupID not needed
  public DynamicCriterionFieldService(String bootstrapServers, String groupID, String dynamicCriterionFieldTopic, boolean masterService)
  {
    this(bootstrapServers, groupID, dynamicCriterionFieldTopic, masterService, (DynamicCriterionFieldListener) null, true);
  }

  //
  //  getSuperListener
  //

  private static GUIManagedObjectListener getSuperListener(DynamicCriterionFieldListener dynamicCriterionFieldListener)
  {
    GUIManagedObjectListener superListener = null;
    if (dynamicCriterionFieldListener != null)
      {
        superListener = new GUIManagedObjectListener()
        {
          @Override public void guiManagedObjectActivated(GUIManagedObject guiManagedObject) { dynamicCriterionFieldListener.dynamicCriterionFieldActivated((DynamicCriterionField) guiManagedObject); }
          @Override public void guiManagedObjectDeactivated(String guiManagedObjectID) { dynamicCriterionFieldListener.dynamicCriterionFieldDeactivated(guiManagedObjectID); }
        };
      }
    return superListener;
  }

  /*****************************************
  *
  *  getSummaryJSONRepresentation
  *
  *****************************************/

  @Override protected JSONObject getSummaryJSONRepresentation(GUIManagedObject guiManagedObject) { throw new UnsupportedOperationException(); }
  
  /*****************************************
  *
  *  accessors
  *
  *****************************************/

  public String generateDynamicCriterionFieldID() { return generateGUIManagedObjectID(); }
  public GUIManagedObject getStoredDynamicCriterionField(String dynamicCriterionFieldID) { return getStoredGUIManagedObject(dynamicCriterionFieldID); }
  public Collection<GUIManagedObject> getStoredDynamicCriterionFields() { return getStoredGUIManagedObjects(); }
  public boolean isActiveDynamicCriterionField(GUIManagedObject dynamicCriterionFieldUnchecked, Date date) { return isActiveGUIManagedObject(dynamicCriterionFieldUnchecked, date); }
  public DynamicCriterionField getActiveDynamicCriterionField(String dynamicCriterionFieldID, Date date) { return (DynamicCriterionField) getActiveGUIManagedObject(dynamicCriterionFieldID, date); }
  public Collection<DynamicCriterionField> getActiveDynamicCriterionFields(Date date) { return (Collection<DynamicCriterionField>) getActiveGUIManagedObjects(date); }

  /*****************************************
  *
  *  addLoyaltyProgramCriterionFields
  *
  *****************************************/

  public void addLoyaltyProgramCriterionFields(LoyaltyProgram loyaltyProgram, boolean newLoyaltyProgram) throws GUIManagerException
  {
    addLoyaltyProgramCriterionField(loyaltyProgram, newLoyaltyProgram, "tier", CriterionDataType.StringCriterion, generateAvailableValuesForTier(loyaltyProgram));
    if (loyaltyProgram instanceof LoyaltyProgramPoints)
      {
        LoyaltyProgramPoints loyaltyProgramPoints = (LoyaltyProgramPoints) loyaltyProgram;
        addLoyaltyProgramCriterionField(loyaltyProgram, newLoyaltyProgram, "rewardpoint.balance", CriterionDataType.IntegerCriterion, null);
        addLoyaltyProgramCriterionField(loyaltyProgram, newLoyaltyProgram, "statuspoint.balance", CriterionDataType.IntegerCriterion, null);
        String statusPointID = loyaltyProgramPoints.getStatusPointsID();
        String rewardPointID = loyaltyProgramPoints.getRewardPointsID();
        addLoyaltyProgramCriterionField(loyaltyProgram, newLoyaltyProgram, "statuspoint.earliestexpirydate",     "statuspoint." + statusPointID + ".earliestexpirydate",     CriterionDataType.DateCriterion, null);
        addLoyaltyProgramCriterionField(loyaltyProgram, newLoyaltyProgram, "rewardpoint.earliestexpirydate",     "rewardpoint." + rewardPointID + ".earliestexpirydate",     CriterionDataType.DateCriterion, null);
        addLoyaltyProgramCriterionField(loyaltyProgram, newLoyaltyProgram, "statuspoint.earliestexpiryquantity", "statuspoint." + statusPointID + ".earliestexpiryquantity", CriterionDataType.IntegerCriterion, null);
        addLoyaltyProgramCriterionField(loyaltyProgram, newLoyaltyProgram, "rewardpoint.earliestexpiryquantity", "rewardpoint." + rewardPointID + ".earliestexpiryquantity", CriterionDataType.IntegerCriterion, null);
        addLoyaltyProgramCriterionField(loyaltyProgram, newLoyaltyProgram, "tierupdatedate", CriterionDataType.DateCriterion, null);
        addLoyaltyProgramCriterionField(loyaltyProgram, newLoyaltyProgram, "optindate", CriterionDataType.DateCriterion, null);
        addLoyaltyProgramCriterionField(loyaltyProgram, newLoyaltyProgram, "optoutdate", CriterionDataType.DateCriterion, null);
      }    
  }
  /*****************************************
  *
  *  addLoyaltyProgramCriterionField with criterionFieldInternalBaseName = criterionFieldBaseName
  *
  *****************************************/

  private void addLoyaltyProgramCriterionField(LoyaltyProgram loyaltyProgram, boolean newLoyaltyProgram, String criterionFieldBaseName, CriterionDataType criterionDataType, JSONArray availableValues) throws GUIManagerException
  {
    addLoyaltyProgramCriterionField(loyaltyProgram, newLoyaltyProgram, criterionFieldBaseName, criterionFieldBaseName, criterionDataType, availableValues);
  }

  /*****************************************
  *
  *  addLoyaltyProgramCriterionField
  *
  *****************************************/

  private void addLoyaltyProgramCriterionField(LoyaltyProgram loyaltyProgram, boolean newLoyaltyProgram, String criterionFieldBaseName, String criterionFieldInternalBaseName, CriterionDataType criterionDataType, JSONArray availableValues) throws GUIManagerException
  {
    //
    //  json constructor
    //

    JSONObject criterionFieldJSON = new JSONObject();
    String id = "loyaltyprogram" + "." + loyaltyProgram.getLoyaltyProgramID() + "." + criterionFieldInternalBaseName;
    criterionFieldJSON.put("id", id);
    criterionFieldJSON.put("display", "Loyalty Program " + loyaltyProgram.getGUIManagedObjectDisplay() + " " + criterionFieldBaseName);
    criterionFieldJSON.put("epoch", loyaltyProgram.getEpoch());
    criterionFieldJSON.put("dataType", criterionDataType.getExternalRepresentation());
    criterionFieldJSON.put("tagFormat", null);
    criterionFieldJSON.put("tagMaxLength", null);
    criterionFieldJSON.put("esField", id);
    criterionFieldJSON.put("retriever", "getLoyaltyProgramCriterionField");
    criterionFieldJSON.put("minValue", null);
    criterionFieldJSON.put("maxValue", null);
    criterionFieldJSON.put("availableValues", availableValues);
    criterionFieldJSON.put("includedOperators", null);
    criterionFieldJSON.put("excludedOperators", null);
    criterionFieldJSON.put("includedComparableFields", null); 
    criterionFieldJSON.put("excludedComparableFields", null);
    DynamicCriterionField criterionField = new DynamicCriterionField(loyaltyProgram, criterionFieldJSON);

    //
    //  put
    //

    putGUIManagedObject(criterionField, SystemTime.getCurrentTime(), newLoyaltyProgram, null);
  }

  /*****************************************
  *
  *  generateAvailableValuesForTier
  *
  *****************************************/

  private JSONArray generateAvailableValuesForTier(LoyaltyProgram loyaltyProgram)
  {
    JSONArray availableValuesField = new JSONArray();
    switch (loyaltyProgram.getLoyaltyProgramType())
      {
        case POINTS:
          for (Tier tier : ((LoyaltyProgramPoints) loyaltyProgram).getTiers())
            {
              availableValuesField.add(tier.getTierName());  
            }
          break;
      }
    return availableValuesField;
  }
  

  /*****************************************
  *
  *  removeLoyaltyProgramCriterionFields
  *
  *****************************************/

  public void removeLoyaltyProgramCriterionFields(GUIManagedObject loyaltyProgram)
  {
    String prefix = "loyaltyprogram" + "." + loyaltyProgram.getGUIManagedObjectID() + ".";
    removeDynamicCriterionField(prefix + "tier", null);
    removeDynamicCriterionField(prefix + "rewardpoint.balance", null);
    removeDynamicCriterionField(prefix + "statuspoint.balance", null);
    
    if (loyaltyProgram instanceof LoyaltyProgramPoints)
      {
        LoyaltyProgramPoints loyaltyProgramPoints = (LoyaltyProgramPoints) loyaltyProgram;
        String statusPointID = loyaltyProgramPoints.getStatusPointsID();
        String rewardPointID = loyaltyProgramPoints.getRewardPointsID();
        removeDynamicCriterionField(prefix + "statuspoint." + statusPointID + ".earliestexpirydate", null);
        removeDynamicCriterionField(prefix + "rewardpoint." + rewardPointID + ".earliestexpirydate", null);
        removeDynamicCriterionField(prefix + "statuspoint." + statusPointID + ".earliestexpiryquantity", null);
        removeDynamicCriterionField(prefix + "rewardpoint." + rewardPointID + ".earliestexpiryquantity", null);
      }
  }

  /*****************************************
  *
  *  addPointCriterionFields
  *
  *****************************************/

  public void addPointCriterionFields(Point point, boolean newPoint) throws GUIManagerException
  {
    addPointCriterionField(point, newPoint, "balance", CriterionDataType.IntegerCriterion, null);
    addPointCriterionField(point, newPoint, "earliestexpirydate", CriterionDataType.DateCriterion, null);
    addPointCriterionField(point, newPoint, "earliestexpiryquantity", CriterionDataType.IntegerCriterion, null);

    addPointMetricsCriterionFields(point, newPoint, "earned");
    addPointMetricsCriterionFields(point, newPoint, "consumed");
    addPointMetricsCriterionFields(point, newPoint, "expired");
  }
  
  private void addPointMetricsCriterionFields(Point point, boolean newPoint, String nature) throws GUIManagerException
  {
    addPointCriterionField(point, newPoint, nature+".yesterday", CriterionDataType.IntegerCriterion, null);
    addPointCriterionField(point, newPoint, nature+".last7days", CriterionDataType.IntegerCriterion, null);
    addPointCriterionField(point, newPoint, nature+".last30days", CriterionDataType.IntegerCriterion, null);
    addPointCriterionField(point, newPoint, nature+".today", CriterionDataType.IntegerCriterion, null);
    addPointCriterionField(point, newPoint, nature+".thisWeek", CriterionDataType.IntegerCriterion, null);
    addPointCriterionField(point, newPoint, nature+".thisMonth", CriterionDataType.IntegerCriterion, null);
  }

  /*****************************************
  *
  *  addPointCriterionField
  *
  *****************************************/

  private void addPointCriterionField(Point point, boolean newPoint, String criterionFieldBaseName, CriterionDataType criterionDataType, JSONArray availableValues) throws GUIManagerException
  {
    //
    //  json constructor
    //

    JSONObject criterionFieldJSON = new JSONObject();
    String id = "point" + "." + point.getPointID() + "." + criterionFieldBaseName;
    criterionFieldJSON.put("id", id);
    criterionFieldJSON.put("display", "Point " + point.getGUIManagedObjectDisplay() + " " + criterionFieldBaseName);
    criterionFieldJSON.put("epoch", point.getEpoch());
    criterionFieldJSON.put("dataType", criterionDataType.getExternalRepresentation());
    criterionFieldJSON.put("tagFormat", null);
    criterionFieldJSON.put("tagMaxLength", null);
    criterionFieldJSON.put("esField", id);
    criterionFieldJSON.put("retriever", "getPointCriterionField");
    criterionFieldJSON.put("minValue", null);
    criterionFieldJSON.put("maxValue", null);
    criterionFieldJSON.put("availableValues", availableValues);
    criterionFieldJSON.put("includedOperators", null);
    criterionFieldJSON.put("excludedOperators", null);
    criterionFieldJSON.put("includedComparableFields", null); 
    criterionFieldJSON.put("excludedComparableFields", null);
    DynamicCriterionField criterionField = new DynamicCriterionField(point, criterionFieldJSON);

    //
    //  put
    //

    putGUIManagedObject(criterionField, SystemTime.getCurrentTime(), newPoint, null);
  }

  /*****************************************
  *
  *  removePointCriterionFields
  *
  *****************************************/

  public void removePointCriterionFields(GUIManagedObject point)
  {
    removeDynamicCriterionField("point" + "." + point.getGUIManagedObjectID() + "." + "balance", null);
    removeDynamicCriterionField("point" + "." + point.getGUIManagedObjectID() + "." + "earliestexpirydate", null);
    removeDynamicCriterionField("point" + "." + point.getGUIManagedObjectID() + "." + "earliestexpiryquantity", null);
  
    removeDynamicCriterionField("point" + "." + point.getGUIManagedObjectID() + "." + "earned.yesterday", null);
    removeDynamicCriterionField("point" + "." + point.getGUIManagedObjectID() + "." + "earned.last7days", null);
    removeDynamicCriterionField("point" + "." + point.getGUIManagedObjectID() + "." + "earned.last30days", null);
    removeDynamicCriterionField("point" + "." + point.getGUIManagedObjectID() + "." + "earned.today", null);
    removeDynamicCriterionField("point" + "." + point.getGUIManagedObjectID() + "." + "earned.thisWeek", null);
    removeDynamicCriterionField("point" + "." + point.getGUIManagedObjectID() + "." + "earned.thisMonth", null);

    removeDynamicCriterionField("point" + "." + point.getGUIManagedObjectID() + "." + "consumed.yesterday", null);
    removeDynamicCriterionField("point" + "." + point.getGUIManagedObjectID() + "." + "consumed.last7days", null);
    removeDynamicCriterionField("point" + "." + point.getGUIManagedObjectID() + "." + "consumed.last30days", null);
    removeDynamicCriterionField("point" + "." + point.getGUIManagedObjectID() + "." + "consumed.today", null);
    removeDynamicCriterionField("point" + "." + point.getGUIManagedObjectID() + "." + "consumed.thisWeek", null);
    removeDynamicCriterionField("point" + "." + point.getGUIManagedObjectID() + "." + "consumed.thisMonth", null);

    removeDynamicCriterionField("point" + "." + point.getGUIManagedObjectID() + "." + "expired.yesterday", null);
    removeDynamicCriterionField("point" + "." + point.getGUIManagedObjectID() + "." + "expired.last7days", null);
    removeDynamicCriterionField("point" + "." + point.getGUIManagedObjectID() + "." + "expired.last30days", null);
    removeDynamicCriterionField("point" + "." + point.getGUIManagedObjectID() + "." + "expired.today", null);
    removeDynamicCriterionField("point" + "." + point.getGUIManagedObjectID() + "." + "expired.thisWeek", null);
    removeDynamicCriterionField("point" + "." + point.getGUIManagedObjectID() + "." + "expired.thisMonth", null);
  }
  
  /*****************************************
  *
  *  addComplexObjectTypeCriterionFields
  *
  *****************************************/

  public void addComplexObjectTypeCriterionFields(ComplexObjectType complexObjectType, boolean newComplexObjectType) throws GUIManagerException
  {
    
    for(String currentName : complexObjectType.getAvailableElements())
      {
        for(Map.Entry<Integer, ComplexObjectTypeSubfield> current : complexObjectType.getSubfields().entrySet())
          {
            Integer subFieldID = current.getKey();
            String subFieldName = current.getValue().getSubfieldName();
            ComplexObjectTypeSubfield subField = current.getValue();
            switch (subField.getCriterionDataType())
              {
              case IntegerCriterion :
              case StringCriterion :
              case StringSetCriterion :
              case DateCriterion :
                //
                //  json constructor
                //
      
                JSONObject criterionFieldJSON = new JSONObject();
                String id = "complexObject." + complexObjectType.getComplexObjectTypeID() + "." + currentName + "." + subFieldName;
                criterionFieldJSON.put("id", id);
                criterionFieldJSON.put("display", complexObjectType.getComplexObjectTypeName() + "." + currentName + "." + subFieldName);
                criterionFieldJSON.put("epoch", complexObjectType.getEpoch());
                criterionFieldJSON.put("dataType", subField.getCriterionDataType().getExternalRepresentation());
                criterionFieldJSON.put("tagFormat", null);
                criterionFieldJSON.put("tagMaxLength", null);
                criterionFieldJSON.put("esField", id);
                criterionFieldJSON.put("retriever", "getComplexObjectFieldValue");
                criterionFieldJSON.put("minValue", null);
                criterionFieldJSON.put("maxValue", null);
    // TODO            criterionFieldJSON.put("availableValues", availableValues);
                criterionFieldJSON.put("includedOperators", null);
                criterionFieldJSON.put("excludedOperators", null);
                criterionFieldJSON.put("includedComparableFields", null); 
                criterionFieldJSON.put("excludedComparableFields", null);
                DynamicCriterionField criterionField = new DynamicCriterionField(complexObjectType, criterionFieldJSON);
      
                //
                //  put
                //
      
                putGUIManagedObject(criterionField, SystemTime.getCurrentTime(), newComplexObjectType, null);  
              
              
                break;
    
              default:
                log.warn("ComplexObjectType: Unsupported CriterionDataType " + subField.getCriterionDataType());
                break;
              }
          }
      }
  }

  /*****************************************
  *
  *  removePointCriterionFields
  *
  *****************************************/

  public void removeComplexObjectTypeCriterionFields(GUIManagedObject guiManagedObject)
  {
    
    ComplexObjectType complexObjectType = (ComplexObjectType)guiManagedObject;
    for(String currentName : complexObjectType.getAvailableElements())
      {
        for(Map.Entry<Integer, ComplexObjectTypeSubfield> current : complexObjectType.getSubfields().entrySet())
          {
            Integer subFieldID = current.getKey();
            String id = "complexObject." + complexObjectType.getComplexObjectTypeID() + "." + currentName + "." + subFieldID;
            removeGUIManagedObject(id, SystemTime.getCurrentTime(), null);
          }
      }
  }

  
  /*****************************************
  *
  *  removeDynamicCriterionField
  *
  *****************************************/

  public void removeDynamicCriterionField(String dynamicCriterionFieldID, String userID) { removeGUIManagedObject(dynamicCriterionFieldID, SystemTime.getCurrentTime(), userID); }

  /*****************************************
  *
  *  interface DynamicCriterionFieldListener
  *
  *****************************************/

  public interface DynamicCriterionFieldListener
  {
    public void dynamicCriterionFieldActivated(DynamicCriterionField dynamicCriterionField);
    public void dynamicCriterionFieldDeactivated(String guiManagedObjectID);
  }
}
