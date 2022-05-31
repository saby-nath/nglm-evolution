/*****************************************************************************
*
*  SegmentationDimensionEligibility.java
*
*****************************************************************************/

package com.evolving.nglm.evolution;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import org.apache.kafka.connect.data.Field;
import org.apache.kafka.connect.data.Schema;
import org.apache.kafka.connect.data.SchemaAndValue;
import org.apache.kafka.connect.data.SchemaBuilder;
import org.apache.kafka.connect.data.Struct;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

import com.evolving.nglm.core.ConnectSerde;
import com.evolving.nglm.core.JSONUtilities;
import com.evolving.nglm.core.SchemaUtilities;
import com.evolving.nglm.evolution.EvaluationCriterion.CriterionOperator;
import com.evolving.nglm.evolution.GUIManagedObject.GUIDependencyDef;
import com.evolving.nglm.evolution.GUIManager.GUIManagerException;
import com.evolving.nglm.evolution.LoyaltyProgram.LoyaltyProgramType;

//@GUIDependencyDef(objectType = "segmentationDimensionRanges", serviceClass = SegmentationDimensionService.class, dependencies = {"loyaltyProgramPoints", "loyaltyprogramchallenge", "loyaltyprogrammission"})
public class SegmentationDimensionRanges extends SegmentationDimension
{
  
  /*****************************************
  *
  *  schema
  *
  *****************************************/

  //
  //  schema
  //

  private static Schema schema = null;
  static
  {
    SchemaBuilder schemaBuilder = SchemaBuilder.struct();
    schemaBuilder.name("segmentation_dimension_ranges");
    schemaBuilder.version(SchemaUtilities.packSchemaVersion(SegmentationDimension.commonSchema().version(),1));
    for (Field field : SegmentationDimension.commonSchema().fields()) schemaBuilder.field(field.name(), field.schema());
    schemaBuilder.field("baseSplit", SchemaBuilder.array(BaseSplit.schema()).schema());
    schema = schemaBuilder.build();
  };

  //
  //  serde
  //

  private static ConnectSerde<SegmentationDimensionRanges> serde = new ConnectSerde<SegmentationDimensionRanges>(schema, false, SegmentationDimensionRanges.class, SegmentationDimensionRanges::pack, SegmentationDimensionRanges::unpack);

  //
  //  accessor
  //

  public static Schema schema() { return schema; }
  public static ConnectSerde<SegmentationDimensionRanges> serde() { return serde; }

  /****************************************
  *
  *  data
  *
  ****************************************/

  private List<BaseSplit> baseSplit;

  /****************************************
  *
  *  accessors
  *
  ****************************************/

  //
  //  public
  //

  public List<BaseSplit> getBaseSplit() { return baseSplit; }

  /*****************************************
  *
  *  getSegments
  *
  *****************************************/
  
  @Override public List<SegmentRanges> getSegments()
  {
    List<SegmentRanges> result = new ArrayList<SegmentRanges>();
    for (BaseSplit currentBaseSplit : baseSplit)
      {
        result.addAll(currentBaseSplit.getSegments());
      }
    return result;
  }

  /*****************************************
  *
  *  constructor -- unpack
  *
  *****************************************/

  public SegmentationDimensionRanges(SchemaAndValue schemaAndValue, List<BaseSplit> baseSplit)
  {
    super(schemaAndValue);
    this.baseSplit = baseSplit;
  }

  /*****************************************
  *
  *  pack
  *
  *****************************************/

  public static Object pack(Object value)
  {
    SegmentationDimensionRanges segmentationDimension = (SegmentationDimensionRanges) value;
    Struct struct = new Struct(schema);
    SegmentationDimension.packCommon(struct, segmentationDimension);
    struct.put("baseSplit", packBaseSplit(segmentationDimension.getBaseSplit()));
    return struct;
  }

  /****************************************
  *
  *  packBaseSplit
  *
  ****************************************/

