package org.sakaiproject.nakamura.repairmigration.api;

import org.apache.sling.commons.json.JSONException;
import org.sakaiproject.nakamura.api.lite.content.Content;

public interface DocRepair {
  boolean detectMigrationErrors(Content content) throws JSONException;
}
