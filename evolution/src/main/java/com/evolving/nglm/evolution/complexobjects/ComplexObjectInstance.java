/****************************************************************************
*
*  ComplexObjectInstances.java
*
****************************************************************************/

package com.evolving.nglm.evolution.complexobjects;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.kafka.connect.data.Schema;
import org.apache.kafka.connect.data.SchemaAndValue;
import org.apache.kafka.connect.data.SchemaBuilder;
import org.apache.kafka.connect.data.Struct;
import org.json.simple.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.evolving.nglm.core.ConnectSerde;
import com.evolving.nglm.core.Deployment;
import com.evolving.nglm.core.SchemaUtilities;
import com.evolving.nglm.core.SystemTime;
import com.evolving.nglm.evolution.EvaluationCriterion.CriterionDataType;
import com.evolving.nglm.evolution.MetricHistory;
import com.evolving.nglm.evolution.datamodel.DataModelFieldValue;

public class ComplexObjectInstance
{
  
  //
  //  logger
  //

  private static final Logger log = LoggerFactory.getLogger(ComplexObjectInstance.class);

  /*****************************************
  *
  *  schema
  *
  *****************************************/

  //
  //  schema
  //

  private static Schema schema = null;
  
  private static ComplexObjectTypeService complexObjectTypeService;
  static
  {
    complexObjectTypeService = new ComplexObjectTypeService(System.getProperty("broker.servers"), "complexobjectinstance-complexobjecttypeservice", Deployment.getComplexObjectTypeTopic(), false);
    complexObjectTypeService.start();
        
    SchemaBuilder schemaBuilder = SchemaBuilder.struct();
    schemaBuilder.name("complex_object_instance");
    schemaBuilder.version(SchemaUtilities.packSchemaVersion(1));
    schemaBuilder.field("complexObjectTypeID", Schema.STRING_SCHEMA);
    schemaBuilder.field("elementID", Schema.STRING_SCHEMA);
    schemaBuilder.field("fieldValues", Schema.BYTES_SCHEMA);
    schemaBuilder.field("metricHistories", SchemaBuilder.map(Schema.STRING_SCHEMA, MetricHistory.schema()).name("subscriber_complex_histories").schema());
    schema = schemaBuilder.build();
  };

  //
  // serde
  //

  private static ConnectSerde<ComplexObjectInstance> serde = new ConnectSerde<ComplexObjectInstance>(schema, false, ComplexObjectInstance.class, ComplexObjectInstance::pack, ComplexObjectInstance::unpack);
  
  //
  // accessor
  //

  public static Schema schema() { return schema; }
  public static ConnectSerde<ComplexObjectInstance> serde() { return serde; }

  /*****************************************
  *
  * data
  *
  *****************************************/

  private String complexObjectTypeID;
  private String elementID;
  private Map<String, DataModelFieldValue> fieldValues; // key is the fieldName
  private Map<String, MetricHistory> metricHistories; // key is the fieldName

  // internal, to avoid un-needed deserialization/serialization
  private boolean modified=false;
  private byte[] byteRepresentation;

  // KEEP ACCESS fieldValues ONLY THROW THIS getter/setter ("on-demand" only serialization/deserialization)

  // this one don't turn on the "modified" boolean, so if fieldValues accessed from it is modified, modification won't be serialized back
  public Map<String, DataModelFieldValue> getFieldValuesReadOnly() {
    if(fieldValues==null) fieldValues=unserializeFields();
    return fieldValues;
  }
  // accessing with this one, modification of the hashmap will be serialized and well saved
  public Map<String, DataModelFieldValue> getFieldValuesForModification() {
    modified=true;
    return getFieldValuesReadOnly();
  }

  // setter will always modify
  public void setFieldValues(Map<String, DataModelFieldValue> fieldValues)
  {
    modified = true;
    this.fieldValues = fieldValues;
  }

  // normal getters
  public String getComplexObjectTypeID() { return complexObjectTypeID; }
  public String getElementID() { return elementID; }
  public Map<String, MetricHistory> getMetricHistories() { return metricHistories; }
  public Map<String, MetricHistory> initAndGetMetricHistories()
  {
    this.metricHistories = new HashMap<String, MetricHistory>();
    return metricHistories;
  }

  /*****************************************
  *
  * constructor default
  *
  *****************************************/

  public ComplexObjectInstance(String complexObjectTypeID, String elementID)
  {
    this.complexObjectTypeID = complexObjectTypeID;
    this.elementID = elementID;
  }
  
  
  /*****************************************
  *
  * constructor unpack
  *
  *****************************************/

