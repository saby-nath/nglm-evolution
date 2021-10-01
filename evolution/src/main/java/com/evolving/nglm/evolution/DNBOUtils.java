/*****************************************************************************
*
*  DNBOUtils.java
*
*****************************************************************************/

package com.evolving.nglm.evolution;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.json.simple.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.evolving.nglm.core.ReferenceDataReader;
import com.evolving.nglm.evolution.DeliveryRequest.Module;
import com.evolving.nglm.evolution.EvolutionEngine.EvolutionEventContext;
import com.evolving.nglm.evolution.Expression.ConstantExpression;
import com.evolving.nglm.evolution.GUIManager.GUIManagerException;
import com.evolving.nglm.evolution.Journey.ContextUpdate;
import com.evolving.nglm.evolution.PurchaseFulfillmentManager.PurchaseFulfillmentRequest;
import com.evolving.nglm.evolution.Token.TokenStatus;
import com.evolving.nglm.evolution.offeroptimizer.DNBOMatrixAlgorithmParameters;
import com.evolving.nglm.evolution.offeroptimizer.GetOfferException;
import com.evolving.nglm.evolution.offeroptimizer.ProposedOfferDetails;

public class DNBOUtils
{
  /*****************************************
  *
  *  configuration
  *
  *****************************************/

  //
  //  logger
  //

  private static final Logger log = LoggerFactory.getLogger(DNBOUtils.class);
  
  public static final int MAX_PRESENTED_OFFERS = 5;
  private static final int HOW_MANY_TIMES_TO_TRY_TO_GENERATE_A_TOKEN_CODE = 100;

  /*****************************************
  *
  *  generateTokenChange 
  *
  *****************************************/

  private static TokenChange generateTokenChange(EvolutionEventContext evolutionEventContext, SubscriberEvaluationRequest subscriberEvaluationRequest, String tokenCode, String action, String str)
  {
   /* String origin = "Journey";
    switch (action)
    {
      case TokenChange.CREATE:
        origin += "Token";
        break;
      case TokenChange.ALLOCATE:
        origin += "NBO";
        break;
      case TokenChange.REDEEM:
        origin += "BestOffer";
        break;
      default:
        break;
        
    }*/
    String origin = null;
    if (subscriberEvaluationRequest.getJourneyNode() != null)
      {
        origin = subscriberEvaluationRequest.getJourneyNode().getNodeName();
      }
    String subscriberID = evolutionEventContext.getSubscriberState().getSubscriberID();
    int tenantID = evolutionEventContext.getSubscriberState().getSubscriberProfile().getTenantID();
    Date date = evolutionEventContext.now();
    String featureID = subscriberEvaluationRequest.getJourneyState().getJourneyID();
    featureID = ActionManager.extractWorkflowFeatureID(evolutionEventContext, subscriberEvaluationRequest, featureID);
    return new TokenChange(evolutionEventContext.getSubscriberState().getSubscriberProfile(), date, evolutionEventContext.getEventID(), tokenCode, action, str, origin, Module.Journey_Manager, featureID, tenantID);
  }
  
  /*****************************************
  *
  *  class ActionManagerDNBO (superclass)
  *
  *****************************************/

  private static class ActionManagerDNBO extends com.evolving.nglm.evolution.ActionManager
  {
    /*****************************************
    *
    *  constructor
    *
    *****************************************/

    public ActionManagerDNBO(JSONObject configuration) throws GUIManagerException
    {
      super(configuration);
    }

