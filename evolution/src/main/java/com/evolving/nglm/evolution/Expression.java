/*****************************************************************************
*
*  Expression.java
*
*****************************************************************************/

package com.evolving.nglm.evolution;

import java.io.IOException;
import java.io.StringReader;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.evolving.nglm.core.Deployment;
import com.evolving.nglm.core.RLMDateUtils;
import com.evolving.nglm.core.ServerRuntimeException;
import com.evolving.nglm.core.SystemTime;
import com.evolving.nglm.evolution.EvaluationCriterion.CriterionDataType;
import com.evolving.nglm.evolution.EvaluationCriterion.CriterionException;
import com.evolving.nglm.evolution.EvolutionUtilities.TimeUnit;

/*****************************************
*
*  class Expression
*
*****************************************/

public abstract class Expression
{
  
  //
  //  logger
  //

  private static final Logger log = LoggerFactory.getLogger(Expression.class);

  
  /*****************************************************************************
  *
  *  enum
  *
  *****************************************************************************/

  //
  //  enum ExpressionContext
  //

  public enum ExpressionContext
  {
    Criterion,
    Parameter,
    ContextVariable
  }

  //
  //  enum Token
  //

  private enum Token
  {
    //
    //  identifiers
    //

    IDENTIFIER,
    FUNCTION_CALL,

    //
    //  constant literals
    //

    INTEGER,
    DOUBLE,
    STRING,
    BOOLEAN,

    //
    //  operators
    //

    PLUS,
    MINUS,
    MULTIPLY,
    DIVIDE,
    MODULO,

    //
    //  syntax
    //

    COMMA,
    LEFT_PAREN,
    RIGHT_PAREN,
    LEFT_BRACKET,
    RIGHT_BRACKET,
    INVALID_CHAR,
    INVALID_IDENTIFIER,
    END_OF_INPUT
  }

  //
  //  ExpressionDataType
  //

  public enum ExpressionDataType
  {
    IntegerExpression,
    DoubleExpression,
    StringExpression,
    BooleanExpression,
    DateExpression,
    TimeExpression,
    IntegerSetExpression,
    StringSetExpression,
    EmptySetExpression,
    OpaqueReferenceExpression,
    NoArgument;
  }

  //
  //  ExpressionOperator
  //

  public enum ExpressionOperator
  {
    PlusOperator(Token.PLUS),
    MinusOperator(Token.MINUS),
    MultiplyOperator(Token.MULTIPLY),
    DivideOperator(Token.DIVIDE),
    ModuloOperator(Token.MODULO),
    UnknownOperator(Token.INVALID_CHAR);
    private Token operatorName;
    private ExpressionOperator(Token operatorName) { this.operatorName = operatorName; }
    public Token getOperatorName() { return operatorName; }
    public static ExpressionOperator fromOperatorName(Token operatorName) { for (ExpressionOperator enumeratedValue : ExpressionOperator.values()) { if (enumeratedValue.getOperatorName() == operatorName) return enumeratedValue; } return UnknownOperator; }
  }
  
  //
  //  ExpressionFunction
  //

  public enum ExpressionFunction
  {
    DateConstantFunction("dateConstant"),
    TimeConstantFunction("timeConstant"),
    TimeAddFunction("timeAdd"),
    DateAddFunction("dateAdd"),
    DateAddOrConstantFunction("dateAddOrConstant"),
    RoundFunction("round"),
    RoundUpFunction("roundUp"),
    RoundDownFunction("roundDown"),
    DaysUntilFunction("daysUntil"),
    MonthsUntilFunction("monthsUntil"),
    DaysSinceFunction("daysSince"),
    MonthsSinceFunction("monthsSince"),
    FirstWordFunction("firstWord"),
    SecondWordFunction("secondWord"),
    ThirdWordFunction("thirdWord"),
    IntFunction("int"),
    StringFunction("string"),
    DoubleFunction("double"),
    MaxFunction("max"),
    MinFunction("min"),
    UnknownFunction("(unknown)");
    private String functionName;
    private ExpressionFunction(String functionName) { this.functionName = functionName; }
    public String getFunctionName() { return functionName; }
    public static ExpressionFunction fromFunctionName(String functionName) { 
      for (ExpressionFunction enumeratedValue : ExpressionFunction.values()) 
        { 
          if (enumeratedValue.getFunctionName().equalsIgnoreCase(functionName)) return enumeratedValue; 
        } return UnknownFunction; 
    }
  }
  
  /*****************************************
  *
  *  data
  *
  *****************************************/

  protected ExpressionDataType type;
  protected String nodeID;
  protected String tagFormat;
  protected Integer tagMaxLength;
  private int tenantID;

  /*****************************************
  *
  *  abstract
  *
  *****************************************/

  public abstract void typeCheck(ExpressionContext expressionContext, TimeUnit baseTimeUnit, int tenantID);
  public abstract int assignNodeID(int preorderNumber);
  public boolean isConstant() { return false; }
  protected abstract Object evaluate(SubscriberEvaluationRequest subscriberEvaluationRequest, TimeUnit baseTimeUnit);
  public Object evaluateConstant() { throw new ServerRuntimeException("constant expression"); }
  public abstract void esQuery(StringBuilder script, TimeUnit baseTimeUnit, int tenantID) throws CriterionException;
  public abstract Object esQueryNoPainless() throws CriterionException;

  /*****************************************
  *
  *  accessors
  *
  *****************************************/

  public ExpressionDataType getType() { return type; }
  public String getNodeID() { return nodeID; }
  public String getTagFormat() { return tagFormat; }
  public Integer getTagMaxLength() { return tagMaxLength; }
  public String getEffectiveTagFormat() { return (tagFormat != errorTagFormat) ? tagFormat : null; }
  public Integer getEffectiveTagMaxLength() { return (tagMaxLength != errorTagMaxLength) ? tagMaxLength : null; }

  /*****************************************
  *
  *  setters
  *
  *****************************************/

  public void setType(ExpressionDataType type) { this.type = type; }
  public void setNodeID(int preorderNumber) { this.nodeID = Integer.toString(preorderNumber); }
  public void setTagFormat(String tagFormat) { this.tagFormat = tagFormat; }
  public void setTagMaxLength(Integer tagMaxLength) { this.tagMaxLength = tagMaxLength; }

  /*****************************************
  *
  *  evaluateExpression
  *
  *****************************************/

  public Object evaluateExpression(SubscriberEvaluationRequest subscriberEvaluationRequest, TimeUnit baseTimeUnit)
  {
    Object result;
    try
      {
        result = evaluate(subscriberEvaluationRequest, baseTimeUnit);
      }
    catch (ExpressionNullException e)
      {
        result = null;
      }
    return result;
  }

  /*****************************************
  *
  *  errorConstants
  *
  *****************************************/

  private static String errorTagFormat = new String("(error)");
  private static Integer errorTagMaxLength = new Integer(0);

  /*****************************************
  *
  *  constructor
  *
  *****************************************/

  protected Expression(int tenantID)
  {
    this.type = null;
    this.nodeID = null;
    this.tagFormat = null;
    this.tagMaxLength = null;
    this.tenantID = tenantID;
  }

  /*****************************************
  *
  *  class ConstantExpression
  *
  *****************************************/

  public static class ConstantExpression extends Expression
  {
    /*****************************************
    *
    *  data
    *
    *****************************************/

    private Object constant;

    /*****************************************
    *
    *  typeCheck
    *
    *****************************************/

    @Override public void typeCheck(ExpressionContext expressionContext, TimeUnit baseTimeUnit, int tenantID) { }

    /*****************************************
    *
    *  assignNodeID
    *
    *****************************************/

    @Override public int assignNodeID(int preorderNumber)
    {
      setNodeID(preorderNumber);
      return preorderNumber;
    }

    /*****************************************
    *
    *  isConstant
    *
    *****************************************/

    @Override public boolean isConstant() { return true; }

    /*****************************************
    *
    *  evaluate
    *
    *****************************************/

    @Override protected Object evaluate(SubscriberEvaluationRequest subscriberEvaluationRequest, TimeUnit baseTimeUnit)
    {
      return constant;
    }

    /*****************************************
    *
    *  evaluateConstant
    *
    *****************************************/

    @Override public Object evaluateConstant()
    {
      return constant;
    }

    /*****************************************
    *
    *  esQuery
    *
    *****************************************/

    @Override public void esQuery(StringBuilder script, TimeUnit baseTimeUnit, int tenantID) throws CriterionException
    {
      switch (getType())
        {
          case IntegerExpression:
            script.append("def right_" + getNodeID() + " = " + ((Number) constant).toString() + "; ");
            break;

          case DoubleExpression:
            script.append("def right_" + getNodeID() + " = " + ((Double) constant).toString() + "; ");
            break;

          case StringExpression:
            script.append("def right_" + getNodeID() + " = '" + ((String) constant) + "'; ");
            break;

          case BooleanExpression:
            script.append("def right_" + getNodeID() + " = " + ((Boolean) constant).toString() + "; ");
            break;

          case DateExpression:
            DateFormat scriptDateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSX");   // TODO EVPRO-99
            script.append("def rightSF_" + getNodeID() + " = new SimpleDateFormat(\"yyyy-MM-dd'T'HH:mm:ss.SSSX\"); ");   // TODO EVPRO-99
            script.append("def rightDT_" + getNodeID() + " = rightSF_" + getNodeID() + ".parse(\"" + scriptDateFormat.format((Date) constant) + "\"); ");
            script.append("def rightCalendar_" + getNodeID() + " = rightSF_" + getNodeID() + ".getCalendar(); ");
            script.append("rightCalendar_" + getNodeID() + ".setTime(rightDT_" + getNodeID() + "); ");
            script.append("def rightInstant_" + getNodeID() + " = rightCalendar_" + getNodeID() + ".toInstant(); ");
            script.append("def right_" + getNodeID() + " = LocalDateTime.ofInstant(rightInstant_" + getNodeID() + ", ZoneOffset.UTC); ");
            break;

          case StringSetExpression:
            script.append("ArrayList right_" + getNodeID() + " = new ArrayList(); ");
            for (Object item : (Set<Object>) constant) script.append("right_" + getNodeID() + ".add(\"" + item.toString() + "\"); ");
            break;
            
          case IntegerSetExpression:
            script.append("ArrayList right_" + getNodeID() + " = new ArrayList(); ");
            for (Object item : (Set<Object>) constant) script.append("right_" + getNodeID() + ".add(" + item.toString() + "); ");
            break;

          case EmptySetExpression:
            script.append("def right_" + getNodeID() + " = new ArrayList(); ");
            break;

          case TimeExpression:
          default:
            throw new CriterionException("invalid criterionField datatype for esQuery");
        }
    }

    /*****************************************
     *
     *  esQueryNoPainless
     *
     *****************************************/

    @Override public Object esQueryNoPainless() throws CriterionException
    {
      switch (getType())
      {
      case IntegerExpression:
      case DoubleExpression:
      case StringExpression:
        return constant;

      case BooleanExpression:
        return ((Boolean) constant).toString();

      case DateExpression:
        DateFormat scriptDateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSX");
        return scriptDateFormat.format((Date) constant);
        //break;

      case StringSetExpression: case IntegerSetExpression:
        //use set to avoid duplicates in the list
        Set<String> returnItems = new HashSet<>();
        for (Object item : (Set<Object>) constant)
        {
          returnItems.add(item.toString());
        }
        return returnItems;

      case EmptySetExpression:
        return new HashSet<>();

      case TimeExpression:
      default:
        throw new CriterionException("invalid criterionField datatype for esQuery");
      }
    }

    /*****************************************
    *
    *  constructor
    *
    *****************************************/

    public ConstantExpression(ExpressionDataType type, Object constant, int tenantID)
    {
      super(tenantID);
      this.constant = constant;
      this.type = type;
    }
  }

  /*****************************************
  *
  *  class ReferenceExpression
  *
  *****************************************/

  public static class ReferenceExpression extends Expression
  {
    /*****************************************
    *
    *  data
    *
    *****************************************/

    private CriterionField reference;

    /*****************************************
    *
    *  typeCheck
    *
    *****************************************/

    @Override public void typeCheck(ExpressionContext expressionContext, TimeUnit baseTimeUnit, int tenantID)
    {
      //
      //  type
      //

      switch (reference.getFieldDataType())
        {
          case IntegerCriterion:
            setType(ExpressionDataType.IntegerExpression);
            break;
          case DoubleCriterion:
            setType(ExpressionDataType.DoubleExpression);
            break;
          case StringCriterion:
            setType(ExpressionDataType.StringExpression);
            break;
          case BooleanCriterion:
            setType(ExpressionDataType.BooleanExpression);
            break;
          case AniversaryCriterion:
          case DateCriterion:
            setType(ExpressionDataType.DateExpression);
            break;
          case TimeCriterion:
            setType(ExpressionDataType.TimeExpression);
            break;
          case StringSetCriterion:
            setType(ExpressionDataType.StringSetExpression);
            break;
          case EvaluationCriteriaParameter:
          case SMSMessageParameter:
          case EmailMessageParameter:
          case PushMessageParameter:
          case NotificationStringParameter:
          case NotificationHTMLStringParameter:
          case Dialog:
          case WorkflowParameter:
            setType(ExpressionDataType.OpaqueReferenceExpression);
            break;

          default:
            throw new ExpressionTypeCheckException("invariant violated");
        }

      //
      //  evaluation.date -- illegal
      //

      switch (expressionContext)
        {
          case Criterion:
            if (reference.getID().equals(CriterionField.EvaluationDateField))
              {
                throw new ExpressionTypeCheckException("illegal reference to " + CriterionField.EvaluationDateField);
              }
            break;
        }

      //
      //  tagFormat/tagMaxLength
      //

      setTagFormat(reference.getTagFormat());
      setTagMaxLength(reference.getTagMaxLength());
    }

    /*****************************************
    *
    *  getCriterionDataType
    *
    *****************************************/

    public CriterionDataType getCriterionDataType() { return reference.getFieldDataType(); }

    /*****************************************
    *
    *  assignNodeID
    *
    *****************************************/

    @Override public int assignNodeID(int preorderNumber)
    {
      setNodeID(preorderNumber);
      return preorderNumber;
    }

