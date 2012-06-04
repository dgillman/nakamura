package org.sakaiproject.nakamura.search.solr;

import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Properties;
import org.apache.felix.scr.annotations.Property;
import org.apache.felix.scr.annotations.Reference;
import org.apache.felix.scr.annotations.ReferenceCardinality;
import org.apache.felix.scr.annotations.ReferencePolicy;
import org.apache.felix.scr.annotations.References;
import org.apache.felix.scr.annotations.Service;
import org.sakaiproject.nakamura.api.search.solr.SolrSearchBatchResultProcessor;
import org.sakaiproject.nakamura.api.search.solr.SolrSearchConstants;
import org.sakaiproject.nakamura.api.search.solr.SolrSearchResultWriter;

import java.util.Map;

@Component(immediate = true, metatype = true)
@Properties(value = {@Property(name = "service.vendor", value = "The Sakai Foundation")})
@Service(value = SolrSearchResultWriterTracker.class)
@References(value = {
        @Reference(name = "SearchResultWriter", referenceInterface = SolrSearchResultWriter.class,
                bind = "bindHelper", unbind = "unbindHelper",
                cardinality = ReferenceCardinality.OPTIONAL_MULTIPLE, policy = ReferencePolicy.DYNAMIC)})
public class SolrSearchResultWriterTracker extends AbstractSolrSearchServletHelperTracker<SolrSearchResultWriter>
  implements SolrSearchServletHelperTracker<SolrSearchResultWriter> {

  public SolrSearchResultWriterTracker() {
    super(SolrSearchConstants.REG_WRITER_NAMES, SolrSearchResultWriter.DEFAULT_WRITER_PROP);
  }

  protected void bindHelper(SolrSearchResultWriter helper, Map<?, ?> props) {
    bind(helper, props);
  }

  protected void unbindHelper(SolrSearchResultWriter helper, Map<?, ?> props) {
    unbind(helper, props);
  }


}
