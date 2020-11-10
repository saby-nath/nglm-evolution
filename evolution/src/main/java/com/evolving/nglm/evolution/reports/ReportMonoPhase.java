/*****************************************************************************
 *
 *  ReportMonoPhase.java
 *
 *****************************************************************************/

package com.evolving.nglm.evolution.reports;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Scanner;
import java.util.zip.Deflater;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import org.apache.http.HttpHost;
import org.elasticsearch.action.search.ClearScrollRequest;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.action.search.SearchScrollRequest;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.client.indices.GetIndexRequest;
import org.elasticsearch.common.unit.TimeValue;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.search.Scroll;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.evolving.nglm.core.AlternateID;
import com.evolving.nglm.evolution.Deployment;

public class ReportMonoPhase
{
  private static final Logger log = LoggerFactory.getLogger(ReportMonoPhase.class);

  private String esNode;
  private LinkedHashMap<String, QueryBuilder> esIndex;
  private boolean onlyKeepAlternateIDs; // when we read subscriberprofile, only keep info about alternateIDs
  private boolean onlyKeepAlternateIDsExtended; // when we read subscriberprofile, only keep info about alternateIDs and a little more (for 2 specific reports)
  private List<String> subscriberFields;
  private ReportCsvFactory reportFactory;
  private String csvfile;
  private RestHighLevelClient elasticsearchReaderClient;

  public ReportMonoPhase(String esNode, LinkedHashMap<String, QueryBuilder> esIndex, ReportCsvFactory factory, String csvfile, boolean onlyKeepAlternateIDs, boolean onlyKeepAlternateIDsExtended)
  {
    this(esNode, esIndex, factory, csvfile, onlyKeepAlternateIDs, onlyKeepAlternateIDsExtended, null);
  }

  public ReportMonoPhase(String esNode, LinkedHashMap<String, QueryBuilder> esIndex, ReportCsvFactory factory, String csvfile, boolean onlyKeepAlternateIDs, boolean onlyKeepAlternateIDsExtended, List<String> subscriberFields)
  {
    this.esNode = esNode;
    this.onlyKeepAlternateIDs = onlyKeepAlternateIDs;
    this.onlyKeepAlternateIDsExtended = onlyKeepAlternateIDsExtended;
    // convert index names to lower case, because this is what ElasticSearch expects
    this.esIndex = new LinkedHashMap<>();
    for (Entry<String, QueryBuilder> elem : esIndex.entrySet())
      {
        this.esIndex.put(elem.getKey().toLowerCase(), elem.getValue());
      }
    log.info("Starting ES read with indexes : " + this.esIndex);
    this.reportFactory = factory;
    this.csvfile = csvfile;
    this.subscriberFields = subscriberFields;
    ReportsCommonCode.initializeDateFormats();
  }

  public ReportMonoPhase(String esNode, LinkedHashMap<String, QueryBuilder> esIndex, ReportCsvFactory factory, String csvfile)
  {
    this(esNode, esIndex, factory, csvfile, false, false);
  }

  public ReportMonoPhase(String esNode, LinkedHashMap<String, QueryBuilder> esIndex, ReportCsvFactory factory, String csvfile, boolean onlyKeepAlternateIDs)
  {
    this(esNode, esIndex, factory, csvfile, onlyKeepAlternateIDs, false);
  }

  public enum PERIOD
  {
    DAYS("DAYS"), WEEKS("WEEKS"), MONTHS("MONTHS"), UNKNOWN("UNKNOWN");

    private String externalRepresentation;

    private PERIOD(String externalRepresentation)
    {
      this.externalRepresentation = externalRepresentation;
    }

    public String getExternalRepresentation()
    {
      return externalRepresentation;
    }

    public static PERIOD fromExternalRepresentation(String externalRepresentation)
    {
      for (PERIOD enumeratedValue : PERIOD.values())
        {
          if (enumeratedValue.getExternalRepresentation().equalsIgnoreCase(externalRepresentation))
            return enumeratedValue;
        }
      return UNKNOWN;
    }
  }

