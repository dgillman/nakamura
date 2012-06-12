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

import com.google.common.collect.Maps;
import com.google.common.collect.Sets;

import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.felix.scr.annotations.Activate;
import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Property;
import org.apache.felix.scr.annotations.Reference;
import org.apache.felix.scr.annotations.Service;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.commons.osgi.PropertiesUtil;
import org.apache.solr.client.solrj.SolrQuery;
import org.apache.solr.client.solrj.SolrQuery.ORDER;
import org.apache.solr.client.solrj.SolrRequest.METHOD;
import org.apache.solr.client.solrj.SolrServer;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.client.solrj.response.QueryResponse;
import org.apache.solr.common.params.CommonParams;
import org.apache.solr.common.params.GroupParams;
import org.sakaiproject.nakamura.api.lite.Session;
import org.sakaiproject.nakamura.api.lite.StorageClientException;
import org.sakaiproject.nakamura.api.lite.StorageClientUtils;
import org.sakaiproject.nakamura.api.lite.accesscontrol.AccessDeniedException;
import org.sakaiproject.nakamura.api.lite.authorizable.Authorizable;
import org.sakaiproject.nakamura.api.lite.authorizable.AuthorizableManager;
import org.sakaiproject.nakamura.api.lite.authorizable.Group;
import org.sakaiproject.nakamura.api.lite.authorizable.User;
import org.sakaiproject.nakamura.api.search.DeletedPathsService;
import org.sakaiproject.nakamura.api.search.SearchUtil;
import org.sakaiproject.nakamura.api.search.solr.Query;
import org.sakaiproject.nakamura.api.search.solr.ResultSetFactory;
import org.sakaiproject.nakamura.api.search.solr.SolrSearchException;
import org.sakaiproject.nakamura.api.search.solr.SolrSearchResultSet;
import org.sakaiproject.nakamura.api.search.solr.SolrSearchUtil;
import org.sakaiproject.nakamura.api.solr.SolrServerService;
import org.sakaiproject.nakamura.util.telemetry.TelemetryCounter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.List;

import static org.sakaiproject.nakamura.api.search.solr.SolrSearchConstants.PARAMS_ITEMS_PER_PAGE;
import static org.sakaiproject.nakamura.api.search.solr.SolrSearchConstants.PARAMS_PAGE;

/**
 *
 */
@Component(metatype = true)
@Service
@Property(name = "type", value = Query.SOLR)

public class SolrResultSetFactory implements ResultSetFactory {

  @Property(longValue = 100L)
  private static final String VERY_SLOW_QUERY_TIME = "verySlowQueryTime";
  @Property(longValue = 10L)
  private static final String SLOW_QUERY_TIME = "slowQueryTime";
  @Property(intValue = 100)
  private static final String DEFAULT_MAX_RESULTS = "defaultMaxResults";
  @Property(value = "POST")
  private static final String HTTP_METHOD = "httpMethod";

  /** only used to mark the logger */
  private final class SlowQueryLogger { }

  private static final Logger LOGGER = LoggerFactory.getLogger(SolrResultSetFactory.class);
  private static final Logger SLOW_QUERY_LOGGER = LoggerFactory.getLogger(SlowQueryLogger.class);

  @Reference
  private SolrServerService solrSearchService;

  @Reference
  private DeletedPathsService deletedPathsService;

  private int defaultMaxResults = 100; // set to 100 to allow testing
  private long slowQueryThreshold;
  private long verySlowQueryThreshold;
  private METHOD queryMethod;

  @Activate
  protected void activate(Map<?, ?> props) {
    defaultMaxResults = PropertiesUtil.toInteger(props.get(DEFAULT_MAX_RESULTS),
        defaultMaxResults);
    slowQueryThreshold = PropertiesUtil.toLong(props.get(SLOW_QUERY_TIME), 10L);
    verySlowQueryThreshold = PropertiesUtil.toLong(props.get(VERY_SLOW_QUERY_TIME), 100L);
    queryMethod = METHOD.valueOf(PropertiesUtil.toString(props.get(HTTP_METHOD), "POST"));
  }

