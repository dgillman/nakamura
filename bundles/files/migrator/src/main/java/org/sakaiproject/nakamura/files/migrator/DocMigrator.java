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
package org.sakaiproject.nakamura.files.migrator;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Sets;
import org.apache.commons.lang.StringUtils;
import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Reference;
import org.apache.felix.scr.annotations.Service;
import org.apache.sling.commons.json.JSONArray;
import org.apache.sling.commons.json.JSONException;
import org.apache.sling.commons.json.JSONObject;
import org.sakaiproject.nakamura.api.files.FileMigrationService;
import org.sakaiproject.nakamura.api.files.FilesConstants;
import org.sakaiproject.nakamura.api.lite.ClientPoolException;
import org.sakaiproject.nakamura.api.lite.Repository;
import org.sakaiproject.nakamura.api.lite.Session;
import org.sakaiproject.nakamura.api.lite.StorageClientException;
import org.sakaiproject.nakamura.api.lite.StorageClientUtils;
import org.sakaiproject.nakamura.api.lite.accesscontrol.AccessDeniedException;
import org.sakaiproject.nakamura.api.lite.accesscontrol.AclModification;
import org.sakaiproject.nakamura.api.lite.accesscontrol.Permissions;
import org.sakaiproject.nakamura.api.lite.accesscontrol.Security;
import org.sakaiproject.nakamura.api.lite.accesscontrol.AclModification.Operation;
import org.sakaiproject.nakamura.api.lite.authorizable.Group;
import org.sakaiproject.nakamura.api.lite.authorizable.User;
import org.sakaiproject.nakamura.api.lite.content.Content;
import org.sakaiproject.nakamura.api.lite.content.ContentManager;
import org.sakaiproject.nakamura.api.resource.lite.LiteJsonImporter;
import org.sakaiproject.nakamura.util.ExtendedJSONWriter;
import org.sakaiproject.nakamura.util.PathUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.StringWriter;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@Component(enabled = true)
public class DocMigrator implements FileMigrationService {
  private static final Logger LOGGER = LoggerFactory.getLogger(DocMigrator.class);

  @Reference
  protected Repository repository;
  private final PageMigrator pageMigrator = new PageMigrator(this);

  protected void processStructure0(JSONObject subtree, JSONObject originalStructure, JSONObject newStructure) throws JSONException {
    LOGGER.trace("processStructure0(JSONObject {}, JSONObject {}, JSONObject {})",
        new Object[] { subtree, originalStructure, newStructure });
    Set<String> widgetsUsed = Sets.newHashSet();
    for (Iterator<String> referenceIterator = subtree.keys(); referenceIterator.hasNext(); ) {
      String reference = referenceIterator.next();
      pageMigrator.processPageReference(subtree, originalStructure, newStructure, widgetsUsed, reference);
    }
    // pruning the widgets at the top level
    for (String widgetId : widgetsUsed) {
      if (newStructure.has(widgetId)) {
        newStructure.remove(widgetId);
      }
    }
  }

  protected JSONObject createNewPageStructure(JSONObject structure0, JSONObject originalDoc) throws JSONException {
    LOGGER.trace("createNewPageStructure(JSONObject {}, JSONObject {})", structure0,
        originalDoc);
    JSONObject newDoc = new JSONObject(originalDoc.toString());
    processStructure0(structure0, originalDoc, newDoc);
    // the finishing touches
    newDoc.put(FilesConstants.SCHEMA_VERSION, 2);
    return newDoc;
  }

  @Override
  public boolean fileContentNeedsMigration(Content content) {
    LOGGER.trace("fileContentNeedsMigration(Content {})", content);
    try {
      final boolean needsMigration = !(content == null || isNotSakaiDoc(content)
          || schemaVersionIsCurrent(content) || contentHasUpToDateStructure(content));
      LOGGER.trace("fileContentNeedsMigration() --> needsMigration = {} for {}",
          needsMigration, content);
      return needsMigration;
    } catch (SakaiDocMigrationException e) {
      LOGGER.error(e.getLocalizedMessage(), e);
      LOGGER.error("Could not determine requiresMigration with content {}", content.getPath());
      throw new RuntimeException("Could not determine requiresMigration with content " + content.getPath());
    }
  }