    /*****************************************
    *
    *  handleToken
    *
    *****************************************/
    // returns TokenChange if error, otherwise 5 elements
    protected Object[] handleToken(EvolutionEventContext evolutionEventContext, SubscriberEvaluationRequest subscriberEvaluationRequest, String action)
    {
      log.debug("ActionManagerDNBO.handleToken() method call");
    	
      /*****************************************
      *
      *  parameters
      *
      *****************************************/
      String strategyID = (String) CriterionFieldRetriever.getJourneyNodeParameter(subscriberEvaluationRequest, "node.parameter.strategy");
      String tokenTypeID = (String) CriterionFieldRetriever.getJourneyNodeParameter(subscriberEvaluationRequest, "node.parameter.tokentype"); 
      String supplierID = (String) CriterionFieldRetriever.getJourneyNodeParameter(subscriberEvaluationRequest, "node.parameter.supplier"); 
      
      /*****************************************
      *
      *  scoring strategy
      *
      *****************************************/
      PresentationStrategy presentationStrategy = evolutionEventContext.getPresentationStrategyService().getActivePresentationStrategy(strategyID, evolutionEventContext.now());
      if (presentationStrategy == null)
        {
          String str = RESTAPIGenericReturnCodes.INVALID_STRATEGY.getGenericResponseCode()+"";//"invalid presentation strategy " + strategyID;
          log.error(str);
          return new Object[] {Collections.<Action>singletonList(generateTokenChange(evolutionEventContext, subscriberEvaluationRequest, "", action, str))};
        }

      log.debug("ActionManagerDNBO.handleToken() strategy valid");

      /*****************************************
      *
      *  token type
      *
      *****************************************/
      TokenType tokenType = evolutionEventContext.getTokenTypeService().getActiveTokenType(tokenTypeID, evolutionEventContext.now());
      if (tokenType == null)
        {
          String str =  RESTAPIGenericReturnCodes.INVALID_TOKEN_TYPE.getGenericResponseCode()+"";//"unknown token type " + tokenTypeID; 
          log.error(str);
          return new Object[] {Collections.<Action>singletonList(generateTokenChange(evolutionEventContext, subscriberEvaluationRequest, "", action, str))};
        }

      log.debug("ActionManagerDNBO.handleToken() tokenType valid");
      
      /*****************************************
      *
      * supplier
      *
      *****************************************/
      Supplier supplier = null;
      if (supplierID != null) {
        supplier = evolutionEventContext.getSupplierService().getActiveSupplier(supplierID, evolutionEventContext.now());
      }
      
      /*****************************************
      *
      *  action -- generate new token code (different from others already associated with this subscriber)
      *
      *****************************************/
      
      DNBOToken token = TokenUtils.generateTokenCode(evolutionEventContext.getSubscriberState().getSubscriberProfile(), tokenType);
      if (token == null)
        {
          String str = RESTAPIGenericReturnCodes.CANNOT_GENERATE_TOKEN_CODE.getGenericResponseCode()+"";             //"unable to generate a new token code";
          if (log.isTraceEnabled()) log.trace(str);
          return new Object[] {Collections.<Action>singletonList(generateTokenChange(evolutionEventContext, subscriberEvaluationRequest, "", action, str))};
        }
      token.setModuleID(Module.Journey_Manager.getExternalRepresentation()); // featureID is set by evolution engine (to journeyID)
      token.setPresentationStrategyID(presentationStrategy.getPresentationStrategyID());
      // TODO : which sales channel to use ?
      token.setPresentedOffersSalesChannel(presentationStrategy.getSalesChannelIDs().iterator().next());
      token.setCreationDate(evolutionEventContext.now());

      /*****************************************
      *
      *  action -- token code
      *
      *****************************************/
      ContextUpdate contextUpdate = new ContextUpdate(ActionType.ActionManagerContextUpdate);
      contextUpdate.getParameters().put("action.token.code", token.getTokenCode());

      /*****************************************
      *
      *  Action list
      *
      *****************************************/
      List<Action> actionList = new ArrayList<>();
      actionList.add(token);
      actionList.add(contextUpdate);

      /*****************************************
      *
      *  return
      *
      *****************************************/
      return new Object[] {actionList, token, contextUpdate, presentationStrategy, tokenType, supplier};
    }
    