  public boolean startOneToOne()
  {
	String indexes = "";
    for (String s : esIndex.keySet())
      indexes += s + " ";
    log.info("Reading data from ES in \"" + indexes + "\" indexes");

    if (csvfile == null)
      {
        log.info("csvfile is null !");
        return false;
      }

    File file = new File(csvfile + ReportUtils.ZIP_EXTENSION);
    if (file.exists())
      {
        log.info(csvfile + " already exists, do nothing");
        return false;
      }
    FileOutputStream fos;
    ZipOutputStream writer;
    try
    {
      fos = new FileOutputStream(file);
      writer = new ZipOutputStream(fos);
      ZipEntry entry = new ZipEntry(new File(csvfile).getName());
      writer.putNextEntry(entry);
      writer.setLevel(Deflater.BEST_SPEED);

      // ESROUTER can have two access points
      // need to cut the string to get at least one
      String node = null;
      int port = 0;
      if (esNode.contains(","))
        {
          String[] split = esNode.split(",");
          if (split[0] != null)
            {
              Scanner s = new Scanner(split[0]);
              s.useDelimiter(":");
              node = s.next();
              port = s.nextInt();
              s.close();
            }
        } else
          {
            Scanner s = new Scanner(esNode);
            s.useDelimiter(":");
            node = s.next();
            port = s.nextInt();
            s.close();
          }

      elasticsearchReaderClient = new RestHighLevelClient(RestClient.builder(new HttpHost(node, port, "http")));

      int i = 0;
      boolean addHeader = true;

      for (Entry<String, QueryBuilder> index : esIndex.entrySet())
        {
          SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder().query(index.getValue());
          if (subscriberFields != null && (i == (esIndex.size()-1))) // subscriber index is always last
            {
              String[] subscriberFieldsArray = subscriberFields.toArray(new String[0]);
              log.debug("Only get these fields from " + index.getKey() + " : " + Arrays.toString(subscriberFieldsArray));
              searchSourceBuilder = searchSourceBuilder.fetchSource(subscriberFieldsArray, null); // only get those fields
            }
          SearchRequest searchRequest = new SearchRequest().source(searchSourceBuilder).allowPartialSearchResults(false);

          // Read all docs from ES, on esIndex[i]
          // Write to topic, one message per document

          Scroll scroll = new Scroll(TimeValue.timeValueSeconds(10L));
          String[] indicesToRead = getIndices(index.getKey());

          //
          //  indicesToRead is blank?
          //

          if (indicesToRead == null || indicesToRead.length == 0)
            {
              i++;
              continue;
            }

          searchRequest.indices(indicesToRead);
          searchRequest.source().size(getScrollSize());
          searchRequest.scroll(scroll);
          SearchResponse searchResponse;
          searchResponse = elasticsearchReaderClient.search(searchRequest, RequestOptions.DEFAULT);

          String scrollId = searchResponse.getScrollId(); // always null
          SearchHit[] searchHits = searchResponse.getHits().getHits();
          if (log.isTraceEnabled()) log.trace("searchHits = " + Arrays.toString(searchHits));
          if (searchHits != null)
            {
              if (log.isTraceEnabled()) 
                {
                  log.trace("getFailedShards = " + searchResponse.getFailedShards());
                  log.trace("getSkippedShards = " + searchResponse.getSkippedShards());
                  log.trace("getTotalShards = " + searchResponse.getTotalShards());
                  log.trace("getTook = " + searchResponse.getTook());
                }
                log.info("for " + Arrays.toString(indicesToRead) + " searchHits.length = " + searchHits.length + " totalHits = " + searchResponse.getHits().getTotalHits());
            }
          boolean alreadyTraced = false;
          while (searchHits != null && searchHits.length > 0)
            {
              if (log.isDebugEnabled()) log.debug("got " + searchHits.length + " hits");
              for (SearchHit searchHit : searchHits)
                {
                  Map<String, Object> sourceMap = searchHit.getSourceAsMap();
                  String key;
                  Map<String, Object> miniSourceMap = sourceMap;
                  if (onlyKeepAlternateIDs && (i == (esIndex.size()-1))) // subscriber index is always last
                    {
                      // size optimize : only keep what is needed for the join later
                      if (!alreadyTraced)
                        {
                          log.info("Keeping only alternate IDs");
                          alreadyTraced = true;
                        }
                      miniSourceMap = new HashMap<>();
                      miniSourceMap.put("subscriberID", sourceMap.get("subscriberID")); // always get "subscriberID"
                      for (AlternateID alternateID : Deployment.getAlternateIDs().values())
                        {
                          String name = alternateID.getName();
                          if (log.isTraceEnabled()) log.trace("Only keep alternateID " + name);
                          if (sourceMap.get(name) == null)
                            {
                              if (log.isTraceEnabled()) log.trace("Unexpected : no value for alternateID " + name);
                            }
                          else
                            {
                              miniSourceMap.put(name, sourceMap.get(name));
                            }
                        }
                      if (onlyKeepAlternateIDsExtended)
                        {
                          miniSourceMap.put("pointBalances", sourceMap.get("pointBalances")); // keep this (for Customer Point Details report)
                          miniSourceMap.put("loyaltyPrograms", sourceMap.get("loyaltyPrograms")); // keep this (for Loyalty Program Customer States report)
                        }
                      sourceMap = null; // to help GC do its job
                    }

                  // We have in miniSourceMap the maping for this ES line, now write it to csv
                  
                	  addHeader &= reportFactory.dumpElementToCsvMono(miniSourceMap, writer, addHeader);
                }

              SearchScrollRequest scrollRequest = new SearchScrollRequest(scrollId);
              scrollRequest.scroll(scroll);
              searchResponse = elasticsearchReaderClient.searchScroll(scrollRequest, RequestOptions.DEFAULT);
              scrollId = searchResponse.getScrollId();
              searchHits = searchResponse.getHits().getHits();
            }
          log.debug("Finished with index " + i);
          if (scrollId != null)
            {
              ClearScrollRequest clearScrollRequest = new ClearScrollRequest();
              clearScrollRequest.addScrollId(scrollId);
              elasticsearchReaderClient.clearScroll(clearScrollRequest, RequestOptions.DEFAULT);
            }
          i++;
        }
    
    	elasticsearchReaderClient.close();
    	writer.flush();
    	writer.closeEntry();
    	writer.close();
    }
    catch (IOException e1)
    {
      log.info("Error when creating " + csvfile + " : " + e1.getLocalizedMessage());
      return false;
    }
    log.info("Finished producing " + csvfile + ReportUtils.ZIP_EXTENSION);
    return true;
  }
  