  private static List<Object> packBaseSplit(List<BaseSplit> baseSplit)
  {
    List<Object> result = new ArrayList<Object>();
    for (BaseSplit split : baseSplit)
      {
        result.add(BaseSplit.pack(split));
      }
    return result;
  }

  /*****************************************
  *
  *  unpack
  *
  *****************************************/

  public static SegmentationDimensionRanges unpack(SchemaAndValue schemaAndValue)
  {
    //
    //  data
    //

    Schema schema = schemaAndValue.schema();
    Object value = schemaAndValue.value();
    Integer schemaVersion = (schema != null) ? SchemaUtilities.unpackSchemaVersion2(schema.version()) : null;

    //
    //  unpack
    //

    Struct valueStruct = (Struct) value;
    List<BaseSplit> baseSplit = unpackBaseSplit(schema.field("baseSplit").schema(), valueStruct.get("baseSplit"));
    
    //
    //  return
    //

    return new SegmentationDimensionRanges(schemaAndValue, baseSplit);
  }
  
  /*****************************************
  *
  *  unpackBaseSplit
  *
  *****************************************/

  private static List<BaseSplit> unpackBaseSplit(Schema schema, Object value)
  {
    //
    //  get schema for BaseSplit
    //

    Schema segmentSchema = schema.valueSchema();
    
    //
    //  unpack
    //

    List<BaseSplit> result = new ArrayList<BaseSplit>();
    List<Object> valueArray = (List<Object>) value;
    for (Object split : valueArray)
      {
        result.add(BaseSplit.unpack(new SchemaAndValue(segmentSchema, split)));
      }

    //
    //  return
    //

    return result;
  }

  /*****************************************
  *
  *  constructor -- JSON
  *
  *****************************************/

  public SegmentationDimensionRanges(SegmentationDimensionService segmentationDimensionService, JSONObject jsonRoot, long epoch, GUIManagedObject existingSegmentationDimensionUnchecked, boolean resetSegmentIDs, int tenantID) throws GUIManagerException
  {
    /*****************************************
    *
    *  super
    *
    *****************************************/

    super(jsonRoot, epoch, existingSegmentationDimensionUnchecked, tenantID);

    /*****************************************
    *
    *  existingSegmentationDimension
    *
    *****************************************/

    SegmentationDimensionRanges existingSegmentationDimension = (existingSegmentationDimensionUnchecked != null && existingSegmentationDimensionUnchecked instanceof SegmentationDimensionRanges) ? (SegmentationDimensionRanges) existingSegmentationDimensionUnchecked : null;

    /*****************************************
    *
    *  attributes
    *
    *****************************************/

    this.baseSplit = decodeBaseSplit(segmentationDimensionService, JSONUtilities.decodeJSONArray(jsonRoot, "baseSplit", true), resetSegmentIDs, tenantID);
    
    /*****************************************
    *
    *  epoch
    *
    *****************************************/

    if (epochChanged(existingSegmentationDimension))
      {
        this.setEpoch(epoch);
      }
  }

  /*****************************************
  *
  *  decodeBaseSplit
  *
  *****************************************/

  private List<BaseSplit> decodeBaseSplit(SegmentationDimensionService segmentationDimensionService, JSONArray jsonArray, boolean resetSegmentIDs, int tenantID) throws GUIManagerException
   {
    List<BaseSplit> result = new ArrayList<BaseSplit>();
    for (int i=0; i<jsonArray.size(); i++)
      {
        result.add(new BaseSplit(segmentationDimensionService, (JSONObject) jsonArray.get(i), resetSegmentIDs, tenantID));
      }
    return result;
  }

  /*****************************************
  *
  *  epochChanged
  *
  *****************************************/

