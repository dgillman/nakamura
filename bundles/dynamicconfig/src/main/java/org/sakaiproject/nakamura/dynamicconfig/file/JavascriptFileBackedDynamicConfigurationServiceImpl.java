package org.sakaiproject.nakamura.dynamicconfig.file;

import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Properties;
import org.apache.felix.scr.annotations.Property;
import org.apache.felix.scr.annotations.Reference;
import org.apache.felix.scr.annotations.Service;
import org.apache.sling.commons.json.JSONException;
import org.apache.sling.commons.json.JSONObject;
import org.apache.sling.commons.osgi.PropertiesUtil;
import org.apache.sling.jcr.api.SlingRepository;
import org.osgi.service.component.ComponentContext;
import org.sakaiproject.nakamura.api.dynamicconfig.DynamicConfigurationService;
import org.sakaiproject.nakamura.api.dynamicconfig.DynamicConfigurationServiceException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jcr.Node;
import javax.jcr.RepositoryException;
import javax.jcr.Session;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.math.BigInteger;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Dictionary;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

@Service
@Component(immediate = true, metatype=true, label = "javascript-backed-dynamic-config")
@Properties(value = {
    @Property(name = JavascriptFileBackedDynamicConfigurationServiceImpl.CONFIG_DIR,
       value = JavascriptFileBackedDynamicConfigurationServiceImpl.DFT_CONFIG_DIR),
    @Property(name = JavascriptFileBackedDynamicConfigurationServiceImpl.CONFIG_FILENAME,
       value = JavascriptFileBackedDynamicConfigurationServiceImpl.DFT_CONFIG_FILENAME),
    @Property(name = JavascriptFileBackedDynamicConfigurationServiceImpl.CUSTOM_DIR,
       value = JavascriptFileBackedDynamicConfigurationServiceImpl.DFT_CUSTOM_DIR)
})
public class JavascriptFileBackedDynamicConfigurationServiceImpl implements DynamicConfigurationService {

  /**
   * default log
   */
  private final Logger log = LoggerFactory.getLogger(getClass());

  /**
   * size of file read chunks
   */
  private static final int CHUNK_SIZE               = 8192;

  // parameter names for configuration
  public static final String CONFIG_DIR             = "config.master.dir";
  public static final String CONFIG_FILENAME        = "config.master.filename";
  public static final String CUSTOM_DIR             = "config.custom.dir";

  // default configuration values
  public static final String DFT_CONFIG_DIR         = "sling/dynamicconfig";
  public static final String DFT_CUSTOM_DIR         = "sling/dynamicconfig/custom";
  public static final String DFT_CONFIG_FILENAME    = "config.js";

  // fallback source of config is not provided on filesystem
  public static final String FALLBACK_SLING_CONFIG = "/dev/configuration/config.js";

  @Reference
  protected SlingRepository repository;

  // directory containing the configuration files
  protected String configurationDir;
  // main configuration file
  protected String configurationFilename;
  // directory containing configuration overrides
  protected String customDir;

  protected static MessageDigest MD5;

  public JavascriptFileBackedDynamicConfigurationServiceImpl () {
    // create a new MD5 digester
    try {
      MD5 = MessageDigest.getInstance("MD5");
    } catch (NoSuchAlgorithmException e) {
      // there is something really wrong if we get here - log the error and return a random UUID
      log.error("failed to obtain MessageDigest object to compute MD5 sum", e);
      log.warn ("unique configuration cache key will be used for every request - no caching is being preformed for config");
      MD5 = null;
    }
  }

  /**
   * gets the main configuration file
   * @return File
   */
  private final File getConfigurationFile() {
    return new File (
    		new File(configurationDir).getAbsolutePath()
    		+ File.separator
    		+ configurationFilename);
  }

  /**
   * gets the overrides directory.
   * @return File
   */
  private final File getCustomDir() {
    if (customDir == null) {
      return null;
    }
    return new File(customDir);
  }

