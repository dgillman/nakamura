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

import com.google.common.collect.ImmutableMap;
import org.apache.sling.commons.json.JSONException;
import org.sakaiproject.nakamura.api.search.solr.SearchServiceException;
import org.sakaiproject.nakamura.api.search.solr.SearchTemplate;
import static org.sakaiproject.nakamura.api.search.solr.SolrSearchConstants.*;

import javax.jcr.Node;
import javax.jcr.Property;
import javax.jcr.PropertyIterator;
import javax.jcr.RepositoryException;
import javax.jcr.Value;
import java.util.Map;

public class JcrSearchTemplate implements SearchTemplate {
  private boolean batch;
  private String[] propertyProviderNames;
  private Map<String, String> defaultValues;
  private String[] decoratorNames;
  private String resourceType;
  private String templateString;
  private Map<String, String[]> queryOptions;
  private String path;
  private String batchResultProcessor;
  private String batchResultWriter;
  private String resultProcessor;
  private String resultWriter;

  public JcrSearchTemplate(Node queryNode) throws RepositoryException, JSONException {

    if (!queryNode.hasProperty(SAKAI_QUERY_TEMPLATE)) {
      throw new SearchServiceException("node is not a template");
    }

    path = queryNode.getPath();
    templateString = getStringProperty(queryNode, SAKAI_QUERY_TEMPLATE);
    decoratorNames = getStringArrayProperty(queryNode, SAKAI_SEARCHRESPONSEDECORATOR);
    propertyProviderNames = getStringArrayProperty(queryNode, SAKAI_PROPERTY_PROVIDER);
    resourceType = getStringProperty(queryNode, "sling:resourceType");
    batchResultProcessor = getStringProperty(queryNode, SAKAI_BATCHRESULTPROCESSOR);
    batchResultWriter = getStringProperty(queryNode, SAKAI_BATCHRESULTWRITER);
    resultProcessor = getStringProperty(queryNode, SAKAI_RESULTPROCESSOR);
    resultWriter = getStringProperty(queryNode, SAKAI_RESULTWRITER);

    batch = batchResultProcessor != null;

    if (queryNode.hasNode(SAKAI_QUERY_TEMPLATE_DEFAULTS)) {
      final ImmutableMap.Builder<String, String> builder = new ImmutableMap.Builder<String, String>();

      final Node defaults = queryNode.getNode(SAKAI_QUERY_TEMPLATE_DEFAULTS);

      final PropertyIterator defaultValueProps = defaults.getProperties();

      while(defaultValueProps.hasNext()) {
        final Property prop = defaultValueProps.nextProperty();
        builder.put(prop.getName(), prop.getString());
      }

      defaultValues = builder.build();
    }

    if (queryNode.hasNode(SAKAI_QUERY_TEMPLATE_OPTIONS)) {
      final ImmutableMap.Builder<String, String[]> builder = new ImmutableMap.Builder<String, String[]>();

      final Node options = queryNode.getNode(SAKAI_QUERY_TEMPLATE_OPTIONS);

      final PropertyIterator optionsProps = options.getProperties();

      while(optionsProps.hasNext()) {
        final Property prop = optionsProps.nextProperty();

        builder.put(prop.getName(), stringArrayFromProperty(prop));
      }

      queryOptions = builder.build();
    }
  }

  protected static String[] stringArrayFromProperty (final Property prop) throws RepositoryException {
    if (prop.isMultiple()) {
      final Value values[] = prop.getValues();

      final String strValues[] = new String[values.length];

      for (int i = 0; i < values.length; i++) {
        final Value value = values[i];
        strValues[i] = value.getString();
      }

      return strValues;
    }
    else {
      return new String[] { prop.getString() };
    }
  }

  protected static String getStringProperty (final Node node, final String key) throws RepositoryException {
    if (!node.hasProperty(key)) {
      return null;
    }

    final Property prop = node.getProperty(key);

    return prop.getString();
  }

  protected static String[] getStringArrayProperty (final Node node, final String key) throws RepositoryException {

    if (!node.hasProperty(key)) {
      return new String[0];
    }

    final Property prop = node.getProperty(key);

    return stringArrayFromProperty(prop);
  }

  @Override
  public boolean isBatch() {
    return batch;
  }

  @Override
  public String[] getPropertyProviderNames() {
    return propertyProviderNames;
  }

  @Override
  public Map<String, String> getDefaultValues() {
    return defaultValues;
  }

  @Override
  public String[] getDecoratorNames() {
    return decoratorNames;
  }

  @Override
  public String getResourceType() {
    return resourceType;
  }

  @Override
  public String getTemplateString() {
    return templateString;
  }

  @Override
  public Map<String, String[]> getQueryOptions() {
    return queryOptions;
  }

  @Override
  public String getPath() {
    return path;
  }

  @Override
  public String getBatchResultProcessor() {
    return batchResultProcessor;
  }

  @Override
  public String getBatchResultWriter() {
    return batchResultWriter;
  }

  @Override
  public String getResultProcessor() {
    return resultProcessor;
  }

  @Override
  public String getResultWriter() {
    return resultWriter;
  }
}