    /*****************************************
    *
    *  evaluate
    *
    *****************************************/

    @Override protected Object evaluate(SubscriberEvaluationRequest subscriberEvaluationRequest, TimeUnit baseTimeUnit)
    {
      //
      //  retrieve
      //
      Object referenceValue = null;
      try {
        referenceValue = reference.retrieve(subscriberEvaluationRequest);
      }
      catch(StackOverflowError e) {
        log.warn("Exception " + e.getClass().getName() + " with criterionField reference : " + reference + " and subscriberEvaluationRequest : " + subscriberEvaluationRequest, e);
      }
      
      
      //
      //  null check
      //

      if (referenceValue == null) throw new ExpressionNullException(reference);

      //
      //  normalize
      //

      switch (type)
        {
          case DateExpression:
            switch (baseTimeUnit)
              {
                case Instant:
                  break;
                case Minute:
                  referenceValue = RLMDateUtils.truncate((Date) referenceValue, Calendar.MINUTE, Deployment.getDeployment(subscriberEvaluationRequest.getTenantID()).getTimeZone());
                  break;
                case Hour:
                  referenceValue = RLMDateUtils.truncate((Date) referenceValue, Calendar.HOUR, Deployment.getDeployment(subscriberEvaluationRequest.getTenantID()).getTimeZone());
                  break;
                case Day:
                  referenceValue = RLMDateUtils.truncate((Date) referenceValue, Calendar.DATE, Deployment.getDeployment(subscriberEvaluationRequest.getTenantID()).getTimeZone());
                  break;
                case Week:
                  referenceValue = RLMDateUtils.truncate((Date) referenceValue, Calendar.DAY_OF_WEEK, Deployment.getDeployment(subscriberEvaluationRequest.getTenantID()).getTimeZone());
                  break;
                case Month:
                  referenceValue = RLMDateUtils.truncate((Date) referenceValue, Calendar.MONTH, Deployment.getDeployment(subscriberEvaluationRequest.getTenantID()).getTimeZone());
                  break;
                case Year:
                  referenceValue = RLMDateUtils.truncate((Date) referenceValue, Calendar.YEAR, Deployment.getDeployment(subscriberEvaluationRequest.getTenantID()).getTimeZone());
                  break;
              }
            break;
            
          case TimeExpression:
            
            //
            // 
            //
            
            break;
        }

      //
      //  return
      //
      
      return referenceValue;
    }

    /*****************************************
    *
    *  esQuery
    *
    *****************************************/

    @Override public void esQuery(StringBuilder script, TimeUnit baseTimeUnit, int tenantID) throws CriterionException
    {
      /*****************************************
      *
      *  esField
      *
      *****************************************/

      String esField = reference.getESField();
      if (esField == null)
        {
          throw new CriterionException("invalid criterionField " + reference);
        }

      /*****************************************
      *
      *  script
      *
      *****************************************/

      switch (getType())
        {
          case StringExpression:
          case IntegerExpression:
          case DoubleExpression:
          case BooleanExpression:
  	    script.append("def right_" + getNodeID() + " = (doc['" + esField + "'].size() != 0) ? doc['" + esField + "']?.value : null; ");
            break;
            
          case StringSetExpression:
          case IntegerSetExpression:
  	    script.append("def right_" + getNodeID() + " = new ArrayList(); right_" + getNodeID() + ".addAll(doc['" + esField + "']); ");
            break;
            
          case DateExpression:
            script.append("def right_" + getNodeID() + "; ");
            script.append("if (doc['" + esField + "'].size() != 0) { ");
            script.append("def rightSF_" + getNodeID() + " = new SimpleDateFormat(\"yyyy-MM-dd'T'HH:mm:ss.SSSX\"); ");   // TODO EVPRO-99
            script.append("def rightMillis_" + getNodeID() + " = doc['" + esField + "'].value.getMillis(); ");
            script.append("def rightCalendar_" + getNodeID() +" = rightSF_" + getNodeID() + ".getCalendar(); ");
            script.append("rightCalendar_" + getNodeID() + ".setTimeInMillis(rightMillis_" + getNodeID() + "); ");
            script.append("def rightInstant_" + getNodeID() + " = rightCalendar_" + getNodeID() + ".toInstant(); ");
            script.append("def rightRaw_" + getNodeID() + " = LocalDateTime.ofInstant(rightInstant_" + getNodeID() + ", ZoneOffset.UTC); ");
            script.append(EvaluationCriterion.constructDateTruncateESScript(getNodeID(), "rightRaw", "tempRight", baseTimeUnit));
            script.append("right_" + getNodeID() + " =  tempRight_" + getNodeID() + "; } ");
            break;
            
          case TimeExpression:
          default:
            throw new CriterionException("invalid criterionField datatype for esQuery");
        }
    }

    /*****************************************
     *
     *  esQueryNoPainless
     *
     *****************************************/

    @Override public Object esQueryNoPainless() throws CriterionException
    {
      throw new CriterionException(this.getClass().getSimpleName()+"cannot be processed into an no painless query");
    }

    /*****************************************
    *
    *  constructor
    *
    *****************************************/

    public ReferenceExpression(CriterionField reference, int tenantID)
    {
      super(tenantID);
      this.reference = reference;
    }

    public String getESField()
    {
      return (reference != null && reference.getESField() != null) ? reference.getESField() : null;
    }
  }

  /*****************************************
  *
  *  class OperatorExpression
  *
  *****************************************/

  public static class OperatorExpression extends Expression
  {
    /*****************************************
    *
    *  data
    *
    *****************************************/

    private ExpressionOperator operator;
    private Expression leftArgument;
    private Expression rightArgument;

    /*****************************************
    *
    *  typeCheck
    *
    *****************************************/

    @Override public void typeCheck(ExpressionContext expressionContext, TimeUnit baseTimeUnit, int tenantID)
    {
      /*****************************************
      *
      *  typeCheck arguments
      *
      *****************************************/

      leftArgument.typeCheck(expressionContext, baseTimeUnit, tenantID);
      rightArgument.typeCheck(expressionContext, baseTimeUnit, tenantID);

      /*****************************************
      *
      *  type
      *
      *****************************************/

      switch (leftArgument.getType())
        {
          case IntegerExpression:
          case DoubleExpression:
            switch (operator)
              {
                case PlusOperator:
                case MinusOperator:
                case MultiplyOperator:
                  switch (rightArgument.getType())
                    {
                      case IntegerExpression:
                      case DoubleExpression:
                        setType((leftArgument.getType() == ExpressionDataType.IntegerExpression && rightArgument.getType() == ExpressionDataType.IntegerExpression) ? ExpressionDataType.IntegerExpression : ExpressionDataType.DoubleExpression);
                        break;
                      default:
                        throw new ExpressionTypeCheckException("type exception");
                    }
                  break;

                case DivideOperator:
                  switch (rightArgument.getType())
                    {
                      case IntegerExpression:
                      case DoubleExpression:
                        setType(ExpressionDataType.DoubleExpression);
                        break;
                      default:
                        throw new ExpressionTypeCheckException("type exception");
                    }
                  break;

                case ModuloOperator:
                  // temp fix : we allow "float % int" as a workaround, in case we are called with "arpu % 3"...
                  if (leftArgument.getType() != ExpressionDataType.IntegerExpression && 
                      leftArgument.getType() != ExpressionDataType.DoubleExpression) throw new ExpressionTypeCheckException("type exception");
                  switch (rightArgument.getType())
                    {
                      case IntegerExpression:
                        setType(ExpressionDataType.IntegerExpression);
                        break;
                      default:
                        throw new ExpressionTypeCheckException("type exception");
                    }
                  break;

                default:
                  throw new ExpressionTypeCheckException("type exception");
              }
            break;
            
          case StringExpression:
            switch (operator)
              {
                case PlusOperator:
                  switch (rightArgument.getType())
                    {
                      case StringExpression:
                        setType(ExpressionDataType.StringExpression);
                        break;
                      default:
                        throw new ExpressionTypeCheckException("type exception");
                    }
                  break;
                  
                default:
                  throw new ExpressionTypeCheckException("type exception");
              }
            break;
            
          default:
            throw new ExpressionTypeCheckException("type exception");
        }

      //
      //  tagFormat
      //

      if (leftArgument.getTagFormat() == errorTagFormat || rightArgument.getTagFormat() == errorTagFormat)
        setTagFormat(errorTagFormat);
      else if (Objects.equals(leftArgument.getTagFormat(), rightArgument.getTagFormat()))
        setTagFormat(leftArgument.getTagFormat());
      else if (leftArgument.getTagFormat() == null)
        setTagFormat(rightArgument.getTagFormat());
      else if (rightArgument.getTagFormat() == null)
        setTagFormat(leftArgument.getTagFormat());
      else
        setTagFormat(errorTagFormat);

      //
      //  tagMaxLength
      //

      if (leftArgument.getTagMaxLength() == errorTagMaxLength || rightArgument.getTagMaxLength() == errorTagMaxLength)
        setTagMaxLength(errorTagMaxLength);
      else if (Objects.equals(leftArgument.getTagMaxLength(), rightArgument.getTagMaxLength()))
        setTagMaxLength(leftArgument.getTagMaxLength());
      else if (leftArgument.getTagMaxLength() == null)
        setTagMaxLength(rightArgument.getTagMaxLength());
      else if (rightArgument.getTagMaxLength() == null)
        setTagMaxLength(leftArgument.getTagMaxLength());
      else
        setTagMaxLength(errorTagMaxLength);
    }

    /*****************************************
    *
    *  assignNodeID
    *
    *****************************************/

    @Override public int assignNodeID(int preorderNumber)
    {
      setNodeID(preorderNumber);
      preorderNumber = leftArgument.assignNodeID(preorderNumber+1);
      preorderNumber = rightArgument.assignNodeID(preorderNumber+1);
      return preorderNumber;
    }

    /*****************************************
    *
    *  evaluate
    *
    *****************************************/

    @Override protected Object evaluate(SubscriberEvaluationRequest subscriberEvaluationRequest, TimeUnit baseTimeUnit)
    {
      /*****************************************
      *
      *  evaluate arguments
      *
      *****************************************/

      Object leftValue = leftArgument.evaluate(subscriberEvaluationRequest, baseTimeUnit);
      Object rightValue = rightArgument.evaluate(subscriberEvaluationRequest, baseTimeUnit);

      /*****************************************
      *
      *  evaluate operator
      *
      *****************************************/

      Object result = null;
      switch (type)
        {
          case IntegerExpression:
          case DoubleExpression:
            Number leftValueNumber = (Number) leftValue;
            Number rightValueNumber = (Number) rightValue;
            switch (operator)
              {
                case PlusOperator:
                  switch (type)
                    {
                      case IntegerExpression:
                        result = new Long(leftValueNumber.longValue() + rightValueNumber.longValue());
                        break;
                      case DoubleExpression:
                        result = new Double(leftValueNumber.doubleValue() + rightValueNumber.doubleValue());
                        break;
                    }
                  break; 
                  
                case MinusOperator:
                  switch (type)
                    {
                      case IntegerExpression:
                        result = new Long(leftValueNumber.longValue() - rightValueNumber.longValue());
                        break;
                      case DoubleExpression:
                        result = new Double(leftValueNumber.doubleValue() - rightValueNumber.doubleValue());
                        break;
                    }
                  break;

                case MultiplyOperator:
                  switch (type)
                    {
                      case IntegerExpression:
                        result = new Long(leftValueNumber.longValue() * rightValueNumber.longValue());
                        break;
                      case DoubleExpression:
                        result = new Double(leftValueNumber.doubleValue() * rightValueNumber.doubleValue());
                        break;
                    }
                  break;

                case DivideOperator:
                  result = new Double(leftValueNumber.doubleValue() / rightValueNumber.doubleValue());
                  break;

                case ModuloOperator:
                  result = new Long(leftValueNumber.longValue() % rightValueNumber.longValue());
                  break;
              }
            break;

          case StringExpression:
            String leftValueString = (String) leftValue;
            String rightValueString = (String) rightValue;
            result = leftValueString + rightValueString;
            break;
        }

      /*****************************************
      *
      *  return
      *
      *****************************************/

      return result;
    }

    /*****************************************
    *
    *  esQuery
    *
    *****************************************/

    @Override public void esQuery(StringBuilder script, TimeUnit baseTimeUnit, int tenantID) throws CriterionException
    {
      /*****************************************
      *
      *  script
      *
      *****************************************/

      //
      //  arguments
      //
      
      leftArgument.esQuery(script, baseTimeUnit, tenantID);
      rightArgument.esQuery(script, baseTimeUnit, tenantID);

      //
      //  operator
      //
      
      switch (operator)
        {
          case PlusOperator:
            script.append("def right_" + getNodeID() + " = right_" + leftArgument.getNodeID() + " + right_" + rightArgument.getNodeID() + "; ");
            break; 

          case MinusOperator:
            script.append("def right_" + getNodeID() + " = right_" + leftArgument.getNodeID() + " - right_" + rightArgument.getNodeID() + "; ");
            break;

          case MultiplyOperator:
            script.append("def right_" + getNodeID() + " = right_" + leftArgument.getNodeID() + " * right_" + rightArgument.getNodeID() + "; ");
            break;

          case DivideOperator:
            script.append("def right_" + getNodeID() + " = right_" + leftArgument.getNodeID() + " / right_" + rightArgument.getNodeID() + "; ");
            break;
        }
    }

    /*****************************************
     *
     *  esQueryNoPainless
     *
     *****************************************/

    @Override public Object esQueryNoPainless() throws CriterionException
    {
      throw new CriterionException(this.getClass().getSimpleName()+"cannot be processed into an no painless query");
    }

    /*****************************************
    *
    *  constructor
    *
    *****************************************/

    public OperatorExpression(ExpressionOperator operator, Expression leftArgument, Expression rightArgument, int tenantID)
    {
      super(tenantID);
      this.operator = operator;
      this.leftArgument = leftArgument;
      this.rightArgument = rightArgument;
    }
  }

  /*****************************************
  *
  *  class UnaryExpression
  *
  *****************************************/

