/*****************************************************************************
*
*  SubscriberRelatives.java
*
*****************************************************************************/

package com.evolving.nglm.evolution;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;

import org.apache.kafka.connect.data.Schema;
import org.apache.kafka.connect.data.SchemaAndValue;
import org.apache.kafka.connect.data.SchemaBuilder;
import org.apache.kafka.connect.data.Struct;
import org.json.simple.JSONObject;

import com.evolving.nglm.core.AlternateID;
import com.evolving.nglm.core.ConnectSerde;
import com.evolving.nglm.core.Deployment;
import com.evolving.nglm.core.JSONUtilities;
import com.evolving.nglm.core.ReferenceDataReader;
import com.evolving.nglm.core.SchemaUtilities;
import com.evolving.nglm.core.SystemTime;
import com.evolving.nglm.evolution.SubscriberProfileService.SubscriberProfileServiceException;

public class SubscriberRelatives
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
    schemaBuilder.name("subscriber_hierarchy");
    schemaBuilder.version(SchemaUtilities.packSchemaVersion(0));
    schemaBuilder.field("parentSubscriberID", Schema.OPTIONAL_STRING_SCHEMA);
    schemaBuilder.field("childrenSubscriberIDs", SchemaBuilder.array(Schema.STRING_SCHEMA));
    schema = schemaBuilder.build();
  };

  //
  // serde
  //

  private static ConnectSerde<SubscriberRelatives> serde = new ConnectSerde<SubscriberRelatives>(schema, false, SubscriberRelatives.class, SubscriberRelatives::pack, SubscriberRelatives::unpack);

  //
  // accessor
  //

  public static Schema schema() { return schema; }
  public static ConnectSerde<SubscriberRelatives> serde() { return serde; }
 
  /*****************************************
   *
   * data
   *
   *****************************************/

  private String parentSubscriberID;
  private List<String> childrenSubscriberIDs; 

  /*****************************************
   *
   * accessors
   *
   *****************************************/

  public String getParentSubscriberID() { return parentSubscriberID; }
  public List<String> getChildrenSubscriberIDs() { return childrenSubscriberIDs; }

  /*****************************************
   *
   * setters
   *
   *****************************************/

  public void setParentSubscriberID(String parentSubscriberID) { this.parentSubscriberID = parentSubscriberID; }
  public void addChildSubscriberID(String childSubscriberID) 
  {
    if(!this.childrenSubscriberIDs.contains(childSubscriberID)) 
      {
        this.childrenSubscriberIDs.add(childSubscriberID);
      }
    
    while(this.childrenSubscriberIDs.size() > 10)
      {
        this.childrenSubscriberIDs.remove(this.childrenSubscriberIDs.remove(0));
      }
  }
  public void removeChildSubscriberID(String childSubscriberID)
  {
    this.childrenSubscriberIDs.remove(childSubscriberID);
  }
  
  /*****************************************
   *
   * constructor
   *
   *****************************************/

  public SubscriberRelatives(String parentSubscriberID, List<String> childrenSubscriberIDs)
    {
      this.parentSubscriberID = parentSubscriberID;
      this.childrenSubscriberIDs = childrenSubscriberIDs;
    }

  /*****************************************
   *
   * constructor -- empty
   *
   *****************************************/

  public SubscriberRelatives()
    {
      this.parentSubscriberID = null;
      this.childrenSubscriberIDs = new ArrayList<String>();
    }
  
  /*****************************************
  *
  *  getJSONRepresentation
  *
  *****************************************/
    
  public JSONObject getJSONRepresentation(String relationshipID, SubscriberProfileService subscriberProfileService, ReferenceDataReader<String, SubscriberGroupEpoch> subscriberGroupEpochReader)
    {
      HashMap<String, Object> json = new HashMap<String, Object>();
      
      //
      //  obj
      //
      
      json.put("relationshipID", relationshipID);
      json.put("relationshipName", Deployment.getSupportedRelationships().get(relationshipID) != null ? Deployment.getSupportedRelationships().get(relationshipID).getName() : null);
      json.put("relationshipDisplay", Deployment.getSupportedRelationships().get(relationshipID) != null ? Deployment.getSupportedRelationships().get(relationshipID).getDisplay() : null);
      
      //
      //  parent
      //
      
      HashMap<String, Object> parentJsonMap = new HashMap<String, Object>();
      try
        {
          if (getParentSubscriberID() != null && !getParentSubscriberID().isEmpty())
            {
              SubscriberProfile parentProfile = subscriberProfileService.getSubscriberProfile(getParentSubscriberID());
              if (parentProfile != null)
                {
                  parentJsonMap.put("subscriberID", getParentSubscriberID());
                  SubscriberEvaluationRequest evaluationRequest = new SubscriberEvaluationRequest(parentProfile, subscriberGroupEpochReader, SystemTime.getCurrentTime(), parentProfile.getTenantID());
                  for (String id : Deployment.getAlternateIDs().keySet())
                    {
                      AlternateID alternateID = Deployment.getAlternateIDs().get(id);
                      CriterionField criterionField = Deployment.getProfileCriterionFields().get(alternateID.getProfileCriterionField());
                      if (criterionField != null)
                        {
                          String alternateIDValue = (String) criterionField.retrieve(evaluationRequest);
                          parentJsonMap.put(alternateID.getID(), alternateIDValue);
                        }
                    }
                }
            }
        } 
      catch (SubscriberProfileServiceException e)
        {
          e.printStackTrace();
        }
      json.put("parentDetails", parentJsonMap.isEmpty() ? null : JSONUtilities.encodeObject(parentJsonMap));
      
      //
      //  children
      //
      
      json.put("numberOfChildren", getChildrenSubscriberIDs().size());
      json.put("childrenSubscriberIDs", JSONUtilities.encodeArray(new ArrayList<String>(unDated(getChildrenSubscriberIDs()))));
      
      //
      //  result
      //
      
      return JSONUtilities.encodeObject(json);
    }
  
 
  private Collection<? extends String> unDated(List<String> childrenSubscriberIDs2) {
	  List<String> unDatedIds=new ArrayList();
	  for(String datedString:childrenSubscriberIDs2) {
		  unDatedIds.add(datedString.substring(0,datedString.lastIndexOf(GUIManager.DATE_SEPERATOR)-1));
	  }
	  return unDatedIds;
	  
  }
