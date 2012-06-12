package org.sakaiproject.nakamura.api.search.solr;

import java.util.Map;

public interface SearchTemplate {

  public boolean isBatch();

  public String[] getPropertyProviderNames();

  public Map<String,String> getDefaultValues();

  public String[] getDecoratorNames();

  public String getResourceType();

  public String getTemplateString();

  public Map<String,String[]> getQueryOptions();

  public String getPath();

  String getBatchResultProcessor();

  String getBatchResultWriter();

  String getResultProcessor();

  String getResultWriter();
}