  public static class UnaryExpression extends Expression
  {
    /*****************************************
    *
    *  data
    *
    *****************************************/

    private ExpressionOperator operator;
    private Expression unaryArgument;

    /*****************************************
    *
    *  typeCheck
    *
    *****************************************/

    @Override public void typeCheck(ExpressionContext expressionContext, TimeUnit baseTimeUnit, int tenantID)
    {
      /*****************************************
      *
      *  typeCheck arguments
      *
      *****************************************/

      unaryArgument.typeCheck(expressionContext, baseTimeUnit, tenantID);

      /*****************************************
      *
      *  type
      *
      *****************************************/

      switch (unaryArgument.getType())
        {
          case IntegerExpression:
          case DoubleExpression:
            switch (operator)
              {
                case PlusOperator:
                case MinusOperator:
                  setType(unaryArgument.getType());
                  break;
                  
                default:
                  throw new ExpressionTypeCheckException("type exception");
              }
            break;

          default:
            throw new ExpressionTypeCheckException("type exception");
        }

      //
      //  tagFormat/tagMaxLength
      //

      setTagFormat(unaryArgument.getTagFormat());
      setTagMaxLength(unaryArgument.getTagMaxLength());
    }

    /*****************************************
    *
    *  assignNodeID
    *
    *****************************************/

    @Override public int assignNodeID(int preorderNumber)
    {
      setNodeID(preorderNumber);
      preorderNumber = unaryArgument.assignNodeID(preorderNumber+1);
      return preorderNumber;
    }

    /*****************************************
    *
    *  evaluate
    *
    *****************************************/

    @Override protected Object evaluate(SubscriberEvaluationRequest subscriberEvaluationRequest, TimeUnit baseTimeUnit)
    {
      /*****************************************
      *
      *  evaluate arguments
      *
      *****************************************/

      Object argumentValue = unaryArgument.evaluate(subscriberEvaluationRequest, baseTimeUnit);

      /*****************************************
      *
      *  evaluate operator
      *
      *****************************************/

      Object result = null;
      switch (type)
        {
          case IntegerExpression:
            switch (operator)
              {
                case PlusOperator:
                  result = argumentValue;
                  break;

                case MinusOperator:
                  result = new Long(-1L * ((Number) argumentValue).longValue());
                  break;
              }
            break;

          case DoubleExpression:
            switch (operator)
              {
                case PlusOperator:
                  result = argumentValue;
                  break;

                case MinusOperator:
                  result = new Double(-1.0 * ((Double) argumentValue).doubleValue());
                  break;
              }
            break;
        }

      /*****************************************
      *
      *  return
      *
      *****************************************/

      return result;
    }

    /*****************************************
    *
    *  esQuery
    *
    *****************************************/

    @Override public void esQuery(StringBuilder script, TimeUnit baseTimeUnit, int tenantID) throws CriterionException
    {
      /*****************************************
      *
      *  script
      *
      *****************************************/

      //
      //  argument
      //
      
      unaryArgument.esQuery(script, baseTimeUnit, tenantID);
      
      //
      //  operator
      //
      
      switch (type)
        {
          case IntegerExpression:
            switch (operator)
              {
                case PlusOperator:
                  script.append("def right_" + getNodeID() + " = right_" + unaryArgument.getNodeID() + "; ");
                  break;

                case MinusOperator:
                  script.append("def right_" + getNodeID() + " = -1 * right_" + unaryArgument.getNodeID() + "; ");
                  break;
              }
            break;

          case DoubleExpression:
            switch (operator)
              {
                case PlusOperator:
                  script.append("def right_" + getNodeID() + " = right_" + unaryArgument.getNodeID() + "; ");
                  break;

                case MinusOperator:
                  script.append("def right_" + getNodeID() + " = -1.0 * right_" + unaryArgument.getNodeID() + "; ");
                  break;
              }
            break;
        }
    }

    /*****************************************
     *
     *  esQueryNoPainless
     *
     *****************************************/

    @Override public Object esQueryNoPainless() throws CriterionException
    {
      throw new CriterionException(this.getClass().getSimpleName()+"cannot be processed into an no painless query");
    }

    /*****************************************
    *
    *  constructor
    *
    *****************************************/

    public UnaryExpression(ExpressionOperator operator, Expression unaryArgument, int tenantID)
    {
      super(tenantID);
      this.operator = operator;
      this.unaryArgument = unaryArgument;
    }
  }

  /*****************************************
  *
  *  class FunctionCallExpression
  *
  *****************************************/

  public static class FunctionCallExpression extends Expression
  {
    /*****************************************
    *
    *  data
    *
    *****************************************/

    private ExpressionFunction function;
    private List<Expression> arguments;

    //
    //  preevaluatedResult
    //

    private Object preevaluatedResult = null;

    /*****************************************
    *
    *  typeCheck
    *
    *****************************************/

    @Override public void typeCheck(ExpressionContext expressionContext, TimeUnit baseTimeUnit, int tenantID)
    {
      /*****************************************
      *
      *  typeCheck arguments
      *
      *****************************************/

      for (Expression argument : arguments)
        {
          argument.typeCheck(expressionContext, baseTimeUnit, tenantID);
        }

      /*****************************************
      *
      *  type
      *
      *****************************************/

      switch (function)
        {
          case DateConstantFunction:
            typeCheckDateConstantFunction(baseTimeUnit, tenantID);
            break;
            
          case TimeConstantFunction:
            typeCheckTimeConstantFunction(tenantID);
            break;
            
          case TimeAddFunction:
            typeCheckTimeAddFunction(baseTimeUnit, tenantID);
            break;

          case DateAddFunction:
            typeCheckDateAddFunction(baseTimeUnit, tenantID);
            break;
            
          case DateAddOrConstantFunction:
            typeCheckDateAddOrConstantFunction(baseTimeUnit, tenantID);
            break;
            
          case RoundFunction:
          case RoundUpFunction:
          case RoundDownFunction:
            typeCheckRoundFunction(function, tenantID);
            break;

          case DaysUntilFunction:
          case MonthsUntilFunction:
          case DaysSinceFunction:
          case MonthsSinceFunction:
            typeCheckUntilFunction(function, tenantID);
            break;

          case FirstWordFunction:
          case SecondWordFunction:
          case ThirdWordFunction:
            typeCheckWordFunction(function, tenantID);
            break;

          case IntFunction:
          case StringFunction:
          case DoubleFunction:
            typeCheckCastFunction(function);
            break;

          case MinFunction:
          case MaxFunction:
            typeCheckMinMaxFunctions(function, tenantID);
            break;
            
          default:
            throw new ExpressionTypeCheckException("type exception");
        }
    }

    /*****************************************
    *
    *  typeCheckDateConstantFunction
    *
    *****************************************/

    private void typeCheckDateConstantFunction(TimeUnit baseTimeUnit, int tenantID)
    {
      /****************************************
      *
      *  arguments
      *
      ****************************************/
      
      //
      //  validate number of arguments
      //
      
      if (arguments.size() != 1) throw new ExpressionTypeCheckException("type exception");

      //
      //  arguments
      //
      
      Expression arg1 = (arguments.size() > 0) ? arguments.get(0) : null;

      //
      //  validate arg1
      //
      
      switch (arg1.getType())
        {
          case StringExpression:
            if (! arg1.isConstant()) throw new ExpressionTypeCheckException("type exception");
            break;

          default:
            throw new ExpressionTypeCheckException("type exception");
        }
      
      //
      //  validate baseTimeUnit
      //

      switch (baseTimeUnit)
        {
          case Unknown:
            throw new ExpressionTypeCheckException("type exception");
        }

      /****************************************
      *
      *  constant evaluation
      *
      ****************************************/
      
      String arg1_value = (String) arg1.evaluate(null, TimeUnit.Unknown);
      try
        {
          preevaluatedResult = evaluateDateConstantFunction(arg1_value, baseTimeUnit, tenantID);
        }
      catch (ExpressionEvaluationException e)
        {
          throw new ExpressionTypeCheckException("type exception");
        }
      
      /****************************************
      *
      *  type
      *
      ****************************************/
      
      setType(ExpressionDataType.DateExpression);
    }
    
    /*****************************************
    *
    *  typeCheckTimeConstantFunction
    *
    *****************************************/

    private void typeCheckTimeConstantFunction(int tenantID)
    {
      /****************************************
      *
      *  arguments
      *
      ****************************************/
      
      //
      //  validate number of arguments
      //
      
      if (arguments.size() != 1) throw new ExpressionTypeCheckException("type exception");

      //
      //  arguments
      //
      
      Expression arg1 = (arguments.size() > 0) ? arguments.get(0) : null;

      //
      //  validate arg1
      //
      
      switch (arg1.getType())
        {
          case StringExpression:
            if (! arg1.isConstant()) throw new ExpressionTypeCheckException("type exception");
            break;

          default:
            throw new ExpressionTypeCheckException("type exception");
        }
      
      /****************************************
      *
      *  constant evaluation
      *
      ****************************************/
      
      String arg1_value = (String) arg1.evaluate(null, TimeUnit.Unknown);
      try
        {
          preevaluatedResult = evaluateTimeConstantFunction(arg1_value);
        }
      catch (ExpressionEvaluationException e)
        {
          throw new ExpressionTypeCheckException("type exception");
        }
      
      /****************************************
      *
      *  type
      *
      ****************************************/
      
      setType(ExpressionDataType.TimeExpression);
    }

    /*****************************************
    *
    *  typeCheckUntilFunction
    *
    *****************************************/

    private void typeCheckUntilFunction(ExpressionFunction function, int tenantID)
    {
      /****************************************
      *
      *  arguments
      *
      ****************************************/
      
      //
      //  validate number of arguments
      //
      
      if (arguments.size() != 1) throw new ExpressionTypeCheckException("type exception");

      //
      //  arguments
      //
      
      Expression arg1 = (arguments.size() > 0) ? arguments.get(0) : null;

      //
      //  validate arg1
      //
      
      switch (arg1.getType())
        {
          case StringExpression: // DaysUntil('2020-09-20')
          case DateExpression: // DaysUntil(var.evaluationDate)
            break;

          default:
            throw new ExpressionTypeCheckException("type exception");
        }
      
      /****************************************
      *
      *  constant evaluation
      *
      ****************************************/
      if (arg1.isConstant())
        {
          Date arg1_value = null;
          Object res = arg1.evaluate(null, TimeUnit.Unknown);
          if (res instanceof String)
            {
              // DaysUntil('2020-09-20')
              arg1_value = evaluateDateConstantFunction((String) res, TimeUnit.Unknown, tenantID);
            }
          else if (res instanceof Date)
            {
              // DaysUntil(var.evaluationDate)
              arg1_value = (Date) res;
            }
          
          try
          {
            switch (function)
            {
              case DaysUntilFunction:
              case MonthsUntilFunction:
              case DaysSinceFunction:
              case MonthsSinceFunction:
                preevaluatedResult = evaluateUntilFunction(arg1_value, function, tenantID);
                break;

              default:
                throw new ExpressionTypeCheckException("type exception");
            }

          }
          catch (ExpressionEvaluationException e)
          {
            throw new ExpressionTypeCheckException("type exception");
          }
        }

      /****************************************
      *
      *  type
      *
      ****************************************/
      
      setType(ExpressionDataType.IntegerExpression);

      /*****************************************
      *
      *  tagFormat/tagMaxLength
      *
      *****************************************/

      setTagFormat(arg1.getTagFormat());
      setTagMaxLength(arg1.getTagMaxLength());
    }

    private void typeCheckWordFunction(ExpressionFunction function, int tenantID)
    {
      /****************************************
      *
      *  arguments
      *
      ****************************************/
      
      //
      //  validate number of arguments
      //
      
      if (arguments.size() != 1) throw new ExpressionTypeCheckException("type exception");

      //
      //  arguments
      //
      
      Expression arg1 = (arguments.size() > 0) ? arguments.get(0) : null;

      //
      //  validate arg1
      //
      
      switch (arg1.getType())
        {
          case StringExpression: // firstWord('this is a sentence')
            break;

          default:
            throw new ExpressionTypeCheckException("type exception");
        }
      
      /****************************************
      *
      *  constant evaluation
      *
      ****************************************/
      if (arg1.isConstant())
        {
          String arg1_value = null;
          Object res = arg1.evaluate(null, TimeUnit.Unknown);
          if (res instanceof String)
            {
              // firstWord('this is a sentence')
              arg1_value = (String) res;
            }
          
          try
          {
            switch (function)
            {
              case FirstWordFunction:
              case SecondWordFunction:
              case ThirdWordFunction:
                preevaluatedResult = evaluateWordFunction(arg1_value, function);
                break;

              default:
                throw new ExpressionTypeCheckException("type exception");
            }

          }
          catch (ExpressionEvaluationException e)
          {
            throw new ExpressionTypeCheckException("type exception");
          }
        }

      /****************************************
      *
      *  type
      *
      ****************************************/
      
      setType(ExpressionDataType.StringExpression);

      /*****************************************
      *
      *  tagFormat/tagMaxLength
      *
      *****************************************/

      setTagFormat(arg1.getTagFormat());
      setTagMaxLength(arg1.getTagMaxLength());
    }

