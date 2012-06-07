package org.sakaiproject.nakamura.files.search;

import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.resource.ResourceResolver;
import static org.junit.Assert.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import static org.mockito.Mockito.*;

import org.mockito.invocation.InvocationOnMock;
import org.mockito.runners.MockitoJUnitRunner;
import org.mockito.stubbing.Answer;
import org.sakaiproject.nakamura.api.lite.Session;
import org.sakaiproject.nakamura.api.lite.SessionAdaptable;
import org.sakaiproject.nakamura.api.lite.authorizable.AuthorizableManager;
import org.sakaiproject.nakamura.api.lite.authorizable.Group;
import org.sakaiproject.nakamura.api.lite.authorizable.User;
import org.sakaiproject.nakamura.api.search.solr.Query;
import org.sakaiproject.nakamura.api.search.solr.Result;
import org.sakaiproject.nakamura.api.search.solr.SolrSearchResultSet;
import org.sakaiproject.nakamura.api.search.solr.SolrSearchServiceFactory;
import org.sakaiproject.nakamura.templates.velocity.VelocityTemplateService;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

@RunWith(MockitoJUnitRunner.class)
public class CollectionCountServiceImplTest {

  CollectionCountServiceImpl collectionCountService;

  MeManagerViewerSearchPropertyProvider propProvider;

  @Mock
  SolrSearchServiceFactory searchServiceFactory;

  @Mock
  SlingHttpServletRequest request;

  @Mock
  Session session;

  @Mock
  AuthorizableManager authzManager;

  @Mock
  User authzBob;

  @Mock
  Group managerGroup;

  @Mock
  ResourceResolver resolver;

  @Mock
  SolrSearchResultSet resultSet;

  class MyVelocityTemplateService extends VelocityTemplateService {
    MyVelocityTemplateService() throws Exception {
      activate(null);
    }
  }

  @Test
  public void testCollectionCount() throws Exception {

    collectionCountService = new CollectionCountServiceImpl();
    propProvider = new MeManagerViewerSearchPropertyProvider();

    javax.jcr.Session jcrSession = mock(javax.jcr.Session.class, withSettings().extraInterfaces(SessionAdaptable.class));

    when(request.getResourceResolver()).thenReturn(resolver);
    when(resolver.adaptTo(javax.jcr.Session.class)).thenReturn(jcrSession);
    when(((SessionAdaptable) jcrSession).getSession()).thenReturn(session);

    when(request.getRemoteUser()).thenReturn("bob");

    when(session.getAuthorizableManager()).thenReturn(authzManager);
    String[] bobPrincipals = new String[] {"bogus-manager-group"};
    when(authzBob.getId()).thenReturn("bob");
    when(authzBob.getPrincipals()).thenReturn(bobPrincipals);
    when(authzManager.findAuthorizable(eq("bob"))).thenReturn(authzBob);
    String[] managerPrincipals = new String[0];
    when(managerGroup.getId()).thenReturn("bogus-manager-group");
    when(managerGroup.getPrincipals()).thenReturn(managerPrincipals);
    when(authzManager.findAuthorizable(eq("bogus-manager-group"))).thenReturn(managerGroup);

    when(resultSet.getSize()).thenReturn(10l);

    when(searchServiceFactory.getSearchResultSet(any(SlingHttpServletRequest.class), any(Query.class))).thenReturn(resultSet);

    collectionCountService.searchServiceFactory = searchServiceFactory;
    collectionCountService.templateService = new MyVelocityTemplateService();
    collectionCountService.propProvider = propProvider;

    assertEquals(10, collectionCountService.getCollectionCount(request));
  }

}
