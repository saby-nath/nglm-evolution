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

import org.json.simple.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.evolving.nglm.core.JSONUtilities;
import com.evolving.nglm.core.ServerRuntimeException;
import com.evolving.nglm.evolution.GUIManagedObject.GUIDependency;
import com.evolving.nglm.evolution.GUIManagedObject.Provides;

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
  
  public static void createDependencyTreeMAP(Map<String, GUIDependencyModelTree> guiDependencyModelTreeMap, GUIDependencyModelTree guiDependencyModelTree, Set<String>  dependencies, String objectID, List<JSONObject> dependencyListOutput, boolean fiddleTest, List<GUIService> guiServiceList) throws IllegalAccessException, IllegalArgumentException, InvocationTargetException, NoSuchMethodException, SecurityException
  {
    log.info("RAJ K createDependencyTreeMAP for {} and ID {} will look into {} types", guiDependencyModelTree.getGuiManagedObjectType(), objectID, dependencies);
    for (String dependency : dependencies)
      {
        //
        // stored and container
        //
        
        Collection<GUIManagedObject> containerObjectList = new ArrayList<GUIManagedObject>();
        
        //
        // get storedObjectList
        //
        
        Class serviceClass = guiDependencyModelTreeMap.get(dependency).getServiceClass();
        Object serviceObject  = getService(guiServiceList, serviceClass);
        Method retriver = serviceClass.getDeclaredMethod("getStoredGUIManagedObjects", null);
        retriver.setAccessible(true);
        Collection<GUIManagedObject> storedObjectList = (Collection<GUIManagedObject>) retriver.invoke(serviceObject, null);
        
        //
        //  containerObjectList
        //
        
        if (storedObjectList != null)
          {
            for (GUIManagedObject guiManagedObject : storedObjectList)
              {
                List<GUIDependency> guiDependencies = guiManagedObject.getGUIDependencies();
                if (guiDependencies != null && guiDependencies.contains(guiDependencyModelTree.getGuiManagedObjectType())) containerObjectList.add(guiManagedObject);
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
            
            if (netxGUIDependencyModelTree!= null) createDependencyTreeMAP(guiDependencyModelTreeMap, netxGUIDependencyModelTree, netxGUIDependencyModelTree.getDependencyList(), nextObjectID, nextDependencyOutputList, fiddleTest, guiServiceList);
            
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
                dependentMap.put("dependents", JSONUtilities.encodeArray(nextDependencyOutputList));
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
    log.info("RAJ K getService call for {}", serviceClass.getName());
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
    log.info("RAJ K found service is {}", result.getClass().getName());
    return result;
  }
}