    private void typeCheckCastFunction(ExpressionFunction function)
    {
      /****************************************
      *
      *  arguments
      *
      ****************************************/
      
      //
      //  validate number of arguments
      //
      
      if (arguments.size() != 1) throw new ExpressionTypeCheckException("type exception");

      //
      //  arguments
      //
      
      Expression arg1 = (arguments.size() > 0) ? arguments.get(0) : null;

      //
      //  validate arg1
      //
      
      switch (function)
      {
        case IntFunction:
        case DoubleFunction:
          switch (arg1.getType())
          {
            case StringExpression: // int('12') double('12.1')
              break;

            default:
              throw new ExpressionTypeCheckException("type exception : string expected, got " + arg1.getType());
          }
          break;
          
        case StringFunction:
          switch (arg1.getType())
          {
            case IntegerExpression: // string(12)
            case DoubleExpression: // string(12.1)
              break;

            default:
              throw new ExpressionTypeCheckException("type exception : int or double expected, got " + arg1.getType());
          }
          break;
          
        default:
          throw new ExpressionTypeCheckException("type exception");
      }
            
      /****************************************
      *
      *  constant evaluation
      *
      ****************************************/
      if (arg1.isConstant()) {
        try {
          Object res = arg1.evaluate(null, TimeUnit.Unknown);
          if (res instanceof String) {
            switch (function)
            {
              case IntFunction:
                preevaluatedResult = Long.parseLong((String) res); // int('12')
                break;

              case DoubleFunction:
                preevaluatedResult = Double.parseDouble((String) res); // double('12.3')
                break;

              default:
                throw new ExpressionTypeCheckException("type exception, int or double function expected, got " + function);
            }
          } else if (res instanceof Double) { 
            switch (function)
            {
              case StringFunction:
                preevaluatedResult = Double.toString((Double) res); // string('3.14')
                break;

              default:
                throw new ExpressionTypeCheckException("type exception, string function expected, got " + function);
            }
          } else if (res instanceof Integer) {
            switch (function)
            {
              case StringFunction:
                preevaluatedResult = Integer.toString((Integer) res); // string('12')
                break;

              default:
                throw new ExpressionTypeCheckException("type exception, string function expected, got " + function);
            }
          } else if (res instanceof Long) {
            switch (function)
            {
              case StringFunction:
                preevaluatedResult = Long.toString((Long) res); // string('12')
                break;

              default:
                throw new ExpressionTypeCheckException("type exception, string function expected, got " + function);
            }
          } else throw new ExpressionTypeCheckException("type exception, int double or string expected, got " + ((res==null)?"null":res.getClass().getSimpleName()));
        } catch (NumberFormatException nfe) {
          throw new ExpressionTypeCheckException("type exception when casting : " + nfe.getLocalizedMessage());
        }
      }

      /****************************************
      *
      *  type
      *
      ****************************************/

      switch (function)
      {
        case IntFunction:
          setType(ExpressionDataType.IntegerExpression);
          break;
        case DoubleFunction:
          setType(ExpressionDataType.DoubleExpression);
          break;
        case StringFunction:
          setType(ExpressionDataType.StringExpression);
          break;
        default:
          log.info("string int or double function expected, got " + function);
      }
      
      /*****************************************
      *
      *  tagFormat/tagMaxLength
      *
      *****************************************/

      setTagFormat(arg1.getTagFormat());
      setTagMaxLength(arg1.getTagMaxLength());
    }

    private void typeCheckMinMaxFunctions(ExpressionFunction function, int tenantID)
    {
      /****************************************
      *
      *  arguments
      *
      ****************************************/
      
      //
      //  validate number of arguments (2 or 3)
      //
      
      if (arguments.size() < 2 || arguments.size() > 3) throw new ExpressionTypeCheckException("type exception : function " + function.getFunctionName() + " needs 2 or 3 arguments");

      //
      //  arguments
      //
      
      Expression arg1 = arguments.get(0);
      Expression arg2 = arguments.get(1);
      Expression arg3 = (arguments.size() > 2) ? arguments.get(2) : null;

      ExpressionDataType commonType = arg1.getType();
      if (arguments.size() == 2) {
        if (arg1.getType() != arg2.getType()) {
          throw new ExpressionTypeCheckException("type exception : both args of " + function.getFunctionName() + " need to be of same type, not " + arg1.getType() + " and " + arg2.getType());
        }
      } else if (arguments.size() == 3) {
        if (arg1.getType() != arg2.getType()) {
          throw new ExpressionTypeCheckException("type exception : all args of " + function.getFunctionName() + " need to be of same type, not " + arg1.getType() + " and " + arg2.getType());
        } else if (arg1.getType() != arg3.getType()) {
          throw new ExpressionTypeCheckException("type exception : all args of " + function.getFunctionName() + " need to be of same type, not " + arg1.getType() + " and " + arg3.getType());
        } 
      }
      
      // set default values for optional parameters
      if (arg3 == null) {
        Object defaultValue = null;
        switch (commonType)
        {
          case IntegerExpression:
            defaultValue = (function == ExpressionFunction.MinFunction) ? Integer.MAX_VALUE : Integer.MIN_VALUE;
            break;
          case DoubleExpression:
            defaultValue = (function == ExpressionFunction.MinFunction) ? Double.MAX_VALUE : Double.MIN_VALUE;
            break;

          default:
            throw new ExpressionTypeCheckException("type exception : int or double expected, got " + arg1.getType());
        }
        arg3 = new ConstantExpression(commonType, defaultValue, tenantID);
      }
                  
      /****************************************
      *
      *  constant evaluation
      *
      ****************************************/
      if (arg1.isConstant() && arg2.isConstant() && arg3.isConstant()) {
        Object res1 = arg1.evaluate(null, TimeUnit.Unknown);
        Object res2 = arg2.evaluate(null, TimeUnit.Unknown);
        Object res3 = arg3.evaluate(null, TimeUnit.Unknown);
        if (res1 instanceof Double) { 
          switch (function)
          {
            case MinFunction:
              preevaluatedResult = Math.min((Double) res1, Math.min((Double) res2, (Double) res3));
              break;
            case MaxFunction:
              preevaluatedResult = Math.max((Double) res1, Math.max((Double) res2, (Double) res3));
              break;

            default:
              throw new ExpressionTypeCheckException("type exception, min/max function expected, got " + function);
          }
        } else if (res1 instanceof Integer) {
          switch (function)
          {
            case MinFunction:
              preevaluatedResult = Math.min((Integer) res1, Math.min((Integer) res2, (Integer) res3));
              break;
            case MaxFunction:
              preevaluatedResult = Math.max((Integer) res1, Math.max((Integer) res2, (Integer) res3));
              break;

            default:
              throw new ExpressionTypeCheckException("type exception, min/max function expected, got " + function);
          }
        } else if (res1 instanceof Long) {
          switch (function)
          {
            // res3 might be Integer due to default values
            case MinFunction:
              preevaluatedResult = Math.min((Long) res1, Math.min((Long) res2, (res3 instanceof Long)?(Long) res3:(Integer) res3));
              break;
            case MaxFunction:
              preevaluatedResult = Math.max((Long) res1, Math.max((Long) res2, (res3 instanceof Long)?(Long) res3:(Integer) res3));
              break;

            default:
              throw new ExpressionTypeCheckException("type exception, min/max function expected, got " + function);
          }
        } else throw new ExpressionTypeCheckException("type exception, int or double expected, got " + ((res1==null)?"null":res1.getClass().getSimpleName()));
      }

      /****************************************
      *
      *  type
      *
      ****************************************/

      switch (commonType)
      {
        case IntegerExpression:
          setType(ExpressionDataType.IntegerExpression);
          break;
        case DoubleExpression:
          setType(ExpressionDataType.DoubleExpression);
          break;
        default:
          log.info("string int or double type expected, got " + commonType);
      }
      
      /*****************************************
      *
      *  tagFormat/tagMaxLength
      *
      *****************************************/

      setTagFormat(arg1.getTagFormat());
      setTagMaxLength(arg1.getTagMaxLength());
    }

    /*****************************************
    *
    *  typeCheckRoundFunction
    *
    *****************************************/

    private void typeCheckRoundFunction(ExpressionFunction function, int tenantID)
    {
      /****************************************
      *
      *  arguments
      *
      ****************************************/
      
      //
      //  validate number of arguments
      //
      
      if (arguments.size() != 1) throw new ExpressionTypeCheckException("type exception");

      //
      //  arguments
      //
      
      Expression arg1 = (arguments.size() > 0) ? arguments.get(0) : null;

      //
      //  validate arg1
      //
      
      switch (arg1.getType())
        {
          case DoubleExpression:
          case IntegerExpression:
            break;

          default:
            throw new ExpressionTypeCheckException("type exception");
        }

      /****************************************
      *
      *  evaluation
      *
      ****************************************/
      if (arg1.isConstant())
        {
          try
          {
            Double arg1_value = (Double) arg1.evaluate(null, TimeUnit.Unknown);
            preevaluatedResult = evaluateRoundFunction(arg1_value, function);
          }
          catch (ExpressionEvaluationException | ClassCastException e)
          {
            throw new ExpressionTypeCheckException("type exception");
          }
        }
      
      /****************************************
      *
      *  type
      *
      ****************************************/
      
      setType(ExpressionDataType.IntegerExpression);
    }

    /*****************************************
    *
    *  typeCheckDateAddFunction
    *
    *****************************************/

    private void typeCheckDateAddFunction(TimeUnit baseTimeUnit, int tenantID)
    {
      /****************************************
      *
      *  arguments
      *
      ****************************************/
      
      //
      //  validate number of arguments
      //
      
      if (arguments.size() != 3) throw new ExpressionTypeCheckException("type exception");

      //
      //  arguments
      //
      
      Expression arg1 = (arguments.size() > 0) ? arguments.get(0) : null;
      Expression arg2 = (arguments.size() > 1) ? arguments.get(1) : null;
      Expression arg3 = (arguments.size() > 2) ? arguments.get(2) : null;

      //
      //  validate arg1
      //
      
      switch (arg1.getType())
        {
          case DateExpression:
            break;

          default:
            throw new ExpressionTypeCheckException("type exception");
        }

      //
      //  validate arg2
      //
      
      switch (arg2.getType())
        {
          case IntegerExpression:
            break;

          default:
            throw new ExpressionTypeCheckException("type exception");
        }

      //
      //  validate arg3
      //
      
      switch (arg3.getType())
        {
          case StringExpression:
            break;

          default:
            throw new ExpressionTypeCheckException("type exception");
        }

      //
      //  validate baseTimeUnit
      //

      switch (baseTimeUnit)
        {
          case Unknown:
            throw new ExpressionTypeCheckException("type exception");
        }
      
      /****************************************
      *
      *  constant evaluation
      *
      ****************************************/

      if (arg3.isConstant())
        {
          String arg3Value = (String) arg3.evaluate(null, TimeUnit.Unknown);
          switch (TimeUnit.fromExternalRepresentation(arg3Value))
            {
              case Instant:
              case Unknown:
                throw new ExpressionTypeCheckException("type exception");
            }
        }

      /****************************************
      *
      *  type
      *
      ****************************************/
      
      setType(ExpressionDataType.DateExpression);

      /*****************************************
      *
      *  tagFormat/tagMaxLength
      *
      *****************************************/

      setTagFormat(arg1.getTagFormat());
      setTagMaxLength(arg1.getTagMaxLength());
    }
    
    /*****************************************
    *
    *  typeCheckTimeAddFunction
    *
    *****************************************/

    private void typeCheckTimeAddFunction(TimeUnit baseTimeUnit, int tenantID)
    {
      /****************************************
      *
      *  arguments
      *
      ****************************************/
      
      //
      //  validate number of arguments
      //
      
      if (arguments.size() != 3) throw new ExpressionTypeCheckException("type exception");

      //
      //  arguments
      //
      
      Expression arg1 = (arguments.size() > 0) ? arguments.get(0) : null;
      Expression arg2 = (arguments.size() > 1) ? arguments.get(1) : null;
      Expression arg3 = (arguments.size() > 2) ? arguments.get(2) : null;
      
      //
      //  validate arg1
      //
      
      switch (arg1.getType())
        {
          case TimeExpression:
            break;

          default:
            throw new ExpressionTypeCheckException("type exception");
        }

      //
      //  validate arg2
      //
      
      switch (arg2.getType())
        {
          case IntegerExpression:
            break;

          default:
            throw new ExpressionTypeCheckException("type exception");
        }

      //
      //  validate arg3
      //
      
      switch (arg3.getType())
        {
          case StringExpression:
            break;

          default:
            throw new ExpressionTypeCheckException("type exception");
        }

      //
      //  validate baseTimeUnit
      //

      switch (baseTimeUnit)
        {
          case Unknown:
            throw new ExpressionTypeCheckException("type exception");
        }
      
      /****************************************
      *
      *  constant evaluation
      *
      ****************************************/

      if (arg3.isConstant())
        {
          String arg3Value = (String) arg3.evaluate(null, TimeUnit.Unknown);
          switch (TimeUnit.fromExternalRepresentation(arg3Value))
            {
              case Instant:
              case Unknown:
                throw new ExpressionTypeCheckException("type exception");
            }
        }

      /****************************************
      *
      *  type
      *
      ****************************************/
      
      setType(ExpressionDataType.TimeExpression);

      /*****************************************
      *
      *  tagFormat/tagMaxLength
      *
      *****************************************/

      setTagFormat(arg1.getTagFormat());
      setTagMaxLength(arg1.getTagMaxLength());
    }
    
