package org.sakaiproject.nakamura.api.search.solr;

public interface SolrQueryFactory {

  public Query getQuery() throws Exception;

}
