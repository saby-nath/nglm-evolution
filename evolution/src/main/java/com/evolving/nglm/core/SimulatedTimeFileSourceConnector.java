/****************************************************************************
*
*  SimulatedTimeFileSourceConnector.java
*
****************************************************************************/

package com.evolving.nglm.core;

import com.rii.utilities.JSONUtilities;
import com.rii.utilities.JSONUtilities.JSONUtilitiesException;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;

import org.apache.kafka.connect.connector.Task;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.text.ParseException;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class SimulatedTimeFileSourceConnector extends FileSourceConnector
{
  /****************************************
  *
  *  taskClass
  *
  ****************************************/

  @Override public Class<? extends Task> taskClass()
  {
    return SimulatedTimeFileSourceTask.class;
  }

  /*****************************************
  *
  *  class SimulatedTimeFileSourceTask
  *
  *****************************************/

  public static class SimulatedTimeFileSourceTask extends com.evolving.nglm.core.FileSourceTask
  {
    /*****************************************
    *
    *  config
    *
    *****************************************/

    //
    //  logger
    //

    private static final Logger log = LoggerFactory.getLogger(SimulatedTimeFileSourceConnector.class);

    /*****************************************
    *
    *  start
    *
    *****************************************/

    @Override public void start(Map<String, String> properties)
    {
      super.start(properties);
    }

    /*****************************************
    *
    *  processRecord
    *
    *****************************************/

    @Override protected List<KeyValue> processRecord(String record) throws FileSourceTaskException
    {
      List<KeyValue> result = null;
      try
        {
          JSONObject jsonRoot = (JSONObject) (new JSONParser()).parse(record);
          DateValue simulatedTime = new DateValue(RLMDateUtils.parseDateFromREST(JSONUtilities.decodeString(jsonRoot, "simulatedTime", true)));
          result = Collections.<KeyValue>singletonList(new KeyValue(StringKey.schema(), "simulatedTime", DateValue.schema(), DateValue.pack(simulatedTime)));
        }
      catch (org.json.simple.parser.ParseException|ParseException|JSONUtilitiesException|ServerRuntimeException e)
        {
          log.info("processRecord error parsing: {}", record);
          log.info("processRecord unknown unparsable json: {}", e.getMessage());
          StringWriter stackTraceWriter = new StringWriter();
          e.printStackTrace(new PrintWriter(stackTraceWriter, true));
          log.info(stackTraceWriter.toString());
          throw new FileSourceTaskException(e);
        }
      return result;
    }
  }
}
