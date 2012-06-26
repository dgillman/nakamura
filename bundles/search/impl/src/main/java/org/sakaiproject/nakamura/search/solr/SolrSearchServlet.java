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

import static org.sakaiproject.nakamura.api.search.solr.SolrSearchConstants.DEFAULT_PAGED_ITEMS;
import static org.sakaiproject.nakamura.api.search.solr.SolrSearchConstants.FACET_FIELDS;
import static org.sakaiproject.nakamura.api.search.solr.SolrSearchConstants.JSON_RESULTS;
import static org.sakaiproject.nakamura.api.search.solr.SolrSearchConstants.PARAMS_ITEMS_PER_PAGE;
import static org.sakaiproject.nakamura.api.search.solr.SolrSearchConstants.PARAMS_PAGE;
import static org.sakaiproject.nakamura.api.search.solr.SolrSearchConstants.SAKAI_BATCHRESULTPROCESSOR;
import static org.sakaiproject.nakamura.api.search.solr.SolrSearchConstants.SAKAI_QUERY_TEMPLATE;
import static org.sakaiproject.nakamura.api.search.solr.SolrSearchConstants.SAKAI_RESULTPROCESSOR;
import static org.sakaiproject.nakamura.api.search.solr.SolrSearchConstants.SAKAI_SEARCHRESPONSEDECORATOR;
import static org.sakaiproject.nakamura.api.search.solr.SolrSearchConstants.SEARCH_PATH_PREFIX;
import static org.sakaiproject.nakamura.api.search.solr.SolrSearchConstants.TIDY;
import static org.sakaiproject.nakamura.api.search.solr.SolrSearchConstants.TOTAL;

import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import org.apache.commons.lang.StringUtils;
import org.apache.felix.scr.annotations.Properties;
import org.apache.felix.scr.annotations.Property;
import org.apache.felix.scr.annotations.Reference;
import org.apache.felix.scr.annotations.sling.SlingServlet;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.SlingHttpServletResponse;
import org.apache.sling.api.request.RequestParameter;
import org.apache.sling.api.request.RequestParameterMap;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.servlets.SlingSafeMethodsServlet;
import org.apache.sling.commons.json.JSONException;
import org.apache.solr.client.solrj.response.FacetField;
import org.apache.solr.client.solrj.util.ClientUtils;
import org.sakaiproject.nakamura.api.doc.ServiceDocumentation;
import org.sakaiproject.nakamura.api.doc.ServiceMethod;
import org.sakaiproject.nakamura.api.doc.ServiceParameter;
import org.sakaiproject.nakamura.api.doc.ServiceResponse;
import org.sakaiproject.nakamura.api.lite.Session;
import org.sakaiproject.nakamura.api.lite.StorageClientException;
import org.sakaiproject.nakamura.api.lite.StorageClientUtils;
import org.sakaiproject.nakamura.api.lite.accesscontrol.AccessDeniedException;
import org.sakaiproject.nakamura.api.lite.authorizable.Authorizable;
import org.sakaiproject.nakamura.api.search.SearchResponseDecorator;
import org.sakaiproject.nakamura.api.search.SearchResultProcessor;
import org.sakaiproject.nakamura.api.search.SearchUtil;
import org.sakaiproject.nakamura.api.search.solr.MissingParameterException;
import org.sakaiproject.nakamura.api.search.solr.Query;
import org.sakaiproject.nakamura.api.search.solr.Result;
import org.sakaiproject.nakamura.api.search.solr.SearchServiceException;
import org.sakaiproject.nakamura.api.search.solr.SearchTemplate;
import org.sakaiproject.nakamura.api.search.solr.SolrQueryFactory;
import org.sakaiproject.nakamura.api.search.solr.SolrSearchBatchJsonResultWriter;
import org.sakaiproject.nakamura.api.search.solr.SolrSearchBatchResultProcessor;
import org.sakaiproject.nakamura.api.search.solr.SolrSearchException;
import org.sakaiproject.nakamura.api.search.solr.SolrSearchJsonResultWriter;
import org.sakaiproject.nakamura.api.search.solr.SolrSearchPropertyProvider;
import org.sakaiproject.nakamura.api.search.solr.SolrSearchResultProcessor;
import org.sakaiproject.nakamura.api.search.solr.SolrSearchResultSet;
import org.sakaiproject.nakamura.api.search.solr.SolrSearchServiceFactory;
import org.sakaiproject.nakamura.api.search.solr.SolrSearchUtil;
import org.sakaiproject.nakamura.api.templates.TemplateService;
import org.sakaiproject.nakamura.util.ExtendedJSONWriter;
import org.sakaiproject.nakamura.util.JcrUtils;
import org.sakaiproject.nakamura.util.LitePersonalUtils;
import org.sakaiproject.nakamura.util.ServletUtils;
import org.sakaiproject.nakamura.util.telemetry.TelemetryCounter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import javax.jcr.Node;
import javax.jcr.RepositoryException;
import javax.jcr.Value;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletResponse;