public JSONObject getNewJSONRepresentation(String relationshipID, SubscriberProfileService subscriberProfileService, ReferenceDataReader<String, SubscriberGroupEpoch> subscriberGroupEpochReader, int tenantID)
  {
    HashMap<String, Object> json = new HashMap<String, Object>();
    
    //
    //  obj
    //
    
    json.put("relationshipID", relationshipID);
    json.put("relationshipName", Deployment.getSupportedRelationships().get(relationshipID) != null ? Deployment.getSupportedRelationships().get(relationshipID).getName() : null);
    json.put("relationshipDisplay", Deployment.getSupportedRelationships().get(relationshipID) != null ? Deployment.getSupportedRelationships().get(relationshipID).getDisplay() : null);
    
    //
    //  parent
    //
    
    HashMap<String, Object> parentJsonMap = new HashMap<String, Object>();
    try
      {
        if (getParentSubscriberID() != null && !getParentSubscriberID().isEmpty())
          {
            SubscriberProfile parentProfile = subscriberProfileService.getSubscriberProfile(getParentSubscriberID());
            if (parentProfile != null)
              {
                parentJsonMap.put("subscriberID", getParentSubscriberID());
                SubscriberEvaluationRequest evaluationRequest = new SubscriberEvaluationRequest(parentProfile, subscriberGroupEpochReader, SystemTime.getCurrentTime(), parentProfile.getTenantID());
                for (String id : Deployment.getAlternateIDs().keySet())
                  {
                    AlternateID alternateID = Deployment.getAlternateIDs().get(id);
                    CriterionField criterionField = Deployment.getProfileCriterionFields().get(alternateID.getProfileCriterionField());
                    if (criterionField != null)
                      {
                        String alternateIDValue = (String) criterionField.retrieve(evaluationRequest);
                        parentJsonMap.put(alternateID.getID(), alternateIDValue);
                      }
                  }
              }
          }
      } 
    catch (SubscriberProfileServiceException e)
      {
        e.printStackTrace();
      }
    json.put("parentDetails", parentJsonMap.isEmpty() ? null : JSONUtilities.encodeObject(parentJsonMap));
    
    //
    //  children
    //
    
    json.put("numberOfChildren", getChildrenSubscriberIDs().size());
    json.put("childrenSubscriberIDs", getDatedMapOfChildren(getChildrenSubscriberIDs(), tenantID));
    
    //
    //  result
    //
    
    return JSONUtilities.encodeObject(json);
  }
  
  private Object getDatedMapOfChildren(List<String> childrenSubscriberIDs,int tenantID) {
	Map datedMap=new HashMap<String,String>();
	String date,childId;
	for(String child: childrenSubscriberIDs ){
		if(child!=null && !child.isEmpty()) {
			date=new String();
			childId=new String();
			childId=new String();
			childId=child.substring(0,child.lastIndexOf(GUIManager.DATE_SEPERATOR)-1);
			date=new String();
		//date=getDateString(child.substring(child.lastIndexOf("@")+1),tenantID);
		date=child.substring(child.lastIndexOf(GUIManager.DATE_SEPERATOR)+1);
		datedMap.put(childId, date);
		}
		}
	
	return datedMap;
}
/*****************************************
   *
   * pack
   *
   *****************************************/

  public static Object pack(Object value)
    {
      SubscriberRelatives hierarchy = (SubscriberRelatives) value;
      Struct struct = new Struct(schema);
      struct.put("parentSubscriberID", hierarchy.getParentSubscriberID());
      struct.put("childrenSubscriberIDs", packChildrenSubscriberIDs(hierarchy.getChildrenSubscriberIDs()));
      return struct;
    }

  /****************************************
   *
   * packChildrenSubscriberIDs
   *
   ****************************************/

  private static List<Object> packChildrenSubscriberIDs(List<String> childrenSubscriberIDs)
    {
      List<Object> result = new ArrayList<Object>();
      for (String childID : childrenSubscriberIDs)
        {
          result.add(childID);
        }
      return result;
    }

  /*****************************************
   *
   * unpack
   *
   *****************************************/

  public static SubscriberRelatives unpack(SchemaAndValue schemaAndValue)
    {
      //
      // data
      //

      Schema schema = schemaAndValue.schema();
      Object value = schemaAndValue.value();
      Integer schemaVersion = (schema != null) ? SchemaUtilities.unpackSchemaVersion0(schema.version()) : null;

      //
      // unpack
      //

      Struct valueStruct = (Struct) value;
      String parentSubscriberID = valueStruct.getString("parentSubscriberID");
      List<String> childrenSubscriberIDs = unpackChildrenSubscriberIDs((List<String>) valueStruct.get("childrenSubscriberIDs"));
      
      //
      // validate
      //

      if (childrenSubscriberIDs == null) 
        {
          childrenSubscriberIDs = new ArrayList<String>();
        }

      //
      // return
      //

      return new SubscriberRelatives(parentSubscriberID, childrenSubscriberIDs);
    }
  
  /*****************************************
   *
   * unpackChildrenSubscriberIDs
   *
   *****************************************/

  private static List<String> unpackChildrenSubscriberIDs(List<String> childrenSubscriberIDs)
    {
      List<String> result = new ArrayList<String>();
      for (String childID : childrenSubscriberIDs)
        {
          result.add(childID);
        }
      return result;
    }

  
  
  /*****************************************
  *
  *  getDateString
  *
  *****************************************/

  public String getDateString(String date,int tenantID)

  {
    String result = null;
    if (null == date) return result;
    System.out.println("=================="+date+"======================");
        SimpleDateFormat dateFormat = new SimpleDateFormat(Deployment.getAPIresponseDateFormat());   // TODO EVPRO-99
        dateFormat.setTimeZone(TimeZone.getTimeZone(Deployment.getDeployment(tenantID).getTimeZone()));
        result = dateFormat.format(date);
     
    return result;
  }
}
