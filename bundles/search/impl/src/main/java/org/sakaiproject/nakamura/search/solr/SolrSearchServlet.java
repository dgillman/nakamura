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
import static org.sakaiproject.nakamura.api.search.solr.SolrSearchConstants.SEARCH_PATH_PREFIX;
import static org.sakaiproject.nakamura.api.search.solr.SolrSearchConstants.TIDY;
import static org.sakaiproject.nakamura.api.search.solr.SolrSearchConstants.TOTAL;

import org.apache.felix.scr.annotations.Properties;
import org.apache.felix.scr.annotations.Property;
import org.apache.felix.scr.annotations.Reference;
import org.apache.felix.scr.annotations.sling.SlingServlet;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.SlingHttpServletResponse;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.servlets.SlingSafeMethodsServlet;
import org.apache.sling.commons.json.JSONException;
import org.apache.solr.client.solrj.response.FacetField;
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
import org.sakaiproject.nakamura.api.search.solr.Result;
import org.sakaiproject.nakamura.api.search.solr.SearchTemplate;
import org.sakaiproject.nakamura.api.search.solr.SolrSearchBatchJsonResultWriter;
import org.sakaiproject.nakamura.api.search.solr.SolrSearchException;
import org.sakaiproject.nakamura.api.search.solr.SolrSearchJsonResultWriter;
import org.sakaiproject.nakamura.api.search.solr.SolrSearchResultSet;
import org.sakaiproject.nakamura.api.search.solr.SolrSearchServiceFactory;
import org.sakaiproject.nakamura.api.search.solr.SolrSearchUtil;
import org.sakaiproject.nakamura.api.templates.TemplateService;
import org.sakaiproject.nakamura.util.ExtendedJSONWriter;
import org.sakaiproject.nakamura.util.telemetry.TelemetryCounter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Iterator;
import java.util.List;

import javax.jcr.Node;
import javax.jcr.RepositoryException;
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
  protected transient TemplateService templateService;

  @Reference
  protected transient SolrSearchPropertyProviderTracker searchPropertyProviderTracker;

  @Reference
  private SearchResponseDecoratorTracker searchResponseDecoratorTracker;

  @Reference
  private SearchBatchJsonResultWriterTracker searchBatchResultWriterTracker;

  @Reference
  private SearchJsonResultWriterTracker searchResultWriterTracker;

  // Default writers
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

  @Override
  protected void doGet(SlingHttpServletRequest request, SlingHttpServletResponse response)
      throws ServletException, IOException {
    //this is presently duplicate code in the BackwardCompatibleSolrQueryFactory
    long nitems = SolrSearchUtil.longRequestParameter(request, PARAMS_ITEMS_PER_PAGE,
       DEFAULT_PAGED_ITEMS);
    long page = SolrSearchUtil.longRequestParameter(request, PARAMS_PAGE, 0);

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

      long[] ranges = SolrSearchUtil.getOffsetAndSize(request, null);

      final SolrSearchResultSet rs;
      try {
        rs = searchServiceFactory.getSearchResultSet(bcsqf, ranges[0], ranges[1], session,  authz);
      } catch (SolrSearchException e) {
        LOGGER.error(e.getMessage(), e);
        response.sendError(e.getCode(), e.getMessage());
        return;
      }

      SolrSearchBatchJsonResultWriter batchResultWriter = null;
      SolrSearchJsonResultWriter resultWriter = null;

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
        }
        if (batchResultWriter == null) {
          batchResultWriter = defaultJsonBatchWriter;
        }
      } else {
        temp = template.getResultWriter();
        if (temp != null) {
          resultWriter = searchResultWriterTracker.getByName(temp);
        }
        if (resultWriter == null) {
          resultWriter = defaultJsonResultWriter;
        }
      }

      response.setContentType("application/json");
      response.setCharacterEncoding("UTF-8");

      ExtendedJSONWriter write = new ExtendedJSONWriter(response.getWriter());
      write.setTidy(isTidy(request));

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

      String[] decoratorNames = template.getDecoratorNames();

      for ( String name : decoratorNames ) {
        SearchResponseDecorator decorator = searchResponseDecoratorTracker.getByName(name);
        if ( decorator != null ) {
          decorator.decorateSearchResponse(request, write);
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


  /**
   * True if our request wants the "tidy" pretty-printed format Copied from
   * org.apache.sling.servlets.get.impl.helpers.JsonRendererServlet
   */
  protected boolean isTidy(SlingHttpServletRequest req) {
    for (String selector : req.getRequestPathInfo().getSelectors()) {
      if (TIDY.equals(selector)) {
        return true;
      }
    }
    return false;
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