@ServiceDocumentation(name = "Solr Search Servlet", okForVersion = "1.2",
  shortDescription = "The Search servlet provides search results from a search template.",
  description = {
    "The Solr Search Servlet responds with search results in json form in response to GETs on search urls. Those URLs are resolved "
        + "as resources of type sakai/solr-search and sakai/sparse-search. The node at the resource containing properties that represent a search template that is "
        + "used to perform the search operation. This allows the UI developer to create nodes in the JCR and configure those nodes to "
        + "act as an end point for a search based view into the content store. If the propertyprovider or the batchresultprocessor are not specified, "
        + "default implementations will be used.",
    "The format of the template is ",
    "<pre>"
        + " nt:unstructured \n"
        + "        -sakai:query-template - a message query template for the query, with placeholders \n"
        + "                                for parameters of the form {request-parameter-name}\n"
        + "        -sakai:propertyprovider - the name of a Property Provider used to populate the properties \n"
        + "                                  to be used in the query \n"
        + "        -sakai:batchresultprocessor - the name of a SearchResultProcessor to be used processing \n"
        + "                                      the result set.\n" + "</pre>",
    "For example:",
    "<pre>" + "/var/search/pool/files\n" + "{  \n"
        + "   \"sakai:query-template\": \"resourceType:sakai/pooled-content AND (manager:${group} OR viewer:${group})\", \n"
        + "   \"sling:resourceType\": \"sakai/solr-search\", \n"
        + "   \"sakai:propertyprovider\": \"PooledContent\",\n"
        + "   \"sakai:batchresultprocessor\": \"LiteFiles\" \n" + "} \n" + "</pre>"
  },
  methods = {
    @ServiceMethod(name = "GET",
      description = {
        "Processes the query request against the selected resource, using the properties on the resource as a ",
        "template for processing the request and a specification for the pre and post processing steps on the search."
      },
      parameters = {
        @ServiceParameter(name = "items", description = { "The number of items per page in the result set." }),
        @ServiceParameter(name = "page", description = { "The page number to start listing the results on." }),
        @ServiceParameter(name = "*", description = { "Any other parameters may be used by the template." })
      },
      response = {
        @ServiceResponse(code = 200, description = "A search response similar to the above will be emitted "),
        @ServiceResponse(code = 403, description = "The search template is not located under /var "),
        @ServiceResponse(code = 400, description = "There are too many results that need to be paged. "),
        @ServiceResponse(code = 500, description = "Any error with the html containing the error")
      })
  })

@SlingServlet(extensions = { "json" }, methods = { "GET" }, resourceTypes = { "sakai/solr-search", "sakai/sparse-search" })
@Properties(value = {
    @Property(name = "service.description", value = { "Performs searches based on the associated node." }),
    @Property(name = "service.vendor", value = { "The Sakai Foundation" })
})
public class SolrSearchServlet extends SlingSafeMethodsServlet {

  private static final long serialVersionUID = 4130126304725079596L;
  private static final Logger LOGGER = LoggerFactory.getLogger(SolrSearchServlet.class);

  @Reference
  protected transient SolrSearchServiceFactory searchServiceFactory;

  @Reference
  private SearchResultProcessorTracker searchResultProcessorTracker;

  @Reference
  private SearchBatchResultProcessorTracker searchBatchResultProcessorTracker;

  @Reference
  private SolrSearchPropertyProviderTracker searchPropertyProviderTracker;

  @Reference
  private SearchResponseDecoratorTracker searchResponseDecoratorTracker;

  @Reference
  private SearchBatchJsonResultWriterTracker searchBatchResultWriterTracker;

  @Reference
  private SearchJsonResultWriterTracker searchResultWriterTracker;


  // Default processors
  /**
   * Reference uses property set on NodeSearchResultProcessor. Other processors can become
   * the default by setting {@link SearchResultProcessor.DEFAULT_PROCESOR_PROP} to true.
   */
  private static final String DEFAULT_BATCH_SEARCH_PROC_TARGET = "(&("
      + SolrSearchBatchResultProcessor.DEFAULT_BATCH_PROCESSOR_PROP + "=true))";
  @Reference(target = DEFAULT_BATCH_SEARCH_PROC_TARGET)
  protected transient SolrSearchBatchResultProcessor defaultSearchBatchProcessor;

