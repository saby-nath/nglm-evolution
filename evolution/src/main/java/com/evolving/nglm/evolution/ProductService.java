/****************************************************************************
*
*  ProductService.java
*
****************************************************************************/

package com.evolving.nglm.evolution;

import java.util.Collection;
import java.util.Date;

import org.json.simple.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.evolving.nglm.core.SystemTime;
import com.evolving.nglm.evolution.GUIManagedObject.IncompleteObject;
import com.evolving.nglm.evolution.GUIManager.GUIManagerException;

public class ProductService extends GUIService
{
  /*****************************************
  *
  *  configuration
  *
  *****************************************/

  //
  //  logger
  //

  private static final Logger log = LoggerFactory.getLogger(ProductService.class);

  /*****************************************
  *
  *  data
  *
  *****************************************/

  private ProductListener productListener = null;

  /*****************************************
  *
  *  constructor
  *
  *****************************************/

  public ProductService(String bootstrapServers, String groupID, String productTopic, boolean masterService, ProductListener productListener, boolean notifyOnSignificantChange)
  {
    super(bootstrapServers, "ProductService", groupID, productTopic, masterService, getSuperListener(productListener), "putProduct", "removeProduct", notifyOnSignificantChange);
  }

  //
  //  constructor
  //

  public ProductService(String bootstrapServers, String groupID, String productTopic, boolean masterService, ProductListener productListener)
  {
    this(bootstrapServers, groupID, productTopic, masterService, productListener, true);
  }

  //
  //  constructor
  //

  public ProductService(String bootstrapServers, String groupID, String productTopic, boolean masterService)
  {
    this(bootstrapServers, groupID, productTopic, masterService, (ProductListener) null, true);
  }

  //
  //  getSuperListener
  //

  private static GUIManagedObjectListener getSuperListener(ProductListener productListener)
  {
    GUIManagedObjectListener superListener = null;
    if (productListener != null)
      {
        superListener = new GUIManagedObjectListener()
        {
          @Override public void guiManagedObjectActivated(GUIManagedObject guiManagedObject) { productListener.productActivated((Product) guiManagedObject); }
          @Override public void guiManagedObjectDeactivated(String guiManagedObjectID, int tenantID) { productListener.productDeactivated(guiManagedObjectID); }
        };
      }
    return superListener;
  }

  /*****************************************
  *
  *  getSummaryJSONRepresentation
  *
  *****************************************/

  @Override protected JSONObject getSummaryJSONRepresentation(GUIManagedObject guiManagedObject)
  {
    JSONObject result = super.getSummaryJSONRepresentation(guiManagedObject);
    result.put("supplierID", guiManagedObject.getJSONRepresentation().get("supplierID"));
    result.put("imageURL", guiManagedObject.getJSONRepresentation().get("imageURL"));
    return result;
  }
  
  /*****************************************
  *
  *  getProducts
  *
  *****************************************/

  public String generateProductID() { return generateGUIManagedObjectID(); }
  public GUIManagedObject getStoredProduct(String productID, int tenantID) { return getStoredGUIManagedObject(productID, tenantID); }
  public GUIManagedObject getStoredProduct(String productID, boolean includeArchived, int tenantID) { return getStoredGUIManagedObject(productID, includeArchived, tenantID); }
  public Collection<GUIManagedObject> getStoredProducts(int tenantID) { return getStoredGUIManagedObjects(tenantID); }
  public Collection<GUIManagedObject> getStoredProducts(boolean includeArchived, int tenantID) { return getStoredGUIManagedObjects(includeArchived, tenantID); }
  public boolean isActiveProductThroughInterval(GUIManagedObject productUnchecked, Date startDate, Date endDate) { return isActiveThroughInterval(productUnchecked, startDate, endDate); }
  public boolean isActiveProduct(GUIManagedObject productUnchecked, Date date) { return isActiveGUIManagedObject(productUnchecked, date); }
  public Product getActiveProduct(String productID, Date date, int tenantID) { return (Product) getActiveGUIManagedObject(productID, date, tenantID); }
  public Collection<Product> getActiveProducts(Date date, int tenantID) { return (Collection<Product>) getActiveGUIManagedObjects(date, tenantID); }

  /*****************************************
  *
  *  putProduct
  *
  *****************************************/

  public void putProduct(GUIManagedObject product, SupplierService supplierService, ProductTypeService productTypeService, DeliverableService deliverableService, boolean newObject, String userID, int tenantID) throws GUIManagerException
  {
    //
    //  now
    //

    Date now = SystemTime.getCurrentTime();

    //
    //  validate
    //

    if (product instanceof Product)
      {
        ((Product) product).validate(supplierService, productTypeService, deliverableService, now);
      }

    //
    //  put
    //

    putGUIManagedObject(product, now, newObject, userID, tenantID);
  }

  /*****************************************
  *
  *  putProduct
  *
  *****************************************/

  public void putProduct(IncompleteObject product, SupplierService supplierService, ProductTypeService productTypeService, DeliverableService deliverableService, boolean newObject, String userID, int tenantID)
  {
    try
      {
        putProduct((GUIManagedObject) product, supplierService, productTypeService, deliverableService, newObject, userID, tenantID);
      }
    catch (GUIManagerException e)
      {
        throw new RuntimeException(e);
      }
  }
  
  /*****************************************
  *
  *  removeProduct
  *
  *****************************************/

  public void removeProduct(String productID, String userID, int tenantID) { removeGUIManagedObject(productID, SystemTime.getCurrentTime(), userID, tenantID); }

  /*****************************************
  *
  *  interface ProductListener
  *
  *****************************************/

  public interface ProductListener
  {
    public void productActivated(Product product);
    public void productDeactivated(String guiManagedObjectID);
  }

  /*****************************************
  *
  *  example main
  *
  *****************************************/

  public static void main(String[] args)
  {
    //
    //  productListener
    //

    ProductListener productListener = new ProductListener()
    {
      @Override public void productActivated(Product product) { System.out.println("product activated: " + product.getProductID()); }
      @Override public void productDeactivated(String guiManagedObjectID) { System.out.println("product deactivated: " + guiManagedObjectID); }
    };

    //
    //  productService
    //

    ProductService productService = new ProductService(Deployment.getBrokerServers(), "example-productservice-001", Deployment.getProductTopic(), false, productListener);
    productService.start();

    //
    //  sleep forever
    //

    while (true)
      {
        try
          {
            Thread.sleep(Long.MAX_VALUE);
          }
        catch (InterruptedException e)
          {
            //
            //  ignore
            //
          }
      }
  }
}
