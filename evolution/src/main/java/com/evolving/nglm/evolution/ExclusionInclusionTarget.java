/*****************************************************************************
*
*  ExclusionInclusionTarget.java (generated by com.evolving.nglm.evolution.tools.GUIManagedObjectGenerator)
*
*****************************************************************************/

package com.evolving.nglm.evolution;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Objects;

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
import com.evolving.nglm.evolution.GUIManager.GUIManagerException;

public class ExclusionInclusionTarget extends GUIManagedObject
{

  public static enum TargetType {
    Exclusion("exclusion"),
    Inclusion("inclusion"),
    Unknown("(unknown)");
    private String externalRepresentation;
    private TargetType(String externalRepresentation) { this.externalRepresentation = externalRepresentation;}
    public String getExternalRepresentation() { return externalRepresentation; }
    public static TargetType fromExternalRepresentation(String externalRepresentation) { for (TargetType enumeratedValue : TargetType.values()) { if (enumeratedValue.getExternalRepresentation().equalsIgnoreCase(externalRepresentation)) return enumeratedValue; } return Unknown; }
  }

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
    schemaBuilder.name("ExclusionInclusionTarget");
    schemaBuilder.version(SchemaUtilities.packSchemaVersion(commonSchema().version(),1));
    for (Field field : commonSchema().fields()) schemaBuilder.field(field.name(), field.schema());
    schemaBuilder.field("targetType", Schema.STRING_SCHEMA);
    schemaBuilder.field("fileID", Schema.OPTIONAL_STRING_SCHEMA);
    schemaBuilder.field("criteriaList", SchemaBuilder.array(EvaluationCriterion.schema()).optional().schema());
    schema = schemaBuilder.build();
  };

  //
  //  serde
  //

  private static ConnectSerde<ExclusionInclusionTarget> serde = 
    new ConnectSerde<ExclusionInclusionTarget>(schema, false, ExclusionInclusionTarget.class, ExclusionInclusionTarget::pack, ExclusionInclusionTarget::unpack);

  //
  //  accessor
  //

  public static Schema schema() { return schema; }
  public static ConnectSerde<ExclusionInclusionTarget> serde() { return serde; }

  /*****************************************
  *
  *  data
  *
  *****************************************/
  private TargetType targetType;
  private String fileID;
  private List<EvaluationCriterion> criteriaList;

  /*****************************************
  *
  *  accessors
  *
  *****************************************/
  public String getExclusionInclusionTargetID() { return getGUIManagedObjectID();}
  public String getExclusionInclusionTargetName() { return getGUIManagedObjectName();}
  public TargetType getTargetType() { return targetType;}
  public String getFileID() { return fileID;}
  public List<EvaluationCriterion> getCriteriaList() { return criteriaList; }
  /*****************************************
  *
  *  constructor -- unpack
  *
  *****************************************/

  public ExclusionInclusionTarget(SchemaAndValue schemaAndValue, TargetType type, String fileID, List<EvaluationCriterion> criteriaList)
  {
    super(schemaAndValue);
    this.targetType = type;
    this.fileID = fileID;
    this.criteriaList = criteriaList;
  }
  
  /*****************************************
  *
  *  pack
  *
  *****************************************/

  public static Object pack(Object value)
  {
    ExclusionInclusionTarget exclusionInclusionTarget = (ExclusionInclusionTarget) value;
    Struct struct = new Struct(schema);
    packCommon(struct, exclusionInclusionTarget);
    struct.put("targetType", exclusionInclusionTarget.getTargetType().getExternalRepresentation());
    struct.put("fileID", exclusionInclusionTarget.getFileID());
    struct.put("criteriaList", packCriteriaList(exclusionInclusionTarget.getCriteriaList()));
    return struct;
  }
  
  /****************************************
  *
  *  packCriteriaList
  *
  ****************************************/

  private static List<Object> packCriteriaList(List<EvaluationCriterion> criteriaList)
  {
    List<Object> result = new ArrayList<Object>();
    for (EvaluationCriterion criterion : criteriaList)
      {
        result.add(EvaluationCriterion.pack(criterion));
      }
    return result;
  }

  /*****************************************
  *
  *  unpack
  *
  *****************************************/

  public static ExclusionInclusionTarget unpack(SchemaAndValue schemaAndValue)
  {
    //
    //  data
    //

    Schema schema = schemaAndValue.schema();
    Object value = schemaAndValue.value();
    Integer schemaVersion = (schema != null) ? SchemaUtilities.unpackSchemaVersion1(schema.version()) : null;

    //
    //  unpack
    //

    Struct valueStruct = (Struct) value;
    TargetType type = TargetType.fromExternalRepresentation(valueStruct.getString("targetType"));
    String fileID = valueStruct.getString("fileID");
    List<EvaluationCriterion> criteriaList = unpackCriteriaList(schema.field("criteriaList").schema(), valueStruct.get("criteriaList"));

    //
    //  return
    //

    return new ExclusionInclusionTarget(schemaAndValue, type, fileID, criteriaList);
  }
  
  /*****************************************
  *
  *  unpackCriteriaList
  *
  *****************************************/

  private static List<EvaluationCriterion> unpackCriteriaList(Schema schema, Object value)
  {
    //
    //  get schema for EvaluationCriterion
    //

    Schema evaluationCriterionSchema = schema.valueSchema();
    
    //
    //  unpack
    //

    List<EvaluationCriterion> result = new ArrayList<EvaluationCriterion>();
    List<Object> valueArray = (List<Object>) value;
    for (Object criterion : valueArray)
      {
        result.add(EvaluationCriterion.unpack(new SchemaAndValue(evaluationCriterionSchema, criterion)));
      }

    //
    //  return
    //

    return result;
  }

  /*****************************************
  *
  *  constructor
  *
  *****************************************/

  public ExclusionInclusionTarget(JSONObject jsonRoot, long epoch, GUIManagedObject existingExclusionInclusionTargetUnchecked) throws GUIManagerException
  {
    /*****************************************
    *
    *  super
    *
    *****************************************/

    super(jsonRoot, (existingExclusionInclusionTargetUnchecked != null) ? existingExclusionInclusionTargetUnchecked.getEpoch() : epoch);

    /*****************************************
    *
    *  existingExclusionInclusionTarget
    *
    *****************************************/

    ExclusionInclusionTarget existingExclusionInclusionTarget = (existingExclusionInclusionTargetUnchecked != null && existingExclusionInclusionTargetUnchecked instanceof ExclusionInclusionTarget) ? (ExclusionInclusionTarget) existingExclusionInclusionTargetUnchecked : null;
    
    /*****************************************
    *
    *  attributes
    *
    *****************************************/
    
    this.targetType = TargetType.fromExternalRepresentation(JSONUtilities.decodeString(jsonRoot, "targetType", true));
    this.fileID = JSONUtilities.decodeString(jsonRoot, "fileID", false);
    this.criteriaList = decodeCriteriaList(JSONUtilities.decodeJSONArray(jsonRoot, "targetingCriteria", false), new ArrayList<EvaluationCriterion>());

    /*****************************************
    *
    *  epoch
    *
    *****************************************/

    if (epochChanged(existingExclusionInclusionTarget))
      {
        this.setEpoch(epoch);
      }
  }
  
  /*****************************************
  *
  *  decodeCriteriaList
  *
  *****************************************/

  private List<EvaluationCriterion> decodeCriteriaList(JSONArray jsonArray, List<EvaluationCriterion> universalCriteria) throws GUIManagerException
  {
    List<EvaluationCriterion> result = new ArrayList<EvaluationCriterion>();

    //
    //  universal criteria
    //

    result.addAll(universalCriteria);

    //
    //  critera
    //

    if (jsonArray != null)
      {
        for (int i=0; i<jsonArray.size(); i++)
          {
            result.add(new EvaluationCriterion((JSONObject) jsonArray.get(i), CriterionContext.Profile));
          }
      }

    //
    //  return
    //

    return result;
  }

  /*****************************************
  *
  *  epochChanged
  *
  *****************************************/

  private boolean epochChanged(ExclusionInclusionTarget existingExclusionInclusionTarget)
  {
    if (existingExclusionInclusionTarget != null && existingExclusionInclusionTarget.getAccepted())
      {
        boolean epochChanged = false;
        epochChanged = epochChanged || ! Objects.equals(getGUIManagedObjectID(), existingExclusionInclusionTarget.getGUIManagedObjectID());
        epochChanged = epochChanged || ! Objects.equals(targetType, existingExclusionInclusionTarget.getTargetType());
        epochChanged = epochChanged || ! Objects.equals(fileID, existingExclusionInclusionTarget.getFileID());
        epochChanged = epochChanged || ! Objects.equals(criteriaList, existingExclusionInclusionTarget.getCriteriaList());
        return epochChanged;
      }
    else
      {
        return true;
      }
  }
  
  /****************************************
  *
  *  validate
  *
  ****************************************/
  
  public void validate(UploadedFileService uploadedFileService, Date now) throws GUIManagerException 
  {
    //
    //  ensure file exists if specified
    //

    if (fileID != null)
      {
        UploadedFile uploadedFile = uploadedFileService.getActiveUploadedFile(fileID, now);
        if (uploadedFile == null)
          { 
            throw new GUIManagerException("unknown uploaded file with id ", fileID);
          }
      }
  }
}
