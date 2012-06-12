package org.sakaiproject.nakamura.search.solr;

import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import org.apache.commons.lang.StringUtils;
import org.apache.felix.scr.annotations.Reference;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.request.RequestParameter;
import org.apache.sling.api.request.RequestParameterMap;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.commons.json.JSONException;
import org.apache.solr.client.solrj.util.ClientUtils;
import org.sakaiproject.nakamura.api.search.SearchUtil;
import org.sakaiproject.nakamura.api.search.solr.MissingParameterException;
import org.sakaiproject.nakamura.api.search.solr.Query;
import org.sakaiproject.nakamura.api.search.solr.SearchServiceException;
import org.sakaiproject.nakamura.api.search.solr.SearchTemplate;
import org.sakaiproject.nakamura.api.search.solr.SolrQueryFactory;
import org.sakaiproject.nakamura.api.search.solr.SolrSearchPropertyProvider;
import org.sakaiproject.nakamura.api.search.solr.SolrSearchUtil;
import org.sakaiproject.nakamura.api.templates.TemplateService;
import org.sakaiproject.nakamura.util.LitePersonalUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jcr.RepositoryException;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import static org.sakaiproject.nakamura.api.search.solr.SolrSearchConstants.DEFAULT_PAGED_ITEMS;
import static org.sakaiproject.nakamura.api.search.solr.SolrSearchConstants.PARAMS_ITEMS_PER_PAGE;
import static org.sakaiproject.nakamura.api.search.solr.SolrSearchConstants.PARAMS_PAGE;
import static org.sakaiproject.nakamura.api.search.solr.SolrSearchConstants.SEARCH_PATH_PREFIX;

public class BackwardCompatibleSolrQueryFactory implements SolrQueryFactory {

  @Reference
  private transient TemplateService templateService;

  @Reference
  private SolrSearchPropertyProviderTracker searchPropertyProviderTracker;

  private static final Logger LOGGER = LoggerFactory.getLogger(BackwardCompatibleSolrQueryFactory.class);

  private SlingHttpServletRequest request;
  private SearchTemplate template;

  public BackwardCompatibleSolrQueryFactory(SlingHttpServletRequest request, SearchTemplate template) {
    this.request = request;
    this.template = template;
  }

  @Override
  public Query getQuery() {
    Resource resource = request.getResource();
    if (!resource.getPath().startsWith(SEARCH_PATH_PREFIX)) {
      throw new IllegalStateException("unable to obtain Node from request");
    }
    // KERN-1147 Respond better when all parameters haven't been provided for a query
    Query query;

    try {
      query = processQuery(request, template);
    } catch (MissingParameterException e) {
      throw new SearchServiceException("Query template is missing parameters", e);
    }

    long nitems = SolrSearchUtil.longRequestParameter(request, PARAMS_ITEMS_PER_PAGE,
       DEFAULT_PAGED_ITEMS);
    long page = SolrSearchUtil.longRequestParameter(request, PARAMS_PAGE, 0);

    // allow number of items to be specified in sakai:query-template-options
    if (query.getOptions().containsKey(PARAMS_ITEMS_PER_PAGE)) {
      nitems = Long.valueOf(String.valueOf(query.getOptions().get(PARAMS_ITEMS_PER_PAGE)));
    } else {
      // add this to the options so that all queries are constrained to a limited
      // number of returns per page.
      query.getOptions().put(PARAMS_ITEMS_PER_PAGE, Long.toString(nitems));
    }

    if (!query.getOptions().containsKey(PARAMS_PAGE)) {
      // add this to the options so that all queries are constrained to a limited
      // number of returns per page.
      query.getOptions().put(PARAMS_PAGE, Long.toString(page));
    }

    return null;
  }

    /**
   * Processes a velocity template so that variable references are replaced by the same
   * properties in the property provider and request.
   *
   * @param request
   *          the request.
   * @return A processed query template
   * @throws MissingParameterException
   */
  protected Query processQuery(SlingHttpServletRequest request, SearchTemplate template)
      throws MissingParameterException {
    // check the resource type and set the query type appropriately
    // default to using solr for queries
    String queryType;
    if ("sakai/sparse-search".equals(template.getResourceType())) {
      queryType = Query.SPARSE;
    } else {
      queryType = Query.SOLR;
    }

    String[] propertyProviderNames = template.getPropertyProviderNames();

    Map<String, String> defaultValues = template.getDefaultValues();

    Map<String, String> propertiesMap = loadProperties(request, propertyProviderNames,
        defaultValues, queryType);

    String queryTemplate = template.getTemplateString();

    // process the query string before checking for missing terms to a) give processors a
    // chance to set things and b) catch any missing terms added by the processors.
    String queryString = templateService.evaluateTemplate(propertiesMap, queryTemplate);

    // expand home directory references to full path; eg. ~user => a:user
    queryString = SearchUtil.expandHomeDirectory(queryString);

    // check for any missing terms & process the query template
    Collection<String> missingTerms = templateService.missingTerms(queryString);
    if (!missingTerms.isEmpty()) {
      throw new MissingParameterException(
          "Your request is missing parameters for the template: "
              + StringUtils.join(missingTerms, ", "));
    }

    // collect query options
    Map<String, String[]> queryOptions = template.getQueryOptions();

    // process the options as templates and check for missing params
    Map<String, Object> options = processOptions(propertiesMap, queryOptions, queryType);

    return new Query(template.getPath(), queryType, queryString, options);
  }