  /**
   * process the configuration of this component
   * @param componentContext
   * @throws Exception
   */
  protected void activate (Map<String,Object> props) throws Exception {
    configurationDir = PropertiesUtil.toString(props.get(CONFIG_DIR), DFT_CONFIG_DIR);
    configurationFilename = PropertiesUtil.toString(props.get(CONFIG_FILENAME), DFT_CONFIG_FILENAME);
    customDir = PropertiesUtil.toString(props.get(CUSTOM_DIR), DFT_CUSTOM_DIR);

    // sanity check - can we read the configuration? if not it's an error
    final File configFile = getConfigurationFile();

    if (!(configFile.exists() && configFile.canRead())) {
      log.info("no master config file: [" + configFile.getAbsolutePath() + "], JCR content [" +
         FALLBACK_SLING_CONFIG + "] will be used instead");
    }

    // sanity check - can we read configuration overrides? if not - warn
    final File customDir = getCustomDir().getAbsoluteFile();

    if (customDir.exists()) {
      if (!customDir.canRead()) {
        log.error("custom configuration directory is unreadable: [" + customDir.getAbsolutePath() + "]");
      }
    } else {
      log.warn("custom configuration directory does not exist - no custom configurations will be applied");
    }
  }

  /**
   * Produce a JSON object from the supplied JSON file.
   *
   * @param jsonFile
   * @return JSONObject representation of the file content
   * @throws IOException
   * @throws FileNotFoundException
   * @throws JSONException
   */
  protected final String collectConfiguration (File jsonFile)
     throws IOException, FileNotFoundException {

    // return a JSON object built from the file content
    return readFileAsString(jsonFile);
  }
  
  /**
   * Produce a JSON object from the supplied JSON file.
   *
   * @param jsFile
   * @return String representation of the file content
   * @throws IOException
   * @throws FileNotFoundException
   * @throws JSONException
   */
  protected final String collectConfigurationJS (File jsFile) throws IOException, FileNotFoundException {
	  return readFileAsString(jsFile);
  }

  /**
   * Read a file into a String  
   * @param file the file to read
   * @return the contents of file or null
   * @throws IOException
   * @throws FileNotFoundException
   */
  protected final String readFileAsString (File file)
     throws IOException, FileNotFoundException {

    if (file == null) {
      log.error("attempt to process null configuration file aborted");
      return null;
    }

    if (!file.canRead()) {
      log.error("attempt to process empty configuration file aborted");
      return null;
    }

    // read the file
    final FileReader reader = new FileReader(file);
    final StringBuffer buffer = new StringBuffer();
    char[] chunk = new char[CHUNK_SIZE];
    int read = 0;

    try {
      while ( (read = reader.read(chunk)) > 0) {
        buffer.append(chunk, 0, read);
      }
    }
    finally {
      if (reader != null) {
        try {
          reader.close();
        } catch (Exception e) {
          log.error("error closing file [" + file.getAbsolutePath() + "]", e);
        }
      }
    }
    return buffer.toString();
  }

  protected final String collectConfigurationFromJCR() throws IOException,
     DynamicConfigurationServiceException {

    Session session = null;

    try {
      session = repository.login();
      final Node node = session.getNode(FALLBACK_SLING_CONFIG);

      if (node == null) {
        throw new DynamicConfigurationServiceException("Configuration is not installed in JCR repository at " +
           FALLBACK_SLING_CONFIG);
      }

      final Node jcrContent = node.getNode("jcr:content");
      final InputStream content = jcrContent.getProperty("jcr:data").getBinary().getStream();
      final StringBuffer buffer = new StringBuffer();
      byte[] chunk = new byte[CHUNK_SIZE];
      int read = 0;

      try {
        while ( (read = content.read(chunk)) > 0) {
          buffer.append(new String (chunk, 0, read));
        }
      }
      finally {
        if (content != null) {
          try {
            content.close();
          } catch (Exception e) {
            log.error("error closing JCR input stream [" + FALLBACK_SLING_CONFIG + "]", e);
          }
        }
      }

      // return a JSON object built from the file content
      return buffer.toString();

    } catch (RepositoryException e) {
      throw new DynamicConfigurationServiceException("Could not login to JCR repository", e);
    }
    finally {
      if (session != null)
      {
        session.logout();
      }
    }

  }

