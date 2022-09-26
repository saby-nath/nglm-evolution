package com.evolving.nglm.evolution;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.apache.kafka.connect.data.Field;
import org.apache.kafka.connect.data.Schema;
import org.apache.kafka.connect.data.SchemaAndValue;
import org.apache.kafka.connect.data.SchemaBuilder;
import org.apache.kafka.connect.data.Struct;
import org.json.simple.JSONObject;

import com.evolving.nglm.core.JSONUtilities;
import com.evolving.nglm.core.SchemaUtilities;
import com.evolving.nglm.evolution.GUIManagedObject.GUIManagedObjectType;
import com.evolving.nglm.evolution.GUIManager.GUIManagerException;

public abstract class Voucher extends GUIManagedObject {

  private static Schema commonSchema = null;
  static {
    SchemaBuilder schemaBuilder = SchemaBuilder.struct();
    schemaBuilder.name("voucher");
    schemaBuilder.version(SchemaUtilities.packSchemaVersion(GUIManagedObject.commonSchema().version(),3));
    for (Field field : GUIManagedObject.commonSchema().fields()) schemaBuilder.field(field.name(), field.schema());
    schemaBuilder.field("supplierID", Schema.STRING_SCHEMA);
    schemaBuilder.field("voucherTypeId", Schema.STRING_SCHEMA);
    schemaBuilder.field("unitaryCost", Schema.OPTIONAL_INT32_SCHEMA);
    schemaBuilder.field("recommendedPrice", Schema.OPTIONAL_INT32_SCHEMA);
    schemaBuilder.field("simpleOffer", Schema.OPTIONAL_BOOLEAN_SCHEMA);
    schemaBuilder.field("workflowID", Schema.OPTIONAL_STRING_SCHEMA);
    schemaBuilder.field("stockAlertThreshold", Schema.OPTIONAL_INT32_SCHEMA);
    schemaBuilder.field("stockAlert", Schema.BOOLEAN_SCHEMA); 
    commonSchema = schemaBuilder.build();
  }

  public static Schema commonSchema() { return commonSchema; }


  private String supplierID;
  private String voucherTypeId;
  private Integer unitaryCost;
  private Integer recommendedPrice;
  private boolean simpleOffer;
  private String workflowID;
  private int stockAlertThreshold;
  private boolean stockAlert;

  public String getVoucherID() { return getGUIManagedObjectID(); }
  public String getVoucherName() { return getGUIManagedObjectName(); }
  public String getVoucherDisplay() { return getGUIManagedObjectDisplay(); }
  public String getSupplierID() { return supplierID; }
  public String getVoucherTypeId() { return voucherTypeId; }
  public Integer getUnitaryCost() { return unitaryCost; }
  public Integer getRecommendedPrice() { return recommendedPrice; }
  public boolean getSimpleOffer() { return simpleOffer; }
  public String getWorkflowID() { return workflowID; }
  public int getStockAlertThreshold() { return stockAlertThreshold; }
  public boolean getStockAlert() { return stockAlert; } 


  public static Object packCommon(Struct struct, Voucher voucher)
  {
    GUIManagedObject.packCommon(struct, voucher);
    struct.put("supplierID", voucher.getSupplierID());
    struct.put("voucherTypeId", voucher.getVoucherTypeId());
    struct.put("unitaryCost", voucher.getUnitaryCost());
    struct.put("recommendedPrice", voucher.getRecommendedPrice());
    struct.put("simpleOffer", voucher.getSimpleOffer());
    struct.put("workflowID", voucher.getWorkflowID());
    struct.put("stockAlertThreshold", voucher.getStockAlertThreshold());
    struct.put("stockAlert", voucher.getStockAlert());
    return struct;
  }