    /*****************************************
    *
    *  handleAllocate
    *
    *****************************************/
    // returns an array of Action if error, otherwise Collection<ProposedOfferDetails>
    protected Object handleAllocate(EvolutionEventContext evolutionEventContext, SubscriberEvaluationRequest subscriberEvaluationRequest, PresentationStrategy strategy, DNBOToken token, TokenType tokenType, ContextUpdate tokenContextUpdate, String action, Supplier supplier)
    {
      /*****************************************
      *
      *  maxNumberofPlays
      *
      *****************************************/
      int boundCount = token.getBoundCount();
      Integer maxNumberofPlaysInt = tokenType.getMaxNumberOfPlays();
      int maxNumberofPlays = (maxNumberofPlaysInt == null) ? Integer.MAX_VALUE : maxNumberofPlaysInt.intValue();
      if (boundCount >= maxNumberofPlays)
        {
          String str = "maxNumberofPlays has been reached " + maxNumberofPlays;
            log.error(str);
            return invalidPurchase(evolutionEventContext, subscriberEvaluationRequest, token, action, str).toArray(new Action[0]);
        }
      token.setBoundCount(boundCount+1);
      
      /*****************************************
      *
      *  services
      *
      *****************************************/
      OfferService offerService = evolutionEventContext.getOfferService();
      ProductService productService = evolutionEventContext.getProductService();
      ProductTypeService productTypeService = evolutionEventContext.getProductTypeService();
      VoucherService voucherService = evolutionEventContext.getVoucherService();
      VoucherTypeService voucherTypeService = evolutionEventContext.getVoucherTypeService();
      CatalogCharacteristicService catalogCharacteristicService = evolutionEventContext.getCatalogCharacteristicService();
      DNBOMatrixService dnboMatrixService = evolutionEventContext.getDnboMatrixService();
      SegmentationDimensionService segmentationDimensionService = evolutionEventContext.getSegmentationDimensionService();
      ScoringStrategyService scoringStrategyService = evolutionEventContext.getScoringStrategyService();
      SalesChannelService salesChannelService = evolutionEventContext.getSalesChannelService();
      SupplierService supplierService = evolutionEventContext.getSupplierService();
      ReferenceDataReader<String, SubscriberGroupEpoch> subscriberGroupEpochReader = evolutionEventContext.getSubscriberGroupEpochReader();

      Date now = evolutionEventContext.now();
      String subscriberID = evolutionEventContext.getSubscriberState().getSubscriberID();

      StringBuffer returnedLog = new StringBuffer();
      SubscriberProfile subscriberProfile = evolutionEventContext.getSubscriberState().getSubscriberProfile();
      DNBOMatrixAlgorithmParameters dnboMatrixAlgorithmParameters = new DNBOMatrixAlgorithmParameters(dnboMatrixService, 0);
      /*****************************************
      *
      *  Score offers for this subscriber
      *
      *****************************************/
      Collection<ProposedOfferDetails> presentedOffers;
      try
        {
          presentedOffers = TokenUtils.getOffers(now, token, subscriberEvaluationRequest, subscriberProfile, strategy, productService, productTypeService, voucherService, voucherTypeService, catalogCharacteristicService, scoringStrategyService, subscriberGroupEpochReader, segmentationDimensionService, dnboMatrixAlgorithmParameters, offerService, supplierService, returnedLog, subscriberID, supplier, subscriberEvaluationRequest.getTenantID());
        }
      catch (GetOfferException e)
        {
          String str = "unknown offer while scoring " + e.getLocalizedMessage();
          log.error(str);
          log.error(returnedLog.toString());
          return invalidPurchase(evolutionEventContext, subscriberEvaluationRequest, token, action, str).toArray(new Action[0]);
        }

      if (presentedOffers.isEmpty()) // is not expected, trace errors
        {
          log.error(returnedLog.toString());
        }
      else if (log.isTraceEnabled()) log.trace(returnedLog.toString());
      
      /*****************************************
      *
      *  transcode list of offers
      *
      *****************************************/
      
      List<String> presentedOfferIDs = new ArrayList<>();
      int index = 0;
      for (ProposedOfferDetails presentedOffer : presentedOffers)
        {
          String offerId = presentedOffer.getOfferId();
          presentedOfferIDs.add(offerId);
          Offer offer = offerService.getActiveOffer(offerId, now);
          if (offer == null)
            {
              String str = "invalid offer returned by scoring " + offerId;
              log.error(str);
              return invalidPurchase(evolutionEventContext, subscriberEvaluationRequest, token, action, str).toArray(new Action[0]);
            }
          tokenContextUpdate.getParameters().put("action.presented.offer." + (index+1), offer.getDisplay());
          if (++index == MAX_PRESENTED_OFFERS)
            break;
        }
      for (int j=index; j<MAX_PRESENTED_OFFERS; j++)
        {
          tokenContextUpdate.getParameters().put("action.presented.offer." + (j+1), "");
        }
      
      List<ProposedOfferDetails> proposedOfferDetails = presentedOffers.stream().collect(Collectors.toList());
      token.setPresentedOffers(proposedOfferDetails);
      // TODO token.setPresentedOffersSalesChannel(salesChannelID);
      token.setBoundDate(now);
      // add a new presentation in the token
      Presentation presentation = new Presentation(now, presentedOfferIDs);
      List<Presentation> currentPresentationHistory = token.getPresentationHistory();
      currentPresentationHistory.add(presentation);
      token.setPresentationHistory(currentPresentationHistory);
      
      /*****************************************
      *
      *  return
      *
      *****************************************/
      return presentedOffers;
    }
    