  /**
   * Process all configuration files into a single JSONObject. The master/default configuration file
   * is read first. Overrides are read (any *.json file in the custom directory) and are written into
   * the JSONObject in order to override configuration.
   *
   * @return
   * @throws IOException
   * @throws DynamicConfigurationServiceException
   */
  protected final String collectConfiguration ()
     throws IOException, JSONException, DynamicConfigurationServiceException {
    String config = null;
    
    final File customConfig = new File(getCustomDir().getAbsolutePath() + File.separator + DFT_CONFIG_FILENAME);
    config = readFileAsString(customConfig);

    if (config != null){
    	log.info("Read custom config.js file from {}", customConfig.getAbsolutePath());
    }
    else {
	    //read the main configuration
	    final File masterConfigFile = getConfigurationFile();
	
	    if (masterConfigFile != null) {
	      try {
	        config = collectConfiguration(masterConfigFile);
	      } catch (Exception e) {
	        log.error ("error processing master configuration file", e);
	      }
	    }
	
	    if (masterConfigFile == null || config == null) {
	      log.info("no master configuration file supplied - trying JCR");
	      config = collectConfigurationFromJCR();
	
	      if (config == null) {
	        throw new DynamicConfigurationServiceException("could not retrieve master configuration from filesystem or JCR");
	      }
	    }
    }
    return config;
  }

  /**
   * Produces a unique cache key representing the state of the configuration. This key may be used in the
   * GET request like the following:
   *
   *  /system/dynamic-config/config.[configurationCacheKey].json
   *
   * As long as the key remains the same the browser and/or load balancer will be able to preserve a cached
   * copy of the config. If the config changes, the configurationCacheKey will change and the browser/load
   * balancer will be forced to pass the GET request to the DynamicGlobalConfigurationServlet to get the new config.
   *
   * @return
   * @throws DynamicConfigurationServiceException
   */
  @Override
  public String getConfigurationCacheKey() throws DynamicConfigurationServiceException {

    String config = null;

    try {
      config = collectConfiguration();
    } catch (Exception e) {
      throw new DynamicConfigurationServiceException("error calculating cache key", e);
    }

    // calculate an MD5 sum of the JSON string. Because this implementation happens to preserve
    //  the order of elements in the JSON that MD5 sum will remain constant for unchanged content
    String key = null;

    if (MD5 != null) {
      MD5.reset();
      MD5.update(config.toString().getBytes());
      byte[] md5sum = MD5.digest();
      BigInteger bigInt = new BigInteger(1, md5sum);
      key = bigInt.toString(16);
    }

    // if the MD5 message digest object is unavailable send a random UUID - no caching will work
    if (key == null) {
      key = UUID.randomUUID().toString();
      log.warn ("error creating cache key - using random UUID");
    }

    log.debug ("configuration cache key: " + key);

    return key;
  }

  /**
   * write the configuration JSON to the out stream
   *
   * @param out
   * @throws DynamicConfigurationServiceException
   */
  @Override
  public void writeConfigurationJSON(OutputStream out) throws DynamicConfigurationServiceException {
    try {
      String config = collectConfiguration();
      out.write(config.toString().getBytes());
    } catch (Exception e) {
      throw new DynamicConfigurationServiceException("failed to write configuration to stream", e);
    }
  }

  /**
   * This file backed instance is not writeable
   *
   * @return always returns false
   */
  @Override
  public boolean isWriteable() {
    return false;
  }

  /**
   * This file backed instance is not writeable - this always throws an error.
   *
   * @param json
   * @throws DynamicConfigurationServiceException
   */
  @Override
  public void mergeConfigurationJSON(final String json) throws DynamicConfigurationServiceException {
    throw new DynamicConfigurationServiceException("configuration is not writeable");
  }
}