    //
    //  typeCheckDateAddOrConstantFunction
    //
    
    
    private void typeCheckDateAddOrConstantFunction(TimeUnit baseTimeUnit, int tenantID)
    {
      /****************************************
      *
      *  arguments
      *
      ****************************************/
      
      //
      //  validate number of arguments
      //
      
      if (arguments.size() != 6 && arguments.size() != 4 /*Because of migration due to EVPRO-432: this second condition (!=4) to be removed lated when all customer have a version >= 1.4.5_1*/) throw new ExpressionTypeCheckException("type exception");

      //
      //  arguments
      //
      
      Expression arg1 = (arguments.size() > 0) ? arguments.get(0) : null;
      Expression arg2 = (arguments.size() > 1) ? arguments.get(1) : null;
      Expression arg3 = (arguments.size() > 2) ? arguments.get(2) : null;
      Expression arg4 = (arguments.size() > 3) ? arguments.get(3) : null;
      Expression arg5 = (arguments.size() > 4) ? arguments.get(4) : null;
      Expression arg6 = (arguments.size() > 5) ? arguments.get(5) : null;

      //
      //  validate arg1
      //
      
      switch (arg1.getType())
        {
          case DateExpression:
            break;

          default:
            throw new ExpressionTypeCheckException("type exception");
        }
      
      //
      //  validate arg2
      //
      
      switch (arg2.getType())
        {
          case DateExpression:
            break;

          default:
            throw new ExpressionTypeCheckException("type exception");
        }

      //
      //  validate arg3
      //
      
      switch (arg3.getType())
        {
          case IntegerExpression:
            break;

          default:
            throw new ExpressionTypeCheckException("type exception");
        }

      //
      //  validate arg4
      //
      
      switch (arg4.getType())
        {
          case StringExpression:
            break;

          default:
            throw new ExpressionTypeCheckException("type exception");
        }
      
      //
      //  validate arg5
      //
      if(arg5 != null) /*this if because of migration due to EVPRO-432: this second conditon to be removed lated when all customer have a version >= 1.4.5_1*/
        {
          switch (arg5.getType())
            {
              case StringExpression:
                break;
    
              default:
                throw new ExpressionTypeCheckException("type exception");
            }
        }
      
      //
      //  validate arg6
      //
      if(arg6 != null) /*this if because of migration due to EVPRO-432: this second conditon to be removed lated when all customer have a version >= 1.4.5_1*/
        {
        switch (arg6.getType())
          {
            case TimeExpression:
              break;
  
            default:
              throw new ExpressionTypeCheckException("type exception");
          }
        }
      
      //
      //  validate baseTimeUnit
      //

      switch (baseTimeUnit)
        {
          case Unknown:
            throw new ExpressionTypeCheckException("type exception");
        }
      
      /****************************************
      *
      *  constant evaluation
      *
      ****************************************/

      if (arg4.isConstant())
        {
          String arg4Value = (String) arg4.evaluate(null, TimeUnit.Unknown);
          switch (TimeUnit.fromExternalRepresentation(arg4Value))
          {
            case Instant:
            case Unknown:
              throw new ExpressionTypeCheckException("type exception");
          }
        }

      /****************************************
      *
      *  type
      *
      ****************************************/
      
      setType(ExpressionDataType.DateExpression);

      /*****************************************
      *
      *  tagFormat/tagMaxLength
      *
      *****************************************/

      setTagFormat(arg1.getTagFormat());
      setTagMaxLength(arg1.getTagMaxLength());
    }

    /*****************************************
    *
    *  assignNodeID
    *
    *****************************************/

    @Override public int assignNodeID(int preorderNumber)
    {
      setNodeID(preorderNumber);
      for (Expression argument : arguments)
        {
          preorderNumber = argument.assignNodeID(preorderNumber+1);
        }
      return preorderNumber;
    }

    /*****************************************
    *
    *  evaluate
    *
    *****************************************/

    @Override protected Object evaluate(SubscriberEvaluationRequest subscriberEvaluationRequest, TimeUnit baseTimeUnit)
    {
      Object result = null;
      boolean expressionNullExceptionOccoured = false;
      ExpressionNullException expressionNullException = null;
      
      /*****************************************
      *
      *  evaluate arguments
      *
      *****************************************/
      
      Object arg1Value = null;
      Object arg2Value = null;
      Object arg3Value = null;
      Object arg4Value = null;
      Object arg5Value = null;
      Object arg6Value = null;
      
      try
        {
          arg1Value = (arguments.size() > 0) ? arguments.get(0).evaluate(subscriberEvaluationRequest, baseTimeUnit) : null;
        } 
      catch (ExpressionNullException e)
        {
          expressionNullExceptionOccoured = true;
          expressionNullException = e;
        }
      try
        {
          arg2Value = (arguments.size() > 1) ? arguments.get(1).evaluate(subscriberEvaluationRequest, baseTimeUnit) : null;
        } 
      catch (ExpressionNullException e)
        {
          expressionNullExceptionOccoured = true;
          expressionNullException = e;
        }
      try
        {
          arg3Value = (arguments.size() > 2) ? arguments.get(2).evaluate(subscriberEvaluationRequest, baseTimeUnit) : null;
        } 
      catch (ExpressionNullException e)
        {
          expressionNullExceptionOccoured = true;
          expressionNullException = e;
        }
      try
        {
          arg4Value = (arguments.size() > 3) ? arguments.get(3).evaluate(subscriberEvaluationRequest, baseTimeUnit) : null;
        } 
      catch (ExpressionNullException e)
        {
          expressionNullExceptionOccoured = true;
          expressionNullException = e;
        }
      try
      {
        arg5Value = (arguments.size() > 4) ? arguments.get(4).evaluate(subscriberEvaluationRequest, baseTimeUnit) : null;
      } 
    catch (ExpressionNullException e)
      {
        expressionNullExceptionOccoured = true;
        expressionNullException = e;
      }
      try
      {
        arg6Value = (arguments.size() > 5) ? arguments.get(5).evaluate(subscriberEvaluationRequest, baseTimeUnit) : null;
      } 
    catch (ExpressionNullException e)
      {
        expressionNullExceptionOccoured = true;
        expressionNullException = e;
      }

      /*****************************************
      *
      *  evaluate operator
      *
      *****************************************/

      switch (function)
        {
          case DateConstantFunction:
            result = preevaluatedResult;
            break;
            
          case TimeConstantFunction:
            if (expressionNullExceptionOccoured) throw expressionNullException;
            result = evaluateTimeConstantFunction((String) arg1Value);
            break;
            
          case TimeAddFunction:
            if (expressionNullExceptionOccoured) throw expressionNullException;
            result = evaluateTimeAddFunction((String) arg1Value, (Number) arg2Value, TimeUnit.fromExternalRepresentation((String) arg3Value), baseTimeUnit, false);
            break;
            
          case DateAddFunction:
            // TODO : don't do roundDown for now, not sure why we could need this
            if (expressionNullExceptionOccoured) throw expressionNullException;
            result = evaluateDateAddFunction((Date) arg1Value, (Number) arg2Value, TimeUnit.fromExternalRepresentation((String) arg3Value), baseTimeUnit, false, subscriberEvaluationRequest.getTenantID());
            break;
            
          case DateAddOrConstantFunction:
            result = evaluateDateAddOrConstantFunction((Date) arg1Value, (Date) arg2Value, (Number) arg3Value, TimeUnit.fromExternalRepresentation((String) arg4Value), (String) arg5Value, (String) arg6Value, baseTimeUnit, false, subscriberEvaluationRequest.getTenantID());
            break;
            
          case RoundFunction:
          case RoundUpFunction:
          case RoundDownFunction:
            if (expressionNullExceptionOccoured) throw expressionNullException;
            Double argDouble;
            if (arg1Value instanceof Long) 
              {
                // We accept int parameters to RoundXXX() as a convenience
                argDouble = ((Long) arg1Value).doubleValue();
              }
            else
              {
                try
                {
                  argDouble = (Double) arg1Value;
                }
                catch (ClassCastException ex)
                {
                  log.info("Issue when converting parameter of " + function.getFunctionName() + " to Double, actual class is " + arg1Value.getClass().getName());
                  argDouble = 0.0;
                }
              }
            result = evaluateRoundFunction(argDouble, function);
            break;
            
          case DaysUntilFunction:
          case MonthsUntilFunction:
          case DaysSinceFunction:
          case MonthsSinceFunction:
            if (expressionNullExceptionOccoured) throw expressionNullException;
            if (arg1Value instanceof String) // If param is a String, then it is a constant, otherwise it is a Date (to be evaluated)
              {
                result = preevaluatedResult;  // DaysUntil('2020-09-20')
              }
            else
              {
                result = evaluateUntilFunction((Date) arg1Value, function, subscriberEvaluationRequest.getTenantID());    
              }
            break;

          case FirstWordFunction:
          case SecondWordFunction:
          case ThirdWordFunction:
            if (expressionNullExceptionOccoured) throw expressionNullException;
            result = evaluateWordFunction((String) arg1Value, function);
            break;

          case IntFunction:
          case StringFunction:
          case DoubleFunction:
            if (expressionNullExceptionOccoured) throw expressionNullException;
            result = evaluateCastFunction(arg1Value, function);
            break;

          case MinFunction:
          case MaxFunction:
            if (expressionNullExceptionOccoured) throw expressionNullException;
            result = evaluateMinMaxFunction(arg1Value, arg2Value, arg3Value, function);
            break;
            
          default:
            throw new ExpressionEvaluationException();
        }
      
      /*****************************************
      *
      *  return
      *
      *****************************************/

      return result;
    }

    /*****************************************
    *
    *  evaluateRoundFunction
    *
    *****************************************/

    private int evaluateRoundFunction(Double arg, ExpressionFunction function)
    {
      /*****************************************
      *
      *  parse argument
      *
      *****************************************/

      int res;
      switch (function)
      {
        case RoundFunction:
          res = (int) Math.round(arg);
          break;
          
        case RoundUpFunction:
          res = (int) Math.ceil(arg);
          break;
          
        case RoundDownFunction:
          res = (int) Math.floor(arg);
          break;
          
        default:
          throw new ExpressionEvaluationException();
            
      }
      /*****************************************
      *
      *  return
      *
      *****************************************/

      return res;
    }

    /*****************************************
    *
    *  evaluateDateConstantFunction
    *
    *****************************************/

    private Date evaluateDateConstantFunction(String arg, TimeUnit baseTimeUnit, int tenantID)
    {
      /*****************************************
      *
      *  parse argument
      *
      *****************************************/

      DateFormat standardDayFormat = new SimpleDateFormat("yyyy-MM-dd");   // TODO EVPRO-99
      DateFormat standardDateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss");   // TODO EVPRO-99
      standardDayFormat.setTimeZone(TimeZone.getTimeZone(Deployment.getDeployment(tenantID).getTimeZone()));
      standardDateFormat.setTimeZone(TimeZone.getTimeZone(Deployment.getDeployment(tenantID).getTimeZone()));
      Date date = null;
      if (date == null) try { date = standardDateFormat.parse(arg.trim()); } catch (ParseException e) { }
      if (date == null) try { date = standardDayFormat.parse(arg.trim()); } catch (ParseException e) { }
      if (date == null) throw new ExpressionEvaluationException();

      /*****************************************
      *
      *  truncate (to baseTimeUnit)
      *
      *****************************************/

      switch (baseTimeUnit)
        {
          case Instant:
            break;
          case Minute:
            date = RLMDateUtils.truncate(date, Calendar.MINUTE, Deployment.getDeployment(tenantID).getTimeZone());
            break;
          case Hour:
            date = RLMDateUtils.truncate(date, Calendar.HOUR, Deployment.getDeployment(tenantID).getTimeZone());
            break;
          case Day:
            date = RLMDateUtils.truncate(date, Calendar.DATE, Deployment.getDeployment(tenantID).getTimeZone());
            break;
          case Week:
            date = RLMDateUtils.truncate(date, Calendar.DAY_OF_WEEK, Deployment.getDeployment(tenantID).getTimeZone());
            break;
          case Month:
            date = RLMDateUtils.truncate(date, Calendar.MONTH, Deployment.getDeployment(tenantID).getTimeZone());
            break;
          case Year:
            date = RLMDateUtils.truncate(date, Calendar.YEAR, Deployment.getDeployment(tenantID).getTimeZone());
            break;
        }
      
      /*****************************************
      *
      *  return
      *
      *****************************************/

      return date;
    }
    
    /*****************************************
    *
    *  evaluateTimeConstantFunction
    *
    *****************************************/

    private String evaluateTimeConstantFunction(String arg)
    {
      /*****************************************
      *
      *  parse argument
      *
      *****************************************/
      
      String[] args = arg.trim().split(":");
      if (args.length != 3) 
        {
          log.error("invalid expression argument for timeConstant, found " + arg + " expected in HH:mm:ss"); 
          throw new ExpressionEvaluationException();
        }

      /*****************************************
      *
      *  return
      *
      *****************************************/

      return arg;
    }

    /*****************************************
    *
    *  evaluateDateAddFunction
    *
    *****************************************/

    private Date evaluateDateAddFunction(Date date, Number number, TimeUnit timeUnit, TimeUnit baseTimeUnit, boolean roundDown, int tenantID)
    {
      //
      //  truncate
      //

      if (roundDown)
        {
          switch (baseTimeUnit)
          {
            case Instant:
              break;
            case Minute:
              date = RLMDateUtils.truncate(date, Calendar.MINUTE, Deployment.getDeployment(tenantID).getTimeZone());
              break;
            case Hour:
              date = RLMDateUtils.truncate(date, Calendar.HOUR, Deployment.getDeployment(tenantID).getTimeZone());
              break;
            case Day:
              date = RLMDateUtils.truncate(date, Calendar.DATE, Deployment.getDeployment(tenantID).getTimeZone());
              break;
            case Week:
              date = RLMDateUtils.truncate(date, Calendar.DAY_OF_WEEK, Deployment.getDeployment(tenantID).getTimeZone());
              break;
            case Month:
              date = RLMDateUtils.truncate(date, Calendar.MONTH, Deployment.getDeployment(tenantID).getTimeZone());
              break;
            case Year:
              date = RLMDateUtils.truncate(date, Calendar.YEAR, Deployment.getDeployment(tenantID).getTimeZone());
              break;
          }
        }
      
      //
      //  add time interval
      //

      switch (timeUnit)
        {
          case Minute:
            date = RLMDateUtils.addMinutes(date, number.intValue());
            break;
          case Hour:
            date = RLMDateUtils.addHours(date, number.intValue());
            break;
          case Day:
            date = RLMDateUtils.addDays(date, number.intValue(), Deployment.getDeployment(tenantID).getTimeZone());
            break;
          case Week:
            date = RLMDateUtils.addWeeks(date, number.intValue(), Deployment.getDeployment(tenantID).getTimeZone());
            break;
          case Month:
            date = RLMDateUtils.addMonths(date, number.intValue(), Deployment.getDeployment(tenantID).getTimeZone());
            break;
          case Year:
            date = RLMDateUtils.addYears(date, number.intValue(), Deployment.getDeployment(tenantID).getTimeZone());
            break;
        }
      
      //
      //  truncate (after adding)
      //
      if (roundDown)
        {
          switch (baseTimeUnit)
          {
            case Instant:
              break;
            case Minute:
              date = RLMDateUtils.truncate(date, Calendar.MINUTE, Deployment.getDeployment(tenantID).getTimeZone());
              break;
            case Hour:
              date = RLMDateUtils.truncate(date, Calendar.HOUR, Deployment.getDeployment(tenantID).getTimeZone());
              break;
            case Day:
              date = RLMDateUtils.truncate(date, Calendar.DATE, Deployment.getDeployment(tenantID).getTimeZone());
              break;
            case Week:
              date = RLMDateUtils.truncate(date, Calendar.DAY_OF_WEEK, Deployment.getDeployment(tenantID).getTimeZone());
              break;
            case Month:
              date = RLMDateUtils.truncate(date, Calendar.MONTH, Deployment.getDeployment(tenantID).getTimeZone());
              break;
            case Year:
              date = RLMDateUtils.truncate(date, Calendar.YEAR, Deployment.getDeployment(tenantID).getTimeZone());
              break;
          }
        }

      //
      //  return
      //
      
      return date;
    }
    