  private boolean epochChanged(SegmentationDimensionRanges existingSegmentationDimension)
  {
    if (existingSegmentationDimension != null && existingSegmentationDimension.getAccepted())
      {
        boolean epochChanged = false;
        epochChanged = epochChanged || ! Objects.equals(getSegmentationDimensionID(), existingSegmentationDimension.getSegmentationDimensionID());
        epochChanged = epochChanged || ! Objects.equals(getSegmentationDimensionName(), existingSegmentationDimension.getSegmentationDimensionName());
        epochChanged = epochChanged || ! Objects.equals(getTargetingType(), existingSegmentationDimension.getTargetingType());
        epochChanged = epochChanged || ! Objects.equals(baseSplit, existingSegmentationDimension.getBaseSplit());
        return epochChanged;
      }
    else
      {
        return true;
      }
   }

  /*****************************************
  *
  *  checkSegments
  *
  *****************************************/
  
  @Override public void checkSegments()
  {
    int numberOfSegments = 0;
    String defaultSegmentID = null;
    for(BaseSplit split : baseSplit)
      {
        
        numberOfSegments = numberOfSegments + split.getSegments().size();
        if ((split.getProfileCriteria() == null || split.getProfileCriteria().isEmpty()) && (split.getVariableName() == null || split.getVariableName().isEmpty()) && split.getSegments().size() == 1)
          {
            SegmentRanges segment = split.getSegments().get(0);
            defaultSegmentID = segment.getID();
          }
      }   
    this.setDefaultSegmentID(defaultSegmentID);
    this.setNumberOfSegments(numberOfSegments);
  }

  @Override public boolean getSegmentsConditionEqual(SegmentationDimension dimension)
  {
    //verify the same type for objects
    if(this.getClass() != dimension.getClass()) return false;
    //cast to ranges dimension
    SegmentationDimensionRanges rangesDimension = (SegmentationDimensionRanges)dimension;
    //if base split size changed inequality
    if(this.getBaseSplit().size() != rangesDimension.getBaseSplit().size()) return false;
    for(BaseSplit split : this.getBaseSplit())
    {
      BaseSplit comparedSameGroupSplit = rangesDimension.getBaseSplit().stream().filter(p -> p.getSplitName().equals(split.getSplitName())).findFirst().orElse(null);
      //if not group name exists in splits means that eligibility criteria changed or damaged
      if(comparedSameGroupSplit == null) return false;
      //verify if number of segments are different between the same splits.
      // This is made before criteria because is less cost effective and if size changed we don't want to verify criteria that is most expensive
      if(split.getSegments().size() != comparedSameGroupSplit.getSegments().size()) return false;
      //verify if profile criteria is the same for splits
      for(EvaluationCriterion criterion : split.getProfileCriteria())
      {
        if(!comparedSameGroupSplit.getProfileCriteria().stream().anyMatch(p -> (p.getCriterionOperator().equals(criterion.getCriterionOperator())
            && (p.getArgumentExpression().equals(criterion.getArgumentExpression())))
            && (p.getCriterionField().equals(criterion.getCriterionField())))) return false;
      }
      for(Segment segment : split.getSegments())
      {
        Segment comparedSplitSegment = comparedSameGroupSplit.getSegments().stream().filter(p -> p.getID().equals(segment.getID())).findFirst().orElse(null);
        //segment does not exists
        if(comparedSplitSegment == null)
        {
          return false;
        }
        else
        {
          //verify segments with the same id are equals
          if(!segment.getSegmentConditionEqual(comparedSplitSegment)) return false;
        }
      }
    }
    return true;
  }
  @Override 
  public Map<String, List<String>> getGUIDependencies(List<GUIService> guiServiceList, int tenantID)
  {
	  Map<String, List<String>> result = new HashMap<String, List<String>>();
	  List<String> loyaltyProgramPointsIDs = new ArrayList<String>();
	    List<String> loyaltyprogramchallengeIDs = new ArrayList<String>();
	    List<String> loyaltyprogrammissionIDs = new ArrayList<String>();
	    result.put("loyaltyprogrampoints", loyaltyProgramPointsIDs);
	    result.put("loyaltyprogramchallenge", loyaltyprogramchallengeIDs);
		result.put("loyaltyprogrammission", loyaltyprogrammissionIDs);
	//	if(this.getTargetingType()==SegmentationDimensionTargetingType.RANGES) {
		for (BaseSplit split : this.getBaseSplit()) {
			for (EvaluationCriterion criterion : split.getProfileCriteria()) {
				String loyaltyProgramPointsID = getGUIManagedObjectIDFromDynamicCriterion(criterion,
						"loyaltyprogrampoints", guiServiceList);
				String loyaltyprogramchallengeID = getGUIManagedObjectIDFromDynamicCriterion(criterion,
						"loyaltyprogramchallenge", guiServiceList);
				String loyaltyprogrammissionID = getGUIManagedObjectIDFromDynamicCriterion(criterion,
						"loyaltyprogrammission", guiServiceList);

				if (loyaltyProgramPointsID != null)
					loyaltyProgramPointsIDs.add(loyaltyProgramPointsID);
				if (loyaltyprogramchallengeID != null)
					loyaltyprogramchallengeIDs.add(loyaltyprogramchallengeID);
				if (loyaltyprogrammissionID != null) loyaltyprogrammissionIDs.add(loyaltyprogrammissionID);
	        
	       
	      }
	    }
	//	}
	  return result;
  }
  