    /*****************************************
    *
    *  invalidPurchase
    *
    *****************************************/
    protected List<Action> invalidPurchase(EvolutionEventContext evolutionEventContext, SubscriberEvaluationRequest subscriberEvaluationRequest, Token token, String tokenChange, String str)
    {
      String offerID = "invalid-offerID";
      String salesChannelID = "invalid-salesChannelID";
      String deliveryRequestSource = subscriberEvaluationRequest.getJourneyState().getJourneyID();
      deliveryRequestSource = extractWorkflowFeatureID(evolutionEventContext, subscriberEvaluationRequest, deliveryRequestSource);
      
      int quantity = 1;
      // create an invalid purchase, so that the journey node uses "failed" connector
      PurchaseFulfillmentRequest request = new PurchaseFulfillmentRequest(evolutionEventContext, deliveryRequestSource, offerID, quantity, salesChannelID, "", "", subscriberEvaluationRequest.getTenantID());
      request.setModuleID(DeliveryRequest.Module.Journey_Manager.getExternalRepresentation());
      request.setFeatureID(deliveryRequestSource);
      List<Action> res = new ArrayList<>();
      res.add(request);
      if (token != null)
        {
          res.add(generateTokenChange(evolutionEventContext, subscriberEvaluationRequest, token.getTokenCode(), tokenChange, str));
        }
      return res;
    }
    
  }
  
  /*****************************************
  *
  *  class ActionManagerToken
  *
  *****************************************/

  public static class ActionManagerToken extends ActionManagerDNBO
  {
    /*****************************************
    *
    *  constructor
    *
    *****************************************/

    public ActionManagerToken(JSONObject configuration) throws GUIManagerException
    {
      super(configuration);
    }
        
    /*****************************************
    *
    *  execute (Token)
    *
    *****************************************/

    @Override public List<Action> executeOnEntry(EvolutionEventContext evolutionEventContext, SubscriberEvaluationRequest subscriberEvaluationRequest)
    {
      log.info("ActionManagerToken.executeOnEntry() method call");
      Object[] res = handleToken(evolutionEventContext, subscriberEvaluationRequest, TokenChange.CREATE);
      @SuppressWarnings("unchecked")
      List<Action> result = (List<Action>) res[0];

      /*****************************************
      *
      *  return
      *
      *****************************************/
      return result;
    }
    
