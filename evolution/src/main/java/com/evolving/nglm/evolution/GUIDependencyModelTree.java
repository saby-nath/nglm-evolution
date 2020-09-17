package com.evolving.nglm.evolution;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.evolving.nglm.evolution.GUIManagedObject.GUIDependencyDef;

/****************************************
*
*  GUIDependencyModelTree.java
*
*****************************************/

public class GUIDependencyModelTree
{
  //
  //  data
  //
  
  private String guiManagedObjectType;
  private Set<String> dependencyList = new HashSet<String>();
  private Class<? extends GUIService> serviceClass;
  
  //
  // accessors
  //
  
  public String getGuiManagedObjectType() { return guiManagedObjectType; }
  public Set<String> getDependencyList() { return dependencyList; }
  public Class<? extends GUIService> getServiceClass() { return serviceClass; }
  
  /*********************************************
   * 
   * constructor
   * 
   *********************************************/
  
  public GUIDependencyModelTree(Class guiDependencyDefClass, final Set<Class<?>> guiDependencyDefClassList)
  {
    GUIDependencyDef guiDependencyDef = (GUIDependencyDef) guiDependencyDefClass.getAnnotation(GUIDependencyDef.class);
    if (guiDependencyDef != null)
      {
        this.guiManagedObjectType = guiDependencyDef.objectType().toLowerCase();
        this.serviceClass = guiDependencyDef.serviceClass();
        this.dependencyList = prepareDependencyList(guiManagedObjectType, guiDependencyDefClassList);
        
      }
  }
  
  /*********************************************
   * 
   * prepareDependencyList
   * 
   *********************************************/
  
  private Set<String> prepareDependencyList(String guiManagedObjectType, final Set<Class<?>> guiDependencyDefClassList)
  {
	  List<String> dependencyList=new ArrayList<>();
    Set<String> result = new HashSet<String>();
    for (Class guiDependencyDefClass : guiDependencyDefClassList)
      { System.out.println(guiDependencyDefClass +" ====for obj type"+ guiManagedObjectType);
        GUIDependencyDef guiDependencyDef = (GUIDependencyDef) guiDependencyDefClass.getAnnotation(GUIDependencyDef.class);
        if (guiDependencyDef.dependencies().length > 0)
          {
        	for(String dep:guiDependencyDef.dependencies()) {
        		dependencyList.add(dep.toLowerCase());
        	}
            Set<String> thisDependencies = new HashSet<>(dependencyList);
            if (thisDependencies.contains(guiManagedObjectType)) result.add(guiDependencyDef.objectType());
          }
      }
    return result;
  }
}
