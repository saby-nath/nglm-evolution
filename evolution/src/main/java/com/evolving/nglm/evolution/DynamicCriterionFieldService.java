/*****************************************************************************
*
*  DynamicCriterionFieldService.java
*
*****************************************************************************/

package com.evolving.nglm.evolution;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.apache.kafka.clients.producer.KafkaProducer;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.evolving.nglm.core.JSONUtilities;
import com.evolving.nglm.core.ServerRuntimeException;
import com.evolving.nglm.core.SystemTime;
import com.evolving.nglm.evolution.EvaluationCriterion.CriterionDataType;
import com.evolving.nglm.evolution.GUIManager.GUIManagerException;
import com.evolving.nglm.evolution.LoyaltyProgram.LoyaltyProgramType;
import com.evolving.nglm.evolution.LoyaltyProgramChallenge.ChallengeLevel;
import com.evolving.nglm.evolution.LoyaltyProgramMission.MissionStep;
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
          @Override public void guiManagedObjectDeactivated(String guiManagedObjectID, int tenantID) { dynamicCriterionFieldListener.dynamicCriterionFieldDeactivated(guiManagedObjectID); }
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
  public Collection<GUIManagedObject> getStoredDynamicCriterionFields(int tenantID) { return getStoredGUIManagedObjects(tenantID); }
  public boolean isActiveDynamicCriterionField(GUIManagedObject dynamicCriterionFieldUnchecked, Date date) { return isActiveGUIManagedObject(dynamicCriterionFieldUnchecked, date); }
  public DynamicCriterionField getActiveDynamicCriterionField(String dynamicCriterionFieldID, Date date) { return (DynamicCriterionField) getActiveGUIManagedObject(dynamicCriterionFieldID, date); }
  public Collection<DynamicCriterionField> getActiveDynamicCriterionFields(Date date, int tenantID) { return (Collection<DynamicCriterionField>) getActiveGUIManagedObjects(date, tenantID); }

  /*****************************************
  *
  *  addLoyaltyProgramCriterionFields
  *
  *****************************************/

  public void addLoyaltyProgramCriterionFields(LoyaltyProgram loyaltyProgram, boolean newLoyaltyProgram) throws GUIManagerException
  {
    if (loyaltyProgram instanceof LoyaltyProgramPoints)
      {
        addLoyaltyProgramCriterionField(loyaltyProgram, newLoyaltyProgram, "tier", CriterionDataType.StringCriterion, generateAvailableValues(loyaltyProgram));
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
    else if (loyaltyProgram instanceof LoyaltyProgramChallenge)
      {
        addLoyaltyProgramCriterionField(loyaltyProgram, newLoyaltyProgram, "level", CriterionDataType.StringCriterion, generateAvailableValues(loyaltyProgram));
        addLoyaltyProgramCriterionField(loyaltyProgram, newLoyaltyProgram, "score", CriterionDataType.IntegerCriterion, null);
        addLoyaltyProgramCriterionField(loyaltyProgram, newLoyaltyProgram, "lastScoreChangeDate", CriterionDataType.DateCriterion, null);
        addLoyaltyProgramCriterionField(loyaltyProgram, newLoyaltyProgram, "levelupdatedate", CriterionDataType.DateCriterion, null);
        addLoyaltyProgramCriterionField(loyaltyProgram, newLoyaltyProgram, "optindate", CriterionDataType.DateCriterion, null);
        addLoyaltyProgramCriterionField(loyaltyProgram, newLoyaltyProgram, "optoutdate", CriterionDataType.DateCriterion, null);
      }
    else if (loyaltyProgram instanceof LoyaltyProgramMission)
      {
        addLoyaltyProgramCriterionField(loyaltyProgram, newLoyaltyProgram, "step", CriterionDataType.StringCriterion, generateAvailableValues(loyaltyProgram));
        addLoyaltyProgramCriterionField(loyaltyProgram, newLoyaltyProgram, "stepupdatedate", CriterionDataType.DateCriterion, null);
        addLoyaltyProgramCriterionField(loyaltyProgram, newLoyaltyProgram, "currentProgression", CriterionDataType.DoubleCriterion, null);
        addLoyaltyProgramCriterionField(loyaltyProgram, newLoyaltyProgram, "isMissionCompleted", CriterionDataType.BooleanCriterion, null);
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
    criterionFieldJSON.put("esField", "loyaltyprograms." + loyaltyProgram.getLoyaltyProgramID() + "." + criterionFieldInternalBaseName);
    criterionFieldJSON.put("retriever", "getLoyaltyProgramCriterionField");
    criterionFieldJSON.put("minValue", null);
    criterionFieldJSON.put("maxValue", null);
    criterionFieldJSON.put("availableValues", availableValues);
    criterionFieldJSON.put("includedOperators", null);
    criterionFieldJSON.put("excludedOperators", null);
    criterionFieldJSON.put("includedComparableFields", null); 
    criterionFieldJSON.put("excludedComparableFields", null);
    DynamicCriterionField criterionField = new DynamicCriterionField(loyaltyProgram, criterionFieldJSON, loyaltyProgram.getTenantID());

    //
    //  put
    //

    putGUIManagedObject(criterionField, SystemTime.getCurrentTime(), newLoyaltyProgram, null);
  }
  
  public final static String CUSTOMCRITERIA_PREFIX = "customCriteria.";
  
  /*****************************************
  *
  *  addCustomCriterionField
  *
  *****************************************/

  public void addCustomCriterionField(CustomCriteria customCriteria, boolean newCustomCriteria) throws GUIManagerException
  {
    //
    //  json constructor
    //

    JSONObject criterionFieldJSON = new JSONObject();
    String id = CUSTOMCRITERIA_PREFIX + customCriteria.getGUIManagedObjectID();
    criterionFieldJSON.put("id", id);
    criterionFieldJSON.put("display", customCriteria.getGUIManagedObjectDisplay());
    criterionFieldJSON.put("epoch", customCriteria.getEpoch());
    criterionFieldJSON.put("dataType", customCriteria.getDataType());
    criterionFieldJSON.put("tagFormat", null);
    criterionFieldJSON.put("tagMaxLength", null);
    criterionFieldJSON.put("esField", id);
    criterionFieldJSON.put("retriever", "getCustomCriterionField");
    criterionFieldJSON.put("minValue", null);
    criterionFieldJSON.put("maxValue", null);
    criterionFieldJSON.put("availableValues", null);
    criterionFieldJSON.put("includedOperators", null);
    criterionFieldJSON.put("excludedOperators", null);
    criterionFieldJSON.put("includedComparableFields", null); 
    criterionFieldJSON.put("excludedComparableFields", null);
    DynamicCriterionField criterionField = new DynamicCriterionField(customCriteria, criterionFieldJSON, customCriteria.getTenantID());

    //
    //  put
    //

    putGUIManagedObject(criterionField, SystemTime.getCurrentTime(), newCustomCriteria, null);
  }

  /*****************************************
  *
  *  generateAvailableValues
  *
  *****************************************/

  private JSONArray generateAvailableValues(LoyaltyProgram loyaltyProgram)
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
          
        case CHALLENGE:
          for (ChallengeLevel level : ((LoyaltyProgramChallenge) loyaltyProgram).getLevels())
            {
              availableValuesField.add(level.getLevelName());  
            }
          break;
          
        case MISSION:
          for (MissionStep step : ((LoyaltyProgramMission) loyaltyProgram).getSteps())
            {
              availableValuesField.add(step.getStepName());  
            }
          break;
          
        default:
          log.error("invalid loyaltyProgram type {}", loyaltyProgram.getLoyaltyProgramType());
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
    if (loyaltyProgram instanceof LoyaltyProgramPoints)
      {
        LoyaltyProgramPoints loyaltyProgramPoints = (LoyaltyProgramPoints) loyaltyProgram;
        
        removeDynamicCriterionField(prefix + "tier", null, loyaltyProgram.getTenantID());
    	removeDynamicCriterionField(prefix + "rewardpoint.balance", null, loyaltyProgram.getTenantID());
    	removeDynamicCriterionField(prefix + "statuspoint.balance", null, loyaltyProgram.getTenantID());
        String statusPointID = loyaltyProgramPoints.getStatusPointsID();
        String rewardPointID = loyaltyProgramPoints.getRewardPointsID();
        removeDynamicCriterionField(prefix + "statuspoint." + statusPointID + ".earliestexpirydate", null, loyaltyProgram.getTenantID());
        removeDynamicCriterionField(prefix + "rewardpoint." + rewardPointID + ".earliestexpirydate", null, loyaltyProgram.getTenantID());
        removeDynamicCriterionField(prefix + "statuspoint." + statusPointID + ".earliestexpiryquantity", null, loyaltyProgram.getTenantID());
        removeDynamicCriterionField(prefix + "rewardpoint." + rewardPointID + ".earliestexpiryquantity", null, loyaltyProgram.getTenantID());
      }
    else if (loyaltyProgram instanceof LoyaltyProgramChallenge)
      {
        removeDynamicCriterionField(prefix + "level", null, loyaltyProgram.getTenantID());
        removeDynamicCriterionField(prefix + "score", null, loyaltyProgram.getTenantID());
        removeDynamicCriterionField(prefix + "lastScoreChangeDate", null, loyaltyProgram.getTenantID());
      }
    else if (loyaltyProgram instanceof LoyaltyProgramMission)
      {
        removeDynamicCriterionField(prefix + "step", null, loyaltyProgram.getTenantID());
        removeDynamicCriterionField(prefix + "currentProgression", null, loyaltyProgram.getTenantID());
        removeDynamicCriterionField(prefix + "isMissionCompleted", null, loyaltyProgram.getTenantID());
      }
  }

  /*****************************************
  *
  *  addPointCriterionFields
  *
  *****************************************/

  public void addPointCriterionFields(Point point, boolean newPoint) throws GUIManagerException
  {
    addPointCriterionFieldFluct(point, newPoint, "balance", CriterionDataType.IntegerCriterion);
    addPointCriterionFieldFluct(point, newPoint, "earliestexpirydate", CriterionDataType.DateCriterion);
    addPointCriterionFieldFluct(point, newPoint, "earliestexpiryquantity", CriterionDataType.IntegerCriterion);

    addPointMetricsCriterionFields(point, newPoint, "earned");
    addPointMetricsCriterionFields(point, newPoint, "consumed");
    addPointMetricsCriterionFields(point, newPoint, "expired");
  }
  
  private void addPointMetricsCriterionFields(Point point, boolean newPoint, String nature) throws GUIManagerException
  {
    addPointCriterionField(point, newPoint, nature+".yesterday", CriterionDataType.IntegerCriterion);
    addPointCriterionField(point, newPoint, nature+".last7days", CriterionDataType.IntegerCriterion);
    addPointCriterionField(point, newPoint, nature+".last30days", CriterionDataType.IntegerCriterion);
    addPointCriterionField(point, newPoint, nature+".today", CriterionDataType.IntegerCriterion);
    addPointCriterionField(point, newPoint, nature+".thisWeek", CriterionDataType.IntegerCriterion);
    addPointCriterionField(point, newPoint, nature+".thisMonth", CriterionDataType.IntegerCriterion);
  }

  private void addPointCriterionField(Point point, boolean newPoint, String criterionFieldBaseName, CriterionDataType criterionDataType) throws GUIManagerException
  {
    addPointCriterionFieldInternal(point, newPoint, criterionFieldBaseName, criterionDataType, "pointBalances." + point.getPointID() + "." + criterionFieldBaseName);
  }
  
  private void addPointCriterionFieldFluct(Point point, boolean newPoint, String criterionFieldBaseName, CriterionDataType criterionDataType) throws GUIManagerException
  {
    addPointCriterionFieldInternal(point, newPoint, criterionFieldBaseName, criterionDataType, "pointFluctuations." + point.getPointID() + "." + criterionFieldBaseName);
  }

  /*****************************************
  *
  *  addPointCriterionFieldInternal
  *
  *****************************************/

  private void addPointCriterionFieldInternal(Point point, boolean newPoint, String criterionFieldBaseName, CriterionDataType criterionDataType, String esField) throws GUIManagerException
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
    criterionFieldJSON.put("esField", esField);
    criterionFieldJSON.put("retriever", "getPointCriterionField");
    criterionFieldJSON.put("minValue", null);
    criterionFieldJSON.put("maxValue", null);
    criterionFieldJSON.put("availableValues", null);
    criterionFieldJSON.put("includedOperators", null);
    criterionFieldJSON.put("excludedOperators", null);
    criterionFieldJSON.put("includedComparableFields", null); 
    criterionFieldJSON.put("excludedComparableFields", null);
    DynamicCriterionField criterionField = new DynamicCriterionField(point, criterionFieldJSON, point.getTenantID());

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

  public void removePointCriterionFields(GUIManagedObject point, int tenantID)
  {
    removeDynamicCriterionField("point" + "." + point.getGUIManagedObjectID() + "." + "balance", null, tenantID);
    removeDynamicCriterionField("point" + "." + point.getGUIManagedObjectID() + "." + "earliestexpirydate", null, tenantID);
    removeDynamicCriterionField("point" + "." + point.getGUIManagedObjectID() + "." + "earliestexpiryquantity", null, tenantID);
  
    removeDynamicCriterionField("point" + "." + point.getGUIManagedObjectID() + "." + "earned.yesterday", null, tenantID);
    removeDynamicCriterionField("point" + "." + point.getGUIManagedObjectID() + "." + "earned.last7days", null, tenantID);
    removeDynamicCriterionField("point" + "." + point.getGUIManagedObjectID() + "." + "earned.last30days", null, tenantID);
    removeDynamicCriterionField("point" + "." + point.getGUIManagedObjectID() + "." + "earned.today", null, tenantID);
    removeDynamicCriterionField("point" + "." + point.getGUIManagedObjectID() + "." + "earned.thisWeek", null, tenantID);
    removeDynamicCriterionField("point" + "." + point.getGUIManagedObjectID() + "." + "earned.thisMonth", null, tenantID);

    removeDynamicCriterionField("point" + "." + point.getGUIManagedObjectID() + "." + "consumed.yesterday", null, tenantID);
    removeDynamicCriterionField("point" + "." + point.getGUIManagedObjectID() + "." + "consumed.last7days", null, tenantID);
    removeDynamicCriterionField("point" + "." + point.getGUIManagedObjectID() + "." + "consumed.last30days", null, tenantID);
    removeDynamicCriterionField("point" + "." + point.getGUIManagedObjectID() + "." + "consumed.today", null, tenantID);
    removeDynamicCriterionField("point" + "." + point.getGUIManagedObjectID() + "." + "consumed.thisWeek", null, tenantID);
    removeDynamicCriterionField("point" + "." + point.getGUIManagedObjectID() + "." + "consumed.thisMonth", null, tenantID);

    removeDynamicCriterionField("point" + "." + point.getGUIManagedObjectID() + "." + "expired.yesterday", null, tenantID);
    removeDynamicCriterionField("point" + "." + point.getGUIManagedObjectID() + "." + "expired.last7days", null, tenantID);
    removeDynamicCriterionField("point" + "." + point.getGUIManagedObjectID() + "." + "expired.last30days", null, tenantID);
    removeDynamicCriterionField("point" + "." + point.getGUIManagedObjectID() + "." + "expired.today", null, tenantID);
    removeDynamicCriterionField("point" + "." + point.getGUIManagedObjectID() + "." + "expired.thisWeek", null, tenantID);
    removeDynamicCriterionField("point" + "." + point.getGUIManagedObjectID() + "." + "expired.thisMonth", null, tenantID);
  }
  
  /*****************************************
  *
  *  addComplexObjectTypeCriterionFields
  *
  *****************************************/

  //migration start EVPRO-1185
  @Deprecated // must be removed when all the customer using adv criteria when using complex fields
  public void addComplexObjectTypeCriterionFields(ComplexObjectType complexObjectType, boolean newComplexObjectType, int tenantID) throws GUIManagerException
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
                DynamicCriterionField criterionField = new DynamicCriterionField(complexObjectType, criterionFieldJSON, tenantID);
      
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
  //migration end EVPRO-1185
  
  /*****************************************
  *
  *  addComplexObjectTypeAdvanceCriterionFields
  *
  *****************************************/

  public void addComplexObjectTypeAdvanceCriterionFields(ComplexObjectType complexObjectType, boolean newComplexObjectType, int tenantID) throws GUIManagerException
  {
    //
    //  subcriteria
    //
    
    List<JSONObject> subcriteriaJSONArray = new LinkedList<JSONObject>();
    Map<String, Object>subcriteriaMap = new HashMap<String, Object>();
    String subCriteriaID = "complex" + "." + complexObjectType.getGUIManagedObjectName() + "." + "element";
    String subCriteriaDisplay = complexObjectType.getGUIManagedObjectDisplay() + " Element";
    List<JSONObject> subCriteriaAvailableValues = new ArrayList<JSONObject>();
    for (String s : complexObjectType.getAvailableElements())
      {
        Map<String, String> availableValueMap = new HashMap<String, String>();
        availableValueMap.put("id", s);
        availableValueMap.put("display", s);
        subCriteriaAvailableValues.add(JSONUtilities.encodeObject(availableValueMap));
      }
    subcriteriaMap.put("id", subCriteriaID);
    subcriteriaMap.put("display", subCriteriaDisplay);
    subcriteriaMap.put("mandatory", true);
    subcriteriaMap.put("dataType", "string");
    subcriteriaMap.put("availableValues", JSONUtilities.encodeArray(subCriteriaAvailableValues));
    subcriteriaJSONArray.add(JSONUtilities.encodeObject(subcriteriaMap));
    
    for(Map.Entry<Integer, ComplexObjectTypeSubfield> subfield : complexObjectType.getSubfields().entrySet())
      {
        boolean metricHistoryType = false;
        String criteriaID = "complex" + "." + complexObjectType.getGUIManagedObjectName() + "." + subfield.getValue().getPrivateID() + "." + subfield.getValue().getSubfieldName() + "." + complexObjectType.getGUIManagedObjectID();
        String criteriaDisplay = complexObjectType.getGUIManagedObjectDisplay() + " - " + subfield.getValue().getSubfieldName();
        String retriever = null;
        switch (subfield.getValue().getCriterionDataType())
        {
          case IntegerCriterion :
            retriever = "getComplexObjectLong";
            break;
            
          case StringCriterion :
            retriever = "getComplexObjectString";
            break;
            
          case BooleanCriterion :
            retriever = "getComplexObjectBoolean";
            break;
            
          case DateCriterion :
            retriever = "getComplexObjectDate";
            break;
            
          case StringSetCriterion :
            retriever = "getComplexObjectStringSet";
            break;
            
          case MetricHistoryCriterion :
            retriever = "getComplexObjectMetricHistory";
            metricHistoryType = false;
            break;
            
          default:
            log.warn("ComplexObjectType: Unsupported CriterionDataType " + subfield.getValue().getCriterionDataType());
            throw new GUIManagerException("ComplexObjectType: Unsupported CriterionDataType ", subfield.getValue().getCriterionDataType().getExternalRepresentation());
        }
        
        if (metricHistoryType)
          {
            for (int i=0; i < 3; i++)
              {
                Map<String, Object> criterionFieldJSONMAP = new LinkedHashMap<String, Object>();
                criterionFieldJSONMAP.put("id", criteriaID.concat(".").concat(String.valueOf(i)));
                criterionFieldJSONMAP.put("display", criteriaDisplay.concat(" - ").concat(String.valueOf(i).concat(" days")));
                criterionFieldJSONMAP.put("dataType", "integer");
                criterionFieldJSONMAP.put("retriever", retriever);
                criterionFieldJSONMAP.put("subcriteria", JSONUtilities.encodeArray(subcriteriaJSONArray));
                criterionFieldJSONMAP.put("tagFormat", null);
                criterionFieldJSONMAP.put("includedOperators", null);
                criterionFieldJSONMAP.put("excludedOperators", null);
                criterionFieldJSONMAP.put("includedComparableFields", null); 
                criterionFieldJSONMAP.put("excludedComparableFields", null);
                criterionFieldJSONMAP.put("esField",subfield.getValue().getSubfieldName()); //RAJ K todo
                log.info("RAJ K metricHistoryType-criterionFieldJSONMAP {}", criterionFieldJSONMAP);
                
                //
                //  criterionFieldJSON
                //
                
                JSONObject criterionFieldJSON = JSONUtilities.encodeObject(criterionFieldJSONMAP);
                
                //
                //  criterionField
                //
                
                DynamicCriterionField criterionField = new DynamicCriterionField(complexObjectType, criterionFieldJSON, tenantID);
                
                //
                //  put
                //

                putGUIManagedObject(criterionField, SystemTime.getCurrentTime(), newComplexObjectType, null);
              }
            
          }
        else
          {
            Map<String, Object> criterionFieldJSONMAP = new LinkedHashMap<String, Object>();
            criterionFieldJSONMAP.put("id", criteriaID);
            criterionFieldJSONMAP.put("display", criteriaDisplay);
            criterionFieldJSONMAP.put("dataType", subfield.getValue().getCriterionDataType().getExternalRepresentation());
            criterionFieldJSONMAP.put("retriever", retriever);
            criterionFieldJSONMAP.put("subcriteria", JSONUtilities.encodeArray(subcriteriaJSONArray));
            criterionFieldJSONMAP.put("tagFormat", null);
            criterionFieldJSONMAP.put("includedOperators", null);
            criterionFieldJSONMAP.put("excludedOperators", null);
            criterionFieldJSONMAP.put("includedComparableFields", null); 
            criterionFieldJSONMAP.put("excludedComparableFields", null);
            criterionFieldJSONMAP.put("esField",subfield.getValue().getSubfieldName());
            log.info("RAJ K criterionFieldJSONMAP {}", criterionFieldJSONMAP);
            
            //
            //  criterionFieldJSON
            //
            
            JSONObject criterionFieldJSON = JSONUtilities.encodeObject(criterionFieldJSONMAP);
            
            //
            //  criterionField
            //
            
            DynamicCriterionField criterionField = new DynamicCriterionField(complexObjectType, criterionFieldJSON, tenantID);
            
            //
            //  put
            //

            putGUIManagedObject(criterionField, SystemTime.getCurrentTime(), newComplexObjectType, null);
          }
      }
  }

  /*****************************************
  *
  *  removeComplexObjectTypeAdvanceCriterionFields
  *
  *****************************************/

  public void removeComplexObjectTypeAdvanceCriterionFields(GUIManagedObject guiManagedObject) // RAJ K to do
  {
    ComplexObjectType complexObjectType = (ComplexObjectType) guiManagedObject;
    for(Map.Entry<Integer, ComplexObjectTypeSubfield> subfield : complexObjectType.getSubfields().entrySet())
      {
        String criteriaID = "complex" + "." + complexObjectType.getGUIManagedObjectName() + "." + subfield.getValue().getPrivateID() + "." + subfield.getValue().getSubfieldName() + "." + complexObjectType.getGUIManagedObjectID();
        GUIManagedObject guiManagedObjectCrt = getStoredDynamicCriterionField(criteriaID);
        if (guiManagedObjectCrt != null) removeGUIManagedObject(criteriaID, SystemTime.getCurrentTime(), null, guiManagedObject.getTenantID());
        
      }
  }
  
  // migration start EVPRO-1185
  @Deprecated // must be removed when all the customer using adv criteria when using complex fields
  public void removeComplexObjectTypeCriterionFields(GUIManagedObject guiManagedObject)
  {
    ComplexObjectType complexObjectType = (ComplexObjectType) guiManagedObject;
    for (String currentName : complexObjectType.getAvailableElements())
      {
        for (Map.Entry<Integer, ComplexObjectTypeSubfield> current : complexObjectType.getSubfields().entrySet())
          {
            Integer subFieldID = current.getKey();
            String id = "complexObject." + complexObjectType.getComplexObjectTypeID() + "." + currentName + "." + current.getValue().getSubfieldName();
            GUIManagedObject guiManagedObjectCrt = getStoredDynamicCriterionField(id);
            if (guiManagedObjectCrt != null)
              {
                removeGUIManagedObject(id, SystemTime.getCurrentTime(), null, guiManagedObject.getTenantID());
              }
          }
      }
  }
  // migration end EVPRO-1185
  
  /*****************************************
  *
  *  removeDynamicCriterionField
  *
  *****************************************/


  public void removeDynamicCriterionField(String dynamicCriterionFieldID, String userID, int tenantID) { removeGUIManagedObject(dynamicCriterionFieldID, SystemTime.getCurrentTime(), userID, tenantID); }


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