  public ComplexObjectInstance(String complexObjectTypeID, String elementID, byte[] fieldValues, Map<String,MetricHistory> metricHistories)
  {
    this(complexObjectTypeID,elementID);
    this.byteRepresentation = fieldValues;// keep only byte representation, will be unpack only "on demand"
    this.metricHistories = metricHistories;
  }

  /*****************************************
  *
  * pack
  *
  *****************************************/

  public static Object pack(Object value)
  {
    ComplexObjectInstance complexObjectInstance = (ComplexObjectInstance) value;
    Struct struct = new Struct(schema);

    log.info("RAJ K pack method called fieldValues {}", complexObjectInstance.serializeFields());
    struct.put("complexObjectTypeID", complexObjectInstance.getComplexObjectTypeID());
    struct.put("elementID", complexObjectInstance.getElementID());
    struct.put("fieldValues", complexObjectInstance.serializeFields());
    struct.put("metricHistories", packMetricHistories(complexObjectInstance.getMetricHistories()));
    return struct;
  }
  
  /*****************************************
  *
  *  unpack
  *
  *****************************************/

  public static ComplexObjectInstance unpack(SchemaAndValue schemaAndValue)
  {
    //
    //  data
    //

    Schema schema = schemaAndValue.schema();
    Object value = schemaAndValue.value();
    Integer schemaVersion = (schema != null) ? SchemaUtilities.unpackSchemaVersion0(schema.version()) : null;

    //
    //  unpack
    //

    Struct valueStruct = (Struct) value;
    String complexObjectTypeID = valueStruct.getString("complexObjectTypeID");
    String elementID = valueStruct.getString("elementID");
    byte[] fieldValues = valueStruct.getBytes("fieldValues");
    Map<String,MetricHistory> metricHistories = schema.field("metricHistories") != null ? unpackMetricHistories(schema.field("metricHistories").schema(), (Map<String,Object>) valueStruct.get("metricHistories")): Collections.<String,MetricHistory>emptyMap();
    
    //
    //  return
    //

    return new ComplexObjectInstance(complexObjectTypeID, elementID, fieldValues, metricHistories);
  }
  
  public static Map<String,Object> packMetricHistories(Map<String,MetricHistory> metricHistories)
  {
    Map<String,Object> result = new HashMap<String,Object>();
    if (metricHistories != null)
      {
        for (String subfieldName : metricHistories.keySet())
          {
            result.put(subfieldName, MetricHistory.pack(metricHistories.get(subfieldName)));
          }
      }
    return result;
  }
  
  private static Map<String,MetricHistory> unpackMetricHistories(Schema schema, Map<String,Object> value)
  {
    //
    //  get schema for ScoreBalances
    //

    Schema metricHistoriesSchema = schema.valueSchema();

    //
    //  unpack
    //

    Map<String,MetricHistory> result = new HashMap<>();
    for (String key : value.keySet())
      {
        result.put(key, MetricHistory.unpack(new SchemaAndValue(metricHistoriesSchema, value.get(key))));
      }

    //
    //  return
    //

    return result;
  }

  /*****************************************
  *
  *  serializeFields
  *
  *****************************************/

  private byte[] serializeFields()
  {
    // for each field of this complex object, let switch on each supported datatype and encode a tag/length/value
    // <field_private_ID 2 bytes><length 2 bytes><value>, so overhead of 4 bytes per value
    ComplexObjectType complexObjectType = complexObjectTypeService.getActiveComplexObjectType(complexObjectTypeID, SystemTime.getCurrentTime());
    if(complexObjectType == null) { /*Should not happen as the detection is done before calling Pack */ return new byte[] {}; };

    return serialize(complexObjectType.getSubfields());
  }