    @Override public Map<String, String> getGUIDependencies(List<GUIService> guiServiceList, JourneyNode journeyNode, int tenantID)
    {
      Map<String, String> result = new HashMap<String, String>();
      Object tokentypeNodeParamObj = journeyNode.getNodeParameters().get("node.parameter.tokentype");
      if (tokentypeNodeParamObj instanceof ParameterExpression && ((ParameterExpression) tokentypeNodeParamObj).getExpression() instanceof ConstantExpression)
        {
          String nodeParam  = (String)  ((ParameterExpression) tokentypeNodeParamObj).getExpression().evaluateConstant();
          if (nodeParam != null) result.put("tokentype", nodeParam);
        }
      else if (tokentypeNodeParamObj instanceof String)
        {
          result.put("tokentype", (String) tokentypeNodeParamObj);
        }
      else
        {
          log.error("unsupported value/type expression {} - skipping", tokentypeNodeParamObj);
        }
      Object nodeParamObj = journeyNode.getNodeParameters().get("node.parameter.strategy");
      if (nodeParamObj instanceof ParameterExpression && ((ParameterExpression) nodeParamObj).getExpression() instanceof ConstantExpression)
        {
          String nodeParam  = (String)  ((ParameterExpression) nodeParamObj).getExpression().evaluateConstant();
          if (nodeParam != null) result.put("presentationstrategy", nodeParam);
        }
      else if (nodeParamObj instanceof String)
        {
          result.put("presentationstrategy", (String) nodeParamObj);
        }
      else
        {
          log.error("unsupported value/type expression {} - skipping for node.parameter.strategy", nodeParamObj);
        }
      return result;
    }
  }
  
  /*****************************************
  *
  *  class ActionManagerAllocate
  *
  *****************************************/

  public static class ActionManagerAllocate extends ActionManagerDNBO
  {
    /*****************************************
    *
    *  constructor
    *
    *****************************************/

    public ActionManagerAllocate(JSONObject configuration) throws GUIManagerException
    {
      super(configuration);
    }
        
    /*****************************************
    *
    *  execute (Allocate)
    *
    *****************************************/

    @Override public List<Action> executeOnEntry(EvolutionEventContext evolutionEventContext, SubscriberEvaluationRequest subscriberEvaluationRequest)
    {
      log.info("ActionManagerAllocate.executeOnEntry() method call");
      Object[] res = handleToken(evolutionEventContext, subscriberEvaluationRequest, TokenChange.ALLOCATE);
      @SuppressWarnings("unchecked")
      List<Action> result = (List<Action>) res[0];
      if (res.length == 1)
        {
          return result;
        }
      DNBOToken token = (DNBOToken) res[1];
      ContextUpdate tokenContextUpdate = (ContextUpdate) res[2];
      PresentationStrategy strategy = (PresentationStrategy) res[3];
      TokenType tokenType = (TokenType) res[4];
      Supplier supplier = (Supplier) res[5];
      
      /*****************************************
      *
      *  action -- score offers (ignore returned value except for error)
      *
      *****************************************/
      
      Object resAllocate = handleAllocate(evolutionEventContext, subscriberEvaluationRequest, strategy, token, tokenType, tokenContextUpdate, TokenChange.ALLOCATE, supplier);
      if (resAllocate instanceof Action[])
        {
          return Arrays.asList((Action[]) resAllocate);
        }
      token.setTokenStatus(TokenStatus.Bound);
      token.setAutoBound(true);
      token.setAutoRedeemed(false);

      /*****************************************
      *
      *  return
      *
      *****************************************/
      return result;
    }
    
