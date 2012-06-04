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
package org.sakaiproject.nakamura.search.processors;

import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Properties;
import org.apache.felix.scr.annotations.Property;
import org.apache.felix.scr.annotations.Reference;
import org.apache.felix.scr.annotations.Service;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.resource.ValueMap;
import org.apache.sling.api.wrappers.ValueMapDecorator;
import org.apache.sling.commons.json.JSONException;
import org.apache.sling.commons.json.io.JSONWriter;
import org.sakaiproject.nakamura.api.lite.Session;
import org.sakaiproject.nakamura.api.lite.StorageClientException;
import org.sakaiproject.nakamura.api.lite.StorageClientUtils;
import org.sakaiproject.nakamura.api.lite.accesscontrol.AccessDeniedException;
import org.sakaiproject.nakamura.api.lite.authorizable.Authorizable;
import org.sakaiproject.nakamura.api.lite.authorizable.AuthorizableManager;
import org.sakaiproject.nakamura.api.lite.authorizable.User;
import org.sakaiproject.nakamura.api.lite.content.Content;
import org.sakaiproject.nakamura.api.search.solr.Result;
import org.sakaiproject.nakamura.api.search.solr.SolrSearchConstants;
import org.sakaiproject.nakamura.api.search.solr.SolrSearchResultWriter;
import org.sakaiproject.nakamura.api.user.BasicUserInfoService;
import org.sakaiproject.nakamura.util.DateUtils;
import org.sakaiproject.nakamura.util.ExtendedJSONWriter;

import java.util.Date;


@Component(label = "GroupJoinRequestSearchResultWriter", description = "Formatter for group join request search results.")
@Properties({
    @Property(name = "service.vendor", value = "The Sakai Foundation"),
    @Property(name = SolrSearchConstants.REG_WRITER_NAMES, value = "GroupJoinRequest") })
@Service
public class GroupJoinRequestSearchResultWriter implements SolrSearchResultWriter {

  @Reference
  private BasicUserInfoService basicUserInfoService;

  /**
   * {@inheritDoc}
   *
   * @see org.sakaiproject.nakamura.api.search.SearchResultProcessor#writeNode(org.apache.sling.api.SlingHttpServletRequest,
   *      org.apache.sling.commons.json.io.JSONWriter,
   *      org.sakaiproject.nakamura.api.search.Aggregator, javax.jcr.query.Row)
   */
  public void writeResult(SlingHttpServletRequest request, JSONWriter write, Result result)
      throws JSONException {
    javax.jcr.Session jcrSession = request.getResourceResolver().adaptTo(javax.jcr.Session.class);
    Session session = StorageClientUtils.adaptToSession(jcrSession);
    String userId = (String) result.getFirstValue(User.NAME_FIELD);
    if (userId != null) {
      try {
        AuthorizableManager authMgr = session.getAuthorizableManager();
        Authorizable auth = authMgr.findAuthorizable(userId);

        if (auth != null) {
          write.object();
          ValueMap map = new ValueMapDecorator(basicUserInfoService.getProperties(auth));
          ((ExtendedJSONWriter)write).valueMapInternals(map);
          write.key("_created");
          Long created = (Long) result.getFirstValue(Content.CREATED_FIELD);
          Date createdDate = null;
          if ( created != null) {
            createdDate = new Date(created);
          } else {
            createdDate = new Date();
          }
          write.value(DateUtils.iso8601(createdDate));
          write.endObject();
        }
      } catch (StorageClientException e) {
        throw new RuntimeException(e.getMessage(), e);
      } catch (AccessDeniedException e) {
      } /*catch (RepositoryException e) {
        throw new RuntimeException(e.getMessage(), e);
      }*/
    }
  }

}
