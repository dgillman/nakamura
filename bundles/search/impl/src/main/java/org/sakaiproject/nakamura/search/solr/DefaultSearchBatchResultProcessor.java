/**
 * Licensed to the Sakai Foundation (SF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The SF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package org.sakaiproject.nakamura.search.solr;

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

@Component(immediate = true, metatype=true)
@Properties(value = {
    @Property(name = "service.vendor", value = "The Sakai Foundation"),
    @Property(name = SolrSearchConstants.REG_BATCH_PROCESSOR_NAMES, value = "Resource"),
    @Property(name = SolrSearchBatchResultProcessor.DEFAULT_BATCH_PROCESSOR_PROP, boolValue = true)
})
@Service(value = SolrSearchBatchResultProcessor.class)
public class DefaultSearchBatchResultProcessor implements
    SolrSearchBatchResultProcessor {

  @Reference
  protected SolrSearchServiceFactory searchServiceFactory;

  /**
   * The non component constructor
   * @param searchServiceFactory
   */
  DefaultSearchBatchResultProcessor(SolrSearchServiceFactory searchServiceFactory) {
    if ( searchServiceFactory == null ) {
      throw new IllegalArgumentException("Search Service Factory must be set when not using as a component");
    }
    this.searchServiceFactory = searchServiceFactory;
  }


  /**
   * Component Constructor.
   */
  public DefaultSearchBatchResultProcessor() {
  }

  public SolrSearchResultSet getSearchResultSet(SlingHttpServletRequest request, Query query) throws SolrSearchException {
    return searchServiceFactory.getSearchResultSet(request, query);  }

}
