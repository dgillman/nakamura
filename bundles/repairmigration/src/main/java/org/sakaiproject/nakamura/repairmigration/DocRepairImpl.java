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
package org.sakaiproject.nakamura.repairmigration;

import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Reference;
import org.apache.felix.scr.annotations.Service;
import org.apache.sling.commons.json.JSONException;
import org.apache.sling.commons.json.JSONObject;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.sakaiproject.nakamura.api.files.FilesConstants;
import org.sakaiproject.nakamura.api.lite.ClientPoolException;
import org.sakaiproject.nakamura.api.lite.Repository;
import org.sakaiproject.nakamura.api.lite.Session;
import org.sakaiproject.nakamura.api.lite.StorageClientUtils;
import org.sakaiproject.nakamura.api.lite.content.Content;
import org.sakaiproject.nakamura.api.lite.content.ContentManager;
import org.sakaiproject.nakamura.repairmigration.api.DocRepair;
import org.sakaiproject.nakamura.util.ExtendedJSONWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.StringWriter;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.Stack;

@Service
@Component(enabled = true)
public class DocRepairImpl implements DocRepair {
  private static final Logger LOGGER = LoggerFactory.getLogger(DocRepairImpl.class);

  public static final int CURRENT_SCHEMA_VERSION = 2;

  public static final String STRUCTURE_ZERO = FilesConstants.STRUCTURE_FIELD_STEM + "0";
  @Reference
  protected Repository repository;

  protected Set<JSONObject> getPagesFromStructure0 (JSONObject structure0, JSONObject sakaiDoc)
     throws JSONException {
    final Set<JSONObject> pages = new HashSet<JSONObject>();
    for (Iterator<String> referenceIterator = structure0.keys(); referenceIterator.hasNext(); ) {
      final String reference = referenceIterator.next();
      if (reference.startsWith("_")) {
        continue;
      }
      final JSONObject structureItem = structure0.getJSONObject(reference);
      if (!structureItem.has("_ref")) {
        continue;
      }
      final String pageId = structureItem.getString("_ref");
      if (!sakaiDoc.has(pageId)) {
        LOGGER.warn("no page found for id: {} in content: {}", new String[] {pageId, sakaiDoc.getString("_path")});
        continue;
      }
      pages.addAll(getPagesFromStructure0(structureItem, sakaiDoc));
      pages.add(sakaiDoc.getJSONObject(pageId));
    }
    return pages;
  }

  protected boolean generatesOmission(Content content) throws JSONException {
    final JSONObject sakaiDocJSON = getJSONFromContent(content);
    final JSONObject structure0 = new JSONObject(getStructure0(content));
    final Set<JSONObject> pages = getPagesFromStructure0(structure0, sakaiDocJSON);
    boolean result = false;

    for (JSONObject page : pages) {
      if (!page.has("page")) {
        continue;
      }
      String pageData = page.getString("page");
      Document pageDOM = Jsoup.parse(pageData);
      Elements topLevelElements = pageDOM.select("body").first().children();
      if (widgetPlacementTriggersError(topLevelElements)){
        String unmigratedHtml = getPostWidgetHTML(topLevelElements);

        if(unmigratedHtml != null) {
          String oneline = unmigratedHtml.replaceAll("[\\r\\n]", "");
          LOGGER.info("MIGRATION ERROR: {} {} [{}]",
             new String[] {Integer.toString(oneline.length()), page.getString("_path"), oneline});
          result = true;
        }
      }
    }
    return result;
  }

  private boolean widgetPlacementTriggersError(Elements topLevelElements) {
    for(Element element : topLevelElements) {
      for (Element widgetElement : element.select(".widget_inline")) {
        String[] elementParts = element.html().replaceFirst(widgetElement.toString(), "##xxxx##").split("##");
        if(elementParts.length > 1 && ("xxxx").equals(elementParts[1]) && !"".equals(elementParts[0])) {
          return true;
        }
      }
    }
    return false;
  }

  protected String getPostWidgetHTML(Elements topLevelElements) throws JSONException {

    Stack<String> htmlBits = new Stack<String>();

    int numElements = topLevelElements.size();
    for (int ind = numElements; ind > 0; ind--) {
      Element mayHaveHtml = topLevelElements.get(ind - 1);
      if (mayHaveHtml.select(".widget_inline").size() > 0) {
        break;
      }
      htmlBits.push(mayHaveHtml.toString());
    }
    if (!htmlBits.empty()) {
      StringBuilder builder = new StringBuilder();
      while (!htmlBits.empty()) {
        builder.append(htmlBits.pop());
      }
      return builder.toString();
    }

    return null;
  }

  @Override
  public boolean detectMigrationErrors (Content content) throws JSONException {
    LOGGER.trace("detectMigrationErrors(Content {})", content);
    final boolean hasMigrationErrors = content != null && content.hasProperty(STRUCTURE_ZERO)
        && schemaVersionIsCurrent(content) && generatesOmission(content);
    LOGGER.trace("detectMigrationErrors() --> hasMigrationErrors = {} for {}",
        hasMigrationErrors, content);
    return hasMigrationErrors;
  }

  private boolean schemaVersionIsCurrent(Content content) {
    LOGGER.trace("schemaVersionIsCurrent(Content {})", content);
    final boolean schemaVersionIsCurrent = (content
        .hasProperty(FilesConstants.SCHEMA_VERSION) && StorageClientUtils.toInt(content
        .getProperty(FilesConstants.SCHEMA_VERSION)) >= CURRENT_SCHEMA_VERSION);
    LOGGER.trace("schemaVersionIsCurrent() --> schemaVersionIsCurrent = {} for {}",
        schemaVersionIsCurrent, content);
    return schemaVersionIsCurrent;
  }

  protected JSONObject getJSONFromContent (Content content) {
    LOGGER.trace("getJSONFromContent(Content {})", content);
    if (content == null) {
      LOGGER
          .debug(
              "getJSONFromContent() --> content == null for {}",
              content);
      return null;
    }
    StringWriter stringWriter = new StringWriter();
    ExtendedJSONWriter stringJsonWriter = new ExtendedJSONWriter(stringWriter);
    Session adminSession = null;
    try {
      adminSession = repository.loginAdministrative();
      ContentManager adminContentManager = adminSession.getContentManager();

      // pull the content JSON using an admin session
      Content adminContent = adminContentManager.get(content.getPath());
      ExtendedJSONWriter.writeContentTreeToWriter(stringJsonWriter, adminContent, false, -1);

      return new JSONObject(stringWriter.toString());
    } catch (Exception e) {
      LOGGER.error(e.getLocalizedMessage(), e);
      throw new RuntimeException("Error getting content: " + content.getPath(), e);
    } finally {
      if (adminSession != null) {
        try {
          adminSession.logout();
        } catch (ClientPoolException e) {
          LOGGER.error(e.getLocalizedMessage(), e);
        }
      }
    }
  }

  private String getStructure0(Content content) {
    LOGGER.trace("getStructure0(Content {})", content);
    Object structure0 = content.getProperty(STRUCTURE_ZERO);
    LOGGER.trace("getStructure0() --> structure0 = {} for {}", structure0,
        content);
    return (structure0 != null) ? structure0.toString() : null;
  }

}