    //
    //  evaluateTimeAddFunction
    //
    
    private String evaluateTimeAddFunction(String time, Number number, TimeUnit timeUnit, TimeUnit baseTimeUnit, boolean roundDown)
    {
      String[] args = time.trim().split(":");
      if (args.length != 3) throw new ExpressionEvaluationException();
      int hh = Integer.parseInt(args[0]);
      int mm = Integer.parseInt(args[1]);
      int ss = Integer.parseInt(args[2]);
      
      switch (timeUnit)
      {
        case Hour:
          hh = hh + number.intValue();
          break;
          
        case Minute:
          mm = mm + number.intValue();
          break;
          
        case Second:
          ss = ss + number.intValue();
          break;
          
        default:
          throw new ExpressionEvaluationException();
      }
      StringBuilder timeBuilder = new StringBuilder();
      timeBuilder.append(hh).append(":").append(mm).append(":").append(ss);
      return timeBuilder.toString(); 
    }
    
    
    //
    //  evaluateDateAddOrConstantFunction
    //
    
    private Date evaluateDateAddOrConstantFunction(Date dateAddDate, Date strictScheduleDate, Number waitDuration, TimeUnit timeUnit, String dayOfWeek, String waitTimeString, TimeUnit baseTimeUnit, boolean roundDown, int tenantID)
    {
      Date result = new Date(0L);
      List<Date> watingDates = new ArrayList<Date>();
      
      //
      // wait for Duration
      //
      
      if (waitDuration != null && timeUnit != TimeUnit.Unknown)
        {
          Date dateAfterWait = dateAddDate;
          dateAfterWait = evaluateDateAddFunction(dateAfterWait, waitDuration, timeUnit, baseTimeUnit, roundDown, tenantID);
          watingDates.add(dateAfterWait);
        }
      
      //
      // wait for strictScheduleDate
      //
      
      if (strictScheduleDate != null) watingDates.add(strictScheduleDate);
      
      //
      // wait for day
      //
      
      if (dayOfWeek != null)
        {
          Date nextDayDate = null;
          switch (dayOfWeek.toUpperCase())
          {
            case "SUNDAY":
              nextDayDate = getNextDayDate(SystemTime.getCurrentTime(), Calendar.SUNDAY, tenantID);
              break;
              
            case "MONDAY":
              nextDayDate = getNextDayDate(SystemTime.getCurrentTime(), Calendar.MONDAY, tenantID);
              break;
              
            case "TUESDAY":
              nextDayDate = getNextDayDate(SystemTime.getCurrentTime(), Calendar.TUESDAY, tenantID);
              break;
              
            case "WEDNESDAY":
              nextDayDate = getNextDayDate(SystemTime.getCurrentTime(), Calendar.WEDNESDAY, tenantID);
              break;
              
            case "THURSDAY":
              nextDayDate = getNextDayDate(SystemTime.getCurrentTime(), Calendar.THURSDAY, tenantID);
              break;
              
            case "FRIDAY":
              nextDayDate = getNextDayDate(SystemTime.getCurrentTime(), Calendar.FRIDAY, tenantID);
              break;
              
            case "SATURDAY":
              nextDayDate = getNextDayDate(SystemTime.getCurrentTime(), Calendar.SATURDAY, tenantID);
              break;

            default:
              break;
          }
          if (nextDayDate != null)
            {
              if (waitTimeString == null) waitTimeString = "00:00:00";
              String[] args = waitTimeString.trim().split(":");
              if (args.length != 3) throw new ExpressionEvaluationException();
              int hh = Integer.parseInt(args[0]);
              int mm = Integer.parseInt(args[1]);
              int ss = Integer.parseInt(args[2]);
              if (RLMDateUtils.truncatedEquals(nextDayDate, dateAddDate, Calendar.DATE, Deployment.getDeployment(tenantID).getTimeZone()))
                {
                  //
                  //  expected exit time of nextDayDate
                  //
                  
                  nextDayDate = RLMDateUtils.setField(nextDayDate, Calendar.HOUR_OF_DAY, hh, Deployment.getDeployment(tenantID).getTimeZone());
                  nextDayDate = RLMDateUtils.setField(nextDayDate, Calendar.MINUTE, mm, Deployment.getDeployment(tenantID).getTimeZone());
                  nextDayDate = RLMDateUtils.setField(nextDayDate, Calendar.SECOND, ss, Deployment.getDeployment(tenantID).getTimeZone());
                  
                  if (nextDayDate.before(dateAddDate))
                    {
                      //
                      //  go to next day
                      //
                      
                      nextDayDate = RLMDateUtils.addDays(nextDayDate, 7, Deployment.getDeployment(tenantID).getTimeZone());
                    }
                  watingDates.add(nextDayDate);
                }
              else
                {
                  //
                  //  next day 
                  //
                  
                  Date expectedDate = nextDayDate;
                  expectedDate = RLMDateUtils.setField(expectedDate, Calendar.HOUR_OF_DAY, hh, Deployment.getDeployment(tenantID).getTimeZone());
                  expectedDate = RLMDateUtils.setField(expectedDate, Calendar.MINUTE, mm, Deployment.getDeployment(tenantID).getTimeZone());
                  expectedDate = RLMDateUtils.setField(expectedDate, Calendar.SECOND, ss, Deployment.getDeployment(tenantID).getTimeZone());
                  watingDates.add(expectedDate);
                }
            }
        }
      else if (waitTimeString != null)
        {
          String[] args = waitTimeString.trim().split(":");
          int hh = Integer.parseInt(args[0]);
          int mm = Integer.parseInt(args[1]);
          int ss = Integer.parseInt(args[2]);
          Date now = SystemTime.getCurrentTime();
          if (RLMDateUtils.truncatedEquals(now, dateAddDate, Calendar.DATE, Deployment.getDeployment(tenantID).getTimeZone()))
            {
              //
              //  expected exit time of today
              //
              
              now = RLMDateUtils.setField(now, Calendar.HOUR_OF_DAY, hh, Deployment.getDeployment(tenantID).getTimeZone());
              now = RLMDateUtils.setField(now, Calendar.MINUTE, mm, Deployment.getDeployment(tenantID).getTimeZone());
              now = RLMDateUtils.setField(now, Calendar.SECOND, ss, Deployment.getDeployment(tenantID).getTimeZone());
              
              if (now.before(dateAddDate))
                {
                  //
                  //  go to next day
                  //
                  
                  now = RLMDateUtils.addDays(now, 1, Deployment.getDeployment(tenantID).getTimeZone());
                }
              watingDates.add(now);
            }
          else
            {
              //
              //  tomorrow 
              //
              
              Date expectedDate = now;
              expectedDate = RLMDateUtils.setField(expectedDate, Calendar.HOUR_OF_DAY, hh, Deployment.getDeployment(tenantID).getTimeZone());
              expectedDate = RLMDateUtils.setField(expectedDate, Calendar.MINUTE, mm, Deployment.getDeployment(tenantID).getTimeZone());
              expectedDate = RLMDateUtils.setField(expectedDate, Calendar.SECOND, ss, Deployment.getDeployment(tenantID).getTimeZone());
              watingDates.add(expectedDate);
            }
        }
      
      //
      //  sort to get the earliest date
      //
      
      if (watingDates.size() > 0)
        {
          Collections.sort(watingDates);
          result = watingDates.get(0);
        }
      
      //
      //  return
      //
      
      if(log.isDebugEnabled()) log.debug("evaluateDateAddOrConstantFunction returning to wait till {}", result);
      return result;
    }

    private Date getNextDayDate(Date now, int dayOfWeek, int tenantID)
    {
      Date tempDate = now;
      if (dayOfWeek == RLMDateUtils.getField(now, Calendar.DAY_OF_WEEK, Deployment.getDeployment(tenantID).getTimeZone())) 
        {
          return now;
        }
      else if(dayOfWeek < RLMDateUtils.getField(now, Calendar.DAY_OF_WEEK, Deployment.getDeployment(tenantID).getTimeZone()))
        {
          tempDate = RLMDateUtils.setField(now, Calendar.DAY_OF_WEEK, dayOfWeek, Deployment.getDeployment(tenantID).getTimeZone());
          tempDate = RLMDateUtils.addDays(tempDate, 7, Deployment.getDeployment(tenantID).getTimeZone());
        }
      else 
        {
          tempDate = RLMDateUtils.setField(now, Calendar.DAY_OF_WEEK, dayOfWeek, Deployment.getDeployment(tenantID).getTimeZone());
        }
      return tempDate;
    }

    /*****************************************
    *
    *  evaluateUntilFunction
    *
    *****************************************/

    private long evaluateUntilFunction(Date date, ExpressionFunction function, int tenantID)
    {
      long res;
      Date now = SystemTime.getCurrentTime();
      switch (function)
      {
        case DaysUntilFunction:
          // RLMDateUtils.daysBetween() is always >=0
          if (now.before(date))
            res = RLMDateUtils.daysBetween(now, date, Deployment.getDeployment(tenantID).getTimeZone());
          else
            res = -RLMDateUtils.daysBetween(date, now, Deployment.getDeployment(tenantID).getTimeZone());
          break;
        case MonthsUntilFunction:
          if (now.before(date))
            res = RLMDateUtils.monthsBetween(now, date, Deployment.getDeployment(tenantID).getTimeZone());
          else
            res = -RLMDateUtils.monthsBetween(date, now, Deployment.getDeployment(tenantID).getTimeZone());
          break;
        case DaysSinceFunction:
          if (date.before(now))
            res = RLMDateUtils.daysBetween(date, now, Deployment.getDeployment(tenantID).getTimeZone());
          else
            res = -RLMDateUtils.daysBetween(now, date, Deployment.getDeployment(tenantID).getTimeZone());
          break;
        case MonthsSinceFunction:
          if (date.before(now))
            res = RLMDateUtils.monthsBetween(date, now, Deployment.getDeployment(tenantID).getTimeZone());
          else
            res = -RLMDateUtils.monthsBetween(now, date, Deployment.getDeployment(tenantID).getTimeZone());
          break;
        default:
          throw new ExpressionEvaluationException();
      }
      return res;
    }

    private String evaluateWordFunction(String phrase, ExpressionFunction function)
    {
      String res = "";
      String words[] = phrase.trim().split("\\s+"); // A whitespace character: [ \t\n\x0B\f\r]
      switch (function)
      {
        case FirstWordFunction:
          if (words.length < 1) {
            log.info("Not enough words in " + phrase + " for " + function.getFunctionName());
            res = ""; // EVPRO-880 return empty string if not enough words in argument
          } else
            res = words[0];
          break;
        case SecondWordFunction:
          if (words.length < 2) {
            log.info("Not enough words in " + phrase + " for " + function.getFunctionName());
            res = ""; // EVPRO-880 return empty string if not enough words in argument
          } else
            res = words[1];
          break;
        case ThirdWordFunction:
          if (words.length < 3) {
            log.info("Not enough words in " + phrase + " for " + function.getFunctionName());
            res = ""; // EVPRO-880 return empty string if not enough words in argument
          } else
            res = words[2];
          break;
        default:
          throw new ExpressionEvaluationException();
      }
      return res.trim();
    }

    private Object evaluateCastFunction(Object arg, ExpressionFunction function)
    {
      Object res;
      try {
        if (arg instanceof String) {
          switch (function)
          {
            case IntFunction:
              res = Long.parseLong((String) arg); // int('12')
              break;

            case DoubleFunction:
              res = Double.parseDouble((String) arg); // double('12.3')
              break;

            default:
              log.info("unexpected function : " + function);
              throw new ExpressionEvaluationException();
          }
        } else if (arg instanceof Double) { 
          switch (function)
          {
            case StringFunction:
              res = Double.toString((Double) arg); // string('3.14')
              break;

            default:
              log.info("unexpected function : " + function);
              throw new ExpressionEvaluationException();
          }
        } else if (arg instanceof Integer) {
          switch (function)
          {
            case StringFunction:
              res = Integer.toString((Integer) arg); // string('12')
              break;

            default:
              log.info("unexpected function : " + function);
              throw new ExpressionEvaluationException();
          }
        } else if (arg instanceof Long) {
          switch (function)
          {
            case StringFunction:
              res = Long.toString((Long) arg); // string('12')
              break;

            default:
              log.info("unexpected function : " + function);
              throw new ExpressionEvaluationException();
          }
        } else {
          log.info("unexpected type : " + ((arg == null)?"null":arg.getClass().getSimpleName()));
          throw new ExpressionEvaluationException();
        }
      } catch (NumberFormatException nfe) {
        log.info("Exception while casting : " + nfe.getLocalizedMessage());
        throw new ExpressionEvaluationException();
      }
      return res;
    }

    
    public List<String> getESFields() {
      List<String> res = new ArrayList<>();
      // check if each argument is a ReferenceExpression, then add its esField
      if (arguments != null) {
        for (Expression exp : arguments) {
          if (exp != null && exp instanceof ReferenceExpression) {
            ReferenceExpression refExp = (ReferenceExpression) exp;
            if (refExp.reference != null && refExp.reference.getESField() != null) {
              res.add(refExp.reference.getESField());
            }
          }
        }
      }
      return res;
    }
    
    
    