  public final boolean startOneToOne(boolean multipleFile)
  {
    if (!multipleFile)
      {
		return startOneToOne();
      }
    else
      {
        if (csvfile == null)
          {
            log.info("csvfile is null !");
            return false;
          }

        File file = new File(csvfile + ReportUtils.ZIP_EXTENSION);
        if (file.exists())
          {
            log.info(csvfile + " already exists, do nothing");
            return false;
          }

        // holding the zip writers of tmp files
        Map<String,ZipOutputStream> tmpZipFiles = new HashMap<>();
        
        // ESROUTER can have two access points
        // need to cut the string to get at least one
        String node = null;
        int port = 0;
        if (esNode.contains(","))
          {
            String[] split = esNode.split(",");
            if (split[0] != null)
              {
                Scanner s = new Scanner(split[0]);
                s.useDelimiter(":");
                node = s.next();
                port = s.nextInt();
                s.close();
              }
          }
        else
          {
            Scanner s = new Scanner(esNode);
            s.useDelimiter(":");
            node = s.next();
            port = s.nextInt();
            s.close();
          }

        elasticsearchReaderClient = new RestHighLevelClient(RestClient.builder(new HttpHost(node, port, "http")));

        int i = 0;

        for (Entry<String, QueryBuilder> index : esIndex.entrySet())
          {

            SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder().query(index.getValue());
            if (subscriberFields != null && (i == (esIndex.size()-1))) // subscriber index is always last
              {
                String[] subscriberFieldsArray = subscriberFields.toArray(new String[0]);
                log.debug("Only get these fields from " + index.getKey() + " : " + Arrays.toString(subscriberFieldsArray));
                searchSourceBuilder = searchSourceBuilder.fetchSource(subscriberFieldsArray, null); // only get those fields
              }
            SearchRequest searchRequest = new SearchRequest().source(searchSourceBuilder).allowPartialSearchResults(false);

            try
            {
              // Read all docs from ES, on esIndex[i]
              // Write to topic, one message per document

              Scroll scroll = new Scroll(TimeValue.timeValueSeconds(10L));
              String[] indicesToRead = getIndices(index.getKey());

              //
              //  indicesToRead is blank?
              //

              if (indicesToRead == null || indicesToRead.length == 0)
                {
                  i++;
                  continue;
                }

              searchRequest.indices(indicesToRead);
              searchRequest.source().size(getScrollSize());
              searchRequest.scroll(scroll);
              SearchResponse searchResponse;
              searchResponse = elasticsearchReaderClient.search(searchRequest, RequestOptions.DEFAULT);

              String scrollId = searchResponse.getScrollId(); // always null
              SearchHit[] searchHits = searchResponse.getHits().getHits();
              if (log.isTraceEnabled()) log.trace("searchHits = " + Arrays.toString(searchHits));
              if (searchHits != null)
                {
                  if (log.isTraceEnabled()) 
                    {
                      log.trace("getFailedShards = " + searchResponse.getFailedShards());
                      log.trace("getSkippedShards = " + searchResponse.getSkippedShards());
                      log.trace("getTotalShards = " + searchResponse.getTotalShards());
                      log.trace("getTook = " + searchResponse.getTook());
                    }
                  log.info("for " + Arrays.toString(indicesToRead) + " searchHits.length = " + searchHits.length + " totalHits = " + searchResponse.getHits().getTotalHits());
                }
              boolean alreadyTraced = false;
              while (searchHits != null && searchHits.length > 0)
                {

                  Map<String, List<Map<String, Object>>> records = new HashMap<String, List<Map<String, Object>>>();

                  if (log.isDebugEnabled()) log.debug("got " + searchHits.length + " hits");
                  for (SearchHit searchHit : searchHits)
                    {
                      Map<String, Object> sourceMap = searchHit.getSourceAsMap();
                      String key;
                      Map<String, Object> miniSourceMap = sourceMap;
                      if (onlyKeepAlternateIDs && (i == (esIndex.size()-1))) // subscriber index is always last
                        {
                          // size optimize : only keep what is needed for the join later
                          if (!alreadyTraced)
                            {
                              log.info("Keeping only alternate IDs");
                              alreadyTraced = true;
                            }
                          miniSourceMap = new HashMap<>();
                          miniSourceMap.put("subscriberID", sourceMap.get("subscriberID")); // always get "subscriberID"
                          for (AlternateID alternateID : Deployment.getAlternateIDs().values())
                            {
                              String name = alternateID.getName();
                              if (log.isTraceEnabled())log.trace("Only keep alternateID " + name);
                              if (sourceMap.get(name) == null)
                                {
                                  if (log.isTraceEnabled())log.trace("Unexpected : no value for alternateID " + name);
                                }
                              else
                                {
                                  miniSourceMap.put(name, sourceMap.get(name));
                                }
                            }
                          if (onlyKeepAlternateIDsExtended)
                            {
                              miniSourceMap.put("pointBalances", sourceMap.get("pointBalances")); // keep this (for Customer Point Details report)
                              miniSourceMap.put("loyaltyPrograms", sourceMap.get("loyaltyPrograms")); // keep this (for Loyalty Program Customer States report)
                            }
                          sourceMap = null; // to help GC do its job
                        }

                      // We have in miniSourceMap the maping for this ES line, now write it to csv
                      Map<String, List<Map<String, Object>>> splittedReportElements = reportFactory.getSplittedReportElementsForFileMono(miniSourceMap);
                      for (String fileKey : splittedReportElements.keySet())
                        {
                          if (records.containsKey(fileKey))
                            {
                              records.get(fileKey).addAll(splittedReportElements.get(fileKey));
                            }
                          else
                            {
                              List<Map<String, Object>> elements = new ArrayList<Map<String, Object>>();
                              elements.addAll(splittedReportElements.get(fileKey));
                              records.put(fileKey, elements);
                            }
                        }
                    }
                  SearchScrollRequest scrollRequest = new SearchScrollRequest(scrollId);
                  scrollRequest.scroll(scroll);
                  searchResponse = elasticsearchReaderClient.searchScroll(scrollRequest, RequestOptions.DEFAULT);
                  scrollId = searchResponse.getScrollId();
                  searchHits = searchResponse.getHits().getHits();

                  // write each final archive zip entry into tmp files first (will be put in 1 archive at the end
                  try
                  {
                    //
                    //  ZIP
                    //

                    for (String key : records.keySet())
                    {

                      String tmpFileName=file+"."+key+".tmp";
                      boolean addHeader = false;
                      ZipOutputStream writer = tmpZipFiles.get(tmpFileName);
                      if(writer==null){
                        addHeader = true;
                        FileOutputStream fos = new FileOutputStream(tmpFileName);
                        writer = new ZipOutputStream(fos);
                        String dataFile[] = csvfile.split("[.]");
                        String dataFileName = dataFile[0] + "_" + key;
                        String zipEntryName = new File(dataFileName + "." + dataFile[1]).getName();
                        ZipEntry entry = new ZipEntry(zipEntryName);
                        writer.putNextEntry(entry);
                        writer.setLevel(Deflater.BEST_SPEED);
                        tmpZipFiles.put(tmpFileName,writer);
                      }
                      for (Map<String, Object> lineMap : records.get(key))
                      {
                        reportFactory.dumpLineToCsv(lineMap, writer, addHeader);
                        addHeader = false;
                      }
                    }

                  }
                  catch (Exception e)
                  {
                    log.error("Error in file {} ", e);
                  }

                }
              log.debug("Finished with index " + i);
              if (scrollId != null)
                {
                  ClearScrollRequest clearScrollRequest = new ClearScrollRequest();
                  clearScrollRequest.addScrollId(scrollId);
                  elasticsearchReaderClient.clearScroll(clearScrollRequest, RequestOptions.DEFAULT);
                }
          } catch (IOException e) { e.printStackTrace(); }
          i++;

        }
        
        try
          {
            elasticsearchReaderClient.close();
          }
        catch (IOException e)
          {
            log.info("Exception while closing ElasticSearch client " + e.getLocalizedMessage());
          }
        
        // close all open tmp writer
        for(Entry<String,ZipOutputStream> tmpWriter : tmpZipFiles.entrySet()){
          try {
            tmpWriter.getValue().flush();
            tmpWriter.getValue().close();
          } catch (IOException e) {
            log.warn("Error while closing tmp file {} ", tmpWriter.getKey());
          }
        }

        // write final file from tmp

        try {

          FileOutputStream fos = new FileOutputStream(file);
          ZipOutputStream writer = new ZipOutputStream(fos);

          for(String tmpFile:tmpZipFiles.keySet()){

            // open tmp file
            FileInputStream fis = new FileInputStream(tmpFile);
            ZipInputStream reader = new ZipInputStream(fis);

            writer.putNextEntry(reader.getNextEntry());
            writer.setLevel(Deflater.BEST_SPEED);

            // copy to final file
            int length;
            byte[] bytes = new byte[5*1024*1024];//5M buffer
            while ((length=reader.read(bytes))!=-1) writer.write(bytes,0,length);

            // close and delete tmp
            reader.closeEntry();
            reader.close();
            new File(tmpFile).delete();
          }
          writer.flush();
          writer.closeEntry();
          writer.close();

        } catch (IOException e) {
          log.error("Error while concatenating tmp files", e);
        }

      }
    log.info("Finished producing " + csvfile + ReportUtils.ZIP_EXTENSION);
    return true;
  }

