/*****************************************************************************
*
*  Deliverable.java
*
*****************************************************************************/

package com.evolving.nglm.evolution;

import java.util.Objects;

import org.apache.kafka.connect.data.Field;
import org.apache.kafka.connect.data.Schema;
import org.apache.kafka.connect.data.SchemaAndValue;
import org.apache.kafka.connect.data.SchemaBuilder;
import org.apache.kafka.connect.data.Struct;
import org.json.simple.JSONObject;

import com.evolving.nglm.core.ConnectSerde;
import com.evolving.nglm.core.JSONUtilities;
import com.evolving.nglm.core.SchemaUtilities;
import com.evolving.nglm.evolution.GUIManager.GUIManagerException;

public class Deliverable extends GUIManagedObject
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
    schemaBuilder.name("deliverable");
    schemaBuilder.version(SchemaUtilities.packSchemaVersion(commonSchema().version(),2));
    for (Field field : commonSchema().fields()) schemaBuilder.field(field.name(), field.schema());
    schemaBuilder.field("fulfillmentProviderID", Schema.STRING_SCHEMA);
    schemaBuilder.field("externalAccountID", Schema.STRING_SCHEMA);
    schemaBuilder.field("unitaryCost", Schema.INT32_SCHEMA);
    schemaBuilder.field("generatedFromAccount", Schema.BOOLEAN_SCHEMA);
    schema = schemaBuilder.build();
  };

  //
  //  serde
  //

  private static ConnectSerde<Deliverable> serde = new ConnectSerde<Deliverable>(schema, false, Deliverable.class, Deliverable::pack, Deliverable::unpack);

  //
  //  accessor
  //

  public static Schema schema() { return schema; }
  public static ConnectSerde<Deliverable> serde() { return serde; }

  /*****************************************
  *
  *  data
  *
  *****************************************/

  private String fulfillmentProviderID;
  private String externalAccountID;
  private int unitaryCost;
  private boolean generatedFromAccount;

  /*****************************************
  *
  *  accessors
  *
  *****************************************/

  public String getDeliverableID() { return getGUIManagedObjectID(); }
  public String getDeliverableName() { return getGUIManagedObjectName(); }
  public String getFulfillmentProviderID() { return fulfillmentProviderID; }
  public String getExternalAccountID() { return externalAccountID; }
  public int getUnitaryCost() { return unitaryCost; }
  public boolean getGeneratedFromAccount() { return generatedFromAccount; }
  
  /*****************************************
  *
  *  constructor -- unpack
  *
  *****************************************/

  public Deliverable(SchemaAndValue schemaAndValue, String fulfillmentProviderID, String externalAccountID, int unitaryCost, boolean generatedFromAccount)
  {
    super(schemaAndValue);
    this.fulfillmentProviderID = fulfillmentProviderID;
    this.externalAccountID = externalAccountID;
    this.unitaryCost = unitaryCost;
    this.generatedFromAccount = generatedFromAccount;
  }
  
  /*****************************************
  *
  *  pack
  *
  *****************************************/

  public static Object pack(Object value)
  {
    Deliverable deliverable = (Deliverable) value;
    Struct struct = new Struct(schema);
    packCommon(struct, deliverable);
    struct.put("fulfillmentProviderID", deliverable.getFulfillmentProviderID());
    struct.put("externalAccountID", deliverable.getExternalAccountID());
    struct.put("unitaryCost", deliverable.getUnitaryCost());
    struct.put("generatedFromAccount", deliverable.getGeneratedFromAccount());
    return struct;
  }

  /*****************************************
  *
  *  unpack
  *
  *****************************************/

  public static Deliverable unpack(SchemaAndValue schemaAndValue)
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
    String fulfillmentProviderID = valueStruct.getString("fulfillmentProviderID");
    String externalAccountID = (schemaVersion >= 2) ? valueStruct.getString("externalAccountID") : fulfillmentProviderID;
    int unitaryCost = valueStruct.getInt32("unitaryCost");
    boolean generatedFromAccount = valueStruct.getBoolean("generatedFromAccount");
    
    //
    //  return
    //

    return new Deliverable(schemaAndValue, fulfillmentProviderID, externalAccountID, unitaryCost, generatedFromAccount);
  }

  /*****************************************
  *
  *  constructor
  *
  *****************************************/

  public Deliverable(JSONObject jsonRoot, long epoch, GUIManagedObject existingDeliverableUnchecked) throws GUIManagerException
  {
    /*****************************************
    *
    *  super
    *
    *****************************************/

    super(jsonRoot, (existingDeliverableUnchecked != null) ? existingDeliverableUnchecked.getEpoch() : epoch);

    /*****************************************
    *
    *  existingDeliverable
    *
    *****************************************/

    Deliverable existingDeliverable = (existingDeliverableUnchecked != null && existingDeliverableUnchecked instanceof Deliverable) ? (Deliverable) existingDeliverableUnchecked : null;
    
    /*****************************************
    *
    *  attributes
    *
    *****************************************/

    this.fulfillmentProviderID = JSONUtilities.decodeString(jsonRoot, "fulfillmentProviderID", true);
    this.externalAccountID = JSONUtilities.decodeString(jsonRoot, "externalAccountID", true);
    this.unitaryCost = JSONUtilities.decodeInteger(jsonRoot, "unitaryCost", true);
    this.generatedFromAccount = JSONUtilities.decodeBoolean(jsonRoot, "generatedFromAccount", Boolean.FALSE);

    /*****************************************
    *
    *  validate
    *
    *****************************************/

    //
    //  validate provider
    //

    //  TBD

    /*****************************************
    *
    *  epoch
    *
    *****************************************/

    if (epochChanged(existingDeliverable))
      {
        this.setEpoch(epoch);
      }
  }

  /*****************************************
  *
  *  epochChanged
  *
  *****************************************/

  private boolean epochChanged(Deliverable existingDeliverable)
  {
    if (existingDeliverable != null && existingDeliverable.getAccepted())
      {
        boolean epochChanged = false;
        epochChanged = epochChanged || ! Objects.equals(getGUIManagedObjectID(), existingDeliverable.getGUIManagedObjectID());
        epochChanged = epochChanged || ! Objects.equals(fulfillmentProviderID, existingDeliverable.getFulfillmentProviderID());
        epochChanged = epochChanged || ! Objects.equals(externalAccountID, existingDeliverable.getExternalAccountID());
        epochChanged = epochChanged || ! (unitaryCost == existingDeliverable.getUnitaryCost());
        epochChanged = epochChanged || ! (generatedFromAccount == existingDeliverable.getGeneratedFromAccount());
        return epochChanged;
      }
    else
      {
        return true;
      }
  }
}