    private Object evaluateMinMaxFunction(Object arg1, Object arg2, Object arg3, ExpressionFunction function)
    {
      Object res;
      if (arg3 == null) {
        arg3 = arg1; // NO-OP for min/max
      }
      if (arg1 instanceof Double) {
        if (!(arg2 instanceof Double)) {
          throw new ExpressionTypeCheckException("type exception, same types expected, got " + arg1.getClass().getSimpleName() + " and " + arg2.getClass().getSimpleName());
        }
        if (!(arg3 instanceof Double)) {
          throw new ExpressionTypeCheckException("type exception, same types expected, got " + arg1.getClass().getSimpleName() + " and " + arg3.getClass().getSimpleName());
        }
        switch (function)
        {
          case MinFunction:
            res = Math.min((Double) arg1, Math.min((Double) arg2, (Double) arg3));
            break;
          case MaxFunction:
            res = Math.max((Double) arg1, Math.max((Double) arg2, (Double) arg3));
            break;

          default:
            throw new ExpressionTypeCheckException("type exception, min/max function expected, got " + function);
        }
      } else if (arg1 instanceof Integer) {
        if (!(arg2 instanceof Integer)) {
          throw new ExpressionTypeCheckException("type exception, same types expected, got " + arg1.getClass().getSimpleName() + " and " + arg2.getClass().getSimpleName());
        }
        if (!(arg3 instanceof Integer)) {
          throw new ExpressionTypeCheckException("type exception, same types expected, got " + arg1.getClass().getSimpleName() + " and " + arg3.getClass().getSimpleName());
        }
        switch (function)
        {
          case MinFunction:
            res = Math.min((Integer) arg1, Math.min((Integer) arg2, (Integer) arg3));
            break;
          case MaxFunction:
            res = Math.max((Integer) arg1, Math.max((Integer) arg2, (Integer) arg3));
            break;

          default:
            throw new ExpressionTypeCheckException("type exception, min/max function expected, got " + function);
        }
      } else if (arg1 instanceof Long) {
        if (!(arg2 instanceof Long)) {
          throw new ExpressionTypeCheckException("type exception, same types expected, got " + arg1.getClass().getSimpleName() + " and " + arg2.getClass().getSimpleName());
        }
        if (!(arg3 instanceof Long)) {
          throw new ExpressionTypeCheckException("type exception, same types expected, got " + arg1.getClass().getSimpleName() + " and " + arg3.getClass().getSimpleName());
        }
        switch (function)
        {
          case MinFunction:
            res = Math.min((Long) arg1, Math.min((Long) arg2, (Long) arg3));
            break;
          case MaxFunction:
            res = Math.max((Long) arg1, Math.max((Long) arg2, (Long) arg3));
            break;

          default:
            throw new ExpressionTypeCheckException("type exception, min/max function expected, got " + function);
        }
      } else throw new ExpressionTypeCheckException("type exception, int or double expected, got " + ((arg1==null)?"null":arg1.getClass().getSimpleName()));

      return res;
    }
    
    /*****************************************
    *
    *  esQuery
    *
    *****************************************/

    @Override public void esQuery(StringBuilder script, TimeUnit baseTimeUnit, int tenantID) throws CriterionException
    {
      /*****************************************
      *
      *  script
      *
      *****************************************/

      switch (function)
        {
          case DateConstantFunction:
            esQueryDateConstantFunction(script, baseTimeUnit, tenantID);
            break;
            
          case DateAddFunction:
            esQueryDateAddFunction(script, baseTimeUnit, tenantID);
            break;
            
          case TimeConstantFunction:
          case TimeAddFunction:
          default:
            throw new ExpressionEvaluationException();
        }
    }

    /*****************************************
     *
     *  esQueryNoPainless
     *
     *****************************************/

    @Override public Object esQueryNoPainless() throws CriterionException
    {
      switch (function)
      {
      case DateConstantFunction:
        return arguments.get(0).evaluateConstant().toString();

      case DateAddFunction:
        throw new CriterionException(this.getClass().getSimpleName()+" "+function.getFunctionName()+"cannot be processed into an no painless query");

      case TimeConstantFunction:
      case TimeAddFunction:
      default:
        throw new ExpressionEvaluationException();
      }
    }

    /*****************************************
    *
    *  esQueryDateConstantFunction
    *
    *****************************************/

    private void esQueryDateConstantFunction(StringBuilder script, TimeUnit baseTimeUnit, int tenantID) throws CriterionException
    {
      /****************************************
      *
      *  arguments
      *
      ****************************************/

      arguments.get(0).esQuery(script, baseTimeUnit, tenantID);
      
      /****************************************
      *
      *  function
      *
      ****************************************/
      
      script.append("def rightSF_" + getNodeID() + " = new SimpleDateFormat(\"yyyy-MM-dd'T'HH:mm:ss\"); ");   // TODO EVPRO-99
      script.append("rightSF_" + getNodeID() + ".setTimeZone(TimeZone.getTimeZone(\"" + Deployment.getDeployment(tenantID).getTimeZone() + "\")); ");
      script.append("def rightDT_" + getNodeID() + " = rightSF_" + getNodeID() + ".parse(right_" + arguments.get(0).getNodeID() + "); ");
      script.append("def rightCalendar_" + getNodeID() + " = rightSF_" + getNodeID() + ".getCalendar(); ");
      script.append("rightCalendar_" + getNodeID() + ".setTime(rightDT_" + getNodeID() + "); ");
      script.append("def rightInstant_" + getNodeID() + " = rightCalendar_" + getNodeID() + ".toInstant(); ");
      script.append("def rightBeforeTruncate_" + getNodeID() + " = LocalDateTime.ofInstant(rightInstant_" + getNodeID() + ", ZoneOffset.UTC); ");
      script.append(EvaluationCriterion.constructDateTruncateESScript(getNodeID(), "rightBeforeTruncate", "right", baseTimeUnit));
    }
    
    /*****************************************
    *
    *  esQueryDateAddFunction
    *
    *****************************************/

    private void esQueryDateAddFunction(StringBuilder script, TimeUnit baseTimeUnit, int tenantID) throws CriterionException
    {
      /*****************************************
      *
      *  validate
      *
      *****************************************/

      if (! arguments.get(2).isConstant())
        {
          throw new CriterionException("invalid criterionField " + arguments.get(2));
        }

      /****************************************
      *
      *  arguments
      *
      ****************************************/
      
      arguments.get(0).esQuery(script, baseTimeUnit, tenantID);
      arguments.get(1).esQuery(script, baseTimeUnit, tenantID);
      
      /****************************************
      *
      *  function
      *
      ****************************************/
      
      //
      //  truncate
      //

      script.append("def rightRawInstantBeforeAdd_" + getNodeID() + " = right_" + arguments.get(0).getNodeID() + "; ");
      script.append(EvaluationCriterion.constructDateTruncateESScript(getNodeID(), "rightRawInstantBeforeAdd", "rightInstantBeforeAdd", baseTimeUnit));

      //
      //  add time interval
      //
      
      TimeUnit timeUnit = TimeUnit.fromExternalRepresentation((String) arguments.get(2).evaluate(null, TimeUnit.Unknown));
      script.append("def rightRawInstant_" + getNodeID() + " = rightInstantBeforeAdd_" + getNodeID() + ".plus(right_" + arguments.get(1).getNodeID() + ", ChronoUnit." + timeUnit.getChronoUnit() + "); ");
      
      //
      //  truncate (after adding)
      //

      script.append(EvaluationCriterion.constructDateTruncateESScript(getNodeID(), "rightRawInstant", "rightInstant", baseTimeUnit));

      //
      //  result
      //
      
      script.append("def right_" + getNodeID() + " = rightInstant_" + getNodeID() + "; ");
    }
    
    /*****************************************
    *
    *  constructor
    *
    *****************************************/

    public FunctionCallExpression(ExpressionFunction function, List<Expression> arguments, int tenantID)
    {
      super(tenantID);
      this.function = function;
      this.arguments = arguments;
    }
  }

  /*****************************************
  *
  *  class ExpressionReader
  *
  *****************************************/

  public static class ExpressionReader
  {
    /*****************************************
    *
    *  data
    *
    *****************************************/

    private CriterionContext criterionContext;
    private String expressionString;
    private TimeUnit expressionBaseTimeUnit;
    private int tenantID;

    //
    //  derived
    //

    private Expression expression;

    /*****************************************
    *
    *  constructor
    *
    *****************************************/

    public ExpressionReader(CriterionContext criterionContext, String expressionString, TimeUnit expressionBaseTimeUnit, int tenantID)
    {
      this.criterionContext = criterionContext;
      this.expressionString = expressionString;
      this.expressionBaseTimeUnit = expressionBaseTimeUnit;
      this.expression = null;
      this.tenantID = tenantID;
    }

    /*****************************************
    *
    *  parseExpression
    *
    *****************************************/

    public Expression parse(ExpressionContext expressionContext, int tenantID) throws ExpressionParseException, ExpressionTypeCheckException
    {
      /*****************************************
      *
      *  parse
      *
      *****************************************/

      if (expressionString != null)
        {
          /*****************************************
          *
          *  pass 1 -- parse expressionString
          *
          *****************************************/

          try
            {
              //
              //  intialize reader
              //

              reader = new StringReader(expressionString);

              //
              //  parse
              //

              expression = parseExpression(tenantID);

              //
              //  input consumed?
              //

              switch (nextToken())
                {
                  case END_OF_INPUT:
                    break;

                  default:
                    parseError(tokenPosition,"expected end of input");
                    break;
                }

              //
              //  parse errors
              //

              if (parseErrors != null)
                {
                  throw new ExpressionParseException(parseErrors);
                }
            }
          finally
            {
              reader.close(); 
            }

          /*****************************************
          *
          *  pass 2 -- typecheck expression
          *
          *****************************************/

          expression.typeCheck(expressionContext, expressionBaseTimeUnit, tenantID);

          /*****************************************
          *
          *  pass 3 -- assignNodeID to expression
          *
          *****************************************/

          expression.assignNodeID(0);
        }

      /*****************************************
      *
      *  return
      *
      *****************************************/

      return expression;
    }

    /*****************************************************************************
    *
    *  character reader
    *
    *****************************************************************************/

    //
    //  whitespace characters (symbolic constants)
    //

    private final char EOF = (char) -1;
    private final char TAB = '\t';
    private final char LF = '\n';
    private final char CR = '\r';
    private final char FF = '\f';

    //
    //  single quote 
    //

    private final char SINGLE_QUOTE = '\'';

    //
    //  character reader state
    //

    private StringReader reader = null;                       // StringReader for lexer input
    private boolean haveCH = false;                           // lexer already has lookahead character
    private char ch = LF;                                     // current character available to lexer
    private LexerPosition chPosition = new LexerPosition();   // file position of current character

    /*****************************************
    *
    *  character classes
    *
    *****************************************/

    private boolean in_range(char low, char c, char high) { return (low <= c && c <= high); }
    private boolean printable(char c) { return (c != CR && c != LF && c != FF && c != EOF); }
    private boolean letter(char c) { return in_range('A',c,'Z') || in_range('a',c,'z'); }
    private boolean digit(char c) { return in_range('0',c,'9'); }
    private boolean id_char(char c) { return (letter(c) || digit(c) || c == '.' || c == '_'); }
    private boolean unary_char(char c) { return (c == '+' || c == '-'); }

    /*****************************************
    *
    *  getNextCharacter
    *
    *****************************************/

    private void getNextCharacter()
    {
      try
        {
          if (ch == LF) chPosition.nextLine();
          boolean done;
          do
            {
              done = true;
              ch = (char) reader.read();
              chPosition.nextChr();
              if (in_range('a',ch,'z') && caseFold)
                {
                  ch = Character.toUpperCase(ch);
                }
              else if (! printable(ch))
                {
                  if (ch == CR)
                    {
                      reader.mark(1);
                      ch = (char) reader.read();
                      if (ch != LF)
                        {
                          parseError(chPosition,"CR not followed by LF");
                          reader.reset();
                          ch = ' ';
                        }
                    }
                  if (ch == LF || ch == FF) { }
                  else if (ch == EOF) { }
                  else
                    {
                      throw new ExpressionParseException("getNextCharacter() - unknown character.");
                    }
                }
            }
          while (! done);
        }
      catch (IOException e)
        {
          throw new ExpressionParseException("IOException (invariant violation)");
        }
    }

    /*****************************************************************************
    *
    *  lexer
    *
    *****************************************************************************/

    //
    //  lexer state (for parser)
    //

    private LexerPosition tokenPosition = new LexerPosition();
    private Object tokenValue;

    //
    //  lexer state (internal)
    //

    private boolean caseFold = false;
    private boolean haveLookaheadToken = false;
    private Token lookaheadToken;
    private LexerPosition lookaheadTokenPosition = new LexerPosition();
    private Object lookaheadTokenValue = null;
    private Token previousToken = Token.END_OF_INPUT;

    /*****************************************
    *
    *  getLookaheadToken
    *
    *****************************************/

    private Token getLookaheadToken()
    {
      boolean done;
      Token result = Token.END_OF_INPUT;
      lookaheadTokenValue = null;
      do
        {
          //
          //  next character
          //

          done = true;
          if (! haveCH) getNextCharacter();
          lookaheadTokenPosition = new LexerPosition(chPosition);
          haveCH = false;

          //
          //  tokenize
          //

          if (ch == ' ' || ch == TAB || ch == FF)
            {
              done = false;               // whitespace
            }
          else if (letter(ch))
            {
              result = getIdentifier();
            }
          else if (digit(ch))
            {
              result = getNumber();
            }
          else if (ch == LF) result = Token.END_OF_INPUT;
          else if (ch == '-') result = Token.MINUS;
          else if (ch == '+') result = Token.PLUS;
          else if (ch == '*') result = Token.MULTIPLY;
          else if (ch == '/') result = Token.DIVIDE;
          else if (ch == '%') result = Token.MODULO;
          else if (ch == '(') result = Token.LEFT_PAREN;
          else if (ch == ')') result = Token.RIGHT_PAREN;
          else if (ch == '[') result = Token.LEFT_BRACKET;
          else if (ch == ']') result = Token.RIGHT_BRACKET;
          else if (ch == ',') result = Token.COMMA;
          else if (ch == EOF) result = Token.END_OF_INPUT;
          else if (ch == SINGLE_QUOTE)
            {
              result = getStringLiteral();
            }
          else
            {
              switch (ch)
                {
                  case '_':
                  case '@':
                  case '\\':
                  case '{':
                  case '}':
                  case '?':
                  case '.':
                    result = Token.INVALID_CHAR;
                    break;
                  default:
                    parseError(chPosition,"this character not permitted here");
                    done = false;
                    break;
                }
            }
        }
      while (! done);
      previousToken = result;
      return result;
    }

