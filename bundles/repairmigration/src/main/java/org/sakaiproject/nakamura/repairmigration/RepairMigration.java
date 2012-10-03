/*
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

import org.apache.felix.scr.annotations.Reference;
import org.apache.felix.scr.annotations.sling.SlingServlet;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.SlingHttpServletResponse;
import org.apache.sling.api.servlets.SlingSafeMethodsServlet;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.service.component.ComponentContext;
import org.sakaiproject.nakamura.api.lite.Repository;
import org.sakaiproject.nakamura.api.lite.Session;
import org.sakaiproject.nakamura.api.lite.StorageClientUtils;
import org.sakaiproject.nakamura.api.lite.authorizable.AuthorizableManager;
import org.sakaiproject.nakamura.api.lite.content.Content;
import org.sakaiproject.nakamura.api.lite.content.ContentManager;
import org.sakaiproject.nakamura.repairmigration.api.DocRepair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletResponse;

@SlingServlet(methods = "GET", paths = "/system/repairmigration", generateComponent = true)
public class RepairMigration extends SlingSafeMethodsServlet {
  private static final long serialVersionUID = 1L;
  private Logger LOGGER = LoggerFactory.getLogger(RepairMigration.class);

  @Reference
  private Repository repository;

  @Reference
  private DocRepair docRepair;

  BundleContext bundleContext;
  ComponentContext componentContext;
  ContentManager cm;
  SwissArmyClassLoader swissArmy;

  protected void activate(ComponentContext context) {
    componentContext = context;
    bundleContext = componentContext.getBundleContext();

    swissArmy = new SwissArmyClassLoader();

    try {
      Session adminSession = repository.loginAdministrative();
      cm = adminSession.getContentManager();
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  /**
   * Scary classloader trickery time. Search for a class by querying the classloaders of
   * every loaded bundle until we find a match. Your Private-Package headers don't scare
   * ME.
   **/
  class SwissArmyClassLoader extends URLClassLoader {
    ClassLoader baseLoader;

    @SuppressWarnings({ "rawtypes" })
    private ConcurrentHashMap<String, Class> classCache = new ConcurrentHashMap<String, Class>();

    public SwissArmyClassLoader() {
      super(new URL[] {}, Thread.currentThread().getContextClassLoader());
    }

    public SwissArmyClassLoader(ClassLoader baseLoader) {
      super(new URL[] {}, baseLoader);
      this.baseLoader = baseLoader;
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    protected Class findClass(String name) throws ClassNotFoundException {
      if (baseLoader != null) {
        try {
          return baseLoader.loadClass(name);
        } catch (ClassNotFoundException e) {
          ; // OK... you asked for it
        }
      }

      if (name.contains(".")) {
        Bundle[] bundles = bundleContext.getBundles();

        for (Bundle b : bundles) {
          try {
            if (b != bundleContext.getBundle()) {
              return b.loadClass(name);
            }
          } catch (ClassNotFoundException e) {
            // keep trying...
          }
        }
      }

      throw new ClassNotFoundException("Couldn't find class: " + name);
    }

    @SuppressWarnings("rawtypes")
    public Class findClassInAnyBundle(String name) throws ClassNotFoundException {
      if (classCache.get(name) == null) {
        classCache.put(name, findClass(name));
      }

      Class clz = classCache.get(name);

      return clz;
    }
  }

  /**
   * Using a custom classloader at run-time requires a fair bit of reflection. For
   * example, I've been reflecting on whether I really want to write Java at all. While
   * I'm figuring that out, maybe this class will make life easier.
   **/
  class ReflectionObject {
    @SuppressWarnings("rawtypes")
    private Class clz;
    private Object obj;

    @SuppressWarnings("rawtypes")
    public ReflectionObject(Class clz, Object obj) {
      this.clz = clz;
      this.obj = obj;
    }

    public ReflectionObject(String className, Object obj) throws ClassNotFoundException {
      this(swissArmy.findClassInAnyBundle(className), obj);
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    public Object call(String methodName, Object... args) throws Exception {
      Class[] types = new Class[args.length];

      for (int i = 0; i < args.length; i++) {
        types[i] = args[i].getClass();
      }

      Method m = clz.getDeclaredMethod(methodName, types);

      return m.invoke(obj, args);
    }

    public Object getField(String fieldName) throws Exception {
      Field field = clz.getDeclaredField(fieldName);
      field.setAccessible(true);
      return field.get(obj);
    }
  }

  /**
   * Finally... the bits that actually do the migration.
   **/
  @SuppressWarnings({ "rawtypes", "unchecked" })
  private int dumpAllPaths(ReflectionObject storageClient, File pathList)
      throws Exception {
    FileWriter outFile = null;
    Iterator allPaths = null;

    int lineCount = 0;

    try {
      outFile = new FileWriter(pathList);
      allPaths = (Iterator) storageClient.call("listAll", "n", "cn");

      while (allPaths.hasNext()) {
        lineCount++;
        ReflectionObject row = new ReflectionObject(
            "org.sakaiproject.nakamura.lite.storage.SparseRow", allPaths.next());
        Map<String, Object> props = (Map<String, Object>) row.call("getProperties");

        if (props.containsKey("_path") && !props.containsKey("_:cid")) {
          outFile.write((String) props.get("_path"));
          outFile.write("\n");
        }
      }
    } finally {
      if (outFile != null) {
        outFile.close();
      }

      if (allPaths != null) {
        new ReflectionObject("org.sakaiproject.nakamura.lite.storage.Disposable",
            allPaths).call("close");
      }
    }

    return lineCount;
  }

  private void migratePath(String path) {
    try {
      Content content = cm.get(path);

      if (docRepair.detectMigrationErrors(content)) {
        LOGGER.info("contains migration errors: {}", path);
      }
    } catch (Exception e) {
      if (path.endsWith("/docstructure")) {
        LOGGER.info("Skipping path '{}'", path);
      } else {
        LOGGER.error("There were problems migrating path '" + path + "'", e);
      }
    }
  }

  /**
   * {@inheritDoc}
   * 
   * @see org.apache.sling.api.servlets.SlingSafeMethodsServlet#doGet(org.apache.sling.api.SlingHttpServletRequest,
   *      org.apache.sling.api.SlingHttpServletResponse)
   */
  @Override
  protected void doGet(SlingHttpServletRequest request, SlingHttpServletResponse response)
      throws ServletException, IOException {

    try {
      final Session session = StorageClientUtils.adaptToSession(request
          .getResourceResolver().adaptTo(javax.jcr.Session.class));
      final AuthorizableManager aM = session.getAuthorizableManager();
      // only allow admins to call this end REST point
      if (aM.getUser().isAdmin()) {
        // Sigh. This is going to be tedious.
        ReflectionObject cmImpl = new ReflectionObject(
            "org.sakaiproject.nakamura.lite.content.ContentManagerImpl", cm);

        ReflectionObject storageClient = new ReflectionObject(
            "org.sakaiproject.nakamura.lite.storage.StorageClient",
            cmImpl.getField("client"));

        File pathList = File.createTempFile("oae-paths", "txt");
        pathList.deleteOnExit();

        LOGGER.info("Dumping all paths to {}", pathList);
        int totalPathCount = dumpAllPaths(storageClient, pathList);
        LOGGER.info("Finished generating path list.  Here we go!");

        BufferedReader paths = new BufferedReader(new FileReader(pathList));

        try {
          String path;
          int pathCount = 0;
          while ((path = paths.readLine()) != null) {
            pathCount++;

            if ((pathCount % 1000) == 0) {
              LOGGER.info("Migrated {} out of {} paths.", pathCount, totalPathCount);
            }

            migratePath(path);

//            if (handleVersions) {
//              migrateVersions(path);
//            }
          }
        } finally {
          paths.close();
        }
      } else {
        response.sendError(HttpServletResponse.SC_FORBIDDEN);
        return;
      }
    } catch (Exception e) {
      throw new ServletException(e);
    }
  }
}
