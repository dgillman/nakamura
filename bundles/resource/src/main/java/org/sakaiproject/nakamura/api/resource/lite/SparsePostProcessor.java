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
package org.sakaiproject.nakamura.api.resource.lite;

import java.util.List;

import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.servlets.post.Modification;

/**
 * The <code>SlingPostProcessor</code> interface defines a service API to be
 * implemented by service providers extending the Sling default POST servlet.
 * Service providers may register OSGi services of this type to be used by the
 * Sling default POST servlet to handle specific operations.
 * <p>
 * During a request the <code>SlingPostOperation</code> service is called
 * with a list of registered post processors. After the operation has performed
 * its changes but before the changes are persistet, all post processors
 * are called.
 */
public interface SparsePostProcessor {

    /**
     * Process the current request.
     * The post processor can inspect the list of changes and perform additional
     * changes. If the processor performs a change it should make the change
     * and add a {@link Modification} object to the changes list.
     * @param request The current request.
     * @param changes The list of changes for this request.
     */
    void process(SlingHttpServletRequest request, List<Modification> changes)
    throws Exception;
}
