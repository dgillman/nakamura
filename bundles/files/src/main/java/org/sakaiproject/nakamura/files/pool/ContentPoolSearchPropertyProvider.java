/*
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
package org.sakaiproject.nakamura.files.pool;

import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Properties;
import org.apache.felix.scr.annotations.Property;
import org.apache.felix.scr.annotations.Service;
import org.apache.jackrabbit.api.security.user.Authorizable;
import org.apache.jackrabbit.api.security.user.Group;
import org.apache.jackrabbit.api.security.user.UserManager;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.jcr.base.util.AccessControlUtil;
import org.apache.solr.client.solrj.util.ClientUtils;
import org.sakaiproject.nakamura.api.search.SearchPropertyProvider;
import org.sakaiproject.nakamura.api.user.UserConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Iterator;
import java.util.Map;

import javax.jcr.RepositoryException;
import javax.jcr.Session;

@Service
@Component(immediate = true)
@Properties(value = {
    @Property(name = "service.vendor", value = "The Sakai Foundation"),
    @Property(name = "service.description", value = "Provides some extra properties for the PooledContent searches."),
    @Property(name = "sakai.search.provider", value = "PooledContent") })
public class ContentPoolSearchPropertyProvider implements SearchPropertyProvider {

  public static final Logger LOGGER = LoggerFactory
      .getLogger(ContentPoolSearchPropertyProvider.class);

  /**
   * {@inheritDoc}
   *
   * @see org.sakaiproject.nakamura.api.search.SearchPropertyProvider#loadUserProperties(org.apache.sling.api.SlingHttpServletRequest,
   *      java.util.Map)
   */
  public void loadUserProperties(SlingHttpServletRequest request,
      Map<String, String> propertiesMap) {
    String userID = request.getRemoteUser();
    Session session = request.getResourceResolver().adaptTo(Session.class);

    if (!UserConstants.ANON_USERID.equals(userID)) {
      addMyGroups(session, propertiesMap);
    }

  }

  /**
   * Gets all the declared groups for a user and adds an xpath constraint for both
   * managers and viewers to the map.
   *
   * @param session
   * @param propertiesMap
   */
  protected void addMyGroups(Session session, Map<String, String> propertiesMap) {
    String sessionUserId = session.getUserID();

    try {
      UserManager um = AccessControlUtil.getUserManager(session);
      Authorizable auth = um.getAuthorizable(sessionUserId);

      // create the manager and viewer query parameters
      String userId = ClientUtils.escapeQueryChars(sessionUserId);
      StringBuilder managers = new StringBuilder("manager:(").append(userId);
      StringBuilder viewers = new StringBuilder("viewer:(").append(userId);

      // add groups to the parameters
      Iterator<Group> groups = auth.memberOf();
      while (groups.hasNext()) {
        Group group = groups.next();
        String groupId = ClientUtils.escapeQueryChars(group.getID());
        managers.append(" OR ").append(groupId);
        viewers.append(" OR ").append(groupId);
      }

      // cap off the parameters
      managers.append(")");
      viewers.append(")");

      // convert to string for reuse
      String managersParam = ClientUtils.escapeQueryChars(managers.toString());
      String viewersParam = ClientUtils.escapeQueryChars(viewers.toString());

      // add properties for query templates
      propertiesMap.put("_meManagerGroupsNoAnd", managersParam);
      propertiesMap.put("_meViewerGroupsNoAnd", viewersParam);
      propertiesMap.put("_meManagerGroups", " AND " + managersParam);
      propertiesMap.put("_meViewerGroups", " AND " + viewersParam);
    } catch (RepositoryException e) {
      LOGGER.error("Could not get the groups for user [{}].",sessionUserId , e);
    }
  }
}