  private byte[] serialize(Map<Integer, ComplexObjectTypeSubfield> complexObjectTypeFields)
  {

    if(!modified) return byteRepresentation;// no modification happened, just sending pack what we unpacked

    List<byte[]> resultList = new ArrayList<>(fieldValues.size());
    int finalBytesSize=0;
    for(DataModelFieldValue fieldValue : fieldValues.values())
      {
        ComplexObjectTypeSubfield fieldType = complexObjectTypeFields.get(fieldValue.getPrivateFieldID());
        if(fieldType != null)
          {
            if(fieldValue.getValue() == null) {/*no need to serialize*/ continue;}
            switch (fieldType.getCriterionDataType())
            {
              case BooleanCriterion:
                Boolean value = (Boolean)fieldValue.getValue();
                byte[] result = new byte[5];
                result[0] = (byte) ((fieldType.getPrivateID() & 0xFF00) >> 8);
                result[1] = (byte) (fieldType.getPrivateID() & 0xFF);
                result[2] = 0;
                result[3] = (byte) (value != null ? 1 : 0);
                result[4] = (byte) (value.booleanValue() ? 1 : 0);
                resultList.add(result);
                finalBytesSize+=result.length;
                break;
                
              case IntegerCriterion : /*Integer and Long are considered the same, all seen as Long */
              case DateCriterion : 
                int size = 0;
                byte[] tempByte = new byte[8];
                long valueLong = 0;
                if(fieldType.getCriterionDataType().equals(CriterionDataType.DateCriterion))
                  {
                    valueLong = ((Date)fieldValue.getValue()).getTime();
                  }
                else
                  {
                    if(fieldValue.getValue() instanceof Long)
                      {
                        valueLong = ((Long)fieldValue.getValue()).longValue();
                      }
                    else if(fieldValue.getValue() instanceof Integer)
                      {
                        valueLong = ((Integer)fieldValue.getValue()).intValue();
                      }
                  }
                byte b0 = (byte) (valueLong >> 56);
                tempByte[0] = b0;
                if(b0 != 0) size = 8;
                
                byte b1 = (byte) ((valueLong >> 48) & 0xFF);
                tempByte[1] = b1;                
                if(size == 0 && b1 != 0) size = 7;
                
                byte b2 = (byte) ((valueLong >> 40) & 0xFF);
                tempByte[2] = b2;                
                if(size == 0 && b2 != 0) size = 6;
                
                byte b3 = (byte) ((valueLong >> 32) & 0xFF);
                tempByte[3] = b3;                
                if(size == 0 && b3 != 0) size = 5;
                
                byte b4 = (byte) ((valueLong >> 24) & 0xFF);
                tempByte[4] = b4;                
                if(size == 0 && b4 != 0) size = 4;
                
                byte b5 = (byte) ((valueLong >> 16) & 0xFF);
                tempByte[5] = b5;                
                if(size == 0 && b5 != 0) size = 3;
                
                byte b6 = (byte) ((valueLong >> 8) & 0xFF);
                tempByte[6] = b6;                
                if(size == 0 && b6 != 0) size = 2;
                
                byte b7 = (byte) (valueLong & 0xFF);
                tempByte[7] = b7;                
                if(size == 0 && b7 != 0) size = 1;
                
                byte[] resultLong = new byte[size + 4]; // +4 for the TL part                
                
                resultLong[0] = (byte) ((fieldType.getPrivateID() & 0xFF00) >> 8);
                resultLong[1] = (byte) (fieldType.getPrivateID() & 0xFF);
                resultLong[2] = (byte) 0;
                resultLong[3] = (byte) size;

                int resultIndex = 4;
                for(int i = 0; i < size; i++) {
                  resultLong[resultIndex] = tempByte[8 - size + i];
                  resultIndex++;
                }
                resultList.add(resultLong);
                finalBytesSize+=resultLong.length;
                break;                

              case StringCriterion :
                String valueString = (String)fieldValue.getValue();
                byte[] stringBytes = valueString.getBytes();
                int stringLength = stringBytes.length;
                byte[] resultString = new byte[4];

                resultString[0] = (byte) ((fieldType.getPrivateID() & 0xFF00) >> 8);
                resultString[1] = (byte) (fieldType.getPrivateID() & 0xFF);
                resultString[2] = (byte) ((stringLength >> 8) & 0xFF);
                resultString[3] = (byte) (stringLength & 0xFF);
                resultList.add(resultString);
                finalBytesSize+=resultString.length;
                resultList.add(stringBytes);
                finalBytesSize+=stringBytes.length;
                break;
              case StringSetCriterion :
                List<String> valueStringSet = (List<String>)fieldValue.getValue();
                if(valueStringSet.size() == 0) { continue; } // no need to serialyse  
                byte[] header = new byte[4];
                int totalTLVLength = 0; // max 2 bytes to encode this
                
                List<byte[]> allStrings = new ArrayList<>();
                for(String current : valueStringSet)
                  {
                    if(current == null) { continue; }
                    int length = current.length();
                    byte[] forThisString = new byte[length + 2]; // because of length coded into 2 bytes
                    totalTLVLength = totalTLVLength + length + 2;
                    forThisString[0] = (byte) ((length >> 8) & 0xFF);
                    forThisString[1] = (byte) (length & 0xFF);
                    for(int i = 0; i < length; i++)
                      {
                        forThisString[i+2] = current.getBytes()[i];
                      }
                    allStrings.add(forThisString);
                  }
                
                header[0] = (byte) ((fieldType.getPrivateID() & 0xFF00) >> 8);
                header[1] = (byte) (fieldType.getPrivateID() & 0xFF);
                header[2] = (byte) ((totalTLVLength >> 8) & 0xFF);
                header[3] = (byte) (totalTLVLength & 0xFF);
                
                resultList.add(header);
                finalBytesSize+=header.length;
                for(byte[] current : allStrings)
                  {
                    resultList.add(current);
                    finalBytesSize+=current.length;
                  }          
                break;
              default:
                log.info("RAJ K data type {}", fieldType.getCriterionDataType()); // should handle with blank array ??               
                break;
              }
          }
        else 
          {
            // the field have disappeared from the config
            // nothing to do
          }
      }
    byte[] result = new byte[finalBytesSize];
    ByteBuffer buffer = ByteBuffer.wrap(result);
    for(byte[] toAdd:resultList) buffer.put(toAdd);
    log.info("RAJ K result {}", result);
    return result;
  }
  
