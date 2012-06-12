package org.sakaiproject.nakamura.api.search.solr;

import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.commons.json.JSONException;
import org.apache.sling.commons.json.io.JSONWriter;

import java.util.Iterator;

public interface SolrSearchBatchJsonResultWriter {

  public static final String DEFAULT_BATCH_WRITER_PROP = "sakai.solr.search.batch.writer.default";

  void writeResults(SlingHttpServletRequest request, JSONWriter write, Iterator<Result> iterator)
      throws JSONException;

}
