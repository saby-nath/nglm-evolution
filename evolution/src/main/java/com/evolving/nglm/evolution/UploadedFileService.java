/****************************************************************************
*
*  UploadedFileService.java
*
****************************************************************************/

package com.evolving.nglm.evolution;

import com.evolving.nglm.evolution.EvaluationCriterion.CriterionDataType;
import com.evolving.nglm.evolution.Expression.ExpressionEvaluationException;
import com.evolving.nglm.evolution.GUIManagedObject.IncompleteObject;
import com.evolving.nglm.evolution.GUIManager.GUIManagerException;
import com.evolving.nglm.evolution.SubscriberProfile.ValidateUpdateProfileRequestException;
import com.rii.utilities.FileUtilities;
import com.evolving.nglm.core.AlternateID;
import com.evolving.nglm.core.Deployment;
import com.evolving.nglm.core.JSONUtilities;
import com.evolving.nglm.core.StringKey;
import com.evolving.nglm.core.SystemTime;
import com.evolving.nglm.core.JSONUtilities.JSONUtilitiesException;
import com.evolving.nglm.core.RLMDateUtils;
import com.evolving.nglm.core.SubscriberIDService.SubscriberIDServiceException;
import com.evolving.nglm.core.SubscriberIDService;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.LineIterator;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class UploadedFileService extends GUIService
{
  /*****************************************
  *
  *  configuration
  *
  *****************************************/

  //
  //  logger
  //

  private static final Logger log = LoggerFactory.getLogger(UploadedFileService.class);

  /*****************************************
  *
  *  data
  *
  *****************************************/

  private UploadedFileListener uploadedFileListener = null;
  public static final String basemanagementApplicationID = "101";
  public static final String FILE_WITH_VARIABLES_APPLICATION_ID = "201";
  public static final String DATATYPE_VRIABLE_PATTERN = "\\<(.*?)\\>";  // <String>Name, <Int>Years, <String>Gift

  /*****************************************
  *
  *  constructor
  *
  *****************************************/

  public UploadedFileService(String bootstrapServers, String uploadFileTopic, boolean masterService, UploadedFileListener uploadedFileListener, boolean notifyOnSignificantChange)
  {
    super(bootstrapServers, "UploadedFileService", uploadFileTopic, masterService, getSuperListener(uploadedFileListener), "putUploadedFile", "deleteUploadedFile", notifyOnSignificantChange);
  }

  public UploadedFileService(String bootstrapServers, String uploadFileTopic, boolean masterService)
  {
    this(bootstrapServers, uploadFileTopic, masterService, (UploadedFileListener) null, true);
  }

  //
  //  getSuperListener
  //

  private static GUIManagedObjectListener getSuperListener(UploadedFileListener uploadedFileListener)
  {
    GUIManagedObjectListener superListener = null;
    if (uploadedFileListener != null)
      {
        superListener = new GUIManagedObjectListener()
        {
          @Override public void guiManagedObjectActivated(GUIManagedObject guiManagedObject) { uploadedFileListener.fileActivated((UploadedFile) guiManagedObject); }
          @Override public void guiManagedObjectDeactivated(String guiManagedObjectID, int tenantID) { uploadedFileListener.fileDeactivated(guiManagedObjectID); }
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
    JSONObject result = new JSONObject();
    result.put("id", guiManagedObject.getJSONRepresentation().get("id"));
    result.put("destinationFilename", guiManagedObject.getJSONRepresentation().get("destinationFilename"));
    result.put("fileType", guiManagedObject.getJSONRepresentation().get("fileType"));
    result.put("fileSize", guiManagedObject.getJSONRepresentation().get("fileSize"));
    result.put("userID", guiManagedObject.getJSONRepresentation().get("userID"));
    result.put("accepted", guiManagedObject.getAccepted());
    result.put("valid", guiManagedObject.getAccepted());
    result.put("processing", isActiveGUIManagedObject(guiManagedObject, SystemTime.getCurrentTime()));
    result.put("readOnly", guiManagedObject.getReadOnly());
    return result;
  }

  /*****************************************
  *
  *  getUploadedFiles
  *
  *****************************************/

  public String generateFileID() { return generateGUIManagedObjectID(); }
  public GUIManagedObject getStoredUploadedFile(String fileID) { return getStoredGUIManagedObject(fileID); }
  public GUIManagedObject getStoredUploadedFile(String fileID, boolean includeArchived) { return getStoredGUIManagedObject(fileID, includeArchived); }
  public Collection<GUIManagedObject> getStoredUploadedFiles(int tenantID) { return getStoredGUIManagedObjects(tenantID); }
  public Collection<GUIManagedObject> getStoredUploadedFiles(boolean includeArchived, int tenantID) { return getStoredGUIManagedObjects(includeArchived, tenantID); }
  public boolean isActiveUploadedFile(GUIManagedObject uploadedFileUnchecked, Date date) { return isActiveGUIManagedObject(uploadedFileUnchecked, date); }
  public UploadedFile getActiveUploadedFile(String uploadedFileID, Date date) { return (UploadedFile) getActiveGUIManagedObject(uploadedFileID, date); }
  public Collection<UploadedFile> getActiveUploadedFiles(Date date, int tenantID) { return (Collection<UploadedFile>) getActiveGUIManagedObjects(date, tenantID); }

  /*****************************************
  *
  *  putUploadedFile
  *
  *****************************************/

  public void putUploadedFile(GUIManagedObject guiManagedObject, InputStream inputStrm, String filename, boolean newObject, String userID) throws GUIManagerException, IOException
  {
    //
    //  now
    //

    Date now = SystemTime.getCurrentTime();
    FileOutputStream destFile = null;
    try {

      //
      // store file
      //

      destFile = new FileOutputStream(new File(UploadedFile.OUTPUT_FOLDER+filename));
      byte[] bytes = new byte[1024];
      int readSize = inputStrm.read(bytes);
      while(readSize > 0) {
        byte[] finalArray = new byte[readSize];
        for(int i = 0; i < readSize ; i++) {
          finalArray[i] = bytes[i];           
        }
        destFile.write(finalArray);
        readSize = inputStrm.read(bytes);
      }
    }catch(Exception e) {
      StringWriter stackTraceWriter = new StringWriter();
      e.printStackTrace(new PrintWriter(stackTraceWriter, true));
      log.error("Exception saving file: putUploadedFile API: {}", stackTraceWriter.toString());
      removeGUIManagedObject(guiManagedObject.getGUIManagedObjectID(), now, userID, guiManagedObject.getTenantID());
    }finally {
      if(destFile != null) {
        destFile.flush();
        destFile.close();
      }
    }
    
    //
    // validate 
    //
    
    if (guiManagedObject instanceof UploadedFile)
      {
        UploadedFile uploadededFile = (UploadedFile) guiManagedObject;
        uploadededFile.validate();

        //
        // count segments
        //
        
        if(uploadededFile.getApplicationID().equals(basemanagementApplicationID))
          {
            Map<String, Integer> count = new HashMap<String,Integer>();
            List<String> lines = new ArrayList<String>();
            
            //
            //  read file
            //
            
            try (Stream<String> stream = Files.lines(Paths.get(UploadedFile.OUTPUT_FOLDER + filename)))
              {
                lines = stream.filter(line -> (line != null && !line.trim().isEmpty())).map(String::trim).collect(Collectors.toList());
                for (String line : lines)
                  {
                    String subscriberIDSegementName[] = line.split(Deployment.getUploadedFileSeparator());
                    if (subscriberIDSegementName.length >= 2)
                      {
                      
                        //
                        //  details
                        //
                      
                        String subscriberID = subscriberIDSegementName[0];
                        String segmentName = subscriberIDSegementName[1];
                      
                        //
                        //  count
                        //
                      
                        if (segmentName != null && !segmentName.trim().isEmpty() && subscriberID != null && !subscriberID.trim().isEmpty())
                          {
                            count.put(segmentName.trim(), count.get(segmentName.trim()) != null ? count.get(segmentName.trim()) + 1 : 1);
                          }
                      }
                    else
                      {
                        log.warn("UploadedFileService.putUploadedFile(not two values, skip. line="+line+")");
                      }
                  }
              }
            catch (IOException e)
              {
                log.warn("UploadedFileService.putUploadedFile(problem with file parsing)", e);
              }
            
            //
            // add metadata
            //
            
            ((UploadedFile) guiManagedObject).addMetaData("segmentCounts", JSONUtilities.encodeObject(count));
          }
      }

    //
    //  put
    //

    putGUIManagedObject(guiManagedObject, now, newObject, userID);   
  }
  
  /*****************************************
  *
  *  putUploadedFile
  *
  *****************************************/

  public void putUploadedFileWithVariables(GUIManagedObject guiManagedObject, InputStream inputStrm, String filename, boolean newObject, String userID) throws GUIManagerException, IOException
  {
    //
    //  now
    //

    Date now = SystemTime.getCurrentTime();
    FileOutputStream destFile = null;
    List<GUIManagerException> violations = new ArrayList<GUIManagerException>(3);
    try {

      //
      // store file
      //

      destFile = new FileOutputStream(new File(UploadedFile.OUTPUT_FOLDER+filename));
      byte[] bytes = new byte[1024];
      int readSize = inputStrm.read(bytes);
      while(readSize > 0) {
        byte[] finalArray = new byte[readSize];
        for(int i = 0; i < readSize ; i++) {
          finalArray[i] = bytes[i];           
        }
        destFile.write(finalArray);
        readSize = inputStrm.read(bytes);
      }
    }catch(Exception e) {
      StringWriter stackTraceWriter = new StringWriter();
      e.printStackTrace(new PrintWriter(stackTraceWriter, true));
      log.error("Exception saving file: putUploadedFileWithVariables API: {}", stackTraceWriter.toString());
      removeGUIManagedObject(guiManagedObject.getGUIManagedObjectID(), now, userID, guiManagedObject.getTenantID());
    }finally {
      if(destFile != null) {
        destFile.flush();
        destFile.close();
      }
    }
    
    //
    // validate 
    //
    
    if (guiManagedObject instanceof UploadedFile)
      {
        UploadedFile uploadededFile = (UploadedFile) guiManagedObject;
        try
          {
            uploadededFile.validate();
          } 
        catch (GUIManagerException e1)
          {
            processViolations(violations, e1);
          }
        
        ArrayList<String> fileHeader = new ArrayList<String>();
        ArrayList<JSONObject> valiablesJSON = new ArrayList<JSONObject>();
        
        //
        //  read file
        //
        
        try
          {
            boolean isHeader = true;
            int lineNumber = 0;
            LineIterator lineIterator = FileUtils.lineIterator(new File(UploadedFile.OUTPUT_FOLDER + filename));
            while (lineIterator.hasNext())
              {
                String line = lineIterator.nextLine();
                lineNumber++;
                line = line.trim();
                if (isHeader)
                  {
                    //
                    //  validate and prepare the variables
                    //
                    
                    isHeader = false;
                    String headers[] = line.split(Deployment.getUploadedFileSeparator(), -1);
                    boolean isFirstColumn = true;
                    for (String header : headers)
                      {
                        header = header.trim();
                        if (isFirstColumn)
                          {
                            //
                            //  first column is for alternteID - validate and set alternateID
                            //
                            
                            Map<String, AlternateID> alternateIDs = Deployment.getAlternateIDs();
                            if (alternateIDs.get(header) == null)
                              {
                                processViolations(violations, new GUIManagerException("invalidAlternateId", header));
                              }
                            fileHeader.add(header);
                            ((UploadedFile) guiManagedObject).setCustomerAlternateID(header);
                            isFirstColumn = false;
                          }
                        else
                          {
                            String dataType = getDatatype(header, violations);
                            String variableName = getVaribaleName(header, violations);
                            validateVaribaleName(variableName, violations);
                            HashMap<String, String> variablesDataTypes = new LinkedHashMap<String, String>();
                            variablesDataTypes.put("name", variableName);
                            variablesDataTypes.put("dataType", dataType);
                            valiablesJSON.add(JSONUtilities.encodeObject(variablesDataTypes));
                            fileHeader.add(variableName);
                          }
                      }
                  }
                else
                  {
                    //
                    //  validate the data type value
                    //
                    
                    String values[] = line.split(Deployment.getUploadedFileSeparator(), -1);
                    boolean isFirstColumn = true;
                    int index = 0;
                    for (String value : values)
                      {
                        value = value.trim();
                        if (!isFirstColumn)
                          {
                            String variableName = fileHeader.get(index);
                            String dataType = getDataType(variableName, valiablesJSON);
                            CriterionDataType CriterionDataType = EvaluationCriterion.CriterionDataType.fromExternalRepresentation(dataType);
                            validateValue(variableName, CriterionDataType, value, lineNumber, violations);
                          }
                        else
                          {
                            if (value.isEmpty()) processViolations(violations, new GUIManagerException("badFieldValue", "''(empty)" + "," + ((UploadedFile) guiManagedObject).getCustomerAlternateID() + "," + lineNumber));
                          }
                        isFirstColumn = false;
                        index++;
                      }
                  }
              }
            if (((UploadedFile) guiManagedObject).getNumberOfLines() == null) ((UploadedFile) guiManagedObject).setNumberOfLines(lineNumber - 1);
            if (violations.size() > 0) prepareAndThrowViolations(violations);
          } 
        catch (IOException e)
          {
            log.warn("UploadedFileService.putUploadedFileWithVariables(problem with file parsing)", e);
          }
        
        //
        // variables
        //
        
        HashMap<String, Object> fileVariables = new LinkedHashMap<String, Object>();
        fileVariables.put("fileVariables", JSONUtilities.encodeArray(valiablesJSON));
        ((UploadedFile) guiManagedObject).addMetaData("variables", JSONUtilities.encodeObject(fileVariables));
      }

    //
    //  put
    //

    putGUIManagedObject(guiManagedObject, now, newObject, userID);  
  }
  
  /*****************************************
  *
  *  validateVaribaleName
  *
  *****************************************/
  
  private void validateVaribaleName(String variableName, List<GUIManagerException> violations) throws GUIManagerException
  {
    if (variableName != null)
      {
        for(int i=0; i < variableName.length();i++)
          {
            Character ch = variableName.charAt(i);
            if(Character.isDigit(ch) || Character.isUpperCase(ch))
              {
                GUIManagerException e = new GUIManagerException("invalidVarName", variableName);
                processViolations(violations, e);
                break;
              }
          }
      }
  }

  /*****************************************
  *
  *  getDataType
  *
  *****************************************/
  
  private String getDataType(String variableName, ArrayList<JSONObject> valiablesJSON)
  {
    String result = null;
    for (JSONObject variableJSON : valiablesJSON)
      {
        String varName = JSONUtilities.decodeString(variableJSON, "name", true);
        String dataType = JSONUtilities.decodeString(variableJSON, "dataType", true);
        if (variableName.equals(varName))
          {
            result = dataType;
            break;
          }
      }
    return result;
  }
  
  /*****************************************
  *
  *  validateValue
  *
  *****************************************/

  private Object validateValue(String variableName, CriterionDataType criterionDataType, String rawValue, int lineNumber, List<GUIManagerException> violations) throws GUIManagerException
  {
    String dataType = criterionDataType.getExternalRepresentation();
    Object result = null;
    switch (criterionDataType)
    {
      case StringCriterion:
        result = rawValue;
        break;
        
      case IntegerCriterion:
        try
          {
            result = Integer.parseInt(rawValue);
          }
        catch(Exception ex)
          {
            processViolations(violations, new GUIManagerException("badFieldValue", rawValue + "," + variableName + "," + lineNumber));
          }
        break;
        
      case DoubleCriterion:
        try
          {
            result = Double.parseDouble(rawValue);
          }
      catch(Exception ex)
        {
          processViolations(violations, new GUIManagerException("badFieldValue", rawValue + "," + variableName + "," + lineNumber));
        }
        break;
        
      case DateCriterion:
        try 
        {
          result = GUIManagedObject.parseDateField(rawValue);
        }
      catch(JSONUtilitiesException ex)
        {
          processViolations(violations, new GUIManagerException("badFieldValue", rawValue + "," + variableName + "," + lineNumber));
        }
        break;
        
      case TimeCriterion:
        String[] args = rawValue.split(":");
        if (args.length != 3) 
          {
            processViolations(violations, new GUIManagerException("badFieldValue", rawValue + "," + variableName + "," + lineNumber));
          }
        result = rawValue;
        break;
        
      case BooleanCriterion:
        try 
        {
          result = Boolean.parseBoolean(rawValue);
        }
      catch(JSONUtilitiesException ex)
        {
          processViolations(violations, new GUIManagerException("badFieldValue", rawValue + "," + variableName + "," + lineNumber));
        }
        break;

      default:
        processViolations(violations, new GUIManagerException("datatype not supported", "invalid dataType " + dataType + " for variable " + variableName));
    }
    return result;
  }

  /*****************************************
  *
  *  getVaribaleName
  *
  *****************************************/
  
  private String getVaribaleName(String header, List<GUIManagerException> violations) throws GUIManagerException
  {
    String result = null;
    Pattern pattern = Pattern.compile(DATATYPE_VRIABLE_PATTERN);
    Matcher matcher = pattern.matcher(header);
    if (matcher.find())
      {
        result = header.replaceAll(matcher.group(0), "");
      }
    else
      {
        processViolations(violations, new GUIManagerException("invalidVarDec", header));
      }
    return result;
  }

  /*****************************************
  *
  *  getDatatype
  *
  *****************************************/
  
  private String getDatatype(String header, List<GUIManagerException> violations) throws GUIManagerException
  {
    String result = null;
    Pattern pattern = Pattern.compile(DATATYPE_VRIABLE_PATTERN);
    Matcher matcher = pattern.matcher(header);
    if (matcher.find())
      {
        result = matcher.group(1);
      }
    else
      {
        processViolations(violations, new GUIManagerException("invalidVarDec", header));
      }
    return result;
  }
  
  /*****************************************
  *
  *  processViolations
  *
  *****************************************/
  
  private void processViolations(List<GUIManagerException> violations, GUIManagerException e) throws GUIManagerException
  {
    violations.add(e);
    if (violations.size() > 2) prepareAndThrowViolations(violations);
  }

  /*****************************************
  *
  *  prepareAndThrowViolations
  *
  *****************************************/
  
  private void prepareAndThrowViolations(List<GUIManagerException> violations) throws GUIManagerException
  {
    StringBuilder responseMessageBuilder = new StringBuilder();
    StringBuilder responseParameterBuilder = new StringBuilder();
    boolean firstOne = true;
    for (GUIManagerException violation : violations)
      {
        if (!firstOne)
          {
            responseMessageBuilder.append("|");
            responseParameterBuilder.append("|");
          }
        responseMessageBuilder.append(violation.getMessage());
        responseParameterBuilder.append(violation.getResponseParameter());
        firstOne = false;
      }
    throw new GUIManagerException(responseMessageBuilder.toString(), responseParameterBuilder.toString());
  }

  /*****************************************
  *
  *  deleteUploadedFile
  *
  *****************************************/

  public void deleteUploadedFile(String fileID, String userID, UploadedFile uploadedFile, int tenantID) {
    
    //
    // remove UploadedFile object
    //

    removeGUIManagedObject(fileID, SystemTime.getCurrentTime(), userID, tenantID); 

    //
    // remove file
    //

    File file = new File(UploadedFile.OUTPUT_FOLDER+uploadedFile.getDestinationFilename());
    if(file.exists()) {
      if(file.delete()) {
        log.debug("UploadedFileService.deleteUploadedFile: File has been deleted successfully");
      }else {
        log.warn("UploadedFileService.deleteUploadedFile: File has not been deleted");
      }
    }else {
      log.warn("UploadedFileService.deleteUploadedFile: File does not exist");
    }
  }
  
  
  /*****************************************
  *
  *  putIncompleteUploadedFile
  *
  *****************************************/

  public void putIncompleteUploadedFile(IncompleteObject template, boolean newObject, String userID)
  {
    putGUIManagedObject(template, SystemTime.getCurrentTime(), newObject, userID);
  }

  /*****************************************
   *
   *  changeFileApplicationId
   *
   *****************************************/

  public void changeFileApplicationId(String fileID, String newApplicationID)
  {
    UploadedFile file = (UploadedFile) getStoredUploadedFile(fileID);
    if (file != null)
      {
        // "change" applicationId, not "set"
        if (file.getApplicationID() != null) file.setApplicationID(newApplicationID);
        // as any GUIManagedObject, same information is duplicated, and we use this
        // following one seems to return GUIManager calls...
        if (file.getJSONRepresentation().get("applicationID") != null) file.getJSONRepresentation().put("applicationID", newApplicationID);
        file.setEpoch(file.getEpoch() + 1);// trigger "changes happened"
        putGUIManagedObject(file, file.getUpdatedDate(), false, null);
      } else
      {
        log.warn("UploadedFileService.changeFileApplicationId: File does not exist");
      }
  }
  
 /*****************************************
 *
 *  createFileWithVariableEvents
 *
 *****************************************/

public void createFileWithVariableEvents(UploadedFile file, SubscriberIDService subscriberIDService, KafkaProducer<byte[], byte[]> kafkaProducer)
{
  if (file != null)
    {
      Date now = SystemTime.getCurrentTime();
      String eventTopic = Deployment.getFileWithVariableEventTopic();
      
      //
      // read file
      //

      boolean isHeader = true;
      Map<String, String> headerMap = new LinkedHashMap<String, String>();
      try
        {
          LineIterator lineIterator = FileUtils.lineIterator(new File(UploadedFile.OUTPUT_FOLDER + file.getDestinationFilename()));
          while (lineIterator.hasNext())
            {
              String line = lineIterator.nextLine().trim();
              Map<String, Object> keyValue = new LinkedHashMap<String, Object>();
              if (isHeader)
                {
                  String headers[] = line.split(Deployment.getUploadedFileSeparator(), -1);
                  boolean isFirstColumn = true;
                  for (String header : headers)
                    {
                      header = header.trim();
                      if (isFirstColumn)
                        {
                          headerMap.put(header, "string");
                        } 
                      else
                        {
                          try
                            {
                              String dataType = getDatatype(header, new ArrayList<GUIManagerException>());
                              String variableName = getVaribaleName(header, new ArrayList<GUIManagerException>());
                              headerMap.put(variableName, dataType);
                            } 
                          catch (GUIManagerException e)
                            {
                              e.printStackTrace();
                            }
                        }
                      isFirstColumn = false;
                    }
                } 
              else
                {
                  String values[] = line.split(Deployment.getUploadedFileSeparator(), -1);
                  int index = 0;
                  List<String> headers = headerMap.keySet().stream().collect(Collectors.toList());
                  for (String value : values)
                    {
                      value = value.trim();
                      String varName = headers.get(index);
                      String varDataType = headerMap.get(varName);
                      try
                        {
                          Object val = validateValue(varName, EvaluationCriterion.CriterionDataType.fromExternalRepresentation(varDataType), value, -1, new ArrayList<GUIManagerException>());
                          keyValue.put(varName, val);
                        } 
                      catch (GUIManagerException e)
                        {
                          e.printStackTrace();
                        }
                      index++;
                    }
                  
                  //
                  //  send
                  //
                  
                  String subscriberID = resolveSubscriberID(subscriberIDService, file.getCustomerAlternateID(), (String) keyValue.get(file.getCustomerAlternateID()));
                  if (subscriberID != null)
                    {
                      FileWithVariableEvent fileWithVariableEvent = new FileWithVariableEvent(subscriberID, now, file.getGUIManagedObjectID(), keyValue);
                      kafkaProducer.send(new ProducerRecord<byte[], byte[]>(eventTopic, StringKey.serde().serializer().serialize(eventTopic, new StringKey(fileWithVariableEvent.getSubscriberID())), FileWithVariableEvent.serde().serializer().serialize(eventTopic, fileWithVariableEvent)));
                    }
                }
              isHeader = false;
            }
        } 
      catch (IOException e1)
        {
          e1.printStackTrace();
        }
    }
  else
    {
      log.warn("UploadedFileService.getFileContent: File does not exist");
    }
}
  
/*****************************************
*
*  resolveSubscriberID
*
*****************************************/

private String resolveSubscriberID(SubscriberIDService subscriberIDService, String alternateID, String alternateIDValue)
{
  String result = null;
  try
    {
      result = subscriberIDService.getSubscriberID(alternateID, alternateIDValue);
    }
  catch (SubscriberIDServiceException e)
    {
      log.error("SubscriberIDServiceException can not resolve subscriberID for {} error is {}", alternateIDValue, e.getMessage());
    }
  return result;
}


  /*****************************************
  *
  *  interface OfferListener
  *
  *****************************************/

  public interface UploadedFileListener
  {
    public void fileActivated(UploadedFile uploadedFile);
    public void fileDeactivated(String guiManagedObjectID);
  }

}