  @Override
  public boolean isPageNode(Content content, ContentManager contentManager)
      throws StorageClientException, AccessDeniedException {
    LOGGER
        .debug("isPageNode(Content {}, ContentManager contentManager)", content);
    if ( content != null && content.hasProperty("page")) {
      String parentPath = PathUtils.getParentReference(content.getPath());
      Content parent = contentManager.get(parentPath);
      if ( parent != null ) {
        final boolean isSakaiDoc = !(isNotSakaiDoc(parent));
        LOGGER.trace("isPageNode() --> isSakaiDoc = {} for {}", isSakaiDoc,
            content);
        return isSakaiDoc;
      }
    }
    LOGGER.trace("isPageNode() --> return false for {}", content);
    return false;
  }

  protected boolean requiresMigration(JSONObject subtree, Content originalStructure, ContentManager contentManager) throws JSONException {
    LOGGER.trace(
        "requiresMigration(JSONObject {}, Content {}, ContentManager contentManager)",
        subtree, originalStructure);
    boolean requiresMigration = false;
    for (Iterator<String> keysIterator = subtree.keys(); keysIterator.hasNext(); ) {
      String key = keysIterator.next();
      if (!key.startsWith("_")) {
        JSONObject structureItem = subtree.getJSONObject(key);
        if (!structureItem.has("_ref") || StringUtils.isBlank(structureItem.getString("_ref"))) {
          continue;
        }
        String ref = structureItem.getString("_ref");
        if (!contentManager.exists(originalStructure.getPath() + "/" + ref + "/rows")) {
          return true;
        }
        requiresMigration = requiresMigration(structureItem, originalStructure, contentManager);
      }
    }
    LOGGER.trace("requiresMigration() --> requiresMigration = {} for {}",
        requiresMigration, originalStructure);
    return requiresMigration;
  }

  private boolean isNotSakaiDoc(Content content) {
    LOGGER.trace("isNotSakaiDoc(Content {})", content);
    final boolean isNotSakaiDoc = !content.hasProperty(STRUCTURE_ZERO);
    LOGGER.trace("isNotSakaiDoc() --> isNotSakaiDoc = {} for {}", isNotSakaiDoc,
        content);
    return isNotSakaiDoc;
  }

