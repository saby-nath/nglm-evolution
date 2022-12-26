package com.evolving.nglm.evolution;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.json.simple.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.evolving.nglm.core.JSONUtilities;
import com.evolving.nglm.core.ServerRuntimeException;
import com.evolving.nglm.evolution.GUIManagedObject.GUIManagedObjectType;

/*****************************************
 * 
 *  GUIManagedObjectDependencyHelper
 *
 *****************************************/

public class GUIManagedObjectDependencyHelper
{
  //
  //  log
  //
  
  private static final Logger log = LoggerFactory.getLogger(GUIManagedObjectDependencyHelper.class);
  
  /****************************************
  *
  *  createDependencyTreeMAP
  *
  ****************************************/
  
  public static void createDependencyTreeMAP(Map<String, GUIDependencyModelTree> guiDependencyModelTreeMap, GUIDependencyModelTree guiDependencyModelTree, Set<String>  dependencies, String objectID, List<JSONObject> dependencyListOutput, boolean fiddleTest, List<GUIService> guiServiceList, int tenantID) throws IllegalAccessException, IllegalArgumentException, InvocationTargetException, NoSuchMethodException, SecurityException
  {
    log.debug("createDependencyTreeMAP for {} - ID {} will look into {} types", guiDependencyModelTree.getGuiManagedObjectType(), objectID, dependencies);
    for (String dependency : dependencies)
      {
        //
        // stored and container
        //
        
        Collection<GUIManagedObject> containerObjectList = new ArrayList<GUIManagedObject>();
        
        //
        // get storedObjectList
        //
        
        Class serviceClass = guiDependencyModelTreeMap.get(dependency.toLowerCase()).getServiceClass();
        Object serviceObject  = getService(guiServiceList, serviceClass);
        Method retriver = serviceClass.getSuperclass().getDeclaredMethod("getStoredGUIManagedObjects", int.class);
        Collection<GUIManagedObject> storedObjectList = (Collection<GUIManagedObject>) retriver.invoke(serviceObject, tenantID);
        
        //
        //  containerObjectList
        //
        
        if (storedObjectList != null)
          {
            for (GUIManagedObject guiManagedObject : storedObjectList)
              {
            	if(guiManagedObject.getGUIManagedObjectType()==GUIManagedObjectType.Other || guiManagedObject.getGUIManagedObjectType()==GUIManagedObjectType.Unknown || guiManagedObject.getGUIManagedObjectType().name().toLowerCase().equals(dependency.toLowerCase())){
                Map<String, List<String>> guiDependencies = guiManagedObject.getGUIDependencies(guiServiceList, guiManagedObject.getTenantID());
                
                 if (guiDependencies != null && !guiDependencies.isEmpty())
                  {
                    List<String> guiDependencyList = guiDependencies.get(guiDependencyModelTree.getGuiManagedObjectType().toLowerCase());
                    if (guiDependencyList != null && guiDependencyList.contains(objectID)) containerObjectList.add(guiManagedObject);
                  }
                }
                
              
              }
          }

        //
        // presentation/recursion
        //

        for (GUIManagedObject guiManagedObject : containerObjectList)
          {
            //
            // prepare recursion data 
            //
            
            GUIDependencyModelTree netxGUIDependencyModelTree = guiDependencyModelTreeMap.get(dependency);
            String nextObjectID = guiManagedObject.getGUIManagedObjectID();
            List<JSONObject> nextDependencyOutputList = new LinkedList<JSONObject>();
            
            //
            //  recursion
            //
            
            if (netxGUIDependencyModelTree!= null) createDependencyTreeMAP(guiDependencyModelTreeMap, netxGUIDependencyModelTree, netxGUIDependencyModelTree.getDependencyList(), nextObjectID, nextDependencyOutputList, fiddleTest, guiServiceList, tenantID);
            
            //
            // dependentMap
            //
            
            Map<String, Object> dependentMap = new LinkedHashMap<String, Object>();
            if (fiddleTest)
              {
                dependentMap.put("name", guiManagedObject.getGUIManagedObjectDisplay());
                dependentMap.put("size", new Integer(500));
                dependentMap.put("children", JSONUtilities.encodeArray(nextDependencyOutputList));
              }
            else
              {
                dependentMap.put("id", guiManagedObject.getGUIManagedObjectID());
                dependentMap.put("name", guiManagedObject.getGUIManagedObjectName());
                dependentMap.put("display", guiManagedObject.getGUIManagedObjectDisplay());
                dependentMap.put("active", guiManagedObject.getActive());
                dependentMap.put("objectType", dependency);
                dependentMap.put("dependencies", JSONUtilities.encodeArray(nextDependencyOutputList));
                if (guiManagedObject instanceof Offer) dependentMap.put("cancellable", ((Offer) guiManagedObject).getCancellable()); // special - to stop editing the objects used in a cancellable offer
              }
            
            //
            // add
            //
            
            dependencyListOutput.add(JSONUtilities.encodeObject(dependentMap));
          }
      }
  }
  
  /***********************************
   * 
   * getService
   * 
   ***********************************/
  
  private static Object getService(List<GUIService> guiServiceList, final Class serviceClass)
  {
    Object result = null;
    for (GUIService guiService : guiServiceList)
      {
        if (serviceClass == guiService.getClass())
          {
            result = guiService;
            break;
          }
      }
    if (result == null) throw new ServerRuntimeException(serviceClass.getName() + " not found in guiServiceList");
    return result;
  }
}