    @Override public Map<String, String> getGUIDependencies(List<GUIService> guiServiceList, JourneyNode journeyNode, int tenantID)
    {
      Map<String, String> result = new HashMap<String, String>();
      Object tokentypeNodeParamObj = journeyNode.getNodeParameters().get("node.parameter.tokentype");
      if (tokentypeNodeParamObj instanceof ParameterExpression && ((ParameterExpression) tokentypeNodeParamObj).getExpression() instanceof ConstantExpression)
        {
          String nodeParam  = (String)  ((ParameterExpression) tokentypeNodeParamObj).getExpression().evaluateConstant();
          if (nodeParam != null) result.put("tokentype", nodeParam);
        }
      else if (tokentypeNodeParamObj instanceof String)
        {
          result.put("tokentype", (String) tokentypeNodeParamObj);
        }
      else
        {
          log.error("unsupported value/type expression {} - skipping", tokentypeNodeParamObj);
        }
      Object nodeParamObj = journeyNode.getNodeParameters().get("node.parameter.strategy");
      if (nodeParamObj instanceof ParameterExpression && ((ParameterExpression) nodeParamObj).getExpression() instanceof ConstantExpression)
        {
          String nodeParam  = (String)  ((ParameterExpression) nodeParamObj).getExpression().evaluateConstant();
          if (nodeParam != null) result.put("presentationstrategy", nodeParam);
        }
      else if (nodeParamObj instanceof String)
        {
          result.put("presentationstrategy", (String) nodeParamObj);
        }
      else
        {
          log.error("unsupported value/type expression {} - skipping for node.parameter.strategy", nodeParamObj);
        }
      return result;
    }
    
  }

  
  /*****************************************
  *
  *  class ActionManagerPurchase
  *
  *****************************************/

  public static class ActionManagerPurchase extends ActionManagerDNBO
  {
    /*****************************************
    *
    *  constructor
    *
    *****************************************/

    public ActionManagerPurchase(JSONObject configuration) throws GUIManagerException
    {
      super(configuration);
    }
        
    /*****************************************
    *
    *  execute (Purchase)
    *
    *****************************************/

    @Override public List<Action> executeOnEntry(EvolutionEventContext evolutionEventContext, SubscriberEvaluationRequest subscriberEvaluationRequest)
    {
      log.info("ActionManagerPurchase.executeOnEntry() method call");
      Object[] res = handleToken(evolutionEventContext, subscriberEvaluationRequest, TokenChange.REDEEM);
      @SuppressWarnings("unchecked")
      List<Action> result = (List<Action>) res[0];
      if (res.length == 1)
        {
          return result;
        }
      DNBOToken token = (DNBOToken) res[1];
      ContextUpdate tokenUpdate = (ContextUpdate) res[2];
      PresentationStrategy strategy = (PresentationStrategy) res[3];
      TokenType tokenType = (TokenType) res[4];
      Supplier supplier = (Supplier) res[5];

      /*****************************************
      *
      *  action -- score offers
      *
      *****************************************/
      
      Object resAllocate = handleAllocate(evolutionEventContext, subscriberEvaluationRequest, strategy, token, tokenType, tokenUpdate, TokenChange.REDEEM, supplier);
      if (resAllocate instanceof Action[])
        {
          return Arrays.asList((Action[]) resAllocate);
        }
      Collection<ProposedOfferDetails> presentedOfferDetailsList = (Collection<ProposedOfferDetails>) resAllocate;
      
      if (presentedOfferDetailsList.isEmpty())
        {
          String str = "cannot select first offer because list is empty";
          log.error(str);
          return invalidPurchase(evolutionEventContext, subscriberEvaluationRequest, token, TokenChange.REDEEM, str);
        }

      //   select 1st offer of the list
      
      ProposedOfferDetails acceptedOfferDetail = presentedOfferDetailsList.iterator().next();
      String offerID = acceptedOfferDetail.getOfferId();
      token.setAcceptedOfferID(offerID);
      Offer offer = evolutionEventContext.getOfferService().getActiveOffer(offerID, evolutionEventContext.now());
      if (offer == null)
        {
          String str = "invalid offer returned by scoring " + offerID; 
          log.error(str);
          return invalidPurchase(evolutionEventContext, subscriberEvaluationRequest, token, TokenChange.REDEEM, str);
        }
      tokenUpdate.getParameters().put("action.accepted.offer", offer.getDisplay());
      
      token.setTokenStatus(TokenStatus.Redeemed);
      token.setAutoBound(true);
      token.setAutoRedeemed(true);
      token.setRedeemedDate(evolutionEventContext.now());
      
      /*****************************************
      *
      *  Effective purchase of the offer
      *
      *****************************************/

      int quantity = 1;
      String salesChannelID = token.getPresentedOffersSalesChannel();
      String deliveryRequestSource = subscriberEvaluationRequest.getJourneyState().getJourneyID();

      PurchaseFulfillmentRequest request = new PurchaseFulfillmentRequest(evolutionEventContext,  deliveryRequestSource, offerID, quantity, salesChannelID, "", "", subscriberEvaluationRequest.getTenantID());
      request.setModuleID(DeliveryRequest.Module.Journey_Manager.getExternalRepresentation());
      request.setFeatureID(deliveryRequestSource);
      result.add(request);
      
      /*****************************************
      *
      *  return
      *
      *****************************************/
      return result;
    }
    