  /*****************************************
  *
  *  ComplexObjectinstanceFieldValue
  *
  *****************************************/

  private Map<String, DataModelFieldValue> unserializeFields()
  {

    ComplexObjectType complexObjectType = complexObjectTypeService.getActiveComplexObjectType(complexObjectTypeID, SystemTime.getCurrentTime());
    if(complexObjectType == null) { return null; }
    if(byteRepresentation == null) { return new HashMap<String, DataModelFieldValue>();}
    return deserialize(byteRepresentation, complexObjectType.getSubfields(), this.complexObjectTypeID);
  }

  private static HashMap<String, DataModelFieldValue> deserialize(byte[] fieldValues, Map<Integer, ComplexObjectTypeSubfield> complexObjectTypeFields, String complexObjectTypeID)
  {
    HashMap<String, DataModelFieldValue> result = new HashMap<String, DataModelFieldValue>();
    ByteBuffer buffer = ByteBuffer.wrap(fieldValues);
    
    int maxPosition = fieldValues.length;
    while(buffer.position() < maxPosition)
      {
        // field ID 
        int fieldID = (buffer.get() << 8) | (0xFF & buffer.get());
        // size
        int size = (buffer.get() << 8) | (0xFF & buffer.get());
        // value
        byte[] value = new byte[size];
        buffer.get(value, 0, value.length);
        
        // now decode the value depending of the type:
        ComplexObjectTypeSubfield fieldType = complexObjectTypeFields.get(fieldID);
        if(fieldType == null) { 
          if(log.isInfoEnabled()){log.info("Can't retrieve complexe object field type " + fieldID + " for " + complexObjectTypeID);   continue; } // can be normal if the field has been removed from 
        }
        
        switch (fieldType.getCriterionDataType())
          {
          case BooleanCriterion:
            if(value[0] == 0) 
              {
                DataModelFieldValue cofv = new DataModelFieldValue(fieldType.getSubfieldName(), fieldID, Boolean.FALSE);
                result.put(fieldType.getSubfieldName(), cofv);
              }
            else if(value[0] == 1)
              {
                DataModelFieldValue cofv = new DataModelFieldValue(fieldType.getSubfieldName(), fieldID, Boolean.TRUE);
                result.put(fieldType.getSubfieldName(), cofv);                
              }
            else 
              {
                // should not happen
                log.warn("Wrong value for a boolean " + value[0]);
              }
            break;
            
          case IntegerCriterion : /*Integer and Long are considered the same, all seen as Long */
            long valueLong = 0;
            for(int i = 0; i < size; i++)
              {
                valueLong = (valueLong << 8) | (0xFF & value[i]);
              }
            DataModelFieldValue cofv = new DataModelFieldValue(fieldType.getSubfieldName(), fieldID, valueLong);
            result.put(fieldType.getSubfieldName(), cofv);  
            break;
            
          case DateCriterion : 
            valueLong = 0;
            for(int i = 0; i < size; i++)
              {
                valueLong = (valueLong << 8) | (0xFF & value[i]);
              }
            Date d = new Date(valueLong);
            cofv = new DataModelFieldValue(fieldType.getSubfieldName(), fieldID, d);
            result.put(fieldType.getSubfieldName(), cofv);  
           break;                

          case StringCriterion :
            String s = new String(value);
            cofv = new DataModelFieldValue(fieldType.getSubfieldName(), fieldID, s);
            result.put(fieldType.getSubfieldName(), cofv);  
            break;
            
          case StringSetCriterion :
            int arrayPosition = 0;
            List<String> stringSet = null;
            
            while(arrayPosition < value.length - 2)
              {
                int length = (value[arrayPosition] << 8) | (value[arrayPosition+1]);
                arrayPosition = arrayPosition + 2;
                byte[] stringBytes = new byte[length];
                for(int i = 0; i < length; i++)
                  {
                    stringBytes[i] = value[arrayPosition];
                    arrayPosition++;
                    if(stringSet == null) { stringSet = new ArrayList<>(); }
                    
                  }
                stringSet.add(new String(stringBytes));
              }
            if(stringSet != null) 
              {
                cofv = new DataModelFieldValue(fieldType.getSubfieldName(), fieldID, stringSet);
                result.put(fieldType.getSubfieldName(), cofv); 
              }            
            break;
          default:
            break;
          }
      }
    return result;
  }

