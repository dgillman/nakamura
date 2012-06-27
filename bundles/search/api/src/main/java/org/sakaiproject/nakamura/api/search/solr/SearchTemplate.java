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
