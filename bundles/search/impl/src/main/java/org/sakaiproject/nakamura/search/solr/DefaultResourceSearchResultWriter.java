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
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.commons.json.JSONException;
import org.apache.sling.commons.json.io.JSONWriter;
import org.sakaiproject.nakamura.api.lite.Session;
import org.sakaiproject.nakamura.api.lite.StorageClientUtils;
import org.sakaiproject.nakamura.api.lite.content.Content;
import org.sakaiproject.nakamura.api.lite.content.ContentManager;
import org.sakaiproject.nakamura.api.search.solr.Result;
import org.sakaiproject.nakamura.api.search.solr.SolrSearchConstants;
import org.sakaiproject.nakamura.api.search.solr.SolrSearchResultWriter;
import org.sakaiproject.nakamura.api.solr.SafeSolrMap;
import org.sakaiproject.nakamura.util.ExtendedJSONWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jcr.Node;
import javax.jcr.RepositoryException;
import java.util.Collection;
import java.util.Map;

/**
 * Formats user profile node search results
 *
 */

@Component(immediate = true, metatype = true)
@Properties(value = { @Property(name = "service.vendor", value = "The Sakai Foundation"),
    @Property(name = SolrSearchConstants.REG_WRITER_NAMES, value = "FullResource") })
@Service
public class DefaultResourceSearchResultWriter implements SolrSearchResultWriter {

  private static final Logger LOGGER = LoggerFactory
      .getLogger(DefaultResourceSearchBatchResultWriter.class);

  public DefaultResourceSearchResultWriter() {
  }

  public void writeResult(SlingHttpServletRequest request, JSONWriter write, Result result)
      throws JSONException {
    ResourceResolver resolver = request.getResourceResolver();
    write.object();
    write.key("searchdoc");
    @SuppressWarnings({ "unchecked", "rawtypes" })
    Map<String, Collection<Object>> resultProps = new SafeSolrMap(result.getProperties());
    ExtendedJSONWriter.writeValueMap(write, resultProps);
    String path = result.getPath();
    Resource resource = resolver.getResource(path);
    if (resource != null) {
      Content content = resource.adaptTo(Content.class);
      Node node = resource.adaptTo(Node.class);
      if (content != null) {
        write.key("content");
        write.object();
        ExtendedJSONWriter.writeNodeContentsToWriter(write, content);
        write.endObject();
      } else if (node != null) {
        try {
          write.key("node");
          write.object();
          ExtendedJSONWriter.writeNodeContentsToWriter(write, node);
          write.endObject();
        } catch (RepositoryException e) {
          LOGGER.warn(e.getMessage(), e);
        }
      }
    } else {
      try {
        Session session = StorageClientUtils.adaptToSession(resolver.adaptTo(javax.jcr.Session.class));
        ContentManager contentManager= session.getContentManager();
        Content content = contentManager.get(path);
        if (content != null) {
          write.key("content");
          write.object();
          ExtendedJSONWriter.writeNodeContentsToWriter(write, content);
          write.endObject();
        }
      } catch ( Exception e ) {
        LOGGER.warn(e.getMessage(), e);
      }
    }
    write.endObject();
  }
}
