/*****************************************************************************
*
*  SubscriberState.java
*
*****************************************************************************/

package com.evolving.nglm.evolution;

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

import org.apache.kafka.common.serialization.Serde;
import org.apache.kafka.connect.data.Schema;
import org.apache.kafka.connect.data.SchemaAndValue;
import org.apache.kafka.connect.data.SchemaBuilder;
import org.apache.kafka.connect.data.Struct;
import org.apache.kafka.connect.data.Timestamp;

import com.evolving.nglm.core.ConnectSerde;
import com.evolving.nglm.core.RLMDateUtils;
import com.evolving.nglm.core.SchemaUtilities;
import com.evolving.nglm.core.SubscriberStreamOutput;
import com.evolving.nglm.core.SubscriberTrace;

public class SubscriberState implements SubscriberStreamOutput, StateStore
{
  /*****************************************
  *
  *  static
  *
  *****************************************/
  
  public static void forceClassLoad() {}
  
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
    schemaBuilder.name("subscriber_state");
    schemaBuilder.version(SchemaUtilities.packSchemaVersion(3));
    schemaBuilder.field("subscriberID", Schema.STRING_SCHEMA);
    schemaBuilder.field("subscriberProfile", SubscriberProfile.getSubscriberProfileSerde().schema());
    schemaBuilder.field("journeyStates", SchemaBuilder.array(JourneyState.schema()).schema());
    schemaBuilder.field("recentJourneyStates", SchemaBuilder.array(JourneyState.schema()).schema());
    schemaBuilder.field("scheduledEvaluations", SchemaBuilder.array(TimedEvaluation.schema()).schema());
    schemaBuilder.field("ucgRuleID", Schema.OPTIONAL_STRING_SCHEMA);
    schemaBuilder.field("ucgEpoch", Schema.OPTIONAL_INT32_SCHEMA);
    schemaBuilder.field("ucgRefreshDay", Timestamp.builder().optional().schema());
    schemaBuilder.field("lastEvaluationDate", Timestamp.builder().optional().schema());
    schemaBuilder.field("journeyRequests", SchemaBuilder.array(JourneyRequest.schema()).schema());
    schemaBuilder.field("journeyResponses", SchemaBuilder.array(JourneyRequest.schema()).schema());
    schemaBuilder.field("loyaltyProgramRequests", SchemaBuilder.array(LoyaltyProgramRequest.schema()).schema());
    schemaBuilder.field("loyaltyProgramResponses", SchemaBuilder.array(LoyaltyProgramRequest.schema()).schema());
    schemaBuilder.field("pointFulfillmentResponses", SchemaBuilder.array(PointFulfillmentRequest.schema()).schema());
    schemaBuilder.field("deliveryRequests", SchemaBuilder.array(DeliveryRequest.commonSerde().schema()).schema());
    schemaBuilder.field("journeyStatisticWrappers", SchemaBuilder.array(JourneyStatisticWrapper.schema()).schema());
    schemaBuilder.field("journeyMetrics", SchemaBuilder.array(JourneyMetric.schema()).schema());
    schemaBuilder.field("propensityOutputs", SchemaBuilder.array(PropensityEventOutput.schema()).defaultValue(Collections.<PropensityEventOutput>emptyList()).schema());
    schemaBuilder.field("profileLoyaltyProgramChangeEvents", SchemaBuilder.array(ProfileLoyaltyProgramChangeEvent.schema()).defaultValue(Collections.<ProfileLoyaltyProgramChangeEvent>emptyList()).schema());
    schemaBuilder.field("subscriberTraceMessage", Schema.OPTIONAL_STRING_SCHEMA);
    schemaBuilder.field("externalAPIOutput", ExternalAPIOutput.serde().optionalSchema()); // TODO : check this 
    schema = schemaBuilder.build();
  };

  //
  //  serde
  //

  private static ConnectSerde<SubscriberState> serde = new ConnectSerde<SubscriberState>(schema, false, SubscriberState.class, SubscriberState::pack, SubscriberState::unpack);
  private static StateStoreSerde<SubscriberState> stateStoreSerde = new StateStoreSerde<SubscriberState>(serde);

  //
  //  accessor
  //

  public static Schema schema() { return schema; }
  public static ConnectSerde<SubscriberState> serde() { return serde; }
  public static StateStoreSerde<SubscriberState> stateStoreSerde() { return stateStoreSerde; }

  /****************************************
  *
  *  data
  *
  ****************************************/

  private String subscriberID;
  private SubscriberProfile subscriberProfile;
  private Set<JourneyState> journeyStates;
  private Set<JourneyState> recentJourneyStates;
  private SortedSet<TimedEvaluation> scheduledEvaluations;
  private String ucgRuleID;
  private Integer ucgEpoch;
  private Date ucgRefreshDay;
  private Date lastEvaluationDate;
  private List<JourneyRequest> journeyRequests;
  private List<JourneyRequest> journeyResponses;
  private List<LoyaltyProgramRequest> loyaltyProgramRequests;
  private List<LoyaltyProgramRequest> loyaltyProgramResponses;
  private List<PointFulfillmentRequest> pointFulfillmentResponses;
  private List<DeliveryRequest> deliveryRequests;
  private List<JourneyStatisticWrapper> journeyStatisticWrappers;
  private List<JourneyMetric> journeyMetrics;
  private List<PropensityEventOutput> propensityOutputs;
  private List<ProfileLoyaltyProgramChangeEvent> profileLoyaltyProgramChangeEvents;
  private SubscriberTrace subscriberTrace;
  private ExternalAPIOutput externalAPIOutput;

  //
  //  in memory only
  //

  private byte[] kafkaRepresentation = null;

  /****************************************
  *
  *  accessors - basic
  *
  ****************************************/

  public String getSubscriberID() { return subscriberID; }
  public SubscriberProfile getSubscriberProfile() { return subscriberProfile; }
  public Set<JourneyState> getJourneyStates() { return journeyStates; }
  public Set<JourneyState> getRecentJourneyStates() { return recentJourneyStates; }
  public SortedSet<TimedEvaluation> getScheduledEvaluations() { return scheduledEvaluations; }
  public String getUCGRuleID() { return ucgRuleID; }
  public Integer getUCGEpoch() { return ucgEpoch; }
  public Date getUCGRefreshDay() { return ucgRefreshDay; }
  public Date getLastEvaluationDate() { return lastEvaluationDate; }
  public List<JourneyRequest> getJourneyRequests() { return journeyRequests; }
  public List<JourneyRequest> getJourneyResponses() { return journeyResponses; }
  public List<LoyaltyProgramRequest> getLoyaltyProgramRequests() { return loyaltyProgramRequests; }
  public List<LoyaltyProgramRequest> getLoyaltyProgramResponses() { return loyaltyProgramResponses; }
  public List<PointFulfillmentRequest> getPointFulfillmentResponses() { return pointFulfillmentResponses; }
  public List<DeliveryRequest> getDeliveryRequests() { return deliveryRequests; }
  public List<JourneyStatisticWrapper> getJourneyStatisticWrappers() { return journeyStatisticWrappers; }
  public List<JourneyMetric> getJourneyMetrics() { return journeyMetrics; }
  public List<PropensityEventOutput> getPropensityOutputs() { return propensityOutputs; }
  public List<ProfileLoyaltyProgramChangeEvent> getProfileLoyaltyProgramChangeEvents() { return profileLoyaltyProgramChangeEvents; }
  public SubscriberTrace getSubscriberTrace() { return subscriberTrace; }
  public ExternalAPIOutput getExternalAPIOutput() { return externalAPIOutput; }

  //
  //  kafkaRepresentation
  //

  @Override public void setKafkaRepresentation(byte[] kafkaRepresentation) { this.kafkaRepresentation = kafkaRepresentation; }
  @Override public byte[] getKafkaRepresentation() { return kafkaRepresentation; }

  /****************************************
  *
  *  setters
  *
  ****************************************/

  public void setLastEvaluationDate(Date lastEvaluationDate) { this.lastEvaluationDate = lastEvaluationDate; }
  public void setSubscriberTrace(SubscriberTrace subscriberTrace) { this.subscriberTrace = subscriberTrace; }
  public void setExternalAPIOutput(ExternalAPIOutput externalAPIOutput) { this.externalAPIOutput = externalAPIOutput; }
  
  //
  //  setUCGState
  //

  public void setUCGState(UCGState ucgState, Date evaluationDate)
  {
    this.ucgRuleID = ucgState.getUCGRuleID();
    this.ucgEpoch = ucgState.getRefreshEpoch();
    this.ucgRefreshDay = RLMDateUtils.truncate(evaluationDate, Calendar.DATE, Calendar.SUNDAY, Deployment.getBaseTimeZone());
  }

  /*****************************************
  *
  *  constructor (simple)
  *
  *****************************************/

  public SubscriberState(String subscriberID)
  {
    try
      {
        this.subscriberID = subscriberID;
        this.subscriberProfile = (SubscriberProfile) SubscriberProfile.getSubscriberProfileConstructor().newInstance(subscriberID);
        this.journeyStates = new HashSet<JourneyState>();
        this.recentJourneyStates = new HashSet<JourneyState>();
        this.scheduledEvaluations = new TreeSet<TimedEvaluation>();
        this.ucgRuleID = null;
        this.ucgEpoch = null;
        this.ucgRefreshDay = null;
        this.lastEvaluationDate = null;
        this.journeyRequests = new ArrayList<JourneyRequest>();
        this.journeyResponses = new ArrayList<JourneyRequest>();
        this.loyaltyProgramRequests = new ArrayList<LoyaltyProgramRequest>();
        this.loyaltyProgramResponses = new ArrayList<LoyaltyProgramRequest>();
        this.pointFulfillmentResponses = new ArrayList<PointFulfillmentRequest>();
        this.deliveryRequests = new ArrayList<DeliveryRequest>();
        this.journeyStatisticWrappers = new ArrayList<JourneyStatisticWrapper>();
        this.journeyMetrics = new ArrayList<JourneyMetric>();
        this.propensityOutputs = new ArrayList<PropensityEventOutput>();
        this.profileLoyaltyProgramChangeEvents = new ArrayList<ProfileLoyaltyProgramChangeEvent>();
        this.subscriberTrace = null;
        this.externalAPIOutput = null;
        this.kafkaRepresentation = null;
      }
    catch (InvocationTargetException e)
      {
        throw new RuntimeException(e.getCause());
      }
    catch (InstantiationException|IllegalAccessException e)
      {
        throw new RuntimeException(e);
      }
  }
  
  /*****************************************
  *
  *  constructor (unpack)
  *
  *****************************************/

  private SubscriberState(String subscriberID, SubscriberProfile subscriberProfile, Set<JourneyState> journeyStates, Set<JourneyState> recentJourneyStates, SortedSet<TimedEvaluation> scheduledEvaluations, String ucgRuleID, Integer ucgEpoch, Date ucgRefreshDay, Date lastEvaluationDate, List<JourneyRequest> journeyRequests, List<JourneyRequest> journeyResponses, List<LoyaltyProgramRequest> loyaltyProgramRequests, List<LoyaltyProgramRequest> loyaltyProgramResponses, List<PointFulfillmentRequest> pointFulfillmentResponses, List<DeliveryRequest> deliveryRequests, List<JourneyStatisticWrapper> journeyStatisticWrappers, List<JourneyMetric> journeyMetrics, List<PropensityEventOutput> propensityOutputs, List<ProfileLoyaltyProgramChangeEvent> profileLoyaltyProgramChangeEvents, SubscriberTrace subscriberTrace, ExternalAPIOutput externalAPIOutput)
  {
    this.subscriberID = subscriberID;
    this.subscriberProfile = subscriberProfile;
    this.journeyStates = journeyStates;
    this.recentJourneyStates = recentJourneyStates;
    this.scheduledEvaluations = scheduledEvaluations;
    this.ucgRuleID = ucgRuleID;
    this.ucgEpoch = ucgEpoch;
    this.ucgRefreshDay = ucgRefreshDay;
    this.lastEvaluationDate = lastEvaluationDate;
    this.journeyRequests = journeyRequests;
    this.journeyResponses = journeyResponses;
    this.loyaltyProgramRequests = loyaltyProgramRequests;
    this.loyaltyProgramResponses = loyaltyProgramResponses;
    this.pointFulfillmentResponses = pointFulfillmentResponses;
    this.deliveryRequests = deliveryRequests;
    this.journeyStatisticWrappers = journeyStatisticWrappers;
    this.journeyMetrics = journeyMetrics;
    this.propensityOutputs = propensityOutputs;
    this.profileLoyaltyProgramChangeEvents = profileLoyaltyProgramChangeEvents;
    this.subscriberTrace = subscriberTrace;
    this.externalAPIOutput = externalAPIOutput;
    this.kafkaRepresentation = null;
  }

  /*****************************************
  *
  *  constructor (copy)
  *
  *****************************************/

  public SubscriberState(SubscriberState subscriberState)
  {
    try
      {
        //
        //  simple fields
        //

        this.subscriberID = subscriberState.getSubscriberID();
        this.subscriberProfile = (SubscriberProfile) SubscriberProfile.getSubscriberProfileCopyConstructor().newInstance(subscriberState.getSubscriberProfile());
        this.recentJourneyStates = new HashSet<JourneyState>(subscriberState.getRecentJourneyStates());
        this.scheduledEvaluations = new TreeSet<TimedEvaluation>(subscriberState.getScheduledEvaluations());
        this.ucgRuleID = subscriberState.getUCGRuleID();
        this.ucgEpoch = subscriberState.getUCGEpoch();
        this.ucgRefreshDay = subscriberState.getUCGRefreshDay();
        this.lastEvaluationDate = subscriberState.getLastEvaluationDate();
        this.journeyRequests = new ArrayList<JourneyRequest>(subscriberState.getJourneyRequests());
        this.journeyResponses = new ArrayList<JourneyRequest>(subscriberState.getJourneyResponses());
        this.loyaltyProgramRequests = new ArrayList<LoyaltyProgramRequest>(subscriberState.getLoyaltyProgramRequests());
        this.loyaltyProgramResponses = new ArrayList<LoyaltyProgramRequest>(subscriberState.getLoyaltyProgramResponses());
        this.pointFulfillmentResponses= new ArrayList<PointFulfillmentRequest>(subscriberState.getPointFulfillmentResponses());
        this.deliveryRequests = new ArrayList<DeliveryRequest>(subscriberState.getDeliveryRequests());
        this.journeyMetrics = new ArrayList<JourneyMetric>(subscriberState.getJourneyMetrics());
        this.propensityOutputs = new ArrayList<PropensityEventOutput>(subscriberState.getPropensityOutputs());
        this.profileLoyaltyProgramChangeEvents= new ArrayList<ProfileLoyaltyProgramChangeEvent>(subscriberState.getProfileLoyaltyProgramChangeEvents());
        this.subscriberTrace = subscriberState.getSubscriberTrace();
        this.externalAPIOutput = subscriberState.getExternalAPIOutput();
        this.kafkaRepresentation = null;

        //
        //  deep copy of journey states
        //

        this.journeyStates = new HashSet<JourneyState>();
        for (JourneyState journeyState : subscriberState.getJourneyStates())
          {
            this.journeyStates.add(new JourneyState(journeyState));
          }
        
        //
        //  deep copy of journey statistic wrappers
        //

        this.journeyStatisticWrappers = new ArrayList<JourneyStatisticWrapper>();
        for(JourneyStatisticWrapper journeyStatisticWrapper : subscriberState.getJourneyStatisticWrappers())
          {
            this.journeyStatisticWrappers.add(new JourneyStatisticWrapper(journeyStatisticWrapper));
          }
      }
    catch (InvocationTargetException e)
      {
        throw new RuntimeException(e.getCause());
      }
    catch (InstantiationException|IllegalAccessException e)
      {
        throw new RuntimeException(e);
      }
  }

  /*****************************************
  *
  *  pack
  *
  *****************************************/

  public static Object pack(Object value)
  {
    SubscriberState subscriberState = (SubscriberState) value;
    Struct struct = new Struct(schema);
    struct.put("subscriberID", subscriberState.getSubscriberID());
    struct.put("subscriberProfile", SubscriberProfile.getSubscriberProfileSerde().pack(subscriberState.getSubscriberProfile()));
    struct.put("journeyStates", packJourneyStates(subscriberState.getJourneyStates()));
    struct.put("recentJourneyStates", packJourneyStates(subscriberState.getRecentJourneyStates()));
    struct.put("scheduledEvaluations", packScheduledEvaluations(subscriberState.getScheduledEvaluations()));
    struct.put("ucgRuleID", subscriberState.getUCGRuleID());
    struct.put("ucgEpoch", subscriberState.getUCGEpoch());
    struct.put("ucgRefreshDay", subscriberState.getUCGRefreshDay());
    struct.put("lastEvaluationDate", subscriberState.getLastEvaluationDate());
    struct.put("journeyRequests", packJourneyRequests(subscriberState.getJourneyRequests()));
    struct.put("journeyResponses", packJourneyRequests(subscriberState.getJourneyResponses()));
    struct.put("loyaltyProgramRequests", packLoyaltyProgramRequests(subscriberState.getLoyaltyProgramRequests()));
    struct.put("loyaltyProgramResponses", packLoyaltyProgramRequests(subscriberState.getLoyaltyProgramResponses()));
    struct.put("pointFulfillmentResponses", packPointFulfillmentResponses(subscriberState.getPointFulfillmentResponses()));
    struct.put("deliveryRequests", packDeliveryRequests(subscriberState.getDeliveryRequests()));
    struct.put("journeyStatisticWrappers", packJourneyStatisticWrappers(subscriberState.getJourneyStatisticWrappers()));
    struct.put("journeyMetrics", packJourneyMetrics(subscriberState.getJourneyMetrics()));
    struct.put("propensityOutputs", packPropensityOutputs(subscriberState.getPropensityOutputs()));
    struct.put("profileLoyaltyProgramChangeEvents", packProfileLoyaltyProgramChangeEvents(subscriberState.getProfileLoyaltyProgramChangeEvents()));
    struct.put("subscriberTraceMessage", subscriberState.getSubscriberTrace() != null ? subscriberState.getSubscriberTrace().getSubscriberTraceMessage() : null);
    struct.put("externalAPIOutput", subscriberState.getExternalAPIOutput() != null ? ExternalAPIOutput.serde().packOptional(subscriberState.getExternalAPIOutput()) : null);
    return struct;
  }

  /*****************************************
  *
  *  packJourneyStates
  *
  *****************************************/

  private static List<Object> packJourneyStates(Set<JourneyState> journeyStates)
  {
    List<Object> result = new ArrayList<Object>();
    for (JourneyState journeyState : journeyStates)
      {
        result.add(JourneyState.pack(journeyState));
      }
    return result;
  }
  
  /*****************************************
  *
  *  packScheduledEvaluations
  *
  *****************************************/

  private static List<Object> packScheduledEvaluations(SortedSet<TimedEvaluation> scheduledEvaluations)
  {
    List<Object> result = new ArrayList<Object>();
    for (TimedEvaluation scheduledEvaluation : scheduledEvaluations)
      {
        result.add(TimedEvaluation.pack(scheduledEvaluation));
      }
    return result;
  }

  /*****************************************
  *
  *  packJourneyRequests
  *
  *****************************************/

  private static List<Object> packJourneyRequests(List<JourneyRequest> journeyRequests)
  {
    List<Object> result = new ArrayList<Object>();
    for (JourneyRequest journeyRequest : journeyRequests)
      {
        result.add(JourneyRequest.pack(journeyRequest));
      }
    return result;
  }

  /*****************************************
  *
  *  packLoyaltyProgramRequests
  *
  *****************************************/

  private static List<Object> packLoyaltyProgramRequests(List<LoyaltyProgramRequest> loyaltyProgramRequests)
  {
    List<Object> result = new ArrayList<Object>();
    for (LoyaltyProgramRequest loyaltyProgramRequest : loyaltyProgramRequests)
      {
        result.add(LoyaltyProgramRequest.pack(loyaltyProgramRequest));
      }
    return result;
  }

  /*****************************************
  *
  *  packPointFulfillmentResponses
  *
  *****************************************/

  private static List<Object> packPointFulfillmentResponses(List<PointFulfillmentRequest> pointFulfillmentResponses)
  {
    List<Object> result = new ArrayList<Object>();
    for (PointFulfillmentRequest pointFulfillmentResponse : pointFulfillmentResponses)
      {
        result.add(PointFulfillmentRequest.pack(pointFulfillmentResponse));
      }
    return result;
  }

  /*****************************************
  *
  *  packDeliveryRequests
  *
  *****************************************/

  private static List<Object> packDeliveryRequests(List<DeliveryRequest> deliveryRequests)
  {
    List<Object> result = new ArrayList<Object>();
    for (DeliveryRequest deliveryRequest : deliveryRequests)
      {
        result.add(DeliveryRequest.commonSerde().pack(deliveryRequest));
      }
    return result;
  }

  /*****************************************
  *
  *  packJourneyStatisticWrappers
  *
  *****************************************/

  private static List<Object> packJourneyStatisticWrappers(List<JourneyStatisticWrapper> journeyStatisticWrappers)
  {
    List<Object> result = new ArrayList<Object>();
    for (JourneyStatisticWrapper journeyStatisticWrapper : journeyStatisticWrappers)
      {
        result.add(JourneyStatisticWrapper.pack(journeyStatisticWrapper));
      }
    return result;
  }

  /*****************************************
  *
  *  packJourneyMetrics
  *
  *****************************************/

  private static List<Object> packJourneyMetrics(List<JourneyMetric> journeyMetrics)
  {
    List<Object> result = new ArrayList<Object>();
    for (JourneyMetric journeyMetric : journeyMetrics)
      {
        result.add(JourneyMetric.pack(journeyMetric));
      }
    return result;
  }

  /*****************************************
  *
  *  packPropensityOutputs
  *
  *****************************************/

  private static List<Object> packPropensityOutputs(List<PropensityEventOutput> propensityOutputs)
  {
    List<Object> result = new ArrayList<Object>();
    for (PropensityEventOutput propensityOutput : propensityOutputs)
      {
        result.add(PropensityEventOutput.pack(propensityOutput));
      }
    return result;
  }
  
  /*****************************************
  *
  *  packProfileLoyaltyProgramChangeEvents
  *
  *****************************************/

  private static List<Object> packProfileLoyaltyProgramChangeEvents(List<ProfileLoyaltyProgramChangeEvent> profileLoyaltyProgramChangeEvents)
  {
    List<Object> result = new ArrayList<Object>();
    for (ProfileLoyaltyProgramChangeEvent profileLoyaltyProgramChangeEvent : profileLoyaltyProgramChangeEvents)
      {
        result.add(ProfileLoyaltyProgramChangeEvent.pack(profileLoyaltyProgramChangeEvent));
      }
    return result;
  }

  /*****************************************
  *
  *  unpack
  *
  *****************************************/

  public static SubscriberState unpack(SchemaAndValue schemaAndValue)
  {
    //
    //  data
    //

    Schema schema = schemaAndValue.schema();
    Object value = schemaAndValue.value();
    Integer schemaVersion = (schema != null) ? SchemaUtilities.unpackSchemaVersion0(schema.version()) : null;

    //
    //  unpack
    //

    Struct valueStruct = (Struct) value;
    String subscriberID = valueStruct.getString("subscriberID");
    SubscriberProfile subscriberProfile = SubscriberProfile.getSubscriberProfileSerde().unpack(new SchemaAndValue(schema.field("subscriberProfile").schema(), valueStruct.get("subscriberProfile")));
    Set<JourneyState> journeyStates = unpackJourneyStates(schema.field("journeyStates").schema(), valueStruct.get("journeyStates"));
    Set<JourneyState> recentJourneyStates = unpackJourneyStates(schema.field("recentJourneyStates").schema(), valueStruct.get("recentJourneyStates"));
    SortedSet<TimedEvaluation> scheduledEvaluations = unpackScheduledEvaluations(schema.field("scheduledEvaluations").schema(), valueStruct.get("scheduledEvaluations"));
    String ucgRuleID = valueStruct.getString("ucgRuleID");
    Integer ucgEpoch = valueStruct.getInt32("ucgEpoch");
    Date ucgRefreshDay = (Date) valueStruct.get("ucgRefreshDay");
    Date lastEvaluationDate = (Date) valueStruct.get("lastEvaluationDate");
    List<JourneyRequest> journeyRequests = unpackJourneyRequests(schema.field("journeyRequests").schema(), valueStruct.get("journeyRequests"));
    List<JourneyRequest> journeyResponses = unpackJourneyRequests(schema.field("journeyResponses").schema(), valueStruct.get("journeyResponses"));
    List<LoyaltyProgramRequest> loyaltyProgramRequests = unpackLoyaltyProgramRequests(schema.field("loyaltyProgramRequests").schema(), valueStruct.get("loyaltyProgramRequests"));
    List<LoyaltyProgramRequest> loyaltyProgramResponses = unpackLoyaltyProgramRequests(schema.field("loyaltyProgramResponses").schema(), valueStruct.get("loyaltyProgramResponses"));
    List<PointFulfillmentRequest> pointFulfillmentResponses = (schemaVersion >= 2) ? unpackPointFulfillmentResponses(schema.field("pointFulfillmentResponses").schema(), valueStruct.get("pointFulfillmentResponses")) : Collections.<PointFulfillmentRequest>emptyList();
    List<DeliveryRequest> deliveryRequests = unpackDeliveryRequests(schema.field("deliveryRequests").schema(), valueStruct.get("deliveryRequests"));
    List<JourneyStatisticWrapper> journeyStatisticWrappers = (schemaVersion >= 3) ?unpackJourneyStatisticWrappers(schema.field("journeyStatisticWrappers").schema(), valueStruct.get("journeyStatisticWrappers")) : new ArrayList<JourneyStatisticWrapper>();
    List<JourneyMetric> journeyMetrics = (schemaVersion >= 2) ? unpackJourneyMetrics(schema.field("journeyMetrics").schema(), valueStruct.get("journeyMetrics")) : new ArrayList<JourneyMetric>();
    List<PropensityEventOutput> propensityOutputs = (schemaVersion >= 2) ? unpackPropensityOutputs(schema.field("propensityOutputs").schema(), valueStruct.get("propensityOutputs")) : new ArrayList<PropensityEventOutput>();
    List<ProfileLoyaltyProgramChangeEvent> profileLoyaltyProgramChangeEvents = (schemaVersion >= 2) ? unpackProfileLoyaltyProgramChangeEvents(schema.field("profileLoyaltyProgramChangeEvents").schema(), valueStruct.get("profileLoyaltyProgramChangeEvents")) : Collections.<ProfileLoyaltyProgramChangeEvent>emptyList();
    SubscriberTrace subscriberTrace = valueStruct.getString("subscriberTraceMessage") != null ? new SubscriberTrace(valueStruct.getString("subscriberTraceMessage")) : null;
    ExternalAPIOutput externalAPIOutput = valueStruct.get("externalAPIOutput") != null ? ExternalAPIOutput.unpack(new SchemaAndValue(schema.field("externalAPIOutput").schema(), valueStruct.get("externalAPIOutput"))) : null;

    //
    //  return
    //

    return new SubscriberState(subscriberID, subscriberProfile, journeyStates, recentJourneyStates, scheduledEvaluations, ucgRuleID, ucgEpoch, ucgRefreshDay, lastEvaluationDate, journeyRequests, journeyResponses, loyaltyProgramRequests, loyaltyProgramResponses,pointFulfillmentResponses, deliveryRequests, journeyStatisticWrappers, journeyMetrics, propensityOutputs, profileLoyaltyProgramChangeEvents, subscriberTrace, externalAPIOutput);
  }

  /*****************************************
  *
  *  unpackJourneyStates
  *
  *****************************************/

  private static Set<JourneyState> unpackJourneyStates(Schema schema, Object value)
  {
    //
    //  get schema for JourneyState
    //

    Schema journeyStateSchema = schema.valueSchema();
    
    //
    //  unpack
    //

    Set<JourneyState> result = new HashSet<JourneyState>();
    List<Object> valueArray = (List<Object>) value;
    for (Object state : valueArray)
      {
        JourneyState journeyState = JourneyState.unpack(new SchemaAndValue(journeyStateSchema, state));
        result.add(journeyState);
      }

    //
    //  return
    //

    return result;
  }

  /*****************************************
  *
  *  unpackScheduledEvaluations
  *
  *****************************************/

  private static SortedSet<TimedEvaluation> unpackScheduledEvaluations(Schema schema, Object value)
  {
    //
    //  get schema for TimedEvaluation
    //

    Schema timedEvaluationSchema = schema.valueSchema();
    
    //
    //  unpack
    //

    SortedSet<TimedEvaluation> result = new TreeSet<TimedEvaluation>();
    List<Object> valueArray = (List<Object>) value;
    for (Object scheduledEvaluation : valueArray)
      {
        result.add(TimedEvaluation.unpack(new SchemaAndValue(timedEvaluationSchema, scheduledEvaluation)));
      }

    //
    //  return
    //

    return result;
  }

  /*****************************************
  *
  *  unpackJourneyRequests
  *
  *****************************************/

  private static List<JourneyRequest> unpackJourneyRequests(Schema schema, Object value)
  {
    //
    //  get schema for JourneyRequest
    //

    Schema journeyRequestSchema = schema.valueSchema();
    
    //
    //  unpack
    //

    List<JourneyRequest> result = new ArrayList<JourneyRequest>();
    List<Object> valueArray = (List<Object>) value;
    for (Object request : valueArray)
      {
        JourneyRequest journeyRequest = JourneyRequest.unpack(new SchemaAndValue(journeyRequestSchema, request));
        result.add(journeyRequest);
      }

    //
    //  return
    //

    return result;
  }

  /*****************************************
  *
  *  unpackLoyaltyProgramRequests
  *
  *****************************************/

  private static List<LoyaltyProgramRequest> unpackLoyaltyProgramRequests(Schema schema, Object value)
  {
    //
    //  get schema for LoyaltyProgramRequest
    //

    Schema loyaltyProgramRequestSchema = schema.valueSchema();
    
    //
    //  unpack
    //

    List<LoyaltyProgramRequest> result = new ArrayList<LoyaltyProgramRequest>();
    List<Object> valueArray = (List<Object>) value;
    for (Object request : valueArray)
      {
        LoyaltyProgramRequest loyaltyProgramRequest = LoyaltyProgramRequest.unpack(new SchemaAndValue(loyaltyProgramRequestSchema, request));
        result.add(loyaltyProgramRequest);
      }

    //
    //  return
    //

    return result;
  }

  /*****************************************
  *
  *  unpackPointFulfillmentResponses
  *
  *****************************************/

  private static List<PointFulfillmentRequest> unpackPointFulfillmentResponses(Schema schema, Object value)
  {
    //
    //  get schema for PointFulfillmentRequest
    //

    Schema pointFulfillmentResponseSchema = schema.valueSchema();
    
    //
    //  unpack
    //

    List<PointFulfillmentRequest> result = new ArrayList<PointFulfillmentRequest>();
    List<Object> valueArray = (List<Object>) value;
    for (Object request : valueArray)
      {
        PointFulfillmentRequest pointFulfillmentResponse = PointFulfillmentRequest.unpack(new SchemaAndValue(pointFulfillmentResponseSchema, request));
        result.add(pointFulfillmentResponse);
      }

    //
    //  return
    //

    return result;
  }

  /*****************************************
  *
  *  unpackDeliveryRequests
  *
  *****************************************/

  private static List<DeliveryRequest> unpackDeliveryRequests(Schema schema, Object value)
  {
    //
    //  get schema for DeliveryRequest
    //

    Schema deliveryRequestSchema = schema.valueSchema();
    
    //
    //  unpack
    //

    List<DeliveryRequest> result = new ArrayList<DeliveryRequest>();
    List<Object> valueArray = (List<Object>) value;
    for (Object request : valueArray)
      {
        DeliveryRequest deliveryRequest = DeliveryRequest.commonSerde().unpack(new SchemaAndValue(deliveryRequestSchema, request));
        result.add(deliveryRequest);
      }

    //
    //  return
    //

    return result;
  }

  /*****************************************
  *
  *  unpackJourneyStatistics
  *
  *****************************************/

  private static List<JourneyStatisticWrapper> unpackJourneyStatisticWrappers(Schema schema, Object value)
  {
    //
    //  get schema for JourneyStatisticWrapper
    //

    Schema journeyStatisticWrapperSchema = schema.valueSchema();
    
    //
    //  unpack
    //

    List<JourneyStatisticWrapper> result = new ArrayList<JourneyStatisticWrapper>();
    List<Object> valueArray = (List<Object>) value;
    for (Object statistic : valueArray)
      {
        JourneyStatisticWrapper journeyStatisticWrapper = JourneyStatisticWrapper.unpack(new SchemaAndValue(journeyStatisticWrapperSchema, statistic));
        result.add(journeyStatisticWrapper);
      }

    //
    //  return
    //

    return result;
  }

  /*****************************************
  *
  *  unpackJourneyMetrics
  *
  *****************************************/

  private static List<JourneyMetric> unpackJourneyMetrics(Schema schema, Object value)
  {
    //
    //  get schema for JourneyMetric
    //

    Schema journeyMetricSchema = schema.valueSchema();
    
    //
    //  unpack
    //

    List<JourneyMetric> result = new ArrayList<JourneyMetric>();
    List<Object> valueArray = (List<Object>) value;
    for (Object metric : valueArray)
      {
        JourneyMetric journeyMetric = JourneyMetric.unpack(new SchemaAndValue(journeyMetricSchema, metric));
        result.add(journeyMetric);
      }

    //
    //  return
    //

    return result;
  }

  /*****************************************
  *
  *  unpackPropensityOutputs
  *
  *****************************************/

  private static List<PropensityEventOutput> unpackPropensityOutputs(Schema schema, Object value)
  {
    //
    //  get schema for PropensityEventOutput
    //

    Schema propensityOutputsSchema = schema.valueSchema();
    
    //
    //  unpack
    //

    List<PropensityEventOutput> result = new ArrayList<PropensityEventOutput>();
    List<Object> valueArray = (List<Object>) value;
    for (Object output : valueArray)
      {
        PropensityEventOutput propensityOutput = PropensityEventOutput.unpack(new SchemaAndValue(propensityOutputsSchema, output));
        result.add(propensityOutput);
      }

    //
    //  return
    //

    return result;
  }
  /*****************************************
  *
  *  unpackProfileLoyaltyProgramChangeEvents
  *
  *****************************************/

  private static List<ProfileLoyaltyProgramChangeEvent> unpackProfileLoyaltyProgramChangeEvents(Schema schema, Object value)
  {
    //
    //  get schema for ProfileChangeEvent
    //

    Schema profileLoyaltyProgramChangeEventSchema = schema.valueSchema();
    
    //
    //  unpack
    //

    List<ProfileLoyaltyProgramChangeEvent> result = new ArrayList<ProfileLoyaltyProgramChangeEvent>();
    List<Object> valueArray = (List<Object>) value;
    for (Object event : valueArray)
      {
        ProfileLoyaltyProgramChangeEvent profileLoyaltyProgramChangeEvent = ProfileLoyaltyProgramChangeEvent.unpack(new SchemaAndValue(profileLoyaltyProgramChangeEventSchema, event));
        result.add(profileLoyaltyProgramChangeEvent);
      }

    //
    //  return
    //

    return result;
  }

  
}
