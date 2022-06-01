/*****************************************************************************
*
*  SegmentationDimensionEligibility.java
*
*****************************************************************************/

package com.evolving.nglm.evolution;

import java.util.ArrayList;
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
import com.evolving.nglm.evolution.GUIManagedObject;
import com.evolving.nglm.evolution.GUIManagedObject.GUIDependencyDef;
import com.evolving.nglm.evolution.GUIManager.GUIManagerException;
import com.evolving.nglm.evolution.LoyaltyProgram.LoyaltyProgramType;

@GUIDependencyDef(objectType = "segmentationDimensionRules", serviceClass = SegmentationDimensionService.class, dependencies = {})
public class SegmentationDimensionEligibility extends SegmentationDimension
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
    schemaBuilder.name("segmentation_dimension_eligibility");
    schemaBuilder.version(SchemaUtilities.packSchemaVersion(SegmentationDimension.commonSchema().version(),1));
    for (Field field : SegmentationDimension.commonSchema().fields()) schemaBuilder.field(field.name(), field.schema());
    schemaBuilder.field("segments", SchemaBuilder.array(SegmentEligibility.schema()).schema());
    schema = schemaBuilder.build();
  };

  //
  //  serde
  //

  private static ConnectSerde<SegmentationDimensionEligibility> serde = new ConnectSerde<SegmentationDimensionEligibility>(schema, false, SegmentationDimensionEligibility.class, SegmentationDimensionEligibility::pack, SegmentationDimensionEligibility::unpack);

  //
  //  accessor
  //

  public static Schema schema() { return schema; }
  public static ConnectSerde<SegmentationDimensionEligibility> serde() { return serde; }

  /****************************************
  *
  *  data
  *
  ****************************************/

  private List<SegmentEligibility> segments;

  /****************************************
  *
  *  accessors
  *
  ****************************************/

  //
  //  abstract
  //

  @Override public List<SegmentEligibility> getSegments() { return segments; }
  
  /*****************************************
  *
  *  constructor -- unpack
  *
  *****************************************/

  public SegmentationDimensionEligibility(SchemaAndValue schemaAndValue, List<SegmentEligibility> segments)
  {
    super(schemaAndValue);
    this.segments = segments;
  }

  /*****************************************
  *
  *  pack
  *
  *****************************************/

  public static Object pack(Object value)
  {
    SegmentationDimensionEligibility segmentationDimension = (SegmentationDimensionEligibility) value;
    Struct struct = new Struct(schema);
    SegmentationDimension.packCommon(struct, segmentationDimension);
    struct.put("segments", packSegments(segmentationDimension.getSegments()));
    return struct;
  }

  /****************************************
  *
  *  packSegments
  *
  ****************************************/

  private static List<Object> packSegments(List<SegmentEligibility> segments)
  {
    List<Object> result = new ArrayList<Object>();
    for (SegmentEligibility segment : segments)
      {
        result.add(SegmentEligibility.pack(segment));
      }
    return result;
  }

  /*****************************************
  *
  *  unpack
  *
  *****************************************/

  public static SegmentationDimensionEligibility unpack(SchemaAndValue schemaAndValue)
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
    List<SegmentEligibility> segments = unpackSegments(schema.field("segments").schema(), valueStruct.get("segments"));

    //
    //  return
    //

    return new SegmentationDimensionEligibility(schemaAndValue, segments);
  }
  
  /*****************************************
  *
  *  unpackSegments
  *
  *****************************************/

  private static List<SegmentEligibility> unpackSegments(Schema schema, Object value)
  {
    //
    //  get schema for EvaluationCriterion
    //

    Schema segmentSchema = schema.valueSchema();
    
    //
    //  unpack
    //

    List<SegmentEligibility> result = new ArrayList<SegmentEligibility>();
    List<Object> valueArray = (List<Object>) value;
    for (Object criterion : valueArray)
      {
        result.add(SegmentEligibility.unpack(new SchemaAndValue(segmentSchema, criterion)));
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

  public SegmentationDimensionEligibility(SegmentationDimensionService segmentationDimensionService, JSONObject jsonRoot, long epoch, GUIManagedObject existingSegmentationDimensionUnchecked, boolean resetSegmentIDs, int tenantID) throws GUIManagerException
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

    SegmentationDimensionEligibility existingSegmentationDimension = (existingSegmentationDimensionUnchecked != null && existingSegmentationDimensionUnchecked instanceof SegmentationDimensionEligibility) ? (SegmentationDimensionEligibility) existingSegmentationDimensionUnchecked : null;

    /*****************************************
    *
    *  attributes
    *
    *****************************************/

    this.segments = decodeSegments(segmentationDimensionService, JSONUtilities.decodeJSONArray(jsonRoot, "segments", true), resetSegmentIDs, tenantID);
    
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
  *  decodeSegments
  *
  *****************************************/

  private List<SegmentEligibility> decodeSegments(SegmentationDimensionService segmentationDimensionService, JSONArray jsonArray, boolean resetSegmentIDs, int tenantID) throws GUIManagerException
   {
    List<SegmentEligibility> result = new ArrayList<SegmentEligibility>();
    for (int i=0; i<jsonArray.size(); i++)
      {
        JSONObject segment = (JSONObject) jsonArray.get(i);
        String segmentID = JSONUtilities.decodeString(segment, "id", false);
        if (segmentID == null || resetSegmentIDs)
          {
            segmentID = segmentationDimensionService.generateSegmentID();
            segment.put("id", segmentID);
          }
        result.add(new SegmentEligibility(segment, tenantID));
      }
    return result;
  }

  /*****************************************
  *
  *  epochChanged
  *
  *****************************************/

  private boolean epochChanged(SegmentationDimensionEligibility existingSegmentationDimension)
  {
    if (existingSegmentationDimension != null && existingSegmentationDimension.getAccepted())
      {
        boolean epochChanged = false;
        epochChanged = epochChanged || ! Objects.equals(getSegmentationDimensionID(), existingSegmentationDimension.getSegmentationDimensionID());
        epochChanged = epochChanged || ! Objects.equals(getSegmentationDimensionName(), existingSegmentationDimension.getSegmentationDimensionName());
        epochChanged = epochChanged || ! Objects.equals(getTargetingType(), existingSegmentationDimension.getTargetingType());
        epochChanged = epochChanged || ! Objects.equals(segments, existingSegmentationDimension.getSegments());
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
    for (SegmentEligibility segment : segments)
      {
        numberOfSegments++;
        if(segment.getProfileCriteria() == null || segment.getProfileCriteria().isEmpty())
          {
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
    if(getSegments().size() != dimension.getSegments().size()) return false;
    for(Segment segment: dimension.getSegments())
    {
      //a new segment in dimension
      if(segment.getID() == null) return false;
      //get stored segment by segment id
      Segment storedSegment = this.getSegments().stream().filter(p -> p.getID().equals(segment.getID())).findFirst().orElse(null);
      //stored segment null means that segment with id does not exist
      if(storedSegment == null)
      {
        return false;
      }
      else
      {
        //verify segments with the same id are equals
        if(! segment.getSegmentConditionEqual(storedSegment)) return false;
      }
    }
    return true;
  }
  
  //EVPRO-1542
  
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
		
		for (SegmentEligibility split : this.getSegments()) {
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