  private String getGUIManagedObjectIDFromDynamicCriterion(EvaluationCriterion criteria, String objectType, List<GUIService> guiServiceList)
	{
		String result = null;
		LoyaltyProgramService loyaltyProgramService = null;
		String loyaltyProgramID = "";
		GUIManagedObject uncheckedLoyalty;
		try {
			Pattern fieldNamePattern = Pattern.compile("^loyaltyprogram\\.([^.]+)\\.(.+)$");
			Matcher fieldNameMatcher = fieldNamePattern.matcher(criteria.getCriterionField().getID());
			if (fieldNameMatcher.find()) {
				loyaltyProgramID = fieldNameMatcher.group(1);
				loyaltyProgramService = (LoyaltyProgramService) guiServiceList.stream()
						.filter(srvc -> srvc.getClass() == LoyaltyProgramService.class).findFirst().orElse(null);
			}
			if (loyaltyProgramService != null) {
				uncheckedLoyalty = loyaltyProgramService.getStoredLoyaltyProgram(loyaltyProgramID);
				switch (objectType.toLowerCase()) {
				case "loyaltyprogrampoints":

					if (uncheckedLoyalty != null && uncheckedLoyalty.getAccepted()
							&& ((LoyaltyProgram) uncheckedLoyalty).getLoyaltyProgramType() == LoyaltyProgramType.POINTS)
						result = uncheckedLoyalty.getGUIManagedObjectID();

					break;

				case "loyaltyprogramchallenge":

					uncheckedLoyalty = loyaltyProgramService.getStoredLoyaltyProgram(loyaltyProgramID);
					if (uncheckedLoyalty != null && uncheckedLoyalty.getAccepted()
							&& ((LoyaltyProgram) uncheckedLoyalty)
									.getLoyaltyProgramType() == LoyaltyProgramType.CHALLENGE)
						result = uncheckedLoyalty.getGUIManagedObjectID();

					break;

				case "loyaltyprogrammission":

					uncheckedLoyalty = loyaltyProgramService.getStoredLoyaltyProgram(loyaltyProgramID);
					if (uncheckedLoyalty != null && uncheckedLoyalty.getAccepted()
							&& ((LoyaltyProgram) uncheckedLoyalty)
									.getLoyaltyProgramType() == LoyaltyProgramType.MISSION)
						result = uncheckedLoyalty.getGUIManagedObjectID();

					break;

				default:
					break;
				}
			}

		} catch (PatternSyntaxException e) {
			if (log.isTraceEnabled())
				log.trace("PatternSyntaxException Description: {}, Index: ", e.getDescription(), e.getIndex());
		}

		return result;
	}
}
