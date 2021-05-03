package com.evolving.nglm.evolution;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.ArrayList;
import java.util.List;

import javax.management.RuntimeErrorException;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

import com.evolving.nglm.core.Deployment;
import com.evolving.nglm.core.JSONUtilities;
import com.evolving.nglm.core.Pair;

public class EDREventsESFieldsDetails
{
  //
  //  data
  //
  
  private String eventName;
  private Class esModelClass;
  private List<ESField> fields;

  /***************************
   * 
   * EDREventsESFieldsDetails
   * 
   ***************************/

  public EDREventsESFieldsDetails(JSONObject jsonRoot)
  {
    this.eventName = JSONUtilities.decodeString(jsonRoot, "eventName", true).toUpperCase();
    String esModelClassStr = JSONUtilities.decodeString(jsonRoot, "esModelClass", true);
    try
      {
        this.esModelClass = Class.forName(esModelClassStr);
      } 
    catch (ClassNotFoundException e)
      {
        throw new RuntimeException("class not found " + e.getMessage());
      }
    this.fields = decodeFields(jsonRoot);

  }

  /***************************
   * 
   * decodeFields
   * 
   ***************************/
  
  private List<ESField> decodeFields(JSONObject jsonRoot)
  {
    List<ESField> fields = new ArrayList<ESField>();
    JSONArray fieldsArray = JSONUtilities.decodeJSONArray(jsonRoot, "fields", true);
    for (int i = 0; i < fieldsArray.size(); i++)
      {
        JSONObject fieldJSON = (JSONObject) fieldsArray.get(i);
        ESField field = new ESField(fieldJSON);
        fields.add(field);

      }
    return fields;
  }
  
  //
  //  getters
  //
  
  public String getEventName() { return eventName; }
  public List<ESField> getFields() { return fields; }
  public Class getEsModelClass() { return esModelClass; }
  
  public class ESField
  {
    private String fieldName;
    private String retrieverName;
    private MethodHandle retriever = null;
    
    public ESField(JSONObject fieldJSON)
    {
      this.fieldName = JSONUtilities.decodeString(fieldJSON, "name", true);
      this.retrieverName = JSONUtilities.decodeString(fieldJSON, "retriever", true);
      
      MethodType methodType = MethodType.methodType(Object.class);
      MethodHandles.Lookup lookup = MethodHandles.lookup();
      try
        {
          this.retriever = lookup.findStatic(esModelClass, retrieverName, methodType);
        } 
      catch (NoSuchMethodException | IllegalAccessException e)
        {
          throw new RuntimeException("retriever error " + e.getMessage());
        }
    }
    
    public String getFieldName () { return fieldName; }
    public String getRetrieverName () { return retrieverName; }
    public MethodHandle getRetriever () { return retriever; }
    
  }

}
