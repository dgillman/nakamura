package org.sakaiproject.nakamura.files.search;

import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Properties;
import org.apache.felix.scr.annotations.Property;
import org.apache.felix.scr.annotations.Reference;
import org.apache.felix.scr.annotations.Service;
import org.apache.sling.api.SlingHttpServletRequest;
import org.sakaiproject.nakamura.api.search.solr.Query;
import org.sakaiproject.nakamura.api.search.solr.SolrSearchBatchResultProcessor;
import org.sakaiproject.nakamura.api.search.solr.SolrSearchConstants;
import org.sakaiproject.nakamura.api.search.solr.SolrSearchException;
import org.sakaiproject.nakamura.api.search.solr.SolrSearchResultSet;
import org.sakaiproject.nakamura.api.search.solr.SolrSearchServiceFactory;

@Component(immediate = true, metatype = true)
@Properties(value = { @Property(name = "service.vendor", value = "The Sakai Foundation"),
    @Property(name = SolrSearchConstants.REG_BATCH_PROCESSOR_NAMES, value = "LiteFiles") })
@Service(value = SolrSearchBatchResultProcessor.class)
public class LiteFileSearchBatchResultProcessor implements SolrSearchBatchResultProcessor {

  @Reference
  protected SolrSearchServiceFactory searchServiceFactory;

  public LiteFileSearchBatchResultProcessor() {
  }

  /**
   * The non component constructor
   * @param searchServiceFactory
   */
  public LiteFileSearchBatchResultProcessor(SolrSearchServiceFactory searchServiceFactory) {
    if ( searchServiceFactory == null ) {
      throw new IllegalArgumentException("Search Service Factory must be set when not using as a component");
    }
    this.searchServiceFactory = searchServiceFactory;
  }

  @Override
  public SolrSearchResultSet getSearchResultSet(SlingHttpServletRequest request, Query query)
     throws SolrSearchException {
    return searchServiceFactory.getSearchResultSet(request, query);
  }
}