  private boolean contentHasUpToDateStructure(Content content) throws SakaiDocMigrationException {
    LOGGER.trace("contentHasUpToDateStructure(Content {})", content);
    Session adminSession = null;
    try {
      adminSession = repository.loginAdministrative();
      JSONObject structure0 = new JSONObject(getStructure0(content));
      final boolean requiresMigration = !requiresMigration(structure0, content,
          adminSession.getContentManager());
      LOGGER.trace("contentHasUpToDateStructure() --> requiresMigration = {} for {}",
          requiresMigration, content);
      return requiresMigration;
    } catch (Exception e) {
      LOGGER.error(e.getLocalizedMessage(), e);
      throw new SakaiDocMigrationException("Error determining if content has an up to date structure.", e);
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

  private boolean schemaVersionIsCurrent(Content content) {
    LOGGER.trace("schemaVersionIsCurrent(Content {})", content);
    final boolean schemaVersionIsCurrent = (content
        .hasProperty(FilesConstants.SCHEMA_VERSION) && StorageClientUtils.toInt(content
        .getProperty(FilesConstants.SCHEMA_VERSION)) >= CURRENT_SCHEMA_VERSION);
    LOGGER.trace("schemaVersionIsCurrent() --> schemaVersionIsCurrent = {} for {}",
        schemaVersionIsCurrent, content);
    return schemaVersionIsCurrent;
  }

  @Override
  public void migrateFileContent(Content content) {
    LOGGER.trace("migrateFileContent(Content {})", content);
    if (content == null || !content.hasProperty(STRUCTURE_ZERO)) {
      LOGGER
          .debug(
              "migrateFileContent() --> content == null || !content.hasProperty(STRUCTURE_ZERO) for {}",
              content);
      return;
    }
    LOGGER.debug("Starting migration of {} : {}", content.getId(), content.getPath());
    StringWriter stringWriter = new StringWriter();
    ExtendedJSONWriter stringJsonWriter = new ExtendedJSONWriter(stringWriter);
    Session adminSession = null;
    try {
      adminSession = repository.loginAdministrative();
      ContentManager adminContentManager = adminSession.getContentManager();
      
      // pull the content JSON using an admin session
      Content adminContent = adminContentManager.get(content.getPath());
      ExtendedJSONWriter.writeContentTreeToWriter(stringJsonWriter, adminContent, false, -1);
      JSONObject newPageStructure = createNewPageStructure(new JSONObject(getStructure0(adminContent)), new JSONObject(stringWriter.toString()));

      JSONObject convertedStructure = (JSONObject) convertArraysToObjects(newPageStructure);
      validateStructure(convertedStructure);
      
      LOGGER.debug("Generated new page structure. Saving content {} : {}",
          content.getId(), content.getPath());
      LiteJsonImporter liteJsonImporter = new LiteJsonImporter();
      liteJsonImporter.importContent(adminContentManager, convertedStructure, content.getPath(), true, true, false, true, adminSession.getAccessControlManager(), Boolean.FALSE);
      
      // lock down basiclti widget ltiKeys
      List<Content> basicLtiWidgets = new LinkedList<Content>();
      collectResourcesOfType(adminContent, "sakai/basiclti", basicLtiWidgets);
      for (Content basicLtiWidget : basicLtiWidgets) {
        String ltiKeysPath = StorageClientUtils.newPath(basicLtiWidget.getPath(), "ltiKeys");
        if (adminContentManager.exists(ltiKeysPath)) {
          accessControlSensitiveNode(ltiKeysPath, adminSession);
        }
      }
    } catch (Exception e) {
      LOGGER.error(e.getLocalizedMessage(), e);
      throw new RuntimeException("Error while migrating " + content.getPath(), e);
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

  @Override
  public Content migrateSinglePage(Content documentContent, Content pageContent) {
    LOGGER.trace("migrateSinglePage(Content {}, Content {})", documentContent,
        pageContent);
    try {
      JSONObject documentJson = jsonFromContent(documentContent);
      String ref = PathUtils.lastElement(pageContent.getPath());
      documentJson.getJSONObject(ref).put("page", pageContent.getProperty("page"));
      String contentId = documentJson.getString("_path");
      Set<String> widgetsUsed = Sets.newHashSet();
      JSONObject migratedPage = pageMigrator.migratePage(documentJson, contentId, widgetsUsed, ref);
      for (Map.Entry pageContentEntry : pageContent.getProperties().entrySet()) {
        if ("page".equals(pageContentEntry.getKey())) {
          continue;
        }
        migratedPage.put((String) pageContentEntry.getKey(), pageContentEntry.getValue());
      }
      return contentFromJson(migratedPage);
    } catch (JSONException e) {
      LOGGER.error(e.getLocalizedMessage(), e);
      throw new RuntimeException("failed to migrate single page: "
          + e.getLocalizedMessage(), e);
    }
  }

  protected Content contentFromJson(JSONObject jsonObject) throws JSONException {
    LOGGER.trace("contentFromJson(JSONObject {})", jsonObject);
    ImmutableMap.Builder<String, Object> propBuilder = ImmutableMap.builder();
    for (Iterator<String> jsonKeys = jsonObject.keys(); jsonKeys.hasNext();) {
      String key = jsonKeys.next();
      Object value = jsonObject.get(key);
      if (value instanceof JSONObject) {
        continue;
      } else if (value instanceof JSONArray) {
        JSONArray array = (JSONArray)value;
        if (array.length() > 0) {
          Object[] outputArray = null;
          Object zeroth = array.get(0);
          if (zeroth instanceof String) {
            outputArray = new String[array.length()];
          } else if (zeroth instanceof Boolean) {
            outputArray = new Boolean[array.length()];
          } else if (zeroth instanceof Integer) {
            outputArray = new Integer[array.length()];
          } else if (zeroth instanceof Double) {
            outputArray = new Double[array.length()];
          } else {
            outputArray = new Object[array.length()];
          }
          for (int i = 0; i < array.length(); i++) {
            outputArray[i] = array.get(i);
          }
          value = outputArray;
        }
      }
      if (!"version".equalsIgnoreCase(key)) {
        propBuilder.put(key, value);
      } else {
        LOGGER.debug("Skipping the 'version' property, as we'll add our own.");
      }
    }
    propBuilder.put("version", jsonObject.toString());
    final Content content = new Content(jsonObject.getString("_path"),
        propBuilder.build());
    LOGGER.trace("contentFromJson() --> content = {} from {}", content, jsonObject);
    return content;
  }

  protected JSONObject jsonFromContent(Content documentContent) throws JSONException {
    LOGGER.trace("jsonFromContent(Content {})", documentContent);
    StringWriter stringWriter = new StringWriter();
    ExtendedJSONWriter stringJsonWriter = new ExtendedJSONWriter(stringWriter);
    ExtendedJSONWriter.writeContentTreeToWriter(stringJsonWriter, documentContent, false, -1);
    return new JSONObject(stringWriter.toString());
  }

  protected Object convertArraysToObjects(Object json) throws JSONException {
    LOGGER.trace("convertArraysToObjects(Object {})", json);
    if (json instanceof JSONObject) {
      JSONObject jsonObject = (JSONObject) json;
      for (Iterator<String> keyIterator = jsonObject.keys(); keyIterator.hasNext(); ) {
        String key = keyIterator.next();
        if (objectIsArrayOfJSONObject(jsonObject.get(key))) {
          jsonObject.put(key, convertArrayToObject((JSONArray) jsonObject.get(key)));
        } else if (jsonObject.get(key) instanceof JSONObject) {
          jsonObject.put(key, convertArraysToObjects(jsonObject.get(key)));
        }
      }
      return jsonObject;
    } else if (objectIsArrayOfJSONObject(json)) {
      return convertArrayToObject((JSONArray) json);
    } else {
      return json;
    }
  }

  private boolean objectIsArrayOfJSONObject(Object json) throws JSONException {
    LOGGER.trace("objectIsArrayOfJSONObject(Object json)");
    return json instanceof JSONArray && ((JSONArray) json).length() > 0 &&
      (((JSONArray) json).get(0) instanceof JSONObject || ((JSONArray) json).get(0) instanceof JSONArray);
  }

  private String getStructure0(Content content) {
    LOGGER.trace("getStructure0(Content {})", content);
    Object structure0 = content.getProperty(STRUCTURE_ZERO);
    LOGGER.trace("getStructure0() --> structure0 = {} for {}", structure0,
        content);
    return (structure0 != null) ? structure0.toString() : null;
  }
  
  protected JSONObject convertArrayToObject(JSONArray jsonArray) throws JSONException {
    LOGGER.trace("convertArrayToObject(JSONArray jsonArray)");
    JSONObject arrayObject = new JSONObject();
    for (int i = 0; i < jsonArray.length(); i++) {
      arrayObject.put("__array__" + i + "__", convertArraysToObjects(jsonArray.get(i)));
    }
    return arrayObject;
  }

  protected void validateStructure(JSONObject newPageStructure) throws JSONException, SakaiDocMigrationException {
    LOGGER.trace("validateStructure(JSONObject newPageStructure)");
    if (!newPageStructure.has(FilesConstants.SCHEMA_VERSION) || !newPageStructure.has(STRUCTURE_ZERO)) {
      LOGGER.debug("new page structure FAILED validation : {}", newPageStructure);
      throw new SakaiDocMigrationException();
    }
    LOGGER.debug("new page structure passes validation.");
  }

  private void collectResourcesOfType(Content content, String resourceType, List<Content> resources) {
    LOGGER.trace(
        "collectResourcesOfType(Content {}, String {}, List<Content> resources)",
        content, resourceType);
    if (resourceType.equals(content.getProperty(Content.SLING_RESOURCE_TYPE_FIELD))) {
      resources.add(content);
    }
    for (Content child : content.listChildren()) {
      collectResourcesOfType(child, resourceType, resources);
    }
  }
  
  /**
   * Apply the necessary access control entries so that only admin users can read/write
   * the sensitive node.
   * 
   * @param sensitiveNodePath
   * @param adminSession
   * @throws StorageClientException
   * @throws AccessDeniedException
   */
  private void accessControlSensitiveNode(final String sensitiveNodePath,
      final Session adminSession) throws StorageClientException, AccessDeniedException {
    LOGGER.trace(
        "accessControlSensitiveNode(final String {}, final Session adminSession)",
        sensitiveNodePath);
    adminSession.getAccessControlManager().setAcl(
        Security.ZONE_CONTENT,
        sensitiveNodePath,
        new AclModification[] {
            new AclModification(AclModification.denyKey(User.ANON_USER), Permissions.ALL
                .getPermission(), Operation.OP_REPLACE),
            new AclModification(AclModification.denyKey(Group.EVERYONE), Permissions.ALL
                .getPermission(), Operation.OP_REPLACE) });
  }
}
