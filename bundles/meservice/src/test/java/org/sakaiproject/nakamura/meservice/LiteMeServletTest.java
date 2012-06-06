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
package org.sakaiproject.nakamura.meservice;
import static junit.framework.Assert.assertEquals;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.SlingHttpServletResponse;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.commons.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;

import static junit.framework.Assert.assertTrue;
import static org.mockito.Mockito.*;
import org.mockito.runners.MockitoJUnitRunner;
import org.sakaiproject.nakamura.api.connections.ConnectionManager;
import org.sakaiproject.nakamura.api.lite.Repository;
import org.sakaiproject.nakamura.api.lite.Session;
import org.sakaiproject.nakamura.api.lite.SessionAdaptable;
import org.sakaiproject.nakamura.api.lite.authorizable.Authorizable;
import org.sakaiproject.nakamura.api.lite.authorizable.AuthorizableManager;
import org.sakaiproject.nakamura.api.message.LiteMessagingService;
import org.sakaiproject.nakamura.api.messagebucket.MessageBucketService;
import org.sakaiproject.nakamura.api.search.solr.Query;
import org.sakaiproject.nakamura.api.search.solr.SolrSearchResultSet;
import org.sakaiproject.nakamura.api.search.solr.SolrSearchServiceFactory;
import org.sakaiproject.nakamura.api.user.BasicUserInfoService;
import org.sakaiproject.nakamura.api.user.UserConstants;
import org.sakaiproject.nakamura.user.BasicUserInfoServiceImpl;
import org.sakaiproject.nakamura.lite.BaseMemoryRepository;

import com.google.common.collect.ImmutableMap;

/**
 *
 */
@RunWith(MockitoJUnitRunner.class)
public class LiteMeServletTest {
  LiteMeServlet meServlet;
  @Mock
  LiteMessagingService messagingService;

  @Mock
  ConnectionManager connectionManager;

  @Mock
  MessageBucketService messageBucketService;

  @Mock
  SolrSearchServiceFactory searchServiceFactory;

  @Mock
  BasicUserInfoService basicUserInfoService;

  @Before
  public void setUp() {
    meServlet = new LiteMeServlet();
    meServlet.messagingService = messagingService;
    meServlet.connectionManager = connectionManager;
    meServlet.messageBucketService = messageBucketService;
    meServlet.searchServiceFactory = searchServiceFactory;
    meServlet.basicUserInfoService = basicUserInfoService;
    
  }

  @Test
  public void testNothingForNow() {
    // I assume someone will add test coverage in the future; otherwise I would just
    // remove the entire class.
  }

  @Test
  public void getCustomLocale() {
    // common la_CO format
    Map<String, Object> props = ImmutableMap.<String, Object>of("locale", Locale.GERMANY.toString());
    Locale locale = meServlet.getLocale(props);
    assertEquals(locale.getLanguage(), Locale.GERMANY.getLanguage());
    assertEquals(locale.getCountry(), Locale.GERMANY.getCountry());

    // the funky format that prompted all this es_419
    props = ImmutableMap.<String, Object>of("locale", "es_419");
    locale = meServlet.getLocale(props);
    assertEquals(locale.getLanguage(), "es");
    assertEquals(locale.getCountry(), "419");
  }

  @Test
  public void getCustomLanguage() {
    Map<String, Object> props = ImmutableMap.<String, Object>of("locale", Locale.GERMAN.toString());
    Locale locale = meServlet.getLocale(props);
    assertEquals(locale.getLanguage(), Locale.GERMAN.getLanguage());
    assertEquals(locale.getCountry(), "");
  }

  @Test
  public void getInvalidLocale() {
    // this is of bad form
    Map<String, Object> props = ImmutableMap.<String, Object>of("locale", "bad_WRONG");
    Locale locale = meServlet.getLocale(props);
    assertEquals(locale.getLanguage(), Locale.US.getLanguage());
    assertEquals(locale.getCountry(), Locale.US.getCountry());

    // this is of bad form
    props = ImmutableMap.<String, Object>of("locale", "123_456");
    locale = meServlet.getLocale(props);
    assertEquals(locale.getLanguage(), Locale.US.getLanguage());
    assertEquals(locale.getCountry(), Locale.US.getCountry());

    // this is of valid form but not a real locale
    props = ImmutableMap.<String, Object>of("locale", "xx_XX");
    locale = meServlet.getLocale(props);
    assertEquals(locale.getLanguage(), "xx");
    assertEquals(locale.getCountry(), "XX");

    // this is just weird
    props = ImmutableMap.<String, Object>of("locale", "_");
    locale = meServlet.getLocale(props);
    assertEquals(locale.getLanguage(), Locale.US.getLanguage());
    assertEquals(locale.getCountry(), Locale.US.getCountry());

    // this is jibberish
    props = ImmutableMap.<String, Object>of("locale", "jibberish");
    locale = meServlet.getLocale(props);
    assertEquals(locale.getLanguage(), Locale.US.getLanguage());
    assertEquals(locale.getCountry(), Locale.US.getCountry());

    // this is for utf8 testing
    props = ImmutableMap.<String, Object>of("locale", "ŠšĐđČčĆćŽž");
    locale = meServlet.getLocale(props);
    assertEquals(locale.getLanguage(), Locale.US.getLanguage());
    assertEquals(locale.getCountry(), Locale.US.getCountry());
  }