    /*****************************************
    *
    *  getIdentifier
    *
    *****************************************/

    private Token getIdentifier()
    {
      //
      //  get token
      //

      StringBuilder buffer = new StringBuilder();
      do
        {
          buffer.append(ch);
          getNextCharacter();
        }
      while (id_char(ch));
      haveCH = true;
      String identifier = buffer.toString();
      lookaheadTokenValue = identifier;

      //
      //  return
      //

      Token result;
      ExpressionFunction functionCall = ExpressionFunction.fromFunctionName(identifier);
      switch (functionCall)
        {
          case UnknownFunction:
            CriterionField criterionField = criterionContext.getCriterionFields(this.tenantID).get(identifier); // TODO EVPRO-99 check if tenant 0 here, not sure at all...
            if (criterionField != null)
              {
                lookaheadTokenValue = criterionField;
                result = Token.IDENTIFIER;
              }
            else if (identifier.equalsIgnoreCase("true"))
              {
                lookaheadTokenValue = Boolean.TRUE;
                result = Token.BOOLEAN;
              }
            else if (identifier.equalsIgnoreCase("false"))
              {
                lookaheadTokenValue = Boolean.FALSE;
                result = Token.BOOLEAN;
              }
            else
              {
                result = Token.INVALID_IDENTIFIER;
              }
            break;
          default:
            lookaheadTokenValue = functionCall;
            result = Token.FUNCTION_CALL;
            break;
        }
      return result;
    }

    /*****************************************
    *
    *  getNumber
    *
    *****************************************/

    private Token getNumber()
    {
      //
      //  get token
      //

      StringBuilder buffer = new StringBuilder();
      boolean fixedPoint = false;
      boolean percent = false;
      while (true)
        {
          if (digit(ch))
            {
              buffer.append(ch);
            }
          else if (ch == '.')
            {
              if (fixedPoint) parseError(chPosition,"bad number");
              buffer.append(ch);
              fixedPoint = true;
            }
          else
            {
              break;
            }
          getNextCharacter();
        }

      //
      //  handle percent
      //

      if (ch == '%')
        {
          percent = true;
          haveCH = false;
        }
      else
        {
          haveCH = true;
        }

      //
      //  calculate and return result
      //

      Token result;
      if (fixedPoint || percent)
        {
          double value = (new Double(buffer.toString())).doubleValue();
          if (percent) value *= 0.01;
          lookaheadTokenValue = new Double(value);
          result = Token.DOUBLE;
        }
      else
        {
          long value = (new Long(buffer.toString())).longValue();
          lookaheadTokenValue = new Long(value);
          result = Token.INTEGER;
        }
      return result;
    }

    /*****************************************
    *
    *  getStringLiteral
    *
    *****************************************/

    private Token getStringLiteral()
    {
      StringBuilder buffer = new StringBuilder();
      boolean saveCaseFold = caseFold;
      caseFold = false;
      getNextCharacter();
      while (true)
        {
          if (ch == SINGLE_QUOTE)
            {
              getNextCharacter();
              if (ch != SINGLE_QUOTE)
                {
                  haveCH = true;
                  break;
                }
            }
          else if (ch == LF || ch == EOF)
            {
              parseError(chPosition,"unterminated string (strings must be on one line)");
              break;
            }
          buffer.append(ch);
          getNextCharacter();
        }
      caseFold = saveCaseFold;
      lookaheadTokenValue = buffer.toString();
      return Token.STRING;
    }

    /*****************************************
    *
    *  nextToken
    *
    *****************************************/

    private Token nextToken()
    {
      //
      //  if we don't have a lookahead token, get one
      //

      if (! haveLookaheadToken)
        {
          lookaheadToken = getLookaheadToken();
          haveLookaheadToken = true;
        }

      //
      //  return the lookahead token
      //

      Token result = lookaheadToken;
      tokenPosition = new LexerPosition(lookaheadTokenPosition);
      tokenValue = lookaheadTokenValue;
      haveLookaheadToken = false;

      //
      //  return
      //

      return result;
    }

    /*****************************************
    *
    *  peekToken
    *
    *****************************************/

    private Token peekToken()
    {
      if (! haveLookaheadToken)
        {
          lookaheadToken = getLookaheadToken();
          haveLookaheadToken = true;
        }

      return lookaheadToken;
    }

    /*****************************************
    *
    *  class lexerPosition
    *
    *****************************************/

    private class LexerPosition
    {
      private short line = 0;             // current line
      private short chr = 0;              // current position on line
      LexerPosition() { }
      LexerPosition(LexerPosition position) { line = position.line; chr = position.chr; }
      void nextLine() { line += 1; chr = 0; }
      void nextChr() { chr += 1; }
      short getLine() { return line; }
      short getChr() { return chr; }
      public String toString() { return "Line " + line + ", Chr " + chr; }
    }

    /*****************************************************************************
    *
    *  parseExpression
    *
    *  <expression> ::= <term> { <adding_op> <term> }*
    *
    *  <term> ::= <primary> {<multiplying_op> <primary>}*
    *
    *  <primary> ::=
    *       <constant>
    *     | <identifier>  
    *     | <unary_op> <primary>
    *     | <functionCall>
    *     | '(' <expression> ')'
    *
    *  <constant> ::= <integer> | <double> | <string> | <boolean> | <integerset> | <stringset>
    *
    *  <integerset> ::= '[' { <integer> ; ',' }* ']'
    *
    *  <stringset> :: '[' { <string> ; ',' }* ']'
    *
    *  <unary_op> ::= '-' | '+'
    *
    *  <multiplying_op> ::= '*' | '/'
    *
    *  <adding_op> ::= '+' | '-'
    *
    *  <function> ::= <functionName> '(' { <expression> ; ',' }* ')'
    *  
    *  <functionName> ::=
    *       'dateConstant'
    *     | 'dateAdd'
    *
    *****************************************************************************/

    /*****************************************
    *
    *  parseExpression
    *
    *****************************************/

    private Expression parseExpression(int tenantID)
    {
      //
      //  <expression> ::= <term> { <adding_op> <term> }*
      //

      Expression result = parseTerm(tenantID);
      boolean parsingExpression = true;
      while (parsingExpression)
        {
          Token token = peekToken();
          switch (token)
            {
              case PLUS:
              case MINUS:
                token = nextToken();
                ExpressionOperator operator = ExpressionOperator.fromOperatorName(token);
                Expression right = parseTerm(tenantID);
                result = new OperatorExpression(operator, result, right, tenantID);
                break;

              default:
                parsingExpression = false;
                break;
            }
        }
      return result;
    }

    /*****************************************
    *
    *  parseTerm
    *
    *****************************************/

    private Expression parseTerm(int tenantID)
    {
      //
      //  <term> ::= <primary> {<multiplying_op> <primary>}*
      //

      Expression result = parsePrimary(tenantID);
      boolean parsingTerm = true;
      while (parsingTerm)
        {
          Token token = peekToken();
          switch (token)
            {
              case MULTIPLY:
              case DIVIDE:
              case MODULO:
                token = nextToken();
                ExpressionOperator operator = ExpressionOperator.fromOperatorName(token);
                Expression right = parsePrimary(tenantID);
                result = new OperatorExpression(operator, result, right, tenantID);
                break;

              default:
                parsingTerm = false;
                break;
            }
        }
      return result;
    }

    /*****************************************
    *
    *  parsePrimary
    *
    *****************************************/

    private Expression parsePrimary(int tenantID)
    {
      //  <primary> ::=
      //       <constant>
      //     | <identifier>  
      //     | <unary_op> <primary>
      //     | <functionCall>
      //     | '(' <expression> ')'
      //
      //  <constant> ::= <integer> | <double> | <string> | <boolean> | <integerset> | <stringset>
      //
      //  <integerset> ::= '[' { <integer> ; ',' }* ']'
      //
      //  <stringset> :: '[' { <string> ; ',' }* ']'
      //
      //  <unary_op> ::= '-' | '+'
      //
      //  <function> ::= <functionName> '(' { <expression> ; ',' }* ')'
      //

      Expression result;
      Token token = nextToken();
      switch (token)
        {
          case INTEGER:
            result = new ConstantExpression(ExpressionDataType.IntegerExpression, tokenValue, tenantID);
            break;

          case DOUBLE:
            result = new ConstantExpression(ExpressionDataType.DoubleExpression, tokenValue, tenantID);
            break;

          case STRING:
            result = new ConstantExpression(ExpressionDataType.StringExpression, tokenValue, tenantID);
            break;

          case BOOLEAN:
            result = new ConstantExpression(ExpressionDataType.BooleanExpression, tokenValue, tenantID);
            break;

          case LEFT_BRACKET:
            Set<Object> setConstant = new HashSet<Object>();
            ExpressionDataType setDataType = ExpressionDataType.EmptySetExpression;
            token = peekToken();
            while (token != Token.RIGHT_BRACKET)
              {
                token = nextToken();
                switch (token)
                  {
                    case INTEGER:
                      switch (setDataType)
                        {
                          case IntegerSetExpression:
                          case EmptySetExpression:
                            setConstant.add(tokenValue);
                            setDataType = ExpressionDataType.IntegerSetExpression;
                            break;

                          default:
                            parseError(tokenPosition,"expected literal");
                            throw new ExpressionParseException(parseErrors);
                        }
                      break;

                    case STRING:
                      switch (setDataType)
                        {
                          case StringSetExpression:
                          case EmptySetExpression:
                            setConstant.add(tokenValue);
                            setDataType = ExpressionDataType.StringSetExpression;
                            break;

                          default:
                            parseError(tokenPosition,"expected literal");
                            throw new ExpressionParseException(parseErrors);
                        }
                      break;

                    default:
                      parseError(tokenPosition,"expected literal");
                      throw new ExpressionParseException(parseErrors);
                  }
                token = peekToken();
                switch (token)
                  {
                    case COMMA:
                      token = nextToken();
                      break;
                    case RIGHT_BRACKET:
                      break;
                    default:
                      parseError(tokenPosition,"expected ']'");
                      throw new ExpressionParseException(parseErrors);
                  }

              }
            token = nextToken();
            result = new ConstantExpression(setDataType, setConstant, tenantID);
            break;

          case IDENTIFIER:
            result = new ReferenceExpression((CriterionField) tokenValue, tenantID);
            break;

          case MINUS:
          case PLUS:
            ExpressionOperator operator = ExpressionOperator.fromOperatorName(token);
            Expression primary = parsePrimary(tenantID);
            result = new UnaryExpression(operator,primary, tenantID);
            break;

          case FUNCTION_CALL:
            ExpressionFunction function = (ExpressionFunction) tokenValue;
            List<Expression> arguments = new ArrayList<Expression>();
            token = nextToken();
            if (token != Token.LEFT_PAREN)
              {
                parseError(tokenPosition,"expected '('");
                throw new ExpressionParseException(parseErrors);
              }
            token = peekToken();
            while (token != Token.RIGHT_PAREN)
              {
                Expression argument = parseExpression(tenantID);
                arguments.add(argument);
                token = peekToken();
                switch (token)
                  {
                    case COMMA:
                      token = nextToken();
                      break;
                    case RIGHT_PAREN:
                      break;
                    default:
                      parseError(tokenPosition,"expected ')'");
                      throw new ExpressionParseException(parseErrors);
                  }
              }
            token = nextToken();
            result = new FunctionCallExpression(function, arguments, tenantID);
            break;

          case LEFT_PAREN:
            result = parseExpression(tenantID);
            token = nextToken();
            if (token != Token.RIGHT_PAREN)
              {
                parseError(tokenPosition,"expected ')'");
                throw new ExpressionParseException(parseErrors);
              }
            break;

          default:
            parseError(tokenPosition,"expected <primary> in " + expressionString);
            throw new ExpressionParseException(parseErrors);
        }
      return result;
    }

    /*****************************************
    *
    *  parseError
    *
    *****************************************/

    private String parseErrors = null;
    private void parseError(LexerPosition position, String message)
    {
      if (parseErrors == null)
        {
          parseErrors = position.toString() + ": " + message;
        }
    }
  }

  /*****************************************
  *
  *  ParseException
  *
  *****************************************/

  public static class ExpressionParseException extends RuntimeException
  {
    /*****************************************
    *
    *  constructor
    *
    *****************************************/

    public ExpressionParseException(String message)
    {
      super(message);
    }
  }

  /*****************************************
  *
  *  TypeCheckException
  *
  *****************************************/

  public static class ExpressionTypeCheckException extends RuntimeException
  {
    /*****************************************
    *
    *  constructor
    *
    *****************************************/

    public ExpressionTypeCheckException(String message)
    {
      super(message);
    }
  }
  
  /*****************************************
  *
  *  ExpressionEvaluationException
  *
  *****************************************/

  public static class ExpressionEvaluationException extends RuntimeException
  {
    /*****************************************
    *
    *  data
    *
    *****************************************/

    private CriterionField criterionField;

    /*****************************************
    *
    *  accessors
    *
    *****************************************/
    
    public CriterionField getCriterionField() { return criterionField; }

    /*****************************************
    *
    *  constructor
    *
    *****************************************/

    //
    //  constructor (criterionField)
    //
    
    public ExpressionEvaluationException(CriterionField criterionField)
    {
      this.criterionField = criterionField;
    }
    
    //
    //  constructor (empty)
    //
    
    public ExpressionEvaluationException()
    {
      this.criterionField = null;
    }
  }

  /*****************************************
  *
  *  ExpressionNullException
  *
  *****************************************/

  private static class ExpressionNullException extends ExpressionEvaluationException
  {
    /*****************************************
    *
    *  constructor
    *
    *****************************************/

    //
    //  constructor (criterionField)
    //
    
    private ExpressionNullException(CriterionField criterionField)
    {
      super(criterionField);
    }
    
    //
    //  constructor (empty)
    //
    
    private ExpressionNullException()
    {
      super();
    }
  }
  
}