  private String[] getIndices(String key)
  {
    StringBuilder existingIndexes = new StringBuilder();
    boolean firstEntry = true;
    
    //
    // blank key 
    //
    
    if (key == null || key.isEmpty()) return null;
    
    for (String index : key.split(","))
      {
        
        //
        //  wildcard
        //
        
        if(index.endsWith("*")) 
          {
            if (!firstEntry) existingIndexes.append(",");
            existingIndexes.append(index); 
            firstEntry = false;
            continue;
          }
        
        //
        //  indices-exists
        //
        
        GetIndexRequest request = new GetIndexRequest(index);
        request.local(false); 
        request.humanReadable(true); 
        request.includeDefaults(false); 
        try
          {
            boolean exists = elasticsearchReaderClient.indices().exists(request, RequestOptions.DEFAULT);
            if (exists) 
              {
                if (!firstEntry) existingIndexes.append(",");
                existingIndexes.append(index);
                firstEntry = false;
              }
            else
              {
                log.debug("{} index does not exists - record will not be in report", index);
              }
          } 
        catch (IOException e)
          {
            e.printStackTrace();
          }
      }
    log.debug("index to be read {}", existingIndexes.toString());
    if (firstEntry) // nothing got added
      {
        return null;
      }
    else
      {
        return existingIndexes.toString().split(",");
      }
  }

  private int getScrollSize()
  {
    int scrollSize = Deployment.getElasticSearchScrollSize();
    if (scrollSize == 0)
      {
        scrollSize = ReportUtils.DEFAULT_ELASTIC_SEARCH_SCROLL_SIZE;
      }
    log.trace("Using " + scrollSize + " as scroll size in Elastic Search");
    return scrollSize;
  }


}
