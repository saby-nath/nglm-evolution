/*****************************************************************************
 *
 *  PriceAlgo.java
 *
 *****************************************************************************/

package com.evolving.nglm.evolution.offeroptimizer;

import java.util.Map;

import org.apache.log4j.Logger;

import com.evolving.nglm.evolution.CatalogCharacteristicService;
import com.evolving.nglm.evolution.Offer;
import com.evolving.nglm.evolution.OfferOptimizationAlgorithm;
import com.evolving.nglm.evolution.OfferOptimizationAlgorithm.OfferOptimizationAlgorithmParameter;
import com.evolving.nglm.evolution.OfferSalesChannelsAndPrice;
import com.evolving.nglm.evolution.ProductService;
import com.evolving.nglm.evolution.ProductTypeService;
import com.evolving.nglm.evolution.SubscriberEvaluationRequest;

public class PriceAlgo implements IOfferOptimizerAlgorithm {

  private static Logger logger = Logger.getLogger(PriceAlgo.class);

  @Override
  public ProposedOfferDetails getOfferPropensityScore(
      Map<OfferOptimizationAlgorithmParameter,String> algoParameters,
      Offer o,
      String salesChannelId,
      double offerCurrentPropensity,
      long offerPrice, SubscriberEvaluationRequest subscriberEvaluationRequest,
      OfferOptimizationAlgorithm algoDefinition,
      ProductService productService, 
      ProductTypeService productTypeService,
      CatalogCharacteristicService catalogCharacteristicService,
      DNBOMatrixAlgorithmParameters dnboMatrixAlgorithmParameterser)
  {
    if (logger.isTraceEnabled())
      {
        logger.trace("PriceAlgo.getOfferPropensityScore Entered "
          + algoParameters + " " + o.getOfferID() + " " + salesChannelId + " "  + offerCurrentPropensity + " "
          + offerPrice + " " + subscriberEvaluationRequest.getSubscriberProfile() + " " + algoDefinition);
      }
    
    OfferOptimizationAlgorithmParameter reversedParameter = new OfferOptimizationAlgorithmParameter("reversed");
    String isReversedString = algoParameters.get(reversedParameter);
    Boolean isReversed = false; // default value
    if (isReversedString != null)
    {
      isReversed = Boolean.parseBoolean(isReversedString);
    }

    double score = 0d; // default value if offer has no price : offer is free
    
    for (OfferSalesChannelsAndPrice oscap : o.getOfferSalesChannelsAndPrices())
      {
        if (oscap.getSalesChannelIDs() != null && oscap.getSalesChannelIDs().contains(salesChannelId))
          {
            score = (double) oscap.getPrice().getAmount(); // simple cast from long to double is enough
            if (isReversed)
              {
                score = -score;
              }
            break;
          }
      }
    if (logger.isTraceEnabled()) logger.trace("PriceAlgo.getOfferPropensityScore priced offer at {}" + score);
    ProposedOfferDetails pod = new ProposedOfferDetails(o.getOfferID(), salesChannelId, score);
    return pod;
  }
}