  protected Map<String, Object> processQueryOptions (final Session session, final Authorizable authz,
     final Query query) throws StorageClientException {

    // Add reader restrictions to solr fq (filter query) parameter,
    // to prevent "reader restrictions" from affecting the solr score
    // of a document.
    Map<String, Object> originalQueryOptions = query.getOptions();
    Map<String, Object> queryOptions = Maps.newHashMap();

    Object existingFilterQuery = null;

    if (originalQueryOptions != null) {
      // copy from originalQueryOptions in case its backed by a ImmutableMap,
      // which prevents saving of filter query changes.
      queryOptions.putAll(originalQueryOptions);
      if (queryOptions.get(CommonParams.FQ) != null) {
        existingFilterQuery = queryOptions.get(CommonParams.FQ);
      }
    }

    Set<String> filterQueries = Sets.newHashSet();
    // add any existing filter queries to the set
    if (existingFilterQuery != null) {
      if (existingFilterQuery instanceof Object[]) {
        CollectionUtils.addAll(filterQueries, (Object[]) existingFilterQuery);
      } else if (existingFilterQuery instanceof Iterable) {
        CollectionUtils.addAll(filterQueries, ((Iterable) existingFilterQuery).iterator());
      } else {
        filterQueries.add(String.valueOf(existingFilterQuery));
      }
    }

    // apply readers restrictions.
    if (authz == null) {
      filterQueries.add("readers:" + User.ANON_USER);
    } else {
      if (!User.ADMIN_USER.equals(authz.getId())) {
        AuthorizableManager am = session.getAuthorizableManager();
        Set<String> readers = Sets.newHashSet();
        for (Iterator<Group> gi = authz.memberOf(am); gi.hasNext();) {
          readers.add(gi.next().getId());
        }
        readers.add(session.getUserId());
        filterQueries.add("readers:(" + StringUtils.join(readers," OR ") + ")");
      }
    }

    // filter out 'excluded' items. these are indexed because we do need to search for
    // some things on the server that the UI doesn't want (e.g. collection groups)
    filterQueries.add("-exclude:true");

    // filter out deleted items
    List<String> deletedPaths = deletedPathsService.getDeletedPaths();
    if (!deletedPaths.isEmpty()) {
      // these are escaped as they are collected
      filterQueries.add("-path:(" + StringUtils.join(deletedPaths, " OR ") + ")");
    }

    // save filterQuery changes
    queryOptions.put(CommonParams.FQ, filterQueries);

    // Ensure proper totals from grouped / collapsed queries.
    if ("true".equals(queryOptions.get(GroupParams.GROUP)) &&
        (queryOptions.get(GroupParams.GROUP_TOTAL_COUNT) == null)) {
      queryOptions.put(GroupParams.GROUP_TOTAL_COUNT, "true");
    }

    return queryOptions;
  }

  protected SolrSearchResultSet executeQuery(final long offset, final long size, final String queryStr,
     final Map<String, Object> queryOptions, final String telemetryStr) throws SolrServerException {
    SolrQuery solrQuery = buildQuery(offset, size, queryStr, queryOptions);

    final SolrServer solrServer = solrSearchService.getServer();
    if ( LOGGER.isDebugEnabled()) {
      try {
        LOGGER.debug("Performing Query {} ", URLDecoder.decode(solrQuery.toString(),"UTF-8"));
      } catch (UnsupportedEncodingException e) {
      }
    }
    long tquery = System.currentTimeMillis();
    final QueryResponse response = solrServer.query(solrQuery, queryMethod);
    tquery = System.currentTimeMillis() - tquery;
    try {
      if ( tquery > verySlowQueryThreshold ) {
        SLOW_QUERY_LOGGER.error("Very slow solr query {} ms {} ",tquery, URLDecoder.decode(solrQuery.toString(),"UTF-8"));
        TelemetryCounter.incrementValue("search","VERYSLOW",telemetryStr);
      } else if ( tquery > slowQueryThreshold ) {
        SLOW_QUERY_LOGGER.warn("Slow solr query {} ms {} ",tquery, URLDecoder.decode(solrQuery.toString(),"UTF-8"));
        TelemetryCounter.incrementValue("search", "SLOW", telemetryStr);
      }
    } catch (UnsupportedEncodingException e) {
    }
    final SolrSearchResultSetImpl rs = new SolrSearchResultSetImpl(response);
    if ( LOGGER.isDebugEnabled()) {
      LOGGER.debug("Got {} hits in {} ms", rs.getSize(), response.getElapsedTime());
    }
    return rs;
  }

