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
import org.apache.felix.scr.annotations.Service;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.commons.json.JSONException;
import org.apache.sling.commons.json.io.JSONWriter;
import org.sakaiproject.nakamura.api.search.solr.Result;
import org.sakaiproject.nakamura.api.search.solr.SolrSearchBatchResultWriter;
import org.sakaiproject.nakamura.api.search.solr.SolrSearchConstants;
import org.sakaiproject.nakamura.api.search.solr.SolrSearchUtil;
import org.sakaiproject.nakamura.util.ExtendedJSONWriter;

import java.util.Iterator;

import static org.sakaiproject.nakamura.api.search.solr.SolrSearchConstants.DEFAULT_PAGED_ITEMS;
import static org.sakaiproject.nakamura.api.search.solr.SolrSearchConstants.PARAMS_ITEMS_PER_PAGE;

@Component(immediate = true, metatype=true)
@Properties(value = {
    @Property(name = "service.vendor", value = "The Sakai Foundation"),
    @Property(name = SolrSearchConstants.REG_BATCH_WRITER_NAMES, value = "Resource"),
    @Property(name = SolrSearchBatchResultWriter.DEFAULT_BATCH_WRITER_PROP, boolValue = true)
})
@Service(value = SolrSearchBatchResultWriter.class)
public class DefaultSearchBatchResultWriter implements
    SolrSearchBatchResultWriter {

  /**
   * Component Constructor.
   */
  public DefaultSearchBatchResultWriter() {
  }

  public void writeResults(SlingHttpServletRequest request, JSONWriter write,
      Iterator<Result> iterator) throws JSONException {

    long nitems = SolrSearchUtil.longRequestParameter(request,
        PARAMS_ITEMS_PER_PAGE, DEFAULT_PAGED_ITEMS);

    for (long i = 0; i < nitems && iterator.hasNext(); i++) {
      Result result = iterator.next();
      ExtendedJSONWriter.writeValueMap(write,result.getProperties());
    }
  }

}