    @Override public Map<String, String> getGUIDependencies(List<GUIService> guiServiceList, JourneyNode journeyNode, int tenantID)
    {
      Map<String, String> result = new HashMap<String, String>();
      Object tokentypeNodeParamObj = journeyNode.getNodeParameters().get("node.parameter.tokentype");
      if (tokentypeNodeParamObj instanceof ParameterExpression && ((ParameterExpression) tokentypeNodeParamObj).getExpression() instanceof ConstantExpression)
        {
          String nodeParam  = (String)  ((ParameterExpression) tokentypeNodeParamObj).getExpression().evaluateConstant();
          if (nodeParam != null) result.put("tokentype", nodeParam);
        }
      else if (tokentypeNodeParamObj instanceof String)
        {
          result.put("tokentype", (String) tokentypeNodeParamObj);
        }
      else
        {
          log.error("unsupported value/type expression {} - skipping", tokentypeNodeParamObj);
        }
      Object nodeParamObj = journeyNode.getNodeParameters().get("node.parameter.strategy");
      if (nodeParamObj instanceof ParameterExpression && ((ParameterExpression) nodeParamObj).getExpression() instanceof ConstantExpression)
        {
          String nodeParam  = (String)  ((ParameterExpression) nodeParamObj).getExpression().evaluateConstant();
          if (nodeParam != null) result.put("presentationstrategy", nodeParam);
        }
      else if (nodeParamObj instanceof String)
        {
          result.put("presentationstrategy", (String) nodeParamObj);
        }
      else
        {
          log.error("unsupported value/type expression {} - skipping for node.parameter.strategy", nodeParamObj);
        }
      return result;
    }
  }
  
  /*****************************************
  *
  *  class ActionManagerAccept
  *
  *****************************************/

  public static class ActionManagerAccept extends ActionManagerDNBO
  {
    /*****************************************
    *
    *  constructor
    *
    *****************************************/

    public ActionManagerAccept(JSONObject configuration) throws GUIManagerException
    {
      super(configuration);
    }
        
    /*****************************************
    *
    *  execute (Accept)
    *
    *****************************************/