  /**
   * @param propertiesMap
   * @param queryOptions
   * @return
   * @throws JSONException
   * @throws MissingParameterException
   */
  private Map<String, Object> processOptions(Map<String, String> propertiesMap,
      Map<String, String[]> queryOptions, String queryType) throws MissingParameterException {
    Set<String> missingTerms = Sets.newHashSet();
    Map<String, Object> options = Maps.newHashMap();
    if (queryOptions != null) {
      for (Map.Entry<String, String[]> entry : queryOptions.entrySet()) {
        String key = entry.getKey();
        String[] values = entry.getValue();

        Set<String> processedVals = Sets.newHashSet();

        for (String value : values) {
          String processedVal = processValue(key, value, propertiesMap,
              queryType, missingTerms);
          processedVals.add(processedVal);
        }
        if (!processedVals.isEmpty()) {
          options.put(key, processedVals);
        }
      }
    }

    if (!missingTerms.isEmpty()) {
      throw new MissingParameterException(
          "Your request is missing parameters for the template: "
              + StringUtils.join(missingTerms, ", "));
    } else {
      return options;
    }
  }
  /**
   * Process a value through the template service and check for missing fields.
   *
   * @param key
   * @param val
   * @param propertiesMap
   * @param queryType
   * @param missingTerms
   * @return
   */
  private String processValue(String key, String val, Map<String, String> propertiesMap,
      String queryType, Set<String> missingTerms) {
    missingTerms.addAll(templateService.missingTerms(propertiesMap, val));
    String processedVal = templateService.evaluateTemplate(propertiesMap, val);
    if ("sort".equals(key)) {
      processedVal = SearchUtil.escapeString(processedVal, queryType);
    }
    return processedVal;
  }

  /**
   * Load properties from the query node, request and property provider.<br/>
   *
   * Overwrite order: query node &lt; request &lt; property provider<br/>
   *
   * This ordering allows the query node to set defaults, the request to override those
   * defaults but the property provider to have the final say in what value is set.
   *
   * @param request
   * @return
   * @throws RepositoryException
   */
  private Map<String, String> loadProperties(SlingHttpServletRequest request,
      String[] propertyProviderNames, Map<String, String> defaultProps, String queryType) {
    Map<String, String> propertiesMap = new HashMap<String, String>();

    // 0. load authorizable (user) information
    String userId = request.getRemoteUser();
    String userPrivatePath = ClientUtils.escapeQueryChars(LitePersonalUtils
       .getPrivatePath(userId));
    propertiesMap.put("_userPrivatePath", userPrivatePath);
    propertiesMap.put("_userId", ClientUtils.escapeQueryChars(userId));

    // 1. load in properties from the query template node so defaults can be set
    for (Map.Entry<String, String> entry : defaultProps.entrySet()) {
      final String key = entry.getKey();
      final String value = entry.getValue();

      if (!key.startsWith("jcr:") && !propertiesMap.containsKey(key)) {
        propertiesMap.put(key, value);
      }
    }

    // 2. load in properties from the request
    RequestParameterMap params = request.getRequestParameterMap();
    for (Map.Entry<String, RequestParameter[]> entry : params.entrySet()) {
      RequestParameter[] vals = entry.getValue();
      String requestValue = vals[0].getString();

      // blank values aren't cool
      if (StringUtils.isBlank(requestValue)) {
        continue;
      }

      // we're selective with what we escape to make sure we don't hinder
      // search functionality
      String key = entry.getKey();
      String val = SearchUtil.escapeString(requestValue, queryType);
      propertiesMap.put(key, val);
    }

    // 3. load properties from a property provider
    if (propertyProviderNames != null) {
      for (String propertyProviderName : propertyProviderNames) {
        LOGGER.debug("Trying Provider Name {} ", propertyProviderName);
        SolrSearchPropertyProvider provider = searchPropertyProviderTracker.getByName(propertyProviderName);
        if (provider != null) {
          LOGGER.debug("Trying Provider {} ", provider);
          provider.loadUserProperties(request, propertiesMap);
        } else {
          LOGGER.warn("No properties provider found for {} ", propertyProviderName);
        }
      }
    } else {
      LOGGER.debug("No Provider ");
    }

    return propertiesMap;
  }


}
