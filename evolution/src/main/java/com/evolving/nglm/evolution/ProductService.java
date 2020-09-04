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
          @Override public void guiManagedObjectDeactivated(String guiManagedObjectID) { productListener.productDeactivated(guiManagedObjectID); }
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
  public GUIManagedObject getStoredProduct(String productID) { return getStoredGUIManagedObject(productID); }
  public GUIManagedObject getStoredProduct(String productID, boolean includeArchived) { return getStoredGUIManagedObject(productID, includeArchived); }
  public Collection<GUIManagedObject> getStoredProducts() { return getStoredGUIManagedObjects(); }
  public Collection<GUIManagedObject> getStoredProducts(boolean includeArchived) { return getStoredGUIManagedObjects(includeArchived); }
  public boolean isActiveProductThroughInterval(GUIManagedObject productUnchecked, Date startDate, Date endDate) { return isActiveThroughInterval(productUnchecked, startDate, endDate); }
  public boolean isActiveProduct(GUIManagedObject productUnchecked, Date date) { return isActiveGUIManagedObject(productUnchecked, date); }
  public Product getActiveProduct(String productID, Date date) { return (Product) getActiveGUIManagedObject(productID, date); }
  public Collection<Product> getActiveProducts(Date date) { return (Collection<Product>) getActiveGUIManagedObjects(date); }

  /*****************************************
  *
  *  putProduct
  *
  *****************************************/

  public void putProduct(GUIManagedObject product, SupplierService supplierService, ProductTypeService productTypeService, DeliverableService deliverableService, boolean newObject, String userID) throws GUIManagerException
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

    putGUIManagedObject(product, now, newObject, userID);
  }

  /*****************************************
  *
  *  putProduct
  *
  *****************************************/

  public void putProduct(IncompleteObject product, SupplierService supplierService, ProductTypeService productTypeService, DeliverableService deliverableService, boolean newObject, String userID)
  {
    try
      {
        putProduct((GUIManagedObject) product, supplierService, productTypeService, deliverableService, newObject, userID);
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

  public void removeProduct(String productID, String userID) { removeGUIManagedObject(productID, SystemTime.getCurrentTime(), userID); }

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
