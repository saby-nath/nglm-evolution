package com.evolving.nglm.evolution;

import com.evolving.nglm.core.ServerRuntimeException;
import com.evolving.nglm.evolution.GUIManagedObject.GUIDependencyDef;

@GUIDependencyDef(objectType = "bulkCampaign", serviceClass = JourneyService.class, dependencies = {"point" , "target"})
public class BulkCampaign extends GUIManagedObject
{

  protected BulkCampaign(String guiManagedObjectID, int tenantID)
  {
    super(guiManagedObjectID, tenantID);
    throw new ServerRuntimeException("Bulk Campaign is marker only - not allowed to create an object");
  }

}