  private void assertJSONKeys (Set<String> expectedKeys, JSONObject json) throws Exception {
    Iterator<String> keyIt = json.keys();
    while(keyIt.hasNext()) {
      String key = keyIt.next();
      assertTrue(expectedKeys.contains(key));
      expectedKeys.remove(key);
    }

    assertTrue(expectedKeys.isEmpty());
  }

  @Test
  public void testValidateMeFeedContent() throws Exception {

    SlingHttpServletRequest request = mock (SlingHttpServletRequest.class);
    SlingHttpServletResponse response = mock (SlingHttpServletResponse.class);
    StringWriter writer = new StringWriter();
    PrintWriter wrappedWriter = new PrintWriter(writer);

    when(response.getWriter()).thenReturn(wrappedWriter);

    when(request.getParameter(eq("uid"))).thenReturn("testUser");

    Repository repository = new BaseMemoryRepository().getRepository();
    Session session = repository.loginAdministrative();
    AuthorizableManager am = session.getAuthorizableManager();

    Map<String, Object> userProps = new HashMap<String, Object>();

    userProps.put("firstName", "first");
    userProps.put("lastName", "last");
    userProps.put("email", "email");
    userProps.put("homePath", "path");

    Map<String, Object> counts = new HashMap<String, Object> ();

    counts.put(UserConstants.CONTENT_ITEMS_PROP, 100);
    counts.put(UserConstants.CONTACTS_PROP, 50);
    counts.put(UserConstants.GROUP_MEMBERSHIPS_PROP, 5);

    userProps.put(UserConstants.COUNTS_PROP, counts);

    HashMap<String, Object> picInfo = new HashMap<String, Object>();

    picInfo.put("name", "256x256_tmp1336495195251.jpg");
    picInfo.put("original", "tmp1336495195251.jpg");
    picInfo.put("selectedx1", 30);
    picInfo.put("selectedy1", 108);
    picInfo.put("selectedx2", 94);
    picInfo.put("selectedy2", 172);

    userProps.put("picture", picInfo);
    am.createUser("testUser", "testUser", "password", userProps);

    ResourceResolver resolver = mock(ResourceResolver.class);
    javax.jcr.Session jcrSession = mock(javax.jcr.Session.class, withSettings().extraInterfaces(SessionAdaptable.class));

    when(request.getResourceResolver()).thenReturn(resolver);
    when(resolver.adaptTo(javax.jcr.Session.class)).thenReturn(jcrSession);
    when(((SessionAdaptable) jcrSession).getSession()).thenReturn(session);

    when(basicUserInfoService.getProperties(any(Authorizable.class))).thenReturn(userProps);

    when(messagingService.getFullPathToStore(anyString(), any(Session.class))).thenReturn("bogusPath");

    SolrSearchResultSet msgSet = mock(SolrSearchResultSet.class);
    when(msgSet.getSize()).thenReturn((long)5);
    when(searchServiceFactory.getSearchResultSet(any(SlingHttpServletRequest.class), any(Query.class), anyBoolean())).thenReturn(
       msgSet);

    meServlet.doGet(request, response);

    writer.getBuffer();

    JSONObject json = new JSONObject(writer.getBuffer().toString());

    HashSet<String> expectedKeys = new HashSet<String>();

    expectedKeys.add("homePath");
    expectedKeys.add("userid");
    expectedKeys.add("profile");
    expectedKeys.add("locale");
    expectedKeys.add("counts");

    assertJSONKeys(expectedKeys, json);

    JSONObject profile = json.getJSONObject("profile");

    expectedKeys = new HashSet<String>();

    expectedKeys.add("picture");
    expectedKeys.add("firstName");
    expectedKeys.add("lastName");
    expectedKeys.add("email");

    assertJSONKeys(expectedKeys, profile);

    JSONObject picture = profile.getJSONObject("picture");

    expectedKeys = new HashSet<String>();
    expectedKeys.add("name");
    expectedKeys.add("original");
    expectedKeys.add("selectedx1");
    expectedKeys.add("selectedy1");
    expectedKeys.add("selectedx2");
    expectedKeys.add("selectedy2");

    assertJSONKeys(expectedKeys, picture);

    JSONObject locale = json.getJSONObject("locale");

    expectedKeys = new HashSet<String>();
    expectedKeys.add("country");
    expectedKeys.add("displayCountry");
    expectedKeys.add("displayLanguage");
    expectedKeys.add("displayName");
    expectedKeys.add("ISO3Country");
    expectedKeys.add("ISO3Language");
    expectedKeys.add("language");
    expectedKeys.add("timezone");

    assertJSONKeys(expectedKeys, locale);

    JSONObject timezone = locale.getJSONObject("timezone");

    expectedKeys = new HashSet<String>();
    expectedKeys.add("name");
    expectedKeys.add("GMT");

    assertJSONKeys(expectedKeys, timezone);

    JSONObject countsJSON = json.getJSONObject("counts");

    expectedKeys = new HashSet<String>();
    expectedKeys.add("content");
    expectedKeys.add("contacts");
    expectedKeys.add("memberships");
    //expectedKeys.add("collections");
    expectedKeys.add("unreadmessages");

    assertJSONKeys(expectedKeys, countsJSON);

  }

}
