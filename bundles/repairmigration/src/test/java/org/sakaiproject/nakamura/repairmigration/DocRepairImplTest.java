package org.sakaiproject.nakamura.repairmigration;

import static org.junit.Assert.*;

import org.apache.sling.commons.json.JSONException;
import org.apache.sling.commons.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import org.sakaiproject.nakamura.api.lite.Repository;
import org.sakaiproject.nakamura.api.lite.Session;
import org.sakaiproject.nakamura.api.lite.accesscontrol.AccessControlManager;
import org.sakaiproject.nakamura.api.lite.content.Content;
import org.sakaiproject.nakamura.api.lite.content.ContentManager;
import org.sakaiproject.nakamura.api.resource.lite.LiteJsonImporter;
import org.sakaiproject.nakamura.lite.BaseMemoryRepository;
import org.sakaiproject.nakamura.util.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;

public class DocRepairImplTest {
  private static final Logger LOGGER = LoggerFactory.getLogger(DocRepairImplTest.class);

  private static final int ALL_ACCESS = 28679;

  private Repository repository;

  private DocRepairImpl docRepair;

  private Session session;

  private JSONObject readJSONFromFile(String fileName) throws IOException, JSONException {
    InputStream in = getClass().getClassLoader().getResourceAsStream(fileName);
    return new JSONObject(IOUtils.readFully(in, "utf-8"));
  }

  @Before
  public void setup() throws Exception {
    docRepair = new DocRepairImpl();
    repository = new BaseMemoryRepository().getRepository();
    docRepair.repository = repository;
    session = repository.loginAdministrative();
  }

  @Test
  public void test_content() throws Exception {
    final String DOC_PATH = "/p/mIumMAqaa";
    repository = new BaseMemoryRepository().getRepository();
    docRepair.repository = repository;
    Session session = repository.loginAdministrative();
    ContentManager contentManager = session.getContentManager();
    AccessControlManager accessControlManager = session.getAccessControlManager();
    JSONObject doc = readJSONFromFile("mIumMAqaa.infinity.json");
    LiteJsonImporter jsonImporter = new LiteJsonImporter();
    jsonImporter.importContent(contentManager, doc, DOC_PATH, true, true, false, true,
        accessControlManager, true);
    Content docContent = contentManager.get(DOC_PATH);
    assertTrue(docRepair.detectMigrationErrors(docContent));
  }
}