  /*****************************************
  *
  *  toString
  *
  *****************************************/

  @Override
  public String toString()
  {
    return "ComplexObjectInstance [complexObjectTypeID=" + complexObjectTypeID + ", fieldValues=" + fieldValues + "]";
  }
  
  public static void main(String[] args)
  {
    Date date = new Date();
    
    Map<Integer, ComplexObjectTypeSubfield> fieldTypes = new HashMap<>();
    JSONObject fieldTypeJSON = new JSONObject();
    fieldTypeJSON.put("fieldName", "NBPatatoes");
    fieldTypeJSON.put("fieldDataType", "integer");
    ComplexObjectTypeSubfield fieldTypeInteger = new ComplexObjectTypeSubfield(fieldTypeJSON);
    fieldTypes.put(fieldTypeInteger.getPrivateID(), fieldTypeInteger);
    System.out.println("integer private field " + fieldTypeInteger.getPrivateID());
    
    fieldTypeJSON = new JSONObject();
    fieldTypeJSON.put("fieldName", "eyeColor");
    fieldTypeJSON.put("fieldDataType", "string");
    ComplexObjectTypeSubfield fieldTypeString = new ComplexObjectTypeSubfield(fieldTypeJSON);
    fieldTypes.put(fieldTypeString.getPrivateID(), fieldTypeString);
    System.out.println("string private field " + fieldTypeString.getPrivateID());
    
    fieldTypeJSON = new JSONObject();
    fieldTypeJSON.put("fieldName", "dateOfBirth");
    fieldTypeJSON.put("fieldDataType", "date");
    ComplexObjectTypeSubfield fieldTypeDate = new ComplexObjectTypeSubfield(fieldTypeJSON);
    fieldTypes.put(fieldTypeDate.getPrivateID(), fieldTypeDate);
    System.out.println("date private field " + fieldTypeDate.getPrivateID());
    
    Map<String, DataModelFieldValue> values = new HashMap<>();
    ComplexObjectInstance instance = new ComplexObjectInstance("AComplexObjectName", "element1");    

    DataModelFieldValue value = new DataModelFieldValue(fieldTypeInteger.getSubfieldName(), fieldTypeInteger.getPrivateID(), new Long(1556788992556635323L));
    values.put(fieldTypeInteger.getSubfieldName(), value);
                                                                                                                   
    value = new DataModelFieldValue(fieldTypeString.getSubfieldName(), fieldTypeString.getPrivateID(), "brown");
    values.put(fieldTypeString.getSubfieldName(), value);

    value = new DataModelFieldValue(fieldTypeString.getSubfieldName(), fieldTypeDate.getPrivateID(), date);
    values.put(fieldTypeDate.getSubfieldName(), value);

    instance.setFieldValues(values);
    byte[] ser = instance.serialize(fieldTypes);

    instance.getFieldValuesForModification();//for real deserialize after
    HashMap<String, DataModelFieldValue> unserValues = deserialize(ser, fieldTypes, "AComplexObjectName");
    
    System.out.println("Integer " + (unserValues.get(fieldTypeInteger.getPrivateID()).equals(values.get(fieldTypeInteger.getPrivateID()))));
    
    
    System.out.println(unserValues);
  }
}