  public Voucher(SchemaAndValue schemaAndValue)
  {
    super(schemaAndValue);
    Object value = schemaAndValue.value();
    Schema schema = schemaAndValue.schema();
    Integer schemaVersion = (schema != null) ? SchemaUtilities.unpackSchemaVersion1(schema.version()) : null;
    Struct valueStruct = (Struct) value;
    this.supplierID = valueStruct.getString("supplierID");
    this.voucherTypeId = valueStruct.getString("voucherTypeId");
    this.unitaryCost = valueStruct.getInt32("unitaryCost");
    this.recommendedPrice = valueStruct.getInt32("recommendedPrice");
    this.simpleOffer = (schemaVersion >= 2) ? valueStruct.getBoolean("simpleOffer") : false;
    this.workflowID = (schema.field("workflowID") != null) ? valueStruct.getString("workflowID") : null;
    this.stockAlertThreshold = (schema.field("stockAlertThreshold") != null) ? valueStruct.getInt32("stockAlertThreshold") : 0;
    this.stockAlert = (schema.field("stockAlert") != null) ? valueStruct.getBoolean("stockAlert") : false;
  }

  public Voucher(JSONObject jsonRoot, GUIManagedObjectType objectType, long epoch, GUIManagedObject existingVoucherUnchecked, int tenantID) throws GUIManagerException
  {

    super(jsonRoot, objectType, (existingVoucherUnchecked != null) ? existingVoucherUnchecked.getEpoch() : epoch, tenantID);

    Voucher existingVoucher = (existingVoucherUnchecked != null && existingVoucherUnchecked instanceof Voucher) ? (Voucher) existingVoucherUnchecked : null;

    this.supplierID = JSONUtilities.decodeString(jsonRoot, "supplierID", true);
    this.voucherTypeId = JSONUtilities.decodeString(jsonRoot, "voucherTypeId", true);
    this.unitaryCost = JSONUtilities.decodeInteger(jsonRoot, "unitaryCost", false);
    this.recommendedPrice = JSONUtilities.decodeInteger(jsonRoot, "recommendedPrice", false);
    this.simpleOffer = JSONUtilities.decodeBoolean(jsonRoot, "simpleOffer", Boolean.FALSE);
    this.workflowID = JSONUtilities.decodeString(jsonRoot, "workflowId", false);
    this.stockAlertThreshold = JSONUtilities.decodeInteger(jsonRoot, "stockAlertThreshold", 0);
    this.stockAlert = JSONUtilities.decodeBoolean(jsonRoot, "stockAlert", Boolean.FALSE);

    if (epochChanged(existingVoucher))
      {
        this.setEpoch(epoch);
      }
  }

  private boolean epochChanged(Voucher existingVoucher) {
    if (existingVoucher != null && existingVoucher.getAccepted()) {
      boolean epochChanged = false;
      epochChanged = epochChanged || ! Objects.equals(getGUIManagedObjectID(), existingVoucher.getGUIManagedObjectID());
      epochChanged = epochChanged || ! Objects.equals(supplierID, existingVoucher.getSupplierID());
      epochChanged = epochChanged || ! Objects.equals(voucherTypeId, existingVoucher.getVoucherTypeId());
      epochChanged = epochChanged || ! Objects.equals(unitaryCost, existingVoucher.getUnitaryCost());
      epochChanged = epochChanged || ! Objects.equals(recommendedPrice, existingVoucher.getRecommendedPrice());
      epochChanged = epochChanged || ! Objects.equals(simpleOffer, existingVoucher.getSimpleOffer());
      epochChanged = epochChanged || ! Objects.equals(workflowID, existingVoucher.getWorkflowID());
      epochChanged = epochChanged || !Objects.equals(stockAlertThreshold, existingVoucher.getStockAlertThreshold());
      epochChanged = epochChanged || !Objects.equals(stockAlert, existingVoucher.getStockAlert());
      return epochChanged;
    }else{
      return true;
    }
  }

  // trying to enforce commonValidate usage in subclasses, no perfect way Im aware of...
  abstract void validate(VoucherTypeService voucherService, UploadedFileService uploadedFileService, Date now) throws GUIManagerException;
  public void commonValidate(VoucherTypeService voucherTypeService, Date date) throws GUIManagerException {
    VoucherType voucherType = voucherTypeService.getActiveVoucherType(getVoucherTypeId(), date);
    if(voucherType==null || voucherType.getCodeType().equals(VoucherType.CodeType.Unknown)) throw new GUIManagerException("unknown voucherType", getVoucherTypeId());
  }
  
  
  @Override
  public String toString() {
    return "supplierID:"+getSupplierID()+", voucherTypeId:"+getVoucherTypeId()+", unitaryCost:"+getUnitaryCost()+", recommendedPrice:"+getRecommendedPrice();
  }

}
