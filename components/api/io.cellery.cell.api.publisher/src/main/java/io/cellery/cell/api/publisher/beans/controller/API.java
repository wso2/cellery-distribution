/*
 *  Copyright (c) 2018, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 *  WSO2 Inc. licenses this file to you under the Apache License,
 *  Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package io.cellery.cell.api.publisher.beans.controller;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.cellery.cell.api.publisher.utils.Constants;

import java.util.List;

/** Class to represent API. */
public class API {

  @JsonProperty(Constants.JsonParamNames.CONTEXT)
  private String context;

  @JsonProperty(Constants.JsonParamNames.DEFINITION)
  private List<ApiDefinition> definitions;

  @JsonProperty(Constants.JsonParamNames.DESTINATION)
  private ApiDestination destination;

  @JsonProperty(Constants.JsonParamNames.GLOBAL)
  private boolean global;

  @JsonProperty(Constants.JsonParamNames.AUTHENTICATE)
  private boolean authenticate;

  @JsonProperty(Constants.JsonParamNames.PORT)
  private int port;

  @JsonProperty(Constants.JsonParamNames.VERSION)
  private String version;

  public String getContext() {
    return context;
  }

  public void setContext(String context) {
    this.context = context;
  }

  public List<ApiDefinition> getDefinitions() {
    return definitions;
  }

  public void setDefinitions(List<ApiDefinition> definitions) {
    this.definitions = definitions;
  }

  public boolean isGlobal() {
    return global;
  }

  public void setGlobal(boolean global) {
    this.global = global;
  }

  public boolean isAuthenticate() {
    return authenticate;
  }

  public void setAuthenticate(boolean authenticate) {
    this.authenticate = authenticate;
  }

  public ApiDestination getDestination() {
    return destination;
  }

  public void setDestination(ApiDestination destination) {
    this.destination = destination;
  }

  public int getPort() {
    return port;
  }

  public void setPort(int port) {
    this.port = port;
  }

  public String getVersion() {
    return version;
  }

  public void setVersion(String version) {
    this.version = version;
  }
}
