package org.sakaiproject.nakamura.api.search.solr;

import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.commons.json.JSONException;
import org.apache.sling.commons.json.io.JSONWriter;

public interface SolrSearchJsonResultWriter {

  public static final String DEFAULT_WRITER_PROP = "sakai.solr.search.writer.default";

  void writeResult(SlingHttpServletRequest request, JSONWriter write, Result result)
      throws JSONException;

}
