package org.sakaiproject.nakamura.search.solr;

import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Properties;
import org.apache.felix.scr.annotations.Property;
import org.apache.felix.scr.annotations.Reference;
import org.apache.felix.scr.annotations.ReferenceCardinality;
import org.apache.felix.scr.annotations.ReferencePolicy;
import org.apache.felix.scr.annotations.References;
import org.apache.felix.scr.annotations.Service;
import org.sakaiproject.nakamura.api.search.solr.SolrSearchBatchResultWriter;
import org.sakaiproject.nakamura.api.search.solr.SolrSearchConstants;
import org.sakaiproject.nakamura.api.search.solr.SolrSearchResultWriter;

import java.util.Map;

@Component(immediate = true, metatype = true)
@Properties(value = {@Property(name = "service.vendor", value = "The Sakai Foundation")})
@Service(value = SolrSearchBatchResultWriterTracker.class)
@References(value = {
        @Reference(name = "SearchBatchResultWriter", referenceInterface = SolrSearchBatchResultWriter.class,
                bind = "bindHelper", unbind = "unbindHelper",
                cardinality = ReferenceCardinality.OPTIONAL_MULTIPLE, policy = ReferencePolicy.DYNAMIC)})
public class SolrSearchBatchResultWriterTracker extends AbstractSolrSearchServletHelperTracker<SolrSearchBatchResultWriter>
  implements SolrSearchServletHelperTracker<SolrSearchBatchResultWriter> {

  public SolrSearchBatchResultWriterTracker() {
    super(SolrSearchConstants.REG_BATCH_WRITER_NAMES, SolrSearchBatchResultWriter.DEFAULT_BATCH_WRITER_PROP);
  }

  protected void bindHelper(SolrSearchBatchResultWriter helper, Map<?, ?> props) {
    bind(helper, props);
  }

  protected void unbindHelper(SolrSearchBatchResultWriter helper, Map<?, ?> props) {
    unbind(helper, props);
  }


}