  /**
   * Reference uses property set on NodeSearchResultProcessor. Other processors can become
   * the default by setting {@link SearchResultProcessor.DEFAULT_PROCESSOR_PROP} to true.
   */
  private static final String DEFAULT_SEARCH_PROC_TARGET = "(&("
      + SolrSearchResultProcessor.DEFAULT_PROCESSOR_PROP + "=true))";
  @Reference(target = DEFAULT_SEARCH_PROC_TARGET)
  protected transient SolrSearchResultProcessor defaultSearchProcessor;

  //Default result writers
   /**
    * Reference uses property set on NodeSearchResultProcessor. Other processors can become
   * the default by setting {@link org.sakaiproject.nakamura.api.search.solr.SolrSearchResultProcessor.DEFAULT_PROCESOR_PROP} to true.
    */
  private static final String DEFAULT_BATCH_SEARCH_WRITE_TARGET = "(&("
      + SolrSearchBatchJsonResultWriter.DEFAULT_BATCH_WRITER_PROP + "=true))";
  @Reference(target = DEFAULT_BATCH_SEARCH_WRITE_TARGET)
  protected transient SolrSearchBatchJsonResultWriter defaultJsonBatchWriter;

   /**
    * Reference uses property set on NodeSearchResultProcessor. Other processors can become
   * the default by setting {@link org.sakaiproject.nakamura.api.search.SearchResultProcessor.DEFAULT_PROCESSOR_PROP} to true.
    */
  private static final String DEFAULT_SEARCH_WRITER_TARGET = "(&("
      + SolrSearchJsonResultWriter.DEFAULT_WRITER_PROP + "=true))";
  @Reference(target = DEFAULT_SEARCH_WRITER_TARGET)
  protected transient SolrSearchJsonResultWriter defaultJsonResultWriter;

  @Reference
  private transient TemplateService templateService;

  class BackwardCompatibleSolrQueryFactory implements SolrQueryFactory {
    private final Logger LOGGER = LoggerFactory.getLogger(BackwardCompatibleSolrQueryFactory.class);

    private TemplateService templateService;
    private SolrSearchPropertyProviderTracker searchPropertyProviderTracker;
    private SlingHttpServletRequest request;
    private SearchTemplate template;

