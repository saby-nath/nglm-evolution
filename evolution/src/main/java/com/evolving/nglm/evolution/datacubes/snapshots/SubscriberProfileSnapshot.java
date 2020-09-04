package com.evolving.nglm.evolution.datacubes.snapshots;

import java.util.Date;

import org.elasticsearch.client.RestHighLevelClient;

import com.evolving.nglm.core.RLMDateUtils;
import com.evolving.nglm.core.SystemTime;
import com.evolving.nglm.evolution.Deployment;
import com.evolving.nglm.evolution.ScheduledJob;
import com.evolving.nglm.evolution.datacubes.SnapshotTask;

public class SubscriberProfileSnapshot extends ScheduledJob
{
  /*****************************************
  *
  *  data
  *
  *****************************************/

  private SnapshotTask snapshot;
  
  /*****************************************
  *
  *  constructor
  *
  *****************************************/
  // @rl: TODO: move in Elasticsearch scheduler
  public SubscriberProfileSnapshot(long schedulingUniqueID, RestHighLevelClient elasticsearch) 
  {
    super(schedulingUniqueID, 
        "Snapshot-SubscriberProfile", 
        Deployment.getElasticsearchJobsScheduling().get("SubscriberProfileSnapshot").getCronEntry(), 
        Deployment.getBaseTimeZone(),
        Deployment.getElasticsearchJobsScheduling().get("SubscriberProfileSnapshot").isScheduledAtRestart());
    this.snapshot = new SnapshotTask(this.jobName, "subscriberprofile", "subscriberprofile_snapshot", elasticsearch);
  }
  
  /*****************************************
  *
  *  DatacubeScheduling
  *
  *****************************************/
  @Override
  protected void run()
  {
    Date now = SystemTime.getCurrentTime();
    // This snapshot is done the day after the "saved" day (after midnight, in the morning usually)
    Date yesterday = RLMDateUtils.addDays(now, -1, Deployment.getBaseTimeZone());
    this.snapshot.run(yesterday);
  }

}
