package org.sakaiproject.nakamura.api.search.solr;

public class SearchServiceException extends RuntimeException {
  public SearchServiceException() {
    super();    //To change body of overridden methods use File | Settings | File Templates.
  }

  public SearchServiceException(String s) {
    super(s);    //To change body of overridden methods use File | Settings | File Templates.
  }

  public SearchServiceException(String s, Throwable throwable) {
    super(s, throwable);    //To change body of overridden methods use File | Settings | File Templates.
  }

  public SearchServiceException(Throwable throwable) {
    super(throwable);    //To change body of overridden methods use File | Settings | File Templates.
  }
}