    public BackwardCompatibleSolrQueryFactory(TemplateService templateService,
       SolrSearchPropertyProviderTracker propertyProviderTracker, SlingHttpServletRequest request,
       SearchTemplate template) {
      this.templateService = templateService;
      this.searchPropertyProviderTracker = propertyProviderTracker;
      this.request = request;
      this.template = template;
    }
    /**
     * Processes a velocity template so that variable references are replaced by the same
     * properties in the property provider and request.
     *
     * @param request
     *          the request.
     * @param template
     *          the query template.
     * @return A processed query template
     * @throws MissingParameterException
     */
    protected Query processQuery(SlingHttpServletRequest request, SearchTemplate template)
       throws RepositoryException, MissingParameterException, JSONException, StorageClientException,
       AccessDeniedException {
      // check the resource type and set the query type appropriately
      // default to using solr for queries
      final String resourceType = template.getResourceType();
      String queryType;
      if ("sakai/sparse-search".equals(resourceType)) {
        queryType = Query.SPARSE;
      } else {
        queryType = Query.SOLR;
      }

      String[] propertyProviderNames = template.getPropertyProviderNames();
      Map<String, String> defaultValues = template.getDefaultValues();
      Map<String, String> propertiesMap = loadProperties(request.getRemoteUser(), propertyProviderNames,
          defaultValues, queryType);

      // load in properties from the request
      RequestParameterMap params = request.getRequestParameterMap();
      for (Entry<String, RequestParameter[]> entry : params.entrySet()) {
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
        Map<String, String[]> queryOptions, String queryType) throws RepositoryException,
        MissingParameterException {
      Set<String> missingTerms = Sets.newHashSet();
      Map<String, Object> options = Maps.newHashMap();
      if (queryOptions != null) {
        for(Map.Entry<String, String[]> entry : queryOptions.entrySet()) {
          String key = entry.getKey();
          String value[] = entry.getValue();

          if (!JcrUtils.isJCRProperty(key)) {
            if (value.length > 1) {
              Set<String> processedVals = Sets.newHashSet();
              for (String val : value) {
                String processedVal = processValue(key, val, propertiesMap,
                    queryType, missingTerms);
                processedVals.add(processedVal);
              }
              if (!processedVals.isEmpty()) {
                options.put(key, processedVals);
              }
            } else {
              String val = value[0];
              String processedVal = processValue(key, val, propertiesMap, queryType,
                  missingTerms);
              options.put(key, processedVal);
            }
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
     * @param userId
     * @param propertyProviderNames
     * @return
     * @throws RepositoryException
     */
    private Map<String, String> loadProperties(String userId, String[] propertyProviderNames,
       Map<String, String> defaultProps, String queryType) throws RepositoryException {
      Map<String, String> propertiesMap = new HashMap<String, String>();

      // 0. load authorizable (user) information
      String userPrivatePath = ClientUtils.escapeQueryChars(LitePersonalUtils
         .getPrivatePath(userId));
      propertiesMap.put("_userPrivatePath", userPrivatePath);
      propertiesMap.put("_userId", ClientUtils.escapeQueryChars(userId));

      // 1. load in properties from the query template node so defaults can be set
      if (defaultProps != null) {
        for (Map.Entry<String, String> entry : defaultProps.entrySet()) {
          String key = entry.getKey();
          if (!key.startsWith("jcr:") && !propertiesMap.containsKey(key)) {
            String val = entry.getValue();
            propertiesMap.put(key, val);
          }
        }
      }

      return propertiesMap;
    }

    @Override
    public Query getQuery() throws Exception {
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

      // This code overlaps in function with code in SolrSearchServlet.goGet()
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
      //end overlap

      return query;
    }
  }

  @Override
  protected void doGet(SlingHttpServletRequest request, SlingHttpServletResponse response)
      throws ServletException, IOException {
    ResourceResolver resolver = request.getResourceResolver();
    Resource resource = request.getResource();
    if (!resource.getPath().startsWith(SEARCH_PATH_PREFIX)) {
      throw new IllegalStateException("unable to obtain Node from request");
    }
    TelemetryCounter.incrementValue("search", "SolrSearchServlet", resource.getPath());

    try {
      final Session session = StorageClientUtils.adaptToSession(resolver.adaptTo(javax.jcr.Session.class));
      final Authorizable authz = session.getAuthorizableManager().findAuthorizable(request.getRemoteUser());
      final Node node = resource.adaptTo(Node.class);

      final SearchTemplate template = new JcrSearchTemplate(node);

      final BackwardCompatibleSolrQueryFactory bcsqf = new BackwardCompatibleSolrQueryFactory(templateService,
         searchPropertyProviderTracker, request, template);

      // This code overlaps in function with code in BackwardCompatibleSolrQueryFactory.getQuery(...)
       long nitems = SolrSearchUtil.longRequestParameter(request, PARAMS_ITEMS_PER_PAGE,
         DEFAULT_PAGED_ITEMS);
       long page = SolrSearchUtil.longRequestParameter(request, PARAMS_PAGE, 0);

       final Map<String, String> defaults = template.getDefaultValues();

       // allow number of items to be specified in sakai:query-template-options
       if (defaults.containsKey(PARAMS_ITEMS_PER_PAGE)) {
        nitems = Long.valueOf(String.valueOf(defaults.get(PARAMS_ITEMS_PER_PAGE)));
       }
      //end overlap

      final SolrSearchResultSet rs;
      try {
        rs = searchServiceFactory.getSearchResultSet(bcsqf, page, nitems, session,  authz);
      } catch (SolrSearchException e) {
        LOGGER.error(e.getMessage(), e);
        response.sendError(e.getCode(), e.getMessage());
        return;
      }

      boolean useBatch = template.isBatch();
      final String batchResultProcessorName = template.getBatchResultProcessor();
      // Get the
      SolrSearchBatchResultProcessor searchBatchProcessor = defaultSearchBatchProcessor;
      if (batchResultProcessorName != null) {
        searchBatchProcessor = searchBatchResultProcessorTracker.getByName(batchResultProcessorName);
        if (searchBatchProcessor == null) {
          searchBatchProcessor = defaultSearchBatchProcessor;
        }
      }

      final String resultProcessorName = template.getResultProcessor();
      SolrSearchResultProcessor searchProcessor = defaultSearchProcessor;
      if (resultProcessorName != null) {
        searchProcessor = searchResultProcessorTracker.getByName(resultProcessorName);
        if (searchProcessor == null) {
          searchProcessor = defaultSearchProcessor;
        }
      }

      SolrSearchBatchJsonResultWriter batchResultWriter = defaultJsonBatchWriter;
      SolrSearchJsonResultWriter resultWriter = defaultJsonResultWriter;

      /*
        The order of this processing is important - template.getBatchResultWriter and template.getResultWriter
        may return the name of a request processor. This was to preserve backward compatibility: result writer
        interfaces were refactored out of request processor interfaces. Rather than revise all existing templates
        to add a result writer field, it has been assumed that a template lacking such a field predates the
        refactor and so the request processor should be returned.

        In order to catch a case where the lack of a result writer *is* indeed an error we need to check for
        null when looking up the ResultWriter. At that point we try to handle the situation by falling back to
        the default result writer.
       */
      String temp = null;
      if (template.isBatch()) {
        temp = template.getBatchResultWriter();
        if (temp != null) {
          batchResultWriter = searchBatchResultWriterTracker.getByName(temp);
        } else if (searchBatchProcessor != null) {
          batchResultWriter = searchBatchProcessor;
        }
      } else {
        temp = template.getResultWriter();
        if (temp != null) {
          resultWriter = searchResultWriterTracker.getByName(temp);
        } else if (searchProcessor != null) {
          resultWriter = searchProcessor;
        }
      }

      response.setContentType("application/json");
      response.setCharacterEncoding("UTF-8");

      ExtendedJSONWriter write = new ExtendedJSONWriter(response.getWriter());
      write.setTidy(ServletUtils.isTidy(request));

      write.object();
      write.key(PARAMS_ITEMS_PER_PAGE);
      write.value(nitems);
      write.key(JSON_RESULTS);

      write.array();

      Iterator<Result> iterator = rs.getResultSetIterator();
      if (template.isBatch()) {
        LOGGER.info("Using batch processor for results");
        batchResultWriter.writeResults(request, write, iterator);
      } else {
        LOGGER.info("Using regular processor for results");
        // We don't skip any rows ourselves here.
        // We expect a rowIterator coming from a resultset to be at the right place.
        for (long i = 0; i < nitems && iterator.hasNext(); i++) {
          // Get the next row.
          Result result = iterator.next();

          // Write the result for this row.
          resultWriter.writeResult(request, write, result);
        }
      }
      write.endArray();

      // write the solr facets out if they exist
      writeFacetFields(rs, write);

      // write the total out after processing the list to give the underlying iterator
      // a chance to walk the results then report how many there were.
      write.key(TOTAL);
      write.value(rs.getSize());

      if ( node.hasProperty(SAKAI_SEARCHRESPONSEDECORATOR)) {
        String[] decoratorNames = getStringArrayProp(node, SAKAI_SEARCHRESPONSEDECORATOR);
        for ( String name : decoratorNames ) {
          SearchResponseDecorator decorator = searchResponseDecoratorTracker.getByName(name);
          if ( decorator != null ) {
            decorator.decorateSearchResponse(request, write);
          }
        }
      }

      write.endObject();
    } catch (RepositoryException e) {
      LOGGER.error(e.getMessage(), e);
      response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, e.getMessage());
    } catch (JSONException e) {
      LOGGER.error(e.getMessage(), e);
      response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, e.getMessage());
    } catch (AccessDeniedException e) {
      LOGGER.error(e.getMessage(), e);
      response.sendError(HttpServletResponse.SC_FORBIDDEN, e.getMessage());
    } catch (StorageClientException e) {
      LOGGER.error(e.getMessage(), e);
      response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, e.getMessage());
    }
  }

  private String[] getStringArrayProp(Node queryNode, String propName) throws RepositoryException {
    String[] propertyProviderNames;
    javax.jcr.Property propProv = queryNode.getProperty(propName);
    if (propProv.isMultiple()) {
      Value[] propProvVals = propProv.getValues();
      propertyProviderNames = new String[propProvVals.length];

      for (int i = 0; i < propProvVals.length; i++) {
        propertyProviderNames[i] = propProvVals[i].getString();
      }
    } else {
      propertyProviderNames = new String[1];
      propertyProviderNames[0] = propProv.getString();
    }
    return propertyProviderNames;
  }

  private void writeFacetFields(SolrSearchResultSet rs, ExtendedJSONWriter writer) throws JSONException {
    if (rs.getFacetFields() != null) {
      List<FacetField> fields = rs.getFacetFields();
      writer.key(FACET_FIELDS);
      writer.array();
      for (FacetField field : fields) {
        writer.object();
        writer.key(field.getName());
        writer.array();
        List<FacetField.Count> values = field.getValues();
        for ( FacetField.Count value : values ) {
          writer.object();
          writer.key(value.getName());
          writer.value(value.getCount());
          writer.endObject();
        }
        writer.endArray();
        writer.endObject();
      }
      writer.endArray();
    }
  }
}