    @Override public List<Action> executeOnEntry(EvolutionEventContext evolutionEventContext, SubscriberEvaluationRequest subscriberEvaluationRequest)
    {
      log.info("ActionManagerAccept.executeOnEntry() method call");
      int rankParam = (Integer) CriterionFieldRetriever.getJourneyNodeParameter(subscriberEvaluationRequest, "node.parameter.rank"); // 1,2,3,...
      if (rankParam < 1)
        {
          String str = "rank must be at least 1, found " + rankParam; 
          log.error(str);
          return invalidPurchase(evolutionEventContext, subscriberEvaluationRequest, null, null, null);
        }
      int rank = rankParam - 1;

      List<Token> currentTokens = evolutionEventContext.getSubscriberState().getSubscriberProfile().getTokens();
      Token lastToken = null;
      String journeyID = subscriberEvaluationRequest.getJourneyState().getJourneyID();
      if (journeyID == null)
        {
          String str = "internal error : cannot find current journey ID"; 
          log.error(str);
          return invalidPurchase(evolutionEventContext, subscriberEvaluationRequest, null, null, null);
        }
      for (Token token : currentTokens)
        {
          String moduleID = token.getModuleID();
          String featureID = token.getFeatureID() + "";
          if (DeliveryRequest.Module.Journey_Manager.getExternalRepresentation().equals(moduleID) && journeyID.equals(featureID))
            {
              lastToken = token;
              // continue to get the last one
            }
        }
      if (lastToken == null)
        {
          String str = "no token previously allocated"; 
          log.error(str);
          return invalidPurchase(evolutionEventContext, subscriberEvaluationRequest, null, null, null);
        }

      if (!(lastToken instanceof DNBOToken))
        {
          String str = "internal error : token is not of the right type : " + lastToken.getClass().getName(); 
          log.error(str);
          return invalidPurchase(evolutionEventContext, subscriberEvaluationRequest, null, null, null);
        }
      DNBOToken token = (DNBOToken) lastToken;
      List<String> presentedOffers = token.getProposedOfferDetails().stream().map(offerDetails -> offerDetails.getOfferId()).collect(Collectors.toList());
      if (presentedOffers == null)
        {
          String str = "token has no presented offers"; 
          log.error(str);
          return invalidPurchase(evolutionEventContext, subscriberEvaluationRequest, token, TokenChange.REDEEM, str);
        }
      if (rank >= presentedOffers.size())
        {
          String str = "presented offers list does not contain enough elements : " + presentedOffers.size() + " , expected >= " + rankParam; 
          log.error(str);
          return invalidPurchase(evolutionEventContext, subscriberEvaluationRequest, token, TokenChange.REDEEM, str);
        }
      String offerID = presentedOffers.get(rank);
      if (offerID == null)
        {
          String str = "internal error : presented offer at rank " + rankParam + " is null"; 
          log.error(str);
          return invalidPurchase(evolutionEventContext, subscriberEvaluationRequest, token, TokenChange.REDEEM, str);
        }
      
      List<Action> result = new ArrayList<>();
      token.setAcceptedOfferID(offerID);
      Offer offer = evolutionEventContext.getOfferService().getActiveOffer(offerID, evolutionEventContext.now());
      if (offer == null)
        {
          String str = RESTAPIGenericReturnCodes.INVALID_TOKEN_CODE.getGenericResponseCode()+"";//"invalid offer returned by scoring " + offerID; 
          log.error(str);
          return Collections.<Action>singletonList(generateTokenChange(evolutionEventContext, subscriberEvaluationRequest, token.getTokenCode(), TokenChange.REDEEM, str));
        }
      ContextUpdate contextUpdate = new ContextUpdate(ActionType.ActionManagerContextUpdate);
      contextUpdate.getParameters().put("action.accepted.offer", offer.getDisplay());
      result.add(contextUpdate);
      
      TokenChange tokenChange = generateTokenChange(evolutionEventContext, subscriberEvaluationRequest, token.getTokenCode(), TokenChange.REDEEM, RESTAPIGenericReturnCodes.SUCCESS.getGenericResponseCode()+"");
      tokenChange.setOrigin("JourneyAccept"); 
      result.add(tokenChange);
      
      token.setTokenStatus(TokenStatus.Redeemed);
      token.setAutoBound(true);
      token.setAutoRedeemed(true);
      token.setRedeemedDate(evolutionEventContext.now());
      
      /*****************************************
      *
      *  Effective purchase of the offer
      *
      *****************************************/

      int quantity = 1;
      String salesChannelID = token.getPresentedOffersSalesChannel();
      String deliveryRequestSource = subscriberEvaluationRequest.getJourneyState().getJourneyID();

      PurchaseFulfillmentRequest request = new PurchaseFulfillmentRequest(evolutionEventContext, deliveryRequestSource, offerID, quantity, salesChannelID, "", "", subscriberEvaluationRequest.getTenantID());
      request.setModuleID(DeliveryRequest.Module.Journey_Manager.getExternalRepresentation());
      request.setFeatureID(deliveryRequestSource);
      result.add(request);
      
      /*****************************************
      *
      *  return
      *
      *****************************************/
      return result;
    }
  }

}
