/*****************************************************************************
*
*  DatacubeScheduling.java
*
*****************************************************************************/

package com.evolving.nglm.evolution.datacubes;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Date;
import java.util.TimeZone;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.evolving.nglm.core.CronFormat;
import com.evolving.nglm.core.SystemTime;
import com.evolving.nglm.core.utilities.UtilitiesException;

public abstract class ScheduledJob implements Comparable<ScheduledJob>
{
  /*****************************************
  *
  *  configuration
  *
  *****************************************/

  //
  //  logger
  //

  private static final Logger log = LoggerFactory.getLogger(ScheduledJob.class);
  
  /*****************************************
  *
  *  data
  *
  *****************************************/

  private long schedulingUniqueID;
  private Date nextGenerationDate;
  private CronFormat periodicGeneration;

  protected final String jobName;
  protected boolean properlyConfigured;

  /*****************************************
  *
  *  constructor
  *
  *****************************************/

  public ScheduledJob(long schedulingUniqueID, String jobName, String periodicGenerationCronEntry, String baseTimeZone, boolean scheduleAtStart)
  {
    this.schedulingUniqueID = schedulingUniqueID;
    this.properlyConfigured = true;
    this.jobName = jobName;
    try
      {
        this.periodicGeneration = new CronFormat(periodicGenerationCronEntry, TimeZone.getTimeZone(baseTimeZone));
        this.nextGenerationDate = (scheduleAtStart) ? SystemTime.getCurrentTime() : this.periodicGeneration.next();
      } 
    catch (UtilitiesException e)
      {
        log.error("bad perodicEvaluationCronEntry {}", e.getMessage());
        StringWriter stackTraceWriter = new StringWriter();
        e.printStackTrace(new PrintWriter(stackTraceWriter, true));
        log.info(stackTraceWriter.toString());
        
        this.properlyConfigured = false;
      }
  }

  /*****************************************
  *
  *  accessors
  *
  *****************************************/
  
  public Date getNextGenerationDate() { return this.nextGenerationDate; }
  public boolean isProperlyConfigured() { return this.properlyConfigured; }

  /*****************************************
  *
  *  absract
  *
  *****************************************/

  protected abstract void run();
  
  /*****************************************
  *
  *  compareTo
  *  
  *  This function sort by generation date, but because
  *  two scheduling can have the same generation date, we 
  *  also provide an arbitrary order with schedulingUniqueID
  *  
  *  compareTo must return 0 if and only if both object
  *  are the exact same scheduling !
  *
  *****************************************/
  @Override
  public int compareTo(ScheduledJob o)
  {
    if(this.schedulingUniqueID == o.schedulingUniqueID)
      {
        return 0;
      }
    
    int result = nextGenerationDate.compareTo(o.getNextGenerationDate());
    if(result == 0) 
      {
        return (this.schedulingUniqueID > o.schedulingUniqueID)? 1: -1;
      } 
    else 
      {
        return result;
      }
    
  }
  
  /*****************************************
  *
  *  generate
  *
  *****************************************/

  public void call() 
  {
    log.info("[" + this.jobName + "] Start (scheduled for " + this.nextGenerationDate.toLocaleString() + ")");
    
    this.run(); // TODO: Maybe add scheduled date later, if needed.
    
    this.nextGenerationDate = periodicGeneration.next();
    log.info("[" + this.jobName + "] End with success. Next call is scheduled for " + this.nextGenerationDate.toLocaleString());
  }

  
  /*****************************************
  *
  *  toString
  *
  *****************************************/
  
  @Override
  public String toString() {
    return "{ID:" + this.schedulingUniqueID + ", " + this.jobName + ": " + this.getNextGenerationDate().toLocaleString() + "}";
  }
}