  /**
   * Process a query string to search using Solr.
   *
   * @param request
   * @param query
   * @param asAnon
   * @return
   * @throws SolrSearchException
   */
  @SuppressWarnings("rawtypes")
  public SolrSearchResultSet processQuery(SlingHttpServletRequest request,  Query query,
      boolean asAnon) throws SolrSearchException {
    try {
      Session session = StorageClientUtils.adaptToSession(request.getResourceResolver().adaptTo(javax.jcr.Session.class));
      AuthorizableManager am = session.getAuthorizableManager();
      Authorizable authz = am.findAuthorizable(request.getRemoteUser());

      Map<String, Object> queryOptions = processQueryOptions(session, authz, query);

      long[] ranges = SolrSearchUtil.getOffsetAndSize(request, queryOptions);

      String telemetryString = request.getResource().getPath();

      return executeQuery(ranges[0], ranges[1], query.getQueryString(), queryOptions, telemetryString);
    } catch (StorageClientException e) {
      throw new SolrSearchException(500, e.getMessage());
    } catch (AccessDeniedException e) {
      throw new SolrSearchException(500, e.getMessage());
    } catch (SolrServerException e) {
        throw new SolrSearchException(500, e.getMessage());
    }
  }

  @Override
  public SolrSearchResultSet processQuery(final Session session, final Query query, final Authorizable authorizable,
     final long offset, final long size) throws SolrSearchException {

    try {
      final Map<String, Object> queryOptions = processQueryOptions(session, authorizable, query);

      long finalOffset = queryOptions.containsKey(PARAMS_PAGE) ? (Long)queryOptions.get(PARAMS_PAGE) : offset;
      long finalSize = queryOptions.containsKey(PARAMS_ITEMS_PER_PAGE) ?
         (Long)queryOptions.get(PARAMS_ITEMS_PER_PAGE) : offset;

      return executeQuery(finalOffset, finalSize, query.getQueryString(), queryOptions, query.getName());
    } catch (StorageClientException e) {
      throw new SolrSearchException(500, e.getMessage());
    } catch (SolrServerException e) {
      throw new SolrSearchException(500, e.getMessage());
    }
  }

  /**
   * @param offset
   * @param size
   * @param queryString
   * @param options
   * @return
   */
  @SuppressWarnings("unchecked")
  private SolrQuery buildQuery(long offset, long size, String queryString,
      Map<String, Object> options) {
    // build the query
    SolrQuery solrQuery = new SolrQuery(queryString);
    solrQuery.setStart((int) offset);
    solrQuery.setRows(Math.min(defaultMaxResults, (int) size));

    // add in some options
    if (options != null) {
      for (Entry<String, Object> option : options.entrySet()) {
        String key = option.getKey();
        Object val = option.getValue();
        if (CommonParams.SORT.equals(key)) {
          parseSort(solrQuery, String.valueOf(val));
        } else if (val instanceof Object[]) {
          for (Object v : (Object[]) val) {
            solrQuery.add(key, String.valueOf(v));
          }
        } else if (val instanceof Iterable) {
          for (Object v : (Iterable<Object>) val) {
            solrQuery.add(key, String.valueOf(v));
          }
        } else {
          solrQuery.add(key, String.valueOf(val));
        }
      }
    }
    return solrQuery;
  }

  /**
   * @param solrQuery
   * @param val
   */
  private void parseSort(SolrQuery solrQuery, String val) {
    /* disable KERN-1855 for now; needs more discussion. */
    // final String[] sortFields = solrQuery.getSortFields();
    // we were using setSortField, now using addSortField; verify state
    // if (sortFields != null && sortFields.length > 0) {
    // throw new IllegalStateException("Expected zero sort fields, found: " + sortFields);
    // }
    // final String[] criteria = val.split(",");
    // for (final String criterion : criteria) {
    // final String[] sort = StringUtils.split(criterion);
    final String[] sort = StringUtils.split(val);

    // use the *_sort fields to have predictable sorting.
    // many of the fields in the index have a lot of processing which
    // causes sorting to yield unpredictable results.
    String sortOn = ("score".equals(sort[0])) ? sort[0] : sort[0] + "_sort";
    switch (sort.length) {
    case 1:
      // solrQuery.addSortField(sort[0], ORDER.asc);
      solrQuery.setSortField(sortOn, ORDER.asc);
      break;
    case 2:
      String sortOrder = sort[1].toLowerCase();
      ORDER o = ORDER.asc;
      try {
        o = ORDER.valueOf(sortOrder);
      } catch (IllegalArgumentException a) {
        if (sortOrder.startsWith("d")) {
          o = ORDER.desc;
        } else {
          o = ORDER.asc;
        }
      }
      // solrQuery.addSortField(sort[0], o);
      solrQuery.setSortField(sortOn, o);
      break;
    default:
      LOGGER.warn("Expected the sort option to be 1 or 2 terms. Found: {}", val);
    }
    // }
  }
}
